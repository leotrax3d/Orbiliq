package org.orbiliq.core.provider.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.orbiliq.core.model.*
import java.time.Instant

/** Vault references are opaque; the core never reads protocol keys or session secrets. */
class AccountHandle(val account: Account, private val vaultReference: String) {
    init { require(vaultReference.isNotBlank()) }
    fun vaultReference(): String = vaultReference
    override fun toString() = "AccountHandle(redacted)"
}
interface ProviderFactory {
    val descriptor: ProviderDescriptor
    /** Must reject accounts belonging to another provider. Each open owns an isolated scope. */
    suspend fun open(account: AccountHandle): ProviderSession
}
interface ProviderSession {
    val accountId: AccountId
    val epoch: String
    val state: StateFlow<ProviderState>
    fun observeCapabilities(scope: CapabilityScope): Flow<CapabilitySnapshot>
    suspend fun evaluate(request: OperationRequest): Availability
    /** Durable events must use backpressure or explicit GapDetected, never silent dropping. */
    fun events(after: ReplayCursor? = null): Flow<EventEnvelope>
    /** Only after the projection transaction commits; reject foreign account/stream/epoch. */
    suspend fun acknowledge(cursor: ReplayCursor): OperationResult<Unit>
    /** Recheck route, expiry, schema, permissions and payload immediately before side effects.
     * Cancellation propagates; callers persist uncertain dispatches for reconciliation.
     */
    suspend fun execute(command: ProviderCommand): DispatchResult
    suspend fun reconcile(request: ReconcileRequest): ReconcileResult
    suspend fun authenticate(response: AuthResponse): AuthResult
    /** Idempotent resource cleanup; never implicitly revokes remote authorization. */
    suspend fun close()
}
enum class AuthState { NeedsInput, Ready, Revoked, Blocked }
enum class ConnectionState { Offline, Connecting, Online }
enum class SyncState { Idle, CatchingUp, Gap, Degraded }
data class ProviderState(
    val auth: AuthState, val connection: ConnectionState, val sync: SyncState,
    val lastDurableEventAt: Instant? = null, val requirements: Set<String> = emptySet(),
    val challenge: AuthChallenge? = null,
)
enum class FailureCategory {
    NetworkUnavailable, RateLimited, AuthRequired, SessionRevoked, IdentityChanged,
    PermissionDenied, CapabilityUnavailable, PolicyExpired, MediaUnavailable,
    StorageFull, StorageCorrupt, IncompatibleProvider, ProtocolFailure, SyncConflict,
}
data class ProviderFailure(val category: FailureCategory, val retryAt: Instant? = null, val diagnosticCode: String? = null)
sealed interface OperationResult<out T> {
    data class Success<T>(val value: T) : OperationResult<T>
    data class Rejected(val failure: ProviderFailure) : OperationResult<Nothing>
    data class FailedNoEffect(val failure: ProviderFailure) : OperationResult<Nothing>
    data class OutcomeUnknown(val diagnosticCode: String? = null) : OperationResult<Nothing>
}
sealed interface DispatchResult {
    data class Rejected(val failure: ProviderFailure) : DispatchResult
    data class RetryableNoEffect(val failure: ProviderFailure) : DispatchResult
    /** Allowed only after durable provider ownership of transport retries. */
    data class Accepted(val receipt: String, val remoteMessage: MessageRef? = null) : DispatchResult { init { require(receipt.isNotBlank()) } }
    data class Confirmed(val remoteMessage: MessageRef? = null) : DispatchResult
    data class OutcomeUnknown(val diagnosticCode: String? = null) : DispatchResult
}
data class ReconcileRequest(val commandId: CommandId, val route: ConversationRef, val receipt: String? = null)
sealed interface ReconcileResult {
    data class Confirmed(val remoteMessage: MessageRef? = null) : ReconcileResult
    data object ProvenNoEffect : ReconcileResult
    data object StillUnknown : ReconcileResult
    data class Failed(val failure: ProviderFailure) : ReconcileResult
}
