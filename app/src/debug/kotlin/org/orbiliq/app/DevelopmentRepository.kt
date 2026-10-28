package org.orbiliq.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import org.orbiliq.core.data.UnconfiguredConversationRepository
import org.orbiliq.core.domain.ConversationRepository
import org.orbiliq.core.provider.mock.PersistentMockRepository
import org.orbiliq.app.storage.AndroidLocalStorage

/** Storage opens lazily on IO. The singleton owns application-lifetime synchronization. */
internal fun developmentConversationRepository(context: Context): ConversationRepository =
    if (BuildConfig.MOCK_MESSENGER) PersistentMockRepository(CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
        AndroidLocalStorage(context).repository
    } else org.orbiliq.app.signal.SignalMessengerRepository(context)
