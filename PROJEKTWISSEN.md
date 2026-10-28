# Projektwissen: Orbiliq

## 1. Was ist Orbiliq?

Orbiliq ist eine moderne, quelloffene, datenschutzorientierte Unified-Messenger-App für Android.

Die grundlegende Idee ist, dass Nutzer nicht mehr zwischen verschiedenen Messenger-Anwendungen wechseln müssen.

Stattdessen sollen sie möglichst ihre gesamte Kommunikation über eine einzige, hochwertige Android-Oberfläche erledigen können.

Orbiliq ist ausdrücklich **kein neuer Messenger-Dienst mit eigenem Kommunikationsnetzwerk**.

Die Anwendung ist ein gemeinsamer Client für bestehende Messenger-Dienste.

### Unterstützte Messenger

Die erste große Version soll folgende Dienste integrieren:

1. Signal
2. WhatsApp
3. Telegram
4. Threema, sofern technisch realistisch

Die Reihenfolge der technischen Integration darf aufgrund von Machbarkeit und Abhängigkeiten angepasst werden.

Langfristig sollen weitere Messenger über eine modulare Provider-Architektur ergänzt werden können.

## 2. Die zentrale Produktvision

Orbiliq soll die Funktionen verschiedener Messenger in einer gemeinsamen Anwendung bereitstellen.

Dazu gehören:

- Gemeinsame Chatliste aller Messenger
- Nachrichten empfangen, lesen und schreiben
- Gemeinsame Kontaktverwaltung
- Zusammenführen mehrerer Messenger-Identitäten derselben Person
- Gemeinsame chronologische Nachrichtenverläufe
- Auswahl des Messengers beim Versenden
- Medien und Sprachnachrichten
- Gruppen und Gruppenverwaltung
- Antworten, Reaktionen, Bearbeiten und Löschen
- Globale Suche
- Zentrale Benachrichtigungen
- Offline-Unterstützung
- Datenschutz- und Sicherheitsfunktionen
- Messenger-spezifische Zusatzfunktionen

Langfristig sollen Nutzer die ursprünglichen Messenger-Apps möglichst selten oder überhaupt nicht mehr öffnen müssen.

Dieses Ziel darf jedoch nicht durch unzuverlässige Integrationen oder falsche Funktionsversprechen erzwungen werden.

## 3. Technologischer Rahmen

Verbindliche Grundlagen:

- Plattform: ausschließlich Android
- Programmiersprache: Kotlin
- Benutzeroberfläche: Jetpack Compose
- Designsystem: Material 3 Expressive
- Architektur: modern, modular, testbar
- Nebenläufigkeit: Kotlin Coroutines und Flow
- Entwicklungsplattform: GitHub
- Lizenz des eigenen Projektcodes: MIT
- Veröffentlichung: zunächst GitHub Releases
- Open Source
- Keine verpflichtenden Google Play Services
- Unterstützung Google-freier Android-Systeme

Die konkrete Auswahl weiterer Bibliotheken und Frameworks erfolgt anhand aktueller technischer Anforderungen.

## 4. Designphilosophie

Orbiliq soll sich wie eine moderne, hochwertige native Android-App anfühlen.

Das Design soll konsequent auf Material 3 Expressive basieren.

Besonders wichtig sind:

- Dynamic Color und Material You
- Light Mode und Dark Mode
- Expressive Shapes
- Shape Morphing
- Spring-basierte Animationen
- Flüssige Übergänge
- Moderne Typografie
- Klare visuelle Hierarchie
- Hochwertige Komponenten
- Edge-to-edge
- Responsive Layouts
- Moderne Navigation
- Expressive Floating Action Buttons
- Bottom Sheets und Dialoge
- Loading States und Empty States
- Gute Accessibility

Die Anwendung soll nicht lediglich wie eine klassische Material-3-App mit stärker abgerundeten Komponenten aussehen.

Animationen sollen hochwertig, sichtbar und reaktionsschnell sein.

Das Design darf die Bedienbarkeit und Zuverlässigkeit nicht beeinträchtigen.

## 5. Technische Grundidee

Orbiliq verwendet eine Provider-/Adapter-Architektur.

Jeder Messenger wird durch einen eigenen Provider integriert.

Der gemeinsame Orbiliq-Kern soll unabhängig von den konkreten Messenger-Protokollen bleiben.

Ein Provider übernimmt beispielsweise:

- Authentifizierung
- Session-Verwaltung
- Synchronisation
- Nachrichten empfangen
- Nachrichten senden
- Medienübertragung
- Kontaktinformationen
- Gruppeninformationen
- Messenger-spezifische Funktionen

Die Integrationsstrategie darf je Messenger unterschiedlich sein.

Mögliche Verfahren sind:

- offizielle APIs
- vorhandene Open-Source-Libraries
- bestehende alternative Clients
- Geräteverknüpfung
- lokale Integration mit installierten Messenger-Apps
- technisch und rechtlich vertretbares Reverse Engineering

Es darf nicht angenommen werden, dass alle Messenger dieselben technischen Möglichkeiten besitzen.

## 6. Capability-System

Jeder Provider muss deklarieren können, welche Funktionen er tatsächlich unterstützt.

Beispiele:

- Textnachrichten
- Bilder
- Videos
- Dateien
- Sprachnachrichten
- Antworten
- Reaktionen
- Bearbeiten
- Löschen
- Weiterleiten
- Gruppen
- Typing Indicators
- Read Receipts
- Presence
- Suche
- Calls
- Status
- Stories
- Channels

Die Benutzeroberfläche und die Domain-Schicht müssen diese Capabilities berücksichtigen.

Nicht unterstützte Funktionen dürfen nicht als funktionsfähig dargestellt werden.

## 7. Unified Contacts und Conversations

Orbiliq unterscheidet zwischen:

**Person:** Eine reale Person innerhalb Orbiliqs.

**Messenger Identity:** Die Identität dieser Person innerhalb eines bestimmten Messengers.

**Conversation:** Eine Unterhaltung innerhalb eines Providers.

Eine Person kann mehrere Messenger-Identitäten besitzen.

Beispiel:

Anna besitzt Signal, WhatsApp und Telegram.

Orbiliq soll diese Identitäten einer gemeinsamen Person zuordnen können.

Der Nutzer muss Zuordnungen korrigieren, zusammenführen und wieder trennen können.

Optional sollen Nachrichten verschiedener Messenger derselben Person in einer gemeinsamen chronologischen Timeline erscheinen.

Jede Nachricht muss weiterhin eindeutig ihrem ursprünglichen Messenger zugeordnet bleiben.

Beim Schreiben muss der Nutzer den gewünschten Versandweg auswählen können.

## 8. Local-first und Datenschutz

Orbiliq soll möglichst vollständig auf dem Android-Gerät arbeiten.

Für den normalen Betrieb darf kein zentraler Orbiliq-Cloud-Dienst notwendig sein.

Sensible Daten müssen angemessen geschützt werden.

Besonders wichtig:

- Sichere Speicherung von Sessions
- Schutz von Kryptoschlüsseln
- Verschlüsselte lokale Datenhaltung, soweit erforderlich
- Konfigurierbarer Medien-Cache
- Datenaufbewahrungsregeln
- Keine unnötige Telemetrie
- Keine Werbe-SDKs
- Keine Tracker
- Möglichst wenige Berechtigungen
- Transparente Berechtigungsbegründungen
- Optionaler biometrischer App-Lock
- Optionaler Screenshot-Schutz

Die Ende-zu-Ende-Verschlüsselung der Messenger darf durch Orbiliq nicht unbemerkt abgeschwächt werden.

## 9. Zuverlässigkeit und Hintergrundbetrieb

Orbiliq soll Nachrichten auch bei instabilen Netzwerkverbindungen zuverlässig verarbeiten.

Die Architektur muss berücksichtigen:

- Offline Queue
- Retry und Backoff
- Synchronisationszustände
- Konfliktbehandlung
- Android Doze
- App Standby
- Prozessbeendigung
- Netzwerkwechsel
- Neustarts
- Akkulaufzeit

Nachrichten dürfen nicht still verloren gehen.

Nicht erfolgreich gesendete Nachrichten müssen für den Nutzer erkennbar bleiben.

## 10. Entwicklungsprinzipien

Die wichtigsten Prioritäten sind:

1. Datenschutz und Sicherheit
2. Zuverlässigkeit
3. Tatsächlich funktionierender Funktionsumfang
4. Design und Benutzerfreundlichkeit
5. Performance und Akkulaufzeit
6. Wartbarkeit
7. App-Größe

Es dürfen keine Fake-Integrationen oder lediglich optisch funktionierenden Features als fertig ausgegeben werden.

Bestehende robuste Open-Source-Lösungen sollen geprüft werden, bevor aufwendige Eigenimplementierungen entstehen.

Lizenzbedingungen und Account-Sperrungsrisiken müssen berücksichtigt werden.

## 11. Entwicklungsprozess

Orbiliq wird schrittweise entwickelt.

Jedes Arbeitspaket baut auf dem tatsächlichen Stand des Repositorys auf.

Vor jeder Implementierung muss der Agent:

1. Das Repository untersuchen.
2. Relevante Dokumentation lesen.
3. Bestehende Architektur verstehen.
4. Vorhandene Funktionen identifizieren.
5. Tests und Build-Zustand prüfen.
6. Erst anschließend Änderungen durchführen.

Der Agent darf bestehende funktionierende Komponenten nicht unnötig ersetzen.

Er soll sinnvolle technische Entscheidungen eigenständig treffen.

Bei erheblichen Sicherheits-, Datenschutz-, Lizenz-, Architektur- oder Account-Risiken muss er Rückfragen stellen, wenn keine eindeutig vertretbare Lösung existiert.

## 12. Langfristige Projektkontinuität

Die Datei `docs/PROJECT_CONTEXT.md` soll als zentrale Wissensbasis dienen.

Sie enthält:

- Produktvision
- Architekturgrundsätze
- Technologiestack
- bereits implementierte Funktionen
- Entwicklungsstand
- wichtige Entscheidungen
- bekannte Einschränkungen
- nächste geplante Entwicklungsschritte

Nach jedem Arbeitspaket muss geprüft werden, ob diese Dokumentation aktualisiert werden muss.

Wichtige Architekturentscheidungen werden zusätzlich als ADRs dokumentiert.

Die Wissensbasis darf niemals den tatsächlichen Repository-Stand ersetzen: Bei Widersprüchen muss der Agent die Ursache untersuchen.

## 13. Allgemeine Definition of Done

Ein Arbeitspaket ist erst abgeschlossen, wenn:

- Die vereinbarten Funktionen implementiert oder nachweislich blockiert sind.
- Die Architektur konsistent bleibt.
- Bestehende Funktionen nicht unbeabsichtigt beschädigt wurden.
- Relevante Tests erfolgreich sind.
- Neue Funktionen angemessen getestet wurden.
- Fehlerfälle berücksichtigt wurden.
- Dokumentation aktualisiert wurde.
- Keine sensiblen Informationen in Logs oder Testartefakten enthalten sind.
- Verbleibende Probleme transparent dokumentiert sind.
- Der nächste Entwicklungsschritt auf einem nachvollziehbaren Stand aufbauen kann.

Wenn Tests mangels Hardware, Accounts oder Infrastruktur nicht ausgeführt werden können, muss dies ausdrücklich dokumentiert werden.

Ein nicht ausgeführter Test darf niemals als bestanden ausgegeben werden.
