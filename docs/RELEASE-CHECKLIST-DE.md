# Veröffentlichungsprüfung: 0.6.0-rc2

Status: signierter Release-Kandidat zur Geräteabnahme.

| Bereich | Bestätigt | Noch am Gerät zu prüfen |
|---|---|---|
| Funktion | 167 aktuelle Tests, keine Fehler oder übersprungenen Tests; keine automatischen APK-Downloads, Play-Set-Prüfung, installierter Versionsabgleich, Cache und Diagnose | Live-Play-Zustellung, echte Installation und Abbruch, OnePlus-ROM |
| Oberfläche | 22 Oberflächentests; Glastransparenz und alle konkreten Animationsmodi im Hoch-/Querformat; kompakte Navigation | Tatsächliche Flüssigkeit, OEM-Installationslogo, große Schrift-/Displaymatrix |
| Paket | Vier optimierte Release-APKs, identische bisherige Signatur, Version 1007, min/target API 23/37, nicht debuggbar | Aktualisierung auf Cems OnePlus 15 |
| Ausrichtung | ZIP und sämtliche nativen ELF-LOAD-Segmente mindestens 16 KiB | Ausführung auf verschiedenen echten Geräten |
| Diagnose | Begrenzte Aufbewahrung, Redaktion, kurze/vollständige Exporte, überlaufsicherer Flush getestet | Export über den echten Android-Dateiauswahldialog |
| Quellen | Fehlerdiagnose und Browserfallback erhalten | Zugangsbeschränkte APKMirror-API, vollständige Live-Matrix |

Lint: 0 Fehler, 313 offen ausgewiesene Warnungen. Vollständige Berichte und Prüfsummen liegen im Lieferpaket. Keine Lint-Baseline blendet Fehler aus. Die detaillierte Anforderungsliste steht in RELEASE-RC2.md; die Prüfumfänge und Grenzen in TEST-RESULTS.md.
