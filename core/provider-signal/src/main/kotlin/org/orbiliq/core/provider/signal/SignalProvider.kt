package org.orbiliq.core.provider.signal

import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

/** Linking-only milestone. No messaging operation is advertised or dispatched. */
class SignalProviderFactory(
    private val transport: SignalCompanionTransport,
    private val clock: Clock = Clock.systemUTC(),
) : ProviderFactory {
    override val descriptor = ProviderDescriptor(
        ProviderId("signal"), "Signal Companion (Einrichtung)", "0.1-linking",
        ContractVersion(1, 0), emptySet(), ReplayClass.BestEffort,
    )
    override suspend fun open(account: AccountHandle): ProviderSession {
        require(account.account.providerId == descriptor.id) { "Foreign provider" }
        return SignalLinkingSession(account.account.id, transport, clock)
    }
}

private class SignalLinkingSession(
    override val accountId: AccountId,
    private val transport: SignalCompanionTransport,
    private val clock: Clock,
) : ProviderSession {
    override val epoch = UUID.randomUUID().toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleGate = Any()
    private val closed = MutableStateFlow(false)
    private val mutableState = MutableStateFlow(providerState(SignalLinkState.Unavailable))
    override val state = mutableState.asStateFlow()
    init {
        scope.launch {
            try {
                transport.statuses().distinctUntilChanged().collect { update(it.link) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { update(SignalLinkState.Unavailable) }
        }
    }

    private fun providerState(link: SignalLinkState): ProviderState = ProviderState(
        auth = when (link) {
            // v1 has no account identity binding. Local registration cannot authenticate
            // an arbitrary AccountHandle, even though the separate setup UI can show Linked.
            SignalLinkState.Linked -> AuthState.Blocked
            SignalLinkState.Revoked -> AuthState.Revoked
            SignalLinkState.NeedsLink, SignalLinkState.ConsentRequired -> AuthState.NeedsInput
            else -> AuthState.Blocked
        },
        // The status endpoint does not report network or synchronization health.
        connection = ConnectionState.Offline, sync = SyncState.Degraded,
        requirements = setOf("signal.messaging.not-implemented", "signal.account-binding.not-implemented", "signal.link.${link.name}"),
        challenge = if (link in setOf(SignalLinkState.NeedsLink, SignalLinkState.ConsentRequired)) challenge() else null,
    )
    private fun update(link: SignalLinkState) = synchronized(lifecycleGate) {
        if (!closed.value) mutableState.value = providerState(link)
    }
    private fun challenge() = AuthChallenge(UUID.randomUUID().toString(), AuthInputKind.External, clock.instant().plusSeconds(120),
        instructionCode = "signal.open-companion")

    override fun observeCapabilities(scope: CapabilityScope): Flow<CapabilitySnapshot> = flow {
        val now = clock.instant()
        emit(CapabilitySnapshot(scope, "signal-linking-only-v1", now, now.plusSeconds(60), emptyMap()))
    }
    override suspend fun evaluate(request: OperationRequest): Availability = Availability.Unsupported("signal.messaging.not-implemented")
    override fun events(after: ReplayCursor?): Flow<EventEnvelope> = emptyFlow()
    override suspend fun acknowledge(cursor: ReplayCursor): OperationResult<Unit> = OperationResult.Rejected(unavailable())
    override suspend fun execute(command: ProviderCommand): DispatchResult = DispatchResult.Rejected(unavailable())
    override suspend fun reconcile(request: ReconcileRequest): ReconcileResult = ReconcileResult.StillUnknown
    override suspend fun authenticate(response: AuthResponse): AuthResult {
        if (closed.value) return AuthResult.Rejected(unavailable())
        val challenge = state.value.challenge ?: return AuthResult.Rejected(unavailable())
        if (response.challengeId != challenge.id || response.kind != AuthInputKind.External || clock.instant() >= challenge.expiresAt) {
            return AuthResult.Rejected(ProviderFailure(FailureCategory.PolicyExpired))
        }
        try {
            transport.openSetup()
            update(transport.refresh().link)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { update(SignalLinkState.Unavailable) }
        if (closed.value) return AuthResult.Rejected(unavailable())
        return state.value.challenge?.let { AuthResult.NeedsInput(it) } ?: AuthResult.Rejected(unavailable())
    }
    override suspend fun close() {
        synchronized(lifecycleGate) {
            closed.value = true
            scope.cancel()
            mutableState.value = providerState(SignalLinkState.Unavailable)
        }
    }
    private fun unavailable() = ProviderFailure(FailureCategory.CapabilityUnavailable, diagnosticCode = "signal.linking-only")
}
