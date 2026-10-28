# ADR 0017: Eine App mit eingebettetem Signal-Client, Lizenz AGPL-3.0-or-later

Status: angenommen (28.10.2026, Entscheidung des Projektinhabers)

## Kontext
Bisher lief Signal als separater AGPL-Companion neben der MIT-lizenzierten Orbiliq-App
(ADR 0015/0016). Nutzer hätten zwei Apps installieren, verknüpfen und aktualisieren müssen.
Alle nutzbaren Signal-Bausteine (Signal-Android, libsignal) sind AGPL; eine eigene
Implementierung würde die Lizenzfrage nicht lösen und erhebliche Krypto-Risiken bringen.

## Entscheidung
- Orbiliq wird als **eine App** (`org.orbiliq.app`) ausgeliefert: Der festgelegte Signal-Android-
  Fork ist die Host-App, Orbiliqs Module (Model, Domain, Provider, Data, Designsystem, Inbox, Host)
  werden vom Generator `integrations/signal-companion/prepare.py` in diesen Build kopiert.
- Orbiliq ist der einzige Launcher; Signal-Oberflächen bleiben intern für Verknüpfung/Fallback.
- Die Brücke läuft **im selben Prozess** (`InProcessHost`/`BridgeMessaging(inProcess = true)`);
  der exportierte ContentProvider und die Fremd-App-Zustimmung entfallen. Kontobindung und alle
  Signal-Regeln (Timer, View-once, Gruppenrechte, Löschfristen) bleiben unverändert geprüft.
- Lizenz des eigenen Codes: **AGPL-3.0-or-later** (alle bisherigen Beiträge stammen vom
  Projektinhaber). Das Gesamtwerk mit Signal-Android ist AGPL-3.0.
- Der eigenständige Gradle-Build bleibt als Entwicklungsumgebung (JVM-Tests, Mock-Modus).

## Folgen
- Einmalige Neuverknüpfung, da sich App-ID und Speicherort ändern; der alte Companion kann danach
  deinstalliert werden.
- Signal-Updates erfordern Orbiliq-Releases; der Upstream-Pin wird regelmäßig nachgezogen.
- Kotlin der eingebetteten Module richtet sich nach Signals Toolchain (Kotlin 2.2.x, Material 3 1.4.0),
  da Signal Abhängigkeiten per Checksumme verifiziert.
- Verbreitung nur mit vollständigem Quelltext (Generator erzeugt das Quellarchiv); kein Signal-Branding.
