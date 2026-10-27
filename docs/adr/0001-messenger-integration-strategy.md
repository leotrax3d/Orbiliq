# ADR 0001: Messengerabhängige Integrationswege statt einheitlichem Vollprovider-Versprechen

- Datum: 27.10.2026
- Status: Rechercheentscheidung dokumentiert; keine Implementierungs-/Distributionsfreigabe für experimentelle Provider
- Grundlage: [Machbarkeitsanalyse](../research/README.md)

## Kontext

Orbiliq soll lokal auf Android mit Kotlin/Compose arbeiten, ohne verpflichtende Play Services oder Orbiliq-Cloud. Eigener Code soll MIT bleiben. Die persönlichen Konten von Signal, Telegram, WhatsApp und gegebenenfalls Threema unterscheiden sich wesentlich hinsichtlich API-Zugang, Lizenz und Geräte-Synchronisation. Das Repository enthielt vor dieser Recherche keine Implementierung.

## Entscheidung

1. Telegram erhält als erster vollständiger Provider die offizielle TDLib mit Kotlin-Adapter über Java/JNI.
2. Signal wird als separater, optionaler Companion auf Grundlage aktuellen Android-Clientcodes untersucht. Verknüpfung bevorzugen; signal-cli ist Referenz, keine vorausgesetzte Android-Bibliothek. AGPL-/GPL-Kombinationen und Werkgrenze vor Distribution prüfen.
3. WhatsApp ist kein zuverlässig zugesicherter Vollprovider. Ein späterer Opt-in-Prototyp bevorzugt technisch whatsmeow vor Baileys; Android-Port, GPL-Abhängigkeit, Sperrrisiko und Sessionbetrieb sind offene Freigabebedingungen. Cloud API und DMA ersetzen keine private Accountintegration.
4. Threema bleibt zunächst bei einer klar gekennzeichneten lokalen Ergänzung mit Original-App. Desktop-Multi-Device-Code wird nicht voreilig in Android portiert. Gateway ist kein persönliches Postfach.
5. Der MIT-Kern hält generische Identitäten, Providerereignisse, Zustände und differenzierte Capabilities. Ein separater Prozess/Companion garantiert keine lizenzrechtliche Unabhängigkeit.
6. Keine eigene Kryptografie und kein neues Messenger-Netz. Keine verpflichtende externe Bridge. Keine Notification-Vorschauen als vollständige Historie oder erfolgreich ausgeführten Antwort-Intent als bestätigte Zustellung darstellen.

## Folgen

Telegram bestimmt das erste belastbare Integrationspaket. Andere Provider bleiben optional, gegebenenfalls dauerhaft eingeschränkt. Das Produkt darf nicht vier gleichwertige Vollintegrationen ankündigen. Calls sind eigene Forschungs-/Implementierungsprojekte. Android-Hintergrundzuverlässigkeit wird gemessen und mit Einschränkungen kommuniziert.

Eine rein permissive Gesamt-APK mit übernommenem Signal-/Threema-Clientcode oder den geprüften WhatsApp-Abhängigkeiten wird nicht als machbar vorausgesetzt. Eigene MIT-Dateien bleiben möglich, Pflichten der übrigen Kombination sind separat zu erfüllen.

## Wiederaufnahme / Revision

Neu bewerten bei offizieller persönlicher API, zusätzlicher Lizenzfreigabe, veränderten Dependencies, nachgewiesenem Android-Companion-Betrieb oder geänderten Produktanforderungen. Für jeden Vollprovider gelten die [Prüfkriterien](../research/05-architecture.md) beziehungsweise die entsprechenden Abschnitte der Einzelanalysen. Kein solcher Prototyp wurde in diesem Arbeitspaket erstellt.
