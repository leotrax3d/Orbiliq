# Signal Provider: direkte Textstrecke in Entwicklung

Stand 28.10.2026. Prompt 11 ist noch nicht abgenommen. Echtes Linking und lokale
Sessionpersistenz sind belegt; die neue Nachrichtenstrecke muss erst mit dem neu
gebauten Companion auf dem Gerät geprüft werden.

## Vertrauensgrenze

Der separate AGPL-Companion bleibt ein modifizierter, festgelegter Signal-Android-
Client. SignalStore, Identitätsschlüssel, Prekeys, Ratchets, Netzwerkverbindungen,
Identity-Mismatch-Prüfungen und JobManager bleiben dort. Orbiliqs MIT-Module
implementieren keine Signal-Kryptografie. IPC bleibt signatur-/UID-geschützt.

Die vorhandene Statusfreigabe gewährt weiterhin keinen Nachrichtenzugriff. Eine
zweite native Zustimmung ist an Clientsigner und SHA-256-Pseudonym von ACI,
Device-ID und Registrierungszeitpunkt gebunden. Jeder Nachrichtenaufruf prüft
Bindung, Registrierung als Linked Device, Sessionentzug und Companion-Lock erneut.
Ein Accountwechsel oder Signerwechsel hebt die Nachrichtenberechtigung nicht auf
einen anderen Account über. Lokaler Widerruf löscht beide Freigaben; er meldet den
Signal-Account nicht ab.

## Datenfluss

`BridgeMessaging` liest die eigene native Signal-Datenbank, keine Datenbank einer
anderen installierten App. Listen und Texte passieren Binder in begrenzten Seiten.
Eine kleine zusätzliche native MessageTable-Methode verwendet stabile ID-Keysets
statt veränderlicher OFFSET-Seiten. Geschichten, geplante Nachrichten und überholte
Editrevisionen werden aus der Seitenauswahl ausgeschlossen.

Die erste Ausbaustufe exportiert normale Direktchat- und Gruppentexte bis 2048
UTF-8-Bytes. Größere Texte benötigen upstream einen Long-Text-Anhang; der Adapter
gibt dafür bisher einen Platzhalter aus. Die Grenze wird gegen die tatsächliche
Upstream-Konstante geprüft. View-once-Inhalte, verschwindende Nachrichten und Chats mit
aktivem Expire-Timer werden nicht in den Kern kopiert. Gruppen sind zunächst nur
lesbar; Medien bleiben im nativen Client und erhalten einen Inhaltsplatzhalter.
Bekannte lokale Nachrichten werden gegen die
Quelle geprüft; entfernte oder nicht mehr exportierbare Inhalte erzeugen
`MessageDeleted`. Alte Editrevisionen werden nicht weiter ausgegeben.

`SignalMessagingSession` bildet diese Quelle auf Providerereignisse ab. Referenzen
bleiben accountgebunden; Service-IDs werden pseudonymisiert, Telefonnummern dienen
nicht als Primärschlüssel. Es gibt kein behauptetes unbegrenztes Serverarchiv:
Qualität `PartialHistory`, Replay `BestEffort`, sichtbarer Gap-Grund für die Grenze
des nativen Gerätebestands. Ein Prozessneustart liest den nativen Bestand erneut.
Es gelten Schutzgrenzen von 1000 Threads und 2000 Seiten pro anfänglichem Chat-
Scan; ein überschrittener Scan wird nicht als vollständig markiert.

`SignalMessengerRepository` verwendet die vorhandene Keystore-/AES-GCM-Projektion
und SyncCoordinator/UnifiedCommunicationService. Account- und Conversation-
Capabilities steuern den Versand. Eine laufende, autorisierte IPC-Verbindung ist
kein Beweis einer Signal-Serververbindung; der Provider kennzeichnet das ausdrücklich
als `signal.connection.local-ipc`. Nicht mehr autorisierte Konten werden aus der
UI-Projektion ausgeblendet, verschlüsselte lokale Speicherung bleibt erhalten.

## Versand und unbekannte Wirkung

Der generische Kern persistiert die Outbox vor Dispatch. Der Companion persistiert
zusätzlich einen verschlüsselten Command-Claim mit Thread, Timestamp und Bodyhash
vor `MessageSender.send`. Keine Nachrichtentexte im Ledger. AES-GCM verwendet einen
separaten nicht exportierbaren Keystore-Schlüssel und die Accountbindung als AAD.
Fehlende Schlüssel bei vorhandenem Ledger erzeugen keine stillen Ersatzschlüssel.

Ein wiederholter Command wird nur abgeglichen, niemals erneut an MessageSender
übergeben. Nach einem Crash zwischen Claim und Insert bleibt die Wirkung unbekannt,
statt eine mögliche Dublette zu erzeugen. Die native Signal-Warteschlange übernimmt
Netzwerk-Retries erst nach erfolgter Einreihung. `Accepted` ist keine Zustellung.
Sent/Delivered/Read stammen aus der nativen Quelle. Native Fehler bleiben als
Fehler sichtbar; späte lokale Dispatchantworten überschreiben sie nicht.

## Capabilities und Prüfung

Freigegebene Capabilities (Stand `586a3c2`), jeweils nur, wenn die Strecke implementiert ist:

| Capability | Umfang |
|---|---|
| `message.text.receive`, `signal.group.title.receive` | Direktchats, „Notiz an mich“, aktive Gruppen (nur lesen) |
| `message.text.send` | Direktchats; 2048 UTF-8-Bytes; nicht bei Ablauf-/Universal-Timer |
| `message.quoted-reply.send`, `signal.quote.receive` | Zitat nur auf exportierbare Textnachricht desselben Direktchats |
| `reaction.add`, `signal.reaction.receive` | eigene Reaktion setzen/ändern (idempotent); Entfernen nicht freigegeben |
| `signal.image.receive` | JPEG/PNG/WebP/GIF bis 16 MiB, Abruf auf Tippen, nur im Speicher |
| `message.media.image.send` | JPEG ohne Metadaten (doppelt neu kodiert) |

Senden (Text, Antwort, Reaktion, Bild) gilt für Direktchats und für Gruppen, in denen das
offizielle Eingabefeld schreiben ließe: aktive v2-Gruppe, Voll-/Adminmitglied,
Ankündigungsgruppen nur für Admins, ohne Ablauftimer.

Nicht freigegeben: Gruppenverwaltung, Video/Audio/Sprachnachrichten/Dateien,
Bearbeiten, Löschen, Typing, Read-Receipt-Versand, Suche, Calls, Stories,
View-once und verschwindende Inhalte (bleiben nativ). Diese Liste beschreibt die
implementierte Adapterstrecke, nicht alle Fähigkeiten des nativen Clients.

Einrichtung mit nur einem Handy: Der Verknüpfungsbildschirm des Companion kann den
aktuellen, kurzlebigen QR-Code nach Bestätigung als Bild + `sgnl://linkdevice`-Link
teilen (nur ephemerer öffentlicher Schlüssel; Datei wird beim Verlassen gelöscht),
damit er auf einem beliebigen zweiten Bildschirm gescannt werden kann. Offizielles
Signal bietet laut Upstream-Code keinen kamerafreien Verknüpfungsweg.

Dreizehn neue JVM-Tests prüfen unter anderem unbekannte Wirkung ohne Resend,
Accountwechsel, fremde Routen, Snapshot-Deduplizierung und Löschereignisse.
Ein zusätzlicher Persistenztest schützt gegen fälschlich bestätigte native Queues
und gegen das Überschreiben späterer Versandfehler. Opt-in Gerätetests prüfen
separate Zustimmung, Bindungsisolation und einen vollständigen Outbox-Roundtrip
ausschließlich nach „Notiz an mich“, einschließlich Wiederholung desselben Commands.
Noch nicht ausgeführte Gerätetests gelten ausdrücklich nicht als bestanden.

Hintergrundempfang im nativen Companion, Android Doze, Geräteentzug, größere
Historien, Native-Job-Retries und tatsächlicher Empfang fremder Geräte müssen
weiter geprüft werden. Orbiliqs eigener App-Prozess läuft nicht garantiert dauerhaft;
die native Quelle bleibt der Wiederanlaufpunkt.
