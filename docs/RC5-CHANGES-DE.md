# UniAPK 0.7.0-rc5

## Diagnose für die App-Entwicklung

Jeder manuelle Export enthält zusätzlich `settings.json` und `runtime.json`. Die Einstellungsdatei beschreibt den beim Export erfassten Zustand, auch wenn die automatische Aufzeichnung ausgeschaltet ist. Der Laufzeitbericht ergänzt aktuelle Vorgänge, Quellenzustände, Installations- und Benachrichtigungsfreigaben, Ordnerzugriff, Cache- und Speicherwerte sowie die wirksamen Leistungsbedingungen.

Die gemeinsame Einstellungsdefinition umfasst 87 Preference-Felder und separat die drei Diagnoseoptionen. Enthalten sind insbesondere Theme, Basisfarbe und Systemfarben, Glastransparenz, Unschärfe und Brechung, Hintergrundanimation und deren Farben/Geschwindigkeit/Dichte, Kompaktmodus, Dock, App-Symbol, Textdarstellung, angefordertes und wirksames CPU-Budget, Versionsfilter, Quellen und Downloadverhalten. Lesbare Bezeichnungen ergänzen die gespeicherten Auswahlwerte; zuletzt dargestellte Farben und aktuelle Renderbedingungen werden zusätzlich ausgewiesen, soweit verfügbar.

Automatische `settings.snapshot`-Ereignisse erfassen den Zustand beim Start, vor Suchen beziehungsweise Vorbereitungen und nach gespeicherten Änderungen. Zusammengehörige Abschnitte besitzen dieselbe `configurationId` und einen Erfassungszeitpunkt. Unveränderte Zustände werden beim normalen Änderungsbeobachter nicht erneut geschrieben. Änderungen werden nach kurzer Ruhezeit gebündelt; Slider-Vorschauen erzeugen keinen Ereignisstrom. Lange persönliche Listen werden in automatischen Schnappschüssen ausdrücklich gekürzt. Im erweiterten aktuellen Einstellungs-Export sind die gespeicherten Listen vollständig vorhanden.

Allgemeine Diagnose enthält sichere App-Optionen sowie Anzahlen persönlicher Listen. Erweiterte Diagnose ergänzt nach Zustimmung die selbst gepflegten Favoriten, Ausschluss- und Versionslisten, Profile und beteiligte Paketnamen. Sie kann Rückschlüsse auf die App-Nutzung erlauben. Die Hinweise bei Auswahl und Export sind entsprechend erweitert.

Eigene API-Zugänge erscheinen in beiden Diagnosearten ausschließlich als eingerichtet/nicht eingerichtet. Passwörter, API-Schlüssel, Authentifizierungssitzungen, Backup-Inhalte, Ordner-URIs und private Dateipfade werden nicht in die Diagnose übernommen. Die Kurzdiagnose entfernt persönliche Daten auch aus zuvor ausführlich aufgezeichneten strukturierten Ereignissen. Numerische Softwareversionen bleiben lesbar, auch wenn sie wie eine IP-Adresse aussehen; tatsächliche Adressen in Fehlermeldungen werden weiter maskiert. Backup-Fehler behalten die Aufrufstellen, ohne ungeprüfte Datei- oder Passwortinhalte zu protokollieren. Ein Registry-Abgleich im Test verhindert, dass neue Preference-Felder unbemerkt weder als Einstellung noch als interner Zustand klassifiziert werden.

## Backup und Wiederherstellung

Unter **Einstellungen → Erweitert / System → Backup & Wiederherstellung** stehen „Einstellungen sichern“ und „Backup wiederherstellen“ bereit. Die Menüstruktur bleibt zweistufig.

Das Backup enthält alle App-Einstellungen einschließlich Darstellung, Quellen, Limits, Download- und Installerwahl, Geräteprofile, Favoriten, ignorierte Apps, übersprungene Versionen, bevorzugte Quellen und eigener API-Zugänge. Zusätzlich werden Erfassungstiefe, Aktivierung und Dateibegrenzung der Diagnose gespeichert. Androids explizite App-Sprachwahl wird berücksichtigt.

Es handelt sich um eine portable `.uniapkbackup`-Datei mit Passwortverschlüsselung. Das Passwort muss 12 bis 256 Zeichen lang sein, wird nicht gespeichert und ist nicht zurücksetzbar. Die App schreibt keine unverschlüsselte temporäre Backupdatei. Die Datei kann auch auf einer neuen Installation eingelesen werden; der private Android-Keystore-Schlüssel wird nicht exportiert.

Vor dem Einspielen werden Passwort, Integrität, Format, App-Zuordnung, Vollständigkeit, Typen, Wertebereiche, Listen und unterstützte Profile geprüft. Doppelte JSON-Feldnamen, zu tiefe Strukturen, übergroße Dateien und widersprüchliche Werte werden abgelehnt. Vor einer Änderung zeigt die App Zeitpunkt, App-Version, Umfang und enthaltene API-Zugänge als Anzahl an. Ein Backup, das erweiterte Diagnose aktiviert, erfordert dafür erneut ausdrückliche Zustimmung.

Eine Wiederherstellung ist während laufender Suchen, Prüfungen, Installationen oder kollidierender Dateioperationen gesperrt. Der vorherige Einstellungszustand wird vor dem Schreiben im verschlüsselten App-Speicher journalisiert. Schreibfehler lösen eine Rücknahme aus; ein unterbrochener Vorgang wird vor dem nächsten regulären App-Start aus diesem Journal zurückgesetzt. Darstellungs-, Filter-, Favoriten- und Ausschlusszustände werden nach erfolgreichem Import neu geladen. CPU-Budget, Icon, Sprache und Hintergrundplanung werden angewandt; nicht erreichbare Android-Funktionen werden als nachzuarbeitende Punkte gemeldet.

APK-Dateien, Cache, Diagnoseereignisse, Installationsverlauf, vorbereitete Dateien und kurzlebige Store-Anmeldesitzungen sind keine Einstellungen und werden nicht in das Backup aufgenommen. Vorhandene APKs und der Installationsverlauf werden durch den Import nicht gelöscht. Die Play-Anmeldesitzung wird nach der Übernahme neu aufgebaut.

Android-Berechtigungen und Dokumentordnerfreigaben können nicht durch eine Backupdatei erteilt werden. Ein gespeicherter Ordner wird erneut auf tatsächliche Nutzbarkeit geprüft. Ohne gültige Freigabe laufen keine automatischen Downloads. Ein externer Installer muss auf dem Zielgerät vorhanden und für das Dateiformat geeignet sein.

## Technische Grundlage und Grenzen

Verschlüsselung: AES-256-GCM mit frischem 128-Bit-Salt, 96-Bit-Nonce und authentifiziertem Header; Schlüsselableitung PBKDF2-HMAC-SHA256 mit 600.000 Iterationen. Android 8 und neuer verwenden die Plattformimplementierung. Android 6/7 erhalten denselben Algorithmus über die schmale Bouncy-Castle-Implementierung 1.86. Es wird kein Systemanbieter ausgetauscht. Die MIT-Lizenz liegt im Quellpaket und in den App-Assets. Entschlüsselte Einstellungen sind auf 2 MiB begrenzt; unkontrollierte KDF-Kosten und Archivdekompression werden nicht akzeptiert.

Primärquellen für die Auswahl: [Android SecretKeyFactory](https://developer.android.com/reference/javax/crypto/SecretKeyFactory), [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [OWASP Cryptographic Storage](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html), [Bouncy Castle](https://www.bouncycastle.org/). Die Auswahl orientiert sich daran; eine unabhängige Sicherheitszertifizierung wird nicht behauptet.

Die Logs bleiben app-eigen und begrenzt. Sie sind keine vollständige Systemüberwachung und können unbekanntes Verhalten anderer Apps nicht erklären. Die tatsächlichen Tests und noch ausstehende physische Geräteabnahme stehen in `TEST-RESULTS.md`.
