# Projektkontext: Orbiliq

Stand: 27. Oktober 2026.

## Produkt und verbindlicher Rahmen

Orbiliq ist als lokale, datenschutzorientierte Unified-Messenger-App für Android geplant. Kotlin, Jetpack Compose, Material 3 Expressive, Coroutines/Flow und eine modulare Providerarchitektur sind vorgegeben. Eigener Projektcode soll MIT-lizenziert sein. Veröffentlichung zunächst über GitHub Releases. Keine verpflichtenden Google Play Services, keine obligatorische Orbiliq-Cloud, keine Tracker oder Werbe-SDKs.

Ziel sind eine gemeinsame Chatliste, Kontakte/Identitäten und möglichst weitgehendes Lesen, Empfangen und Senden über vorhandene Messenger. Orbiliq ist kein eigenes Kommunikationsnetz. Funktionsgrenzen und Datenschutz haben Vorrang vor vollständigen Funktionsversprechen. Ein grundlegendes Expressive-Theme und ein erklärter Inbox-Leerzustand sind angelegt; die eigentlichen Produktansichten sind noch nicht gestaltet.

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

Auf dem persistenten Mock mit Sync Engine können jetzt Chat-UI, sichere Medienengine,
Recovery-UI und Android-Hintergrundscheduler ohne echte Konten aufgebaut werden. Für die erste reale Integration bleibt ein begrenzter Telegram-/TDLib-Machbarkeitsprototyp mit reproduzierbarem Android-Build und Google-freiem Login-/Sync-/Sessionnachweis empfohlen. Dieser Schritt ist **noch nicht beauftragt oder implementiert**. Weitere Companion-Prototypen erst nach den in der Recherche dokumentierten Freigabebedingungen verfolgen.

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
