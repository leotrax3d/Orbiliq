# Build- und Prüfprotokoll – Android Foundation

Stand: 27.10.2026. Kein Emulator wird auf diesem Laptop erneut gestartet.

## Umgebung und Befehle

Linux x86_64, Temurin JDK 17.0.21, Android SDK Platform 37.0,
Build Tools 36.0.0, Gradle Wrapper 9.6.0. Build mit einem Worker und ohne
parallele Modulbuilds; keine Messengerkonten oder echten Nachrichten verwendet.
SDK-Pfad und JDK-Installation gehören zur Umgebung und werden nicht versioniert.

Initiale Aufnahme der Dependency Locks und SHA-256-Prüfsummen:

```sh
./gradlew --no-daemon --max-workers=1 :app:assembleDebug test :app:lintDebug :app:assembleDebugAndroidTest --write-locks --write-verification-metadata sha256
```

Die Verifikationsdatei ist eine dokumentierte Trust-on-first-use-Aufnahme aus Google
Maven, Maven Central und den Pluginquellen. Anschließend wird ohne Schreibflags gebaut.
Der Wrapper prüft zusätzlich den festgelegten SHA-256-Hash der Gradle-Distribution.

## Lokaler Prüfstand

- Debug-APK gebaut; `apksigner verify --verbose` bestätigt gültige v2-Signatur.
- `aapt dump badging`: `org.orbiliq.app`, Version 0.1.0 (1), minSdk 26, targetSdk 37.
- Vier JVM-Tests erfolgreich: Provider-ID (2), unkonfigurierte Inbox (1),
  StateFlow-/ViewModel-Aktualisierung mit virtueller Zeit (1).
- Vollständiger erster Build erfolgreich: Debug-APK, `test`, `lintDebug` und
  `assembleDebugAndroidTest`. Lint: 0 Fehler; zunächst 7 Hinweise. Launcher-Icon und
  Backupregeln wurden daraufhin ergänzt, Compose-Gerätetest auf die v2-API migriert.
- Clean-Checkout-Nachweis bestanden: Commit `adea187` in ein neues Verzeichnis
  geklont, ohne `local.properties`, `.gradle` oder Build-Ausgaben aus dem Projekt.
  SDK ausschließlich über `ANDROID_HOME`, JDK über `JAVA_HOME` bereitgestellt.
  Bereits heruntergeladene globale Gradle-Abhängigkeiten wurden wiederverwendet;
  der Build-Output-Cache war explizit deaktiviert. Keine Schreibflags für Locks oder
  Prüfsummen, keine Änderung versionierter Dateien nach dem Build.
- Ergebnis: **BUILD SUCCESSFUL in 4m 31s**, 183 Aufgaben ausgeführt, vier Tests
  erfolgreich, Debug- und Instrumentation-Test-APK gebaut. Beide Compose-Gerätetests
  einschließlich der v2-Test-API kompilieren; sie wurden nicht auf Android ausgeführt.
- Finales Lint: **0 Fehler, 7 Hinweise**. Fünf betreffen verfügbare Updates für
  Gradle, Lifecycle und Coroutines; die dokumentierte, kompatible Versionskombination
  bleibt für diesen Build fixiert. Zwei kosmetische Hinweise betreffen den redundanten
  API-26-Iconordner und die noch fehlende monochrome Iconebene. Kein Hinweis wurde
  unterdrückt. Die Iconverfeinerung gehört zur folgenden UI-Ausarbeitung.

Exakter Befehl im frischen Checkout:

```sh
./gradlew --no-daemon --no-build-cache :app:assembleDebug test :app:lintDebug :app:assembleDebugAndroidTest
```

Die abschließenden Dokumentationsänderungen ändern den geprüften Code nicht.
Die mit `apksigner` geprüfte APK ist lokal zusätzlich unter
`build/distributions/orbiliq-0.1.0-debug.apk` gesichert (nicht versioniert).
SHA-256: `972e499a022da2f217c2d215f94041aaa73e752898e45b22c1b98ef004b0d22b`.
Buildlog, Lintbericht, Test-APK und SHA256SUMS liegen daneben. Die APK ist nur für
Entwicklung signiert, keine sicherheitsgeprüfte Messenger-Releaseversion.

## Bewusst nicht als bestanden ausgewiesen

- Gerätetest / Activity-Start: kein physisches Gerät angeschlossen. Der erste
  Emulatorversuch wurde wegen Speichermangels abgebrochen. Auf Nutzerwunsch wird
  dieser Laptop nicht mehr für Emulatoren verwendet. Die zwei Compose-Gerätetests
  prüfen später Start/Leerzustand und Activity-Neuerstellung auf einem echten Gerät
  oder einem geeigneten anderen Rechner.
- GitHub-CI: Repository privat angelegt, Actions wegen fehlender CI-Minuten auf
  Nutzerwunsch deaktiviert. Workflow ist vorbereitet, aber kein erfolgreicher
  GitHub-Lauf wird behauptet. Nach dem ersten Push startete GitHub trotzdem zwei
  dynamische Dependabot-Prüfungen. Diese wurden abgebrochen und die Dependabot-
  Zeitplanung entfernt. Der Android-Buildworkflow wurde nicht gestartet.
- Keine Provider-, Konto-, Verschlüsselungs-, Sync- oder Hintergrundfreigabe.

Damit bleiben die ursprünglichen DoD-Nachweise „Start auf Android“ und „CI erfolgreich“
extern offen. Die Umsetzung wird mit diesen ausdrücklich vereinbarten Infrastrukturgrenzen
übergeben; der dokumentierte Build ersetzt keinen Laufzeittest.

## Nachholen der externen Nachweise

README-Build ausführen, APK auf ein angeschlossenes Android-Gerät installieren,
Activity starten und `./gradlew :app:connectedDebugAndroidTest` ausführen. Ergebnisse
unter `app/build/reports/androidTests/` prüfen. Nach Verfügbarkeit von CI-Minuten
Actions wieder aktivieren und den Workflow manuell starten. Keine echten Zugangsdaten
oder privaten Nachrichten für diese Foundation-Smoke-Tests verwenden.

## Arbeitspaket 4: Domain Model und Provider API

27.10.2026, gleiche Toolchain und Infrastrukturgrenzen. JDK explizit über
`JAVA_HOME=/home/bertram/.local/share/orbiliq-tools/jdk17` und dessen `bin` im PATH
bereitgestellt; keine neue Abhängigkeit oder Änderung der Locks/Prüfsummen nötig.

```sh
./gradlew --offline --max-workers=1 test :app:assembleDebug :app:lintDebug
./gradlew --offline --max-workers=1 :core:provider-api:test
```

Der Gesamtbefehl ist erfolgreich (3m 25s). 47 JVM-Tests erfolgreich, davon 43 neu:
11 Domainmodell-, 13 Capability-, 10 Providervertrags- und 9 Sessionguard-Tests.
Die bestehenden zwei Provider-ID-, ein Data- und ein Inbox-Test bestehen weiter.
Debug-APK gebaut; Lint mit 0 Fehlern und 2 bestehenden Hinweisen
(`ObsoleteSdkInt`, `MonochromeLauncherIcon`; offline keine Updatehinweise). Die aktualisierten Quellen führen keine Android-
oder konkreten Messengerabhängigkeiten in Model/Domain/Provider API ein.
Ein erster Gesamtversuch fand die String-Längenannahme im Compose-Listenschlüssel;
diese wurde auf `AccountId.value` migriert, der erfolgreiche Gesamtbuild enthält die Korrektur.
Nach dem Gesamtlauf wurden im Guard zusätzlich Capabilitybeobachtung und Extensionevents
gegen fremde Scopes abgesichert; der zweite Befehl prüft den abschließenden API-Stand und ist ebenfalls erfolgreich
(1m 58s, alle 19 Provider-API-Tests bestanden).

Keine Geräte-, Account-, Netzwerk-, Persistenz-, Medien- oder CI-Tests ausgeführt.
Kein Emulator gestartet. Synthetische Provider sind ausschließlich Testimplementierungen;
die Tests belegen keine tatsächliche Messengerintegration oder sichere Medienengine.
Die APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`; die frühere unter
`build/distributions/` archivierte Foundation-APK ist kein Artefakt dieses Arbeitspakets.

## Arbeitspaket 5: Lokaler Mock-/Test-Messenger

27.10.2026, gleiche lokale JDK-/SDK-Toolchain. Der vorhandene Ausgangsstand wurde
zunächst mit `./gradlew --offline --max-workers=1 test` erfolgreich geprüft.
Es wurden keine Netzwerkdienste oder Messengeraccounts verwendet und keine neuen
externen Bibliotheken eingeführt. Das neue Modul besitzt einen eigenen Dependency Lock.

```sh
./gradlew --offline --max-workers=1 test :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --write-locks
```

Erfolgreich in 3m 23s: **70 JVM-Tests, 0 Fehler**, davon **23 neue Mock-Tests**.
Abgedeckt sind Login/Logout, Kontakte/Gruppen/Inbox, Direkt-/Gruppenversand,
Replies/Reaktionen/Edit/Delete/Typing/Receipts, virtuelle Eingangsverzögerung,
Offline/Reconnect, Fehler-/Rate-Limit-/Unknown-Outcome-Simulation, Idempotenz,
Reconciliation, Capabilitywechsel und Senderberechtigungen, Revisionskonflikte,
Sync-Gap/Resync, Replay bei langsamen Collectoren, Accounts/Close, Medienlimits,
Byte-Roundtrip, partielle Downloads, Cancellation und alle sieben Szenarienskripte.
Auch die Kombination mit `CheckedProviderSession` wird geprüft.

Debug-APK und Android-Test-APK gebaut. Lint: **0 Fehler, 2 bestehende Hinweise**
(`ObsoleteSdkInt`, `MonochromeLauncherIcon`). Testresultate im neuen Modul:
`core/provider-mock/build/reports/tests/test/` und `build/test-results/test/`.
Die vorhandenen Compose-Gerätetests wurden für Normal- und Mock-Inbox angepasst.

Keine Geräte-/Emulatorläufe, keine tatsächliche Messengerintegration und kein
CI-Lauf ausgeführt. Der Mock prüft keine Produktivpersistenz, Android-Hintergrund-
zustellung, Codec-/Decoderkompatibilität oder reale Dienstfähigkeiten.

Zusatzprüfung mit aktiviertem Mock-Schalter erfolgreich (2m 33s):

```sh
./gradlew --offline --max-workers=1 :core:provider-mock:test :app:assembleDebug :app:assembleDebugAndroidTest :app:compileReleaseKotlin -Porbiliq.mock=true
```

Damit kompilieren Debug-Injection, beide Compose-Testzustände und der davon getrennte
Release-Source-Set. Anschließend wurde der Event-Close-Test um Sessions **vor** dem
Login mit leerem Journal erweitert und die Close-Signalisierung dafür abgesichert.
Der abschließende Lauf prüft diese Korrektur und baut die Mock-APK erneut:

```sh
./gradlew --offline --max-workers=1 :core:provider-mock:test :app:assembleDebug :app:dependencies --configuration releaseRuntimeClasspath -Porbiliq.mock=true
```

Auch dieser Lauf ist erfolgreich, alle 23 Mock-Tests bestehen. Der ausgegebene
Release-Runtimegraph enthält **keinen** `provider-mock`. Die letzte erzeugte
`app/build/outputs/apk/debug/app-debug.apk` hat den Mock-Schalter aktiviert.
Das lokale Abschlusslog liegt unter `build/mock-final-validation.log` (ignoriert).
Die Test-APK ist kompiliert, aber weiterhin nicht auf einem Android-Gerät ausgeführt.

## Arbeitspaket 6: Lokale Persistenz und sichere Speicherung

27.10.2026, unveränderte lokale JDK-17-/Android-Toolchain. Ausgangsstand zunächst mit
`./gradlew --offline --max-workers=1 test` erfolgreich geprüft (70 JVM-Tests).
Keine neue externe Bibliothek, kein Netzwerkdienst und kein Messengeraccount verwendet.

Erster vollständiger Integrationslauf erfolgreich: 86 JVM-Tests, Debug-APK mit Mock,
Android-Test-APK, Lint und Release-Kompilierung. Danach wurden zusätzliche feste v2-
Migrationsdaten, Commit-vor-Acknowledge und die Korrelation bei einem verspäteten
Unknown-Ergebnis ergänzt. Der abschließende Befehl verwendet keine Schreibflags für Locks
oder Dependency-Verifikation:

```sh
JAVA_HOME=/home/bertram/.local/share/orbiliq-tools/jdk17 \
PATH=/home/bertram/.local/share/orbiliq-tools/jdk17/bin:$PATH \
./gradlew --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8' \
  test :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest \
  :app:compileReleaseKotlin -Porbiliq.mock=true
```

Die 20 neuen Tests bestehen: 15 Persistenz-/Sicherheitstests in `core:data` und fünf
Integrations-/Koordinatortests in `core:provider-mock`. Zusammen **90 JVM-Tests, 0 Fehler**.
Abdeckung: verschlüsselte Disk-Wiederöffnung und fortgesetzte Mock-Synchronisation,
Attachments als Metadaten, alle Content-/Commandvarianten und opaque Daten, feste v1-/v2-
Fixtures, Kontaktmerge/-unlink, Archiv-/Retentionregeln, Scopekollisionen, Commitfehler,
Cursoratomizität, Acknowledge erst nach Commit, GCM-Manipulation/falscher Schlüssel/AAD,
Dispatch-Cancellation, OutcomeUnknown, accountgebundene Attachment-/Commandkorrelation
und Vorrang dauerhafter Versandkorrelation. Die Aufbewahrung entfernt auch alte
abgeschlossene Outbox-Nutzlasten; aktive Versandfälle bleiben erhalten.

Lint enthält 0 Fehler und 3 Hinweise: die bisherigen `ObsoleteSdkInt` und
`MonochromeLauncherIcon` sowie `UseKtx` für die explizite SQLite-Transaktion. Der letzte
Hinweis ist eine optionale Schreibweisenempfehlung, kein Transaktionsfehler; keine
Unterdrückung oder zusätzliche Abhängigkeit eingeführt.

Drei neue Android-Instrumentationstests prüfen Keystore-/Vault-/Projektions-Wiederöffnung,
SQLite-v1-Migration und 3-MiB-Ciphertext außerhalb einer einzelnen CursorWindow. Sie sind
nur kompiliert, **nicht auf Android ausgeführt**. Kein Emulator gestartet, kein Gerätetest,
kein Geräte-Prozessneustart und kein CI-Lauf. JVM-KeyGenerator ersetzt in Tests den echten
Android Keystore; dies ist kein Nachweis für Hardware-Keyschutz auf einem bestimmten Gerät.
Die DoD ist damit auf Repository-/Mock-Ebene geprüft; ihr Android-Laufzeitnachweis bleibt
offen. Vollständige Grenzen und nächste Schritte im
[Speicher-/Sicherheitskonzept](architecture/local-storage.md).

Ein wiederverwendeter Gradle-Daemon geriet beim Zwischenlauf unter lokalen Speicherdruck.
Die betroffenen eigenen Buildprozesse wurden beendet; der abschließende Einzelbuild
verwendet deshalb einen frischen Daemon mit expliziten Heap-/Metaspacegrenzen.
Dies ändert keine Projekteinstellungen und startet keinen Emulator.

Abschließender Einzelbuild: **BUILD SUCCESSFUL in 6m 2s**, 216 Aufgaben (26 ausgeführt,
190 aktuell). Debug-APK mit aktiviertem Mock, Android-Test-APK, Lint und
Release-Kotlin-Kompilierung erfolgreich. Das lokale Abschlusslog liegt unter
`build/storage-validation.log` (ignoriert); APKs unter `app/build/outputs/apk/`.

## Prompt 7: Sync Engine und Offline Queue

Validiert am 27.10.2026 mit lokalem JDK 17 und gecachten Abhängigkeiten:

```sh
JAVA_HOME=/home/bertram/.local/share/orbiliq-tools/jdk17 ./gradlew \
  :core:data:test :core:provider-mock:test :app:compileDebugKotlin \
  -Porbiliq.mock=true --offline
```

Abschließender Lauf: **BUILD SUCCESSFUL in 10s**, 38 Aufgaben. 16 Data-Tests und
35 Mock-Tests (davon sieben neue Sync-Tests), **51 Tests, 0 Fehler**. Android-Debug-
Kompilierung mit Mock erfolgreich. Der abschließende inkrementelle Lauf verwendet
bereits erfolgreich kompilierte Artefakte der vorangehenden Läufe; kein Clean-Build.
Log: `build/sync-validation-final.log` (ignoriert).

Neue Nachweise: atomare optimistische Nachricht/Queue mit Store- und Session-Neuanlage,
Offline/Reconnect einschließlich neuer Capability-Revision, Übernahme der Remote-ID ohne
Nachrichtenduplikat, Antwortverlust nach Remotecommit ohne doppelte Sendung, unbekannter
Effekt bei verlorener Remotehistorie, persistenter Backoff/Fehler und späterer Erfolg,
automatischer Empfang nach Verbindungswechsel, Konfliktpersistenz und Transporttimeout.
Bestehende Tests für Cancellation, ACK nach Commit und verschlüsselte Wiederöffnung
bleiben erfolgreich. Ein beim Reconnect entdeckter Revisionsfehler wurde behoben;
JUnit-Rückgabetyp und Offline-Restore im neuen Testaufbau wurden korrigiert.

Neustarts werden durch neue Repository-/Providerinstanzen simuliert, nicht durch Beenden
eines Android-Prozesses. Keine Geräte-, Emulator-, Doze-, Boot- oder echten Netzwerktests.
Die flüchtige Mock-Remotehistorie ermöglicht nach einem Crash nicht immer einen sicheren
Effektnachweis; dann bleibt der Auftrag sichtbar OutcomeUnknown. Android-Scheduler und
Recovery-UI sind weiterhin offen. Siehe [Sync Engine](architecture/sync-engine.md).

## Prompt 8: Unified Contacts

Validiert am 27.10.2026 mit lokalem JDK 17 und gecachten Abhängigkeiten:

```sh
JAVA_HOME=/home/bertram/.local/share/orbiliq-tools/jdk17 ./gradlew \
  :core:data:test :core:provider-mock:test :app:compileDebugKotlin \
  -Porbiliq.mock=true --offline
```

Abschluss: **BUILD SUCCESSFUL in 39s**, 38 Aufgaben (14 ausgeführt, 24 aktuell).
27 Data-Tests und 35 Mock-Tests: **62 Tests, 0 Fehler**, einschließlich elf neuer
Kontaktregressionstests. Android-Debug-Kotlin-Kompilierung mit Mock erfolgreich.
Log: `build/contacts-validation-final.log` (ignoriert). Inkrementeller Build, kein Clean-Build.

Neue Nachweise: drei Provideridentitäten mit formatierten internationalen Nummern und
Wiederöffnung, keine Zuordnung allein anhand gleicher Namen, nationaler/ungültiger Nummern
oder unbestätigter Evidenz; Providerkollisionen und eigene Identitäten; persistentes Split
und Unlink trotz Replay; Nummernwechsel; Schutz vor Brücken zwischen manuell getrennten
Personen; Compare-and-set-Korrektur und veraltete Aufrufe; atomarer Abbruch bei Commitfehler;
Migration eines v4-Snapshots ohne erfundene Evidenz. Bestehende Persistenz-/Sync-/Mocktests
bleiben erfolgreich. Während der Entwicklung wurden eine fehlende Schutzmarkierung der
verbleibenden Split-Mitglieder und die Bytearray-Kopien im Test-Snapshotbackend korrigiert.

Keine Geräte-, Emulator- oder echten Accounttests. Keine Kontaktverwaltungs-UI und keine
neuen produktiven Provider. Nummernbelege sind synthetisch; ihre Verlässlichkeit bei realen
Providern ist mit der jeweiligen Integration nachzuweisen. Siehe [Unified Contacts](architecture/unified-contacts.md).

## Arbeitspaket 9: Unified Inbox und Unified Conversations

27.10.2026, vorhandene Toolchain, keine neuen Abhängigkeiten. Der erste Aufruf ohne
explizites JAVA_HOME scheiterte vor Gradlestart; mit dem vorhandenen JDK 17 bestanden
zunächst die unveränderten Data-/Mock-Tests. Nach Implementierung bestanden diese erneut,
einschließlich sechs neuer Integrationstests mit drei simulierten Messenger-Providern.

Finaler Prüfbefehl (JAVA_HOME auf das oben dokumentierte JDK gesetzt):

```sh
./gradlew --offline --max-workers=1 test :app:assembleDebug -Porbiliq.mock=true
```

Ergebnis: BUILD SUCCESSFUL. Alle JVM-Tests erfolgreich, Debug-APK mit Mock gebaut.
Die neuen Tests prüfen gemeinsame chronologische Inbox, stabile Timeline mit erhaltener
Herkunft, getrennte Providerchats, Archivierung und leere Chats, gezielten dauerhaften
Versand inklusive Wiederöffnung und optimistischer ID-Erhaltung, Capabilityunterschiede,
Offline-/Textlängenablehnung, Cross-Route-Antworten, falsche Providerwahl, fehlende Sessions,
reaktives Unlink und die atomare Abweisung bei Kontaktänderung zwischen Prüfung und Enqueue.
`git diff --check` ohne Befund. Keine Geräte-/Emulatortests ausgeführt; neue Timeline-/
Versandwahl-UI sowie reale Messenger sind nicht Teil dieses Domain-Arbeitspakets.
