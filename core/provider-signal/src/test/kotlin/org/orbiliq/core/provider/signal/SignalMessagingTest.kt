package org.orbiliq.core.provider.signal

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

class SignalMessagingTest {
    private val binding = SignalBinding("ab".repeat(32))
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private class Peer(private val initial: SignalBinding) : SignalMessagingTransport {
        @Volatile var current: SignalBinding? = initial
        val rows = java.util.concurrent.ConcurrentHashMap<Long, SignalText>()
        var sendCalls = 0
        var unknown = false
        var threadRows = listOf(SignalThread(7, "Fixture", true, true, "9"))
        override suspend fun binding() = current
        override suspend fun threads(binding: SignalBinding) = threadRows
        override suspend fun texts(binding: SignalBinding, thread: Long, beforeId: Long): SignalTextPage {
            val remaining = rows.values.filter { it.thread == thread && it.id < beforeId }.sortedByDescending { it.id }
            val page = remaining.take(SignalMessagingContract.PAGE_SIZE)
            return SignalTextPage(page, if (remaining.size > page.size) page.last().id else null)
        }
        override suspend fun lookup(binding: SignalBinding, thread: Long, ids: List<Long>) = ids.mapNotNull { rows[it]?.takeIf { row -> row.thread == thread } }
        var lastReplyTo: Long? = null
        var lastImage: String? = null
        var lastEdit: Long? = null
        override suspend fun deleteForEveryone(binding: SignalBinding, thread: Long, message: Long): SignalSendResult {
            rows.remove(message); return SignalSendResult.Queued(message)
        }
        val staged = mutableMapOf<String, Int>()
        override suspend fun stageImage(binding: SignalBinding, token: String, jpeg: ByteArray): Boolean { staged[token] = jpeg.size; return true }
        override suspend fun sendText(binding: SignalBinding, commandId: String, thread: Long, text: String, replyTo: Long?, image: String?, edit: Long?): SignalSendResult {
            sendCalls++; lastReplyTo = replyTo; lastImage = image; lastEdit = edit
            return if (unknown) SignalSendResult.Unknown else SignalSendResult.Queued(22)
        }
        var imageCalls = 0
        override suspend fun attachment(binding: SignalBinding, thread: Long, message: Long, attachment: Long): Pair<String, ByteArray>? {
            imageCalls++
            val row = rows[message] ?: return null
            row.images.find { it.id == attachment }?.let { return it.mimeType to byteArrayOf(1, 2, 3) }
            return row.files.find { it.id == attachment }?.let { it.mimeType to byteArrayOf(4, 5) }
        }
        val stagedFiles = mutableMapOf<String, String>()
        val slides = mutableMapOf<String, String>()
        override suspend fun stageFile(binding: SignalBinding, token: String, bytes: ByteArray, mimeType: String, name: String?, slide: String): Boolean {
            stagedFiles[token] = mimeType; slides[token] = slide; return true
        }
        val actions = mutableListOf<Pair<String, Map<String, Any>>>()
        override suspend fun action(binding: SignalBinding, method: String, extras: Map<String, Any>): SignalSendResult {
            actions += method to extras
            if (method == "messageDeleteLocal") rows.remove(extras["message"] as Long)
            return SignalSendResult.Queued((extras["message"] as? Long) ?: 1L)
        }
        var reactCalls = 0
        override suspend fun react(binding: SignalBinding, thread: Long, message: Long, emoji: String): SignalSendResult {
            reactCalls++
            val row = rows.getValue(message)
            rows[message] = SignalText(row.id, row.thread, row.sender, row.text, row.outgoing, row.timestamp, row.received, row.delivery,
                reactions = row.reactions.filter { it.actor != "self" } + SignalReaction("self", emoji))
            return SignalSendResult.Queued(message)
        }
        override suspend fun reconcileText(binding: SignalBinding, commandId: String, thread: Long): SignalSendResult =
            if (unknown) SignalSendResult.Unknown else SignalSendResult.Queued(22)
    }
    private fun text(id: Long = 11, delivery: String = "Sent", outgoing: Boolean = false) =
        SignalText(id, 7, if (outgoing) "self" else "9", "private fixture", outgoing, now.toEpochMilli(), now.toEpochMilli(), delivery)
    private suspend fun running(peer: Peer, block: suspend (SignalMessagingSession, MutableList<EventEnvelope>) -> Unit) = coroutineScope {
        val session = SignalMessagingSession(binding, peer, clock = clock, pollMillis = 10)
        val events = CopyOnWriteArrayList<EventEnvelope>()
        val collector = launch {
            try { session.events().collect { events += it } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Tests can inspect the fail-closed state after a source failure. */ }
        }
        try {
            withTimeout(5000) { session.state.first { it.sync == SyncState.Gap } }
            block(session, events)
        } finally { collector.cancelAndJoin(); session.close() }
    }
    private fun scope(session: SignalMessagingSession) = CapabilityScope.Conversation(ProviderId("signal"), ConversationRef(session.accountId, "7"))
    private suspend fun command(session: SignalMessagingSession, payload: CommandPayload = CommandPayload.SendText("hello")) =
        ProviderCommand(CommandId("fixture-command"), scope(session).ref, payload, now, now.plusSeconds(60), session.observeCapabilities(scope(session)).first().revision)

    @Test fun textCapabilitiesDoNotPromiseMediaGroupSendingOrHistory() = runBlocking {
        running(Peer(binding)) { session, _ ->
            val caps = session.observeCapabilities(scope(session)).first()
            assertEquals(setOf(Operations.SendText, Operations.Reply, Operations.React, Operations.sendMedia(AttachmentKind.Image), Operations.ReceiveText, SignalMessagingSession.QuoteReceive, SignalMessagingSession.ReactionReceive, SignalMessagingSession.ImageReceive,
                SignalMessagingSession.FileReceive, Operations.sendMedia(AttachmentKind.File), SignalMessagingSession.ReactionRemove,
                Operations.ReadReceipt, Operations.Typing, SignalMessagingSession.GroupRename, SignalMessagingSession.GroupLeave,
                Operations.sendMedia(AttachmentKind.VoiceNote), Operations.sendMedia(AttachmentKind.Video)), caps.operations.keys)
            assertTrue(session.evaluate(OperationRequest(scope(session), Operations.React)) is Availability.Available)
            assertTrue(session.evaluate(OperationRequest(scope(session), Operations.SendText, textLength = 4001)) is Availability.Restricted)
            assertTrue(session.evaluate(OperationRequest(scope(session), Operations.Reply, textLength = 2, sizeBytes = 2)) is Availability.Available)
            assertTrue(session.evaluate(OperationRequest(CapabilityScope.Conversation(ProviderId("signal"), ConversationRef(session.accountId, "unknown")), Operations.SendText, textLength = 2)) is Availability.Unsupported)
            assertEquals(ReplayClass.BestEffort, caps.operations.getValue(Operations.ReceiveText).replay)
        }
    }
    @Test fun snapshotReplaysAreDeduplicatedAndMissingMessagesBecomeDeletions() = runBlocking {
        val peer = Peer(binding).apply { rows[11] = text() }
        running(peer) { session, events ->
            withTimeout(5000) { while (events.none { it.payload is ProviderEvent.MessageUpserted }) delay(10) }
            delay(60)
            assertEquals(1, events.count { it.payload is ProviderEvent.MessageUpserted })
            peer.rows.clear()
            withTimeout(5000) { while (events.none { it.payload is ProviderEvent.MessageDeleted }) delay(10) }
            val cursor = events.last().cursor
            assertTrue(session.acknowledge(cursor) is OperationResult.Success)
            assertTrue(session.acknowledge(cursor.copy(epoch = "foreign")) is OperationResult.Rejected)
        }
    }
    @Test fun nativeQueueAcceptanceIsNotDeliveryConfirmation() = runBlocking {
        val peer = Peer(binding).apply { rows[22] = text(22, "Pending", true) }
        running(peer) { session, _ ->
            val command = command(session)
            assertTrue(session.execute(command) is DispatchResult.Accepted)
            assertEquals(ReconcileResult.StillUnknown, session.reconcile(ReconcileRequest(command.id, command.route)))
            peer.rows[22] = text(22, "Sent", true)
            assertTrue(session.reconcile(ReconcileRequest(command.id, command.route)) is ReconcileResult.Confirmed)
            assertEquals(1, peer.sendCalls)
        }
    }
    @Test fun uncertainNativeEffectIsNeverBlindlyResentDuringReconciliation() = runBlocking {
        val peer = Peer(binding).apply { unknown = true }
        running(peer) { session, _ ->
            val command = command(session)
            assertTrue(session.execute(command) is DispatchResult.OutcomeUnknown)
            repeat(3) { assertEquals(ReconcileResult.StillUnknown, session.reconcile(ReconcileRequest(command.id, command.route))) }
            assertEquals(1, peer.sendCalls)
        }
    }
    @Test fun stalePolicyAndForeignRoutesAreRejectedBeforeTransport() = runBlocking {
        val peer = Peer(binding)
        running(peer) { session, _ ->
            val command = command(session)
            assertTrue(session.execute(command.copy(capabilityRevision = "stale")) is DispatchResult.Rejected)
            assertTrue(session.execute(command.copy(route = ConversationRef(AccountId("foreign"), "7"))) is DispatchResult.Rejected)
            assertTrue(session.execute(command.copy(createdAt = now.minusSeconds(60), expiresAt = now)) is DispatchResult.Rejected)
            assertEquals(0, peer.sendCalls)
        }
    }
    @Test fun bindingChangeBlocksOldAccountAndClearsCapabilities() = runBlocking {
        val peer = Peer(binding)
        running(peer) { session, _ ->
            peer.current = SignalBinding("cd".repeat(32))
            withTimeout(5000) { session.state.first { it.auth == AuthState.Blocked } }
            assertTrue(session.observeCapabilities(scope(session)).first().operations.isEmpty())
            assertEquals(0, peer.sendCalls)
        }
    }
    @Test fun wireValuesDoNotExposePrivateContentsThroughDiagnostics() {
        assertEquals("SignalBinding(redacted)", binding.toString())
        assertEquals("SignalText(redacted)", text().toString())
        assertFalse(SignalThread(7, "private contact", true, true, "9").toString().contains("private"))
    }
    @Test fun keysetPagesImportBeyondTheFirstPage() = runBlocking {
        val peer = Peer(binding).apply { (1L..121L).forEach { rows[it] = text(it) } }
        running(peer) { _, events ->
            assertEquals(121, events.count { it.payload is ProviderEvent.MessageUpserted })
        }
    }
    @Test fun readOnlyGroupsExposeTextAndTitleButNeverSending() = runBlocking {
        val peer = Peer(binding).apply {
            threadRows = listOf(SignalThread(7, "Fixture group", false, false, "9"))
            rows[11] = text()
        }
        running(peer) { session, events ->
            assertTrue(events.any { it.payload is ProviderEvent.GroupChanged })
            val changed = events.mapNotNull { (it.payload as? ProviderEvent.ConversationChanged)?.conversation }.single()
            assertEquals(ConversationKind.Group, changed.kind)
            assertTrue(session.evaluate(OperationRequest(scope(session), Operations.ReceiveText)) is Availability.Available)
            assertTrue(session.evaluate(OperationRequest(scope(session), Operations.SendText, textLength = 4, sizeBytes = 4, targetKind = ConversationKind.Group)) is Availability.Restricted)
            assertTrue(session.execute(command(session)) is DispatchResult.Rejected)
            assertEquals(0, peer.sendCalls)
        }
    }
    @Test fun writableGroupsFollowTheCompanionsComposerRules() = runBlocking {
        val peer = Peer(binding).apply {
            threadRows = listOf(SignalThread(7, "Fixture group", false, true, "9"))
            rows[11] = text()
        }
        running(peer) { session, _ ->
            assertTrue(session.evaluate(OperationRequest(scope(session), Operations.SendText, textLength = 4, sizeBytes = 4, targetKind = ConversationKind.Group)) is Availability.Available)
            assertTrue(session.execute(command(session)) is DispatchResult.Accepted)
            assertEquals(1, peer.sendCalls)
            val messageScope = CapabilityScope.Message(ProviderId("signal"), MessageRef(scope(session).ref, "11"))
            assertTrue(session.evaluate(OperationRequest(messageScope, Operations.React)) is Availability.Available)
        }
    }
    @Test fun unsupportedNativeContentRemainsAnExplicitPlaceholder() = runBlocking {
        val peer = Peer(binding).apply { rows[11] = SignalText(11, 7, "9", "", false, now.toEpochMilli(), now.toEpochMilli(), "Sent", supported = false) }
        running(peer) { _, events ->
            assertTrue(events.mapNotNull { (it.payload as? ProviderEvent.MessageUpserted)?.message }.single().content is MessageContent.Unsupported)
        }
    }
    @Test fun receivedQuoteReferencesTheOriginalAndRepliesPassTheNativeOriginal() = runBlocking {
        val peer = Peer(binding).apply {
            rows[11] = text()
            rows[12] = SignalText(12, 7, "self", "reply fixture", true, now.toEpochMilli(), now.toEpochMilli(), "Sent", replyTo = 11)
        }
        running(peer) { session, events ->
            val messages = events.mapNotNull { (it.payload as? ProviderEvent.MessageUpserted)?.message }
            val original = messages.single { it.ref?.remoteMessageId == "11" }
            assertEquals(original.id, messages.single { it.ref?.remoteMessageId == "12" }.replyTo)
            assertEquals(null, original.replyTo)
            assertTrue(session.execute(command(session, CommandPayload.SendText("hi", MessageRef(scope(session).ref, "not-native")))) is DispatchResult.Rejected)
            assertEquals(0, peer.sendCalls)
            assertTrue(session.execute(command(session, CommandPayload.SendText("hi", MessageRef(scope(session).ref, "11")))) is DispatchResult.Accepted)
            assertEquals(11L, peer.lastReplyTo)
        }
    }
    private fun reactionEvents(events: List<EventEnvelope>) = events.mapNotNull { it.payload as? ProviderEvent.ReactionChanged }
        .map { Triple(it.actor.remoteIdentityId, it.reaction, it.removed) }
    @Test fun reactionSnapshotsBecomeAddChangeAndRemoveEvents() = runBlocking {
        val peer = Peer(binding).apply { rows[11] = SignalText(11, 7, "9", "x", false, 1, 1, "Sent", reactions = listOf(SignalReaction("9", "👍"), SignalReaction("self", "❤"))) }
        running(peer) { _, events ->
            assertEquals(setOf(Triple("9", "👍", false), Triple("self", "❤", false)), reactionEvents(events).toSet())
            peer.rows[11] = SignalText(11, 7, "9", "x", false, 1, 1, "Sent", reactions = listOf(SignalReaction("9", "😂")))
            withTimeout(5000) { while (reactionEvents(events).size < 5) delay(10) }
            assertEquals(listOf(Triple("9", "👍", true), Triple("self", "❤", true), Triple("9", "😂", false)).toSet(), reactionEvents(events).drop(2).toSet())
            delay(100)
            assertEquals(5, reactionEvents(events).size) // Unchanged polls emit nothing new.
        }
    }
    @Test fun reactionRemovedWhileOfflineIsRemovedAfterRestart() = runBlocking<Unit> {
        val peer = Peer(binding).apply { rows[11] = text() }
        val session = SignalMessagingSession(binding, peer, clock = clock, pollMillis = 10,
            previousReactions = mapOf(MessageRef(ConversationRef(SignalMessagingSession.accountFor(binding).id, "7"), "11") to mapOf("9" to "👍")))
        try {
            val removal = withTimeout(5000) { session.events().mapNotNull { it.payload as? ProviderEvent.ReactionChanged }.first() }
            assertTrue(removal.removed && removal.reaction == "👍")
        } finally { session.close() }
    }
    @Test fun reactionContractRejectsDuplicateActors() {
        assertThrows(IllegalArgumentException::class.java) { SignalText(5, 7, "9", "x", false, 1, 1, "Sent", reactions = listOf(SignalReaction("9", "a"), SignalReaction("9", "b"))) }
    }
    @Test fun ownReactionIsQueuedNativelyAndConfirmedFromTheNativeState() = runBlocking {
        val peer = Peer(binding).apply { rows[11] = text() }
        running(peer) { session, _ ->
            val ref = MessageRef(scope(session).ref, "11")
            val messageScope = CapabilityScope.Message(ProviderId("signal"), ref)
            assertTrue(session.evaluate(OperationRequest(messageScope, Operations.React)) is Availability.Available)
            val command = ProviderCommand(CommandId("react-fixture"), scope(session).ref, CommandPayload.React(ref, "x"), now, now.plusSeconds(60),
                session.observeCapabilities(messageScope).first().revision)
            val accepted = session.execute(command)
            assertTrue(accepted is DispatchResult.Accepted)
            assertEquals(1, peer.reactCalls)
            val result = session.reconcile(ReconcileRequest(command.id, command.route, (accepted as DispatchResult.Accepted).receipt))
            assertTrue(result is ReconcileResult.Confirmed)
        }
    }
    @Test fun reactionsInReadOnlyGroupsAreNotOffered() = runBlocking {
        val peer = Peer(binding).apply { threadRows = listOf(SignalThread(7, "Group", false, false, "9")); rows[11] = text() }
        running(peer) { session, _ ->
            val messageScope = CapabilityScope.Message(ProviderId("signal"), MessageRef(scope(session).ref, "11"))
            assertTrue(session.evaluate(OperationRequest(messageScope, Operations.React)) !is Availability.Available)
        }
    }
    @Test fun imagesBecomeMediaWithCaptionAndAreFetchedOnlyOnDemandForTheirOwnMessage() = runBlocking {
        val peer = Peer(binding).apply {
            rows[11] = SignalText(11, 7, "9", "caption", false, 1, 1, "Sent", images = listOf(SignalImage(5, "image/jpeg", 3)))
        }
        running(peer) { session, events ->
            val message = events.mapNotNull { (it.payload as? ProviderEvent.MessageUpserted)?.message }.single()
            val media = message.content as MessageContent.Media
            assertEquals("caption", media.caption)
            assertEquals(0, peer.imageCalls)
            assertArrayEquals(byteArrayOf(1, 2, 3), session.image(message.ref!!, media.attachments.single()))
            assertNull(session.image(message.ref!!, AttachmentId("foreign:image:5")))
            assertNull(session.image(MessageRef(ConversationRef(AccountId("other"), "7"), "11"), media.attachments.single()))
            assertEquals(1, peer.imageCalls)
        }
    }
    @Test fun stagedImageIsSentOnlyWithItsOwnAccountToken() = runBlocking {
        val peer = Peer(binding)
        running(peer) { session, _ ->
            val staged = requireNotNull(session.stageImage(byteArrayOf(1, 2, 3)))
            val token = staged.value.substringAfterLast(":")
            assertEquals(3, peer.staged[token])
            val op = Operations.sendMedia(AttachmentKind.Image)
            assertTrue(session.evaluate(OperationRequest(scope(session), op, textLength = 0, sizeBytes = 0, mimeType = "image/jpeg")) is Availability.Available)
            assertTrue(session.execute(command(session, CommandPayload.SendMedia(AttachmentId("foreign:staged:$token"), AttachmentKind.Image))) is DispatchResult.Rejected)
            assertEquals(0, peer.sendCalls)
            assertTrue(session.execute(command(session, CommandPayload.SendMedia(staged, AttachmentKind.Image, "caption"))) is DispatchResult.Accepted)
            assertEquals(token, peer.lastImage)
        }
    }
    @Test fun voiceVideoAndDocumentsAreTypedMediaFetchedOnDemand() = runBlocking {
        val peer = Peer(binding).apply {
            rows[11] = SignalText(11, 7, "9", "", false, 1, 1, "Sent", files = listOf(SignalFile(6, "audio/aac", 2, "voice"), SignalFile(8, "application/pdf", 2, "file")))
        }
        running(peer) { session, events ->
            val message = events.mapNotNull { (it.payload as? ProviderEvent.MessageUpserted)?.message }.single()
            val ids = (message.content as MessageContent.Media).attachments
            assertEquals(listOf(AttachmentKind.VoiceNote, AttachmentKind.File), ids.map { SignalMessagingSession.kindOf(it) })
            val loaded = session.attachment(message.ref!!, ids.first())
            assertEquals("audio/aac", loaded?.first)
            assertNull(session.image(message.ref!!, ids.first()))
        }
    }
    @Test fun documentsAreStagedUnchangedButImagesAndVideosAreRefusedOnTheFilePath() = runBlocking {
        val peer = Peer(binding)
        running(peer) { session, _ ->
            assertNull(session.stageFile(byteArrayOf(1), "image/png", "x.png"))
            assertNull(session.stageFile(byteArrayOf(1), "video/mp4", "x.mp4"))
            val staged = requireNotNull(session.stageFile(byteArrayOf(1, 2), "application/pdf", "x.pdf"))
            assertEquals("application/pdf", peer.stagedFiles.values.single())
            assertTrue(session.execute(command(session, CommandPayload.SendMedia(staged, AttachmentKind.File))) is DispatchResult.Accepted)
            assertTrue(session.execute(command(session, CommandPayload.SendMedia(staged, AttachmentKind.Image))) is DispatchResult.Rejected)
        }
    }
    @Test fun editAndDeleteFollowTheCompanionsPerMessageRules() = runBlocking {
        val peer = Peer(binding).apply {
            rows[11] = SignalText(11, 7, "self", "mine", true, 1, 1, "Sent", canEdit = true, canDelete = true)
            rows[12] = SignalText(12, 7, "9", "theirs", false, 1, 1, "Sent")
        }
        running(peer) { session, _ ->
            val mine = MessageRef(scope(session).ref, "11")
            val theirs = MessageRef(scope(session).ref, "12")
            fun at(ref: MessageRef) = CapabilityScope.Message(ProviderId("signal"), ref)
            assertTrue(session.evaluate(OperationRequest(at(mine), Operations.EditText, textLength = 3, sizeBytes = 3)) is Availability.Available)
            assertTrue(session.evaluate(OperationRequest(at(theirs), Operations.EditText, textLength = 3, sizeBytes = 3)) !is Availability.Available)
            assertTrue(session.evaluate(OperationRequest(at(theirs), Operations.DeleteAll)) !is Availability.Available)
            assertTrue(session.execute(command(session, CommandPayload.EditText(theirs, "new", null))) is DispatchResult.Rejected)
            assertTrue(session.execute(command(session, CommandPayload.EditText(mine, "new", null))) is DispatchResult.Accepted)
            assertEquals(11L, peer.lastEdit)
            assertTrue(session.execute(command(session, CommandPayload.Delete(theirs, forEveryone = false))) is DispatchResult.Accepted)
            val deleting = command(session, CommandPayload.Delete(mine, forEveryone = true))
            val accepted = session.execute(deleting) as DispatchResult.Accepted
            assertTrue(session.reconcile(ReconcileRequest(deleting.id, deleting.route, accepted.receipt)) is ReconcileResult.Confirmed)
        }
    }
    @Test fun readTypingUnreactAndDeleteForMeReachTheCompanionOnlyWhenOffered() = runBlocking {
        val peer = Peer(binding).apply { rows[11] = text() }
        running(peer) { session, _ ->
            val chat = scope(session).ref
            val ref = MessageRef(chat, "11")
            assertTrue(session.markRead(chat, now))
            assertTrue(session.typing(chat, true))
            assertTrue(session.removeReaction(ref))
            assertFalse(session.markRead(ConversationRef(AccountId("other"), "7"), now))
            assertEquals(listOf("messageRead", "messageTyping", "messageUnreact"), peer.actions.map { it.first })
            val deleting = command(session, CommandPayload.Delete(ref, forEveryone = false))
            val accepted = session.execute(deleting) as DispatchResult.Accepted
            assertTrue(session.reconcile(ReconcileRequest(deleting.id, deleting.route, accepted.receipt)) is ReconcileResult.Confirmed)
        }
    }
    @Test fun groupManagementFollowsTheCompanionsRights() = runBlocking {
        val peer = Peer(binding).apply { threadRows = listOf(SignalThread(7, "Group", false, true, "9", canRename = false, canLeave = true)) }
        running(peer) { session, _ ->
            assertFalse(session.renameGroup(scope(session).ref, "Neu"))
            assertTrue(session.leaveGroup(scope(session).ref))
            assertEquals(listOf("groupLeave"), peer.actions.map { it.first })
        }
    }
    @Test fun voiceAndVideoUseTheirOwnSlidesAndValidateTypes() = runBlocking {
        val peer = Peer(binding)
        running(peer) { session, _ ->
            assertNull(session.stageVoice(byteArrayOf(1), "application/pdf"))
            val voice = requireNotNull(session.stageVoice(byteArrayOf(1), "audio/aac"))
            val video = requireNotNull(session.stageVideo(byteArrayOf(1)))
            assertEquals(setOf("voice", "video"), peer.slides.values.toSet())
            assertEquals(AttachmentKind.VoiceNote, SignalMessagingSession.kindOf(voice))
            assertTrue(session.execute(command(session, CommandPayload.SendMedia(voice, AttachmentKind.VoiceNote))) is DispatchResult.Accepted)
            assertTrue(session.execute(command(session, CommandPayload.SendMedia(video, AttachmentKind.Video))) is DispatchResult.Accepted)
            assertTrue(session.execute(command(session, CommandPayload.SendMedia(video, AttachmentKind.VoiceNote))) is DispatchResult.Rejected)
        }
    }
    @Test fun imageContractRejectsUnsupportedTypesAndOversize() {
        assertThrows(IllegalArgumentException::class.java) { SignalImage(1, "video/mp4", 10) }
        assertThrows(IllegalArgumentException::class.java) { SignalImage(1, "image/png", SignalMessagingContract.MAX_IMAGE_BYTES + 1) }
        assertThrows(IllegalArgumentException::class.java) { SignalText(1, 7, "9", "", false, 1, 1, "Sent", supported = false, images = listOf(SignalImage(1, "image/png", 1))) }
    }
    @Test fun quoteMayNotReferenceItself() {
        assertThrows(IllegalArgumentException::class.java) { SignalText(5, 7, "9", "x", false, 1, 1, "Sent", replyTo = 5) }
    }
    @Test fun factoryRequiresExactCurrentAccountAndBindingReference() = runBlocking<Unit> {
        val peer = Peer(binding)
        val factory = SignalMessagingProviderFactory(peer)
        val account = SignalMessagingSession.accountFor(binding)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { factory.open(AccountHandle(account, "wrong-reference")) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { factory.open(AccountHandle(account.copy(id = AccountId("other")), binding.id)) } }
        val session = factory.open(AccountHandle(account, binding.id))
        assertEquals(account.id, session.accountId)
        session.close()
        peer.current = null
        assertThrows(IllegalArgumentException::class.java) { runBlocking { factory.open(AccountHandle(account, binding.id)) } }
    }
    @Test fun failedProjectionCommitEndsStreamAndUncommittedMessageReplays() = runBlocking<Unit> {
        val peer = Peer(binding).apply { rows[11] = text() }
        val session = SignalMessagingSession(binding, peer, clock = clock, pollMillis = 10)
        try {
            var failed = false
            try {
                withTimeout(5000) { session.events().collect {
                    if (it.payload is ProviderEvent.MessageUpserted) throw IllegalStateException("synthetic commit failure")
                } }
            } catch (_: IllegalStateException) { failed = true }
            assertTrue("Projection failure was swallowed", failed)
            val replay = withTimeout(5000) { session.events().first { it.payload is ProviderEvent.MessageUpserted } }
            assertTrue((replay.payload as ProviderEvent.MessageUpserted).message.ref?.remoteMessageId == "11")
        } finally { session.close() }
    }
    @Test fun utf8WireLimitIsCheckedBeforeNativeQueueForMultibyteText() = runBlocking {
        val peer = Peer(binding)
        running(peer) { session, _ ->
            val tooLarge = command(session, CommandPayload.SendText("界".repeat(683)))
            assertTrue(session.execute(tooLarge) is DispatchResult.Rejected)
            assertEquals(0, peer.sendCalls)
            assertTrue(session.execute(command(session, CommandPayload.SendText("ä".repeat(1024)))) is DispatchResult.Accepted)
            assertEquals(1, peer.sendCalls)
        }
    }
}
