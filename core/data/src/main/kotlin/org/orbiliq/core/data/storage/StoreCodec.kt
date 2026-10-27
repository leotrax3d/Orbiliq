package org.orbiliq.core.data.storage

import java.io.*
import java.time.Instant
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

/** Explicit bounded wire format. Never Java object deserialization or class names from disk. */
internal object StoreCodec {
    const val VERSION = 5
    fun encode(state: LocalState): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out -> Writer(out).state(state) }
    }.toByteArray()
    fun decode(bytes: ByteArray): LocalState = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val state = Reader(input).state()
        require(input.available() == 0) { "Trailing storage data" }
        state
    }
    private class Writer(val out: DataOutputStream) {
        fun s(v: String) { val b = v.toByteArray(Charsets.UTF_8); require(b.size <= 16 * 1024 * 1024); out.writeInt(b.size); out.write(b) }
        fun i(v: Int) = out.writeInt(v)
        fun l(v: Long) = out.writeLong(v)
        fun b(v: Boolean) = out.writeBoolean(v)
        fun bytes(v: ByteArray) { require(v.size <= 16 * 1024 * 1024); i(v.size); out.write(v) }
        fun instant(v: Instant) { l(v.epochSecond); i(v.nano) }
        fun <T> nullable(v: T?, write: (T) -> Unit) { b(v != null); if(v != null) write(v) }
        fun <T> list(v: Collection<T>, write: (T) -> Unit) { require(v.size <= 100000); i(v.size); v.forEach(write) }
        fun storedReaction(v: StoredReaction) { messageRef(v.message); identityRef(v.actor); s(v.reaction) }
        fun operationKey(v: OperationKey) { s(v.name); i(v.schemaVersion) }
        fun providerData(v: ProviderData) { s(v.providerId.value); s(v.namespace); i(v.schemaVersion); bytes(v.payload()) }
        fun account(v: Account) { s(v.id.value); s(v.providerId.value); s(v.displayName); nullable(v.ownIdentityId) { s(it.value) } }
        fun person(v: Person) { s(v.id.value); s(v.displayName) }
        fun personIdentityLink(v: PersonIdentityLink) { s(v.personId.value); s(v.identityId.value); s(v.origin.name) }
        fun identityRef(v: IdentityRef) { s(v.accountId.value); s(v.remoteIdentityId) }
        fun conversationRef(v: ConversationRef) { s(v.accountId.value); s(v.remoteConversationId) }
        fun messageRef(v: MessageRef) { conversationRef(v.conversation); s(v.remoteMessageId) }
        fun attachmentRef(v: AttachmentRef) { s(v.accountId.value); s(v.remoteAttachmentId) }
        fun groupRef(v: GroupRef) { s(v.accountId.value); s(v.remoteGroupId) }
        fun messengerIdentity(v: MessengerIdentity) { s(v.id.value); identityRef(v.ref); s(v.displayName); list(v.extensions) { providerData(it) }; nullable(v.phone) { s(it.number); b(it.ownershipConfirmed) } }
        fun groupMember(v: GroupMember) { s(v.identityId.value); s(v.role.name) }
        fun group(v: Group) { s(v.id.value); groupRef(v.ref); s(v.title); list(v.members) { groupMember(it) }; list(v.extensions) { providerData(it) } }
        fun securityContext(v: SecurityContext) { s(v.encryption.name); s(v.verification.name) }
        fun contentPolicy(v: ContentPolicy) { nullable(v.expiresAt) { instant(it) }; b(v.viewOnce); b(v.exportAllowed); b(v.cacheAllowed) }
        fun conversation(v: Conversation) { s(v.id.value); conversationRef(v.ref); s(v.title); s(v.kind.name); list(v.participants) { s(it.value) }; nullable(v.groupId) { s(it.value) }; nullable(v.parentId) { s(it.value) }; securityContext(v.security); list(v.extensions) { providerData(it) } }
        fun attachment(v: Attachment) { s(v.id.value); s(v.accountId.value); s(v.kind.name); s(v.mimeType); nullable(v.sizeBytes) { l(it) }; nullable(v.fileName) { s(it) }; nullable(v.ref) { attachmentRef(it) }; contentPolicy(v.policy); list(v.extensions) { providerData(it) } }
        fun message(v: Message) { s(v.id.value); s(v.conversationId.value); conversationRef(v.route); identityRef(v.sender); messageContent(v.content); s(v.direction.name); instant(v.receivedAt); nullable(v.serverTime) { instant(it) }; nullable(v.ref) { messageRef(it) }; nullable(v.replyTo) { s(it.value) }; nullable(v.providerOrder) { s(it) }; nullable(v.revision) { s(it) }; s(v.delivery.name); s(v.quality.name); securityContext(v.security); contentPolicy(v.policy); list(v.extensions) { providerData(it) } }
        fun replayCursor(v: ReplayCursor) { s(v.accountId.value); s(v.streamId); s(v.epoch); s(v.token) }
        fun syncCheckpoint(v: SyncCheckpoint) { replayCursor(v.cursor); s(v.state.name) }
        fun providerCommand(v: ProviderCommand) { s(v.id.value); conversationRef(v.route); commandPayload(v.payload); instant(v.createdAt); instant(v.expiresAt); s(v.capabilityRevision); list(v.dependencies) { s(it.value) }; nullable(v.idempotencyKey) { s(it) }; i(v.schemaVersion) }
        fun pendingOperation(v: PendingOperation) { providerCommand(v.command); s(v.status.name); i(v.attempts); nullable(v.retryAt) { instant(it) }; nullable(v.receipt) { s(it) }; nullable(v.failure) { s(it.name) } }
        fun retentionPolicy(v: RetentionPolicy) { nullable(v.messageDays) { i(it) }; i(v.mediaDays); l(v.mediaMaxBytes) }
        fun messageContent(v: MessageContent) { when(v) {
            is MessageContent.Text -> { i(0); s(v.text) }
            is MessageContent.Media -> { i(1); list(v.attachments) { s(it.value) }; nullable(v.caption) { s(it) } }
            is MessageContent.Service -> { i(2); s(v.code); list(v.actors) { s(it.value) } }
            is MessageContent.Unsupported -> { i(3); s(v.type.value); i(v.schemaVersion); s(v.description); nullable(v.data) { providerData(it) } }
        } }
        fun commandPayload(v: CommandPayload) { when(v) {
            is CommandPayload.SendText -> { i(0); s(v.text); nullable(v.replyTo) { messageRef(it) } }
            is CommandPayload.SendMedia -> { i(1); s(v.attachment.value); s(v.kind.name); nullable(v.caption) { s(it) } }
            is CommandPayload.EditText -> { i(2); messageRef(v.message); s(v.text); nullable(v.expectedRevision) { s(it) } }
            is CommandPayload.Delete -> { i(3); messageRef(v.message); b(v.forEveryone) }
            is CommandPayload.React -> { i(4); messageRef(v.message); s(v.reaction) }
            is CommandPayload.MarkRead -> { i(5); messageRef(v.through) }
            is CommandPayload.SetTyping -> { i(6); b(v.active) }
            CommandPayload.Logout -> { i(7);  }
            is CommandPayload.Extension -> { i(8); operationKey(v.operation); providerData(v.data) }
        } }
        fun state(v: LocalState) { i(VERSION); list(v.accounts, ::account); list(v.people, ::person); list(v.identities, ::messengerIdentity); list(v.links, ::personIdentityLink); list(v.groups, ::group); list(v.conversations, ::conversation); list(v.messages, ::message); list(v.attachments, ::attachment); list(v.metadata, ::providerData); list(v.checkpoints, ::syncCheckpoint); list(v.pending, ::pendingOperation); retentionPolicy(v.retention); list(v.archived) { s(it.value) }; list(v.reactions, ::storedReaction); list(v.manualIdentities) { s(it.value) } }
    }
    private class Reader(val input: DataInputStream) {
        var version = VERSION
        fun i() = input.readInt()
        fun l() = input.readLong()
        fun b() = input.readBoolean()
        fun bytes(): ByteArray { val size = i(); require(size in 0..16 * 1024 * 1024 && size <= input.available()); return ByteArray(size).also(input::readFully) }
        fun s() = bytes().toString(Charsets.UTF_8)
        fun instant(): Instant = Instant.ofEpochSecond(l(), i().toLong())
        fun <T> nullable(read: () -> T): T? = if(b()) read() else null
        fun <T> list(read: () -> T): List<T> { val count = i(); require(count in 0..100000); return List(count) { read() } }
        fun storedReaction(): StoredReaction = StoredReaction(messageRef(), identityRef(), s())
        fun operationKey(): OperationKey = OperationKey(s(), i())
        fun providerData(): ProviderData = ProviderData(ProviderId(s()), s(), i(), bytes())
        fun account(): Account = Account(AccountId(s()), ProviderId(s()), s(), nullable { IdentityId(s()) })
        fun person(): Person = Person(PersonId(s()), s())
        fun personIdentityLink(): PersonIdentityLink = PersonIdentityLink(PersonId(s()), IdentityId(s()), LinkOrigin.valueOf(s()))
        fun identityRef(): IdentityRef = IdentityRef(AccountId(s()), s())
        fun conversationRef(): ConversationRef = ConversationRef(AccountId(s()), s())
        fun messageRef(): MessageRef = MessageRef(conversationRef(), s())
        fun attachmentRef(): AttachmentRef = AttachmentRef(AccountId(s()), s())
        fun groupRef(): GroupRef = GroupRef(AccountId(s()), s())
        fun messengerIdentity(): MessengerIdentity = MessengerIdentity(IdentityId(s()), identityRef(), s(), list { providerData() }, if(version >= 5) nullable { PhoneEvidence(s(), b()) } else null)
        fun groupMember(): GroupMember = GroupMember(IdentityId(s()), GroupRole.valueOf(s()))
        fun group(): Group = Group(GroupId(s()), groupRef(), s(), list { groupMember() }.toSet(), list { providerData() })
        fun securityContext(): SecurityContext = SecurityContext(Encryption.valueOf(s()), Verification.valueOf(s()))
        fun contentPolicy(): ContentPolicy = ContentPolicy(nullable { instant() }, b(), b(), b())
        fun conversation(): Conversation = Conversation(ConversationId(s()), conversationRef(), s(), ConversationKind.valueOf(s()), list { IdentityId(s()) }.toSet(), nullable { GroupId(s()) }, nullable { ConversationId(s()) }, securityContext(), list { providerData() })
        fun attachment(): Attachment = Attachment(AttachmentId(s()), AccountId(s()), AttachmentKind.valueOf(s()), s(), nullable { l() }, nullable { s() }, nullable { attachmentRef() }, contentPolicy(), list { providerData() })
        fun message(): Message = Message(MessageId(s()), ConversationId(s()), conversationRef(), identityRef(), messageContent(), MessageDirection.valueOf(s()), instant(), nullable { instant() }, nullable { messageRef() }, nullable { MessageId(s()) }, nullable { s() }, nullable { s() }, DeliveryState.valueOf(s()), DataQuality.valueOf(s()), securityContext(), contentPolicy(), list { providerData() })
        fun replayCursor(): ReplayCursor = ReplayCursor(AccountId(s()), s(), s(), s())
        fun syncCheckpoint(): SyncCheckpoint = SyncCheckpoint(replayCursor(), SyncState.valueOf(s()))
        fun providerCommand(): ProviderCommand = ProviderCommand(CommandId(s()), conversationRef(), commandPayload(), instant(), instant(), s(), list { CommandId(s()) }.toSet(), nullable { s() }, i())
        fun pendingOperation(): PendingOperation = PendingOperation(providerCommand(), PendingStatus.valueOf(s()), i(), nullable { instant() }, if(version >= 3) nullable { s() } else null, if(version >= 4) nullable { FailureCategory.valueOf(s()) } else null)
        fun retentionPolicy(): RetentionPolicy = RetentionPolicy(nullable { i() }, i(), l())
        fun messageContent(): MessageContent = when(i()) {
            0 -> MessageContent.Text(s())
            1 -> MessageContent.Media(list { AttachmentId(s()) }, nullable { s() })
            2 -> MessageContent.Service(s(), list { IdentityId(s()) }.toSet())
            3 -> MessageContent.Unsupported(MessageType(s()), i(), s(), nullable { providerData() })
            else -> error("Unsupported content tag")
        }
        fun commandPayload(): CommandPayload = when(i()) {
            0 -> CommandPayload.SendText(s(), nullable { messageRef() })
            1 -> CommandPayload.SendMedia(AttachmentId(s()), AttachmentKind.valueOf(s()), nullable { s() })
            2 -> CommandPayload.EditText(messageRef(), s(), nullable { s() })
            3 -> CommandPayload.Delete(messageRef(), b())
            4 -> CommandPayload.React(messageRef(), s())
            5 -> CommandPayload.MarkRead(messageRef())
            6 -> CommandPayload.SetTyping(b())
            7 -> CommandPayload.Logout
            8 -> CommandPayload.Extension(operationKey(), providerData())
            else -> error("Unsupported content tag")
        }
        fun state(): LocalState { version = i(); require(version in 1..VERSION) { "Unsupported storage version" }; return LocalState(
            accounts = list(::account),
            people = list(::person),
            identities = list(::messengerIdentity),
            links = list(::personIdentityLink),
            groups = list(::group),
            conversations = list(::conversation),
            messages = list(::message),
            attachments = list(::attachment),
            metadata = list(::providerData),
            checkpoints = list(::syncCheckpoint),
            pending = list(::pendingOperation),
            retention = if(version >= 2) retentionPolicy() else RetentionPolicy(),
            archived = if(version >= 2) list { ConversationId(s()) }.toSet() else emptySet(),
            reactions = if(version >= 3) list(::storedReaction) else emptyList(),
            manualIdentities = if(version >= 5) list { IdentityId(s()) }.toSet() else emptySet(),
        ) }
    }
}
