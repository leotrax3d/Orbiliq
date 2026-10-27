package org.orbiliq.core.domain

import kotlinx.coroutines.flow.Flow
import org.orbiliq.core.model.ConversationSummary

interface ConversationRepository {
    fun observeInbox(): Flow<List<ConversationSummary>>
}
