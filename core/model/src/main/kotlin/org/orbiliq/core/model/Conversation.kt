package org.orbiliq.core.model

@JvmInline
value class ProviderId(val value: String) {
    init { require(value.isNotBlank()) { "Provider ID must not be blank" } }
}

data class ConversationRef(val accountId: String, val remoteConversationId: String)
data class ConversationSummary(val ref: ConversationRef, val title: String)
