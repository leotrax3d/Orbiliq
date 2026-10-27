package org.orbiliq.core.model

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CapabilityPolicyTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val provider = ProviderId("arbitrary")
    private val account = AccountId("a")
    private val route = ConversationRef(account, "c")
    private val scopes = listOf(CapabilityScope.Provider(provider), CapabilityScope.Account(provider, account), CapabilityScope.Conversation(provider, route))
    private val request = OperationRequest(scopes.last(), Operations.SendText, textLength = 5, targetKind = ConversationKind.Direct)
    private fun snapshots(availability: Availability = Availability.Available()) = scopes.map {
        CapabilitySnapshot(it, "r1", now.minusSeconds(1), now.plusSeconds(60), mapOf(Operations.SendText to OperationSupport(availability)))
    }
    private fun evaluate(snapshots: List<CapabilitySnapshot>, request: OperationRequest = this.request) = CapabilityPolicy.evaluate(request, snapshots, now)
    @Test fun completeCurrentHierarchyAllowsOperation() { assertTrue(evaluate(snapshots()) is Availability.Available) }
    @Test fun missingWrongOrReorderedAncestryFailsClosed() {
        assertEquals(Availability.Unknown, evaluate(snapshots().drop(1)))
        assertEquals(Availability.Unknown, evaluate(snapshots().reversed()))
        assertEquals(Availability.Unknown, evaluate(snapshots().map { it.copy(scope = CapabilityScope.Provider(ProviderId("wrong"))) }))
    }
    @Test fun childCannotEnableUnsupportedParent() {
        val snapshots = snapshots().toMutableList()
        snapshots[0] = snapshots[0].copy(operations = emptyMap())
        assertTrue(evaluate(snapshots) is Availability.Unsupported)
    }
    @Test fun revokedRightsAreReevaluated() {
        val original = snapshots()
        assertTrue(evaluate(original) is Availability.Available)
        val revoked = original.toMutableList()
        revoked[2] = revoked[2].copy(revision = "r2", operations = mapOf(Operations.SendText to OperationSupport(Availability.Restricted(Restriction.PermissionDenied))))
        assertEquals(Availability.Restricted(Restriction.PermissionDenied), evaluate(revoked))
    }
    @Test fun expiredAndFutureSnapshotsDoNotAuthorize() {
        assertEquals(Availability.Unknown, evaluate(snapshots().map { it.copy(expiresAt = now) }))
        assertEquals(Availability.Unknown, evaluate(snapshots().map { it.copy(evaluatedAt = now.plusSeconds(1)) }))
    }
    @Test fun unknownOperationAndSchemaVersionAreUnsupported() {
        assertTrue(evaluate(snapshots(), request.copy(operation = Operations.ReceiveText)) is Availability.Unsupported)
        assertTrue(evaluate(snapshots(), request.copy(operation = Operations.SendText.copy(schemaVersion = 2))) is Availability.Unsupported)
    }
    @Test fun parentLimitsRemainEffectiveWithUnrestrictedChild() {
        val limited = snapshots().toMutableList()
        limited[0] = snapshots(Availability.Available(OperationConstraints(maxTextLength = 4)))[0]
        assertEquals(Availability.Restricted(Restriction.Policy), evaluate(limited))
        assertTrue(evaluate(limited, request.copy(textLength = 4)) is Availability.Available)
        assertTrue(evaluate(limited, request.copy(textLength = null)) is Availability.Restricted)
    }
    @Test fun unknownSafetyRequirementsBlockExecution() {
        assertTrue(evaluate(snapshots(Availability.Available(OperationConstraints(unknownRequirements = setOf("new-protection"))))) is Availability.Restricted)
    }
    @Test fun offlineQueueNeedsExplicitConsentAtEveryScope() {
        assertTrue(evaluate(snapshots(), request.copy(offlineQueue = true)) is Availability.Restricted)
        assertTrue(evaluate(snapshots(Availability.Available(OperationConstraints(persistentOfflineQueue = true))), request.copy(offlineQueue = true)) is Availability.Available)
    }
    @Test fun payloadMimeSizeTargetAndDeadlineAreChecked() {
        val constraints = OperationConstraints(maxBytes = 10, mimeTypes = setOf("image/png"), targetKinds = setOf(ConversationKind.Direct), deadline = now.plusSeconds(1))
        val snapshots = snapshots(Availability.Available(constraints))
        val valid = request.copy(sizeBytes = 10, mimeType = "image/png")
        assertTrue(evaluate(snapshots, valid) is Availability.Available)
        listOf(valid.copy(sizeBytes = 11), valid.copy(mimeType = "image/jpeg"), valid.copy(targetKind = ConversationKind.Channel), valid.copy(sizeBytes = null)).forEach {
            assertTrue(evaluate(snapshots, it) is Availability.Restricted)
        }
        assertFalse(constraints.copy(deadline = now).permits(valid, now))
    }
    @Test fun messageScopeRequiresConversationAndAccountAncestors() {
        val scope = CapabilityScope.Message(provider, MessageRef(route, "m"))
        val snapshots = snapshots() + snapshots().last().copy(scope = scope)
        assertTrue(evaluate(snapshots, request.copy(scope = scope)) is Availability.Available)
        assertEquals(Availability.Unknown, evaluate(snapshots.takeLast(1), request.copy(scope = scope)))
    }
    @Test fun receiveAndPreviewDoNotGrantSend() {
        val preview = scopes.map { CapabilitySnapshot(it, "r", now.minusSeconds(1), now.plusSeconds(1), mapOf(Operations.Preview to OperationSupport(Availability.Available()))) }
        assertTrue(evaluate(preview) is Availability.Unsupported)
    }
    @Test fun offlineRestrictionIsNotUnsupported() {
        assertEquals(Availability.Restricted(Restriction.Offline), evaluate(snapshots(Availability.Restricted(Restriction.Offline))))
    }
}
