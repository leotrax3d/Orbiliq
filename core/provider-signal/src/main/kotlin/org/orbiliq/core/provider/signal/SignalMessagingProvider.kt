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
    override val descriptor = ProviderDescriptor(ProviderId("signal"), "Signal Companion", "0.2-direct-text",
        ContractVersion(1, 0), setOf(Operations.SendText, Operations.ReceiveText), ReplayClass.BestEffort)
    override suspend fun open(account: AccountHandle): ProviderSession {
        require(account.account.providerId == descriptor.id)
        val binding = requireNotNull(transport.binding()) { "signal.messaging.consent-required" }
        require(account.account.id == SignalMessagingSession.accountFor(binding).id && account.vaultReference() == binding.id)
        return SignalMessagingSession(binding, transport)
    }
}

/** Direct normal text only. Native Signal owns encryption, network retries and identity checks. */
class SignalMessagingSession(
    private val binding: SignalBinding,
    private val transport: SignalMessagingTransport,
    previousMessages: Collection<MessageRef> = emptyList(),
    previousCommands: Map<MessageRef, CommandId> = emptyMap(),
    private val clock: Clock = Clock.systemUTC(),
    private val pollMillis: Long = 2000,
) : ProviderSession {
    override val accountId = accountFor(binding).id
    override val epoch = UUID.randomUUID().toString()
    private val job = SupervisorJob()
    private val stateValue = MutableStateFlow(ProviderState(AuthState.Blocked, ConnectionState.Offline, SyncState.CatchingUp))
    override val state = stateValue.asStateFlow()
    private val threadState = MutableStateFlow<Map<Long, SignalThread>>(emptyMap())
    private val sequence = AtomicLong()
    private val known = previousMessages.groupBy { it.conversation.remoteConversationId.toLong() }.mapValues { (_, refs) ->
        refs.map { it.remoteMessageId.toLong() }.toMutableSet()
    }.toMutableMap()
    private val revisions = mutableMapOf<String, String>()
    private val initialScanComplete = mutableSetOf<Long>()
    private val firstPageBoundary = mutableMapOf<Long, Long>()
    private val commandMessages = java.util.concurrent.ConcurrentHashMap(previousCommands)
    private val sendGate = Mutex()
    init {
        require(pollMillis > 0)
        require(previousMessages.all { it.conversation.accountId == accountId })
        CoroutineScope(job + Dispatchers.Default).launch {
            while (isActive) {
                try {
                    check(transport.binding()?.id == binding.id)
                    stateValue.value = state.value.copy(auth = AuthState.Ready, connection = ConnectionState.Online,
                        requirements = setOf("signal.connection.local-ipc", "signal.history.latest-page-only", "signal.ephemeral.native-only"))
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { stateValue.value = ProviderState(AuthState.Blocked, ConnectionState.Offline, SyncState.Degraded) }
                delay(pollMillis)
            }
        }
    }
    companion object {
        val provider = ProviderId("signal")
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
        val ready = job.isActive && state.value.auth == AuthState.Ready && validScope(scope)
        val allowed = remote == null || (thread?.direct == true && thread.canSend)
        val operations = if (!ready) emptyMap() else mapOf(
            Operations.ReceiveText to OperationSupport(Availability.Available(), quality = DataQuality.PartialHistory, replay = ReplayClass.BestEffort, security = security),
            Operations.SendText to OperationSupport(if (allowed) Availability.Available(OperationConstraints(maxTextLength = SignalMessagingContract.MAX_TEXT,
                persistentOfflineQueue = true)) else Availability.Restricted(Restriction.Policy),
                quality = DataQuality.SinceCheckpoint, replay = ReplayClass.BestEffort, security = security),
        )
        return CapabilitySnapshot(scope, "signal-text-v1:${thread?.canSend}:$ready", now, now.plusSeconds(15), operations)
    }
    override fun observeCapabilities(scope: CapabilityScope): Flow<CapabilitySnapshot> = combine(state, threadState) { _, _ -> support(scope) }
    override suspend fun evaluate(request: OperationRequest): Availability {
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
                    requirements = setOf("signal.connection.local-ipc", "signal.history.latest-page-only", "signal.ephemeral.native-only"))
                for (thread in threads) {
                    if (!thread.direct) continue
                    event(ProviderEvent.IdentityChanged(MessengerIdentity(identityId(thread.peer), IdentityRef(accountId, thread.peer),
                        if (thread.peer == "self") "Ich" else thread.title)), "peer:${thread.peer}", revision(thread.title))
                    event(ProviderEvent.ConversationChanged(Conversation(conversationId(thread.id), conversation(thread.id), thread.title,
                        ConversationKind.Direct, participants = setOf(identityId("self"), identityId(thread.peer)), security = security)), "thread:${thread.id}", revision(thread.title + thread.canSend))
                    val ids = known.getOrPut(thread.id) { mutableSetOf() }
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
                            event(ProviderEvent.MessageDeleted(messageRef(thread.id, id), null), "deleted:$id", "1")
                            ids.remove(id); revisions.remove("message:$id")
                        }
                        found.forEach { checked[it.id] = it }
                    }
                    for (text in checked.values) {
                        val sender = IdentityRef(accountId, if (text.outgoing) "self" else text.sender)
                        event(ProviderEvent.IdentityChanged(MessengerIdentity(identityId(sender.remoteIdentityId), sender,
                            if (text.outgoing) "Ich" else thread.title)), "sender:${text.sender}", revision(thread.title + text.outgoing))
                        val message = Message(MessageId("${accountId.value}:message:${text.id}"), conversationId(thread.id), conversation(thread.id), sender,
                            MessageContent.Text(text.text), if (text.outgoing) MessageDirection.Outgoing else MessageDirection.Incoming,
                            Instant.ofEpochMilli(text.received), Instant.ofEpochMilli(text.timestamp), messageRef(thread.id, text.id),
                            revision = revision(text.text + text.delivery), delivery = DeliveryState.valueOf(text.delivery),
                            quality = DataQuality.PartialHistory, security = security)
                        val command = commandMessages[message.ref]
                        event(ProviderEvent.MessageUpserted(message, command), "message:${text.id}", "${message.revision}:${command?.value}")
                        ids.add(text.id)
                    }
                }
                // Removed threads must not leave copied messages behind.
                for (thread in known.keys.filter { id -> threads.none { it.id == id } }) {
                    for (id in known.getValue(thread)) event(ProviderEvent.MessageDeleted(messageRef(thread, id), null), "deleted:$id", "1")
                    known.remove(thread)
                }
                stateValue.value = state.value.copy(sync = SyncState.Gap)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                stateValue.value = ProviderState(AuthState.Blocked, ConnectionState.Offline, SyncState.Degraded,
                    requirements = setOf("signal.messaging.unavailable"))
            }
            delay(pollMillis)
        }
    }
    override suspend fun acknowledge(cursor: ReplayCursor): OperationResult<Unit> =
        if (job.isActive && cursor.accountId == accountId && cursor.streamId == "signal-snapshot" && cursor.epoch == epoch &&
            cursor.token.toLongOrNull()?.let { it in 1..sequence.get() } == true) OperationResult.Success(Unit)
        else OperationResult.Rejected(ProviderFailure(FailureCategory.SyncConflict))
    override suspend fun execute(command: ProviderCommand): DispatchResult = sendGate.withLock {
        val text = command.payload as? CommandPayload.SendText ?: return DispatchResult.Rejected(ProviderFailure(FailureCategory.CapabilityUnavailable))
        val thread = command.route.remoteConversationId.toLongOrNull()
        val scope = CapabilityScope.Conversation(provider, command.route)
        if (!job.isActive || thread == null || command.route.accountId != accountId || text.replyTo != null || command.schemaVersion != 1 ||
            clock.instant() < command.createdAt || clock.instant() >= command.expiresAt || command.dependencies.isNotEmpty() ||
            command.capabilityRevision != support(scope).revision || evaluate(OperationRequest(scope, Operations.SendText, textLength = text.text.length)) !is Availability.Available)
            return DispatchResult.Rejected(ProviderFailure(FailureCategory.CapabilityUnavailable))
        return try {
            check(transport.binding()?.id == binding.id)
            when (val result = transport.sendText(binding, command.id.value, thread, text.text)) {
                is SignalSendResult.Queued -> {
                    commandMessages[messageRef(thread, result.messageId)] = command.id
                    DispatchResult.Accepted(result.messageId.toString())
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
    override suspend fun authenticate(response: AuthResponse): AuthResult = AuthResult.Rejected(ProviderFailure(FailureCategory.AuthRequired))
    override suspend fun close() { job.cancel(); stateValue.value = ProviderState(AuthState.Blocked, ConnectionState.Offline, SyncState.Degraded) }
}
