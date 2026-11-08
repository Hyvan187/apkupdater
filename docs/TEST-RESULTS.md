# UniAPK 0.7.0-rc5: abschließender Prüfstand

App-Fingerabdruck: `b432c0e0422a5bf3bcb08aa73df3457428909d9048e732e32b5d716c17ebc45d`. App-Implementierung: `bd0a88e1f0f5455b46411428477f6a002a32bd45`, Branch `glass/uniapk-0.7.0-rc5`. Dokumentation und Änderungen der Aufnahmehilfe für Tests verändern diesen Fingerabdruck nicht. Quellbaum, abschließender Commit und Dateiprüfsummen stehen im Release unter reports/SOURCE-MANIFEST.json.

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| Vollständige lokale Regression | 349 Tests, 0 fehlgeschlagen, 0 Fehler, 0 übersprungen |
| Ergänzung gegenüber RC4 | 36 zusätzliche Prüfungen für Konfiguration, Diagnose, Verschlüsselung, Validierung, Rücknahme und Backup-Oberfläche |
| Release-Lint | 0 Fehler / Fatals, 292 Warnungen; vorhandene technische Hinweise bleiben im vollständigen Bericht sichtbar |
| Release-Bau | Vier optimierte signierte APKs, 0.7.0-rc5 / 1013, nicht debuggable |
| Signaturkontinuität | Gleiches Zertifikat wie RC1 bis RC4; v1/v2/v3 überprüft |
| Tatsächliche Architekturen | ARM64, ARM32 und x86_64 separat; Universal zusätzlich mit x86 |
| Ausrichtung | ZIP-Ausrichtung und native ELF-LOAD-Segmente jeweils auf 16-KiB-Tauglichkeit geprüft |
| Einstellungen | Alle nicht statischen Preference-Felder sind als Einstellung oder interner Zustand klassifiziert; 87 gesicherte Preference-Felder plus drei Diagnoseoptionen |
| Diagnose | Aktueller Zustand auch bei deaktivierter Aufzeichnung; Änderungsbeobachter, Zeitpunkte, persönliche Listen und deren Begrenzung, sichere Standard-/Detailausgabe, strukturierte Datentypen und Geheimnisbereinigung geprüft |
| Fehlerdaten | Softwareversionen bleiben lesbar; tatsächliche Adressen bleiben maskiert. Backup-Fehler behalten Aufrufstellen ohne ungeprüfte Fehlermeldungsinhalte. |
| Verschlüsselung | Passwortfehler, manipulierte Daten/Header, Größen- und Kostenbegrenzung, frische Zufallswerte, veröffentlichter PBKDF2-SHA256-Testvektor, Unicode-Gleichheit der Implementierungen und tatsächlicher API-23-Fallback geprüft |
| Wiederherstellung | Vollständige Einstellungen einschließlich Zugängen und Listen; Vorschau; ausdrückliche Zustimmung für erweiterte Diagnose; Werte-/Strukturprüfung vor Schreibvorgängen; Rücknahme nach injiziertem Schreibfehler und Wiederanlauf mit Journal geprüft |
| Oberfläche | Backup unter Erweitert / System auf der zweiten Ebene erreichbar; Passwortabgleich, verschlüsselte Dateiausgabe, Vorschau und tatsächliche Übernahme in Preferences und laufende Darstellung geprüft |
| Bestandsschutz | Bestehende Versions-, Quellen-, Download-, Installer-, Cache-, Menü-, Kontrast-, Icon- und Bewegungsprüfungen weiterhin Bestandteil der vollständigen Regression |
| Quellstand | Unabhängiger Byte-Abgleich des App-Fingerabdrucks mit allen vier DEX-Paketen und Git-Blob-Prüfsummen sämtlicher ausgelieferter Quelldateien |

Der abschließende vollständige Gradle-Lauf endete erfolgreich nach 6 Minuten 51 Sekunden. validation-final-2.log und validation-completion.log enthalten die tatsächliche Abschlussausgabe. JUnit-XML, Lint-Ergebnisse, Zuordnung der optimierten Klassen und unabhängige APK-Verifikation liegen vollständig im Release.

Die fünf Backup-Oberflächentests wurden für die abschließenden Aufnahmen zusätzlich separat ausgeführt. Die Aufnahmehilfe erfasst den sichtbaren Compose-Bereich, bei geöffnetem Dialog dessen eigene Oberfläche; dies ändert keinen App-Code. Der zusätzliche Lauf und seine Ergebnisse liegen unter reports/validation-backup-ui.log und reports/backup-ui-junit. Insgesamt enthält reports/screenshots-host 41 native Hostansichten einschließlich der neuen Backup-Oberfläche und Dialoge.

Die zusätzliche GitHub-Prüfung für den App-Implementierungs-Commit wurde gestartet (Run 42501539835). Der bei der Paketierung tatsächlich beobachtete Status steht in reports/ci-summary.json. Ein laufender Job gilt nicht als bestanden. Die oben genannten lokalen Prüfungen sind davon unabhängig abgeschlossen.

## Verbleibende Abnahme und Grenzen

Keine physische Geräteabnahme auf OnePlus 15 oder Samsung S23 durchgeführt. Passwortableitungsdauer auf Mittelklassegeräten, reale Dokumentanbieter, Ordnerfreigaben nach Gerätewechsel, Launcher- und Sprachwechsel sowie erneute Hintergrundplanung sind auf einem Telefon gemäß RELEASE-CHECKLIST-DE.md zu prüfen. Native Hostansichten sind keine Messung eines physischen Telefons und keine vollständig gestartete Android-OS-Emulation.

Das Backup umfasst Einstellungen und selbst gepflegte Listen, keine APK-Dateien, Diagnoseereignisse, Installationsverläufe oder kurzlebigen Anmeldesitzungen. Android-Berechtigungen können nicht übertragen werden. Ohne Passwort ist keine Wiederherstellung möglich. Eine unabhängige kryptografische Sicherheitszertifizierung wird nicht behauptet. Diagnoseberichte sind app-eigen und können fremdes System- oder App-Verhalten nur insoweit erklären, wie es der App zugänglich ist.

Bisherige Grenzen bleiben bestehen: Unbekannte Build-Nummern werden nicht erfunden; Launcher können Transparenz begrenzen; fremde Play-Lizenzprüfungen lassen sich nicht allgemein abschalten; das CPU-Budget ist keine harte Kernbindung aller Android-Threads.

Der neue Umfang steht in RC5-CHANGES-DE.md. Der vorherige Prüfbericht bleibt unter TEST-RESULTS-RC4.md erhalten.
