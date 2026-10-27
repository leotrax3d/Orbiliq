# ADR 0006: Google-freier Hintergrundbetrieb mit sichtbaren Grenzen

- Datum: 27.10.2026
- Status: Angenommen als Architekturentscheidung; noch nicht implementiert
- Grundlage: [Gesamtarchitektur](../architecture/README.md), [Providervertrag](../architecture/provider-contract.md), [ADR 0001](0001-messenger-integration-strategy.md)

## Kontext

Android kann Prozesse und Netzzugriff begrenzen; weder WorkManager noch ein beliebiger FGS-Typ garantiert Echtzeitempfang.

## Entscheidung

WorkManager für persistente nachholbare Arbeit; aktive Verbindungen im zulässigen Lifecycle, FGS nur bei belegter Eignung. Push ist optional und providerspezifisch. Notifications folgen durable Projektion; Actions durchlaufen Domainvalidierung und Outbox. Previewadapter bleiben separat.

## Verworfene Alternativen

FCM als Pflicht; WorkManager als permanenter Socketdienst; Notificationvorschauen als vollständige Chats darstellen.

## Folgen und Nachweise

Empfangsfrische und Einschränkungen müssen sichtbar sein. Doze/Force-stop/OEM-Verhalten wird praktisch gemessen; kein allzuständiges Echtzeitversprechen.

Die konkreten Abläufe und Akzeptanzkriterien sind in der Gesamtarchitektur beschrieben. Es wurden in diesem Dokumentationspaket keine Laufzeittests ausgeführt.

## Revision

Erneut bewerten, wenn ein Implementierungsnachweis die Annahmen widerlegt oder neue Plattform-/Providerbedingungen eine Änderung erzwingen. Änderungen erhalten einen nachfolgenden ADR mit Verweis auf diese Entscheidung.
