package org.orbiliq.core.provider.mock

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.data.storage.*
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*

class MessengerRepositoryTest {
    private class Memory : SnapshotStorage {
        var bytes: ByteArray? = null
        override fun close() {}
        override fun read() = bytes?.copyOf()
        override fun write(bytes: ByteArray) { this.bytes = bytes.copyOf() }
    }
    @Test fun `UI projection exposes contacts accounts and reversible persistent archive`() = runTest {
        val storage = Memory()
        val local = PersistentLocalRepository(storage)
        val wrapper = PersistentMockRepository(backgroundScope) { local }
        val first = wrapper.observeMessenger().first()
        assertEquals(2, first.entries.size)
        assertTrue(first.people.isNotEmpty())
        assertTrue(first.identities.isNotEmpty())
        assertEquals(MockFixtures.account.id, first.accounts.single().id)
        val chat = first.entries.first().route.conversation
        wrapper.archiveConversation(chat.id, true)
        assertTrue(chat.id in wrapper.observeMessenger().first().archived)
        assertTrue(wrapper.observeUnifiedInbox().first().none { it.route.conversation.id == chat.id })
        assertTrue(chat.id in PersistentLocalRepository(storage).snapshot().archived)
        wrapper.archiveConversation(chat.id, false)
        assertEquals(2, wrapper.observeUnifiedInbox().first().size)
    }
    @Test fun `UI text and reply use durable outbox and retain original route`() = runTest {
        val local = PersistentLocalRepository(Memory())
        val wrapper = PersistentMockRepository(backgroundScope) { local }
        val route = wrapper.observeMessenger().first().entries.first { it.route.conversation.kind == ConversationKind.Direct }.route
        val target = ConversationTarget.ProviderChat(route.conversation.ref)
        val original = wrapper.observeTimeline(target).first().messages.first { it.ref != null }
        val result = wrapper.sendText(target, route, "Synthetic UI reply", original.id)
        assertTrue(result is SendTextResult.Queued)
        val message = wrapper.observeTimeline(target).first().messages.single { it.id.value == (result as SendTextResult.Queued).commandId.value }
        assertEquals("Synthetic UI reply", (message.content as MessageContent.Text).text)
        assertEquals(original.id, message.replyTo)
        assertEquals(route.conversation.ref, message.route)
        assertEquals(1, local.snapshot().pending.size)
        wrapper.sync().drain()
        assertEquals(PendingStatus.Confirmed, local.snapshot().pending.single().status)
    }
    @Test fun `read only provider rejects UI send without adding optimistic message`() = runTest {
        val local = PersistentLocalRepository(Memory())
        val wrapper = PersistentMockRepository(backgroundScope) { local }
        val route = wrapper.observeMessenger().first().entries.first().route
        wrapper.session().setProfile(MockProfile.ReadOnly)
        val before = local.snapshot().messages
        val result = wrapper.sendText(ConversationTarget.ProviderChat(route.conversation.ref), route, "not allowed")
        assertTrue(result is SendTextResult.Unavailable)
        assertEquals(before, local.snapshot().messages)
        assertTrue(local.snapshot().pending.isEmpty())
    }
}
