# ADR 0012: Erste verschlüsselte lokale Persistenz

- Datum: 27.10.2026
- Status: Implementiert; Android-Laufzeitnachweis offen
- Präzisiert ADR 0004 und begrenzt die erste Umsetzung von ADR 0005

## Entscheidung

Der vorhandene Kotlin/JVM-Kern erhält einen explizit versionierten Binärcodec,
transaktionale Repositoryports und eine verschlüsselte Domainprojektion. Android
persistiert diese als AES-GCM-Blob in SQLite und hält den nicht exportierbaren Schlüssel
im Android Keystore. Credentials/Sessions erhalten einen separaten atomaren Vault mit
zweckgebundener Authentifizierung. Keine Klartext-Preferences und kein automatisches Backup.

Die erste Implementierung verwendet Android-SQLite statt Room/SQLCipher. Sie hält auch
Journal/WAL frei von privaten Klartextinhalten und benötigt keine neue native Abhängigkeit.
Die JVM-Tests können Projektion, Verschlüsselung und Mock-Neustart unabhängig von Android
prüfen. Dieses Paket ersetzt die langfristige normalisierte Room-/SQLCipher-Architektur
nicht. Die ganze Projektion wird bei jeder Mutation neu geschrieben, mit 64-MiB-Limit;
produktive große Historien und FTS benötigen den späteren Ausbau. Tabellen-/Dateigröße und
Schreibmuster bleiben sichtbar. Keine Produktiv-Sicherheitsfreigabe wird daraus abgeleitet.

Commandzustände, Projektion und Cursor sind dauerhaft. Dispatch wird vor dem Remoteeffekt
markiert, unterbrochene Versuche gelten als unbekannt. Keine automatische Wiederholung
ungeklärter Effekte. Pending Operations und Archives behalten Daten, sofern keine
strengere Providerablauffrist gilt. Vollständige Workerplanung und Reconciliation folgen.

## Folgen

Der Debug-Mock kann seine Domainprojektion über eine Wiederöffnung hinweg fortführen.
Die rohe Mock-Session bleibt ein In-Memory-Testwerkzeug. Medienbytes, simulierte
Zukunftsevents und die fiktive Remote-Idempotenzhistorie sind kein persistenter Providerdienst.
Ein Bytecache, portable Archive, Keyrotation und Recovery-UI folgen dem
[Speicher-/Sicherheitskonzept](../architecture/local-storage.md).

JVM-Wiederöffnung ist automatisiert geprüft. Reale Keystore-/SQLite-Migrationstests
werden für ein physisches Gerät bereitgestellt; ohne Hardware nicht als bestanden zählen.
