// SPDX-License-Identifier: AGPL-3.0-or-later
package org.orbiliq.app

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import org.orbiliq.app.signal.AndroidSignalMessaging
import org.orbiliq.core.domain.MessengerRepository
import org.orbiliq.core.provider.signal.SignalMessagingTransport

/** Standalone development app: Hilt singleton and IPC to the separate companion. */
object TestHost {
    fun messenger(context: Context): MessengerRepository =
        EntryPointAccessors.fromApplication(context, ConversationRepositoryEntryPoint::class.java).conversations() as MessengerRepository
    fun transport(context: Context): SignalMessagingTransport = AndroidSignalMessaging(context)
    fun foreignBindingRejected(context: Context, wrong: String): Boolean {
        val denied = context.contentResolver.call(android.net.Uri.parse("content://${org.orbiliq.core.provider.signal.SignalBridgeContract.AUTHORITY}"),
            "messageThreads", null, android.os.Bundle().apply {
                putInt("messageProtocolVersion", org.orbiliq.core.provider.signal.SignalMessagingContract.VERSION); putString("accountBinding", wrong)
            }) ?: return false
        return denied.keySet() == setOf("error") && denied.getString("error") == "Unavailable"
    }
}
