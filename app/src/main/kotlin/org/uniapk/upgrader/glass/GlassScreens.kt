package org.uniapk.upgrader.glass

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.ui.component.LoadingImageApp
import org.uniapk.upgrader.viewmodel.UpdatesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun safeStatusColor(positive: Boolean): Color = if(MaterialTheme.colorScheme.background.luminance() < .2f) {
    if(positive) Color(0xff78dfb2) else Color(0xffffd38b)
} else { if(positive) Color(0xff146948) else Color(0xff865500) }
val ScreenPadding: PaddingValues @Composable get() = PaddingValues(start = LocalDeviceLayout.current.horizontalPadding, end = LocalDeviceLayout.current.horizontalPadding, top = 8.dp, bottom = 120.dp)
@Composable
fun ScreenHeader(title: String, subtitle: String, onSettings: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        if(onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
        Column(Modifier.weight(1f)) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        if(onSettings != null) IconButton(onClick = onSettings, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(.06f))) { Icon(Icons.Default.Settings, "Einstellungen") }
    }
}
@Composable
fun SecondaryText(text: String, modifier: Modifier = Modifier) = Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
@Composable
fun StateMessage(title: String, message: String, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Info) {
    GlassPanel {
        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(spacing(12)))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp)); SecondaryText(message)
    }
}
@Composable
fun OverviewScreen(viewModel: UpdatesViewModel, onUpdates: () -> Unit = {}, onSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val seasonalEnabled=LocalAppearance.current.seasonal
    val prefs = koinInject<Prefs>()
    val detection = remember { DeviceDetection(context, prefs) }
    val rom by produceState<RomResult?>(null) { value = withContext(Dispatchers.IO) { detection.rom() } }
    val state = viewModel.state().collectAsStateWithLifecycle().value
    val selection = koinInject<AppSelection>()
    val scopeFilter by selection.scope.collectAsStateWithLifecycle()
    val releaseFilters by koinInject<ReleaseFilterStore>().state.collectAsStateWithLifecycle()
    val all = state.updates().filter { CategoryPolicy.matches(scopeFilter, it.system, it.packageName) && ReleasePolicy.allowed(it.version,releaseFilters.alpha,releaseFilters.beta,releaseFilters.preview,it.prerelease) }
    val health = SourceHealth.flow.collectAsStateWithLifecycle().value
    val scanProgress by ScanProgress.state.collectAsStateWithLifecycle()
    val working = state is UpdatesUiState.Loading || health.any { it.running } || scanProgress.active
    val readyGroups=all.appGroups().filter{group->group.candidates.any{it.fileReady&&it.verdict.installAllowed}}
    val ready=readyGroups.size
    val handler=LocalUriHandler.current
    val pending = all.appGroups().count { it.status == Compatibility.UNKNOWN }
    val blocked = all.appGroups().count { it.status == Compatibility.INCOMPATIBLE }
    var report by remember { mutableStateOf<String?>(null) };var inspecting by remember{mutableStateOf(false)};var showReport by remember{mutableStateOf(false)}
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            inspecting=true;showReport=true
            try{report = withContext(Dispatchers.IO) { AppCache.use {
                val f = File.createTempFile("glass-local-", ".apk", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input)
                        f.outputStream().use { out ->
                            val buffer = ByteArray(65536); var size = 0L
                            while(true) { val n = input.read(buffer); if(n < 0) break; size += n; require(size <= 512L * 1024 * 1024) { "Die Datei ist größer als 512 MiB." }; out.write(buffer, 0, n) }
                        }
                    }
                    ApkSetInspector(context, detection).inspect(f).text()
                }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e: Exception) { "Prüfung nicht möglich: ${e.message}" } finally { f.delete() }
            } }}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){report="Prüfung nicht möglich: ${e.message}"}finally{inspecting=false}
        }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(spacing(16))) {
        item { ScreenHeader("Übersicht", "UNIVERSAL APK UPGRADER", onSettings) }
        if(scanProgress.active) item { ScanProgressCard() }
        if(seasonalEnabled) item {val d=java.util.Calendar.getInstance();val greeting=MotionPolicy.greeting(d.get(java.util.Calendar.MONTH)+1,d.get(java.util.Calendar.DAY_OF_MONTH));if(greeting.isNotBlank())SecondaryText(greeting)}
        item { Row(horizontalArrangement=Arrangement.spacedBy(spacing(8))) {
            Metric("Bereit",ready,safeStatusColor(true),Modifier.weight(1f)){viewModel.selectCategory(0);onUpdates()}
            Metric("Funde",pending,safeStatusColor(false),Modifier.weight(1f)){viewModel.selectCategory(1);onUpdates()}
            Metric("Blockiert",blocked,MaterialTheme.colorScheme.error,Modifier.weight(1f)){viewModel.selectCategory(2);onUpdates()}
        } }
        if(readyGroups.isNotEmpty()){
            item{Text("Bereit zur Installation ($ready)",style=MaterialTheme.typography.titleMedium)}
            items(readyGroups,key={"ready-${it.packageName}"}){group->val u=group.candidates.first{it.fileReady&&it.verdict.installAllowed};GlassPanel(compact=true){Row(verticalAlignment=Alignment.CenterVertically){LoadingImageApp(u.packageName,Modifier.size(40.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(u.name,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis);SecondaryText(u.version)};Button(onClick={viewModel.install(u,handler)}){Text("Installieren")}}}}
        }
        item { GlassPanel(compact=true) {
            Text(if(ready>0)"$ready Updates vorbereitet" else if(pending>0)"$pending neue Versionsfunde" else "Deine Updates im Blick",style=MaterialTheme.typography.titleLarge)
            SecondaryText("Suche vergleicht Versionen. APK vorbereiten lädt die gewählte Datei und prüft sie vor der Installation.")
            Button(onClick={viewModel.refresh()},enabled=!working,modifier=Modifier.fillMaxWidth()){Text(if(working)"Suche läuft …" else "Nach Updates suchen")}
            SecondaryText(if(prefs.lastCheck.get()==0L)"Noch keine vollständige Suche" else "Letzte vollständige Suche: ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(prefs.lastCheck.get()))}")
            val since=prefs.quietSince.get()
            if(since>0L&&all.isEmpty()&&!working){val days=((System.currentTimeMillis()-since)/86400000L).coerceAtLeast(0);SecondaryText(if(days>0)"Seit $days Tagen bei deinen Suchen keine neueren Versionen gefunden." else "Bei der letzten vollständigen Suche keine neueren Versionen gefunden.")}
            SecondaryText("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · ${rom?.label ?: "ROM wird erkannt …"}")
        } }
        if(health.any { it.problem != null }) item { StateMessage("Quellen teilweise nicht erreichbar", health.filter { it.problem != null }.joinToString("\n") { "${it.name}: ${it.problem}" }) }
        if(inspecting)item{GlassPanel{Text("APK wird untersucht");UniProgress(label="Lokale APK wird untersucht")}}
        item { Row(verticalAlignment=Alignment.CenterVertically){Text("WERKZEUGE",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);HelpButton("Lokale APK-Prüfung",HelpText.CHECK+" Die Dateiauswahl analysiert nur deine Datei; es findet keine Installation statt.")} }
        item { GlassPanel(Modifier.clickable { if(!inspecting)picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip", "application/x-zip-compressed")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("APK untersuchen", style = MaterialTheme.typography.titleSmall); SecondaryText("Datei prüfen, ohne sie zu installieren") }; Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
            }
        } }
        item { var expanded by remember { mutableStateOf(false) }; GlassPanel(Modifier.clickable { expanded = !expanded }) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Automatische Geräteerkennung", style = MaterialTheme.typography.titleSmall); SecondaryText(rom?.label ?: "Wird ermittelt …") }; Icon(if(expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null) }
            if(expanded) { Spacer(Modifier.height(spacing(12))); SecondaryText(rom?.detail.orEmpty()); SecondaryText("${Build.SUPPORTED_ABIS.joinToString()} · API ${Build.VERSION.SDK_INT}\nDie erkannte Geräte-ROM bestätigt keine Herkunft einer fremden APK.") }
        } }
    }
    if(showReport)AlertDialog(onDismissRequest={showReport=false},title={Text(if(inspecting)"APK wird untersucht" else "APK-Analyse")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())){if(inspecting){UniProgress(label="Lokale APK wird untersucht");SecondaryText("Datei lesen und technische Voraussetzungen prüfen. Es wird nichts installiert.")}else Text(report.orEmpty(),style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={showReport=false}){Text(if(inspecting)"Im Hintergrund fortsetzen" else "Schließen")}})

}
@Composable
private fun Metric(label: String, count: Int, color: Color, modifier: Modifier, click: () -> Unit) {
    GlassPanel(modifier.clickable(onClick = click), compact = true) { Column(Modifier.padding(6.dp)) { Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color); Text(label, style = MaterialTheme.typography.labelSmall) } }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassUpdatesScreen(viewModel: UpdatesViewModel, onSettings: () -> Unit = {}) {
    val state = viewModel.state().collectAsStateWithLifecycle().value
    val selection = koinInject<AppSelection>()
    val scopeFilter by selection.scope.collectAsStateWithLifecycle()
    val releaseFilters by koinInject<ReleaseFilterStore>().state.collectAsStateWithLifecycle()
    val all = state.updates().filter { CategoryPolicy.matches(scopeFilter, it.system, it.packageName) && ReleasePolicy.allowed(it.version,releaseFilters.alpha,releaseFilters.beta,releaseFilters.preview,it.prerelease) }
    val health = SourceHealth.flow.collectAsStateWithLifecycle().value
    val scanProgress by ScanProgress.state.collectAsStateWithLifecycle()
    val working = state is UpdatesUiState.Loading || health.any { it.running } || scanProgress.active
    val deletingPackages by viewModel.deletingPackages.collectAsStateWithLifecycle()
    val requestedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selected=if(requestedCategory>=0)requestedCategory else if(all.none{it.verdict.installAllowed}&&all.any{it.verdict.status==Compatibility.UNKNOWN})1 else 0
    var query by remember { mutableStateOf("") }
    val handler = LocalUriHandler.current
    val ignoredStore=koinInject<IgnoredAppStore>()
    val ignored by ignoredStore.state.collectAsStateWithLifecycle()
    var ignoreCandidate by remember{mutableStateOf<AppUpdate?>(null)}
    if(selected==3){IgnoredAppsScreen{viewModel.selectCategory(1)};return}
    val status = listOf(Compatibility.COMPATIBLE, Compatibility.UNKNOWN, Compatibility.INCOMPATIBLE)[selected.coerceIn(0,2)]
    val groups = all.appGroups()
    val favoriteStore=koinInject<FavoriteStore>()
    val favorites by favoriteStore.state.collectAsStateWithLifecycle()
    var onlyFavorites by remember{mutableStateOf(false)}
    val visible = groups.filter { (!onlyFavorites || it.packageName in favorites) && it.status == status && (it.candidates.any { c -> c.name.contains(query, true) } || it.packageName.contains(query, true)) }.sortedBy { it.best().name.lowercase() }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(spacing(12))) {
        item { ScreenHeader("Updates", "SUCHE, PRÜFUNG UND INSTALLATION", onSettings) }
        if(scanProgress.active) item { ScanProgressCard() }
        item { ScopeChoices(scopeFilter, selection::select) }
        item { FilterChip(onlyFavorites,{onlyFavorites=!onlyFavorites},label={Text("Nur Favoriten")});HelpButton("Favoritenfilter","Zeigt nur Apps, die du mit dem Stern markiert hast.") }
        item { HelpTitle("Was bedeuten die Bereiche?","Funde: neuere Versionsangaben aus den Quellen. Bereit: die gewählte APK wurde geladen und technisch geprüft. Blockiert: mindestens eine konkrete Voraussetzung passt nicht. "+HelpText.BLOCKED+" "+HelpText.CHECK) }

        item { FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing(8))) {
            listOf("Bereit", "Funde", "Blockiert", "Ignorierte Apps").forEachIndexed { i, label ->
                val category = Compatibility.entries.let { listOf(Compatibility.COMPATIBLE, Compatibility.UNKNOWN, Compatibility.INCOMPATIBLE)[i.coerceAtMost(2)] }
                FilterChip(selected == i, { viewModel.selectCategory(i) }, label = { Text("$label ${if(i==3)ignored.size else groups.count { (!onlyFavorites || it.packageName in favorites) && it.status == category }}", style = MaterialTheme.typography.labelSmall) })
            }
        } }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Updates durchsuchen") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = CircleShape, singleLine = true) }
        if(all.any{it.isInstalling}) item {TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={viewModel.cancelVerification()}){Text("APK-Prüfung abbrechen")};HelpButton("Prüfung abbrechen","Stoppt die manuell gestartete Einzel- oder Sammelprüfung nach Freigabe des laufenden Netzwerkaufrufs. Es wird nichts installiert. Bereits gespeicherte öffentliche APK-Kopien bleiben erhalten.")}

        if(health.any { it.problem != null }) item { StateMessage("Quellenfehler", health.filter { it.problem != null }.joinToString("\n") { "${it.name}: ${it.problem}" }) }
        if(state is UpdatesUiState.Error) item { StateMessage("Suche fehlgeschlagen", "Bitte Verbindung und aktivierte Quellen prüfen.") }
        if(visible.isEmpty() && !working) item { StateMessage(when(selected) { 0 -> "Keine vorbereiteten APKs"; 1 -> "Keine neuen Funde"; else -> "Keine blockierten Varianten" }, when(selected) { 0 -> "Nur tatsächlich geprüfte APKs erscheinen hier. Weitere Funde findest du unter Funde."; 1 -> "Starte eine neue Suche oder prüfe die Quellen in den Einstellungen."; else -> "Ältere Systemzweige und nachweislich unpassende APKs erscheinen separat in diesem Bereich." }) }
        if(selected == 1 && visible.isNotEmpty()) item { OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { viewModel.verifyAll() }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Funde vorübergehend prüfen") };HelpButton("Alle APKs prüfen",HelpText.CHECK+" Pro App wird eine unterstützte Quelle verwendet. Bereits geprüfte und blockierte Dateien werden übersprungen. Es wird nacheinander geprüft; Webquellen bleiben manuell."); SecondaryText("Temporäre Prüfdownloads werden anschließend gelöscht. Für eine Installation die gewünschte APK einzeln vorbereiten.") }
        items(visible, key = { it.packageName }) { group -> UpdateGroupCard(group, viewModel.preferred(group.packageName), { viewModel.verify(it) }, { viewModel.install(it, handler) }, viewModel::ignoreRelease,viewModel::prefer,discard={viewModel.discardPrepared(it)},deleting=group.packageName in deletingPackages,ignoreApp={ignoreCandidate=it}) }
    }
    ignoreCandidate?.let{u->AlertDialog(onDismissRequest={ignoreCandidate=null},title={Text("${u.name} ignorieren?")},text={Text("Schließt alle Versionen dieser App von Updateabfragen, Prüfungen und automatischen Downloads aus. Die App bleibt installiert. Unter Ignorierte Apps kannst du sie jederzeit wieder zulassen.")},confirmButton={TextButton(onClick={ignoredStore.set(u.packageName,true);ignoreCandidate=null}){Text("App ignorieren")}},dismissButton={TextButton(onClick={ignoreCandidate=null}){Text("Abbrechen")}})}
}
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ScopeChoices(selected: AppScope, choose: (AppScope) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing(6))) {
        AppScope.entries.forEach { value -> FilterChip(selected == value, { choose(value) }, label = { Text(if(value == AppScope.GENERAL) "Allgemeine" else value.label, style = MaterialTheme.typography.labelSmall) }) }
    }
}

