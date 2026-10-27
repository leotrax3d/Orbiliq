package org.orbiliq.core.provider.api

import org.orbiliq.core.model.*
import java.time.Instant

data class ReplayCursor(val accountId: AccountId, val streamId: String, val epoch: String, val token: String) {
    init { require(streamId.isNotBlank()); require(epoch.isNotBlank()); require(token.isNotBlank()) }
}
sealed interface ProviderEvent {
    data class MessageUpserted(val message: Message, val commandId: CommandId? = null) : ProviderEvent
    data class MessageDeleted(val ref: MessageRef, val revision: String?) : ProviderEvent
    data class ConversationChanged(val conversation: Conversation) : ProviderEvent
    data class IdentityChanged(val identity: MessengerIdentity) : ProviderEvent
    data class GroupChanged(val group: Group) : ProviderEvent
    data class CapabilitiesChanged(val snapshot: CapabilitySnapshot) : ProviderEvent
    data class ReceiptChanged(val message: MessageRef, val state: DeliveryState) : ProviderEvent
    data class ReactionChanged(val message: MessageRef, val actor: IdentityRef, val reaction: String, val removed: Boolean) : ProviderEvent {
        init { require(actor.accountId == message.conversation.accountId) }
    }
    data class TypingChanged(val conversation: ConversationRef, val actor: IdentityRef, val active: Boolean) : ProviderEvent {
        init { require(actor.accountId == conversation.accountId) }
    }
    data class GapDetected(val reasonCode: String) : ProviderEvent
    data class AuthChanged(val state: AuthState) : ProviderEvent
    data class Extension(val data: ProviderData) : ProviderEvent
}
data class EventEnvelope(
    val eventId: String, val cursor: ReplayCursor, val occurredAt: Instant?, val receivedAt: Instant,
    val payload: ProviderEvent, val providerRevision: String? = null, val schemaVersion: Int = 1,
) {
    init {
        require(eventId.isNotBlank()); require(schemaVersion > 0)
        val account = when (payload) {
            is ProviderEvent.MessageUpserted -> payload.message.route.accountId
            is ProviderEvent.MessageDeleted -> payload.ref.conversation.accountId
            is ProviderEvent.ConversationChanged -> payload.conversation.ref.accountId
            is ProviderEvent.IdentityChanged -> payload.identity.ref.accountId
            is ProviderEvent.GroupChanged -> payload.group.ref.accountId
            is ProviderEvent.ReceiptChanged -> payload.message.conversation.accountId
            is ProviderEvent.TypingChanged -> payload.conversation.accountId
            is ProviderEvent.ReactionChanged -> payload.message.conversation.accountId
            is ProviderEvent.CapabilitiesChanged -> when (val scope = payload.snapshot.scope) {
                is CapabilityScope.Account -> scope.accountId
                is CapabilityScope.Conversation -> scope.ref.accountId
                is CapabilityScope.Message -> scope.ref.conversation.accountId
                is CapabilityScope.Provider -> null
            }
            else -> null
        }
        require(account == null || account == cursor.accountId) { "Cross-account event" }
    }
}
