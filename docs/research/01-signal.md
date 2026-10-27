# Signal: Integrationsentscheidung

**Stand: 27.10.2026. Entscheidung: bedingter lokaler Companion-Ansatz; kein direktes MIT-only-Embedding.**

## API, Protokoll und vorhandene Projekte

Signal veröffentlicht Kryptografie-Spezifikationen und Clientcode. Das ist keine unterstützte öffentliche API für einen vollständigen Drittclient. `libsignal` enthält Kryptografie und weitere Signal-Bausteine; Transport, Accountzustand, Gruppen, Medien und lokale Datenhaltung werden dadurch nicht automatisch zu einem fertigen Messenger-SDK. Die aktuelle Kryptografiedokumentation umfasst unter anderem PQXDH, Double Ratchet, Sesame und ML-KEM Braid. Keine eigene Variante oder Auswahl alter Protokollstände implementieren. [Signal-Dokumentation](https://signal.org/docs/), [libsignal](https://github.com/signalapp/libsignal).

| Projekt | Konkreter Befund | Bedeutung für Orbiliq |
|---|---|---|
| [Signal Android](https://github.com/signalapp/Signal-Android) | Offizieller vollständiger Android-Client, AGPL; Servicecode innerhalb des Repositories | Beste plattformnahe Basis für separaten Provider-Companion, kein stabiles öffentliches SDK |
| [Molly](https://github.com/mollyim/mollyim-android) | Android-Fork, AGPL-3.0-only; WebSocket, Google-freie Variante, optional UnifiedPush | Wertvolle alternative Android-Basis; keine vorhandene universelle IPC-Schnittstelle unterstellen |
| [libsignal](https://github.com/signalapp/libsignal/blob/fc90b90fa2b9c37822fa47ba0e0dcdf387a0c071/README.md) | Rust mit Java/JNI; Android-Artefakte und ABIs vorhanden; Fremdnutzung ausdrücklich unsupported, APIs änderbar | Technisch Android-taugliche Kryptografie; kein MIT-kompatibles reines Gesamtpaket |
| [signal-cli](https://github.com/AsamK/signal-cli) | CLI, JSON-RPC, D-Bus; gute Messaging-Funktionen; derzeit mindestens JRE 25 und native libsignal-client | Für Referenzversuche gut; Desktop-/Serverdistribution ist kein Android-AAR |
| [Turasa/libsignal-service-java](https://github.com/Turasa/libsignal-service-java) | Gepatchter Service-Fork, von signal-cli genutzt; Provisioning ohne GCM | Vorhandenen Code evaluieren statt Service neu bauen; Lizenzangaben widersprüchlich: README AGPL, LICENSE/GitHub GPL |
| [mautrix-signal](https://github.com/mautrix/signal) | Aktuelle Go-basierte Matrix-Bridge, AGPL, Linking und optionaler History-Backfill dokumentiert | Belegt wiederverwendbare Serviceintegration; ist keine transparente Android-Library und nicht generell ein signal-cli-Wrapper |

Untersuchte SHAs und konkrete Dateien: [Quellenregister](07-sources.md). Die CLI fordert häufige Updates; mehrere Monate alte Versionen können serverseitig inkompatibel werden. Das ist ein Betriebsbedarf, keine zugesicherte dreimonatige Kompatibilitätsfrist. [signal-cli README](https://github.com/AsamK/signal-cli/blob/49aaef000f5459b9a95d145f2441091e9623476a/README.md).

## Authentifizierung und Geräteverknüpfung

**Verknüpftes Gerät bevorzugen.** QR-/Provisioning-Anmeldung nutzt ein bestehendes Primärgerät, erzeugt einen eigenen Geräte-Endpunkt und vermeidet die erneute Registrierung des Alltagskontos. Der aktuelle Signal-Support nennt auch Android und iPhone als verknüpfbare Geräte. Die frühere pauschale Aussage „nur Desktop/iPad“ ist damit nicht mehr zutreffend. Der Support nennt fünf verknüpfte Geräte, 45 Tage Inaktivität als Entkopplungsgrund und mindestens eine Online-Verbindung des Primärgeräts innerhalb von 30 Tagen. Diese Regeln vor Implementierung erneut abfragen. [Linked Devices](https://support.signal.org/hc/en-us/articles/360007320551-Linked-Devices).

Registrierung über signal-cli unterstützt Telefonnummer, SMS/Anruf, gegebenenfalls CAPTCHA und PIN. Eine Neuregistrierung kann einen bisherigen Primärclient abmelden. Deshalb keine automatische Registrierung oder Recovery mit dem persönlichen Konto. Das aktuelle CLI unterstützt auch Linking bestehender Konten ohne Telefonnummer; daraus folgt keine allgemein zugesicherte telefonnummerlose Neuregistrierung für Orbiliq. Hier weichen aktuelle technische Dokumentation und ältere Formulierungen der Nutzungsbedingungen voneinander ab. [CLI-Anmeldung](https://github.com/AsamK/signal-cli/blob/49aaef000f5459b9a95d145f2441091e9623476a/README.md), [Signal-Bedingungen](https://signal.org/legal/).

QR auf demselben Telefon ist ein gesondertes UX-Problem: Die Kamera der Original-App kann nicht den eigenen Bildschirm scannen. Kein nicht belegtes „ein Klick verbindet beide Apps“ versprechen. Zweitbildschirm/unterstützter Provisioning-Übergabefluss muss im Prototyp nachgewiesen werden.

## Sessions, Verschlüsselung und Synchronisation

Der Provider verwaltet Identitätsschlüssel, Prekeys, Ratchet-Sessions, Geräteauthentifizierung, Gruppen-/Profilzustände und gegebenenfalls History-Transfer. Private Schlüssel bleiben im Companion; Orbiliq bekommt nur vom Nutzer freigegebene entschlüsselte Nutzdaten. Native Protokollschlüssel lassen sich nicht pauschal als nicht exportierbare Android-Keystore-Schlüssel ablegen: Die Library benötigt sie häufig im Speicher. Keystore schützt stattdessen lokale Verschlüsselungsschlüssel. [libsignal](https://github.com/signalapp/libsignal), [Architektur](05-architecture.md).

Neue Nachrichten kommen an jedes verknüpfte Gerät; Sync-Nachrichten müssen auch eigene ausgehende Nachrichten, Kontakte/Gruppen und Lesestatus abbilden. ACI/PNI und Benutzernamen sind relevant; Telefonnummern sind keine hinreichenden Primärschlüssel. Regelmäßiger Empfang ist für einen konsistenten Servicezustand erforderlich. Session-Store und empfangene Ereignisse müssen absturzfest verarbeitet werden. [CLI-Handbuch](https://github.com/AsamK/signal-cli/blob/49aaef000f5459b9a95d145f2441091e9623476a/man/signal-cli.1.adoc).

**Historie differenzieren:** Signal bietet inzwischen beim Linking einen Geräte-Transfer von Chats und begrenzter Medienhistorie. Das ist kein beliebig abrufbares Serverarchiv. Die offizielle Linking-Funktion belegt nicht automatisch die Unterstützung in signal-cli oder einem neuen Adapter. Die aktuelle mautrix-Dokumentation beschreibt optionalen Backfill beim Linking; für den gewählten Android-Companion muss der konkrete Transferpfad geprüft werden. Nicht nur „ab jetzt“ behaupten, aber auch keinen vollständigen Altbestand zusagen. [Signal-Gerätehilfe](https://support.signal.org/hc/en-us/articles/360007320551-Linked-Devices), [mautrix-Authentifizierung](https://docs.mau.fi/bridges/go/signal/authentication.html).

E2EE endet am lokalen Provider-Gerät. Jede weitere Kopie in Timeline, Suchindex und Mediencache ist Klartext auf einem zusätzlichen Endpoint und braucht denselben Schutz. Sicherheitsnummern, Identitätswechsel, verschwindende Nachrichten und View-once-Inhalte dürfen nicht durch Zusammenführung oder Cache umgangen werden.

## Feature-Einschätzung

Die folgenden Messaging-Funktionen sind für vorhandene Serviceclients belegt; ihre Orbiliq-/Android-Anbindung bleibt bis zum Prototyp experimentell.

| Bereich | Einstufung | Begründung / Einschränkung |
|---|---|---|
| Text senden/empfangen, Anhänge | E | CLI bietet `send`, `receive`, Attachment-Upload/-Download; MIME/Größen/Retry zusätzlich abbilden |
| Bilder, Videos, Dateien, Voice Notes | E | Protokoll-/Attachment-Funktionen vorhanden; Codec, Darstellung und Voice-Metadaten Android-seitig prüfen |
| Gruppen / Verwaltung | E | Gruppenbefehle, Rollen, Einladungen und Sync; keine universelle Gleichheit aller Clientfeatures |
| Antworten / Reaktionen | E | `send` mit Quote-Feldern und `sendReaction`; Nachrichtenidentität korrekt erhalten |
| Bearbeiten / für alle löschen | E | `--edit-timestamp`, `remoteDelete`; Regeln/Fristen und Gegenstellen berücksichtigen |
| Kontakte / Receipts / Typing | E | Service-IDs, Kontakt-/Profilzustände und entsprechende Befehle; kein allgemeines Online-/Last-seen-Feature |
| Stories | X | CLI hat inzwischen `sendStory`, Story-Reaktionen/-Antworten und Empfangsoptionen; vollständiger Stories-Workflow nicht nachgewiesen |
| Channels | — | Kein mit Telegram-Kanälen gleichzusetzendes öffentliches Channel-Konzept in den geprüften Quellen |
| Calls | N im ersten Provider | Anrufsignalisierung/Events sind kein Audio-/Video-Stack; RingRTC und Android-Call-Lifecycle wären ein eigenes Projekt |
| Offline / Altbestand | E/X | Eigener Cache möglich; Geräte-Transfer und Lückenbehandlung backendabhängig |

Funktionsbelege: [versioniertes signal-cli-Handbuch](https://github.com/AsamK/signal-cli/blob/49aaef000f5459b9a95d145f2441091e9623476a/man/signal-cli.1.adoc), [Signal-Android-Quellcode](https://github.com/signalapp/Signal-Android/tree/6e9dddc01f1b26e608ac683807c51e139c141ed2). Die Zahl vorhandener Methoden beweist keine fehlerfreie Interoperabilität.

Für spätere Calls ist [Signal RingRTC](https://github.com/signalapp/ringrtc/tree/84c5b2aff246392b365d73d6628529a149d6aed2) der konkrete vorhandene Kandidat: WebRTC-basierte Sprach-/Video-Middleware, AGPL-Lizenzdatei. Eine Integration muss Service-Signalisierung, Medienstack, Gerätezustände und Android-Audio-/Call-Lifecycle zusammenbringen. Kein Hinweis auf Call-Events in einer Messaging-Library beweist diese vollständige Funktion. RingRTC wurde nur anhand README und Lizenz geprüft; kein Build oder Abhängigkeitsaudit.

## Google-freier Hintergrundbetrieb

Ohne Google ist Serviceverkehr über eigene Verbindungen grundsätzlich möglich. Molly dokumentiert WebSockets und UnifiedPush. UnifiedPush benötigt dort einen MollySocket-Server und ist laut gelesenem README für verknüpfte Geräte derzeit nicht verfügbar. Es ist daher **keine** lokale Standardlösung für den empfohlenen Linked-Companion. Kein Pflichtserver einführen. [Molly-README, UnifiedPush](https://github.com/mollyim/mollyim-android/blob/892d6264ba007b5458a8b74cf9183c665f1f17a1/README.md#unifiedpush).

Socket, Wiederverbindung und lokaler Dienst müssen Doze, Neustarts und OEM-Prozessbeendigung überstehen oder Empfangslücken sichtbar machen. Die Original-App kann hierfür Referenz sein; ein selbst gebauter Companion erbt deren Zuverlässigkeit nicht automatisch. Einzelheiten im [Hintergrundkonzept](05-architecture.md).

## Lizenz, Kontorisiko und Wartung

`Signal-Android`, Molly und libsignal sind AGPL; signal-cli nennt GPLv3, seine tatsächlichen Service-/Kryptoabhängigkeiten müssen zusätzlich geprüft werden. Direkte Einbindung kann eine copyleftpflichtige Kombination erzeugen. MIT-Eigenanteile sind möglich; die komplette App darf nicht als ausschließlich MIT lizenziert beschrieben werden. Ein separater APK-/IPC-Companion ist eine zu prüfende Architektur, kein Lizenztrick. Details und Konflikt bei Turasa: [Lizenzdossier](06-licenses-and-risks.md).

**Kontorisiko: mittel, nicht quantifizierbar.** Es gibt keine Signal-Zusage zur dauerhaften Unterstützung beliebiger Fremdclients. CAPTCHA, Rate-Limits, Registration Lock, Verbindungs-/Versionsänderungen und Sperrung bleiben möglich. Die Bedingungen untersagen unter anderem unerlaubte/automatisierte Registrierungen und missbräuchliche Kommunikation. Kein Anti-Ban-Versprechen. [Signal-Nutzungsbedingungen](https://signal.org/legal/).

**Wartung: hoch.** Änderungen an Clientprotokoll, Kryptografie, Gruppen, Registrierung und Sync zeitnah verfolgen; ABI- und Store-Migrationen prüfen. Das Risiko ist eher die Kopplung an laufend veränderte offizielle Clients als fehlender Einblick in den Quellcode.

## Empfohlene Architektur und Freigabebedingungen

1. MIT-Kern hält ausschließlich das eigene generische Provider-Protokoll und Domainmodell.
2. Separat installierbarer AGPL-Companion auf Basis **eines** aktuellen Android-Clients; Signal Android als primäre Referenz, Molly als Alternative mit geprüftem Updatepfad. Keine beiden Forks gleichzeitig pflegen.
3. Verknüpftes Gerät mit eigenen Stores; IPC über nutzerbestätigte Bindung, authentifizierte Gegenstelle und kurzlebige Medien-Handles. Kein Auslesen der Original-App-Datenbank.
4. signal-cli für Funktionsvergleich oder getrennte Forschungsumgebung; keine JVM-25-Portierung als unbemerkte Nebenaufgabe. Ein externer signal-cli-Server wäre nur optional und verändert die Vertrauensgrenze.
5. Bis zur Freigabe nur klar bezeichnete Benachrichtigungs-/App-Übergabe als begrenzter Fallback.

**Später nachzuweisen:** unabhängige Lizenz-/Distributionsgrenze; Android-ABI-Build; Linking auf dem Zielgerät; Schlüsselwechsel; Geräteentzug; eingehende und eigene ausgehende Sync-Nachrichten; Medien/Gruppen/Edits; Prozessabbruch während Empfang; Doze ohne Play Services. Scheitern Lizenzgrenze oder Wartungsbudget, bleibt Signal bei der Ergänzungsintegration. Kein produktiver Provider wurde mit diesem Bericht freigegeben.
