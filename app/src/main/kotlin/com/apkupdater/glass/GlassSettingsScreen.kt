package com.apkupdater.glass

import android.content.Intent
import android.os.Build
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
import com.apkupdater.diagnostics.AppDiagnostics
import com.apkupdater.prefs.Prefs
import com.apkupdater.worker.UpdatesWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable fun GlassSettingsScreen(onBack:()->Unit,onAppearance:()->Unit,onFiltersChanged:()->Unit={},onHistory:()->Unit={},onCacheCleared:()->Unit={}) {
    val appearanceStore=koinInject<AppearanceStore>();val appearance by appearanceStore.state.collectAsState()
    val prefs=koinInject<Prefs>();val languages=koinInject<AppLanguageStore>();val history=koinInject<HistoryStore>()
    val selection=koinInject<AppSelection>();val selected by selection.scope.collectAsState()
    val filters=koinInject<ReleaseFilterStore>();val health by SourceHealth.flow.collectAsState();val entries by history.state.collectAsState()
    val language by languages.state.collectAsState();val context=LocalContext.current;val uri=LocalUriHandler.current;val scope=rememberCoroutineScope();val layout=LocalDeviceLayout.current
    var languageMenu by remember { mutableStateOf(false) };var presetMenu by remember { mutableStateOf(false) };var installerMenu by remember { mutableStateOf(false) }
    var region by remember { mutableStateOf(prefs.manualRegion.get()) };var model by remember { mutableStateOf(prefs.simulatedModel.get()) };var profileMessage by remember { mutableStateOf("") }
    var folderText by remember { mutableStateOf(if(prefs.downloadTree.get().isBlank())"Download/Liquid-APK-Store" else "Eigener Ordner ausgewählt") }
    var installer by remember { mutableStateOf(prefs.installerPackage.get()) };var confirmInstaller by remember { mutableStateOf<Pair<String,String>?>(null) }
    var legal by remember { mutableStateOf(false) };var restore by remember { mutableStateOf(false) };var clearHistory by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) };var diagnostic by remember { mutableStateOf("") };var clearing by remember { mutableStateOf(false) };var cacheMessage by remember { mutableStateOf("") }
    val detection=remember { DeviceDetection(context,prefs) }
    val rom by produceState<RomResult?>(null) { value=withContext(Dispatchers.IO){detection.rom()} }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { selectedUri ->
        if(selectedUri!=null)runCatching { context.contentResolver.takePersistableUriPermission(selectedUri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);prefs.downloadTree.put(selectedUri.toString());folderText="Eigener Ordner ausgewählt" }
            .onFailure { folderText="Ordnerzugriff fehlgeschlagen. Bitte einen anderen Ordner wählen.";AppDiagnostics.error("downloads.folder_failed",error=it) }
    }
    val choices=remember(installerMenu){InstallerChoices.available(context)}
    fun useModel(value: String) {
        if(!TestDeviceProfiles.valid(value)){profileMessage="Modellangabe ist ungültig. Höchstens 80 Zeichen ohne Steuerzeichen verwenden.";return}
        prefs.simulatedModel.put(value.trim());prefs.playAuthData.put(com.aurora.gplayapi.data.models.AuthData("",""))
        model=value.trim();profileMessage=if(model.isBlank())"Echtes Geräteprofil aktiv." else "Testprofil gespeichert. Wird bei der nächsten Play-Anmeldung verwendet."
        AppDiagnostics.info("profile.changed",mapOf("simulation" to model.isNotBlank()))
    }
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
        Column(Modifier.widthIn(max=layout.contentMaxWidth).fillMaxSize().statusBarsPadding().padding(horizontal=layout.horizontalPadding)) {
            ScreenHeader("Einstellungen","LIQUID-APK-STORE",onBack=onBack)
            LazyColumn(Modifier.weight(1f).navigationBarsPadding(),contentPadding=PaddingValues(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item { SettingsSection("Darstellung und Sprache") }
                item { GlassPanel {
                    Row(Modifier.fillMaxWidth().clickable(onClick=onAppearance),verticalAlignment=Alignment.CenterVertically) {
                        Text("Erscheinungsbild",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        TextButton(onClick=onAppearance){Text("Design anpassen")}
                    }
                    GlassTransparencyControl(appearanceStore,appearance)
                    Text("Sprache",style=MaterialTheme.typography.titleSmall)
                    val tag=languages.current(context)
                    OutlinedButton(onClick={languageMenu=true},modifier=Modifier.fillMaxWidth()) { Text(when(tag){"de"->"Deutsch";"en"->"Englisch";else->"Systemsprache verwenden"}) }
                    SecondaryText("Display automatisch: ${layout.dpi} dpi · ${layout.widthDp} × ${layout.heightDp} dp")
                } }
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
                    HelpTitle("Manuelle Prüfdownloads","Suche, Hintergrundsuche und Prüfinformationen verwenden nur Metadaten. APK-Dateien werden ausschließlich nach einer ausdrücklich bezeichneten Downloadaktion geladen. Die Grenzwerte gelten für bewusste Einzel- und Sammeldownloads.",showHelp=true)
                    SecondaryText("Keine automatischen APK-Downloads. Downloads erst nach deiner ausdrücklichen Aktion.")
                    var count by remember{mutableIntStateOf(prefs.autoCheckCount.get())};var size by remember{mutableIntStateOf(prefs.autoVerifyLimitMiB.get())};var budget by remember{mutableIntStateOf(prefs.autoCheckBudgetMiB.get())}
                    ValueSlider("Apps pro Durchlauf: $count",count.toFloat(),12f,{count=it.toInt();prefs.autoCheckCount.put(count)},1f..24f,22,"Maximale Anzahl bei einem bewusst gestarteten Sammeldownload.")
                    HelpTitle("Einzellimit: $size MiB","Maximale Größe pro bewusst gestarteter APK-Prüfung beziehungsweise pro Play-Paketset.")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(64,128,256,512).forEach{v->FilterChip(size==v,{size=v;prefs.autoVerifyLimitMiB.put(v)},label={Text("$v MiB")})}}
                    HelpTitle("Gesamtbudget: $budget MiB","Obergrenze je Durchlauf. Bei unbekannter Größe wird das volle Einzellimit reserviert.")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(256,768,1536).forEach{v->FilterChip(budget==v,{budget=v;prefs.autoCheckBudgetMiB.put(v)},label={Text("$v MiB")})}}
                } }
                item { GlassPanel {
                    HelpTitle("Hintergrundsuche","Android führt geplante Prüfungen bei passenden Netz- und Systembedingungen aus; die Uhrzeit ist nicht exakt garantiert.")
                    SettingSwitch("Täglich nach Updates suchen","Keine automatische Installation.",{prefs.enableAlarm.get()},{prefs.enableAlarm.put(it);val wm=WorkManager.getInstance(context);if(it){UpdatesWorker.launch(wm);if(Build.VERSION.SDK_INT>=33)permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)}else UpdatesWorker.cancel(wm)})
                    SettingSwitch("Hintergrundsuche nur ungetaktet","Sucht nur ohne getaktete Verbindung.",{prefs.wifiOnly.get()},{prefs.wifiOnly.put(it);if(prefs.enableAlarm.get())UpdatesWorker.launch(WorkManager.getInstance(context))})
                } }
                item { SettingsSection("Gerät und ROM") }
                item { GlassPanel {
                    HelpTitle("Region und Testprofil",HelpText.PROFILE)
                    val overview=RegionOverview(rom?.region ?: Region.UNKNOWN,region)
                    Text("Aktive Regionsübersicht",style=MaterialTheme.typography.titleSmall)
                    SecondaryText("Geräte-ROM: ${rom?.label ?: "Wird ausgelesen …"}")
                    SecondaryText("Vergleichsregion: ${if(region=="AUTO")"Automatisch · ${overview.comparison}" else region}")
                    SecondaryText("Wirksame Prüfregion: ${overview.effective.name}")
                    if(overview.mismatch)SecondaryText("Die Vergleichsregion weicht von der Geräte-ROM ab. Die Installation prüft weiterhin die echte ROM.")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("AUTO","EU","GLO","IN","CN","OTHER").forEach{v->FilterChip(region==v,{region=v;prefs.manualRegion.put(v)},label={Text(if(v=="AUTO")"Automatisch" else v)})}}
                    val preset=TestDeviceProfiles.presets.find{it.model==model}
                    OutlinedButton(onClick={presetMenu=true},modifier=Modifier.fillMaxWidth()){Text(preset?.name ?: if(model.isBlank())"Testgerät auswählen" else "Eigene Modellangabe")}
                    OutlinedTextField(model,{model=it.take(80)},label={Text("Simuliertes Gerätemodell")},supportingText={Text("Modellnummer oder Testname, z. B. CPH2745")},isError=!TestDeviceProfiles.valid(model),modifier=Modifier.fillMaxWidth(),singleLine=true)
                    Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick={useModel(model)},modifier=Modifier.weight(1f)){Text("Testprofil anwenden")};HelpButton("Gerätesimulation",HelpText.PROFILE)}
                    TextButton(onClick={region="AUTO";prefs.manualRegion.put("AUTO");useModel("")}){Text("Echtes Geräteprofil verwenden")}
                    if(profileMessage.isNotBlank())SecondaryText(profileMessage)
                } }
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
                    health.forEach{entry->Text(entry.name,style=MaterialTheme.typography.labelLarge);SecondaryText(if(entry.running)"Wird abgefragt …" else entry.problem ?: "Kein Fehler gemeldet");entry.problem?.let{SecondaryText(NetworkDiagnostics.hints(it))}}
                    OutlinedButton(onClick={testing=true;scope.launch{try{diagnostic=NetworkDiagnostics.test(context)}finally{testing=false}}},enabled=!testing){Text(if(testing)"Verbindung wird geprüft …" else "APKMirror erneut testen")}
                    if(diagnostic.isNotBlank())SecondaryText(diagnostic)
                    TextButton(onClick={runCatching{uri.openUri("https://www.apkmirror.com/")}}){Text("APKMirror-Webseite öffnen")}
                } }
                item { SettingsSection("Downloads und Installation") }
                item { GlassPanel {
                    SecondaryText(folderText)
                    Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick={folder.launch(null)},modifier=Modifier.weight(1f)){Text("APK-Ordner auswählen")};HelpButton("APK-Ordner auswählen","Android kann Stammordner sperren. Wähle einen Unterordner; bereits gespeicherte APKs werden nicht verschoben.")}
                    TextButton(onClick={prefs.downloadTree.put("");folderText="Download/Liquid-APK-Store"}){Text("Standardordner verwenden")}
                    SettingSwitch("APKs nach Installation behalten","Aus: erst nach bestätigtem Erfolg löschen. Bei Fehler oder Abbruch behalten.",{prefs.keepDownloadedApks.get()},{prefs.keepDownloadedApks.put(it)})
                    HelpTitle("Installer",HelpText.EXTERNAL)
                    OutlinedButton(onClick={installerMenu=true},modifier=Modifier.fillMaxWidth()){Text(if(installer=="SYSTEM")"Android PackageInstaller" else choices.find{it.first==installer}?.second ?: "Gewählter Installer nicht verfügbar")}
                } }
                item { SettingsSection("Verlauf und Speicher") }
                item { GlassPanel {
                    Text("Updateverlauf",style=MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick=onHistory){Text("Updateverlauf öffnen")}
                    TextButton(onClick={clearHistory=true},enabled=entries.isNotEmpty()){Text("Updateverlauf löschen")}
                    HelpTitle("Ausgeblendete Versionen",HelpText.RESTORE,showHelp=true)
                    SecondaryText("Ausgeblendete Versionsfunde wieder in die Updateprüfung aufnehmen.")
                    OutlinedButton(onClick={restore=true}){Text("Versionen wieder einblenden")}
                } }
                item { GlassPanel {
                    HelpTitle("Cache", "Entfernt Netzwerk- und Bildcache sowie private APK-Prüfdateien. Einstellungen, Diagnoseprotokolle, öffentliche Downloads und ausstehende externe Installationen bleiben erhalten. Aktive Prüfungen und Installationen müssen zuerst abgeschlossen sein.",showHelp=true)
                    val http=koinInject<okhttp3.Cache>()
                    OutlinedButton(onClick={clearing=true;scope.launch {try{val bytes=AppCache.clear(context,http);onCacheCleared();cacheMessage="Cache geleert · zuvor mindestens ${bytes/1024} KiB."}catch(e:Exception){cacheMessage=e.message ?: "Cache konnte nicht geleert werden.";AppDiagnostics.error("cache.clear_failed",error=e)}finally{clearing=false}}},enabled=!clearing,modifier=Modifier.fillMaxWidth()){Text(if(clearing)"Cache wird geleert …" else "Cache leeren")}
                    if(cacheMessage.isNotBlank())SecondaryText(cacheMessage)
                } }
                item { SettingsSection("Diagnose und Entwicklung") }
                item { DiagnosticSettingsCard() }
                item { SettingsSection("Projekt") }
                item { GlassPanel {
                    OutlinedButton(onClick={runCatching{uri.openUri(SupportLink.uri())}.onFailure{AppDiagnostics.error("support.open_failed",error=it)}},modifier=Modifier.fillMaxWidth()){Text("☕ Like my project? Buy me a coffee :)")}
                    SecondaryText("Liquid-APK-Store ${BuildConfig.VERSION_NAME}")
                    TextButton(onClick={legal=true}){Text("Lizenzen und Rechtliches")}
                } }
            }
        }
    }
    if(languageMenu)GlassChoiceSheet("Sprache auswählen",languages.current(context),listOf(GlassChoice("SYSTEM","Systemsprache verwenden"),GlassChoice("de","Deutsch"),GlassChoice("en","Englisch")),{languageMenu=false}){languages.set(context,it)}
    if(presetMenu)GlassChoiceSheet("Testgerät auswählen",TestDeviceProfiles.presets.find{it.model==model}?.model ?: "ACTUAL",listOf(GlassChoice("ACTUAL","Echtes Gerät · ${Build.MODEL}"))+TestDeviceProfiles.presets.map{GlassChoice(it.model,it.name,it.note)}, {presetMenu=false}){model=if(it=="ACTUAL")"" else it}
    if(installerMenu)GlassChoiceSheet("Installer auswählen",installer,listOf(GlassChoice("SYSTEM","Android PackageInstaller · empfohlen","Unterstützt geprüfte Einzel-APKs und APK-Sets."))+choices.map{GlassChoice(it.first,it.second,"Externe Übergabe geprüfter Einzel-APKs.")},{installerMenu=false}){id->if(id=="SYSTEM"){installer=id;prefs.installerPackage.put(id)}else choices.find{it.first==id}?.let{confirmInstaller=it}}
    if(restore)SettingsConfirm("Versionen wieder einblenden?",HelpText.RESTORE,{restore=false}){prefs.ignoredVersions.put(emptyList());prefs.ignoredReleases.put(emptyList());restore=false;onFiltersChanged()}
    if(clearHistory)SettingsConfirm("Updateverlauf löschen?","Entfernt die gespeicherten Verlaufseinträge. Installierte Apps und APK-Dateien bleiben erhalten.",{clearHistory=false}){history.clear();clearHistory=false;AppDiagnostics.info("history.cleared")}
    confirmInstaller?.let{choice->SettingsConfirm("${choice.second} verwenden?",HelpText.EXTERNAL,{confirmInstaller=null}){installer=choice.first;prefs.installerPackage.put(installer);confirmInstaller=null}}
    if(legal)AlertDialog(onDismissRequest={legal=false},title={Text("Lizenzen und Rechtliches")},text={Text("Liquid-APK-Store steht unter GNU GPLv3. Enthält Code aus APKUpdater, Copyright rumboalla und Mitwirkende; Änderungen von Hyvan187. Vollständige Lizenz und passender Quellcode liegen dem Downloadpaket bei. Android-Roboter: von Google bereitgestelltes Werk gemäß CC BY 3.0; Liquid-Glass-Darstellung angepasst.")},confirmButton={TextButton(onClick={legal=false}){Text("Schließen")}})
}

@Composable private fun SettingsSection(title:String) { Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=8.dp)) }
@Composable private fun SettingsConfirm(title:String,body:String,dismiss:()->Unit,confirm:()->Unit) { AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick=confirm){Text("Bestätigen")}},dismissButton={TextButton(onClick=dismiss){Text("Abbrechen")}}) }
@Composable private fun SettingSwitch(title:String,description:String,get:()->Boolean,set:(Boolean)->Unit) {
    var checked by remember(title){mutableStateOf(get())}
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyMedium);SecondaryText(description)}
        Switch(checked,{checked=it;set(it)})
        if(HelpTopics.technical(title))HelpButton(title,if(title=="Google Play")description+" Die Aurora-Anmeldung überträgt das Geräteprofil; ein Modell-Testwert gilt nur für diese Anmeldung." else description)
    }
}
