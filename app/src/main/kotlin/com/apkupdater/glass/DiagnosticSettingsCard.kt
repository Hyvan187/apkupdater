package com.apkupdater.glass

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apkupdater.diagnostics.AppDiagnostics
import com.apkupdater.diagnostics.DiagnosticExport
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DiagnosticSettingsCard() {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val config by AppDiagnostics.state.collectAsState()
    var busy by remember { mutableStateOf(false) };var message by remember { mutableStateOf("") };var size by remember { mutableLongStateOf(0L) }
    LaunchedEffect(config) { size=AppDiagnostics.storageBytes() }
    fun exported(uri: android.net.Uri?,kind: DiagnosticExport) {
        if(uri==null)return
        busy=true
        scope.launch {
            try { AppDiagnostics.export(context,uri,kind);size=AppDiagnostics.storageBytes();message="Diagnoseprotokoll exportiert." }
            catch(e:Exception) { AppDiagnostics.error("diagnostic.export_failed",error=e);message="Export fehlgeschlagen. Bitte einen anderen Speicherort wählen." }
            finally {busy=false}
        }
    }
    val short=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { exported(it,DiagnosticExport.SHORT) }
    val full=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { exported(it,DiagnosticExport.FULL) }
    fun filename(kind: String)="Liquid-APK-Store-diagnostics-$kind-${SimpleDateFormat("yyyyMMdd-HHmm",Locale.ROOT).format(Date())}.zip"
    GlassPanel {
        HelpTitle("Entwicklerdiagnose","Standard erfasst Abläufe, Quellenfehler, Installationsereignisse und Abstürze. Ausführlich ergänzt technische Ereignisse, Laufzeiten und längere Aufrufketten. Kurzexport: bis zu 200 relevante Einträge. Vollständiger Export: alle gespeicherten Einträge, höchstens 7 Tage beziehungsweise 4 MiB. Es werden keine Anfrageinhalte, Zugangsdaten, vollständigen App-Listen oder Gerätekennungen aufgezeichnet.",showHelp=true)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Diagnoseprotokoll speichern",Modifier.weight(1f));Switch(config.enabled,{AppDiagnostics.configure(enabled=it)})
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!config.verbose,{AppDiagnostics.configure(verbose=false)},label={Text("Standard")})
            FilterChip(config.verbose,{AppDiagnostics.configure(verbose=true)},label={Text("Ausführlich")})
        }
        SecondaryText("Erfasst auch Hintergrundprüfungen. Gespeicherte Protokolle bleiben nach Neustarts erhalten. Bei beendeter App läuft keine dauerhafte Aufzeichnung.")
        SecondaryText("Gespeichert: ${size/1024} KiB · maximal 4 MiB / 7 Tage")
        if(AppDiagnostics.storageProblem())SecondaryText("Der Protokollspeicher konnte zuletzt nicht beschrieben werden.")
        OutlinedButton(onClick={short.launch(filename("short"))},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Kurzprotokoll exportieren") }
        OutlinedButton(onClick={full.launch(filename("full"))},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Vollständiges Protokoll exportieren") }
        TextButton(onClick={busy=true;scope.launch {try{AppDiagnostics.clear();size=0;message="Diagnoseprotokolle gelöscht."}catch(e:Exception){message="Protokolle konnten nicht gelöscht werden. Bitte erneut versuchen."}finally{busy=false}}},enabled=!busy) { Text("Diagnoseprotokolle löschen") }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank())SecondaryText(message)
    }
}
