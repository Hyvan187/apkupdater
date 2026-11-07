# UniAPK 0.7.0-rc1: Prüfstand

Geprüfter Implementierungscommit: `d4af549775455728bc81f5a3105e904cb1bfeb1e`. Sein Quellbaum: `7ed08d1b9960dd92e9e06b7d4ed4379f1f3cdd57`. Abschließende Änderungen an Übergabe und Berichten betreffen nur Dokumentation. Die Zuordnung zum ausgelieferten Gesamtbaum und der in allen vier APKs enthaltene App-Fingerabdruck stehen in reports/SOURCE-MANIFEST.json.

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| Vollständige lokale Hosttests | 217 Tests, 0 Fehler, 0 fehlgeschlagen, 0 übersprungen |
| Release-Lint | 0 Fehler / Fatals, 301 Warnungen; vollständige XML-, Text- und HTML-Berichte beigefügt |
| Optimierter Release-Bau | Erfolgreich; JDK 21, Java-17-Bytecode, API 23 bis Ziel-API 37 |
| Signatur aller vier APKs | v1/v2/v3 gültig, gleicher erwarteter Zertifikat-Fingerabdruck; nicht debuggable |
| ABI-Inhalte | ARM64 nur arm64-v8a; ARM32 nur armeabi-v7a; x86_64 nur x86_64; Universal enthält diese drei und x86 |
| Ausrichtung | APK-ZIP und native ELF-LOAD-Segmente mit 16-KiB-Prüfung bestanden |
| Produktidentität | org.uniapk.upgrader, Version 0.7.0-rc1 / 1009, vollständiger App-Name und Launcher-Kurzname UniAPK geprüft |
| Unabhängige CI | Lauf 42418854596, Build und Android-17-Smoke erfolgreich, identischer Implementierungscommit |
| Nativer Android-17-Start | Debug-APK im x86_64-Emulator installiert, Activity-Start „Status: ok“, Prozess nach 15 Sekunden noch vorhanden; synthetischer Screenshot beigefügt |
| Vier bereitgestellte Diagnosearchive | ZIP-Struktur, Metadaten und sämtliche Ereignisse ausgewertet; datensparsame Zusammenfassung beigefügt |

## Inhaltliche Regression

Die bisherigen Schutztests bleiben erhalten. Zusätzliche Prüfungen decken Produkt-/Länderauswahl mit tatsächlich angewendeten Codes, gesperrte unbelegte Kombinationen, Samsung-CSC-Priorität, widersprüchliche Regionsdaten, Xiaomi und weitere Hersteller, gleiche sichtbare Versionen, Download-Standardwerte und Speicherreserve, API-Zugangsdaten nur am vorgesehenen HTTPS-Host, APKMirror-Webmodus ohne API-Aufruf und ohne übernommene Fremdfreigabe, Diagnosebereinigung, Ringrotation, Cache-Leases, automatische Downloadbelege, tatsächliche Manifestdaten nach automatischem Download und Android-6-Ordnerkompatibilität ab.

Reale signierte APK-Testdateien decken Manifest-/Signatur-/Split-Fehler ab; ausschließlich der vom Host nicht unterstützte Android-Paketmetadatenleser wird dabei ersetzt. Compose-Prüfungen verwenden die native Grafikimplementierung des Hosttests und decken kompakte/helle Oberflächen, Quellenauswahl sowie Hintergrundmodi in Hoch- und Querformat ab. Die Bilder enthalten synthetische Testdaten.

## Warnungen und Aussagegrenzen

Lint ist nicht warnungsfrei. Der vollständige Bericht enthält überwiegend Übersetzungs-/Ressourcenhinweise sowie Hinweise zu API-Konstanten, Formatierung, Icon-Ressourcen und Bibliotheksständen. Es wurden weder eine Fehlerbaseline noch pauschale neue Unterdrückungen eingeführt. Ein in der Prüfung entdeckter ungeschützter API-24-Aufruf wurde für API 23 abgesichert und separat getestet.

Die unabhängige CI startet eine Debug-APK; sie ist keine zusätzliche ausgelieferte Produktvariante und ersetzt keinen physischen Test der signierten Release-APKs auf den beiden privaten Telefonen. Der Emulator zeigte eine vom Play-Anbieter verweigerte Anfrage; die App blieb gestartet und stellte die Quellenstörung dar. Der Smoke-Test belegt daher Installation/Start und Fehlerdarstellung, keine erfolgreiche Live-Play-Zustellung.

Die genauen alten OnePlus-Installerablehnungen waren mangels damals aufgezeichneter Android-Statusmeldung nicht eindeutig rekonstruierbar. Der neue Stand protokolliert Nummer und bereinigte Meldung. Konto-/IP-/Store-Länderfreigaben, durch Hersteller gesperrte ROM-Werte, echte Ordnerberechtigungen, Launcher-Masken und gemessener Akku-/Leistungsbedarf bleiben physische Geräteabnahme gemäß RELEASE-CHECKLIST-DE.md.

Keine externe Zertifizierung, vollständige Absturzfreiheit, Malwarefreiheit oder universelle Herstellerkompatibilität wird aus diesen Prüfungen abgeleitet.
