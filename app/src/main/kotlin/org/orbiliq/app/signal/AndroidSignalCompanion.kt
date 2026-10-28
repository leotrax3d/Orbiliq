package org.orbiliq.app.signal

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import org.orbiliq.core.provider.signal.*

/** All IPC on IO. Neither diagnostics nor UI receive raw transport exceptions. */
internal class AndroidSignalCompanion(private val activity: Activity) : SignalCompanionTransport {
    private val context = activity.applicationContext
    private val uri = Uri.parse("content://${SignalBridgeContract.AUTHORITY}")
    override fun statuses() = flow {
        while (currentCoroutineContext().isActive) {
            emit(refresh())
            delay(2000)
        }
    }

    override suspend fun refresh(): SignalCompanionStatus = withContext(Dispatchers.IO) {
        val trusted = verifyPeer()
        if (trusted != null) return@withContext SignalCompanionStatus(trusted)
        try {
            val response = call("status") ?: return@withContext SignalCompanionStatus(SignalLinkState.Unavailable)
            // A future companion must never silently upgrade this linking-only client to messaging.
            SignalBridgeContract.decodeStatus(response.getInt("protocolVersion", -1), response.getString("linkState"),
                response.getStringArray("capabilities")?.toList())
        } catch (e: CancellationException) { throw e }
        catch (_: SecurityException) { SignalCompanionStatus(SignalLinkState.Untrusted) }
        catch (_: Exception) { SignalCompanionStatus(SignalLinkState.Unavailable) }
    }

    override suspend fun openSetup() {
        if (withContext(Dispatchers.IO) { verifyPeer() } != null) throw SecurityException("Companion unavailable")
        withContext(Dispatchers.Main) {
            @Suppress("DEPRECATION") // Required for system-authenticated callingPackage at the companion.
            activity.startActivityForResult(Intent().setComponent(ComponentName(
                SignalBridgeContract.PACKAGE, SignalBridgeContract.CONSENT_ACTIVITY)), 1401)
        }
    }

    override suspend fun revokeAccess() = withContext(Dispatchers.IO) {
        if (verifyPeer() != null) throw SecurityException("Companion unavailable")
        val response = call("revoke") ?: throw IllegalStateException("Companion unavailable")
        if (SignalBridgeContract.decodeStatus(response.getInt("protocolVersion", -1), response.getString("linkState"),
                response.getStringArray("capabilities")?.toList()).link != SignalLinkState.ConsentRequired) {
            throw IllegalStateException("Access revocation unconfirmed")
        }
        Unit
    }

    private fun call(method: String): Bundle? = context.contentResolver.acquireUnstableContentProviderClient(uri)?.use {
        it.call(method, null, Bundle().apply { putInt("protocolVersion", SignalBridgeContract.VERSION) })
    }

    @Suppress("DEPRECATION")
    private fun verifyPeer(): SignalLinkState? { return try {
        val pm = context.packageManager
        val provider = pm.resolveContentProvider(SignalBridgeContract.AUTHORITY, 0)
            ?: return SignalLinkState.Missing
        val activityInfo = pm.getActivityInfo(ComponentName(SignalBridgeContract.PACKAGE, SignalBridgeContract.CONSENT_ACTIVITY), 0)
        if (provider.packageName != SignalBridgeContract.PACKAGE || !provider.exported ||
            provider.readPermission != SignalBridgeContract.PERMISSION || provider.writePermission != SignalBridgeContract.PERMISSION ||
            activityInfo.packageName != provider.packageName || !activityInfo.exported ||
            activityInfo.permission != SignalBridgeContract.PERMISSION || activityInfo.applicationInfo.uid != provider.applicationInfo.uid ||
            !SignalBridgeTrust.permits(SignalBridgeContract.PACKAGE,
                pm.getPackagesForUid(provider.applicationInfo.uid)?.toSet().orEmpty(), signers(context.packageName), signers(provider.packageName))) {
            SignalLinkState.Untrusted
        } else null
    } catch (_: PackageManager.NameNotFoundException) { SignalLinkState.Missing }
    catch (_: Exception) { SignalLinkState.Unavailable }
    }

    @Suppress("DEPRECATION")
    private fun signers(name: String): Set<String> {
        val info = context.packageManager.getPackageInfo(name,
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
    }
}
