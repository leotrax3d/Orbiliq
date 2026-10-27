# Orbiliq

Datenschutzorientierter Unified-Messenger für Android. Aktuell: Kotlin-/Compose-Grundgerüst mit Material 3 Expressive, Dynamic Color und Hell-/Dunkelmodus.
Es sind **keine Messenger integriert**; die Inbox erklärt diesen Zustand.
Keine Netzwerkberechtigung, Tracker oder Play-Services-Pflicht.

## Build nach einem Clean Checkout

Voraussetzungen: Linux/macOS/Windows, JDK 17, Android SDK mit akzeptierten Lizenzen,
Platform `android-37.0`, Build Tools `36.0.0`, Platform Tools. Android Studio ist optional.
`JAVA_HOME` auf JDK 17 und `ANDROID_HOME` auf den SDK-Ordner setzen
(alternativ SDK-Pfad in unversionierter `local.properties`: `sdk.dir=/absoluter/pfad`).

```sh
sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"
sdkmanager --licenses
./gradlew --no-daemon :app:assembleDebug test :app:lintDebug :app:assembleDebugAndroidTest
```

Windows: `gradlew.bat` statt `./gradlew`. Der erste Build benötigt Internetzugriff
auf Google Maven, Maven Central und Gradle. SDK/JDK und Debug-Schlüssel werden nicht mitgeliefert.

APK: `app/build/outputs/apk/debug/app-debug.apk` (Android 8/API 26 oder neuer).

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n org.orbiliq.app/.MainActivity
./gradlew :app:connectedDebugAndroidTest
```

Der letzte Befehl benötigt ein entsperrtes, per USB verbundenes Gerät mit aktivierter
USB-Debugging-Funktion oder einen Emulator auf einem geeigneten Rechner.
Auf dem Entwicklungslaptop wird auf Nutzerwunsch kein Emulator gestartet.
Der aktuelle lokale Prüfstand steht in [Buildvalidierung](docs/build-validation.md).
Die UI-Prüfung startet die echte Activity samt Hilt-Graph und prüft den Leerzustand.
JVM-Ergebnisse stehen unter `<modul>/build/reports/tests/`, Android-Ergebnisse unter
`app/build/reports/androidTests/`, Lint unter `app/build/reports/lint-results-debug.html`.

## Struktur und Entwicklung

[Implementierte Architektur](docs/architecture/foundation.md),
[Zielarchitektur](docs/architecture/README.md), [Projektkontext](docs/PROJECT_CONTEXT.md),
[Beitragen](CONTRIBUTING.md), [Sicherheit](SECURITY.md).
Versionen liegen im [Version Catalog](gradle/libs.versions.toml).
Wrapper, Dependency Locks und SHA-256-Verifikation fixieren Build-Eingaben.
Debug-APKs werden lokal automatisch signiert; unterschiedliche Debug-Schlüssel führen
zu unterschiedlichen APK-Bytes. Es wird keine bitidentische Signatur zugesichert.
Eigener Code: [MIT](LICENSE); Abhängigkeiten behalten ihre jeweiligen Lizenzen.

## CI

`.github/workflows/android.yml` baut Debug- und Test-APK, führt JVM-Tests und Lint aus
und testet den Start auf einem Emulator. APK und Berichte werden als Artefakte hochgeladen.
Repository: [leotrax3d/Orbiliq](https://github.com/leotrax3d/Orbiliq) (privat).
GitHub Actions ist auf Wunsch wegen fehlender CI-Minuten deaktiviert. Der Workflow
bleibt vorbereitet; ein erfolgreicher GitHub-Lauf ist noch nicht nachgewiesen.
Nach Verfügbarkeit von Minuten unter Settings → Actions wieder aktivieren und den
Workflow unter Actions → Android → Run workflow manuell starten.
Ein lokaler Build allein ist kein Nachweis einer erfolgreichen GitHub-Ausführung.
Automatische Dependabot-Zeitpläne sind ebenfalls entfernt; Updates werden nach
CONTRIBUTING manuell geprüft, bis automatisierte Jobs ausdrücklich wieder gewünscht sind.
