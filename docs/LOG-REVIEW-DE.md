# Prüfung der vier bereitgestellten Diagnosearchive

Alle vier ZIPs waren unbeschädigt, die enthaltenen JSON-/Textdateien lesbar und die Eintragszahlen mit metadata.json konsistent. Die Originale bleiben unverändert und werden weder in die neue APK noch in den öffentlichen Quellcode aufgenommen. Die Zuordnung A/B dient hier einer datensparsamen Zusammenfassung.

| Lauf | Vollprotokoll | Kurzprotokoll | Einordnung |
|---|---:|---:|---|
| A, Samsung-Test | 191 Einträge | 188 Einträge | Alle Vollereignisse im aktuellen Format 2. Ein bestätigter Installationslauf vorhanden. Vier ERROR-Ereignisse, darunter APKMirror-Zugriff und Play-Download-Zeitüberschreitung. |
| B, OnePlus-Test | 471 Einträge | 200 Einträge | Davon 200 aktuelle Format-2-Ereignisse und 271 ältere Einträge aus der vorherigen Fassung. Die ältere Kohorte darf nicht pauschal als aktueller RC3-Fehler gezählt werden. Zwei vom Installer abgewiesene Versuche sind belegt; die genaue Android-Meldung wurde zuvor nicht gespeichert. |

Der Kurzexport von Lauf B erreicht seine dokumentierte Grenze von 200 Einträgen. Dass die Kurzzahlen von den Vollzahlen abweichen, ist damit kein Archivfehler. Lauf A erzeugte den Kurzexport vor weiteren Exportereignissen. Kein Vollprotokoll enthält ein crash.uncaught-Ereignis. Dies ist kein Nachweis allgemeiner Absturzfreiheit.

## Konkrete Befunde und Korrekturen

- APKMirror: wiederholte HTTP-401-Antworten. Ein aktuell legitim zur allgemeinen Einbettung freigegebener Anbieter-Schlüssel wurde nicht nachgewiesen. Neue Option für eigene Zugangsdaten; ohne Schlüssel wird ausdrücklich Webmodus genutzt. APKMirror-Webmodus gilt nicht als vollständig abgefragter API-Katalog und begründet allein keinen „aktuell“-Zeitraum.
- Netzwerk: Zeitüberschreitungen, unterbrochene Verbindung und ältere nicht ausreichend klassifizierte Fehler. Eine InterruptedIOException mit timeout und nachgelagerter Socket-/HTTP2-Ursache wird jetzt vorrangig als Zeitüberschreitung erkannt. Der Downloadpfad erhält längere Gesamtzeit und unmittelbare Abbruchbindung an den laufenden HTTP-Aufruf.
- Installation: die alten Protokolle erfassten die Ausnahmeart, jedoch nicht die notwendige Android-Statusnummer und die bereinigte Statusmeldung. Diese werden nun mitgeführt; das Prozessfenster stellt die Fehlermeldung dar. Es wird keine erfolgreiche Installation behauptet, bevor der tatsächliche Paketstand bestätigt wurde. Aus den alten Aufzeichnungen lässt sich die ursprüngliche OEM-Ablehnung nicht eindeutig reparieren oder rückwirkend erklären.
- Fortschritt: ein unbestimmter Balken allein war für den bewussten APK-Download unzureichend. Das neue Prozessfenster zeigt übertragene Bytes und bei belastbarer Größenangabe Prozent, danach die tatsächliche Analysephase und deren Evidenz. Eine fehlende Gesamtgröße wird offen benannt.
- Diagnoseumfang: Standard/Ausführlich und Kurz/Vollständig waren zwei unterschiedlich wirkende Auswahlachsen. Neu gibt es einen gemeinsamen Umfang für Aufzeichnung und Export. Allgemeine Exporte entfernen Geräteprofile, App-Paketnamen, freie Ausnahmetexte und Aufrufketten; erweiterte Aufzeichnung/Export erklärt technische Rückschlussmöglichkeiten und benötigt eine bewusste Bestätigung.
- Aufbewahrung: automatische lokale Ringdateien sind nun mit 3/5/10 auswählbar. Alte Dateien werden bei der Grenze entfernt; jede Datei ist größenbegrenzt, zusätzlich gilt die Altersgrenze. Exportierte ZIPs werden nicht nachträglich vom privaten Recorder gelöscht.

## Personenbezug und Transparenz

Die bisherigen Vollprotokolle enthielten technische Angaben wie Gerätemodell, Hersteller, Android-/Displaykonfiguration, Sprache, Zeitzone, Netztyp und Paketnamen beteiligter Apps. Diese können zusammengenommen Rückschlüsse auf Gerät und Nutzung erlauben. Das ist von fest in eine APK eingebauten privaten Nutzervorgaben zu unterscheiden. Vorinstallierte persönliche Namen/Beispiele wurden aus der neuen Produktquelle entfernt. Öffentliche Modellkennungen werden ausschließlich als dokumentierte Katalogdaten geführt.

Die neue App protokolliert keine Anfrage-/Antwortinhalte, Nachrichten, Passwörter oder absichtlich eindeutige Hardwarekennungen. Bekannte Zugangsdaten-/Pfad-/Kennungsmuster werden bereinigt. Eine vollständige Anonymitätsgarantie für beliebige technische Fehlermeldungen ist dennoch nicht seriös; die erweiterte Diagnose enthält deshalb einen deutlich sichtbaren Hinweis und Bestätigungsdialog. Auch bereinigte ZIPs sollten vor Weitergabe kontrolliert werden.

Die Archive entsprechen einem geeigneten ZIP/JSONL-Diagnoseformat; eine universelle externe Zertifizierung für dieses app-eigene Format wird nicht behauptet. Wichtig sind belastbare Ergebnisse, Ursachen, Ablaufzuordnung, begrenzte Aufbewahrung und Datenminimierung. Die neuen Merkmale werden durch tatsächliche Export-, Redaktions-, Rotations- und Prozesspfadtests abgedeckt. Reale Android-Ordnerauswahl, Hersteller-Installer und Live-Play-Zustellung bleiben Geräteabnahme.
