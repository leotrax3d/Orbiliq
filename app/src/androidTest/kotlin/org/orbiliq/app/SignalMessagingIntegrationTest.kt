package org.orbiliq.app

import android.net.Uri
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.signal.*

/** Explicit real-device tests. No credentials, private contents or bindings in assertion output. */
class SignalMessagingIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()

    @Test fun statusConsentDoesNotPermitMessaging() = runBlocking {
        assumeTrue("Requires explicit denied-grant fixture", args.getString("signalMessaging") == "denied")
        assertNull(TestHost.transport(context).binding())
    }

    @Test fun consentedBindingIsStableAndForeignBindingCannotRead() = runBlocking {
        assumeTrue("Requires native message consent", args.getString("signalMessaging") == "granted")
        val peer = TestHost.transport(context)
        val binding = withTimeout(15_000) {
            var value = peer.binding()
            while (value == null) { kotlinx.coroutines.delay(200); value = peer.binding() }
            value
        }
        assertTrue(peer.binding()?.id == binding.id)
        val threads = peer.threads(binding)
        assertTrue(threads.size <= SignalMessagingContract.MAX_THREADS)
        assertTrue(threads.single { it.peer == "self" }.let { it.direct && it.canSend })
        val wrong = if (binding.id == "00".repeat(32)) "11".repeat(32) else "00".repeat(32)
        assertTrue("Foreign binding was accepted", TestHost.foreignBindingRejected(context, wrong))
    }

    @Test fun selfTextTraversesPersistentOutboxNativeQueueAndProjectionOnce() = runBlocking {
        assumeTrue("Explicit self-send opt-in required", args.getString("signalSelfSend") == "true")
        val repository = TestHost.messenger(context)
        val snapshot = withTimeout(60_000) { repository.observeMessenger().first { s ->
            s.accounts.any { account -> account.ownIdentityId != null && s.entries.any { it.route.conversation.participants == setOf(account.ownIdentityId) } }
        } }
        val own = snapshot.accounts.single().ownIdentityId!!
        val route = snapshot.entries.single { it.route.conversation.participants == setOf(own) }.route
        val target = ConversationTarget.ProviderChat(route.conversation.ref)
        val marker = "Orbiliq Signal Integrationstest ${UUID.randomUUID()}"
        // A persisted snapshot is visible before the session has loaded native threads; sending stays closed until then.
        withTimeout(60_000) {
            while (repository.sendRoutes(target, marker).none { it.availability is Availability.Available }) kotlinx.coroutines.delay(200)
        }
        val accepted = repository.sendText(target, route, marker)
        assertTrue("Self text was not queued: " + accepted::class.simpleName + "/" + ((accepted as? SendTextResult.Unavailable)?.availability?.let { it::class.simpleName + ":" + ((it as? Availability.Restricted)?.reason ?: (it as? Availability.Unsupported)?.code) }), accepted is SendTextResult.Queued)
        val commandId = (accepted as SendTextResult.Queued).commandId
        val timeline = withTimeout(90_000) { repository.observeTimeline(target).first { timeline ->
            timeline.messages.any { (it.content as? MessageContent.Text)?.text == marker && it.ref != null &&
                it.delivery in setOf(DeliveryState.Sent, DeliveryState.Delivered, DeliveryState.Read) }
        } }
        assertTrue("Own test message was duplicated", timeline.messages.count { (it.content as? MessageContent.Text)?.text == marker } == 1)
        val peer = TestHost.transport(context)
        val binding = requireNotNull(peer.binding())
        assertTrue(SignalMessagingSession.accountFor(binding).id == route.conversation.ref.accountId)
        val repeat = peer.sendText(binding, commandId.value, route.conversation.ref.remoteConversationId.toLong(), marker)
        val original = timeline.messages.single { (it.content as? MessageContent.Text)?.text == marker }.ref!!
        assertTrue("Native duplicate claim was not reconciled", repeat is SignalSendResult.Queued && repeat.messageId.toString() == original.remoteMessageId)
    }

    /** Opt-in, own "Notiz an mich" only: reply, reaction and a synthetic image through the real outbox and companion. */
    @Test fun selfReplyReactionAndImageRoundTrip() = runBlocking {
        assumeTrue("Explicit self-send opt-in required", args.getString("signalSelfSend") == "true")
        val repository = TestHost.messenger(context)
        val snapshot = withTimeout(60_000) { repository.observeMessenger().first { s ->
            s.accounts.any { account -> account.ownIdentityId != null && s.entries.any { it.route.conversation.participants == setOf(account.ownIdentityId) } }
        } }
        val own = snapshot.accounts.single().ownIdentityId!!
        val route = snapshot.entries.single { it.route.conversation.participants == setOf(own) }.route
        val target = ConversationTarget.ProviderChat(route.conversation.ref)
        val marker = "Orbiliq Signal Antworttest ${UUID.randomUUID()}"
        withTimeout(60_000) {
            while (repository.sendRoutes(target, marker).none { it.availability is Availability.Available }) kotlinx.coroutines.delay(200)
        }
        assertTrue(repository.sendText(target, route, marker) is SendTextResult.Queued)
        val original = withTimeout(90_000) { repository.observeTimeline(target).first { t ->
            t.messages.any { (it.content as? MessageContent.Text)?.text == marker && it.ref != null && it.delivery in setOf(DeliveryState.Sent, DeliveryState.Delivered, DeliveryState.Read) }
        } }.messages.single { (it.content as? MessageContent.Text)?.text == marker }
        val reply = "Antwort $marker"
        val replyResult = repository.sendText(target, route, reply, original.id)
        assertTrue("Reply not queued: " + replyResult::class.simpleName, replyResult is SendTextResult.Queued)
        withTimeout(90_000) { repository.observeTimeline(target).first { t ->
            t.messages.any { (it.content as? MessageContent.Text)?.text == reply && it.ref != null && it.replyTo == original.id }
        } }
        val thumbs = "👍"
        assertTrue("Reaction not offered", repository.canReact(original.id))
        assertTrue(repository.react(original.id, thumbs))
        withTimeout(90_000) { repository.observeTimeline(target).first { t -> t.reactions[original.id]?.get(thumbs) == 1 } }
        assertTrue("Image sending not offered", repository.canSendImage(route))
        val bitmap = android.graphics.Bitmap.createBitmap(64, 48, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(40, 90, 200)) }
        val jpeg = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val caption = "Bildtest $marker"
        val imageResult = repository.sendImage(target, route, jpeg, caption)
        assertTrue("Image not queued: " + imageResult::class.simpleName, imageResult is SendTextResult.Queued)
        val sent = withTimeout(120_000) { repository.observeTimeline(target).first { t ->
            t.messages.any { (it.content as? MessageContent.Media)?.caption == caption && it.ref != null &&
                (it.content as MessageContent.Media).attachments.single().value.contains(":image:") }
        } }.messages.single { (it.content as? MessageContent.Media)?.caption == caption }
        val bytes = repository.loadImage(sent.id, (sent.content as MessageContent.Media).attachments.single())
        assertTrue("Sent image not readable", bytes != null && android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null)
    }

    /** Opt-in, own "Notiz an mich" only: a synthetic text document is sent unchanged and read back from the companion. */
    @Test fun selfDocumentRoundTripKeepsBytes() = runBlocking {
        assumeTrue("Explicit self-send opt-in required", args.getString("signalSelfSend") == "true")
        val repository = TestHost.messenger(context)
        val snapshot = withTimeout(60_000) { repository.observeMessenger().first { s ->
            s.accounts.any { account -> account.ownIdentityId != null && s.entries.any { it.route.conversation.participants == setOf(account.ownIdentityId) } }
        } }
        val own = snapshot.accounts.single().ownIdentityId!!
        val route = snapshot.entries.single { it.route.conversation.participants == setOf(own) }.route
        val target = ConversationTarget.ProviderChat(route.conversation.ref)
        withTimeout(60_000) { while (!repository.canSendFile(route)) kotlinx.coroutines.delay(200) }
        val content = "Orbiliq Dokumenttest ${UUID.randomUUID()}".toByteArray()
        val caption = "Dateitest ${UUID.randomUUID()}"
        val result = repository.sendFile(target, route, content, "text/plain", "orbiliq-test.txt", caption)
        assertTrue("File not queued: " + result::class.simpleName, result is SendTextResult.Queued)
        val sent = withTimeout(120_000) { repository.observeTimeline(target).first { t ->
            t.messages.any { (it.content as? MessageContent.Media)?.caption == caption && it.ref != null &&
                (it.content as MessageContent.Media).attachments.single().value.contains(":file:") }
        } }.messages.single { (it.content as? MessageContent.Media)?.caption == caption }
        val id = (sent.content as MessageContent.Media).attachments.single()
        assertTrue(repository.attachmentKind(id) == AttachmentKind.File)
        val loaded = repository.loadAttachment(sent.id, id)
        assertTrue("Document bytes differ", loaded != null && loaded.bytes.contentEquals(content))
    }

    /** Opt-in, own "Notiz an mich" only: edit an own text, then delete it for everyone. */
    @Test fun selfEditThenDeleteForEveryone() = runBlocking<Unit> {
        assumeTrue("Explicit self-send opt-in required", args.getString("signalSelfSend") == "true")
        val repository = TestHost.messenger(context)
        val snapshot = withTimeout(60_000) { repository.observeMessenger().first { s ->
            s.accounts.any { account -> account.ownIdentityId != null && s.entries.any { it.route.conversation.participants == setOf(account.ownIdentityId) } }
        } }
        val own = snapshot.accounts.single().ownIdentityId!!
        val route = snapshot.entries.single { it.route.conversation.participants == setOf(own) }.route
        val target = ConversationTarget.ProviderChat(route.conversation.ref)
        val marker = "Orbiliq Bearbeitungstest ${UUID.randomUUID()}"
        withTimeout(60_000) { while (repository.sendRoutes(target, marker).none { it.availability is Availability.Available }) kotlinx.coroutines.delay(200) }
        assertTrue(repository.sendText(target, route, marker) is SendTextResult.Queued)
        val original = withTimeout(90_000) { repository.observeTimeline(target).first { t ->
            t.messages.any { (it.content as? MessageContent.Text)?.text == marker && it.ref != null && it.delivery in setOf(DeliveryState.Sent, DeliveryState.Delivered, DeliveryState.Read) }
        } }.messages.single { (it.content as? MessageContent.Text)?.text == marker }
        withTimeout(60_000) { while (!repository.canEdit(original.id)) kotlinx.coroutines.delay(200) }
        val edited = "$marker (bearbeitet)"
        assertTrue(repository.edit(original.id, edited))
        val revised = withTimeout(90_000) { repository.observeTimeline(target).first { t ->
            t.messages.any { (it.content as? MessageContent.Text)?.text == edited && it.ref != null } &&
                t.messages.none { (it.content as? MessageContent.Text)?.text == marker }
        } }.messages.single { (it.content as? MessageContent.Text)?.text == edited }
        withTimeout(60_000) { while (!repository.canDeleteForEveryone(revised.id)) kotlinx.coroutines.delay(200) }
        assertTrue(repository.deleteForEveryone(revised.id))
        withTimeout(90_000) { repository.observeTimeline(target).first { t ->
            t.messages.none { (it.content as? MessageContent.Text)?.text?.startsWith(marker) == true }
        } }
    }

    private suspend fun selfChat(repository: MessengerRepository): Pair<SendRoute, ConversationTarget> {
        val snapshot = withTimeout(60_000) { repository.observeMessenger().first { s ->
            s.accounts.any { account -> account.ownIdentityId != null && s.entries.any { it.route.conversation.participants == setOf(account.ownIdentityId) } }
        } }
        val own = snapshot.accounts.single().ownIdentityId!!
        val route = snapshot.entries.single { it.route.conversation.participants == setOf(own) }.route
        val target = ConversationTarget.ProviderChat(route.conversation.ref)
        withTimeout(60_000) { while (repository.sendRoutes(target, "x").none { it.availability is Availability.Available }) kotlinx.coroutines.delay(200) }
        return route to target
    }

    /** Opt-in, own "Notiz an mich" only: remove an own reaction, mark read, then delete for me. */
    @Test fun selfUnreactReadAndDeleteForMe() = runBlocking<Unit> {
        assumeTrue("Explicit self-send opt-in required", args.getString("signalSelfSend") == "true")
        val repository = TestHost.messenger(context)
        val (route, target) = selfChat(repository)
        val marker = "Orbiliq Aktionstest ${UUID.randomUUID()}"
        assertTrue(repository.sendText(target, route, marker) is SendTextResult.Queued)
        val message = withTimeout(90_000) { repository.observeTimeline(target).first { t ->
            t.messages.any { (it.content as? MessageContent.Text)?.text == marker && it.ref != null && it.delivery in setOf(DeliveryState.Sent, DeliveryState.Delivered, DeliveryState.Read) }
        } }.messages.single { (it.content as? MessageContent.Text)?.text == marker }
        val thumbs = "👍"
        assertTrue(repository.react(message.id, thumbs))
        withTimeout(90_000) { repository.observeTimeline(target).first { it.reactions[message.id]?.get(thumbs) == 1 } }
        assertTrue(repository.removeReaction(message.id))
        withTimeout(90_000) { repository.observeTimeline(target).first { it.reactions[message.id].isNullOrEmpty() } }
        assertTrue("Mark read refused", repository.markRead(route, java.time.Instant.now()))
        repository.setTyping(route, false) // Result depends on the user's own Signal setting.
        withTimeout(60_000) { while (!repository.canDeleteForMe(message.id)) kotlinx.coroutines.delay(200) }
        assertTrue(repository.deleteForMe(message.id))
        withTimeout(90_000) { repository.observeTimeline(target).first { t -> t.messages.none { (it.content as? MessageContent.Text)?.text == marker } } }
    }

    /** Opt-in, own "Notiz an mich" only: synthetic voice note and a synthetic MP4 carrying a GPS location. */
    @Test fun selfVoiceNoteAndVideoArriveWithoutLocation() = runBlocking<Unit> {
        assumeTrue("Explicit self-send opt-in required", args.getString("signalSelfSend") == "true")
        val repository = TestHost.messenger(context)
        val (route, target) = selfChat(repository)
        assertTrue(repository.canSendMedia(route, AttachmentKind.VoiceNote))
        val voice = repository.sendMedia(target, route, AttachmentKind.VoiceNote, silentAac(), "audio/aac", null)
        assertTrue("Voice not queued: " + voice::class.simpleName, voice is SendTextResult.Queued)
        val caption = "Videotest ${UUID.randomUUID()}"
        val located = syntheticMp4WithLocation()
        assertTrue("Fixture lacks a location", locationOf(located) != null)
        val video = repository.sendMedia(target, route, AttachmentKind.Video, located, "video/mp4", caption)
        assertTrue("Video not queued: " + video::class.simpleName, video is SendTextResult.Queued)
        val sent = withTimeout(180_000) { repository.observeTimeline(target).first { t ->
            t.messages.any { (it.content as? MessageContent.Media)?.caption == caption && it.ref != null &&
                (it.content as MessageContent.Media).attachments.single().value.contains(":file:") }
        } }.messages.single { (it.content as? MessageContent.Media)?.caption == caption }
        val id = (sent.content as MessageContent.Media).attachments.single()
        assertTrue(repository.attachmentKind(id) == AttachmentKind.Video)
        val back = requireNotNull(repository.loadAttachment(sent.id, id))
        assertNull("Location survived", locationOf(back.bytes))
    }

    private fun locationOf(mp4: ByteArray): String? {
        val file = java.io.File.createTempFile("orbiliq-test", ".mp4", context.cacheDir)
        return try {
            file.writeBytes(mp4)
            android.media.MediaMetadataRetriever().run { try { setDataSource(file.path); extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_LOCATION) } finally { release() } }
        } finally { file.delete() }
    }

    private fun silentAac(): ByteArray {
        val format = android.media.MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 1).apply {
            setInteger(android.media.MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(android.media.MediaFormat.KEY_BIT_RATE, 64000)
        }
        val codec = android.media.MediaCodec.createEncoderByType("audio/mp4a-latm")
        codec.configure(format, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
        val out = java.io.ByteArrayOutputStream()
        val info = android.media.MediaCodec.BufferInfo()
        var fed = 0; var done = false; var eos = false
        while (!done) {
            if (!eos) { val i = codec.dequeueInputBuffer(10_000); if (i >= 0) {
                val buffer = codec.getInputBuffer(i)!!; buffer.clear()
                val n = minOf(buffer.capacity(), 2048)
                buffer.put(ByteArray(n)); fed += n
                eos = fed > 44100 * 2
                codec.queueInputBuffer(i, 0, n, fed * 1_000_000L / (44100 * 2), if (eos) android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
            } }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o >= 0) {
                if (info.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                    val len = info.size + 7
                    out.write(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), (0x40 or (4 shl 2)).toByte(), (0x40 or (len shr 11)).toByte(),
                        ((len shr 3) and 0xFF).toByte(), (((len and 7) shl 5) or 0x1F).toByte(), 0xFC.toByte()))
                    val chunk = ByteArray(info.size); codec.getOutputBuffer(o)!!.get(chunk); out.write(chunk)
                }
                done = info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                codec.releaseOutputBuffer(o, false)
            }
        }
        codec.stop(); codec.release()
        return out.toByteArray()
    }

    private fun syntheticMp4WithLocation(): ByteArray {
        val file = java.io.File.createTempFile("orbiliq-located", ".mp4", context.cacheDir)
        try {
            val format = android.media.MediaFormat.createVideoFormat("video/avc", 320, 240).apply {
                setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(android.media.MediaFormat.KEY_BIT_RATE, 500_000); setInteger(android.media.MediaFormat.KEY_FRAME_RATE, 15)
                setInteger(android.media.MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val codec = android.media.MediaCodec.createEncoderByType("video/avc")
            codec.configure(format, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface(); codec.start()
            val muxer = android.media.MediaMuxer(file.path, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setLocation(52.52f, 13.405f)
            val info = android.media.MediaCodec.BufferInfo()
            var track = -1; var frames = 0; var done = false
            while (!done) {
                if (frames < 30) {
                    val canvas = surface.lockHardwareCanvas()
                    canvas.drawColor(android.graphics.Color.rgb(20 + frames * 7, 90, 200)); surface.unlockCanvasAndPost(canvas)
                    frames++
                    if (frames == 30) codec.signalEndOfInputStream()
                }
                val o = codec.dequeueOutputBuffer(info, 20_000)
                when {
                    o == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                    o >= 0 -> {
                        if (info.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && track >= 0)
                            muxer.writeSampleData(track, codec.getOutputBuffer(o)!!, info)
                        done = info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(o, false)
                    }
                }
            }
            codec.stop(); codec.release(); surface.release(); muxer.stop(); muxer.release()
            return file.readBytes()
        } finally { file.delete() }
    }
}
