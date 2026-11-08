# RC3-Geräteabnahme

Die signierten Release-APKs sind gebaut und technisch geprüft. Für OnePlus 15 und Samsung S23: arm64-v8a.apk. UniAPK RC1/RC2 kann mit gleicher Signatur und Paketkennung aktualisiert werden.

- Favorit in Updates setzen, unter Apps > Favorisierte Apps kontrollieren und wieder entfernen.
- Eine App dauerhaft ignorieren, eine neue Suche starten und unter Ignorierte Apps wieder zulassen.
- Eine APK vorbereiten, Prozessfenster für später schließen, direkt auf Start installieren oder die vorbereitete Datei löschen.
- Einstellungen > Downloads und Installer > Bei jeder Installation fragen wählen. Android-Installer sowie den tatsächlich installierten APKMirror-Installer mit unterstützter APK beziehungsweise APKS bestätigen. Erfolg muss erst nach installiertem Versionswechsel im Verlauf stehen.
- Bei einer bisher über Play installierten App den tatsächlichen Play-Weg testen. Eine fremde Herkunfts-/Lizenzprüfung kann nicht universell abgeschaltet werden.
- Light, Dusk, Dark, OLED, Glas, Farben, Kompaktmodus, automatische Leistung, Dock und Launcher-Varianten auf dem jeweiligen Telefon prüfen. Transparenz und alte Verknüpfungen hängen vom Launcher ab.

Neue Probleme mit der passend gewählten Diagnose erfassen; die erweiterte Diagnose kann technische Geräte-/Paketdaten enthalten. Keine persönlichen Originale in öffentlichen Quellcode übernehmen. Tatsächlicher Testumfang und Grenzen stehen in TEST-RESULTS.md.

# UniAPK 0.7.0-rc2: Installation und Geräteabnahme

Für OnePlus 15 und Galaxy S23 ist **arm64-v8a.apk** vorgesehen. Universal ist eine Alternative. Pro Gerät nur eine Variante installieren.

Die neutrale Paketkennung `org.uniapk.upgrader` bedeutet eine separate Installation neben der bisherigen App. Android überträgt deren private Einstellungen, Verlauf oder Zugangsdaten nicht. Gegenüber UniAPK RC1 bleibt die Paketkennung jedoch gleich; RC2 wird als gewöhnliche Aktualisierung installiert. Diese Trennung ist beabsichtigt; private Vorgaben werden nicht in den neuen Produktstand eingebaut.

## Nachgewiesener Umfang

Der genaue Anforderungsabgleich steht in REQUIREMENTS-DE.md, die Auswertung aller vier bereitgestellten Archive in LOG-REVIEW-DE.md. TEST-RESULTS.md dokumentiert die tatsächlich bestandenen Prüfungen. Signaturen, Architekturinhalt, Dateiprüfsummen und Quellstand werden im Release-Paket unabhängig ausgewiesen.

## Noch am physischen Gerät zu bestätigen

1. Systeminstaller: erfolgreiche Installation, Abbruch und abgelehnte Installation; danach tatsächlichen Paketstand und Verlauf vergleichen. Die früheren OnePlus-Ablehnungen sind ohne damalige Android-Statusmeldung nicht vollständig rekonstruierbar.
2. Downloadordner: Berechtigung erteilen und entziehen. Automatische Downloads müssen bis zur ausdrücklichen Aktivierung mit gültigem Ordner ausbleiben. Reine Sammelprüfungen dürfen keine öffentliche APK hinterlassen.
3. Länderprofil: Produkt und Region getrennt wählen, die technischen Angaben kontrollieren. Keine unbelegten Länderzuordnungen erzwingen. Konto, IP und Store-Freigabe können das Ergebnis weiterhin bestimmen.
4. ROM-Erkennung: gelesene Herstellerhinweise prüfen. Bei gesperrten oder widersprüchlichen Systemwerten ist „nicht auslesbar“ das korrekte Ergebnis; die Sprache des Telefons ist kein ROM-Nachweis.
5. Diagnose: allgemeine und erweiterte Datei exportieren; Datenschutzhinweis, Bestätigung und Begrenzung auf 3/5/10 lokale Dateien prüfen. Exportierte Nutzerdateien werden separat verwaltet.
6. Oberfläche: rundes und abgerundetes Launcher-Icon, Animationen, Drehung und echte Bildrate/Akkuwirkung der Ressourcenwahl kontrollieren.

Diese Punkte sind konkrete Geräteabnahme, keine behaupteten bereits durchgeführten Tests auf den beiden privaten Telefonen. Die Hosttests und der Android-17-Emulator ersetzen weder OEM-Installationsrichtlinien noch Live-Store-Berechtigungen.

## Bewusste Grenzen

Ein allgemein zur Einbettung autorisierter aktueller APKMirror-Schlüssel wurde nicht nachgewiesen; das Feld bleibt leer und der Webmodus bleibt nutzbar. Die Prozessoroption begrenzt rechenintensive App-Aufgaben und Effekte, garantiert aber keine harte Kernbindung sämtlicher Android-Threads. Technische APK-Prüfung ist keine Malware- oder Funktionsgarantie. Größen- und Anzahlbudgets sind dokumentierte konservative Vorgaben, keine erfundene branchenweite Norm.
