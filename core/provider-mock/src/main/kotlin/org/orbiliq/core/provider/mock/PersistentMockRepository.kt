package org.orbiliq.core.provider.mock

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.orbiliq.core.data.storage.*
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*

/** App-scope sync, independent from screen subscriptions. Initialization performs IO off main. */
class PersistentMockRepository(
    private val scope: CoroutineScope,
    private val openRepository: suspend () -> LocalRepository,
) : MessengerRepository {
    private val mutex = Mutex()
    private var local: LocalRepository? = null
    private var active: MockProviderSession? = null
    private var coordinator: SyncCoordinator? = null
    suspend fun sync(): SyncCoordinator { initialize(); return checkNotNull(coordinator) }
    suspend fun session(): MockProviderSession { initialize(); return checkNotNull(active) }
    private suspend fun initialize(): LocalRepository = mutex.withLock {
        local?.let { return@withLock it }
        val repository = openRepository()
        val snapshot = repository.snapshot()
        val account = snapshot.accounts.find { it.id == MockFixtures.account.id } ?: MockFixtures.account
        val session = MockProviderFactory(MockConfiguration(requireLogin = false, start = Instant.now())).createSession(account)
        if(snapshot.accounts.any { it.id == session.accountId }) session.restore(snapshot)
        else repository.saveAccount(session.account)
        // Commit the initial replay before publishing initialization to inbox subscribers.
        session.recordedEvents.forEach { repository.apply(it) }
        coordinator = SyncCoordinator(repository, session, beforeApply = {
            session.attachments.value.forEach { repository.saveAttachment(it) }
        })
        active = session
        local = repository
        scope.launch(Dispatchers.IO) {
            checkNotNull(coordinator).run()
        }
        repository
    }
    private suspend fun communication(): UnifiedCommunicationService {
        val repository = withContext(Dispatchers.IO) { initialize() }
        val session = checkNotNull(active)
        // The app uses wall time; isolated scenarios retain their deterministic virtual clock.
        val elapsed = Duration.between(session.currentTime, Instant.now())
        if (!elapsed.isNegative) session.advanceBy(elapsed)
        return UnifiedCommunicationService(repository, { id -> session.takeIf { it.accountId == id } }, { session.currentTime })
    }
    override fun observeMessenger(): Flow<MessengerSnapshot> = flow {
        val repository = withContext(Dispatchers.IO) { initialize() }
        emitAll(repository.observeState().map { s ->
            val latest = s.messages.groupBy { it.route }.mapValues { it.value.maxWith(Message.timelineOrder) }
            MessengerSnapshot(s.conversations.mapNotNull { c ->
                s.accounts.find { it.id == c.ref.accountId }?.let { UnifiedInboxEntry(SendRoute(it.providerId, c), latest[c.ref]) }
            }.sortedByDescending { it.latestMessage?.displayTime }, s.people, s.identities, s.links, s.accounts, s.archived, simulation = true)
        })
    }.flowOn(Dispatchers.IO)
    override fun observeUnifiedInbox(): Flow<List<UnifiedInboxEntry>> = flow { emitAll(communication().observeUnifiedInbox()) }.flowOn(Dispatchers.IO)
    override fun observeTimeline(target: ConversationTarget): Flow<UnifiedTimeline> = flow { emitAll(communication().observeTimeline(target)) }.flowOn(Dispatchers.IO)
    override suspend fun sendRoutes(target: ConversationTarget, text: String, replyTo: MessageId?) =
        withContext(Dispatchers.IO) { communication().sendRoutes(target, text, replyTo) }
    override suspend fun sendText(target: ConversationTarget, route: SendRoute, text: String, replyTo: MessageId?) =
        withContext(Dispatchers.IO) { communication().sendText(target, route, text, replyTo) }
    override suspend fun archiveConversation(id: ConversationId, archived: Boolean) {
        withContext(Dispatchers.IO) { initialize().archive(id, archived) }
    }
    override fun observeInbox(): Flow<List<ConversationSummary>> = flow {
        val repository = withContext(Dispatchers.IO) { initialize() }
        emitAll(repository.observeInbox().map { list -> list.map { it.copy(title = "[Mock] ${it.title}") } })
    }.flowOn(Dispatchers.IO)
}
