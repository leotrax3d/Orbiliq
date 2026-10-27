# Lokale Persistenz und Sicherheitskonzept

Stand: 27.10.2026. Implementierter erster Persistenzstand; reale Messenger fehlen weiterhin.

## Speicher und Repository

`core:data/storage` enthält `LocalRepository`, `SnapshotStorage`, `CiphertextStorage`,
`SecretVault`, `PersistentLocalRepository`, den expliziten Binärcodec und `SyncCoordinator`.
Android-spezifische SQLite-, Keystore- und Vault-Implementierungen liegen vorerst in
`app/storage`; Domain und Mock bleiben JVM-testbar. Alle IO-Aufrufe gehören auf
`Dispatchers.IO`. Eine Singleton-Instanz besitzt die Datenbank; parallele Prozesse oder
mehrere schreibende Repositoryinstanzen werden nicht unterstützt.

Gespeichert werden Accounts, Personen, Identitäten und reversible Zuordnungen, Gruppen
mit Mitgliedern, Conversations, alle MessageContent-Varianten, Attachments als Metadaten,
opaque Providerdaten einschließlich Version/Payload, Reaktionen, Sync-Cursor und -Status,
Pending Operations einschließlich Command, Dependencies, Idempotenzschlüssel,
Versuchszähler, Retryzeit, Status und Provider-Übernahmebeleg, Aufbewahrungsregeln und
lokale Archivzuordnungen. IDs/Account-/Conversation-Scopes bleiben erhalten.
Kollidierende Identitäts-, Conversation-, Gruppen- oder Nachrichten-IDs werden abgelehnt.
Zusätzliche Remotealias-Mappings über den bestehenden `IdMapping`-Port sind noch offen;
der Mock verwendet unveränderliche lokale IDs und Remote-Referenzen.

Eine Mutation bildet eine neue Projektion, codiert und verschlüsselt diese und ersetzt
sie in einer SQLite-Transaktion. Erst nach erfolgreichem Commit wird der Flow aktualisiert.
Nachricht und Cursor werden gemeinsam geschrieben. Provider-Acknowledgement folgt dem
Commit. Ein Schreibfehler lässt sowohl die bisherige Projektion als auch den Cursor stehen.
Replay muss geordnet von einem einzigen accountgebundenen Collector erfolgen. Derselbe
letzte Cursor und wiederholte Upserts sind idempotent; eine beliebige Umordnung alter
Ereignisse ist kein unterstützter Providervertrag. Typing/Capability/Auth-Ereignisse sind
transient; Sitzungsgeheimnisse gehören in den Vault. Sync-Gaps bleiben als Gap gespeichert
und benötigen explizite Recovery, keine behauptete vollständige Historie.

Die App verwendet Android-SQLite mit **einer verschlüsselten Projektion**, noch keine
normalisierten Room-Tabellen und kein SQLCipher. Dabei bleiben nur Schema, Dateigröße und
Schreibzeitpunkte sichtbar. SQLite-Journal und WAL erhalten ausschließlich Ciphertext.
Der Prototyp schreibt bei jeder Mutation die gesamte Projektion neu; er ist für den Mock
und kleine lokale Datenbestände gedacht. Harte Obergrenze: 64 MiB Ciphertext; einzelne
Strings/Providerpayloads maximal 16 MiB, Sammlungen maximal 100.000 Elemente. Zu große
Commits schlagen fehl und löschen keine bestehenden Daten. Datenbank-Lesezugriffe holen
Blöcke unterhalb der Android-CursorWindow-Grenze. Für große Historien, FTS und produktiven
Medienbetrieb ist der in ADR 0005 geplante Room-/SQLCipher-Ausbau erforderlich.
[ADR 0012](../adr/0012-first-local-persistence.md) dokumentiert diese begrenzte Abweichung.

## Migrationen und Fehlerfälle

SQLite-Schema v1: `projection(id, payload)`. v2 ergänzt `storage_metadata`; Upgrade erhält
die verschlüsselte Nutzlast. `SQLiteOpenHelper` führt die Migration transaktional aus.
Unbekannte Upgrades und Downgrades werden abgelehnt. Kein destructive fallback.

Der unabhängig versionierte, explizite Nutzlastcodec liest v1 (Basisdaten), v2
(Aufbewahrung/Archive) und v3 (Reaktionen/Übernahmebelege). Fehlende Felder erhalten
definierte Defaults; beim nächsten erfolgreichen Commit wird v3 geschrieben. Keine
Java-Objektdeserialisierung, Reflection oder Klassennamen aus dem Datenträger.
Neue Felder benötigen eine neue Version und feste Legacy-Fixtures. Beschädigte,
zu neue oder nicht authentifizierbare Daten werden nicht als leere Installation behandelt.

`Dispatching` wird beim Wiederöffnen zu `OutcomeUnknown`. Der Koordinator persistiert
`Dispatching` vor einem Remoteaufruf und unterscheidet NoEffect, Accepted, Confirmed,
Rejected und Unknown. Ein Unknown/Accepted wird niemals blind erneut verschickt.
Bestätigende Commandkorrelation hat Vorrang vor einem verspäteten Unknown-Ergebnis.
Automatische Retryplanung/Backoff, Leases für mehrere Worker und ein allgemeiner
Reconciliation-Worker sind noch nicht implementiert. Der Koordinator muss hinter dem
bestehenden Capability-/Sessionguard verwendet werden. Eine Providerübernahme (`Accepted`)
ersetzt keinen Zustellnachweis.

## Sensible Daten, Credentials, Sessions und Schlüssel

Alle Projektionsinhalte und Vault-Werte werden mit AES-256-GCM verschlüsselt. Jede
Verschlüsselung nutzt einen vom Cipher erzeugten zufälligen 96-Bit-IV und einen 128-Bit-Tag.
Version und Datenzweck werden geprüft; AAD trennt Projektion und individuelle Vaultreferenzen.
Der nicht exportierbare AES-Schlüssel liegt unter einem versionierten Alias im Android
Keystore. Hardwareabsicherung hängt vom Gerät ab; StrongBox wird nicht vorausgesetzt.
Es gibt keinen hardcodierten Schlüssel und keine Klartext-Schlüsseldatei. Die Mechanismen
folgen [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

Die Datenbank und der atomare verschlüsselte Vault liegen in `Context.noBackupFilesDir`,
also im privaten, credential-encrypted App-Bereich. `allowBackup=false` und die vorhandenen
Backup-/Transferausschlüsse gelten zusätzlich. Vor dem ersten Geräte-Unlock gibt es keinen
Direct-Boot-Zugriff. Der Schlüssel verlangt keine Biometrie pro Zugriff, damit späterer
Hintergrundsync möglich bleibt. Ein UI-App-Lock wäre ein separater Schutz; er wird durch
dieses Paket nicht vorgetäuscht.

Provider erhalten opaque Vaultreferenzen statt Secrets im Accountmodell. Passwörter,
Sessions, Refresh-Tokens und Protokollschlüssel gehören ausschließlich in den Vault oder
in einen separat abgesicherten nativen Providerstore. Keine normalen Preferences für
Secrets, keine Logausgaben von Inhalten, Kommandos oder Exception-Payloads. Der Vault
schreibt nur Ciphertext über Android `AtomicFile`. Rückgegebene ByteArrays müssen nach
Verwendung vom Aufrufer überschrieben werden; vollständige Heapbereinigung kann die JVM
nicht garantieren. Der synthetische Mock besitzt keine echte Session und braucht keinen
Vault-Eintrag.

Fehlt der Keystore-Schlüssel bei vorhandenen Speicherdateien, stoppt der Zugriff. Kein
stilles Ersetzen des Schlüssels, keine automatische Löschung. Keyverlust, beschädigte
Daten oder ein ungültiger Tag benötigen eine sichtbare Recoveryentscheidung. Ein
Recovery-/Reset-Screen, Schlüsselrotation und portable verschlüsselte Backups sind noch
offen. Root, kompromittierter Prozess und Inhalte auf einem entsperrten Gerät werden
nicht durch At-Rest-Verschlüsselung beseitigt. Dateigrößen/Schreibmuster sind sichtbar;
Rollback alter gültiger Ciphertexte wird nicht erkannt.

## Medien-Cache, Aufbewahrung und lokale Archive

Implementiert: persistente Attachment-Metadaten/Policies, `RetentionPolicy`, explizites
`setRetention`, `prune` und `archive`. Standard: Nachrichten unbegrenzt, geplanter
Mediencache 30 Tage / 256 MiB. Die Werte sind über den Repositoryport konfigurierbar;
ein Einstellungsscreen und periodischer Cleanup sind nächste Integrationsschritte.

`prune(now)` entfernt alte Nachrichten nur außerhalb lokaler Archive und aktiver
Outbox-Routen. Vom Provider vorgegebene Ablaufzeiten haben auch im Archiv Vorrang.
Verwaiste Reaktionen werden entfernt. Abgeschlossene Outbox-Einträge samt Nachrichtentext
werden nach derselben Altersregel entfernt, außer im Archiv oder solange aktive Kommandos
sie als Dependency benötigen. Ausstehende Kommandos bleiben nachvollziehbar.
`archive` blendet eine Conversation aus der Inbox aus, behält ihre Daten und Route und
ist umkehrbar. Ein lokales Archiv ist Bestandteil derselben verschlüsselten Datenbank,
kein unabhängiges Backup und kein Umgehen von View-once/Remoteablaufregeln.

Konzept für den **noch nicht implementierten** Byte-Cache: interner No-Backup-Bereich,
zufällige Objektnamen, accountgebundene Referenzen, separate zufällige Objektschlüssel,
Streaming-AEAD aus einer geprüften Bibliothek. Metadaten/Thumbnails ebenfalls geschützt;
keine Klartext-Tempdateien, externen Speicherpfade oder öffentlichen Medienindizes.
Cacheaufnahme nur bei `cacheAllowed`, nie bei `viewOnce`; Ablauf bei jedem Lesezugriff
und beim Cleanup prüfen. LRU-Quota, Maximalalter und Providerablauf gemeinsam auswerten;
providerseitig kürzere Fristen gewinnen. Nach Logout/Accountentfernung Objektschlüssel
und Vaultwerte löschen. Sicheres physisches Überschreiben auf Flash kann nicht zugesagt
werden. Kurzlebige, eng berechtigte ContentProvider-Streams sind für erlaubte Exporte
vorgesehen; kein permanenter Klartext-Exportcache.

Persistiert werden zurzeit ausdrücklich **keine Medienbytes**. Mockmedien verbleiben
bei der isolierten Session, simulierte Transfers und geplante virtuelle Events sind
flüchtig. Ein Neustart stellt Message-/Attachment-Metadaten wieder her, aber keine
abspielbaren Mockdateien. Ein portables Archivformat muss später verschlüsselt,
versioniert, integrity-geprüft und explizit exportiert/importiert werden; private
Protokollschlüssel und View-once-Inhalte werden nicht automatisch exportiert.

## Mock-Integration und Nachweise

`PersistentMockRepository` öffnet den lokalen Speicher im App-Scope auf IO. Beim Neustart
stellt es den Mock aus der gespeicherten Projektion her, rekonstruiert IDzähler und
startet einen neuen Replay-Epoch. Neue Command-IDs sind UUIDs. Lokale Kontaktzuordnungen,
Archive und Outbox bleiben dabei erhalten. Neue Ereignisse werden dauerhaft projiziert;
der App-Scope überlebt das Abmelden eines Inbox-Collectors. Der normale In-Memory-Mock
für isolierte Szenarien bleibt verfügbar. Release enthält weiterhin keinen Mock.

JVM-Tests prüfen Disk-Wiederöffnung, neue Events nach Wiederöffnung, Atomizität bei
Commitfehlern, Codecvarianten, Legacy-Fixture, Manipulation/falschen Schlüssel/AAD,
Retention/Archive, Kontaktzuordnungen, Scopekollisionen und unsichere Versandwirkung.
Android-Instrumentationstests für tatsächlichen Keystore/Vault, Schema-v1-Migration und
große CursorWindow-Lesezugriffe sind vorbereitet. Diese müssen auf Hardware ausgeführt
werden; ein kompilierter Test ersetzt diesen Nachweis nicht. Details im
[Prüfprotokoll](../build-validation.md).
