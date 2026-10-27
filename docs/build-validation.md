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
