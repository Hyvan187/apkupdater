# UniAPK 0.7.0-rc3

## Umgesetzte Anforderungen

| Bereich | Verhalten |
|---|---|
| Favoriten | Derselbe gespeicherte Zustand in Apps und Updates. Der Stern zeigt Kontur oder Füllung und meldet die Änderung direkt. Favorisierte Apps sind als umbrechende Kategorie sichtbar. |
| Ignorierte Apps | Dauerhafter Ausschluss aller Versionen vor Quellenabfragen, vor Einzel-/Sammelprüfungen und automatischen Downloads sowie vor Installationen. Eigene Liste mit Wiederzulassen; vorhandene Apps bleiben installiert. |
| Hintergrundeffekte | Polarlichter als wenige bewegte Lichtvorhänge, Leuchtwürmchen als wandernde pulsierende Lichtpunkte. Die bisherigen Dichte-, Farb-, Geschwindigkeits- und Energiesparregeln bleiben wirksam. |
| Leistung | Automatisches Aufgabenbudget nach verfügbaren Prozessoren, niedrigem Arbeitsspeicherstatus, Energiesparen und thermischer Belastung. Manuelle Auswahl bleibt möglich. Keine vorgetäuschte harte CPU-Kernzuweisung. |
| Kompaktmodus | Appweit kleinere Karteninnenräume, Listenabstände und Typografie. Bedienziele bleiben mindestens 48 dp hoch. |
| Designs | Light, Dusk, Dark und OLED als visuelle Auswahlkarten; Android-Systemdesign, Material-You-Farben und eigene Farben bleiben möglich. Fünf Farbpaletten ergänzt. |
| Einstellungen | Themenkarten nach den gelieferten Bildvorlagen, getrennte Detailbereiche und visuelle Designauswahl. Liquid Glass, Berechtigungen, Sprache, Quellen, Profile, Diagnose, Verlauf und Cache bleiben erreichbar. |
| App-Symbol | Original, Schwarz, Weiß, Milchweiß und Transparent über Launcher-Aliase, adaptive Masken und monochrome Ebenen. Launcher bestimmt endgültige Systemfarbe, Transparenz und die Aktualisierung alter Verknüpfungen. |
| Navigation | Pill, Classic, Labeled, Expressive und Shape-shift; Breiten Compact, 85 Prozent und Large relativ zum nach sicheren Rändern verfügbaren Platz. |
| Bereit auf Start | Vorbereitete Apps erscheinen automatisch mit direkter Installationsaktion. Begrenzte private Wiederherstellung nach kurzem Neustart, solange die Datei vorhanden ist; erneute Prüfung vor Installation. |
| Installer | Bei jeder Installation fragen als Option, Auswahl geeigneter Wege nach Dateiformat. Einzel-APKs werden aus Archiven entpackt; vollständige geprüfte Sets als APKS übergeben, einschließlich tatsächlicher URI-Lesefreigabe. Eine Übergabe allein gilt nicht als Installationserfolg. |
| Play-Herkunft | Bei bislang über Play installierten Apps standardmäßig Auswahl vor dem Storewechsel, mit tatsächlichem Play-Updateweg. UniAPK gibt keinen fremden Installer vor und kann keine fremde Lizenz- oder Integritätsprüfung abschalten. |

Zusätzlich: ein kontrollierter Wiederholungsversuch für vorübergehende GitHub-Metadatenfehler; verständliche Behandlung von Android-Installationsabbrüchen; Erhalt vorbereiteter Dateien beim normalen ViewModel-Ende; Vermeidung unnötiger verschlüsselter Schreibvorgänge. Cachealter und Speicherbudgets bleiben begrenzt.

## Neues OnePlus-Protokoll

Alle drei Dateien des vollständig bereitgestellten Diagnosearchivs vom 8. November wurden lokal gelesen. Version und App-Fingerabdruck stimmen mit der ausgelieferten RC2 überein. 154 Ereignisse: 133 INFO, 6 DEBUG, 5 WARN, 10 ERROR, keine verworfenen Ereignisse.

Vier Metadatensuchen abgeschlossen, eine davon mit einer ausgefallenen Quelle. Dabei keine APK-Downloads protokolliert. Ein ausdrücklich als Einzelvorbereitung gekennzeichneter Download ist vorhanden. Vier externe Installationsversuche scheitern an UniAPKs bisheriger pauschaler Archiv-Sperre. Ein Android-Versuch meldet Abbruch beziehungsweise nicht erteilte Freigabe; ein späterer Versuch wird durch Systemstatus und beobachteten Versionswechsel als erfolgreich bestätigt. Fünf GitHub-Anfragen erhalten HTTP 504. Daraus wird weder ein Gerätefehler noch eine unpassende APK abgeleitet.

Die neue Herkunftsmeldung beim Öffnen einer fremden App liegt nicht als deren Laufzeitprotokoll vor. Googles Installer-/Lizenzprüfung ist eine belegte mögliche Erklärung, keine bewiesene Einzelursache. Die persönliche Originaldatei und eindeutige Sitzung-/Geräteangaben werden nicht in Produktcode, Repository oder Release kopiert. Die erweiterte Diagnose enthält technische Gerätedaten und beteiligte Paketnamen und ist deshalb weiterhin mit Warnhinweis und ausdrücklicher Bestätigung versehen.

## Primärquellen und Grenzen

- Android PackageInstaller.SessionParams: https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams . Eine fremde Installerkennung verlangt die privilegierte INSTALL_PACKAGES-Berechtigung. Normale Installationsfreigabe genügt nicht; packageSource ist nur eine Quellenangabe und erzeugt keine Play-Lizenz.
- Google Installer-/Lizenzprüfung: https://support.google.com/googleplay/android-developer/answer/15621622?hl=en . Geschützte fremde Apps können eine tatsächliche Google-Play-Installation verlangen.
- Android Update-Eigentum: https://source.android.com/docs/setup/create/app-ownership . Installer of Record und Update-Inhaber sind unterschiedliche Angaben.
- APKMirror: https://www.apkmirror.com/apk/apkmirror/apkmirror-installer-official/ . Anbieter nennt APK, APKM, XAPK und APKS als unterstützt. RC3 bietet externe Wege nur für registrierte exportierte MIME-Handler. Verschlüsselte, unvollständige oder technisch abgelehnte Sets sowie OBB-Erweiterungen bleiben gesperrt.

Testergebnisse stehen im abschließenden TEST-RESULTS.md. Hostprüfungen ersetzen keine Ausführung der installierten Drittanbieter-Installer auf privaten Telefonen. Launcher-Eigenheiten, echte Store-/Kontoländerberechtigungen und fremde Lizenzprüfungen bleiben außerhalb einer allgemeinen UniAPK-Garantie.
