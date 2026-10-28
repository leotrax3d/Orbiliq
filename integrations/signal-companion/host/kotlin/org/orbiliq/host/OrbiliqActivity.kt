// SPDX-License-Identifier: AGPL-3.0-or-later
package org.orbiliq.host

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.orbiliq.feature.inbox.InboxRoute

/** Launcher of the combined app: Orbiliq's unified inbox on top of the embedded Signal client. */
class OrbiliqActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val conversations = OrbiliqHost.conversations(this)
        setContent { InboxRoute(conversations, onProviderSetup = { OrbiliqHost.openSignalSetup(this) }) }
    }
}
