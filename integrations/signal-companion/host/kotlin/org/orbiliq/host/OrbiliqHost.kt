// SPDX-License-Identifier: AGPL-3.0-or-later
package org.orbiliq.host

import android.app.Activity
import android.content.Context
import android.os.Bundle
import org.orbiliq.app.signal.AndroidSignalMessaging
import org.orbiliq.app.signal.SignalMessengerRepository
import org.orbiliq.core.domain.ConversationRepository

/**
 * Wiring of the single Orbiliq app that embeds the Signal client. The Signal side installs the
 * in-process bridge at application start; there is no exported IPC surface in this build.
 */
object OrbiliqHost {
    @Volatile private var bridge: ((String, Bundle) -> Bundle?)? = null
    @Volatile private var setup: ((Activity) -> Unit)? = null
    @Volatile private var repository: ConversationRepository? = null

    fun install(bridge: (String, Bundle) -> Bundle?, openSignalSetup: (Activity) -> Unit) {
        this.bridge = bridge
        this.setup = openSignalSetup
    }

    fun openSignalSetup(activity: Activity) { setup?.invoke(activity) }

    /** In-process transport (same account-bound rules as the bridge); used by the repository and tests. */
    fun transport(context: Context): org.orbiliq.core.provider.signal.SignalMessagingTransport =
        AndroidSignalMessaging(context.applicationContext, requireNotNull(bridge) { "Signal bridge not installed" })
    /** Raw bridge call for in-process verification (e.g. a foreign binding must be refused); null = refused. */
    fun probe(method: String, extras: Bundle): Bundle? = bridge?.invoke(method, extras)
    /** One process-wide owner of encrypted storage and the Signal session. */
    fun conversations(context: Context): ConversationRepository = repository ?: synchronized(this) {
        repository ?: SignalMessengerRepository(context.applicationContext, transport(context)).also { repository = it }
    }
}
