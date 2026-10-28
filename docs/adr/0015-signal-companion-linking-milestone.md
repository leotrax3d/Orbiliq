# ADR 0015: Signal zuerst als separater Linked-Device-Einrichtungspfad

- Datum: 27.10.2026
- Status: Einrichtungscode implementiert; keine Freigabe als Messagingprovider
- Ergänzt: ADR 0001 und ADR 0007

## Entscheidung

Der erste konkrete Companion wird aus Signal Android an Revision
`6e9dddc01f1b26e608ac683807c51e139c141ed2` erzeugt. Er behält den vorhandenen
Provisioning-/Krypto-/Sessionstack. Die unabhängigen eigenen Ergänzungen bleiben MIT;
der veränderte Client und seine Distribution unterliegen weiterhin den Upstream-Pflichten.
Es wird kein Signal-Code in die Orbiliq-Hauptapp eingebunden und kein Remote-Server eingeführt.

Der Generator liefert einen eigenen Android-Paketnamen und verhindert die Übernahme
des Upstream-Debugsigners. Die lokale Prototypkombination verlangt denselben aktuellen
Signer beider APKs. Android-Signature-Permission, exakte Paket-/UID-Prüfung und explizite
Zustimmung im Companion bilden gemeinsam die Grenze. Keine Trust-on-first-use-Freigabe
anhand eines Paketnamens, kein exportierter ungeschützter Binder und kein Bearertoken
in einem Intent. Ein abweichend signierter Fremdcompanion wird derzeit abgewiesen.

IPC-Version 1 exponiert ausschließlich lokale Linkingzustände und lokalen Zugriffsentzug.
Zustimmung gilt nur für diese Zustandsabfrage; sie ist keine Freigabe für spätere
Nachrichtenübertragung. Der Client lehnt unbekannte Versionen und nichtleere
Capabilitylisten ab. Ein späterer Nachrichtenvertrag braucht eine neue explizite
Version, kontogebundene Autorisierung, ein dauerhaftes Journal und eigene Abnahme.

Die Providerklasse meldet keine Onlineverbindung und keinen gelungenen Sync aus lokal
vorhandenen Credentials. Sie exponiert keine Nachrichtenoperationen, quittiert keine
Cursor und behauptet keine erfolgreichen Aufträge oder beweisbare Versandwirkung.
Die normale Inbox/Outbox erhält noch keinen realen Signal-Account. Der separate
Einrichtungseinstieg ist auch ohne Mock verfügbar und nennt diese Grenze ausdrücklich.

Für den Prototyp entfallen die regulären Signal-Logcat-/PersistentLogger-Sinks; persistierte
Crashtexte und an den Systemhandler übergebene Exceptions werden redigiert. Das reduziert
Diagnosemöglichkeiten. System-/Bibliothekslogs und der gesamte Upstream sind damit nicht
vollständig sicherheitsgeprüft.

## Folgen und offene Nachweise

Der [Companion-Leitfaden](../../integrations/signal-companion/README.md) enthält Erzeugung,
Vertragsgrenze und Bedienweg. Der erzeugte vollständige Clientquellbaum liegt unter
`build/` und wird nicht als großer vendorter Fremdquellbaum in den MIT-Kern aufgenommen.
Generator, eigene Ergänzungen und feste Upstreamrevision sind versioniert.

Keine Produktionssignierung oder Distribution; die konkrete Lizenz-/Paketgrenze bleibt
vor Veröffentlichung zu prüfen. Ein Buildnachweis ersetzt weder den QR-Scan auf Hardware
noch Session-, Entkopplungs-, Doze- oder Accounttests. Das Ein-Gerät-QR-Problem bleibt offen.
Die vollständige Signal-Definition-of-Done ist nicht erreicht. Nächster vertikaler Schritt:
Kontoidentität/Epochwechsel, Empfangsjournal und Projektion mit Commit/Ack, dann Textversand
mit Command-Reconciliation; erst danach weitere positive Capabilities.
