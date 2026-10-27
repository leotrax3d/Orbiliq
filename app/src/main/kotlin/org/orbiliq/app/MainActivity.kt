package org.orbiliq.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.orbiliq.core.domain.ConversationRepository
import org.orbiliq.core.designsystem.OrbiliqTheme
import org.orbiliq.feature.inbox.InboxRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var conversations: ConversationRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OrbiliqTheme { InboxRoute(conversations) } }
    }
}
