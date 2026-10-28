package org.orbiliq.app

import android.net.Uri
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.orbiliq.app.signal.AndroidSignalMessaging
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.signal.*

/** Explicit real-device tests. No credentials, private contents or bindings in assertion output. */
class SignalMessagingIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()

    @Test fun statusConsentDoesNotPermitMessaging() = runBlocking {
        assumeTrue("Requires explicit denied-grant fixture", args.getString("signalMessaging") == "denied")
        assertNull(AndroidSignalMessaging(context).binding())
    }

    @Test fun consentedBindingIsStableAndForeignBindingCannotRead() = runBlocking {
        assumeTrue("Requires native message consent", args.getString("signalMessaging") == "granted")
        val peer = AndroidSignalMessaging(context)
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
        val denied = context.contentResolver.call(Uri.parse("content://${SignalBridgeContract.AUTHORITY}"), "messageThreads", null,
            Bundle().apply { putInt("messageProtocolVersion", SignalMessagingContract.VERSION); putString("accountBinding", wrong) })!!
        assertTrue(denied.keySet() == setOf("error"))
        assertTrue(denied.getString("error") == "Unavailable")
    }

    @Test fun selfTextTraversesPersistentOutboxNativeQueueAndProjectionOnce() = runBlocking {
        assumeTrue("Explicit self-send opt-in required", args.getString("signalSelfSend") == "true")
        val repository = EntryPointAccessors.fromApplication(context, ConversationRepositoryEntryPoint::class.java).conversations() as MessengerRepository
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
        val peer = AndroidSignalMessaging(context)
        val binding = requireNotNull(peer.binding())
        assertTrue(SignalMessagingSession.accountFor(binding).id == route.conversation.ref.accountId)
        val repeat = peer.sendText(binding, commandId.value, route.conversation.ref.remoteConversationId.toLong(), marker)
        val original = timeline.messages.single { (it.content as? MessageContent.Text)?.text == marker }.ref!!
        assertTrue("Native duplicate claim was not reconciled", repeat is SignalSendResult.Queued && repeat.messageId.toString() == original.remoteMessageId)
    }

    /** Opt-in, own "Notiz an mich" only: reply, reaction and a synthetic image through the real outbox and companion. */
    @Test fun selfReplyReactionAndImageRoundTrip() = runBlocking {
        assumeTrue("Explicit self-send opt-in required", args.getString("signalSelfSend") == "true")
        val repository = EntryPointAccessors.fromApplication(context, ConversationRepositoryEntryPoint::class.java).conversations() as MessengerRepository
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
        val repository = EntryPointAccessors.fromApplication(context, ConversationRepositoryEntryPoint::class.java).conversations() as MessengerRepository
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
}
