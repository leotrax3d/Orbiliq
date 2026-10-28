package org.orbiliq.core.data.storage

import java.nio.file.Files
import java.time.Instant
import javax.crypto.KeyGenerator
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

class PersistenceTest {
    private val account = Account(AccountId("a"), ProviderId("test"), "Synthetic account")
    private val identity = MessengerIdentity(IdentityId("self"), IdentityRef(account.id, "self"), "Synthetic person")
    private val route = ConversationRef(account.id, "chat")
    private val conversation = Conversation(ConversationId("c"), route, "Synthetic chat", ConversationKind.Direct)
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private fun message(content: MessageContent = MessageContent.Text("synthetic private text")) = Message(
        MessageId("m"), conversation.id, route, identity.ref, content, MessageDirection.Incoming, now,
        ref = MessageRef(route, "remote-message"), extensions = listOf(ProviderData(account.providerId, "message", 7, byteArrayOf(9, 8))))
    private var sequence = 0
    private fun event(payload: ProviderEvent): EventEnvelope {
        sequence++
        return EventEnvelope("event-$sequence", ReplayCursor(account.id, "stream", "epoch", "$sequence"), now, now, payload)
    }
    private class Backend : CiphertextStorage {
        var bytes: ByteArray? = null
        var fail = false
        override fun read() = bytes?.copyOf()
        override fun write(bytes: ByteArray) { if(fail) error("Synthetic storage failure"); this.bytes = bytes.copyOf() }
        override fun close() {}
    }
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val backend = Backend()
    private fun repository() = PersistentLocalRepository(EncryptedSnapshotStorage(backend, BlobCipher { key }))
    private suspend fun seed(r: LocalRepository) {
        r.saveAccount(account); r.apply(event(ProviderEvent.IdentityChanged(identity)))
        r.apply(event(ProviderEvent.ConversationChanged(conversation)))
    }
    private fun command(payload: CommandPayload = CommandPayload.SendText("synthetic outgoing")) = ProviderCommand(
        CommandId("command"), route, payload, now, now.plusSeconds(300), "1", idempotencyKey = "synthetic-key")

    @Test fun `native queued echo is accepted and later failure cannot be overwritten by dispatch response`() = runTest {
        val r = repository(); seed(r)
        val command = command()
        val optimistic = message().copy(id = MessageId(command.id.value), direction = MessageDirection.Outgoing, ref = null, delivery = DeliveryState.Pending)
        r.enqueue(command, optimistic)
        r.updateOperation(command.id, PendingStatus.Dispatching)
        val queued = message().copy(direction = MessageDirection.Outgoing, delivery = DeliveryState.Pending)
        r.apply(event(ProviderEvent.MessageUpserted(queued, command.id)))
        assertEquals(PendingStatus.Accepted, r.snapshot().pending.single().status)
        assertEquals(1, r.snapshot().messages.size)
        assertEquals(MessageId(command.id.value), r.snapshot().messages.single().id)
        r.apply(event(ProviderEvent.MessageUpserted(queued.copy(delivery = DeliveryState.Failed), command.id)))
        r.updateOperation(command.id, PendingStatus.Accepted, receipt = "late-response")
        assertEquals(PendingStatus.Rejected, r.snapshot().pending.single().status)
        assertEquals(DeliveryState.Failed, r.snapshot().messages.single().delivery)
        assertEquals(PendingStatus.Rejected, repository().snapshot().pending.single().status)
    }
    @Test fun `native deletion before queue response removes optimistic copy without resurrecting it`() = runTest {
        val r = repository(); seed(r)
        val command = command()
        r.enqueue(command, message().copy(id = MessageId(command.id.value), direction = MessageDirection.Outgoing, ref = null))
        r.updateOperation(command.id, PendingStatus.Dispatching)
        val wrong = MessageRef(ConversationRef(account.id, "foreign-route"), "remote")
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { r.apply(event(ProviderEvent.MessageDeleted(wrong, null, command.id))) } }
        assertEquals(1, r.snapshot().messages.size)
        val remote = MessageRef(route, "remote")
        r.apply(event(ProviderEvent.MessageDeleted(remote, null, command.id)))
        r.updateOperation(command.id, PendingStatus.Accepted, receipt = "remote", remote = remote)
        assertTrue(r.snapshot().messages.isEmpty())
        assertTrue(repository().snapshot().messages.isEmpty())
    }

    @Test fun `reopening restores projection metadata links media checkpoints and outbox`() = runTest {
        val r = repository(); seed(r)
        val person = Person(PersonId("person"), "Synthetic Person")
        r.savePerson(person); r.link(PersonIdentityLink(person.id, identity.id, LinkOrigin.Manual))
        val attachment = Attachment(AttachmentId("image"), account.id, AttachmentKind.Image, "image/png",
            123, "synthetic.png", AttachmentRef(account.id, "file"), ContentPolicy(cacheAllowed = true))
        r.saveAttachment(attachment)
        val group = Group(GroupId("group"), GroupRef(account.id, "group"), "Synthetic Group",
            setOf(GroupMember(identity.id, GroupRole.Owner)))
        r.apply(event(ProviderEvent.GroupChanged(group)))
        r.apply(event(ProviderEvent.MessageUpserted(message(MessageContent.Media(listOf(attachment.id), "caption")))))
        r.saveMetadata(ProviderData(account.providerId, "opaque", 2, byteArrayOf(1, 2, 3)))
        r.enqueue(command()); r.setRetention(RetentionPolicy(90, 10, 12345)); r.archive(conversation.id, true)
        val reopened = repository(); val s = reopened.snapshot()
        assertEquals(listOf(account), s.accounts); assertEquals(listOf(attachment), s.attachments)
        assertEquals(person, reopened.observePeople().first().single())
        assertEquals(identity, reopened.observeIdentities(person.id).first().single())
        assertEquals("caption", (s.messages.single().content as MessageContent.Media).caption)
        assertArrayEquals(byteArrayOf(9, 8), s.messages.single().extensions.single().payload())
        assertArrayEquals(byteArrayOf(1, 2, 3), s.metadata.single().payload())
        assertEquals("4", s.checkpoints.single().cursor.token)
        assertEquals(group, s.groups.single())
        assertEquals(command(), s.pending.single().command)
        assertEquals(RetentionPolicy(90, 10, 12345), s.retention)
        assertTrue(reopened.observeInbox().first().isEmpty())
        assertFalse(String(checkNotNull(backend.bytes)).contains("Synthetic"))
    }
    @Test fun `failed commit publishes neither message nor cursor`() = runTest {
        val r = repository(); seed(r); val previous = checkNotNull(backend.bytes).copyOf()
        backend.fail = true
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { r.apply(event(ProviderEvent.MessageUpserted(message()))) } }
        assertTrue(r.snapshot().messages.isEmpty()); assertEquals("2", r.snapshot().checkpoints.single().cursor.token)
        assertArrayEquals(previous, backend.bytes); assertTrue(repository().snapshot().messages.isEmpty())
    }
    @Test fun `replay is idempotent and delete receipt reaction survive reopening`() = runTest {
        val r = repository(); seed(r)
        val envelope = event(ProviderEvent.MessageUpserted(message()))
        r.apply(envelope); r.apply(envelope); assertEquals(1, r.snapshot().messages.size)
        r.apply(event(ProviderEvent.ReceiptChanged(message().ref!!, DeliveryState.Read)))
        r.apply(event(ProviderEvent.ReactionChanged(message().ref!!, identity.ref, "ok", false)))
        assertEquals(DeliveryState.Read, repository().snapshot().messages.single().delivery)
        assertEquals("ok", repository().snapshot().reactions.single().reaction)
        r.apply(event(ProviderEvent.MessageDeleted(message().ref!!, "2")))
        assertTrue(repository().snapshot().messages.isEmpty()); assertTrue(repository().snapshot().reactions.isEmpty())
    }
    @Test fun `interrupted dispatch becomes unknown and accepted receipt survives`() = runTest {
        val r = repository(); seed(r); r.enqueue(command())
        r.updateOperation(command().id, PendingStatus.Dispatching)
        assertEquals(PendingStatus.Dispatching, r.snapshot().pending.single().status)
        val reopened = repository()
        assertEquals(PendingStatus.OutcomeUnknown, reopened.snapshot().pending.single().status)
        reopened.updateOperation(command().id, PendingStatus.Accepted, receipt = "synthetic-receipt")
        assertEquals("synthetic-receipt", repository().snapshot().pending.single().receipt)
    }
    @Test fun `merge and unlink persist without changing routes`() = runTest {
        val r = repository(); seed(r)
        val a = Person(PersonId("p1"), "A"); val b = Person(PersonId("p2"), "B")
        r.savePerson(a); r.savePerson(b); r.link(PersonIdentityLink(a.id, identity.id, LinkOrigin.Manual)); r.merge(a.id, b.id)
        val reopened = repository(); assertEquals(b.id, reopened.snapshot().links.single().personId)
        reopened.unlink(identity.id); assertTrue(repository().snapshot().links.isEmpty())
        assertEquals(route, repository().snapshot().conversations.single().ref)
    }
    @Test fun `scope collisions fail before modifying storage`() = runTest {
        val r = repository(); seed(r)
        val other = Account(AccountId("other"), account.providerId, "Other"); r.saveAccount(other)
        val foreign = conversation.copy(ref = ConversationRef(other.id, "other"))
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { r.apply(EventEnvelope("bad", ReplayCursor(other.id, "stream", "epoch", "1"), now, now, ProviderEvent.ConversationChanged(foreign))) } }
        assertEquals(route, repository().snapshot().conversations.single().ref)
    }
    @Test fun `foreign command correlation and attachment ID reassignment fail atomically`() = runTest {
        val r = repository(); seed(r); r.enqueue(command())
        val other = account.copy(id = AccountId("other")); r.saveAccount(other)
        val otherRoute = ConversationRef(other.id, "chat")
        val otherConversation = conversation.copy(id = ConversationId("other-c"), ref = otherRoute)
        r.apply(EventEnvelope("other-conversation", ReplayCursor(other.id, "stream", "epoch", "1"), now, now,
            ProviderEvent.ConversationChanged(otherConversation)))
        val foreign = message().copy(id = MessageId("other-m"), conversationId = otherConversation.id,
            route = otherRoute, sender = IdentityRef(other.id, "self"), ref = MessageRef(otherRoute, "remote"))
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking {
            r.apply(EventEnvelope("foreign", ReplayCursor(other.id, "stream", "epoch", "2"), now, now,
                ProviderEvent.MessageUpserted(foreign, command().id)))
        } }
        assertTrue(r.snapshot().messages.isEmpty())
        assertEquals(PendingStatus.Queued, r.snapshot().pending.single().status)
        val attachment = Attachment(AttachmentId("same-file"), account.id, AttachmentKind.File, "application/octet-stream")
        r.saveAttachment(attachment)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { r.saveAttachment(attachment.copy(accountId = other.id)) } }
        assertEquals(account.id, repository().snapshot().attachments.single().accountId)
    }
    @Test fun `retention protects archives and unsent operations but honors remote expiry`() = runTest {
        val r = repository(); seed(r); r.apply(event(ProviderEvent.MessageUpserted(message())))
        r.setRetention(RetentionPolicy(messageDays = 1)); r.archive(conversation.id, true)
        r.prune(now.plusSeconds(172800)); assertEquals(1, r.snapshot().messages.size)
        r.archive(conversation.id, false); r.enqueue(command()); r.prune(now.plusSeconds(172800)); assertEquals(1, r.snapshot().messages.size)
        r.apply(event(ProviderEvent.MessageUpserted(message().copy(policy = ContentPolicy(expiresAt = now.plusSeconds(60))))))
        r.prune(now.plusSeconds(120)); assertTrue(repository().snapshot().messages.isEmpty())
    }
    @Test fun `old nonarchived completed history is pruned`() = runTest {
        val r = repository(); seed(r); r.apply(event(ProviderEvent.MessageUpserted(message())))
        r.enqueue(command()); r.updateOperation(command().id, PendingStatus.Confirmed)
        r.setRetention(RetentionPolicy(messageDays = 1)); r.prune(now.plusSeconds(172800))
        assertTrue(repository().snapshot().messages.isEmpty())
        assertTrue(repository().snapshot().pending.isEmpty())
    }
    @Test fun `cipher rejects tampering wrong key and wrong purpose`() {
        val cipher = BlobCipher { key }; val bytes = cipher.encrypt("synthetic-secret".toByteArray(), "vault")
        assertArrayEquals("synthetic-secret".toByteArray(), cipher.decrypt(bytes, "vault"))
        assertFalse(bytes.contentEquals(cipher.encrypt("synthetic-secret".toByteArray(), "vault")))
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(bytes, "projection") }
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(AEADBadTagException::class.java) { BlobCipher { other }.decrypt(bytes, "vault") }
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(bytes, "vault") }
    }
    @Test fun `all content and command variants round trip through explicit codec`() {
        val data = ProviderData(account.providerId, "test", 1, byteArrayOf(4))
        val ref = message().ref!!
        val contents = listOf(MessageContent.Text("test"), MessageContent.Media(listOf(AttachmentId("media"))),
            MessageContent.Service("joined", setOf(identity.id)), MessageContent.Unsupported(MessageType("future"), 3, "Unknown", data))
        val payloads = listOf(CommandPayload.SendText("test", ref), CommandPayload.SendMedia(AttachmentId("media"), AttachmentKind.Video, "caption"),
            CommandPayload.EditText(ref, "new", "2"), CommandPayload.Delete(ref, true), CommandPayload.React(ref, "ok"),
            CommandPayload.MarkRead(ref), CommandPayload.SetTyping(true), CommandPayload.Logout,
            CommandPayload.Extension(OperationKey("test.extension", 5), data))
        val state = LocalState(messages = contents.mapIndexed { n, c -> message(c).copy(id = MessageId("m$n")) },
            pending = payloads.mapIndexed { n, p -> PendingOperation(command(p).copy(id = CommandId("c$n"))) })
        val decoded = StoreCodec.decode(StoreCodec.encode(state))
        assertEquals(contents.map { it.type }, decoded.messages.map { it.content.type })
        assertEquals(payloads.map { it.operation }, decoded.pending.map { it.command.payload.operation })
        assertArrayEquals(byteArrayOf(4), (decoded.messages.last().content as MessageContent.Unsupported).data!!.payload())
        val second = StoreCodec.encode(decoded)
        assertArrayEquals(StoreCodec.encode(state), second)
    }
    @Test fun `unsupported schema and malformed data fail without resetting storage`() = runTest {
        val r = repository(); seed(r)
        val original = checkNotNull(backend.bytes).copyOf()
        val bad = event(ProviderEvent.MessageUpserted(message())).copy(schemaVersion = 99)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { r.apply(bad) } }
        assertArrayEquals(original, backend.bytes)
        backend.bytes = byteArrayOf(1, 2)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repository().snapshot() } }
        assertArrayEquals(byteArrayOf(1, 2), backend.bytes)
    }
    @Test fun `version one fixture migrates account without destructive reset`() {
        // Fixed independently encoded v1 fixture: one account, ten empty collections.
        val fixture = java.util.Base64.getDecoder().decode("AAAAAQAAAAEAAAABYQAAAAR0ZXN0AAAAA0FjYwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        val migrated = StoreCodec.decode(fixture)
        assertEquals(Account(AccountId("a"), ProviderId("test"), "Acc"), migrated.accounts.single())
        assertEquals(RetentionPolicy(), migrated.retention)
        assertTrue(migrated.archived.isEmpty()); assertTrue(migrated.reactions.isEmpty())
        assertEquals(migrated.accounts, StoreCodec.decode(StoreCodec.encode(migrated)).accounts)
    }
    @Test fun `version two fixture retains custom retention and archive`() {
        val fixture = java.util.Base64.getDecoder().decode("AAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAcAAAADAAAAAAAABAAAAAABAAAADmxlZ2FjeS1hcmNoaXZl")
        val state = StoreCodec.decode(fixture)
        assertEquals(RetentionPolicy(7, 3, 1024), state.retention)
        assertEquals(setOf(ConversationId("legacy-archive")), state.archived)
        assertTrue(state.reactions.isEmpty())
        assertEquals(state.retention, StoreCodec.decode(StoreCodec.encode(state)).retention)
    }
    @Test fun `disk reopen uses encrypted bytes only`() = runTest {
        val file = Files.createTempFile("orbiliq-synthetic-", ".db")
        try {
            fun open() = PersistentLocalRepository(EncryptedSnapshotStorage(object : CiphertextStorage {
                override fun read(): ByteArray? = Files.readAllBytes(file).takeIf { it.isNotEmpty() }
                override fun write(bytes: ByteArray) { Files.write(file, bytes) }
                override fun close() {}
            }, BlobCipher { key }))
            val r = open(); seed(r); r.apply(event(ProviderEvent.MessageUpserted(message())))
            assertEquals(message().content, open().snapshot().messages.single().content)
            assertFalse(String(Files.readAllBytes(file)).contains("synthetic private text"))
        } finally { Files.deleteIfExists(file) }
    }
}
