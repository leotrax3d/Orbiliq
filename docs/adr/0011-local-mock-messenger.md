# ADR 0011: Deterministischer lokaler Mock-Messenger

Status: Angenommen. Datum: 27.10.2026.

## Entscheidung

Ein eigenes Kotlin/JVM-Modul `core:provider-mock` implementiert den bestehenden
Providervertrag und den Inboxport mit ausschließlich synthetischem In-Memory-Zustand.
Zeit, Remoteeingänge, Capabilities, Fehler und Konflikte sind explizit steuerbar.
Keine echten Protokolle, Konten, Netzwerke oder Androidruntime sind erforderlich.
Typisierte Typing-Commands/-Events und eine eigene Fehlerkategorie `SyncConflict`
ergänzen die gemeinsame API, ohne messengerabhängige Fallunterscheidungen.

Commands werden atomar validiert und idempotent verarbeitet; unbekannte Wirkung
und sicher wiederholbare Fehler bleiben unterscheidbar. Replay gilt nur innerhalb
einer Session. Der Mock meldet keine dauerhafte Offlinequeue oder durable Replays.
Szenarien und Eventjournal sind für begrenzte Tests, nicht Produktionsspeicherung.

Debug-Builds können per `-Porbiliq.mock=true` die vorhandene Inbox speisen. Release
bindet das Modul nicht ein. Testkontakte sind in der Inbox sichtbar markiert.

## Folgen

UI-, Integrations- und Regressionstests können ohne Messengeraccounts entstehen.
Core-Persistenz, Outbox, Prozesswiederherstellung, Android-Hintergrundbetrieb und
Codecvalidierung benötigen weiterhin eigene Implementierungen und Tests.
Details und ausführbare Beispiele: [Mock Provider](../architecture/mock-provider.md).
