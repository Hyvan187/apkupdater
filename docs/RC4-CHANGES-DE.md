# UniAPK 0.7.0-rc4

Die fünf angeforderten Phasen sind Grundlage dieses Stands. Paketkennung und Signatur bleiben erhalten. Ein Update über RC3 behält Einstellungen, Favoriten und Verlauf.

| Phase | Umsetzung |
|---|---|
| 1. Informationsarchitektur | Vier Hauptbereiche: Allgemein, Erscheinungsbild, Updates & Versionen, Erweitert / System. Optionen stehen direkt in der jeweiligen zweiten Ebene. Berechtigungen und Verlauf sind dort eingebettet. Quellen, Geräteprofil, Installation und Versionsfilter bleiben erreichbar. |
| 1. Schnellzugriff | Unter Allgemein konfigurierbare Schalter für die Startübersicht. Die Leiste kann ganz ausgeblendet werden. Dort auch Sprache und die tatsächlich beim nächsten Start geöffnete Startansicht. |
| 2. Version überspringen | Direkte Aktion an der Updatekarte und in der Quellenauswahl, neben dauerhaftem Ignorieren. Bei bekannten Versionscodes wird dieser konkrete Build quellenübergreifend ausgeblendet. Ein höherer Code bleibt auch bei identischem Versionsnamen erlaubt. Kein Download durch diese Aktion. |
| 2. Durchgängige Regeln | Dieselbe Sperre greift bei Metadatenergebnissen, Einzel-/Sammelprüfung, automatischen Downloads, nach dem Lesen der tatsächlichen Manifestversion und vor Installation. Wiederherstellen unter Updates & Versionen, Ausgeblendete Versionen. Dauerhaft ignorierte Apps bleiben ein eigenständiger vollständiger Ausschluss. |
| 3. Gemeinsame Farbpalette | Fünf Vorlagen oder eine frei wählbare Basisfarbe mit Farbrad und HEX-Eingabe. Daraus entstehen Schaltflächen, Akzente, Container, Hintergrund- und Glastönungen. Separate Button-/Hintergrundfarbregler entfallen. Optional eine zusammenhängende Android-Systempalette. |
| 3. Kontrast | Lesbare Vordergründe werden aus den Rollen abgeleitet. Tests prüfen mindestens 4,5:1 für Texte und Aktionsfarben in allen vier Designs mit extremen und normalen Basisfarben. Fehler-/Warnfarben behalten ihre Bedeutung. |
| 3. Ruhige Glasflächen | Die zuvor bunt gefüllten Einstellungs-Kacheln verwenden denselben GlassPanel-Stil, dieselbe Palette, dezente Glanzkanten und standardisierte Abstände. Light, Dusk, Dark und OLED bleiben erhalten. |
| 4. App-Symbole | Vier auf dem ursprünglichen dreidimensionalen Motiv beruhende Rastervarianten: Schwarz, Weiß, Milchweiß und Transparent. Roboter, Pfeile, Rundungen, Glanz und Materialtiefe passen zum Original. Adaptive und monochrome Ebenen sowie Legacy-Symbole bleiben vorhanden. Alle fünf Varianten wurden mit drei Masken nativ gerendert. |
| 5. Ladeanzeigen | Die Welle verändert nun ihre Geometrie über die Zeit, auch bei bekanntem Teilfortschritt. Dezente Glanzlinie und Lichtsaum betten sie in die Glasflächen ein. Neuen Installationen wird die Welle voreingestellt, bestehende Auswahl bleibt bestehen. Linie, Segmente und Ring bleiben wählbar. |
| 5. Zusammenhängende Bewegung | Gemeinsames Tempo und Easing für Navigation, Einstellungen, Aufklappen, Dialogeinblendung, Glasdruck, Dock und lokale Farbpalettenwechsel. Ein zentraler Beobachter berücksichtigt Systemanimationen, Energiesparen, Lebenszyklus und das Aufgabenbudget. Die Unschärfe wird nicht pro Animationsbild neu erzeugt. |

## Übernahme bestehender Einstellungen

Eine frühere eigene Akzentfarbe wird einmalig zur gemeinsamen Basisfarbe. Wenn nur eine eigene Hintergrundfarbe vorhanden war, dient diese als Basis. Unabhängige Farbdeckkräfte werden nicht mehr als separate UI-Farben angewandt. Das gewählte Design bestimmt die Helligkeit; OLED behält einen exakt schwarzen Grund.

Bei einem Versionsfund ohne belastbaren Build-Code kann nur der Versionsname verglichen werden. Dieser Fall wird in der Liste ausdrücklich als „ohne Build-Code“ bezeichnet. Ebenso bleiben ältere RC3-Einträge ohne gespeicherten Code sichtbar und wiederherstellbar; deren unbekannte ursprüngliche Build-Nummer wird nicht erfunden. Ein geänderter Versionsname wird dann wieder angeboten.

## Grenzen der Prüfung

Prozentwerte werden nur aus tatsächlich bekannten Gesamtbytes beziehungsweise einem bekannten Fortschritt gebildet. Unbekannte Laufzeiten bleiben unbestimmt. Bei reduzierten Animationen, deaktivierten Systemanimationen, Energiesparen und sehr kleinem Leistungsbudget werden Bewegungen pausiert oder Übergänge sofort ausgeführt.

Die genauen CPU-/GPU-Zeiten, OEM-Unschärfe, Launcher-Aktualisierung und das Erscheinungsbild auf den beiden physischen Geräten müssen nach Installation beurteilt werden. Die nativen Android-17-Hostansichten ersetzen diese Geräteabnahme nicht. Ein transparentes adaptives Symbol kann durch den Launcher mit einem eigenen Hintergrund versehen werden.

Vorhandene Installations-, Signatur-, Versions-, Cache- und Datenschutzregeln bleiben Bestandteil der Regression. Keine privaten Geräteprotokolle oder Signierschlüssel sind Teil der App oder dieses Downloadpakets.
