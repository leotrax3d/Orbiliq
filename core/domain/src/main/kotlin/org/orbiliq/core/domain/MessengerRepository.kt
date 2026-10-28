package org.orbiliq.core.domain

import kotlinx.coroutines.flow.Flow
import org.orbiliq.core.model.*

/** UI-facing local projection. Provider and storage implementations stay outside the UI. */
data class MessengerSnapshot(
    val entries: List<UnifiedInboxEntry>,
    val people: List<Person>,
    val identities: List<MessengerIdentity>,
    val links: List<PersonIdentityLink>,
    val accounts: List<Account>,
    val archived: Set<ConversationId>,
)
interface MessengerRepository : ConversationRepository, UnifiedCommunication {
    fun observeMessenger(): Flow<MessengerSnapshot>
    suspend fun archiveConversation(id: ConversationId, archived: Boolean)
}
