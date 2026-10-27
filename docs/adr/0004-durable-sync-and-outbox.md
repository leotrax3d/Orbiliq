# ADR 0004: Persistente Inbox/Outbox mit explizit unbekannter Versandwirkung

- Datum: 27.10.2026
- Status: Angenommen; erste Implementierung in core:data (siehe Sync-Engine-Dokumentation)
- Grundlage: [Gesamtarchitektur](../architecture/README.md), [Providervertrag](../architecture/provider-contract.md), [ADR 0001](0001-messenger-integration-strategy.md)

## Kontext

Prozessabbruch und Timeouts können nach Remoteeffekt auftreten. Flow und Netzwerkstatus belegen weder Persistenz noch Zustellung.

## Entscheidung

Lokale Transaktionen für Aufträge/Projektion, Replay/Deduplizierung und Checkpoints; accountgebundene Koordinatoren mit Leases. Retry nur bei sicher fehlendem Effekt oder nachgewiesener Idempotenz; sonst OutcomeUnknown und Reconciliation. Persistente Providerqueue übernimmt Retry erst nach belegter Übernahme.

## Verworfene Alternativen

Exactly-once über alle Dienste versprechen; alle Timeouts erneut senden; Flow als einziges Journal.

## Folgen und Nachweise

Mehr Zustände und Recoverylogik. BestEffort-Provider haben sichtbare Verlust-/Historiengrenzen; ein Gerätetest muss jeden kritischen Crashpunkt prüfen.

Die konkreten Abläufe und Akzeptanzkriterien sind in der Gesamtarchitektur beschrieben. Es wurden in diesem Dokumentationspaket keine Laufzeittests ausgeführt.

## Revision

Erneut bewerten, wenn ein Implementierungsnachweis die Annahmen widerlegt oder neue Plattform-/Providerbedingungen eine Änderung erzwingen. Änderungen erhalten einen nachfolgenden ADR mit Verweis auf diese Entscheidung.

## Implementierungsstand Prompt 7

Persistente Queue, Commit vor Dispatch/ACK, Backoff und Reconciliation sind umgesetzt.
Accountbesitz wird aktuell durch genau einen App-Scope-Koordinator pro Store gewährleistet;
prozessübergreifende Leases und Android-Scheduler sind noch nicht implementiert.
Siehe [Sync Engine](../architecture/sync-engine.md) für Vertrag und Grenzen.
