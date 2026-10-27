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
        UnifiedTimeline(routes, s.messages.filter { it.route in refs }.sortedWith(Message.timelineOrder))
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
            textLength = text.length, targetKind = route.conversation.kind))
        if (textSupport !is Availability.Available || reply == null) return textSupport
        return session.evaluate(OperationRequest(scope, Operations.Reply,
            textLength = text.length, targetKind = route.conversation.kind))
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
