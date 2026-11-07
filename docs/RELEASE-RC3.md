# Anforderungsabgleich: 0.6.0-rc3

Die gesicherte RC2-Quelle wurde mit 319 Dateiprüfsummen abgeglichen und isoliert fortgeführt. Übergabe und frühere Funktionskorrekturen sind erhalten. Die zusätzliche Prüfung hat die doppelte Transparenzbedienung bestätigt; erst RC3 beseitigt sie vollständig aus den allgemeinen Einstellungen. Die historische RC2-Aussage war dafür zu weitgehend.

| Anforderung | Umsetzung und Beleg |
|---|---|
| Zwei aktuelle Protokolle vollständig prüfen | Beide Archive, Metadaten, sämtliche 263 JSONL-Einträge und 124 Kurzexport-Einträge geprüft. Ausführliche anonymisierte Auswertung und Originaldatei-Prüfsummen im Lieferpaket; private Protokolle werden nicht öffentlich eingecheckt. |
| Fehler sinnvoll behandeln | Bestätigte eigene Abbrüche getrennt von HTTP/2-, DNS-, TLS- und Zeitüberschreitungsfehlern; Ursachenketten und Netzwerk-/Quellenabschlüsse. Normale Android-Update-/Nutzerbeendigungen werden nicht als Absturz geführt. |
| Aussagekräftige Diagnose | Format 2 mit Zeit, Sequenz, Prozess-/Ablauf-/Anfragekennung, Ergebnissen, expliziten Downloadauslösern, Build-/ABI-/Displaydaten und grobem Netzwerktyp; keine vollständigen URLs, Inhalte oder Zugangsdaten. Ganze UTF-8-Zeilen und verlässliche Kurzexport-Zählung. |
| APKMirror-401 beheben, soweit möglich | Wiederholte abgelehnte API-Aufrufe pausieren; bewusste erneute API-Abfrage und gesonderter Webtest. Fehlende zulässige API-Zugangsdaten bleiben eine externe Grenze. |
| Gleiche APK-Größen untersuchen | Alle vier RC2- und RC3-Dateien direkt geöffnet: korrekte unterschiedliche ABIs, Größen und Prüfsummen. Der gemeinsame DEX-/Ressourcenanteil erklärt die gleiche gerundete Größenanzeige. Keine künstliche Vergrößerung. |
| Transparenz einmal logisch anordnen | Regler nur unter Erscheinungsbild; allgemeine Einstellungen öffnen diesen Bildschirm. Oberflächentest prüft Abwesenheit im allgemeinen Menü sowie genau einen wirksamen Regler im Erscheinungsbild. |
| Gesamte App erneut prüfen | Bestehende Regressionen zu Suche, Kompatibilität, Quellenwahl, APK-Sets, Cache, Installationserkennung, Darstellung und Export erneut ausgeführt; 183 Tests bestanden. Release-Lint und Prüfung aller vier signierten Pakete bestanden. |
| Frühere Anforderungen erhalten | Play bleibt auswählbar, Suche/Berichte laden keine APKs, explizite Prüfung/Installation kontrolliert Gesamtsets; installierte Versionen werden abgeglichen. Hintergrundanimationen und Transparenz bleiben durch vorhandene Oberflächentests abgedeckt. |
| Zuverlässiger Build | SDK-Installation und namespace-erhaltende Metadatenanpassung korrigiert. Aktueller GitHub-Nachweis separat mit tatsächlichem Ergebnis dokumentiert. |
| Fertige ZIP mit allen APKs | ARM64, ARMv7, x86_64, Universal; Version 1008 / 0.6.0-rc3; Signaturidentität erhalten, nicht debuggbar. Quellstand, Berichte, genaue Bytes und Prüfsummen beiliegend. |

Die automatische Prüfung ersetzt keine vollständige Geräteabnahme. Noch erforderlich sind Cems reale Update-/Abbruchabläufe, Live-Play-/OEM-Verhalten, breite Zugänglichkeitsprüfung und Messungen von Bildrate, Speicher und Energie. 292 Lint-Warnungen sind offengelegt. Ein Fehlerfreiheits- oder Zertifizierungsversprechen lässt sich daraus nicht ableiten.
