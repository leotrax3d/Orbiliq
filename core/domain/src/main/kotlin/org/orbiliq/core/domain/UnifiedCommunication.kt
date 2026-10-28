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
/** [reactions] summarises received reactions per message as emoji -> count; display only. */
data class UnifiedTimeline(val routes: List<SendRoute>, val messages: List<Message>, val reactions: Map<MessageId, Map<String, Int>> = emptyMap())
class LoadedAttachment(val mimeType: String, val bytes: ByteArray) { override fun toString() = "LoadedAttachment(redacted)" }
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
    /** Only true when the message's own provider currently offers reaction sending for it. */
    suspend fun canReact(message: MessageId): Boolean = false
    /** Queues a reaction in the durable outbox; true means queued, not delivered. */
    suspend fun react(message: MessageId, emoji: String): Boolean = false
    /** Image bytes for display only (never persisted by Orbiliq); null when unavailable. */
    suspend fun loadImage(message: MessageId, attachment: AttachmentId): ByteArray? = null
    /** Provider-declared kind of an attachment id, or null when unknown. */
    fun attachmentKind(attachment: AttachmentId): AttachmentKind? = null
    /** Bytes for in-memory playback or an explicit user save; never persisted by Orbiliq. */
    suspend fun loadAttachment(message: MessageId, attachment: AttachmentId): LoadedAttachment? = null
    suspend fun canSendFile(route: SendRoute): Boolean = false
    /** Sends a picked document unchanged; images and videos are refused on this path. */
    suspend fun sendFile(target: ConversationTarget, route: SendRoute, bytes: ByteArray, mimeType: String, name: String?, caption: String?): SendTextResult =
        SendTextResult.Unavailable(Availability.Unsupported("media.unsupported"))
    /** Whether [route] currently accepts metadata-free JPEG images. */
    suspend fun canSendImage(route: SendRoute): Boolean = false
    /** [jpeg] must already be re-encoded without metadata; Queued means durable ownership, not delivery. */
    suspend fun sendImage(target: ConversationTarget, route: SendRoute, jpeg: ByteArray, caption: String?): SendTextResult =
        SendTextResult.Unavailable(Availability.Unsupported("media.unsupported"))
}
