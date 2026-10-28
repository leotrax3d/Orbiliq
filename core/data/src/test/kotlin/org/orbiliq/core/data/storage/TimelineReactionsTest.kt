package org.orbiliq.core.data.storage

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.domain.ConversationTarget
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*
import java.time.Instant

class TimelineReactionsTest {
    private object Memory : SnapshotStorage {
        var bytes: ByteArray? = null
        override fun read() = bytes?.copyOf()
        override fun write(bytes: ByteArray) { this.bytes = bytes.copyOf() }
        override fun close() {}
    }
    private val account = AccountId("synthetic")
    private val route = ConversationRef(account, "7")
    private var sequence = 0
    private suspend fun PersistentLocalRepository.event(payload: ProviderEvent) =
        apply(EventEnvelope("${++sequence}", ReplayCursor(account, "s", "1", "$sequence"), Instant.EPOCH, Instant.EPOCH, payload))

    @Test fun `timeline counts received reactions per message and drops removed ones`() = runTest {
        Memory.bytes = null
        val r = PersistentLocalRepository(Memory)
        r.saveAccount(Account(account, ProviderId("synthetic"), "Synthetic"))
        r.event(ProviderEvent.ConversationChanged(Conversation(ConversationId("c"), route, "Fixture", ConversationKind.Direct)))
        val ref = MessageRef(route, "11")
        val sender = IdentityRef(account, "a")
        r.event(ProviderEvent.MessageUpserted(Message(MessageId("m"), ConversationId("c"), route, sender, MessageContent.Text("x"),
            MessageDirection.Incoming, Instant.EPOCH, ref = ref)))
        r.event(ProviderEvent.ReactionChanged(ref, sender, "👍", false))
        r.event(ProviderEvent.ReactionChanged(ref, IdentityRef(account, "b"), "👍", false))
        r.event(ProviderEvent.ReactionChanged(ref, IdentityRef(account, "c"), "❤", false))
        r.event(ProviderEvent.ReactionChanged(ref, IdentityRef(account, "c"), "❤", true))
        val timeline = UnifiedCommunicationService(r, { null }).observeTimeline(ConversationTarget.ProviderChat(route)).first()
        assertEquals(mapOf(MessageId("m") to mapOf("👍" to 2)), timeline.reactions)
    }
}
