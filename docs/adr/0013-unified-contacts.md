# ADR 0013: Konservative und korrigierbare Kontaktzuordnungen

Status: angenommen, 27.10.2026.

Personen bleiben lokale Aggregate stabiler Messengeridentitäten. Automatische Gleichheit
beruht ausschließlich auf explizit bestätigter aktueller Nummerninhaberschaft und konservativ
normalisierten internationalen Nummern. Adapter tragen die Verantwortung für die Aussagekraft
des Belegs. Namen und Remote-IDs sind keine dienstübergreifenden Matchingmerkmale.

Mehrdeutigkeit, eigene Identitäten, Providerduplikate und widersprüchliche manuelle Aggregate
sperren automatische Zuordnung. Bestehende manuelle Personen werden nie automatisch
verschmolzen. Automatische Links werden bei neuen Providerinformationen neu bewertet.
Manuelle Operationen setzen persistente Ausschlüsse; Split schützt beide Teilmengen.
Korrekturen können die erwartete aktuelle Mitgliedschaft atomar prüfen.

Evidenz und Ausschlüsse liegen im vorhandenen verschlüsselten Snapshot (Codec v5), ohne
neuen Dienst, Netzwerkzugriff oder zusätzliche Abhängigkeit. Nummerngleichheit ist kein
absoluter Beweis realer Personengleichheit (Recycling/geteilte Nummern); manuelle Korrekturen
bleiben daher zwingend möglich. Produktive Provider müssen geeignete Belege erst liefern.
Eine Kontaktverwaltungs-UI ist ein folgendes Arbeitspaket.
