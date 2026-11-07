package com.apkupdater.glass
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.apkupdater.BuildConfig
import com.apkupdater.prefs.Prefs
import com.apkupdater.worker.UpdatesWorker
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable fun GlassSettingsScreen(onBack:()->Unit,onAppearance:()->Unit,onFiltersChanged:()->Unit={},onHistory:()->Unit={}) {
 val languages=koinInject<AppLanguageStore>()
 val prefs=koinInject<Prefs>();val selection=koinInject<AppSelection>();val selected by selection.scope.collectAsState();val filters=koinInject<ReleaseFilterStore>();val health by SourceHealth.flow.collectAsState()
 val context=LocalContext.current;val uri=LocalUriHandler.current;val scope=rememberCoroutineScope()
 var folderText by remember{mutableStateOf(if(prefs.downloadTree.get().isBlank())"Download/Liquid-APK-Store" else "Eigener Ordner ausgewählt")}
 var diagnostic by remember{mutableStateOf<String?>(null)};var testing by remember{mutableStateOf(false)}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
 val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){u->if(u!=null)runCatching{context.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);prefs.downloadTree.put(u.toString());folderText="Eigener Ordner ausgewählt"}.onFailure{folderText="Ordnerzugriff fehlgeschlagen. Bitte einen anderen Ordner wählen."}}
 var region by remember{mutableStateOf(prefs.manualRegion.get())};var model by remember{mutableStateOf(prefs.simulatedModel.get())}
 var confirmRegion by remember{mutableStateOf<String?>(null)};var confirmModel by remember{mutableStateOf(false)};var restore by remember{mutableStateOf(false)}
 var legal by remember{mutableStateOf(false)}
 var installer by remember{mutableStateOf(prefs.installerPackage.get())};var installerMenu by remember{mutableStateOf(false)};var confirmInstaller by remember{mutableStateOf<Pair<String,String>?>(null)}
 val choices=remember(installerMenu){InstallerChoices.available(context)}
 Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=20.dp)) {
  ScreenHeader("Einstellungen","LIQUID-APK-STORE",onBack=onBack)
  LazyColumn(Modifier.weight(1f).navigationBarsPadding(),contentPadding=PaddingValues(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
   item{GlassPanel(Modifier.clickable(onClick=onAppearance)){HelpTitle("Erscheinungsbild","Öffnet Farben, Glas, OLED-Modus, Schrift und Hintergrundanimationen. Die Updateprüfung wird dadurch nicht verändert.");TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick=onAppearance){Text("Design anpassen")}}}
   item{GlassPanel{
    HelpTitle("Sprache","Android 13 und neuer verwaltet die App-Sprache. Systemsprache folgt der Android-Auswahl. Derzeit gibt es Deutsch und Englisch für die Oberfläche; einige Hilfs- und technische Meldungen bleiben in der Ausgangssprache. Nicht unterstützte Systemsprachen verwenden Englisch. Android übersetzt fehlende Sprachen nicht automatisch.")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("SYSTEM" to "Systemsprache verwenden","de" to "Deutsch","en" to "Englisch").forEach{(tag,label)->TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={languages.set(context,tag)}){Text(label)}}}
    if(android.os.Build.VERSION.SDK_INT>=33)OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={languages.openSettings(context)}){Text("Android-Spracheinstellungen öffnen")}
   }}
   item{Text("Updateprüfung",style=MaterialTheme.typography.titleMedium)}
   item{GlassPanel{
    HelpTitle("App-Auswahl","Alle umfasst sämtliche sichtbaren Apps. Allgemeine Apps sind selbst installierte Apps. System umfasst alle Systempakete. Google-System-Apps ist eine Teilmenge nach Paketname und Android-Systemkennzeichnung.")
    ScopeChoices(selected,selection::select)
    SettingSwitch("Beta-Versionen ausblenden","Erkennbare Beta-Versionen nicht anbieten.",{prefs.ignoreBeta.get()},{filters.change(beta=it);onFiltersChanged()})
    SettingSwitch("Alpha-Versionen ausblenden","Erkennbare Alpha-Versionen nicht anbieten.",{prefs.ignoreAlpha.get()},{filters.change(alpha=it);onFiltersChanged()})
    SettingSwitch("Alle Vorabversionen ausblenden","Auch Beta, Alpha, RC, Preview und markierte Testversionen.",{prefs.ignorePreRelease.get()},{filters.change(preview=it);onFiltersChanged()})
    HelpTitle("Wie werden APKs geprüft?",HelpText.CHECK)
   }}
   item{GlassPanel{
    HelpTitle("Automatische APK-Prüfung","Prüft direkt verfügbare APKs nacheinander, ohne Installation. Standard: bis zu 12 Apps, 128 MiB je APK, 768 MiB Gesamtbudget pro Durchlauf. Unbekannte Dateigrößen reservieren das volle Einzellimit. Das schützt Speicher, Netz und Akku auch auf schnellen Geräten.")
    SettingSwitch("Automatische Prüfung aktiv","Nach der Suche geeignete APKs laden und prüfen.",{prefs.autoVerify.get()},{prefs.autoVerify.put(it)})
    SettingSwitch("Automatische APK-Prüfung nur ungetaktet","Schont mobiles Datenvolumen.",{prefs.autoVerifyWifiOnly.get()},{prefs.autoVerifyWifiOnly.put(it)})
    var count by remember{mutableIntStateOf(prefs.autoCheckCount.get())};var size by remember{mutableIntStateOf(prefs.autoVerifyLimitMiB.get())};var budget by remember{mutableIntStateOf(prefs.autoCheckBudgetMiB.get())}
    ValueSlider("Apps pro Durchlauf: $count",count.toFloat(),12f,{count=it.toInt();prefs.autoCheckCount.put(count)},1f..24f,22,"Anzahl automatisch geprüfter Apps. 12 ist ein ausgewogener Ausgangswert; höhere Werte benötigen mehr Daten und Speicher.")
    HelpTitle("Einzellimit: $size MiB","Eine Datei darf dieses Downloadlimit nicht überschreiten. Größere APKs können einzeln manuell geprüft werden.")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(64,128,256).forEach{v->FilterChip(size==v,{size=v;prefs.autoVerifyLimitMiB.put(v)},label={Text("$v MiB")})}}
    HelpTitle("Gesamtbudget: $budget MiB","Obergrenze geplanter automatischer Downloads pro Durchlauf; bekannte Größen werden addiert. Fehlende Größen reservieren das Einzellimit.")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(256,768,1536).forEach{v->FilterChip(budget==v,{budget=v;prefs.autoCheckBudgetMiB.put(v)},label={Text("$v MiB")})}}
   }}
   item{GlassPanel{
    HelpTitle("Hintergrundsuche","Android führt geplante Arbeit aus, wenn Netz- und Systembedingungen passen. Eine exakte Uhrzeit ist nicht garantiert.")
    SettingSwitch("Täglich nach Updates suchen","Keine automatische Installation.",{prefs.enableAlarm.get()},{prefs.enableAlarm.put(it);val wm=WorkManager.getInstance(context);if(it){UpdatesWorker.launch(wm);if(android.os.Build.VERSION.SDK_INT>=33)permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)}else UpdatesWorker.cancel(wm)})
    SettingSwitch("Hintergrundsuche nur ungetaktet","Sucht nur ohne getaktete Verbindung.",{prefs.wifiOnly.get()},{prefs.wifiOnly.put(it);if(prefs.enableAlarm.get())UpdatesWorker.launch(WorkManager.getInstance(context))})
   }}
   item{GlassPanel{
    HelpTitle("Updatequellen","Alle Quellen sind beim ersten Start dieser Version eingeschaltet. Du kannst sie einzeln deaktivieren. Herkunft und Reihenfolge ersetzen keine APK-Prüfung.")
    SettingSwitch("APKMirror","API-Abfrage und manuelle Webseite; keine Downloadfreigabe.",{prefs.useApkMirror.get()},{prefs.useApkMirror.put(it);onFiltersChanged()})
    SettingSwitch("GitHub","Zugeordnete Entwickler-Releases.",{prefs.useGitHub.get()},{prefs.useGitHub.put(it);onFiltersChanged()})
    SettingSwitch("GitLab","Zugeordnete Entwickler-Releases.",{prefs.useGitLab.get()},{prefs.useGitLab.put(it);onFiltersChanged()})
    SettingSwitch("F-Droid","Open-Source-Katalog; Signatur kann abweichen.",{prefs.useFdroid.get()},{prefs.useFdroid.put(it);onFiltersChanged()})
    SettingSwitch("IzzyOnDroid","Open-Source-Repository.",{prefs.useIzzy.get()},{prefs.useIzzy.put(it);onFiltersChanged()})
    SettingSwitch("APKPure","Anbieterkatalog; jede Datei separat prüfen.",{prefs.useApkPure.get()},{prefs.useApkPure.put(it);onFiltersChanged()})
    SettingSwitch("Aptoide","Anbieterkatalog; jede Datei separat prüfen.",{prefs.useAptoide.get()},{prefs.useAptoide.put(it);onFiltersChanged()})
    SettingSwitch("Google Play","Vorhandene experimentelle Integration; Verfügbarkeit abhängig vom Dienst.",{prefs.usePlay.get()},{prefs.usePlay.put(it);onFiltersChanged()})
   }}
   item{GlassPanel{
    HelpTitle("Quellenstatus", "DNS bedeutet, dass die Internetadresse nicht aufgelöst wurde. Das kann an Verbindung, privatem DNS, VPN oder einem Filter liegen. Ein HTTP-Fehler bedeutet, dass ein Server geantwortet hat. Beides ist kein Beleg für eine unpassende APK.")
    health.forEach{s->Text(s.name,style=MaterialTheme.typography.labelLarge);SecondaryText(if(s.running)"Wird abgefragt …" else s.problem ?: "Kein Fehler gemeldet");s.problem?.let{SecondaryText(NetworkDiagnostics.hints(it))}}
    Row{OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={testing=true;scope.launch{diagnostic=NetworkDiagnostics.test(context);testing=false}},enabled=!testing){Text(if(testing)"Verbindung wird geprüft …" else "APKMirror erneut testen")};HelpButton("Verbindungstest","Prüft DNS und HTTPS von APKMirror. Eine erreichbare Webseite bestätigt nicht automatisch den API-Zugriff.")}
    diagnostic?.let{SecondaryText(it)}
    Row{TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={uri.openUri("https://www.apkmirror.com/")}){Text("APKMirror-Webseite öffnen")};HelpButton("Web-Fallback","Öffnet den Browser. Eine dort geladene Datei lässt sich über den lokalen APK-Analyzer prüfen. Zugriffssperren werden nicht umgangen.")}
   }}
   item{GlassPanel{
    HelpTitle("Downloads und Installation","Manuell geladene APKs werden öffentlich abgelegt. Die Installation verwendet weiterhin die privat geprüften Bytes. Automatische temporäre Prüfdateien bleiben privat.")
    SecondaryText(folderText)
    Row{OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={folder.launch(null)}){Text("APK-Ordner auswählen")};HelpButton("APK-Ordner auswählen","Wähle einen vorhandenen oder lege im Android-Dialog einen neuen Unterordner an. Android 11+ erlaubt nicht jeden Stammordner. Bereits gespeicherte APKs werden nicht verschoben.")}
    Row{TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={prefs.downloadTree.put("");folderText="Download/Liquid-APK-Store"}){Text("Standardordner verwenden")};HelpButton("Standardordner","Neue Kopien verwenden wieder den öffentlichen Standardordner. Bereits abgelegte Dateien werden nicht verschoben.")}
    SettingSwitch("APKs nach Installation behalten","Aus: erst nach bestätigtem Erfolg löschen. Bei Fehler oder Abbruch behalten.",{prefs.keepDownloadedApks.get()},{prefs.keepDownloadedApks.put(it)})
    HelpTitle("Installer",HelpText.EXTERNAL)
    OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={installerMenu=true}){Text(if(installer=="SYSTEM")"Android PackageInstaller" else choices.find{it.first==installer}?.second ?: "Gewählter Installer nicht verfügbar")}
    DropdownMenu(installerMenu,{installerMenu=false}){
     DropdownMenuItem(text={Text("Android PackageInstaller · empfohlen")},onClick={installer="SYSTEM";prefs.installerPackage.put(installer);installerMenu=false})
     choices.forEach{c->DropdownMenuItem(text={Text(c.second)},onClick={installerMenu=false;confirmInstaller=c})}
    }
   }}
   item{GlassPanel{
    HelpTitle("Updateverlauf","Zeigt nur bestätigte, über diese App gestartete Installationen. Externe Übergaben werden anhand des Paketstands bestätigt.")
    OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick=onHistory){Text("Updateverlauf öffnen")}
    HelpTitle("Ausgeblendete Versionen",HelpText.RESTORE)
    OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={restore=true}){Text("Versionen wieder einblenden")}
   }}
   item{GlassPanel{
    HelpTitle("Region und Testprofil",HelpText.PROFILE)
    SecondaryText("Manuelle Region: $region")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("AUTO","EU","GLO","IN","CN","OTHER").forEach{v->FilterChip(region==v,{if(v=="AUTO"){region=v;prefs.manualRegion.put(v)}else confirmRegion=v},label={Text(if(v=="AUTO")"Automatisch" else v)})}}
    OutlinedTextField(model,{model=it.take(80)},label={Text("Simuliertes Gerätemodell")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    Row{OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={confirmModel=true}){Text("Testprofil anwenden")};HelpButton("Gerätesimulation",HelpText.PROFILE)}
    TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={model="";region="AUTO";prefs.simulatedModel.put("");prefs.manualRegion.put("AUTO");prefs.playAuthData.put(com.aurora.gplayapi.data.models.AuthData("",""))}){Text("Echtes Geräteprofil verwenden")}
    if(ProfilePolicy.simulationActive(prefs))SecondaryText("Testprofil aktiv. Keine Aufhebung von Installationssperren; keine globale Geräteänderung.")
   }}
   item{GlassPanel{Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={uri.openUri("https://www.paypal.me/cem7dzemo")},modifier=Modifier.weight(1f)){Text("☕ Like my project? Buy me a coffee :)")};HelpButton("Projekt unterstützen","Öffnet Cem auf PayPal. Die Spende ist freiwillig; die App löst keine Zahlung aus.")}}}
   item{SecondaryText("Liquid-APK-Store ${BuildConfig.VERSION_NAME}");Row{TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={legal=true}){Text("Lizenzen und Rechtliches")};HelpButton("Lizenzen","Öffnet die Open-Source-Lizenz und vorgeschriebene Urheberangaben.")}}
  }
 }
 if(legal)AlertDialog(onDismissRequest={legal=false},title={Text("Lizenzen und Rechtliches")},text={Text("Liquid-APK-Store steht unter GNU GPLv3. Enthält Code aus APKUpdater, Copyright rumboalla und Mitwirkende; Änderungen von Hyvan187. Die vollständige Lizenz und der passende Quellcode liegen dem Downloadpaket bei. Android-Roboter: aus einem von Google bereitgestellten Werk abgeleitet, verwendet gemäß CC BY 3.0. Änderungen: Liquid-Glass-Darstellung.")},confirmButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={legal=false}){Text("Schließen")}})
 if(restore)AlertDialog(onDismissRequest={restore=false},title={Text("Versionen wieder einblenden?")},text={Text(HelpText.RESTORE)},confirmButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={prefs.ignoredVersions.put(emptyList());prefs.ignoredReleases.put(emptyList());restore=false;onFiltersChanged()}){Text("Wieder einblenden")}},dismissButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={restore=false}){Text("Abbrechen")}})
 confirmRegion?.let{v->AlertDialog(onDismissRequest={confirmRegion=null},title={Text("Manuelle Region verwenden?")},text={Text(HelpText.PROFILE)},confirmButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={region=v;prefs.manualRegion.put(v);confirmRegion=null}){Text("Als Vergleich speichern")}},dismissButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={confirmRegion=null}){Text("Abbrechen")}})}
 if(confirmModel)AlertDialog(onDismissRequest={confirmModel=false},title={Text("Gerätesimulation aktivieren?")},text={Text(HelpText.PROFILE)},confirmButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={prefs.simulatedModel.put(model.trim());prefs.playAuthData.put(com.aurora.gplayapi.data.models.AuthData("",""));confirmModel=false}){Text("Testprofil speichern")}},dismissButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={confirmModel=false}){Text("Abbrechen")}})
 confirmInstaller?.let{c->AlertDialog(onDismissRequest={confirmInstaller=null},title={Text("${c.second} verwenden?")},text={Text(HelpText.EXTERNAL)},confirmButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={installer=c.first;prefs.installerPackage.put(installer);confirmInstaller=null}){Text("Installer wählen")}},dismissButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={confirmInstaller=null}){Text("Abbrechen")}})}
}
@Composable private fun SettingSwitch(title:String,description:String,get:()->Boolean,set:(Boolean)->Unit){var checked by remember(title){mutableStateOf(get())};Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyMedium);SecondaryText(description)};Switch(checked,{checked=it;set(it)});HelpButton(title,if(title=="Google Play")description+" Für die vorhandene Play-Integration wird der Aurora-Anmeldedienst verwendet. Geräteprofil-Daten werden für den Katalogzugriff übertragen; ein Testmodell wird nur nach deiner Auswahl gesendet." else description)}}
