package org.orbiliq.core.provider.api

import org.orbiliq.core.model.*
import java.time.Instant

sealed interface CommandPayload {
    val operation: OperationKey
    data class SendText(val text: String, val replyTo: MessageRef? = null) : CommandPayload {
        init { require(text.isNotEmpty()) }
        override val operation = if (replyTo == null) Operations.SendText else Operations.Reply
    }
    data class SendMedia(val attachment: AttachmentId, val kind: AttachmentKind, val caption: String? = null) : CommandPayload {
        override val operation = Operations.sendMedia(kind)
    }
    data class EditText(val message: MessageRef, val text: String, val expectedRevision: String?) : CommandPayload {
        init { require(text.isNotEmpty()) }
        override val operation = Operations.EditText
    }
    data class Delete(val message: MessageRef, val forEveryone: Boolean) : CommandPayload {
        override val operation = if (forEveryone) Operations.DeleteAll else Operations.DeleteSelf
    }
    data class React(val message: MessageRef, val reaction: String) : CommandPayload {
        init { require(reaction.isNotBlank()) }
        override val operation = Operations.React
    }
    data class MarkRead(val through: MessageRef) : CommandPayload { override val operation = Operations.ReadReceipt }
    data class SetTyping(val active: Boolean) : CommandPayload { override val operation = Operations.Typing }
    data object Logout : CommandPayload { override val operation = OperationKey("account.logout") }
    /** Only explicitly registered, understood extensions may be dispatched. */
    data class Extension(override val operation: OperationKey, val data: ProviderData) : CommandPayload
}
data class ProviderCommand(
    val id: CommandId, val route: ConversationRef, val payload: CommandPayload,
    val createdAt: Instant, val expiresAt: Instant, val capabilityRevision: String,
    val dependencies: Set<CommandId> = emptySet(), val idempotencyKey: String? = null,
    val schemaVersion: Int = 1,
) {
    init {
        require(schemaVersion > 0); require(expiresAt > createdAt); require(capabilityRevision.isNotBlank())
        require(id !in dependencies); require(idempotencyKey == null || idempotencyKey.isNotBlank())
        val target = when (payload) {
            is CommandPayload.SendText -> payload.replyTo
            is CommandPayload.EditText -> payload.message
            is CommandPayload.Delete -> payload.message
            is CommandPayload.React -> payload.message
            is CommandPayload.MarkRead -> payload.through
            else -> null
        }
        require(target == null || target.conversation == route) { "Cross-route command reference" }
    }
}
