# Android Foundation (Prompt 3)

## Befund vor Umsetzung

Nur Recherche/ADRs/Dokumentation, keine Git-Metadaten, Quellen, Builds oder Tests.
ADR 0002 erlaubt ausdrücklich, Module erst bei konkretem Bedarf anzulegen.
Die Zielarchitektur wird in einem kleinen, gerichteten Graph begonnen:

```text
app -> feature:inbox -> core:domain -> core:model
app -> core:data -> core:domain
app -> core:designsystem
core:provider-api -> core:model
```

`model`, `domain`, `provider-api` und `data` sind Kotlin/JVM. Domain/Provider-API exportieren
Flow; Android, Hilt und Compose bleiben außerhalb. `data` liefert bei fehlender
Konfiguration ausschließlich eine leere Inbox; es ist kein persistentes Repository.
Der Hilt-Graph wird ausschließlich in `app` zusammengesetzt. Inbox besitzt ein ViewModel
und beobachtet dessen StateFlow lifecyclebewusst. Der Providerdescriptor ist lediglich die
erste Discovery-Grenze, noch keine Implementierung des vollständigen Providervertrags.

Room/SQLCipher, Sync/Outbox, Security, Medien, Hintergrunddienste, Notifications und konkrete
Provider werden mit ihren ersten echten Implementierungen ergänzt. Kein TDLib-/Login-Prototyp
ist Bestandteil dieses Schritts. Kein Fakeprovider wird als Produktfunktion eingebunden.
Tests können Ports direkt mit Fakes implementieren; ein eigenes Testing-Modul wäre jetzt leer.

## Buildentscheidungen

AGP 9.4.1, Gradle 9.6.0, Kotlin/Compose Compiler 2.4.21, Compose BOM 2026.09.00,
JDK/Bytecode 17, compile/target SDK 37, minSdk 26, Build Tools 36.0.0.
Standardmäßig ein Gradle-Worker, kein paralleler Modulbuild und Kotlin-Kompilation im
Gradle-Prozess: begrenzter Speicherverbrauch auf dem Entwicklungslaptop. Tests und
Build starten keinen Emulator automatisch.
AGP verwendet eingebautes Kotlin; kein zusätzliches Android-Kotlin-Plugin.
Version Catalog statt verteilter Versionsstrings, Gradle Wrapper mit Distributionshash,
Dependency Locks und strikte SHA-256-Verifikation. Kein buildSrc/Convention-Plugin bis
wiederkehrende Buildregeln diese zusätzliche Komplexität rechtfertigen.

MinSdk 26 begrenzt die anfängliche Testmatrix; vor Provider-/Native-Integration erneut prüfen.
Material 3 Expressive wird über das Designsystem gekapselt, dynamische Farben ab API 31,
statische Material-Farben darunter, System-Hell-/Dunkelmodus und Edge-to-edge.
Die stabile Material-3-Version 1.4.0 macht `MaterialExpressiveTheme` und das MotionScheme
nur intern verfügbar. Deshalb wird ausschließlich Material 3 auf `1.5.0-beta02` angehoben;
die Android-Buildwerkzeuge und die übrige Compose-BOM bleiben stabil. Der lokale Opt-in
ist im Designsystem gekapselt. Diese begründete UI-Bibliotheksausnahme ist vor Releases
auf die nächste stabile Version zu migrieren. Die AAR verlangt compileSdk 37 / AGP 9.1.0
oder neuer, was die Foundation erfüllt. [Upstream-Releases](https://developer.android.com/jetpack/androidx/releases/compose-material3).

Offizielle Grundlagen: [AGP-Kompatibilität](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[Compose BOM](https://developer.android.com/develop/ui/compose/bom).

## Prüfstatus

Der konkrete lokale Nachweis wird in `docs/build-validation.md` festgehalten.
Ein konfigurierter Workflow bedeutet noch keinen erfolgreichen GitHub-Lauf.
