package org.orbiliq.core.model

import java.time.Instant

/** The open type ID carries extension semantics; old clients retain Unsupported content. */
@JvmInline value class MessageType(val value: String) {
    init { require(value.isNotBlank()) }
}
sealed interface MessageContent {
    val type: MessageType
    data class Text(val text: String) : MessageContent {
        override val type = MessageType("text")
        init { require(text.isNotEmpty()) }
    }
    data class Media(val attachments: List<AttachmentId>, val caption: String? = null) : MessageContent {
        override val type = MessageType("media")
        init { require(attachments.isNotEmpty()); require(attachments.distinct().size == attachments.size) }
    }
    data class Service(val code: String, val actors: Set<IdentityId> = emptySet()) : MessageContent {
        override val type = MessageType("service")
        init { require(code.isNotBlank()) }
    }
    data class Unsupported(
        override val type: MessageType, val schemaVersion: Int,
        val description: String, val data: ProviderData? = null,
    ) : MessageContent { init { require(schemaVersion > 0) } }
}
enum class AttachmentKind { Image, Video, File, Audio, VoiceNote, Other }
data class ContentPolicy(
    val expiresAt: Instant? = null, val viewOnce: Boolean = false,
    val exportAllowed: Boolean = false, val cacheAllowed: Boolean = false,
)
data class Attachment(
    val id: AttachmentId, val accountId: AccountId, val kind: AttachmentKind,
    val mimeType: String, val sizeBytes: Long? = null, val fileName: String? = null,
    val ref: AttachmentRef? = null, val policy: ContentPolicy = ContentPolicy(),
    val extensions: List<ProviderData> = emptyList(),
) {
    init { require(mimeType.isNotBlank()); require(sizeBytes == null || sizeBytes >= 0)
        require(ref == null || ref.accountId == accountId) }
}
enum class MessageDirection { Incoming, Outgoing }
enum class DeliveryState { Pending, Accepted, Sent, Delivered, Read, Failed, OutcomeUnknown }
data class Message(
    val id: MessageId, val conversationId: ConversationId, val route: ConversationRef,
    val sender: IdentityRef, val content: MessageContent, val direction: MessageDirection,
    val receivedAt: Instant, val serverTime: Instant? = null,
    val ref: MessageRef? = null, val replyTo: MessageId? = null,
    val providerOrder: String? = null, val revision: String? = null,
    val delivery: DeliveryState = DeliveryState.Pending,
    val quality: DataQuality = DataQuality.PreviewOnly,
    val security: SecurityContext = SecurityContext(), val policy: ContentPolicy = ContentPolicy(),
    val extensions: List<ProviderData> = emptyList(),
) {
    init { require(sender.accountId == route.accountId); require(ref == null || ref.conversation == route) }
    val displayTime: Instant get() = serverTime ?: receivedAt
    companion object {
        val timelineOrder: Comparator<Message> = compareBy<Message> { it.displayTime }.thenBy { it.id.value }
    }
}
