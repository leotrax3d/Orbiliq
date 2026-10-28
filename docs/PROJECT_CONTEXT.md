# Projektkontext: Orbiliq

Stand: 28. Oktober 2026.

**Prompt-11-Stand `586a3c2`:** Signal-Direktchats unterstützen Textempfang/-versand,
Zitat-Antworten, eigene Reaktionen, Bildempfang und metadatenfreien Bildversand;
Gruppen sind nur lesbar. Hardware-nachgewiesen (Pixel 10a, echter Account): Linking,
Session nach Neustart, verweigerte/erteilte Nachrichtenfreigabe, Fremdbindung abgewiesen,
Text an „Notiz an mich“ genau einmal inkl. Wiederholung. Antworten, Reaktionen und
Bilder zusätzlich auf Hardware bestanden (lokal gebauter Companion `a52d81f`, 11,7 s):
Antwort mit Zitatbezug, eigene Reaktion nativ bestätigt, synthetisches Bild gesendet
und als echtes Signal-Bild wieder geladen – ausschließlich in „Notiz an mich“.
Noch offen: Empfang von fremden Geräten, Neustart/Offline/Widerruf/Doze-Nachweis,
Video/Audio/Dateien, Bearbeiten/Löschen. Gruppenversand ist implementiert
(`afae158`, Regeln des offiziellen Eingabefelds) und JVM-getestet; kein Gerätetest,
da Gruppennachrichten andere Personen erreichen (nur mit ausdrücklicher Freigabe).
Neun Gerätetests nach Installation dieses Builds bestanden (13,8 s).
Details: [Signal-Provider](architecture/signal-provider.md), [Übergabe](SIGNAL_HANDOFF.md).

Aktueller Entwicklungsnachtrag zu Prompt 11: Die [direkte Signal-Textstrecke](architecture/signal-provider.md)
ist im Code angelegt (separate kontogebundene Nachrichtenzustimmung, paginierte native
Quelle, generische Outbox/Sync und verschlüsselter Command-Claim ohne Blind-Resend).
Neue Messaging-Gerätetests stehen aus; die bisher installierte Companion-APK bietet
noch keinen Nachrichtenzugriff. Kein Abschluss oder Messaging-Abnahmenachweis.
Aktuellster lokaler Nachweis: 142 relevante JVM-Tests und neun Python-Generatortests
bestanden; App-/Test-APK und Lint (0 Fehler/11 Warnungen) erfolgreich. Native
Nachrichten-Builds bis `1d820af` erfolgreich, jüngere 2-KiB-/Timer-Korrekturen benötigen
einen neuen Companion-Lauf. Zur sicheren Fortsetzung dient der
[vollständige Agentenprompt](SIGNAL_NEXT_AGENT_PROMPT.md).

Nachtrag 28.10.2026: Fortsetzung von Prompt 11 auf Basis des Branches
`codex/signal-provider-tests`. Zusätzliche Linking-Sperren für wiederhergestellte
Registrierungszustände, Start-fresh und Rückwärtsnavigation; sieben Generator-Tests
gegen die feste Upstream-Revision und zehn Signal-Provider-JVM-Tests lokal bestanden.
Generierte Security-Dateien werden vor lokalem Build und in CI auf Konsistenz geprüft;
alte Quellbäume müssen neu erzeugt werden. Windows-Checkout verwendet lokal
`core.longpaths=true`. Recherche erneut geprüft: Signal Android/Molly unverändert,
libsignal/signal-cli fortgeschritten; Client-Kryptodependencies unverändert beibehalten.
Kein neuer APK-/Account-/Linkingnachweis. Signal bleibt Setup/Status ohne Messaging-
Capabilities; Prompt 11 und seine Definition of Done sind weiterhin offen.

Weiterer Nachtrag: Echtes Device Linking auf dem Pixel vom Nutzer durchgeführt und
über signaturgeschütztes Status-IPC als `Linked` bestätigt. Vier Hardwaretests
einschließlich opt-in Linked-Erwartung bestanden, auch nach Companion-Prozessneustart
(3,569 s; begrenzte Toleranz ausschließlich für anfängliches Unavailable). Nachweis
erfolgte mit korrigiertem Companion aus CI 40668223010. Keine Messaging-Capabilities
freigegeben. Sessionpersistenz ist damit für diesen Prozessneustart belegt; Sync,
Empfang und Versand in Orbiliq bleiben Implementierungsarbeit.

## Produkt und verbindlicher Rahmen

Orbiliq ist als lokale, datenschutzorientierte Unified-Messenger-App für Android geplant. Kotlin, Jetpack Compose, Material 3 Expressive, Coroutines/Flow und eine modulare Providerarchitektur sind vorgegeben. Eigener Projektcode soll MIT-lizenziert sein. Veröffentlichung zunächst über GitHub Releases. Keine verpflichtenden Google Play Services, keine obligatorische Orbiliq-Cloud, keine Tracker oder Werbe-SDKs.

Ziel sind eine gemeinsame Chatliste, Kontakte/Identitäten und möglichst weitgehendes Lesen, Empfangen und Senden über vorhandene Messenger. Orbiliq ist kein eigenes Kommunikationsnetz. Funktionsgrenzen und Datenschutz haben Vorrang vor vollständigen Funktionsversprechen. Die Smartphone-Oberfläche mit Expressive-Theme, Inbox, Conversation/Composer, Personen, Konten und Einstellungen ist mit dem persistenten Mock verbunden; Geräteabnahme steht aus.

## Tatsächlicher Repository-Stand

Vor dem ersten Arbeitspaket war das Repository leer. Vorhanden sind Recherche, Gesamtarchitektur/ADRs und jetzt eine Android Foundation mit Gradle Wrapper, Version Catalog, Kotlin-/Compose-Modulen, Hilt-Zusammenbau, Tests und CI-Konfiguration. **Keine produktiven Messengerprovider oder echten Konten. Lokale verschlüsselte Nachrichtenspeicherung und Mock-Synchronisation sind implementiert.** Der konkrete Build-/Testnachweis steht in [Buildvalidierung](build-validation.md). Die gewünschte MIT-Lizenz ist eine Projektvorgabe; durch diese Dokumentation wurde keine bestehende Fremdlizenz geändert und keine Gesamtpaket-Lizenzfreigabe erteilt.

## Ergebnisse des ersten Arbeitspakets

Vollständige [Machbarkeitsanalyse und Entscheidungsmatrix](research/README.md), Einzelanalysen für Signal, Telegram, WhatsApp und Threema, Architektur-/Hintergrundkonzept, Lizenz-/Risikodossier, Quellenregister sowie Revisions-/Dateinachweise unter `docs/research/`.

- Telegram: TDLib über Java/JNI als erste vollständige Integrationsbasis auswählen. Cloud-Chats sind nicht E2EE, Secret Chats gerätegebunden; Channels haben API-/Sponsored-Messages-Pflichten.
- Signal: bedingter separater Android-Companion auf vorhandener Clientbasis; Lizenz-/Distributionsgrenze und Linked-Device-Betrieb erst nachweisen. signal-cli ist kein direktes Android-SDK.
- WhatsApp: vollständiger persönlicher Zugang bleibt experimentell. whatsmeow bevorzugter technischer Prototypkandidat, Baileys Alternative; beide enthalten konkret geprüfte GPL-Kryptoabhängigkeiten trotz MPL-/MIT-Hauptlizenz. Cloud API und DMA sind andere Produktmodelle.
- Threema: zunächst lokale Ergänzung mit Original-App. Neue Desktop-Beta unterstützt Linking von Android aus, ist aber kein freigegebenes Android-Drittclient-SDK. Clientcode und libthreema AGPL; Gateway ist eine separate Identität.

[ADR 0001](adr/0001-messenger-integration-strategy.md) hält die Entscheidung fest. Alle künftigen Provider benötigen unterschiedliche Capabilities, sichtbare Sync-/Authzustände und eine lokale, sichere Datenhaltung. Eine gemeinsame Timeline erhält Providerherkunft und E2EE-Unterschiede; Kontaktzuordnungen sind korrigierbar.

## Ergebnisse des zweiten Arbeitspakets

Die [technische Gesamtarchitektur](architecture/README.md) definiert Modulgraph, Domain/Data/Provider/UI, persistente Sync Engine, Room/SQLCipher, geschützten Medienstore, Notifications, Background Processing, Security Boundary sowie Hilt, Coroutines/Flow und Fehlerbehandlung. Der [Providervertrag](architecture/provider-contract.md) beschreibt versionierte Commands/Events und kontextabhängige Operationsfähigkeiten einschließlich aller geplanten Sonderfunktionen. Gemeinsame Geschäftslogik entscheidet anhand von Capabilities, nicht anhand des Messengernamens.

[ADR 0002–0008](adr/README.md) dokumentieren die angenommenen Zielentscheidungen. Wichtig: unbekannte Versandwirkung wird abgeglichen statt blind erneut gesendet; Previewadapter erhalten keine Vollproviderrechte; App-Lock und strenger Schlüssel-Lock haben verschiedene Hintergrundgrenzen. Die damaligen Modul-/API-Namen waren Entwurf; die inzwischen realisierten Teilmodule sind in der Foundation-Dokumentation aufgeführt. Dokumentationsverweise und Abdeckung wurden geprüft; in diesem damaligen Arbeitspaket keine Build-, Geräte- oder Kontotests ausgeführt.

## Ergebnisse des dritten Arbeitspakets

[Android Foundation](architecture/foundation.md): `app`, `core:model`, `core:domain`,
`core:provider-api`, `core:data`, `core:designsystem`, `feature:inbox`.
Eine echte Activity zeigt einen erklärten Leerzustand, Hilt liefert das noch unkonfigurierte
Repository. StateFlow/ViewModel, lifecyclebewusste Compose-Beobachtung, Expressive Theme,
Dynamic Color und Edge-to-edge bilden die UI-Grundlage. Keine Fakechats oder Login-Attrappen.
MIT, README, CONTRIBUTING, SECURITY, Gitignore, Dependency-Management und GitHub-CI sind angelegt.
Weitere Zielmodule werden erst mit realen Implementierungen ergänzt. Der Build aus einem
frischen Checkout ohne Build-Output-Cache ist erfolgreich; Debug-APK, Test-APK,
vier JVM-Tests und Lint (0 Fehler, 7 nicht blockierende Hinweise) sind nachgewiesen.
Start-/UI-Gerätetests sind nicht ausgeführt: kein Gerät angeschlossen, Emulator auf
Nutzerwunsch auf diesem Laptop ausgeschlossen. Das private GitHub-Repository ist
`leotrax3d/Orbiliq`; Actions bleibt wegen fehlender CI-Minuten deaktiviert.
[Prüfprotokoll](build-validation.md) hält Befehle, APK-Hash und Grenzen fest.

## Ergebnisse des vierten Arbeitspakets

[Domain Model und Provider API](architecture/domain-model.md) implementieren getrennte,
typisierte IDs und Entitäten für Personen, Messengeridentitäten, Accounts, Conversations,
Messages, Attachments und Groups. ProviderId/OperationKey bleiben offen. Versionierte opaque
Providerdaten und Unsupported-Nachrichten erhalten unbekannte Inhalte. Account-/Conversation-
Referenzen verhindern IDkollisionen; der Mappingport mit atomarer In-Memory-Referenz hält
lokale IDs bei wechselnden Remotealiasen stabil. Dauerhafte DB-Mappings folgen separat.

Capabilities besitzen vollständige Scopeketten, Revisionen, Gültigkeit, Constraints,
Reife und Qualität. Unbekannte/veraltete Unterstützung sperrt. Factory/Session, Registry,
Commands/Events, Replaycursor, Auth-, Medien- und Ergebnisverträge sind Kotlin/JVM ohne
Messengerabhängigkeiten. Ein optional einzubindender Sessionguard prüft vor Dispatch erneut.
Der PeopleRepository-Port beschreibt reversible lokale Kontaktzuordnungen, keine fertige
Merge-Persistenz. [ADR 0010](adr/0010-domain-identities-and-provider-api.md) hält Entscheidungen fest.
Tests und Buildnachweis stehen im [Prüfprotokoll](build-validation.md).
Keine konkrete Integration, Outbox, DB, Netzwerkverbindung oder neue Produkt-UI hinzugefügt.

## Ergebnisse des fünften Arbeitspakets

Der [lokale Mock-Messenger](architecture/mock-provider.md) implementiert im eigenen
Kotlin/JVM-Modul `core:provider-mock` Factory, Session, Medienport und Inbox-Repository.
Ohne Account oder Netzwerk: synthetischer Code-Login, Kontakte, Direktchat und Gruppe,
Text/Medien, Replies, Reaktionen, Edit/Delete, Typing und Receipts. Steuerbare Zeit,
verzögerter Versand/Eingang, Offline/Reconnect, unterschiedliche Capabilityprofile,
Netzwerk-/Rate-Limit-Fehler, unbekannte Versandwirkung und Revisions-/Sync-Konflikte.
Sieben wiederverwendbare Szenarien und automatisierte Regressionstests sind enthalten.
Die Provider-API wurde um typisiertes Typing und `SyncConflict` ergänzt.

`-Porbiliq.mock=true` speist in Debug-Builds die vorhandene Inbox mit sichtbar markierten
Testchats. Release hat keine Mock-Abhängigkeit. Neue vollständige Chat-/Login-Screens
sind nicht Bestandteil dieses Pakets. Das Testbackend lebt ausschließlich im Speicher:
kein dauerhafter Medienstore, keine persistente Outbox, keine Wiederherstellung über
Prozessneustarts. Replay wird entsprechend als BestEffort deklariert. Szenarienmedien
sind synthetische Nutzdaten, keine Codec-Testdateien. [ADR 0011](adr/0011-local-mock-messenger.md)
hält die Grenze fest; Test-/Buildnachweise stehen im [Prüfprotokoll](build-validation.md).

## Ergebnisse des sechsten Arbeitspakets

[Lokale Persistenz und Sicherheitskonzept](architecture/local-storage.md): `core:data` speichert
Accounts, Personen/Identitäten/Links, Gruppen, Conversations, Messages, Attachment-Metadaten,
Providerdaten, Reaktionen, Sync-Cursor und Pending Operations. Transaktionale Copy-on-write-
Projektion, Flow erst nach Commit, expliziter Codec mit Migrationen v1–v3. Unterbrochener
Dispatch wird als OutcomeUnknown wiederhergestellt. Kontaktmerge/-unlink, persistente
Aufbewahrung und lokale Archive sind über Repositoryports verfügbar.

Android-SQLite enthält ausschließlich AES-GCM-Ciphertext; Keystore hält den nicht
exportierbaren Schlüssel. Ein atomarer verschlüsselter Vault nimmt Credentials/Sessions auf;
keine Secrets in Preferences/Logs, privater No-Backup-Speicher und bestehende Backupausschlüsse.
[ADR 0012](adr/0012-first-local-persistence.md) dokumentiert die erste begrenzte Umsetzung:
noch kein Room/SQLCipher, komplette Projektion pro Commit, 64-MiB-Limit. Normalisierte
Tabellen, FTS und zusätzliche persistente Remotealias-Mappings bleiben ein Ausbau.

Der Debug-Mock verwendet nun einen persistenten App-Scope-Wrapper, rekonstruiert die lokale
Projektion nach Wiederöffnung und persistiert neue Ereignisse mit Cursor. UUID-Command-IDs
und rekonstruierte Nachrichtenzähler verhindern Mock-Neustartkollisionen. Der isolierte
Mock bleibt für Szenarien in-memory. Medienbytes, geplante virtuelle Ereignisse und die
fiktive Remote-Idempotenzhistorie bleiben flüchtig. Bytecache, portable Archive, Keyrotation,
Recovery-/Settings-UI, periodischer Cleanup sowie allgemeine Retry-/Reconciliation-Worker
sind beschrieben, aber noch nicht implementiert. Release bindet keinen Mock ein.
JVM-Nachweise und Android-Buildnachweise stehen im [Prüfprotokoll](build-validation.md);
tatsächlicher Keystore-/Geräte-Prozessneustart bleibt ohne Hardware ungeprüft.

## Bekannte Grenzen und nächste Arbeitspakete

Technische Funktionsbewertungen sind aus Dokumentation/Code abgeleitet, nicht praktisch bestanden. Vollständige transitive Lizenzprüfung, Rechtsbewertung der konkreten Distribution, Geräte-/Account-/Hintergrund-/Medientests und Sicherheitsreview stehen aus. Android garantiert keinen sofortigen Empfang in jedem Zustand. Notification-Adapter liefern Ausschnitte und bedingte Antworten, keine vollständigen Chats.

Die Chat-UI arbeitet jetzt auf dem persistenten Mock mit Sync Engine. Sichere Medienengine,
Recovery-UI und Android-Hintergrundscheduler können darauf ohne echte Konten aufgebaut werden. Für die erste reale Integration bleibt ein begrenzter Telegram-/TDLib-Machbarkeitsprototyp mit reproduzierbarem Android-Build und Google-freiem Login-/Sync-/Sessionnachweis empfohlen. Dieser Schritt ist **noch nicht beauftragt oder implementiert**. Weitere Companion-Prototypen erst nach den in der Recherche dokumentierten Freigabebedingungen verfolgen.

Bei späteren Arbeiten zuerst Repository und aktuelle Anweisungen lesen; Recherchequellen bei zeitabhängigen Aussagen erneut prüfen. Projektkontext beschreibt den Stand und ersetzt keine Prüfung tatsächlicher Dateien.

## Ergebnisse des siebten Arbeitspakets

Die [Sync Engine](architecture/sync-engine.md) verarbeitet Replay und persistente Outbox
providerunabhängig im App-Scope des Debug-Mocks. Optimistische Nachrichten und Aufträge
committen atomar; bestätigte Remote-IDs erhalten lokale IDs. Retry/Backoff, persistierte
Fehlerkategorien, Ablauf, Dependencies und Reconciliation sind implementiert (Codec v4).
Unklare Effekte nach Crash werden nie blind erneut gesendet. Fehlende Mock-Remotehistorie
bleibt explizit unklar. Android-Hintergrundscheduler, Geräte-Prozess-/Netzwerktests und
Recovery-UI bleiben offen; JVM-Nachweise siehe Buildvalidierung.

## Ergebnisse des achten Arbeitspakets

[Unified Contacts](architecture/unified-contacts.md) ergänzt konservatives lokales Matching
über bestätigte internationale Telefonnummern, eigene Personen ohne belastbare Evidenz,
Konfliktbeobachtung sowie manuelles Merge, Split, Unlink und Compare-and-set-Korrekturen.
Manuelle Ausschlüsse überleben Replay und Wiederöffnung. Geänderte automatische Evidenz
wird neu bewertet; Nachrichten-/Providerreferenzen bleiben erhalten. Codec v5 migriert ältere
Snapshots ohne erfundene Evidenz. Tests schützen gegen Namensgleichheit, unsichere Nummern,
Providerkollisionen, Brücken zwischen getrennten Personen und verlorene manuelle Korrekturen.
Kontaktverwaltungs-UI und reale Providerbelege sind noch nicht implementiert.

## Ergebnisse des neunten Arbeitspakets

[Unified Communication](architecture/unified-communication.md) ergänzt einen Domainport und
einen providerunabhängigen Use-Case-Service für eine chronologische Inbox aller Accounts,
optionale Personen-Timelines aus verknüpften Direktchats und expliziten Textversand über
einen gewählten Provider-/Account-/Conversationweg. Nachrichtenherkunft und Sicherheitsdaten
bleiben unverändert. Capabilities gelten pro Route; Cross-Route-Antworten und veraltete
Personenzuordnungen werden abgewiesen. Versand nutzt die bestehende persistente Outbox und
Sync Engine. Mehrere Mockprovider sind mit eigenen Provider-IDs gleichzeitig simulierbar.
Details und Grenzen stehen im Architekturtext und in ADR 0014; UI und reale Provider folgen
separat. Keine Codecänderung und keine neue externe Abhängigkeit erforderlich.


## Ergebnisse des zehnten Arbeitspakets

Die [Messenger-Oberfläche](architecture/messenger-ui.md) verbindet `feature:inbox` über den
Domainport `MessengerRepository` mit dem persistenten Mock und Unified Communication.
Smartphone-Navigation mit Chats, Menschen und Konten; Conversation/Composer mit Replies,
Versandstatus, Herkunft und dauerhaftem Outboxversand; Personen-/Identitätsansicht,
Kontodetails und persistierte Hell/Dunkel/System-/Dynamic-Color-Einstellungen. Filter für
Direkt/Gruppe/Archiv, lokale persistente Archivierung und Suche in Titel/letzter Nachricht.

Material 3 Expressive 1.5.0-beta02 anhand offizieller Quellen bestätigt. Expressive Theme,
Emphasized-Typografie, springbasierte Filter-Formwechsel und Seitentransitionen, animierte
Listen, Medium Extended FAB, Sheets/Dialoge, Skeleton-/Loading-/Empty-/Fehlerzustände und
48-dp-Touch-Targets. SavedState erhält Entwürfe über Navigation/Activity-Neuanlage; keine
dauerhafte Draftdatenbank nach Taskentfernung. Edge-to-edge, Systemleisten und IME berücksichtigt.

Kein Emulator gestartet. Hardwareabnahme für Darstellung, Animation, Tastatur, TalkBack
und große Schrift bleibt erforderlich. Medien sind erklärte Platzhalter; neue Remotechats,
Kontakt-Merge-UI, reale Provideranmeldung und weitergehende Nachrichtenaktionen bleiben offen.
Build-/Testnachweise siehe [Buildvalidierung](build-validation.md).

## Prompt 11: Signal – unvollständig

Die [erneute Signal-Prüfung](research/08-signal-revalidation.md) bestätigt die bisherigen
Signal-Android-/Molly-/libsignal-Revisionen und beschreibt konkrete Provisioning-,
Sekundärgeräte- und Versandpfade. Der Linking-UI-Schalter ist im geprüften
Signal-Android-Build standardmäßig aus und für Debug an; die offizielle Gerätehilfe allein
ist kein Release-Buildnachweis.

In der anschließenden Fortsetzung wurde der [Signal-Einrichtungsmilestone](../integrations/signal-companion/README.md)
implementiert: Generator eines separaten Clientquellbaums mit eigener Application-ID,
vorhandenem Linked-Device-Flow, lokaler Zustimmungsoberfläche und abgesicherter Status-IPC.
`core:provider-signal` enthält den MIT-Vertrag, Signaturpolicy und eine auf Einrichtung
begrenzte Providerklasse. Orbiliq prüft die aktuelle Signatur, Paket/UID und Komponenten;
der Companion prüft pro Aufruf Android-Permission, UID/Paket, aktuelle Signatur und lokale
Zustimmung. Eine No-Backup-Datei bindet diese ausschließlich für Statusabfragen geltende
Zustimmung an das Zertifikat. Beide Prototyp-APKs benötigen denselben aktuellen Signer.
Normale Upstream-Logsinks werden für den Companion deaktiviert und Crashdetails redigiert.
Eigene Ergänzungen sind MIT; der modifizierte Signal-Client behält seine AGPL-Pflichten.

Unter Konten ist die separate Einrichtung auch ohne Mock erreichbar. Es werden keine
QR-Daten oder Protokollschlüssel an Orbiliq übertragen. Lokale Credentials werden nicht
als Online-/Sync-Nachweis dargestellt. Kein echter Signal-Account wird in Inbox/Outbox
eingetragen und keine positive Messaging-Capability ergänzt; Aufträge werden abgewiesen.
Die zehn neuen JVM-Tests bestehen. Am 28.10. wurde ein physisches Pixel 10a mit API 37
für fünf erfolgreiche UI-Tests einschließlich Signal-Setup und drei erfolgreiche
Storage-/Keystore-Tests genutzt. Espresso wurde dafür explizit auf 3.7.0 gepinnt;
die transitiv verwendete 3.5.0 scheiterte an einer auf API 37 entfernten internen API.
Ältere UI-Tests berücksichtigen nun bestehende archivierte Mock-Chats ohne Datenreset.
Android-IPC-Integrationstests benötigen den separat installierten Companion und sind
noch nicht bestanden. Beim Companion reichten 1536 MiB Heap nicht für den Ressourcenmerge;
der ressourcenbegrenzte Launcher nutzt nach gemessenem GC-Stau mit 2304 MiB jetzt
3584 MiB mit G1-GC, Configuration Cache und auf Nutzerwunsch zwei CPU-Kerne mit Nice 15.
Auch 3072 MiB scheiterten mit bestätigtem Java-Heapfehler im Kotlin-Compiler. Für den
Debug-Companion wird die optionale Kotlin-JVM-Bytecodeoptimierung deaktiviert; ein
lokaler erfolgreicher Gesamtbuild ist damit noch nicht nachgewiesen. Der separate
GitHub-CI-Build an `a4fdc94` ist inzwischen in 23m 55s erfolgreich abgeschlossen
(652 Aufgaben); APK-/Quellartefakt wird für lokale Signatur- und Hardwareprüfungen genutzt. Android-Accounttyp und
Kontakt-MIME-Typen sind gegenüber der originalen Signal-App isoliert.
Keine parallelen großen Builds auf dem lokalen 8-GB-Laptop. Nach ausdrücklicher Freigabe
für das öffentliche Repository läuft der Companion-Build wieder auf GitHub CI; der
lokale Build wurde dafür beendet. CI erhält keine lokalen Schlüssel oder Accountdaten.
Konkrete Buildnachweise
und Grenzen stehen im [Prüfprotokoll](build-validation.md); [ADR 0015](adr/0015-signal-companion-linking-milestone.md)
hält die Entscheidung fest.

Die Definition of Done von Prompt 11 ist weiterhin **nicht erfüllt**. Offen sind echte
Account-/QR-/Session-/Entkopplungsnachweise, das Ein-Gerät-QR-Problem, kontogebundene
Nachrichtenautorisierung, dauerhaftes Ereignisjournal mit Commit/Ack, Empfang/Synchronisation,
Textversand samt Reconciliation und erst danach Medien/Gruppen/Replies/Reaktionen.
Einrichtungen und Testdoubles allein erfüllen keine Messagingintegration.

Nachtrag 28.10.: Der erste Companion ist gebaut, lokal mit Orbiliqs Signer signiert
und auf dem Pixel installiert. Drei IPC-Tests sowie Ablehnung der ADB-Shell bestanden.
ConsentRequired → NeedsLink nach Nutzerzustimmung ist real geprüft. Beim weiteren
Bedienen wurde jedoch der Telefonnummernweg genutzt; Status danach PrimaryDeviceNotSupported.
Kein erfolgreiches Linking. Der erste Companion ist ohne Datenlöschung deaktiviert;
Original-Signal-Accountstatus noch ungeklärt. Der Generator sperrt nun zusätzlich
Primärregistrierung an UI und beiden HTTP-APIs. Neue Boundarytests und Neubau laufen
in CI 40668223010 (Commit fd5dce6). Vor weiteren Accounttests Ergebnis und ursprünglichen
Accountstatus prüfen. Nicht blind löschen, unregister auslösen oder Primärregistrierung
fortsetzen. Vollständige Orbiliq-CI 40666966910 an 5347ce8 ist grün; lokale noch
uncommittete UI-Änderungen sind nicht Teil dieses CI-Nachweises.

Der Nutzer hat anschließend bestätigt, dass die ursprüngliche Signal-App weiterhin
normal angemeldet ist. Er möchte den Companion-Test von vorne beginnen. Die alte
Companion-Installation bleibt bis zum korrigierten APK deaktiviert; kein serverseitiges
Unregister und keine Änderung am ursprünglichen Signal-Paket.
