/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.orbiliq.signal.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.orbiliq.core.provider.signal.SignalBridgeContract
import org.orbiliq.core.provider.signal.SignalLinkState
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService

/** Version 1 exposes local linking status only. Never returns identity, QR, keys or messages. */
class BridgeProvider : ContentProvider() {
  override fun onCreate() = true

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
    val ctx = requireNotNull(context)
    val access = BridgeAccess(ctx)
    access.enforceCaller()
    if (method in setOf("messageSession", "messageThreads", "messageTexts", "messageLookup", "messageSend", "messageReconcile", "messageReact", "messageImage", "messageStageImage", "messageDelete", "messageDeleteLocal", "messageUnreact", "messageRead", "messageTyping", "groupRename", "groupLeave")) {
      return try { BridgeMessaging(ctx).call(method, requireNotNull(extras)) }
      catch (_: Exception) { Bundle().apply { putString("error", "Unavailable") } }
    }
    if (extras?.getInt("protocolVersion", -1) != SignalBridgeContract.VERSION) {
      return status(SignalLinkState.UpdateRequired)
    }
    if (method != "status" && method != "revoke") throw IllegalArgumentException("Unsupported bridge operation")
    return try { when (method) {
      "status" -> status(when {
        !access.hasConsent() -> SignalLinkState.ConsentRequired
        KeyCachingService.isLocked(ctx) -> SignalLinkState.Locked
        SignalStore.account.isUnauthorizedReceived -> SignalLinkState.Revoked
        !SignalStore.account.isRegistered -> SignalLinkState.NeedsLink
        SignalStore.account.isPrimaryDevice -> SignalLinkState.PrimaryDeviceNotSupported
        else -> SignalLinkState.Linked
      })
      "revoke" -> { BridgeMessaging(ctx).revoke(); access.revoke(); status(if (access.hasConsent()) SignalLinkState.Unavailable else SignalLinkState.ConsentRequired) }
      else -> throw IllegalArgumentException("Unsupported bridge operation")
    } } catch (_: Exception) { status(SignalLinkState.Unavailable) }
  }

  private fun status(state: SignalLinkState) = Bundle().apply {
    putInt("protocolVersion", SignalBridgeContract.VERSION)
    putString("linkState", state.name)
    putStringArray("capabilities", emptyArray())
  }
  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw SecurityException("Unsupported")
  override fun getType(uri: Uri): String? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = throw SecurityException("Unsupported")
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw SecurityException("Unsupported")
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw SecurityException("Unsupported")
}
