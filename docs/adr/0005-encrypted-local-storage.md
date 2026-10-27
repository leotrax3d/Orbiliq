# ADR 0005: Verschlüsselte Projektion und privater Medienstore

- Datum: 27.10.2026
- Status: Angenommen als Architekturentscheidung; noch nicht implementiert
- Grundlage: [Gesamtarchitektur](../architecture/README.md), [Providervertrag](../architecture/provider-contract.md), [ADR 0001](0001-messenger-integration-strategy.md)

## Kontext

Nachrichtenkopien, Suchindex und Thumbnails sind ebenso sensibel wie Sessions. TDLib-eigene Verschlüsselung schützt nicht automatisch alle Orbiliq-Daten.

## Entscheidung

Room mit aktuellem SQLCipher-Android für Projektion/FTS/Journal/Outbox; Keystore-geschützte zufällige Datenschlüssel. Privater Streaming-AEAD-Medienstore mit separaten Objektschlüsseln und accountgebundenen Referenzen. Keine sensiblen automatischen Backups. DataStore nur für harmlose Einstellungen.

## Verworfene Alternativen

Klartext-Room plus alleiniger App-Lock; ungeschützter externer Mediencache; eigene Verschlüsselungsalgorithmen.

## Folgen und Nachweise

Native Build-/Migrations- und Keymanagementaufwand. SQLCipher-/Room-Kombination und Medienbibliothek müssen im Prototyp geprüft werden. Schlüsselverlust kann Daten unlesbar machen; Export/Backup bleibt eigenes Vorhaben.

Die konkreten Abläufe und Akzeptanzkriterien sind in der Gesamtarchitektur beschrieben. Es wurden in diesem Dokumentationspaket keine Laufzeittests ausgeführt.

## Revision

Erneut bewerten, wenn ein Implementierungsnachweis die Annahmen widerlegt oder neue Plattform-/Providerbedingungen eine Änderung erzwingen. Änderungen erhalten einen nachfolgenden ADR mit Verweis auf diese Entscheidung.
