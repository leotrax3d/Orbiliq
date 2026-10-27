# Domain Model und Provider API

Stand: 27.10.2026. Implementierter Kotlin/JVM-Vertrag, keine Messengerintegration.

## Modellgrenzen

`core:model` enthält ausschließlich Kotlin/JDK-Werte und Regeln:

| Begriff | Identität und Verantwortung |
|---|---|
| Provider | Offene `ProviderId`; ein Adaptertyp, kein Messenger-Enum |
| Account | Lokale `AccountId`, Providerzuordnung und eigene optionale Identity; keine Sessionsecrets |
| Person | Lokale reale Person mit eigener `PersonId` |
| MessengerIdentity | Lokale `IdentityId` plus accountgebundene `IdentityRef`; unabhängig von Person |
| PersonIdentityLink | Reversible lokale Beziehung mit Herkunft; Links schreiben keine Routen um |
| Conversation | Lokale `ConversationId` plus accountgebundene `ConversationRef`, Typ, Teilnehmer, optionale Gruppe/Parent |
| Group | Eigene `GroupId`/`GroupRef`, Mitglieder und normalisierte Rollen; keine Namensfusion |
| Message | Stabile `MessageId`, lokale Conversation, Originalroute, Sender, Inhalt, optionale Remote-Referenz |
| Attachment | Eigene `AttachmentId`, Account, Medienart, Metadaten, Policy und optionale Remote-Referenz |

IDs sind getrennte Value Classes mit nichtleeren opaque Werten. Neue lokale IDs können
über UUID-Generatoren erzeugt werden; für deterministische Tests lassen sich Werte direkt
konstruieren. Telefonnummern, Anzeigenamen und Nachrichtentexte sind keine Schlüssel.
Remote-IDs werden weder geparst noch normalisiert. Account-IDs sind global lokal eindeutig;
Remote-Nachrichten sind zusätzlich nach Conversation getrennt.

`IdMapping<Remote, Local>` beschreibt den Persistenzport. `StableIdIndex` ist eine
synchronisierte In-Memory-Referenzimplementierung: atomare Bindung, Konfliktablehnung,
idempotente Wiederholung und mehrere Aliase pro lokaler ID. Temporäre und bestätigte
Provider-IDs bleiben derselben lokalen ID zugeordnet. Sie ist **keine dauerhafte DB**;
eine spätere DB muss dieselben Regeln mit Unique Constraints/Transaktionen über Neustarts
sichern. Getrennte typisierte Indizes für Identity-, Conversation-, Message-, Group- und
Attachment-Referenzen verwenden. Remote-Account-IDs können adapterintern als Alias auf
`AccountId` geführt werden; der Core behandelt die lokale Session als Kontogrenze.

`MessageContent` hat Text-, Media-, Service- und Unsupported-Varianten. Die offene
`MessageType` und versionierte `ProviderData` bewahren neue Inhaltsarten. Neue gemeinsame
Semantik wird additiv als Variante eingeführt; ein alter Core behält Unsupported samt
opaque Daten. ProviderData ist defensiv kopiert und gibt seinen Payload nicht in
`toString` aus. Namespaces und Versionen gehören dem Adapter. Daten sind dennoch sensibel:
verschlüsselt speichern, nicht loggen, begrenzen und validieren; keine Credentials dort
ablegen. Der API-Typ ist kein Ersatz für künftige IPC-Payloadlimits/Codecvalidierung.

Messages behalten Server-/Empfangszeit, Reihenfolge, Revision, Reply-ID, Qualität,
Zustellstatus, Sicherheitskontext und ContentPolicy. Timeline-Sortierung nutzt Anzeigezeit
und stabile lokale ID; keine globale Kausalität. Route und Senderkonto müssen übereinstimmen.
Export und Cache sind bei unbekannter Policy zunächst gesperrt. Ein View-once-Flag allein
implementiert noch keine sichere Medienbehandlung.

## Capabilities

`OperationKey(name, schemaVersion)` ist offen. Bekannte Operationen sind Hilfskonstanten,
keine abgeschlossene Liste. Empfang, Versand, Preview und QuickReply sind getrennt.
Snapshots tragen Scope, Revision, Auswertungs-/Ablaufzeit und OperationSupport mit Reife,
Replay, Qualität und Security. `CapabilityPolicy.evaluate` verlangt die vollständige
Scope-Kette in Reihenfolge Provider → Account → Conversation → Message. Fehlende,
fremde, zukünftige oder abgelaufene Snapshots erlauben nichts. Ein Kind kann keine
Elternsperre aufheben. Jeder Constraint wird unabhängig geprüft, ohne generisches Merge.
Unbekannte Operationen/Versionen sind Unsupported, unbekannte Schutzanforderungen sperren.
Bei gesetzten Limits sind fehlende Requestparameter ebenfalls gesperrt. Textlängen
werden in UTF-16-Codeeinheiten angegeben; ein Adapter muss Backendlimits entsprechend
konservativ abbilden oder zusätzlich selbst prüfen.

Die Policy liefert eine Entscheidung für genau den übergebenen Request. Das erfolgreiche
Ergebnis ersetzt keine Snapshotkette und ist keine wiederverwendbare Berechtigung.
Offlinequeue muss auf jeder Ebene explizit gestattet sein. Eigene Privacy-/Releasepolitik
muss vor Freigabe zusätzlich eingeschränkt werden; Experimental ist keine Produktfreigabe.

## Provider-Vertrag

`core:provider-api` hängt nur von Model und Coroutines ab. `ProviderRegistry` registriert
explizite Factories anhand offener IDs und lehnt Duplikate/inkompatible Vertragsversionen
ab. Descriptor: Runtime-/Contractversion, statisches Potenzial und Replayklasse.
`ProviderFactory.open` erzeugt eine isolierte Accountsession. `AccountHandle` enthält nur
Account und opaque Vaultreferenz. Auth/Verbindung/Sync sind unabhängig beobachtbar.

Die Session besitzt Flow-Events, Capabilitybeobachtung/-auswertung, typisierte Commands,
Acknowledgement, Reconciliation, Auth und explizites Close. Commands tragen stabile ID,
Route, Version, Frist, Capabilityrevision, Abhängigkeiten und optionalen IdempotencyKey.
Replies/Edits/Delete/Reactions/Receipts dürfen keine andere Route referenzieren. Aktuell
sind häufige Messaging-Payloads und Logout typisiert. Weitere Gruppen-, History-, Calls-
oder Status-Payloads werden bei tatsächlicher Implementierung additiv ergänzt; der offene
Operationskatalog behauptet keine Implementierung dieser Funktionen.

`CheckedProviderSession` bietet wiederverwendbare Prüfung unmittelbar vor Dispatch:
Account, Vertragsversion, Fristen, statisches Potenzial, aktuelle dynamische Availability
und Payloadconstraints. Änderungen an Nachrichten werden im Message-Scope geprüft.
Unbekannte Erweiterungen brauchen explizite Registrierung im statischen Potenzial.
Cursor-/Eventkonto, Epoche und Eventversion werden geprüft; Stream-/Tokenvalidierung bleibt
beim Adapter. Auth-Challenges werden nach ID, Typ, Version und Ablauf geprüft; Secrets
sind nicht Teil von toString und können explizit überschrieben werden. Aufrufer müssen
Secretkopien und Response-Lebensdauer selbst begrenzen.

Adapter müssen die Session am Composition Root mit dem Guard umschließen und weiterhin
Protokollrechte unmittelbar bei der Operation prüfen. Evaluate ist keine Reservierung.
Account-Lifecycle/Close werden vom späteren Sessionkoordinator serialisiert; der Guard
ist kein Ersatz für strukturiertes Ressourcenmanagement. Beobachtung selbst ist keine
Ausführungsberechtigung. Bei fehlenden Medienparametern in einer generischen Dispatch-
Prüfung wird ein gesetzter Größen-/MIME-Constraint abgelehnt; vollständige Mediencommands
und deren Validierung folgen mit dem Medienpfad.

Dispatch unterscheidet Rejected, RetryableNoEffect, Accepted, Confirmed, OutcomeUnknown.
Accepted setzt eine nachweislich persistente Providerqueue voraus. Cancellation bleibt
Cancellation; nach möglichem externem Effekt muss die künftige Sync Engine den dauerhaft
markierten InFlight-Auftrag reconciliieren. Keine automatische Wiederholung unbekannter
Wirkung. Eventcursor enthält Account/Stream/Epoche; Event-ID bleibt beim Replay stabil.
Upserts können Edits mit Revision darstellen, Deletes sind eigene Tombstone-Ereignisse.
MediaSource/MediaSink definieren einen getrennten streambaren Medienport ohne Androidtypen;
Chunkgrenzen, EOF (leeres Array), Ressourcenbesitz und Backpressure sind vom Adapter
zu beachten. Der Aufrufer schließt Quelle/Senke auch bei Fehler oder Cancellation.

## Nachweise und Grenzen

JVM-Tests prüfen IDvalidierung, Aliaswechsel/-konflikte, Account-/Conversationgrenzen,
Person-/Identitytrennung, unbekannte Inhalte, defensive Payloadkopien, Gruppen/Topics,
Timeline, Sicherheitswerte und Capabilityhierarchien einschließlich Widerruf, Fristen,
Constraints und Offlinequeue. Synthetische Text-/Previewprovider prüfen den offenen
Vertrag; Guardtests prüfen tatsächliches Verhindern von Dispatch, Message-Scope,
Cancellation, Replaygrenzen, Authablauf und Close.

Keine produktive Providerregistrierung, kein Netzwerk, kein persistenter Store, keine
Merge-/Unmerge-Implementierung, Outbox, Codec oder Medienengine in diesem Arbeitspaket.
`PeopleRepository` definiert den lokalen, reversiblen Zuordnungsport; dessen Transaktionen
werden mit dem Data Layer implementiert. Reale Adapter benötigen weiterhin eigene
Contract-/Geräte-/Account-/Sicherheitsnachweise. Buildnachweis: [Prüfprotokoll](../build-validation.md).
