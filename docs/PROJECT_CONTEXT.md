# Projektkontext: Orbiliq

Stand: 27. Oktober 2026.

## Produkt und verbindlicher Rahmen

Orbiliq ist als lokale, datenschutzorientierte Unified-Messenger-App für Android geplant. Kotlin, Jetpack Compose, Material 3 Expressive, Coroutines/Flow und eine modulare Providerarchitektur sind vorgegeben. Eigener Projektcode soll MIT-lizenziert sein. Veröffentlichung zunächst über GitHub Releases. Keine verpflichtenden Google Play Services, keine obligatorische Orbiliq-Cloud, keine Tracker oder Werbe-SDKs.

Ziel sind eine gemeinsame Chatliste, Kontakte/Identitäten und möglichst weitgehendes Lesen, Empfangen und Senden über vorhandene Messenger. Orbiliq ist kein eigenes Kommunikationsnetz. Funktionsgrenzen und Datenschutz haben Vorrang vor vollständigen Funktionsversprechen. Ein grundlegendes Expressive-Theme und ein erklärter Inbox-Leerzustand sind angelegt; die eigentlichen Produktansichten sind noch nicht gestaltet.

## Tatsächlicher Repository-Stand

Vor dem ersten Arbeitspaket war das Repository leer. Vorhanden sind Recherche, Gesamtarchitektur/ADRs und jetzt eine Android Foundation mit Gradle Wrapper, Version Catalog, Kotlin-/Compose-Modulen, Hilt-Zusammenbau, Tests und CI-Konfiguration. **Keine produktiven Provider, Konten, Nachrichtenspeicherung oder Versandfunktionen.** Der konkrete Build-/Testnachweis steht in [Buildvalidierung](build-validation.md). Die gewünschte MIT-Lizenz ist eine Projektvorgabe; durch diese Dokumentation wurde keine bestehende Fremdlizenz geändert und keine Gesamtpaket-Lizenzfreigabe erteilt.

## Ergebnisse des ersten Arbeitspakets

Vollständige [Machbarkeitsanalyse und Entscheidungsmatrix](research/README.md), Einzelanalysen für Signal, Telegram, WhatsApp und Threema, Architektur-/Hintergrundkonzept, Lizenz-/Risikodossier, Quellenregister sowie Revisions-/Dateinachweise unter `docs/research/`.

- Telegram: TDLib über Java/JNI als erste vollständige Integrationsbasis auswählen. Cloud-Chats sind nicht E2EE, Secret Chats gerätegebunden; Channels haben API-/Sponsored-Messages-Pflichten.
- Signal: bedingter separater Android-Companion auf vorhandener Clientbasis; Lizenz-/Distributionsgrenze und Linked-Device-Betrieb erst nachweisen. signal-cli ist kein direktes Android-SDK.
- WhatsApp: vollständiger persönlicher Zugang bleibt experimentell. whatsmeow bevorzugter technischer Prototypkandidat, Baileys Alternative; beide enthalten konkret geprüfte GPL-Kryptoabhängigkeiten trotz MPL-/MIT-Hauptlizenz. Cloud API und DMA sind andere Produktmodelle.
- Threema: zunächst lokale Ergänzung mit Original-App. Neue Desktop-Beta unterstützt Linking von Android aus, ist aber kein freigegebenes Android-Drittclient-SDK. Clientcode und libthreema AGPL; Gateway ist eine separate Identität.

[ADR 0001](adr/0001-messenger-integration-strategy.md) hält die Entscheidung fest. Alle künftigen Provider benötigen unterschiedliche Capabilities, sichtbare Sync-/Authzustände und eine lokale, sichere Datenhaltung. Eine gemeinsame Timeline erhält Providerherkunft und E2EE-Unterschiede; Kontaktzuordnungen sind korrigierbar.

## Ergebnisse des zweiten Arbeitspakets

Die [technische Gesamtarchitektur](architecture/README.md) definiert Modulgraph, Domain/Data/Provider/UI, persistente Sync Engine, Room/SQLCipher, geschützten Medienstore, Notifications, Background Processing, Security Boundary sowie Hilt, Coroutines/Flow und Fehlerbehandlung. Der [Providervertrag](architecture/provider-contract.md) beschreibt versionierte Commands/Events und kontextabhängige Operationsfähigkeiten einschließlich aller geplanten Sonderfunktionen. Gemeinsame Geschäftslogik entscheidet anhand von Capabilities, nicht anhand des Messengernamens.

[ADR 0002–0008](adr/README.md) dokumentieren die angenommenen Zielentscheidungen. Wichtig: unbekannte Versandwirkung wird abgeglichen statt blind erneut gesendet; Previewadapter erhalten keine Vollproviderrechte; App-Lock und strenger Schlüssel-Lock haben verschiedene Hintergrundgrenzen. Die damaligen Modul-/API-Namen waren Entwurf; die inzwischen realisierten Teilmodule sind in der Foundation-Dokumentation aufgeführt. Dokumentationsverweise und Abdeckung wurden geprüft; in diesem damaligen Arbeitspaket keine Build-, Geräte- oder Kontotests ausgeführt.

## Ergebnisse des dritten Arbeitspakets

[Android Foundation](architecture/foundation.md): `app`, `core:model`, `core:domain`,
`core:provider-api`, `core:data`, `core:designsystem`, `feature:inbox`.
Eine echte Activity zeigt einen erklärten Leerzustand, Hilt liefert das noch unkonfigurierte
Repository. StateFlow/ViewModel, lifecyclebewusste Compose-Beobachtung, Expressive Theme,
Dynamic Color und Edge-to-edge bilden die UI-Grundlage. Keine Fakechats oder Login-Attrappen.
MIT, README, CONTRIBUTING, SECURITY, Gitignore, Dependency-Management und GitHub-CI sind angelegt.
Weitere Zielmodule werden erst mit realen Implementierungen ergänzt. Konkrete Testresultate
und verbleibende Infrastrukturgrenzen stehen in `docs/build-validation.md`.

## Bekannte Grenzen und nächste Arbeitspakete

Technische Funktionsbewertungen sind aus Dokumentation/Code abgeleitet, nicht praktisch bestanden. Vollständige transitive Lizenzprüfung, Rechtsbewertung der konkreten Distribution, Geräte-/Account-/Hintergrund-/Medientests und Sicherheitsreview stehen aus. Android garantiert keinen sofortigen Empfang in jedem Zustand. Notification-Adapter liefern Ausschnitte und bedingte Antworten, keine vollständigen Chats.

Empfohlener nächster Schritt ist ein begrenzter Telegram-/TDLib-Machbarkeitsprototyp mit reproduzierbarem Android-Build und Google-freiem Login-/Sync-/Sessionnachweis. Dieser Schritt ist **noch nicht beauftragt oder implementiert**. Weitere Companion-Prototypen erst nach den in der Recherche dokumentierten Freigabebedingungen verfolgen.

Bei späteren Arbeiten zuerst Repository und aktuelle Anweisungen lesen; Recherchequellen bei zeitabhängigen Aussagen erneut prüfen. Projektkontext beschreibt den Stand und ersetzt keine Prüfung tatsächlicher Dateien.
