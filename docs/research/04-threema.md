# Threema: Integrationsentscheidung

**Stand: 27.10.2026. Entscheidung: lokale Ergänzung für V1; vollständigen Multi-Device-Provider zurückstellen.**

## Offizielle Angebote und vorhandener Code

Threema veröffentlicht vollständigen Clientcode und Protokolldokumentation, bietet aber in den geprüften Quellen kein allgemein freigegebenes Android-SDK für einen persönlichen Drittclient. Das offizielle **Gateway** ist eine andere Identität und ein anderer Workflow. Open Source, Serverzugang und bezahlte Produktlizenz sind getrennt zu behandeln. [Threema Open Source](https://threema.com/en/why-threema/open-source), [Gateway API](https://gateway.threema.ch/en/developer/api).

| Projekt / Weg | Konkreter Befund | Konsequenz |
|---|---|---|
| [Threema Android](https://github.com/threema-ch/threema-android) | Offizieller Android-Client, AGPL, Kotlin/Java/Rust, dokumentierte Lizenz- und Buildvarianten | Gute Verhaltens-/Protokollreferenz; kein MIT-Client-SDK |
| [Threema Libre](https://threema.com/en/faq/libre-installation) | Offizielle freie Android-Variante ohne proprietäre Komponenten; Shop-Lizenz erforderlich | Sinnvolle Original-App auf Google-freien Geräten, nicht kostenlose unbegrenzte Dienstnutzung |
| [Threema Web](https://github.com/threema-ch/threema-web) | AGPL-Webclient; Android-Verbindung über verschlüsselten Web-/Telefon-Kanal, SaltyRTC/WebRTC-Komponenten | Vollerer Telefonzugriff möglich, aber Original-App online erforderlich; WebView-/Protokollport bleibt eigene Arbeit |
| [Threema Desktop](https://github.com/threema-ch/threema-desktop) | Eigenständiger Desktop-Client für Windows/macOS/Linux, AGPL, TypeScript und Rust/WASM | Technisch interessantester Linked-Device-Ansatz; Desktop-Runtime ist kein Android-Kotlin-SDK |
| `libthreema` im Android-/Desktop-Repository | Rust-Code tatsächlich eingebettet; Android mit UniFFI-Werkzeugen, Desktop mit WASM; `license = AGPL-3.0-only` | Nicht als hypothetische freie MIT-Library behandeln; Rust allein ersetzt nicht sämtliche Client-/Synczustände |
| [Threema Gateway](https://gateway.threema.ch/en/developer/api) | HTTPS-API mit Java-/Rust-/weiteren SDKs, separater Gateway-ID und API-Secret | Offizielle Serviceintegration, aber kein Zugang zum persönlichen Konto/Altverlauf |

Konkrete libthreema-Dateien: [Android Cargo.toml](https://github.com/threema-ch/threema-android/blob/d44a2b53b2ea2c0bf1065bdaa96987570e5b4e6d/domain/libthreema/lib/Cargo.toml), [Desktop Cargo.toml](https://github.com/threema-ch/threema-desktop/blob/93a6615c5567f0cf8371619dc1a5e888eed1d0b6/packages/libthreema-wasm/libs/libthreema/lib/Cargo.toml). Ein zunächst vermutetes eigenständiges öffentliches `threema-ch/libthreema`-Repository lieferte 404; die Analyse stützt sich auf die tatsächlich vorhandenen eingebetteten Quellen.

## Identität, Authentifizierung und Geräteverknüpfung

Threema verwendet eine Threema-ID und langfristige Schlüssel, nicht zwingend eine Telefonnummer. Kontaktzuordnung muss auch ohne Telefonnummer funktionieren. Eine Wiederherstellung einer ID aus einem Backup ist **keine** zusätzliche unabhängige Gerätesession. Niemals denselben primären Account-Key ungeprüft in mehrere konkurrierende Mobilclients kopieren. [Threema-Support](https://threema.com/en/support/private).

Die Android-Quellen beschreiben mehrere Lizenzpfade: Google-Lizenznachweis, Shop-Lizenzschlüssel, Work-Zugang und Allowlist für Entwicklungsvarianten. Ein selbst gebauter Client kann nicht einfach den Google-Lizenznachweis einer anderen App herstellen. Je nach Variante sind bestehende ID-Backups beziehungsweise Shop-Lizenzprüfung vorgesehen. Das erlaubt keine Umgehung serverseitiger Produktkontrollen. [Versionierte Android-Bauanleitung und Lizenzprüfungen](https://github.com/threema-ch/threema-android/blob/d44a2b53b2ea2c0bf1065bdaa96987570e5b4e6d/README.md).

**Zwei Verknüpfungsgenerationen unterscheiden:**

- Der ältere Web-/Desktop-Weg benötigt die mobile App online. Er bietet Zugriff auf deren Chats/Kontakte/Medien und speichert eine wiederverwendbare Session; er ist keine eigenständige Offline-Geräte-Replik.
- Die neue **Threema 2.0 Desktop-Beta** arbeitet nach Linking unabhängig vom Telefon. Android-Unterstützung besteht seit April 2025. Die beim Abruf aktuelle FAQ nennt Android ab 6.0, iOS ab 6.4 und bis zu zwei Computer; mehrere Mobilgeräte/Tablets sind als geplant beschrieben. Daraus folgt **keine offizielle Zusage für Orbiliq als zusätzliches Android-Gerät**.

Belege: [Web/Desktop-Support](https://threema.com/en/support/private), [Android-Desktop-Ankündigung](https://threema.com/en/blog/android-new-desktop-beta), [aktuelle Multi-Device-FAQ](https://threema.com/en/faq/md-overview). Die FAQ ist für aktuelle Grenzen maßgeblicher als ältere PDFs, die nur iOS nennen. Limits vor Prototyp erneut prüfen.

## Protokolle, Verschlüsselung und Synchronisation

Client-Server-, E2EE- und Multi-Device-Schichten sind getrennt. Threema verwendet eigene Protokolle; Ibex ergänzt die E2E-Schicht um Forward Secrecy. Eine Implementierung nur mit generischem NaCl-/libsodium-Encrypt/Decrypt wäre unvollständig. Aktuelle Nachrichtenformate, Nonces, Replay-Schutz, Gruppen-, Medien- und Identitätszustände gehören dazu. [Threema zu Ibex](https://threema.com/en/blog/security-proof-ibex), [Cryptography Whitepaper](https://threema.com/press-files/2_documentation/cryptography_whitepaper.pdf) als vom Hersteller referenzierte weiterführende Spezifikation; das PDF wurde hier nicht vollständig ausgewertet.

Für Multi-Device ist im Repository ein konkretes Protokolldokument vorhanden: Schlüssel aus einer Device Group Key, unterschiedliche abgeleitete Schlüssel, persistente Nonce-Verwaltung und Transaktionen über Chat-/Mediator-Verbindungen. Ein WebSocket plus Rust-FFI genügt damit nicht für einen vollständigen zweiten Client. [Versioniertes Multi-Device-Protokoll](https://github.com/threema-ch/threema-desktop/blob/93a6615c5567f0cf8371619dc1a5e888eed1d0b6/packages/libthreema-wasm/libs/libthreema/threema-protocols/src/md.md).

Ein potenzieller Port muss Linking, Geräteschlüssel, dauerhaften Sync-/Transaktionszustand, Nachrichtenarchiv, Medien und Revocation vollständig übernehmen. Historie ist nur im vom jeweiligen Linking-/Transferpfad bereitgestellten Umfang verfügbar; kein generisches nachträgliches Serverarchiv versprechen. Telefon-Backup, Threema Safe und vollständige Chat-/Medienhistorie dürfen nicht gleichgesetzt werden.

## Warum Gateway nicht die persönliche Integration löst

Gateway authentifiziert eine eigene API-ID samt Secret; die Identität beginnt typischerweise mit `*`. Das Basic-Verfahren verschlüsselt serverseitig und unterstützt keinen Empfang. E2E-Modus hält den privaten Schlüssel beim Integrator und unterstützt Empfang/Receipts per Callback. Bezahlte Credits, separater Account und erreichbare Empfangsinfrastruktur passen nicht zu einem lokalen persönlichen Konto-Provider. [Gateway-Modi und API](https://gateway.threema.ch/en/developer/api), [Gateway-Identität](https://threema.com/en/products/work/gateway).

Es wird nicht pauschal behauptet, Gateway könne ausschließlich Text oder prinzipiell keinerlei Gruppenformate transportieren. Die API enthält auch Medien-/E2E-/Gruppenmechanismen. Entscheidend ist: Sie bietet **keinen autorisierten Login in das persönliche bestehende Konto samt dessen Zustand**. Die SDKs wären für einen späteren separaten Gateway-Anwendungsfall zu prüfen; dafür wird hier keine Library eingeführt oder Lizenzfreigabe behauptet.

## Features je Strategie

| Feature | Empfohlene lokale Ergänzung | Denkbarer voller Multi-Device-Port |
|---|---|---|
| Texte lesen/empfangen | E: sichtbare Notifications | X: vorhandener Desktop-/Android-Code, Port und Freigabe offen |
| Texte senden | E: vorhandene RemoteInput-Antwort, sonst App-Übergabe | X |
| Alte Nachrichten / vollständige Suche | N; höchstens eigener Vorschau-Cache | X, Transferumfang prüfen |
| Medien / Voice Notes | E für Vorschau/Teilen, N für vollständigen In-App-Austausch | X, Format-/Codec-/Dateipfade übernehmen |
| Gruppen lesen/schreiben | E für benachrichtigte Inhalte/Antworten | X |
| Gruppen verwalten | N | X |
| Reaktionen / zitierende Antworten | N als zugesicherte Integration | X; nicht aus Original-App-Unterstützung auf fertige Port-API schließen |
| Bearbeiten / für alle löschen | N | X; Clientversionen und unterstützte Formate prüfen |
| Kontakte | E: Titel/Person-Felder, keine vollständige Kontaktliste | X, ID-/Vertrauensstufen übernehmen |
| Lesestatus / Typing | N als konsistenter Datenstrom | X |
| Calls | N, nur Original-App öffnen | N im ersten Portumfang; separate Medien-/Signalisierungsarbeit |
| Status/Stories/Channels | —; kein entsprechendes Privatdienst-Konzept in geprüften Quellen belegt | — |
| Multi-Device | N als eigener Orbiliq-Endpunkt | X: Desktop-Unterstützung belegt, Android-Drittclient nicht |

Der alte Webclient wäre ein dritter Weg mit breiterem Zugriff, aber Telefon-/Session-Abhängigkeit. Ein eingebetteter Webclient bietet noch keine native gemeinsame Timeline. DOM-Auslesen, unklare WebView-Sicherheit und doppelte Hintergrundverbindungen sind keine empfehlenswerte Ersatzarchitektur.

## Google-freier Betrieb, Lizenz und Risiken

Threema Libre und Threema Push belegen den offiziellen Google-freien Weg. Push benötigt zugelassene Hintergrundaktivität und Datenverbindung; ein Orbiliq-Provider kann diesen Dienst nicht ohne passenden Account-/Clientcode einfach nutzen. Bei der Ergänzungsintegration erledigt die Original-App den Empfang; Orbiliq sieht nur die von ihr und Android freigegebenen Notifications. [Threema Libre](https://threema.com/en/faq/libre-installation), [Threema Push](https://threema.com/en/faq/threema-push).

**Lizenz:** Apps und eingebettetes libthreema AGPL; keine reine MIT-Übernahme. Offene Quellen erlauben Änderungen unter ihren Bedingungen, ersetzen aber keine Shop-Lizenz oder Drittclient-/Serverzugangsvereinbarung. Eine gesonderte Lizenz/Freigabe wäre eine Möglichkeit, ist aber nicht zugesagt. [Open-Source-Bedingungen](https://threema.com/en/why-threema/open-source), [Lizenzanalyse](06-licenses-and-risks.md).

**Kontorisiko:** bei zugelassener Original-App/Notifications niedrig; bei einem neuen Vollclient unklar und höher. Keine belastbare Ban-Häufigkeit für Orbiliq ableitbar. Lizenzchecks und nicht zugesicherter Gerätetyp sind konkrete Zugangsrisiken, kein Nachweis regelmäßiger Sperren. **Wartung sehr hoch:** Port über Kotlin/Java/Rust/TypeScript-Grenzen, Änderungen der Beta, Multi-Device-Transaktionen und Clientfreigaben. Öffentliches Herstellerrepo wird releaseweise aktualisiert, kein stetiger SDK-Support zugesagt.

## Empfohlene Architektur und spätere Freigabebedingungen

Für V1: eigenständiger Notification-/Handoff-Adapter mit Capability „Vorschau“, „bedingte Schnellantwort“ und „Original-App öffnen“. Keine vollständigen Conversation-, Kontakt- oder Sync-Fähigkeiten vortäuschen. Eine normale installierte Threema-App stellt Orbiliq nicht automatisch eine exportierte vollständige Messenger-API zur Verfügung.

Für spätere Forschung: unabhängigen AGPL-Companion auf Grundlage vorhandener Android-/Desktop-Multi-Device-Komponenten untersuchen, **erst** mit geklärtem Lizenz-/Server-/Gerätetyp und reproduzierbarem Android-Build. Kein Neuimplementieren der Kryptografie oder Umgehen von Lizenzprüfungen. Prüfen: Linking auf gleichem Gerät, Gerätenutzung bei ausgeschaltetem Primärgerät, History-Transfer, Gruppen, Revoke, Nonce-/Transaktionspersistenz bei Absturz und Doze ohne Play Services. Ohne diese Belege bleibt der Vollprovider zurückgestellt. Hier keine Konten-/Gerätetests ausgeführt.
