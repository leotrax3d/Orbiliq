package org.orbiliq.core.data.storage

import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

/** One coordinator per account/store owner. All remote effects are preceded by a durable claim. */
class SyncCoordinator(
    private val repository: LocalRepository,
    private val session: ProviderSession,
    private val timeoutMillis: Long = 30_000,
    private val beforeApply: suspend (EventEnvelope) -> Unit = {},
    private val now: () -> Instant = Instant::now,
) {
    init { require(timeoutMillis > 0) }
    private val dispatchLock = Mutex()
    private val mutableFailure = MutableStateFlow<FailureCategory?>(null)
    val failure: StateFlow<FailureCategory?> = mutableFailure.asStateFlow()

    /** Caller owns the lifetime (app scope); cancellation never means a send had no effect. */
    suspend fun run(): Unit = coroutineScope {
        launch {
            var failures = 0
            while (isActive) {
                session.state.first { it.connection == ConnectionState.Online && it.auth == AuthState.Ready }
                try { collect(); failures = 0 }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableFailure.value = FailureCategory.ProtocolFailure; failures++ }
                delay(backoff(failures.coerceAtLeast(1)))
            }
        }
        launch {
            while (isActive) {
                try { drain() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableFailure.value = FailureCategory.StorageCorrupt }
                delay(250)
            }
        }
    }
    suspend fun collect() {
        val checkpoint = repository.snapshot().checkpoints.find { it.cursor.accountId == session.accountId }
        val cursor = checkpoint?.cursor?.takeIf { it.epoch == session.epoch }
        session.events(cursor).collect { envelope ->
            require(envelope.cursor.accountId == session.accountId && envelope.cursor.epoch == session.epoch)
            beforeApply(envelope)
            repository.apply(envelope)
            if(envelope.payload is ProviderEvent.GapDetected) mutableFailure.value = FailureCategory.SyncConflict
            check(session.acknowledge(envelope.cursor) is OperationResult.Success) { "Provider acknowledgement failed" }
        }
    }
    /** Also useful for deterministic workers/tests. Network signals are hints, never proof of delivery. */
    suspend fun drain() = dispatchLock.withLock {
        if(session.state.value.connection != ConnectionState.Online || session.state.value.auth != AuthState.Ready) return@withLock
        val operations = repository.snapshot().pending.filter { it.command.route.accountId == session.accountId }
        for (op in operations) {
            if(op.retryAt?.let { it > now() } == true) continue
            when(op.status) {
                PendingStatus.Dispatching, PendingStatus.OutcomeUnknown, PendingStatus.Accepted -> {
                    val result = try { withTimeoutOrNull(timeoutMillis) { session.reconcile(ReconcileRequest(op.command.id, op.command.route, op.receipt)) } ?: ReconcileResult.StillUnknown }
                    catch (c: CancellationException) { throw c }
                    catch (_: Exception) { ReconcileResult.StillUnknown }
                    when(result) {
                        is ReconcileResult.Confirmed -> repository.updateOperation(op.command.id, PendingStatus.Confirmed, remote = result.remoteMessage)
                        ReconcileResult.ProvenNoEffect -> repository.updateOperation(op.command.id, PendingStatus.RetryableNoEffect)
                        is ReconcileResult.Failed -> repository.updateOperation(op.command.id, op.status, now().plusMillis(backoff(op.attempts + 1)), failure = result.failure.category)
                        ReconcileResult.StillUnknown -> repository.updateOperation(op.command.id, PendingStatus.OutcomeUnknown, now().plusSeconds(30))
                    }
                }
                PendingStatus.Queued, PendingStatus.RetryableNoEffect -> {
                    val current = repository.snapshot().pending
                    if(op.command.dependencies.any { id -> current.none { it.command.id == id && it.status == PendingStatus.Confirmed } }) continue
                    if(op.command.expiresAt <= now()) repository.updateOperation(op.command.id, PendingStatus.Rejected, failure = FailureCategory.PolicyExpired)
                    else dispatchClaimed(op.command.id)
                }
                else -> Unit
            }
        }
    }
    suspend fun dispatch(id: CommandId): DispatchResult = dispatchLock.withLock { dispatchClaimed(id) }
    private suspend fun dispatchClaimed(id: CommandId): DispatchResult {
        val operation = repository.snapshot().pending.single { it.command.id == id }
        require(operation.command.route.accountId == session.accountId)
        require(operation.status in setOf(PendingStatus.Queued, PendingStatus.RetryableNoEffect))
        val provider = repository.snapshot().accounts.single { it.id == session.accountId }.providerId
        val capabilities = withTimeoutOrNull(timeoutMillis) { session.observeCapabilities(CapabilityScope.Conversation(provider, operation.command.route)).first() }
            ?: error("Capability lookup timed out")
        require(capabilities.scope == CapabilityScope.Conversation(provider, operation.command.route))
        val command = operation.command.copy(capabilityRevision = capabilities.revision)
        repository.updateOperation(id, PendingStatus.Dispatching, capabilityRevision = capabilities.revision)
        val result = try { withTimeoutOrNull(timeoutMillis) { session.execute(command) } ?: DispatchResult.OutcomeUnknown("transport.timeout") }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { DispatchResult.OutcomeUnknown("transport.exception") }
        when(result) {
            is DispatchResult.Confirmed -> repository.updateOperation(id, PendingStatus.Confirmed, remote = result.remoteMessage)
            is DispatchResult.Accepted -> repository.updateOperation(id, PendingStatus.Accepted, receipt = result.receipt, remote = result.remoteMessage)
            is DispatchResult.Rejected -> repository.updateOperation(id, PendingStatus.Rejected, failure = result.failure.category)
            is DispatchResult.RetryableNoEffect -> repository.updateOperation(id, PendingStatus.RetryableNoEffect,
                maxOf(result.failure.retryAt ?: now(), now().plusMillis(backoff(operation.attempts + 1))), failure = result.failure.category)
            is DispatchResult.OutcomeUnknown -> repository.updateOperation(id, PendingStatus.OutcomeUnknown)
        }
        return result
    }
    private fun backoff(attempt: Int): Long = (1000L shl (attempt - 1).coerceIn(0, 8)).coerceAtMost(300000L)
}
