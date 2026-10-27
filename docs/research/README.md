# Messenger-Machbarkeitsanalyse für Orbiliq

**Stand: 27. Oktober 2026. Status: Recherche und Architekturentscheidung, keine Implementierung.**

## Ergebnis

Eine lokale Android-App mit weitgehend vollständigem Zugriff auf alle vier persönlichen Messenger-Konten ist **kein belastbar zusagbares erstes Release**. Telegram ist mit einer offiziellen Client-Bibliothek gut integrierbar. Signal ist technisch möglich, erfordert aber eine Copyleft- und Android-Strategie. WhatsApp bleibt eine inoffizielle, sperrungsgefährdete Integration. Threema ist für die erste Version auf eine ergänzende lokale Integration zu begrenzen.

Die Entscheidung berücksichtigt Kotlin, Compose, lokale Verarbeitung, Google-freies Android und MIT für eigenen Orbiliq-Code. „MIT für eigenen Code“ und „ausschließlich MIT-Bedingungen für das gesamte ausgelieferte Programm“ sind unterschiedliche Anforderungen. Die zweite wird durch vorhandene Signal-, WhatsApp- und Threema-Abhängigkeiten vielfach verhindert. Siehe [Lizenzprüfung](06-licenses-and-risks.md).

## Entscheidungsmatrix

Die Bewertungen sind technische Schlussfolgerungen dieser Recherche. Sie ersetzen keinen Geräteversuch. Details und Primärbelege stehen in den verlinkten Einzelanalysen und im [Quellenregister](07-sources.md).

| Messenger / Strategie | Lokaler Android-Betrieb | Funktionspotenzial | Lizenz für Gesamtpaket | Konto-/Betriebsrisiko | Entscheidung |
|---|---|---|---|---|---|
| Telegram: TDLib über JNI | Offiziell vorgesehene Plattform | Sehr hoch; Cloud und separate Secret Chats | Boost-Lizenz, gut mit eigenem MIT-Code kombinierbar; weitere Abhängigkeiten prüfen | Vergleichsweise niedrig; API-Regeln, Flood-Limits, Login-Grenzen | **GO als erster vollständiger Provider** |
| Signal: separater Companion auf Basis Signal Android/Molly | Android-Basis vorhanden; IPC und Provider müssten entwickelt werden | Hoch; verknüpftes Gerät bevorzugen | AGPL-Komponente; Eigenanteile können MIT bleiben, unabhängige Werkgrenze prüfen | Mittel bis hoch; keine freigegebene Service-API für Fremdclients | **Bedingtes GO für Machbarkeitsprototyp, kein Release-GO** |
| Signal: signal-cli direkt in Haupt-APK | Aktuell JRE 25 und native Bibliotheken; kein Android-SDK | Gute Messaging-Abdeckung; Calls fehlen als fertiger Stack | GPL plus AGPL-/Service-Abhängigkeiten | Hoher Portierungs- und Aktualisierungsbedarf | **Nicht als direkte Android-Basis wählen**; Protokoll-/Feature-Referenz |
| WhatsApp: whatsmeow in getrenntem Companion | Go/NDK-Brücke technisch untersuchbar, nicht nachgewiesen | Gutes Messaging, begrenzte Historie; keine Calls | MPL **plus GPL-Abhängigkeit**; nicht MIT-only | Hoch; inoffizielles Protokoll, Sperren, Entkopplung | **Nur experimenteller Opt-in-Prototyp nach Lizenzklärung** |
| WhatsApp: Baileys | Zusätzliche Node-kompatible Runtime nötig | Breites Messaging | MIT **plus GPL-libsignal** | Hoch; Breaking Changes und Sperren | **Alternative Referenz**, schlechterer Android-Fit |
| WhatsApp: Cloud API / DMA | Backend bzw. eigener interoperabler Dienst nötig | Geschäftskonto bzw. dienstübergreifende Chats | API-Verträge statt persönlicher Client-Lizenz | Offizielle Zugänge, aber anderes Produktmodell | **Für persönliche Kontenintegration verwerfen** |
| Threema: Benachrichtigungen, Antworten, App-Übergabe | Lokal, Original-App bleibt erforderlich | Nur Ausschnitte und bedingte Schnellantwort | Eigener Adapter MIT möglich | Niedriges Protokollrisiko, hohe funktionale Begrenzung | **GO als klar begrenzte Ergänzung**, nicht als vollständiger Provider |
| Threema: Desktop-Multi-Device-Port | Vorhandene Protokolle und Rust-Code, aber kein fertiges Android-Client-SDK | Potenziell Messaging, Hersteller-Beta begrenzt | AGPL, auch libthreema | Sehr hoher Integrationsaufwand; Drittclient-Freigaben offen | **Zurückstellen; erst Hersteller-/Lizenz- und Android-Nachweis** |
| Threema Gateway | HTTPS-Senden lokal möglich; Empfang per Callback | Separate Gateway-ID, kein persönliches Postfach | SDK-Lizenz und Vertrag separat | Anderes Identitäts-/Betriebsmodell | **Für dieses Ziel verwerfen** |

**Empfohlene Reihenfolge:** Telegram → begrenzte lokale Ergänzungen → Signal-Companion-Prototyp → optionaler WhatsApp-Prototyp → Threema-Multi-Device neu bewerten. Die Prototypen sind spätere, gesonderte Arbeitspakete.

## Feature-Matrix

**Z** = zuverlässig realisierbar: dokumentierte, geeignete Schnittstelle; noch nicht in Orbiliq getestet. **E** = realisierbar mit Einschränkungen. **X** = experimentell: Portierung, inoffizieller Zugang oder ungeprüfte Voraussetzungen. **N** = für die gewählte Strategie nicht sinnvoll realisierbar. **—** = kein entsprechendes Dienstkonzept belegt; nicht als „fehlgeschlagene Integration“ interpretieren.

Die Matrix bewertet Telegram/TDLib, den **potenziellen** Signal-Companion, den **experimentellen** WhatsApp/whatsmeow-Companion und die **empfohlene Threema-Benachrichtigungsintegration**. Signal- und WhatsApp-Zeilen bewerten bei Basisfunktionen das dokumentierte Backend-Potenzial nach erfolgreichem Prototyp; das Gesamtprodukt bleibt dort X bis zu dessen Freigabe. Kein Z/E ist ein Testnachweis.

| Feature | Telegram | Signal | WhatsApp | Threema, lokale Ergänzung |
|---|---|---|---|---|
| Persönliches Konto authentifizieren | E | X | X | E: nur Zugriffserlaubnis, Konto bleibt Original-App |
| Neue Texte lesen/empfangen/senden | Z | E | X | E: Vorschau und vorhandene Antwortaktion |
| Vollständige bestehende Historie | E: Cloud ja, fremde Secret Chats nein | X: Backend-abhängiger Geräte-Transfer | E/X: gelieferte History-Sync-Blöcke | N |
| Medien: Bild, Video, Datei | Z | E | X | E: Vorschau/Teilen; vollständige bidirektionale Medienintegration N |
| Sprachnachrichten | Z | E | X | N innerhalb Orbiliq; Übergabe möglich |
| Gruppen lesen/schreiben | Z | E | X | E: nur benachrichtigte Ausschnitte |
| Gruppen verwalten | E: Rechte | E | X | N |
| Reaktionen senden/empfangen | E: Chat-/Accountregeln | E | X | N als zugesicherte Fähigkeit |
| Antworten mit Nachrichtenbezug | Z | E | X | N: Schnellantwort ist kein zitierendes Reply |
| Bearbeiten | E: Fristen/Rechte | E | X | N |
| Lokal / für alle löschen | E / E | E / E | E / X | E: nur eigenen Vorschau-Cache / N |
| Kontakte/Identitäten | E: Upload optional | E: ACI/PNI, Telefonnummer optional sichtbar | X: JID/LID-Abgleich | E: unvollständige Anzeigeinformationen |
| Typing / Lesebestätigungen / Presence | E | E: keine allgemeine Online-Presence | X | N als konsistente Datenquelle |
| Offline-Ansicht / Ausgangswarteschlange | Z / E | E / E | E / X | E: eigener Vorschau-Cache / N für zuverlässigen Versand |
| Status/Stories lesen und senden | E | X | X | — |
| Channels | E: inkl. API-Pflichten | — | X: Newsletter-API | — |
| Audio-/Video-Calls direkt in Orbiliq | X: weiterer Medienstack | N im empfohlenen ersten Companion-Umfang | N mit whatsmeow | N |
| Multi-Device | Z: Cloud; Secret Chats gerätegebunden | E: verknüpftes Gerät, Limits | X: Companion, Limits | N als Orbiliq-Feature |
| Ohne verpflichtende Play Services | E | E | E im Protokoll; Original-App-Onboarding separat | E: Threema Libre/Push |
| Zeitnaher Hintergrundempfang unter allen Android-Zuständen | E | X | X | E: hängt an Original-App und OS |

Einzelheiten: [Signal](01-signal.md), [Telegram](02-telegram.md), [WhatsApp](03-whatsapp.md), [Threema](04-threema.md), [gemeinsame Architektur und Hintergrundbetrieb](05-architecture.md).

## Untersuchungsmethode und Grenzen

- Ausgangsrepository war leer: keine App, Tests, Builddateien, Lizenzdatei oder bestehende Architektur. Der eingefügte Projekttext ist die Anforderungsgrundlage.
- Herstellerdokumentation, öffentliche Client-Repositories, tatsächliche LICENSE-Dateien, Dependency-Manifeste und ausgewählte API-/Protokolldateien wurden gelesen. Nicht nur Projektbeschreibungen oder Sternzahlen.
- GitHub-Revisionen wurden über die öffentliche API ermittelt. [Repository-Snapshot](repository-snapshot.json) und [Datei-Nachweise](source-artifacts.json) machen untersuchte Stände nachvollziehbar. Ein Head-SHA ist eine Beobachtung, keine Empfehlung, diesen Entwicklungsstand ungeprüft auszuliefern.
- Feature-Behauptungen unterscheiden Herstellerfunktion, Library-Funktion und Android-Eignung. Fehlende öffentliche Freigaben werden als offen markiert; aus nicht gefundenen APIs folgt kein mathematischer Nichtexistenzbeweis.
- Kein Account wurde angemeldet oder verknüpft, keine Nachricht versandt, keine App gebaut. Android-/Doze-/Medien-/Multi-Device-Tests sowie eine vollständige transitive Lizenz- und Sicherheitsprüfung sind **nicht ausgeführt**.
- Preise, Versionsstände, Nutzungsbedingungen, Geräte-Limits und Protokollkompatibilität können sich ändern. Vor einem späteren Prototyp und vor Veröffentlichung erneut prüfen. Fehlgeschlagene Quellenzugriffe sind im Quellenregister benannt.

## Definition of Done dieses Arbeitspakets

| Kriterium | Ergebnis |
|---|---|
| Technische Entscheidung für alle vier Messenger | Einzelanalysen mit primären und alternativen Strategien vorhanden |
| APIs, Projekte, Auth, Sessions, Kryptografie, Sync, Medien und Sonderfunktionen | In Einzelanalysen und Feature-Matrix abgedeckt |
| MIT, transitive Lizenzen, Ban-/RE-/Wartungsrisiken | Gesondert bewertet, einschließlich konkreter GPL-Abhängigkeiten |
| Empfohlene Architektur | Provider-, Companion-, Capability- und Hintergrundkonzept beschrieben |
| Quellen und nachvollziehbare Revisionsstände | Register, Permalinks, JSON-Snapshots vorhanden |
| Produktiver Messenger-Code | Keiner erstellt |
| Kontinuität | Projektkontext und ADR ergänzt; keine Implementierung als fertig markiert |
