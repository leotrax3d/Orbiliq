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

## Arbeitspaket 10: Material 3 Expressive UI Foundation

27.10.2026, JDK 17 aus der vorhandenen lokalen Toolchain, vorhandener Android SDK,
ein Gradle-Worker, offline. Vor Änderungen bestanden die unveränderten JVM-Tests.
Offizielle Android-/Material-Quellen wurden aktuell geprüft, siehe
[UI-Architektur und Quellen](architecture/messenger-ui.md).

Vollständiger Prüflauf:

```sh
JAVA_HOME=/home/bertram/.local/share/orbiliq-tools/jdk17 ./gradlew --offline --max-workers=1 \
  test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  :app:compileReleaseKotlin -Porbiliq.mock=true --write-locks
```

**BUILD SUCCESSFUL in 4m 9s**, 216 Aufgaben. **117 JVM-Tests, 0 Fehler**:
27 Data-, 26 Model-, 19 Provider-API-, 44 Mock- und 1 Feature-Test. Darin drei neue
UI-Port-Integrationstests für Kontakte/Konten, persistentes reversibles Archiv,
Text/Reply über Outbox und ReadOnly-Ablehnung ohne optimistische Nachricht.
Debug-APK, Android-Test-APK und Release-Kotlin-Kompilierung erfolgreich.
**Lint: 0 Fehler, 3 Hinweise** (vorhandene Launcher-Icon-/Resource-Konfiguration und SQLite-KTX).
Log: `build/ui-foundation-validation.log` (ignoriert).

Der erste Build erforderte nach Aufnahme von Activity/BackHandler ins Feature die
Aktualisierung der Dependency Locks. Der neue Testdouble wurde um `close()` ergänzt.
Ein neuer Integrationstest deckte einen echten Uhrzeitfehler auf: UI-Commands mit
Systemzeit wurden vom Mock mit fixer Szenarienzeit als PolicyExpired abgelehnt.
Der persistente App-Adapter startet jetzt mit aktueller Zeit und verwendet eine gemeinsame
Sessionuhr; die isolierten Mock-Szenarien behalten ihre deterministische Uhr.

Die abschließende gezielte Prüfung nach Verlagerung der Schreibzugriffe auf IO und
Präzisierung des Android-Composer-Tests nutzt:

```sh
JAVA_HOME=/home/bertram/.local/share/orbiliq-tools/jdk17 ./gradlew --offline --max-workers=1 \
  :core:provider-mock:test :app:assembleDebug :app:assembleDebugAndroidTest -Porbiliq.mock=true
```

**Abschließender Lauf: BUILD SUCCESSFUL in 1m 40s**, 128 Aufgaben. Die 44 Mock-Tests
bestehen erneut; Debug-APK und aktualisierte Test-APK erfolgreich gebaut.
Log: `build/ui-foundation-final.log` (ignoriert).

Android-UI-Tests prüfen jetzt Start/Wiederanlage, Chat-Composer, Entwurferhalt über
Navigation und Activity-Neuanlage, Filter, Personen, Konten und persistierten Dark Mode.
**Nur kompiliert, nicht auf Gerät ausgeführt.** `adb devices` lieferte keine Geräte;
auf Nutzerwunsch keinen Emulator auf diesem Laptop gestartet. Keine Screenshots oder
visuelle Abnahme von Springs, Schriftgrößen, TalkBack und Tastatur/Systemleisten.
Die Bedienwege sind implementiert; die tatsächliche Geräteabnahme bleibt offen.
Medienaktionen und reale Messengerprovider sind nicht als funktionsfähig ausgewiesen.

Dependency Locks des Features anschließend für sämtliche Konfigurationen mit
`:feature:inbox:dependencies --write-locks` offline erfolgreich aktualisiert
(6 Sekunden, keine unaufgelösten Abhängigkeiten). `git diff --check` ohne Befund.

SHA-256 der finalen lokalen Mock-Debug-APK:
`daed2ec30712c6dc368d6e3a482e4b55f35c282fcc11ced7b6a76d3541207392`.

## Prompt 11: erneute Signal-Recherche, keine Providerimplementierung

27.10.2026. Bestehender Prüfzustand mit folgendem Befehl geprüft:

```sh
JAVA_HOME=/home/bertram/.local/share/orbiliq-tools/jdk17 ./gradlew --offline --max-workers=1 \
  test -Porbiliq.mock=true
```

BUILD SUCCESSFUL in 1m 47s; 84 Aufgaben, 4 ausgeführt, 2 aus Cache, 78 up-to-date.
Insbesondere die meisten JVM-Testaufgaben waren up-to-date; kein vollständiger frischer
Testlauf. Keine Änderungen am Produktcode dieses Arbeitsschritts, keine neuen Signaltests,
kein Upstream-/Companion-Build, keine Hardware-/Accounttests. Die neue JSON-Quellendatei
wurde erfolgreich geparst, `git diff --check` ohne Befund. Die
[Signal-Nachprüfung](research/08-signal-revalidation.md) und der Projektkontext halten die
fehlende Implementierung und die **nicht erfüllte Definition of Done** ausdrücklich fest.

## Prompt 11, Fortsetzung: Einrichtungscode und erste Hardwaretests

28.10.2026. `core:provider-signal`, der separate Companion-Generator und die geschützte
Status-/Zustimmungs-IPC sind implementiert. Die Hauptapp enthält nur eigenen MIT-Code;
der große Client wird getrennt gebaut. Es gibt weiterhin keine Signal-Nachrichtenintegration.
Auch ein lokal verknüpfter Companion authentifiziert noch keinen konkreten Orbiliq-Account:
ohne Kontobindung bleibt die Providerklasse `Blocked`. Positive Messaging-Capabilities fehlen.

Erster vollständiger Orbiliq-Prüflauf nach der Unterbrechung:

```sh
# Prozess auf einen CPU-Kern und Nice 15 begrenzt, JDK 17.
./gradlew --no-daemon --offline --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1024m -XX:ActiveProcessorCount=1' \
  :core:provider-signal:test :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:compileReleaseKotlin -Porbiliq.mock=true --write-locks
```

**BUILD SUCCESSFUL in 3m 10s**, 154 Aufgaben. Debug-/Test-APK und Release-Kotlin erfolgreich.
Die zehn neuen JVM-Tests prüfen Signatur-/Paketpolicy, fremde Accounts, ablaufende und
falsche Challenges, Cancellation, geschlossene/entzogene Sessions, redigierte Fehler und
die Ablehnung unbekannter Versionen, Capabilities, Cursor und Versandbehauptungen.
Nach der zusätzlichen Kontobindungssperre bestehen sie erneut.

### Physisches Gerät: Pixel 10a, Android API 37

Auf ausdrücklichen Nutzerhinweis wurde das verbundene Gerät verwendet. Kein Emulator,
keine automatische Primärregistrierung, keine Auswertung der Signal-Datenbank und keine
pauschalen Logcat-Dumps. Orbiliq- und Test-APK wurden mit `adb install -r` installiert,
ohne App-Daten zu löschen. Reale Signal-Konten wurden durch diese Tests nicht verändert.

Die ersten vier UI-Tests scheiterten an **Espresso 3.5.0**, das Compose transitiv mitbrachte:
`InputManager.getInstance` ist auf API 37 nicht mehr vorhanden. Der
[offizielle Fix](https://github.com/android/android-test/commit/fddcda4adc8eaba515fbf2d790cf8dfe5c1b722d)
verwendet die öffentliche Serviceabfrage; die
[AndroidX-Releases](https://developer.android.com/jetpack/androidx/releases/test)
führen 3.7.0 als stabile Version. Diese Version ist jetzt explizit nur für Androidtests
gepinnt. Dependency Locks und SHA-256-Verifikation wurden aktualisiert; der POM-Hash wurde
zusätzlich mit dem direkt von Google Maven abgerufenen POM verglichen und stimmt überein.

Neubau inklusive aktueller zehn JVM-Tests und Auflösung der vollständigen Dependency Locks:
**BUILD SUCCESSFUL in 11m 49s**, 132 Aufgaben. Kein `FAILED` in den Dependencyreports.
Log: `build/signal-device-test-build.log` (ignoriert).

Danach bestand bereits die neue Signal-Setup-Navigation. Vier ältere Mock-Tests setzten
jedoch einen nicht archivierten Beispielchat voraus. Das Gerät enthielt einen bestehenden
archivierten Mock-Direktchat. Die Tests prüfen jetzt einen geladenen Inboxzustand und öffnen
den Chat bei Bedarf über den Archivfilter, ohne seinen Archivstatus zurückzusetzen.
Test-APK-Neubau: **BUILD SUCCESSFUL in 2m 41s**, 111 Aufgaben.

Auf dem Gerät ausgeführt:

```sh
adb shell am instrument -w -r \
  -e class org.orbiliq.app.LaunchTest,org.orbiliq.app.MessengerNavigationTest,org.orbiliq.app.SignalSetupNavigationTest \
  org.orbiliq.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r \
  -e class org.orbiliq.app.storage.AndroidStorageTest \
  org.orbiliq.app.test/androidx.test.runner.AndroidJUnitRunner
```

**5 UI-Tests bestanden** (10,276 s): Start, Activity-Neuanlage, Entwurf/Navigation/Versand
über den synthetischen Mock, Filter/Personen/Konten/Settings und der erklärende separate
Signal-Einrichtungseinstieg mit Rückkehr. **3 Storage-Tests bestanden** (0,821 s):
Keystore/Vault/Projektion nach Wiederöffnung, additive SQLite-Migration und große
Ciphertextblöcke. Mock-Testnachrichten sind synthetisch; der vorhandene Archivzustand wurde
beibehalten. Diese Tests belegen keinen echten Signal-Versand.
Logs: `build/signal-device-ui-tests.log`, `build/signal-device-storage-tests.log` (ignoriert).
Der zuerst fehlgeschlagene Espresso-Lauf und die Archivannahme sind separat aufbewahrt.

### Separater Companion-Build: noch offen

Der erste Aufruf verwendete irrtümlich `:app`; der Upstream benennt dieses Projekt in
`:Signal-Android` um. Generator, Anleitung und der neue ressourcenbegrenzte Launcher
`integrations/signal-companion/build.py` verwenden jetzt den richtigen Taskpfad.
Die Generierung der festen Revision und die signaturgeschützte Manifestkonfiguration
wurden erfolgreich geprüft. Upstream-Kryptografie und Servicequellen werden nicht kopiert
oder neu implementiert, sondern im separaten Client gebaut.

Der erste große Build überlastete mit mehreren JVMs den 8-GB-Laptop. Zusätzliche Prozesse
wurden beendet; weitere große Builds laufen nacheinander mit Nice 15, inzwischen auf Nutzerwunsch auf zwei CPU-Kernen.
Ein Versuch wurde gezielt zur Speicherentlastung abgebrochen, nicht wegen eines Codefehlers.
1536 MiB Java-Heap führten anschließend reproduzierbar zu `Java heap space` in
`:Signal-Android:mergeGithubProdSpinnerResources`. Mit **2304 MiB** wurde dieser Schritt
erfolgreich abgeschlossen. Der vollständige APK-Build und die lokalen IPC-Tests mit
installiertem Companion sind an diesem Dokumentationsstand noch nicht abgeschlossen.
Aus Zwischenmodulen wird kein erfolgreicher APK- oder Accountnachweis abgeleitet.

Weiter offen: echter QR-Link auf dem Zielgerät, Prozessneustart/Session/Entkopplung,
kontogebundene Nachrichtenautorisierung, Sync/Empfang/Versand, Medien, Gruppen, Replies
und Reaktionen. Die ursprüngliche Definition of Done ist weiterhin **nicht erfüllt**.

### Fortsetzung auf dem Laptop

2304 MiB führten bei der großen Kotlin-Appkompilierung zu dauerhaftem GC-Stau; der
Versuch wurde beendet. Auch 3072 MiB mit G1 scheiterten anschließend mit bestätigtem
`Java heap space` im Kotlin-Compiler (8m 18s, `build/signal-companion-build.log`).
Der nächste Versuch verwendet 3584 MiB, G1, Configuration Cache, einen Gradle-Worker
und In-Process-Kompilierung. Nur im Debug-Task wird die optionale Kotlin-JVM-Optimierung
übersprungen. Protokollbibliotheken werden dadurch nicht ersetzt.

Nach einer Unterbrechung war der vorherige Prozess nicht mehr vorhanden; der Build
wurde auf ausdrücklichen Nutzerwunsch auf dem Laptop fortgesetzt. Das aktuelle separate
Log ist `build/signal-companion-laptop-resume.log`. Es liegt noch kein erfolgreicher
APK-Nachweis vor. Die Original-Signal-App wird nicht überschrieben. Der Generator
isoliert zusätzlich Android-Accounttyp und Kontakt-MIME-Typen des Companions.

### Wechsel auf GitHub CI nach Freigabe

Der Nutzer hat die erneute CI-Nutzung für das nun öffentliche Repository ausdrücklich
freigegeben. Die Repository-Actions waren noch deaktiviert und wurden wieder aktiviert.
Der eigene Branch `codex/signal-companion-ci` enthält nur Companion-Generator, Overlay,
geteilten Vertrag und Workflow; weitere lokale App-/UI-Änderungen bleiben unberührt.
Lauf: https://github.com/leotrax3d/Orbiliq/actions/runs/40662562720 .
Der lokale Versuch wurde nach bestätigtem CI-Start zur Lastentlastung beendet.
Ein automatisch zusätzlich gestarteter allgemeiner Android-Build wurde abgebrochen,
da er auf diesem schmalen Branch die noch lokalen App-Änderungen nicht prüft.
CI verwendet einen eigenen kurzlebigen Debug-Schlüssel. Lokale Schlüssel und
Signal-Accountdaten wurden nicht hochgeladen. Der CI-APK-Signer muss vor lokalen
IPC-Tests durch den passenden lokalen Debug-Signer ersetzt werden. Ergebnis noch offen.

Der erste CI-Lauf scheiterte vor der Kompilierung, weil `setup-android` standardmäßig
das entfernte SDK-Paket `tools` anforderte. Beide Workflowdateien verwenden jetzt
explizit `platform-tools`. Der Folgelauf
https://github.com/leotrax3d/Orbiliq/actions/runs/40662676620
hat SDK-/Quellvorbereitung abgeschlossen und den eigentlichen APK-Build gestartet.

Parallel läuft auf `codex/signal-provider-tests` die allgemeine Android-CI mit dem
neuen Signal-Providermodul und seinen credentialfreien Tests. Der erste Lauf
40664458352 scheiterte vor Tests an vier fehlenden Metadatenprüfsummen. Die POMs
`guava-parent:33.4.0-jre`, `kotlinx-coroutines-bom:1.8.0` sowie die Gradle-Module von
`junit-bom:5.10.2` und `5.11.0-M2` wurden direkt über HTTPS von Maven Central
bezogen, mit dessen SHA-1-Seiten gegengeprüft und mit SHA-256 in die bestehende
strenge Verification-Metadatei aufgenommen. Keine Verifikation wurde deaktiviert.
Folgelauf: https://github.com/leotrax3d/Orbiliq/actions/runs/40664717329 .

### Erster vollständiger Companion-Build erfolgreich

GitHub-Lauf **40662676620** an Commit `a4fdc94` ist erfolgreich abgeschlossen:
**BUILD SUCCESSFUL in 23m 55s**, 652 Aufgaben (540 ausgeführt, 112 aus Cache).
Das Artefakt `signal-companion-research` enthält APKs und den modifizierten
entsprechenden Quellbaum (611267781 Bytes komprimiertes CI-Artefakt). Der Build
benötigte weder lokalen Signierschlüssel noch Signal-Accountdaten.
Log: `build/signal-companion-ci-success.log` (ignoriert).

Der separate Orbiliq-Lauf 40665933219 hat den expliziten Schritt
`:core:provider-signal:test` erfolgreich abgeschlossen. Der allgemeine Android-/Lint-/
Emulatorteil läuft zu diesem Zeitpunkt noch. Vorherige Fehlversuche hatten zusätzliche
fehlende Eltern-/BOM-Metadaten für Guava 33.6.0-jre und Groovy 4.0.29 gefunden;
deren SHA-256-Werte wurden nach demselben Maven-Central-Abgleich ergänzt.

Installation, Hardware-IPC und reales Linking sind durch diese CI-Ergebnisse noch
nicht nachgewiesen. Die Definition of Done von Prompt 11 bleibt offen.

### Hardware-IPC und unerwünschter Primärregistrierungsweg

Die ARM64-APK des ersten erfolgreichen Companion-CI-Laufs wurde lokal neu signiert.
`apksigner verify` bestätigt den identischen SHA-256-Signer wie Orbiliq. Im fertigen
Manifest wurden eigene Application-ID/Authority, Signature-Permission und geschützte
Activity geprüft; die kompilierten Authenticator-/Syncadapter-XMLs und Kontakt-MIME-Typen
sind isoliert. Installation nur per `adb install -r` für das eigene Companion-Paket.
Orbiliq und Test-APK wurden ohne Datenreset neu installiert.

**3 echte lokale IPC-Tests bestanden** auf dem Pixel (3,576 s): primitive Statusfelder,
Versionsablehnung und verweigerter generischer Query. Ein zusätzlicher ADB-Shell-Aufruf
scheiterte an der Signature-Permission, wie vorgesehen. Die reale Setup-UI zeigte
zuerst ConsentRequired; nach ausdrücklicher Nutzerzustimmung NeedsLink.
Logs: `build/signal-companion-device-ipc-tests.log`,
`build/signal-companion-shell-denied.log` (ignoriert).

Anschließend wurde am Gerät der native Telefonnummernweg geöffnet und eine Telefonnummer
eingegeben; der Nutzer berichtete weitere ungezielte Klicks. Die Status-IPC meldete danach
**PrimaryDeviceNotSupported**. Es liegt kein erfolgreicher Sekundärgeräte-Link vor.
Der erste Companion sperrte Primärregistrierung nur am vorgesehenen Einstieg und nicht
zuverlässig für andere Upstream-Einstiege. Dieser Build ist für Accounttests überholt.
Der Companion wurde vorübergehend mit `pm disable-user` deaktiviert, ohne Datenlöschung.
Die ursprüngliche Signal-App wurde nicht geändert; deren aktueller Accountstatus muss
der Nutzer noch prüfen. Kein PIN wurde vom Agenten eingegeben oder angefordert.
Kein QR-Bild wurde übertragen; der lokale Captureversuch brach vor der Aufnahme ab.

Korrektur an `fd5dce6`: Linking als erzwungener Activity-Einstieg, Beschränkung restaurierter
Navigation, entfernte Primärregistrierungs-/SMS-/Verifikationsoperationen in beiden
Registration-HTTP-APIs und vier strikte Mock-Transporttests. CI-Lauf
https://github.com/leotrax3d/Orbiliq/actions/runs/40668223010
prüft zuerst diese Grenze und baut dann neu. Ergebnis der Korrektur noch offen.

### Orbiliq-CI vollständig erfolgreich

https://github.com/leotrax3d/Orbiliq/actions/runs/40666966910 ist **erfolgreich**,
einschließlich Signal-JVM-Tests, Android-Build, gesamter JVM-Tests, Lint und Emulatortests.
Geprüft ist der Commit `5347ce8` auf `codex/signal-provider-tests`: der bestehende
versionierte Appstand plus neues Signal-Providermodul. Weitere noch lokale Änderungen
der Setup-UI und Messengeroberfläche sind nicht Teil dieses CI-Commits; ihre oben
dokumentierten Hardware-/Buildprüfungen sind getrennte Nachweise.

Der ursprüngliche lokale Quellbaum ist inzwischen als überholt markiert und der
Buildlauncher lehnt Quellbäume ohne alle acht HTTP-Registrierungssperren und ohne
Navigationspolicy ab. Das erste lokal signierte APK heißt jetzt ausdrücklich
`companion-first-build-primary-registration-unsafe.apk` und ist kein Installationsziel.
Die vollständige grüne CI enthält laut heruntergeladenem Report 10 Signal-JVM-Tests
(0 Fehler, 0 übersprungen) und 5 Emulator-Instrumentationstests (0 Fehler).

Der korrigierte Companion-Lauf 40668223010 hat den Schritt
`Verify primary registration is blocked without network` erfolgreich abgeschlossen
(vier Boundarytests). Anschließend ist der korrigierte APK-Build gestartet.
Das ist noch kein erfolgreiches Device Linking.
# Fortsetzung Signal am 28.10.2026 im neuen Windows-Workspace

Ausgangspunkt: `origin/codex/signal-provider-tests`; Arbeitsbranch
`codex/signal-completion`. Vorhandener Companion-CI-Lauf 40668223010 über `gh run view`
als success bestätigt, Artefakte heruntergeladen. Kein Installationsnachweis daraus
abgeleitet. Neue Änderungen sind in diesem alten APK nicht enthalten.

- `SIGNAL_UPSTREAM_SOURCE=<lokaler pinned Git-Checkout> python -m unittest discover
  -s integrations/signal-companion -p test_prepare.py -v`: **7 Tests bestanden**,
  keine übersprungen. Vier Tests extrahieren Originaldateien aus dem festen
  Signal-Commit und prüfen tatsächliche Generatorpatches einschließlich Restore,
  Back-Navigation, HTTP-Blockade, erhaltenem Secondary-Device-Einstieg und Driftfehler.
  Drei Tests prüfen die Security-Manifestkonsistenz. Keine echten Credentials.
- `prepare.py --source-cache build/signal-companion/source --output
  build/signal-companion/source-v2`: erfolgreich. Der erste Checkout ohne
  `core.longpaths=true` hatte Windows-Pfadlängenfehler bei Screenshotfixtures;
  der neue Generator vermeidet diese im frischen Checkout.
- `gradlew.bat --no-daemon --max-workers=1
  -Dorg.gradle.jvmargs="-Xmx2g -XX:ActiveProcessorCount=2"
  :core:provider-signal:test`: **BUILD SUCCESSFUL**, XML: 10 Tests, 0 Fehler,
  0 Fehlschläge, 0 übersprungen. Zunächst fehlte JDK 17; nach lokalem Download
  eines Temurin-17-JDKs mit Abgleich gegen den veröffentlichten SHA-256 bestanden.
- Kein neuer Companion-APK-Build, keine Instrumentationstests, kein Accounttest.
  Das vom Nutzer angeschlossene Gerät ist für ADB bislang offline/fehlend.
  Keine QR-Daten, Schlüssel oder Nachrichten erfasst. Keine Signal-Appdaten verändert.
