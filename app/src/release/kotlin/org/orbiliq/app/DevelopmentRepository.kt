package org.orbiliq.app

import android.content.Context
import org.orbiliq.core.data.UnconfiguredConversationRepository
import org.orbiliq.core.domain.ConversationRepository

internal fun developmentConversationRepository(context: Context): ConversationRepository = org.orbiliq.app.signal.SignalMessengerRepository(context)
