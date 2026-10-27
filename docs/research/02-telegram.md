# Telegram: Integrationsentscheidung

**Stand: 27.10.2026. Entscheidung: TDLib über die offizielle Java/JNI-Anbindung; erster vollständiger Provider.**

## APIs, Protokoll und Projektwahl

Telegram unterscheidet die Bot API, die Client-API/TDLib und eine Gateway-API für Verifizierungscodes. Für das persönliche Postfach braucht Orbiliq eine **User-Session über TDLib**, keinen Bot-Token. Auch Business-Bots ersetzen nicht den allgemeinen persönlichen Client. MTProto selbst nachzuimplementieren würde Netzwerk-, Kryptografie-, Datenbank- und Updatearbeit ohne erkennbaren Vorteil duplizieren. [Telegram-API-Übersicht](https://core.telegram.org/api).

| Projekt | Befund | Entscheidung |
|---|---|---|
| [TDLib](https://github.com/tdlib/td) | Offizielle C++-Clientbibliothek, Android und Java/JNI; übernimmt Transport, Datenbank und geordnete Updates | **Auswählen**; Kotlin-Adapter um generierte Java-API, Coroutines/Flow nur an der Grenze |
| [TDLib-Android-Beispiel](https://github.com/tdlib/td/tree/master/example/android) | Buildpfad mit NDK/CMake und OpenSSL; kein Compose-UI-Framework | Reproduzierbaren eigenen ABI-Build ableiten statt unbekanntes AAR übernehmen |
| [Telegram Android](https://github.com/DrKLO/Telegram) | Vollständiger offizieller Android-Client, GPL-2.0-Lizenzdatei | Verhaltensreferenz, keinen GPL-UI-/Clientcode in eine MIT-only-App kopieren |
| [Telegram X](https://github.com/TGX-Android/Telegram-X) | Android-Client auf TDLib, GPL-3.0-Lizenzdatei | Belegt Android-Eignung; separaten Appcode nicht mit Boost-TDLib verwechseln |

Die Lizenz der gewählten TDLib ist **Boost Software License 1.0 (BSL-1.0)**, nicht die andersartige „Business Source License“. Sie ist mit eigenem MIT-Code gut kombinierbar; Copyright- und Lizenzhinweise sowie transitive Native-Abhängigkeiten bleiben zu berücksichtigen. [TDLib-Lizenz](https://github.com/tdlib/td/blob/4c6377aa7386c39217878a9a56d581922ad37ca4/LICENSE_1_0.txt).

## Authentifizierung, Session und Multi-Device

Orbiliq benötigt eine eigene `api_id` und `api_hash`. Keine aus einer anderen App kopierten Kennungen oder veröffentlichte Testkennung ausliefern. Die Anwendungskennung ist keine Benutzer-Session und in einer verteilten APK kein verlässlich geheim zu haltender Serverschlüssel. Benutzerautorisierung und Sessiondaten strikt getrennt behandeln. [Eigene Anwendung registrieren](https://core.telegram.org/api/obtaining_api_id).

Der Adapter bildet die TDLib-Authorization-State-Machine ab: Parameter, Telefonnummer oder QR, Code, gegebenenfalls Passwort für 2FA, zusätzliche E-Mail-/weitere vom aktuellen Schema verlangte Schritte, Ready, Logout und Closed. Ein fest verdrahteter Ablauf „Nummer → SMS → fertig“ ist unzureichend. QR-Login kann durch einen bestehenden Client bestätigt werden; auf demselben Telefon ist die konkrete UX zu testen. [User Authorization](https://core.telegram.org/api/auth), [TDLib-Schema](https://github.com/tdlib/td/blob/4c6377aa7386c39217878a9a56d581922ad37ca4/td/generate/scheme/td_api.tl).

**Ohne Google gilt:** TDLib-Verkehr benötigt keine Play Services. Der Server bestimmt jedoch die verfügbaren Login-Verfahren. SMS-/Firebase-/Integrity-Flows sind nicht für jeden Drittclient verfügbar; Code über bestehenden offiziellen Client, QR oder andere vom Server angebotene Verfahren einplanen. Ein vollständig Google-freies Onboarding für jeden neuen Account ist damit nicht garantiert. [Telegram-Authentifizierung](https://core.telegram.org/api/auth), [Firebase-SMS-Methode](https://core.telegram.org/method/auth.requestFirebaseSms).

Pro Account eine eigene TDLib-Instanz mit Datenbank-/Dateiverzeichnis und starkem Datenbankschlüssel. `close` beendet den Client ohne Logout, `logOut` widerruft die Session; niemals beim Prozessende ausloggen. Widerrufene Sessions und Auth-Fehler müssen erneute Anmeldung auslösen. Für Multi-Account Accountspezifik und Dateipfade nie mischen. `setDatabaseEncryptionKey` ermöglicht einen Schlüsselwechsel. Medienablagen separat schützen: Eine verschlüsselte TDLib-Datenbank beweist keine Verschlüsselung aller heruntergeladenen Dateien. [Versioniertes API-Schema](https://github.com/tdlib/td/blob/4c6377aa7386c39217878a9a56d581922ad37ca4/td/generate/scheme/td_api.tl).

Cloud-Chats sind zwischen angemeldeten Geräten synchronisiert. **Secret Chats sind eigenständige, gerätegebundene Sitzungen.** Orbiliq kann neue Secret Chats unterstützen, aber nicht bereits in einem anderen Client bestehende Secret Chats einfach durch Login übernehmen. [TDLib-Einstieg](https://core.telegram.org/tdlib/getting-started).

## Verschlüsselung und Sync

Cloud-Chats, normale Gruppen und Channels sind **nicht Ende-zu-Ende-verschlüsselt**. MTProto-Transportverschlüsselung ist etwas anderes. E2EE gilt für Secret Chats; diese sind 1:1 und haben andere Semantik. Die Unified-Timeline muss diesen Unterschied pro Nachricht/Unterhaltung erhalten. Nie einen globalen E2EE-Schalter für alle Telegram-Inhalte anzeigen. [Secret-Chat-Protokoll](https://core.telegram.org/api/end-to-end).

TDLib betreibt die Protokoll-/Update-/lokale Datenbanklogik. Orbiliq verarbeitet Updates zu Chats, Nachrichten, Sendefehlern, Dateien und Änderungs-/Löschereignissen als Projektion. Historie paginieren (`getChatHistory`), Chatlistenpositionen erhalten und unbekannte Typen ausdrücklich darstellen. Sendestatus erst nach Library-Updates als bestätigt anzeigen. Nicht parallel ein zweites MTProto-Syncsystem entwickeln. [TDLib-Einstieg](https://core.telegram.org/tdlib/getting-started), [offizielle API-Referenz](https://core.telegram.org/tdlib/docs/td__api_8h.html).

## Feature-Einschätzung und konkrete APIs

| Bereich | Einstufung | Vorhandene Schnittstelle / Grenze |
|---|---|---|
| Text senden/empfangen | Z | `sendMessage`, `updateNewMessage`, Send-success/-failed-Updates |
| Cloud-Historie und Multi-Device | Z/E | `getChatHistory`, Updates; nicht vorhandene/gelöschte Historie bleibt unerreichbar |
| Secret Chats | E | `use_secret_chats`, separate Chattypen; keine Synchronisierung fremder Gerätesessions |
| Bilder, Videos, Dateien, Audio und Voice Notes | Z | InputMessageContent, `sendMessageAlbum`, Datei-Upload/-Download; Größen, Rechte und Streaming separat |
| Gruppen, Supergruppen und Topics | Z/E | Gruppentypen, Mitglieder/Rechte, Topics; Administrationsfunktionen nur bei Berechtigung |
| Reaktionen und Antworten | E/Z | `addMessageReaction`, `InputMessageReplyTo`; zulässige Reaktionen/Accountregeln dynamisch |
| Bearbeiten und Löschen | E | `editMessageText`, Medien-/Caption-Methoden; `deleteMessages` mit `revoke`; `messageProperties` beachten |
| Kontakte | E | `importContacts`, Nutzerobjekte, Benutzernamen; Kontakt-Upload nur ausdrücklich, nicht als Voraussetzung |
| Typing, Lesestatus, Presence | E | Entsprechende Updates und Aktionen; Datenschutzeinstellungen respektieren |
| Stories und Emoji-Status | E | Unter anderem `postStory`; Account-, Premium- und Serverregeln |
| Channels | E | Technisch breite Unterstützung, **Sponsored-Messages-Pflicht** als Releasebedingung |
| Audio-/Video-Calls | X | `createCall` und Signalisierung vorhanden; Audio/Video/WebRTC und Android-Integration zusätzlich nötig |
| Offline-Ansicht / Versand | Z/E | TDLib-Cache; Versand ist erst nach tatsächlicher Bestätigung erfolgreich |

Beleg für Methoden/Datentypen ist das [geprüfte Schema am festen Commit](https://github.com/tdlib/td/blob/4c6377aa7386c39217878a9a56d581922ad37ca4/td/generate/scheme/td_api.tl). Damit ist API-Existenz belegt, kein Orbiliq-Laufzeittest.

### Calls: vorhandener Medienstack statt Eigenentwicklung

Als konkreter Kandidat für eine spätere Call-Phase existiert [TelegramMessenger/tgcalls](https://github.com/TelegramMessenger/tgcalls/tree/3a85cd9bfd279ca8aa6f2579e1d2958c75ad03e1). README und LICENSE nennen **LGPLv3**. TDLib-Signalisierung allein liefert keinen Ton, Video oder Android-Audiofokus. Die Kombination mit tgcalls erfordert einen kompatiblen Signalisierungs-/Medienpfad, native Builds, Geräte-/Codec-Tests und Erfüllung der LGPL-Pflichten für kombinierte Werke, insbesondere die einschlägigen Austausch-/Relinking-Möglichkeiten. Nicht als permissiv lizenzierten Zusatz einplanen. Der vollständige Medienstack und seine Drittkomponenten wurden hier nicht auditiert; Calls bleiben X.

## Hintergrundbetrieb ohne Play Services

TDLib braucht einen laufenden Prozess oder einen geeigneten Wake-up-Pfad. Seine Notification API verwaltet anzuzeigende Notifications; sie ist kein Android-Systemdienst und startet keine beliebig beendete App. Push-Registrierung ist providerspezifisch; FCM ist optional, keine Pflicht. UnifiedPush lässt sich nicht ohne Telegram-seitige Unterstützung als beliebiger Ersatz registrieren. Eigene Verbindung und verzögerter Resync sind der lokale Basispfad; Grenzen offen darstellen. [TDLib Notification API](https://core.telegram.org/tdlib/notification-api), [Android-Analyse](05-architecture.md).

## Nutzungsregeln, Sperren und Aufwand

Die API-Bedingungen verlangen unter anderem eigene API-Kennung, deutliche Kennzeichnung der API-Nutzung, Beachtung grundlegender Funktionen/Privatsphäre sowie Unterstützung offizieller Sponsored Messages, sobald Kanäle zugänglich sind. Ein Werbe-SDK ist dafür nicht erforderlich; die Projektvorgabe „keine Werbe-SDKs“ verbietet solche API-Inhalte nicht automatisch. Dennoch verlangt die Anzeige gesponserter Inhalte eine bewusste Produkt- und Datenschutzentscheidung. **Channels zunächst im Prototyp nicht anbieten**, bis eine regelkonforme Entscheidung getroffen ist. Ein Teilclient ist nicht automatisch von den weiteren Grundfunktionspflichten befreit; vor öffentlichem Release vollständigen Abgleich durchführen. [Telegram API Terms](https://core.telegram.org/api/terms).

**Sperrrisiko niedrig bis mittel**, nicht null: Telegram beobachtet Drittclient-Logins, nennt permanente Sperren bei Missbrauch und hat Flood-Limits. Serverseitige `FLOOD_WAIT`-/Auth-Fehler nicht mit hektischen Retries beantworten. **Wartung mittel:** Schema-/TDLib-Updates, NDK-/ABI-/Datenbankmigrationen und neue Authorization States. Kein eigenes Reverse Engineering notwendig. [API-Registrierung und Missbrauchsschutz](https://core.telegram.org/api/obtaining_api_id).

## Umsetzungsempfehlung für das nächste Arbeitspaket

Ein schmaler Kotlin-Provider vor TDLib-Java/JNI; keine Kopie von Telegram-X-Code. Zuerst Cloud-Text, Historie, Dateianhänge und Wiederanmeldung; danach Rechte, Reaktionen und Edits. Secret Chats nur mit korrekter Geräte-/E2EE-Darstellung freischalten. Calls bleiben eigenes Forschungsprojekt mit separater Medienstack-Lizenzprüfung. „GO“ bedeutet Auswahl der Integrationsbasis, nicht fertiger Provider.

Spätere Nachweise: Android-arm64-/Emulator-Build, aktuelle Plattformanforderungen an native Bibliotheken einschließlich 16-KB-Seiten, QR/2FA ohne Play Services, Session-Revoke, Prozessabbruch/Offline-Sync, keine doppelten Sendungen, Mediencache-Schutz, API-Bedingungen. Keine dieser Prüfungen wurde hier ausgeführt.
