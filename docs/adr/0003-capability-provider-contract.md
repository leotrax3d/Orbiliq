# ADR 0003: Operationen und kontextabhängige Capabilities

- Datum: 27.10.2026
- Status: Angenommen als Architekturentscheidung; noch nicht implementiert
- Grundlage: [Gesamtarchitektur](../architecture/README.md), [Providervertrag](../architecture/provider-contract.md), [ADR 0001](0001-messenger-integration-strategy.md)

## Kontext

Messenger haben unterschiedliche Funktionen, Rechte, Historie und Integrationsqualität. Ein globales supportsMessages reicht nicht.

## Entscheidung

Versionierter Providervertrag mit offenen ProviderIds, typisierten Commands/Events und Capabilityprüfung je Account, Conversation, Message und Payload. Default deny für unbekannte Operationen/Constraints; Prüfung bei Enqueue und Execute. Calls, Vorschauen und Vollprovider bleiben unterscheidbar.

## Verworfene Alternativen

Provider-Enums mit verteilten Namensvergleichen; kleinster gemeinsamer Nenner; untypisierte universelle RPC-Maps.

## Folgen und Nachweise

Mehr Vertrags- und Conformancearbeit. Änderungen sind additiv und ausgehandelt; UI kann fehlende Rechte erklären. Ein Text-only-Provider braucht keinen Umbau des Kerns.

Die konkreten Abläufe und Akzeptanzkriterien sind in der Gesamtarchitektur beschrieben. Es wurden in diesem Dokumentationspaket keine Laufzeittests ausgeführt.

## Revision

Erneut bewerten, wenn ein Implementierungsnachweis die Annahmen widerlegt oder neue Plattform-/Providerbedingungen eine Änderung erzwingen. Änderungen erhalten einen nachfolgenden ADR mit Verweis auf diese Entscheidung.
