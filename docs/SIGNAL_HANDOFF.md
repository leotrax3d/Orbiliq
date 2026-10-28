# Signal – gesicherter Zwischenstand

Stand: 28.10.2026. Prompt 11 ist **nicht abgeschlossen**.

## Fortsetzung im neuen Workspace am 28.10.2026

### Geräteverbindung und Signierschlüssel

WLAN-ADB erfolgreich mit dem Pixel gekoppelt; `get-state` bestätigt `device`.
Keinen Kopplungscode in Dokumentation, Git oder Logs übernehmen.
USB blieb offline/fehlend. Der heruntergeladene CI-Companion aus 40668223010 wurde
lokal mit dem vorhandenen Debug-Keystore neu signiert. `adb install -r` wurde von
Android mit `INSTALL_FAILED_UPDATE_INCOMPATIBLE` verweigert: der alte installierte
Companion hat einen anderen Signer. Auch das öffentliche Signaturzertifikat der
installierten Orbiliq-APK unterscheidet sich vom hiesigen Debug-Schlüssel.
Der Nutzer kennt keinen weiteren Keystore. Kein Uninstall, Datenreset oder Aktivieren
des alten deaktivierten Companions ausgeführt. Ein sauberer Neustart der Test-Apps
benötigt ausdrückliche Zustimmung zum Verlust ihrer lokalen Daten. Die ursprüngliche
Signal-App bleibt unangetastet.

Security-Fortsetzung ist als `9839d88` auf `codex/signal-completion` gepusht;
neuer Companion-CI-Lauf: https://github.com/leotrax3d/Orbiliq/actions/runs/40693619335
(zum Zeitpunkt dieses Eintrags läuft er noch; Ergebnis prüfen).
Der erste Windows-Orbiliq-Build scheiterte am fehlenden verifizierten Windows-AAPT2-
Artefakt. Hash durch unabhängigen Download von Google Maven gegengeprüft und ergänzt.
App-/Test-APK-Build wird danach erneut ausgeführt.

- Aktiver Arbeitsbranch: `codex/signal-completion`, auf Basis von
  `origin/codex/signal-provider-tests`; `main` enthält den Signal-Zwischenstand nicht.
- CI 40668223010 erfolgreich. Artefakte liegen lokal unter
  `build/artifacts/run-40668223010`; keine Installation, kein Resigning und keine
  Änderung von Gerätedaten in dieser Fortsetzung vorgenommen.
- Weitere UI-Lücken gefunden und im Generator geschlossen: asynchroner Restore
  (vor Session-Netzwerkvalidierung), leere/alte SavedState-Stacks, Start-fresh und
  NavigateBackToScreen. Primärregistrierung bleibt an beiden HTTP-APIs blockiert.
- Frischer Quellbaum `build/signal-companion/source-v2` erfolgreich erzeugt.
  `ORBILIQ-SECURITY.json` Generation 2 erfasst die Security-Dateihashes;
  `build.py` lehnt alte/abweichende Generationen ab. Das ist eine Konsistenzprüfung,
  keine Signatur oder Sicherheitszertifizierung. CI prüft sie ebenfalls.
- Sieben Python-Tests sowie zehn vorhandene Signal-JVM-Tests lokal bestanden.
  Kein neuer Companion-APK-Build und keine Hardwaretests bestanden/behauptet.
- Handy vom Nutzer angeschlossen. ADB 37.0.0 sah das Gerät zunächst als offline,
  nach Neustart wechselnd offline/fehlend; Windows erkennt Pixel 10a als WPD.
  Nutzer erhielt Schritte zum erneuten USB-Debugging und zur Computerfreigabe.
  Auf eine nutzbare ADB-Verbindung warten, keine Kontodaten zurücksetzen.
- Neue Recherchebelege stehen in `research/08-signal-revalidation.md`.

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
