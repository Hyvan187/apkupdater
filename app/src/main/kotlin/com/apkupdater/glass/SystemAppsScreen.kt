package com.apkupdater.glass

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
import com.apkupdater.prefs.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private data class PackageRow(val name: String, val pkg: String, val version: String, val code: Long, val system: Boolean, val updated: Boolean, val enabled: Boolean, val source: String)

@Composable
fun SystemAppsScreen() {
    val context = LocalContext.current
    val prefs = koinInject<Prefs>()
    var apps by remember { mutableStateOf<List<PackageRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    var ignored by remember { mutableStateOf(prefs.ignoredApps.get().toSet()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val packages = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())) else pm.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS)
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
        (app.name.contains(query, true) || app.pkg.contains(query, true)) && when(mode) {
            0 -> app.system || app.updated
            1 -> listOf("com.oplus.", "com.oneplus.", "com.oppo.", "com.coloros.", "com.heytap.", "com.realme.").any { app.pkg.startsWith(it) }
            2 -> app.pkg.startsWith("com.google.") && (app.system || app.updated)
            3 -> app.updated
            4 -> !app.enabled
            5 -> app.pkg in ignored
            else -> true
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("System-Apps", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("App oder Paket suchen") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("System", "OPlus", "Google-System", "Aktualisiert", "Deaktiviert", "Ignoriert", "Alle Apps").forEachIndexed { i, label ->
                FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(label) })
            }
        }
        error?.let { Text("Paketabfrage fehlgeschlagen: $it") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(filtered, key = { it.pkg }) { app ->
                var expanded by remember { mutableStateOf(false) }
                GlassPanel(effectLevel = prefs.effectLevel.get(), modifier = Modifier.clickable { expanded = !expanded }) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium)
                    Text(app.pkg, style = MaterialTheme.typography.bodySmall)
                    Text("${app.version} (${app.code}) · ${if (app.enabled) "aktiv" else "deaktiviert"}")
                    if (expanded) {
                        Text("Installationsquelle: ${app.source.ifEmpty { "nicht ausgewiesen" }}")
                        Text("System: ${app.system} · System-Update: ${app.updated}")
                        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))) }) { Text("Android-App-Einstellungen") }
                        Row {
                            Switch(checked = app.pkg !in ignored, onCheckedChange = { enabled ->
                                ignored = if (enabled) ignored - app.pkg else ignored + app.pkg
                                prefs.ignoredApps.put(ignored.toList())
                            })
                            Text("Updates prüfen", Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }
}
