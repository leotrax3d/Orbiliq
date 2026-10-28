// SPDX-License-Identifier: MIT
package org.orbiliq.core.provider.signal

/** Separate opt-in protocol. The v1 status grant never permits these operations. */
object SignalMessagingContract {
    const val VERSION = 2
    const val MAX_TEXT = 2048
    const val MAX_TEXT_BYTES = 2048
    const val PAGE_SIZE = 50
    const val MAX_THREADS = 1000
    const val MAX_REACTIONS = 64
    const val MAX_IMAGES = 10
    const val MAX_IMAGE_BYTES = 16L * 1024 * 1024
    val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    const val STAGE_CHUNK_BYTES = 256 * 1024
    const val MAX_IMAGE_EDGE = 4096
}

class SignalBinding(val id: String) {
    init { require(id.matches(Regex("[0-9a-f]{64}"))) }
    override fun toString() = "SignalBinding(redacted)"
}
class SignalThread(val id: Long, val title: String, val direct: Boolean, val canSend: Boolean, val peer: String) {
    init { require(id > 0); require(title.length <= 512); require(peer == "self" || peer.matches(Regex("[0-9]+|service:[0-9a-f]{64}"))) }
    override fun toString() = "SignalThread(redacted)"
}
class SignalText(
    val id: Long, val thread: Long, val sender: String, val text: String,
    val outgoing: Boolean, val timestamp: Long, val received: Long, val delivery: String,
    val supported: Boolean = true,
    val senderName: String = "",
    /** Native id of the quoted original in the same thread; absent when unresolved or not exportable. */
    val replyTo: Long? = null,
    val reactions: List<SignalReaction> = emptyList(),
    val images: List<SignalImage> = emptyList(),
) {
    init {
        require(id > 0 && thread > 0 && timestamp > 0 && received > 0)
        require(if (supported) (text.isNotEmpty() || images.isNotEmpty()) && text.length <= SignalMessagingContract.MAX_TEXT else text.isEmpty() && images.isEmpty())
        require(images.size <= SignalMessagingContract.MAX_IMAGES && images.map { it.id }.distinct().size == images.size)
        require(!supported || text.toByteArray(Charsets.UTF_8).size <= SignalMessagingContract.MAX_TEXT_BYTES)
        require(sender == "self" || sender.matches(Regex("[0-9]+|service:[0-9a-f]{64}")))
        require(senderName.length <= 512)
        require(replyTo == null || (replyTo > 0 && replyTo != id))
        require(reactions.size <= SignalMessagingContract.MAX_REACTIONS && reactions.map { it.actor }.distinct().size == reactions.size)
        require(delivery in setOf("Pending", "Sent", "Delivered", "Read", "Failed"))
    }
    override fun toString() = "SignalText(redacted)"
}
/** Metadata only; bytes are streamed on explicit demand and never cached by the provider. */
class SignalImage(val id: Long, val mimeType: String, val sizeBytes: Long) {
    init { require(id > 0 && mimeType in SignalMessagingContract.IMAGE_TYPES && sizeBytes in 1..SignalMessagingContract.MAX_IMAGE_BYTES) }
    override fun toString() = "SignalImage(redacted)"
}
/** Signal permits one reaction per actor and message. */
class SignalReaction(val actor: String, val emoji: String) {
    init { require(actor == "self" || actor.matches(Regex("[0-9]+|service:[0-9a-f]{64}"))); require(emoji.isNotBlank() && emoji.length <= 32) }
    override fun toString() = "SignalReaction(redacted)"
}
sealed interface SignalSendResult {
    class Queued(val messageId: Long) : SignalSendResult { init { require(messageId > 0) } }
    data object Unknown : SignalSendResult
    data object Rejected : SignalSendResult
}
class SignalTextPage(val rows: List<SignalText>, val nextBeforeId: Long?) {
    init { require(rows.size <= SignalMessagingContract.PAGE_SIZE); require(nextBeforeId == null || nextBeforeId > 0) }
    override fun toString() = "SignalTextPage(redacted)"
}
/** All methods enforce the exact consented account binding again at the companion. */
interface SignalMessagingTransport {
    suspend fun binding(): SignalBinding?
    suspend fun threads(binding: SignalBinding): List<SignalThread>
    suspend fun texts(binding: SignalBinding, thread: Long, beforeId: Long = Long.MAX_VALUE): SignalTextPage
    suspend fun lookup(binding: SignalBinding, thread: Long, ids: List<Long>): List<SignalText>
    /** [replyTo] quotes a native message of the same thread; the companion validates it again. */
    suspend fun sendText(binding: SignalBinding, commandId: String, thread: Long, text: String, replyTo: Long? = null, image: String? = null): SignalSendResult
    suspend fun reconcileText(binding: SignalBinding, commandId: String, thread: Long): SignalSendResult
    /** Sets the own reaction (idempotent: an identical existing reaction is not resent). */
    suspend fun react(binding: SignalBinding, thread: Long, message: Long, emoji: String): SignalSendResult = SignalSendResult.Rejected
    /** Decrypted image bytes of one exportable message, bounded by [SignalMessagingContract.MAX_IMAGE_BYTES]. */
    suspend fun image(binding: SignalBinding, thread: Long, message: Long, attachment: Long): ByteArray? = null
    /** Hands a JPEG to the companion's encrypted store; true once fully staged under [token]. */
    suspend fun stageImage(binding: SignalBinding, token: String, jpeg: ByteArray): Boolean = false
}
