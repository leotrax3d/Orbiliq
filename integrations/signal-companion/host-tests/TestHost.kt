// SPDX-License-Identifier: AGPL-3.0-or-later
package org.orbiliq.app

import android.content.Context
import android.os.Bundle
import org.orbiliq.core.domain.MessengerRepository
import org.orbiliq.core.provider.signal.SignalMessagingContract
import org.orbiliq.core.provider.signal.SignalMessagingTransport
import org.orbiliq.host.OrbiliqHost

/** Combined app: the single in-process repository and bridge of the embedded Signal client. */
object TestHost {
    fun messenger(context: Context): MessengerRepository = OrbiliqHost.conversations(context) as MessengerRepository
    fun transport(context: Context): SignalMessagingTransport = OrbiliqHost.transport(context)
    fun foreignBindingRejected(context: Context, wrong: String): Boolean = OrbiliqHost.probe("messageThreads", Bundle().apply {
        putInt("messageProtocolVersion", SignalMessagingContract.VERSION); putString("accountBinding", wrong)
    }) == null
}
