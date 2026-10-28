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
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.DocumentSlide
import org.thoughtcrime.securesms.mms.ImageSlide
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
    /** In-flight uploads only; a process death simply fails staging before anything is queued. */
    private val staging = HashMap<String, java.io.ByteArrayOutputStream>()
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
      .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
  }
  private val grant = AtomicFile(File(context.noBackupFilesDir, "orbiliq-message-grant-v1"))
  private fun identity(recipient: Recipient): String = if (recipient.isSelf) "self" else
    recipient.serviceId.orElse(null)?.let { "service:" + hash(it.toString()) } ?: recipient.id.toLong().toString()
  private fun canSendNormal(recipient: Recipient, thread: Long): Boolean =
    (if (recipient.isGroup) canWriteGroup(recipient) else recipient.isRegistered) && !recipient.isBlocked && recipient.expiresInSeconds == 0 &&
      (SignalStore.settings.universalExpireTimer == 0 || !SignalDatabase.messages.canSetUniversalTimer(thread))
  /** Same rules as the official composer: active v2 group, full member/admin, announcement groups admin-only. */
  private fun canWriteGroup(recipient: Recipient): Boolean {
    if (!recipient.isPushV2Group || !recipient.isActiveGroup) return false
    val group = SignalDatabase.groups.getGroup(recipient.id).orElse(null) ?: return false
    val level = group.memberLevel(Recipient.self())
    val admin = level == org.thoughtcrime.securesms.database.GroupTable.MemberLevel.ADMINISTRATOR
    return group.isActive && !group.isTerminated && (admin || level == org.thoughtcrime.securesms.database.GroupTable.MemberLevel.FULL_MEMBER) &&
      (!group.isAnnouncementGroup || admin)
  }
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
              val group = if (recipient.isPushV2Group) SignalDatabase.groups.getGroup(recipient.id).orElse(null) else null
              val admin = group?.memberLevel(Recipient.self()) == org.thoughtcrime.securesms.database.GroupTable.MemberLevel.ADMINISTRATOR
              putBoolean("canRename", group != null && group.isActive && !group.isTerminated &&
                (admin || group.attributesAccessControl == org.thoughtcrime.securesms.groups.GroupAccessControl.ALL_MEMBERS))
              putBoolean("canLeave", group != null && group.isActive && !group.isTerminated)
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
      "messageImage" -> image(extras)
      "messageStageImage" -> stage(binding, extras)
      "messageDelete" -> remoteDelete(extras)
      "messageDeleteLocal" -> localDelete(extras)
      "messageUnreact" -> unreact(extras)
      "messageRead" -> markRead(extras)
      "messageTyping" -> typing(extras)
      "groupRename" -> renameGroup(extras)
      "groupLeave" -> leaveGroup(extras)
      else -> error("Unsupported bridge operation")
    }.apply { putInt("messageProtocolVersion", SignalMessagingContract.VERSION) }
  }
  /**
   * Receives a JPEG in bounded chunks, re-encodes it here (authoritative EXIF/GPS removal), and keeps it
   * encrypted in Signal's own blob store until a send command references its token.
   */
  private fun stage(binding: String, extras: Bundle): Bundle {
    val token = requireNotNull(extras.getString("token"))
    require(token.matches(Regex("[0-9a-f]{32}")))
    val total = extras.getLong("total")
    val chunk = requireNotNull(extras.getByteArray("chunk"))
    val fileType = extras.getString("fileType")
    val fileName = extras.getString("fileName")?.take(128)?.replace(Regex("[/\\\\:*?\"<>|\u0000-\u001f]"), "_")
    val slide = extras.getString("slide") ?: "doc"
    require(slide in setOf("doc", "voice", "video"))
    require(fileType == null || (fileType.length in 3..128 && '/' in fileType && when (slide) {
      "voice" -> fileType.startsWith("audio/")
      "video" -> fileType == "video/mp4"
      else -> !fileType.startsWith("image/") && !fileType.startsWith("video/")
    }))
    require(total in 1..(if (fileType == null) SignalMessagingContract.MAX_IMAGE_BYTES else SignalMessagingContract.MAX_FILE_BYTES) &&
      chunk.size in 1..SignalMessagingContract.STAGE_CHUNK_BYTES)
    if (staging.size > 4 && token !in staging) staging.clear()
    val buffer = staging.getOrPut(token) { java.io.ByteArrayOutputStream() }
    check(extras.getLong("offset") == buffer.size().toLong() && buffer.size() + chunk.size <= total)
    buffer.write(chunk)
    if (buffer.size().toLong() < total) return Bundle().apply { putString("result", "Partial") }
    val bytes = staging.remove(token)!!.toByteArray()
    if (fileType != null) {
      val payload = if (slide == "video") remuxVideo(bytes).also { bytes.fill(0) } else bytes
      val uri = org.thoughtcrime.securesms.dependencies.AppDependencies.blobs.forData(payload).withMimeType(fileType)
        .createForMultipleSessionsOnDisk(context)
      val ledger = Ledger(context, binding)
      val state = ledger.read()
      state.put("staged:$token", JSONObject().put("uri", uri.toString()).put("size", payload.size.toLong()).put("type", fileType)
        .put("name", fileName ?: "Datei").put("slide", slide))
      ledger.write(state)
      bytes.fill(0)
      return Bundle().apply { putString("result", "Staged") }
    }
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    check(bounds.outMimeType == "image/jpeg" && bounds.outWidth in 1..SignalMessagingContract.MAX_IMAGE_EDGE &&
      bounds.outHeight in 1..SignalMessagingContract.MAX_IMAGE_EDGE)
    val bitmap = requireNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    val clean = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    val width = bitmap.width; val height = bitmap.height
    bitmap.recycle(); bytes.fill(0)
    check(clean.size <= SignalMessagingContract.MAX_IMAGE_BYTES)
    val uri = org.thoughtcrime.securesms.dependencies.AppDependencies.blobs.forData(clean).withMimeType("image/jpeg")
      .createForMultipleSessionsOnDisk(context)
    val ledger = Ledger(context, binding)
    val state = ledger.read()
    state.put("staged:$token", JSONObject().put("uri", uri.toString()).put("size", clean.size.toLong()).put("width", width).put("height", height))
    ledger.write(state)
    return Bundle().apply { putString("result", "Staged") }
  }
  private fun rejected() = Bundle().apply { putString("result", "Rejected") }
  private fun queued(id: Long) = Bundle().apply { putString("result", "Queued"); putLong("messageId", id) }
  /** Delete for me, synced to the user's other linked devices like the official client. */
  private fun localDelete(extras: Bundle): Bundle {
    val thread = extras.getLong("thread"); val id = extras.getLong("message")
    require(thread > 0 && id > 0)
    val message = SignalDatabase.messages.getMessageRecordOrNull(id) ?: return queued(id)
    if (message.threadId != thread || !exportable(message)) return rejected()
    SignalDatabase.messages.deleteMessage(id)
    org.thoughtcrime.securesms.jobs.MultiDeviceDeleteSyncJob.enqueueMessageDeletes(setOf(message))
    return queued(id)
  }
  private fun unreact(extras: Bundle): Bundle {
    val thread = extras.getLong("thread"); val id = extras.getLong("message")
    require(thread > 0 && id > 0)
    val message = SignalDatabase.messages.getMessageRecordOrNull(id)
    val recipient = SignalDatabase.threads.getRecipientForThreadId(thread)
    if (message == null || recipient == null || message.threadId != thread || !exportable(message) || !canSendNormal(recipient, thread)) return rejected()
    val own = SignalDatabase.reactions.getReactions(MessageId(id)).find { it.author == Recipient.self().id } ?: return queued(id)
    MessageSender.sendReactionRemoval(context, MessageId(id), own)
    return queued(id)
  }
  /** Marks read up to a timestamp; receipts follow the user's Signal setting. Never in disappearing chats. */
  private fun markRead(extras: Bundle): Bundle {
    val thread = extras.getLong("thread"); val until = extras.getLong("until")
    require(thread > 0 && until > 0)
    val recipient = SignalDatabase.threads.getRecipientForThreadId(thread) ?: return rejected()
    if (recipient.expiresInSeconds != 0) return rejected()
    val infos = SignalDatabase.threads.setReadSince(org.thoughtcrime.securesms.notifications.v2.ConversationId.forConversation(thread), until)
    org.thoughtcrime.securesms.dependencies.AppDependencies.messageNotifier.updateNotification(context)
    org.thoughtcrime.securesms.notifications.MarkReadReceiver.process(infos)
    return Bundle().apply { putString("result", "Queued") }
  }
  private fun typing(extras: Bundle): Bundle {
    val thread = extras.getLong("thread")
    require(thread > 0)
    val recipient = SignalDatabase.threads.getRecipientForThreadId(thread) ?: return rejected()
    if (!SignalStore.settings.isTypingIndicatorsEnabled || !canSendNormal(recipient, thread)) return rejected()
    val sender = org.thoughtcrime.securesms.dependencies.AppDependencies.typingStatusSender
    if (extras.getBoolean("active")) sender.onTypingStarted(thread) else sender.onTypingStoppedWithNotify(thread)
    return Bundle().apply { putString("result", "Queued") }
  }
  private fun groupFor(thread: Long): Pair<Recipient, org.thoughtcrime.securesms.database.model.GroupRecord>? {
    val recipient = SignalDatabase.threads.getRecipientForThreadId(thread)?.takeIf { it.isPushV2Group } ?: return null
    val group = SignalDatabase.groups.getGroup(recipient.id).orElse(null)?.takeIf { it.isActive && !it.isTerminated } ?: return null
    return recipient to group
  }
  private fun renameGroup(extras: Bundle): Bundle {
    val thread = extras.getLong("thread")
    val title = requireNotNull(extras.getString("title")).trim()
    require(thread > 0 && title.isNotEmpty() && title.codePointCount(0, title.length) <= 32)
    val (_, group) = groupFor(thread) ?: return rejected()
    return try {
      org.thoughtcrime.securesms.groups.GroupManager.updateGroupDetails(context, group.id, null, false, title, true, group.description, false)
      Bundle().apply { putString("result", "Queued") }
    } catch (_: Exception) { rejected() }
  }
  private fun leaveGroup(extras: Bundle): Bundle {
    val thread = extras.getLong("thread")
    require(thread > 0)
    val (_, group) = groupFor(thread) ?: return rejected()
    return try {
      org.thoughtcrime.securesms.groups.GroupManager.leaveGroup(context, group.id.requirePush(), true)
      Bundle().apply { putString("result", "Queued") }
    } catch (_: Exception) { rejected() }
  }
  /** Copies only audio/video tracks into a fresh MP4, so location and other container metadata are dropped. */
  private fun remuxVideo(bytes: ByteArray): ByteArray {
    val extractor = android.media.MediaExtractor()
    val out = java.io.File(context.noBackupFilesDir, "orbiliq-remux-" + java.util.UUID.randomUUID() + ".mp4")
    try {
      extractor.setDataSource(object : android.media.MediaDataSource() {
        override fun getSize() = bytes.size.toLong()
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
          if (position >= bytes.size) return -1
          val count = minOf(size.toLong(), bytes.size - position).toInt()
          System.arraycopy(bytes, position.toInt(), buffer, offset, count); return count
        }
        override fun close() {}
      })
      val muxer = android.media.MediaMuxer(out.path, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      val tracks = HashMap<Int, Int>()
      var video = false
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
          if (mime.startsWith("video/")) {
            video = true
            if (format.containsKey(android.media.MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(format.getInteger(android.media.MediaFormat.KEY_ROTATION))
          }
          extractor.selectTrack(i); tracks[i] = muxer.addTrack(format)
        }
      }
      check(video)
      muxer.start()
      val buffer = java.nio.ByteBuffer.allocate(4 * 1024 * 1024)
      val info = android.media.MediaCodec.BufferInfo()
      while (true) {
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        info.set(0, size, extractor.sampleTime, if (extractor.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC != 0) android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
        muxer.writeSampleData(tracks.getValue(extractor.sampleTrackIndex), buffer, info)
        extractor.advance()
      }
      muxer.stop(); muxer.release()
      val clean = out.readBytes()
      check(clean.size in 1..SignalMessagingContract.MAX_FILE_BYTES)
      return clean
    } finally { extractor.release(); out.delete() }
  }
  /** Delete for everyone with Signal's own time/ownership rules; repeating an applied delete is a no-op. */
  private fun remoteDelete(extras: Bundle): Bundle {
    val thread = extras.getLong("thread")
    val id = extras.getLong("message")
    require(thread > 0 && id > 0)
    val message = SignalDatabase.messages.getMessageRecordOrNull(id)
    if (message?.isRemoteDelete == true && message.threadId == thread) return Bundle().apply { putString("result", "Queued"); putLong("messageId", id) }
    val recipient = SignalDatabase.threads.getRecipientForThreadId(thread)
    if (message == null || recipient == null || message.threadId != thread || !exportable(message) || !canSendNormal(recipient, thread) ||
        !org.thoughtcrime.securesms.util.MessageConstraintsUtil.isValidRemoteDeleteSend(message, System.currentTimeMillis()))
      return Bundle().apply { putString("result", "Rejected") }
    MessageSender.sendRemoteDelete(id)
    return Bundle().apply { putString("result", "Queued"); putLong("messageId", id) }
  }
  private fun shareableFile(attachment: DatabaseAttachment): Boolean =
    attachment.contentType?.let { it.length in 3..128 && '/' in it } == true && attachment.stickerLocator == null &&
      attachment.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE && attachment.size in 1..SignalMessagingContract.MAX_FILE_BYTES
  private fun fileKind(attachment: DatabaseAttachment): String = when {
    attachment.voiceNote -> "voice"
    attachment.contentType!!.startsWith("audio/") -> "audio"
    attachment.contentType!!.startsWith("video/") -> "video"
    else -> "file"
  }
  private fun shareableImage(attachment: DatabaseAttachment): Boolean =
    attachment.contentType in SignalMessagingContract.IMAGE_TYPES && attachment.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE &&
      attachment.size in 1..SignalMessagingContract.MAX_IMAGE_BYTES
  /** Streams one decrypted image of an exportable message through a pipe; nothing is written to disk. */
  private fun image(extras: Bundle): Bundle {
    val thread = extras.getLong("thread")
    val id = extras.getLong("message")
    val attachmentId = extras.getLong("attachment")
    require(thread > 0 && id > 0 && attachmentId > 0)
    val message = SignalDatabase.messages.getMessageRecordOrNull(id)
    check(message != null && message.threadId == thread && exportable(message))
    val attachment = SignalDatabase.attachments.getAttachmentsForMessage(id).single { !it.quote && it.attachmentId.id == attachmentId }
    check(shareableImage(attachment) || shareableFile(attachment))
    val pipe = android.os.ParcelFileDescriptor.createPipe()
    Thread {
      try {
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
          SignalDatabase.attachments.getAttachmentStream(attachment.attachmentId, 0).use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
              val read = input.read(buffer)
              if (read < 0) break
              total += read
              check(total <= SignalMessagingContract.MAX_FILE_BYTES)
              out.write(buffer, 0, read)
            }
          }
        }
      } catch (_: Exception) { try { pipe[1].closeWithError("unavailable") } catch (_: Exception) {} }
    }.start()
    return Bundle().apply { putParcelable("stream", pipe[0]); putString("mimeType", attachment.contentType) }
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
    val media = SignalDatabase.attachments.getAttachmentsForMessage(message.id).filter { !it.quote }
    val images = media.filter(::shareableImage)
    val files = media.filter { it !in images && shareableFile(it) }
    val bodyOk = message.body.length <= SignalMessagingContract.MAX_TEXT &&
      message.body.toByteArray(Charsets.UTF_8).size <= SignalServiceMessageLimits.MAX_INLINE_BODY_SIZE_BYTES
    // Any other attachment (video, voice, file, unfinished download) keeps the whole message native-only.
    val supported = bodyOk && images.size + files.size == media.size && media.size <= SignalMessagingContract.MAX_IMAGES &&
      (message.body.isNotEmpty() || media.isNotEmpty())
    return Bundle().apply {
      putLong("id", message.id); putLong("thread", message.threadId)
      putString("sender", identity(message.fromRecipient))
      putString("senderName", message.fromRecipient.getDisplayName(context).take(512))
      putString("text", if (supported) message.body else ""); putBoolean("supported", supported); putBoolean("outgoing", message.isOutgoing)
      putLong("timestamp", message.dateSent); putLong("received", message.dateReceived)
      quoted(message)?.let { putLong("replyTo", it) }
      val writable = SignalDatabase.threads.getRecipientForThreadId(message.threadId)?.let { canSendNormal(it, message.threadId) } == true
      val now = System.currentTimeMillis()
      putBoolean("canEdit", writable && supported && message.body.isNotEmpty() &&
        org.thoughtcrime.securesms.util.MessageConstraintsUtil.isValidEditMessageSend(message, now))
      putBoolean("canDelete", writable && org.thoughtcrime.securesms.util.MessageConstraintsUtil.isValidRemoteDeleteSend(message, now))
      if (supported) {
        putLongArray("imageIds", images.map { it.attachmentId.id }.toLongArray())
        putStringArrayList("imageTypes", ArrayList(images.map { requireNotNull(it.contentType) }))
        putLongArray("imageSizes", images.map { it.size }.toLongArray())
        putLongArray("fileIds", files.map { it.attachmentId.id }.toLongArray())
        putStringArrayList("fileTypes", ArrayList(files.map { requireNotNull(it.contentType) }))
        putLongArray("fileSizes", files.map { it.size }.toLongArray())
        putStringArrayList("fileKinds", ArrayList(files.map(::fileKind)))
      }
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
    if (recipient == null || (!recipient.isIndividual && !recipient.isSelf && !recipient.isGroup) || !canSendNormal(recipient, thread) ||
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
    val imageToken = extras.getString("image")
    val edit = extras.getLong("edit", 0L)
    require(edit >= 0)
    if (entry != null) {
      check(entry.getLong("thread") == thread && entry.optLong("replyTo", 0L) == replyTo && entry.optString("image", "") == (imageToken ?: "") &&
        entry.optLong("edit", 0L) == edit)
      if (method == "messageSend") check(entry.getString("bodyHash") == hash(requireNotNull(extras.getString("text"))))
      return find(entry)
    }
    if (method == "messageReconcile") return Bundle().apply { putString("result", "Unknown") }
    val text = requireNotNull(extras.getString("text"))
    val staged = imageToken?.let { state.optJSONObject("staged:$it") ?: return Bundle().apply { putString("result", "Rejected") } }
    require((text.isNotEmpty() || staged != null) && text.length <= SignalMessagingContract.MAX_TEXT)
    require(text.toByteArray(Charsets.UTF_8).size <= SignalServiceMessageLimits.MAX_INLINE_BODY_SIZE_BYTES)
    val recipient = SignalDatabase.threads.getRecipientForThreadId(thread)
    if (recipient == null || (!recipient.isIndividual && !recipient.isSelf && !recipient.isGroup) || !canSendNormal(recipient, thread))
      return Bundle().apply { putString("result", "Rejected") }
    if (edit != 0L) {
      val target = SignalDatabase.messages.getMessageRecordOrNull(edit)
      if (target == null || target.threadId != thread || !exportable(target) || target.body.isEmpty() || imageToken != null || replyTo != 0L ||
          !org.thoughtcrime.securesms.util.MessageConstraintsUtil.isValidEditMessageSend(target, System.currentTimeMillis()))
        return Bundle().apply { putString("result", "Rejected") }
    }
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
    imageToken?.let { entry.put("image", it) }
    if (edit != 0L) entry.put("edit", edit)
    state.put(key, entry).put("lastTimestamp", timestamp)
    ledger.write(state) // Durable claim BEFORE any potentially effective send; never auto-resend a claim.
    MessageSender.send(context, OutgoingMessage(threadRecipient = recipient, sentTimeMillis = timestamp, body = text, isSecure = true, outgoingQuote = quote, messageToEdit = edit,
      attachments = staged?.let {
        val uri = android.net.Uri.parse(it.getString("uri"))
        listOf(if (it.optString("slide") == "voice") org.thoughtcrime.securesms.mms.AudioSlide(uri, it.getLong("size"), it.getString("type"), true).asAttachment()
          else if (it.optString("slide") == "video") org.thoughtcrime.securesms.mms.VideoSlide(context, uri, it.getLong("size"), false).asAttachment()
          else if (it.has("type")) DocumentSlide(context, uri, it.getString("type"), it.getLong("size"), it.getString("name")).asAttachment()
          else ImageSlide(context, uri, it.getLong("size"), it.getInt("width"), it.getInt("height"), null).asAttachment())
      }.orEmpty()),
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
