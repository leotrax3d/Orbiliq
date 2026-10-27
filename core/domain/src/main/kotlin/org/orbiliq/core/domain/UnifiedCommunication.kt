package org.orbiliq.core.domain

import kotlinx.coroutines.flow.Flow
import org.orbiliq.core.model.*

/** Separate provider chats are the default; merging is an explicit query, never a rewrite. */
sealed interface ConversationTarget {
    data class ProviderChat(val ref: ConversationRef) : ConversationTarget
    data class PersonChat(val personId: PersonId) : ConversationTarget
}
data class SendRoute(val providerId: ProviderId, val conversation: Conversation)
data class UnifiedInboxEntry(val route: SendRoute, val latestMessage: Message?)
data class UnifiedTimeline(val routes: List<SendRoute>, val messages: List<Message>)
data class RouteAvailability(val route: SendRoute, val availability: Availability)
sealed interface SendTextResult {
    data class Queued(val commandId: CommandId) : SendTextResult
    data class Unavailable(val availability: Availability) : SendTextResult
    data object InvalidRoute : SendTextResult
    data object InvalidReply : SendTextResult
}
interface UnifiedCommunication {
    /** Newest activity first; empty chats last, stable route tie breaking. */
    fun observeUnifiedInbox(): Flow<List<UnifiedInboxEntry>>
    /** Oldest message first. Original IDs, routes, security and content are retained. */
    fun observeTimeline(target: ConversationTarget): Flow<UnifiedTimeline>
    suspend fun sendRoutes(target: ConversationTarget, text: String, replyTo: MessageId? = null): List<RouteAvailability>
    /** Explicit route required. Queued means local durable ownership, not successful delivery. */
    suspend fun sendText(target: ConversationTarget, route: SendRoute, text: String, replyTo: MessageId? = null): SendTextResult
}
