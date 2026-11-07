# Anforderungsabgleich: 0.6.0-rc2

Die Übergabe `UEBERGABE.md` und `STARTPROMPT.txt` wurde vollständig gelesen und mit dem lokalen Stand verglichen. Das lokale 0.1-Gerüst ist älter als RC1/RC2. Die gesicherten Fassungen wurden in einem isolierten Quellbaum zusammengeführt. Die aktuellen Änderungen an GlassSurface, Appearance, AppearanceScreen, GlassChoiceSheet und GlassTransparencyUiTest bleiben erhalten. Der doppelt übernommene Transparenzregler wurde entfernt.

| Nr. | Anforderung | Ergebnis und Nachweis |
|---:|---|---|
| 1 | Vier signierte Release-APKs | ARM64, ARMv7, x86_64 und Universal, Version 1007 / 0.6.0-rc2; Einzelprüfungen unter reports/release-verification.json im Lieferpaket. |
| 2 | Einheitliches neues Logo | Manifest, Aktivität, Legacy-/Rund-/adaptive Icons vereinheitlicht; monochrome Android-13-Ressource ergänzt. Tatsächlicher OEM-Dialog bleibt Geräteprüfung. |
| 3 | Entwicklerdiagnose | Standard/Ausführlich, kurze/vollständige ZIP-Exporte, begrenzter lokaler Speicher und Hintergrund-/Fehlerereignisse; Rotation, Redaktion, Retention und volle Warteschlange getestet. |
| 4 | PayPal-Verschleierung | Ziel unverändert, per Digest getestet; vollständiger Rohlink wird in jeder Release-APK gesucht. Bei Nutzung bleibt das Ziel rekonstruierbar. |
| 5 | Simuliertes Testgerät | Auswahl für OnePlus, OPPO, Google und Samsung plus eigene Modellangabe. Nur nächste Play-Anmeldung; keine IMEI/Seriennummer und keine simulierte Installationsfreigabe. |
| 6 | Verlauf direkt löschen | In den Einstellungen und im Verlauf, jeweils mit Bestätigung. Installierte Apps und Dateien bleiben erhalten. |
| 7 | Ausgeblendete Versionen erklären | Quellenübergreifende Versionsausblendung und Wiederherstellung erläutert; kein Downgrade und keine Deinstallation. |
| 8 | Einstellungen ordnen | Darstellung/Sprache, Updateprüfung, Gerät/ROM, Quellen, Installation, Verlauf/Speicher, Diagnose und Projekt. |
| 9 | Sprachauswahl | Gläsernes Auswahlmenü für Deutsch/Englisch/System; native App-Sprache wird berücksichtigt. |
| 10 | Glas in Menüs/Dialogen | Gemeinsame transparente Hosts, koordinatengerechte Hintergrundabtastung, erreichbare Schaltflächen bei begrenzter Fensterhöhe. Transparenz auch bei deaktivierten Effekten getestet. |
| 11 | DPI und Schrift | Konfigurierte dp-Fenstergröße, echte Android-Dichte und Schriftgröße; kompakte und querformatige Hostansichten geprüft. |
| 12 | Sicheres Cacheleeren | Aktive Vorgänge gesperrt; nur eigene temporäre Dateien und Bild-/HTTP-Caches entfernt. Einstellungen, Diagnose und ausstehende Installer-Übergaben bleiben erhalten. |
| 13 | Region live erklären | Erkannte ROM, gewählte Vergleichsregion und wirksame Prüfregion separat. Manuelle Auswahl übergeht das tatsächliche Gerät nicht. |
| 14 | Echter Live-Fortschritt | Quellen-/Prüfschritte in Übersicht und Updates; keine erfundenen Prozente. Serialisierung, Abbruch, Fehlerzähler und veraltete Rückmeldungen getestet. |
| 15 | Technische Risiken | Konkrete Evidenz für Konflikte/offene Nachweise; eindeutig getrennte Beschriftungen für Risiken und Prüfverfahren. |
| 16 | Play wirklich prüfbar | Auswahl aktiviert; BASE/SPLIT-Gesamtpfad mit echter Set-Prüfung, Größen-/Prüfsummenprüfung und gemeinsamem PackageInstaller. Live-Play-Zustellung bleibt Geräteabnahme. |
| 17 | Erscheinungsbild/Animation | Lazy-Liste, gecachte Effekte, reduzierte Einstellungsdarstellung, Vorschau ohne laufende Speicherschreibvorgänge und Vollflächenhintergrund. Alle konkreten Modi im Hoch-/Querformat getestet; reale FPS offen. |
| 18 | Installierte Updates entfernen | Tatsächlicher Paketstand, alle Quellenvarianten und verspätete Ergebnisse abgeglichen. Abbruch/Fehler bleibt ohne Erfolgseintrag; Selbstupdate wird nach Neustart bestätigt. |
| 19 | Keine automatischen APK-Downloads | Suche, Worker und Berichte bleiben Metadatenpfade; alte Einstellung wird deaktiviert. Downloads nur über ausdrücklich benannte Aktionen, kein stilles Nachladen fehlender Dateien. |
| 20 | Frühere Aufträge erhalten | Favoriten, Filter, Installerwahl, Downloadordner, öffentliche Kopien, Glas-/Farbsteuerung, Android-17-Ziel und Herkunftsprüfungen bleiben enthalten. |
| 21 | Eine verantwortliche Umsetzung | Änderungen ausschließlich in diesem isolierten zusammengeführten Quellstand; keine erneute Bearbeitung fremder Chat-Arbeitsverzeichnisse. |

Die Implementierungsarbeiten sind zusammengeführt. Unabhängige Live-Dienste und Cems physisches Gerät werden nicht durch Hosttests ersetzt. Die konkrete verbleibende Geräteabnahme ist in TEST-RESULTS.md festgehalten.

Herstellerreferenzen für die Modellvorlagen, geprüft am 07.11.2026: [OnePlus 16](https://www.oneplus.com/cn/16/specs), [OPPO Find X10 Pro Max](https://www.oppo.com/de/smartphones/series-find-x/find-x10-pro-max/), [Google Pixel 11](https://blog.google/intl/de-de/produkte/hardware/google-pixel-11-pro-xl/), [Samsung S26 Ultra](https://www.samsung.com/de/smartphones/galaxy-s26-ultra/). Produktnamen sind Testzeichenfolgen; regionale Modellkennungen sind gesondert zu prüfen.
