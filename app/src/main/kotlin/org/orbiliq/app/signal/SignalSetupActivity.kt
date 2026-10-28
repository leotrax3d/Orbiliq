package org.orbiliq.app.signal

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.orbiliq.core.designsystem.OrbiliqTheme
import org.orbiliq.core.provider.signal.*

class SignalSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val transport = AndroidSignalCompanion(this)
        val statuses = transport.statuses()
        setContent {
            val status by statuses.collectAsStateWithLifecycle(SignalCompanionStatus(SignalLinkState.Unavailable))
            val scope = rememberCoroutineScope()
            var failure by remember { mutableStateOf(false) }
            OrbiliqTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("Signal einrichten", style = MaterialTheme.typography.headlineMedium)
                        Text("Experimenteller Companion. Geräteverknüpfung und Schlüsselverwaltung laufen in einer separaten App. Nach eigener Freigabe im Companion kann Orbiliq normale Direktchat-Texte übernehmen und senden sowie Gruppentexte anzeigen. Medien, Gruppenversand und verschwindende Inhalte bleiben vorerst im Companion.")
                        Text(statusLabel(status.link), style = MaterialTheme.typography.titleMedium)
                        if (status.link == SignalLinkState.Missing) Text("Installiere zuerst den separat gebauten Orbiliq Signal Companion. Beide lokalen Builds benötigen dieselbe Signatur.")
                        if (status.link !in setOf(SignalLinkState.Missing, SignalLinkState.Untrusted, SignalLinkState.Unavailable, SignalLinkState.UpdateRequired)) {
                            Button(onClick = {
                                scope.launch {
                                    try { transport.openSetup(); failure = false }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { failure = true }
                                }
                            }) { Text("Companion öffnen") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try { transport.revokeAccess(); failure = false }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { failure = true }
                                }
                            }) { Text("Orbiliq-Zugriff entziehen") }
                        }
                        if (failure) Text("Die Aktion konnte nicht ausgeführt werden. Prüfe den Companion.", color = MaterialTheme.colorScheme.error)
                        Text("Der angezeigte Zustand beschreibt lokale Zugangsdaten. Er bestätigt keine Verbindung zum Signal-Dienst. Das Entziehen des Orbiliq-Zugriffs entfernt das verknüpfte Signal-Gerät nicht.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { finish() }) { Text("Zurück") }
                    }
                }
            }
        }
    }
}

private fun statusLabel(state: SignalLinkState): String = when (state) {
    SignalLinkState.Missing -> "Companion fehlt"
    SignalLinkState.Untrusted -> "Signatur oder App-Identität nicht vertrauenswürdig"
    SignalLinkState.ConsentRequired -> "Zustimmung im Companion erforderlich"
    SignalLinkState.NeedsLink -> "Geräteverknüpfung erforderlich"
    SignalLinkState.Linked -> "Verknüpftes Gerät lokal eingerichtet"
    SignalLinkState.Revoked -> "Signal hat die Session zurückgewiesen"
    SignalLinkState.Locked -> "Companion ist gesperrt"
    SignalLinkState.PrimaryDeviceNotSupported -> "Primärgerät wird für diese Integration nicht unterstützt"
    SignalLinkState.UpdateRequired -> "Companion und Orbiliq benötigen eine kompatible Version"
    SignalLinkState.Unavailable -> "Companion-Zustand nicht verfügbar"
}
