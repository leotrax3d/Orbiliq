package org.orbiliq.core.model

import java.util.UUID

@JvmInline value class ProviderId(val value: String) {
    init { require(value.isNotBlank()) }
}
@JvmInline value class PersonId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun random() = PersonId(UUID.randomUUID().toString()) }
}
@JvmInline value class IdentityId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun random() = IdentityId(UUID.randomUUID().toString()) }
}
@JvmInline value class AccountId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun random() = AccountId(UUID.randomUUID().toString()) }
}
@JvmInline value class ConversationId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun random() = ConversationId(UUID.randomUUID().toString()) }
}
@JvmInline value class MessageId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun random() = MessageId(UUID.randomUUID().toString()) }
}
@JvmInline value class AttachmentId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun random() = AttachmentId(UUID.randomUUID().toString()) }
}
@JvmInline value class GroupId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun random() = GroupId(UUID.randomUUID().toString()) }
}
@JvmInline value class CommandId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object { fun random() = CommandId(UUID.randomUUID().toString()) }
}

/** Remote identifiers are opaque and must never be parsed, trimmed or case-folded. */
data class IdentityRef(val accountId: AccountId, val remoteIdentityId: String) {
    init { require(remoteIdentityId.isNotBlank()) }
}
data class ConversationRef(val accountId: AccountId, val remoteConversationId: String) {
    init { require(remoteConversationId.isNotBlank()) }
}
data class MessageRef(val conversation: ConversationRef, val remoteMessageId: String) {
    init { require(remoteMessageId.isNotBlank()) }
}
data class AttachmentRef(val accountId: AccountId, val remoteAttachmentId: String) {
    init { require(remoteAttachmentId.isNotBlank()) }
}
data class GroupRef(val accountId: AccountId, val remoteGroupId: String) {
    init { require(remoteGroupId.isNotBlank()) }
}

/** Persistence implementations must bind atomically and reject alias reassignment.
 * Multiple aliases may resolve to one stable local ID, including temporary/final IDs.
 * Account and conversation scope is part of the key; payload equality is never an ID.
 */
interface IdMapping<Remote : Any, Local : Any> {
    suspend fun resolve(remote: Remote): Local?
    suspend fun aliases(local: Local): Set<Remote>
    suspend fun bind(remote: Remote, local: Local)
}
