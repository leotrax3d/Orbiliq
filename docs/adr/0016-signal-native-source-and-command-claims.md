# ADR 0016: Native Signal-Quelle, kontogebundene Zustimmung und Versand-Claims

- Datum: 28.10.2026
- Status: Implementierung und Abnahme laufen; keine vollständige Providerfreigabe
- Ergänzt ADR 0015 nach echtem Linking-/Sessionnachweis

## Entscheidung

Die Nachrichtenstrecke bleibt im ausgewählten separaten Signal-Android-Companion.
Orbiliq bekommt weder Protokollschlüssel noch direkte HTTP-/Signal-Protokollzugänge.
Eine eigene native Nachrichtenzustimmung bindet die Freigabe an Accountkontext und
aktuellen Orbiliq-Signer. Die vorherige Statusfreigabe erweitert sich nicht automatisch.

Die native verschlüsselte Signal-Datenbank ist Quelle für lokale Wiederanläufe.
Stabile ID-Keysets und beschränkte Binderseiten vermeiden veränderliche OFFSETs
und übergroße IPC-Antworten. Quellbestand und unterstützte Inhalte sind begrenzt;
deshalb `PartialHistory` und `BestEffort` mit ausdrücklicher Sync-Grenze statt
eines unbelegten dauerhaften Empfangsjournals oder beliebigen Serverarchivs.

Für Versand nutzt der Kern seine persistente Outbox. Vor der Übergabe an Signals
MessageSender wird im Companion ein verschlüsselter Claim persistiert. Wiederholte
Commands werden gegen Timestamp/Thread/Bodyhash abgeglichen, niemals blind erneut
gesendet. Ein Absturz zwischen Claim und Quelleintrag darf ein sichtbares unbekanntes
Ergebnis hinterlassen. Native Queueannahme wird nicht als tatsächlicher Versand
oder Zustellung dargestellt. Spätere native Fehler bleiben terminal sichtbar.

## Erste Funktionsgrenze

Normale Direktchat-Texte senden/empfangen; Gruppentitel und normale Gruppentexte
empfangen. Gruppenrollen/-versand, Medien, Antworten/Reaktionen und weitere Aktionen
bleiben bis zur jeweiligen Implementierung und Prüfung gesperrt. Zu lange oder
anderweitig nicht darstellbare normale Inhalte behalten einen ausdrücklichen
Platzhalter. View-once- und verschwindende Inhalte werden nicht exportiert.

Lokaler Nachrichtenwiderruf blendet das Accountmaterial aus der UI aus. Die bereits
verschlüsselte lokale Projektion und native Signal-Session werden dabei nicht
stillschweigend gelöscht oder serverseitig abgemeldet. Ausstehende native Jobs können
bereits übernommene Nachrichten weiter versenden; lokaler Freigabewiderruf ist kein
Remote-Unsend.

## Abnahme

JVM-/Generator-/Persistenzregressionen, echte Accountbindung, Selbstnachrichten-
Roundtrip ohne Dubletten, Neustart, Widerruf, Empfang, Doze und Netzwechsel müssen
mit den jeweils vorhandenen Mitteln belegt werden. Nicht ausgeführte Tests bleiben
offen. Der aktuelle Prüfstand steht in Buildvalidierung und SIGNAL_HANDOFF.
