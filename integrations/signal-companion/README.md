# Separater Signal-Companion: Einrichtungsmilestone

**Experimenteller Quellcode. Kein fertiger Orbiliq-Messengerprovider.**

`prepare.py` erzeugt eine separat baubare Signal-Android-Arbeitskopie aus der festgelegten
Revision `6e9dddc01f1b26e608ac683807c51e139c141ed2`. Es werden keine Signal-Kryptografie,
Service-Credentials oder Ratchet-Stores in die Hauptapp übernommen. Device Linking,
Sessionhaltung und Schlüsselverwaltung bleiben beim vorhandenen Android-Clientcode.

## Erzeugen und bauen

Voraussetzungen: Python 3, Git, JDK 21, Android SDK 37 mit Build Tools 36.0.0 und die vom
festgelegten Upstream benötigten Artefakte. Das ist ein großer separater Clientbuild.
Keine Emulatoren werden durch diese Befehle gestartet.

```sh
python3 integrations/signal-companion/prepare.py
# sdk.dir in build/signal-companion/source/local.properties eintragen.
# JAVA_HOME auf eine lokale JDK-21-Installation setzen.
python3 integrations/signal-companion/build.py
```

Ein vorhandener lokaler Git-Clone kann mit `--source-cache /absoluter/pfad` als Quelle
dienen. Die Revision wird trotzdem geprüft. Bestehende Zielverzeichnisse werden nicht
überschrieben; für eine neue Erzeugung `--output` verwenden. Der erzeugte Quellbaum ist
unter `build/` absichtlich ignoriert. Seine Änderungen bestehen aus Build-/Manifestanpassung,
den hier versionierten Bridgequellen und dem kopierten unabhängigen MIT-Vertrag aus
`core:provider-signal`. Die Hauptapp hängt nicht von diesem AGPL-Clientbuild ab.

`build.py` prüft Revision und JDK 21 und startet den richtigen Upstream-Modulpfad
`:Signal-Android`. Auf Nutzerwunsch begrenzt es auf Linux die CPU-Affinität auf zwei Kerne und setzt
Nice 15. Gradle nutzt einen Worker, maximal 3584 MiB Heap, G1-GC und keinen separaten
Kotlin-Daemon. Große Builds auf dem lokalen 8-GB-Laptop nacheinander ausführen;
währenddessen sind Codearbeit und leichte Prüfungen weiterhin möglich.
1536 MiB erwiesen sich beim Ressourcenmerge des vollständigen Clients als zu knapp;
2304 MiB mit Serial-GC führten im großen App-Kotlin-Modul zu dauerhaft vollem Heap und
überwiegender Speicherbereinigung. Diese Parameter sind deshalb kein schneller Buildpfad.
Auch 3072 MiB reichten für die große App-Kotlin-Kompilierung nicht aus. Der aktuelle
Prototyp lässt den Upstream-Configuration-Cache aktiv und deaktiviert ausschließlich
für `githubProdSpinner` den JVM-Bytecode-Optimierer (`-Xno-optimize`, im verwendeten
Kotlin-2.2.20-Compiler geprüft). Die Protokollbibliotheken und Releasevarianten bleiben
davon unberührt. Ein erfolgreicher vollständiger APK-Build ist damit noch nicht behauptet.

Application-ID: `org.orbiliq.signal.companion`. Der Client besitzt damit eigene
Android-Daten und kann neben Signal installiert werden. Die GitHub-Distribution hat
keinen automatischen Signal-APK-Updater. In dieser Variante wird das bereits vorhandene
Linking-UI explizit aktiviert. Der Setup-Einstieg nutzt `LinkAccount(showCreateAccount=false)`;
keine automatische Primärregistrierung, Recovery oder Telefonnummerneingabe durch Orbiliq.
Der übernommene vollständige Client hat weiterhin eigene Oberflächen; deren Funktionalität
ist kein Nachweis entsprechender Orbiliq-Capabilities.

Auch `authenticator.xml` und `syncadapter.xml` verwenden einen eigenen Android-Accounttyp;
die zugehörigen Kontakt-MIME-Typen sind ebenfalls getrennt. Der Upstream verwendet dort
teilweise feste Signal-IDs, obwohl der Laufzeitcode bereits `BuildConfig.APPLICATION_ID`
verwendet. Der Buildlauncher lehnt Quellbäume ohne isolierten Accounttyp ab.

## Lokale Vertrauensgrenze

Für diesen ersten Prototyp müssen beide APKs mit demselben aktuellen Zertifikat signiert
sein. Lokale Debugbuilds verwenden den lokalen SDK-Debugschlüssel; der Companion nutzt
ausdrücklich keinen ausgelieferten Upstream-Debugschlüssel. Debugsignaturen sind keine
Produktionsvertrauensbasis. Release-Signierung und Distribution sind nicht eingerichtet.

- Android-Signature-Permission an Provider und Setup-Activity.
- Orbiliq prüft Authority, exportierte Komponenten, erforderliche Permission, Paket,
  UID-Zuordnung und das aktuelle Signaturzertifikat vor IPC und Activitystart.
- Companion prüft pro IPC-Aufruf die systemseitige Binder-UID, deren Paketzuordnung und
  aktuelle Signatur. Eine vom Aufrufer behauptete UID oder ein Intent-Extra wird nicht vertraut.
- Setup läuft über `startActivityForResult`; die Zustimmung prüft das systemseitige
  `callingPackage`. Nur ein sichtbarer Button erteilt den lokalen Statuszugriff.
- Zustimmung liegt im No-Backup-Verzeichnis des Companions und ist an das Zertifikat
  gebunden. Widerruf und Signaturwechsel sperren den Zugriff. Keine Credential-Referenz,
  QR-Nutzdaten oder Signal-Schlüssel werden gespeichert oder übertragen.
- Gesperrter Client, lokal noch nicht registrierter Client, Primärgerät und vom Dienst
  bereits zurückgewiesene Credentials werden unterschieden. Lokal registriert ist kein
  Beweis einer aktuellen Netzwerkverbindung oder zeitnah erkannten Remote-Entkopplung.
- Screenshot-Schutz für Setup; Zustimmungsbuttons lehnen verdeckte Touchereignisse ab.
  Bridge und Android-Adapter protokollieren keine Nutzdaten oder rohen Exceptions.
  Der Generator entfernt außerdem die normalen Logcat-/PersistentLogger-Sinks aus der
  Clientinitialisierung und redigiert persistierte sowie weitergereichte Crashdetails.
  Dadurch fehlen ausführliche Diagnosen; Drittanbieter-/Systemlogs sind nicht vollständig auditiert.

Version 1 kennt nur `status` und `revoke`. Antworten enthalten ausschließlich
`protocolVersion`, `linkState` und eine **leere** `capabilities`-Liste. Unbekannte Methoden
werden verworfen. Read-/Write-/Query- und Medienzugriffe sind nicht implementiert.
Die Hauptapp lehnt abweichende Vertragsversionen und nichtleere Capabilitylisten ab.
Kein HTTP-Server, keine Netzwerkbridge und keine verpflichtende Cloud werden eingeführt.

## Nutzung und tatsächlicher Umfang

In Orbiliq unter **Konten → Signal Companion · Einrichtung (experimentell)** öffnen.
Nach Installation beider lokal signierter APKs im Companion den Statuszugriff erlauben
und ein **zusätzliches verknüpftes Gerät** einrichten. Keine vorhandene Telefonnummer
erneut als Primärgerät registrieren. Der QR-Scan auf demselben Telefon ist nicht gelöst;
es kann ein zweiter Bildschirm nötig sein. Ein sicherer Ein-Gerät-Bedienweg ist nicht
als bestanden ausgewiesen.

Die Orbiliq-Oberfläche zeigt ausschließlich den lokalen Einrichtungszustand. Das Konto
wird noch nicht in die gemeinsame Inbox/Outbox aufgenommen. Die Providerklasse hat
keine positiven Messaging-Capabilities und lehnt jeden Nachrichtenauftrag ab. Sie meldet
keinen authentifizierten Orbiliq-Account, Online-/Sync-Erfolg und bestätigt keine
Replaycursor oder Versandwirkungen: Die sichere Zuordnung zur konkreten Kontoidentität
fehlt noch, auch wenn der separate Einrichtungsstatus bereits „verknüpft“ anzeigt.
Der lokale Zugriffsentzug entfernt keine Signal-Geräteverknüpfung; dies erfolgt separat
am Primärgerät. Geräteentzug wird erst nach dessen Erkennung durch den Companion sichtbar.

Noch offen: Kontoidentifikation ohne Schlüsselübertragung, dauerhaftes Ereignisjournal,
Replay/Ack nach Commit, Integration in Sync Engine und Inbox, Sendepfad mit dauerhafter
Command-Zuordnung und Reconciliation. Danach Medien, Gruppen, Replies und Reaktionen mit
deren tatsächlichen Sicherheits- und Aufbewahrungsregeln. Keine Calls/Stories/Presence
und kein unbelegter History-Backfill in Orbiliq. Die ursprüngliche Definition of Done ist
weiterhin offen.

## Lizenzen und Nachweise

Die unabhängig geschriebenen Dateien unter `overlay/` und der gemeinsame Vertrag sind
**MIT** wie der eigene Orbiliq-Code. [UPSTREAM-LICENSE](UPSTREAM-LICENSE) enthält den
AGPL-Text des verwendeten Clients. Der modifizierte Client behält dessen Lizenzpflichten,
Lizenztexte und Header; die MIT-Ergänzungen ändern daran nichts. Der Vertrag wird zusammen mit
Orbiliqs MIT-Lizenz in den erzeugten Clientquellbaum kopiert. Diese technische Trennung
ist keine abschließende Rechtsbewertung einer konkreten Distribution. Vor Veröffentlichung
sind komplette Quellcodebereitstellung, Notices, transitive Pflichten und Paketgrenze
für die konkrete Variante zu prüfen. Kein Produktionsrelease wird hiermit freigegeben.

Build-/Testresultate und nicht ausgeführte Geräte-/Accountprüfungen stehen in
[Buildvalidierung](../../docs/build-validation.md). JVM-Tests prüfen den lokalen
Providervertrag und die Signaturpolicy, nicht die vollständige Android-Permissiongrenze
oder den tatsächlichen Signal-Dienst. Der bestehende Upstream-Logging- und Sicherheitsstack
ist nicht vollständig auditiert.

## GitHub CI

Der Workflow `signal-companion.yml` baut auf `codex/signal-companion-ci` die feste
Upstream-Revision mit JDK 21, zwei Compiler-Kernen und einem Worker. CI nutzt einen
eigenen kurzlebigen Debug-Signer und erhält keine lokalen Schlüssel oder Accountdaten.
Das Artefakt enthält APK und modifizierten Quellbaum. Vor dem lokalen IPC-Test muss
die APK auf dem Laptop mit demselben lokalen Debug-Signer wie Orbiliq signiert werden;
der CI-Signer allein wird von der installierten Orbiliq-App nicht akzeptiert.
Ein grüner Build belegt noch keinen Account-Link und keine Messagingfunktion.

Der lokale Signaturschritt erfolgt ausschließlich mit einer frisch heruntergeladenen
CI-APK und einem neuen Ausgabepfad, beispielsweise (JDK und SDK-Tools im `PATH`):

```sh
apksigner verify --print-certs companion-ci.apk
apksigner sign --ks "$HOME/.android/debug.keystore" \
  --ks-key-alias AndroidDebugKey --ks-pass pass:android --key-pass pass:android \
  --out companion-local.apk companion-ci.apk
apksigner verify --print-certs companion-local.apk
apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
```

Die SHA-256-Zertifikatsdigests müssen nach dem lokalen Signieren übereinstimmen.
Vor einer Installation zusätzlich im fertigen Manifest die Application-ID
`org.orbiliq.signal.companion`, die eigene Bridge-Authority und Signature-Permission
prüfen. Nur das Companion-Paket installieren; die vorhandene Signal-App wird nicht
ersetzt. Falls Orbiliq vor der Permission-Definition installiert wurde, dieselbe
Orbiliq-APK ohne Datenlöschung erneut installieren. Ein abweichender Signer darf nicht
über eine gelockerte Trustpolicy umgangen werden.

## Primärregistrierung gesperrt

Ein Hardwaretest zeigte, dass im ersten Build durch weitere Upstream-Einstiege eine
Telefonnummernregistrierung erreichbar blieb. Dieser Build ist für weitere Accounttests
überholt. Der Generator erzwingt nun Linking als Activity-Einstieg, beschränkt restaurierte
Navigation auf Linking/Sync und entfernt die Primärregistrierungsoperationen aus beiden
HTTP-APIs. Strikte Mock-Transporttests prüfen Ablehnung vor jeder Netzwerkaktion.
Ein bereits als Primärgerät registrierter Companion wird dadurch nicht automatisch
entkoppelt, gelöscht oder zum Sekundärgerät umgewandelt. Zuerst muss der Nutzer den
Status seines ursprünglichen Signal-Accounts klären. Kein erneuter Accounttest mit
dem ersten APK-Build.
# Ergänzung zur Sicherheitsgeneration 2 (28.10.2026)

Alte generierte Quellbäume vor dem nächsten Build neu erzeugen. Generation 2 sperrt
zusätzlich asynchrone Registrierung-Wiederherstellung, leere SavedState-Stacks,
Start-fresh und Rückwärtsnavigation zum Primäraccount-Login. Die Sperre beider
primären HTTP-Registrierungswege bleibt bestehen. `build.py` prüft die erzeugte
Datei `ORBILIQ-SECURITY.json` und lehnt alte oder nachträglich geänderte Security-Dateien
ab. Diese Hashprüfung prüft Konsistenz, keine Herkunft; Review und APK-Signatur bleiben
erforderlich. CI führt dieselbe Prüfung aus.

Credentialfreie Generator-Integrationstests (Python-Standardbibliothek):

```sh
SIGNAL_UPSTREAM_SOURCE=/path/to/pinned/signal-checkout python3 -m unittest discover -s integrations/signal-companion -p test_prepare.py -v
```

Die Tests lesen Originaldateien mit `git show` aus der festen Revision, verändern den
Upstream-Checkout nicht und brauchen weder Netzwerkverkehr zu Signal noch Credentials.
Ohne `SIGNAL_UPSTREAM_SOURCE` werden die vier Upstreamtests ausdrücklich übersprungen;
die drei Manifesttests laufen weiterhin. CI setzt den Pfad und führt alle sieben aus.
Die Tests ersetzen keinen Linking-, Session-, Sync- oder Nachrichten-Gerätetest.
