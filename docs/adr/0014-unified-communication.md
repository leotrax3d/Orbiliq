# ADR 0014: Abgeleitete Personen-Timelines und explizite Versandwege

Status: angenommen, 27.10.2026.

Die gemeinsame Inbox und optionale Personen-Timelines sind Projektionen derselben lokalen
Nachrichten. Eine Personen-Conversation bekommt keine eigene Remote-ID und schreibt weder
Nachrichten noch Originalconversations um. Nur eindeutig zugeordnete Direktchats gehören
zu einem Personenverlauf; Gruppen bleiben unabhängig.

Ein Versand wählt Provider, Account und Conversation explizit. Capabilityprüfung erfolgt
pro Route und konkreter Operation; keine dienstübergreifende Vereinigung und kein stiller
Fallback. Antworten bleiben innerhalb ihrer ursprünglichen Route. Ein atomarer Check der
Personenmitgliedschaft beim dauerhaften Enqueue schützt vor parallelen Kontaktkorrekturen.
Danach ist die Route des Auftrags fest; Sync und Fehlerbehandlung verwenden die vorhandene
Outbox und je Account einen SyncCoordinator. Es ist keine neue Speichermigration erforderlich.
