# ADR 0002: Modularer local-first Kotlin-Kern

- Datum: 27.10.2026
- Status: Angenommen als Architekturentscheidung; noch nicht implementiert
- Grundlage: [Gesamtarchitektur](../architecture/README.md), [Providervertrag](../architecture/provider-contract.md), [ADR 0001](0001-messenger-integration-strategy.md)

## Kontext

UI und Messengerprotokolle dürfen sich nicht gegenseitig bestimmen; bislang existiert kein Appcode.

## Entscheidung

Gerichtete Gradle-Module mit reinem Kotlin-Modell/Domain, Repository-Ports und lokaler UI-Lesequelle. Compose/Material 3 Expressive bleibt im UI. Der Composition Root registriert optionale Provider. Kein erforderlicher Orbiliq-Server oder Play-Services-Paket.

## Verworfene Alternativen

Monolith mit Protokollzugriff aus ViewModels sowie Backend-zentrierte Bridge als Pflicht.

## Folgen und Nachweise

Mehr Mapping und klarer Schema-/Modulbesitz; Account- und Timelineherkunft bleiben erhalten. Neue Provider ändern nur Adapter und Registrierung, solange die Semantik im Vertrag liegt.

Die konkreten Abläufe und Akzeptanzkriterien sind in der Gesamtarchitektur beschrieben. Es wurden in diesem Dokumentationspaket keine Laufzeittests ausgeführt.

## Revision

Erneut bewerten, wenn ein Implementierungsnachweis die Annahmen widerlegt oder neue Plattform-/Providerbedingungen eine Änderung erzwingen. Änderungen erhalten einen nachfolgenden ADR mit Verweis auf diese Entscheidung.
