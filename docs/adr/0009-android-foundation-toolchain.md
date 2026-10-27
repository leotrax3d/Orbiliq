# ADR 0009: Kleine Android Foundation mit fixierter Toolchain

- Datum: 27.10.2026
- Status: Angenommen und im Grundgerüst umgesetzt
- Ergänzt: ADR 0002 und ADR 0008

## Entscheidung

Gradle 9.6.0 / AGP 9.4.1, JDK 17, Kotlin und Compose Compiler 2.4.21,
compile/target SDK 37, Build Tools 36.0.0, minSdk 26. Version Catalog,
Wrapper-Distributionshash, Dependency Locks und SHA-256-Verifikation werden versioniert.
Hilt bleibt im App-Zusammenbau. Android-Module verwenden das eingebaute Kotlin von AGP.

Zunächst sieben Module mit tatsächlicher Verantwortung; keine leeren Provider-, Sync-,
Datenbank- oder Security-Module. Das unkonfigurierte Repository liefert ausschließlich
eine leere Liste und wird vor echter Nachrichtenspeicherung durch die geschützte lokale
Projektion nach ADR 0005 ersetzt. Kein persistenter In-Memory-Messenger als Zwischenprodukt.

## Expressive-Ausnahme

Die stabile Compose BOM 2026.09.00 enthält Material 3 1.4.0, dessen Expressive-Theme
intern ist. Material 3 wird daher explizit auf 1.5.0-beta02 gesetzt. Der Opt-in bleibt
im Designsystem. Die Buildwerkzeuge selbst sind stabil. Kein Zugriff auf interne APIs
und keine selbst kopierte Material-Bibliothek. Vor Releases stabile Expressive-Version
prüfen; die Ausnahme ist keine allgemeine Freigabe von Vorababhängigkeiten.

## Folgen

API 26 ist die anfängliche Installationsuntergrenze; native Provider können spätere
Neubewertung erfordern. Die Foundation hat weder Datenhaltung noch Messengerrechte.
Reproduzierbare Eingaben bedeuten keine identischen APK-Signaturen mit verschiedenen
lokalen Debug-Schlüsseln. GitHub-CI ist vorbereitet, bleibt auf Nutzerwunsch wegen
fehlender Minuten deaktiviert. Lokale Prüfungen ersetzen keinen behaupteten GitHub-Lauf.

[Implementierungsdetails](../architecture/foundation.md),
[Prüfnachweis](../build-validation.md).
