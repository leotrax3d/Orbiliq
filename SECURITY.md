# Sicherheit

Die Foundation ist noch kein nutzbarer Messenger. Nur der aktuelle Entwicklungsstand
wird gepflegt; es gibt noch keine sicherheitsgeprüfte Releaseversion.
Keine echten Sessions oder Nachrichten werden gespeichert. Verschlüsselte Persistenz,
Keystore, Backup-/Transferausschlüsse für sensible Stores und Providerprüfung sind vor
Einführung solcher Daten umzusetzen und zu testen. Die Foundation deaktiviert Backups und enthält explizite Ausschlüsse für Cloud-Backups,
Geräte- und plattformübergreifende Transfers (einschließlich gerätegeschützter Speicherbereiche).
Klartextverkehr ist deaktiviert; es werden keine gefährlichen Berechtigungen angefordert.
Die XML-Regeln sind Grundlage, noch kein praktisch bestandener Backup-/Restore-Test.
[Android-Backupregeln](https://developer.android.com/identity/data/autobackup).

Schwachstellen nicht mit Tokens, Kontaktdaten oder Nachrichten in öffentlichen Issues melden.
Falls das GitHub-Repository Private Vulnerability Reporting anbietet, dort privat melden.
Bis ein privater Kontaktweg eingerichtet ist, keine vertraulichen Details veröffentlichen;
ein öffentliches Issue darf lediglich um einen privaten Kontaktweg bitten.
Es wird derzeit keine Reaktionsfrist zugesichert. Reproduktionsschritte ausschließlich mit
synthetischen Daten bereitstellen. MIT ist keine Freigabe der späteren Providerabhängigkeiten.
