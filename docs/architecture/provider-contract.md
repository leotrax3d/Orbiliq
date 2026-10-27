# Providervertrag und Capability-Katalog

Stand: 27.10.2026. Normativer Zielvertrag; Kotlin-Beispiele sind Signaturskizzen. Der inzwischen kompilierte Kernvertrag und seine bewusst noch offenen Funktionsfamilien sind unter [Domain Model und Provider API](domain-model.md) beschrieben. [Gesamtarchitektur](README.md).

## Vertrag und Lebenszyklus

Ein `ProviderFactory` besitzt einen Descriptor (offene ProviderId, Name/Branding, Runtimeversion, Vertragsversion und statisches Potenzial) und erzeugt eine accountgebundene `ProviderSession`. Eine neue Generation derselben Session besitzt eine eigene Epoche; alte Cursor und Commands werden nicht unkontrolliert übernommen. Mehrere Konten desselben Providers sind unabhängig.

```kotlin
interface ProviderFactory {
    val descriptor: ProviderDescriptor
    suspend fun open(account: AccountHandle): ProviderSession
}
interface ProviderSession {
    val state: StateFlow<ProviderState>
    fun observeCapabilities(scope: CapabilityScope): Flow<CapabilitySnapshot>
    suspend fun evaluate(request: OperationRequest): Availability
    fun events(after: ReplayCursor?): Flow<EventEnvelope>
    suspend fun acknowledge(cursor: ReplayCursor): OperationResult<Unit>
    suspend fun execute(command: ProviderCommand): DispatchResult
    suspend fun reconcile(request: ReconcileRequest): ReconcileResult
    suspend fun authenticate(response: AuthResponse): AuthResult
    suspend fun close()
}
```

`close` beendet Ressourcen, nicht die Remoteautorisierung. Logout/Revoke ist ein expliziter Command. AccountHandle enthält lokale IDs und opaque Vaultreferenzen, keine frei auslesbaren Fremdschlüssel. Media-Transfer verwendet streambare Handles über einen separaten, accountgebundenen Port; keine Android-URI-/Binder-Typen in der Kotlin-API. Optionale typisierte Ports für Messaging, History, Media, Groups, EphemeralContent und Calls können intern getrennt implementiert werden. `execute` ist ein versionierter Dispatcher über deren typisierte Commands, kein `Map<String, Any>`-RPC.

`ProviderState` enthält unabhängig voneinander Auth (`NeedsInput`, `Ready`, `Revoked`, `Blocked`), Verbindung (`Offline`, `Connecting`, `Online`), Sync (`Idle`, `CatchingUp`, `Gap`, `Degraded`), letzte durable Verarbeitung und Betriebsvoraussetzungen. „Online“ bedeutet weder vollständige Historie noch erfolgreiche Anmeldung in jedem Feature.

Auth-Challenges sind versionierte Varianten für Code/Passwort/QR/Bestätigung/externen Schritt mit Ablauf und zulässigen Antworten; Geheimnisse werden nicht in Projektion oder Logs persistiert. Unbekannte Varianten führen zu UpdateRequired. Zurückgezogene oder abgelaufene Challengeantworten werden verworfen. Kein fest vorausgesetzter SMS-Login.

## Capability-Semantik

Eine Capability ist eine **Operation**, etwa `message.text.send`, mit versioniertem Schema. Kritische Operationsfamilien verwenden typisierte Requests/Payloads; offene IDs ermöglichen additive Erweiterungen. Unbekannte IDs sind für alte Clients Unsupported; sicherheitsrelevante unbekannte Constraints dürfen nicht ignoriert werden. Basisvertrag und Payloadversionen werden beim Handshake ausgehandelt, inkompatible Versionen blockiert.

```kotlin
sealed interface Availability {
    data class Available(val constraints: OperationConstraints) : Availability
    data class Restricted(val reason: Restriction, val retryAt: Instant?) : Availability
    data class Unsupported(val reason: String) : Availability
    data object Unknown : Availability
}
data class CapabilitySnapshot(
    val scope: CapabilityScope,
    val revision: String,
    val evaluatedAt: Instant,
    val expiresAt: Instant?,
    val operations: Map<OperationKey, OperationSupport>
)
```

Scopes: Provider → Account → Conversation → Message; Payloadparameter (z. B. Dateigröße oder Emoji) kommen bei evaluate hinzu. Effektive Unterstützung ist die Schnittmenge aus Adapterimplementierung, Backendfunktion, freigegebener Reife, Nutzerprivacy, aktuellen Rechten, Fristen und konkretem Payload. Ein Kindscope kann fehlende Elternunterstützung nicht freischalten. Der Provider ist zuständig für Protokollrechte, der Core für eigene Privacy-/Releasepolitik; Constraints werden nicht durch ein ungeprüftes generisches Map-Merge kombiniert.

Snapshots enthalten zusätzlich Richtung/Umfang, Einschränkungen, Reife (`Experimental`, `Verified`), Datenqualität (`PreviewOnly`, `SinceCheckpoint`, `PartialHistory`, `ProviderHistory`), Replayklasse, Sicherheitskontext und Voraussetzungen. Datenqualität ist keine Versandberechtigung. Lokale Offlineanzeige bleibt bei abgelaufenen Capabilities möglich. Offline-Enqueue ist nur gestattet, wenn ein gültiges Potenzial und eine ausdrücklich erlaubte persistente Commandart vorliegen; Ausführung wartet auf aktuelle Prüfung. `Restricted(Offline)` unterscheidet sich von Unsupported.

UI blendet dauernd unmögliche Operationen aus und erklärt relevante vorübergehende Sperren. Domain und Provider erzwingen dieselben Regeln auch bei Notificationactions oder alten Screens. `evaluate` ist keine reservierte Berechtigung: Execute kann wegen zwischenzeitlicher Rechteänderung erneut ablehnen.

## Operationskatalog

| Familie | Getrennte Operationen und notwendige Constraints |
|---|---|
| Text | receive/send; maximale Länge/Entities, Zieltypen, persistentes Offline-Enqueue |
| Medien | receive/download/upload/send je Bild, Video, Datei, Audio, Voice Note; MIME/Größe/Dauer, Caption, Album, Resume, Export und Cachepolitik |
| Antworten | quoted-reply.send/receive; Originalreferenz und Thread; getrennt von notification.quick-reply |
| Reaktionen | receive/add/remove; erlaubtes Set, Custom Emoji, Anzahl, eigene/fremde Reaktion |
| Edit | text/caption/media edit getrennt; Eigentümer, Deadline, Revision und erlaubte Felder |
| Delete | local.remove, remote.deleteSelf, remote.deleteAll; Rechte, Frist, Ziel-/Geräteumfang |
| Forward | expliziter Forward mit Herkunft versus lokale Kopie; Schutz-/Exportregeln |
| Gruppen | messages.read/send, create, invite, leave, members.read, roles.change, metadata.edit, topics; aktuelle Rolle und Einladungsregeln |
| Presence | observe/publish getrennt; Privatsphäre, Sichtbarkeitsumfang, TTL; unknown ist nicht offline |
| Typing | observe/send getrennt; Actiontypen und TTL, keine Offlinequeue |
| Read Receipts | observe/send getrennt; Einzel-/Gruppenumfang und Privacy; delivered separat, nicht mit read gleichsetzen |
| Suche | local.cached (Coreleistung), provider.remoteSearch; Filter, Paging, Historyumfang und Securitygrenzen |
| Status | profile.status.read/set für Text-/Emoji-/Profilstatus; kein automatisches Gleichsetzen mit Stories |
| Stories | list/read/publish/reply/react/delete; Audience, Ablauf, View-Receipt und Mediengrenzen |
| Channels | discover/subscribe/read/publish/admin; Rollen, Kommentare/Topics und geltende Inhaltspflichten |
| Calls | observe-signaling, handoff, start-audio, start-video, accept, reject, end; 1:1/Gruppe, Medienstack, Berechtigungen, Lifecycle |
| History/Sync | initial-load, page-back, reconcile, replay, own-outgoing-sync; Cursorumfang, Lücken, Gerätegebundenheit |
| Integration | notification.preview, notification.quick-reply, app.handoff; gültige Aktionslease, Original-App und fehlende Versandbestätigung |

Ein Call-Event schaltet niemals Callteilnahme frei. Unterstützung von Empfang bedeutet nicht Versand. Lokales Löschen oder lokale Suche sind Fähigkeiten des Cores über dessen Bestand und keine behaupteten Remoteoperationen. Neue Nachrichten beginnen, existierende Nachrichten beantworten und Handoff sind getrennte Operationen.

## Commands, Events und Zustellsemantik

Commands enthalten CommandId, AccountId, Zielreferenz, typisierten Payload, Abhängigkeiten, Erstellzeit, Ablauf, Capabilityrevision und optional einen vom Backend unterstützten IdempotencyKey. Der Provider muss ungeeignete Idempotenz ausdrücklich ablehnen; ein lokaler UUID-String bewirkt keine Serverdeduplizierung.

DispatchResult unterscheidet `Rejected` (kein Effekt), `RetryableNoEffect`, `Accepted` (dauerhaft übernommen, mit Quittungsreferenz), `Confirmed` (belegter Dienstzustand) und `OutcomeUnknown`. Nur Accepted erlaubt Übergabe des Retrybesitzes an eine nachgewiesen persistente Providerqueue. Bloßes Einreihen in RAM wird nicht als Accepted zurückgegeben. Eigene ausgehende Syncnachrichten werden über Quittungs-/Client-/Remote-ID dem vorhandenen lokalen Auftrag zugeordnet. Gleicher Text und Zeitnähe sind keine ausreichende Deduplizierung.

EventEnvelope enthält schemaVersion, AccountId, StreamId, Epoche, EventId, Cursor, optionale Providerrevision, occurredAt/receivedAt und typisierten Payload. Beispiele: MessageUpserted, MessageEdited, MessageDeleted, ReactionChanged, ReceiptChanged, ConversationChanged, IdentityChanged, CapabilitiesChanged, GapDetected, AuthChanged. Protokoll-Rohdaten bleiben beim Adapter; UnsupportedContent enthält nur begrenzte sichere Metadaten.

Events sind mindestens einmal verarbeitbar, falls Replay vorhanden ist. EventId/Cursor müssen nach Wiederanlauf stabil bleiben. Wo dies technisch unmöglich ist, deklariert der Provider BestEffort und einen Reconciliationumfang. Cursor gelten nur für den dokumentierten Stream/Epoche. Replay-Backpressure, maximale Batchgrößen und Ackpunkt sind Teil des Adaptervertrags. Nach unbekannter Epoche wird reconciled, kein Cursor blind fortgeschrieben.

## Erweiterungen und Abnahmeregel

Provider dürfen versionierte, namespaced Zusatzoperationen anbieten. Ein alter Core zeigt höchstens einen erklärenden Hinweis/Handoff; er führt unbekannte Commands nicht aus. Neue Standardsemantik benötigt einen additiven Vertrag mit Migration und Fallback, nicht einen Messengervergleich im Kern. Branding und Registrierung dürfen ProviderIds verwenden; gemeinsame Geschäftslogik entscheidet anhand von Operation/Availability.

Jeder Adapter liefert ein Capability-Profil mit belegten Tests, Backendrevision und verbleibenden Lücken. Die [Contract-Tests](README.md#teststrategie-und-erweiterungsnachweis) gelten auch für IPC-Adapter. Ein synthetischer Text-only-Provider und ein Preview-only-Provider müssen ohne Codeänderung in gemeinsamen Screens funktionieren. Bis zu echten Integrationsnachweisen sind sämtliche realen Providerprofile geplant, nicht verifiziert.
