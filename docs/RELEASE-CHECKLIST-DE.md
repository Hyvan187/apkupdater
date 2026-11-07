# Veröffentlichungsprüfung: 0.6.0-rc3

Status: signierter Release-Kandidat zur Geräteabnahme, Version 1008. Die ursprüngliche Übergabe und die RC2-Anforderungen bleiben erhalten; RC3 behebt die bei der erneuten Prüfung gefundenen Lücken.

| Bereich | Bestätigt | Noch am Gerät zu prüfen |
|---|---|---|
| Funktion | 183 aktuelle Tests ohne Fehler oder übersprungene Tests; keine automatischen APK-Downloads, expliziter Downloadzweck, Play-Set-Prüfung, Versionsabgleich, Cache und Diagnose | Live-Play-Zustellung, echte Installation und Abbruch, OnePlus-ROM |
| Oberfläche | Transparenz ausschließlich im Erscheinungsbild; Prüfung aller konkreten Animationsmodi im Hoch-/Querformat; kompakte Navigation; einheitlicher App-Name | Gemessene Flüssigkeit, OEM-Installationslogo, große Schrift-/Displaymatrix |
| Paket | Vier optimierte Release-APKs, bisherige Signatur, min/target API 23/37, nicht debuggbar; spezialisierte ABIs und Universal direkt im APK-Inhalt nachgewiesen | Aktualisierung auf Cems OnePlus 15 |
| Ausrichtung | ZIP und sämtliche nativen ELF-LOAD-Segmente mindestens 16 KiB | Ausführung auf weiteren echten Geräten |
| Diagnose | Voll-/Kurzexport, Typen und Ursachen, Ablauf-/Anfragekennungen, UTF-8-Grenzen, Redaktion, Retention, Warteschlangenüberlauf und kontrollierter Dokumentanbieter getestet | Neuer Export und reale Installationsabläufe über den OnePlus-Dateiauswahldialog |
| Quellen | Abbruchzuordnung, veraltete Rückmeldungen, Play-Netzdiagnose, Authentifizierungssperrfrist und bewusste Wiederholung getestet | APKMirror benötigt gültige Zugangsdaten; vollständige Live-Matrix |

Release-Lint: 0 Fehler, 292 offen ausgewiesene Warnungen. Häufigste Kategorien: 166 Untranslatable, 22 UnusedTranslation, je 20 MissingTranslation und UnusedQuantity. Vollständige Berichte liegen im Lieferpaket. Keine neue Lint-Baseline oder pauschale Unterdrückung blendet diese Warnungen aus. Eine vollständige Übersetzung aller historischen Sprachressourcen wird nicht behauptet.

Der konkrete GitHub-Lauf und sein tatsächliches Ergebnis stehen in TEST-RESULTS.md und den mitgelieferten CI-Belegen. Die Anforderungslisten stehen in RELEASE-RC2.md (historisch) und RELEASE-RC3.md (aktuell). Externe Zugänge und reale Gerätemessungen sind keine durch Quelltext allein lösbaren Restarbeiten.
