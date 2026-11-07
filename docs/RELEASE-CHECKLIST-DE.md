# Veröffentlichungsprüfung: 0.6.0-rc1

**Status: Release-Kandidat, keine vollständige Produktionsfreigabe.**

| Bereich | Nachweis | Noch offen |
|---|---|---|
| Funktionen | 124 automatisierte Tests ohne Fehler; Paket-, Versions-, Signatur-, Split- und Filterprüfungen | Vollständige Live-Prüfung aller Quellen und Installationswege; APKMirror API antwortet ohne gültige Zugangsdaten mit 401 |
| Oberfläche | Hoch-/Querformat, Navigation, gespeicherte Einstellungen und selektive runde Info-Symbole im API-37-Host geprüft; Ansichten visuell kontrolliert | Vollständige Geräte-/Schriftgrößen-/Barrierefreiheitsmatrix |
| Stabilität und Leistung | Release kompiliert, Code und Ressourcen optimiert | OnePlus-Dauertest, reale Flüssigglas-FPS, GPU-/Akku- und Speicher-Messungen |
| Sicherheit und Datenschutz | APK-Signaturen vor Installation, geprüfte private Dateien, Keystore-Migration mit Entfernung alter Klartextdaten implementiert; alte gemeinsame API-Zugangsdaten entfernt; jsoup aktualisiert | Vollständige Abhängigkeits-/Datenschutzprüfung und Geräteabnahme der Sicherheitsfunktionen |
| Kompatibilität | Ein APK für ARMv7, ARM64, x86 und x86_64; min API 23, target API 37; 16-KiB-ZIP-/ELF-Ausrichtung geprüft | Persönliches OnePlus 15 mit IN-ROM, verifizierte APK-ROM-Herkunft und alle echten Android-Versionen |
| Sprache | Deutsch/Englisch/Systemsprache auswählbar | Vollständige Übersetzung sämtlicher Texte und weitere Sprachen |
| Paket | Nicht debuggbarer, signierter und optimierter Release-Build; bisherige Signatur für Aktualisierung beibehalten | Endgültige Freigabe nach Schließung der offenen Punkte |

283 Lint-Warnungen bleiben im vollständigen Bericht sichtbar. Überwiegend betreffen sie alte Übersetzungsressourcen, Sprachkonfigurationen und Empfehlungen. Es wurden keine Lint-Fehler durch eine Baseline ausgeblendet.

Eindeutige APK-Sets werden gemeinsam geprüft. Variantenarchive, unklare erforderliche Splits, OBB-Dateien und die Übergabe von Archiven an externe Installer bleiben gesperrt. Eine manuell eingestellte Region ersetzt keinen Herkunftsnachweis.

Zusatzlauf: Ein Softwareemulator mit Android 10/API 29 startete. Die Installation überschritt jedoch das Zeitlimit. Dieser Versuch zählt nicht als bestandener Start-, Installations- oder Keystore-Gerätetest.
