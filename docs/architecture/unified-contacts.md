# Unified Contacts

Personen sind lokale Aggregate; MessengerIdentity und ihre Account-/Remote-Referenz
bleiben beim Zusammenführen, Trennen und Korrigieren unverändert. Nachrichten und
Versandwege werden nicht umgeschrieben. Alle Änderungen committen atomar vor Flow-Publikation.

Provider können optional PhoneEvidence liefern. ownershipConfirmed darf ausschließlich
aktuelle autoritative Nummerninhaberschaft ausdrücken; Profiltext, importierte Adressbücher
und Benutzernamen reichen nicht. Ohne geeigneten Providerbeleg bleibt der Standard false.
Die Normalisierung akzeptiert nur internationale Nummern mit + oder 00 und einfache
Formatierungszeichen. Keine Länderannahmen, Unicodeziffern, Durchwahlen oder Suffixsuche.
Die Syntaxprüfung bestätigt weder Nummernvergabe noch Inhaberschaft.

IdentityChanged berechnet automatische Zuordnungen neu. Identische bestätigte Nummern
unterschiedlicher Provider können eine Person bilden. Fehlende Belege ergeben eigene
Personen. Automatische Links werden bei geänderter Evidenz neu bewertet. Mehrere manuelle
Zielpersonen, widersprüchliche Mitglieder, eigene Identitäten und mehrere Identitäten
desselben Providers blockieren Matching. observeContactConflicts liefert typisierte Konflikte
mit Identitäts-IDs, ohne Telefonnummern oder Namen in Fehlertexten.

link ist eine ausdrückliche manuelle Zuordnung; bestehende manuelle Links zu anderen
Personen erfordern reassign mit erwartetem bisherigen Ziel. split erzeugt atomar eine neue
Person und schützt beide bisherigen Teilmengen vor erneuter automatischer Zusammenführung.
unlink persistiert eine Ausschlussmarkierung, damit Replay die Entscheidung nicht rückgängig
macht. merge ist eine ausdrückliche manuelle Entscheidung auch bei widersprüchlicher Evidenz;
Identitäts-IDs bleiben für spätere individuelle Korrekturen verfügbar. reassign arbeitet als
Compare-and-set und wirft ContactAssignmentConflict bei veraltetem Zustand. Gleichzeitige
Aufrufe sind durch den Repository-Mutex serialisiert. Automatisches Matching verschmilzt
keine zwei manuell etablierten Personen. Manuelle Entscheidungen haben Vorrang.

Codec v5 speichert Evidenz und manuelle Ausschlüsse im vorhandenen verschlüsselten Snapshot;
v1–v4 bleiben lesbar, ältere Identitäten erhalten keine erfundene Telefonnummer.
Die Umsetzung ist ein Domain-/Persistenzbaustein. Eine Kontaktverwaltungsoberfläche,
produktive Provider mit autoritativen Nummernbelegen und Geräteprüfungen folgen separat.
Nummernrecycling und gemeinsam genutzte Telefonnummern sind durch Nummerngleichheit allein
nicht beweisbar auszuschließen; deshalb nur bestätigte aktuelle Providerbelege verwenden und
jede Zuordnung über die manuellen Operationen korrigierbar halten.
