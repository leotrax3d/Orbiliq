package org.orbiliq.core.model

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DomainModelTest {
    private val a = AccountId("a")
    private val route = ConversationRef(a, "chat")
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private fun message(id: String = "m", ref: MessageRef? = null) = Message(
        MessageId(id), ConversationId("c"), route, IdentityRef(a, "sender"),
        MessageContent.Text("hello"), MessageDirection.Incoming, now, ref = ref,
    )
    private fun invalid(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java, block)
    }
    @Test fun allLocalIdsRejectBlankValues() {
        listOf<(String) -> Any>(::PersonId, ::IdentityId, ::AccountId, ::ConversationId,
            ::MessageId, ::AttachmentId, ::GroupId, ::CommandId).forEach { constructor ->
            invalid { constructor("  ") }
        }
    }
    @Test fun generatedIdsAreUniqueAndRoundTrip() {
        val ids = (1..1000).map { MessageId.random() }
        assertEquals(1000, ids.toSet().size)
        assertEquals(ids.first(), MessageId(ids.first().value))
    }
    @Test fun remoteIdsRemainOpaqueAndScoped() {
        assertNotEquals(IdentityRef(a, "A"), IdentityRef(a, "a"))
        assertEquals(" 001 ", IdentityRef(a, " 001 ").remoteIdentityId)
        assertNotEquals(route, ConversationRef(AccountId("b"), "chat"))
        assertNotEquals(MessageRef(route, "1"), MessageRef(ConversationRef(a, "other"), "1"))
        invalid { MessageRef(route, "") }
        invalid { IdentityRef(a, " ") }
        invalid { GroupRef(a, "") }
        invalid { AttachmentRef(a, "") }
    }
    @Test fun temporaryAndConfirmedReferencesKeepLocalMessageId() = runTest {
        val index = StableIdIndex<MessageRef, MessageId>()
        val local = message().id
        val temporary = MessageRef(route, "temporary")
        val confirmed = MessageRef(route, "server")
        index.bind(temporary, local); index.bind(confirmed, local); index.bind(confirmed, local)
        assertEquals(local, index.resolve(confirmed))
        assertEquals(setOf(temporary, confirmed), index.aliases(local))
        assertEquals(local, message(ref = confirmed).id)
    }
    @Test fun mappingsRejectAliasHijackingAndPreserveOriginal() = runTest {
        val index = StableIdIndex<MessageRef, MessageId>()
        val ref = MessageRef(route, "1")
        index.bind(ref, MessageId("first"))
        try { index.bind(ref, MessageId("second")); fail("must reject") } catch (_: IllegalArgumentException) { }
        assertEquals(MessageId("first"), index.resolve(ref))
        assertNull(index.resolve(MessageRef(ConversationRef(AccountId("b"), "chat"), "1")))
        assertTrue(index.aliases(MessageId("absent")).isEmpty())
    }
    @Test fun identitiesOfOnePersonRetainSeparateAccounts() {
        val person = Person(PersonId("p"), "Anna")
        val first = MessengerIdentity(IdentityId("i1"), IdentityRef(a, "42"), "Anna")
        val second = MessengerIdentity(IdentityId("i2"), IdentityRef(AccountId("b"), "42"), "Anna")
        val links = setOf(first, second).map { PersonIdentityLink(person.id, it.id, LinkOrigin.Manual) }
        assertEquals(2, links.size)
        assertNotEquals(first.ref, second.ref)
        assertEquals(message().route, message().copy(sender = first.ref).route)
    }
    @Test fun crossAccountAndCrossConversationReferencesAreRejected() {
        invalid { message().copy(sender = IdentityRef(AccountId("b"), "sender")) }
        invalid { message().copy(ref = MessageRef(ConversationRef(a, "wrong"), "m")) }
        invalid { Attachment(AttachmentId("x"), a, AttachmentKind.File, "text/plain", ref = AttachmentRef(AccountId("b"), "x")) }
    }
    @Test fun unknownTypesAndOpaqueProviderDataSurvive() {
        val bytes = byteArrayOf(1, 2, 3)
        val data = ProviderData(ProviderId("future"), "org.example.poll", 2, bytes)
        bytes[0] = 9
        val copy = data.payload(); copy[1] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), data.payload())
        val content = MessageContent.Unsupported(MessageType("org.example.poll"), 2, "Poll", data)
        assertSame(data, (message().copy(content = content).content as MessageContent.Unsupported).data)
        assertFalse(data.toString().contains("1, 2, 3"))
        invalid { ProviderData(ProviderId("x"), "", 1, bytes) }
        invalid { ProviderData(ProviderId("x"), "namespace", 0, bytes) }
    }
    @Test fun attachmentAndContentValidation() {
        invalid { Attachment(AttachmentId("x"), a, AttachmentKind.File, "text/plain", -1) }
        invalid { MessageContent.Text("") }
        invalid { MessageContent.Media(emptyList()) }
        invalid { MessageContent.Media(listOf(AttachmentId("x"), AttachmentId("x"))) }
        invalid { MessageContent.Unsupported(MessageType("x"), 0, "x") }
        val voice = Attachment(AttachmentId("v"), a, AttachmentKind.VoiceNote, "audio/ogg", 0)
        assertEquals(AttachmentKind.VoiceNote, voice.kind)
        assertFalse(voice.policy.exportAllowed)
        assertFalse(voice.policy.cacheAllowed)
    }
    @Test fun groupsAndTopicsHaveDistinctStableIdentity() {
        invalid { Conversation(ConversationId("c"), route, "Group", ConversationKind.Group) }
        val group = Group(GroupId("g"), GroupRef(a, "g"), "Group")
        val conversation = Conversation(ConversationId("c"), route, "Group", ConversationKind.Group, groupId = group.id)
        val topic = Conversation(ConversationId("t"), ConversationRef(a, "topic"), "Topic", ConversationKind.Topic, parentId = conversation.id)
        assertEquals(conversation.id, topic.parentId)
    }
    @Test fun timelineOrderIsDeterministicAndPreservesSecurity() {
        val first = message("a").copy(security = SecurityContext(Encryption.EndToEnd, Verification.Verified))
        val second = message("b").copy(security = SecurityContext(Encryption.Transport))
        assertEquals(listOf(first, second), listOf(second, first).sortedWith(Message.timelineOrder))
        assertEquals(Encryption.EndToEnd, first.security.encryption)
        assertEquals(Encryption.Transport, second.security.encryption)
        assertEquals(now.minusSeconds(1), first.copy(serverTime = now.minusSeconds(1)).displayTime)
    }
}
