# UniAPK 0.7.0-rc3: abschließender Prüfstand

App-Fingerabdruck: `cbb840f116e62c541a8e70b2799742788b458467b3dbebc81a553be5ed8604ea`. Die App-Quelldateien und die eingebetteten Werte aller vier APKs werden unabhängig abgeglichen. Dokumentations- und Prüferänderungen verändern diesen App-Fingerabdruck nicht.

| Prüfung | Ergebnis |
|---|---|
| Vollständige lokale Regression | 288 Tests, 0 fehlgeschlagen, 0 Fehler, 0 übersprungen |
| Ergänzung gegenüber RC2 | 38 zusätzliche Prüfungen, bestehende Schutztests erhalten |
| Release-Lint | 0 Fehler / Fatals, 286 Warnungen |
| Release-Bau | Vier optimierte signierte APKs, 0.7.0-rc3 / 1011, nicht debuggable |
| Signaturkontinuität | Gleiches Zertifikat wie RC1/RC2, v1/v2/v3 überprüft |
| Tatsächliche Architekturen | ARM64, ARM32, x86_64 separat; Universal zusätzlich x86 |
| Ausrichtung | ZIP und native ELF-LOAD-Segmente mit 16-KiB-Prüfung |
| Launcher | Kompilierte fünf Aliasdefinitionen, genau Original standardmäßig aktiviert, korrekte Zielaktivität und UniAPK-Labelressource geprüft; Formen und Vorschauen nativ gerendert |
| Sichtprüfung | Themenkarten, Designauswahl, Bereit-Zeilen, Favoriten, Dock, Polarlichter und Leuchtwürmchen auf künstlichen Android-17-Hostansichten |
| Unabhängige CI | Vorbereitung nach reparierter Variantenauflösung passiert; weitere App-Tests liefen beim Berichtstand noch. Aktueller Beobachtungsstand in reports/ci-summary.json |
| Physische Herstellergeräte | Kein zusätzlicher RC3-Gerätestart auf den privaten Telefonen behauptet |

## Neue Regressionen

Favoriten werden tatsächlich umgeschaltet, gespeichert und zugänglich als Schaltzustand angezeigt. Die App-Liste stellt die Favoritenkategorie ohne seitliches Wegscrollen bereit. Ignorierte Pakete verschwinden quellenübergreifend aus Updates, bleiben gespeichert und lassen sich wieder zulassen. Die Download- und Installationspfade prüfen den Ausschluss erneut. Automatische Downloads sind weiterhin standardmäßig aus.

Installer-Übergaben verwenden echte signierte APK-Testdateien: direkte APK, einzelne APK in einem Archiv und vollständiges APK-Set. Die äußere Set-Datei wird für geeignete Handler als APKS erzeugt, ohne die signierten inneren APKs zu verändern. URI und Lesefreigabe werden geprüft. Verschiedene Signierer bleiben gesperrt. Externes Öffnen ist kein bestätigter Installationserfolg. Der Host-Metadatenleser wird dort ersetzt, wo Androids nativer Paketmetadatenleser im Host nicht verfügbar ist; die Signatur- und Manifestbytes bleiben real.

Weitere Prüfungen decken die private Wiederherstellung vorbereiteter Dateien ohne Download, verworfene/gelöschte/abgelaufene Dateien, Speicherkorridore, automatische CPU-Budgets bei Energiesparen und thermischer Belastung, gespeicherte Design-/Dock-/Kompaktwerte, fünf Launcher-Zustände, vier Designmodi, alle Dockgrößen, wirksame Kartenverdichtung und mindestens 48 dp hohe Bedienziele ab. Der Zurück-Pfeil in den neuen Übersichten bleibt nach dem Scrollen erreichbar.

Die Testumgebung erhält pro Fall neue App-Verzeichnisse. Ihr statisch zwischengespeicherter AndroidX-FileProvider-Wurzelpfad wird zwischen den Übergabetests zurückgesetzt; Produktionsfreigaben werden dafür nicht erweitert. Die adaptive Symbolvorschau nutzt konfigurationsabhängige Android-Ressourcen. Es wurden keine neuen pauschalen Lint-Unterdrückungen oder Fehlerbaselines eingeführt.

## Grenzen

Lint ist nicht warnungsfrei; die vollständigen Berichte sind beigefügt. Es bleiben überwiegend Übersetzungs-, Ressourcen-, Stil- und Bibliothekshinweise. Ein alter manueller CI-Vorbereitungsschritt löste AGP-Varianten mehrdeutig auf; er wurde durch den regulären Kompilierungsschritt ersetzt. Ein noch laufender externer Auftrag wird nicht als bestandener Test oder Android-Systemstart ausgewiesen.

Die Hostgrafik ist ein Android-17-Grafiktest und keine Ausführung von OxygenOS oder One UI. Die tatsächlichen Dialoge installierter Drittanbieter-Installer, Launcher-Maskierung und Hintergrundtransparenz müssen auf den Telefonen bestätigt werden. Die konkrete Fremd-App-Meldung zur Play-Herkunft wird nicht als nachgewiesener UniAPK-Absturz ausgegeben. Ein tatsächlich über Play durchgeführtes Update ist der reguläre Weg, die Play-Zuordnung zu erhalten; keine fremde Lizenz oder Herkunft wird vorgetäuscht.

Screenshots und Testpakete enthalten ausschließlich künstliche Testdaten. Persönliche Diagnoseoriginale, private Schlüssel und Zugangsdaten werden nicht ausgeliefert. Quellzuordnung und Prüfsummen: reports/SOURCE-MANIFEST.json und SHA256SUMS.txt.
