package org.orbiliq.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*

sealed interface MessengerState {
    data object Loading : MessengerState
    data class Ready(val snapshot: MessengerSnapshot) : MessengerState
    data object Unconfigured : MessengerState
    data object Error : MessengerState
}
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InboxViewModel(repository: ConversationRepository) : ViewModel() {
    val conversations = repository.observeInbox().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val messenger = repository as? MessengerRepository
    private val reload = MutableStateFlow(0)
    val state = reload.flatMapLatest {
        messenger?.observeMessenger()?.map<MessengerSnapshot, MessengerState> { MessengerState.Ready(it) }
            ?.onStart { emit(MessengerState.Loading) }?.catch { emit(MessengerState.Error) }
            ?: flowOf(MessengerState.Unconfigured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessengerState.Loading)
    fun retry() { reload.value++ }
    fun timeline(ref: ConversationRef): Flow<UnifiedTimeline> =
        messenger?.observeTimeline(ConversationTarget.ProviderChat(ref))?.catch { _notice.value = "Verlauf konnte nicht geladen werden."; emit(UnifiedTimeline(emptyList(), emptyList())) } ?: flowOf(UnifiedTimeline(emptyList(), emptyList()))
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }
    suspend fun textConstraints(route: SendRoute): OperationConstraints? = try {
        (messenger?.sendRoutes(ConversationTarget.ProviderChat(route.conversation.ref), "x", null)
            ?.find { it.route.conversation.ref == route.conversation.ref }?.availability as? Availability.Available)?.constraints
    } catch (c: CancellationException) { throw c }
      catch (_: Exception) { null }
    suspend fun canSend(route: SendRoute, text: String, reply: MessageId?): Boolean = try {
        messenger?.sendRoutes(ConversationTarget.ProviderChat(route.conversation.ref), text.ifBlank { " " }, reply)
            ?.any { it.route.conversation.ref == route.conversation.ref && it.availability is Availability.Available } == true
    } catch (c: CancellationException) { throw c }
      catch (_: Exception) { false }
    suspend fun send(route: SendRoute, text: String, reply: MessageId?): Boolean = try {
        when (messenger?.sendText(ConversationTarget.ProviderChat(route.conversation.ref), route, text.trim(), reply)) {
            is SendTextResult.Queued -> true
            else -> { _notice.value = "Versand nicht verfügbar. Entwurf bleibt erhalten."; false }
        }
    } catch (c: CancellationException) { throw c }
      catch (_: Exception) { _notice.value = "Nachricht konnte nicht gespeichert werden. Bitte erneut versuchen."; false }
    suspend fun canReact(message: MessageId): Boolean = try { messenger?.canReact(message) == true }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { false }
    fun react(message: MessageId, emoji: String) { viewModelScope.launch {
        val queued = try { messenger?.react(message, emoji) == true }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { false }
        if (!queued) _notice.value = "Reaktion nicht verfügbar."
    } }
    fun archive(id: ConversationId, archived: Boolean) { viewModelScope.launch {
        try { messenger?.archiveConversation(id, archived) }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { _notice.value = "Ordner konnte nicht geändert werden." }
    } }
}
