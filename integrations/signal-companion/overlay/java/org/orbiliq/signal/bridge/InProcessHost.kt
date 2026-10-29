/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.orbiliq.signal.bridge

import android.app.Application
import android.content.Intent
import android.os.Bundle
import org.orbiliq.host.OrbiliqHost
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore

/** Connects Orbiliq's UI to the embedded Signal client in the same process. Never exported. */
object InProcessHost {
  @JvmStatic fun install(application: Application) {
    val messaging = BridgeMessaging(application, inProcess = true)
    OrbiliqHost.install(
      bridge = { method, extras ->
        try { messaging.call(method, extras) } catch (_: Exception) { null } // Fail closed without leaking details.
      },
      openSignalSetup = { activity ->
        // Signal's own start routine first creates its encrypted stores, then routes to linking.
        // Primary registration stays blocked by CompanionRegistrationPolicy.
        if (!(SignalStore.account.isRegistered && SignalStore.account.isPrimaryDevice)) {
          activity.startActivity(Intent(activity, MainActivity::class.java))
        }
      },
    )
  }
}
