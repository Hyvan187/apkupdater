# Grenzen und Quellen

Stand der Recherche: 7. November 2026. Die Größenwerte sind eigene konservative Engineering-Entscheidungen; ein verbindlicher Standard für automatische Batchgrößen wurde nicht festgestellt. Google nennt im App-Bundle-FAQ Basismodule bis 500 MB und größere kumulierte Zustellungen. Das rechtfertigt keine unbeschränkten Hintergrunddownloads auf einem Telefon.

- Android App Bundle FAQ: https://developer.android.com/guide/app-bundle/faq
- Android Threadsteuerung: https://developer.android.com/reference/android/os/Process und https://developer.android.com/topic/performance/threads
- Öffentlicher Google-Gerätekatalog: https://storage.googleapis.com/play_public/supported_devices.csv . Ein minimierter Auszug steht in device-catalog-evidence.json; der Katalog bestätigt Modell-/Devicecodes, allein keine vollständige regionale SKU-Zuordnung.
- Herstellerquellen für die regionalen Modellzuordnungen stehen direkt in TestDeviceProfiles.kt. OPPO-China-Spezifikationen belegen PMW110/PMX110, internationale Seiten CPH2911/CPH2913. Die OnePlus-Veröffentlichungsangabe trennt CPH2745 von CPH2747. Öffentliche Modellcodes sind keine individuellen Gerätekennungen.
- APKMirror FAQ: https://www.apkmirror.com/faq/ . Kein aktuell dokumentierter allgemein zur Einbettung freigegebener API-Schlüssel wurde nachgewiesen. Die historische Diskussion https://github.com/rumboalla/apkupdater/issues/58 ist kein aktueller Berechtigungsnachweis; alte veröffentlichte App-Zugangsdaten wurden nicht übernommen.
- Android-Prozessbeendigungen: https://developer.android.com/reference/android/app/ApplicationExitInfo . Normale App-Updates/Nutzerbeendigungen belegen keinen Crash.

Externe Store-Berechtigungen, reale OEM-Installerablehnungen und physische Leistungsverhältnisse können durch Code-/Hosttests nicht abschließend bestätigt werden.


Für RC2 geprüft: Androids Storage Access Framework mit persistierbarer Ordnerfreigabe, Benachrichtigungsfreigabe ab API 33 und installationsbezogene Freigabe ab API 26. Die App benötigt dafür keine Berechtigung für sämtliche Dateien. Hersteller können zusätzliche Dialoge/Richtlinien einführen.
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/develop/ui/compose/notifications/notification-permission
- https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()

Die Risikoeinordnung ist eine eigene konservative Regel anhand tatsächlicher Systemkennzeichnung, bekannter zentraler Paketnamen, Herstellernamensräumen, Vorabzweig und vorhandener technischer Nachweise. Sie ist weder ein statistischer Crash-Score noch ein Herstellerzertifikat.
