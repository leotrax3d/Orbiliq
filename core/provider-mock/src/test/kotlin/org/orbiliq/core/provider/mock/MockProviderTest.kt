package org.orbiliq.core.provider.mock

import java.time.Duration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

@OptIn(ExperimentalCoroutinesApi::class)
class MockProviderTest {
    private fun session(profile: MockProfile = MockProfile.Full) = MockProviderFactory(MockConfiguration(profile, requireLogin = false)).createSession()
    private fun command(s: MockProviderSession, payload: CommandPayload = CommandPayload.SendText("Hallo"), id: String = "cmd", route: ConversationRef = s.directRoute) =
        ProviderCommand(CommandId(id), route, payload, s.currentTime, s.currentTime.plusSeconds(60), s.capabilityRevision)
    private suspend fun send(s: MockProviderSession, id: String = "cmd"): MessageRef =
        (s.execute(command(s, id = id)) as DispatchResult.Confirmed).remoteMessage!!
    private fun rejected(result: DispatchResult, category: FailureCategory) = assertEquals(category, (result as DispatchResult.Rejected).failure.category)

    @Test fun loginRejectsWrongExpiredAndOfflineInputThenSeedsOnce() = runTest {
        val s = MockProviderFactory().createSession()
        assertEquals(AuthState.NeedsInput, s.state.value.auth)
        assertTrue(s.messages.value.isEmpty())
        suspend fun login(code: String = "000000", challenge: String = "mock-login", kind: AuthInputKind = AuthInputKind.Code): AuthResult =
            AuthResponse(challenge, kind, code.toCharArray()).use { s.authenticate(it) }
        assertTrue(login("123456") is AuthResult.Rejected)
        assertTrue(login(challenge = "foreign") is AuthResult.Rejected)
        assertTrue(login(kind = AuthInputKind.Password) is AuthResult.Rejected)
        s.setOnline(false); assertTrue(login() is AuthResult.Rejected)
        s.setOnline(true); s.advanceBy(Duration.ofMinutes(5)); assertTrue(login() is AuthResult.Rejected)
        s.restartLogin(); assertEquals(AuthResult.Ready, login())
        assertEquals(3, s.identities.value.size); assertEquals(2, s.conversations.value.size)
        assertEquals(3, s.groups.value.single().members.size)
        assertEquals(2, s.messages.value.size)
        assertTrue(login() is AuthResult.Rejected)
        s.restartLogin(); assertEquals(AuthResult.Ready, login()); assertEquals(2, s.messages.value.size)
    }
    @Test fun sendsToDirectAndGroupAndRepositoryUsesRealFixtureState() = runTest {
        val s = session()
        val inbox = s.observeInbox().first()
        assertEquals(2, inbox.size); assertTrue(inbox.all { it.title.startsWith("[Mock]") })
        val direct = send(s)
        val group = s.execute(command(s, id = "group", route = s.groupRoute)) as DispatchResult.Confirmed
        assertEquals(s.groupRoute, group.remoteMessage!!.conversation)
        assertEquals(MessageDirection.Outgoing, s.messages.value.first { it.ref == direct }.direction)
        assertEquals(2, s.timeline(s.directRoute).first().size)
    }
    @Test fun repliesReactionsTypingReceiptsEditsAndDeletesEmitTypedEvents() = runTest {
        val s = session(); val ref = send(s)
        val reply = s.execute(command(s, CommandPayload.SendText("Antwort", ref), "reply")) as DispatchResult.Confirmed
        assertEquals(s.messages.value.first { it.ref == ref }.id, s.messages.value.first { it.ref == reply.remoteMessage }.replyTo)
        assertTrue(s.execute(command(s, CommandPayload.React(ref, "👍"), "reaction")) is DispatchResult.Confirmed)
        assertTrue(s.execute(command(s, CommandPayload.SetTyping(true), "typing")) is DispatchResult.Confirmed)
        s.incomingTyping(); s.incomingReaction(ref, "👍"); s.incomingReaction(ref, "👍", removed = true)
        s.incomingReceipt(ref, DeliveryState.Delivered); s.incomingReceipt(ref, DeliveryState.Read, Duration.ofSeconds(2))
        s.advanceBy(Duration.ofSeconds(3))
        assertEquals(DeliveryState.Read, s.messages.value.first { it.ref == ref }.delivery)
        s.incomingReceipt(ref, DeliveryState.Delivered)
        assertEquals(DeliveryState.Read, s.messages.value.first { it.ref == ref }.delivery)
        assertTrue(s.execute(command(s, CommandPayload.EditText(ref, "Bearbeitet", "1"), "edit")) is DispatchResult.Confirmed)
        assertEquals("Bearbeitet", (s.messages.value.first { it.ref == ref }.content as MessageContent.Text).text)
        assertTrue(s.execute(command(s, CommandPayload.Delete(ref, true), "delete")) is DispatchResult.Confirmed)
        assertFalse(s.messages.value.any { it.ref == ref })
        val events = s.events().take(s.recordedEvents.size).toList().map { it.payload }
        assertTrue(events.any { it is ProviderEvent.ReactionChanged && it.removed })
        assertTrue(events.any { it is ProviderEvent.TypingChanged && !it.active })
        assertTrue(events.any { it is ProviderEvent.MessageDeleted })
    }
    @Test fun permissionsMissingTargetsAndStaleRevisionsFailWithoutEffect() = runTest {
        val s = session(); val incoming = s.messages.value.first().ref!!
        rejected(s.execute(command(s, CommandPayload.EditText(incoming, "No", "1"))), FailureCategory.PermissionDenied)
        rejected(s.execute(command(s, CommandPayload.Delete(incoming, true))), FailureCategory.PermissionDenied)
        rejected(s.execute(command(s, CommandPayload.React(MessageRef(s.directRoute, "missing"), "x"))), FailureCategory.ProtocolFailure)
        val ref = send(s)
        s.remoteEdit(ref, "Remote")
        rejected(s.execute(command(s, CommandPayload.EditText(ref, "Local", "1"), "edit")), FailureCategory.SyncConflict)
        assertEquals("Remote", (s.messages.value.first { it.ref == ref }.content as MessageContent.Text).text)
        assertTrue(s.execute(command(s, CommandPayload.EditText(ref, "Merged", "2"), "resolved")) is DispatchResult.Confirmed)
    }
    @Test fun offlineAndDelayedIncomingWaitForReconnectInStableOrder() = runTest {
        val s = session(); val original = s.messages.value.size
        s.receiveText(text = "first", delay = Duration.ofSeconds(2))
        s.receiveText(text = "second", delay = Duration.ofSeconds(2))
        s.advanceBy(Duration.ofSeconds(1)); assertEquals(original, s.messages.value.size)
        s.setOnline(false); s.advanceBy(Duration.ofSeconds(5))
        assertEquals(original, s.messages.value.size)
        assertTrue(s.execute(command(s)) is DispatchResult.RetryableNoEffect)
        s.setOnline(true)
        assertEquals(listOf("first", "second"), s.messages.value.takeLast(2).map { (it.content as MessageContent.Text).text })
        assertTrue(s.messages.value.last().serverTime!! < s.messages.value.last().receivedAt)
        assertTrue(s.execute(command(s)) is DispatchResult.Confirmed)
    }
    @Test fun faultQueueDistinguishesSafeRetryRateLimitAndUnknownEffects() = runTest {
        val s = session(); val initial = s.messages.value.size
        s.failNext(MockFault.NetworkNoEffect)
        assertTrue(s.execute(command(s)) is DispatchResult.RetryableNoEffect)
        assertEquals(initial, s.messages.value.size)
        s.failNext(MockFault.RateLimited)
        val limited = s.execute(command(s)) as DispatchResult.RetryableNoEffect
        assertEquals(s.currentTime.plusSeconds(30), limited.failure.retryAt)
        s.failNext(MockFault.UnknownNoEffect)
        assertTrue(s.execute(command(s)) is DispatchResult.OutcomeUnknown)
        assertEquals(ReconcileResult.ProvenNoEffect, s.reconcile(ReconcileRequest(CommandId("cmd"), s.directRoute)))
        s.failNext(MockFault.UnknownAfterCommit)
        val c = command(s)
        assertTrue(s.execute(c) is DispatchResult.OutcomeUnknown)
        assertEquals(initial + 1, s.messages.value.size)
        assertTrue(s.reconcile(ReconcileRequest(c.id, c.route)) is ReconcileResult.Confirmed)
        assertTrue(s.execute(c) is DispatchResult.Confirmed)
        assertEquals(initial + 1, s.messages.value.size)
    }
    @Test fun idempotencyKeysDependenciesAndCommandCollisionsAreChecked() = runTest {
        val s = session(); val c = command(s).copy(idempotencyKey = "key")
        val first = s.execute(c)
        assertEquals(first, s.execute(c.copy(id = CommandId("new"))))
        rejected(s.execute(c.copy(payload = CommandPayload.SendText("different"))), FailureCategory.ProtocolFailure)
        rejected(s.execute(command(s, id = "dependent").copy(dependencies = setOf(CommandId("missing")))), FailureCategory.ProtocolFailure)
        assertTrue(s.execute(command(s, id = "dependent").copy(dependencies = setOf(c.id))) is DispatchResult.Confirmed)
    }
    @Test fun capabilityProfilesAndLiveRevocationAreEnforced() = runTest {
        val s = session(MockProfile.TextOnly)
        val scope = CapabilityScope.Conversation(MockFixtures.providerId, s.directRoute)
        assertTrue(s.evaluate(OperationRequest(scope, Operations.SendText, textLength = 5)) is Availability.Available)
        assertTrue(s.evaluate(OperationRequest(scope, Operations.React)) is Availability.Unsupported)
        rejected(s.execute(command(s, CommandPayload.SetTyping(true))), FailureCategory.CapabilityUnavailable)
        val stale = command(s)
        s.setProfile(MockProfile.ReadOnly)
        rejected(s.execute(stale), FailureCategory.CapabilityUnavailable)
        assertTrue(s.observeCapabilities(scope).first().operations.getValue(Operations.SendText).availability is Availability.Unsupported)
        s.setProfile(MockProfile.Full)
        assertTrue(s.execute(command(s)) is DispatchResult.Confirmed)
        val unsupported = OperationRequest(scope, OperationKey("unknown"))
        assertTrue(s.evaluate(unsupported) is Availability.Unsupported)
    }
    @Test fun invalidSchemaExpiryScopeAndLengthNeverDispatch() = runTest {
        val s = session(); val c = command(s)
        rejected(s.execute(c.copy(schemaVersion = 2)), FailureCategory.IncompatibleProvider)
        rejected(s.execute(c.copy(createdAt = s.currentTime.minusSeconds(60), expiresAt = s.currentTime)), FailureCategory.PolicyExpired)
        rejected(s.execute(c.copy(createdAt = s.currentTime.plusSeconds(1))), FailureCategory.PolicyExpired)
        rejected(s.execute(c.copy(route = ConversationRef(AccountId("foreign"), "anna"))), FailureCategory.PermissionDenied)
        rejected(s.execute(c.copy(route = ConversationRef(s.accountId, "unknown"))), FailureCategory.PermissionDenied)
        rejected(s.execute(command(s, CommandPayload.SendText("x".repeat(4097)))), FailureCategory.CapabilityUnavailable)
        assertTrue(s.evaluate(OperationRequest(CapabilityScope.Provider(ProviderId("foreign")), Operations.SendText)) is Availability.Restricted)
    }
    @Test fun replayReconnectAndSlowConsumersLoseNoEvents() = runTest {
        val s = session(); val initial = s.events().take(8).toList()
        val cursor = initial.last().cursor
        assertTrue(s.acknowledge(cursor) is OperationResult.Success)
        val collected = mutableListOf<EventEnvelope>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            s.events(cursor).collect { delay(10); collected += it }
        }
        repeat(30) { s.receiveText(text = "burst $it") }
        advanceTimeBy(301); runCurrent()
        assertEquals(30, collected.size)
        assertEquals(30, collected.map { it.eventId }.distinct().size)
        collector.cancel()
        assertTrue(s.acknowledge(cursor.copy(streamId = "foreign")) is OperationResult.Rejected)
        assertTrue(s.acknowledge(cursor.copy(token = "9999")) is OperationResult.Rejected)
        assertTrue(s.acknowledge(cursor.copy(epoch = "old")) is OperationResult.Rejected)
        assertThrows(IllegalStateException::class.java) { s.events(cursor.copy(token = "0")) }
        assertEquals(collected, s.events(cursor).take(30).toList())
    }
    @Test fun syncGapResyncAndRemoteDeleteKeepProjectionConsistent() = runTest {
        val s = session(); val ref = s.messages.value.first().ref!!
        s.simulateSyncGap(); assertEquals(SyncState.Gap, s.state.value.sync)
        s.remoteDelete(ref); s.resync()
        assertEquals(SyncState.Idle, s.state.value.sync)
        assertFalse(s.messages.value.any { it.ref == ref })
        assertEquals(1, s.messages.value.size)
    }
    @Test fun accountSessionsFactoryRegistryAndCloseAreIsolated() = runTest {
        val factory = MockProviderFactory(MockConfiguration(requireLogin = false))
        val registry = ProviderRegistry(listOf(factory))
        val a = registry.find(MockFixtures.providerId)!!.open(MockFixtures.handle()) as MockProviderSession
        val b = factory.open(MockFixtures.handle(MockFixtures.account.copy(id = AccountId("b"))))
        assertNotEquals(a.epoch, b.epoch)
        assertNotEquals(a.messages.value.first().id, b.messages.value.first().id)
        val cursor = a.events().first().cursor
        assertTrue(b.acknowledge(cursor) is OperationResult.Rejected)
        a.receiveText(delay = Duration.ofSeconds(10)); a.close(); a.close()
        rejected(a.execute(command(a)), FailureCategory.PermissionDenied)
        assertTrue(a.media.upload(Bytes(byteArrayOf(1)), "image/png") is OperationResult.Rejected)
        assertTrue(b.execute(command(b)) is DispatchResult.Confirmed)
        assertThrows(IllegalArgumentException::class.java) { factory.createSession(MockFixtures.account.copy(providerId = ProviderId("other"))) }
    }
    @Test fun logoutRevokesCommandsAndFreshLoginWorks() = runTest {
        val s = session()
        assertTrue(s.execute(command(s, CommandPayload.Logout)) is DispatchResult.Confirmed)
        rejected(s.execute(command(s, id = "after")), FailureCategory.AuthRequired)
        s.restartLogin()
        AuthResponse("mock-login", AuthInputKind.Code, "000000".toCharArray()).use { assertEquals(AuthResult.Ready, s.authenticate(it)) }
        assertTrue(s.execute(command(s, id = "after")) is DispatchResult.Confirmed)
    }
    @Test fun reusableScenarioCatalogRunsWithoutNetworkOrWallClockWaits() = runTest {
        for (scenario in MockScenarios.Scenario.entries) {
            val s = session()
            val results = MockScenarios.run(s, scenario)
            when (scenario) {
                MockScenarios.Scenario.OfflineRecovery -> assertTrue(results.first() is DispatchResult.RetryableNoEffect)
                MockScenarios.Scenario.UnknownDelivery -> {
                    assertTrue(results.first() is DispatchResult.OutcomeUnknown)
                    assertEquals(3, s.messages.value.size)
                }
                MockScenarios.Scenario.RevisionConflict -> rejected(results.last(), FailureCategory.SyncConflict)
                MockScenarios.Scenario.Capabilities -> assertTrue(results.last() is DispatchResult.Confirmed)
                MockScenarios.Scenario.Conversation -> assertTrue(s.messages.value.any { it.delivery == DeliveryState.Read })
                MockScenarios.Scenario.Media -> assertEquals(AttachmentKind.entries.size, s.attachments.value.size)
                MockScenarios.Scenario.SyncGap -> assertEquals(SyncState.Idle, s.state.value.sync)
            }
            s.close()
        }
    }
    @Test fun delayedOutgoingRechecksCapabilitiesAndSupportsCancellation() = runTest {
        val factory = MockProviderFactory(MockConfiguration(requireLogin = false, dispatchDelayMillis = 1000))
        val s = factory.createSession()
        val pending = async { s.execute(command(s)) }
        runCurrent(); assertEquals(2, s.messages.value.size)
        s.setProfile(MockProfile.ReadOnly)
        advanceTimeBy(1000); runCurrent()
        rejected(pending.await(), FailureCategory.CapabilityUnavailable)
        s.setProfile(MockProfile.Full)
        val canceled = launch { s.execute(command(s, id = "cancel")) }
        runCurrent(); canceled.cancelAndJoin(); advanceTimeBy(1000)
        assertEquals(2, s.messages.value.size)
        assertTrue(s.execute(command(s, id = "success")) is DispatchResult.Confirmed)
    }
    @Test fun liveCapabilitiesNotifyCollectorsAndReadonlyStillReceivesMedia() = runTest {
        val s = session()
        val snapshots = mutableListOf<CapabilitySnapshot>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            s.observeCapabilities(CapabilityScope.Account(MockFixtures.providerId, s.accountId)).collect { snapshots += it }
        }
        s.setProfile(MockProfile.ReadOnly)
        s.receiveMedia(AttachmentKind.Image, byteArrayOf(1), "image/png")
        assertEquals(1, s.attachments.value.size)
        assertTrue(s.media.upload(Bytes(byteArrayOf(2)), "image/png") is OperationResult.Rejected)
        assertEquals(2, snapshots.size)
        assertNotEquals(snapshots[0].revision, snapshots[1].revision)
        job.cancel()
    }
    @Test fun mockAlsoWorksThroughCommonCheckedSessionBoundary() = runTest {
        val factory = MockProviderFactory(MockConfiguration(requireLogin = false))
        val s = factory.createSession()
        val guard = CheckedProviderSession(s, factory.descriptor, java.time.Clock.fixed(s.currentTime, java.time.ZoneOffset.UTC))
        assertTrue(guard.execute(command(s)) is DispatchResult.Confirmed)
        assertEquals(9, guard.events().take(9).toList().size)
        guard.close()
        rejected(guard.execute(command(s, id = "closed")), FailureCategory.PermissionDenied)
    }
    @Test fun messageCapabilitiesReflectSenderPermissions() = runTest {
        val s = session()
        val incoming = s.messages.value.first().ref!!
        val scope = CapabilityScope.Message(MockFixtures.providerId, incoming)
        assertEquals(Availability.Restricted(Restriction.PermissionDenied), s.evaluate(OperationRequest(scope, Operations.EditText, textLength = 3)))
        assertTrue(s.observeCapabilities(scope).first().operations.getValue(Operations.DeleteAll).availability is Availability.Restricted)
    }
    @Test fun mediaFailureAfterPartialSinkWriteDoesNotClaimNoEffect() = runTest {
        val s = session()
        val ref = (s.media.upload(Bytes(ByteArray(9000)), "image/png") as OperationResult.Success).value
        var written = 0
        val sink = object : MediaSink {
            override suspend fun write(chunk: ByteArray) { written += chunk.size; s.setOnline(false) }
            override suspend fun close() = Unit
        }
        assertTrue(s.media.download(ref, sink) is OperationResult.OutcomeUnknown)
        assertEquals(8192, written)
    }
    @Test fun activeEventCollectorsCompleteOnClose() = runTest {
        for (s in listOf(session(), MockProviderFactory().createSession())) {
            var completed = false
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { s.events().collect(); completed = true }
            runCurrent(); assertFalse(completed)
            s.close(); job.join(); assertTrue(completed)
        }
    }
    private class Bytes(private val bytes: ByteArray) : MediaSource {
        override val sizeBytes = bytes.size.toLong()
        private var offset = 0
        var closed = false
        override suspend fun read(maxBytes: Int): ByteArray {
            val end = minOf(bytes.size, offset + maxBytes)
            return bytes.copyOfRange(offset, end).also { offset = end }
        }
        override suspend fun close() { closed = true }
    }
    @Test fun mediaRoundTripBoundsOwnershipAndOfflineFailures() = runTest {
        val s = session(); val bytes = ByteArray(20000) { (it % 251).toByte() }
        val source = Bytes(bytes)
        val ref = (s.media.upload(source, "image/png") as OperationResult.Success).value
        assertFalse(source.closed)
        val attachment = s.attachments.value.single()
        assertEquals(ref, attachment.ref)
        val output = mutableListOf<Byte>(); var sinkClosed = false
        val sink = object : MediaSink {
            override suspend fun write(chunk: ByteArray) { assertTrue(chunk.size <= 8192); output.addAll(chunk.toList()) }
            override suspend fun close() { sinkClosed = true }
        }
        assertTrue(s.media.download(ref, sink) is OperationResult.Success)
        assertArrayEquals(bytes, output.toByteArray()); assertFalse(sinkClosed)
        assertTrue(s.execute(command(s, CommandPayload.SendMedia(attachment.id, AttachmentKind.Image))) is DispatchResult.Confirmed)
        assertTrue(s.media.download(ref.copy(accountId = AccountId("other")), sink) is OperationResult.Rejected)
        assertTrue(s.media.upload(Bytes(ByteArray(1024 * 1024 + 1)), "image/png") is OperationResult.Rejected)
        s.setOnline(false); assertTrue(s.media.download(ref, sink) is OperationResult.Rejected)
        s.setOnline(true)
        rejected(s.execute(command(s, CommandPayload.SendMedia(AttachmentId("unknown"), AttachmentKind.Image), "bad")), FailureCategory.MediaUnavailable)
        source.close(); sink.close()
    }
    @Test fun incomingMediaSupportsEveryKindAndRetainsBytes() = runTest {
        val s = session()
        AttachmentKind.entries.forEach { kind -> s.receiveMedia(kind, byteArrayOf(1,2,3), "application/mock") }
        assertEquals(AttachmentKind.entries.toSet(), s.attachments.value.map { it.kind }.toSet())
        assertEquals(AttachmentKind.entries.size, s.messages.value.count { it.content is MessageContent.Media })
    }
    @Test fun cancellationOfMediaSourcePropagatesWithoutCommittingAttachment() = runTest {
        val s = session()
        val source = object : MediaSource {
            override val sizeBytes: Long? = null
            override suspend fun read(maxBytes: Int): ByteArray = throw CancellationException("synthetic")
            override suspend fun close() = Unit
        }
        try { s.media.upload(source, "image/png"); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertTrue(s.attachments.value.isEmpty())
    }
}
