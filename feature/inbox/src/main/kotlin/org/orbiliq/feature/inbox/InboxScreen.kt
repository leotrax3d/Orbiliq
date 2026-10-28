@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.orbiliq.feature.inbox

import android.content.Context
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import org.orbiliq.core.designsystem.OrbiliqTheme
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun InboxRoute(repository: ConversationRepository, onProviderSetup: (() -> Unit)? = null) {
    val model: InboxViewModel = viewModel(factory = viewModelFactory { initializer { InboxViewModel(repository) } })
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var appearance by rememberSaveable { mutableStateOf(preferences.getString("mode", "System") ?: "System") }
    var dynamic by rememberSaveable { mutableStateOf(preferences.getBoolean("dynamic", true)) }
    val dark = when (appearance) { "Dunkel" -> true; "Hell" -> false; else -> isSystemInDarkTheme() }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    OrbiliqTheme(darkTheme = dark, dynamicColor = dynamic) {
        MessengerApp(model, appearance, dynamic, { appearance = it; preferences.edit().putString("mode", it).apply() },
            { dynamic = it; preferences.edit().putBoolean("dynamic", it).apply() }, onProviderSetup)
    }
}

@Composable
private fun MessengerApp(model: InboxViewModel, appearance: String, dynamic: Boolean, setAppearance: (String) -> Unit, setDynamic: (Boolean) -> Unit, onProviderSetup: (() -> Unit)?) {
    val state by model.state.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val screenStates = rememberSaveableStateHolder()
    val motion = MaterialTheme.motionScheme
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var chatId by rememberSaveable { mutableStateOf<String?>(null) }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var newChat by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("Alle") }
    var query by rememberSaveable { mutableStateOf("") }
    val snapshot = (state as? MessengerState.Ready)?.snapshot
    val chat = snapshot?.entries?.find { it.route.conversation.id.value == chatId }
    val person = snapshot?.people?.find { it.id.value == personId }
    BackHandler(chatId != null || personId != null || settings) { chatId = null; personId = null; settings = false }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); model.clearNotice() } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (chatId == null && personId == null && !settings) NavigationBar {
            listOf("Chats", "Menschen", "Konten").forEachIndexed { index, label ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index; query = "" },
                    icon = { MessengerIcon(label) }, label = { Text(label) })
            }
        } },
        floatingActionButton = { if (tab == 0 && chatId == null && personId == null && !settings && snapshot != null)
            MediumExtendedFloatingActionButton(onClick = { newChat = true }, icon = { MessengerIcon("Add") }, text = { Text("Nachricht") }) },
    ) { padding ->
        AnimatedContent(targetState = when { chat != null -> "chat:$chatId"; person != null -> "person:$personId"; settings -> "settings"; else -> "tab:$tab" },
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            transitionSpec = { (fadeIn(motion.fastEffectsSpec()) + slideInHorizontally(motion.defaultSpatialSpec()) { it / 8 }) togetherWith fadeOut(motion.fastEffectsSpec()) }, label = "Navigation") { destination ->
          screenStates.SaveableStateProvider(destination) {
            val destinationChat = snapshot?.entries?.find { "chat:${it.route.conversation.id.value}" == destination }
            val destinationPerson = snapshot?.people?.find { "person:${it.id.value}" == destination }
            when {
                destinationChat != null -> ConversationScreen(destinationChat.route, snapshot, model, onBack = { chatId = null }, onPerson = { personId = it; chatId = null })
                destinationPerson != null -> PersonScreen(destinationPerson, snapshot, { personId = null }) { chatId = it; personId = null }
                destination == "settings" -> SettingsScreen(appearance, dynamic, setAppearance, setDynamic) { settings = false }
                else -> Column(Modifier.fillMaxSize()) {
                    Header(if (tab == 0) "Orbiliq" else if (tab == 1) "Menschen" else "Konten", if (tab == 0) "Alles an einem Ort." else if (tab == 1) "Verbindungen, die zählen." else "Deine Messenger. Deine Wahl.")
                    if (tab == 2 && onProviderSetup != null) {
                        FilledTonalButton(onClick = onProviderSetup, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("Signal Companion · Einrichtung (experimentell)")
                        }
                    }
                    snapshot?.syncNotice?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall) }
                    when (state) {
                        MessengerState.Loading -> LoadingState()
                        MessengerState.Error -> EmptyState("Laden fehlgeschlagen", "Die lokalen Daten sind gerade nicht verfügbar.", "Erneut versuchen", model::retry)
                        MessengerState.Unconfigured -> EmptyState("Noch keine Unterhaltungen", "Es ist noch kein Messenger eingerichtet. Verwende für die lokale Vorschau die Mock-Version von Orbiliq.")
                        is MessengerState.Ready -> when (tab) {
                            0 -> {
                                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), singleLine = true,
                                    label = { Text("Chats und letzte Nachricht suchen") }, shape = MaterialTheme.shapes.extraLarge)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("Alle", "Direkt", "Gruppen", "Archiv").forEach { label -> MorphFilter(label, filter == label) { filter = label } }
                                }
                                val entries = snapshot!!.entries.filter { e ->
                                    val c = e.route.conversation
                                    (if (filter == "Archiv") c.id in snapshot.archived else c.id !in snapshot.archived) &&
                                        (filter != "Direkt" || c.kind == ConversationKind.Direct) && (filter != "Gruppen" || c.kind == ConversationKind.Group) &&
                                        (c.title.contains(query, true) || messageText(e.latestMessage).contains(query, true))
                                }
                                if (entries.isEmpty()) EmptyState(if (query.isBlank()) "Hier ist Platz für Neues" else "Keine Treffer", "Wähle einen anderen Filter oder öffne eine Unterhaltung.", "Filter zurücksetzen", { query = ""; filter = "Alle" })
                                else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    item { Label("LOKALER MOCK · ${entries.size} CHATS") }
                                    items(entries, key = { it.route.conversation.id.value }) { entry -> ChatRow(entry, Modifier.animateItem()) { chatId = entry.route.conversation.id.value } }
                                }
                            }
                            1 -> {
                                if (snapshot!!.people.isEmpty()) EmptyState("Noch keine Kontakte", "Personen erscheinen hier, sobald dein Provider Kontakte bereitstellt.")
                                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(snapshot.people, key = { it.id.value }) { p ->
                                        Surface(onClick = { personId = p.id.value }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                            ListItem(content = { Text(p.displayName) }, leadingContent = { Avatar(p.displayName) }, supportingContent = { Text("${snapshot.links.count { it.personId == p.id }} Messenger-Identitäten") })
                                        }
                                    }
                                }
                            }
                            else -> AccountsScreen(snapshot!!, { settings = true })
                        }
                    }
                }
            }
          }
        }
    }
    if (newChat && snapshot != null) ModalBottomSheet(onDismissRequest = { newChat = false }) {
        Text("Unterhaltung öffnen", Modifier.padding(24.dp), style = MaterialTheme.typography.headlineMedium)
        Text("Vorhandene Unterhaltungen", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).navigationBarsPadding(), contentPadding = PaddingValues(16.dp)) {
            items(snapshot.entries) { entry -> ChatRow(entry) { chatId = entry.route.conversation.id.value; newChat = false } }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, back: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        if (back != null) TextButton(onClick = back, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("‹ Zurück") }
        Text(title, style = MaterialTheme.typography.headlineLargeEmphasized)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun Label(text: String) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable
private fun Avatar(name: String) {
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(20.dp, 12.dp, 20.dp, 12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Text(name.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString(""), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
@Composable
private fun MorphFilter(label: String, selected: Boolean, click: () -> Unit) {
    val corner by animateDpAsState(if (selected) 16.dp else 28.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "Filterform")
    Surface(onClick = click, shape = RoundedCornerShape(corner), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.heightIn(min = 48.dp).semantics { this.selected = selected; role = Role.RadioButton }) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
    }
}
@Composable
private fun ChatRow(entry: UnifiedInboxEntry, modifier: Modifier = Modifier, click: () -> Unit) {
    Surface(onClick = click, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp, 10.dp, 24.dp, 10.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(entry.route.conversation.title)
            Column(Modifier.weight(1f)) {
                Text(entry.route.conversation.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(messageText(entry.latestMessage).ifEmpty { "Schreibe die erste Nachricht" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${entry.route.providerId.value} · ${if (entry.route.conversation.kind == ConversationKind.Group) "Gruppe" else "Direktchat"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            entry.latestMessage?.let { Text(time(it), style = MaterialTheme.typography.labelSmall) }
        }
    }
}
internal fun messageText(message: Message?): String = when (val c = message?.content) {
    is MessageContent.Text -> c.text
    is MessageContent.Media -> c.caption ?: "Medienanhang · Vorschau nicht verfügbar"
    is MessageContent.Service -> "Systemnachricht: ${c.code}"
    is MessageContent.Unsupported -> c.description
    null -> ""
}
private fun time(message: Message) = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(message.displayTime)

@Composable
private fun EmptyState(title: String, description: String, action: String? = null, click: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(32.dp, 12.dp, 32.dp, 12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
            Text("✦", Modifier.padding(24.dp).clearAndSetSemantics {}, style = MaterialTheme.typography.displayMedium)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(description, style = MaterialTheme.typography.bodyLarge)
        if (action != null) FilledTonalButton(onClick = click) { Text(action) }
    }
}
@Composable
private fun LoadingState() {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LoadingIndicator(Modifier.size(48.dp).semantics { contentDescription = "Unterhaltungen werden geladen" })
        repeat(3) { Surface(Modifier.fillMaxWidth().height(88.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {} }
    }
}

@Composable
private fun ConversationScreen(route: SendRoute, snapshot: MessengerSnapshot, model: InboxViewModel, onBack: () -> Unit, onPerson: (String) -> Unit) {
    val timelineFlow = remember(route.conversation.ref) { model.timeline(route.conversation.ref) }
    val timeline by timelineFlow.collectAsStateWithLifecycle(UnifiedTimeline(emptyList(), emptyList()))
    var draft by rememberSaveable(route.conversation.id.value) { mutableStateOf("") }
    var replyId by rememberSaveable(route.conversation.id.value) { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var archiveDialog by remember { mutableStateOf(false) }
    val allowed by produceState<Boolean?>(null, route, draft, replyId, timeline, snapshot.syncNotice) {
        value = model.canSend(route, draft.trim(), replyId?.let(::MessageId))
    }
    val textConstraints by produceState<OperationConstraints?>(null, route, snapshot.syncNotice) { value = model.textConstraints(route) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val ownIds = snapshot.accounts.mapNotNull { it.ownIdentityId }.toSet()
    val peers = route.conversation.participants - ownIds
    val linked = snapshot.links.firstOrNull { it.identityId in peers }
    val archived = route.conversation.id in snapshot.archived
    LaunchedEffect(timeline.messages.size) {
        if (timeline.messages.isNotEmpty()) list.animateScrollToItem(timeline.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Chats") }
            TextButton(onClick = { details = true }, modifier = Modifier.weight(1f)) {
                Column { Text(route.conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium); Text(route.providerId.value, style = MaterialTheme.typography.labelSmall) }
            }
            TextButton(onClick = { archiveDialog = true }) { Text(if (archived) "Zurücklegen" else "Archiv") }
        }
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            Text(if (snapshot.simulation) "Lokaler Testchat · keine echte Messenger-Verbindung" else "Lokale Kopie · Sicherheitsnummer im Companion prüfen", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
        }
        if (timeline.messages.isEmpty()) Box(Modifier.weight(1f)) { EmptyState("Sag einfach Hallo", "Deine erste Nachricht startet diese Unterhaltung.") }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(timeline.messages, key = { _, message -> message.id.value }) { index, message ->
                val date = message.displayTime.atZone(ZoneId.systemDefault()).toLocalDate()
                if (index == 0 || timeline.messages[index - 1].displayTime.atZone(ZoneId.systemDefault()).toLocalDate() != date) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(date.format(DateTimeFormatter.ofPattern("dd. MMM yyyy")), style = MaterialTheme.typography.labelMedium)
                    }
                }
                val outgoing = message.direction == MessageDirection.Outgoing
                Column(Modifier.fillMaxWidth().animateItem(), horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
                    Surface(shape = if (outgoing) RoundedCornerShape(24.dp, 24.dp, 6.dp, 24.dp) else RoundedCornerShape(24.dp, 24.dp, 24.dp, 6.dp),
                        color = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.widthIn(max = 320.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!outgoing && route.conversation.kind == ConversationKind.Group) Text(snapshot.identities.find { it.ref == message.sender }?.displayName ?: "Teilnehmer", style = MaterialTheme.typography.labelMedium)
                            message.replyTo?.let { id -> Text("↳ ${timeline.messages.find { it.id == id }?.let { messageText(it).take(80) } ?: "Ursprüngliche Nachricht nicht geladen"}", style = MaterialTheme.typography.labelMedium) }
                            Text(messageText(message), style = MaterialTheme.typography.bodyLarge)
                            timeline.reactions[message.id]?.takeIf { it.isNotEmpty() }?.let { counts ->
                                Text(counts.entries.sortedByDescending { it.value }.joinToString("  ") { (emoji, n) -> if (n > 1) "$emoji $n" else emoji },
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.semantics { contentDescription = "Reaktionen: " + counts.entries.joinToString { "${it.key} ${it.value}" } })
                            }
                            Text("${time(message)} · ${route.providerId.value}${if (outgoing) " · ${deliveryLabel(message.delivery)}" else ""}", style = MaterialTheme.typography.labelSmall)
                            val canReply by produceState(false, route, message.ref, snapshot.syncNotice) { value = message.ref != null && model.canSend(route, "x", message.id) == true }
                            if (canReply && message.content is MessageContent.Text) TextButton(onClick = { replyId = message.id.value }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Antworten") }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(replyId != null) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Antwort: ${messageText(timeline.messages.find { it.id.value == replyId }).take(60)}", Modifier.weight(1f), maxLines = 2)
                TextButton(onClick = { replyId = null }) { Text("Aufheben") }
            }
        }
        if (allowed == false) Text("Dieser Versandweg ist momentan nicht verfügbar. Dein Entwurf bleibt erhalten.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), label = { Text("Nachricht über ${if (snapshot.simulation) "Mock" else route.providerId.value}") }, shape = MaterialTheme.shapes.extraLarge, maxLines = 5, enabled = !sending,
                supportingText = {
                    val limit = textConstraints?.maxBytes
                    Text(if (limit == null) "${draft.length} Zeichen" else "${draft.toByteArray(Charsets.UTF_8).size}/$limit Byte")
                }, isError = textConstraints?.maxBytes?.let { draft.toByteArray(Charsets.UTF_8).size > it } == true)
            FilledIconButton(onClick = {
                scope.launch {
                    sending = true
                    try { if (model.send(route, draft, replyId?.let(::MessageId))) { draft = ""; replyId = null } }
                    finally { sending = false }
                }
            }, enabled = draft.isNotBlank() && !sending && allowed == true,
                modifier = Modifier.padding(bottom = 24.dp).size(56.dp).semantics { contentDescription = "Nachricht senden" }) {
                if (sending) CircularProgressIndicator(Modifier.size(24.dp)) else MessengerIcon("Send")
            }
        }
    }
    if (archiveDialog) AlertDialog(onDismissRequest = { archiveDialog = false }, title = { Text(if (archived) "Zurück in die Inbox?" else "Chat archivieren?") },
        text = { Text("Der Verlauf bleibt lokal erhalten und ist über den Ordnerfilter erreichbar.") },
        confirmButton = { TextButton(onClick = { model.archive(route.conversation.id, !archived); archiveDialog = false; onBack() }) { Text(if (archived) "Zurücklegen" else "Archivieren") } },
        dismissButton = { TextButton(onClick = { archiveDialog = false }) { Text("Abbrechen") } })
    if (details) ModalBottomSheet(onDismissRequest = { details = false }) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(route.conversation.title)
            Text(route.conversation.title, style = MaterialTheme.typography.headlineMedium)
            Text("Provider: ${route.providerId.value}\nKonto: ${snapshot.accounts.find { it.id == route.conversation.ref.accountId }?.displayName}\nVerschlüsselung: ${route.conversation.security.encryption}\nIdentitätsprüfung: ${route.conversation.security.verification}")
            Text(if (route.conversation.kind == ConversationKind.Group && route.conversation.participants.isEmpty()) "Gruppe · Mitgliederverwaltung im Companion" else "${route.conversation.participants.size} Identitäten · ${if (route.conversation.kind == ConversationKind.Group) "Gruppe" else "Direktchat"}")
            if (linked != null) FilledTonalButton(onClick = { details = false; onPerson(linked.personId.value) }) { Text("Person ansehen") }
        }
    }
}
internal fun deliveryLabel(state: DeliveryState): String = when (state) {
    DeliveryState.Pending -> "In Warteschlange"
    DeliveryState.Accepted -> "Angenommen"
    DeliveryState.Sent -> "Gesendet"
    DeliveryState.Delivered -> "Zugestellt"
    DeliveryState.Read -> "Gelesen"
    DeliveryState.Failed -> "Fehlgeschlagen"
    DeliveryState.OutcomeUnknown -> "Versand unklar · wird geprüft"
}
@Composable
private fun PersonScreen(person: Person, snapshot: MessengerSnapshot, back: () -> Unit, openChat: (String) -> Unit) {
    val ids = snapshot.links.filter { it.personId == person.id }.map { it.identityId }.toSet()
    val identities = snapshot.identities.filter { it.id in ids }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(person.displayName, "Person & Messenger-Identitäten", back) }
        item { Box(Modifier.padding(24.dp)) { Avatar(person.displayName) } }
        items(identities, key = { it.id.value }) { identity ->
            ListItem(content = { Text(identity.displayName) }, supportingContent = { Text(snapshot.accounts.find { it.id == identity.ref.accountId }?.displayName ?: "Lokale Identität") })
        }
        item { Label("UNTERHALTUNGEN") }
        val entries = snapshot.entries.filter { it.route.conversation.kind == ConversationKind.Direct && it.route.conversation.participants.any { id -> id in ids } }
        if (entries.isEmpty()) item { Text("Noch keine Direktunterhaltung vorhanden.", Modifier.padding(24.dp)) }
        items(entries) { entry -> Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { ChatRow(entry) { openChat(entry.route.conversation.id.value) } } }
    }
}
@Composable
private fun AccountsScreen(snapshot: MessengerSnapshot, settings: () -> Unit) {
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FilledTonalButton(onClick = settings, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Darstellung & Einstellungen") } }
        item { Label(if (snapshot.simulation) "VERBUNDEN · LOKALE SIMULATION" else "LOKAL FREIGEGEBENE KONTEN") }
        items(snapshot.accounts, key = { it.id.value }) { account ->
            Surface(onClick = { accountId = account.id.value }, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(account.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(if (snapshot.simulation) "Mock Provider · lokal auf diesem Gerät" else "${account.providerId.value} · lokaler Companion", style = MaterialTheme.typography.bodyLarge)
                    Text("Kontodetails öffnen", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { Text(if (snapshot.simulation) "Dieses Build verwendet ausschließlich synthetische Mock-Daten." else "Verknüpfe den Companion und erteile dort den Nachrichtenzugriff. Die Historie umfasst den vorhandenen nativen Gerätebestand. Normale Direktchat-Texte können gesendet werden, Gruppentexte sind lesbar. Medien, Gruppenversand und verschwindende Inhalte bleiben vorerst im Companion.", style = MaterialTheme.typography.bodyMedium) }
    }
    snapshot.accounts.find { it.id.value == accountId }?.let { account -> ModalBottomSheet(onDismissRequest = { accountId = null }) {
        Column(Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(account.displayName, style = MaterialTheme.typography.headlineMedium)
            Text("Provider: ${account.providerId.value}\n${snapshot.entries.count { it.route.conversation.ref.accountId == account.id }} Unterhaltungen")
            Text(if (snapshot.simulation) "Lokale Simulation. Es besteht keine Verbindung zu einem echten Messenger." else "Verfügbare Aktionen werden vom Provider geprüft. Ein lokaler Versandauftrag bestätigt noch keine Zustellung. Medien und geschützte Inhalte bleiben im Companion.")
        }
    } }
}
@Composable
private fun SettingsScreen(appearance: String, dynamic: Boolean, setAppearance: (String) -> Unit, setDynamic: (Boolean) -> Unit, back: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Einstellungen", "So fühlt sich Orbiliq nach dir an.", back) }
        item { Label("DARSTELLUNG") }
        items(listOf("System", "Hell", "Dunkel")) { mode ->
            Row(Modifier.fillMaxWidth().clickable { setAppearance(mode) }.padding(horizontal = 24.dp).heightIn(min = 56.dp).semantics { role = Role.RadioButton; selected = appearance == mode }, verticalAlignment = Alignment.CenterVertically) {
                Text(mode, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                RadioButton(selected = appearance == mode, onClick = null)
            }
        }
        item { ListItem(content = { Text("Farben des Hintergrundbilds") }, supportingContent = { Text("Dynamic Color ab Android 12") }, trailingContent = {
            Switch(checked = dynamic, onCheckedChange = setDynamic, modifier = Modifier.semantics { contentDescription = "Dynamische Farben" })
        }) }
        item { Label("DATENSCHUTZ & SPEICHER") }
        item { Text("Nachrichten bleiben lokal verschlüsselt gespeichert. Keine Orbiliq-Cloud, keine Tracker. App-Lock und automatische Aufbewahrungsregeln sind noch nicht eingerichtet.", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyLarge) }
        item { Label("ÜBER ORBILIQ") }
        item { Text("Material 3 Expressive · Orbiliq\nEigener App-Code: Open Source · MIT. Der separate Signal-Companion enthält AGPL-Code.\nBewegungen folgen der systemweiten Animationseinstellung.", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium) }
    }
}
