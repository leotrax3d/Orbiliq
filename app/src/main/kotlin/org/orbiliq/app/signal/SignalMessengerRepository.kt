package org.orbiliq.app.signal

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.orbiliq.app.storage.AndroidLocalStorage
import org.orbiliq.core.data.storage.*
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.signal.*

/** Single application owner of encrypted storage and the current consented Signal session. */
internal class SignalMessengerRepository(context: Context) : MessengerRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transport = AndroidSignalMessaging(context.applicationContext)
    private val ready = CompletableDeferred<LocalRepository>()
    private val account = MutableStateFlow<AccountId?>(null)
    private val notice = MutableStateFlow<String?>("Signal-Companion fehlt oder Nachrichtenzugriff ist noch nicht freigegeben.")
    @Volatile private var session: SignalMessagingSession? = null
    init {
        scope.launch {
            try {
                val storage = AndroidLocalStorage(context.applicationContext)
                val repository = storage.repository
                repository.snapshot()
                ready.complete(repository)
                var coordinator: Job? = null
                while (isActive) {
                    val binding = transport.binding()
                    val id = binding?.let { SignalMessagingSession.accountFor(it).id }
                    if (id != account.value) {
                        coordinator?.cancelAndJoin(); session?.close(); session = null
                        account.value = null
                        notice.value = "Signal-Companion ist nicht verfügbar oder der Nachrichtenzugriff fehlt."
                        if (binding != null && id != null) {
                            repository.saveAccount(SignalMessagingSession.accountFor(binding))
                            val snapshot = repository.snapshot()
                            val commands = snapshot.pending.filter { it.command.route.accountId == id }.mapNotNull { operation ->
                                operation.receipt?.toLongOrNull()?.let { MessageRef(operation.command.route, it.toString()) to operation.command.id }
                            }.toMap()
                            val next = SignalMessagingSession(binding, transport, snapshot.messages.filter { it.route.accountId == id }.mapNotNull { it.ref }, commands,
                                previousReactions = snapshot.reactions.filter { it.message.conversation.accountId == id }
                                    .groupBy { it.message }.mapValues { (_, rows) -> rows.associate { it.actor.remoteIdentityId to it.reaction } })
                            session = next
                            account.value = id
                            notice.value = null
                            coordinator = launch {
                                launch { next.state.collect { state ->
                                    notice.value = if (state.sync == org.orbiliq.core.provider.api.SyncState.Degraded)
                                        "Signal-Synchronisation unterbrochen. Bitte den Companion prüfen." else null
                                } }
                                SyncCoordinator(repository, next).run()
                            }
                        }
                    }
                    delay(2000)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                notice.value = "Der lokale Signal-Speicher ist nicht verfügbar."
                ready.completeExceptionally(IllegalStateException("signal.storage.unavailable"))
            }
        }
    }
    private suspend fun communication() = UnifiedCommunicationService(ready.await(), { id -> session?.takeIf { it.accountId == id } })
    override fun observeMessenger(): Flow<MessengerSnapshot> = flow {
        emit(MessengerSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptySet()))
        val repository = ready.await()
        emitAll(combine(repository.observeState(), account, notice) { s, active, syncNotice ->
            val latest = s.messages.filter { it.route.accountId == active }.groupBy { it.route }.mapValues { it.value.maxWith(Message.timelineOrder) }
            val identities = s.identities.filter { it.ref.accountId == active }
            val identityIds = identities.map { it.id }.toSet()
            val links = s.links.filter { it.identityId in identityIds }
            val peopleIds = links.map { it.personId }.toSet()
            MessengerSnapshot(s.conversations.filter { it.ref.accountId == active }.mapNotNull { c ->
                s.accounts.find { it.id == active }?.let { UnifiedInboxEntry(SendRoute(it.providerId, c), latest[c.ref]) }
            }.sortedByDescending { it.latestMessage?.displayTime }, s.people.filter { it.id in peopleIds },
                identities, links,
                s.accounts.filter { it.id == active }, s.archived, syncNotice = syncNotice)
        })
    }.flowOn(Dispatchers.IO)
    override fun observeInbox(): Flow<List<ConversationSummary>> = observeMessenger().map { s ->
        s.entries.map { ConversationSummary(it.route.conversation.ref, it.route.conversation.title) }
    }
    override fun observeUnifiedInbox(): Flow<List<UnifiedInboxEntry>> = observeMessenger().map { it.entries }
    override fun observeTimeline(target: ConversationTarget): Flow<UnifiedTimeline> = flow {
        emitAll(combine(communication().observeTimeline(target), account) { timeline, active ->
            UnifiedTimeline(timeline.routes.filter { it.conversation.ref.accountId == active }, timeline.messages.filter { it.route.accountId == active })
        })
    }.flowOn(Dispatchers.IO)
    override suspend fun sendRoutes(target: ConversationTarget, text: String, replyTo: MessageId?) = withContext(Dispatchers.IO) {
        communication().sendRoutes(target, text, replyTo).filter { it.route.conversation.ref.accountId == account.value }
    }
    override suspend fun sendText(target: ConversationTarget, route: SendRoute, text: String, replyTo: MessageId?) = withContext(Dispatchers.IO) {
        check(route.conversation.ref.accountId == account.value)
        communication().sendText(target, route, text, replyTo)
    }
    override suspend fun archiveConversation(id: ConversationId, archived: Boolean) = withContext(Dispatchers.IO) {
        val repository = ready.await()
        check(repository.snapshot().conversations.any { it.id == id && it.ref.accountId == account.value })
        repository.archive(id, archived)
    }
}
