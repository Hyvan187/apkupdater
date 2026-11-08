package org.uniapk.upgrader.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.ui.component.LoadingImageApp
import java.text.DateFormat
import java.util.Date

@Composable
fun UpdateCard(update: AppUpdate, verify: () -> Unit, install: () -> Unit, ignore: () -> Unit, source: () -> Unit) =
    UpdateGroupCard(UpdateGroup(update.packageName, listOf(update)), null, { verify() }, { install() }, { ignore() }, {}, source)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateGroupCard(group: UpdateGroup, preferred: String?, verify: (AppUpdate) -> Unit, install: (AppUpdate) -> Unit, ignore: (AppUpdate) -> Unit, prefer: (AppUpdate) -> Unit, external: (() -> Unit)? = null,discard:((AppUpdate)->Unit)?=null,deleting:Boolean=false,ignoreApp:((AppUpdate)->Unit)?=null) {
    val favorites=org.koin.compose.koinInject<FavoriteStore>()
    val flags by favorites.state.collectAsState()
    var chosenKey by remember(group.packageName) { mutableStateOf<String?>(null) }
    val update = group.candidates.find { it.artifactKey == chosenKey } ?: group.best(preferred)
    var expanded by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var changelog by remember{mutableStateOf(false)}
    var technical by remember { mutableStateOf(false) };var deletePrepared by remember{mutableStateOf(false)}
    val handler = LocalUriHandler.current
    val appContext=androidx.compose.ui.platform.LocalContext.current
    val blocked = update.verdict.status == Compatibility.INCOMPATIBLE
    val busy = deleting||group.candidates.any { it.isInstalling }
    val color = if(blocked) MaterialTheme.colorScheme.error else safeStatusColor(update.verdict.installAllowed)
    GlassPanel {
        Row(Modifier.clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            LoadingImageApp(update.packageName, Modifier.size(52.dp)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(update.name.ifBlank { update.packageName }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SecondaryText("${update.oldVersion.ifBlank { "?" }} → ${update.version}")
                Text(if(deleting)"APKs werden gelöscht …" else if(busy)"APK wird vorbereitet …" else if(blocked) "Abgelehnt" else if(update.verdict.installAllowed) "Geprüft · bereit" else "Datei noch ungeprüft", style = MaterialTheme.typography.labelSmall, color = color)
            }
            FavoriteButton(update.packageName)
            Icon(if(expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Details", Modifier.size(20.dp))
        }
        Spacer(Modifier.height(spacing(10)))
        SecondaryText("${architectureLabel(update.abis)} · ${group.sources().size} ${if(group.sources().size == 1) "Quelle" else "Quellen"}")
        if(blocked) SecondaryText(update.verdict.summary)
        val assessment=TechnicalAssessment.from(update)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Risikoeinschätzung",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
            HelpButton("Risikoeinschätzung",UpdateRisk.assess(update).summary+" "+assessment.details())
        }
        RiskSummary(update)
        update.verificationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        AnimatedVisibility(expanded,enter=expandVertically(UniMotion.spec(motionAllowed()))+fadeIn(UniMotion.spec(motionAllowed())),exit=shrinkVertically(UniMotion.spec(motionAllowed()))+fadeOut(UniMotion.spec(motionAllowed()))) { Column {
            Spacer(Modifier.height(spacing(12)))
            FactRow("Version", "${update.oldVersion} → ${update.version}")
            FactRow("Architektur", architectureLabel(update.abis))
            FactRow("Kategorie", if(group.system) "System-App" else "Allgemeine App")
            FactRow("Quelle", update.source.name)
            update.updateOwner?.let{FactRow("Update-Verwaltung",it)}
            FactRow("Signatur", when(update.verdict.evidence.find { it.property == "Signatur" }?.status) { Compatibility.COMPATIBLE -> "Passt zur Installation"; Compatibility.INCOMPATIBLE -> "Passt nicht"; else -> "Noch offen" })
            update.verifiedAt?.let { FactRow("Geprüft", DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))) }
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { technical = true }) { Text("Technische Details") }
            Row(verticalAlignment=Alignment.CenterVertically) { Text("Prüfverfahren",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium);HelpButton("Prüfverfahren",HelpText.CHECK) }
        }
        }
        if(busy) { Spacer(Modifier.height(spacing(10))); UniProgress(label=if(deleting)"APKs werden gelöscht" else "APK wird vorbereitet") }
        else if(!blocked || group.candidates.any { it.verdict.status != Compatibility.INCOMPATIBLE }) {
            Spacer(Modifier.height(spacing(10)))
            if(update.verdict.installAllowed) Button(onClick = { if(update.fileReady)install(update) else verify(update) }, modifier = Modifier.fillMaxWidth()) { Text(if(update.fileReady)"Installieren" else "APK erneut laden und vorbereiten") }
            OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Text(if(update.verdict.installAllowed) "Andere Quelle / erneut prüfen" else "Quelle wählen · APK vorbereiten") }
        }
        if(discard!=null&&group.candidates.any{it.fileReady}&&!busy)TextButton(onClick={deletePrepared=true}){Icon(Icons.Default.Delete,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Vorbereitete APKs löschen")}
        if(update.whatsNew.isNotBlank())TextButton(onClick={changelog=true}){Text("Änderungen dieser Version")}
        else if(expanded)SecondaryText("Die Quelle stellt keinen Änderungsverlauf bereit.")
        TextButton(onClick={ignore(update)}){Text("Diese Version überspringen")}
        if(ignoreApp!=null)TextButton(onClick={ignoreApp(update)}){Icon(Icons.Default.Close,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("App dauerhaft ignorieren")}
        if(expanded)TextButton(onClick={PlayInstallPolicy.open(context=appContext,packageName=update.packageName)}){Text("In Google Play öffnen")}

    }
    if(picker) {
        var selected by remember { mutableStateOf(update) }
        var variants by remember { mutableStateOf<String?>(null) }
        GlassMenuSheet(onDismiss = { picker = false }) {
                HelpTitle("Quelle auswählen","Wähle Anbieter und Variante. Geprüft wird genau diese Datei; andere Quellen übernehmen ihre Freigabe nicht. APKMirror öffnet die Webseite.")
                SecondaryText("${update.name} · Anbieter behalten eigene Varianten und Prüfergebnisse.")
                LazyColumn(Modifier.weight(1f,fill=false).heightIn(max = 390.dp)) {
                    val options=group.sources(preferred).let{sources->if(sources.none{it.source==ApkMirrorSource})sources+ApkMirrorWeb.option(update) else sources}
                    items(options, key = { it.source.name }) { option ->
                        val allowed = option.verdict.status != Compatibility.INCOMPATIBLE && (option.link is Link.Url || option.link is Link.Xapk || option.link is Link.Play)
                        Row(Modifier.fillMaxWidth().clickable(enabled = allowed) { selected = option }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected.source == option.source, { if(allowed) selected = option }, enabled = allowed)
                            Column(Modifier.weight(1f)) {
                                Text(option.source.name, style = MaterialTheme.typography.titleSmall)
                                SecondaryText("${option.version}${if(option.versionCode>0)" · Build ${option.versionCode}" else ""} · ${architectureLabel(option.abis)}")
                                SecondaryText(if(option.verdict.status == Compatibility.INCOMPATIBLE) "Gesperrt: ${option.verdict.summary}" else if((option.link !is Link.Url && option.link !is Link.Xapk && option.link !is Link.Play)) "Paketformat derzeit nicht prüfbar" else if(option.source == ApkMirrorSource) "Webseite · Download und lokale Prüfung nötig" else if(option.verdict.installAllowed) "APK geprüft" else if(option.link is Link.Play) "Play-Paketset · Basis und Splits werden gemeinsam geprüft" else "Direkter Download · wird geprüft")
                            }
                        }
                        val alternatives = group.candidates.filter { it.source == option.source }
                        if(alternatives.size > 1) {
                            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { variants = if(variants == option.source.name) null else option.source.name }) { Text("${alternatives.size} Varianten") }
                            if(variants == option.source.name) alternatives.forEach { variant ->
                                Row(Modifier.fillMaxWidth().clickable(enabled = variant.verdict.status != Compatibility.INCOMPATIBLE && (variant.link is Link.Url || variant.link is Link.Xapk || variant.link is Link.Play)) { selected = variant }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected.artifactKey == variant.artifactKey, { selected = variant }, enabled = variant.verdict.status != Compatibility.INCOMPATIBLE && (variant.link is Link.Url || variant.link is Link.Xapk || variant.link is Link.Play))
                                    SecondaryText("${variant.version}${if(variant.versionCode>0)" · Build ${variant.versionCode}" else ""} · ${architectureLabel(variant.abis)}${if(variant.verdict.status == Compatibility.INCOMPATIBLE) " · gesperrt" else ""}")
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    TextButton(onClick={picker=false;ignore(selected)},modifier=Modifier.weight(1f)){Text("Diese Version überspringen")}
                    if(ignoreApp!=null)TextButton(onClick={picker=false;ignoreApp(selected)},modifier=Modifier.weight(1f)){Text("App dauerhaft ignorieren")}
                }
                SecondaryText("Überspringen betrifft diesen Build. Ein höherer Build erscheint wieder; bei fehlendem Build-Code gilt der Versionsname.")
                Button(onClick = {
                    if(selected.verdict.status != Compatibility.INCOMPATIBLE && (selected.link is Link.Url || selected.link is Link.Xapk || selected.link is Link.Play)) {
                        chosenKey = selected.artifactKey; prefer(selected); picker = false
                        if(selected.source == ApkMirrorSource) { if(external != null) external() else (selected.link as? Link.Url)?.let { handler.openUri(it.link) } }
                        else verify(selected)
                    }
                }, enabled = selected.verdict.status != Compatibility.INCOMPATIBLE && (selected.link is Link.Url || selected.link is Link.Xapk || selected.link is Link.Play), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text(if(selected.source == ApkMirrorSource) "Quellseite öffnen" else "Auswahl herunterladen und prüfen") }
        }
    }
    if(deletePrepared)AlertDialog(onDismissRequest={deletePrepared=false},title={Text("Vorbereitete APKs löschen?")},text={Text("Entfernt die vorbereiteten Dateien dieser App und ihre von UniAPK gespeicherten Kopien. Die installierte App bleibt erhalten. Die Version steht wieder unter Funde und wird nicht erneut automatisch geladen, bis du sie selbst vorbereitest.")},confirmButton={TextButton(onClick={deletePrepared=false;discard?.invoke(update)}){Text("APKs löschen")}},dismissButton={TextButton(onClick={deletePrepared=false}){Text("Abbrechen")}})
    if(changelog)AlertDialog(onDismissRequest={changelog=false},title={Text("Änderungen · ${update.version}")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())){SecondaryText("Laut ${update.source.name}; Angaben der Quelle, keine eigene Zusicherung.");Text(org.jsoup.Jsoup.parse(update.whatsNew.take(32000)).wholeText())}},confirmButton={TextButton(onClick={changelog=false}){Text("Schließen")}})
    if(technical) AlertDialog(onDismissRequest = { technical = false }, title = { Text("Technische Details") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            val profilePrefs=org.koin.compose.koinInject<org.uniapk.upgrader.prefs.Prefs>()
            if(ProfilePolicy.simulationActive(profilePrefs))SecondaryText("Store-Testprofil: ${TestDeviceProfiles.presets.find{it.model==profilePrefs.simulatedPreset.get()}?.name ?: "Echtes Gerät"} · Länderfassung ${profilePrefs.simulatedRegion.get()} · manuelle Vergleichsregion ${profilePrefs.manualRegion.get()}. APK-Region nicht durch diese Auswahl belegt. Die Installationsprüfung verwendet das wirkliche Gerät.")
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

