# Beitragen

Vor Änderungen `docs/PROJECT_CONTEXT.md` und die betreffenden ADRs lesen.
Kleine, überprüfbare Änderungen mit Problem, Verhalten und Testnachweis beschreiben.
Kotlin-Stil: vier Leerzeichen, sprechende Namen, keine Secrets oder Nachrichteninhalte in Logs.
Neue Module nur mit konkreter Verantwortung; Features kennen keine Data-Implementierungen.
Cancellation weiterreichen, keine GlobalScope-Jobs. Unfertige Providerfähigkeiten nicht freigeben.

Vor einem Pull Request die Build-/Testbefehle aus README ausführen. UI-Änderungen auf
Gerät/Emulator prüfen; nicht ausgeführte Prüfungen ausdrücklich nennen.
Architekturänderungen als neues ADR dokumentieren, Projektkontext aktualisieren.

## Abhängigkeiten

Nur feste stabile Versionen im Version Catalog (dokumentierte Ausnahme: Material 3 Expressive beta); Google Maven und Maven Central sind die
zulässigen Bibliotheksquellen. Keine lokalen JARs oder dynamischen Versionen.
Bei Updates Upstream, Kompatibilität, Lizenz und transitive Änderungen prüfen, dann:

```sh
./gradlew :app:assembleDebug test :app:lintDebug :app:assembleDebugAndroidTest --write-locks --write-verification-metadata sha256
```

Geänderte Lockfiles und `gradle/verification-metadata.xml` gemeinsam prüfen und einchecken.
Neue Prüfsummen sind eine Trust-on-first-use-Aufnahme, kein unabhängiges Herkunftsgutachten.
Keine unkontrollierte Regenerierung zur Umgehung einer Verifikationsabweichung.
Release-Signierung und echte Providerkonten gehören nicht in die CI-Grundlage.
Beiträge werden unter der MIT-Lizenz des Projekts eingereicht; Fremdcode separat kennzeichnen.
