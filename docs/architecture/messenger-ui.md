# Messenger UI · Material 3 Expressive

Stand und Quellenprüfung: 27.10.2026.

## Implementierung

`feature:inbox` enthält die Smartphone-Oberfläche. Drei gleichrangige Ziele: Chats,
Menschen und Konten. Eine Unterhaltung öffnet sich ohne Bottom Navigation; Details,
Personen und Einstellungen haben explizite Zurückwege und unterstützen Android Back.
Die Nachricht-FAB öffnet ein Sheet zur Auswahl vorhandener Unterhaltungen. Neue
Remotechats/-kontakte können über den bisherigen Domainvertrag noch nicht angelegt werden.

Die Inbox zeigt alle Accounts der Repositoryprojektion nach letzter Aktivität. Filter:
Alle, Direkt, Gruppen, Archiv. Die Suche umfasst Titel und letzte Nachricht, keine
Volltextsuche über den Verlauf. Archivierung ist lokal dauerhaft und umkehrbar.
Personen zeigen ihre Identitäten und vorhandene Direktchats; Konten ihre Providerherkunft.
Mock-Hinweise vermeiden den Eindruck echter Signal-/WhatsApp-/Telegram-Verbindungen.

Conversation View: chronologischer Verlauf mit Datum, Sender bei Gruppen, ursprünglicher
Providerherkunft, Versandstatus, Reply-Auswahl und mehrzeiligem Composer. Versand nutzt
`UnifiedCommunication` und die persistente Outbox. Capabilityprüfung vor Aktivierung und
erneut vor Versand; Fehler erhalten den Entwurf. Unklare Effekte werden separat benannt.
Entwürfe und Reply-Auswahl leben im SavedState pro Bildschirm: erhalten bei Navigation
und Activity-Neuanlage, keine zugesicherte dauerhafte Draftdatenbank nach Taskentfernung.

`MessengerRepository` ist ein UI-Port in `core:domain`, implementiert vom bestehenden
`PersistentMockRepository`. UI kennt keine Provider-Session oder Storageimplementierung.
Projektion und schreibende Aktionen laufen im Adapter auf Dispatchers.IO. Der App-Mock
beginnt mit der aktuellen Uhrzeit und gleicht seine virtuelle Uhr vor Kommunikationsaktionen
ab; Commands verwenden dieselbe Sessionuhr. Isolierte Mock-Szenarien bleiben deterministisch.
`MessengerSnapshot` liefert Kontakte/Links/Accounts und archivierte Unterhaltungen; Timeline
und Versand bleiben beim Unified-Communication-Port. Release enthält weiterhin keinen Mock.

## Design und Bedienung

- Bestehendes Material 3 `1.5.0-beta02` bleibt bewusst gepinnt; Expressive-APIs sind nicht
  vollständig in der stabilen Version 1.4.0 öffentlich verfügbar.
- `MaterialExpressiveTheme`, expressive Light-Fallbackpalette, dynamische Farben ab API 31,
  persistierte Auswahl System/Hell/Dunkel und Dynamic Color. Systemleisten folgen der Auswahl.
- Emphasized-Headlines, asymmetrische Avatare/Chatflächen, gruppierte Container,
  Medium Extended FAB, Material Bottom Sheets und Bestätigungsdialog.
- Morphing der Filterecken mit `fastSpatialSpec`, Seitenwechsel mit Spatial-/Effects-Tokens,
  animierte Listeneinträge. Compose beachtet die systemweite Animationsskalierung.
- Edge-to-edge über Activity, Scaffold-Insetverbrauch, IME-Padding und `adjustResize`.
- Touch Targets mindestens 48 dp, 56-dp-Sendebutton, skalierende Systemtypografie,
  horizontal scrollbarere Filter, Textlabels und Auswahlsemantik. Keine heruntergeladenen Fonts.
- Expressive LoadingIndicator plus Skeletonflächen; erklärte leere Ergebnisse, fehlende
  Konten, Ladefehler mit Retry und Snackbar für Speicher-/Versandfehler.

## Offizielle Referenzen

Am 27.10.2026 geprüft:

- [Compose Material 3 Release Notes](https://developer.android.com/jetpack/androidx/releases/compose-material3):
  Stand 21.10.2026, stable 1.4.0, beta 1.5.0-beta02.
- [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3):
  Expressive Theme und Dynamic Color mit Light/Dark-Fallback.
- [Material Motion Physics](https://m3.material.io/styles/motion/overview/how-it-works):
  Spatial-/Effects-Springs und Expressive Motion Scheme.
- [Edge-to-edge](https://developer.android.com/develop/ui/compose/system/setup-e2e)
  und [Insets](https://developer.android.com/develop/ui/compose/system/insets-ui):
  Systemleisten, Insetverbrauch und Tastatur.
- [Accessibility API Defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults):
  mindestens 48 dp für interaktive Ziele.

## Validierungsgrenzen

JVM-Integrationstests prüfen die UI-Repositoryprojektion, Kontakte und Konten, persistentes
Archiv und Wiederherstellung, Text/Reply-Outbox und Ablehnung bei ReadOnly-Capabilities.
Android-UI-Tests decken Composer, Entwurferhalt, Navigation, Filter und Darstellungseinstellungen
ab; Ausführung auf Hardware steht aus. Kein Emulator auf diesem Laptop starten (bestehende
Projektvorgabe). Screenshots, tatsächliches Springgefühl, TalkBack, große Schrift und
IME-/Systemleistenverhalten müssen auf einem Gerät abgenommen werden.

Medien werden als erklärte Platzhalter dargestellt. Aufnahme/Upload/Playback, Edit/Delete/
Reaktionen in der Oberfläche, Kontakt-Merge-Verwaltung, neue Remoteunterhaltungen und reale
Kontoverknüpfung sind nicht Teil dieser UI-Foundation. Keine inaktiven Aktionsattrappen dafür.
