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
    private fun images(row: Bundle): List<SignalImage> {
        val ids = row.getLongArray("imageIds") ?: return emptyList()
        val types = requireNotNull(row.getStringArrayList("imageTypes"))
        val sizes = requireNotNull(row.getLongArray("imageSizes"))
        require(ids.size == types.size && ids.size == sizes.size)
        return ids.indices.map { SignalImage(ids[it], types[it], sizes[it]) }
    }
    private fun files(row: Bundle): List<SignalFile> {
        val ids = row.getLongArray("fileIds") ?: return emptyList()
        val types = requireNotNull(row.getStringArrayList("fileTypes"))
        val sizes = requireNotNull(row.getLongArray("fileSizes"))
        val kinds = requireNotNull(row.getStringArrayList("fileKinds"))
        require(ids.size == types.size && ids.size == sizes.size && ids.size == kinds.size)
        return ids.indices.map { SignalFile(ids[it], types[it], sizes[it], kinds[it]) }
    }
    override suspend fun stageFile(binding: SignalBinding, token: String, bytes: ByteArray, mimeType: String, name: String?): Boolean =
        stage(binding, token, bytes) { putString("fileType", mimeType); name?.let { putString("fileName", it) } }
    @Suppress("DEPRECATION")
    override suspend fun attachment(binding: SignalBinding, thread: Long, message: Long, attachment: Long): Pair<String, ByteArray>? = withContext(Dispatchers.IO) {
        val result = call("messageImage", binding, Bundle().apply { putLong("thread", thread); putLong("message", message); putLong("attachment", attachment) })
        val mime = requireNotNull(result.getString("mimeType"))
        val stream = requireNotNull(result.getParcelable<android.os.ParcelFileDescriptor>("stream"))
        android.os.ParcelFileDescriptor.AutoCloseInputStream(stream).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                check(out.size() <= SignalMessagingContract.MAX_FILE_BYTES)
            }
            out.toByteArray().takeIf { it.isNotEmpty() }?.let { mime to it }
        }
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
            it.getLong("replyTo").takeIf { id -> id > 0 }, reactions(it), images(it), files(it), it.getBoolean("canEdit"), it.getBoolean("canDelete"))
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
    override suspend fun sendText(binding: SignalBinding, commandId: String, thread: Long, text: String, replyTo: Long?, image: String?, edit: Long?): SignalSendResult = result(call("messageSend", binding,
        Bundle().apply { putString("commandId", commandId); putLong("thread", thread); putString("text", text); replyTo?.let { putLong("replyTo", it) }; image?.let { putString("image", it) }; edit?.let { putLong("edit", it) } }))
    override suspend fun deleteForEveryone(binding: SignalBinding, thread: Long, message: Long): SignalSendResult = result(call("messageDelete", binding,
        Bundle().apply { putLong("thread", thread); putLong("message", message) }))
    override suspend fun stageImage(binding: SignalBinding, token: String, jpeg: ByteArray): Boolean = stage(binding, token, jpeg) {}
    private suspend fun stage(binding: SignalBinding, token: String, bytes: ByteArray, extra: Bundle.() -> Unit): Boolean {
        require(bytes.isNotEmpty() && bytes.size <= SignalMessagingContract.MAX_FILE_BYTES)
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(bytes.size, offset + SignalMessagingContract.STAGE_CHUNK_BYTES)
            val result = call("messageStageImage", binding, Bundle().apply {
                putString("token", token); putLong("total", bytes.size.toLong()); putLong("offset", offset.toLong())
                putByteArray("chunk", bytes.copyOfRange(offset, end)); extra()
            }).getString("result")
            offset = end
            if (offset == bytes.size) return result == "Staged"
            check(result == "Partial")
        }
        return false
    }
    override suspend fun reconcileText(binding: SignalBinding, commandId: String, thread: Long): SignalSendResult = result(call("messageReconcile", binding,
        Bundle().apply { putString("commandId", commandId); putLong("thread", thread) }))
    override suspend fun react(binding: SignalBinding, thread: Long, message: Long, emoji: String): SignalSendResult = result(call("messageReact", binding,
        Bundle().apply { putLong("thread", thread); putLong("message", message); putString("emoji", emoji) }))
}
