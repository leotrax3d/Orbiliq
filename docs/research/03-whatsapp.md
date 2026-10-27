# WhatsApp: Integrationsentscheidung

**Stand: 27.10.2026. Entscheidung: kein verlässlich zugesicherter persönlicher Vollprovider. Optionaler lokaler Companion mit whatsmeow bleibt experimentell und lizenzabhängig.**

## Offizielle APIs und ihre tatsächliche Reichweite

Die **WhatsApp Business Platform / Cloud API** ist ein offizieller, von Meta gehosteter Geschäftskanal. Meta beschreibt Business-Portfolio, WhatsApp Business Account und Business-Telefonnummer als Voraussetzungen. Sie ist keine universelle API, um ein beliebiges privates Konto samt bisherigen Gruppen und Chats als weiteren persönlichen Client zu öffnen. HTTPS-Senden vom Telefon macht einen Cloud-/Webhook-Workflow nicht lokal. Empfang erfordert einen erreichbaren Webhook-Endpunkt. Tokens gehören nicht als gemeinsames Entwicklergeheimnis in eine APK. [Meta-eigene API-Sammlung bei Postman](https://www.postman.com/meta/whatsapp-business-platform/documentation/wlk6lh4/whatsapp-cloud-api), [Meta zur Cloud API](https://about.fb.com/news/2022/05/announcing-new-products-to-make-business-messaging-easier/).

Business-App-/Cloud-API-Coexistence ist als möglicher Geschäftsworkflow getrennt zu prüfen; sie ist kein Beleg für eine private Client-API. Dieser Bericht setzt keine überholte Behauptung voraus, Business-App und Cloud API könnten unter keinen Umständen dieselbe Nummer nutzen. Auch ein erweiterter Business-Funktionsumfang würde die Produktentscheidung für private Orbiliq-Konten nicht ändern.

**DMA-Interoperabilität ist ebenfalls kein Account-Login.** Meta beschreibt Kommunikation mit Nutzern zugelassener anderer Dienste und Opt-in europäischer WhatsApp-Nutzer. Das würde Orbiliq zu einem eigenen interoperablen Kommunikationsdienst mit Vertrags-/Betriebsanforderungen führen, nicht zum lokalen Client eines bestehenden WhatsApp-Kontos. Kein Zugriff auf dessen gesamtes bisheriges Postfach folgt daraus. Das widerspricht der Vorgabe, kein eigenes Netz mit Pflichtserver zu bauen. [Meta zur Interoperabilität](https://about.fb.com/news/2025/11/messaging-interoperability-whatsapp-enables-third-party-chats-for-users-in-europe/).

## Bestehende FOSS-Projekte konkret

| Projekt | Technischer Befund | Lizenz-/Android-Folge |
|---|---|---|
| [whatsmeow](https://github.com/tulir/whatsmeow) | Go, direkter Web-Multi-Device-Client; private und Gruppenmedien, App-State, Receipts, Retry; Status experimentell, Calls nicht implementiert | MPL-2.0 **mit GPL-3.0-Abhängigkeit `go.mau.fi/libsignal v0.2.2`**; Go-Mobile-/JNI-Port im Companion zu prüfen |
| [Baileys](https://github.com/WhiskeySockets/Baileys) | TypeScript, direkte WebSockets statt Chromium; Multi-Device; v7 mit Breaking Changes; Auth-/History-Beispiele | Hauptprojekt MIT, aber `libsignal ^6.0.0` GPL-3.0; Node-APIs und Runtime sind kein Android-WebView-JavaScript |
| [Cobalt](https://github.com/Auties00/Cobalt) | Java/Kotlin-API für Linked-/Mobile-/Cloud-Transport; aktuelle Basis Java 25, virtuelle Threads und plattformabhängige native Medienkomponenten | MIT-Hauptlizenz; keine belegte Android-Drop-in-Eignung, Native-/Dependency-Lizenzen offen; nicht wegen „Kotlin“ bevorzugen |
| [whatsapp-web.js](https://github.com/pedroslopez/whatsapp-web.js) | Automatisiert offiziellen Webclient via Node/Puppeteer/Browser | Apache-2.0-Hauptprojekt; Chromium-/DOM-/Webclient-Abhängigkeit, hoher Ressourcenbedarf; Android-Hintergrund ungeeignet |
| [mautrix-whatsapp](https://github.com/mautrix/whatsapp) | Matrix-Bridge auf whatsmeow-Basis, AGPL | Wertvolle Feature-/Fehlerbehandlung-Referenz, aber Homeserver/Bridge und zusätzliche Vertrauensgrenze; keine lokale Standardarchitektur |

Primärdateien und SHAs: [Quellenregister](07-sources.md). Bei WhatsApp ist **kein offizieller vollständiger FOSS-Verbraucherclient** in diesen Projekten enthalten. Vorhandene Implementierungen sparen Reverse Engineering, machen den verwendeten Servicezugang aber nicht offiziell.

### Wesentlicher transitiver Lizenzbefund

Der [geprüfte whatsmeow-Dependency-Graph](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/go.mod) nennt `go.mau.fi/libsignal v0.2.2`. Dessen tatsächlich abgerufenes Versionsarchiv enthält GPLv3, ebenso das [zugehörige Repository](https://github.com/tulir/libsignal-protocol-go). Die pauschale Aussage „MPL erlaubt MIT-App, also ist whatsmeow erledigt“ ist falsch.

Baileys nennt [im package.json](https://github.com/WhiskeySockets/Baileys/blob/81345beb67ca3fb0eb79a2c20cd4ac24da517929/package.json) `libsignal ^6.0.0`. Das konkret geprüfte Paket 6.0.0 und [WhiskeySockets/libsignal-node](https://github.com/WhiskeySockets/libsignal-node) sind GPLv3. Der Lockfile-resolvierte Graph eines späteren Builds bleibt gesondert zu prüfen. Beide Integrationen dürfen nicht als MIT-only-Gesamtpaket freigegeben werden. Keine Kryptobibliothek nur zur Lizenzumgehung neu schreiben. [Lizenzfolgen](06-licenses-and-risks.md).

## Authentifizierung, Geräte und Session

**Nur Companion-Linking untersuchen**, keine inoffizielle Neuregistrierung als Primärtelefon. QR oder Pairing-Code werden vom vorhandenen WhatsApp auf dem Primärtelefon bestätigt. Der Pairing-Code ist kein Mobile-Registrierungstoken. QR auf demselben Telefon benötigt einen nachgewiesenen Übergabe-/Zweitbildschirmablauf. [Baileys-Linking-Dokumentation](https://github.com/WhiskeySockets/Baileys/blob/81345beb67ca3fb0eb79a2c20cd4ac24da517929/README.md), [offizielle Gerätehilfe](https://faq.whatsapp.com/1317564962315842/).

Persistente Daten umfassen Geräteidentität, Noise-/Signal-Schlüssel, Prekeys, Ratchet-Sessions, App-State-Sync-Schlüssel und Kontakt-/Gerätezustände. Das sind Konto-Zugangsdaten, keine wegwerfbaren Caches. Bei Baileys müssen Schlüsseländerungen laufend gespeichert werden; die Beispiel-Dateispeicherung ist kein fertiges verschlüsseltes Produktions-Backend. Für whatsmeow bietet `store/sqlstore` eine Storestruktur; Verschlüsselung und Android-Storeadapter sind eigene Aufgaben. [Baileys-Sessionbeispiel](https://github.com/WhiskeySockets/Baileys/blob/81345beb67ca3fb0eb79a2c20cd4ac24da517929/README.md), [whatsmeow SQL-Store](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/store/sqlstore/container.go).

Companions können nach Linking ohne dauernd online bleibendes Primärtelefon kommunizieren; dennoch gelten serverseitige Geräte-, Aktivitäts- und Entkopplungsregeln. Die offiziellen Help-Seiten wurden teilweise ohne auslesbaren Inhalt geliefert; **keine feste aktuelle Zahl oder Inaktivitätsfrist wird hier als verifiziert ausgegeben**. Bei Linking, Revoke und Wiederanmeldung reale Limits prüfen, keinesfalls Geräteplätze durch wiederholtes Neulinken verbrauchen.

## E2EE, Historie und Identitäten

Die bestehende Library übernimmt das WhatsApp-spezifische Signal-/Multi-Device-Verfahren. Gleicher Kryptografie-Familienname bedeutet keine direkte Interoperabilität mit Signals Dienst. Orbiliq ist bei diesem Ansatz selbst ein entschlüsselnder Endpunkt. Keine TLS-Deaktivierung, kein Session-Export in Logs und keine automatische Akzeptanz beliebiger Identitätswechsel. Sicherheitscodes/-ereignisse müssen im Prototyp mit dem Primärclient abgeglichen werden. Die genaue aktuelle Kryptografie ist anhand kompatibler Upstream-Versionen zu übernehmen; das Hersteller-Whitepaper war im Abruf nicht vollständig zugänglich. [whatsmeow-Implementierung](https://github.com/tulir/whatsmeow), [Source-Dateinachweise](source-artifacts.json).

History-Sync überträgt vom Dienst/Primärgerät bereitgestellte Blöcke; nicht jede bisherige Nachricht und jedes ältere Medium muss enthalten sein. `syncFullHistory` bei Baileys oder `BuildHistorySyncRequest` bei whatsmeow sind **keine Vollständigkeitsgarantie**. Orbiliq braucht erkennbare History-Lücken, Fortschritt und ein eigenes dauerhaftes Nachrichtenmodell. Sessionstore und Nachrichtenarchiv nicht verwechseln. [whatsmeow send.go](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/send.go).

Account-/Kontaktidentitäten sind providerinterne JIDs; Telefonnummern und LIDs sind nicht austauschbar. Migrationen und Zuordnungen erhalten, sonst entstehen doppelte Kontakte und falsch adressierte Nachrichten. Keine dauerhafte Telefonnummer als alleinigen Schlüssel annehmen. Baileys' Breaking Changes und die aktuellen Protokolltypen sind hierfür Warnsignale, keine Stabilitätszusicherung. [Baileys](https://github.com/WhiskeySockets/Baileys).

## Feature-Einschätzung für whatsmeow

Alle Funktionen bleiben auf Orbiliq-Ebene X, bis der Android- und Kontoversuch erfolgreich ist. „Vorhanden“ bedeutet geprüfte Library-Unterstützung.

| Bereich | Befund / Einstufung |
|---|---|
| Text, Empfang, private Chats und Gruppen | Vorhanden, **X** als Integration; nach bewiesenem Betrieb E, nie Herstellerzusage |
| Bilder, Videos, Dateien, Audio/Voice Notes | Vorhandener Medienpfad, **X**; Upload, Download, MIME, temporäre URLs, fehlende alte Medien behandeln |
| Gruppenverwaltung, Einladungen | Vorhanden, **X**; Mitglieds-/Adminrechte und aktuelle Gruppenmetadaten beachten |
| Antworten | Kontext-/quoted-message-Daten möglich, **X**; Origin-ID und Teilnehmer erhalten |
| Reaktionen | `BuildReaction`, **X** |
| Bearbeiten / Löschen für alle | `BuildEdit`, `BuildRevoke`, **X**; Rechte und Fristen serverseitig |
| Lokal löschen | **E** im eigenen Archiv, keine Zusage über andere Geräte |
| Kontakte, App-State, Typing und Receipts | Vorhanden, **X**; LID/JID-Migration und Datenschutz berücksichtigen |
| Status/Stories | Upstream nennt Statusversand selbst experimentell, **X**; kein V1-Versprechen |
| Channels | `newsletter.go` mit Abonnements, Nachrichten und Reaktionen; **X**, keine gleichwertige Vollabdeckung behaupten |
| Calls | **N** mit gewählter Library; Anrufereignisse/`RejectCall` sind keine Gesprächsteilnahme |
| Vollständige Historie | **E/X**; gelieferter Syncumfang begrenzt, alte Medien können fehlen |

Belege: [README](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/README.md), [Nachrichtenmethoden](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/send.go), [Newsletter](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/newsletter.go), [Calls](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/call.go).

## Hintergrundbetrieb ohne Google

Whatsmeow und Baileys kommunizieren selbst per Netzverbindung; ihre Protokollkommunikation braucht keine Play Services. Google-freie Registrierung und Verfügbarkeit der Original-App sind eine andere Frage. Dauerhafter Socketbetrieb unter Android erfordert ein zulässiges Hintergrundkonzept; Go-Runtime oder Node bewirken keine OS-Ausnahme. Vier unabhängige Providerverbindungen kosten zudem Akku. Kein beliebiger UnifiedPush-Distributor kann ohne Gegenseite WhatsApp-Wakeups erzeugen. [Android-Rahmenbedingungen](05-architecture.md).

## Konten, Reverse Engineering und Wartung

**Ban-Risiko hoch, Wahrscheinlichkeit unbekannt.** WhatsApp untersagt unerlaubte Clients-/Servicezugriffe und Reverse Engineering in seinen Bedingungen; die rechtliche Anwendbarkeit einzelner Klauseln und mögliche gesetzliche Interoperabilitätsrechte sind getrennte Fragen. Eine FOSS-Lizenz erlaubt weder automatisch die Nutzung des Dienstes noch verhindert sie Kontosperren. QR-Linking und geringe Sendemenge bieten keinen sicheren Schutz. Auch bei rein persönlicher Verwendung kann der Zugang brechen. [WhatsApp Terms](https://www.whatsapp.com/legal/terms-of-service), [Baileys-Disclaimer](https://github.com/WhiskeySockets/Baileys).

**Wartung sehr hoch:** Webversion, App-State, Protobuf, Geräte-/LID-Umstellungen, Auth-Challenges und History-Sync ändern sich. Projektaktivität belegt keine stabile Schnittstelle. Kompatibilitätsupdates müssen kurzfristig möglich sein; eine kaputte Session darf nicht automatisch gelöscht oder durch neue Registrierungen „repariert“ werden.

## Empfohlene Strategie und Abbruchkriterien

Für ein verlässlich angekündigtes V1: Original-App-Übergabe und optional die [begrenzte Benachrichtigungsintegration](05-architecture.md). Kein vollständiges WhatsApp-Versprechen.

Falls später ausdrücklich ein experimenteller Provider verfolgt wird: **whatsmeow** als technische erste Wahl, Go-Wrapper mit schmaler Android-Grenze und isoliertem Companion; GPL-/MPL-Pflichten vollständig beachten und die Unabhängigkeit vom MIT-Kern vor Distribution prüfen. Baileys als Referenz/Alternative, falls Android-Runtime und Lizenzpfad begründet tragfähig werden. Cobalt wegen Java-25-/Native-Annahmen nicht als schnelle Kotlin-Lösung darstellen. Kein Pflichtserver, keine eingebettete Browserautomatisierung als Kern.

Spätere Go/No-go-Nachweise: exakter Lizenzgraph; Android-Go-/JNI-Build, SQLite- und verschlüsselter Sessionstore; Pairing auf gleichem Gerät; History-Lücken; Schlüssel-/Kontaktmigrationen; eigene ausgehende Sync-Ereignisse; Medien/Gruppen/Edits; Doze/Neustart; Entkopplung. Wiederholte Sperren, notwendige Schutzumgehung oder unhaltbare Protokollwartung beenden den Vollprovider-Versuch. In dieser Recherche keine dieser Konto-/Geräteprüfungen ausgeführt.
