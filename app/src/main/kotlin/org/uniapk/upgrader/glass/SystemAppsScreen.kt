package org.uniapk.upgrader.glass

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.uniapk.upgrader.prefs.Prefs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private data class PackageRow(val name: String, val pkg: String, val version: String, val code: Long, val system: Boolean, val updated: Boolean, val enabled: Boolean, val source: String)

@Composable
fun SystemAppsScreen(onSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = koinInject<Prefs>()
    var apps by remember { mutableStateOf<List<PackageRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val selection = koinInject<AppSelection>()
    val category by selection.scope.collectAsStateWithLifecycle()
    val favoriteStore=koinInject<FavoriteStore>()
    val favorites by favoriteStore.state.collectAsStateWithLifecycle()
    var mode by remember { mutableIntStateOf(0) }
    var ignored by remember { mutableStateOf(prefs.ignoredApps.get().toSet()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val flags = if(Build.VERSION.SDK_INT >= 24) PackageManager.MATCH_DISABLED_COMPONENTS else @Suppress("DEPRECATION") PackageManager.GET_DISABLED_COMPONENTS
                val packages = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong())) else pm.getInstalledPackages(flags)
                packages.mapNotNull { info -> info.applicationInfo?.let { app ->
                    PackageRow(pm.getApplicationLabel(app).toString(), info.packageName, info.versionName.orEmpty(),
                        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(),
                        app.flags and ApplicationInfo.FLAG_SYSTEM != 0, app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0, app.enabled,
                        runCatching { if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(info.packageName).installingPackageName.orEmpty() else @Suppress("DEPRECATION") pm.getInstallerPackageName(info.packageName).orEmpty() }.getOrDefault("Unbekannt"))
                } }.sortedBy { it.name.lowercase() }
            }.onSuccess { apps = it }.onFailure { error = it.message }
        }
    }
    val filtered = apps.filter { app ->
        (app.name.contains(query, true) || app.pkg.contains(query, true)) && CategoryPolicy.matches(category, app.system || app.updated, app.pkg) && when(mode) {
            1 -> !app.enabled
            2 -> app.pkg in ignored
            3 -> app.pkg in favorites
            else -> true
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Deine Apps", "INSTALLIERTE PAKETE", onSettings)
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("App oder Paket suchen") }, modifier = Modifier.fillMaxWidth())
        ScopeChoices(category, selection::select)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Alle Zustände", "Deaktiviert", "Ignoriert", "Favoriten").forEachIndexed { i, label ->
                FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(label) })
            }
        }
        error?.let { Text("Paketabfrage fehlgeschlagen: $it") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)) {
            items(filtered, key = { it.pkg }) { app ->
                var expanded by remember { mutableStateOf(false) }
                GlassPanel( modifier = Modifier.clickable { expanded = !expanded }) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        org.uniapk.upgrader.ui.component.LoadingImageApp(app.pkg, Modifier.size(48.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            SecondaryText("${app.version} · ${if(app.system || app.updated) "System" else "App"}${if(!app.enabled) " · deaktiviert" else ""}")
                        }
                    }
                    Row { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={favoriteStore.toggle(app.pkg)}){Text(if(app.pkg in favorites)"★ Favorit entfernen" else "☆ Als Favorit markieren")} }
                    if (expanded) {
                        SecondaryText(app.pkg)
                        Text("Installationsquelle: ${app.source.ifEmpty { "nicht ausgewiesen" }}")
                        Text("System: ${app.system} · System-Update: ${app.updated}")
                        OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))) }) { Text("Android-App-Einstellungen") }
                        Row {
                            Switch(checked = app.pkg !in ignored, onCheckedChange = { enabled ->
                                ignored = if (enabled) ignored - app.pkg else ignored + app.pkg
                                prefs.ignoredApps.put(ignored.toList())
                            })
                            Text("Updates prüfen", Modifier.padding(12.dp));HelpButton("App ignorieren","Aus deaktiviert die Updateabfrage für dieses Paket. Die installierte App wird nicht deaktiviert oder deinstalliert.")
                        }
                    }
                }
            }
        }
    }
}
