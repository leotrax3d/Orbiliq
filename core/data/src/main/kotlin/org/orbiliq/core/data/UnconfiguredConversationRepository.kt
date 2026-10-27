package org.orbiliq.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.orbiliq.core.domain.ConversationRepository
import org.orbiliq.core.model.ConversationSummary

/** No accounts or stores exist in the foundation. Never generates sample conversations. */
class UnconfiguredConversationRepository : ConversationRepository {
    override fun observeInbox(): Flow<List<ConversationSummary>> = flowOf(emptyList())
}
