package org.uniapk.upgrader.glass

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.uniapk.upgrader.BuildConfig
import org.uniapk.upgrader.diagnostics.AppDiagnostics
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.worker.UpdatesWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun GlassSettingsDetailScreen(section:Int,onBack:()->Unit,onAppearance:()->Unit,onFiltersChanged:()->Unit={},onHistory:()->Unit={},onCacheCleared:()->Unit={},onPermissions:()->Unit={}) {
    val prefs=koinInject<Prefs>();val languages=koinInject<AppLanguageStore>();val history=koinInject<HistoryStore>()
    val selection=koinInject<AppSelection>();val selected by selection.scope.collectAsState()
    val filters=koinInject<ReleaseFilterStore>();val health by SourceHealth.flow.collectAsState();val entries by history.state.collectAsState()
    val language by languages.state.collectAsState();val context=LocalContext.current;val uri=LocalUriHandler.current;val scope=rememberCoroutineScope();val layout=LocalDeviceLayout.current
    var languageMenu by remember { mutableStateOf(false) };var presetMenu by remember { mutableStateOf(false) };var installerMenu by remember { mutableStateOf(false) }
    var region by remember { mutableStateOf(prefs.manualRegion.get()) };var model by remember { mutableStateOf(prefs.simulatedModel.get()) };var profileMessage by remember { mutableStateOf("") }
    var activateAfterFolder by remember{mutableStateOf(false)};var automatic by remember{mutableStateOf(prefs.autoDownloadUpdates.get())}
    var folderStatus by remember{mutableStateOf<FolderStatus?>(null)};var folderMessage by remember{mutableStateOf("")};var folderBusy by remember{mutableStateOf(false)}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle;var folderRevision by remember{mutableIntStateOf(0)}
    DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)folderRevision++};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
    LaunchedEffect(folderRevision){folderStatus=withContext(Dispatchers.IO){FolderAccess.status(context,prefs)}}
    var installer by remember { mutableStateOf(prefs.installerPackage.get()) };var confirmInstaller by remember { mutableStateOf<Pair<String,String>?>(null) }
    var legal by remember { mutableStateOf(false) };var restore by remember { mutableStateOf(false) };var clearHistory by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) };var diagnostic by remember { mutableStateOf("") };var clearing by remember { mutableStateOf(false) };var cacheMessage by remember { mutableStateOf("") }
    val detection=remember { DeviceDetection(context,prefs) }
    val rom by produceState<RomResult?>(null) { value=withContext(Dispatchers.IO){detection.rom()} }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { selectedUri ->
        if(selectedUri==null&&activateAfterFolder){activateAfterFolder=false;automatic=false;prefs.autoDownloadUpdates.put(false)}
        if(selectedUri!=null)scope.launch{folderBusy=true;try{folderStatus=withContext(Dispatchers.IO){FolderAccess.select(context,prefs,selectedUri)};folderMessage="Ordner freigegeben.";if(activateAfterFolder){automatic=folderStatus?.writable==true;prefs.autoDownloadUpdates.put(automatic)}}catch(e:Exception){folderMessage=e.message ?: "Ordnerzugriff fehlgeschlagen.";if(activateAfterFolder){automatic=false;prefs.autoDownloadUpdates.put(false)};AppDiagnostics.error("downloads.folder_failed",error=e)}finally{activateAfterFolder=false;folderBusy=false;folderRevision++}}

    }
    val choices=remember(installerMenu){InstallerChoices.allAvailable(context)}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
        Column(Modifier.widthIn(max=layout.contentMaxWidth).fillMaxSize().statusBarsPadding().padding(horizontal=layout.horizontalPadding)) {
            ScreenHeader(listOf("Berechtigungen","Sprache und Anzeige","Updateprüfung","Gerät und ROM","Quellen und Verbindung","Downloads und Installer","Verlauf und Speicher","Diagnose","Über UniAPK")[section],"EINSTELLUNGEN",onBack=onBack)
            LazyColumn(Modifier.weight(1f).navigationBarsPadding(),contentPadding=PaddingValues(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(spacing(14))) {
                if(section==0){
                item {GlassPanel{Text("Berechtigungen",style=MaterialTheme.typography.titleSmall);SecondaryText("Installationsfreigabe, Benachrichtigungen und APK-Ordner prüfen.");OutlinedButton(onClick=onPermissions,modifier=Modifier.fillMaxWidth()){Text("Berechtigungen verwalten")}}}
                }
                if(section==1){
                item { SettingsSection("Design und Sprache") }
                item { GlassPanel {
                    Row(Modifier.fillMaxWidth().clickable(onClick=onAppearance),verticalAlignment=Alignment.CenterVertically) {
                        Text("Design und Leistung",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        TextButton(onClick=onAppearance){Text("Design anpassen")}
                    }
                    Text("Sprache",style=MaterialTheme.typography.titleSmall)
                    val tag=languages.current(context)
                    OutlinedButton(onClick={languageMenu=true},modifier=Modifier.fillMaxWidth()) { Text(when(tag){"de"->"Deutsch";"en"->"Englisch";else->"Systemsprache verwenden"}) }
                    SecondaryText("Display automatisch: ${layout.dpi} dpi · ${layout.widthDp} × ${layout.heightDp} dp")
                } }
                }
                if(section==2){
                item { SettingsSection("Updateprüfung") }
                item { GlassPanel {
                    HelpTitle("App-Auswahl","Alle umfasst sichtbare Apps. Allgemeine Apps sind selbst installiert; System umfasst Systempakete. Google-System-Apps ist die entsprechende Teilmenge.")
                    ScopeChoices(selected,selection::select)
                    SettingSwitch("Beta-Versionen ausblenden","Erkennbare Beta-Versionen nicht anbieten.",{prefs.ignoreBeta.get()},{filters.change(beta=it);onFiltersChanged()})
                    SettingSwitch("Alpha-Versionen ausblenden","Erkennbare Alpha-Versionen nicht anbieten.",{prefs.ignoreAlpha.get()},{filters.change(alpha=it);onFiltersChanged()})
                    SettingSwitch("Alle Vorabversionen ausblenden","Auch Beta, Alpha, RC, Preview und markierte Testversionen.",{prefs.ignorePreRelease.get()},{filters.change(preview=it);onFiltersChanged()})
                    HelpTitle("Wie werden APKs geprüft?",HelpText.CHECK)
                } }
                item { GlassPanel {
                    HelpTitle("Download- und Prüflimits","Metadatensuche lädt keine APKs, außer automatische Downloads sind bewusst eingeschaltet. Eine reine Sammelprüfung lädt Prüfdateien vorübergehend und löscht sie anschließend. Einzelne Updates werden über ein Prozessfenster vorbereitet. Das Downloadbudget beträgt höchstens 20 % des freien Speichers. Zusätzlich werden mindestens 1 GiB Reserve und mehrere vorübergehende Installationskopien berücksichtigt.",showHelp=true)
                    SecondaryText("Standard: 10 Apps, bis 512 MiB je Paketset, 2 GiB pro Durchlauf. Die tatsächliche Grenze berücksichtigt freien Speicher; unbekannte Größen reservieren das Einzellimit.")
                    var count by remember{mutableIntStateOf(prefs.autoCheckCount.get())};var size by remember{mutableIntStateOf(prefs.autoVerifyLimitMiB.get())};var budget by remember{mutableIntStateOf(prefs.autoCheckBudgetMiB.get())}
                    ValueSlider("Apps pro Durchlauf: $count",count.toFloat(),10f,{count=it.toInt();prefs.autoCheckCount.put(count)},1f..24f,22,"Maximale Anzahl bei einem bewusst gestarteten Sammeldownload.")
                    HelpTitle("Einzellimit: $size MiB","Maximale Größe pro bewusst gestarteter APK-Prüfung beziehungsweise pro Play-Paketset.")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(spacing(6))){listOf(128,256,512).forEach{v->FilterChip(size==v,{size=v;prefs.autoVerifyLimitMiB.put(v)},label={Text("$v MiB")})}}
                    HelpTitle("Gesamtbudget: $budget MiB","Obergrenze je Durchlauf. Bei unbekannter Größe wird das volle Einzellimit reserviert.")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(spacing(6))){listOf(512,1024,2048).forEach{v->FilterChip(budget==v,{budget=v;prefs.autoCheckBudgetMiB.put(v)},label={Text("$v MiB")})}}
                } }
                item { GlassPanel {
                    HelpTitle("Hintergrundsuche","Android führt geplante Prüfungen bei passenden Netz- und Systembedingungen aus; die Uhrzeit ist nicht exakt garantiert.")
                    SettingSwitch("Täglich nach Updates suchen","Keine automatische Installation.",{prefs.enableAlarm.get()},{prefs.enableAlarm.put(it);val wm=WorkManager.getInstance(context);if(it){UpdatesWorker.launch(wm);if(Build.VERSION.SDK_INT>=33)permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)}else UpdatesWorker.cancel(wm)})
                    SettingSwitch("Hintergrundsuche nur ungetaktet","Startet geplante Suchen nur über Verbindungen, die Android ohne Datenlimit meldet, meist WLAN. Mobilfunk mit Datenlimit und als getaktet markiertes WLAN werden übersprungen. Es wird dabei kein Datenvolumen selbst gemessen.",{prefs.wifiOnly.get()},{prefs.wifiOnly.put(it);if(prefs.enableAlarm.get())UpdatesWorker.launch(WorkManager.getInstance(context))})
                } }
                }
                if(section==3){
                item { SettingsSection("Gerät und ROM") }
                item { DeviceProfileSettings() }
                }
                if(section==4){
                item { SettingsSection("Quellen und Verbindung") }
                item { GlassPanel {
                    Text("Updatequellen",style=MaterialTheme.typography.titleSmall)
                    SettingSwitch("APKMirror","API-Abfrage und manuelle Webseite; keine Downloadfreigabe.",{prefs.useApkMirror.get()},{prefs.useApkMirror.put(it);onFiltersChanged()})
                    SettingSwitch("GitHub","Zugeordnete Entwickler-Releases.",{prefs.useGitHub.get()},{prefs.useGitHub.put(it);onFiltersChanged()})
                    SettingSwitch("GitLab","Zugeordnete Entwickler-Releases.",{prefs.useGitLab.get()},{prefs.useGitLab.put(it);onFiltersChanged()})
                    SettingSwitch("F-Droid","Open-Source-Katalog; Signatur kann abweichen.",{prefs.useFdroid.get()},{prefs.useFdroid.put(it);onFiltersChanged()})
                    SettingSwitch("IzzyOnDroid","Open-Source-Repository.",{prefs.useIzzy.get()},{prefs.useIzzy.put(it);onFiltersChanged()})
                    SettingSwitch("APKPure","Anbieterkatalog; jede Datei separat prüfen.",{prefs.useApkPure.get()},{prefs.useApkPure.put(it);onFiltersChanged()})
                    SettingSwitch("Aptoide","Anbieterkatalog; jede Datei separat prüfen.",{prefs.useAptoide.get()},{prefs.useAptoide.put(it);onFiltersChanged()})
                    SettingSwitch("Google Play","Liefert je nach App eine Basis-APK mit zusätzlichen Split-Paketen.",{prefs.usePlay.get()},{prefs.usePlay.put(it);onFiltersChanged()})
                } }
                item { GlassPanel {
                    HelpTitle("Quellenstatus","DNS: Anbieteradresse konnte nicht aufgelöst werden. HTTP: Ein Server hat geantwortet. Ein Netzwerkfehler ist kein Nachweis für eine unpassende APK.")
                    health.forEach{entry->Text(entry.name,style=MaterialTheme.typography.labelLarge);SecondaryText(if(entry.running)"Wird abgefragt …" else if(entry.webOnly)"Webmodus: Link zur jeweiligen App; keine automatische API-Abfrage ohne Schlüssel." else entry.problem ?: "Kein Fehler gemeldet");entry.problem?.let{SecondaryText(NetworkDiagnostics.hints(it))}}
                    OutlinedButton(onClick={testing=true;scope.launch{try{diagnostic=NetworkDiagnostics.test(context)}finally{testing=false}}},enabled=!testing){Text(if(testing)"Verbindung wird geprüft …" else "APKMirror-Webverbindung testen")}
                    OutlinedButton(onClick={ProviderCooldown.retry("ApkMirror");onFiltersChanged()}){Text("APKMirror-API erneut abfragen")}
                    if(testing)UniProgress(label="Webverbindung wird getestet")
                    if(diagnostic.isNotBlank())SecondaryText(diagnostic)
                    TextButton(onClick={runCatching{uri.openUri("https://www.apkmirror.com/")}}){Text("APKMirror-Webseite öffnen")}
                } }
                item { SourceApiSettings(onFiltersChanged) }
                }
                if(section==5){
                item { SettingsSection("Downloads und Installation") }
                item { GlassPanel {
                    val currentFolder=folderStatus
                    StatusLine(if(currentFolder?.writable==true)StatusTone.SUCCESS else StatusTone.WARNING,"APK-Ordner",currentFolder?.headline ?: "Ordner wird geprüft")
                    currentFolder?.let{SecondaryText(it.location)}
                    if(folderMessage.isNotBlank())SecondaryText(folderMessage)
                    if(folderBusy)UniProgress(label="Ordnerfreigabe wird geprüft")
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("APKs bei der Suche automatisch laden");SecondaryText("Standardmäßig aus. Lädt höchstens eine passende Variante pro App, prüft sie und speichert sie im ausdrücklich gewählten Zielordner. Keine automatische Installation.")};Switch(automatic,{enabled->
                        if(!enabled){automatic=false;prefs.autoDownloadUpdates.put(false)}else if(folderStatus?.writable==true){automatic=true;prefs.autoDownloadUpdates.put(true)}else{activateAfterFolder=true;folderMessage="Für automatische Downloads zuerst einen Ordner freigeben.";folder.launch(null)}
                    })}
                    SettingSwitch("Automatische Downloads nur ohne Datenlimit","Wartet bei einer von Android als getaktet erkannten Verbindung, zum Beispiel vielen Mobilfunktarifen. Meist ist WLAN erlaubt; als getaktet markiertes WLAN wird ebenfalls ausgeschlossen.",{prefs.autoVerifyWifiOnly.get()},{prefs.autoVerifyWifiOnly.put(it)})
                    Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick={folder.launch(prefs.downloadTree.get().takeIf{it.isNotBlank()}?.let(android.net.Uri::parse))},enabled=!folderBusy,modifier=Modifier.weight(1f)){Text(if(folderStatus?.selected==true)"APK-Ordner ändern" else "APK-Ordner auswählen")};HelpButton("APK-Ordner auswählen","Android kann Stammordner sperren. Wähle einen Unterordner; bereits gespeicherte APKs werden nicht verschoben.")}
                    TextButton(onClick={prefs.downloadTree.put("");prefs.autoDownloadUpdates.put(false);automatic=false;folderRevision++;folderMessage=if(Build.VERSION.SDK_INT>=29)"Manuelle Downloads verwenden wieder Downloads / UniAPK. Automatische Downloads sind aus." else "Vor dem Speichern einen Ordner auswählen. Automatische Downloads sind aus."}){Text(if(Build.VERSION.SDK_INT>=29)"Standardordner verwenden" else "Ordnerfreigabe zurücksetzen")}
                    SettingSwitch("APKs nach Installation behalten","Aus: erst nach bestätigtem Erfolg löschen. Bei Fehler oder Abbruch behalten.",{prefs.keepDownloadedApks.get()},{prefs.keepDownloadedApks.put(it)})
                    SettingSwitch("Play-Herkunft berücksichtigen","Bei bisher über Play installierten Apps zuerst Play oder einen anderen Installer wählen. Eine Lizenzprüfung anderer Apps kann UniAPK nicht abschalten.",{prefs.preferPlay.get()},{prefs.preferPlay.put(it)})
                    HelpTitle("Installer",HelpText.EXTERNAL)
                    OutlinedButton(onClick={installerMenu=true},modifier=Modifier.fillMaxWidth()){Text(if(installer==InstallerChoices.ASK)"Bei jeder Installation fragen" else if(installer=="SYSTEM")"Android PackageInstaller" else choices.find{it.first==installer}?.second ?: "Gewählter Installer nicht verfügbar")}
                } }
                }
                if(section==6){
                item { SettingsSection("Verlauf und Speicher") }
                item { GlassPanel {
                    Text("Updateverlauf",style=MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick=onHistory){Text("Updateverlauf öffnen")}
                    TextButton(onClick={clearHistory=true},enabled=entries.isNotEmpty()){Text("Updateverlauf löschen")}
                    HelpTitle("Ausgeblendete Versionen",HelpText.RESTORE,showHelp=true)
                    SecondaryText("Ausgeblendete Versionsfunde wieder in die Updateprüfung aufnehmen.")
                    HiddenVersions(onFiltersChanged)
                } }
                item { GlassPanel {
                    HelpTitle("Cache", "Entfernt Netzwerk- und Bildcache sowie private APK-Prüfdateien. Einstellungen, Diagnoseprotokolle, öffentliche Downloads und ausstehende externe Installationen bleiben erhalten. Aktive Prüfungen und Installationen müssen zuerst abgeschlossen sein.",showHelp=true)
                    val http=koinInject<okhttp3.Cache>()
                    val cacheBytes by produceState(0L){while(true){value=withContext(Dispatchers.IO){AppCache.bytes(context)};kotlinx.coroutines.delay(2000)}}
                    SecondaryText("Aktuell freigebbarer Cache: ${ResourcePolicy.label(cacheBytes)}")
                    OutlinedButton(onClick={clearing=true;scope.launch {try{val bytes=AppCache.clear(context,http);onCacheCleared();cacheMessage="Cache geleert · zuvor mindestens ${bytes/1024} KiB."}catch(e:Exception){cacheMessage=e.message ?: "Cache konnte nicht geleert werden.";AppDiagnostics.error("cache.clear_failed",error=e)}finally{clearing=false}}},enabled=!clearing,modifier=Modifier.fillMaxWidth()){Text(if(clearing)"Cache wird geleert …" else "Cache leeren")}
                    if(clearing)UniProgress(label="Cache wird geleert")
                    if(cacheMessage.isNotBlank())SecondaryText(cacheMessage)
                } }
                }
                if(section==7){
                item { SettingsSection("Diagnose und Entwicklung") }
                item { DiagnosticSettingsCard() }
                }
                if(section==8){
                item { SettingsSection("Projekt") }
                item { GlassPanel {
                    OutlinedButton(onClick={runCatching{uri.openUri(SupportLink.uri())}.onFailure{AppDiagnostics.error("support.open_failed",error=it)}},modifier=Modifier.fillMaxWidth()){Text("☕ Like my project? Buy me a coffee :)")}
                    SecondaryText("Universal APK Upgrader ${BuildConfig.VERSION_NAME}")
                    TextButton(onClick={legal=true}){Text("Lizenzen und Rechtliches")}
                } }
                }
            }
        }
    }
    if(languageMenu)GlassChoiceSheet("Sprache auswählen",languages.current(context),listOf(GlassChoice("SYSTEM","Systemsprache verwenden"),GlassChoice("de","Deutsch"),GlassChoice("en","Englisch")),{languageMenu=false}){languages.set(context,it)}

    if(installerMenu)GlassChoiceSheet("Installer auswählen",installer,listOf(GlassChoice(InstallerChoices.ASK,"Bei jeder Installation fragen","Zeigt passende Installationsassistenten für die geprüfte Datei."),GlassChoice("SYSTEM","Android PackageInstaller · empfohlen","Unterstützt geprüfte Einzel-APKs und APK-Sets."))+choices.map{GlassChoice(it.first,it.second,"Übergabe passender geprüfter APKs oder APK-Sets.")},{installerMenu=false}){id->if(id in listOf("SYSTEM",InstallerChoices.ASK)){installer=id;prefs.installerPackage.put(id)}else choices.find{it.first==id}?.let{confirmInstaller=it}}
    if(restore)SettingsConfirm("Versionen wieder einblenden?",HelpText.RESTORE,{restore=false}){prefs.ignoredVersions.put(emptyList());prefs.ignoredReleases.put(emptyList());restore=false;onFiltersChanged()}
    if(clearHistory)SettingsConfirm("Updateverlauf löschen?","Entfernt die gespeicherten Verlaufseinträge. Installierte Apps und APK-Dateien bleiben erhalten.",{clearHistory=false}){history.clear();clearHistory=false;AppDiagnostics.info("history.cleared")}
    confirmInstaller?.let{choice->SettingsConfirm("${choice.second} verwenden?",HelpText.EXTERNAL,{confirmInstaller=null}){installer=choice.first;prefs.installerPackage.put(installer);confirmInstaller=null}}
    if(legal)AlertDialog(onDismissRequest={legal=false},title={Text("Lizenzen und Rechtliches")},text={Text("Universal APK Upgrader steht unter GNU GPLv3. Enthält Code aus APKUpdater, Copyright rumboalla und Mitwirkende; Änderungen von UniAPK contributors. Vollständige Lizenz und passender Quellcode liegen dem Downloadpaket bei. Android-Roboter: von Google bereitgestelltes Werk gemäß CC BY 3.0; Liquid-Glass-Darstellung angepasst.")},confirmButton={TextButton(onClick={legal=false}){Text("Schließen")}})
}

@Composable private fun SettingsSection(title:String) { Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=8.dp)) }
@Composable private fun SettingsConfirm(title:String,body:String,dismiss:()->Unit,confirm:()->Unit) { AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick=confirm){Text("Bestätigen")}},dismissButton={TextButton(onClick=dismiss){Text("Abbrechen")}}) }
@Composable private fun SettingSwitch(title:String,description:String,get:()->Boolean,set:(Boolean)->Unit) {
    var checked by remember(title){mutableStateOf(get())}
    Row(Modifier.fillMaxWidth().padding(vertical=spacing(8)),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyMedium);SecondaryText(description)}
        Switch(checked,{checked=it;set(it)})
        if(HelpTopics.technical(title))HelpButton(title,if(title=="Google Play")description+" Die Aurora-Anmeldung überträgt das Geräteprofil; ein Modell-Testwert gilt nur für diese Anmeldung." else description)
    }
}

@Composable fun GlassSettingsScreen(onBack:()->Unit,onAppearance:()->Unit,onFiltersChanged:()->Unit={},onHistory:()->Unit={},onCacheCleared:()->Unit={},onPermissions:()->Unit={}){
 var section by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf<Int?>(null)}
 androidx.activity.compose.BackHandler(enabled=section!=null){section=null}
 section?.let{chosen->GlassSettingsDetailScreen(chosen,{section=null},onAppearance,onFiltersChanged,onHistory,onCacheCleared,onPermissions);return}
 val prefs=koinInject<Prefs>();val context=LocalContext.current;val a=LocalAppearance.current
 var stable by remember{mutableStateOf(prefs.ignorePreRelease.get())};var background by remember{mutableStateOf(prefs.enableAlarm.get())}
 val rowHeight=if(a.compact)118.dp else 144.dp
 LazyColumn(Modifier.fillMaxSize().statusBarsPadding(),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(spacing(14))){
  item{SettingsHero("Einstellungen","Universal APK Upgrader ${BuildConfig.VERSION_NAME}",Icons.Default.Settings,onBack)}
  item{Text("Schnellzugriff",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)}
  item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
   FilterChip(stable,{stable=!stable;prefs.ignorePreRelease.put(stable);org.koin.core.context.GlobalContext.get().get<ReleaseFilterStore>().change(preview=stable);onFiltersChanged()},modifier=Modifier.weight(1f).heightIn(min=64.dp),label={Column{Text("Nur stabile Versionen");SecondaryText(if(stable)"Aktiv" else "Aus")}})
   FilterChip(background,{background=!background;prefs.enableAlarm.put(background);if(background)UpdatesWorker.launch(WorkManager.getInstance(context))else UpdatesWorker.cancel(WorkManager.getInstance(context))},modifier=Modifier.weight(1f).heightIn(min=64.dp),label={Column{Text("Hintergrundsuche");SecondaryText(if(background)"Aktiv" else "Aus")}})
  }}
  item{Text("Alle Einstellungen",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)}
  item{SettingsTile("Downloads und Installer","APK-Ordner, automatische Downloads und Installation",Color(0xff244570),UniIcons.Download,Modifier.fillMaxWidth().height(rowHeight)){section=5}}
  item{Row(Modifier.fillMaxWidth().height(rowHeight*2+12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
   SettingsTile("Design und Leistung","Design, Glas, Bewegung, Dock und Dichte",Color(0xff4a246b),UniIcons.Palette,Modifier.weight(1f).fillMaxHeight(),onAppearance)
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
    SettingsTile("Quellen","Anbieter und Verbindung",Color(0xff1f6278),UniIcons.Wifi,Modifier.weight(1f).fillMaxWidth()){section=4}
    SettingsTile("Speicher","Cache und Verlauf",Color(0xff70431e),UniIcons.Storage,Modifier.weight(1f).fillMaxWidth()){section=6}
   }
  }}
  item{SettingsTile("Updateprüfung","App-Auswahl, Versionsfilter und Prüflimits",Color(0xff286344),UniIcons.Security,Modifier.fillMaxWidth().height(rowHeight)){section=2}}
  item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
   SettingsTile("Gerät und ROM","Region und Testprofil",Color(0xff704064),UniIcons.PhoneAndroid,Modifier.weight(1f).height(rowHeight)){section=3}
   SettingsTile("Sprache","Sprache und Anzeige",Color(0xff246b68),UniIcons.Language,Modifier.weight(1f).height(rowHeight)){section=1}
  }}
  item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
   SettingsTile("Berechtigungen","Zugriffe verwalten",Color(0xff66551f),Icons.Default.Check,Modifier.weight(1f).height(rowHeight),onPermissions)
   SettingsTile("Diagnose","Protokolle und Fehler",Color(0xff414d76),Icons.Default.Info,Modifier.weight(1f).height(rowHeight)){section=7}
  }}
  item{SettingsTile("Über UniAPK","Version, Lizenzen und Projekt unterstützen",Color(0xff3d4050),Icons.Default.Favorite,Modifier.fillMaxWidth().height(rowHeight)){section=8}}
 }
}
