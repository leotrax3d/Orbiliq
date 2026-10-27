package org.orbiliq.core.data.storage

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*
import java.time.Instant

/** One owner per database. All operations execute on the caller's IO dispatcher. */
interface SnapshotStorage : AutoCloseable {
    fun read(): ByteArray?
    /** Atomic durable replacement; failure must preserve the previous snapshot. */
    fun write(bytes: ByteArray)
}

enum class PendingStatus { Queued, Dispatching, RetryableNoEffect, Accepted, OutcomeUnknown, Confirmed, Rejected }
data class PendingOperation(val command: ProviderCommand, val status: PendingStatus = PendingStatus.Queued,
    val attempts: Int = 0, val retryAt: Instant? = null, val receipt: String? = null, val failure: FailureCategory? = null) {
    init { require(attempts >= 0) }
    override fun toString() = "PendingOperation(status=$status, attempts=$attempts)"
}
data class StoredReaction(val message: MessageRef, val actor: IdentityRef, val reaction: String)
data class SyncCheckpoint(val cursor: ReplayCursor, val state: SyncState = SyncState.Idle)
data class RetentionPolicy(val messageDays: Int? = null, val mediaDays: Int = 30, val mediaMaxBytes: Long = 256L * 1024 * 1024) {
    init { require(messageDays == null || messageDays >= 1); require(mediaDays >= 0); require(mediaMaxBytes >= 0) }
}
/** Snapshots contain private data. Never log them. */
class LocalState(
    val accounts: List<Account> = emptyList(), val people: List<Person> = emptyList(),
    val identities: List<MessengerIdentity> = emptyList(), val links: List<PersonIdentityLink> = emptyList(),
    val groups: List<Group> = emptyList(), val conversations: List<Conversation> = emptyList(),
    val messages: List<Message> = emptyList(), val attachments: List<Attachment> = emptyList(),
    val metadata: List<ProviderData> = emptyList(), val checkpoints: List<SyncCheckpoint> = emptyList(),
    val pending: List<PendingOperation> = emptyList(), val retention: RetentionPolicy = RetentionPolicy(),
    val archived: Set<ConversationId> = emptySet(), val reactions: List<StoredReaction> = emptyList(),
    val manualIdentities: Set<IdentityId> = emptySet(),
)

interface LocalRepository : ConversationRepository, PeopleRepository {
    fun observeState(): Flow<LocalState>
    suspend fun snapshot(): LocalState
    suspend fun saveAccount(account: Account)
    suspend fun savePerson(person: Person)
    suspend fun saveAttachment(attachment: Attachment)
    suspend fun saveMetadata(data: ProviderData)
    /** Commit event and checkpoint together. Only acknowledge after this returns. */
    suspend fun apply(event: EventEnvelope)
    suspend fun enqueue(command: ProviderCommand, optimistic: Message? = null, expectedPerson: PersonId? = null)
    suspend fun updateOperation(id: CommandId, status: PendingStatus, retryAt: Instant? = null, receipt: String? = null, failure: FailureCategory? = null, remote: MessageRef? = null, capabilityRevision: String? = null)
    suspend fun setRetention(policy: RetentionPolicy)
    suspend fun archive(id: ConversationId, archived: Boolean)
    suspend fun prune(now: Instant)
}

/** Copy-on-write projection; publish only after durable commit. No silent reset on corruption. */
class PersistentLocalRepository(private val storage: SnapshotStorage) : LocalRepository {
    private val mutex = Mutex()
    private var loaded = false
    private val state = MutableStateFlow(LocalState())
    private fun load(): LocalState {
        if (!loaded) {
            val decoded = storage.read()?.let { bytes -> try { StoreCodec.decode(bytes) } finally { bytes.fill(0) } } ?: LocalState()
            val recovered = MutableLocalState(decoded)
            recovered.pending.replaceAll { if(it.status == PendingStatus.Dispatching) it.copy(status = PendingStatus.OutcomeUnknown) else it }
            state.value = recovered.freeze()
            loaded = true
        }
        return state.value
    }
    override suspend fun snapshot(): LocalState = mutex.withLock { load() }
    override fun observeState(): Flow<LocalState> = flow { snapshot(); emitAll(state) }
    override fun observeInbox(): Flow<List<ConversationSummary>> = observeState().map { s ->
        unifiedInbox(s).map { ConversationSummary(it.route.conversation.ref, it.route.conversation.title) }
    }
    override fun observePeople(): Flow<List<Person>> = observeState().map { it.people }
    override fun observeIdentities(personId: PersonId): Flow<List<MessengerIdentity>> = observeState().map { s ->
        val ids = s.links.filter { it.personId == personId }.map { it.identityId }.toSet()
        s.identities.filter { it.id in ids }
    }
    private suspend fun mutate(block: MutableLocalState.() -> Unit) = mutex.withLock {
        val next = MutableLocalState(load()).apply(block).freeze()
        val bytes = StoreCodec.encode(next)
        try { storage.write(bytes) } finally { bytes.fill(0) }
        state.value = next
    }
    override suspend fun saveAccount(account: Account) = mutate {
        accounts.find { it.id == account.id }?.let { require(it.providerId == account.providerId) }
        accounts.replace(account) { it.id == account.id }
        reconcileContacts()
    }
    override suspend fun savePerson(person: Person) = mutate { people.replace(person) { it.id == person.id } }
    override suspend fun saveAttachment(attachment: Attachment) = mutate {
        require(accounts.any { it.id == attachment.accountId })
        attachments.find { it.id == attachment.id }?.let { require(it.accountId == attachment.accountId) }
        require(attachment.ref == null || attachments.none { it.ref == attachment.ref && it.id != attachment.id })
        attachments.replace(attachment) { it.id == attachment.id }
    }
    override suspend fun saveMetadata(data: ProviderData) = mutate {
        metadata.replace(data) { it.providerId == data.providerId && it.namespace == data.namespace }
    }
    override fun observeContactConflicts(): Flow<List<ContactConflict>> = observeState().map { contactConflicts(it) }
    override suspend fun reassign(identityId: IdentityId, expectedPerson: PersonId?, target: PersonId) = mutate {
        if (links.find { it.identityId == identityId }?.personId != expectedPerson) throw ContactAssignmentConflict()
        require(people.any { it.id == target } && identities.any { it.id == identityId })
        val protected = links.filter { it.personId == expectedPerson || it.personId == target }.map { it.identityId }.toSet()
        manualIdentities.addAll(protected)
        links.replaceAll { if (it.identityId in protected) it.copy(origin = LinkOrigin.Manual) else it }
        links.replace(PersonIdentityLink(target, identityId, LinkOrigin.Manual)) { it.identityId == identityId }
        manualIdentities.add(identityId)
    }
    override suspend fun split(identityId: IdentityId, expectedPerson: PersonId, person: Person) = mutate {
        if (links.find { it.identityId == identityId }?.personId != expectedPerson) throw ContactAssignmentConflict()
        require(people.none { it.id == person.id })
        manualIdentities.addAll(links.filter { it.personId == expectedPerson }.map { it.identityId })
        links.replaceAll { if (it.personId == expectedPerson) it.copy(origin = LinkOrigin.Manual) else it }
        people.add(person)
        links.replace(PersonIdentityLink(person.id, identityId, LinkOrigin.Manual)) { it.identityId == identityId }
        manualIdentities.add(identityId)
    }
    override suspend fun link(link: PersonIdentityLink) = mutate {
        require(people.any { it.id == link.personId } && identities.any { it.id == link.identityId })
        require(link.origin == LinkOrigin.Manual) { "Automatic evidence is managed by matching" }
        val old = links.find { it.identityId == link.identityId }
        if (old != null && old.personId != link.personId && !old.origin.isAutomatic()) throw ContactAssignmentConflict()
        if (old != null && old.personId != link.personId) {
            val siblings = links.filter { it.personId == old.personId }.map { it.identityId }.toSet()
            manualIdentities.addAll(siblings)
            links.replaceAll { if (it.identityId in siblings) it.copy(origin = LinkOrigin.Manual) else it }
        }
        links.replace(link) { it.identityId == link.identityId }
        manualIdentities.add(link.identityId)
        if (old != null && old.origin.isAutomatic() && old.personId != link.personId && links.none { it.personId == old.personId }) {
            people.removeAll { it.id == old.personId }
        }
    }
    override suspend fun unlink(identityId: IdentityId) = mutate {
        require(identities.any { it.id == identityId })
        links.removeAll { it.identityId == identityId }; manualIdentities.add(identityId)
    }
    override suspend fun merge(source: PersonId, target: PersonId) = mutate {
        require(source != target && people.any { it.id == source } && people.any { it.id == target })
        manualIdentities.addAll(links.filter { it.personId == source || it.personId == target }.map { it.identityId })
        links.replaceAll { if (it.personId == source || it.personId == target) it.copy(personId = target, origin = LinkOrigin.Manual) else it }
        people.removeAll { it.id == source }
    }
    override suspend fun apply(event: EventEnvelope) = mutate {
        require(event.schemaVersion == 1)
        val account = event.cursor.accountId
        require(accounts.any { it.id == account })
        val previous = checkpoints.find { it.cursor.accountId == account && it.cursor.streamId == event.cursor.streamId }
        if (previous?.cursor == event.cursor) return@mutate
        // Sessions must supply ordered replay within an epoch. An epoch change requires provider recovery.
        when (val p = event.payload) {
            is ProviderEvent.IdentityChanged -> {
                identities.find { it.id == p.identity.id }?.let { require(it.ref == p.identity.ref) }
                require(identities.none { it.ref == p.identity.ref && it.id != p.identity.id })
                require(p.identity.ref.accountId == account)
                identities.replace(p.identity) { it.id == p.identity.id }
                reconcileContacts()
            }
            is ProviderEvent.ConversationChanged -> {
                conversations.find { it.id == p.conversation.id }?.let { require(it.ref == p.conversation.ref) }
                require(conversations.none { it.ref == p.conversation.ref && it.id != p.conversation.id })
                conversations.replace(p.conversation) { it.id == p.conversation.id }
            }
            is ProviderEvent.GroupChanged -> {
                groups.find { it.id == p.group.id }?.let { require(it.ref == p.group.ref) }
                require(groups.none { it.ref == p.group.ref && it.id != p.group.id })
                groups.replace(p.group) { it.id == p.group.id }
            }
            is ProviderEvent.MessageUpserted -> {
                require(conversations.any { it.id == p.message.conversationId && it.ref == p.message.route })
                val optimistic = p.commandId?.let { id -> messages.find { it.id == MessageId(id.value) && it.route == p.message.route } }
                val existing = messages.find { it.ref != null && it.ref == p.message.ref }
                val projected = p.message.copy(id = optimistic?.id ?: existing?.id ?: p.message.id)
                messages.find { it.id == projected.id }?.let {
                    require(it.route == projected.route)
                    require(it.ref == projected.ref || (it == optimistic && it.ref == null))
                }
                messages.removeAll { it.id == projected.id || (projected.ref != null && it.ref == projected.ref) }
                messages.add(projected)
                p.commandId?.let { id ->
                    pending.find { it.command.id == id }?.let { require(it.command.route == p.message.route) }
                    pending.replaceAll { if(it.command.id == id) it.copy(status = PendingStatus.Confirmed) else it }
                }
            }
            is ProviderEvent.MessageDeleted -> {
                messages.removeAll { it.ref == p.ref }; reactions.removeAll { it.message == p.ref }
            }
            is ProviderEvent.ReceiptChanged -> messages.replaceAll { if(it.ref == p.message) it.copy(delivery = p.state) else it }
            is ProviderEvent.Extension -> metadata.replace(p.data) { it.providerId == p.data.providerId && it.namespace == p.data.namespace }
            // Keep durable extension facts without treating transient typing/capabilities as history.
            is ProviderEvent.ReactionChanged -> {
                reactions.removeAll { it.message == p.message && it.actor == p.actor && it.reaction == p.reaction }
                if(!p.removed) reactions.add(StoredReaction(p.message, p.actor, p.reaction))
            }
            else -> Unit
        }
        checkpoints.replace(SyncCheckpoint(event.cursor, if(event.payload is ProviderEvent.GapDetected) SyncState.Gap else previous?.state ?: SyncState.Idle)) {
            it.cursor.accountId == account && it.cursor.streamId == event.cursor.streamId
        }
    }
    override suspend fun enqueue(command: ProviderCommand, optimistic: Message?, expectedPerson: PersonId?) = mutate {
        if (expectedPerson != null && communicationRoutes(freeze(), ConversationTarget.PersonChat(expectedPerson))
                .none { it.conversation.ref == command.route }) throw ContactAssignmentConflict()
        require(accounts.any { it.id == command.route.accountId })
        require(conversations.any { it.ref == command.route })
        require(pending.none { it.command.id == command.id })
        optimistic?.let {
            require(it.id == MessageId(command.id.value) && it.route == command.route && it.ref == null)
            require(it.direction == MessageDirection.Outgoing && messages.none { m -> m.id == it.id })
            messages.add(it)
        }
        pending.add(PendingOperation(command))
    }
    override suspend fun updateOperation(id: CommandId, status: PendingStatus, retryAt: Instant?, receipt: String?, failure: FailureCategory?, remote: MessageRef?, capabilityRevision: String?) = mutate {
        require(pending.any { it.command.id == id })
        pending.replaceAll { if(it.command.id == id) it.copy(command = capabilityRevision?.let { revision -> it.command.copy(capabilityRevision = revision) } ?: it.command, status = if(it.status == PendingStatus.Confirmed) PendingStatus.Confirmed else status,
            attempts = it.attempts + if(status == PendingStatus.Dispatching) 1 else 0, retryAt = retryAt, receipt = receipt ?: it.receipt, failure = failure) else it }
        val operation = pending.single { it.command.id == id }
        require(remote == null || remote.conversation == operation.command.route)
        messages.replaceAll { m -> if(m.id == MessageId(id.value) && m.route == operation.command.route) m.copy(
            ref = remote ?: m.ref, delivery = when(operation.status) {
                PendingStatus.Confirmed -> if(m.delivery in setOf(DeliveryState.Delivered, DeliveryState.Read)) m.delivery else DeliveryState.Sent
                PendingStatus.Accepted -> DeliveryState.Accepted
                PendingStatus.Rejected -> DeliveryState.Failed
                PendingStatus.OutcomeUnknown, PendingStatus.Dispatching -> DeliveryState.OutcomeUnknown
                else -> DeliveryState.Pending
            }) else m }
    }
    override suspend fun setRetention(policy: RetentionPolicy) = mutate { retention = policy }
    override suspend fun archive(id: ConversationId, archived: Boolean) = mutate {
        require(conversations.any { it.id == id })
        if(archived) this.archived.add(id) else this.archived.remove(id)
    }
    override suspend fun prune(now: Instant) = mutate {
        val activeRoutes = pending.filter { it.status !in setOf(PendingStatus.Confirmed, PendingStatus.Rejected) }.map { it.command.route }.toSet()
        messages.removeAll { m ->
            val expired = m.policy.expiresAt?.let { it <= now } == true
            val old = retention.messageDays?.let { m.receivedAt < now.minusSeconds(it.toLong() * 86400) } == true
            expired || (old && m.conversationId !in archived && m.route !in activeRoutes)
        }
        val activeDependencies = pending.filter { it.status !in setOf(PendingStatus.Confirmed, PendingStatus.Rejected) }
            .flatMap { it.command.dependencies }.toSet()
        val archivedRoutes = conversations.filter { it.id in archived }.map { it.ref }.toSet()
        retention.messageDays?.let { days ->
            val cutoff = now.minusSeconds(days.toLong() * 86400)
            pending.removeAll { it.status in setOf(PendingStatus.Confirmed, PendingStatus.Rejected) &&
                it.command.createdAt < cutoff && it.command.route !in archivedRoutes && it.command.id !in activeDependencies }
        }
        reactions.removeAll { r -> messages.none { it.ref == r.message } }
        val used = messages.flatMap { (it.content as? MessageContent.Media)?.attachments.orEmpty() }.toSet()
        attachments.removeAll { it.id !in used && it.policy.expiresAt?.let { t -> t <= now } == true }
    }
}

private fun <T> MutableList<T>.replace(value: T, predicate: (T) -> Boolean) { removeAll(predicate); add(value) }

internal class MutableLocalState(s: LocalState) {
    val accounts = s.accounts.toMutableList(); val people = s.people.toMutableList()
    val identities = s.identities.toMutableList(); val links = s.links.toMutableList()
    val groups = s.groups.toMutableList(); val conversations = s.conversations.toMutableList()
    val messages = s.messages.toMutableList(); val attachments = s.attachments.toMutableList()
    val metadata = s.metadata.toMutableList(); val checkpoints = s.checkpoints.toMutableList()
    val pending = s.pending.toMutableList()
    val reactions = s.reactions.toMutableList()
    val manualIdentities = s.manualIdentities.toMutableSet()
    var retention = s.retention; val archived = s.archived.toMutableSet()
    fun freeze() = LocalState(accounts.toList(), people.toList(), identities.toList(), links.toList(), groups.toList(),
        conversations.toList(), messages.toList(), attachments.toList(), metadata.toList(), checkpoints.toList(),
        pending.toList(), retention, archived.toSet(), reactions.toList(), manualIdentities.toSet())
}
