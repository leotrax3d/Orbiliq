# Signal – gesicherter Zwischenstand

Stand: 28.10.2026. Prompt 11 ist **nicht abgeschlossen**.

## Aktueller Zustand

- Separater Signal-Android-Companion, feste Revision `6e9dddc01f1b26e608ac683807c51e139c141ed2`.
- Orbiliq besitzt Setup-/Status-IPC, Signatur-/UID-Prüfungen und bewusst keine positiven Messaging-Capabilities. Kein echter Signal-Account wird in Orbiliqs Inbox/Outbox geführt.
- Erster Companion-Build in CI 40662676620 erfolgreich. Lokal signiert und installiert: drei Hardware-IPC-Tests bestanden; ADB-Shell-Zugriff korrekt verweigert. Zustimmung und NeedsLink real geprüft.
- Beim weiteren manuellen Bedienen wurde der Telefonnummernweg geöffnet. Danach meldete der Companion PrimaryDeviceNotSupported. Kein erfolgreicher Linked-Device-Nachweis.
- Der erste Companion ist per `pm disable-user --user 0 org.orbiliq.signal.companion` deaktiviert, seine Daten wurden nicht gelöscht. Kein serverseitiges Unregister durchgeführt.
- Der Nutzer hat anschließend ausdrücklich bestätigt: **Die ursprüngliche Signal-App ist weiterhin normal angemeldet.** Er möchte den Companion-Test neu beginnen.
- Korrigierter Generator erzwingt Linking und sperrt primäre Session-/SMS-/Verifikations-/Registrierungsoperationen in beiden HTTP-APIs. Vier strikte Mock-Transporttests in CI bestanden.
- Korrigierter APK-Build: https://github.com/leotrax3d/Orbiliq/actions/runs/40668223010 (Commit `fd5dce6`). Zuletzt noch im APK-Build. Nicht versehentlich das erste APK erneut installieren.
- Orbiliq-CI 40666966910 an `5347ce8` vollständig grün: 10 Signal-JVM-Tests und 5 Emulator-Instrumentationstests. Dieser ältere CI-Stand enthält noch nicht alle im anschließenden Sicherungscommit veröffentlichten UI-Änderungen.

## Nächste Schritte

1. Ergebnis von CI 40668223010 prüfen; bei Erfolg Artefakt in einen neuen, nach Run-ID benannten Ordner herunterladen.
2. ARM64-APK, kompiliertes Manifest, eigene Account-/Kontaktkennungen und Signature-Permission prüfen. APK ausschließlich lokal mit Orbiliqs Debug-Signer neu signieren; keine Schlüssel nach CI übertragen.
3. Den vom Nutzer gewünschten Neustart ausschließlich auf den Companion beziehen. Keine Original-Signal-Daten löschen und kein serverseitiges Unregister ausführen. Bestehende Testdaten vor einer Löschung gezielt berücksichtigen.
4. Korrigierten Companion installieren; beachten, dass `install -r` den deaktivierten Paketstatus beibehalten kann. Erst für den vorbereiteten neuen Linking-Test wieder aktivieren.
5. Native Statuszustimmung erneut durch den Nutzer bestätigen lassen. Kopplung ausschließlich per QR, keine Telefonnummern-/PIN-Neuregistrierung.
6. Primärgerät ist dasselbe angeschlossene Android-Handy. Falls QR-Anzeige über USB nötig: ausschließlich lokal auf dem Laptop, kein Hochladen/Decodieren/Protokollieren. FLAG_SECURE nie umgehen. Bisher wurde kein QR-Screenshot aufgenommen; der Captureversuch wurde vor Aufnahme abgebrochen.
7. Erst nach echtem Linking Session/Neustart/Entkopplung prüfen. Danach echte kontogebundene Autorisierung, dauerhaftes Journal/Commit/Ack, Empfang/Sync und Textversand mit Reconciliation implementieren. Medien/Gruppen/Replies/Reaktionen bleiben ebenfalls offen.

## Lokale Besonderheiten

Der alte generierte Standardquellbaum ist als überholt markiert. `build.py` lehnt ihn ab. Neue Quellen in einem frischen Ausgabeverzeichnis generieren; Marker nicht einfach entfernen. Der erste lokal signierte APK-Dateiname enthält ausdrücklich `primary-registration-unsafe`.

Laptop: maximal zwei CPU-Kerne, Nice 15, keine parallelen großen JVM-Builds. Große Builds über die ausdrücklich wieder freigegebene GitHub CI ausführen. Das Handy ist Testgerät. Den gewünschten Hinweiston nur bei erforderlichem Nutzereingriff oder nach Abschluss **aller** Punkte von Prompt 11 abspielen; bisher gab es keinen Abschlusston.

Weitere genaue Nachweise: [Buildvalidierung](build-validation.md), [Projektkontext](PROJECT_CONTEXT.md), [Companion-Anleitung](../integrations/signal-companion/README.md).
