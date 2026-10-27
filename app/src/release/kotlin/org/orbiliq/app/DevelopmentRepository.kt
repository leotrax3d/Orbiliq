package org.orbiliq.app

import android.content.Context
import org.orbiliq.core.data.UnconfiguredConversationRepository
import org.orbiliq.core.domain.ConversationRepository

internal fun developmentConversationRepository(@Suppress("UNUSED_PARAMETER") context: Context): ConversationRepository = UnconfiguredConversationRepository()
