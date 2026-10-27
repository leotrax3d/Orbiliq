# ADR 0008: Constructor Injection, strukturierte Coroutines und typisierte Fehler

- Datum: 27.10.2026
- Status: Angenommen als Architekturentscheidung; noch nicht implementiert
- Grundlage: [Gesamtarchitektur](../architecture/README.md), [Providervertrag](../architecture/provider-contract.md), [ADR 0001](0001-messenger-integration-strategy.md)

## Kontext

Accountisolation und reproduzierbare Recoverytests brauchen kontrollierte Lifecycles, Zeit und Fehlerzustände.

## Entscheidung

Hilt nur für Android-Zusammenbau, explizite Accountfactories, reine Core-Ports. Injizierte Dispatcher/Clock/IDs; Supervisor je Account mit behandelten Fehlern. DB-Flow für Daten, StateFlow für Zustand, keine verlierbaren Streams für dauerhafte Events. Normalisierte Fehler bestimmen Retry/Blockierung/Abgleich; Cancellation wird weitergegeben.

## Verworfene Alternativen

Globale Service-Locator/GlobalScope; Runtime-Pluginladung; pauschales catch-and-retry und rohe Providerfehler in der UI.

## Folgen und Nachweise

Mehr explizite Lifecycles und Ports, dafür JVM-Tests ohne Android und deterministische virtuelle Zeit. Überlastung muss persistiert oder als Lücke behandelt werden.

Die konkreten Abläufe und Akzeptanzkriterien sind in der Gesamtarchitektur beschrieben. Es wurden in diesem Dokumentationspaket keine Laufzeittests ausgeführt.

## Revision

Erneut bewerten, wenn ein Implementierungsnachweis die Annahmen widerlegt oder neue Plattform-/Providerbedingungen eine Änderung erzwingen. Änderungen erhalten einen nachfolgenden ADR mit Verweis auf diese Entscheidung.
