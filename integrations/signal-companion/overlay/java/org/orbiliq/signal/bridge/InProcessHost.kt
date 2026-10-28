/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.orbiliq.signal.bridge

import android.app.Application
import android.content.Intent
import android.os.Bundle
import org.orbiliq.host.OrbiliqHost
import org.signal.registration.RegistrationActivity
import org.signal.registration.RegistrationRoute
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService

/** Connects Orbiliq's UI to the embedded Signal client in the same process. Never exported. */
object InProcessHost {
  @JvmStatic fun install(application: Application) {
    val messaging = BridgeMessaging(application, inProcess = true)
    OrbiliqHost.install(
      bridge = { method, extras ->
        try { messaging.call(method, extras) } catch (_: Exception) { null } // Fail closed without leaking details.
      },
      openSignalSetup = { activity ->
        val intent = when {
          SignalStore.account.isRegistered && SignalStore.account.isPrimaryDevice -> null
          SignalStore.account.isRegistered || KeyCachingService.isLocked(activity) -> Intent(activity, MainActivity::class.java)
          else -> RegistrationActivity.createIntent(activity, startDestination = RegistrationRoute.LinkAccount(showCreateAccount = false))
        }
        intent?.let(activity::startActivity)
      },
    )
  }
}
