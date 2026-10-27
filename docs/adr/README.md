# Architekturentscheidungen

Stand: 27.10.2026. ADR 0001 dokumentiert die Integrationsstrategie; ADR 0002–0008 sind angenommene Zielentscheidungen, keine Implementierungs- oder Releasebelege.

- [ADR 0001: Messengerabhängige Integrationswege statt einheitlichem Vollprovider-Versprechen](0001-messenger-integration-strategy.md)
- [ADR 0002: Modularer local-first Kotlin-Kern](0002-modular-local-first-core.md)
- [ADR 0003: Operationen und kontextabhängige Capabilities](0003-capability-provider-contract.md)
- [ADR 0004: Persistente Inbox/Outbox mit explizit unbekannter Versandwirkung](0004-durable-sync-and-outbox.md)
- [ADR 0005: Verschlüsselte Projektion und privater Medienstore](0005-encrypted-local-storage.md)
- [ADR 0006: Google-freier Hintergrundbetrieb mit sichtbaren Grenzen](0006-background-and-notifications.md)
- [ADR 0007: Security Boundary und optionale lokale Companions](0007-security-and-companion-boundary.md)
- [ADR 0008: Constructor Injection, strukturierte Coroutines und typisierte Fehler](0008-di-coroutines-and-errors.md)

- [ADR 0009: Kleine Android Foundation mit fixierter Toolchain](0009-android-foundation-toolchain.md)

Details: [Gesamtarchitektur](../architecture/README.md) und [Capability-Vertrag](../architecture/provider-contract.md).
