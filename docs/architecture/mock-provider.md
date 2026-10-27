# Lokaler Mock-Messenger

`:core:provider-mock` ist ein Kotlin/JVM-Modul ohne Android-, Socket-, HTTP-,
Messenger- oder Dateisystemzugriffe. Es implementiert `ProviderFactory`,
`ProviderSession`, `ProviderMedia` und den Inbox-Repositoryport. Alle Kontakte,
Nachrichten und Medien sind synthetisch. Keine Accounts, Berechtigungen,
Google-Dienste oder Netzwerkverbindungen werden benötigt.

## Einstieg und Login

```kotlin
val factory = MockProviderFactory()
val session = factory.open(MockFixtures.handle())
AuthResponse("mock-login", AuthInputKind.Code, "000000".toCharArray()).use {
    check(session.authenticate(it) == AuthResult.Ready)
}
val result = session.execute(session.newCommand(CommandPayload.SendText("Hallo")))
```

Der feste Testcode ist kein Zugangsschlüssel. Falscher Code, falsche Art/ID,
Offline-Login und nach fünf virtuellen Minuten abgelaufene Challenges werden
abgewiesen. `restartLogin()` erneuert die Challenge. Logout widerruft den lokalen
Authzustand. `requireLogin = false` ermöglicht sofortige Previews. Jede Session
besitzt isolierte Daten, Accountreferenzen und einen eigenen Replay-Epoch.

## Beobachtung und Szenarien

- `state`, `conversations`, `identities`, `groups`, `messages`, `attachments`:
  schreibgeschützte StateFlows. `timeline(route)` sortiert Nachrichten stabil;
  `observeInbox()` liefert sichtbar als `[Mock]` markierte Chats.
- Startbestand: eigenes Profil, Anna, Ben, Direktchat, Gruppe mit Mitgliedern/Rollen
  und zwei Nachrichten. Das ist ein lokaler Testbestand, keine Fremddienstintegration.
- `execute`: Text, Medien, Antworten, Reaktionen, Bearbeiten mit erwarteter Revision,
  Löschen, Typing, Read Receipts und Logout über den regulären Providervertrag.
- `receiveText`, `receiveMedia`, `incomingTyping`, `incomingReaction`,
  `incomingReceipt`, `remoteEdit`, `remoteDelete`: kontrollierter Remoteeingang.
- `MockScenarios.run(session, scenario)`: wiederverwendbare Skripte `Conversation`,
  `Media`, `OfflineRecovery`, `UnknownDelivery`, `RevisionConflict`, `Capabilities`,
  `SyncGap`. Mit angemeldeter Full-Session starten. Absichtlich abgewiesene Commands
  sind im Rückgabewert sichtbar. Jedes Szenario lässt sich in frischer Session wiederholen.

```kotlin
val session = MockProviderFactory(MockConfiguration(requireLogin = false)).createSession()
MockScenarios.run(session, MockScenarios.Scenario.Conversation)
session.setOnline(false)
session.receiveText(text = "Verspätet", delay = Duration.ofSeconds(3))
session.advanceBy(Duration.ofSeconds(10)) // weiterhin kein Eingang
session.setOnline(true)                 // überfälliger Eingang wird zugestellt
```

Virtuelle Zeit startet fest am 27.10.2026, 12:00 UTC. `advanceBy` arbeitet ohne
reale Wartezeit. Fällige Eingänge bleiben bei Offline/Authsperre vorgemerkt;
Reihenfolge bei gleichem Zeitpunkt ist stabil. Nachrichten behalten Sende- und
Empfangszeit getrennt. Typing endet nach steuerbarer Dauer. Für langsamen Versand
kann `dispatchDelayMillis` gesetzt werden: Coroutine-Delay, in `runTest` virtuell
steuerbar. Prüfung und Side Effects erfolgen erst danach; Abbruch wirkt vor Versand.

## Capabilities, Fehler und Konflikte

`Full`, `TextOnly`, `ReadOnly` bilden unterschiedliche Unterstützung ab.
`setProfile` und `setOnline` ändern die Capabilityrevision und benachrichtigen
Beobachter. Commands mit veralteter Revision, ungültigen Scopes, Ablaufzeit,
Schema, unerfüllten Abhängigkeiten oder zu langen Texten werden abgewehrt.
Ein fremder Sender darf nicht bearbeitet oder für alle gelöscht werden.

`failNext` steuert den nächsten sonst gültigen Dispatch:

| Fehler | Wirkung | Ergebnis |
|---|---|---|
| NetworkNoEffect | keine | RetryableNoEffect |
| RateLimited | keine, Retryzeit +30 s | RetryableNoEffect |
| UnknownNoEffect | keine, Antwort verloren | OutcomeUnknown |
| UnknownAfterCommit | ausgeführt, Antwort verloren | OutcomeUnknown |

`reconcile` weist die tatsächliche Wirkung nach. Gleiche Command-ID oder gleicher
Idempotency-Key mit gleicher Route/Payload erzeugen keine Duplikate; abweichende
Payloads werden zurückgewiesen. Der Mock legt **keine persistente Offline-Outbox**
an und bestätigt keine dauerhafte Retryübernahme mit `Accepted`. Offline-Versand
muss vom aufrufenden Core sichtbar gehalten und erneut beauftragt werden.

`remoteEdit` simuliert konkurrierende Änderungen. Veraltete `expectedRevision`
führt zu `SyncConflict` statt Überschreiben. `simulateSyncGap()` erzeugt einen
sichtbaren Gapzustand/Event, `resync()` wiederholt den aktuellen lokalen Bestand.
Das ist ein steuerbarer Provider-Abgleich, keine implementierte Core-Sync-Engine.

## Events und Medien

`events(after)` gibt seit Sessionbeginn beziehungsweise strikt nach dem Cursor
Events aus und beobachtet weitere Eingänge. Ein append-only In-Memory-Journal
verhindert Verlust bei langsamen Collectoren; `acknowledge` prüft Account, Stream,
Epoch und existierende Position. `recordedEvents` liefert einen Snapshot für
Assertions. Close beendet aktive Eventcollector und verwirft geplante Eingänge.
Der Journalverbrauch wächst mit dem Test; frische Sessions je Test verwenden.
Keine Haltbarkeit über Sessionende oder Prozessneustart: Replay ist im Descriptor
bewusst `BestEffort`, keine vorgetäuschte Produktionspersistenz.

Medien werden mit standardmäßig höchstens 1 MiB vollständig im Speicher gehalten;
Up-/Download erfolgt in begrenzten Chunks. Upload liefert Attachmentreferenzen,
Metadaten und eine defensive Kopie. Streams gehören dem Aufrufer, auch bei Fehlern
oder Cancellation. Downloads erlauben keine fremden Accountreferenzen und liefern
nie Netzwerk-URLs. `receiveMedia` kann alle AttachmentKinds einschließlich
Sprachnotizen erzeugen. Szenariobytes sind bewusst kleine synthetische Nutzdaten,
keine abspielbaren Video-/Audiodateien; Decoder-/Codec-Tests benötigen gültige,
separat bereitgestellte lokale Mediendateien.

## Android und Regression

```sh
./gradlew --offline :core:provider-mock:test
./gradlew --offline :app:assembleDebug -Porbiliq.mock=true
./gradlew --offline :app:assembleDebugAndroidTest -Porbiliq.mock=true
```

Die optionale Debug-Injection zeigt den lokalen Bestand in der vorhandenen Inbox.
Ohne Property bleibt deren Leerzustand bestehen. Release verwendet ausschließlich
das unkonfigurierte Repository und hat keine Mock-Abhängigkeit. Der vorhandene
Gerätetest prüft je nach Build beide Zustände und Activity-Neuerstellung.
Es wurde keine neue Chat-/Login-/Medien-UI gebaut; zukünftige Screens können die
StateFlows und Szenariosteuerung direkt in ihren Testaufbau injizieren.

Der Build kann mit bereits lokal vorhandenen Abhängigkeiten offline laufen.
Ein erstmaliger Gradle-/SDK-Download ist davon getrennt. Die lokale Testsuite
prüft Providerfunktionen, Isolation, Validierung, Idempotenz, Fehlersimulation,
Replay, Cancellation, Medien und den Sessionguard ohne Account und Netzwerk.

## Ergänzung durch Arbeitspaket 6

Der isolierte `MockProviderSession` bleibt im Speicher. Der Debug-App-Einstieg verwendet
jetzt `PersistentMockRepository`: verschlüsselte Domainprojektion, Wiederherstellung und
laufende Persistierung neuer Ereignisse. Details und Grenzen (insbesondere flüchtige
Medienbytes) im [lokalen Speicherkonzept](local-storage.md).
