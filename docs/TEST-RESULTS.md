# UniAPK 0.7.0-rc2: abschließender Prüfstand

App-Fingerabdruck: `b0cba08402d67a9e83404fef8b06dd7ac36914b6be3698d9187efcb3cb2b78be`. Der gleiche Wert ist in jeder der vier ausgelieferten APKs enthalten und wurde unabhängig aus den App-Quelldateien neu berechnet. Der vollständige Quellbaum mit Dateimodi und SHA-256-Werten steht in reports/SOURCE-MANIFEST.json. Abschließende Dokumentationsänderungen verändern den App-Fingerabdruck nicht.

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| Vollständige lokale Regression | 250 Tests, 0 Fehler, 0 fehlgeschlagen, 0 übersprungen |
| Neue Prüfungen gegenüber RC1 | 33 zusätzliche Tests; bisherige Schutztests erhalten |
| Release-Lint | 0 Fehler / Fatals, 282 Warnungen; vollständige Berichte beigefügt |
| Release-Bau | Alle vier optimierten APKs erfolgreich, Version 0.7.0-rc2 / 1010 |
| Signaturen | Erwartetes beibehaltenes Zertifikat, v1/v2/v3 gültig; nicht debuggable |
| Tatsächliche Architekturen | ARM64, ARM32, x86_64 separat; Universal zusätzlich mit x86 |
| Speicher-Seitengröße | ZIP- und native ELF-LOAD-Ausrichtung mit 16-KiB-Prüfung bestanden |
| Native Grafikprüfungen | Android-17-Hostgrafik, alle Hintergrundmodi in Hoch-/Querformat, neue Dialoge und Fortschrittsstile |
| Launcher | Transparenter freistehender Vordergrund; native Vorschau mit runder, abgerundeter und eckiger Maske |
| Unabhängige CI | Externe Prüfung durch Infrastrukturfehler begrenzt; Details in reports/ci-summary.json |
| Lokaler Android-17-Release-Test | Kein zusätzlicher bestandener Android-Systemstart behauptet |

## Geprüfte neue Abläufe

Die Tests prüfen die erste Einrichtung ohne erzwungene Freigaben, erneutes Öffnen der Berechtigungsseite, beschreibbare und schreibgeschützte Ordner, angezeigten Unterordnerpfad und entzogene Berechtigung. Sie unterscheiden tatsächliche Dateilöschung von fehlendem Zugriff. Die Löschfunktion entfernt mehrere vorbereitete Quellenvarianten, setzt sie zurück zu Funde und verhindert einen unbemerkten erneuten automatischen Download. Bewusst aufbewahrte öffentliche Kopien bleiben bei privater Cachealterung erhalten.

Weitere Prüfungen decken die Einstufung allgemeiner, Google-System- und Herstellerkomponenten, kritische Bestätigung vor Installer-/Netzwerkaufrufen, unverändert wirksame technische Sperren, lokale APK-Berichte, unveränderte gespeicherte Modusindizes, Matrix, Dichte aller Effekte, gespeicherte Animationsfarben sowie tatsächliche und unbestimmte Fortschrittswerte ab.

Die APK-Vorbereitung verwendet weiterhin reale signierte Test-APKs und Paketsets. Der im Host nicht verfügbare Android-Paketmetadatenleser wird in diesen Fällen ersetzt; Signatur-, Manifest-, Datei- und Setdaten bleiben tatsächlich geprüft. Einige Koordinatortests setzen synthetischen vorbereiteten Zustand ein, um die Lösch-/Abgleichlogik isoliert ohne echten Download zu prüfen. Die Screenshotdaten stammen aus künstlichen Test-/Emulatorumgebungen.

## Einschränkungen

Lint ist nicht warnungsfrei. Die verbleibenden Hinweise betreffen überwiegend Übersetzungen/Ressourcen, Stil, Bibliotheksstände und bestehende Plattformhinweise. Es wurden keine pauschalen neuen Fehlerunterdrückungen oder Baselines eingeführt. Die lokal gebauten APKs sind signierte Release-Builds; eine etwaige unabhängige CI installiert eine Debug-APK desselben App-Quellstands, keine zusätzliche Produktvariante.

Die externe CI wurde vor den App-Tests durch HTTP-504-Antworten der Downloadserver blockiert. Der zusätzliche lokale Android-17-Softwareemulator erreichte ohne verfügbare Hardwarebeschleunigung innerhalb von acht Minuten keinen abgeschlossenen Systemstart. Die APK wurde dort daher nicht gestartet; dieser Zusatztest gilt ausdrücklich nicht als bestanden.

Die alten OnePlus-Installerablehnungen bleiben rückwirkend ohne die damals fehlende Android-Statusmeldung nicht eindeutig erklärbar. Das neu bereitgestellte RC1-Protokoll enthält einen erfolgreichen Suchlauf, aber keine Downloads oder Installationsversuche. Daraus wird kein Nachweis für unaufgezeichnete Vorgänge abgeleitet.

Herstellerspezifische Dialoge, reale Store-/Kontoländerfreigaben, Funktionsfähigkeit kritischer Systemkomponenten, Launcher-Eigenheiten und gemessene Akku-/Leistungsauswirkungen bleiben Geräteabnahme. Risikoangaben sind konservative Hinweise und keine Crash-Prozentwerte, Malwareprüfung, externe Zertifizierung oder vollständige Funktionsgarantie.
