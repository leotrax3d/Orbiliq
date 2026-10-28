# Signal – gesicherter Zwischenstand

Stand: 28.10.2026. Prompt 11 ist **nicht abgeschlossen**.

## Neuester Fortsetzungsstand: erste Messaging-Geräteabnahme bestanden

**Hardware-Nachweis 28.10.2026, Pixel 10a**, Companion aus Run 40717591141 (`729dfdc`,
`verify_artifact.py` gegen eigenen Commit bestanden, lokal neu signiert, Signer
`50c8a5…` identisch zu App/Test/Installation, nur `install -r`), App/Test aus `4f002d0`:
1. `signalMessaging=denied` (nur Statusfreigabe): kein Nachrichtenzugriff – bestanden.
2. Nutzer erteilte Nachrichtenfreigabe nativ im Companion (kein neues Linking).
   `signalMessaging=granted`: stabile Bindung, Fremdbindung abgewiesen – bestanden.
3. `signalSelfSend=true`: Text an „Notiz an mich“ durch Outbox → Native → Projektion,
   genau einmal, Wiederholung derselben Command-ID ohne Dublette – bestanden (3,4 s).
Nicht getestet: Empfang fremder Nachrichten, Prozessneustart mit Messaging,
fehlendes Netz, Widerruf/Lock, Doze/Hintergrund, Zitate/Reaktionen auf Hardware.
Reaktionen-Companion: Run 40721700076 (`4f002d0`) noch zu prüfen/installieren.

### Vorheriger Stand

Aktueller Companion-Lauf: **[40717591141](https://github.com/leotrax3d/Orbiliq/actions/runs/40717591141)**,
Commit `729dfdc` (zusätzlich empfangene Zitat-Antworten). Vorheriger Lauf
[40714064220](https://github.com/leotrax3d/Orbiliq/actions/runs/40714064220) (`704c286`)
enthält diese noch nicht. Ergebnisse prüfen; vor Installation
`verify_artifact.py <source.tar.gz>` gegen den aktuellen Generator ausführen.

Neu (`729dfdc`, nur JVM-getestet): Empfangene Zitat-Antworten. Native löst
Quote-Zeitstempel+Autor nur zu exportierbaren Nachrichten desselben Threads auf
(nie zu View-once/Ablauf/gelöschten); Provider setzt `Message.replyTo` und meldet
`signal.quote.receive`. **Antworten senden bleibt nicht unterstützt** (Reply-Command
wird vor dem Transport abgelehnt). Reaktionen (Commit danach, nur JVM-getestet, braucht neuen
Companion-Build): Native exportiert Emoji + pseudonymisierten Akteur je exportierbarer
Nachricht; Provider diffed Snapshots zu `ReactionChanged` (Hinzufügen/Ändern/Entfernen),
auch über Neustarts aus den lokal gespeicherten Reaktionen; Timeline zeigt sie
schreibgeschützt. Capability `signal.reaction.receive`; **Reagieren senden nicht unterstützt.**

**Für die nächste Agentenübernahme zuerst den vollständigen
[Übergabeprompt](SIGNAL_NEXT_AGENT_PROMPT.md) lesen.** Nutzer verlangt explizit,
vor Kontext-/Nutzungslimit alles zu pushen und einen Fortsetzungsprompt bereitzulegen.

Aktueller Code: eigener kontogebundener Nachrichten-Consent, Protokoll v2,
normaler Direkttextversand/-empfang, readonly Gruppentitel/-texte, stabile
Keyset-Seiten aus der nativen DB, verschlüsselter Command-Claim vor Native-Send,
Generische Outbox-/Sync-Einbindung, UTF-8-Bytegrenze und geschützte Hauptansicht.
Medien, Gruppenversand/-verwaltung, Replies und Reaktionen sind weiterhin offen.
Sie dürfen nicht durch einfaches Anschalten von Capabilities als fertig gelten.

Wichtiger Upstream-Befund: Inline-Texte sind auf 2048 UTF-8-Bytes begrenzt,
längere Texte benötigen Long-Text-Anhänge. Generierung gleicht dieses Limit mit
der tatsächlichen nativen Konstante ab. Normaler Versand berücksichtigt zusätzlich,
ob der universelle Timer den Chat automatisch in einen verschwindenden Chat
umstellen würde. Keine Timer-/View-once-Umgehung.

Letzter vollständiger lokaler Lauf: **142 JVM-Tests ohne Fehler**, App-/Test-APK
und Lint erfolgreich, 50 s. Darunter 23 Signal-, 29 Data-, 44 Mocktests. Lint
0 Fehler/11 Warnungen. Neun Python-Generator-/Manifest-/Drifttests bestanden.
Messaging-Gerätetests noch nicht ausgeführt; installierter Companion ist weiterhin
der alte korrigierte Linking-Build aus 40668223010.

Native CI: 40699016698 (`6e185da`, Protokoll v1) und 40705057485 (`1d820af`,
Protokoll v2) erfolgreich. Beide liegen vor den aktuellen UTF-8-/Timer-Korrekturen;
nicht als endgültigen Stand installieren/abnehmen. Ein neuer aktueller CI-Lauf
wird auf diesem Branch gestartet und seine Run-ID separat nachgetragen.
Keine neue Deinstallation oder Original-Signal-Änderung nötig. Gleicher lokaler
Signer: `install -r` bewahrt die bereits erfolgreich verknüpfte Session.

## Fortsetzung im neuen Workspace am 28.10.2026

### Geräteverbindung und Signierschlüssel

### Echtes Linking und Session-Prozessneustart nachgewiesen

Nutzer hat den Companion erfolgreich über die ursprüngliche Signal-App verknüpft.
Zusätzlicher opt-in Hardwaretest `localLinkStateMatchesExplicitHardwareExpectation`
prüft über das authentifizierte IPC ausschließlich den Status `Linked` (keine IDs,
Schlüssel oder Nutzdaten). Mit `-e expectedSignalLinkState Linked` bestanden zunächst
vier Tests. Nach `am force-stop org.orbiliq.signal.companion` lieferte der erste
sofortige Status kurz `Unavailable`; die Folgemessung wieder `Linked`.
Test berücksichtigt jetzt maximal 15 Sekunden Anlaufzeit ausschließlich für
`Unavailable`, nicht für Revoked/Untrusted/UpdateRequired. Erneuter echter Cold-Start-
Test: **OK (4 tests), 3,569 s**. Das bestätigt die lokale Sessionpersistenz über
Prozessneustart, noch keine Sync-/Empfangs-/Versandfunktion in Orbiliq.

Für den Test wurde der ungeschützte RegistrationActivity-Bildschirm ausschließlich
lokal als Bild angezeigt, nicht decodiert oder hochgeladen. Ein älterer Code führte
in der primären App zu „Netzwerkfehler“; Ablauf ist eine plausible, nicht bewiesene
Ursache (ProvisioningSocket-Lifespan 90 Sekunden). Nach frischem Code Erfolg laut
Nutzer und anschließendem IPC-Nachweis. Beide temporären Bilder nach erfolgreichem
Linking entfernt. Keine Screenshot-Schutzflags verändert.

Nutzer regte Accessibility für das spätere Linking an. Nicht implementiert: breiter
App-Bedienzugriff und kein belegter Mechanismus zum Einspeisen eines QR in die Kamera.
Upstream exportiert DeviceProvisioningActivity für `sgnl://linkdevice`/`tsdevice`,
die geprüfte Implementierung öffnet aber nur einen Dialog mit Weiterleitung zu den
Linked-Device-Einstellungen. Das belegt keine direkte QR-Übernahme. Eine gezielte
Same-device-Übergabe bleibt gesondert zu untersuchen; noch keine Produktzusage.

**Aktueller Nachtrag:** Nutzer hat ausdrücklich zugestimmt, ausschließlich Orbiliq,
den Orbiliq Signal Companion und das Testpaket einschließlich ihrer lokalen Daten
neu aufzusetzen. Genau diese drei Pakete deinstalliert, dann die hier gebaute App-/
Test-APK und den lokal signierten Linking-only-Companion aus Run 40668223010 installiert:
alle drei Installationen erfolgreich. Original-Signal weder deinstalliert noch
zurückgesetzt. `SignalCompanionIntegrationTest` erneut auf echter Hardware:
**OK (3 tests), 5,389 s**. Orbiliq MainActivity geöffnet, Nutzer um native Zustimmung
und Meldung des Linking-Bildschirms gebeten. Keine QR-Daten erfasst.
Die neue Companion-Generation aus Run 40693619335 ist noch nicht installiert.
Direkter Shell-Start der nicht exportierten SignalSetupActivity wurde erwartungsgemäß
abgewiesen; MainActivity wurde regulär geöffnet. Kein Exportflag geändert.

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
