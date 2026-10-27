package org.orbiliq.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.orbiliq.core.domain.ConversationRepository

class InboxViewModel(repository: ConversationRepository) : ViewModel() {
    val conversations = repository.observeInbox().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
}
