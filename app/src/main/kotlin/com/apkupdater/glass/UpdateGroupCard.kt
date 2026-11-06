package com.apkupdater.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkupdater.data.ui.*
import com.apkupdater.ui.component.LoadingImageApp
import java.text.DateFormat
import java.util.Date

@Composable
fun UpdateCard(update: AppUpdate, verify: () -> Unit, install: () -> Unit, ignore: () -> Unit, source: () -> Unit) =
    UpdateGroupCard(UpdateGroup(update.packageName, listOf(update)), null, { verify() }, { install() }, { ignore() }, {}, source)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateGroupCard(group: UpdateGroup, preferred: String?, verify: (AppUpdate) -> Unit, install: (AppUpdate) -> Unit, ignore: (AppUpdate) -> Unit, prefer: (AppUpdate) -> Unit, external: (() -> Unit)? = null) {
    var chosenKey by remember(group.packageName) { mutableStateOf<String?>(null) }
    val update = group.candidates.find { it.artifactKey == chosenKey } ?: group.best(preferred)
    var expanded by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var technical by remember { mutableStateOf(false) }
    val handler = LocalUriHandler.current
    val blocked = update.verdict.status == Compatibility.INCOMPATIBLE
    val busy = group.candidates.any { it.isInstalling }
    val color = if(blocked) MaterialTheme.colorScheme.error else safeStatusColor(update.verdict.installAllowed)
    GlassPanel {
        Row(Modifier.clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            LoadingImageApp(update.packageName, Modifier.size(52.dp)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(update.name.ifBlank { update.packageName }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SecondaryText("${update.oldVersion.ifBlank { "?" }} → ${update.version}")
                Text(if(busy) "Prüfung läuft …" else if(blocked) "Abgelehnt" else if(update.verdict.installAllowed) "Geprüft · bereit" else "Prüfung offen", style = MaterialTheme.typography.labelSmall, color = color)
            }
            Icon(if(expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Details", Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        SecondaryText("${architectureLabel(update.abis)} · ${group.sources().size} ${if(group.sources().size == 1) "Quelle" else "Quellen"}")
        if(blocked) SecondaryText(update.verdict.summary)
        update.verificationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if(expanded) {
            Spacer(Modifier.height(12.dp))
            FactRow("Version", "${update.oldVersion} → ${update.version}")
            FactRow("Architektur", architectureLabel(update.abis))
            FactRow("Kategorie", if(group.system) "System-App" else "Allgemeine App")
            FactRow("Quelle", update.source.name)
            FactRow("Signatur", when(update.verdict.evidence.find { it.property == "Signatur" }?.status) { Compatibility.COMPATIBLE -> "Passt zur Installation"; Compatibility.INCOMPATIBLE -> "Passt nicht"; else -> "Noch offen" })
            update.verifiedAt?.let { FactRow("Geprüft", DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))) }
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { technical = true }) { Text("Technische Details") }
        }
        if(busy) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if(!blocked || group.candidates.any { it.verdict.status != Compatibility.INCOMPATIBLE }) {
            Spacer(Modifier.height(10.dp))
            if(update.verdict.installAllowed) Button(onClick = { install(update) }, modifier = Modifier.fillMaxWidth()) { Text("Installieren") }
            OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Text(if(update.verdict.installAllowed) "Andere Quelle / erneut prüfen" else "Quelle wählen und prüfen") }
        }
        if(expanded) TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { ignore(update) }) { Text("Diese Version ausblenden") }
    }
    if(picker) {
        var selected by remember { mutableStateOf(update) }
        var variants by remember { mutableStateOf<String?>(null) }
        ModalBottomSheet(onDismissRequest = { picker = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
                Text("Quelle auswählen", style = MaterialTheme.typography.titleLarge)
                SecondaryText("${update.name} · Anbieter behalten eigene Varianten und Prüfergebnisse.")
                LazyColumn(Modifier.heightIn(max = 390.dp)) {
                    items(group.sources(preferred), key = { it.source.name }) { option ->
                        val allowed = option.verdict.status != Compatibility.INCOMPATIBLE && option.link is Link.Url
                        Row(Modifier.fillMaxWidth().clickable(enabled = allowed) { selected = option }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected.source == option.source, { if(allowed) selected = option }, enabled = allowed)
                            Column(Modifier.weight(1f)) {
                                Text(option.source.name, style = MaterialTheme.typography.titleSmall)
                                SecondaryText("${option.version} · ${architectureLabel(option.abis)}")
                                SecondaryText(if(option.verdict.status == Compatibility.INCOMPATIBLE) "Gesperrt: ${option.verdict.summary}" else if(option.link !is Link.Url) "Paketformat derzeit nicht prüfbar" else if(option.source == ApkMirrorSource) "Webseite · Download und lokale Prüfung nötig" else if(option.verdict.installAllowed) "APK geprüft" else "Direkter Download · wird geprüft")
                            }
                        }
                        val alternatives = group.candidates.filter { it.source == option.source }
                        if(alternatives.size > 1) {
                            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { variants = if(variants == option.source.name) null else option.source.name }) { Text("${alternatives.size} Varianten") }
                            if(variants == option.source.name) alternatives.forEach { variant ->
                                Row(Modifier.fillMaxWidth().clickable(enabled = variant.verdict.status != Compatibility.INCOMPATIBLE && variant.link is Link.Url) { selected = variant }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected.artifactKey == variant.artifactKey, { selected = variant }, enabled = variant.verdict.status != Compatibility.INCOMPATIBLE && variant.link is Link.Url)
                                    SecondaryText("${variant.version} · ${architectureLabel(variant.abis)}${if(variant.verdict.status == Compatibility.INCOMPATIBLE) " · gesperrt" else ""}")
                                }
                            }
                        }
                    }
                }
                Button(onClick = {
                    if(selected.verdict.status != Compatibility.INCOMPATIBLE && selected.link is Link.Url) {
                        chosenKey = selected.artifactKey; prefer(selected); picker = false
                        if(selected.source == ApkMirrorSource) { if(external != null) external() else (selected.link as? Link.Url)?.let { handler.openUri(it.link) } }
                        else verify(selected)
                    }
                }, enabled = selected.verdict.status != Compatibility.INCOMPATIBLE && selected.link is Link.Url, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text(if(selected.source == ApkMirrorSource) "Quellseite öffnen" else "Auswahl herunterladen und prüfen") }
            }
        }
    }
    if(technical) AlertDialog(onDismissRequest = { technical = false }, title = { Text("Technische Details") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            SecondaryText(update.packageName); SecondaryText("Versionscode: ${update.oldVersionCode} → ${update.versionCode}")
            update.verdict.evidence.forEach { Text(it.property, style = MaterialTheme.typography.labelMedium); SecondaryText(it.detail) }
            update.sha256?.let { SecondaryText("SHA-256: $it") }
            SecondaryText("Die Prüfung bestätigt technische Voraussetzungen, keine vollständige Funktionsgarantie.")
        }
    }, confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { technical = false }) { Text("Schließen") } })
}
@Composable
private fun FactRow(label: String, value: String) = Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    SecondaryText(label, Modifier.width(88.dp)); Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
}
