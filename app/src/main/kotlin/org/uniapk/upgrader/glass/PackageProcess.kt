package org.uniapk.upgrader.glass

import org.uniapk.upgrader.data.ui.AppUpdate
import org.uniapk.upgrader.viewmodel.UpdatesViewModel
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

enum class PackagePhase { DOWNLOAD, INSPECT, READY, INSTALLING, DONE, FAILED, CANCELLED }
data class PackageProcess(val update:AppUpdate,val phase:PackagePhase=PackagePhase.DOWNLOAD,val bytes:Long=0,val total:Long?=null,val evidence:List<Evidence> = emptyList(),val message:String="")
@Composable fun PackageProcessDialog(viewModel:UpdatesViewModel) {
    val process by viewModel.process.collectAsState();val risk by viewModel.riskConfirmation.collectAsState();if(risk!=null)return;val p=process ?: return;val uri=LocalUriHandler.current
    val active=p.phase in setOf(PackagePhase.DOWNLOAD,PackagePhase.INSPECT,PackagePhase.INSTALLING)
    AlertDialog(onDismissRequest={viewModel.hideProcess()},title={Text(p.update.name)},text={
        LazyColumn(Modifier.heightIn(max=430.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item{Text(when(p.phase){PackagePhase.DOWNLOAD->"APK wird heruntergeladen";PackagePhase.INSPECT->"APK und Paketbestandteile werden geprüft";PackagePhase.READY->"Technisch geprüft · bereit zur Installation";PackagePhase.INSTALLING->"Android-Installation";PackagePhase.DONE->"Installiert · im Verlauf gespeichert";PackagePhase.FAILED->"Vorgang nicht abgeschlossen";PackagePhase.CANCELLED->"Vorgang abgebrochen"})}
            if(p.phase==PackagePhase.DOWNLOAD)item{
                val total=p.total
                if(total!=null&&total>0){val fraction=(p.bytes.toFloat()/total).coerceIn(0f,1f);UniProgress(fraction,label="APK-Download: ${(fraction*100).toInt()} Prozent");Text("${(fraction*100).toInt()} % · ${ResourcePolicy.label(p.bytes)} / ${ResourcePolicy.label(total)}")}
                else{UniProgress(label="APK wird verarbeitet");SecondaryText("${ResourcePolicy.label(p.bytes)} geladen · Anbieter nennt keine Gesamtgröße, daher keine belastbare Prozentangabe.")}
            }
            if(p.phase==PackagePhase.INSTALLING)item{UniProgress(label="Warten auf Android-Installation");SecondaryText("Bestätige die Installation im Android-Dialog.")}
            if(p.phase==PackagePhase.INSPECT)item{UniProgress(label="APK wird verarbeitet");SecondaryText("Manifest lesen; Paketname und Version, Signatur, Android, Architektur, Splits und erkennbare Abhängigkeiten mit der echten Installation abgleichen.")}
            items(p.evidence.size){i->val e=p.evidence[i];StatusLine(when(e.status){Compatibility.COMPATIBLE->StatusTone.SUCCESS;Compatibility.INCOMPATIBLE->StatusTone.ERROR;else->StatusTone.WARNING},e.property,e.detail)}
            if(p.message.isNotBlank())item{SecondaryText(p.message)}
            if(p.phase==PackagePhase.READY)item{RiskSummary(p.update)}
            if(p.phase==PackagePhase.READY)item{SecondaryText("Schließen hält die Datei vorübergehend unter Bereit. Nach einer Stunde oder bei Speicherknappheit kann eine erneute ausdrückliche Downloadaktion nötig sein.")}
        }
    },confirmButton={
        if(p.phase==PackagePhase.READY)Button(onClick={viewModel.install(p.update,uri)}){Text("Jetzt installieren")}
        else if(p.phase==PackagePhase.FAILED&&p.update.fileReady&&p.update.verdict.installAllowed)TextButton(onClick={viewModel.install(p.update,uri)}){Text("Installation erneut starten")}
        else if(p.phase==PackagePhase.FAILED||p.phase==PackagePhase.CANCELLED)TextButton(onClick={viewModel.verify(p.update)}){Text("Erneut laden und prüfen")}
        else TextButton(onClick={viewModel.hideProcess()}){Text(if(active)"Im Hintergrund fortsetzen" else "Schließen")}
    },dismissButton={if(active&&p.phase!=PackagePhase.INSTALLING)TextButton(onClick={viewModel.cancelVerification()}){Text("Abbrechen")}else if(p.phase==PackagePhase.READY)TextButton(onClick={viewModel.hideProcess()}){Text("Später installieren")}})
}

@Composable fun RiskSummary(update:AppUpdate){val risk=UpdateRisk.assess(update);StatusLine(when(risk.level){RiskLevel.LOWER->StatusTone.SUCCESS;RiskLevel.BLOCKED->StatusTone.ERROR;else->StatusTone.WARNING},risk.level.label,risk.category);SecondaryText(risk.summary)}
@Composable fun RiskConfirmationDialog(viewModel:UpdatesViewModel){val update by viewModel.riskConfirmation.collectAsState();val u=update ?: return;AlertDialog(onDismissRequest=viewModel::dismissRisk,title={Text("Kritische Komponente aktualisieren?")},text={Column{Text(u.name);RiskSummary(u);SecondaryText("Die technische Prüfung ersetzt keine Funktionsprüfung. Die vorhandenen Sperren bleiben bestehen.")}},confirmButton={TextButton(onClick=viewModel::confirmRisk){Text("Risiko verstanden · fortfahren")}},dismissButton={TextButton(onClick=viewModel::dismissRisk){Text("Abbrechen")}})}
