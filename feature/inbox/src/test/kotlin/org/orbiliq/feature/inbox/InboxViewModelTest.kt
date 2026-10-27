package org.orbiliq.feature.inbox

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbiliq.core.domain.ConversationRepository
import org.orbiliq.core.model.ConversationRef
import org.orbiliq.core.model.ConversationSummary

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    @Test fun observesLocalChangesWhileSubscribed() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                val localInbox = MutableStateFlow(emptyList<ConversationSummary>())
                val repository = object : ConversationRepository {
                    override fun observeInbox() = localInbox
                }
                val model = InboxViewModel(repository)
                try {
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                        model.conversations.collect()
                    }
                    runCurrent()
                    val conversation = ConversationSummary(ConversationRef("account", "chat"), "Test")
                    localInbox.value = listOf(conversation)
                    runCurrent()
                    assertEquals(listOf(conversation), model.conversations.value)
                } finally {
                    model.viewModelScope.cancel()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
