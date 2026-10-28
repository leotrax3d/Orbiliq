package org.orbiliq.core.provider.signal

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

class SignalMessagingProviderFactory(private val transport: SignalMessagingTransport) : ProviderFactory {
    override val descriptor = ProviderDescriptor(ProviderId("signal"), "Signal Companion", "0.3-direct-text-quotes",
        ContractVersion(1, 0), setOf(Operations.SendText, Operations.Reply, Operations.React, Operations.sendMedia(AttachmentKind.Image), Operations.ReceiveText, OperationKey("signal.group.title.receive"), SignalMessagingSession.QuoteReceive, SignalMessagingSession.ReactionReceive, SignalMessagingSession.ImageReceive, SignalMessagingSession.FileReceive,
        Operations.sendMedia(AttachmentKind.File)), ReplayClass.BestEffort)
    override suspend fun open(account: AccountHandle): ProviderSession {
        require(account.account.providerId == descriptor.id)
        val binding = requireNotNull(transport.binding()) { "signal.messaging.consent-required" }
        require(account.account.id == SignalMessagingSession.accountFor(binding).id && account.vaultReference() == binding.id)
        return SignalMessagingSession(binding, transport)
    }
}

/** Normal direct text and read-only groups. Native Signal owns protocol state and retries. */
class SignalMessagingSession(
    private val binding: SignalBinding,
    private val transport: SignalMessagingTransport,
    previousMessages: Collection<MessageRef> = emptyList(),
    previousCommands: Map<MessageRef, CommandId> = emptyMap(),
    private val clock: Clock = Clock.systemUTC(),
    private val pollMillis: Long = 2000,
    /** Locally committed reactions (message -> actor -> emoji) so removals while offline are still diffed. */
    previousReactions: Map<MessageRef, Map<String, String>> = emptyMap(),
) : ProviderSession {
    override val accountId = accountFor(binding).id
    override val epoch = UUID.randomUUID().toString()
    private val job = SupervisorJob()
    private val stateValue = MutableStateFlow(ProviderState(AuthState.Blocked, ConnectionState.Offline, SyncState.CatchingUp))
    override val state = stateValue.asStateFlow()
    private val threadState = MutableStateFlow<Map<Long, SignalThread>>(emptyMap())
    private val sequence = AtomicLong()
    private val known = java.util.concurrent.ConcurrentHashMap<Long, MutableSet<Long>>().apply {
        previousMessages.groupBy { it.conversation.remoteConversationId.toLong() }.forEach { (thread, refs) ->
            put(thread, java.util.concurrent.ConcurrentHashMap.newKeySet<Long>().apply { addAll(refs.map { it.remoteMessageId.toLong() }) })
        }
    }
    private val revisions = mutableMapOf<String, String>()
    private val initialScanComplete = mutableSetOf<Long>()
    private val firstPageBoundary = mutableMapOf<Long, Long>()
    private val commandMessages = java.util.concurrent.ConcurrentHashMap(previousCommands)
    private val sendGate = Mutex()
    private val reactionState = previousReactions.filterKeys { it.conversation.accountId == accountId }
        .mapKeys { it.key.remoteMessageId.toLong() }.toMutableMap()
    init {
        require(pollMillis > 0)
        require(previousMessages.all { it.conversation.accountId == accountId })
        CoroutineScope(job + Dispatchers.Default).launch {
            while (isActive) {
                try {
                    check(transport.binding()?.id == binding.id)
                    stateValue.value = state.value.copy(auth = AuthState.Ready, connection = ConnectionState.Online,
                        requirements = setOf("signal.connection.local-ipc", "signal.history.native-device-only", "signal.ephemeral.native-only"))
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { stateValue.value = ProviderState(AuthState.Blocked, ConnectionState.Offline, SyncState.Degraded) }
                delay(pollMillis)
            }
        }
    }
    companion object {
        val provider = ProviderId("signal")
        /** Received quote references only; sending quoted replies is not implemented. */
        val QuoteReceive = OperationKey("signal.quote.receive")
        /** Received reactions only; sending reactions is not implemented. */
        val ReactionReceive = OperationKey("signal.reaction.receive")
        /** Images are fetched on demand from the companion and decoded in memory only. */
        val ImageReceive = OperationKey("signal.image.receive")
        fun attachmentId(account: AccountId, image: Long) = AttachmentId("${account.value}:image:$image")
        fun stagedId(account: AccountId, token: String) = AttachmentId("${account.value}:staged:$token")
        /** Audio, voice notes, video and documents: fetched on demand, played or saved explicitly by the user. */
        val FileReceive = OperationKey("signal.file.receive")
        fun fileId(account: AccountId, file: SignalFile) = AttachmentId("${account.value}:file:${file.id}:${file.kind}")
        fun kindOf(attachment: AttachmentId): AttachmentKind? = when {
            ":image:" in attachment.value || ":staged:" in attachment.value -> AttachmentKind.Image
            ":stagedfile:" in attachment.value -> AttachmentKind.File
            ":file:" in attachment.value -> when (attachment.value.substringAfterLast(":")) {
                "voice" -> AttachmentKind.VoiceNote; "audio" -> AttachmentKind.Audio; "video" -> AttachmentKind.Video; "file" -> AttachmentKind.File
                else -> null
            }
            else -> null
        }
        fun accountFor(binding: SignalBinding) = Account(AccountId("signal-${binding.id}"), provider, "Signal", IdentityId("signal-${binding.id}:self"))
        private val security = SecurityContext(Encryption.EndToEnd, Verification.Unknown)
    }
    private fun conversation(thread: Long) = ConversationRef(accountId, thread.toString())
    private fun messageRef(thread: Long, id: Long) = MessageRef(conversation(thread), id.toString())
    private fun conversationId(thread: Long) = ConversationId("${accountId.value}:$thread")
    private fun identityId(sender: String) = if (sender == "self") IdentityId("${accountId.value}:self") else IdentityId("${accountId.value}:sender:$sender")
    private fun revision(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun validScope(scope: CapabilityScope): Boolean = scope.providerId == provider && when (scope) {
        is CapabilityScope.Provider -> true
        is CapabilityScope.Account -> scope.accountId == accountId
        is CapabilityScope.Conversation -> scope.ref.accountId == accountId
        is CapabilityScope.Message -> scope.ref.conversation.accountId == accountId
    }
    private fun support(scope: CapabilityScope): CapabilitySnapshot {
        val now = clock.instant()
        val remote = when (scope) {
            is CapabilityScope.Conversation -> scope.ref.remoteConversationId.toLongOrNull()
            is CapabilityScope.Message -> scope.ref.conversation.remoteConversationId.toLongOrNull()
            else -> null
        }
        val thread = remote?.let { threadState.value[it] }
        val routeScoped = scope is CapabilityScope.Conversation || scope is CapabilityScope.Message
        val ready = job.isActive && state.value.auth == AuthState.Ready && validScope(scope) && (!routeScoped || thread != null)
        // The companion decides writability (membership, announcement-only, timers) and reports it per thread.
        val allowed = !routeScoped || thread?.canSend == true
        val operations = if (!ready) emptyMap() else buildMap {
            putAll(mapOf(
            Operations.ReceiveText to OperationSupport(Availability.Available(), quality = DataQuality.PartialHistory, replay = ReplayClass.BestEffort, security = security),
            ImageReceive to OperationSupport(Availability.Available(OperationConstraints(maxBytes = SignalMessagingContract.MAX_IMAGE_BYTES,
                mimeTypes = SignalMessagingContract.IMAGE_TYPES)), quality = DataQuality.PartialHistory, replay = ReplayClass.BestEffort, security = security),
            FileReceive to OperationSupport(Availability.Available(OperationConstraints(maxBytes = SignalMessagingContract.MAX_FILE_BYTES)),
                quality = DataQuality.PartialHistory, replay = ReplayClass.BestEffort, security = security),
            Operations.sendMedia(AttachmentKind.File) to OperationSupport(if (allowed) Availability.Available(OperationConstraints(
                maxBytes = SignalMessagingContract.MAX_FILE_BYTES, maxTextLength = SignalMessagingContract.MAX_TEXT, persistentOfflineQueue = true))
                else Availability.Restricted(Restriction.Policy), quality = DataQuality.SinceCheckpoint, replay = ReplayClass.BestEffort, security = security),
            ReactionReceive to OperationSupport(Availability.Available(), quality = DataQuality.PartialHistory, replay = ReplayClass.BestEffort, security = security),
            QuoteReceive to OperationSupport(Availability.Available(), quality = DataQuality.PartialHistory, replay = ReplayClass.BestEffort, security = security),
            Operations.SendText to sendSupport(allowed),
            Operations.Reply to sendSupport(allowed),
            Operations.sendMedia(AttachmentKind.Image) to OperationSupport(if (allowed) Availability.Available(OperationConstraints(
                maxBytes = SignalMessagingContract.MAX_IMAGE_BYTES, mimeTypes = setOf("image/jpeg"), maxTextLength = SignalMessagingContract.MAX_TEXT,
                persistentOfflineQueue = true)) else Availability.Restricted(Restriction.Policy),
                quality = DataQuality.SinceCheckpoint, replay = ReplayClass.BestEffort, security = security),
            Operations.React to OperationSupport(if (allowed && routeScoped) Availability.Available() else Availability.Restricted(Restriction.Policy),
                quality = DataQuality.SinceCheckpoint, replay = ReplayClass.BestEffort, security = security),
            ))
            if (remote == null || thread?.direct == false) put(OperationKey("signal.group.title.receive"),
                OperationSupport(Availability.Available(), quality = DataQuality.PartialHistory, replay = ReplayClass.BestEffort, security = security))
        }
        return CapabilitySnapshot(scope, "signal-text-v1:${thread?.canSend}:$ready", now, now.plusSeconds(15), operations)
    }
    private fun sendSupport(allowed: Boolean) = OperationSupport(if (allowed) Availability.Available(OperationConstraints(maxTextLength = SignalMessagingContract.MAX_TEXT,
        maxBytes = SignalMessagingContract.MAX_TEXT_BYTES.toLong(), persistentOfflineQueue = true)) else Availability.Restricted(Restriction.Policy),
        quality = DataQuality.SinceCheckpoint, replay = ReplayClass.BestEffort, security = security)
    override fun observeCapabilities(scope: CapabilityScope): Flow<CapabilitySnapshot> = combine(state, threadState) { _, _ -> support(scope) }
    override suspend fun evaluate(request: OperationRequest): Availability {
        if (request.operation in setOf(Operations.SendText, Operations.Reply) && request.targetKind in setOf(ConversationKind.Channel, ConversationKind.Topic))
            return Availability.Restricted(Restriction.Policy)
        val availability = support(request.scope).operations[request.operation]?.availability ?: return Availability.Unsupported("signal.operation.unavailable")
        return if (availability is Availability.Available && !availability.constraints.permits(request, clock.instant())) Availability.Restricted(Restriction.Policy) else availability
    }
    override fun events(after: ReplayCursor?): Flow<EventEnvelope> = flow {
        require(after == null || (after.accountId == accountId && after.epoch == epoch && after.streamId == "signal-snapshot"))
        suspend fun event(payload: ProviderEvent, key: String, version: String, force: Boolean = false) {
            if (!force && revisions[key] == version) return
            val number = sequence.incrementAndGet()
            emit(EventEnvelope("$epoch:$number", ReplayCursor(accountId, "signal-snapshot", epoch, number.toString()), null, clock.instant(), payload))
            revisions[key] = version
        }
        event(ProviderEvent.GapDetected("signal.history.native-device-only"), "gap", "1")
        event(ProviderEvent.IdentityChanged(MessengerIdentity(identityId("self"), IdentityRef(accountId, "self"), "Ich")), "self", "1")
        while (currentCoroutineContext().isActive && job.isActive) {
            try {
                check(transport.binding()?.id == binding.id)
                val threads = transport.threads(binding)
                require(threads.size <= SignalMessagingContract.MAX_THREADS && threads.map { it.id }.distinct().size == threads.size)
                threadState.value = threads.associateBy { it.id }
                stateValue.value = ProviderState(AuthState.Ready, ConnectionState.Online, SyncState.CatchingUp,
                    requirements = setOf("signal.connection.local-ipc", "signal.history.native-device-only", "signal.ephemeral.native-only"))
                for (thread in threads) {
                    if (thread.direct) event(ProviderEvent.IdentityChanged(MessengerIdentity(identityId(thread.peer), IdentityRef(accountId, thread.peer),
                        if (thread.peer == "self") "Ich" else thread.title)), "peer:${thread.peer}", revision(thread.title))
                    val groupId = if (thread.direct) null else GroupId("${accountId.value}:group:${thread.id}")
                    if (groupId != null) event(ProviderEvent.GroupChanged(Group(groupId, GroupRef(accountId, thread.id.toString()), thread.title)),
                        "group:${thread.id}", revision(thread.title))
                    event(ProviderEvent.ConversationChanged(Conversation(conversationId(thread.id), conversation(thread.id), thread.title,
                        if (thread.direct) ConversationKind.Direct else ConversationKind.Group,
                        participants = if (thread.direct) setOf(identityId("self"), identityId(thread.peer)) else emptySet(), groupId = groupId,
                        security = security)), "thread:${thread.id}", revision(thread.title + thread.canSend + thread.direct))
                    val ids = known.getOrPut(thread.id) { java.util.concurrent.ConcurrentHashMap.newKeySet<Long>() }
                    val current = mutableListOf<SignalText>()
                    var beforeId = Long.MAX_VALUE
                    var pages = 0
                    var newBoundary: Long? = null
                    do {
                        val page = sendGate.withLock { transport.texts(binding, thread.id, beforeId) }
                        require(page.rows.all { it.thread == thread.id && it.id < beforeId })
                        current += page.rows
                        if (pages++ == 0) newBoundary = page.nextBeforeId
                        val next = page.nextBeforeId ?: break
                        require(next < beforeId)
                        if (thread.id in initialScanComplete && (page.rows.any { it.id in ids } ||
                            firstPageBoundary[thread.id]?.let { next <= it } == true)) break
                        check(pages < 2000) // Explicit source bound; a failed scan is never marked complete.
                        beforeId = next
                    } while (job.isActive)
                    initialScanComplete.add(thread.id)
                    newBoundary?.let { firstPageBoundary[thread.id] = it }
                    val checked = mutableMapOf<Long, SignalText>()
                    current.forEach { checked[it.id] = it }
                    for (chunk in ids.toList().chunked(SignalMessagingContract.PAGE_SIZE)) {
                        val found = sendGate.withLock { transport.lookup(binding, thread.id, chunk) }
                        require(found.all { it.thread == thread.id && it.id in chunk })
                        for (id in chunk) if (found.none { it.id == id }) {
                            val ref = messageRef(thread.id, id)
                            event(ProviderEvent.MessageDeleted(ref, null, commandMessages[ref]), "deleted:$id", "1")
                            ids.remove(id); revisions.remove("message:$id"); reactionState.remove(id)
                        }
                        found.forEach { checked[it.id] = it }
                    }
                    for (text in checked.values) {
                        val sender = IdentityRef(accountId, if (text.outgoing) "self" else text.sender)
                        val senderName = if (text.outgoing) "Ich" else text.senderName.ifEmpty { if (thread.direct) thread.title else "Signal-Teilnehmer" }
                        event(ProviderEvent.IdentityChanged(MessengerIdentity(identityId(sender.remoteIdentityId), sender,
                            senderName)), "sender:${text.sender}", revision(senderName))
                        val message = Message(MessageId("${accountId.value}:message:${text.id}"), conversationId(thread.id), conversation(thread.id), sender,
                            if (text.supported && (text.images.isNotEmpty() || text.files.isNotEmpty()))
                                MessageContent.Media(text.images.map { attachmentId(accountId, it.id) } + text.files.map { fileId(accountId, it) }, text.text.ifEmpty { null })
                            else if (text.supported) MessageContent.Text(text.text) else MessageContent.Unsupported(MessageType("signal.native-content"), 1, "Diesen Inhalt im Signal Companion öffnen"),
                            if (text.outgoing) MessageDirection.Outgoing else MessageDirection.Incoming,
                            Instant.ofEpochMilli(text.received), Instant.ofEpochMilli(text.timestamp), messageRef(thread.id, text.id),
                            replyTo = text.replyTo?.let { MessageId("${accountId.value}:message:$it") },
                            providerOrder = text.id.toString().padStart(20, '0'), revision = revision(text.text + text.delivery + text.supported + ":" + text.replyTo + ":" + text.images.joinToString { "${it.id}" } + text.files.joinToString { "f${it.id}" }), delivery = DeliveryState.valueOf(text.delivery),
                            quality = DataQuality.PartialHistory, security = security, policy = ContentPolicy(cacheAllowed = true))
                        val command = commandMessages[message.ref]
                        revisions.remove("deleted:${text.id}")
                        event(ProviderEvent.MessageUpserted(message, command), "message:${text.id}", "${message.revision}:${command?.value}")
                        ids.add(text.id)
                        val previous = reactionState[text.id].orEmpty()
                        val current = text.reactions.associate { it.actor to it.emoji }
                        for ((actor, emoji) in previous) if (current[actor] != emoji)
                            event(ProviderEvent.ReactionChanged(message.ref!!, IdentityRef(accountId, actor), emoji, true), "reaction:${text.id}:$actor", "-$emoji", force = true)
                        for ((actor, emoji) in current) if (previous[actor] != emoji)
                            event(ProviderEvent.ReactionChanged(message.ref!!, IdentityRef(accountId, actor), emoji, false), "reaction:${text.id}:$actor", emoji, force = true)
                        reactionState[text.id] = current
                    }
                }
                // Removed threads must not leave copied messages behind.
                for (thread in known.keys.filter { id -> threads.none { it.id == id } }) {
                    for (id in known.getValue(thread)) {
                        val ref = messageRef(thread, id)
                        event(ProviderEvent.MessageDeleted(ref, null, commandMessages[ref]), "deleted:$id", "1")
                    }
                    known.remove(thread)
                }
                stateValue.value = state.value.copy(sync = SyncState.Gap)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                stateValue.value = ProviderState(AuthState.Blocked, ConnectionState.Offline, SyncState.Degraded,
                    requirements = setOf("signal.messaging.unavailable"))
                // Never continue emitting after a downstream projection/commit failure.
                // The coordinator owns retry and resumes from its committed checkpoint.
                throw IllegalStateException("signal.messaging.unavailable")
            }
            delay(pollMillis)
        }
    }.onCompletion { cause ->
        if (cause != null && cause !is CancellationException) stateValue.value = ProviderState(AuthState.Blocked, ConnectionState.Offline, SyncState.Degraded,
            requirements = setOf("signal.messaging.unavailable"))
    }
    override suspend fun acknowledge(cursor: ReplayCursor): OperationResult<Unit> =
        if (job.isActive && cursor.accountId == accountId && cursor.streamId == "signal-snapshot" && cursor.epoch == epoch &&
            cursor.token.toLongOrNull()?.let { it in 1..sequence.get() } == true) OperationResult.Success(Unit)
        else OperationResult.Rejected(ProviderFailure(FailureCategory.SyncConflict))
    private suspend fun react(command: ProviderCommand, payload: CommandPayload.React): DispatchResult {
        val thread = command.route.remoteConversationId.toLongOrNull()
        val message = payload.message.remoteMessageId.toLongOrNull()
        val scope = CapabilityScope.Message(provider, payload.message)
        if (!job.isActive || thread == null || message == null || message <= 0 || command.route.accountId != accountId || command.schemaVersion != 1 ||
            payload.reaction.length > 32 || clock.instant() < command.createdAt || clock.instant() >= command.expiresAt || command.dependencies.isNotEmpty() ||
            command.capabilityRevision != support(scope).revision || evaluate(OperationRequest(scope, Operations.React)) !is Availability.Available)
            return DispatchResult.Rejected(ProviderFailure(FailureCategory.CapabilityUnavailable))
        return try {
            check(transport.binding()?.id == binding.id)
            when (transport.react(binding, thread, message, payload.reaction)) {
                is SignalSendResult.Queued -> DispatchResult.Accepted("reaction:$message:${payload.reaction}")
                SignalSendResult.Rejected -> DispatchResult.Rejected(ProviderFailure(FailureCategory.PolicyExpired))
                SignalSendResult.Unknown -> DispatchResult.OutcomeUnknown("signal.reaction.unknown")
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { DispatchResult.OutcomeUnknown("signal.reaction.unknown") }
    }
    /** Stages a JPEG in the companion's encrypted store; the returned id is only valid for this account. */
    suspend fun stageImage(jpeg: ByteArray): AttachmentId? {
        if (!job.isActive || jpeg.isEmpty() || jpeg.size > SignalMessagingContract.MAX_IMAGE_BYTES) return null
        val token = UUID.randomUUID().toString().replace("-", "")
        return try {
            check(transport.binding()?.id == binding.id)
            if (sendGate.withLock { transport.stageImage(binding, token, jpeg) }) stagedId(accountId, token) else null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }
    /** Stages a document unchanged; images and videos must use their dedicated paths. */
    suspend fun stageFile(bytes: ByteArray, mimeType: String, name: String?): AttachmentId? {
        if (!job.isActive || bytes.isEmpty() || bytes.size > SignalMessagingContract.MAX_FILE_BYTES ||
            mimeType.startsWith("image/") || mimeType.startsWith("video/")) return null
        val token = UUID.randomUUID().toString().replace("-", "")
        return try {
            check(transport.binding()?.id == binding.id)
            if (sendGate.withLock { transport.stageFile(binding, token, bytes, mimeType, name) }) AttachmentId("${accountId.value}:stagedfile:$token") else null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }
    override suspend fun execute(command: ProviderCommand): DispatchResult = sendGate.withLock {
        (command.payload as? CommandPayload.React)?.let { return react(command, it) }
        (command.payload as? CommandPayload.SendMedia)?.let { media ->
            val prefix = "${accountId.value}:" + if (media.kind == AttachmentKind.File) "stagedfile:" else "staged:"
            val token = media.attachment.value.takeIf { media.kind in setOf(AttachmentKind.Image, AttachmentKind.File) && it.startsWith(prefix) }?.removePrefix(prefix)
                ?.takeIf { it.matches(Regex("[0-9a-f]{32}")) } ?: return DispatchResult.Rejected(ProviderFailure(FailureCategory.CapabilityUnavailable))
            return dispatch(command, media.caption.orEmpty(), null, token, media.operation)
        }
        val text = command.payload as? CommandPayload.SendText ?: return DispatchResult.Rejected(ProviderFailure(FailureCategory.CapabilityUnavailable))
        val replyTo = text.replyTo?.let { ref -> ref.remoteMessageId.toLongOrNull()?.takeIf { it > 0 && ref.conversation == command.route } ?: -1L }
        if (replyTo == -1L) return DispatchResult.Rejected(ProviderFailure(FailureCategory.CapabilityUnavailable))
        return dispatch(command, text.text, replyTo, null, text.operation)
    }
    private suspend fun dispatch(command: ProviderCommand, body: String, replyTo: Long?, image: String?, operation: OperationKey): DispatchResult {
        val thread = command.route.remoteConversationId.toLongOrNull()
        val scope = CapabilityScope.Conversation(provider, command.route)
        if (!job.isActive || thread == null || command.route.accountId != accountId || command.schemaVersion != 1 ||
            clock.instant() < command.createdAt || clock.instant() >= command.expiresAt || command.dependencies.isNotEmpty() ||
            command.capabilityRevision != support(scope).revision || (body.isEmpty() && image == null) ||
            evaluate(OperationRequest(scope, operation, textLength = body.length,
                sizeBytes = body.toByteArray(Charsets.UTF_8).size.toLong(),
                mimeType = if (image == null || operation != Operations.sendMedia(AttachmentKind.Image)) null else "image/jpeg",
                targetKind = if (threadState.value[thread]?.direct == false) ConversationKind.Group else ConversationKind.Direct)) !is Availability.Available ||
            body.toByteArray(Charsets.UTF_8).size > SignalMessagingContract.MAX_TEXT_BYTES)
            return DispatchResult.Rejected(ProviderFailure(FailureCategory.CapabilityUnavailable))
        return try {
            check(transport.binding()?.id == binding.id)
            when (val result = transport.sendText(binding, command.id.value, thread, body, replyTo, image)) {
                is SignalSendResult.Queued -> {
                    commandMessages[messageRef(thread, result.messageId)] = command.id
                    known.getOrPut(thread) { java.util.concurrent.ConcurrentHashMap.newKeySet<Long>() }.add(result.messageId)
                    DispatchResult.Accepted(result.messageId.toString(), messageRef(thread, result.messageId))
                }
                SignalSendResult.Rejected -> DispatchResult.Rejected(ProviderFailure(FailureCategory.PolicyExpired))
                SignalSendResult.Unknown -> DispatchResult.OutcomeUnknown("signal.send.unknown")
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { DispatchResult.OutcomeUnknown("signal.send.unknown") }
    }
    override suspend fun reconcile(request: ReconcileRequest): ReconcileResult {
        if (!job.isActive || request.route.accountId != accountId) return ReconcileResult.Failed(ProviderFailure(FailureCategory.AuthRequired))
        return try {
            check(transport.binding()?.id == binding.id)
            val thread = request.route.remoteConversationId.toLong()
            request.receipt?.takeIf { it.startsWith("reaction:") }?.let { receipt ->
                // Reactions are state, not a message: confirmed once the native own reaction matches.
                val (message, emoji) = receipt.removePrefix("reaction:").split(":", limit = 2).let { it[0].toLong() to it[1] }
                val row = transport.lookup(binding, thread, listOf(message)).singleOrNull() ?: return ReconcileResult.StillUnknown
                return if (row.reactions.any { it.actor == "self" && it.emoji == emoji }) ReconcileResult.Confirmed(null) else ReconcileResult.StillUnknown
            }
            val result = transport.reconcileText(binding, request.commandId.value, thread)
            if (result !is SignalSendResult.Queued) return ReconcileResult.StillUnknown
            commandMessages[messageRef(thread, result.messageId)] = request.commandId
            val text = transport.lookup(binding, thread, listOf(result.messageId)).singleOrNull() ?: return ReconcileResult.StillUnknown
            when (text.delivery) {
                "Sent", "Delivered", "Read" -> ReconcileResult.Confirmed(messageRef(thread, text.id))
                "Failed" -> ReconcileResult.Failed(ProviderFailure(FailureCategory.ProtocolFailure))
                else -> ReconcileResult.StillUnknown
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { ReconcileResult.StillUnknown }
    }
    /** On-demand image bytes for an exported message of this account, or null. */
    suspend fun image(message: MessageRef, attachment: AttachmentId): ByteArray? =
        attachment(message, attachment)?.takeIf { attachment.value.startsWith("${accountId.value}:image:") }?.second
    /** MIME type and bytes of any exported attachment of this account's message, or null. */
    suspend fun attachment(message: MessageRef, attachment: AttachmentId): Pair<String, ByteArray>? {
        if (!job.isActive || message.conversation.accountId != accountId) return null
        val value = attachment.value
        val image = when {
            value.startsWith("${accountId.value}:image:") -> value.removePrefix("${accountId.value}:image:").toLongOrNull()
            value.startsWith("${accountId.value}:file:") -> value.removePrefix("${accountId.value}:file:").substringBefore(":").toLongOrNull()
            else -> null
        } ?: return null
        val thread = message.conversation.remoteConversationId.toLongOrNull() ?: return null
        val id = message.remoteMessageId.toLongOrNull() ?: return null
        return try {
            check(transport.binding()?.id == binding.id)
            sendGate.withLock { transport.attachment(binding, thread, id, image) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }
    override suspend fun authenticate(response: AuthResponse): AuthResult = AuthResult.Rejected(ProviderFailure(FailureCategory.AuthRequired))
    override suspend fun close() { job.cancel(); stateValue.value = ProviderState(AuthState.Blocked, ConnectionState.Offline, SyncState.Degraded) }
}
