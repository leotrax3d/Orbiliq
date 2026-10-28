package org.orbiliq.core.data.storage

import java.time.Instant
import kotlinx.coroutines.flow.*
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

/** Sessions are borrowed from the app's account registry; their sync coordinators drain the outbox. */
class UnifiedCommunicationService(
    private val repository: LocalRepository,
    private val sessionFor: (AccountId) -> ProviderSession?,
    private val now: () -> Instant = Instant::now,
) : UnifiedCommunication {
    override fun observeUnifiedInbox(): Flow<List<UnifiedInboxEntry>> = repository.observeState().map { s ->
        unifiedInbox(s)
    }.distinctUntilChanged()

    override fun observeTimeline(target: ConversationTarget): Flow<UnifiedTimeline> = repository.observeState().map { s ->
        val routes = communicationRoutes(s, target)
        val refs = routes.map { it.conversation.ref }.toSet()
        val messages = s.messages.filter { it.route in refs }.sortedWith(Message.timelineOrder)
        val byRef = messages.filter { it.ref != null }.associateBy { it.ref }
        val reactions = s.reactions.mapNotNull { r -> byRef[r.message]?.let { it.id to r.reaction } }
            .groupBy({ it.first }, { it.second }).mapValues { (_, emoji) -> emoji.groupingBy { it }.eachCount() }
        UnifiedTimeline(routes, messages, reactions)
    }.distinctUntilChanged()

    override suspend fun sendRoutes(target: ConversationTarget, text: String, replyTo: MessageId?): List<RouteAvailability> {
        require(text.isNotEmpty())
        val s = repository.snapshot()
        return communicationRoutes(s, target).map { route ->
            val reply = replyTo?.let { id -> s.messages.find { it.id == id } }
            val availability = if (replyTo != null && (reply?.route != route.conversation.ref || reply.ref == null))
                Availability.Restricted(Restriction.Policy)
            else evaluate(route, text, reply?.ref)
            RouteAvailability(route, availability)
        }
    }

    private suspend fun evaluate(route: SendRoute, text: String, reply: MessageRef?): Availability {
        val session = sessionFor(route.conversation.ref.accountId) ?: return Availability.Unknown
        if (session.accountId != route.conversation.ref.accountId) return Availability.Unknown
        val scope = CapabilityScope.Conversation(route.providerId, route.conversation.ref)
        val textSupport = session.evaluate(OperationRequest(scope, Operations.SendText,
            textLength = text.length, sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong(), targetKind = route.conversation.kind))
        if (textSupport !is Availability.Available || reply == null) return textSupport
        return session.evaluate(OperationRequest(scope, Operations.Reply,
            textLength = text.length, sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong(), targetKind = route.conversation.kind))
    }

    private suspend fun reactionScope(message: MessageId): Pair<ProviderSession, CapabilityScope.Message>? {
        val s = repository.snapshot()
        val ref = s.messages.find { it.id == message }?.ref ?: return null
        val provider = s.accounts.find { it.id == ref.conversation.accountId }?.providerId ?: return null
        val session = sessionFor(ref.conversation.accountId)?.takeIf { it.accountId == ref.conversation.accountId } ?: return null
        return session to CapabilityScope.Message(provider, ref)
    }
    override suspend fun canReact(message: MessageId): Boolean {
        val (session, scope) = reactionScope(message) ?: return false
        return session.evaluate(OperationRequest(scope, Operations.React)) is Availability.Available
    }
    override suspend fun react(message: MessageId, emoji: String): Boolean {
        require(emoji.isNotBlank() && emoji.length <= 32)
        val (session, scope) = reactionScope(message) ?: return false
        if (session.evaluate(OperationRequest(scope, Operations.React)) !is Availability.Available) return false
        val revision = session.observeCapabilities(scope).first().revision
        val time = now()
        repository.enqueue(ProviderCommand(CommandId.random(), scope.ref.conversation, CommandPayload.React(scope.ref, emoji),
            time, time.plusSeconds(300), revision), null)
        return true
    }

    suspend fun imageAvailability(route: SendRoute, caption: String?): Availability = mediaAvailability(route, caption, AttachmentKind.Image)
    suspend fun mediaAvailability(route: SendRoute, caption: String?, kind: AttachmentKind): Availability {
        val session = sessionFor(route.conversation.ref.accountId)?.takeIf { it.accountId == route.conversation.ref.accountId } ?: return Availability.Unknown
        val text = caption.orEmpty()
        return session.evaluate(OperationRequest(CapabilityScope.Conversation(route.providerId, route.conversation.ref), Operations.sendMedia(kind),
            textLength = text.length, sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong(),
            mimeType = if (kind == AttachmentKind.Image) "image/jpeg" else null, targetKind = route.conversation.kind))
    }
    /** Enqueues an image already staged by the route's provider under [attachment]. */
    suspend fun sendStagedImage(target: ConversationTarget, route: SendRoute, attachment: AttachmentId, caption: String?): SendTextResult =
        sendStaged(target, route, attachment, AttachmentKind.Image, caption)
    suspend fun sendStaged(target: ConversationTarget, route: SendRoute, attachment: AttachmentId, kind: AttachmentKind, caption: String?): SendTextResult {
        val s = repository.snapshot()
        val current = communicationRoutes(s, target).find {
            it.providerId == route.providerId && it.conversation.ref == route.conversation.ref
        } ?: return SendTextResult.InvalidRoute
        val availability = mediaAvailability(current, caption, kind)
        if (availability !is Availability.Available) return SendTextResult.Unavailable(availability)
        val session = sessionFor(current.conversation.ref.accountId) ?: return SendTextResult.Unavailable(Availability.Unknown)
        val capability = session.observeCapabilities(CapabilityScope.Conversation(current.providerId, current.conversation.ref)).first()
        val own = s.accounts.find { it.id == session.accountId }?.ownIdentityId
        val sender = s.identities.find { it.id == own }?.ref ?: return SendTextResult.Unavailable(Availability.Unknown)
        val id = CommandId.random()
        val time = now()
        val command = ProviderCommand(id, current.conversation.ref, CommandPayload.SendMedia(attachment, kind, caption?.ifBlank { null }),
            time, time.plusSeconds(300), capability.revision)
        val optimistic = Message(MessageId(id.value), current.conversation.id, current.conversation.ref, sender,
            MessageContent.Media(listOf(attachment), caption?.ifBlank { null }), MessageDirection.Outgoing, time, security = current.conversation.security)
        try {
            repository.enqueue(command, optimistic, (target as? ConversationTarget.PersonChat)?.personId)
        } catch (_: ContactAssignmentConflict) { return SendTextResult.InvalidRoute }
        return SendTextResult.Queued(id)
    }

    override suspend fun sendText(target: ConversationTarget, route: SendRoute, text: String, replyTo: MessageId?): SendTextResult {
        require(text.isNotEmpty())
        val s = repository.snapshot()
        val current = communicationRoutes(s, target).find {
            it.providerId == route.providerId && it.conversation.ref == route.conversation.ref
        } ?: return SendTextResult.InvalidRoute
        val reply = replyTo?.let { id -> s.messages.find { it.id == id } }
        if (replyTo != null && (reply?.route != current.conversation.ref || reply.ref == null)) return SendTextResult.InvalidReply
        val availability = evaluate(current, text, reply?.ref)
        if (availability !is Availability.Available) return SendTextResult.Unavailable(availability)
        val session = sessionFor(current.conversation.ref.accountId) ?: return SendTextResult.Unavailable(Availability.Unknown)
        val capability = session.observeCapabilities(CapabilityScope.Conversation(current.providerId, current.conversation.ref)).first()
        val own = s.accounts.find { it.id == session.accountId }?.ownIdentityId
        val sender = s.identities.find { it.id == own }?.ref ?: return SendTextResult.Unavailable(Availability.Unknown)
        val id = CommandId.random()
        val time = now()
        val command = ProviderCommand(id, current.conversation.ref, CommandPayload.SendText(text, reply?.ref),
            time, time.plusSeconds(300), capability.revision)
        val optimistic = Message(MessageId(id.value), current.conversation.id, current.conversation.ref, sender,
            MessageContent.Text(text), MessageDirection.Outgoing, time, replyTo = replyTo,
            security = current.conversation.security)
        try {
            repository.enqueue(command, optimistic, (target as? ConversationTarget.PersonChat)?.personId)
        } catch (_: ContactAssignmentConflict) { return SendTextResult.InvalidRoute }
        return SendTextResult.Queued(id)
    }
}

internal fun communicationRoutes(s: LocalState, target: ConversationTarget): List<SendRoute> {
    val identities = when (target) {
        is ConversationTarget.PersonChat -> s.links.filter { it.personId == target.personId }.map { it.identityId }.toSet()
        is ConversationTarget.ProviderChat -> emptySet()
    }
    return s.conversations.filter { c -> when (target) {
        is ConversationTarget.ProviderChat -> c.ref == target.ref
        is ConversationTarget.PersonChat -> {
            val own = s.accounts.find { it.id == c.ref.accountId }?.ownIdentityId
            val peers = c.participants - setOfNotNull(own)
            c.kind == ConversationKind.Direct && peers.size == 1 && peers.all { peer ->
                peer in identities && s.identities.any { it.id == peer && it.ref.accountId == c.ref.accountId }
            }
        }
    } }.mapNotNull { c -> s.accounts.find { it.id == c.ref.accountId }?.let { SendRoute(it.providerId, c) } }
        .sortedWith(compareBy<SendRoute> { it.providerId.value }.thenBy { it.conversation.ref.accountId.value }
            .thenBy { it.conversation.ref.remoteConversationId })
}

internal fun unifiedInbox(s: LocalState): List<UnifiedInboxEntry> {
    val latest = s.messages.groupBy { it.route }.mapValues { (_, messages) -> messages.maxWith(Message.timelineOrder) }
    return s.conversations.filter { it.id !in s.archived }.mapNotNull { c ->
        s.accounts.find { it.id == c.ref.accountId }?.let { UnifiedInboxEntry(SendRoute(it.providerId, c), latest[c.ref]) }
    }.sortedWith(compareByDescending<UnifiedInboxEntry> { it.latestMessage?.displayTime }
        .thenBy { it.route.providerId.value }.thenBy { it.route.conversation.ref.accountId.value }
        .thenBy { it.route.conversation.ref.remoteConversationId })
}
