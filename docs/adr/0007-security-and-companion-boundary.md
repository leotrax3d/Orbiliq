# ADR 0007: Security Boundary und optionale lokale Companions

- Datum: 27.10.2026
- Status: Angenommen als Architekturentscheidung; noch nicht implementiert
- Grundlage: [Gesamtarchitektur](../architecture/README.md), [Providervertrag](../architecture/provider-contract.md), [ADR 0001](0001-messenger-integration-strategy.md)

## Kontext

Provider entschlüsseln Nachrichten; Native-Code und optionale externe APKs erzeugen zusätzliche Vertrauensgrenzen. App-Lock und Hintergrundentschlüsselung können verschiedene Schutzziele haben.

## Entscheidung

Protokollschlüssel bleiben beim Backend; keine eigene Messengerkryptografie. In-process-Provider gelten als voll vertrauenswürdig. Companions verwenden nutzerbestätigtes Pairing, gegenseitige UID-/Zertifikatsprüfung, begrenzte versionierte Binder-IPC und kurzlebige Medienhandles. Standard-UI-Lock erlaubt Hintergrundsync, optionaler strenger Schlüssel-Lock schränkt diesen ein.

## Verworfene Alternativen

Ungeschütztes Localhost-RPC; Paketname als Authentisierung; Prozessgrenze als Lizenzfreigabe; nicht exportierbare Keystore-Protokollschlüssel pauschal versprechen.

## Folgen und Nachweise

Pairing, Widerruf und Keyhandling benötigen Review und Angriffstests. Copyleft-/Distributionsprüfung aus ADR 0001 bleibt unabhängig und offen. Keine vollständige Abwehr eines kompromittierten OS.

Die konkreten Abläufe und Akzeptanzkriterien sind in der Gesamtarchitektur beschrieben. Es wurden in diesem Dokumentationspaket keine Laufzeittests ausgeführt.

## Revision

Erneut bewerten, wenn ein Implementierungsnachweis die Annahmen widerlegt oder neue Plattform-/Providerbedingungen eine Änderung erzwingen. Änderungen erhalten einen nachfolgenden ADR mit Verweis auf diese Entscheidung.
