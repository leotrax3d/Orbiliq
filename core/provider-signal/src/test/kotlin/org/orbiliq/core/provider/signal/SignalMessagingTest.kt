package org.orbiliq.core.provider.signal

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

class SignalMessagingTest {
    private val binding = SignalBinding("ab".repeat(32))
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private class Peer(private val initial: SignalBinding) : SignalMessagingTransport {
        @Volatile var current: SignalBinding? = initial
        val rows = java.util.concurrent.ConcurrentHashMap<Long, SignalText>()
        var sendCalls = 0
        var unknown = false
        override suspend fun binding() = current
        override suspend fun threads(binding: SignalBinding) = listOf(SignalThread(7, "Fixture", true, true, "9"))
        override suspend fun texts(binding: SignalBinding, thread: Long, beforeId: Long) = SignalTextPage(rows.values.filter { it.thread == thread && it.id < beforeId }.toList(), null)
        override suspend fun lookup(binding: SignalBinding, thread: Long, ids: List<Long>) = ids.mapNotNull { rows[it]?.takeIf { row -> row.thread == thread } }
        override suspend fun sendText(binding: SignalBinding, commandId: String, thread: Long, text: String): SignalSendResult {
            sendCalls++
            return if (unknown) SignalSendResult.Unknown else SignalSendResult.Queued(22)
        }
        override suspend fun reconcileText(binding: SignalBinding, commandId: String, thread: Long): SignalSendResult =
            if (unknown) SignalSendResult.Unknown else SignalSendResult.Queued(22)
    }
    private fun text(id: Long = 11, delivery: String = "Sent", outgoing: Boolean = false) =
        SignalText(id, 7, if (outgoing) "self" else "9", "private fixture", outgoing, now.toEpochMilli(), now.toEpochMilli(), delivery)
    private suspend fun running(peer: Peer, block: suspend (SignalMessagingSession, MutableList<EventEnvelope>) -> Unit) = coroutineScope {
        val session = SignalMessagingSession(binding, peer, clock = clock, pollMillis = 10)
        val events = CopyOnWriteArrayList<EventEnvelope>()
        val collector = launch { session.events().collect { events += it } }
        try {
            withTimeout(5000) { session.state.first { it.sync == SyncState.Gap } }
            block(session, events)
        } finally { collector.cancelAndJoin(); session.close() }
    }
    private fun scope(session: SignalMessagingSession) = CapabilityScope.Conversation(ProviderId("signal"), ConversationRef(session.accountId, "7"))
    private suspend fun command(session: SignalMessagingSession, payload: CommandPayload = CommandPayload.SendText("hello")) =
        ProviderCommand(CommandId("fixture-command"), scope(session).ref, payload, now, now.plusSeconds(60), session.observeCapabilities(scope(session)).first().revision)

    @Test fun textCapabilitiesDoNotPromiseMediaGroupsHistoryOrReplies() = runBlocking {
        running(Peer(binding)) { session, _ ->
            val caps = session.observeCapabilities(scope(session)).first()
            assertEquals(setOf(Operations.SendText, Operations.ReceiveText), caps.operations.keys)
            assertTrue(session.evaluate(OperationRequest(scope(session), Operations.SendText, textLength = 4001)) is Availability.Restricted)
            assertTrue(session.evaluate(OperationRequest(scope(session), Operations.Reply, textLength = 2)) is Availability.Unsupported)
            assertEquals(ReplayClass.BestEffort, caps.operations.getValue(Operations.ReceiveText).replay)
        }
    }
    @Test fun snapshotReplaysAreDeduplicatedAndMissingMessagesBecomeDeletions() = runBlocking {
        val peer = Peer(binding).apply { rows[11] = text() }
        running(peer) { session, events ->
            withTimeout(5000) { while (events.none { it.payload is ProviderEvent.MessageUpserted }) delay(10) }
            delay(60)
            assertEquals(1, events.count { it.payload is ProviderEvent.MessageUpserted })
            peer.rows.clear()
            withTimeout(5000) { while (events.none { it.payload is ProviderEvent.MessageDeleted }) delay(10) }
            val cursor = events.last().cursor
            assertTrue(session.acknowledge(cursor) is OperationResult.Success)
            assertTrue(session.acknowledge(cursor.copy(epoch = "foreign")) is OperationResult.Rejected)
        }
    }
    @Test fun nativeQueueAcceptanceIsNotDeliveryConfirmation() = runBlocking {
        val peer = Peer(binding).apply { rows[22] = text(22, "Pending", true) }
        running(peer) { session, _ ->
            val command = command(session)
            assertTrue(session.execute(command) is DispatchResult.Accepted)
            assertEquals(ReconcileResult.StillUnknown, session.reconcile(ReconcileRequest(command.id, command.route)))
            peer.rows[22] = text(22, "Sent", true)
            assertTrue(session.reconcile(ReconcileRequest(command.id, command.route)) is ReconcileResult.Confirmed)
            assertEquals(1, peer.sendCalls)
        }
    }
    @Test fun uncertainNativeEffectIsNeverBlindlyResentDuringReconciliation() = runBlocking {
        val peer = Peer(binding).apply { unknown = true }
        running(peer) { session, _ ->
            val command = command(session)
            assertTrue(session.execute(command) is DispatchResult.OutcomeUnknown)
            repeat(3) { assertEquals(ReconcileResult.StillUnknown, session.reconcile(ReconcileRequest(command.id, command.route))) }
            assertEquals(1, peer.sendCalls)
        }
    }
    @Test fun stalePolicyAndForeignRoutesAreRejectedBeforeTransport() = runBlocking {
        val peer = Peer(binding)
        running(peer) { session, _ ->
            val command = command(session)
            assertTrue(session.execute(command.copy(capabilityRevision = "stale")) is DispatchResult.Rejected)
            assertTrue(session.execute(command.copy(route = ConversationRef(AccountId("foreign"), "7"))) is DispatchResult.Rejected)
            assertTrue(session.execute(command.copy(createdAt = now.minusSeconds(60), expiresAt = now)) is DispatchResult.Rejected)
            assertEquals(0, peer.sendCalls)
        }
    }
    @Test fun bindingChangeBlocksOldAccountAndClearsCapabilities() = runBlocking {
        val peer = Peer(binding)
        running(peer) { session, _ ->
            peer.current = SignalBinding("cd".repeat(32))
            withTimeout(5000) { session.state.first { it.auth == AuthState.Blocked } }
            assertTrue(session.observeCapabilities(scope(session)).first().operations.isEmpty())
            assertEquals(0, peer.sendCalls)
        }
    }
    @Test fun wireValuesDoNotExposePrivateContentsThroughDiagnostics() {
        assertEquals("SignalBinding(redacted)", binding.toString())
        assertEquals("SignalText(redacted)", text().toString())
        assertFalse(SignalThread(7, "private contact", true, true, "9").toString().contains("private"))
    }
}
