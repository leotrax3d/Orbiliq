package org.orbiliq.core.provider.mock

import java.nio.file.Files
import javax.crypto.KeyGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.data.storage.*
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

class PersistentMockTest {
    private class Backend : CiphertextStorage {
        var bytes: ByteArray? = null
        var fail = false
        override fun read() = bytes?.copyOf()
        override fun write(bytes: ByteArray) { if(fail) error("synthetic failure"); this.bytes = bytes.copyOf() }
        override fun close() {}
    }
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun repository(backend: CiphertextStorage) = PersistentLocalRepository(EncryptedSnapshotStorage(backend, BlobCipher { key }))

    @Test fun `mock synchronizes to encrypted disk restores and keeps new IDs unique`() = runBlocking {
        val file = Files.createTempFile("orbiliq-mock-synthetic-", ".db")
        fun backend() = object : CiphertextStorage {
            override fun read() = Files.readAllBytes(file).takeIf { it.isNotEmpty() }
            override fun write(bytes: ByteArray) { Files.write(file, bytes) }
            override fun close() {}
        }
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var local = repository(backend())
            var wrapper = PersistentMockRepository(scope) { local }
            val session = wrapper.session()
            session.receiveText(text = "synthetic restart message")
            withTimeout(5000) { local.observeState().first { s -> s.messages.any { (it.content as? MessageContent.Text)?.text == "synthetic restart message" } } }
            session.receiveMedia(AttachmentKind.VoiceNote, byteArrayOf(1, 2, 3), "audio/ogg")
            withTimeout(5000) { local.observeState().first { it.attachments.isNotEmpty() && it.messages.any { m -> m.content is MessageContent.Media } } }
            local.saveAccount(session.account.copy(displayName = "Renamed synthetic account"))
            val previous = local.snapshot()
            scope.coroutineContext[Job]!!.cancelAndJoin(); session.close()
            local = repository(backend()); scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            wrapper = PersistentMockRepository(scope) { local }
            val restored = wrapper.session()
            assertEquals("Renamed synthetic account", restored.account.displayName)
            assertEquals(previous.messages, restored.messages.value)
            assertEquals(previous.conversations, restored.conversations.value)
            assertEquals(previous.attachments, restored.attachments.value)
            restored.receiveText(text = "synthetic second message")
            val current = withTimeout(5000) { local.observeState().first { it.messages.size == previous.messages.size + 1 } }
            assertEquals(current.messages.size, current.messages.map { it.id }.distinct().size)
            assertEquals(current.messages.size, current.messages.map { it.ref }.distinct().size)
            assertFalse(String(Files.readAllBytes(file)).contains("synthetic restart message"))
            scope.coroutineContext[Job]!!.cancelAndJoin(); restored.close()
            assertEquals(current.messages, repository(backend()).snapshot().messages)
        } finally { scope.cancel(); Files.deleteIfExists(file) }
    }
    @Test fun `sync commits before acknowledgement and safe dispatch stores unknown effects`() = runBlocking {
        val backend = Backend(); val local = repository(backend)
        val session = MockProviderFactory(MockConfiguration(requireLogin = false)).createSession()
        local.saveAccount(session.account)
        session.recordedEvents.forEach { local.apply(it) }
        val coordinator = SyncCoordinator(local, session)
        val command = session.newCommand(CommandPayload.SendText("synthetic send"))
        local.enqueue(command); session.failNext(MockFault.UnknownAfterCommit)
        assertTrue(coordinator.dispatch(command.id) is DispatchResult.OutcomeUnknown)
        assertEquals(PendingStatus.OutcomeUnknown, repository(backend).snapshot().pending.single().status)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { coordinator.dispatch(command.id) } }
        // Confirm using the provider's durable command correlation after ambiguous dispatch.
        val correlated = session.recordedEvents.last { (it.payload as? ProviderEvent.MessageUpserted)?.commandId == command.id }
        local.apply(correlated)
        assertEquals(PendingStatus.Confirmed, repository(backend).snapshot().pending.single().status)
        session.close()
    }
    @Test fun `acknowledgement occurs after commit and never after failed storage`() = runBlocking {
        val backend = Backend(); val local = repository(backend)
        val session = MockProviderFactory(MockConfiguration(requireLogin = false)).createSession()
        local.saveAccount(session.account)
        var acknowledgements = 0
        val envelope = session.recordedEvents.first()
        val tracking = object : ProviderSession by session {
            override fun events(after: ReplayCursor?) = kotlinx.coroutines.flow.flowOf(envelope)
            override suspend fun acknowledge(cursor: ReplayCursor): OperationResult<Unit> {
                assertEquals(cursor, repository(backend).snapshot().checkpoints.single().cursor)
                acknowledgements++
                return OperationResult.Success(Unit)
            }
        }
        backend.fail = true
        assertThrows(IllegalStateException::class.java) { runBlocking { SyncCoordinator(local, tracking).collect() } }
        assertEquals(0, acknowledgements)
        assertTrue(repository(backend).snapshot().checkpoints.isEmpty())
        backend.fail = false
        SyncCoordinator(local, tracking).collect()
        assertEquals(1, acknowledgements)
        session.close()
    }
    @Test fun `durable correlation wins over a late unknown dispatch result`() = runBlocking {
        val backend = Backend(); val local = repository(backend)
        val session = MockProviderFactory(MockConfiguration(requireLogin = false)).createSession()
        local.saveAccount(session.account); session.recordedEvents.forEach { local.apply(it) }
        val command = session.newCommand(CommandPayload.SendText("synthetic race")); local.enqueue(command)
        val racing = object : ProviderSession by session {
            override suspend fun execute(command: ProviderCommand): DispatchResult {
                session.execute(command)
                local.apply(session.recordedEvents.last())
                return DispatchResult.OutcomeUnknown()
            }
        }
        SyncCoordinator(local, racing).dispatch(command.id)
        assertEquals(PendingStatus.Confirmed, repository(backend).snapshot().pending.single().status)
        session.close()
    }
    @Test fun `cancelled dispatch remains ambiguous on reopen`() = runBlocking {
        val backend = Backend(); val local = repository(backend)
        val session = MockProviderFactory(MockConfiguration(requireLogin = false, dispatchDelayMillis = 10000)).createSession()
        local.saveAccount(session.account); session.recordedEvents.forEach { local.apply(it) }
        val command = session.newCommand(CommandPayload.SendText("synthetic send")); local.enqueue(command)
        val job = launch { SyncCoordinator(local, session).dispatch(command.id) }
        withTimeout(5000) { local.observeState().first { it.pending.single().status == PendingStatus.Dispatching } }
        job.cancelAndJoin()
        assertEquals(PendingStatus.OutcomeUnknown, repository(backend).snapshot().pending.single().status)
        session.close()
    }
}
