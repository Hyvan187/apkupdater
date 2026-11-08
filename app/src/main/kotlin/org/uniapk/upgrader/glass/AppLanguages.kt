package org.uniapk.upgrader.glass
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import org.uniapk.upgrader.prefs.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalTextStyle

val LocalDisplayLanguage=staticCompositionLocalOf{"de"}
class AppLanguageStore(private val prefs:Prefs) {
 private val mutable=MutableStateFlow(prefs.appLanguage.get())
 val state:StateFlow<String> = mutable
 fun current(context:Context):String = if(Build.VERSION.SDK_INT>=33)context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore(",").ifBlank{"SYSTEM"} else prefs.appLanguage.get()
 fun set(context:Context,tag:String){require(tag in setOf("SYSTEM","de","en"));if(Build.VERSION.SDK_INT>=33){context.getSystemService(LocaleManager::class.java).applicationLocales=if(tag=="SYSTEM")LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)}else{prefs.appLanguage.put(tag);mutable.value=tag};prefs.appLanguage.put(tag);mutable.value=tag}
 fun openSettings(context:Context){if(Build.VERSION.SDK_INT>=33)context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS,Uri.parse("package:${context.packageName}")))}
}
@Composable fun Text(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,style:TextStyle=LocalTextStyle.current,fontWeight:FontWeight?=null,maxLines:Int=Int.MAX_VALUE,overflow:TextOverflow=TextOverflow.Clip){
 androidx.compose.material3.Text(text=UiEnglish.translate(text,LocalDisplayLanguage.current),modifier=modifier,color=color,style=style,fontWeight=fontWeight,maxLines=maxLines,overflow=overflow)
}
object UiEnglish {
 private val exact=mapOf(
  "Allgemein" to "General", "Updates & Versionen" to "Updates & versions", "Erweitert / System" to "Advanced / system",
  "Startansicht" to "Start view", "Startübersicht" to "Overview", "Installierte Apps" to "Installed apps",
  "Basisfarbe" to "Base color", "Basisfarbe anpassen" to "Customize base color", "Diese Version überspringen" to "Skip this version",
  "Installationsfreigabe verwalten" to "Manage installation permission",
  "Design, Farbpalette, Glas und Leistung" to "Theme, palette, glass and performance",
  "Sprache, Startansicht, Schnellzugriff und Berechtigungen" to "Language, start view, shortcuts and permissions",
  "Design, Farbpalette, Glas, Icons und Leistung" to "Theme, palette, glass, icons and performance",
  "Quellen, Versionsfilter, Downloads und Installation" to "Sources, version filters, downloads and installation",
  "Cache, Verlauf, Diagnose und App-Informationen" to "Cache, history, diagnostics and app information",
  "Diese Ansicht öffnet sich beim nächsten App-Start." to "This view opens the next time you start the app.",
  "Wähle die Schalter für die Schnellstart-Leiste auf der Startübersicht." to "Choose the toggles for the overview shortcut bar.",
  "Die Schnellstart-Leiste ist ausgeblendet." to "The shortcut bar is hidden.",
  "Eine Basisfarbe bestimmt Akzente, Schaltflächen und Glastönungen. Die Kontraste passen sich dem Design an." to "One base color defines accents, buttons and glass tints. Contrast adapts to the theme.",
  "Verwendet die zusammenhängende Android-Farbpalette statt deiner Basisfarbe." to "Uses the coordinated Android palette instead of your base color.",
  "Überspringen betrifft diesen Build. Ein höherer Build erscheint wieder; bei fehlendem Build-Code gilt der Versionsname." to "Skips this build. A higher build is shown again; without a build code, the version name is used.",

  "Design und Leistung" to "Design and performance",
  "Leistungsbudget" to "Performance budget",
  "Berechtigungen" to "Permissions",
  "Berechtigungen verwalten" to "Manage permissions",
  "UNIAPK EINRICHTEN" to "SET UP UNIAPK",
  "ZUGRIFFE VERWALTEN" to "MANAGE ACCESS",
  "Weiter zur App" to "Continue to app",
  "Fertig" to "Done",
  "Apps installieren" to "Install apps",
  "Installation erlauben" to "Allow installation",
  "Freigabe verwalten" to "Manage access",
  "Benachrichtigungen" to "Notifications",
  "Benachrichtigungen erlauben" to "Allow notifications",
  "Benachrichtigungen verwalten" to "Manage notifications",
  "APK-Ordner" to "APK folder",
  "Ordner auswählen" to "Choose folder",
  "Ordner ändern" to "Change folder",
  "APK-Ordner ändern" to "Change APK folder",
  "Ordner freigegeben" to "Folder access granted",
  "Ordnerzugriff fehlt" to "Folder access unavailable",
  "Kein eigener Ordner gewählt" to "No custom folder selected",
  "Android-App-Info öffnen" to "Open Android app info",
  "Animationsfarben" to "Animation colors",
  "Passend zum Effekt" to "Effect colors",
  "Einfarbig" to "Single color",
  "Zwei Farben" to "Two colors",
  "Animationsfarbe" to "Animation color",
  "Zweite Animationsfarbe" to "Second animation color",
  "Ladeanzeige" to "Progress indicator",
  "Linie" to "Line",
  "Segmente" to "Segments",
  "Welle" to "Wave",
  "Ring" to "Ring",
  "Vorschau · 65 %" to "Preview \u00b7 65%",
  "Vorbereitete APKs löschen" to "Delete prepared APKs",
  "Vorbereitete APKs löschen?" to "Delete prepared APKs?",
  "APKs löschen" to "Delete APKs",
  "Keine vorbereiteten APKs" to "No prepared APKs",
  "Keine neuen Funde" to "No new findings",
  "Datei noch ungeprüft" to "File not checked yet",
  "Risikoeinschätzung" to "Risk assessment",
  "Geringeres Risiko" to "Lower risk",
  "Erhöhtes Risiko" to "Elevated risk",
  "Hohes Risiko" to "High risk",
  "Nicht installierbar" to "Not installable",
  "Kritische Komponente aktualisieren?" to "Update a critical component?",
  "Risiko verstanden · fortfahren" to "Risk understood \u00b7 continue",
  "Was bedeuten die Bereiche?" to "What do these sections mean?",
  "Funde vorübergehend prüfen" to "Check findings temporarily",
  "SUCHE, PRÜFUNG UND INSTALLATION" to "SEARCH, CHECK AND INSTALL",
  "APK vorbereiten" to "Prepare APK",
  "Google-System-App" to "Google system app",
  "System-App" to "System app",
  "Allgemeine App" to "User app",
  "APK wird untersucht" to "Inspecting APK",
  "Übersicht" to "Overview", "Nach Updates suchen" to "Find updates", "Funde" to "Found", "Deine Updates im Blick" to "Your updates at a glance",
  "Quelle wählen · APK vorbereiten" to "Choose source · prepare APK", "APK erneut laden und vorbereiten" to "Download again and prepare", "Funde nur auf Kompatibilität prüfen" to "Check compatibility without keeping downloads",
  "APK wird heruntergeladen" to "Downloading APK", "APK und Paketbestandteile werden geprüft" to "Inspecting APK and package parts", "Jetzt installieren" to "Install now", "Später installieren" to "Install later",
  "Technisch geprüft · bereit zur Installation" to "Technically checked · ready to install", "Android-Installation" to "Android installation", "Installiert · im Verlauf gespeichert" to "Installed · saved to history", "Vorgang nicht abgeschlossen" to "Process not completed", "Vorgang abgebrochen" to "Process cancelled",
  "Im Hintergrund fortsetzen" to "Continue in background", "Erneut laden und prüfen" to "Download and check again", "Installation erneut starten" to "Retry installation",
  "Änderungen dieser Version" to "What's new in this version", "Die Quelle stellt keinen Änderungsverlauf bereit." to "The source provides no release notes.",
  "Testgerät" to "Test device", "Länderversion" to "Regional version", "Echtes Gerät" to "Actual device", "Europa" to "Europe", "Indien" to "India", "China" to "China", "Global" to "Global",
  "Optionale API-Zugänge" to "Optional API credentials", "APKMirror API-Schlüssel" to "APKMirror API key", "GitHub-Token · optional" to "GitHub token · optional", "GitLab-Token · optional" to "GitLab token · optional", "Zugänge speichern" to "Save credentials",
  "Allgemeine Diagnose" to "General diagnostics", "Erweiterte Diagnose" to "Detailed diagnostics", "Automatisch lokal aufzeichnen" to "Record locally automatically", "Umfang der Aufzeichnung und des Exports" to "Capture and export scope", "Alte Logdateien automatisch entfernen" to "Remove old log files automatically", "Gewählte Diagnose als ZIP exportieren" to "Export selected diagnostics as ZIP", "Lokale Diagnose löschen" to "Delete local diagnostics",
  "Technische Gerätedaten enthalten" to "Contains technical device information", "Bewusst exportieren" to "Export with these details", "Erweiterte Aufzeichnung aktivieren" to "Enable detailed recording",
  "CPU und Glaseffekte" to "CPU and glass effects", "CPU-Aufgaben" to "CPU tasks", "Polarlicht" to "Aurora", "Farbnebel" to "Color nebula", "Prismen" to "Prisms", "Farbwellen" to "Color waves", "Konfetti" to "Confetti",

  "Glastransparenz" to "Glass transparency", "Standard: 54 %" to "Default: 54%", "0 % · deckend" to "0% · opaque", "100 % · transparent" to "100% · transparent",
  "Für Karten, Menüs, Dialoge und Navigation. Höher zeigt mehr Hintergrund; Text und Symbole bleiben deckend. Unschärfe bleibt separat einstellbar." to "For cards, menus, dialogs and navigation. Higher values reveal more background; text and icons remain opaque. Blur is adjusted separately.",
  "Standard · 54 % Glastransparenz · Unschärfe und Brechung an" to "Standard · 54% glass transparency · blur and refraction on",
  "Standard ist ausgewogen. Hoch kostet mehr Grafikleistung; Aus deaktiviert Unschärfe und Brechung. Die Transparenz bleibt einstellbar." to "Standard is balanced. High requires more graphics work; Off disables blur and refraction. Transparency remains adjustable.",
  "Setzt nur Qualität, Glastransparenz, Unschärfe und Brechung auf ausgewogene Werte. Deine Farben bleiben erhalten." to "Resets quality, glass transparency, blur and refraction to balanced values. Your colors are retained.",
  "Einstellungen" to "Settings","Erscheinungsbild" to "Appearance","Design anpassen" to "Customize appearance","Updateprüfung" to "Update checks","Updates" to "Updates","Start" to "Home","Apps" to "Apps","Deine Apps" to "Your apps","INSTALLIERTE PAKETE" to "INSTALLED PACKAGES","App oder Paket suchen" to "Search apps or packages",
  "Alle" to "All","Allgemeine" to "User apps","Allgemeine Apps" to "User apps","System-Apps" to "System apps","Google-System-Apps" to "Google system apps","Alle Zustände" to "All states","Deaktiviert" to "Disabled","Ignoriert" to "Ignored","Favoriten" to "Favorites","Nur Favoriten" to "Favorites only",
  "Prüfstatus" to "Check status","Bereit" to "Ready","Prüfen" to "Check","Blockiert" to "Blocked","Abgelehnt" to "Rejected","Prüfung offen" to "Check pending","Prüfung läuft …" to "Checking …","Geprüft · bereit" to "Checked · ready",
  "Alles im Blick." to "Everything at a glance.","Sicher aktualisieren." to "Update safely.","Updates suchen" to "Find updates","Quellen werden geprüft …" to "Checking sources …","WERKZEUGE" to "TOOLS","APK untersuchen" to "Inspect APK","Datei prüfen, ohne sie zu installieren" to "Inspect a file without installing it","Automatische Geräteerkennung" to "Automatic device detection","Noch keine abgeschlossene Suche" to "No completed search yet",
  "Keine freigegebenen Updates" to "No approved updates","Nichts zur Prüfung offen" to "No pending checks","Keine blockierten Varianten" to "No blocked variants","Updates durchsuchen" to "Search updates","Alle verfügbaren APKs prüfen" to "Check all available APKs","Sammelprüfung abbrechen" to "Cancel batch check","Prüfung abbrechen" to "Cancel check","Quellenfehler" to "Source errors","Suche fehlgeschlagen" to "Search failed",
  "Installieren" to "Install","Andere Quelle / erneut prüfen" to "Other source / check again","Quelle wählen und prüfen" to "Choose source and check","Diese Version ausblenden" to "Hide this version","Technische Details" to "Technical details","Quelle auswählen" to "Select source","Auswahl herunterladen und prüfen" to "Download selection and check","Quellseite öffnen" to "Open source page",
  "Version" to "Version","Architektur" to "Architecture","Kategorie" to "Category","Quelle" to "Source","Signatur" to "Signature","Geprüft" to "Checked","Passt zur Installation" to "Matches installed app","Passt nicht" to "Does not match","Noch offen" to "Still unknown","System-App" to "System app","Allgemeine App" to "User app",
  "Verstanden" to "Got it","Schließen" to "Close","Abbrechen" to "Cancel","Übernehmen" to "Apply","Löschen" to "Delete","Wieder einblenden" to "Show again","Standard wiederherstellen" to "Restore default",
  "Empfohlenes Liquid Glass" to "Recommended Liquid Glass","Empfohlene Glaswerte anwenden" to "Apply recommended glass settings","Ausgewogen: Standard · 46 % Glasdeckkraft · Unschärfe und Brechung an" to "Balanced: Standard · 46% glass opacity · blur and refraction on",
  "Standard · 46 % Glasdeckkraft · Unschärfe und Brechung an" to "Standard · 46% glass opacity · blur and refraction on","FARBEN, GLAS UND BEWEGUNG" to "COLORS, GLASS AND MOTION","Design" to "Theme","System" to "System","Hell" to "Light","Dunkel" to "Dark","OLED-Optimierung" to "OLED optimization","Schwarzer Hintergrund; statische Farblichtflächen entfallen." to "Black background without static colored glows.","Systemfarben" to "System colors","Akzente aus deinem Android-Design." to "Accents from your Android theme.","Button- und Akzentfarbe" to "Button and accent color","Hintergrundfarbe" to "Background color","Buttonfarbe" to "Button color",
  "Glas und Bewegung" to "Glass and motion","Animationen reduzieren" to "Reduce motion","Pausiert Hintergrundbewegung und Parallaxe." to "Pauses background motion and parallax.","Hintergrundunschärfe" to "Background blur","Weichzeichnet den hinter Glas liegenden Inhalt." to "Blurs content behind the glass.","Flüssiges Glas" to "Liquid glass","Lichtbrechung ab Android 13 bei Standard oder Hoch." to "Refraction on Android 13+ at Standard or High.","Stärkere Navigationsunschärfe" to "Stronger navigation blur","Die schwebende Leiste erhält mehr Unschärfe." to "Adds more blur to floating navigation.","Aus" to "Off","Reduziert" to "Reduced","Standard" to "Standard","Hoch" to "High",
  "Hintergrundanimation" to "Background animation","Keine" to "None","Kreise" to "Circles","Ringe" to "Rings","Netz" to "Network","Weltraum" to "Space","Formen" to "Shapes","Schnee" to "Snow","Raster" to "Grid","Partikel" to "Particles","Lavalampe" to "Lava lamp","Zufällig" to "Random","Parallax-Effekt" to "Parallax effect","Sanfte Verschiebung beim Neigen; nur bei aktivem Bildschirm." to "Subtle tilt movement while the app is active.","Saisonale Designs" to "Seasonal themes","Lokale Winter- und Jahreswechsel-Effekte; keine Standortabfrage." to "Local winter and New Year effects; no location access.",
  "Schrift" to "Typography","Systemschrift" to "System font","Sans Serif" to "Sans serif","Serif" to "Serif","Monospace" to "Monospace","Helligkeit" to "Brightness","HEX-Farbe" to "HEX color","Alle Darstellungswerte auf Standard zurücksetzen" to "Reset all appearance settings",
  "App-Auswahl" to "App selection","Beta-Versionen ausblenden" to "Hide beta versions","Erkennbare Beta-Versionen nicht anbieten." to "Do not offer identifiable beta versions.","Alpha-Versionen ausblenden" to "Hide alpha versions","Erkennbare Alpha-Versionen nicht anbieten." to "Do not offer identifiable alpha versions.","Alle Vorabversionen ausblenden" to "Hide all prereleases","Auch Beta, Alpha, RC, Preview und markierte Testversionen." to "Includes beta, alpha, RC, preview and marked test releases.","Wie werden APKs geprüft?" to "How are APKs checked?",
  "Automatische APK-Prüfung" to "Automatic APK checks","Automatische Prüfung aktiv" to "Enable automatic checks","Nach der Suche geeignete APKs laden und prüfen." to "Download and inspect eligible APKs after searching.","Automatische APK-Prüfung nur ungetaktet" to "Automatic checks on unmetered networks only","Schont mobiles Datenvolumen." to "Saves mobile data.","Hintergrundsuche" to "Background search","Täglich nach Updates suchen" to "Search for updates daily","Keine automatische Installation." to "No automatic installation.","Hintergrundsuche nur ungetaktet" to "Background search on unmetered networks only","Sucht nur ohne getaktete Verbindung." to "Search only using an unmetered connection.",
  "Updatequellen" to "Update sources","API-Abfrage und manuelle Webseite; keine Downloadfreigabe." to "API query and manual web page; not an APK approval.","Zugeordnete Entwickler-Releases." to "Associated developer releases.","Open-Source-Katalog; Signatur kann abweichen." to "Open-source catalog; signatures may differ.","Open-Source-Repository." to "Open-source repository.","Anbieterkatalog; jede Datei separat prüfen." to "Provider catalog; inspect each file separately.","Vorhandene experimentelle Integration; Verfügbarkeit abhängig vom Dienst." to "Existing experimental integration; service availability varies.",
  "Quellenstatus" to "Source status","Kein Fehler gemeldet" to "No error reported","Wird abgefragt …" to "Querying …","Verbindung wird geprüft …" to "Testing connection …","APKMirror erneut testen" to "Test APKMirror again","APKMirror-Webseite öffnen" to "Open APKMirror website",
  "Downloads und Installation" to "Downloads and installation","APK-Ordner auswählen" to "Choose APK folder","Standardordner verwenden" to "Use default folder","Eigener Ordner ausgewählt" to "Custom folder selected","APKs nach Installation behalten" to "Keep APKs after installation","Aus: erst nach bestätigtem Erfolg löschen. Bei Fehler oder Abbruch behalten." to "Off: delete after confirmed success only. Keep on failure or cancellation.","Installer" to "Installer","Android PackageInstaller · empfohlen" to "Android PackageInstaller · recommended","Gewählter Installer nicht verfügbar" to "Selected installer unavailable",
  "Updateverlauf" to "Installation history","Updateverlauf öffnen" to "Open installation history","BESTÄTIGTE INSTALLATIONEN" to "CONFIRMED INSTALLATIONS","Was wird gespeichert?" to "What is recorded?","Noch keine Updates im Verlauf" to "No installations recorded yet","Erfolgreiche Installationen erscheinen hier automatisch." to "Successful installations appear here automatically.","Verlauf löschen" to "Clear history","Verlauf löschen?" to "Clear history?","Alle gespeicherten Verlaufseinträge entfernen?" to "Remove all saved history entries?",
  "Ausgeblendete Versionen" to "Hidden versions","Versionen wieder einblenden" to "Show hidden versions again","Versionen wieder einblenden?" to "Show hidden versions again?","Region und Testprofil" to "Region and test profile","Automatisch" to "Automatic","Simuliertes Gerätemodell" to "Simulated device model","Testprofil anwenden" to "Apply test profile","Echtes Geräteprofil verwenden" to "Use real device profile","Manuelle Region verwenden?" to "Use manual region?","Als Vergleich speichern" to "Save for comparison","Gerätesimulation aktivieren?" to "Enable device simulation?","Testprofil speichern" to "Save test profile","Testprofil aktiv. Keine Aufhebung von Installationssperren; keine globale Geräteänderung." to "Test profile active. Installation blocks remain; no global device change.",
  "Projekt unterstützen" to "Support the project","Lizenzen und Rechtliches" to "Licenses and legal information","Lizenzen" to "Licenses","Frohe Feiertage" to "Happy holidays","Frohes neues Jahr" to "Happy New Year",
  "Apps entdecken" to "Discover apps","QUELLEN DURCHSUCHEN" to "SEARCH SOURCES","App- oder Paketname" to "App or package name","Suchen" to "Search","Herunterladen und prüfen" to "Download and check","Suche nicht möglich" to "Search unavailable","Suche nach einer App" to "Find an app","Android-App-Einstellungen" to "Android app settings","Updates prüfen" to "Check updates","☆ Als Favorit markieren" to "☆ Add favorite","★ Favorit entfernen" to "★ Remove favorite",
  "Sprache" to "Language","Systemsprache verwenden" to "Use system language","Deutsch" to "German","Englisch" to "English","Android-Spracheinstellungen öffnen" to "Open Android language settings",
  HelpText.CHECK to "Discovery and reading reports use metadata only unless automatic downloads are explicitly enabled with a selected destination. A batch compatibility check deletes its temporary files. Individual preparation downloads and checks the chosen APK before offering installation. Checks cover package name, version, signature, Android requirements, architecture, package components and detectable dependencies. Nothing is installed or executed. Missing evidence stays unknown; runtime functionality cannot be guaranteed.",
  HelpText.BLOCKED to "Blocked means at least one verified requirement does not match, such as signature, Android version, architecture or system branch. This app does not offer installation for that file.",
  HelpText.RESTORE to "Clears only the hidden-release list. Those versions can reappear in the next search. No installed app is reset, removed or downgraded. Ignored apps remain ignored.",
  HelpText.PROFILE to "Choose a product and then a separate region. Only documented model and device codes are sent on the next Google Play authentication, with a locale hint. Account/IP country and store entitlement still apply. Unsupported regional combinations are disabled. A manual region is a comparison hint; other sources may not support device substitution. Android and other apps keep the real identity. Installation checks always use the actual device. Manual values are not compatibility evidence.",
  HelpText.EXTERNAL to "Hands a previously checked APK to an installed app that can open APK files. Installation remains user-confirmed. External installers may not report progress or success; history is updated only after the installed version code is observed."
 )
 private val explanations=mapOf(
  "Android 13 und neuer verwaltet die App-Sprache. Systemsprache folgt der Android-Auswahl. Derzeit gibt es Deutsch und Englisch für die Oberfläche; einige Hilfs- und technische Meldungen bleiben in der Ausgangssprache. Nicht unterstützte Systemsprachen verwenden Englisch. Android übersetzt fehlende Sprachen nicht automatisch." to "Android 13+ manages the app language. System default follows Android. German and English interface labels are available; some help and technical messages retain their original language. Unsupported system languages fall back to English. Android does not automatically translate missing languages.",
  "Öffnet Farben, Glas, OLED-Modus, Schrift und Hintergrundanimationen. Die Updateprüfung wird dadurch nicht verändert." to "Opens colors, glass, OLED mode, typography and background animations. Update checks are unaffected.",
  "Öffnet die freiwillige Spendenseite auf PayPal. Die Spende ist freiwillig; die App löst keine Zahlung aus." to "Opens the optional donation page on PayPal. Donations are optional; the app does not initiate a payment.",
  "Alle umfasst sämtliche sichtbaren Apps. Allgemeine Apps sind selbst installierte Apps. System umfasst alle Systempakete. Google-System-Apps ist eine Teilmenge nach Paketname und Android-Systemkennzeichnung." to "All includes every visible app. User apps are installed by the user. System includes every system package. Google system apps are a subset identified by package namespace and Android system flags.",
  "Prüft direkt verfügbare APKs nacheinander, ohne Installation. Standard: bis zu 12 Apps, 128 MiB je APK, 768 MiB Gesamtbudget pro Durchlauf. Unbekannte Dateigrößen reservieren das volle Einzellimit. Das schützt Speicher, Netz und Akku auch auf schnellen Geräten." to "Inspects direct APKs sequentially without installing. Default: up to 12 apps, 128 MiB per file, 768 MiB per run. Unknown sizes reserve the full per-file allowance. Limits protect storage, data and battery even on fast devices.",
  "Android führt geplante Arbeit aus, wenn Netz- und Systembedingungen passen. Eine exakte Uhrzeit ist nicht garantiert." to "Android runs scheduled work when network and system conditions permit. Exact timing is not guaranteed.",
  "Alle Quellen sind beim ersten Start dieser Version eingeschaltet. Du kannst sie einzeln deaktivieren. Herkunft und Reihenfolge ersetzen keine APK-Prüfung." to "All sources are enabled on the first launch of this version. You can disable them individually. Source identity and ordering do not replace APK checks.",
  "DNS bedeutet, dass die Internetadresse nicht aufgelöst wurde. Das kann an Verbindung, privatem DNS, VPN oder einem Filter liegen. Ein HTTP-Fehler bedeutet, dass ein Server geantwortet hat. Beides ist kein Beleg für eine unpassende APK." to "DNS means the address could not be resolved. Connection, private DNS, VPN or filtering may be responsible. An HTTP error means a server responded. Neither establishes APK incompatibility.",
  "Manuell geladene APKs werden öffentlich abgelegt. Die Installation verwendet weiterhin die privat geprüften Bytes. Automatische temporäre Prüfdateien bleiben privat." to "Manual downloads are stored publicly. Installation still uses the privately verified bytes. Automatic temporary inspection files remain private.",
  "Wähle einen vorhandenen oder lege im Android-Dialog einen neuen Unterordner an. Android 11+ erlaubt nicht jeden Stammordner. Bereits gespeicherte APKs werden nicht verschoben." to "Choose an existing folder or create a subfolder in Android's picker. Android 11+ restricts some root folders. Existing files are not moved.",
  "Neue Kopien verwenden wieder den öffentlichen Standardordner. Bereits abgelegte Dateien werden nicht verschoben." to "New copies use the public default folder again. Existing files are not moved.",
  "Zeigt nur bestätigte, über diese App gestartete Installationen. Externe Übergaben werden anhand des Paketstands bestätigt." to "Shows confirmed installations initiated through this app. External handoffs are checked against the installed package version.",
  "Nur über diese App gestartete und bestätigte Updates. Externe Übergaben werden beim nächsten Start anhand des installierten Versionscodes bestätigt. Keine vollständige Systemhistorie." to "Only installations initiated through this app are recorded. External handoffs are checked against the installed version code on the next launch. This is not a complete system history.",
  "Löscht nur diese Liste. Installierte Apps und APK-Dateien bleiben erhalten." to "Clears this list only. Installed apps and APK files remain unchanged.",
  "Öffnet die Open-Source-Lizenz und vorgeschriebene Urheberangaben." to "Opens the open-source license and required copyright notices.",
  "Standard ist ausgewogen. Hoch kostet mehr Grafikleistung; Aus nutzt feste Flächen." to "Standard is balanced. High requires more graphics work; Off uses solid surfaces.",
  "Niedrig zeigt mehr Hintergrund. Höher verbessert die Lesbarkeit. Standard: 46 %." to "Lower values reveal more background. Higher values improve readability. Default: 46%.",
  "Es werden verfügbare Systemschriften verwendet. Die Textgröße wird mit Androids Schriftgröße kombiniert." to "Uses available system fonts. Text scaling combines with the Android font-size setting.",
  "100 % entspricht der Android-Schriftgröße ohne zusätzlichen Faktor." to "100% uses the Android font size without an additional scale factor.",
  "Wähle Farbton und Sättigung im Kreis oder gib den RGB-HEX-Code ein. Deckkraft mischt die Farbe mit der App-Fläche; Android zeigt nicht andere Apps hinter dem Fenster." to "Choose hue and saturation on the wheel or enter an RGB HEX code. Opacity blends the color with the app surface; other apps are not visible behind this window.",
  "Markiert die App in dieser Anwendung. Unter Favoriten findest du sie schneller. Prüfung und installierte App werden dadurch nicht verändert." to "Marks an app here for quick access under Favorites. Inspection and the installed package are unchanged.",
  "Zeigt nur Apps, die du mit dem Stern markiert hast." to "Shows only apps you have starred.",
  "Aus deaktiviert die Updateabfrage für dieses Paket. Die installierte App wird nicht deaktiviert oder deinstalliert." to "Off excludes this package from update queries. It does not disable or uninstall the app.",
  "Entfernt nur diese Versionsbezeichnung dieser App aus den Suchergebnissen." to "Removes only this version label for this app from search results.",
  "Die App wird von Android verwaltet." to "Android manages this app.",
  "Technisch geprüft. System-Apps können dennoch von ROM-Funktionen und Herstellerdiensten abhängen." to "Technically checked. System apps may still depend on ROM features and vendor services.",
  "Technisch geprüft. Laufzeitfehler und verstecktes Verhalten lassen sich dadurch nicht ausschließen." to "Technically checked. Runtime failures and hidden behavior cannot be ruled out.",
  "APK oder wichtige Anforderungen sind noch nicht vollständig belegt. Keine Installationsfreigabe." to "The APK or important requirements are not fully verified. Installation is not approved.",
  "Mindestens eine technische Voraussetzung passt nicht. Installation gesperrt." to "At least one technical requirement does not match. Installation is blocked.",
  "Technisch geprüft, aber eine Vorabversion. Fehler und Datenverlust sind eher möglich; Freigabe ist keine Funktionsgarantie." to "Technically checked, but a prerelease. Failures and data loss may be more likely; approval is not a runtime guarantee."
 )
 private val current=mapOf(
  "APK-Analyse" to "APK analysis",
  "APK-Prüfung abbrechen" to "Cancel APK check",
  "APKMirror-API erneut abfragen" to "Retry APKMirror API",
  "APKMirror-Webverbindung testen" to "Test APKMirror website connection",
  "APKs gesammelt herunterladen und prüfen" to "Download and check APKs in a batch",
  "Aktive Regionsübersicht" to "Current region overview",
  "Ausführlich" to "Detailed",
  "Ausgeblendete Versionsfunde wieder in die Updateprüfung aufnehmen." to "Include hidden versions in update discovery again.",
  "Bestätigen" to "Confirm",
  "Cache" to "Cache",
  "Der Protokollspeicher konnte zuletzt nicht beschrieben werden." to "The diagnostic store could not be written to, or its queue was full.",
  "Diagnoseprotokoll speichern" to "Save diagnostic log",
  "Diagnoseprotokolle löschen" to "Clear diagnostic logs",
  "Die Prüfung bestätigt technische Voraussetzungen, keine vollständige Funktionsgarantie." to "The checks establish technical requirements; they do not guarantee that the app will work.",
  "Die Vergleichsregion weicht von der Geräte-ROM ab. Die Installation prüft weiterhin die echte ROM." to "The comparison region differs from the device ROM. Installation still checks the actual ROM.",
  "Entwicklerdiagnose" to "Developer diagnostics",
  "Erfasst auch Hintergrundprüfungen. Gespeicherte Protokolle bleiben nach Neustarts erhalten. Bei beendeter App läuft keine dauerhafte Aufzeichnung." to "Includes background checks. Saved logs survive app restarts. No continuous recording takes place while the app is stopped.",
  "Keine automatischen APK-Downloads. Downloads erst nach deiner ausdrücklichen Aktion." to "No automatic APK downloads. A download requires your explicit action.",
  "Kurzprotokoll exportieren" to "Export short log",
  "Manuelle Prüfdownloads" to "Explicit check downloads",
  "Prüfverfahren" to "How checks work",
  "Quellen teilweise nicht erreichbar" to "Some sources are unavailable",
  "Startet nur nach dieser Aktion einen begrenzten Sammeldownload. Öffnen von Prüfinformationen lädt keine APK." to "Only this action starts a bounded batch download. Opening check details does not download an APK.",
  "Technische Risiken" to "Technical risks",
  "Updateverlauf löschen" to "Clear installation history",
  "Vollständiges Protokoll exportieren" to "Export full log",
  "Darstellung und Sprache" to "Appearance and language",
  "Gerät und ROM" to "Device and ROM",
  "Quellen und Verbindung" to "Sources and connection",
  "Verlauf und Speicher" to "History and storage",
  "Diagnose und Entwicklung" to "Diagnostics and development",
  "Projekt" to "Project",
  "Testgerät auswählen" to "Choose test device",
  "Sprache auswählen" to "Choose language",
  "Kein Fehler gemeldet" to "No reported errors",
  "1 Nachweis offen" to "1 check remains unresolved",
  "1 technischer Konflikt" to "1 technical conflict",
  "Keine festgestellten technischen Konflikte" to "No identified technical conflicts",
  "Geräte-ROM:" to "Device ROM:",
  "Wirksame Prüfregion:" to "Effective check region:",
  "Vergleichsregion:" to "Comparison region:",
  "Standard" to "Standard",
  "Aus" to "Off",
  "Cache leeren" to "Clear cache",
  "Protokolle konnten nicht gelöscht werden. Bitte erneut versuchen." to "The logs could not be cleared. Please try again.",
  "Diagnoseprotokoll exportiert." to "Diagnostic log exported.",
  "Export fehlgeschlagen. Bitte einen anderen Speicherort wählen." to "Export failed. Please select another destination.",
  "Universal APK Upgrader steht unter GNU GPLv3. Enthält Code aus APKUpdater, Copyright rumboalla und Mitwirkende; Änderungen von UniAPK contributors. Vollständige Lizenz und passender Quellcode liegen dem Downloadpaket bei. Android-Roboter: von Google bereitgestelltes Werk gemäß CC BY 3.0; Liquid-Glass-Darstellung angepasst." to "Universal APK Upgrader is licensed under GNU GPLv3. Includes APKUpdater code, copyright rumboalla and contributors, with modifications by UniAPK contributors. The full license and corresponding source are included in the download. Android robot artwork is provided by Google under CC BY 3.0 and adapted for the glass design."
 )
 fun translate(raw:String,language:String):String {
  if(language!="en")return raw
  (current[raw] ?: exact[raw] ?: explanations[raw])?.let{return it}
  if(raw.contains("\n\n"))return raw.split("\n\n").joinToString("\n\n"){translate(it,language)}
  var out=raw
  val replacements=listOf("Glastransparenz:" to "Glass transparency:","Effektqualität:" to "Effect quality:","Glasdeckkraft:" to "Glass opacity:","Geschwindigkeit:" to "Speed:","Effektdichte:" to "Effect density:","Zusätzliche Textgröße:" to "Additional text size:","Deckkraft:" to "Opacity:","Apps pro Durchlauf:" to "Apps per run:","Einzellimit:" to "Per-file limit:","Gesamtbudget:" to "Total budget:","Manuelle Region:" to "Manual region:","Risiko:" to "Risk:","Versionscode:" to "Version code:","Letzte Suche" to "Last search","Updates bereit." to "updates ready.","Architektur noch offen" to "Architecture unknown","Quelle" to "Source","Quellen" to "Sources","Ungeklärt" to "Uncertain","Erhöht" to "Elevated","Restrisiko" to "Residual risk")
  for((de,en) in replacements.sortedByDescending{it.first.length})out=out.replace(de,en)
  return out
 }
}
