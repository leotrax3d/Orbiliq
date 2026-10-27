# Lizenzen, Dienstzugang und Risiken

**Stand: 27.10.2026. Technische Lizenzvorprüfung; keine abschließende Freigabe eines noch nicht existierenden Distributionspakets.**

## Was „MIT-kompatibel“ für Orbiliq bedeutet

Eigener, unabhängig geschriebener Code kann unter MIT stehen. Fremder Code behält seine Lizenz. MIT kann grundsätzlich Bestandteil eines GPL-/AGPL-Gesamtwerks sein; umgekehrt wird GPL-/AGPL-Code nicht durch einen MIT-Adapter permissiv. „MIT und GPL sind inkompatibel“ wäre zu pauschal. Das konkrete Problem ist die gewünschte **ausschließlich permissive Distribution der Kombination**, nicht die Möglichkeit, eigene Dateien unter MIT zu veröffentlichen.

GPL verlangt bei Verbreitung betroffener kombinierter Werke insbesondere entsprechende Lizenz- und Quellcodebereitstellung. AGPL enthält zusätzliche Pflichten für modifizierte Programme bei Interaktion über ein Netzwerk. Ob eine konkrete IPC-Kombination ein einheitliches Werk oder unabhängige Programme bildet, ist nicht allein an Prozess, APK oder Socket abzulesen. Das ist vor einer Distribution zu beurteilen. Die hier geprüften vollständigen Lizenztexte stehen beispielsweise bei [signal-cli/GPLv3](https://github.com/AsamK/signal-cli/blob/49aaef000f5459b9a95d145f2441091e9623476a/LICENSE) und [Signal/libsignal/AGPLv3](https://github.com/signalapp/libsignal/blob/fc90b90fa2b9c37822fa47ba0e0dcdf387a0c071/LICENSE). GNU-Webseiten waren beim Abruf teilweise nicht erreichbar; die Repository-Lizenztexte wurden direkt gelesen.

MPL ist grundsätzlich dateibezogenes Copyleft und erlaubt Kombinationen mit anders lizenzierten eigenen Dateien unter ihren Bedingungen. Betroffene Dateien und Änderungen bleiben MPL, Source-/Hinweispflichten gelten. Diese Aussage entscheidet aber nicht über **zusätzliche GPL-Abhängigkeiten**. [Mozilla-Lizenztext](https://www.mozilla.org/en-US/MPL/2.0/), [Mozilla-FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/).

## Konkrete Komponentenprüfung

Die Tabellen nennen beobachtete Lizenztexte/Metadaten. „GPLv3“ oder „AGPLv3“ legt keine nicht geprüfte `-only`-/`-or-later`-Variante für jede Einzeldatei fest. GitHub-SPDX-Erkennung ist ein Hinweis; Lizenzheader, Ausnahmen und der exakte Abhängigkeitsgraph sind vor Nutzung maßgeblich.

| Komponente | Geprüfter Befund | Eigener MIT-Kern / Distribution |
|---|---|---|
| TDLib | Boost Software License 1.0 in `LICENSE_1_0.txt` | Direkte Integration grundsätzlich geeignet, Third-party-Notices und konkrete native Abhängigkeiten prüfen |
| Telegram Android / Telegram X | GPLv2- bzw. GPLv3-Lizenzdatei | Nicht als permissive Codequelle behandeln; TDLib getrennt verwenden |
| Signal Android / libsignal | AGPLv3-Lizenzdateien | Keine MIT-only-Gesamtdistribution durch bloßes Linking |
| Molly | README nennt AGPL-3.0-only, LICENSE vorhanden | Wie Signal; eigenständiger Companion als zu prüfende Option |
| signal-cli | GPLv3 im Hauptprojekt, native und Serviceabhängigkeiten | GPL-Hauptlizenz allein ist keine vollständige Paketfreigabe; AGPL-Teile mitprüfen |
| Turasa/libsignal-service-java | LICENSE/GitHub GPLv3, README nennt AGPLv3 | **Widerspruch offen**; Dateirechte und gewähltes Artefakt klären, nicht günstigere Angabe auswählen |
| whatsmeow | MPL-2.0; `go.mod` enthält `go.mau.fi/libsignal v0.2.2` | MPL-Anteil kombinierbar; konkrete GPL-Abhängigkeit verhindert pauschale permissive Gesamtfreigabe |
| `go.mau.fi/libsignal v0.2.2` | LICENSE im tatsächlich geladenen Versionsarchiv GPLv3 | Verbreitungs-/Kombinationspflichten gesondert relevant |
| Baileys | MIT-Hauptprojekt; `package.json` enthält `libsignal ^6.0.0` | MIT-Schild am Hauptrepository reicht nicht |
| `libsignal 6.0.0` (WhiskeySockets) | npm-Paket-LICENSE und package.json GPLv3 | Konkreter Copyleft-Befund; spätere aufgelöste Version erneut prüfen |
| Cobalt | MIT-Hauptlizenz; Java-25-/Native-Stack | Grundsätzlich permissive Hauptlizenz; kein vollständig auditierter Medien-/Abhängigkeitsgraph und kein belegter Android-Port |
| whatsapp-web.js | Apache-2.0-Hauptprojekt | Mit MIT-Eigenanteilen grundsätzlich kombinierbar, Lizenz-/Notice- und Browser-/Webclient-Bedingungen bleiben |
| mautrix-signal / mautrix-whatsapp | AGPLv3 | Bridge-/Servicepflichten und weitere Abhängigkeiten; nicht als MIT-Android-SDK übernehmen |
| Threema Android / Desktop / Web | AGPLv3 | Kopieren/Linking nicht unter ausschließlich MIT-Bedingungen |
| Threema `libthreema` | Beide geprüften Rust-Package-Manifeste nennen AGPL-3.0-only | Kein permissiver Ausweg aus den Clientlizenzen |
| Telegram tgcalls | README/ LICENSE LGPLv3 | Separater Call-Stack mit LGPL-Kombinations-/Relinking-Pflichten; nicht Teil der TDLib-Boost-Freigabe |
| Signal RingRTC | AGPLv3-Lizenzdatei, WebRTC-Middleware | Zusätzlicher Copyleft-/Native-Stack; nicht durch Messaging-Freigabe abgedeckt |
| Threema-Gateway-SDKs | Mehrere offizielle SDKs vorhanden, für Produktziel verworfen | Keine pauschale Lizenzfreigabe aller SDKs behauptet; Auswahl wäre eigenes Arbeitspaket |

Primärbelege sind die festen [Repository-Links](07-sources.md#repository-staende) und [Datei-/Versionsarchivnachweise](source-artifacts.json). Das Go-Versionsarchiv und das npm-Paket wurden lediglich gelesen, nicht installiert oder ausgeführt. Ihre Hashes identifizieren den abgerufenen Inhalt; keine Signatur- oder Supply-Chain-Zertifizierung.

## Lizenzverträgliche Produktoptionen

1. **MIT-Kern plus permissive direkte Bibliotheken:** Telegram/TDLib ist der klare Start. Eigenen Adaptercode MIT halten, fremde Texte/Notices unverändert beilegen. Auch hier ist „alles MIT“ sachlich falsch, weil TDLib Boost bleibt.
2. **MIT-Eigenanteile plus vollständig copyleftkonforme Gesamtvariante:** Technisch denkbar, widerspricht aber einer ausschließlich permissiven Gesamtveröffentlichung. Die Produktlizenz wird durch diesen Bericht nicht geändert.
3. **Unabhängige optionale Companion-Programme:** Eigene Installierbarkeit, Datenhoheit, eigenes Release, eigene Quellcode-/Lizenzpflichten, generische dokumentierte IPC. Diese Merkmale unterstützen eine Abgrenzung, garantieren sie nicht. Keine automatische Rechtsfreigabe durch Binder/HTTP.
4. **Zusätzliche Erlaubnis/Duallizenz:** Nur bei tatsächlich erteilter Erlaubnis aller erforderlichen Rechteinhaber. Nicht als verfügbar voraussetzen; eine Maintainer-Zusage deckt nicht automatisch fremde Beiträge ab.
5. **Begrenzte Android-Integration mit Original-App:** Selbst geschriebener Notification-/Intent-Adapter braucht keinen kopierten Messenger-Clientcode; funktional kein Ersatz für einen vollständigen Client.

Es gibt keinen sinnvollen Auftrag, Ratchets, Gerätesync und Protokollparser neu zu schreiben, nur um eine Lizenzprüfung zu vermeiden. Das wäre ein erhebliches Sicherheits- und Wartungsprojekt und erfüllt nicht den Wunsch nach Wiederverwendung.

## Dienstbedingungen und Reverse Engineering

FOSS-Lizenz, Markenrechte, API-Nutzungsvertrag und technische Kontozulassung sind vier unterschiedliche Ebenen. Eine Nutzungsbedingung ist ihrerseits keine abschließende Aussage über gesetzliche Rechte; dieser Bericht erteilt weder eine pauschale Rechtswidrigkeitsbehauptung noch eine Interoperabilitätsfreigabe.

| Messenger | Primärgrundlage | Abgeleitetes Risiko und Konsequenz |
|---|---|---|
| Telegram | [API-Bedingungen](https://core.telegram.org/api/terms), [Registrierung](https://core.telegram.org/api/obtaining_api_id) | Drittclients ausdrücklich vorgesehen, dennoch Beobachtung, Flood-/Missbrauchssperren und API-Entzug möglich. Channels benötigen Sponsored Messages; Produktkonflikt vor Release lösen |
| Signal | [Bedingungen](https://signal.org/legal/), [libsignal-Hinweise](https://github.com/signalapp/libsignal) | Keine stabile Fremdclient-Schnittstelle zugesagt; unerlaubte Registrierung/Serviceausbeutung untersagt. Linken statt Registrierungsautomatisierung, kurzfristige Updates nötig |
| WhatsApp | [Bedingungen](https://www.whatsapp.com/legal/terms-of-service), [Baileys-Disclaimer](https://github.com/WhiskeySockets/Baileys) | Inoffizieller Consumer-Zugang und Reverse Engineering mit erheblichem Vertrags-/Sperrrisiko. Opt-in allein macht Nutzung nicht autorisiert; keine Ban-Garantie |
| Threema | [Open Source](https://threema.com/en/why-threema/open-source), [Client-Lizenzprüfungen](https://github.com/threema-ch/threema-android/blob/d44a2b53b2ea2c0bf1065bdaa96987570e5b4e6d/README.md) | Bezahlte Servernutzung/ID-Erstellung und Gerätefreigabe separat. Kein Umgehen von Lizenzchecks, kein vorausgesetzter Android-Zweitclient-Zugang |

Sperrwahrscheinlichkeiten lassen sich aus öffentlich sichtbaren Issuezahlen nicht seriös ableiten. „Hoch“ bezeichnet hier die ungünstige Zugangslage und fehlende Zusicherung, keine gemessene Prozentzahl. Eine Account-Sperre kann die gesamte Kommunikation eines Nutzers beeinträchtigen und ist gravierender als ein App-Absturz.

## Risikoregister

| Risiko | Eintritt / Auswirkung | Maßnahme / Abbruchbedingung |
|---|---|---|
| Ungeprüfte Copyleft-Abhängigkeit | Bereits konkret bei beiden WhatsApp-Kandidaten gefunden / hoch | Exakte Versionen und ganze Distribution prüfen; kein Produktions-Embedding ohne erfüllte Pflichten |
| WhatsApp-Sperrung oder blockiertes Linking | Nicht quantifizierbar / sehr hoch | Nur optionaler experimenteller Modus; keine Umgehung, keine Massenregistrierung; bei wiederholten Problemen Ansatz stoppen |
| Signal-/Threema-Serviceänderung | Erwartbar / hoch | Upstream-Beobachtung als reale Wartungsaufgabe; deaktivierbare Capabilities, keine stillen Fehlzustände |
| Android suspendiert Empfang | Erwartbar / hoch | Doze-/OEM-Messung, ehrlicher Empfangsstatus, Catch-up; keine universelle Echtzeitzusage |
| Sessionverlust oder doppelter Versand nach Crash | Möglich / hoch | Transaktionen, Journal, Provider-IDs, Status „Ergebnis unbekannt“, kein blindes Replay |
| E2EE durch Bridge missverständlich dargestellt | Möglich / sehr hoch | Endpoint-/Servergrenze zeigen; keine zentrale Bridge als unbemerkte Voreinstellung |
| Klartext in Cache, Suche oder Logs | Möglich / sehr hoch | Sämtliche Projektionen schützen; keine Secrets/QRs/OTP im Log; Ablauffristen auch im Index |
| Falsche Kontaktzusammenführung | Möglich / hoch | Dienstidentitäten beibehalten; lokale, korrigierbare Zuordnung; explizite Versandroute |
| Featurematrix wird als Testbericht verstanden | Möglich / hoch | Reifegrad, Backendbeleg und reale Gerätevalidierung getrennt ausweisen |
| Wartungsbudget übersteigt Projektkapazität | Bei vier Backends hoch / hoch | Stufenweise Integration; Telegram zuerst; Provider notfalls als Ergänzung belassen |

## Relativer Wartungsbedarf

| Weg | Initialaufwand | Laufende Wartung | Haupttreiber |
|---|---|---|---|
| Telegram/TDLib | Mittel | Mittel | Native Builds, Schema, Auth-/UI-Semantik |
| Signal/Android-Companion | Hoch | Hoch | Forkpflege, Service-/Kryptografie-/Syncänderungen, IPC |
| WhatsApp/whatsmeow-Companion | Hoch | Sehr hoch | Android-Go-Port, nicht zugesicherter Webzugang, App-State, Sperren |
| WhatsApp/Baileys lokal | Sehr hoch | Sehr hoch | Zusätzliche Runtime plus dieselben Protokollrisiken |
| Threema/Multi-Device-Port | Sehr hoch | Sehr hoch | Voller Clientzustand, Beta-Protokoll, Android-Gerätetyp und Lizenz |
| Notification-/Handoff-Adapter | Niedrig bis mittel | Mittel | App-/OS-Notificationänderungen, Redaktionen und unvollständige Daten |

Die Werte sind begründete relative Einschätzungen, keine belastbaren Personenwochen-Schätzungen ohne Team-/Account-/Gerätenachweis. Kritische Upstream-Änderungen können kurzfristige Releases erfordern; eine jährliche Library-Aktualisierung genügt für inoffizielle Provider nicht.
