# Empfohlene lokale Provider-Architektur

**Stand: 27.10.2026. Architekturvorschlag; keine Module oder Schnittstellen implementiert.**

> Fortschreibung: Der [Gesamtentwurf](../architecture/README.md) und [Providervertrag](../architecture/provider-contract.md) konkretisieren diese Vorstudie. Für die Zielarchitektur gelten ADR 0002–0008; die Recherche- und Freigabegrenzen bleiben erhalten.

## Grenzen und Datenfluss

Orbiliq sollte einen eigenen Kotlin-/MIT-Kern mit Domainmodell, verschlüsselter lokaler Projektion und Compose-Oberfläche erhalten. Jeder Provider besitzt seinen tatsächlichen Auth-/Protokollzustand. TDLib kann nach Prüfung direkt eingebunden werden; Copyleft-Backends werden zunächst als eigenständige optionale Companions betrachtet. Die Lizenzbewertung der Werkgrenze ist eine Freigabebedingung, keine technische Garantie. [Lizenzdossier](06-licenses-and-risks.md).

```text
Compose-Oberfläche
       |
Domain: Personen, Provideridentitäten, Conversations, Nachrichten, Capabilities
       |
Lokale verschlüsselte Projektion / Suchindex / Ausgangsaufträge
       |
Provider-Koordination, Ereignisjournal, Sync- und Authzustände
       +-- Telegram-Adapter -- TDLib Java/JNI -- Telegram
       +-- lokale authentifizierte IPC -- Signal-Companion -- Signal
       +-- lokale authentifizierte IPC -- WhatsApp-Companion (experimentell)
       +-- Notification-/Antwort-/Handoff-Adapter -- installierte Original-Apps

Kein obligatorischer Orbiliq-Server; Companion-/Bridge-Server wären andere Betriebsarten.
```

Die Skizze beschreibt logische Zuständigkeiten, keine bereits bewiesene Lizenztrennung. Original-Apps liegen in ihrer Android-Sandbox. Kein Root, kein Auslesen fremder Datenbanken, keine Accessibility-Klickautomatisierung oder fremde Sessionextraktion als Produktionsgrundlage.

## Providervertrag und Capabilities

Ein einzelnes `supportsMessages = true` ist zu grob. Ein Provider sollte pro Account, Conversation, Nachricht und Operation ausweisen:

| Aspekt | Beispiele |
|---|---|
| Datenqualität | Nur Vorschau; ab Linking vollständig; History-Lücken; synchronisiert bis Zeitpunkt/Checkpoint |
| Lesen / Schreiben | Text, Datei, Voice Note jeweils getrennt für Empfang und Versand |
| Änderungen | Zitierende Antwort, Reaktion, Edit, lokales Löschen, Remote-Löschen mit Frist/Recht |
| Auth | Benutzereingabe erforderlich, QR ausstehend, bereit, entkoppelt, gesperrt, nicht unterstützt |
| Synchronisation | Verbindung besteht; Catch-up läuft; eingeschränkter Empfang; letzte erfolgreiche Verarbeitung |
| Verschlüsselung | E2EE, Cloud-Chat ohne E2EE, Vorschau aus fremder App; Verifikations-/Identitätsstatus |
| Betrieb | Direkte Session oder Original-App erforderlich; lokales Backend oder externer Server |
| Reife | Z, E, X oder N nach [Definition](README.md#feature-matrix), zusätzlich Geräte-/Libraryversion |

Zeitfenster/Rechte kommen vom Provider. Eine global ausgeblendete Edit-Funktion wäre zu wenig, eine überall aktive Edit-Schaltfläche falsch. Unbekannte Nachrichtentypen erhalten einen erklärenden Platzhalter mit Providerherkunft; kein stilles Verwerfen. Keinen Messenger stillschweigend als Ausweichversand nutzen.

## Einheitliche Identitäten und Timeline

- Schlüssel mindestens aus Provider, Account, Conversation und Provider-Nachrichten-ID zusammensetzen. UUIDs, ACI/PNI, Telegram-IDs, WhatsApp-JID/LID und Threema-ID nicht auf Telefonnummern reduzieren.
- Eine reale Person wird lokal und reversibel mit mehreren Provideridentitäten verknüpft. Telefonnummer, Anzeigename und Avatar sind Vorschläge, keine belastbare Identitätsverifikation. Kein automatischer Kontaktupload für den Abgleich.
- Gemeinsame Timeline ist eine lokale Projektion. Original-Konversation, Transport, E2EE-Status, Thread-/Reply-Bezug, Sender, Server-/Clientzeit und Zustellzustand bleiben sichtbar bzw. rekonstruierbar.
- Nachrichten anderer Dienste sind keine kryptografisch oder semantisch gemeinsame Conversation. Gruppen verschiedener Provider niemals allein nach Namen zusammenführen.
- Zeitstempel erlauben nur eine deterministische Anzeigeordnung, keine globale kausale Reihenfolge über alle Dienste. Edits, Deletes und Reactions referenzieren immer das Originalereignis.

Diese Anforderungen folgen aus den unterschiedlichen Identitäts-, Cloud- und Multi-Device-Modellen der [Einzelanalysen](README.md).

## Zustellung, Ausgangsaufträge und Resynchronisation

1. Nutzerauftrag mit stabiler lokaler ID, explizitem Versandweg und gesicherten Medienreferenzen persistieren.
2. Provider sendet und liefert ein Ergebnis mit Provider-ID oder eindeutigem Fehler. „An Library übergeben“ ist noch keine Empfängerzustellung.
3. Timeout nach möglichem Versand ist **Ergebnis unbekannt**, nicht automatisch „nicht gesendet“. Wo möglich Provider-ID/Idempotenz und Sync-Ereignisse abgleichen. Kein Exactly-once-Versprechen über APIs hinweg.
4. Exponentieller Backoff mit Jitter für vorübergehende Netzfehler, Server-Retry-Vorgaben priorisieren. Authentifizierungs-, Identitäts-, Lizenz- und Ban-Fehler verlangen eigene Zustände, keine Endlosschleife.
5. Ereignisjournal und Projektionsänderung atomar persistieren; Duplikate anhand stabiler Provideridentität erkennen. Fortschritt erst nach dauerhafter Verarbeitung bestätigen, soweit das Backend eine solche Kontrolle erlaubt.
6. Falls die Library schon vorher quittiert und keinen Replaypfad bietet, genau diese Verlustlücke messen und dokumentieren. Ein Flow-Collector allein ist kein dauerhaftes Empfangsjournal.
7. Nach Prozessneustart erst Session/Sync rekonstruieren; nicht pauschal Queue erneut senden. Löschungen und Ablauffristen gelten auch für Suchindex, Vorschaudaten und Medienkopien.

Providerstores bleiben Quelle für Protokollzustand; Orbiliqs Projektion dient Oberfläche/Suche. Keine unabhängige zweite Ratchet- oder MTProto-Zustandsmaschine entwickeln.

## Schlüssel, lokale Daten und IPC

Keystore-geschützte Wrapping-Schlüssel für zufällige Datenbankschlüssel; Libraryschlüssel müssen gegebenenfalls kurzzeitig im Java-/Rust-/Go-Speicher vorliegen. Nicht pauschal behaupten, alle Protokollschlüssel blieben im Keystore. Datenbanken, Journal, Volltextindex, temporäre Uploads, Thumbnails und Mediencache gesondert in den Schutzumfang nehmen. TDLib-eigene DB-Verschlüsselung ersetzt keine verschlüsselte Unified-Projektion. [TDLib-Dokumentation](https://core.telegram.org/tdlib/).

Sensible Sessiondateien nicht in öffentliche Verzeichnisse oder unkontrollierte Android-Backups exportieren. Ein späteres Backup-/Transferformat muss Kryptositzungen und Medien sauber unterscheiden. Biometrischer App-Lock und Hintergrundentschlüsselung brauchen eine explizite Produktentscheidung: Ein zwingend bei jeder Verwendung zu entsperrender Schlüssel kann Empfang im gesperrten Zustand verhindern.

Companion-IPC: explizite Services, gegenseitige Identifikation, Nutzerbindung und möglichst kleine Berechtigungsfläche. Unterschiedlich signierte unabhängige APKs können nicht einfach dieselbe Signature-Permission verwenden; Zertifikats-/UID-Prüfung und sicherer Pairing-Mechanismus sind zu entwerfen. Kein ungeschütztes Localhost-HTTP; keine Secrets in Intents oder Logs. Medien per kurzlebiger berechtigter URI/Dateideskriptor statt allgemein zugänglichem Dateipfad. Binder-Payloadgrenzen und Backpressure einplanen. Diese Maßnahmen machen IPC sicherer, entscheiden aber nicht die Copyleft-Frage.

## Android-Hintergrundbetrieb ohne Play Services

**Keine Strategie garantiert sofortigen Empfang unter allen Android-Zuständen.** Doze kann Netzwerkzugriff und Jobs verschieben; Force-stop und entzogene Berechtigungen können Empfang unterbrechen. WorkManager ist für nachholbare Arbeit geeignet, nicht für eine durchgehend offene Messenger-Verbindung oder zeitgenaue Inbox-Abfragen. [Android: Doze und App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).

Ein Foreground Service ist keine pauschale Dauerlösung. Android dokumentiert für Apps mit Target Android 15+ unter anderem ein gemeinsames sechsstündiges Hintergrundbudget pro 24 Stunden für `dataSync`-Services; andere Typen haben eigene Bedingungen. Kein willkürliches Ausweichen auf `remoteMessaging` oder `specialUse`: Die tatsächliche erlaubte Nutzung ist zu belegen. GitHub-Sideloading hebt OS-Grenzen nicht auf. [FGS-Timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout), [FGS-Typen](https://developer.android.com/develop/background-work/services/fgs/service-types).

| Betriebsart | Nutzen | Grenze |
|---|---|---|
| App sichtbar / aktive Verbindung | Direkter Empfang und Sync | Kein Hintergrundnachweis |
| Zulässiger sichtbarer Verbindungsdienst, ggf. bewusste Akku-Ausnahme | Kann kontinuierlichen Empfang verbessern | Diensttyp, Doze, OEM und Energieverbrauch prüfen; keine Garantie |
| Geplante Hintergrundarbeit | Catch-up, Retries, Cachepflege | Verzögerbar; keine Echtzeit-Inbox |
| Optionales providerunterstütztes Push | Effizienterer Wake-up | FCM keine Pflicht; Drittclient-/Tokenunterstützung gesondert |
| UnifiedPush | Offener Wake-up-Pfad bei geeigneter Gegenseite | Kein universeller Pushzugriff auf vier Dienste; ggf. Server nötig |
| Original-App plus Notification Listener | Nutzt deren Empfangspfad | Nur veröffentlichte Notifications, keine Vollhistorie |

Signal/Molly zeigt konkret, dass UnifiedPush einen MollySocket-Server benötigt und im geprüften Stand für Linked Devices nicht verfügbar ist. Threema dokumentiert eigenen Push und benötigte Hintergrundfreigaben. Das sind dienstspezifische Lösungen. [Molly](https://github.com/mollyim/mollyim-android/blob/892d6264ba007b5458a8b74cf9183c665f1f17a1/README.md#unifiedpush), [Threema Push](https://threema.com/en/faq/threema-push).

Orbiliq soll letzte erfolgreiche Synchronisierung, Verbindungszustand und eingeschränkten Hintergrundbetrieb anzeigen. „Keine neuen Nachrichten“ darf nicht behauptet werden, wenn nur die Verbindung fehlt. Neustart/Entsperren/Netzwechsel lösen einen erlaubten Resync aus; Geräteentzug führt zu Reauth, nicht ungefragtem Relinking.

## Benachrichtigungsintegration: genaue Reichweite

`NotificationListenerService` liefert vom System freigegebene Benachrichtigungen nach expliziter Nutzerfreigabe. Er eröffnet weder die Messenger-Datenbank noch einen beliebigen Sendekanal. Auf aktuellen Android-Versionen können sensible Inhalte redigiert werden; Profile und Gerätepolitik begrenzen Zugriff zusätzlich. [Android-Referenz](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

Eine vom Messenger veröffentlichte Reply-Action kann `RemoteInput` und einen `PendingIntent` enthalten. Orbiliq kann eine vom Nutzer eingegebene Antwort darüber übermitteln, wenn Aktion, Chatbindung und Berechtigung noch gültig sind. Ein erfolgreich ausgeführter PendingIntent beweist keine Serverzustellung; Original-App kann gesperrt sein oder später scheitern. [Android: Direct reply](https://developer.android.com/develop/ui/compose/notifications/create-notification#reply-action).

Nicht belastbar lieferbar: komplette ältere Historie, sämtliche ausgehenden Nachrichten, stumme Chats ohne Notifications, vollständige Kontaktliste, Medienarchiv, konsistente Reactions/Edits/Deletes, Gruppenverwaltung, Offline-Versand mit Zustellgarantie oder beliebige neue Conversations. Chatnamen/Notification-IDs sind zudem keine verlässlichen Dienst-IDs. Duplikate/Updates/Sammelnotifications und verschwundene Vorschauen nicht zu erfundenen Nachrichten zusammenbauen.

Share-Intents und dokumentierte Deep Links sind hilfreiche Übergaben. Sie können die Original-App öffnen, bieten aber keinen garantierten Versand innerhalb Orbiliqs. `Quick reply` und zitierende Antwort sind unterschiedliche Capabilities. Fallback nur getrennt und als **Ergänzungsmodus** anbieten, nachgewiesenen Vollprovider-Empfang nicht zusätzlich duplizieren.

## Spätere Verifikation und Betriebsfreigabe

Noch nicht ausgeführte, notwendige Prüfungen:

- Emulator und reale Geräte, mindestens Google-freies System plus Herstellergerät; aktuelle Android-Versionen und native 16-KB-Kompatibilität.
- Login, 2FA/QR, Session-Revoke, Kontowechsel und Schlüssel-/Identitätswechsel mit eigenen Testkonten.
- Flugmodus, Offline-Senden, App-/Companion-Prozessabbruch während Send/Receive, Reconnect, Neustart, gesperrtes Gerät, Force-stop, Doze und App Standby.
- Gleichzeitige Nutzung mit Primärclient: eigene ausgehende Nachrichten, Lesezustand, Edits/Deletes, Reactions, Gruppen und History-Lücken.
- Große/fehlende Medien, doppelte/ungeordnete Ereignisse, beendete/ablaufende Inhalte, nicht unterstützte Typen.
- Akkumessung bei mehreren Accounts, sichtbare Diagnose, keine sensiblen Logs, keine ungeschützten Dateien.
- Exakter Dependency-Lock, vollständige Lizenzliste, Source-/Notice-Pflichten und Review der ausgelieferten Kombination.

Ein Provider erhält erst nach diesen Nachweisen konkrete produktive Capabilities. Dieser Bericht dokumentiert Auswahl und Risiken, keinen bestandenen Integrations- oder Sicherheitstest.
