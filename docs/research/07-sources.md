# Quellenregister und Evidenz

**Abrufdatum: 27. Oktober 2026.** Primärquellen sind Herstellerseiten, offizielle API-Dokumentation, Maintainer-Repositories und Original-Lizenz-/Dependency-Dateien. Suchergebnisse dienten nur zum Auffinden; Drittartikel wurden nicht als entscheidende technische Belege verwendet.

## Evidenzstufen

- **Dokumentiert:** Hersteller/Projekt beschreibt Verhalten oder eine API ist im gelesenen Schema vorhanden.
- **Quellcode geprüft:** Ausgewählte Manifeste, Lizenztexte oder Implementierungsdateien wurden gelesen. Kein vollständiger Audit des Projekts.
- **Abgeleitet:** Android-/Architektur-/Risikoentscheidung dieses Berichts auf Basis dieser Belege.
- **Praktisch verifiziert:** In diesem Arbeitspaket keine Messenger-Funktion. Keine Accounts, Geräteverknüpfung, Nachrichten oder Builds getestet.

Quellen können einen Dienst oder ein Backend beschreiben, ohne dessen Android-Einbindung zu belegen. Die Featuretabellen beachten diese Grenze.

## Repository-Staende

Die folgende Tabelle ist aus dem GitHub-API-Abruf erzeugt. Commit-Datum ist das Datum des untersuchten Default-Branch-Heads, nicht notwendigerweise das letzte veröffentlichte Release. Ein nicht archiviertes oder kürzlich aktualisiertes Projekt ist nicht automatisch sicher oder Android-kompatibel. SPDX-Werte sind GitHub-Metadaten; die Bewertung in der [Lizenzanalyse](06-licenses-and-risks.md) berücksichtigt zusätzlich gelesene Lizenztexte.

| Repository | Untersuchte Revision | Commit-Datum (UTC) | Lizenz-Metadatum | Archiviert |
|---|---|---|---|---|
| [AsamK/signal-cli](https://github.com/AsamK/signal-cli) | [`49aaef000f54`](https://github.com/AsamK/signal-cli/tree/49aaef000f5459b9a95d145f2441091e9623476a) | 2026-10-24 | GPL-3.0 | nein |
| [signalapp/libsignal](https://github.com/signalapp/libsignal) | [`fc90b90fa2b9`](https://github.com/signalapp/libsignal/tree/fc90b90fa2b9c37822fa47ba0e0dcdf387a0c071) | 2026-10-23 | AGPL-3.0 | nein |
| [signalapp/Signal-Android](https://github.com/signalapp/Signal-Android) | [`6e9dddc01f1b`](https://github.com/signalapp/Signal-Android/tree/6e9dddc01f1b26e608ac683807c51e139c141ed2) | 2026-10-26 | AGPL-3.0 | nein |
| [tdlib/td](https://github.com/tdlib/td) | [`4c6377aa7386`](https://github.com/tdlib/td/tree/4c6377aa7386c39217878a9a56d581922ad37ca4) | 2026-10-27 | BSL-1.0 | nein |
| [TGX-Android/Telegram-X](https://github.com/TGX-Android/Telegram-X) | [`9aea5c143511`](https://github.com/TGX-Android/Telegram-X/tree/9aea5c14351165d99b8ef901e946e8bab99c079c) | 2026-10-25 | GPL-3.0 | nein |
| [DrKLO/Telegram](https://github.com/DrKLO/Telegram) | [`3de37c8cfb03`](https://github.com/DrKLO/Telegram/tree/3de37c8cfb039452be7398909461da6288babf15) | 2026-10-25 | GPL-2.0 | nein |
| [tulir/whatsmeow](https://github.com/tulir/whatsmeow) | [`1eec1136a50b`](https://github.com/tulir/whatsmeow/tree/1eec1136a50b2ef148b05f1819e77a9d989e0457) | 2026-10-27 | MPL-2.0 | nein |
| [WhiskeySockets/Baileys](https://github.com/WhiskeySockets/Baileys) | [`81345beb67ca`](https://github.com/WhiskeySockets/Baileys/tree/81345beb67ca3fb0eb79a2c20cd4ac24da517929) | 2026-10-26 | MIT | nein |
| [Auties00/Cobalt](https://github.com/Auties00/Cobalt) | [`cb9fbf172507`](https://github.com/Auties00/Cobalt/tree/cb9fbf172507626948fbc3611d0a6fb00bb3a0f1) | 2026-07-15 | MIT | nein |
| [pedroslopez/whatsapp-web.js](https://github.com/pedroslopez/whatsapp-web.js) | [`064a3d5a5a3d`](https://github.com/pedroslopez/whatsapp-web.js/tree/064a3d5a5a3dce1281a6a12740b5a7051339d154) | 2026-09-28 | Apache-2.0 | nein |
| [threema-ch/threema-android](https://github.com/threema-ch/threema-android) | [`d44a2b53b2ea`](https://github.com/threema-ch/threema-android/tree/d44a2b53b2ea2c0bf1065bdaa96987570e5b4e6d) | 2026-09-23 | AGPL-3.0 | nein |
| [threema-ch/threema-desktop](https://github.com/threema-ch/threema-desktop) | [`93a6615c5567`](https://github.com/threema-ch/threema-desktop/tree/93a6615c5567f0cf8371619dc1a5e888eed1d0b6) | 2026-09-14 | AGPL-3.0 | nein |
| [threema-ch/threema-web](https://github.com/threema-ch/threema-web) | [`f755cff075cb`](https://github.com/threema-ch/threema-web/tree/f755cff075cba8660bb51b7cf639679a482c5ed5) | 2026-10-25 | AGPL-3.0 | nein |
| [mautrix/signal](https://github.com/mautrix/signal) | [`6dc9ac3e0c9a`](https://github.com/mautrix/signal/tree/6dc9ac3e0c9a31a5ba59287091282d624e83c706) | 2026-10-27 | AGPL-3.0 | nein |
| [mautrix/whatsapp](https://github.com/mautrix/whatsapp) | [`021504afb01a`](https://github.com/mautrix/whatsapp/tree/021504afb01a5f117dd3ffa368f5a3afe8b19f51) | 2026-10-27 | AGPL-3.0 | nein |
| [Turasa/libsignal-service-java](https://github.com/Turasa/libsignal-service-java) | [`010f0a24776a`](https://github.com/Turasa/libsignal-service-java/tree/010f0a24776ad33c988f8f28dcceb97446f4420b) | 2026-10-24 | GPL-3.0 | nein |
| [mollyim/mollyim-android](https://github.com/mollyim/mollyim-android) | [`892d6264ba00`](https://github.com/mollyim/mollyim-android/tree/892d6264ba007b5458a8b74cf9183c665f1f17a1) | 2026-09-08 | AGPL-3.0 | nein |
| [tulir/libsignal-protocol-go](https://github.com/tulir/libsignal-protocol-go) | [`c231dd95d97e`](https://github.com/tulir/libsignal-protocol-go/tree/c231dd95d97ec136df1224fa5e4980a8bc39bb56) | 2026-05-29 | GPL-3.0 | nein |
| [WhiskeySockets/libsignal-node](https://github.com/WhiskeySockets/libsignal-node) | [`bcea72df9ec3`](https://github.com/WhiskeySockets/libsignal-node/tree/bcea72df9ec34d9d9140ab30619cf479c7c144c7) | 2026-05-13 | GPL-3.0 | nein |

Maschinenlesbar: [repository-snapshot.json](repository-snapshot.json). Die beobachteten Versionsstände sind kein späterer Dependency-Lock.

## Geprüfte Schlüsseldateien

- **AsamK/signal-cli:** [README.md](https://github.com/AsamK/signal-cli/blob/49aaef000f5459b9a95d145f2441091e9623476a/README.md), [LICENSE](https://github.com/AsamK/signal-cli/blob/49aaef000f5459b9a95d145f2441091e9623476a/LICENSE), [build.gradle.kts](https://github.com/AsamK/signal-cli/blob/49aaef000f5459b9a95d145f2441091e9623476a/build.gradle.kts), [man/signal-cli.1.adoc](https://github.com/AsamK/signal-cli/blob/49aaef000f5459b9a95d145f2441091e9623476a/man/signal-cli.1.adoc).
- **signalapp/libsignal:** [README.md](https://github.com/signalapp/libsignal/blob/fc90b90fa2b9c37822fa47ba0e0dcdf387a0c071/README.md), [LICENSE](https://github.com/signalapp/libsignal/blob/fc90b90fa2b9c37822fa47ba0e0dcdf387a0c071/LICENSE).
- **Turasa/libsignal-service-java:** [README.md](https://github.com/Turasa/libsignal-service-java/blob/010f0a24776ad33c988f8f28dcceb97446f4420b/README.md), [LICENSE](https://github.com/Turasa/libsignal-service-java/blob/010f0a24776ad33c988f8f28dcceb97446f4420b/LICENSE).
- **mollyim/mollyim-android:** [README.md](https://github.com/mollyim/mollyim-android/blob/892d6264ba007b5458a8b74cf9183c665f1f17a1/README.md), [LICENSE](https://github.com/mollyim/mollyim-android/blob/892d6264ba007b5458a8b74cf9183c665f1f17a1/LICENSE).
- **tdlib/td:** [LICENSE_1_0.txt](https://github.com/tdlib/td/blob/4c6377aa7386c39217878a9a56d581922ad37ca4/LICENSE_1_0.txt), [td/generate/scheme/td_api.tl](https://github.com/tdlib/td/blob/4c6377aa7386c39217878a9a56d581922ad37ca4/td/generate/scheme/td_api.tl).
- **tulir/whatsmeow:** [go.mod](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/go.mod), [LICENSE](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/LICENSE), [send.go](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/send.go), [newsletter.go](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/newsletter.go), [call.go](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/call.go), [store/sqlstore/container.go](https://github.com/tulir/whatsmeow/blob/1eec1136a50b2ef148b05f1819e77a9d989e0457/store/sqlstore/container.go).
- **tulir/libsignal-protocol-go:** [LICENSE](https://github.com/tulir/libsignal-protocol-go/blob/c231dd95d97ec136df1224fa5e4980a8bc39bb56/LICENSE).
- **WhiskeySockets/Baileys:** [package.json](https://github.com/WhiskeySockets/Baileys/blob/81345beb67ca3fb0eb79a2c20cd4ac24da517929/package.json), [LICENSE](https://github.com/WhiskeySockets/Baileys/blob/81345beb67ca3fb0eb79a2c20cd4ac24da517929/LICENSE).
- **WhiskeySockets/libsignal-node:** [package.json](https://github.com/WhiskeySockets/libsignal-node/blob/bcea72df9ec34d9d9140ab30619cf479c7c144c7/package.json), [LICENSE](https://github.com/WhiskeySockets/libsignal-node/blob/bcea72df9ec34d9d9140ab30619cf479c7c144c7/LICENSE).
- **Auties00/Cobalt:** [README.md](https://github.com/Auties00/Cobalt/blob/cb9fbf172507626948fbc3611d0a6fb00bb3a0f1/README.md), [pom.xml](https://github.com/Auties00/Cobalt/blob/cb9fbf172507626948fbc3611d0a6fb00bb3a0f1/pom.xml), [LICENSE](https://github.com/Auties00/Cobalt/blob/cb9fbf172507626948fbc3611d0a6fb00bb3a0f1/LICENSE).
- **threema-ch/threema-android:** [README.md](https://github.com/threema-ch/threema-android/blob/d44a2b53b2ea2c0bf1065bdaa96987570e5b4e6d/README.md), [domain/libthreema/lib/Cargo.toml](https://github.com/threema-ch/threema-android/blob/d44a2b53b2ea2c0bf1065bdaa96987570e5b4e6d/domain/libthreema/lib/Cargo.toml).
- **threema-ch/threema-desktop:** [README.md](https://github.com/threema-ch/threema-desktop/blob/93a6615c5567f0cf8371619dc1a5e888eed1d0b6/README.md), [packages/libthreema-wasm/libs/libthreema/lib/Cargo.toml](https://github.com/threema-ch/threema-desktop/blob/93a6615c5567f0cf8371619dc1a5e888eed1d0b6/packages/libthreema-wasm/libs/libthreema/lib/Cargo.toml), [packages/libthreema-wasm/libs/libthreema/threema-protocols/src/md.md](https://github.com/threema-ch/threema-desktop/blob/93a6615c5567f0cf8371619dc1a5e888eed1d0b6/packages/libthreema-wasm/libs/libthreema/threema-protocols/src/md.md).

Zusätzlich für die Call-Abgrenzung anhand README und LICENSE geprüft:

- **TelegramMessenger/tgcalls:** [README.md](https://github.com/TelegramMessenger/tgcalls/blob/3a85cd9bfd279ca8aa6f2579e1d2958c75ad03e1/README.md), [LICENSE](https://github.com/TelegramMessenger/tgcalls/blob/3a85cd9bfd279ca8aa6f2579e1d2958c75ad03e1/LICENSE).
- **signalapp/ringrtc:** [README.md](https://github.com/signalapp/ringrtc/blob/84c5b2aff246392b365d73d6628529a149d6aed2/README.md), [LICENSE](https://github.com/signalapp/ringrtc/blob/84c5b2aff246392b365d73d6628529a149d6aed2/LICENSE).

[Source-artifacts.json](source-artifacts.json) enthält weitere gelesene Dateien mit SHA-256 der abgerufenen Bytes. Es wurden ausgewählte Dateien, keine kompletten Repositories, reproduzierbar erfasst.

Zusätzlich konkret geprüfte transitive Versionsarchive:

- [go.mau.fi/libsignal v0.2.2, Go-Proxy-Archiv](https://proxy.golang.org/go.mau.fi/libsignal/@v/v0.2.2.zip): LICENSE im Archiv GPLv3; von whatsmeow in `go.mod` verlangt.
- [libsignal 6.0.0, Original-npm-Paket](https://registry.npmjs.org/libsignal/-/libsignal-6.0.0.tgz): LICENSE im Archiv GPLv3; Baileys verlangt den Bereich `^6.0.0`. [npm-Metadaten der Version](https://registry.npmjs.org/libsignal/6.0.0).

Archive wurden im Speicher gelesen, nicht installiert oder ausgeführt. Hashes und interne Lizenzpfade stehen im JSON. Weder vollständige SBOM noch Prüfung aller transitiven Abhängigkeiten oder kryptografischer Herkunftssignaturen.

## Hersteller-/Plattformquellen nach Aussage

| Quelle | Verwendete Aussage | Grenze |
|---|---|---|
| [Signal Technical Documentation](https://signal.org/docs/) | Kryptografie-Spezifikationen, keine fertige Service-API | Keine eigene Kryptografie herleiten |
| [Signal Linked Devices](https://support.signal.org/hc/en-us/articles/360007320551-Linked-Devices) | Aktuelle Gerätetypen, Transfer, Limits und Entkopplung | Kein Beleg für dieselbe Funktion in jedem Fremdclient |
| [Signal Terms & Privacy](https://signal.org/legal/) | Registrierung, missbräuchliche Nutzung, Konto-/Vertrauensgrenzen | Ältere Telefonnummer-Formulierungen nicht als vollständige aktuelle Featurebeschreibung behandeln |
| [mautrix-Signal-Authentifizierung](https://docs.mau.fi/bridges/go/signal/authentication.html) | Linking und optionaler History-Backfill | Belegt Bridge, keinen Android-Port |
| [Telegram APIs](https://core.telegram.org/api) | Client-API/TDLib versus Bot/Gateway | API-Existenz ist keine fertige Orbiliq-Funktion |
| [TDLib Getting Started](https://core.telegram.org/tdlib/getting-started) | Chat-/Update-/Datei-/Secret-Chat-Modell | UI und Android-Lifecycle bleiben App-Aufgabe |
| [TDLib API Reference](https://core.telegram.org/tdlib/docs/td__api_8h.html) | Überblick verfügbarer Methoden/Typen | Fester Quellcode-Snapshot für genaue API maßgeblich |
| [TDLib Android-Buildbeispiel](https://github.com/tdlib/td/blob/master/example/android/README.md) | Konkreter NDK-/JNI-Buildpfad | Kein selbst ausgeführter Build |
| [Telegram API-ID](https://core.telegram.org/api/obtaining_api_id) | Eigene Kennung, Drittclient-Beobachtung, Missbrauchssperren | Keine individuelle Accountfreigabe |
| [Telegram API Terms](https://core.telegram.org/api/terms) | Grundfunktionen, Branding, Transparenz, Sponsored Messages | Vor Release erneut prüfen |
| [Telegram User Authorization](https://core.telegram.org/api/auth) | Serverabhängige Loginverfahren, SMS-/Drittclientgrenzen | Nicht jeden dokumentierten Weg jedem Account zusagen |
| [Telegram Firebase SMS](https://core.telegram.org/method/auth.requestFirebaseSms) | Integrity-/Firebase-spezifischer Pfad | Kein Google-Zwang für gesamten TDLib-Verkehr |
| [Telegram E2E / Secret Chats](https://core.telegram.org/api/end-to-end) | E2EE-Schicht gegenüber Cloud-Chats | Kein E2EE für normale Gruppen/Channels behaupten |
| [TDLib Notification API](https://core.telegram.org/tdlib/notification-api) | Notificationupdates, Push-Verarbeitung | Kein Betriebssystem-Wakeup ohne passenden Kanal |
| [Meta: Cloud API](https://about.fb.com/news/2022/05/announcing-new-products-to-make-business-messaging-easier/) | Offizielle Business-Cloud, von Meta gehostet | Keine private Kontenintegration |
| [Meta-eigene Postman-Dokumentation](https://www.postman.com/meta/whatsapp-business-platform/documentation/wlk6lh4/whatsapp-cloud-api) | Business-Voraussetzungen und API-Modell | Fallback für eingeschränkten developers.facebook.com-Abruf |
| [Meta: WhatsApp-Interoperabilität](https://about.fb.com/news/2025/11/messaging-interoperability-whatsapp-enables-third-party-chats-for-users-in-europe/) | Dienstübergreifende Chats, europäisches Opt-in | Keine API zum Login in fremde/bestehende WhatsApp-Konten |
| [WhatsApp Terms](https://www.whatsapp.com/legal/terms-of-service) | Unerlaubter Zugang/Reverse Engineering, Accountmaßnahmen | Rechtsanwendbarkeit und Kontoentscheidung gesondert |
| [Threema Open Source](https://threema.com/en/why-threema/open-source) | AGPL, kostenpflichtige Dienstnutzung, Self-build-Grenzen | Aktuelle Buildvarianten zusätzlich im Repo geprüft |
| [Threema Desktop-Multi-Device-FAQ](https://threema.com/en/faq/md-overview) | Unabhängiger Desktop, Android-/iOS-Versionen und Computerlimit | Beta, kein öffentliches Android-Drittclient-SDK |
| [Threema Android-Desktop-Ankündigung](https://threema.com/en/blog/android-new-desktop-beta) | Android-Linking seit 22.04.2025 | Historischer Beginn, nicht heutiger Vollfunktionsumfang |
| [Threema Private Support](https://threema.com/en/support/private) | Web/Telefon-Abhängigkeit, Identitäts-/Backupgrenzen | Mehrere Generationen im Supporttext unterscheiden |
| [Threema Libre](https://threema.com/en/faq/libre-installation) | Offizielle Google-freie Variante, Shop-Lizenz | Kein allgemeiner lizenzfreier Serverzugang |
| [Threema Push](https://threema.com/en/faq/threema-push) | Google-freier Empfang, Hintergrundfreigaben | Kein beliebiges Push-SDK für Orbiliq |
| [Threema Ibex](https://threema.com/en/blog/security-proof-ibex) | Eigenes E2E-/Forward-Secrecy-Protokoll | Keine eigene Kryptografieprüfung durchgeführt |
| [Threema Gateway API](https://gateway.threema.ch/en/developer/api) | Basic/E2E, Empfangs-Callbacks, SDKs | Kein persönlicher Account-Client |
| [Threema Gateway Produkt](https://threema.com/en/products/work/gateway) | Separate Gateway-ID | Kein Zugriff auf persönliches Altpostfach |
| [Android Doze](https://developer.android.com/training/monitoring-device-state/doze-standby) | Netz-/Jobgrenzen und Ausnahmen | Geräteverhalten nicht gemessen |
| [Android FGS-Timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout) | `dataSync`-Budget bei Target Android 15+ | Nicht auf alle Servicearten pauschalisieren |
| [Android FGS-Typen](https://developer.android.com/develop/background-work/services/fgs/service-types) | Passender Diensttyp und Voraussetzungen | Kein willkürlicher Typwechsel zur Umgehung |
| [Android NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) | Benachrichtigungszugriff und Plattformgrenzen | Keine Messenger-Datenbank-API |
| [Android Direct Reply](https://developer.android.com/develop/ui/compose/notifications/create-notification#reply-action) | RemoteInput/Reply-Action | Keine bestätigte Zustellung an Empfänger |
| [MPL-2.0](https://www.mozilla.org/en-US/MPL/2.0/), [FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/) | Dateibezogene Pflichten und größere Werke | Hebt GPL-Abhängigkeiten nicht auf |

## Nicht vollständig zugängliche oder nicht ausgewertete Quellen

- `developers.facebook.com/docs/whatsapp/cloud-api/overview`: Webabruf 429; stattdessen Meta-eigene Postman-Sammlung und Meta-Ankündigung genutzt. Aktuelle Detailfeatures/Preise der Cloud API und Coexistence wurden nicht abschließend verifiziert; sie sind keine Entscheidungsgrundlage für den Consumer-Provider.
- WhatsApp Help Center, [Geräteverknüpfung](https://faq.whatsapp.com/1317564962315842/) und [inoffizielle Apps](https://faq.whatsapp.com/1217634902127718): Seiten lieferten im Webtool keinen auslesbaren Inhalt. Daher keine aktuellen numerischen Limits daraus behauptet.
- WhatsApp Security Whitepaper: Abruf scheiterte beim CDN-Redirect. Keine eigene Aussage über konkrete aktuelle Kryptografieversion oder formalen Sicherheitsnachweis daraus abgeleitet.
- GNU-FAQ und AGPL-Webseite: Abrufe liefen in Timeouts. Vollständige GPL-/AGPL-Texte aus den tatsächlich geprüften Repository-LICENSE-Dateien gelesen.
- Threema: vermutete eigenständige öffentliche Repositories `threema-ch/libthreema` und `threema-ch/threema-protocols` waren 404. Stattdessen eingebettete Quellen im offiziellen Android-/Desktop-Snapshot gefunden und geprüft. Ein 404 beweist keine allgemeine Nichtveröffentlichung.
- Threema Crypto-Whitepaper wird als vom Hersteller referenzierte weiterführende Dokumentation genannt, hier nicht vollständig ausgewertet. Der konkrete Multi-Device- und Lizenzbefund stammt aus den tatsächlich gelesenen Repo-Dateien.
- Eine vermutete Threema-FAQ-Adresse zu Beta-Limitierungen war nicht zugänglich. Deshalb kein erfundener vollständiger aktueller Beta-Featurekatalog.

Die offenen Zugriffe sind keine fehlenden Integrationsentscheidungen: Die tragenden Unterschiede (persönlicher Client versus Business/Gateway, tatsächliche Dependency-Lizenzen, Android-Portierungsbedarf und OS-Grenzen) sind mit den übrigen Primärquellen belegt. Spätere produktive Freigabe benötigt aktuellere Detail- und Praxiskontrollen.

Die abschließende erneute GitHub-Metadatenabfrage für Call-Projekte wurde mit HTTP 403 (API-Rate-Limit) abgewiesen. Die zuvor ermittelten festen Revisionen und direkt gelesenen Rohdateien sind im Dateinachweis erfasst; keine erneute vollständige Metadatenprüfung behauptet.
