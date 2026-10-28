/* SPDX-License-Identifier: MIT */
package org.orbiliq.signal.bridge

import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject
import org.orbiliq.core.provider.signal.SignalMessagingContract
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.messages.SignalServiceMessageLimits

/** No protocol keys leave SignalStore. All exported payloads require a second native grant. */
internal class BridgeMessaging(private val context: Context) {
  companion object {
    val gate = Any()
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
      .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
  }
  private val grant = AtomicFile(File(context.noBackupFilesDir, "orbiliq-message-grant-v1"))
  private fun identity(recipient: Recipient): String = if (recipient.isSelf) "self" else
    recipient.serviceId.orElse(null)?.let { "service:" + hash(it.toString()) } ?: recipient.id.toLong().toString()
  private fun canSendNormal(recipient: Recipient, thread: Long): Boolean =
    !recipient.isGroup && recipient.isRegistered && !recipient.isBlocked && recipient.expiresInSeconds == 0 &&
      (SignalStore.settings.universalExpireTimer == 0 || !SignalDatabase.messages.canSetUniversalTimer(thread))
  private fun currentBinding(): String {
    check(!KeyCachingService.isLocked(context))
    check(SignalStore.account.isRegistered && !SignalStore.account.isPrimaryDevice && !SignalStore.account.isUnauthorizedReceived)
    return hash("orbiliq-signal-v1|${SignalStore.account.requireAci()}|${SignalStore.account.deviceId}|${SignalStore.account.registeredAtTimestamp}")
  }
  fun approve() = synchronized(gate) {
    check(BridgeAccess(context).hasConsent())
    val bytes = ("scope-v2:" + currentBinding() + ":" + BridgeAccess(context).currentClientSigner()).toByteArray(Charsets.UTF_8)
    val stream = grant.startWrite()
    try { stream.write(bytes); grant.finishWrite(stream) }
    catch (e: Exception) { grant.failWrite(stream); throw e }
  }
  fun revoke() = synchronized(gate) { grant.delete() }
  private fun authorizedBinding(): String {
    check(BridgeAccess(context).hasConsent())
    val binding = currentBinding()
    check(grant.readFully().toString(Charsets.UTF_8) == "scope-v2:" + binding + ":" + BridgeAccess(context).currentClientSigner())
    return binding
  }
  fun call(method: String, extras: Bundle): Bundle = synchronized(gate) {
    require(extras.getInt("messageProtocolVersion", -1) == SignalMessagingContract.VERSION)
    val binding = authorizedBinding()
    if (method != "messageSession") check(extras.getString("accountBinding") == binding)
    when (method) {
      "messageSession" -> Bundle().apply { putString("accountBinding", binding) }
      "messageThreads" -> {
        val rows = arrayListOf<Bundle>()
        SignalDatabase.threads.readerFor(SignalDatabase.threads.getRecentPushConversationList(SignalMessagingContract.MAX_THREADS - 1)).use { reader ->
          while (true) {
            val record = reader.getNext() ?: break
            val recipient = record.recipient
            // Groups/media/ephemeral content remain in the native companion until implemented.
            if (!recipient.isIndividual && !recipient.isSelf && !(recipient.isGroup && recipient.isActiveGroup)) continue
            rows += Bundle().apply {
              putLong("id", record.threadId)
              putString("title", recipient.getDisplayName(context).take(512))
              putBoolean("direct", !recipient.isGroup)
              putString("peer", identity(recipient))
              putBoolean("canSend", canSendNormal(recipient, record.threadId))
            }
          }
        }
        val self = Recipient.self()
        val selfThread = SignalDatabase.threads.getOrCreateThreadIdFor(self)
        if (rows.none { it.getLong("id") == selfThread }) rows += Bundle().apply {
          putLong("id", selfThread); putString("title", "Notiz an mich")
          putBoolean("direct", true); putString("peer", "self")
          putBoolean("canSend", canSendNormal(self, selfThread))
        }
        val afterThread = extras.getLong("afterThread", 0L)
        require(afterThread >= 0)
        val remaining = rows.filter { it.getLong("id") > afterThread }.sortedBy { it.getLong("id") }
        val page = ArrayList(remaining.take(SignalMessagingContract.PAGE_SIZE))
        Bundle().apply {
          putParcelableArrayList("rows", page)
          if (remaining.size > page.size) putLong("nextAfterThread", page.last().getLong("id"))
        }
      }
      "messageTexts" -> {
        val thread = extras.getLong("thread")
        require(thread > 0)
        val beforeId = extras.getLong("beforeId", Long.MAX_VALUE)
        require(beforeId > 0)
        val recipient = SignalDatabase.threads.getRecipientForThreadId(thread)
        check(recipient != null && (recipient.isIndividual || recipient.isSelf || (recipient.isGroup && recipient.isActiveGroup)))
        val rows = arrayListOf<Bundle>()
        var scanned = 0
        var lastId = 0L
        MessageTable.mmsReaderFor(SignalDatabase.messages.getOrbiliqConversationPage(thread, beforeId, SignalMessagingContract.PAGE_SIZE)).use { reader ->
          while (true) {
            val message = reader.getNext() ?: break
            scanned++; lastId = message.id
            export(message)?.let { rows += it }
          }
        }
        Bundle().apply {
          putParcelableArrayList("rows", rows)
          if (scanned == SignalMessagingContract.PAGE_SIZE) putLong("nextBeforeId", lastId)
        }
      }
      "messageLookup" -> {
        val thread = extras.getLong("thread")
        val ids = requireNotNull(extras.getLongArray("ids"))
        require(thread > 0 && ids.size <= SignalMessagingContract.PAGE_SIZE && ids.all { it > 0 })
        val rows = arrayListOf<Bundle>()
        for (id in ids) SignalDatabase.messages.getMessageRecordOrNull(id)?.takeIf { it.threadId == thread }?.let { export(it) }?.let { rows += it }
        Bundle().apply { putParcelableArrayList("rows", rows) }
      }
      "messageSend", "messageReconcile" -> send(binding, method, extras)
      "messageReact" -> react(extras)
      else -> error("Unsupported bridge operation")
    }.apply { putInt("messageProtocolVersion", SignalMessagingContract.VERSION) }
  }
  private fun exportable(message: MessageRecord): Boolean {
    val recipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId) ?: return false
    return (recipient.isIndividual || recipient.isSelf || (recipient.isGroup && recipient.isActiveGroup)) && recipient.expiresInSeconds == 0 &&
      message.isSecure && message.isLatestRevision && !message.isViewOnce && !message.isRemoteDelete && message.expiresIn == 0L && !message.isUpdate
  }
  /** Local id of the quoted original, only when it is itself exportable from the same thread. */
  private fun quoted(message: MessageRecord): Long? {
    val quote = (message as? MmsMessageRecord)?.quote?.takeIf { !it.isOriginalMissing && it.id > 0 } ?: return null
    return SignalDatabase.messages.getMessageFor(quote.id, quote.author)
      ?.takeIf { it.threadId == message.threadId && it.id != message.id && exportable(it) }?.id
  }
  private fun export(message: MessageRecord): Bundle? {
    if (!exportable(message)) return null
    val supported = message.body.isNotEmpty() && message.body.length <= SignalMessagingContract.MAX_TEXT &&
      message.body.toByteArray(Charsets.UTF_8).size <= SignalServiceMessageLimits.MAX_INLINE_BODY_SIZE_BYTES &&
      SignalDatabase.attachments.getAttachmentsForMessage(message.id).all { it.quote }
    return Bundle().apply {
      putLong("id", message.id); putLong("thread", message.threadId)
      putString("sender", identity(message.fromRecipient))
      putString("senderName", message.fromRecipient.getDisplayName(context).take(512))
      putString("text", if (supported) message.body else ""); putBoolean("supported", supported); putBoolean("outgoing", message.isOutgoing)
      putLong("timestamp", message.dateSent); putLong("received", message.dateReceived)
      quoted(message)?.let { putLong("replyTo", it) }
      val reactions = SignalDatabase.reactions.getReactions(MessageId(message.id)).take(SignalMessagingContract.MAX_REACTIONS)
      putStringArrayList("reactionEmoji", ArrayList(reactions.map { it.emoji }))
      putStringArrayList("reactionActor", ArrayList(reactions.map { identity(Recipient.resolved(it.author)) }))
      putString("delivery", when {
        message.isFailed -> "Failed"
        message.hasReadReceipt() -> "Read"
        message.hasDeliveryReceipt() -> "Delivered"
        message.isSent || !message.isOutgoing -> "Sent"
        else -> "Pending"
      })
    }
  }
  /** Own reaction on an exportable message of a direct chat; an identical existing reaction is never resent. */
  private fun react(extras: Bundle): Bundle {
    val thread = extras.getLong("thread")
    val id = extras.getLong("message")
    val emoji = requireNotNull(extras.getString("emoji"))
    require(thread > 0 && id > 0 && emoji.isNotBlank() && emoji.length <= 32)
    val recipient = SignalDatabase.threads.getRecipientForThreadId(thread)
    val message = SignalDatabase.messages.getMessageRecordOrNull(id)
    if (recipient == null || (!recipient.isIndividual && !recipient.isSelf) || !recipient.isRegistered || recipient.isBlocked ||
        message == null || message.threadId != thread || !exportable(message))
      return Bundle().apply { putString("result", "Rejected") }
    val own = SignalDatabase.reactions.getReactions(MessageId(id)).find { it.author == Recipient.self().id }
    if (own?.emoji != emoji) MessageSender.sendNewReaction(context, MessageId(id), emoji)
    return Bundle().apply { putString("result", "Queued"); putLong("messageId", id) }
  }
  private fun send(binding: String, method: String, extras: Bundle): Bundle {
    val command = requireNotNull(extras.getString("commandId"))
    require(command.length in 1..128)
    val thread = extras.getLong("thread")
    require(thread > 0)
    val ledger = Ledger(context, binding)
    val state = ledger.read()
    val key = hash(command)
    var entry = state.optJSONObject(key)
    val replyTo = extras.getLong("replyTo", 0L)
    require(replyTo >= 0)
    if (entry != null) {
      check(entry.getLong("thread") == thread && entry.optLong("replyTo", 0L) == replyTo)
      if (method == "messageSend") check(entry.getString("bodyHash") == hash(requireNotNull(extras.getString("text"))))
      return find(entry)
    }
    if (method == "messageReconcile") return Bundle().apply { putString("result", "Unknown") }
    val text = requireNotNull(extras.getString("text"))
    require(text.isNotEmpty() && text.length <= SignalMessagingContract.MAX_TEXT)
    require(text.toByteArray(Charsets.UTF_8).size <= SignalServiceMessageLimits.MAX_INLINE_BODY_SIZE_BYTES)
    val recipient = SignalDatabase.threads.getRecipientForThreadId(thread)
    if (recipient == null || (!recipient.isIndividual && !recipient.isSelf) || !canSendNormal(recipient, thread))
      return Bundle().apply { putString("result", "Rejected") }
    // Quote only an exportable, normal text message of this very thread; never media/ephemeral originals.
    val quote = if (replyTo == 0L) null else {
      val original = SignalDatabase.messages.getMessageRecordOrNull(replyTo)
      if (original == null || original.threadId != thread || !exportable(original) || original.body.isEmpty() ||
          !SignalDatabase.attachments.getAttachmentsForMessage(original.id).all { it.quote })
        return Bundle().apply { putString("result", "Rejected") }
      QuoteModel(original.dateSent, if (original.isOutgoing) Recipient.self().id else original.fromRecipient.id,
        original.body, false, null, emptyList(), QuoteModel.Type.NORMAL, null)
    }
    check(state.length() < 100_000)
    val timestamp = maxOf(System.currentTimeMillis(), state.optLong("lastTimestamp", 0L) + 1)
    entry = JSONObject().put("thread", thread).put("timestamp", timestamp).put("bodyHash", hash(text)).put("replyTo", replyTo)
    state.put(key, entry).put("lastTimestamp", timestamp)
    ledger.write(state) // Durable claim BEFORE any potentially effective send; never auto-resend a claim.
    MessageSender.send(context, OutgoingMessage(threadRecipient = recipient, sentTimeMillis = timestamp, body = text, isSecure = true, outgoingQuote = quote),
      thread, MessageSender.SendType.SIGNAL, null, null)
    return find(entry)
  }
  private fun find(entry: JSONObject): Bundle {
    val matches = SignalDatabase.messages.getMessagesBySentTimestamp(entry.getLong("timestamp")).filter {
      it.isOutgoing && it.threadId == entry.getLong("thread") && hash(it.body) == entry.getString("bodyHash")
    }
    return Bundle().apply {
      putString("result", if (matches.size == 1) "Queued" else "Unknown")
      if (matches.size == 1) putLong("messageId", matches.single().id)
    }
  }
}

/** Metadata-only command ledger, encrypted with an independent non-exportable Keystore key. */
private class Ledger(context: Context, private val binding: String) {
  private val directory = context.noBackupFilesDir
  private val file = AtomicFile(File(context.noBackupFilesDir, "orbiliq-send-${binding}.bin"))
  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val alias = "org.orbiliq.signal.bridge.ledger.v1"
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    check(directory.listFiles().orEmpty().none { it.name.startsWith("orbiliq-send-") })
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
      init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
    }.generateKey()
  }
  fun read(): JSONObject {
    val bytes = try { file.readFully() } catch (_: java.io.FileNotFoundException) { return JSONObject() }
    require(bytes.size in 29..16_777_216)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
    cipher.updateAAD(binding.toByteArray())
    val plain = cipher.doFinal(bytes, 12, bytes.size - 12)
    return try { JSONObject(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
  }
  fun write(state: JSONObject) {
    val plain = state.toString().toByteArray()
    require(plain.size <= 16_000_000)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(binding.toByteArray())
    val bytes = try { cipher.iv + cipher.doFinal(plain) } finally { plain.fill(0) }
    val stream = file.startWrite()
    try { stream.write(bytes); file.finishWrite(stream) }
    catch (e: Exception) { file.failWrite(stream); throw e }
  }
}
