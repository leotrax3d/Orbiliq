package org.orbiliq.core.provider.api

import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.orbiliq.core.model.*

/** Common fail-closed boundary for adapters. The delegate must still check protocol
 * permissions atomically with dispatch: a successful evaluation is not a reservation.
 * The caller must persist InFlight before invoking execute; this is not an outbox.
 */
class CheckedProviderSession(
    private val delegate: ProviderSession,
    private val descriptor: ProviderDescriptor,
    private val clock: Clock,
) : ProviderSession by delegate {
    @Volatile private var closed = false

    private fun owns(scope: CapabilityScope): Boolean = scope.providerId == descriptor.id && when (scope) {
        is CapabilityScope.Provider -> true
        is CapabilityScope.Account -> scope.accountId == accountId
        is CapabilityScope.Conversation -> scope.ref.accountId == accountId
        is CapabilityScope.Message -> scope.ref.conversation.accountId == accountId
    }
    override fun observeCapabilities(scope: CapabilityScope): Flow<CapabilitySnapshot> {
        require(!closed && owns(scope))
        return delegate.observeCapabilities(scope).map { snapshot ->
            check(snapshot.scope == scope) { "Invalid capability scope" }
            snapshot
        }
    }
    override suspend fun evaluate(request: OperationRequest): Availability {
        if (closed || !owns(request.scope)) return Availability.Restricted(Restriction.PermissionDenied)
        if (request.operation !in descriptor.potential) return Availability.Unsupported("operation.unknown")
        val available = delegate.evaluate(request)
        return if (available is Availability.Available && !available.constraints.permits(request, clock.instant()))
            Availability.Restricted(Restriction.Policy) else available
    }
    override suspend fun execute(command: ProviderCommand): DispatchResult {
        fun rejected(category: FailureCategory) = DispatchResult.Rejected(ProviderFailure(category))
        if (closed || command.route.accountId != accountId) return rejected(FailureCategory.PermissionDenied)
        if (command.schemaVersion != 1) return rejected(FailureCategory.IncompatibleProvider)
        val now = clock.instant()
        if (now < command.createdAt || now >= command.expiresAt) return rejected(FailureCategory.PolicyExpired)
        val extension = command.payload as? CommandPayload.Extension
        if (extension != null && extension.data.providerId != descriptor.id) return rejected(FailureCategory.IncompatibleProvider)
        val textLength = when (val payload = command.payload) {
            is CommandPayload.SendText -> payload.text.length
            is CommandPayload.EditText -> payload.text.length
            is CommandPayload.SendMedia -> payload.caption?.length
            else -> null
        }
        val message = when (val payload = command.payload) {
            is CommandPayload.EditText -> payload.message
            is CommandPayload.Delete -> payload.message
            is CommandPayload.React -> payload.message
            is CommandPayload.MarkRead -> payload.through
            else -> null
        }
        val scope = message?.let { CapabilityScope.Message(descriptor.id, it) }
            ?: CapabilityScope.Conversation(descriptor.id, command.route)
        val request = OperationRequest(scope, command.payload.operation, textLength = textLength)
        if (evaluate(request) !is Availability.Available) return rejected(FailureCategory.CapabilityUnavailable)
        return delegate.execute(command)
    }
    override fun events(after: ReplayCursor?): Flow<EventEnvelope> {
        require(!closed)
        require(after == null || after.accountId == accountId && after.epoch == epoch)
        return delegate.events(after).map { event ->
            check(event.cursor.accountId == accountId && event.cursor.epoch == epoch && event.schemaVersion == 1) {
                "Invalid event scope or schema; reconciliation required"
            }
            when (val payload = event.payload) {
                is ProviderEvent.CapabilitiesChanged -> check(owns(payload.snapshot.scope))
                is ProviderEvent.Extension -> check(payload.data.providerId == descriptor.id)
                else -> Unit
            }
            event
        }
    }
    override suspend fun acknowledge(cursor: ReplayCursor): OperationResult<Unit> =
        if (closed || cursor.accountId != accountId || cursor.epoch != epoch)
            OperationResult.Rejected(ProviderFailure(FailureCategory.ProtocolFailure))
        else delegate.acknowledge(cursor)

    override suspend fun reconcile(request: ReconcileRequest): ReconcileResult =
        if (closed || request.route.accountId != accountId)
            ReconcileResult.Failed(ProviderFailure(FailureCategory.PermissionDenied))
        else delegate.reconcile(request)

    override suspend fun authenticate(response: AuthResponse): AuthResult {
        val challenge = state.value.challenge
        if (closed || challenge == null || challenge.id != response.challengeId ||
            challenge.kind != response.kind || clock.instant() >= challenge.expiresAt)
            return AuthResult.Rejected(ProviderFailure(FailureCategory.AuthRequired))
        if (challenge.schemaVersion != 1) return AuthResult.UpdateRequired
        return delegate.authenticate(response)
    }
    override suspend fun close() {
        if (!closed) {
            // Set only after successful cleanup, permitting retry after cancellation.
            delegate.close()
            closed = true
        }
    }
}
