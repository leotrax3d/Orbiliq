package org.orbiliq.core.provider.mock

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.data.storage.*
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

class SyncEngineTest {
    private class Disk : SnapshotStorage {
        var bytes: ByteArray? = null
        override fun read() = bytes?.copyOf()
        override fun write(bytes: ByteArray) { this.bytes = bytes.copyOf() }
        override fun close() {}
    }
    private suspend fun setup(): Triple<Disk, PersistentLocalRepository, MockProviderSession> {
        val disk = Disk(); val local = PersistentLocalRepository(disk)
        val session = MockProviderFactory(MockConfiguration(requireLogin = false)).createSession()
        local.saveAccount(session.account); session.recordedEvents.forEach { local.apply(it) }
        return Triple(disk, local, session)
    }
    private suspend fun enqueue(local: LocalRepository, session: MockProviderSession): ProviderCommand {
        val command = session.newCommand(CommandPayload.SendText("synthetic queued text"))
        val chat = local.snapshot().conversations.single { it.ref == command.route }
        local.enqueue(command, Message(MessageId(command.id.value), chat.id, chat.ref, session.self,
            MessageContent.Text("synthetic queued text"), MessageDirection.Outgoing, session.currentTime))
        return command
    }
    @Test fun `offline queue survives reopen and reconciles optimistic message without duplicate`() = runBlocking {
        val (disk, local, original) = setup()
        val command = enqueue(local, original)
        original.close()
        val reopened = PersistentLocalRepository(disk)
        val session = MockProviderFactory(MockConfiguration(requireLogin = false)).createSession()
        session.restore(reopened.snapshot())
        session.setOnline(false)
        val engine = SyncCoordinator(reopened, session) { session.currentTime }
        engine.drain()
        assertEquals(PendingStatus.Queued, reopened.snapshot().pending.single().status)
        session.setOnline(true)
        engine.drain()
        session.recordedEvents.forEach { reopened.apply(it) }
        engine.drain()
        val result = reopened.snapshot()
        assertEquals(PendingStatus.Confirmed, result.pending.single().status)
        val message = result.messages.single { it.id == MessageId(command.id.value) }
        assertNotNull(message.ref)
        assertEquals(1, result.messages.count { (it.content as? MessageContent.Text)?.text == "synthetic queued text" })
        session.close()
    }
    @Test fun `lost response is reconciled before retry and never sent twice`() = runBlocking {
        val (_, local, session) = setup(); enqueue(local, session)
        session.failNext(MockFault.UnknownAfterCommit)
        val engine = SyncCoordinator(local, session) { session.currentTime }
        engine.drain(); assertEquals(PendingStatus.OutcomeUnknown, local.snapshot().pending.single().status)
        engine.drain(); engine.drain()
        assertEquals(PendingStatus.Confirmed, local.snapshot().pending.single().status)
        assertEquals(1, local.snapshot().pending.single().attempts)
        assertEquals(1, session.messages.value.count { (it.content as? MessageContent.Text)?.text == "synthetic queued text" })
        session.close()
    }
    @Test fun `interrupted process with lost provider history stays visibly unknown`() = runBlocking {
        val (disk, local, session) = setup(); val command = enqueue(local, session)
        local.updateOperation(command.id, PendingStatus.Dispatching)
        session.execute(command); session.close()
        val reopened = PersistentLocalRepository(disk)
        val next = MockProviderFactory(MockConfiguration(requireLogin = false)).createSession()
        next.restore(reopened.snapshot())
        val engine = SyncCoordinator(reopened, next) { next.currentTime }
        engine.drain(); engine.drain()
        assertEquals(PendingStatus.OutcomeUnknown, reopened.snapshot().pending.single().status)
        assertEquals(1, reopened.snapshot().pending.single().attempts)
        next.close()
    }
    @Test fun `network failure persists backoff across process restart`() = runBlocking {
        val (disk, local, session) = setup(); enqueue(local, session)
        val failing = object : ProviderSession by session {
            override suspend fun execute(command: ProviderCommand) = DispatchResult.RetryableNoEffect(ProviderFailure(FailureCategory.NetworkUnavailable))
        }
        SyncCoordinator(local, failing) { session.currentTime }.drain()
        val reopened = PersistentLocalRepository(disk)
        SyncCoordinator(reopened, session) { session.currentTime }.drain()
        val op = reopened.snapshot().pending.single()
        assertEquals(1, op.attempts); assertNotNull(op.retryAt)
        assertEquals(FailureCategory.NetworkUnavailable, op.failure)
        session.advanceBy(java.time.Duration.ofSeconds(2))
        SyncCoordinator(reopened, session) { session.currentTime }.drain()
        assertEquals(PendingStatus.Confirmed, reopened.snapshot().pending.single().status)
        session.close()
    }
    @Test fun `running engine receives events after network switch`() = runBlocking {
        val (_, local, session) = setup()
        val engine = SyncCoordinator(local, session) { session.currentTime }
        val job = launch { engine.run() }
        try {
            session.setOnline(false)
            session.receiveText(text = "synthetic reconnect input")
            session.setOnline(true)
            withTimeout(5000) {
                local.observeState().first { state -> state.messages.any {
                    (it.content as? MessageContent.Text)?.text == "synthetic reconnect input"
                } }
            }
        } finally { job.cancelAndJoin(); session.close() }
        Unit
    }
    @Test fun `conflict rejection remains persisted after reopen`() = runBlocking {
        val (disk, local, session) = setup(); enqueue(local, session)
        val conflict = object : ProviderSession by session {
            override suspend fun execute(command: ProviderCommand) = DispatchResult.Rejected(ProviderFailure(FailureCategory.SyncConflict))
        }
        SyncCoordinator(local, conflict) { session.currentTime }.drain()
        val op = PersistentLocalRepository(disk).snapshot().pending.single()
        assertEquals(PendingStatus.Rejected, op.status)
        assertEquals(FailureCategory.SyncConflict, op.failure)
        session.close()
    }

    @Test fun `transport timeout preserves unknown effect for reconciliation`() = kotlinx.coroutines.test.runTest {
        val (disk, local, session) = setup(); enqueue(local, session)
        val stalled = object : ProviderSession by session {
            override suspend fun execute(command: ProviderCommand): DispatchResult = awaitCancellation()
        }
        SyncCoordinator(local, stalled, timeoutMillis = 10) { session.currentTime }.drain()
        assertEquals(PendingStatus.OutcomeUnknown, PersistentLocalRepository(disk).snapshot().pending.single().status)
        session.close()
    }

}
