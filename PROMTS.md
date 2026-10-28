# Orbiliq – sequenzielle Entwicklungs-Prompts

## Globale Regel für alle Prompts ab Prompt 2

Bevor du Änderungen vornimmst:

1. Untersuche den vollständigen aktuellen Repository-Stand.
2. Lies relevante Dokumentation, ADRs und vorhandene Tests.
3. Verstehe bestehende Architekturentscheidungen.
4. Prüfe, welche Teile der aktuellen Aufgabe bereits implementiert wurden.
5. Verwende vorhandene Komponenten weiter, statt parallele Implementierungen anzulegen.
6. Führe bestehende Tests aus, bevor größere Änderungen vorgenommen werden.
7. Passe deinen Plan an den tatsächlichen Repository-Stand an.

Gehe nicht davon aus, dass vorherige Arbeitspakete exakt nach ursprünglichem Plan umgesetzt wurden.

Bei normalen Implementierungsentscheidungen darfst du selbstständig entscheiden.

Frage mich jedoch vor Entscheidungen mit erheblichen Auswirkungen auf Sicherheit, Datenschutz, Account-Sperrungsrisiko, Lizenzierung, grundlegende Architektur oder potenziellen Datenverlust, sofern keine eindeutig sinnvolle Lösung existiert.

Dokumentiere wichtige Entscheidungen und hinterlasse das Repository nach jedem Arbeitspaket in einem konsistenten, testbaren Zustand.

---

# Prompt 1 – Messenger-Machbarkeitsanalyse

Wir entwickeln **Orbiliq**, eine lokale, datenschutzorientierte Unified-Messenger-App für Android mit Kotlin und Jetpack Compose.

Orbiliq soll zunächst Signal, Telegram, WhatsApp und – sofern realistisch – Threema integrieren. Ziel ist, Nachrichten möglichst vollständig innerhalb Orbiliqs lesen, empfangen und senden zu können.

Implementiere noch keine App.

Führe stattdessen eine gründliche technische Recherche für jeden Messenger durch.

Untersuche insbesondere:

- offizielle APIs
- Protokolle
- bestehende FOSS-Clients
- geeignete Libraries
- alternative Clients
- Authentifizierung
- Geräteverknüpfung
- Session-Verwaltung
- Verschlüsselung
- Synchronisation
- Multi-Device
- Hintergrundbetrieb
- Betrieb ohne Google Play Services
- Nachrichten senden/empfangen
- Medien
- Gruppen
- Reaktionen
- Antworten
- Bearbeiten/Löschen
- Kontakte
- Status/Stories/Channels
- Calls
- Lizenzkompatibilität mit MIT
- Account-/Ban-Risiko
- Reverse-Engineering-Risiken
- Wartungsaufwand bei Protokolländerungen

Analysiere vorhandene Open-Source-Projekte konkret, statt unnötig Protokolle neu zu implementieren.

Erstelle anschließend eine Entscheidungsmatrix und empfehle für jeden Messenger die beste Integrationsstrategie. Mehrere Strategien pro Messenger sind zulässig.

Klassifiziere Features jeweils beispielsweise als:

- zuverlässig realisierbar
- realisierbar mit Einschränkungen
- experimentell
- nicht sinnvoll realisierbar

Erstelle die Ergebnisse unter `docs/research/`.

**Definition of Done:** Für jeden der vier Messenger existiert eine belastbare technische Integrationsentscheidung, einschließlich Quellen, Risiken, Lizenzsituation und empfohlener Architektur. Noch kein produktiver Messenger-Code.

---

# Prompt 2 – Gesamtarchitektur und ADRs

Analysiere zuerst Repository, Rechercheergebnisse und vorhandene Dokumentation.

Entwirf darauf aufbauend die technische Gesamtarchitektur von Orbiliq.

Ziele:

- Android-only
- Kotlin
- Jetpack Compose
- local-first
- kein notwendiger Orbiliq-Cloud-Service
- kein Google-Play-Services-Zwang
- modular
- testbar
- Provider leicht ergänzbar

Definiere insbesondere:

- Modulstruktur
- Domain Layer
- Data Layer
- Provider Layer
- UI Layer
- Sync Engine
- lokale Persistenz
- Media Storage
- Notification Layer
- Background Processing
- Security Boundary
- Dependency Injection
- Coroutines/Flow-Konzept
- Fehlerbehandlung

Entwirf eine Capability-basierte Provider-Architektur.

Die Kern-App darf nicht mit überall verteilten Konstrukten wie `if provider == WhatsApp` arbeiten.

Ein Provider muss deklarieren können, welche Funktionen er unterstützt.

Berücksichtige dabei Text, Medien, Reaktionen, Antworten, Edit/Delete, Gruppen, Presence, Typing, Read Receipts, Suche, Status/Stories/Channels und Calls, ohne vorauszusetzen, dass jeder Provider alles unterstützt.

Dokumentiere grundlegende Entscheidungen als ADRs.

**Definition of Done:** Eine nachvollziehbare Architektur existiert, die neue Messenger aufnehmen kann, ohne den Orbiliq-Kern grundlegend umzubauen.

---

# Prompt 3 – Repository und Android Foundation

Analysiere zuerst den aktuellen Stand und die Architekturentscheidungen.

Erstelle jetzt das eigentliche Android-Projekt.

Verwende:

- Kotlin
- Jetpack Compose
- aktuelle stabile Android-Werkzeuge
- sinnvolle modulare Gradle-Struktur
- Version Catalog
- reproduzierbare Builds

Richte die zuvor beschlossene Architektur als Projektstruktur ein.

Zusätzlich:

- MIT LICENSE
- README
- CONTRIBUTING
- SECURITY
- Architektur-Dokumentation
- `.gitignore`
- grundlegende CI
- Debug-Build
- Unit-Test-Infrastruktur
- Instrumentation/UI-Test-Grundlage
- Dependency Management

Vermeide unnötige Enterprise-Komplexität.

Die App muss nach diesem Schritt kompilieren und auf einem Android-Gerät bzw. Emulator starten.

**Definition of Done:** Clean Checkout → dokumentierter Build → funktionierende Debug-APK → CI erfolgreich.

---

# Prompt 4 – Domain Model und Provider API

Implementiere das zentrale Domain Model und die Provider-Abstraktion.

Orbiliq muss klar unterscheiden zwischen:

- Person
- Messenger Identity
- Account
- Conversation
- Message
- Attachment
- Group
- Provider

Entwirf außerdem ein erweiterbares Modell für unterschiedliche Message Types.

Implementiere die Provider-Schnittstellen entsprechend der bisherigen Architektur.

Capabilities müssen explizit modelliert sein.

Provider-spezifische Daten dürfen erhalten bleiben, ohne das gemeinsame Domain Model mit Messenger-Sonderfällen zu verschmutzen.

Berücksichtige außerdem stabile IDs und Mapping zwischen Provider-IDs und Orbiliq-IDs.

Schreibe umfangreiche Unit Tests.

**Definition of Done:** Der Orbiliq-Core kann theoretisch beliebig viele Provider darstellen, ohne einen konkreten Messenger zu kennen.

---

# Prompt 5 – Mock Provider und Testsystem

Implementiere einen vollständigen lokalen Mock-/Test-Messenger.

Dieser soll realistische Szenarien simulieren:

- Login
- Chats
- Einzelkontakte
- Gruppen
- eingehende Nachrichten
- ausgehende Nachrichten
- Medien
- Antworten
- Reaktionen
- Edit/Delete
- Typing
- Read Receipts
- unterschiedliche Capabilities
- Netzwerkfehler
- Offline-Zustände
- verzögerte Nachrichten
- Sync-Konflikte

Der Mock Provider soll später für UI-, Integrations- und Regressionstests verwendet werden.

Er darf keine Netzwerkverbindung benötigen.

**Definition of Done:** Große Teile von Orbiliq können entwickelt und getestet werden, ohne einen echten Messenger-Account zu verwenden.

---

# Prompt 6 – Datenbank und sichere lokale Speicherung

Implementiere die lokale Persistenzschicht.

Sie muss das Domain Model sinnvoll speichern können und insbesondere unterstützen:

- Accounts
- Personen
- Messenger-Identitäten
- Conversations
- Messages
- Attachments
- Provider-Metadaten
- Sync-Zustände
- Pending Operations

Berücksichtige Migrationen von Anfang an.

Entwickle außerdem ein Konzept für:

- sensible Daten
- Credentials
- Sessions
- Schlüssel
- Medien-Cache
- konfigurierbare Aufbewahrungszeiten
- lokale Archive

Verwende Android-Sicherheitsmechanismen sinnvoll.

Secrets dürfen weder in Logs noch ungeschützt in normalen Preferences landen.

Implementiere Repository-Abstraktionen und Tests.

**Definition of Done:** Der Mock Provider kann Daten persistent mit Orbiliqs Datenmodell synchronisieren und ein Neustart verliert keine vorgesehenen Daten.

---

# Prompt 7 – Sync Engine und Offline Queue

Implementiere eine providerunabhängige Synchronisations-Engine.

Sie soll unterstützen:

- initialen Sync
- inkrementellen Sync
- eingehende Änderungen
- ausgehende Operationen
- Offline Queue
- Retry
- Backoff
- Fehlerzustände
- Konflikte
- Netzwerkwechsel
- App-Neustarts

Beispiel:

Nachricht schreiben → lokal sichtbar → Queue → Provider verfügbar → senden → Server-/Provider-ID übernehmen.

Fehler dürfen niemals still verloren gehen.

Vermeide doppelte Nachrichten und doppelte Sendungen soweit technisch möglich.

Teste insbesondere Prozessneustarts und Verbindungsabbrüche.

**Definition of Done:** Mit dem Mock Provider funktioniert zuverlässiges Senden und Empfangen auch bei simuliert instabiler Verbindung.

---

# Prompt 8 – Unified Contacts

Implementiere Orbiliqs Personen- und Identitätsmodell vollständig.

Eine Person kann mehrere Messenger-Identitäten besitzen.

Beispiel:

Anna  
→ Signal  
→ WhatsApp  
→ Telegram

Entwickle vorsichtiges automatisches Matching anhand zuverlässiger Informationen wie normalisierten Telefonnummern.

Unsichere Zuordnungen dürfen nicht einfach automatisch zusammengeführt werden.

Implementiere:

- automatisches Matching
- manuelles Zusammenführen
- Trennen
- Korrektur falscher Zuordnungen
- Konfliktbehandlung

Schreibe Tests gegen versehentliche Zusammenführungen.

**Definition of Done:** Mehrere Messenger-Identitäten können zuverlässig einer gemeinsamen Orbiliq-Person zugeordnet werden.

---

# Prompt 9 – Unified Inbox und Unified Conversations

Implementiere die Domain-/Use-Case-Schicht für die gemeinsame Kommunikation.

Die Inbox soll Conversations aller Provider gemeinsam chronologisch darstellen können.

Zusätzlich sollen Nachrichten mehrerer Messenger derselben Person optional in einer gemeinsamen Timeline erscheinen.

Jede Nachricht muss ihre Herkunft behalten.

Beim Senden muss bestimmt werden können, welcher Provider verwendet wird.

Capability-Unterschiede müssen berücksichtigt werden.

Implementiere zunächst gegen den Mock Provider.

**Definition of Done:** Orbiliqs Kern kann mehrere simulierte Messenger gleichzeitig zu einer gemeinsamen Inbox und gemeinsamen Personen-Conversations zusammenführen.

---

# Prompt 10 – Material 3 Expressive UI Foundation

Entwickle jetzt die eigentliche Orbiliq-Oberfläche.

Nutze konsequent aktuelles **Material 3 Expressive** mit Jetpack Compose.

Orientiere dich an aktuellen offiziellen Android-/Material-Empfehlungen und prüfe deren aktuellen Stand.

Berücksichtige insbesondere:

- expressive Shapes
- Shape Morphing
- Spring Motion
- hochwertige Übergänge
- moderne Typografie
- Edge-to-edge
- Dynamic Color
- Light/Dark Mode
- große Touch Targets
- expressive FABs
- moderne Sheets/Dialoge
- Loading/Skeleton States
- Empty States

Entwickle eine sinnvolle Smartphone-Navigation, statt die Navigation künstlich an eine vorher festgelegte Struktur anzupassen.

Implementiere mindestens:

- Unified Inbox
- Conversation View
- Composer
- Contact/Person View
- Account-/Provider-Bereich
- grundlegende Settings
- Filter/Ordner

Nutze zunächst den Mock Provider.

Animationen sollen sichtbar expressive, aber schnell und funktional sein.

**Definition of Done:** Orbiliq ist mit dem Mock Provider bereits als vollständige Messenger-Oberfläche bedienbar.

---

# Prompt 11 – Signal Provider

Analysiere zuerst insbesondere die aktuelle Messenger-Recherche erneut und prüfe, ob sich technische oder Upstream-Gegebenheiten verändert haben.

Implementiere anschließend Signal anhand der zuvor ausgewählten Integrationsstrategie.

Priorität:

1. sichere Authentifizierung/Device Linking
2. Session
3. Sync
4. Empfangen
5. Text senden
6. Medien
7. Gruppen
8. Antworten/Reaktionen
9. weitere zuverlässig unterstützte Funktionen

Erfinde keine scheinbar funktionierenden Features.

Expose alle tatsächlich verfügbaren Funktionen über Provider Capabilities.

Achte besonders auf Signal-Protokoll, Schlüsselmaterial und sensible Logs.

Ergänze Integrationstests soweit ohne reale Credentials möglich.

**Definition of Done:** Ein echter Signal-Account kann über die gewählte Strategie zuverlässig die vorgesehenen Kernfunktionen in Orbiliq verwenden.

---

# Prompt 12 – Telegram Provider

Implementiere Telegram anhand der recherchierten und aktuell überprüften Integrationsstrategie.

Nutze möglichst robuste bestehende APIs/Libraries statt unnötiger Eigenimplementierungen.

Unterstütze schrittweise:

- Auth
- Session
- Chat-Sync
- Text
- Medien
- Gruppen
- Replies
- Reactions
- Edit/Delete
- Channels und weitere sinnvoll unterstützte Telegram-Funktionen

Mappe Telegram-spezifische Funktionen sauber auf das Orbiliq-Modell.

Was nicht sinnvoll vereinheitlicht werden kann, darf als Provider Extension bestehen.

**Definition of Done:** Telegram funktioniert als echter zweiter Provider parallel zum bestehenden Provider und beweist damit praktisch die Provider-Architektur.

---

# Prompt 13 – WhatsApp Provider

Überprüfe zuerst erneut die aktuelle technische und rechtliche Situation.

WhatsApp ist besonders hinsichtlich inoffizieller Clients, Protokolländerungen und Account-Sperrungen kritisch.

Treffe keine riskante neue Architekturentscheidung ohne Rückfrage.

Implementiere anschließend die zuvor beschlossene Strategie.

Priorisiere:

- Auth/Device Linking
- Session
- Synchronisation
- Empfangen
- Senden
- Medien
- Gruppen
- Replies/Reactions
- Edit/Delete, sofern zuverlässig möglich

Isoliere besonders wartungsintensive oder reverse-engineerte Komponenten sauber vom Orbiliq-Core.

Dokumentiere bekannte Risiken transparent.

**Definition of Done:** Die vereinbarten WhatsApp-Kernfunktionen arbeiten innerhalb Orbiliqs oder ein klar dokumentierter technischer Blocker wurde nachgewiesen.

---

# Prompt 14 – Threema Provider / Go-No-Go

Überprüfe die Recherche zu Threema anhand des inzwischen vorhandenen Orbiliq-Systems erneut.

Bewerte, ob eine echte Integration technisch, rechtlich und hinsichtlich Wartbarkeit sinnvoll ist.

Wenn ja, implementiere den Provider schrittweise analog zu den anderen Providern.

Wenn nein:

- keine Fake-Integration bauen,
- Gründe dokumentieren,
- mögliche Fallbacks untersuchen,
- Architektur für eine spätere Integration offenhalten.

**Definition of Done:** Entweder existiert eine belastbare Threema-Integration oder eine nachvollziehbare dokumentierte Go-/No-Go-Entscheidung.

---

# Prompt 15 – Notifications und zuverlässiger Background Sync

Implementiere die zentrale Android-Notification- und Background-Schicht.

Orbiliq soll Benachrichtigungen verschiedener Messenger zentral verwalten.

Unterstütze:

- Notification Channels
- Conversations
- Gruppierung
- Direktantwort
- Lockscreen Privacy
- mehrere Accounts/Provider
- Deep Links
- Notification Actions

Entwickle außerdem einen zuverlässigen Background-Betrieb ohne zwingende Google Play Services.

Berücksichtige:

- Doze
- App Standby
- Prozessbeendigung
- Reboot
- Netzwerkwechsel
- Android-Hintergrundbeschränkungen
- Akkuverbrauch

Unterscheide sauber zwischen Providern, die Push unterstützen, und solchen, die dauerhafte oder periodische Verbindungen benötigen.

**Definition of Done:** Orbiliq kann unter realistischen Android-Bedingungen zuverlässig neue Nachrichten verarbeiten und anzeigen, soweit die Provider-Technik dies ermöglicht.

---

# Prompt 16 – Suche, Medien, Gruppen und erweiterte Funktionen

Vervollständige jetzt die wichtigsten übergreifenden Messenger-Funktionen.

Implementiere eine schnelle lokale globale Suche über:

- Personen
- Chats
- Nachrichten
- Medien

Verbessere außerdem:

- Media Pipeline
- Upload/Download
- Media Cache
- Sprachnachrichten
- Dateien
- Gruppen
- Mitglieder
- Gruppeninformationen
- providerabhängige Adminfunktionen

Ergänze, soweit Provider dies unterstützen:

- Antworten
- Reaktionen
- Weiterleiten
- Edit
- Delete
- Pinning
- Mehrfachauswahl

Die UI darf Aktionen nur anzeigen, wenn die entsprechende Capability vorhanden ist.

Berücksichtige Status/Stories/Channels konzeptionell, ohne inkompatible Messenger-Konzepte künstlich gleichzusetzen.

Calls dürfen weiterhin zur Original-App weiterleiten, wenn native Integration unverhältnismäßig wäre.

**Definition of Done:** Die zentralen Messenger-Funktionen sind providerübergreifend konsistent nutzbar.

---

# Prompt 17 – Security & Privacy Hardening

Führe einen gezielten Security- und Privacy-Audit des gesamten Projekts durch.

Untersuche insbesondere:

- Session Secrets
- Kryptoschlüssel
- lokale Datenbank
- Medien
- Backups
- Logs
- Notifications
- IPC
- Intents
- Deep Links
- Exported Components
- FileProvider
- Android Permissions
- Clipboard
- Screenshots
- temporäre Dateien
- Crash Reports
- Netzwerkkommunikation
- Dependency Supply Chain

Implementiere bzw. vervollständige:

- biometrischen App-Lock
- konfigurierbaren Screenshot-Schutz
- sichere Secret Storage
- Privacy-Einstellungen
- transparente Berechtigungsbegründungen

Keine Tracker, Werbe-SDKs oder unnötige Telemetrie.

Überprüfe außerdem, ob Orbiliq tatsächlich ohne Google Play Services funktionsfähig bleibt.

Erstelle ein Threat Model und dokumentiere verbleibende Risiken.

**Definition of Done:** Security-Review dokumentiert, kritische Findings behoben und verbleibende Risiken transparent festgehalten.

---

# Prompt 18 – Gesamt-QA und erste Alpha-Release

Betrachte jetzt das gesamte Projekt als Release-Kandidat.

Führe einen umfassenden Audit durch:

- Architektur
- Security
- Datenschutz
- Datenintegrität
- Provider
- Sync
- Offline Queue
- UI
- Background Processing
- Notifications
- Performance
- Akkuverbrauch
- Accessibility
- Prozessneustarts
- Netzwerkfehler
- Datenbankmigrationen
- Fehlerzustände

Erweitere automatisierte Tests gezielt.

Teste insbesondere Kombinationen mehrerer Provider gleichzeitig.

Keine Tests dürfen nur deshalb entfernt oder abgeschwächt werden, damit CI grün wird.

Verbessere CI und GitHub-Projektstruktur.

Erstelle:

- Release-Workflow
- Debug/Alpha APK
- Changelog
- Installationsanleitung
- Provider-Kompatibilitätsmatrix
- bekannte Einschränkungen
- Security-Hinweise
- Architekturübersicht

Überprüfe außerdem alle Dependencies und Lizenzen auf Kompatibilität mit dem MIT-Projekt.

Behebe gefundene Probleme eigenständig, solange keine riskante Grundsatzentscheidung erforderlich ist.

Am Ende erstelle einen Release-Readiness-Bericht mit:

- bestandenen Tests
- unterstützten Funktionen je Provider
- bekannten Bugs
- Security-Risiken
- Account-/Ban-Risiken
- noch nicht unterstützten Funktionen
- empfohlenen nächsten Schritten

**Definition of Done:** Aus einem frischen Checkout lässt sich Orbiliq reproduzierbar bauen und installieren. Die tatsächlich funktionierenden Provider und Features sind transparent dokumentiert und eine erste Alpha-Version kann über GitHub Releases veröffentlicht werden.
