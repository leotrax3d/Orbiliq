# Orbiliq

Datenschutzorientierter Unified-Messenger für Android. Aktuell: bedienbare Compose-Messenger-Oberfläche mit Material 3 Expressive, Dynamic Color und Hell-/Dunkelmodus.
Die Signal-Integration ist in Entwicklung: echtes Device Linking und lokale Sessionpersistenz sind nachgewiesen; die neue Nachrichtenstrecke ist noch nicht auf Hardware abgenommen. Ein optionaler lokaler Mock-Messenger ermöglicht Entwicklung ohne Account und Netzwerk.
Der Orbiliq-Kern nutzt lokale IPC; der separate Companion übernimmt Signal-Netzwerkverkehr. Keine Tracker oder verpflichtenden Play Services im Kern.

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
Die UI-Tests starten die echte Activity samt Hilt-Graph und prüfen Start, Navigation und Composer. Ausgeführte Prüfungen und Grenzen stehen im Prüfprotokoll.
JVM-Ergebnisse stehen unter `<modul>/build/reports/tests/`, Android-Ergebnisse unter
`app/build/reports/androidTests/`, Lint unter `app/build/reports/lint-results-debug.html`.

## Lokaler Mock-/Test-Messenger

```sh
./gradlew --offline :core:provider-mock:test
./gradlew --offline :app:assembleDebug -Porbiliq.mock=true
```

Der Debug-Schalter öffnet die Messenger-Oberfläche mit zwei lokalen Testchats und sichtbaren Mock-Hinweisen. Chats lesen, Texte/Antworten senden, archivieren, Personen und Konten ansehen sowie Darstellung einstellen sind bedienbar.
[UI-Konzept, Quellen und Grenzen](docs/architecture/messenger-ui.md).
Ohne Schalter wartet die App auf einen separat installierten und ausdrücklich für Nachrichten freigegebenen Signal-Companion. Release enthält den Mock nicht.
Login, Kontakte/Gruppen, Nachrichten/Medien und Fehler lassen sich über die Provider-API
steuern. Sieben wiederverwendbare Szenarien decken Unterhaltung, Medien, Offline-Recovery,
unklare Versandwirkung, Revisionskonflikte, Capabilitywechsel und Sync-Gaps ab.
[API-Beispiele, Zeitsteuerung und Grenzen](docs/architecture/mock-provider.md).
`--offline` beim Build setzt bereits vorhandene Gradle-/SDK-Abhängigkeiten voraus.

## Struktur und Entwicklung

Experimenteller [Signal-Companion-Einrichtungspfad](integrations/signal-companion/README.md):
separater Clientbuild aus festgelegter Signal-Android-Revision, lokale Zustimmung und
Verknüpfungsstatus. In Orbiliq unter Konten erreichbar. Die neue [Nachrichtenstrecke](docs/architecture/signal-provider.md)
implementiert normale Direktchat-Texte und reine Gruppenanzeige hinter einer separaten
Account-Zustimmung; die Geräteabnahme steht noch aus. Nicht unterstützte Aktionen bleiben gesperrt.

[Android Foundation](docs/architecture/foundation.md),
[Domain Model und Provider API](docs/architecture/domain-model.md),
[Zielarchitektur](docs/architecture/README.md), [Projektkontext](docs/PROJECT_CONTEXT.md),
[Beitragen](CONTRIBUTING.md), [Sicherheit](SECURITY.md).
Versionen liegen im [Version Catalog](gradle/libs.versions.toml).
Wrapper, Dependency Locks und SHA-256-Verifikation fixieren Build-Eingaben.
Debug-APKs werden lokal automatisch signiert; unterschiedliche Debug-Schlüssel führen
zu unterschiedlichen APK-Bytes. Es wird keine bitidentische Signatur zugesichert.
Lizenz: [GNU AGPL-3.0-or-later](LICENSE) (seit 28.10.2026, zuvor MIT). Orbiliq bettet den
Signal-Android-Client (AGPL-3.0-only) in eine gemeinsame App ein; das Gesamtwerk ist AGPL.
Wer die App verbreitet oder als Netzdienst anbietet, muss den vollständigen Quelltext
bereitstellen. Abhängigkeiten behalten ihre jeweiligen Lizenzen. Orbiliq ist kein offizielles
Signal-Produkt und nicht mit Signal Messenger LLC verbunden.

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

## Lokale Persistenz

Mit `-Porbiliq.mock=true` persistiert der Debug-Mock jetzt seine Domainprojektion
verschlüsselt in Android-SQLite und stellt sie nach Wiederöffnung wieder her.
Keystore, atomarer Secret-Vault, Migrationen, Pending Operations, Kontaktzuordnungen,
Aufbewahrung und lokale Archive sind vorbereitet bzw. über Repositoryports implementiert.
Medienbytes bleiben bisher flüchtig. Architektur, 64-MiB-Prototypgrenze und noch offene
Android-Gerätenachweise stehen im [Speicher-/Sicherheitskonzept](docs/architecture/local-storage.md).
