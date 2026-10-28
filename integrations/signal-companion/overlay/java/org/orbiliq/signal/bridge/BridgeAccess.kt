/* SPDX-License-Identifier: MIT */
package org.orbiliq.signal.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import org.orbiliq.core.provider.signal.SignalBridgeContract
import org.orbiliq.core.provider.signal.SignalBridgeTrust

internal class BridgeAccess(private val context: Context) {
  private val grant = AtomicFile(File(context.noBackupFilesDir, "orbiliq-bridge-grant-v1"))

  fun enforceCaller() {
    context.enforceCallingPermission(SignalBridgeContract.PERMISSION, "Bridge access denied")
    enforceUid(Binder.getCallingUid())
  }

  fun enforceUid(uid: Int) {
    val pm = context.packageManager
    if (!SignalBridgeTrust.permits(
        SignalBridgeContract.CLIENT_PACKAGE,
        pm.getPackagesForUid(uid)?.toSet().orEmpty(),
        signers(context.packageName), signers(SignalBridgeContract.CLIENT_PACKAGE)
      )) throw SecurityException("Bridge access denied")
  }

  fun hasConsent(): Boolean = synchronized(BridgeAccess::class.java) {
    val expected = signers(SignalBridgeContract.CLIENT_PACKAGE).singleOrNull() ?: return@synchronized false
    try { grant.openRead().use { it.readBytes().toString(Charsets.UTF_8) == expected } }
    catch (_: java.io.IOException) { false }
  }

  fun approve() = synchronized(BridgeAccess::class.java) {
    val digest = signers(SignalBridgeContract.CLIENT_PACKAGE).single()
    val stream = grant.startWrite()
    try {
      stream.write(digest.toByteArray(Charsets.UTF_8))
      grant.finishWrite(stream)
    } catch (e: Exception) {
      grant.failWrite(stream)
      throw e
    }
  }

  fun revoke() = synchronized(BridgeAccess::class.java) { grant.delete() }

  @Suppress("DEPRECATION")
  private fun signers(name: String): Set<String> = try {
    val pm = context.packageManager
    val info = pm.getPackageInfo(name, if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
    val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
    certificates.orEmpty().map { signature ->
      MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
    }.toSet()
  } catch (_: PackageManager.NameNotFoundException) { emptySet() }
}
