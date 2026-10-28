# Signal: erneute Prüfung für Prompt 11

Stand: 27.10.2026. **Recherche aktualisiert, Implementierungsauftrag nicht abgeschlossen.**

## Nachprüfung am 28.10.2026

Erneute `git ls-remote ... HEAD`-Abfragen: Signal Android bleibt auf
`6e9dddc01f1b26e608ac683807c51e139c141ed2`, Molly auf
`892d6264ba007b5458a8b74cf9183c665f1f17a1`. Dagegen steht
[signal-cli](https://github.com/AsamK/signal-cli/tree/1e878f38457d22adb55d9b08b7f58543fdb92318)
jetzt auf `1e878f38457d22adb55d9b08b7f58543fdb92318` und
[libsignal](https://github.com/signalapp/libsignal/tree/904c8dc8216073b078a5f2f5090e3da8382e6a92)
auf `904c8dc8216073b078a5f2f5090e3da8382e6a92`.
Der [libsignal-Vergleich](https://github.com/signalapp/libsignal/compare/fc90b90fa2b9c37822fa47ba0e0dcdf387a0c071...904c8dc8216073b078a5f2f5090e3da8382e6a92)
enthält sieben neue Commits, unter anderem Swift-Package-Unterstützung und ein Update
auf boring 5.3.0. Daraus folgt keine automatisch getestete Android-Kompatibilität.
Der festgelegte Android-Client verwendet weiterhin seine eigene libsignal-Version
0.105.4; Kryptobibliotheken werden nicht unabhängig vom Client ausgetauscht.

Die erneut gelesene aktuelle signal-cli-README verlangt weiterhin JRE 25 und native
libsignal-client-Bibliotheken. libsignal bezeichnet Fremdnutzung weiterhin als
unsupported. Kein neuer belegter Android-SDK- oder öffentlicher Vollclient-API-Weg
ersetzt damit die gewählte Companion-Strategie.
Die [Signal-Gerätehilfe](https://support.signal.org/hc/en-us/articles/360007320551-Linked-Devices)
bestätigt weiterhin Android-Linking, optionalen Chattransfer mit 45 Tagen Medien,
maximal fünf Linked Devices und die bereits dokumentierten Inaktivitätsbedingungen.

Codeprüfung fand zusätzliche lokale Navigationswege zur Primärregistrierung:
asynchrone Wiederherstellung, Start-fresh und NavigateBackToScreen. Diese werden
jetzt zusätzlich zur vorhandenen HTTP-Sperre gesperrt. Sieben credentialfreie
Generator-/Konsistenztests bestehen; das ist kein Linking- oder Accountnachweis.
Der alte CI-Lauf 40668223010 ist erfolgreich, enthält diese neuen Sperren aber noch
nicht. Weitere Nachweise siehe Buildvalidierung und SIGNAL_HANDOFF.

**Nachtrag zur anschließenden Fortsetzung:** Der unten beschriebene fehlende Companion
war der Stand vor der Implementierung. Inzwischen sind ein pinned Clientgenerator,
Einrichtungs-/Zustands-IPC und ein Orbiliq-Setup-Einstieg implementiert. Nachrichtenintegration
und reale Accountabnahme fehlen weiterhin. Aktueller Codeumfang:
[Companion-Leitfaden](../../integrations/signal-companion/README.md),
[ADR 0015](../adr/0015-signal-companion-linking-milestone.md) und
[Buildvalidierung](../build-validation.md). Die folgenden Befunde dokumentieren die
vorausgehende Recherche; ihre damaligen Aussagen „kein Code/keine Tests“ sind kein
aktueller Implementierungsstatus.

Diese Prüfung ersetzt keinen Companion, keinen Android-Build des Upstreams und keinen
Interoperabilitätstest. Die Definition of Done eines mit echtem Konto nutzbaren Providers
ist nicht erreicht. Es wurde kein Signal-Provider registriert und keine Signal-Capability
als verfügbar hinzugefügt.

## Gegenprüfung der bisherigen Entscheidung

Die erneut über GitHub `commits/main` abgefragten Revisionen stimmen mit der bisherigen
Recherche überein:

| Projekt | Erneut beobachtete Revision |
|---|---|
| Signal Android | `6e9dddc01f1b26e608ac683807c51e139c141ed2` |
| Molly Android | `892d6264ba007b5458a8b74cf9183c665f1f17a1` |
| libsignal | `fc90b90fa2b9c37822fa47ba0e0dcdf387a0c071` |

Es gibt damit bei diesen drei Branchständen keine neu beobachtete Änderung gegenüber
der ersten Recherche. Das beweist keine unveränderte Serverkonfiguration, keine
Kompatibilität sämtlicher Releasevarianten und keine vollständige Prüfung aller Tags.
Abgerufene Dateien, feste URLs, SHA-256 und der fehlgeschlagene Abruf von `BUILDING.md`
sind im [Abrufnachweis](signal-revalidation-2026-10-27.json) verzeichnet.

Die [aktuelle Signal-Gerätehilfe](https://support.signal.org/hc/en-us/articles/360007320551-Linked-Devices)
beschreibt weiterhin Android als verknüpfbares Gerät, einen QR-Scan durch das Primärgerät
und optionalen Historientransfer. Die bereits dokumentierte Wahl eines separaten lokalen
Companions bleibt technisch plausibel. Ein Smartphone kann seinen eigenen Bildschirm
nicht mit seiner Kamera scannen; eine sichere Lösung für diesen Bedienweg ist weiterhin
nicht in Orbiliq implementiert.

Die erneut gelesene [signal-cli-README](https://github.com/AsamK/signal-cli/blob/49aaef000f5459b9a95d145f2441091e9623476a/README.md)
fordert weiterhin JRE 25 und native libsignal-Bibliotheken. Ein CLI-/Serveradapter wäre
ein anderer Integrationsweg und ist kein Ersatznachweis für den gewählten Android-Companion.
Das [libsignal-README](https://github.com/signalapp/libsignal/blob/fc90b90fa2b9c37822fa47ba0e0dcdf387a0c071/README.md)
bezeichnet Fremdnutzung weiter als unsupported. Molly dokumentiert weiterhin den
zusätzlichen MollySocket-Bedarf und fehlendes UnifiedPush für Linked Devices.

## Konkret geprüfte Android-Einstiegspunkte

Alle folgenden Pfade beziehen sich auf die oben genannte feste Signal-Android-Revision:

| Datei | Befund und Konsequenz |
|---|---|
| `feature/registration/.../RegistrationRepository.kt` | `startLinkDeviceProvisioning()` und Verarbeitung von Linked-Device-Provisioning vorhanden; Historientransfer wird mit `allowLinkAndSync = isCleanStart()` eingeschränkt. Nicht jede erneute Bindung darf als vollständiger Historienimport gelten. |
| `app/.../registration/data/RegistrationRepository.kt` | Registrierung als Sekundärgerät über `registerAsSecondaryDevice`, Prekeys für ACI/PNI und danach weiterer Accountzustand. Ein QR-String allein erzeugt keine einsatzfähige Session. |
| `lib/libsignal-service/.../provisioning/ProvisioningSocket.kt` | Provisioning-WebSocket, eigene Cipher-Verarbeitung, Ablauf und Abbruch; der geprüfte Socket hat eine Lebensdauer von 90 Sekunden. Provisioning muss im Companion verbleiben. |
| `app/build.gradle.kts` | `LINK_DEVICE_UX_ENABLED` standardmäßig `false`, im Debug-Build auf `true` gesetzt. Die Supportseite ist deshalb kein Beleg dafür, dass jede aus diesem SHA gebaute Releasevariante das gewünschte UI freischaltet. Weitere Konfiguration und Laufzeitverhalten wurden nicht vollständig geprüft. |
| `app/.../sms/MessageSender.java` | Versand hängt unter anderem an SignalDatabase, SignalStore und JobManager. Kein unabhängiger Textversand-SDK-Einstieg ohne den Clientzustand. |
| `app/src/main/AndroidManifest.xml` | In den geprüften Komponenten kein Orbiliq-Providerdienst gefunden. Beispielsweise RemoteReplyReceiver ist nicht exportiert. Shares/Notifications belegen keinen authentifizierten Zugriff auf Session und vollständige Nachrichtenereignisse. Ein vollständiger Audit aller zusammengeführten Manifeste ist das nicht. |

Quelltextlinks zu jeder Datei stehen im Abrufnachweis. Diese Befunde sind offene
Implementierungsarbeit, **kein Nachweis grundsätzlicher Unmöglichkeit**.

## Noch zu implementierende vertikale Integration

Die bisherige bedingte Strategie setzt eine neue Companion-Anwendung auf einer vorhandenen
Android-Clientbasis voraus. Im Orbiliq-Repository existieren weder dieser Fork noch seine
IPC-Implementierung. Ein ausschließlich gegen einen Testdouble geschriebener Provider
würde diese Abhängigkeit verdecken und die gewünschte echte Accountintegration nicht liefern.

1. Signal Android als primäre Basis verwenden, Revision und Updates nachvollziehbar halten;
   eigener Package-/Authority-Namensraum, eigener privater Store und separat installierbares
   APK. Bestehende Signal-Installation und deren Datenbank nicht auslesen oder ersetzen.
   AGPL-/Quellcode-/Notice-Pflichten und die konkrete Distributionsgrenze vor Veröffentlichung
   prüfen; dieser Bericht erteilt keine rechtliche Freigabe.
2. Linking zunächst im Companion mit dem vorhandenen Sekundärgeräteflow ausführen;
   Abbruch, Timeout, Rate-Limit, Device-Limit und widerrufene Session korrekt abbilden.
   Keine Neuregistrierung des persönlichen Primärkontos als automatischen Fallback.
3. Lokale IPC implementieren: explizite Gegenstelle, authentifizierte App-Identität,
   nutzerbestätigte Bindung und serverseitige Prüfung jedes Aufrufers. Paketname oder
   vom Client behauptete UID allein reichen nicht. Prozessneustart, App-Update, Widerruf
   und Austausch des Signaturzertifikats müssen die Bindung sicher behandeln.
4. Empfang im Companion dauerhaft journalisieren; Replay erst nach atomarem Commit der
   Orbiliq-Projektion quittieren. IPC-Verbindungsabbruch darf keine Nachricht still verlieren.
   Cursor und Epochwechsel müssen kontogebunden sein. Eigene ausgehende Sync-Nachrichten
   sind ebenso erforderlich wie eingehende Texte und Kontakt-/Gruppenzustände.
5. Textversand an den vorhandenen dauerhaften Versandpfad des Companions anbinden;
   Command-ID und Ergebnis dauerhaft zuordnen. Nach Crash ohne nachweisbares Ergebnis
   `OutcomeUnknown` und Reconciliation verwenden. Eine IPC-Annahme ist keine Zustellung.
6. Erst danach Medien, Gruppen, Replies und Reaktionen ergänzen. Medien nur über
   kurzlebige autorisierte Handles übertragen; View-once, Ablauf und Identitätswechsel
   vor Aufnahme in Orbiliqs Cache berücksichtigen. Keine solche Semantik als zuverlässig
   deklarieren, solange die lokale Projektion sie nicht durchsetzt.

## Capabilities und Sicherheitsgrenze

Aktuell tatsächlich in Orbiliq verfügbare Signal-Funktionen: **keine**. Die bestehende
ProviderRegistry enthält keinen Signal-Provider; es gibt keine Signal-Session und damit
keinen wahrheitsgemäßen positiven CapabilitySnapshot für irgendeinen Account. Die
Backend-Potenziale aus `01-signal.md` bleiben Forschungsbewertungen, keine Laufzeitrechte.

Ein späterer Provider muss ausgehandelte, implementierte und für den konkreten Account
gültige Unterstützung exponieren. Veraltete IPC-Version, fehlende Bindung, Sessionentzug
und nicht unterstützte Inhaltstypen dürfen keinen Versand zulassen. Calls, Presence,
Stories und umfassender Backfill bleiben ohne eigenen Nachweis deaktiviert.

Keine Schlüssel, Ratchet-Stores oder Account-Entropy-Pools über IPC exportieren. Ebenso
keine Provisioning-URLs, QR-Nutzdaten, Credentials, Nachrichtentexte, Telefonnummern oder
rohen Upstream-Exceptions in Orbiliq-Logs übernehmen. Upstream-Logging muss für die konkrete
Companion-Konfiguration separat geprüft werden; ein MIT-Wrapper redigiert dessen Logs nicht
automatisch. Kein eigener Signal-Kryptografiestack wurde geschrieben.

## Teststatus und fehlende Abnahme

Der bestehende Orbiliq-JVM-Prüflauf war erfolgreich (`test -Porbiliq.mock=true`, offline,
JDK 17, ein Worker; 1m 47s, 84 Aufgaben). Die meisten Testaufgaben waren `UP-TO-DATE`;
es war kein frischer vollständiger Testlauf. Er prüft keinen Signal-Account.
Es wurden keine neuen Signal-Integrationstests implementiert, weil kein
ausführbarer Signal-Adapter oder Companion vorliegt. Weder Upstream-APK noch IPC wurden
gebaut. Kein Account wurde verknüpft, kein Schlüsselmaterial erzeugt, keine Nachricht gesendet.

Ohne Credentials später ausführbar: falsche UID/Signatur, fehlende Zustimmung, abgelaufene
Bindung, IPC-Versionskonflikt, QR-Timeout/Abbruch, redigierte Diagnostics, Replay und Ack nach
Commit, Crash zwischen Versand und Antwort, unbekannte Versandwirkung, Epochwechsel,
Capabilitywiderruf sowie Beschränkungen für View-once/verschwindende Nachrichten. Diese
Tests sind **geplant, nicht bestanden**. Ein Testdouble allein beweist keine Interoperabilität.

Mit separatem Testkonto und Hardware erforderlich: QR-Linking, Session nach Prozessneustart,
Geräteentzug, Identitätswechsel, Empfang und eigener Outgoing-Sync, Textversand, anschließend
Medien/Gruppen/Replies/Reaktionen sowie Google-freier Empfang unter Doze und Netzwechsel.
Die fehlende Account-/Hardwareabnahme rechtfertigt keine Behauptung eines fertigen Providers.
