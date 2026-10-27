# Technische Gesamtarchitektur von Orbiliq

Stand: 27.10.2026. **Beschlossener Zielentwurf; Android-Teilgrundgerüst inzwischen implementiert, siehe [Foundation](foundation.md).**

## Ausgangslage und Geltungsbereich

Geprüft wurden der gesamte vorhandene Dokumentationsbestand, insbesondere [Projektkontext](../PROJECT_CONTEXT.md), [Recherche](../research/README.md), deren vier Provideranalysen, [Architekturvorstudie](../research/05-architecture.md), Lizenzdossier und ADR 0001 sowie das bereitgestellte Projektwissen. Bei der ursprünglichen Architekturplanung gab es keine Android-Quellen, Gradle-Dateien, Tests oder Git-Metadaten. Die inzwischen realisierte Teilstruktur und ihr Prüfnachweis stehen in der [Foundation-Dokumentation](foundation.md). Die Recherche beschreibt Potenzial, keine produktiven Capabilities.

Orbiliq ist ein Android-Client in Kotlin mit Compose/Material 3 Expressive. Lokale Ansichten und Aufträge funktionieren ohne Orbiliq-Server und ohne Play Services. Kommunikation mit den jeweiligen Messenger-Diensten bleibt notwendig. Der Entwurf konkretisiert ADR 0001; dessen Integrations- und Releasebedingungen gelten weiter. Bibliotheksversionen, minSdk und targetSdk sind inzwischen in der Foundation festgelegt.

## Schichten und Abhängigkeitsrichtung

```text
:app                         Composition Root, Navigation, Android-Einstiege
  -> :feature:*              Compose + ViewModels
       -> :core:domain       Use Cases + Repository-Ports
            -> :core:model   reine Kotlin-Werte und Regeln

:core:data -> domain, database, media, provider-api
:core:sync -> provider-api, domain, eigener SyncStore-Port
:core:data implementiert SyncStore über :core:database
:provider:* -> provider-api -> model
:core:background -> sync, domain
:core:notifications -> domain
:core:database / media / security -> model bzw. kleine eigene Ports
:app verbindet Implementierungen, Ports und Provider per Hilt
```

Pfeile bedeuten Compile-Abhängigkeiten, nicht die Richtung jedes Laufzeitaufrufs. Es gibt keine Kreisabhängigkeit zwischen Sync und Data. `:core:sync` kennt weder Room noch konkrete Provider. Domain kennt weder Android, Hilt, Compose, Room, TDLib noch Binder. Features kennen keine Data-Implementierungen. Concrete Provider importieren sich gegenseitig nicht. Kein allgemeines `utils`-Modul als Ablage ungeklärter Zuständigkeiten.

| Geplantes Gradle-Modul | Verantwortung / Grenze |
|---|---|
| `:app` | Manifest, Activity, DI-Graph, Feature-Navigation und explizite Providerregistrierung; einzige Stelle für konkrete Zusammenstellung |
| `:core:model` | IDs, Entitäten, Capabilities, normalisierte Fehler; Kotlin/JVM |
| `:core:domain` | Use Cases, Repository-Interfaces, Versand-/Kontakt-/Privacy-Regeln; Kotlin/JVM |
| `:core:provider-api` | Providervertrag, Commands, Events, Auth- und Transportzustände; Kotlin/JVM mit Coroutines |
| `:core:data` | Repositories, Mapping, Transaktionen, SyncStore-Implementierung, lokale Projektionen |
| `:core:database` | Room/SQLCipher, Entities, DAOs, Schema-/Migrationsbesitz |
| `:core:sync` | Account-Koordination, Inbox-/Outbox-Verarbeitung, Reconciliation, Retry-Policy |
| `:core:media` | verschlüsselter Blobstore, Import/Export, Cache, Transferkoordination über Ports |
| `:core:security` | Android Keystore, Key-Vault, App-Lock, IPC-Vertrauensprüfung; keine Messenger-Kryptografie |
| `:core:background` | WorkManager, zulässige Service-Einstiege, Netzwerk-/Boot-Signale |
| `:core:notifications` | Notification-Renderer, Channels, sichere Actions; kein Nachrichtenarchiv |
| `:core:designsystem` | Material 3 Expressive, Theme, Accessibility, gemeinsame UI-Komponenten |
| `:feature:inbox`, `:feature:conversation`, `:feature:contacts`, `:feature:search`, `:feature:accounts`, `:feature:settings` | eigener Screen-State und Nutzeraktionen; nur öffentliche Feature-Einstiege |
| `:provider:telegram` | TDLib-Adapter, accountgetrennte native Session, Protokollmapping |
| `:provider:companion` | generischer lokaler IPC-Client für optionale externe Backends |
| `:provider:notification` | begrenzte Vorschauen, bedingtes RemoteInput und App-Handoff |
| `:core:testing`, `:provider:contract-tests` | Fakes, virtuelle Zeit, gemeinsame Verhaltensverträge; keine Produktionsabhängigkeiten |

Signal-/WhatsApp-Backends wären eigenständige, separat gebaute Companion-Apps nach Freigabe; ihre Implementierung gehört nicht stillschweigend in den Haupt-APK-Graph. Nicht jeder Tabellenposten muss im ersten Commit ein eigenes leeres Modul werden: zuerst vertikaler Telegram-Textpfad, weitere Module mit echter Verantwortung ergänzen. Die Grenzen bleiben verbindlich.

Implementierter Modell-/API-Stand: [Domain Model und Provider API](domain-model.md).
Lokales Testbackend mit Debug-Injection: [Mock-Messenger](mock-provider.md).

## Domain Layer: Identitäten und fachliche Regeln

- `ProviderId` ist ein offener stabiler String, kein Enum aller Messenger. `AccountId` ist eine lokale UUID für genau eine Installation/Session eines Providers.
- `IdentityRef(accountId, remoteIdentityId)` und Aliaszuordnungen erhalten providerinterne IDs und Migrationen. Telefonnummern sind optionale Attribute.
- `Person(personId)` und `PersonIdentityLink` bilden lokale, manuell korrigierbare Beziehungen mit Herkunft des Zuordnungsvorschlags ab. Merge/Unmerge verändert keine Remoteidentitäten oder Originalnachrichten. Kein automatischer Kontaktupload.
- `ConversationRef(accountId, remoteConversationId)` bleibt Transportziel. Gruppen und Topics erhalten eigene IDs, Rollen und Mitgliedschaften. Keine automatische Gruppenfusion anhand des Namens.
- `MessageRef(conversationRef, remoteMessageId)` ist nach Bestätigung die Remoteidentität; `LocalMessageId` bleibt auch beim Wechsel einer temporären Provider-ID stabil. Replies referenzieren das Original, optional mit Provider-Thread-ID.
- `Message` enthält Sender, Inhalt, Originalroute, Serverzeit, lokale Empfangszeit, optionale Providerreihenfolge, Revision, Ablaufzeit, Herkunft/Qualität und Sicherheitszustand. Bodyvarianten: Text, Media, Service, Unsupported. Nicht unterstützte Inhalte werden erklärt, nicht verworfen.
- `UnifiedTimeline` ist eine lokale Ansicht über explizit ausgewählte Conversations. Sortierung: angezeigte Zeit, danach stabile lokale ID; keine globale Kausalität behaupten. Versenden verlangt ein konkretes Zielkonto und eine konkrete Conversation. Kein stiller Providerwechsel.
- `SecurityContext` unterscheidet E2EE, Cloud/Transport und fremde Notification-Vorschau sowie bekannte/unbekannte Verifikation. Zusammenführung verändert diese Werte nicht.
- `ContentPolicy` enthält Ablauf, View-once, Export-/Cacheverbote. Inhalte, deren Schutzsemantik noch nicht zuverlässig umgesetzt ist, werden nicht heruntergeladen oder als unterstützt freigegeben.

Repository-Ports: `ConversationRepository`, `MessageRepository`, `PeopleRepository`, `AccountRepository`, `CapabilityRepository`, `SearchRepository`, `MediaRepository`. Listen werden paginiert aus lokalen Projektionen gelesen. Use Cases wie `ObserveInbox`, `ObserveTimeline`, `QueueMessage`, `EditMessage`, `DeleteMessage`, `MarkRead`, `MergePeople`, `RequestHistory` prüfen fachliche Voraussetzungen; keine Pass-through-Klasse pro DAO-Methode.

## Capability-basierter Providervertrag

Der normative Vertrag steht in [Providervertrag und Capability-Katalog](provider-contract.md). Maßgeblich ist die Operation am konkreten Ziel, nicht der Messengername. Ein Provider publiziert statisches Potenzial und dynamische Account-/Conversation-/Message-Einschränkungen. Unbekannt oder veraltet berechtigt nie zum Versand. Die Domain prüft beim Enqueue, die Sync Engine unmittelbar vor Ausführung und der Provider erneut gegen seinen aktuellen Zustand. Änderungen führen zu sichtbarem Blockieren oder Ablehnen, nicht zu einer anderen Operation.

Provider-eigene Auth-Schritte und Zusatzfunktionen werden als typisierte, versionierte Beschreibungen übergeben. Die generische UI kann unterstützte Formtypen rendern; unbekannte Schritte verlangen ein kompatibles Update oder eine klar bezeichnete Übergabe. Beliebiger Code, HTML und Javascript aus einem Provider werden nicht ausgeführt. Stark spezialisierte native Oberflächen sind optional registrierte Feature-Erweiterungen im Composition Root, niemals Bedingungen in gemeinsamen Screens.

## Data Layer und lokale Persistenz

Die Orbiliq-Datenbank ist die einzige Lesequelle für archivierte UI-Daten. Providerstores bleiben autoritativ für Protokoll-/Sessionzustand; Remoteereignisse für bestätigten Dienstzustand. Orbiliq besitzt lokale Entwürfe, Kontaktlinks, Cachepolitik und Auftragsstatus. Keine zweite Ratchet-/MTProto-Zustandsmaschine.

Entscheidung: Room mit SQLCipher für die lokale Projektion, einschließlich Suchindex, Journal und Outbox. Nicht das ältere `android-database-sqlcipher` einführen. Integration, native ABIs, Seitengrößen und Versionen im Buildprototyp prüfen. DataStore nur für nicht sensible Einstellungen wie Theme; keine Sessions, Nachrichtentexte oder Kontaktdaten dort.

| Tabellenbereich | Wichtige Invarianten |
|---|---|
| Accounts / Capabilities / SyncCheckpoints | Provider-ID, Runtime-/Vertragsversion, Fähigkeitssnapshot und Revision; Checkpoint je Stream/Epoche |
| Persons / IdentityLinks / IdentityAliases | reversible lokale Links, accountgebundene Remoteidentitäten |
| Conversations / Members / Topics | Remote-ID nur im Account eindeutig; Rollen und Sicherheitskontext |
| Messages / MessageAliases / Revisions / Reactions / Receipts | eindeutige Remote-Referenz; lokale ID stabil; versionierte Änderungen; Tombstones |
| InboxEvents / ProjectionProgress | eindeutiger Eventkey je Account/Stream/Epoche; atomare Projektion und Fortschritt |
| OutboxCommands / Dependencies / Attempts | stabile Command-ID, Route, Ziel, Policyrevision, Status, nextAttemptAt, Lease, Ergebnisreferenz |
| MediaObjects / MediaRefs / Transfers | Besitzkonto, zufällige Blob-ID, Lebensdauer, Verschlüsselungsmetadaten, Referenzen |
| SearchIndex / NotificationLedger | gleicher Schutz wie Nachrichten, Inhalts-/Ereignisreferenz statt zweite unkontrollierte Textkopie |

Transaktionen enthalten keine Netzwerkaufrufe. Enqueue schreibt optimistische Nachricht und Outbox atomar; Versand erst nach Commit. Ein Scheduler-Signal ist nur ein Hinweis: beim Start und periodisch wird die persistente Queue gescannt. Unique Constraints sichern Deduplizierung. Migrationsschema wird exportiert und mit realistischen verschlüsselten Altständen geprüft; keine destruktive Migration oder stilles Sessionlöschen. Platzmangel beendet Empfangsbestätigung und zeigt einen Fehler. Bei korruptem Store keine automatische Löschung.

Volltextsuche umfasst ausschließlich erlaubte, lokal vorhandene Inhalte. Ergebnisse zeigen Cache-/Historiengrenzen; Remote-Suche ist eine gesonderte Capability und verändert den lokalen Vollständigkeitsanspruch nicht. Kein externes Embedding-/Such-Backend.

## Sync Engine und Konflikte

Je Account ein langlebiger logischer Koordinator mit begrenzter Parallelität. Prozess, Worker und Vordergrund teilen dieselben persistenten Leases mit Fencing-Token; abgelaufene Besitzer dürfen keinen neuen lokalen Zustand committen. Das ersetzt keine Remote-Idempotenz. Conversationabhängige Commands laufen geordnet; Medien können begrenzt parallel übertragen werden. Ein defektes Konto blockiert keine anderen Konten.

**Empfang:** Providerjournal/Replay → normalisiertes Event → atomar Inbox + Projektion + Checkpoint → bestätigter Cursor, falls unterstützt → Notification-Auftrag. Bei getrenntem durable Inbox-Schritt wird nur der persistierte Empfang bestätigt, der Projektionsfortschritt separat geführt. Nach Absturz werden unprojizierte Events wieder aufgenommen. Doppelte Events ändern den Zustand nicht erneut. Kein pauschales Event-Sourcing: Journal wird nach erfolgreicher Projektion und sicherem Replayfenster datensparsam gekürzt.

Ein Callback-Flow ist kein Journal. Provider ohne Replay/Bestätigung melden `BestEffort` und erkennbare Lücken; Reconciliation lädt erreichbare Historie nach. Unkontrollierbares Ack vor Orbiliq-Persistenz ist eine dokumentierte Verlustgrenze. Notification-Vorschauen erhalten keine erfundenen stabilen Messenger-IDs und keinen Vollständigkeitsstatus.

**Versand:** `Queued → Preparing → InFlight → ProviderAccepted → Sent`, danach optional `Delivered` und `Read`. ProviderAccepted bedeutet lediglich dauerhafte Übernahme durch das Backend. Nebenpfade: `RetryScheduled`, `Blocked`, `Failed`, `Cancelled`, `OutcomeUnknown`. Zustellung/Lesen sind separate monotone Bestätigungen mit Empfängerumfang; Gruppen-Receipts sind keine einzelnen universellen Booleans.

Vor dem externen Aufruf wird InFlight dauerhaft gespeichert. Crash, Timeout oder Cancellation nach möglichem externem Effekt führen ohne sichere Idempotenz zu OutcomeUnknown. Erst anhand stabiler Client-/Remote-ID und Sync abgleichen. Keine automatische Neuübermittlung bei unklarer Wirkung; wenn Abgleich unmöglich bleibt, bietet die UI bewusstes erneutes Senden mit Duplikathinweis. Ein Nutzerabbruch nach möglichem Versand kann keine Zustellung rückgängig machen. Native persistente Providerqueues besitzen den Transport-Retry nach bestätigter Übernahme; Orbiliq fragt nach, statt zusätzlich zu senden. Diese Besitzübergabe muss der Adapter nachweisen.

Vorübergehende Fehler: exponentieller Backoff mit Jitter, Obergrenze und persistiertem Termin; Server-Retry-After hat Vorrang. Abhängige Aufträge (Upload → Send → Edit/Reply) warten auf eindeutige Referenzen. Abgelaufene Editfristen und zurückgezogene Rechte ergeben sichtbares Failed/Blocked. Typing/Presence sind kurzlebig, werden nicht offline nachgesendet.

Konflikte werden nach providerspezifischen Revisionen/Sequenzen aufgelöst, nicht nach pauschalem Last-write-wins anhand Geräteuhren. Receiptzustände steigen nur bei passendem Ereignis; alte Edits dürfen neuere nicht überschreiben. Frühe Deletes erzeugen Tombstones und verhindern Wiederauferstehen durch verspätete Inserts. Fehlende Referenzen werden begrenzt zurückgestellt und nachgeladen. Nicht vergleichbare Revisionen lösen Provider-Reconciliation und sichtbare Unsicherheit aus. Lokales Löschen, Remote-Löschen und Retention sind unterschiedliche Operationen; Tombstone-Metadaten bleiben nur soweit für Deduplizierung erforderlich ohne Inhalt erhalten.

## Media Storage

Appinterner verschlüsselter Blobstore, Metadaten in der geschützten DB. Zufällige Blob-IDs, keine ungeschützten Dateinamen oder kontenübergreifende Hash-Deduplizierung. Zufälliger Schlüssel pro Objekt, über Accountschlüssel gewrappt; bewährte Streaming-AEAD-Bibliothek nach Dependencyprüfung, keine eigene Cipherkonstruktion. Thumbnails, Wellenformen und temporäre Aufnahmen unterliegen derselben Policy.

Import über Photo Picker/SAF und gültige URI-Berechtigungen, begrenzte Streams, Größen-/MIME-Prüfung; vor Offline-Enqueue in den eigenen Store kopieren. Upload übernimmt der Provider mit seinen Format-/Größenregeln. Downloads sind explizit oder über konfigurierbare Netz-/Cachepolitik erlaubt. Transfers besitzen Offset/Token nur wenn das Backend Resume unterstützt; sonst kontrollierter Neustart.

Datei-/DB-Commit ist nicht atomar: in Staging verschlüsseln, vollständig schließen, atomar umbenennen, dann DB-Referenz committen. Beim Start verwaiste Staging-/Blobdateien und unterbrochene Transfers bereinigen. Outbox- und aktive Transferreferenzen schützen Dateien vor LRU-Eviction. Quota, verfügbare Bytes und aktive Entschlüsselungsstreams begrenzen; keine ganzen Videos im RAM.

Providerbibliotheken können Klartextdateien verlangen: nur privater kurzlebiger Bereich, kurze Lease, Cleanup bei Erfolg/Crash, keine Backupaufnahme. Wenn das gewünschte Schutzniveau damit nicht erfüllbar ist, betreffende Capability blockieren. Exporte nur ausdrücklich über kurzlebige Content-URIs mit minimaler Leseberechtigung. Exportierte Kopien können später nicht zurückgerufen werden. Ablauf/View-once entfernt auch Index, Vorschaudaten, Notification und interne Kopien; physische sichere Flash-Löschung wird nicht zugesichert.

## UI Layer

Compose-Screens beobachten unveränderliche `StateFlow<UiState>` aus ViewModels und senden Nutzeraktionen an Use Cases. Lokale Daten erscheinen sofort mit Frische-, Lücken-, Sicherheits- und Queuezustand. `collectAsStateWithLifecycle`, stabile Listenschlüssel und Paging verhindern unbegrenztes Laden. `SavedStateHandle` speichert IDs und Navigation, keine Sessions oder umfangreichen Klartextinhalte.

Capability-Auswertung erzeugt erklärbare verfügbare/gesperrte Aktionen. Read-only, fehlendes Recht, Offline, Update erforderlich und experimenteller Modus sind verschiedene Zustände. Route und Verschlüsselungsmodus bleiben im Composer sichtbar. Dauerhafte Fehler/Sendestatus kommen aus DB-Zustand; einmalige UI-Effekte sind keine einzige Quelle wichtiger Fehler. Material 3 Expressive, reduzierte Bewegung, TalkBack, skalierbare Schrift und ausreichende Kontraste sind gemeinsame Designsystempflichten.

## Notification Layer

Nach erfolgreicher Projektion werden idempotente Notification-Aufträge verarbeitet; stabile IDs und Ledger verhindern doppelte Hinweise nach Replay. Aufholen älterer Historie erzeugt standardmäßig keine Benachrichtigungsflut. Konten/Chats haben konfigurierbare Channels, sensible Inhalte sind auf dem Sperrbildschirm standardmäßig verborgen. Fehlende Notification-Berechtigung verhindert keine lokale Speicherung.

Actions nutzen explizite, passend beschränkte PendingIntents und nicht erratbare Zuordnung; RemoteInput benötigt eine passende mutable-Konfiguration mit eng festgelegtem Ziel. Jeder Einstieg validiert Account, Conversation, Lock-/Privacyzustand und aktuelle Capability erneut und persistiert den Auftrag. „Als gelesen markieren“ erfolgt nur bei bewusster Aktion oder definierter Sichtbarkeitsregel, niemals durch bloßes Anzeigen einer Notification. Navigation verwendet lokale IDs.

Der eingehende Notification-Provider ist hiervon getrennt: Nutzerfreigabe, bekannte Original-App, Vorschauqualität, optional gültige Schnellantwort. PendingIntents werden nicht als dauerhafte Offlineaufträge serialisiert; ungültige Antwortaktionen erfordern neue Aktion oder Handoff. Parallelbetrieb mit einem Vollprovider wird je Konto ausdrücklich ausgeschlossen oder deduplizierend nachgewiesen, nicht anhand gleicher Nachrichtentexte geraten.

## Background Processing

WorkManager dient nachholbarem Sync, Queue-Scans, Retry und Cleanup, nicht einer dauerhaften Socketverbindung. Ein eindeutiger Account-Worker und die Lease verhindern parallele Ausführung. Nur opaque IDs in WorkData, keine Texte/Secrets; nextAttemptAt bleibt in der verschlüsselten DB. Ein begrenzter Worker verarbeitet mehrere fällige Aufträge und plant Restarbeit neu. Constraints und OS-Wartezeiten bleiben sichtbar.

Vordergrundaktivität aktiviert geeignete Providerverbindungen. Dauerbetrieb über einen sichtbaren Dienst wird nur für nachgewiesen zulässige Android-Servicearten angeboten; kein FGS-Typ als Umgehung. App-Start, Netzwechsel, zulässiger Boot-/Unlock-Einstieg und Worker lösen Reconciliation aus. Vor erster Geräteentsperrung bleibt der sensible Store geschlossen. Force-stop, Doze und OEM-Beendigung werden nicht durch Echtzeitversprechen verdeckt. Optionales Push erfordert Providerunterstützung und ausdrückliche Konfiguration; FCM oder ein eigener Server sind keine Basisabhängigkeit.

## Security Boundary

Bedrohungsmodell: verlorenes/gesperrtes Gerät, andere Apps, manipulierte IPC-/Medieninputs, Backup-/Logabfluss und widerrufene Sessions. Ein kompromittiertes entsperrtes OS oder privilegierter Angreifer kann nicht durch App-Lock zuverlässig abgewehrt werden. Orbiliq und freigegebene Companions sind zusätzliche entschlüsselnde Endpunkte; keine durchgängige E2EE über die gemeinsame lokale Timeline behaupten.

Zufällige DB-/Accountschlüssel werden mit Android-Keystore-Schlüsseln gewrappt; Hardwarestützung nur behaupten, wenn auf dem Gerät nachgewiesen. Sessions bleiben beim Provider, der Core bekommt keine Ratchet-/Login-Secrets. Provider-native Stores und temporäre Medien sind eigenständig zu schützen. Alle sensiblen Dateien und Sessions werden aus automatischen Cloud-/Gerätebackups ausgeschlossen. Keystoreverlust führt zu erklärter Nichtverfügbarkeit/Reauth, nicht stiller Neuinitialisierung. Ein sicherer Export/Transfer ist ein späteres eigenes ADR.

Standard-App-Lock sperrt Oberfläche, Exporte und Actions, erlaubt nach erster Geräteentsperrung Hintergrund-Sync mit nicht benutzerpräsenzgebundenem Wrapping-Key. Das ist eine UI-Sperre und wird so benannt. Optionaler strenger Modus schließt sensible Stores/Provider bei Sperre und verlangt Benutzerpräsenz zur Schlüsselverwendung; dann sind Empfang/Benachrichtigungsinhalte eingeschränkt. Keine gegenseitig unvereinbaren Sicherheits- und Empfangsversprechen.

In-process-Provider sind voll vertrauenswürdig und **keine Sandbox**. Native Abstürze können die Haupt-App beenden. Companions haben eigene UID/Stores, jedoch nach Freigabe Klartextzugriff auf ihren Datenumfang. IPC verwendet expliziten Binder-Service, Vertrags-/Versionshandshake, Paket-/UID-/Zertifikatsprüfung und nutzerbestätigtes Pairing. Bei verschieden signierten APKs werden Zertifikatsfingerprints über einen vertrauenswürdigen Installationsweg bestätigt; keine bloß behauptete Paketkennung als Vertrauen. Beide Seiten prüfen jeden Aufruf, UID-Wechsel/Zertifikatsrotation verlangen erneute autorisierte Bindung. Ein einmaliges Pairing-Token wird nur über einen expliziten, authentisierten Nutzerdialog ausgetauscht und geschützt gespeichert; keine Secrets in impliziten Intents.

Operationen sind auf gepaarte Accounts begrenzt; Widerruf beendet Zugriff und Medienleases. Binder transportiert begrenzte Metadaten, Medien per FD/kurzlebiger URI. Payloadgrößen, Schema, Sequenz und Ressourcenverbrauch werden geprüft. Kein offener localhost-HTTP-Port. Ein separater Prozess ist weder automatische Lizenzfreigabe noch vollständiger Schutz gegen bösartigen gepaarten Provider.

Logs enthalten nur lokale Diagnosecodes, Dauer und redigierte Zustandswechsel. Keine Inhalte, Telefonnummern, Remote-IDs, Tokens oder SQL-Bindings. Keine Telemetrie/Tracker. Berechtigungen werden funktionsbezogen angefragt; Kontakte, Kamera, Mikrofon und Notificationzugriff sind optional. TLS-Prüfung bleibt aktiv; keine eigene Messenger-Kryptografie.

## Dependency Injection und Coroutines/Flow

Hilt im Android-Composition-Root, Constructor Injection in Implementierungen. Providerfabriken werden explizit als Map nach offener ProviderId registriert. Keine Reflection-Suche oder aus dem Netz geladenen Plugins. Tests konstruieren Domain/Sync direkt mit Fakes; Core benötigt keine DI-Annotationen. Account-Scope wird durch eine Factory und explizites close/cancel geführt, nicht durch einen globalen Session-Singleton.

Ein `SupervisorJob` isoliert Accounts; darunter strukturierte Child-Jobs für Empfang, Reconciliation und Versand. Fehler werden normalisiert und sichtbar, niemals nur vom Supervisor verschluckt. Keine GlobalScope-Jobs. CancellationException wird weitergereicht; irreversible Nebenwirkungen werden anschließend über persistierte Zustände abgeglichen. UI-Scope besitzt keinen Protokolllifecycle.

Reads liefern Flow aus DB; suspend-Funktionen persistieren Commands und geben Command-ID zurück. StateFlow repräsentiert aktuellen Status, nicht jedes Nachrichtenereignis. SharedFlow dient nur verlierbaren UI-/Präsenzhinweisen. Callbackadapter mit bounded Channel/callbackFlow dürfen bei Überlastung keine dauerhaften Events mit DROP_OLDEST verlieren: diskbasiertes Journal oder expliziter Overflow mit Reconnect/Gap. JNI-Callbacks dürfen nicht unkontrolliert blockiert werden. Close entfernt Callbackregistrierungen.

IO/CPU-Dispatcher, Clock, ID-Generator und Retry-Zufall sind injizierbar. Blocking JNI/Dateioperationen laufen nicht auf Main; CPU-Mapping/Thumbnails auf begrenztem Default-Pool. Reihenfolge wird pro Stream erhalten, nicht durch unbeschränktes launch zerstört. Typing und Presence dürfen conflated werden und verfallen nach TTL.

## Fehlerbehandlung und Diagnose

`OperationResult` unterscheidet fachliche Ablehnung, sichere fehlgeschlagene Ausführung und unbekannte Wirkung. Normalisierte Kategorien: NetworkUnavailable, RateLimited(retryAt), AuthRequired, SessionRevoked, IdentityChanged, PermissionDenied, CapabilityUnavailable, PolicyExpired, MediaUnavailable, StorageFull, StorageCorrupt, IncompatibleProvider, ProtocolFailure und OutcomeUnknown. Providerfehlercodes sind redigierte Zusatzdiagnose, keine UI-Texte.

Die Domain entscheidet anhand der Kategorie: Retry, blockierter Auftrag mit Nutzeraktion, dauerhaftes Scheitern oder Abgleich. Keine pauschale catch-and-retry-Schleife und kein automatisches Akzeptieren eines Identitätswechsels. Decodefehler landen als begrenzter Quarantäneeintrag ohne unnötige Rohdaten, erzeugen Lücken-/Updatehinweis und stoppen nicht andere Konten. Debugexport ist ausdrücklich und lokal redigiert. Lokale Diagnose zeigt letzte erfolgreiche Verarbeitung, Queueumfang, Lücken und erforderliche Aktion.

## Teststrategie und Erweiterungsnachweis

Spätere Implementierung muss folgende überprüfbare Verträge erfüllen:

| Ebene | Akzeptanzkriterium |
|---|---|
| Domain/JVM | Capabilityentzug nach Enqueue verhindert Execute; Merge/Unmerge erhält Routen; kein Fallback zu anderem Provider |
| Sync mit virtueller Zeit | Duplikate und Out-of-order-Edit/Delete; Backoff/Retry-After; Absturz vor/nach Commit/Send; OutcomeUnknown löst keine blinde Neuübermittlung aus |
| DB/Android | verschlüsselte Migration, FTS-/Medienlöschung, Transaktionen und Unique Constraints, StorageFull, Leases/Fencing |
| Providervertrag | mehrere Konten, Auth/Revoke, Event-Replay oder deklarierte Lücke, ID-Mapping, dynamische Rechte, Größenlimits und unbekannte Typen |
| UI | Text-only, Preview-only und voller Fakeprovider erzeugen passende Aktionen und nachvollziehbare Einschränkungen ohne Namensabfragen |
| IPC/Security | fremde UID, falsches Zertifikat, Replay/Version/Übergröße, widerrufenes Pairing und abgelaufene Medienhandles |
| reale Geräte | Google-frei, Doze, Standby, Force-stop, Neustart/Unlock, OEM, Akku und native ABIs; echte kontrollierte Testkonten |

Neuer Provider: Factory/Adapter erstellen, Auth/Mapping und deklarierte Operationen implementieren, Contract Suite bestehen, Dependency-/Lizenzprüfung durchführen, im App-Graph registrieren. Beispiel eines neuen Text-only-Providers: er deklariert nur Textempfang/-versand und seine Replayqualität; gemeinsame Inbox, Composer, Outbox, Suche im lokalen Bestand und Benachrichtigungen funktionieren ohne Änderungen an Domain oder gemeinsamen Screens. Neue Semantik wie eine bislang unbekannte Inhaltsart kann eine additive Vertrags-/Renderer-Erweiterung benötigen; das ist kein Umbau vorhandener Messengerzweige.

Startprofil: Telegram-Text/Cloud-Historie und begrenzte Medien nach Prototypnachweis. Signal bedingter Companion, WhatsApp experimentell, Threema Preview/Handoff gemäß ADR 0001. Calls bleiben deaktiviert bis vollständiger Medienstack, Lifecycle und Sicherheitsnachweis vorliegen. Channels bleiben nach der Rechercheentscheidung bis zur gesonderten Produkt-/API-Freigabe deaktiviert.

## Umsetzung und offene Nachweise

1. Kotlin-/Android-Build mit festgehaltenen Versionen, Modulgrenzen und Fakeprovider; Contract Suite und lokale Text-Outbox.
2. Room/SQLCipher-/Keystore- und TDLib-ABI-Prototyp, Sessionisolation und Crash-Recovery; kein Release ohne Persistenznachweis.
3. Durchgehender Telegram-Textpfad samt Auth, Reconciliation und sichtbarem Hintergrundzustand auf Google-freiem Gerät.
4. Medien, Privacy/Retention, Rechtewechsel und reale Hintergrundmessungen; danach weitere Provider entlang ihrer Freigabebedingungen.

Im ursprünglichen Architekturpaket wurden Dokumentation, lokale Verweise und Entscheidungsabdeckung geprüft. Aktueller Implementierungs-/Teststand: [Foundation](foundation.md) und [Buildvalidierung](../build-validation.md). **Im ursprünglichen Architekturpaket wurde keine App gebaut und es wurden keine ausführbaren Schnittstellen oder Geräte-, Konto-, Sicherheits- oder Lizenzfreigabetests umgesetzt.** Insbesondere Bibliothekskombination, Keyhandling, strenger Lock, IPC-Pairing und jeweilige Replaygarantien benötigen Implementierungsnachweise. Ohne erfüllte Bedingungen wird eine Capability nicht produktiv freigegeben.

## ADRs und technische Quellen

[ADR-Index](../adr/README.md). Die Entscheidungen sind Orbiliq-Entwurfsentscheidungen, keine Herstellerzusagen. Plattformgrundlagen wurden am 27.10.2026 anhand primärer Dokumentation ergänzt:

- [Android: Offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first): lokale Lesequelle, asynchrone Writes und persistente Nachholarbeit.
- [SQLCipher Android](https://github.com/sqlcipher/sqlcipher-android): Room-Anbindung; [Migration zum aktuellen Artefakt](https://www.zetetic.net/sqlcipher/sqlcipher-for-android-migration/).
- Weitere Provider-/Android-Belege und ihre Recherchegrenzen stehen im [bestehenden Quellenregister](../research/07-sources.md). Zeitabhängige Service-/Target-SDK-Regeln vor Umsetzung erneut prüfen.

Implementierungsstand Arbeitspaket 6: [Lokale Persistenz und Sicherheitskonzept](local-storage.md),
mit ausdrücklich begrenzter erster SQLite-/AES-GCM-Umsetzung gemäß ADR 0012.

[Unified Contacts](unified-contacts.md) beschreibt das implementierte konservative
Matching, manuelle Korrekturen und persistente Ausschlüsse (ADR 0013).
- [Unified Inbox und Personen-Conversations](unified-communication.md)
