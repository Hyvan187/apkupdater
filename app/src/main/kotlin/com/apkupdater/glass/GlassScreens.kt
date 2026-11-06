package com.apkupdater.glass

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkupdater.prefs.Prefs
import com.apkupdater.viewmodel.UpdatesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun OverviewScreen(viewModel: UpdatesViewModel) {
    val context = LocalContext.current
    val prefs = koinInject<Prefs>()
    val detection = remember { DeviceDetection(context, prefs) }
    val state = viewModel.state().collectAsStateWithLifecycle().value
    val updates = state.updates()
    var report by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            report = "APK wird lokal geprüft …"
            report = withContext(Dispatchers.IO) {
                val file = File.createTempFile("glass-local-", ".apk", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input)
                        file.outputStream().use { out ->
                            val buffer = ByteArray(65536); var count = 0L
                            while (true) { val n = input.read(buffer); if (n < 0) break; count += n; require(count <= 512L * 1024 * 1024) { "Datei größer als 512 MiB" }; out.write(buffer, 0, n) }
                        }
                    }
                    ApkAnalyzer(context, detection).analyze(file).text()
                } catch (e: Exception) { "Prüfung nicht möglich: ${e.message}" } finally { file.delete() }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("APKUpdater Glass", style = MaterialTheme.typography.headlineLarge) }
        item { GlassPanel(effectLevel = prefs.effectLevel.get()) {
            Text("Dein Gerät", style = MaterialTheme.typography.titleLarge)
            Text(detection.description())
            Text("Automatische Region: nicht belegt. Wähle die ROM gegebenenfalls manuell.", style = MaterialTheme.typography.bodySmall)
        } }
        item { GlassPanel(effectLevel = prefs.effectLevel.get()) {
            Text("Updateprüfung", style = MaterialTheme.typography.titleLarge)
            Text("${updates.size} Kandidaten · ${updates.count { it.verdict.status == Compatibility.COMPATIBLE }} kompatibel")
            Text("${updates.count { it.verdict.status == Compatibility.UNKNOWN }} ungeklärt · ${updates.count { it.verdict.status == Compatibility.INCOMPATIBLE }} inkompatibel")
            Text(if (prefs.lastCheck.get() == 0L) "Noch keine abgeschlossene Prüfung" else "Letzte Ergebnisse: ${DateFormat.getDateTimeInstance().format(Date(prefs.lastCheck.get()))}")
            Button(onClick = { viewModel.refresh() }) { Text("Jetzt prüfen") }
        } }
        item { GlassPanel(effectLevel = prefs.effectLevel.get()) {
            Text("Lokaler APK-Analyzer", style = MaterialTheme.typography.titleLarge)
            Text("Signatur, Versionscode, Android, Architektur und Split-Hinweise prüfen. Es wird nichts installiert.")
            OutlinedButton(onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }) { Text("APK auswählen") }
            report?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        } }
    }
}

@Composable
fun GlassSettings() {
    val prefs = koinInject<Prefs>()
    var region by remember { mutableStateOf(prefs.romRegion.get()) }
    var effect by remember { mutableIntStateOf(prefs.effectLevel.get()) }
    var filter by remember { mutableIntStateOf(prefs.compatibilityFilter.get()) }
    var wifi by remember { mutableStateOf(prefs.wifiOnly.get()) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ROM und Kompatibilität", style = MaterialTheme.typography.titleLarge)
        var open by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { open = true }) { Text("ROM: $region") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("AUTO", "EU", "GLO", "IN", "CN", "OTHER").forEach { r -> DropdownMenuItem(text = { Text(r) }, onClick = { region = r; prefs.romRegion.put(r); open = false }) }
        }
        Text("Die Auswahl beschreibt deine ROM. Sie bestätigt keine Herkunft einer APK.", style = MaterialTheme.typography.bodySmall)
        listOf("Nur kompatible Updates", "Kompatible und ungeklärte Updates", "Alle Kandidaten").forEachIndexed { index, text ->
            Row(Modifier.fillMaxWidth().clickable { filter = index; prefs.compatibilityFilter.put(index) }) {
                RadioButton(selected = filter == index, onClick = { filter = index; prefs.compatibilityFilter.put(index) }); Text(text, Modifier.padding(top = 12.dp))
            }
        }
        Text("Glaseffekt: ${listOf("Aus", "Reduziert", "Standard", "Hoch", "Dynamisch")[effect]}")
        Slider(value = effect.toFloat(), onValueChange = { effect = it.toInt(); prefs.effectLevel.put(effect) }, valueRange = 0f..4f, steps = 3)
        Text("Dynamisch verwendet derzeit dieselbe Qualitätsstufe wie Hoch. Keine dauerhaften Hintergrundanimationen.", style = MaterialTheme.typography.bodySmall)
        Row { Switch(checked = wifi, onCheckedChange = { wifi = it; prefs.wifiOnly.put(it) }); Text("Hintergrundprüfung nur ungetaktet", Modifier.padding(12.dp)) }
    }
}

@Composable
fun GlassUpdatesScreen(viewModel: UpdatesViewModel) {
    val prefs = koinInject<Prefs>()
    val state = viewModel.state().collectAsStateWithLifecycle().value
    val all = state.updates()
    var filter by remember { mutableIntStateOf(prefs.compatibilityFilter.get()) }
    val handler = LocalUriHandler.current
    val visible = all.filter { filter == 2 || it.verdict.status == Compatibility.COMPATIBLE || (filter == 1 && it.verdict.status == Compatibility.UNKNOWN) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Updates", style = MaterialTheme.typography.headlineLarge)
            Button(onClick = { viewModel.refresh() }) { Text("Aktualisieren") }
            Text("${all.size} Kandidaten. Fehlende Nachweise sind keine Freigabe.")
            OutlinedButton(onClick = { filter = (filter + 1) % 3; prefs.compatibilityFilter.put(filter) }) { Text(listOf("Nur kompatibel", "Mit ungeklärten", "Alle Kandidaten")[filter]) }
            if (visible.isEmpty()) Text("Keine passenden Ergebnisse in diesem Filter. Ungeklärte Kandidaten kannst du separat einblenden.")
        }
        items(visible, key = { it.id }) { update ->
            var expanded by remember { mutableStateOf(false) }
            GlassPanel(effectLevel = prefs.effectLevel.get(), modifier = Modifier.clickable { expanded = !expanded }) {
                Text(update.name.ifEmpty { update.packageName }, style = MaterialTheme.typography.titleMedium)
                Text(update.packageName, style = MaterialTheme.typography.bodySmall)
                Text("${update.oldVersion} → ${update.version}")
                val color = when(update.verdict.status) { Compatibility.COMPATIBLE -> androidx.compose.ui.graphics.Color(0xff60dfaa); Compatibility.UNKNOWN -> androidx.compose.ui.graphics.Color(0xffffd183); else -> MaterialTheme.colorScheme.error }
                Text(when(update.verdict.status) { Compatibility.COMPATIBLE -> "Technisch kompatibel"; Compatibility.UNKNOWN -> "Nicht vollständig verifizierbar"; else -> "Nicht kompatibel" }, color = color)
                Text("${(listOf(update.source.name) + update.additionalSources).distinct().joinToString()} · Code ${update.oldVersionCode} → ${update.versionCode}", style = MaterialTheme.typography.bodySmall)
                if (expanded) {
                    update.verdict.evidence.forEach { Text("${it.property}: ${it.status}\n${it.detail}", style = MaterialTheme.typography.bodySmall) }
                    if (update.whatsNew.isNotEmpty()) Text(update.whatsNew)
                }
                if (update.source == com.apkupdater.data.ui.ApkMirrorSource) {
                    OutlinedButton(onClick = { viewModel.install(update, handler) }) { Text("Quellseite öffnen") }
                    Text("Ein externer Download wird von Glass nicht automatisch geprüft.", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedButton(onClick = { viewModel.verify(update) }, enabled = !update.isInstalling) { Text("APK herunterladen und prüfen") }
                    if (update.verdict.installAllowed) Button(onClick = { viewModel.install(update, handler) }, enabled = !update.isInstalling) { Text("Installieren") }
                }
                update.sha256?.let { Text("Geprüfte Datei SHA-256: $it", style = MaterialTheme.typography.bodySmall) }
                if (update.isInstalling) LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = { viewModel.ignoreVersion(update.id) }) { Text("Version ignorieren") }
            }
        }
    }
}
