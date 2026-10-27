package org.orbiliq.core.model

import java.time.Instant

/** Open, versioned operation IDs. Unknown operations never grant permission. */
data class OperationKey(val name: String, val schemaVersion: Int = 1) {
    init { require(name.isNotBlank()); require(schemaVersion > 0) }
}
object Operations {
    val SendText = OperationKey("message.text.send")
    val ReceiveText = OperationKey("message.text.receive")
    val Reply = OperationKey("message.quoted-reply.send")
    val EditText = OperationKey("message.text.edit")
    val DeleteSelf = OperationKey("message.remote.deleteSelf")
    val DeleteAll = OperationKey("message.remote.deleteAll")
    val React = OperationKey("reaction.add")
    val ReadReceipt = OperationKey("receipt.read.send")
    val Typing = OperationKey("typing.send")
    val History = OperationKey("history.page-back")
    val CreateGroup = OperationKey("group.create")
    val Preview = OperationKey("notification.preview")
    val QuickReply = OperationKey("notification.quick-reply")
    fun sendMedia(kind: AttachmentKind) = OperationKey("message.media.${kind.name.lowercase()}.send")
}
sealed interface CapabilityScope {
    val providerId: ProviderId
    data class Provider(override val providerId: ProviderId) : CapabilityScope
    data class Account(override val providerId: ProviderId, val accountId: AccountId) : CapabilityScope
    data class Conversation(override val providerId: ProviderId, val ref: ConversationRef) : CapabilityScope
    data class Message(override val providerId: ProviderId, val ref: MessageRef) : CapabilityScope
}
enum class Restriction { Offline, PermissionDenied, AuthRequired, Policy, Expired, UpdateRequired, RateLimited }
enum class Maturity { Experimental, Verified }
enum class ReplayClass { Durable, SinceCheckpoint, BestEffort }
data class OperationConstraints(
    val maxTextLength: Int? = null, val maxBytes: Long? = null,
    val mimeTypes: Set<String>? = null, val targetKinds: Set<ConversationKind>? = null,
    val deadline: Instant? = null, val persistentOfflineQueue: Boolean = false,
    /** A client that does not understand these must fail closed. */
    val unknownRequirements: Set<String> = emptySet(),
) {
    init { require(maxTextLength == null || maxTextLength >= 0); require(maxBytes == null || maxBytes >= 0) }
    fun permits(request: OperationRequest, now: Instant): Boolean =
        unknownRequirements.isEmpty() &&
            (deadline == null || now < deadline) &&
            (maxTextLength == null || request.textLength?.let { it <= maxTextLength } == true) &&
            (maxBytes == null || request.sizeBytes?.let { it <= maxBytes } == true) &&
            (mimeTypes == null || request.mimeType?.let { it in mimeTypes } == true) &&
            (targetKinds == null || request.targetKind?.let { it in targetKinds } == true) &&
            (!request.offlineQueue || persistentOfflineQueue)
}
sealed interface Availability {
    data class Available(val constraints: OperationConstraints = OperationConstraints()) : Availability
    data class Restricted(val reason: Restriction, val retryAt: Instant? = null) : Availability
    data class Unsupported(val code: String) : Availability
    data object Unknown : Availability
}
data class OperationSupport(
    val availability: Availability, val maturity: Maturity = Maturity.Experimental,
    val quality: DataQuality = DataQuality.PreviewOnly,
    val replay: ReplayClass = ReplayClass.BestEffort,
    val security: SecurityContext = SecurityContext(),
)
data class OperationRequest(
    val scope: CapabilityScope, val operation: OperationKey,
    val textLength: Int? = null, val sizeBytes: Long? = null, val mimeType: String? = null,
    val targetKind: ConversationKind? = null, val offlineQueue: Boolean = false,
) { init { require(textLength == null || textLength >= 0); require(sizeBytes == null || sizeBytes >= 0) } }
data class CapabilitySnapshot(
    val scope: CapabilityScope, val revision: String, val evaluatedAt: Instant,
    val expiresAt: Instant, val operations: Map<OperationKey, OperationSupport>,
) {
    init { require(revision.isNotBlank()); require(expiresAt > evaluatedAt) }
}

/** Requires the exact complete ancestry, in order. A child cannot elevate its parent.
 * Each constraint is checked independently; no lossy generic constraint merge.
 */
object CapabilityPolicy {
    fun evaluate(request: OperationRequest, snapshots: List<CapabilitySnapshot>, now: Instant): Availability {
        val scope = request.scope
        val ancestry = buildList {
            add(CapabilityScope.Provider(scope.providerId))
            when (scope) {
                is CapabilityScope.Provider -> Unit
                is CapabilityScope.Account -> add(scope)
                is CapabilityScope.Conversation -> {
                    add(CapabilityScope.Account(scope.providerId, scope.ref.accountId)); add(scope)
                }
                is CapabilityScope.Message -> {
                    add(CapabilityScope.Account(scope.providerId, scope.ref.conversation.accountId))
                    add(CapabilityScope.Conversation(scope.providerId, scope.ref.conversation)); add(scope)
                }
            }
        }
        if (snapshots.map { it.scope } != ancestry) return Availability.Unknown
        for (snapshot in snapshots) {
            if (now < snapshot.evaluatedAt || now >= snapshot.expiresAt) return Availability.Unknown
            val support = snapshot.operations[request.operation] ?: return Availability.Unsupported("operation.unknown")
            val availability = support.availability
            if (availability !is Availability.Available) return availability
            if (!availability.constraints.permits(request, now)) return Availability.Restricted(Restriction.Policy)
        }
        // Consumers must evaluate the original snapshots for every new request.
        return Availability.Available()
    }
}
