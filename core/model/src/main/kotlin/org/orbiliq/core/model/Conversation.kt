package org.orbiliq.core.model

data class ConversationSummary(val ref: ConversationRef, val title: String)

enum class ConversationKind { Direct, Group, Channel, Topic, Saved }
data class Conversation(
    val id: ConversationId,
    val ref: ConversationRef,
    val title: String,
    val kind: ConversationKind,
    val participants: Set<IdentityId> = emptySet(),
    val groupId: GroupId? = null,
    val parentId: ConversationId? = null,
    val security: SecurityContext = SecurityContext(),
    val extensions: List<ProviderData> = emptyList(),
) {
    init { require(kind != ConversationKind.Group || groupId != null) }
}
