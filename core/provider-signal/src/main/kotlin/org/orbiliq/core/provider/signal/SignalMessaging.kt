// SPDX-License-Identifier: MIT
package org.orbiliq.core.provider.signal

/** Separate opt-in protocol. The v1 status grant never permits these operations. */
object SignalMessagingContract {
    const val VERSION = 2
    const val MAX_TEXT = 4000
    const val PAGE_SIZE = 50
    const val MAX_THREADS = 1000
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
) {
    init {
        require(id > 0 && thread > 0 && timestamp > 0 && received > 0)
        require(if (supported) text.isNotEmpty() && text.length <= SignalMessagingContract.MAX_TEXT else text.isEmpty())
        require(sender == "self" || sender.matches(Regex("[0-9]+|service:[0-9a-f]{64}")))
        require(senderName.length <= 512)
        require(delivery in setOf("Pending", "Sent", "Delivered", "Read", "Failed"))
    }
    override fun toString() = "SignalText(redacted)"
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
    suspend fun sendText(binding: SignalBinding, commandId: String, thread: Long, text: String): SignalSendResult
    suspend fun reconcileText(binding: SignalBinding, commandId: String, thread: Long): SignalSendResult
}
