package org.orbiliq.core.provider.mock

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import org.orbiliq.core.domain.ConversationRepository
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

/** In-memory test backend. Each mutation is atomic, each session owns its virtual time.
 * Advance time explicitly; no background jobs, sockets, wall-clock waits or automatic retries.
 * Events survive slow collectors but deliberately do not survive close/process death.
 */
class MockProviderSession internal constructor(
    val account: Account, private val configuration: MockConfiguration,
) : ProviderSession, ConversationRepository {
    override val accountId = account.id
    override val epoch: String = UUID.randomUUID().toString()
    private val lock = Any()
    private var closed = false
    private var now = configuration.start
    private var sequence = 0L
    private var revision = 1L
    private var serial = 0L
    private var online = !configuration.initiallyOffline
    private var profile = configuration.profile
    private val faults = ArrayDeque<MockFault>()
    private val journal = MutableStateFlow<List<EventEnvelope>>(emptyList())
    private val changes = MutableStateFlow(0L)
    private val mutableState = MutableStateFlow(ProviderState(
        if (configuration.requireLogin) AuthState.NeedsInput else AuthState.Ready,
        if (online) ConnectionState.Online else ConnectionState.Offline, SyncState.Idle,
        challenge = if (configuration.requireLogin) challenge() else null,
    ))
    override val state: StateFlow<ProviderState> = mutableState.asStateFlow()
    private val mutableConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = mutableConversations.asStateFlow()
    private val mutableMessages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = mutableMessages.asStateFlow()
    private val mutableIdentities = MutableStateFlow<List<MessengerIdentity>>(emptyList())
    val identities: StateFlow<List<MessengerIdentity>> = mutableIdentities.asStateFlow()
    private val mutableGroups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = mutableGroups.asStateFlow()
    private val mutableAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = mutableAttachments.asStateFlow()
    private val mediaBytes = mutableMapOf<AttachmentRef, ByteArray>()
    private data class Completed(val command: ProviderCommand, val result: DispatchResult.Confirmed)
    private val completed = mutableMapOf<CommandId, Completed>()
    private val uncertain = mutableSetOf<CommandId>()
    private val keys = mutableMapOf<String, CommandId>()
    private data class Scheduled(val at: Instant, val order: Long, val action: () -> Unit)
    private val scheduled = mutableListOf<Scheduled>()
    private var acknowledged = 0L
    val media: ProviderMedia = MockMedia(this, configuration.maxMediaBytes)
    val recordedEvents: List<EventEnvelope> get() = synchronized(lock) { journal.value.toList() }
    val currentTime: Instant get() = synchronized(lock) { now }
    val capabilityRevision: String get() = synchronized(lock) { revision.toString() }
    val directRoute = ConversationRef(accountId, "anna")
    val groupRoute = ConversationRef(accountId, "team")
    val self = IdentityRef(accountId, "self")
    val anna = IdentityRef(accountId, "anna")

    init { if (!configuration.requireLogin) seed() }
    private fun challenge() = AuthChallenge("mock-login", AuthInputKind.Code, now.plusSeconds(300), instructionCode = "mock.enter-000000")
    private fun checkOpen() = check(!closed) { "Mock session closed" }
    private fun event(payload: ProviderEvent, occurredAt: Instant = now) {
        sequence++
        journal.value = journal.value + EventEnvelope("mock-$sequence", ReplayCursor(accountId, "mock", epoch, "$sequence"),
            occurredAt, now, payload)
        mutableState.value = mutableState.value.copy(lastDurableEventAt = now)
    }
    private fun changed() { revision++; changes.value++ }
    private fun seed() {
        if (mutableConversations.value.isNotEmpty()) return
        mutableIdentities.value = listOf(
            MessengerIdentity(IdentityId("${accountId.value}:self"), self, "Du (Mock)"),
            MessengerIdentity(IdentityId("${accountId.value}:anna"), anna, "Anna Beispiel"),
            MessengerIdentity(IdentityId("${accountId.value}:ben"), IdentityRef(accountId, "ben"), "Ben Beispiel"),
        )
        mutableIdentities.value.forEach { event(ProviderEvent.IdentityChanged(it)) }
        val members = mutableIdentities.value.mapIndexed { index, identity ->
            GroupMember(identity.id, if (index == 0) GroupRole.Owner else GroupRole.Member)
        }.toSet()
        val group = Group(GroupId("${accountId.value}:team"), GroupRef(accountId, "team"), "Orbiliq Testgruppe", members)
        mutableGroups.value = listOf(group)
        event(ProviderEvent.GroupChanged(group))
        mutableConversations.value = listOf(
            Conversation(ConversationId("${accountId.value}:anna"), directRoute, "Anna Beispiel", ConversationKind.Direct,
                mutableIdentities.value.take(2).map { it.id }.toSet()),
            Conversation(ConversationId("${accountId.value}:team"), groupRoute, group.title, ConversationKind.Group,
                members.map { it.identityId }.toSet(), group.id),
        )
        mutableConversations.value.forEach { event(ProviderEvent.ConversationChanged(it)) }
        addMessage(directRoute, anna, MessageContent.Text("Hallo! Dies ist eine lokale Testnachricht."), MessageDirection.Incoming)
        addMessage(groupRoute, IdentityRef(accountId, "ben"), MessageContent.Text("Willkommen in der Testgruppe."), MessageDirection.Incoming)
    }
    /** Restore a local mock account before exposing the session. No remote history is invented. */
    internal fun restore(snapshot: org.orbiliq.core.data.storage.LocalState) = synchronized(lock) {
        require(snapshot.accounts.any { it == account })
        val routes = snapshot.conversations.filter { it.ref.accountId == accountId }
        if(routes.isEmpty()) return@synchronized
        mutableConversations.value = routes
        mutableIdentities.value = snapshot.identities.filter { it.ref.accountId == accountId }
        mutableGroups.value = snapshot.groups.filter { it.ref.accountId == accountId }
        mutableMessages.value = snapshot.messages.filter { it.route.accountId == accountId && it.ref != null }
        snapshot.pending.filter { it.command.route.accountId == accountId }.forEach { op ->
            val message = snapshot.messages.find { it.id == MessageId(op.command.id.value) && it.ref != null }
            if(message != null) completed[op.command.id] = Completed(op.command, DispatchResult.Confirmed(message.ref))
            else if(op.status in setOf(org.orbiliq.core.data.storage.PendingStatus.OutcomeUnknown,
                org.orbiliq.core.data.storage.PendingStatus.Dispatching, org.orbiliq.core.data.storage.PendingStatus.Accepted)) uncertain.add(op.command.id)
        }
        mutableAttachments.value = snapshot.attachments.filter { it.accountId == accountId }
        serial = (mutableMessages.value.mapNotNull { it.ref?.remoteMessageId?.removePrefix("m")?.toLongOrNull() } +
            mutableAttachments.value.mapNotNull { it.ref?.remoteAttachmentId?.removePrefix("media")?.toLongOrNull() }).maxOrNull() ?: serial
        serial = maxOf(serial, 2L)
        now = maxOf(now, mutableMessages.value.maxOfOrNull { it.receivedAt } ?: now)
        journal.value = emptyList(); sequence = 0
        resync()
    }
    override fun observeInbox(): Flow<List<ConversationSummary>> = conversations.map { list -> list.map { ConversationSummary(it.ref, "[Mock] ${it.title}") } }
    fun timeline(route: ConversationRef): Flow<List<Message>> {
        synchronized(lock) { requireRoute(route) }
        return messages.map { list -> list.filter { it.route == route }.sortedWith(Message.timelineOrder) }
    }
    private fun requireRoute(route: ConversationRef): Conversation =
        mutableConversations.value.firstOrNull { it.ref == route } ?: throw IllegalArgumentException("Unknown mock route")
    private fun owns(scope: CapabilityScope): Boolean = scope.providerId == account.providerId && when (scope) {
        is CapabilityScope.Provider -> true
        is CapabilityScope.Account -> scope.accountId == accountId
        is CapabilityScope.Conversation -> mutableConversations.value.any { it.ref == scope.ref }
        is CapabilityScope.Message -> mutableMessages.value.any { it.ref == scope.ref }
    }
    private fun availability(operation: OperationKey, scope: CapabilityScope? = null): Availability = when {
        closed -> Availability.Restricted(Restriction.PermissionDenied)
        mutableState.value.auth != AuthState.Ready -> Availability.Restricted(Restriction.AuthRequired)
        !online -> Availability.Restricted(Restriction.Offline)
        scope is CapabilityScope.Message && operation in setOf(Operations.EditText, Operations.DeleteAll) &&
            mutableMessages.value.firstOrNull { it.ref == scope.ref }?.sender != self -> Availability.Restricted(Restriction.PermissionDenied)
        operation !in MockFixtures.operations -> Availability.Unsupported("mock.unsupported")
        profile == MockProfile.ReadOnly && operation !in setOf(Operations.History, Operations.ReceiveText, CommandPayload.Logout.operation) -> Availability.Unsupported("mock.read-only")
        profile == MockProfile.TextOnly && operation !in setOf(Operations.SendText, Operations.ReceiveText, Operations.History, CommandPayload.Logout.operation) -> Availability.Unsupported("mock.text-only")
        operation in setOf(Operations.SendText, Operations.Reply, Operations.EditText) -> Availability.Available(OperationConstraints(maxTextLength = configuration.maxTextLength))
        else -> Availability.Available()
    }
    private fun snapshot(scope: CapabilityScope) = CapabilitySnapshot(scope, "$revision", now, now.plusSeconds(86400),
        MockFixtures.operations.associateWith { OperationSupport(availability(it, scope), quality = DataQuality.ProviderHistory) })
    override fun observeCapabilities(scope: CapabilityScope): Flow<CapabilitySnapshot> {
        synchronized(lock) { checkOpen(); require(owns(scope)) }
        return changes.map { synchronized(lock) { snapshot(scope) } }
    }
    override suspend fun evaluate(request: OperationRequest): Availability = synchronized(lock) {
        if (!owns(request.scope)) return@synchronized Availability.Restricted(Restriction.PermissionDenied)
        val result = availability(request.operation, request.scope)
        if (result is Availability.Available && !result.constraints.permits(request, now)) Availability.Restricted(Restriction.Policy) else result
    }
    override suspend fun authenticate(response: AuthResponse): AuthResult = synchronized(lock) {
        val challenge = mutableState.value.challenge
        if (closed || challenge == null || response.challengeId != challenge.id || response.kind != challenge.kind || now >= challenge.expiresAt)
            return@synchronized AuthResult.Rejected(ProviderFailure(FailureCategory.AuthRequired))
        if (!online) return@synchronized AuthResult.Rejected(ProviderFailure(FailureCategory.NetworkUnavailable))
        val secret = response.secret()
        val valid = try { secret.contentEquals(charArrayOf('0','0','0','0','0','0')) } finally { secret.fill('\u0000') }
        if (!valid) return@synchronized AuthResult.Rejected(ProviderFailure(FailureCategory.AuthRequired))
        mutableState.value = mutableState.value.copy(auth = AuthState.Ready, challenge = null)
        changed(); event(ProviderEvent.AuthChanged(AuthState.Ready)); seed()
        runDue(); AuthResult.Ready
    }
    /** Refreshes an expired synthetic login challenge; never accepts real credentials. */
    fun restartLogin() = synchronized(lock) {
        checkOpen(); mutableState.value = mutableState.value.copy(auth = AuthState.NeedsInput, challenge = challenge())
        changed(); event(ProviderEvent.AuthChanged(AuthState.NeedsInput))
    }
    fun setOnline(value: Boolean) = synchronized(lock) {
        checkOpen(); online = value
        mutableState.value = mutableState.value.copy(connection = if (value) ConnectionState.Online else ConnectionState.Offline)
        changed(); event(ProviderEvent.CapabilitiesChanged(snapshot(CapabilityScope.Account(account.providerId, accountId))))
        if (value) runDue()
    }
    fun setProfile(value: MockProfile) = synchronized(lock) {
        checkOpen(); profile = value; changed()
        event(ProviderEvent.CapabilitiesChanged(snapshot(CapabilityScope.Account(account.providerId, accountId))))
    }
    fun failNext(fault: MockFault) = synchronized(lock) { checkOpen(); faults.addLast(fault) }
    override fun events(after: ReplayCursor?): Flow<EventEnvelope> {
        val initial = synchronized(lock) { checkOpen(); if (after == null) 0L else cursorNumber(after) ?: error("Invalid mock cursor") }
        return flow {
            var delivered = initial
            journal.combine(changes) { batch, _ -> batch }.takeWhile { synchronized(lock) { !closed } }.collect { batch ->
                for (envelope in batch) {
                    val index = envelope.cursor.token.toLong()
                    if (index > delivered) { emit(envelope); delivered = index }
                }
            }
        }
    }
    private fun cursorNumber(cursor: ReplayCursor): Long? {
        if (cursor.accountId != accountId || cursor.streamId != "mock" || cursor.epoch != epoch) return null
        return cursor.token.toLongOrNull()?.takeIf { it in 1..sequence && cursor.token == "$it" }
    }
    override suspend fun acknowledge(cursor: ReplayCursor): OperationResult<Unit> = synchronized(lock) {
        val index = cursorNumber(cursor)
        if (closed || index == null) OperationResult.Rejected(ProviderFailure(FailureCategory.ProtocolFailure))
        else { acknowledged = maxOf(acknowledged, index); OperationResult.Success(Unit) }
    }
    private fun reject(category: FailureCategory, code: String? = null) = DispatchResult.Rejected(ProviderFailure(category, diagnosticCode = code))
    private fun addMessage(route: ConversationRef, sender: IdentityRef, content: MessageContent, direction: MessageDirection,
        reply: MessageId? = null, commandId: CommandId? = null, occurredAt: Instant = now): Message {
        val conversation = requireRoute(route)
        serial++
        val message = Message(MessageId("${accountId.value}:m$serial"), conversation.id, route, sender, content, direction,
            now, occurredAt, MessageRef(route, "m$serial"), replyTo = reply, revision = "1", delivery = DeliveryState.Sent,
            quality = DataQuality.ProviderHistory)
        mutableMessages.value = mutableMessages.value + message
        event(ProviderEvent.MessageUpserted(message, commandId), occurredAt)
        return message
    }
    private fun updateMessage(message: Message) {
        mutableMessages.value = mutableMessages.value.map { if (it.ref == message.ref) message else it }
        event(ProviderEvent.MessageUpserted(message))
    }
    fun newCommand(payload: CommandPayload, route: ConversationRef = directRoute): ProviderCommand = synchronized(lock) {
        checkOpen(); serial++
        ProviderCommand(CommandId("mock-command-${UUID.randomUUID()}"), route, payload, now, now.plusSeconds(300), "$revision")
    }
    override suspend fun execute(command: ProviderCommand): DispatchResult {
        delay(configuration.dispatchDelayMillis)
        currentCoroutineContext().ensureActive()
        return dispatch(command)
    }
    private fun dispatch(command: ProviderCommand): DispatchResult = synchronized(lock) {
        if (closed || command.route.accountId != accountId) return@synchronized reject(FailureCategory.PermissionDenied)
        if (command.schemaVersion != 1) return@synchronized reject(FailureCategory.IncompatibleProvider)
        if (mutableConversations.value.none { it.ref == command.route }) return@synchronized reject(FailureCategory.PermissionDenied)
        val previous = completed[command.id] ?: command.idempotencyKey?.let { keys[it]?.let(completed::get) }
        if (previous != null) {
            if (previous.command.route != command.route || previous.command.payload != command.payload)
                return@synchronized reject(FailureCategory.ProtocolFailure, "mock.idempotency-conflict")
            completed[command.id] = Completed(command, previous.result)
            return@synchronized previous.result
        }
        if (now < command.createdAt || now >= command.expiresAt) return@synchronized reject(FailureCategory.PolicyExpired)
        if (mutableState.value.auth != AuthState.Ready) return@synchronized reject(FailureCategory.AuthRequired)
        if (!online) return@synchronized DispatchResult.RetryableNoEffect(ProviderFailure(FailureCategory.NetworkUnavailable))
        if (command.capabilityRevision != "$revision") return@synchronized reject(FailureCategory.CapabilityUnavailable, "mock.stale-capabilities")
        if (command.dependencies.any { it !in completed }) return@synchronized reject(FailureCategory.ProtocolFailure, "mock.unmet-dependency")
        val payload = command.payload
        val available = availability(payload.operation)
        val textLength = when (payload) {
            is CommandPayload.SendText -> payload.text.length
            is CommandPayload.EditText -> payload.text.length
            else -> null
        }
        if (payload is CommandPayload.SendMedia && (payload.caption?.length ?: 0) > configuration.maxTextLength)
            return@synchronized reject(FailureCategory.CapabilityUnavailable)
        if (available !is Availability.Available || !available.constraints.permits(OperationRequest(
                CapabilityScope.Conversation(account.providerId, command.route), payload.operation, textLength = textLength), now))
            return@synchronized reject(FailureCategory.CapabilityUnavailable)
        val targetRef = when (payload) {
            is CommandPayload.SendText -> payload.replyTo
            is CommandPayload.EditText -> payload.message
            is CommandPayload.Delete -> payload.message
            is CommandPayload.React -> payload.message
            is CommandPayload.MarkRead -> payload.through
            else -> null
        }
        val target = targetRef?.let { ref -> mutableMessages.value.firstOrNull { it.ref == ref } }
        if (targetRef != null && target == null) return@synchronized reject(FailureCategory.ProtocolFailure, "mock.message-missing")
        if ((payload is CommandPayload.EditText || payload is CommandPayload.Delete && payload.forEveryone) && target?.sender != self)
            return@synchronized reject(FailureCategory.PermissionDenied)
        if (payload is CommandPayload.EditText && (target?.content !is MessageContent.Text || payload.expectedRevision != target.revision))
            return@synchronized reject(FailureCategory.SyncConflict, "mock.revision-conflict")
        if (payload is CommandPayload.SendMedia && mutableAttachments.value.none { it.id == payload.attachment && it.kind == payload.kind })
            return@synchronized reject(FailureCategory.MediaUnavailable)
        val fault = faults.removeFirstOrNull()
        when (fault) {
            MockFault.NetworkNoEffect -> return@synchronized DispatchResult.RetryableNoEffect(ProviderFailure(FailureCategory.NetworkUnavailable))
            MockFault.RateLimited -> return@synchronized DispatchResult.RetryableNoEffect(ProviderFailure(FailureCategory.RateLimited, now.plusSeconds(30)))
            MockFault.UnknownNoEffect -> return@synchronized DispatchResult.OutcomeUnknown("mock.lost-response")
            else -> Unit
        }
        var remote: MessageRef? = null
        when (payload) {
            is CommandPayload.SendText -> remote = addMessage(command.route, self, MessageContent.Text(payload.text), MessageDirection.Outgoing, target?.id, command.id).ref
            is CommandPayload.SendMedia -> remote = addMessage(command.route, self, MessageContent.Media(listOf(payload.attachment), payload.caption), MessageDirection.Outgoing, commandId = command.id).ref
            is CommandPayload.EditText -> updateMessage(target!!.copy(content = MessageContent.Text(payload.text), revision = nextRevision(target)))
            is CommandPayload.Delete -> {
                mutableMessages.value = mutableMessages.value.filterNot { it.ref == payload.message }
                event(ProviderEvent.MessageDeleted(payload.message, nextRevision(target!!)))
            }
            is CommandPayload.React -> event(ProviderEvent.ReactionChanged(payload.message, self, payload.reaction, false))
            is CommandPayload.MarkRead -> receipt(payload.through, DeliveryState.Read)
            is CommandPayload.SetTyping -> event(ProviderEvent.TypingChanged(command.route, self, payload.active))
            CommandPayload.Logout -> {
                mutableState.value = mutableState.value.copy(auth = AuthState.Revoked, challenge = null)
                changed(); event(ProviderEvent.AuthChanged(AuthState.Revoked))
            }
            is CommandPayload.Extension -> return@synchronized reject(FailureCategory.CapabilityUnavailable)
        }
        val confirmed = DispatchResult.Confirmed(remote)
        completed[command.id] = Completed(command, confirmed)
        command.idempotencyKey?.let { keys[it] = command.id }
        if (fault == MockFault.UnknownAfterCommit) DispatchResult.OutcomeUnknown("mock.lost-response") else confirmed
    }
    private fun nextRevision(message: Message) = ((message.revision?.toLongOrNull() ?: 0) + 1).toString()
    override suspend fun reconcile(request: ReconcileRequest): ReconcileResult = synchronized(lock) {
        if (closed || request.route.accountId != accountId) return@synchronized ReconcileResult.Failed(ProviderFailure(FailureCategory.PermissionDenied))
        if (!online) return@synchronized ReconcileResult.Failed(ProviderFailure(FailureCategory.NetworkUnavailable))
        val result = completed[request.commandId]
        if (request.commandId in uncertain) ReconcileResult.StillUnknown
        else if (result == null) ReconcileResult.ProvenNoEffect
        else if (result.command.route != request.route) ReconcileResult.Failed(ProviderFailure(FailureCategory.PermissionDenied))
        else ReconcileResult.Confirmed(result.result.remoteMessage)
    }
    private fun schedule(delay: Duration, action: () -> Unit) {
        require(!delay.isNegative); serial++; scheduled += Scheduled(now.plus(delay), serial, action)
        runDue()
    }
    private fun runDue() {
        if (!online || mutableState.value.auth != AuthState.Ready) return
        while (true) {
            val next = scheduled.filter { it.at <= now }.minWithOrNull(compareBy<Scheduled> { it.at }.thenBy { it.order }) ?: break
            scheduled.remove(next); next.action()
        }
    }
    fun advanceBy(duration: Duration) = synchronized(lock) {
        checkOpen(); require(!duration.isNegative); now = now.plus(duration); changes.value++; runDue()
    }
    fun receiveText(route: ConversationRef = directRoute, text: String = "Neue lokale Nachricht", delay: Duration = Duration.ZERO) = synchronized(lock) {
        checkOpen(); requireRoute(route); require(text.isNotEmpty())
        val sentAt = now.plus(delay)
        schedule(delay) { addMessage(route, anna, MessageContent.Text(text), MessageDirection.Incoming, occurredAt = sentAt) }
    }
    /** Creates a fully local incoming attachment, including voice-note/video/file fixtures. */
    fun receiveMedia(kind: AttachmentKind, bytes: ByteArray, mimeType: String,
        route: ConversationRef = directRoute, delay: Duration = Duration.ZERO) = synchronized(lock) {
        checkOpen(); requireRoute(route); require(bytes.size <= configuration.maxMediaBytes)
        require(mimeType.isNotBlank()); require(!delay.isNegative)
        val copy = bytes.copyOf()
        schedule(delay) {
            val result = storeMedia(copy, mimeType, kind, incoming = true)
            copy.fill(0)
            if (result is OperationResult.Success) {
                val attachment = mutableAttachments.value.first { it.ref == result.value }
                addMessage(route, anna, MessageContent.Media(listOf(attachment.id)), MessageDirection.Incoming)
            }
        }
    }
    fun incomingTyping(route: ConversationRef = directRoute, duration: Duration = Duration.ofSeconds(3)) = synchronized(lock) {
        checkOpen(); requireRoute(route); require(!duration.isNegative)
        schedule(Duration.ZERO) { event(ProviderEvent.TypingChanged(route, anna, true)) }
        schedule(duration) { event(ProviderEvent.TypingChanged(route, anna, false)) }
    }
    fun incomingReaction(ref: MessageRef, reaction: String, removed: Boolean = false) = synchronized(lock) {
        checkOpen(); require(reaction.isNotBlank()); require(mutableMessages.value.any { it.ref == ref })
        schedule(Duration.ZERO) { event(ProviderEvent.ReactionChanged(ref, anna, reaction, removed)) }
    }
    private fun receipt(ref: MessageRef, delivery: DeliveryState) {
        val message = mutableMessages.value.firstOrNull { it.ref == ref } ?: return
        if (delivery.ordinal < message.delivery.ordinal) return
        mutableMessages.value = mutableMessages.value.map { if (it.ref == ref) it.copy(delivery = delivery) else it }
        event(ProviderEvent.ReceiptChanged(ref, delivery))
    }
    fun incomingReceipt(ref: MessageRef, delivery: DeliveryState, delay: Duration = Duration.ZERO) = synchronized(lock) {
        checkOpen(); require(mutableMessages.value.any { it.ref == ref }); require(delivery in setOf(DeliveryState.Delivered, DeliveryState.Read))
        schedule(delay) { receipt(ref, delivery) }
    }
    /** Simulates a remote edit racing a local expectedRevision, without silently overwriting it. */
    fun remoteEdit(ref: MessageRef, text: String) = synchronized(lock) {
        checkOpen(); require(text.isNotEmpty())
        val message = mutableMessages.value.first { it.ref == ref }
        schedule(Duration.ZERO) {
            val current = mutableMessages.value.firstOrNull { it.ref == message.ref }
            if (current != null) updateMessage(current.copy(content = MessageContent.Text(text), revision = nextRevision(current)))
        }
    }
    fun remoteDelete(ref: MessageRef) = synchronized(lock) {
        checkOpen(); require(mutableMessages.value.any { it.ref == ref })
        schedule(Duration.ZERO) {
            mutableMessages.value = mutableMessages.value.filterNot { it.ref == ref }
            event(ProviderEvent.MessageDeleted(ref, null))
        }
    }
    fun simulateSyncGap() = synchronized(lock) {
        checkOpen(); mutableState.value = mutableState.value.copy(sync = SyncState.Gap)
        event(ProviderEvent.GapDetected("mock.sync-conflict"))
    }
    fun resync() = synchronized(lock) {
        checkOpen(); check(online && mutableState.value.auth == AuthState.Ready)
        mutableState.value = mutableState.value.copy(sync = SyncState.CatchingUp)
        mutableIdentities.value.forEach { event(ProviderEvent.IdentityChanged(it)) }
        mutableGroups.value.forEach { event(ProviderEvent.GroupChanged(it)) }
        mutableConversations.value.forEach { event(ProviderEvent.ConversationChanged(it)) }
        mutableMessages.value.forEach { event(ProviderEvent.MessageUpserted(it)) }
        mutableState.value = mutableState.value.copy(sync = SyncState.Idle)
    }
    internal fun mediaFailure(upload: Boolean = false): ProviderFailure? = synchronized(lock) {
        when {
            closed -> ProviderFailure(FailureCategory.PermissionDenied)
            mutableState.value.auth != AuthState.Ready -> ProviderFailure(FailureCategory.AuthRequired)
            !online -> ProviderFailure(FailureCategory.NetworkUnavailable)
            upload && profile != MockProfile.Full -> ProviderFailure(FailureCategory.CapabilityUnavailable)
            else -> null
        }
    }
    internal fun storeMedia(bytes: ByteArray, mime: String, requestedKind: AttachmentKind? = null, incoming: Boolean = false): OperationResult<AttachmentRef> = synchronized(lock) {
        mediaFailure(upload = !incoming)?.let { return@synchronized OperationResult.Rejected(it) }
        serial++
        val ref = AttachmentRef(accountId, "media$serial")
        val kind = requestedKind ?: when { mime.startsWith("image/") -> AttachmentKind.Image; mime.startsWith("video/") -> AttachmentKind.Video
            mime.startsWith("audio/") -> AttachmentKind.Audio; else -> AttachmentKind.File }
        mutableAttachments.value += Attachment(AttachmentId("${accountId.value}:media$serial"), accountId, kind, mime, bytes.size.toLong(), ref = ref)
        mediaBytes[ref] = bytes.copyOf()
        OperationResult.Success(ref)
    }
    internal fun loadMedia(ref: AttachmentRef): ByteArray? = synchronized(lock) { mediaBytes[ref]?.copyOf() }
    override suspend fun close() = synchronized(lock) {
        if (!closed) {
            closed = true; scheduled.clear(); faults.clear(); mediaBytes.values.forEach { it.fill(0) }; mediaBytes.clear()
            mutableState.value = mutableState.value.copy(connection = ConnectionState.Offline)
            changes.value++; journal.value = emptyList()
        }
    }
}
