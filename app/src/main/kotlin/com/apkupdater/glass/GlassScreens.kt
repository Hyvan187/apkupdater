package com.apkupdater.glass

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
import com.apkupdater.data.ui.*
import com.apkupdater.prefs.Prefs
import com.apkupdater.ui.component.LoadingImageApp
import com.apkupdater.viewmodel.UpdatesViewModel
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
        Spacer(Modifier.height(12.dp))
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
    val ready = all.appGroups().count { it.status == Compatibility.COMPATIBLE }
    val pending = all.appGroups().count { it.status == Compatibility.UNKNOWN }
    val blocked = all.appGroups().count { it.status == Compatibility.INCOMPATIBLE }
    var report by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            report = "Die Datei wird lokal geprüft …"
            report = withContext(Dispatchers.IO) { AppCache.use {
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
                } catch(e: Exception) { "Prüfung nicht möglich: ${e.message}" } finally { f.delete() }
            } }
        }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeader("Alles im Blick.", "LIQUID-APK-STORE", onSettings) }
        if(scanProgress.active) item { ScanProgressCard() }
        if(seasonalEnabled) item {val d=java.util.Calendar.getInstance();val greeting=MotionPolicy.greeting(d.get(java.util.Calendar.MONTH)+1,d.get(java.util.Calendar.DAY_OF_MONTH));if(greeting.isNotBlank())SecondaryText(greeting)}
        item { GlassPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(.13f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(12.dp))
                Column { Text("${Build.MANUFACTURER} ${Build.MODEL}", style = MaterialTheme.typography.titleSmall); SecondaryText("Android ${Build.VERSION.RELEASE} · ${rom?.label ?: "ROM wird erkannt …"}") }
            }
            Spacer(Modifier.height(22.dp))
            Text(if(ready > 0) "$ready Updates bereit." else "Sicher aktualisieren.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            SecondaryText(if(pending > 0) "$pending Funde benötigen eine APK-Prüfung. Ältere Systemzweige und ungeklärte Herstellerpakete werden nicht freigegeben." else "Neue Versionen werden mit deiner Installation und deinem Gerät abgeglichen.")
            Spacer(Modifier.height(20.dp))
            Button(onClick = { viewModel.refresh() }, modifier = Modifier.fillMaxWidth(), shape = CircleShape, enabled = !working) {
                if(working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(if(scanProgress.phase==ScanPhase.VERIFYING) "APKs werden geprüft …" else if(working) "Quellen werden geprüft …" else "Updates suchen", Modifier.padding(vertical = 5.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(if(prefs.lastCheck.get() == 0L) "Noch keine abgeschlossene Suche" else "Letzte Suche ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(prefs.lastCheck.get()))}", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Bereit", ready, safeStatusColor(true), Modifier.weight(1f), onUpdates)
            Metric("Prüfen", pending, safeStatusColor(false), Modifier.weight(1f), onUpdates)
            Metric("Blockiert", blocked, MaterialTheme.colorScheme.error, Modifier.weight(1f), onUpdates)
        } }
        if(health.any { it.problem != null }) item { StateMessage("Quellen teilweise nicht erreichbar", health.filter { it.problem != null }.joinToString("\n") { "${it.name}: ${it.problem}" }) }
        item { HelpButton("Lokale APK-Prüfung",HelpText.CHECK+" Die Dateiauswahl analysiert nur deine Datei; es findet keine Installation statt.");Text("WERKZEUGE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { GlassPanel(Modifier.clickable { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip", "application/x-zip-compressed")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("APK untersuchen", style = MaterialTheme.typography.titleSmall); SecondaryText("Datei prüfen, ohne sie zu installieren") }; Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
            }
        } }
        item { var expanded by remember { mutableStateOf(false) }; GlassPanel(Modifier.clickable { expanded = !expanded }) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Automatische Geräteerkennung", style = MaterialTheme.typography.titleSmall); SecondaryText(rom?.label ?: "Wird ermittelt …") }; Icon(if(expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null) }
            if(expanded) { Spacer(Modifier.height(12.dp)); SecondaryText(rom?.detail.orEmpty()); SecondaryText("${Build.SUPPORTED_ABIS.joinToString()} · API ${Build.VERSION.SDK_INT}\nDie erkannte Geräte-ROM bestätigt keine Herkunft einer fremden APK.") }
        } }
    }
    report?.let { body -> AlertDialog(onDismissRequest = { report = null }, title = { Text("APK-Analyse") }, text = { androidx.compose.foundation.rememberScrollState().let { scroll -> androidx.compose.foundation.layout.Box(Modifier.heightIn(max = 440.dp).verticalScroll(scroll)) { Text(body, style = MaterialTheme.typography.bodySmall) } } }, confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { report = null }) { Text("Schließen") } }) }
}
@Composable
private fun Metric(label: String, count: Int, color: Color, modifier: Modifier, click: () -> Unit) {
    GlassPanel(modifier.clickable(onClick = click), compact = true) { Column(Modifier.padding(6.dp)) { Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color); Text(label, style = MaterialTheme.typography.labelSmall) } }
}
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
    var selected by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val handler = LocalUriHandler.current
    val status = listOf(Compatibility.COMPATIBLE, Compatibility.UNKNOWN, Compatibility.INCOMPATIBLE)[selected]
    val groups = all.appGroups()
    val favoriteStore=koinInject<FavoriteStore>()
    val favorites by favoriteStore.state.collectAsStateWithLifecycle()
    var onlyFavorites by remember{mutableStateOf(false)}
    val visible = groups.filter { (!onlyFavorites || it.packageName in favorites) && it.status == status && (it.candidates.any { c -> c.name.contains(query, true) } || it.packageName.contains(query, true)) }.sortedBy { it.best().name.lowercase() }
    LaunchedEffect(groups.map { it.packageName to it.status }) { if(groups.none { it.status == Compatibility.COMPATIBLE } && groups.any { it.status == Compatibility.UNKNOWN }) selected = 1 }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Updates", "GEPRÜFT STATT NUR NEUER", onSettings) }
        if(scanProgress.active) item { ScanProgressCard() }
        item { ScopeChoices(scopeFilter, selection::select) }
        item { FilterChip(onlyFavorites,{onlyFavorites=!onlyFavorites},label={Text("Nur Favoriten")});HelpButton("Favoritenfilter","Zeigt nur Apps, die du mit dem Stern markiert hast.") }
        item { HelpTitle("Prüfstatus",HelpText.BLOCKED+"\n\n"+HelpText.CHECK) }

        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Bereit", "Prüfen", "Blockiert").forEachIndexed { i, label ->
                val category = Compatibility.entries.let { listOf(Compatibility.COMPATIBLE, Compatibility.UNKNOWN, Compatibility.INCOMPATIBLE)[i] }
                FilterChip(selected == i, { selected = i }, label = { Text("$label ${groups.count { (!onlyFavorites || it.packageName in favorites) && it.status == category }}", style = MaterialTheme.typography.labelSmall) })
            }
        } }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Updates durchsuchen") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = CircleShape, singleLine = true) }
        if(all.any{it.isInstalling}) item {TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={viewModel.cancelVerification()}){Text("APK-Prüfung abbrechen")};HelpButton("Prüfung abbrechen","Stoppt die manuell gestartete Einzel- oder Sammelprüfung nach Freigabe des laufenden Netzwerkaufrufs. Es wird nichts installiert. Bereits gespeicherte öffentliche APK-Kopien bleiben erhalten.")}

        if(health.any { it.problem != null }) item { StateMessage("Quellenfehler", health.filter { it.problem != null }.joinToString("\n") { "${it.name}: ${it.problem}" }) }
        if(state is UpdatesUiState.Error) item { StateMessage("Suche fehlgeschlagen", "Bitte Verbindung und aktivierte Quellen prüfen.") }
        if(visible.isEmpty() && !working) item { StateMessage(when(selected) { 0 -> "Keine freigegebenen Updates"; 1 -> "Nichts zur Prüfung offen"; else -> "Keine blockierten Varianten" }, when(selected) { 0 -> "Nur tatsächlich geprüfte APKs erscheinen hier. Weitere Funde findest du unter Prüfen."; 1 -> "Starte eine neue Suche oder prüfe die Quellen in den Einstellungen."; else -> "Ältere Systemzweige und nachweislich unpassende APKs erscheinen separat in diesem Bereich." }) }
        if(selected == 1 && visible.isNotEmpty()) item { OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { viewModel.verifyAll() }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("APKs gesammelt herunterladen und prüfen") };HelpButton("Alle APKs prüfen",HelpText.CHECK+" Pro App wird eine unterstützte Quelle verwendet. Bereits geprüfte und blockierte Dateien werden übersprungen. Es wird nacheinander geprüft; Webquellen bleiben manuell."); SecondaryText("Startet nur nach dieser Aktion einen begrenzten Sammeldownload. Öffnen von Prüfinformationen lädt keine APK.") }
        items(visible, key = { it.packageName }) { group -> UpdateGroupCard(group, viewModel.preferred(group.packageName), { viewModel.verify(it) }, { viewModel.install(it, handler) }, viewModel::ignoreRelease, viewModel::prefer) }
    }
}
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ScopeChoices(selected: AppScope, choose: (AppScope) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AppScope.entries.forEach { value -> FilterChip(selected == value, { choose(value) }, label = { Text(if(value == AppScope.GENERAL) "Allgemeine" else value.label, style = MaterialTheme.typography.labelSmall) }) }
    }
}
