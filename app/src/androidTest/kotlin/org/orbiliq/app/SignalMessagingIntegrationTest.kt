package org.orbiliq.app

import android.net.Uri
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.orbiliq.app.signal.AndroidSignalMessaging
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.signal.*

/** Explicit real-device tests. No credentials, private contents or bindings in assertion output. */
class SignalMessagingIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()

    @Test fun statusConsentDoesNotPermitMessaging() = runBlocking {
        assumeTrue("Requires explicit denied-grant fixture", args.getString("signalMessaging") == "denied")
        assertNull(AndroidSignalMessaging(context).binding())
    }

    @Test fun consentedBindingIsStableAndForeignBindingCannotRead() = runBlocking {
        assumeTrue("Requires native message consent", args.getString("signalMessaging") == "granted")
        val peer = AndroidSignalMessaging(context)
        val binding = withTimeout(15_000) {
            var value = peer.binding()
            while (value == null) { kotlinx.coroutines.delay(200); value = peer.binding() }
            value
        }
        assertTrue(peer.binding()?.id == binding.id)
        val threads = peer.threads(binding)
        assertTrue(threads.size <= SignalMessagingContract.MAX_THREADS)
        assertTrue(threads.filterNot { it.direct }.all { !it.canSend })
        val wrong = if (binding.id == "00".repeat(32)) "11".repeat(32) else "00".repeat(32)
        val denied = context.contentResolver.call(Uri.parse("content://${SignalBridgeContract.AUTHORITY}"), "messageThreads", null,
            Bundle().apply { putInt("messageProtocolVersion", SignalMessagingContract.VERSION); putString("accountBinding", wrong) })!!
        assertTrue(denied.keySet() == setOf("error"))
        assertTrue(denied.getString("error") == "Unavailable")
    }

    @Test fun selfTextTraversesPersistentOutboxNativeQueueAndProjectionOnce() = runBlocking {
        assumeTrue("Explicit self-send opt-in required", args.getString("signalSelfSend") == "true")
        val repository = EntryPointAccessors.fromApplication(context, ConversationRepositoryEntryPoint::class.java).conversations() as MessengerRepository
        val snapshot = withTimeout(60_000) { repository.observeMessenger().first { s ->
            s.accounts.any { account -> account.ownIdentityId != null && s.entries.any { it.route.conversation.participants == setOf(account.ownIdentityId) } }
        } }
        val own = snapshot.accounts.single().ownIdentityId!!
        val route = snapshot.entries.single { it.route.conversation.participants == setOf(own) }.route
        val target = ConversationTarget.ProviderChat(route.conversation.ref)
        val marker = "Orbiliq Signal Integrationstest ${UUID.randomUUID()}"
        val accepted = repository.sendText(target, route, marker)
        assertTrue("Self text was not queued", accepted is SendTextResult.Queued)
        val commandId = (accepted as SendTextResult.Queued).commandId
        val timeline = withTimeout(90_000) { repository.observeTimeline(target).first { timeline ->
            timeline.messages.any { (it.content as? MessageContent.Text)?.text == marker && it.ref != null &&
                it.delivery in setOf(DeliveryState.Sent, DeliveryState.Delivered, DeliveryState.Read) }
        } }
        assertTrue("Own test message was duplicated", timeline.messages.count { (it.content as? MessageContent.Text)?.text == marker } == 1)
        val peer = AndroidSignalMessaging(context)
        val binding = requireNotNull(peer.binding())
        assertTrue(SignalMessagingSession.accountFor(binding).id == route.conversation.ref.accountId)
        val repeat = peer.sendText(binding, commandId.value, route.conversation.ref.remoteConversationId.toLong(), marker)
        val original = timeline.messages.single { (it.content as? MessageContent.Text)?.text == marker }.ref!!
        assertTrue("Native duplicate claim was not reconciled", repeat is SignalSendResult.Queued && repeat.messageId.toString() == original.remoteMessageId)
    }
}
