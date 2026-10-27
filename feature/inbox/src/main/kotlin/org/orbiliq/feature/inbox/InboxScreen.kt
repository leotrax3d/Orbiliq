package org.orbiliq.feature.inbox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.orbiliq.core.domain.ConversationRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxRoute(repository: ConversationRepository) {
    val model: InboxViewModel = viewModel(factory = viewModelFactory {
        initializer { InboxViewModel(repository) }
    })
    val conversations by model.conversations.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_title)) }) }) { padding ->
        if (conversations.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.empty_description), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(conversations, key = { "${it.ref.accountId.length}:${it.ref.accountId}${it.ref.remoteConversationId}" }) { conversation ->
                    ListItem { Text(conversation.title) }
                }
            }
        }
    }
}
