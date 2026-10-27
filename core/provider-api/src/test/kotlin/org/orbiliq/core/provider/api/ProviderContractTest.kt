package org.orbiliq.core.provider.api

import java.time.Instant
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.model.*

class ProviderContractTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val account = AccountId("a")
    private val route = ConversationRef(account, "c")
    private fun command(payload: CommandPayload = CommandPayload.SendText("hello")) = ProviderCommand(
        CommandId("cmd"), route, payload, now, now.plusSeconds(60), "revision",
    )
    /** Synthetic provider: verifies that discovery and account/session contracts have
     * no dependency on a real messenger. No production integration is claimed.
     */
    private class SyntheticFactory(id: String, operations: Set<OperationKey>) : ProviderFactory {
        override val descriptor = ProviderDescriptor(ProviderId(id), id, "test", ContractVersion(1, 0), operations, ReplayClass.BestEffort)
        override suspend fun open(account: AccountHandle): ProviderSession {
            require(account.account.providerId == descriptor.id)
            return SyntheticSession(account.account.id, descriptor)
        }
    }
    private class SyntheticSession(override val accountId: AccountId, private val descriptor: ProviderDescriptor) : ProviderSession {
        override val epoch = "new-session"
        override val state = MutableStateFlow(ProviderState(AuthState.Ready, ConnectionState.Online, SyncState.Idle))
        private var closed = false
        override fun observeCapabilities(scope: CapabilityScope): Flow<CapabilitySnapshot> = emptyFlow()
        override suspend fun evaluate(request: OperationRequest): Availability =
            if (!closed && request.operation in descriptor.potential) Availability.Available() else Availability.Unsupported("unsupported")
        override fun events(after: ReplayCursor?): Flow<EventEnvelope> = emptyFlow()
        override suspend fun acknowledge(cursor: ReplayCursor): OperationResult<Unit> =
            if (cursor.accountId == accountId && cursor.epoch == epoch) OperationResult.Success(Unit)
            else OperationResult.Rejected(ProviderFailure(FailureCategory.ProtocolFailure))
        override suspend fun execute(command: ProviderCommand): DispatchResult {
            if (closed || command.route.accountId != accountId || command.payload.operation !in descriptor.potential)
                return DispatchResult.Rejected(ProviderFailure(FailureCategory.CapabilityUnavailable))
            return DispatchResult.OutcomeUnknown("test.uncertain")
        }
        override suspend fun reconcile(request: ReconcileRequest): ReconcileResult = ReconcileResult.StillUnknown
        override suspend fun authenticate(response: AuthResponse): AuthResult = AuthResult.Ready
        override suspend fun close() { closed = true }
    }
    @Test fun arbitraryTextAndPreviewProvidersRegisterWithoutMessengerBranches() = runTest {
        val text = SyntheticFactory("org.example.text", setOf(Operations.SendText, Operations.ReceiveText))
        val preview = SyntheticFactory("org.example.preview", setOf(Operations.Preview))
        val registry = ProviderRegistry(listOf(text, preview))
        assertEquals(2, registry.descriptors.size)
        assertNull(registry.find(ProviderId("missing")))
        for (factory in listOf(text, preview)) {
            val session = registry.find(factory.descriptor.id)!!.open(AccountHandle(Account(account, factory.descriptor.id, "test"), "vault"))
            val result = session.execute(command())
            if (Operations.SendText in factory.descriptor.potential) assertTrue(result is DispatchResult.OutcomeUnknown)
            else assertTrue(result is DispatchResult.Rejected)
        }
    }
    @Test fun duplicateAndIncompatibleRegistrationsAreRejected() {
        val factory = SyntheticFactory("new", emptySet())
        assertThrows(IllegalArgumentException::class.java) { ProviderRegistry(listOf(factory, factory)) }
        assertThrows(IllegalArgumentException::class.java) { ProviderRegistry(listOf(factory), ContractVersion(2, 0)) }
        assertThrows(IllegalArgumentException::class.java) { ProviderRegistry(listOf(factory), ContractVersion(1, 1)) }
        assertTrue(ContractVersion(1, 2).supports(ContractVersion(1, 1)))
    }
    @Test fun accountsAreIsolatedAndCloseIsIdempotent() = runTest {
        val factory = SyntheticFactory("text", setOf(Operations.SendText))
        val first = factory.open(AccountHandle(Account(account, factory.descriptor.id, "first"), "vault1"))
        val second = factory.open(AccountHandle(Account(AccountId("b"), factory.descriptor.id, "second"), "vault2"))
        assertTrue(second.execute(command()) is DispatchResult.Rejected)
        first.close(); first.close()
        assertTrue(first.execute(command()) is DispatchResult.Rejected)
        assertTrue(second.execute(command().copy(route = ConversationRef(AccountId("b"), "c"))) is DispatchResult.OutcomeUnknown)
    }
    @Test fun staleAndForeignCursorAreRejected() = runTest {
        val factory = SyntheticFactory("text", emptySet())
        val session = factory.open(AccountHandle(Account(account, factory.descriptor.id, "account"), "vault"))
        val cursor = ReplayCursor(account, "messages", session.epoch, "opaque")
        assertTrue(session.acknowledge(cursor) is OperationResult.Success)
        assertTrue(session.acknowledge(cursor.copy(epoch = "old")) is OperationResult.Rejected)
        assertTrue(session.acknowledge(cursor.copy(accountId = AccountId("other"))) is OperationResult.Rejected)
    }
    @Test fun uncertainOutcomeRemainsDistinctFromSafeRetryAndAcceptance() {
        val uncertain: DispatchResult = DispatchResult.OutcomeUnknown()
        assertFalse(uncertain is DispatchResult.RetryableNoEffect)
        assertFalse(uncertain is DispatchResult.Accepted)
        assertThrows(IllegalArgumentException::class.java) { DispatchResult.Accepted("") }
    }
    @Test fun crossRouteRepliesEditsDeletesReactionsAndReceiptsFailAtConstruction() {
        val foreign = MessageRef(ConversationRef(account, "other"), "m")
        listOf(CommandPayload.SendText("reply", foreign), CommandPayload.EditText(foreign, "edit", null),
            CommandPayload.Delete(foreign, true), CommandPayload.React(foreign, "ok"), CommandPayload.MarkRead(foreign)).forEach {
            assertThrows(IllegalArgumentException::class.java) { command(it) }
        }
    }
    @Test fun commandExpiryDependenciesAndVersionAreValidated() {
        assertThrows(IllegalArgumentException::class.java) { command().copy(expiresAt = now) }
        assertThrows(IllegalArgumentException::class.java) { command().copy(dependencies = setOf(CommandId("cmd"))) }
        assertThrows(IllegalArgumentException::class.java) { command().copy(schemaVersion = 0) }
        assertThrows(IllegalArgumentException::class.java) { command().copy(capabilityRevision = "") }
        assertEquals(Operations.Reply, command(CommandPayload.SendText("reply", MessageRef(route, "m"))).payload.operation)
        assertEquals(Operations.DeleteSelf, CommandPayload.Delete(MessageRef(route, "m"), false).operation)
    }
    @Test fun eventEnvelopeRetainsReplayIdentityAndRejectsCrossAccountData() {
        val cursor = ReplayCursor(account, "messages", "epoch", "1")
        val event = EventEnvelope("event", cursor, now, now, ProviderEvent.MessageDeleted(MessageRef(route, "m"), "revision"))
        assertEquals(event, event.copy())
        assertEquals(cursor, event.cursor)
        assertThrows(IllegalArgumentException::class.java) { event.copy(cursor = cursor.copy(accountId = AccountId("wrong"))) }
        assertThrows(IllegalArgumentException::class.java) { event.copy(schemaVersion = 0) }
    }
    @Test fun authenticationSecretsAndVaultReferencesAreNotLogged() {
        val input = "sensitive".toCharArray()
        val response = AuthResponse("challenge", AuthInputKind.Password, input)
        input.fill('x')
        assertArrayEquals("sensitive".toCharArray(), response.secret())
        val copy = response.secret(); copy.fill('x')
        assertFalse(response.toString().contains("sensitive"))
        response.close()
        assertTrue(response.secret().all { it == '\u0000' })
        val handle = AccountHandle(Account(account, ProviderId("p"), "user"), "secret-vault-ref")
        assertFalse(handle.toString().contains("secret-vault-ref"))
    }
    @Test fun extensionOperationAndUnknownContentNeedNoMessengerType() {
        val data = ProviderData(ProviderId("org.future"), "org.future.poll", 2, byteArrayOf(4))
        val operation = OperationKey("org.future.poll.vote", 2)
        val extension = command(CommandPayload.Extension(operation, data))
        assertEquals(operation, extension.payload.operation)
        assertTrue(EventEnvelope("ext", ReplayCursor(account, "extension", "e", "1"), now, now, ProviderEvent.Extension(data)).payload is ProviderEvent.Extension)
    }
}
