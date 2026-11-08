package org.uniapk.upgrader.glass

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.uniapk.upgrader.diagnostics.AppDiagnostics
import org.uniapk.upgrader.diagnostics.DiagnosticExport
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DiagnosticSettingsCard() {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val config by AppDiagnostics.state.collectAsState()
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")};var size by remember{mutableLongStateOf(0)}
    var confirmation by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){while(true){size=AppDiagnostics.storageBytes();delay(2000)}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->
        if(uri!=null){busy=true;scope.launch{try{AppDiagnostics.export(context,uri,if(AppDiagnostics.state.value.verbose)DiagnosticExport.FULL else DiagnosticExport.SHORT);message="Diagnose exportiert. Vor dem Teilen den Inhalt prüfen."}catch(e:Exception){message="Export fehlgeschlagen: ${e.message}"}finally{busy=false}}}
    }
    fun launchExport(){val mode=if(config.verbose)"detailed" else "errors";export.launch("UniAPK-diagnostics-$mode-${SimpleDateFormat("yyyyMMdd-HHmm",Locale.ROOT).format(Date())}.zip")}
    GlassPanel {
        HelpTitle("Diagnoseprotokolle","Automatische Aufzeichnung speichert Ereignisse ausschließlich im privaten Speicher dieser App. Der Ordner ist für andere Apps nicht frei lesbar; Android löscht ihn bei Deinstallation. Erst Exportieren erzeugt eine ZIP an dem von dir gewählten Ort. Im Hintergrund werden nur tatsächlich ausgeführte UniAPK-Vorgänge erfasst. Es gibt keine dauerhafte systemweite Überwachung.",showHelp=true)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Automatisch lokal aufzeichnen",Modifier.weight(1f));Switch(config.enabled,{AppDiagnostics.configure(enabled=it)})}
        Text("Umfang der Aufzeichnung und des Exports",style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(spacing(8))) {
            FilterChip(!config.verbose,{AppDiagnostics.configure(verbose=false)},label={Text("Allgemeine Diagnose")})
            FilterChip(config.verbose,{if(!config.verbose)confirmation="capture"},label={Text("Erweiterte Diagnose")})
        }
        SecondaryText("Jeder Export enthält eine aktuelle Einstellungsübersicht und einen Laufzeitbericht: Design und Effekte, Such- und Downloadregeln, Quellenstatus, Berechtigungen, Speicher und Leistungsbudget. Automatische Schnappschüsse ordnen Änderungen und Vorgänge zeitlich zu.")
        SecondaryText(if(config.verbose)"Enthält allgemeine und tiefe technische Fehler, Geräte-/Displaykonfiguration, Netzart, beteiligte App-Paketnamen und selbst gepflegte App-Listen. Diese Angaben können Rückschlüsse auf dein Gerät und deine App-Nutzung erlauben." else "Ablaufübersicht und allgemeine Fehler. Im Export werden Geräteprofile, App-Paketnamen, Fehlermeldungstexte und Aufrufketten entfernt. Höchstens 200 Einträge; bei Bedarf erweiterte Diagnose aktivieren und den Fehler erneut auslösen.")
        SecondaryText("API-Zugänge werden nur als eingerichtet oder nicht eingerichtet ausgewiesen. Keine Passwörter, Tokens, IMEI, Seriennummern, Nachrichten, Datei- oder Anfrageinhalte. Auch bereinigte Protokolle vor dem Teilen kontrollieren.")
        Text("Alte Logdateien automatisch entfernen",style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(spacing(8))){listOf(3,5,10).forEach{n->FilterChip(config.maxFiles==n,{AppDiagnostics.configure(maxFiles=n)},label={Text("$n Dateien")})}}
        SecondaryText("Je Datei höchstens 512 KiB, außerdem maximal 7 Tage. Beim Erreichen der Grenze wird die älteste Datei entfernt. Dies betrifft lokale Logdateien, keine bereits exportierten ZIPs.")
        SecondaryText("Aktuell ${ResourcePolicy.label(size)} · Obergrenze ${ResourcePolicy.label(config.maxFiles*512L*1024)}")
        if(AppDiagnostics.storageProblem())SecondaryText("Der Protokollspeicher konnte zuletzt nicht beschrieben werden.")
        OutlinedButton(onClick={if(config.verbose)confirmation="export" else launchExport()},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Gewählte Diagnose als ZIP exportieren")}
        TextButton(onClick={busy=true;scope.launch{try{AppDiagnostics.clear();size=0;message="Lokale Diagnoseprotokolle gelöscht."}catch(e:Exception){message="Löschen fehlgeschlagen."}finally{busy=false}}},enabled=!busy){Text("Lokale Diagnose löschen")}
        if(busy)UniProgress(label="Diagnose wird verarbeitet")
        if(message.isNotBlank())SecondaryText(message)
    }
    confirmation?.let{action->AlertDialog(onDismissRequest={confirmation=null},title={Text("Technische Gerätedaten enthalten")},text={Text("Die erweiterte Diagnose enthält unter anderem Modell/Hersteller, Android-/Displaykonfiguration, Sprache, Zeitzone, Netzart, beteiligte App-Paketnamen und selbst gepflegte Listen mit Favoriten oder ausgeschlossenen Versionen. Das kann Rückschlüsse auf Gerät und App-Nutzung ermöglichen. Zugangsdaten, eindeutige Hardwarekennungen und persönliche Inhalte werden weiterhin ausgeschlossen. ${if(action=="export")"Die ZIP nur bewusst weitergeben." else "Ab jetzt gilt dieser Umfang für neu erfasste Ereignisse."}")},confirmButton={TextButton(onClick={confirmation=null;if(action=="export")launchExport() else AppDiagnostics.configure(verbose=true)}){Text(if(action=="export")"Bewusst exportieren" else "Erweiterte Aufzeichnung aktivieren")}},dismissButton={TextButton(onClick={confirmation=null}){Text("Abbrechen")}})}
}

