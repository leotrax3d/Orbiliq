package org.orbiliq.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.orbiliq.core.domain.ConversationRepository
import org.orbiliq.feature.inbox.InboxRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var conversations: ConversationRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { InboxRoute(conversations, onProviderSetup = {
            startActivity(android.content.Intent(this, org.orbiliq.app.signal.SignalSetupActivity::class.java))
        }) }
    }
}
