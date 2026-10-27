package org.orbiliq.core.provider.api

import java.time.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.model.*

class CheckedProviderSessionTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val provider = ProviderId("synthetic")
    private val account = AccountId("a")
    private val route = ConversationRef(account, "c")
    private val descriptor = ProviderDescriptor(provider, "Synthetic", "test", ContractVersion(1, 0), setOf(Operations.SendText, Operations.EditText), ReplayClass.Durable)
    private fun command() = ProviderCommand(CommandId("cmd"), route, CommandPayload.SendText("hello"), now, now.plusSeconds(10), "revision")
    private inner class Backend : ProviderSession {
        override val accountId = account
        override val epoch = "e"
        override val state = MutableStateFlow(ProviderState(AuthState.Ready, ConnectionState.Online, SyncState.Idle))
        var availability: Availability = Availability.Available()
        var effects = 0
        var closed = 0
        var cancel = false
        var lastScope: CapabilityScope? = null
        var event: EventEnvelope? = null
        override fun observeCapabilities(scope: CapabilityScope): Flow<CapabilitySnapshot> = emptyFlow()
        override suspend fun evaluate(request: OperationRequest): Availability { lastScope = request.scope; return availability }
        override fun events(after: ReplayCursor?): Flow<EventEnvelope> = flow { event?.let { emit(it) } }
        override suspend fun acknowledge(cursor: ReplayCursor): OperationResult<Unit> = OperationResult.Success(Unit)
        override suspend fun execute(command: ProviderCommand): DispatchResult {
            if (cancel) throw CancellationException("test")
            effects++; return DispatchResult.OutcomeUnknown()
        }
        override suspend fun reconcile(request: ReconcileRequest): ReconcileResult = ReconcileResult.StillUnknown
        override suspend fun authenticate(response: AuthResponse): AuthResult { effects++; return AuthResult.Ready }
        override suspend fun close() { closed++ }
    }
    private fun checked(backend: Backend) = CheckedProviderSession(backend, descriptor, Clock.fixed(now, ZoneOffset.UTC))
    @Test fun capabilityRevocationBeforeDispatchPreventsSideEffect() = runTest {
        val backend = Backend(); val session = checked(backend)
        val request = OperationRequest(CapabilityScope.Conversation(provider, route), Operations.SendText)
        assertTrue(session.evaluate(request) is Availability.Available)
        backend.availability = Availability.Restricted(Restriction.PermissionDenied)
        assertTrue(session.execute(command()) is DispatchResult.Rejected)
        assertEquals(0, backend.effects)
    }
    @Test fun expiredFutureAndUnsupportedSchemaCommandsNeverReachBackend() = runTest {
        val backend = Backend(); val session = checked(backend)
        listOf(command().copy(createdAt = now.minusSeconds(2), expiresAt = now),
            command().copy(createdAt = now.plusSeconds(1)), command().copy(schemaVersion = 2),
            command().copy(route = ConversationRef(AccountId("other"), "c"))).forEach {
            assertTrue(session.execute(it) is DispatchResult.Rejected)
        }
        assertEquals(0, backend.effects)
    }
    @Test fun descriptorPotentialBoundsOverclaimingBackend() = runTest {
        val backend = Backend(); val session = checked(backend)
        assertTrue(session.execute(command().copy(payload = CommandPayload.Logout)) is DispatchResult.Rejected)
        val request = OperationRequest(CapabilityScope.Account(ProviderId("foreign"), account), Operations.SendText)
        assertTrue(session.evaluate(request) is Availability.Restricted)
        assertEquals(0, backend.effects)
        assertThrows(IllegalArgumentException::class.java) {
            session.observeCapabilities(CapabilityScope.Account(provider, AccountId("foreign")))
        }
    }
    @Test fun payloadLimitsAreEnforcedBeforeEffect() = runTest {
        val backend = Backend(); val session = checked(backend)
        backend.availability = Availability.Available(OperationConstraints(maxTextLength = 4))
        assertTrue(session.execute(command()) is DispatchResult.Rejected)
        backend.availability = Availability.Available(OperationConstraints(maxTextLength = 5))
        assertTrue(session.execute(command()) is DispatchResult.OutcomeUnknown)
        assertEquals(1, backend.effects)
    }
    @Test fun editIsEvaluatedAtMessageScope() = runTest {
        val backend = Backend(); val session = checked(backend)
        val ref = MessageRef(route, "m")
        session.execute(command().copy(payload = CommandPayload.EditText(ref, "edit", "v")))
        assertEquals(CapabilityScope.Message(provider, ref), backend.lastScope)
    }
    @Test fun cancellationIsNotConvertedToSafeRetry() = runTest {
        val backend = Backend(); backend.cancel = true
        try { checked(backend).execute(command()); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertEquals(0, backend.effects)
    }
    @Test fun closeIsIdempotentAndBlocksFurtherEffects() = runTest {
        val backend = Backend(); val session = checked(backend)
        session.close(); session.close()
        assertEquals(1, backend.closed)
        assertTrue(session.execute(command()) is DispatchResult.Rejected)
    }
    @Test fun replayRejectsOldEpochAndForeignEnvelopes() = runTest {
        val backend = Backend(); val session = checked(backend)
        val cursor = ReplayCursor(account, "s", "e", "t")
        assertTrue(session.acknowledge(cursor.copy(epoch = "old")) is OperationResult.Rejected)
        assertThrows(IllegalArgumentException::class.java) { session.events(cursor.copy(epoch = "old")) }
        backend.event = EventEnvelope("event", cursor.copy(accountId = AccountId("foreign")), now, now, ProviderEvent.GapDetected("test"))
        try { session.events().toList(); fail("Foreign event must fail") } catch (_: IllegalStateException) { }
        backend.event = EventEnvelope("event", cursor, now, now,
            ProviderEvent.Extension(ProviderData(ProviderId("foreign"), "extension", 1, byteArrayOf())))
        try { session.events().toList(); fail("Foreign extension must fail") } catch (_: IllegalStateException) { }
        assertTrue(session.reconcile(ReconcileRequest(CommandId("c"), ConversationRef(AccountId("foreign"), "c"))) is ReconcileResult.Failed)
    }
    @Test fun expiredWrongAndUnknownAuthChallengesFailClosed() = runTest {
        val backend = Backend(); val session = checked(backend)
        val response = AuthResponse("challenge", AuthInputKind.Code, charArrayOf('1'))
        val challenge = AuthChallenge("challenge", AuthInputKind.Code, now.plusSeconds(1), instructionCode = "code")
        assertTrue(session.authenticate(response) is AuthResult.Rejected)
        backend.state.value = backend.state.value.copy(challenge = challenge.copy(expiresAt = now))
        assertTrue(session.authenticate(response) is AuthResult.Rejected)
        backend.state.value = backend.state.value.copy(challenge = challenge.copy(schemaVersion = 2))
        assertEquals(AuthResult.UpdateRequired, session.authenticate(response))
        backend.state.value = backend.state.value.copy(challenge = challenge.copy(kind = AuthInputKind.Password))
        assertTrue(session.authenticate(response) is AuthResult.Rejected)
        assertEquals(0, backend.effects)
        backend.state.value = backend.state.value.copy(challenge = challenge)
        assertEquals(AuthResult.Ready, session.authenticate(response))
        response.close()
    }
}
