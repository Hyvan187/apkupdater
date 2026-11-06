package com.apkupdater.glass

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.apkupdater.BuildConfig
import com.apkupdater.prefs.Prefs
import com.apkupdater.util.Themer
import com.apkupdater.worker.UpdatesWorker
import org.koin.compose.koinInject

@Composable
fun GlassSettingsScreen(dynamic: MutableState<Boolean>, effects: MutableIntState, onBack: () -> Unit) {
    val prefs = koinInject<Prefs>()
    val themer = koinInject<Themer>()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var theme by remember { mutableIntStateOf(prefs.theme.get()) }
    var scope by remember { mutableIntStateOf(prefs.scanScope.get()) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Einstellungen", "DEINE UPDATE-ZENTRALE", onBack = onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Updateprüfung", style = MaterialTheme.typography.titleMedium) }
        item { GlassPanel {
            SecondaryText("Welche Apps sollen die Quellen prüfen?")
            listOf("Alle installierten Apps", "Nur System-Apps", "Nur OPlus-Apps").forEachIndexed { i, label ->
                Row { RadioButton(scope == i, { scope = i; prefs.scanScope.put(i) }); Text(label, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            SettingSwitch("Beta-Versionen ausblenden", "Vorabversionen werden nicht empfohlen.", { prefs.ignoreBeta.get() }, { prefs.ignoreBeta.put(it) })
            SettingSwitch("Alpha-Versionen ausblenden", "Experimentelle Versionen überspringen.", { prefs.ignoreAlpha.get() }, { prefs.ignoreAlpha.put(it) })
        } }
        item { Text("Hintergrund", style = MaterialTheme.typography.titleMedium) }
        item { GlassPanel {
            SettingSwitch("Täglich nach Updates suchen", "Die Installation erfordert immer deine Bestätigung.", { prefs.enableAlarm.get() }, {
                prefs.enableAlarm.put(it)
                val wm = WorkManager.getInstance(context)
                if(it) { UpdatesWorker.launch(wm); if(android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) } else UpdatesWorker.cancel(wm)
            })
            SettingSwitch("Nur ungetaktete Verbindung", "Vermeidet automatische Prüfungen über getaktete Netze.", { prefs.wifiOnly.get() }, { prefs.wifiOnly.put(it); if(prefs.enableAlarm.get()) UpdatesWorker.launch(WorkManager.getInstance(context)) })
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
        item { Text("Darstellung", style = MaterialTheme.typography.titleMedium) }
        item { GlassPanel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("System", "Dunkel", "Hell").forEachIndexed { i, label ->
                    FilterChip(theme == i, { theme = i; prefs.theme.put(i); themer.setTheme(com.apkupdater.ui.theme.isDarkTheme(i)) }, label = { Text(label) })
                }
            }
            SettingSwitch("Systemfarben", "Material You für Akzente und Bedienelemente", { dynamic.value }, { dynamic.value = it; prefs.dynamicColors.put(it) })
            Spacer(Modifier.height(8.dp)); Text("Glasqualität: ${listOf("Aus", "Reduziert", "Standard", "Hoch")[effects.intValue]}", style = MaterialTheme.typography.bodyMedium)
            Slider(effects.intValue.toFloat(), { effects.intValue = it.toInt(); prefs.effectLevel.put(it.toInt()) }, valueRange = 0f..3f, steps = 2)
            SecondaryText("Die schwebende Navigation nimmt die Farben des darunterliegenden Inhalts auf. Reduzierte Systemanimationen werden berücksichtigt.")
        } }
        item { GlassPanel {
            Text("Automatische ROM-Erkennung", style = MaterialTheme.typography.titleSmall)
            SecondaryText("Verwendet lesbare Firmware-Eigenschaften. Es gibt keine manuelle Regionsfreigabe. Fehlende oder widersprüchliche Kennungen bleiben offen.")
            Spacer(Modifier.height(12.dp)); Text("APKUpdater Glass ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
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
