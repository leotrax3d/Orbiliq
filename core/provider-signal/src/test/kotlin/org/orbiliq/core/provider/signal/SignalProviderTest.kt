package org.orbiliq.core.provider.signal

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

@OptIn(ExperimentalCoroutinesApi::class)
class SignalProviderTest {
    private val digest = "ab".repeat(32)
    private val account = AccountHandle(Account(AccountId("signal-local"), ProviderId("signal"), "Signal"), "local-companion")
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private class Transport(initial: SignalLinkState) : SignalCompanionTransport {
        val status = MutableStateFlow(SignalCompanionStatus(initial))
        var opened = 0
        var cancelled = false
        override fun statuses() = status
        override suspend fun refresh() = status.value
        override suspend fun openSetup() { if (cancelled) throw CancellationException(); opened++ }
        override suspend fun revokeAccess() { status.value = SignalCompanionStatus(SignalLinkState.ConsentRequired) }
    }

    @Test fun wireContractRejectsUnknownFeaturesVersionsAndStates() {
        assertEquals(SignalLinkState.Linked, SignalBridgeContract.decodeStatus(1, "Linked", emptyList()).link)
        for (status in listOf(
            SignalBridgeContract.decodeStatus(2, "Linked", emptyList()),
            SignalBridgeContract.decodeStatus(1, "Linked", listOf("message.text.send")),
            SignalBridgeContract.decodeStatus(1, "Linked", null),
            SignalBridgeContract.decodeStatus(1, "future.state", emptyList()),
            SignalBridgeContract.decodeStatus(1, null, emptyList()),
        )) assertEquals(SignalLinkState.UpdateRequired, status.link)
    }

    @Test fun trustRequiresExactPackageAndSingleMatchingCurrentSigner() {
        fun trust(packages: Set<String>, self: Set<String>, peer: Set<String>) =
            SignalBridgeTrust.permits("org.orbiliq.app", packages, self, peer)
        assertTrue(trust(setOf("org.orbiliq.app"), setOf(digest), setOf(digest)))
        assertFalse(trust(setOf("org.evil"), setOf(digest), setOf(digest)))
        assertFalse(trust(setOf("org.orbiliq.app", "org.evil"), setOf(digest), setOf(digest)))
        assertFalse(trust(emptySet(), setOf(digest), setOf(digest)))
        assertFalse(trust(setOf("org.orbiliq.app"), emptySet(), emptySet()))
        assertFalse(trust(setOf("org.orbiliq.app"), setOf(digest), setOf("cd".repeat(32))))
        assertFalse(trust(setOf("org.orbiliq.app"), setOf(digest), setOf(digest, "cd".repeat(32))))
        assertFalse(trust(setOf("org.orbiliq.app"), setOf("not-a-certificate"), setOf("not-a-certificate")))
    }

    @Test fun foreignAccountIsRejected() = runBlocking {
        val factory = SignalProviderFactory(Transport(SignalLinkState.Linked), clock)
        try {
            factory.open(AccountHandle(Account(AccountId("wrong"), ProviderId("mock"), "Wrong"), "vault"))
            fail("Foreign account accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun evenLinkedDeviceCannotClaimMessagingOrNetworkHealth() = runBlocking {
        val factory = SignalProviderFactory(Transport(SignalLinkState.Linked), clock)
        assertTrue(factory.descriptor.potential.isEmpty())
        val session = factory.open(account)
        try {
            withTimeout(5000) { session.state.first { "signal.link.Linked" in it.requirements } }
            assertEquals(AuthState.Blocked, session.state.value.auth)
            assertTrue("signal.account-binding.not-implemented" in session.state.value.requirements)
            assertEquals(ConnectionState.Offline, session.state.value.connection)
            assertEquals(SyncState.Degraded, session.state.value.sync)
            val caps = session.observeCapabilities(CapabilityScope.Account(ProviderId("signal"), account.account.id)).first()
            assertTrue(caps.operations.isEmpty())
            val route = ConversationRef(account.account.id, "thread")
            val request = OperationRequest(CapabilityScope.Conversation(ProviderId("signal"), route), Operations.SendText, textLength = 4)
            assertTrue(session.evaluate(request) is Availability.Unsupported)
            assertTrue(session.execute(ProviderCommand(CommandId("cmd"), route, CommandPayload.SendText("test"),
                now, now.plusSeconds(60), caps.revision)) is DispatchResult.Rejected)
            assertEquals(ReconcileResult.StillUnknown, session.reconcile(ReconcileRequest(CommandId("cmd"), route)))
            assertTrue(session.acknowledge(ReplayCursor(account.account.id, "s", session.epoch, "1")) is OperationResult.Rejected)
            assertTrue(session.events().toList().isEmpty())
        } finally { session.close() }
    }

    @Test fun setupRequiresCurrentChallengeAndNeverReceivesSecrets() = runBlocking {
        val transport = Transport(SignalLinkState.NeedsLink)
        val session = SignalProviderFactory(transport, clock).open(account)
        try {
            val challenge = withTimeout(5000) { session.state.first { it.challenge != null }.challenge!! }
            AuthResponse("foreign", AuthInputKind.External, charArrayOf()).use {
                assertTrue(session.authenticate(it) is AuthResult.Rejected)
            }
            AuthResponse(challenge.id, AuthInputKind.Password, "secret".toCharArray()).use {
                assertTrue(session.authenticate(it) is AuthResult.Rejected)
            }
            assertEquals(0, transport.opened)
            AuthResponse(challenge.id, AuthInputKind.External, charArrayOf()).use {
                assertTrue(session.authenticate(it) is AuthResult.NeedsInput)
            }
            assertEquals(1, transport.opened)
        } finally { session.close() }
    }

    @Test fun revokedLockedAndUntrustedAreNeverReady() = runBlocking {
        for (link in listOf(SignalLinkState.Revoked, SignalLinkState.Locked, SignalLinkState.Untrusted,
            SignalLinkState.UpdateRequired, SignalLinkState.PrimaryDeviceNotSupported)) {
            val transport = Transport(link)
            val session = SignalProviderFactory(transport, clock).open(account)
            try {
                withTimeout(5000) { session.state.first { "signal.link.${link.name}" in it.requirements } }
                assertNotEquals(AuthState.Ready, session.state.value.auth)
                assertNull(session.state.value.challenge)
            } finally { session.close() }
        }
    }

    @Test fun linkedStatusRevocationAndCloseStayFailClosed() = runBlocking {
        val transport = Transport(SignalLinkState.Linked)
        val session = SignalProviderFactory(transport, clock).open(account)
        withTimeout(5000) { session.state.first { "signal.link.Linked" in it.requirements } }
        assertEquals(AuthState.Blocked, session.state.value.auth)
        transport.status.value = SignalCompanionStatus(SignalLinkState.Revoked)
        withTimeout(5000) { session.state.first { it.auth == AuthState.Revoked } }
        session.close()
        transport.status.value = SignalCompanionStatus(SignalLinkState.Linked)
        assertEquals(AuthState.Blocked, session.state.value.auth)
        assertTrue(session.authenticate(AuthResponse(session.epoch, AuthInputKind.External, charArrayOf())) is AuthResult.Rejected)
    }

    @Test fun cancellationDoesNotBecomeAuthenticationFailure() = runBlocking {
        val transport = Transport(SignalLinkState.NeedsLink).apply { cancelled = true }
        val session = SignalProviderFactory(transport, clock).open(account)
        try {
            val challenge = withTimeout(5000) { session.state.first { it.challenge != null }.challenge!! }
            try {
                AuthResponse(challenge.id, AuthInputKind.External, charArrayOf()).use { session.authenticate(it) }
                fail("Cancellation swallowed")
            } catch (_: CancellationException) { }
        } finally { session.close() }
    }

    @Test fun expiredSetupChallengeCannotOpenCompanion() = runBlocking {
        var time = now
        val mutableClock = object : Clock() {
            override fun instant() = time
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = this
        }
        val transport = Transport(SignalLinkState.NeedsLink)
        val session = SignalProviderFactory(transport, mutableClock).open(account)
        try {
            val challenge = withTimeout(5000) { session.state.first { it.challenge != null }.challenge!! }
            time = challenge.expiresAt
            AuthResponse(challenge.id, AuthInputKind.External, charArrayOf()).use {
                assertEquals(AuthResult.Rejected(ProviderFailure(FailureCategory.PolicyExpired)), session.authenticate(it))
            }
            assertEquals(0, transport.opened)
        } finally { session.close() }
    }

    @Test fun streamFailureIsSanitizedAndNeverKeepsReadyState() = runBlocking {
        val failStream = CompletableDeferred<Unit>()
        val transport = object : SignalCompanionTransport {
            override fun statuses(): Flow<SignalCompanionStatus> = flow {
                emit(SignalCompanionStatus(SignalLinkState.Linked))
                failStream.await()
                throw IllegalStateException("sensitive upstream exception must not escape")
            }
            override suspend fun refresh() = SignalCompanionStatus(SignalLinkState.Unavailable)
            override suspend fun openSetup() = Unit
            override suspend fun revokeAccess() = Unit
        }
        val session = SignalProviderFactory(transport, clock).open(account)
        try {
            withTimeout(5000) { session.state.first { "signal.link.Linked" in it.requirements } }
            failStream.complete(Unit)
            withTimeout(5000) { session.state.first { "signal.link.Unavailable" in it.requirements } }
            assertEquals(AuthState.Blocked, session.state.value.auth)
            assertFalse(session.state.value.toString().contains("sensitive upstream"))
        } finally { session.close() }
    }
}
