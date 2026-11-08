# UniAPK 0.7.0-rc4: abschließender Prüfstand

App-Fingerabdruck: `d90cfbd391fc2c291cfb65d5ceb273071037557c10ea3bbb0a191b488b1019bf`. App-Implementierung: `bf013e9a13125478d427a4ea75e46a03c9deafd9`, Branch `glass/uniapk-0.7.0-rc4`. Dokumentationsänderungen verändern den App-Fingerabdruck nicht. Quellbaum, abschließender Dokumentations-Commit und sämtliche Dateiprüfsummen stehen im Release unter reports/SOURCE-MANIFEST.json.

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| Vollständige lokale Regression | 313 Tests, 0 fehlgeschlagen, 0 Fehler, 0 übersprungen |
| Ergänzung gegenüber RC3 | 25 zusätzliche Policy-/UI-Prüfungen; bestehende Funktions- und Schutzprüfungen erhalten |
| Release-Lint | 0 Fehler / Fatals, 288 Warnungen. Zwei zusätzliche Hinweise empfehlen lediglich die KTX-Schreibweise für URI-Konvertierungen. |
| Release-Bau | Vier optimierte signierte APKs, 0.7.0-rc4 / 1012, nicht debuggable |
| Signaturkontinuität | Gleiches Zertifikat wie RC1/RC2/RC3; v1/v2/v3 überprüft |
| Tatsächliche Architekturen | ARM64, ARM32, x86_64 separat; Universal mit diesen und x86 |
| Ausrichtung | ZIP-Ausrichtung und native ELF-LOAD-Segmente jeweils auf 16-KiB-Tauglichkeit geprüft |
| Launcher | Fünf kompilierte Aliasdefinitionen, Original allein standardmäßig aktiviert, passende Zielaktivität, fünf unterschiedliche Icon-Ressourcen und UniAPK-Labelressource geprüft |
| Icons / Masken | Fünf detaillierte Symbole mit rundem, abgerundetem und eckigem Ausschnitt nativ gerendert; Motiv und Größen visuell kontrolliert |
| Menü / Design | Vier Bereiche; Allgemein mit Startansicht/Schnellzugriff/Berechtigungen; Quellen und Installer bleiben erreichbar. Light, Dusk, Dark, OLED und Basisfarben mit nativen Hostansichten geprüft. |
| Versionslogik | Konkreter Code quellenübergreifend übersprungen; höherer Code mit identischem Versionsnamen erlaubt; unbekannte Codes, ältere Einträge, Wiederherstellung und dauerhafter App-Ausschluss geprüft |
| Farbkontraste | Alle vier Designs, schwarze/weiße/gesättigte/graue Basisfarben sowie Vorlagen; Text- und Aktionsrollen mindestens 4,5:1 geprüft |
| Bewegung / Fortschritt | Tatsächlich zeitlich veränderte Wellengeometrie, bekannte/unbestimmte Fortschrittssemantik, reduzierte Systemanimationen, abgestimmte Menü-/Dock-/Dialogbewegung; keine erfundenen Prozentwerte |
| Sichtprüfung | 37 PNGs von nativen Android-17-Hostansichten im Release, einschließlich Icons und aller vier Designs |
| Quellstand | Unabhängiger Byte-Abgleich des App-Fingerabdrucks mit allen vier DEX-Paketen und Git-Blob-Prüfsummen sämtlicher ausgelieferter Quelldateien |

Der abschließende Gradle-Lauf endete erfolgreich nach 6 Minuten 14 Sekunden. Der Client-Mitschnitt ist unvollständig; validation-completion.log enthält die tatsächliche Abschlussausgabe desselben Gradle-Daemons. JUnit-XML, Lint-Ergebnisse und die unabhängige APK-Verifikation sind vollständig vorhanden.

Die zusätzliche GitHub-Prüfung für denselben Implementierungs-Commit wurde gestartet (Run 42494227461). Ihr bei der Paketierung beobachteter Status steht in reports/ci-summary.json; ein noch laufender Job wird nicht als bestanden gewertet. Die oben genannten lokalen Prüfungen sind davon unabhängig abgeschlossen.

## Verbleibende Abnahme und Grenzen

Keine physische Geräteabnahme auf OnePlus 15 oder Samsung S23 durchgeführt. Reale OEM-Bildrate, Akkuwirkung, Launcher-Aktualisierung und Fremdinstaller sind gemäß RELEASE-CHECKLIST-DE.md zu prüfen. Native Hostansichten sind keine Messung eines physischen Telefons und keine vollständig gestartete Android-OS-Emulation.

Ohne Build-Code kann eine übersprungene Version nur anhand ihres Namens erkannt werden. Für alte Einträge wird keine unbekannte Build-Nummer erfunden. Transparenz kann vom Launcher begrenzt werden. Fremde Play-Lizenz- und Herkunftsprüfungen kann UniAPK nicht allgemein abschalten. Das CPU-Budget ist keine harte Kernbindung aller Android-Threads.

Der aktuelle Umfang und die Migration der fünf Phasen stehen in RC4-CHANGES-DE.md; der vorherige Prüfbericht bleibt unter TEST-RESULTS-RC3.md erhalten.
