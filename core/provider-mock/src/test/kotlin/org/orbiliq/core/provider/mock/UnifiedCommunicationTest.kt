package org.orbiliq.core.provider.mock

import java.time.Duration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.data.storage.*
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

@OptIn(ExperimentalCoroutinesApi::class)
class UnifiedCommunicationTest {
    private class Disk : SnapshotStorage {
        private var bytes: ByteArray? = null
        override fun read() = bytes?.copyOf()
        override fun write(bytes: ByteArray) { this.bytes = bytes.copyOf() }
        override fun close() {}
    }
    private class Fixture {
        val disk = Disk()
        val local = PersistentLocalRepository(disk)
        val person = Person(PersonId("person-anna"), "Anna")
        val target = ConversationTarget.PersonChat(person.id)
        val sessions = (1..3).map { index ->
            val provider = ProviderId("mock.messenger.$index")
            val account = Account(AccountId("account-$index"), provider, "Mock $index", IdentityId("account-$index:self"))
            MockProviderFactory(MockConfiguration(requireLogin = false), provider).createSession(account)
        }
        val service = UnifiedCommunicationService(local, { id -> sessions.find { it.accountId == id } }, { sessions.first().currentTime })
        suspend fun initialize() {
            sessions.forEach { session ->
                local.saveAccount(session.account)
                session.recordedEvents.forEach { local.apply(it) }
            }
            local.savePerson(person)
            sessions.forEach { local.link(PersonIdentityLink(person.id, IdentityId("${it.accountId.value}:anna"), LinkOrigin.Manual)) }
        }
        suspend fun apply(session: MockProviderSession) {
            // Apply only events after the current committed checkpoint.
            val position = local.snapshot().checkpoints.find { it.cursor.accountId == session.accountId }?.cursor?.token?.toLong() ?: 0
            session.recordedEvents.filter { it.cursor.token.toLong() > position }.forEach { local.apply(it) }
        }
    }

    @Test fun `three messenger inbox orders activity and timelines preserve all origins`() = runTest {
        val f = Fixture(); f.initialize()
        val timeline = f.service.observeTimeline(f.target).first()
        assertEquals(3, timeline.routes.size)
        assertEquals(3, timeline.messages.size)
        assertEquals(f.sessions.map { it.directRoute }.toSet(), timeline.messages.map { it.route }.toSet())
        assertEquals(timeline.messages.sortedWith(Message.timelineOrder), timeline.messages)
        assertEquals(6, f.service.observeUnifiedInbox().first().size)
        val latest = f.sessions.last()
        latest.advanceBy(Duration.ofSeconds(10)); latest.receiveText(text = "latest"); f.apply(latest)
        assertEquals(latest.directRoute, f.service.observeUnifiedInbox().first().first().route.conversation.ref)
        assertEquals(latest.directRoute, f.local.observeInbox().first().first().ref)
        assertEquals(2, f.service.observeTimeline(ConversationTarget.ProviderChat(latest.directRoute)).first().messages.size)
        assertEquals("latest", (f.service.observeTimeline(f.target).first().messages.last().content as MessageContent.Text).text)
        val archive = latest.conversations.value.single { it.ref == latest.directRoute }.id
        f.local.archive(archive, true)
        assertEquals(5, f.service.observeUnifiedInbox().first().size)
        assertEquals(4, f.service.observeTimeline(f.target).first().messages.size)
    }

    @Test fun `selected messenger receives durable send and other sessions stay unchanged`() = runTest {
        val f = Fixture(); f.initialize()
        val route = f.service.observeTimeline(f.target).first().routes[1]
        val selected = f.sessions[1]
        val before = f.sessions.map { it.messages.value.size }
        val result = f.service.sendText(f.target, route, "chosen") as SendTextResult.Queued
        assertEquals(route.conversation.ref, f.local.snapshot().pending.single().command.route)
        val optimistic = f.service.observeTimeline(f.target).first().messages.single { it.id == MessageId(result.commandId.value) }
        assertEquals(DeliveryState.Pending, optimistic.delivery)
        val reopened = PersistentLocalRepository(f.disk)
        assertEquals(result.commandId, reopened.snapshot().pending.single().command.id)
        SyncCoordinator(reopened, selected, now = { selected.currentTime }).drain()
        val checkpoint = reopened.snapshot().checkpoints.single { it.cursor.accountId == selected.accountId }.cursor.token.toLong()
        selected.recordedEvents.filter { it.cursor.token.toLong() > checkpoint }.forEach { reopened.apply(it) }
        assertEquals(before[0], f.sessions[0].messages.value.size)
        assertEquals(before[1] + 1, selected.messages.value.size)
        assertEquals(before[2], f.sessions[2].messages.value.size)
        val sent = reopened.snapshot().messages.single { it.id == optimistic.id }
        assertEquals(route.conversation.ref, sent.route)
        assertNotNull(sent.ref)
        assertEquals(DeliveryState.Sent, sent.delivery)
    }

    @Test fun `capabilities and cross messenger replies never fall back to another route`() = runTest {
        val f = Fixture(); f.initialize()
        f.sessions[1].setProfile(MockProfile.TextOnly)
        f.sessions[2].setProfile(MockProfile.ReadOnly)
        val routes = f.service.sendRoutes(f.target, "hello")
        assertTrue(routes[0].availability is Availability.Available)
        assertTrue(routes[1].availability is Availability.Available)
        assertTrue(routes[2].availability is Availability.Unsupported)
        assertTrue(f.service.sendText(f.target, routes[2].route, "blocked") is SendTextResult.Unavailable)
        val messages = f.service.observeTimeline(f.target).first().messages
        val firstReply = messages.single { it.route == routes[0].route.conversation.ref }.id
        assertEquals(SendTextResult.InvalidReply, f.service.sendText(f.target, routes[1].route, "wrong route", firstReply))
        val secondReply = messages.single { it.route == routes[1].route.conversation.ref }.id
        assertTrue(f.service.sendText(f.target, routes[1].route, "unsupported reply", secondReply) is SendTextResult.Unavailable)
        assertTrue(f.service.sendText(f.target, routes[0].route, "x".repeat(4097)) is SendTextResult.Unavailable)
        f.sessions[0].setOnline(false)
        assertTrue(f.service.sendText(f.target, routes[0].route, "offline") is SendTextResult.Unavailable)
        assertTrue(f.local.snapshot().pending.isEmpty())
        val forged = routes[0].route.copy(providerId = routes[1].route.providerId)
        assertEquals(SendTextResult.InvalidRoute, f.service.sendText(f.target, forged, "wrong provider"))
    }

    @Test fun `unlink updates active person timeline without changing stored messages`() = runTest {
        val f = Fixture(); f.initialize()
        val emissions = mutableListOf<UnifiedTimeline>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.service.observeTimeline(f.target).toList(emissions) }
        val messages = f.local.snapshot().messages
        val stale = emissions.last().routes.first()
        f.local.unlink(IdentityId("account-1:anna"))
        assertEquals(2, emissions.last().routes.size)
        assertEquals(messages, f.local.snapshot().messages)
        assertEquals(SendTextResult.InvalidRoute, f.service.sendText(f.target, stale, "stale selection"))
        assertEquals(6, f.service.observeUnifiedInbox().first().size)
        val restored = UnifiedCommunicationService(PersistentLocalRepository(f.disk), { null })
        assertEquals(2, restored.observeTimeline(f.target).first().routes.size)
        assertEquals(Availability.Unknown, restored.sendRoutes(f.target, "unavailable").first().availability)
    }

    @Test fun `membership change during capability evaluation is rejected atomically at enqueue`() = runTest {
        val f = Fixture(); f.initialize()
        val session = f.sessions.first()
        val proxy = object : ProviderSession by session {
            override suspend fun evaluate(request: OperationRequest): Availability {
                f.local.unlink(IdentityId("account-1:anna"))
                return session.evaluate(request)
            }
        }
        val service = UnifiedCommunicationService(f.local, { proxy }, { session.currentTime })
        val route = service.observeTimeline(f.target).first().routes.first()
        assertEquals(SendTextResult.InvalidRoute, service.sendText(f.target, route, "changed"))
        assertTrue(f.local.snapshot().pending.isEmpty())
    }

    @Test fun `empty chats remain visible with stable order and no name based merging`() = runTest {
        val f = Fixture(); f.initialize()
        val s = f.sessions.first()
        val original = s.conversations.value.first()
        val empty = original.copy(id = ConversationId("empty"), ref = ConversationRef(s.accountId, "empty"), participants = emptySet())
        val cursor = f.local.snapshot().checkpoints.single { it.cursor.accountId == s.accountId }.cursor
        f.local.apply(EventEnvelope("empty", cursor.copy(token = "999"), s.currentTime, s.currentTime, ProviderEvent.ConversationChanged(empty)))
        val inbox = f.service.observeUnifiedInbox().first()
        assertEquals(empty.ref, inbox.last().route.conversation.ref)
        assertNull(inbox.last().latestMessage)
        assertEquals(3, f.service.observeTimeline(f.target).first().routes.size)
        val unlinkedPerson = Person(PersonId("same-name"), "Anna")
        f.local.savePerson(unlinkedPerson)
        assertTrue(f.service.observeTimeline(ConversationTarget.PersonChat(unlinkedPerson.id)).first().messages.isEmpty())
    }
}
