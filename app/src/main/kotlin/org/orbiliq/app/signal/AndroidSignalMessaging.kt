package org.orbiliq.app.signal

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.orbiliq.core.provider.signal.*

/** Application-scoped IPC, independent from Activity lifecycle. No raw errors escape. */
internal class AndroidSignalMessaging(private val context: Context) : SignalMessagingTransport {
    private val uri = Uri.parse("content://${SignalBridgeContract.AUTHORITY}")
    @Suppress("DEPRECATION")
    private fun trusted(): Boolean {
        val pm = context.packageManager
        val peer = pm.resolveContentProvider(SignalBridgeContract.AUTHORITY, 0) ?: return false
        fun signers(name: String): Set<String> {
            val info = pm.getPackageInfo(name, if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        }
        return peer.packageName == SignalBridgeContract.PACKAGE && peer.exported &&
            peer.readPermission == SignalBridgeContract.PERMISSION && peer.writePermission == SignalBridgeContract.PERMISSION &&
            SignalBridgeTrust.permits(SignalBridgeContract.PACKAGE, pm.getPackagesForUid(peer.applicationInfo.uid)?.toSet().orEmpty(),
                signers(context.packageName), signers(peer.packageName))
    }
    private suspend fun call(method: String, binding: SignalBinding? = null, extras: Bundle = Bundle()): Bundle = withContext(Dispatchers.IO) {
        try {
            check(trusted())
            extras.putInt("messageProtocolVersion", SignalMessagingContract.VERSION)
            binding?.let { extras.putString("accountBinding", it.id) }
            val result = context.contentResolver.acquireUnstableContentProviderClient(uri)?.use { it.call(method, null, extras) }
            check(result != null && result.getInt("messageProtocolVersion", -1) == SignalMessagingContract.VERSION && !result.containsKey("error"))
            result
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { throw IllegalStateException("signal.messaging.unavailable") }
    }
    override suspend fun binding(): SignalBinding? = try { SignalBinding(requireNotNull(call("messageSession").getString("accountBinding"))) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { null }
    @Suppress("DEPRECATION")
    private fun rows(bundle: Bundle): List<Bundle> = requireNotNull(bundle.getParcelableArrayList<Bundle>("rows")).also {
        require(it.size <= SignalMessagingContract.MAX_THREADS)
    }
    override suspend fun threads(binding: SignalBinding): List<SignalThread> {
        val result = mutableListOf<SignalThread>()
        var after = 0L
        do {
            val bundle = call("messageThreads", binding, Bundle().apply { putLong("afterThread", after) })
            val page = rows(bundle).map {
                SignalThread(it.getLong("id"), requireNotNull(it.getString("title")), it.getBoolean("direct"), it.getBoolean("canSend"), requireNotNull(it.getString("peer")))
            }
            require(page.size <= SignalMessagingContract.PAGE_SIZE && page.all { it.id > after })
            result += page
            require(result.size <= SignalMessagingContract.MAX_THREADS)
            val next = bundle.getLong("nextAfterThread")
            if (next == 0L) break
            require(next > after)
            after = next
        } while (true)
        return result
    }
    private fun reactions(row: Bundle): List<SignalReaction> {
        val emoji = row.getStringArrayList("reactionEmoji").orEmpty()
        val actors = row.getStringArrayList("reactionActor").orEmpty()
        require(emoji.size == actors.size)
        return actors.zip(emoji) { actor, value -> SignalReaction(actor, value) }
    }
    private fun decode(bundle: Bundle): List<SignalText> = rows(bundle).also { require(it.size <= SignalMessagingContract.PAGE_SIZE) }.map {
        SignalText(it.getLong("id"), it.getLong("thread"), requireNotNull(it.getString("sender")), requireNotNull(it.getString("text")),
            it.getBoolean("outgoing"), it.getLong("timestamp"), it.getLong("received"), requireNotNull(it.getString("delivery")), it.getBoolean("supported", true), it.getString("senderName").orEmpty(),
            it.getLong("replyTo").takeIf { id -> id > 0 }, reactions(it))
    }
    override suspend fun texts(binding: SignalBinding, thread: Long, beforeId: Long): SignalTextPage {
        val result = call("messageTexts", binding, Bundle().apply { putLong("thread", thread); putLong("beforeId", beforeId) })
        return SignalTextPage(decode(result), result.getLong("nextBeforeId").takeIf { it > 0 })
    }
    override suspend fun lookup(binding: SignalBinding, thread: Long, ids: List<Long>): List<SignalText> = decode(call("messageLookup", binding,
        Bundle().apply { putLong("thread", thread); putLongArray("ids", ids.toLongArray()) }))
    private fun result(bundle: Bundle): SignalSendResult = when (bundle.getString("result")) {
        "Queued" -> SignalSendResult.Queued(bundle.getLong("messageId"))
        "Rejected" -> SignalSendResult.Rejected
        else -> SignalSendResult.Unknown
    }
    override suspend fun sendText(binding: SignalBinding, commandId: String, thread: Long, text: String, replyTo: Long?): SignalSendResult = result(call("messageSend", binding,
        Bundle().apply { putString("commandId", commandId); putLong("thread", thread); putString("text", text); replyTo?.let { putLong("replyTo", it) } }))
    override suspend fun reconcileText(binding: SignalBinding, commandId: String, thread: Long): SignalSendResult = result(call("messageReconcile", binding,
        Bundle().apply { putString("commandId", commandId); putLong("thread", thread) }))
    override suspend fun react(binding: SignalBinding, thread: Long, message: Long, emoji: String): SignalSendResult = result(call("messageReact", binding,
        Bundle().apply { putLong("thread", thread); putLong("message", message); putString("emoji", emoji) }))
}
