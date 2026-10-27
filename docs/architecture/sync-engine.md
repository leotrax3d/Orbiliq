# Sync Engine und Offline Queue

`core:data/storage/SyncCoordinator` ist providerunabhängig. Ein App-Scope besitzt
je Account/Store genau einen Koordinator. `run()` betreibt Event-Replay und Outbox;
`drain()` erlaubt deterministische Worker-/Testläufe. Der persistente Debug-Mock
startet ihn bei Initialisierung. Keine zusätzliche Bibliothek oder Cloud nötig.

Ohne Cursor beginnt initialer Replay, mit passendem Epoch-Cursor inkrementeller
Replay. Eventprojektion und Cursor committen gemeinsam, erst danach erfolgt ACK.
Gaps bleiben als Checkpointzustand erhalten und werden als SyncConflict gemeldet.
Ein Provider muss Resync/Reparatur anbieten; generische opaque Cursor können vom
Kern weder sortiert noch selbst repariert werden.

`enqueue(command, optimistic)` schreibt Nachricht und Auftrag atomar. Die lokale
Nachrichten-ID ist `MessageId(command.id.value)`. Bestätigung und korrelierte Events
übernehmen die Provider-ID unter Beibehaltung der lokalen ID. Wiederholte Upserts
werden über Providerreferenz zusammengeführt. Der Command behält seine ID und
Idempotency-Key über alle Versuche; Abhängigkeiten müssen vorher bestätigt sein.
Vor Dispatch wird die aktuelle Conversation-Capability-Revision geladen und mit dem
Dispatch-Claim persistiert. Der Provider prüft weiterhin Payload, Scope und Berechtigungen;
ein Reconnect allein verwirft keine zuvor eingereihten Aufträge.

Vor Dispatch wird Dispatching persistiert; Wiederöffnung macht daraus OutcomeUnknown.
Nur RetryableNoEffect bzw. ProvenNoEffect erlauben erneuten Versand. Accepted bedeutet
Providerbesitz und wird ebenfalls nur abgeglichen. Versand und Reconciliation haben eine konfigurierbare Timeoutgrenze (Standard 30 Sekunden).
Unklare Antworten, Timeouts, Exceptions und
Cancellation führen niemals zu blindem Resend. Retry hat exponentiellen Backoff
(1 Sekunde bis 256 Sekunden), persistente Deadlines und respektiert längeres RetryAt.
Abgelaufene Commands werden sichtbar abgelehnt. Konflikte/Providerfehler bleiben mit
FailureCategory im Auftrag erhalten (Speicherformat v4, v1–v3 lesbar).

Netzwerk/Auth werden vor Queueverarbeitung geprüft; Reconnect setzt persistente
Backofffristen nicht zurück. Der Koordinator prüft alle 250 ms, im Hintergrund braucht
Android künftig einen Scheduler/Foreground-Service nach ADR 0006. Dies ist kein
Doze-/Boot-/WorkManager-Nachweis. Fehler des laufenden Event-/Speicherpfads sind über
`failure` beobachtbar, Aufträge über `LocalRepository.observeState()`; eine Recovery-UI
steht aus. `PersistentMockRepository.sync()` stellt den Koordinator bereit.

Die Mock-Remotehistorie ist weiterhin flüchtig. Bei Wiederöffnung werden belegte
Bestätigungen rekonstruiert; fehlt nach einem Crash der Remoteeffektnachweis, bleibt
StillUnknown sichtbar. Der Mock behauptet in diesem Fall ausdrücklich kein ProvenNoEffect.
Das verhindert Duplikate, kann aber eine manuelle spätere Klärung erforderlich machen.

JVM-Tests simulieren lokale Store-Wiederöffnung/Prozessverlust, Offline/Reconnect,
verlorene Antworten nach Commit und Backoff. Kein Android-Geräteprozess wird gestartet.
