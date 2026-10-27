# ADR 0010: Typisierte lokale IDs und offene Provider-Semantik

Status: angenommen und als Kotlin/JVM-Vertrag implementiert, 27.10.2026.

## Entscheidung

Person, MessengerIdentity, Account, Conversation, Message, Attachment und Group haben
unterschiedliche lokale IDtypen. ProviderId und Remote-Referenzen bleiben opaque und offen.
Remote-IDs sind mindestens accountgebunden, Nachrichten zusätzlich conversationgebunden.
Ein Mapping darf mehrere Remotealiase auf dieselbe lokale ID binden, niemals einen Alias
auf eine andere lokale ID umhängen. Temporäre Provider-IDs ersetzen keine lokalen IDs.

PersonIdentityLink hält korrigierbare Kontaktezuordnungen außerhalb der Transportidentität.
Neue Nachrichtentypen erhalten offene Typ-IDs und Unsupported-Fallback. Namespaced,
versionierte opaque ProviderData erhalten Erweiterungen außerhalb gemeinsamer Fachfelder.

Capabilityentscheidungen verlangen vollständige, aktuelle Scopeketten; unbekannte Semantik
sperrt. Providerregistrierung ist explizit und vertragsversioniert. Ein Sessionguard
prüft vor Dispatch erneut, ersetzt jedoch weder Protokollprüfung noch persistente Outbox.

## Folgen

Weitere Messenger benötigen keine Änderungen am gemeinsamen Modell oder Messengerzweige.
Neue gemeinsame Fachsemantik kann additive Varianten/Commands benötigen. Anbieteradapter
besitzen Codecs und Remote-Revisionen; Data besitzt dauerhafte Aliase und Projektionen.
Die aktuelle Aliasimplementierung ist nur In-Memory, eine DB folgt separat.
Typisierte AccountId ersetzt den Foundation-String auch in Inbox-Test und Compose-Key.

Details und Grenzen: [Domain Model](../architecture/domain-model.md).
