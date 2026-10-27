# Unified Inbox und Personen-Conversations

`UnifiedCommunication` ist der Domainport; `UnifiedCommunicationService` setzt ihn auf
`LocalRepository` und einer vom App-Scope verwalteten Account-/Sessionregistry um.
Es gibt keine Abhängigkeit von einem konkreten Messenger und keine Netzwerkdienste.

`observeUnifiedInbox()` zeigt alle nicht archivierten Providerchats absteigend nach
letzter Nachrichtenzeit (`serverTime ?: receivedAt`). Leere Chats folgen am Ende.
Bei Zeitgleichheit entscheiden Provider, Account und Remote-Conversation-ID stabil.
Der bestehende `observeInbox()`-Port nutzt dieselbe Sortierung. Jede Inboxzeile enthält
Provider-ID, Account-/Conversationreferenz und optional die unveränderte letzte Nachricht.

`ConversationTarget.ProviderChat` liefert einen einzelnen Verlauf. Erst die explizite Wahl
von `PersonChat` führt die Direktchats verknüpfter Identitäten zusammen. Es werden nur
Direktchats mit genau einem bekannten Gegenüber berücksichtigt; Gruppen, Channels,
mehrdeutige Teilnehmerlisten und bloße Namensgleichheit bewirken keine Zusammenführung.
Archivierung entfernt Chats aus der Inbox, nicht ihre Nachrichten aus einem geöffneten Verlauf.
Alle Nachrichten bleiben unverändert, einschließlich Route, Sender, Remote-ID, lokaler ID,
SecurityContext, Qualität, Inhalt und Zustellstatus. Die Timeline sortiert aufsteigend mit
`Message.timelineOrder`. Eingänge, Bearbeitungen, Löschungen und Kontaktkorrekturen wirken
über den gemeinsamen lokalen StateFlow. Es gibt keine zweite Kopie der Nachrichten.

`sendRoutes(target, text, replyTo)` bewertet jede Route separat für den konkreten Text.
SendText und bei Antworten zusätzlich Reply müssen verfügbar sein. Offline, ReadOnly,
Textlängenlimits, fehlende Sessions und weitere Providerrestriktionen bleiben sichtbar.
Capabilities werden niemals über mehrere Provider vereinigt. Eine Antwort muss eine
bekannte Remote-Nachricht derselben Route referenzieren; dienstübergreifende Zitate werden
abgewiesen. Der Textversand erfordert immer eine konkrete `SendRoute`; es gibt keinen
impliziten Standardprovider und keinen Fallback auf einen anderen Messenger.

`sendText` prüft die aktuelle Zielmitgliedschaft und Unterstützung erneut und speichert
Command plus optimistische Nachricht atomar in der vorhandenen dauerhaften Outbox.
Die Personenmitgliedschaft wird beim Commit nochmals geprüft, damit eine parallele
Kontaktkorrektur eine veraltete Auswahl abweisen kann. `Queued` bestätigt ausschließlich
die lokale Persistierung. Pro Account übernimmt die vorhandene `SyncCoordinator`-Instanz
Versand, erneute Capabilityprüfung unmittelbar vor Effekten, Retry und Reconciliation.
Nach dem Enqueue bleibt die gewählte Route fest, auch wenn Kontakte später getrennt werden.
Neue Offline-Aufträge werden hier als nicht verfügbar zurückgegeben; bereits persistierte
Aufträge überstehen nachfolgenden Verbindungsverlust im bestehenden Syncmechanismus.
Die Registry und die Lebensdauer der Syncjobs gehören dem Aufrufer.

Der Mock kann über `MockProviderFactory(configuration, providerId)` mehrere ausdrücklich
simulierte Messenger mit getrennten Accounts und Capabilityprofilen darstellen. Gleiche
Remote-Chatnamen kollidieren dank Accountscoping nicht. Dies sind keine Signal-/WhatsApp-/
Telegramintegrationen. `UnifiedCommunicationTest` prüft drei solche Messenger gemeinsam,
persistenten gezielten Versand, Herkunft, Sortierung, Archivierung, Capabilityunterschiede,
Cross-Route-Antworten, fehlende Sessions und Kontaktkorrekturen einschließlich Commitrace.
Der Android-Debug-Einstieg bleibt beim vorhandenen einzelnen Mockkonto; eine UI für
Personen-Timeline und Versandwegauswahl ist ein separates Arbeitspaket.

## Verwendung im Core

```kotlin
val sessionsByAccount = sessions.associateBy { it.accountId }
val communication = UnifiedCommunicationService(localRepository, sessionsByAccount::get)
// Für jede Session existiert ein vom App-Scope betriebener SyncCoordinator.
val target = ConversationTarget.PersonChat(personId)
val timeline = communication.observeTimeline(target)
val choices = communication.sendRoutes(target, "Hallo")
// selectedRoute stammt aus der expliziten Auswahl des Nutzers.
val result = communication.sendText(target, selectedRoute, "Hallo")
```

Die Capabilitybewertung ist eine suspendierende Abfrage und muss nach Providerzustands-
oder Texteingabeänderungen erneut erfolgen. Ein angezeigtes verfügbares Angebot garantiert
keinen späteren Transporterfolg: der Use Case prüft beim Enqueue und der Provider beim
Dispatch erneut. Fehler und unklare Ergebnisse bleiben in der vorhandenen Outbox sichtbar.
