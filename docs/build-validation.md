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
- Clean-Checkout-Nachweis für diesen letzten Stand: Abschlussprüfung läuft.

## Bewusst nicht als bestanden ausgewiesen

- Gerätetest / Activity-Start: kein physisches Gerät angeschlossen. Der erste
  Emulatorversuch wurde wegen Speichermangels abgebrochen. Auf Nutzerwunsch wird
  dieser Laptop nicht mehr für Emulatoren verwendet. Die zwei Compose-Gerätetests
  prüfen später Start/Leerzustand und Activity-Neuerstellung auf einem echten Gerät
  oder einem geeigneten anderen Rechner.
- GitHub-CI: Repository privat angelegt, Actions wegen fehlender CI-Minuten auf
  Nutzerwunsch deaktiviert. Workflow ist vorbereitet, aber kein erfolgreicher
  GitHub-Lauf wird behauptet.
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
