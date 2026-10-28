# Übergabeprompt: Prompt 11 wirklich fertigstellen

Arbeite im Repository `leotrax3d/Orbiliq` auf **`codex/signal-completion`** weiter.
Nicht von `main` neu beginnen: Dort fehlen die bereits begonnenen Signal-/UI-Arbeiten.
Der Nutzer verlangt ausdrücklich, bis zur vollständigen Erfüllung von Prompt 11
weiterzuarbeiten. Bei nahem Kontext-/Nutzungslimit alles committen/pushen und diesen
Übergabeprompt aktualisieren. **Prompt 11 ist noch nicht fertig.**

## Auftrag und verbindliche Grenzen

Aktuelle Messenger-Recherche/Upstream prüfen, dann Signal anhand der gewählten
Strategie vervollständigen: sicherer Login/Device Linking, Session, Sync, Empfang,
Textversand, Medien, Gruppen, Replies/Reaktionen, weitere zuverlässig unterstützte
Funktionen. Nur implementierte Funktionen über Capabilities freigeben. Schlüssel
und Signal-Protokollzustand im Companion halten, keine sensiblen Logs oder Fake-
Erfolgsmeldungen. Credentialfreie Integrationstests ergänzen. DoD: echter Account
kann die vorgesehenen Kernfunktionen zuverlässig in Orbiliq verwenden.

Lies zuerst vorhandene `AGENTS.md` (falls vorhanden), `docs/PROJECT_CONTEXT.md`,
`docs/SIGNAL_HANDOFF.md`, `docs/architecture/signal-provider.md`, ADR 0015/0016,
`integrations/signal-companion/README.md` und den tatsächlichen Code. Historische
Dokumentationsabsätze können ältere Stände beschreiben. Keine Subagenten beauftragt.

## Tatsächlich erreicht

- Ausgewählt bleibt ein separater Android-Companion auf Signal-Android, AGPL-Code
  ausschließlich dort. Fester Upstream `6e9dddc01f1b26e608ac683807c51e139c141ed2`
  (8.33.2), dessen libsignal-Abhängigkeit 0.105.4 unverändert bleibt. Kein signal-cli-
  Server und keine neue Kryptografie im MIT-Kern.
- **Echtes Linking ist gelungen**, vom Nutzer durchgeführt und per regulärem
  signaturgeschütztem IPC als `Linked` geprüft. Vier Hardwaretests bestanden, auch
  nach Companion-Prozessneustart: zuletzt 3,569 s. Der erste Zugriff kann während
  Native-Initialisierung kurz `Unavailable` liefern; Test toleriert nur diesen
  Zustand höchstens 15 s, niemals Revoked/Untrusted/UpdateRequired.
- Original-Signal ist weiterhin normal angemeldet und wurde nicht zurückgesetzt.
- Die aktuell installierte Companion-Version stammt noch aus dem korrigierten
  **Run 40668223010** (`fd5dce6`), lokal neu signiert. Sie bietet nur Setup/Status,
  **noch keine Nachrichtenbrücke**. Nicht mit fertig getesteter Nachrichtenintegration
  verwechseln. Bisher keine neue Nachrichtenzustimmung erteilt und kein neuer
  Messaging-Gerätetest ausgeführt.
- Die neue Nachrichtenbrücke ist implementiert, aber noch nicht auf Hardware
  abgenommen: separate Zustimmung an Accountbindung und aktuellen Clientsigner,
  normale Direktchat-Texte senden/empfangen, Gruppentitel/-texte nur empfangen.
- Root-App nutzt `SignalMessengerRepository`, AndroidSignalMessaging, bestehenden
  verschlüsselten LocalRepository, SyncCoordinator und UnifiedCommunicationService.
  Mock bleibt über `-Porbiliq.mock=true` separat aktivierbar, nicht im Release.
- Native Signal-Datenbank bleibt Wiederanlaufquelle. Begrenzte Binderseiten,
  stabile ID-Keyset-Seiten, Source-Lookups für bekannte Nachrichten und Löschungen.
  Native Thread-/Message-IDs sind im geprüften Upstream AUTOINCREMENT. Service-IDs
  werden für Identitäten pseudonymisiert; keine Telefonnummer als Primärschlüssel.
- Vor MessageSender wird ein AES-GCM-/Keystore-geschützter Command-Claim mit
  Timestamp/Thread/Bodyhash persistiert. Wiederholte Commands werden abgeglichen,
  nicht blind neu gesendet. Native Queueannahme ist nicht Sent/Delivered/Read.
- Generischer Vertrag ergänzt: Accepted kann Remote-Ref tragen; MessageDeleted
  kann Command-Korrelation tragen. Späte Queueantworten dürfen native Fehler oder
  gelöschte optimistische Kopien nicht wiederherstellen.
- View-once, verschwindende Inhalte und Chats mit aktiver Ablaufregel werden nicht
  exportiert. Nicht darstellbare normale Inhalte erhalten einen Platzhalter.
  Root-MainActivity und Setup nutzen FLAG_SECURE. Keine QR-/Schlüsselbilder in Git.

## Wichtige neue Korrekturen im aktuellen Code

- Signal erlaubt inline **2048 UTF-8-Bytes**, nicht 4000 Zeichen. Längere Texte
  brauchen einen Long-Text-Anhang, der noch nicht implementiert ist. MAX_TEXT und
  MAX_TEXT_BYTES jetzt 2048, UTF-8-Größe wird im Use Case, Sessionguard, Adapter und
  Native vor Dispatch geprüft. Composer zeigt die tatsächliche Bytegrenze.
- `prepare.py` gleicht die Grenze mit der echten Upstream-Datei
  `SignalServiceMessageLimits.kt` ab und bricht bei Drift ab.
- Normaler Textversand ist zusätzlich gesperrt, wenn der universelle Timer den
  Chat beim nächsten Send automatisch in einen verschwindenden Chat verwandeln
  würde (`SignalStore.settings.universalExpireTimer` und
  `SignalDatabase.messages.canSetUniversalTimer`). Diese Einstellung nie umgehen.
- Ein fehlgeschlagener Projektions-Commit muss den Ereignisstrom verlassen, statt
  weitere Emits in einem verletzten Flow auszuführen. Coordinator übernimmt Retry.
- Fehlende Zustimmung und Sync-Ausfälle werden als `MessengerSnapshot.syncNotice`
  angezeigt. Fremde/nicht mehr autorisierte Accountdaten sind in der UI ausgeblendet.
- Generator hat Fingerprint aus Generator/Overlays/geteilten Dateien plus Hashes
  der erzeugten Security-Dateien. **Keine Marker manuell neu versiegeln**, um einen
  veralteten Baum zu bauen; frisches Output-Verzeichnis verwenden.

## CI-Stand: genau unterscheiden

- 40668223010: alter korrigierter Linking-Companion, derzeit auf dem Gerät.
- 40693619335: fehlgeschlagen (nun korrigierter Kotlin-Smartcastfehler im alten
  Restore-Patch). Nicht als bestanden darstellen.
- **40699016698**, Commit `6e185da`: erfolgreich; erste Nachrichtenbrücke,
  Nachrichtenprotokoll v1, danach weiter gehärtet. Nicht als aktuellen Build nehmen.
- **40705057485**, Commit `1d820af`: ebenfalls erfolgreich, Nachrichtenprotokoll v2,
  readonly Gruppen/Platzhalter. Enthält noch NICHT die anschließend gefundenen
  UTF-8-/universeller-Timer-Korrekturen. Nicht als endgültig abgenommenen Stand nehmen.
- **40714064220**, Commit `704c286`: neuer Lauf für den korrigierten Stand mit
  2-KiB-UTF-8-Prüfung und universellem Timer. Beim Schreiben läuft er noch.
  https://github.com/leotrax3d/Orbiliq/actions/runs/40714064220
  Ergebnis prüfen; nicht ohne erfolgreichen Build oder vorzeitige Geräteabnahme
  als bestanden zählen.
- Workflow: `gh workflow run signal-companion.yml --ref codex/signal-completion`.
  Große native Builds können lange dauern. Nie den großen Signal-Fork lokal auf
  dem Laptop bauen, keine häufigen unveränderten Statuspolls.

## Letzter lokaler Nachweis

- Voller relevanter Lauf erfolgreich: 142 JVM-Tests über Model, Provider API,
  Signal, Data, Mock und Inbox; alle ohne Fehler. Darunter 23 Signaltests
  (13 neue Messagingtests + 10 bisherige), 29 Data- und 44 Mocktests.
- App-/Test-APK und Lint erfolgreich gebaut, letzter vollständiger Lauf 50 s,
  zwei CPUs/ein Worker. Lint zuletzt 0 Fehler/11 Warnungen (nicht als fehlerfrei
  ohne Warnungen bezeichnen). Neun Python-Generator-/Manifest-/Upstreamtests bestanden.
- Keine neuen Account-Messaging-, Medien-, Doze- oder Network-Wechseltests bestanden
  oder behauptet. Geräteabnahme ist der unmittelbar nächste wichtige Schritt.

## Gerät, Schlüssel und Ressourcen

- Pixel 10a, WLAN-ADB erfolgreich gepaart. `adb devices -l` und `adb mdns services`
  benutzen, aktuelle Verbindung auswählen. Nach ADB-Serverneustart kann die stabile
  mDNS-Gerätekennung statt einer alten TCP-Aliasverbindung nötig sein. Kein altes
  Pairing-OTP in Git/Logs übernehmen. USB blieb offline/unbrauchbar.
- Native/Root/Test sind derzeit mit dem hier vorhandenen lokalen Debug-Keystore
  signiert. Frühere Installationen hatten anderen Signer. Nutzer erlaubte einmal
  ausdrücklich, **nur die drei Testpakete samt Daten** neu aufzusetzen; das wurde
  ausgeführt. Jetzt ausschließlich `install -r`, keine erneute Löschung vornehmen.
- Original-Signal niemals deinstallieren, Daten löschen, neu registrieren oder
  serverseitig unregister aufrufen. Linking/Keys bleiben nativ.
- CI-APK mit dem lokalen `.android/debug.keystore` neu signieren; keine privaten
  Signing-/Signal-Schlüssel in CI oder Git. Signer von App/Test/Companion vergleichen.
- Windows: `C:\Users\leona\Documents\ChatGPT\Orbiliq`. SDK im üblichen
  `AppData\Local\Android\Sdk`. JDK 17 liegt lokal unter
  `build/toolchains/jdk17/jdk-17.0.20.1+1`, JDK 21 unter `.jdks/temurin-21.0.11`.
  `python` ist hier ein Windows-Store-Shim: Bundled-Python über
  `load_workspace_dependencies` finden. Gradle ein Worker, ActiveProcessorCount=2,
  maximal ca. 2 GiB für den kleinen Orbiliq-Build, keine parallelen großen Builds.
- Der Nutzer wollte einen Hinweiston nur bei erforderlichem Eingriff oder nach
  Abschluss ALLER Punkte. Kein falscher Abschlusston; Prompt 11 ist offen.

## Nächste konkrete Schritte

1. Aktuellen Push/Arbeitsbaum und neuesten Companion-CI-Lauf prüfen. Falls noch
   kein korrigierter Lauf existiert, starten. Nicht die alten Artefakte verwechseln.
2. Aktuelle ARM64-APK und korrespondierenden Source herunterladen, Manifest,
   Policy-/Versionsstand und Signatur prüfen, lokal neu signieren. Nur per `-r`
   aktualisieren. App-/Test-APK aus exakt passendem aktuellen Code ebenfalls updaten.
3. Vor der neuen Zustimmung ausführen: Instrumentationklasse
   `org.orbiliq.app.SignalMessagingIntegrationTest`, Parameter
   `signalMessaging=denied` (nur die entsprechende Testmethode oder Klasse).
   Bisherige Statusfreigabe darf keinen Nachrichtenzugriff erlauben.
4. Nutzer in Orbiliq: Konten -> Signal Companion Einrichtung -> Companion öffnen ->
   **Nachrichtenzugriff für diesen verknüpften Account erlauben**. Native Zustimmung
   nicht per Dateimanipulation oder Debug-Hintertür umgehen. KEIN neues Linking nötig.
5. `SignalMessagingIntegrationTest` mit `signalMessaging=granted` prüft stabile
   Bindung und verweigerten Fremdaccountzugriff. Keine private Payload ausgeben.
6. Dieselbe Klasse mit `signalSelfSend=true` prüft über den Hilt-Singleton den
   vollständigen persistenten Outbox-/Native-/Projektions-Roundtrip ausschließlich
   zu „Notiz an mich“, inklusive Wiederholung derselben Command-ID ohne Dublette.
   Keine Nachrichten an andere Personen ohne ausdrückliche Nutzeranweisung senden.
7. Echten Empfang/Sync (etwa eigene Synchronisationsnachricht vom Primärclient),
   Prozessneustart, fehlendes Netzwerk, Widerruf/Lock und Hintergrundbetrieb prüfen.
   Ergebnis jeweils korrekt begrenzen; keinen nicht ausgeführten Test als bestanden
   deklarieren. UI-Tests mit privaten Accounts dürfen keine Semantikbäume/Screenshots
   mit privaten Daten in Berichte kopieren; die Daten-/Status-Tests redigieren Ausgaben.
8. Danach weiter an Medien, Gruppenversand/-rollen, Replies und Reaktionen arbeiten.
   Aktuell sind diese nicht implementiert, nicht pauschal als grundsätzlich technisch
   unmöglich erklären. Upstream kann viel mehr als der derzeitige Adapter. Capabilities
   müssen dem tatsächlich fertiggestellten Transport und UI folgen.
9. Hilfreicher Medienbefund für die nächste Implementierung:
   `SignalDatabase.attachments.getAttachmentStream` entschlüsselt nativ;
   `AppDependencies.blobs.forData(InputStream, size).createForMultipleSessionsOnDisk`
   persistiert verschlüsselt über Prozessstarts. `UriAttachment`/OutgoingMessage
   werden im offiziellen Versand genutzt. Rohes Durchreichen von Bildern darf keine
   EXIF-/GPS-Metadaten unbemerkt weitergeben. Keine View-once-/Expiry-Umgehung,
   keine langfristigen ungeschützten Handles oder Klartext-Caches. Nur analysiert,
   noch kein Mediencode implementiert.
10. Dokumentation, Tests und GitHub-Stand nach jedem echten Meilenstein aktualisieren;
    erst mit tatsächlichem Kernfunktionsnachweis Prompt 11 als abgeschlossen melden.
