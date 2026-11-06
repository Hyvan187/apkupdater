package com.apkupdater.glass

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.apkupdater.BuildConfig
import com.apkupdater.prefs.Prefs
import com.apkupdater.worker.UpdatesWorker
import org.koin.compose.koinInject

@Composable
fun GlassSettingsScreen(onBack: () -> Unit, onAppearance: () -> Unit, onFiltersChanged: () -> Unit = {}) {
    val prefs = koinInject<Prefs>()
    val selection = koinInject<AppSelection>()
    val scope by selection.scope.collectAsState()
    val health by SourceHealth.flow.collectAsState()
    val filters = koinInject<ReleaseFilterStore>()
    val context = LocalContext.current
    var folderProblem by remember { mutableStateOf<String?>(null) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if(uri!=null) runCatching { context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);prefs.downloadTree.put(uri.toString()) }.onFailure { folderProblem="Ordnerzugriff nicht möglich. Bitte einen anderen Ordner auswählen." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Einstellungen", "DEINE UPDATE-ZENTRALE", onBack = onBack)
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Updateprüfung", style = MaterialTheme.typography.titleMedium) }
        item { GlassPanel {
            SecondaryText("Welche Apps sollen die Quellen prüfen?")
            ScopeChoices(scope, selection::select)
            SettingSwitch("Beta-Versionen ausblenden", "Vorabversionen werden nicht empfohlen.", { prefs.ignoreBeta.get() }, { filters.change(beta=it);onFiltersChanged() })
            SettingSwitch("Alpha-Versionen ausblenden", "Experimentelle Versionen überspringen.", { prefs.ignoreAlpha.get() }, { filters.change(alpha=it);onFiltersChanged() })
        } }
        item { GlassPanel { SettingSwitch("Alle Vorabversionen ausblenden", "Auch RC, Preview, Nightly und markierte Vorabversionen ausblenden.", {prefs.ignorePreRelease.get()}, {filters.change(preview=it);onFiltersChanged()}) } }
        item { Text("Hintergrund", style = MaterialTheme.typography.titleMedium) }
        item { GlassPanel {
            SettingSwitch("Täglich nach Updates suchen", "Die Installation erfordert immer deine Bestätigung.", { prefs.enableAlarm.get() }, {
                prefs.enableAlarm.put(it)
                val wm = WorkManager.getInstance(context)
                if(it) { UpdatesWorker.launch(wm); if(android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) } else UpdatesWorker.cancel(wm)
            })
            SettingSwitch("Nur ungetaktete Verbindung", "Vermeidet automatische Prüfungen über getaktete Netze.", { prefs.wifiOnly.get() }, { prefs.wifiOnly.put(it); if(prefs.enableAlarm.get()) UpdatesWorker.launch(WorkManager.getInstance(context)) })
        } }
        item { GlassPanel {
            Text("Downloads",style=MaterialTheme.typography.titleMedium)
            SecondaryText(if(android.os.Build.VERSION.SDK_INT>=29) "Manuell direkt geladene APKs: Download/Liquid-APK-Updater. Automatische Prüfdateien bleiben privat." else "Bitte einen sichtbaren Downloadordner auswählen.")
            SettingSwitch("APKs nach Installation behalten","Aus: öffentliche Kopie erst nach erfolgreicher Installation löschen. Bei Abbruch oder Fehler bleibt sie erhalten.",{prefs.keepDownloadedApks.get()},{prefs.keepDownloadedApks.put(it)})
            if(android.os.Build.VERSION.SDK_INT<29) OutlinedButton(onClick={folder.launch(null)}){Text("Downloadordner auswählen")}
            folderProblem?.let{SecondaryText(it)}
        } }
        item { Text("Updatequellen", style = MaterialTheme.typography.titleMedium) }
        item { GlassPanel {
            SecondaryText("Quellen liefern Suchergebnisse. Die Herkunft allein ist kein Kompatibilitätsnachweis.")
            SettingSwitch("GitHub", "Zugeordnete Entwickler-Releases", { prefs.useGitHub.get() }, { prefs.useGitHub.put(it) })
            SettingSwitch("GitLab", "Zugeordnete Entwickler-Releases", { prefs.useGitLab.get() }, { prefs.useGitLab.put(it) })
            SettingSwitch("F-Droid", "Paketkatalog mit eigener Signierung", { prefs.useFdroid.get() }, { prefs.useFdroid.put(it) })
            SettingSwitch("IzzyOnDroid", "Open-Source-Apps aus dem Repository", { prefs.useIzzy.get() }, { prefs.useIzzy.put(it) })
            SettingSwitch("APKMirror", "Externe Quellseiten, keine automatische Freigabe", { prefs.useApkMirror.get() }, { prefs.useApkMirror.put(it) })
            SettingSwitch("APKPure", "Anbieterkatalog, jede APK wird separat bewertet", { prefs.useApkPure.get() }, { prefs.useApkPure.put(it) })
            SettingSwitch("Aptoide", "Anbieterkatalog, jede APK wird separat bewertet", { prefs.useAptoide.get() }, { prefs.useAptoide.put(it) })
            SettingSwitch("Google Play", "Experimentelle vorhandene Integration", { prefs.usePlay.get() }, { prefs.usePlay.put(it) })
        } }
        item { GlassPanel(Modifier.clickable(onClick=onAppearance)) {
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Erscheinungsbild",style=MaterialTheme.typography.titleMedium);SecondaryText("Farben, Schrift und empfohlenes Glasdesign") }
                Icon(Icons.AutoMirrored.Filled.ArrowForward,"Erscheinungsbild öffnen")
            }
        } }
        item { GlassPanel {
            Text("APK-Prüfung", style = MaterialTheme.typography.titleMedium)
            SettingSwitch("APKs automatisch prüfen", "Lädt bis zu sechs direkt verfügbare APKs je Suche zur technischen Prüfung herunter. Keine Installation.", { prefs.autoVerify.get() }, { prefs.autoVerify.put(it) })
            SettingSwitch("Automatische APK-Prüfung nur ungetaktet", "Manuelle Prüfungen sind jederzeit möglich.", { prefs.autoVerifyWifiOnly.get() }, { prefs.autoVerifyWifiOnly.put(it) })
            var limit by remember { mutableIntStateOf(prefs.autoVerifyLimitMiB.get()) }
            Text("Automatisches Downloadlimit je APK", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(32,64,128).forEach { size -> FilterChip(limit == size, { limit = size; prefs.autoVerifyLimitMiB.put(size) }, label = { Text("$size MiB") }) }
            }
            SecondaryText("APKMirror und nicht vollständig unterstützte Archive bleiben manuell. Eine technische Prüfung garantiert keine fehlerfreie App-Funktion.")
        } }
        if(health.any { it.problem != null }) item { GlassPanel {
            Text("Quellendiagnose", style = MaterialTheme.typography.titleMedium)
            health.filter { it.problem != null }.forEach { Text(it.name, style = MaterialTheme.typography.labelLarge); SecondaryText(it.problem.orEmpty()) }
            SecondaryText("Eine neue Updateprüfung fragt die aktivierten Quellen erneut ab.")
        } }
        item { GlassPanel {
            Text("Ausgeblendete Versionen", style = MaterialTheme.typography.titleMedium)
            var restored by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { prefs.ignoredVersions.put(emptyList()); prefs.ignoredReleases.put(emptyList()); restored = true }) { Text("Versionen wieder einblenden") }
            if(restored) SecondaryText("Bei der nächsten Updateprüfung wieder sichtbar.")
        } }
        item { GlassPanel {
            Text("Automatische ROM-Erkennung", style = MaterialTheme.typography.titleSmall)
            SecondaryText("Verwendet lesbare Firmware-Eigenschaften. Es gibt keine manuelle Regionsfreigabe. Fehlende oder widersprüchliche Kennungen bleiben offen.")
            Spacer(Modifier.height(12.dp)); Text("Liquid-APK-Updater ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
            SecondaryText("GPLv3 · Basierend auf APKUpdater von rumboalla und Mitwirkenden. Keine Werbung, kein hinzugefügtes Tracking.")
        } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
}

@Composable
private fun SettingSwitch(title: String, description: String, get: () -> Boolean, set: (Boolean) -> Unit) {
    var checked by remember(title) { mutableStateOf(get()) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).padding(top = 3.dp)) { Text(title, style = MaterialTheme.typography.bodyMedium); SecondaryText(description) }
        Switch(checked, { checked = it; set(it) })
    }
}
