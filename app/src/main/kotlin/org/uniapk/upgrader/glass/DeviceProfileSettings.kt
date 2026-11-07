package org.uniapk.upgrader.glass
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.uniapk.upgrader.prefs.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DeviceProfileSettings() {
    val prefs=koinInject<Prefs>();val context=LocalContext.current
    var preset by remember{mutableStateOf(prefs.simulatedPreset.get())};var region by remember{mutableStateOf(prefs.simulatedRegion.get())};var menu by remember{mutableStateOf(false)};var regionMenu by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    var comparison by remember{mutableStateOf(prefs.manualRegion.get())}
    val detected by produceState<RomResult?>(null){value=withContext(Dispatchers.IO){DeviceDetection(context,prefs).rom()}}
    val variant=TestDeviceProfiles.resolve(preset,region)
    fun save(){
        if(preset!="ACTUAL"&&variant==null){message="Diese regionale Kennung ist noch nicht belegt und wird nicht erfunden.";return}
        prefs.simulatedPreset.put(preset);prefs.simulatedRegion.put(region);prefs.simulatedModel.put(variant?.code.orEmpty());prefs.playAuthData.put(com.aurora.gplayapi.data.models.AuthData("",""));prefs.lastPlayCheck.put(0)
        message=if(preset=="ACTUAL")"Echtes Geräteprofil aktiv." else "Testprofil gespeichert. Die nächste Play-Anmeldung verwendet die belegte Modellkennung."
    }
    GlassPanel {
        HelpTitle("Gerät, ROM und Testprofil",HelpText.PROFILE)
        SecondaryText("Erkannte Geräte-ROM: ${detected?.label ?: "Wird ausgelesen …"}")
        if(detected?.region==Region.UNKNOWN)SecondaryText(detected?.detail.orEmpty())
        Text("Manuelle Vergleichsregion",style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){(listOf("AUTO")+TestDeviceProfiles.regions.keys).forEach{key->FilterChip(comparison==key,{comparison=key;prefs.manualRegion.put(key)},label={Text(if(key=="AUTO")"Automatisch" else key)})}}
        SecondaryText("Installationsprüfung: tatsächliche ROM ${detected?.region?.name ?: "UNKNOWN"}. Eine manuelle Vergleichsregion ersetzt keinen Herkunftsnachweis.")
        Text("Testgerät für die Store-Abfrage",style=MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth()){Text(TestDeviceProfiles.presets.find{it.model==preset}?.name ?: "Echtes Gerät")}
        OutlinedButton(onClick={regionMenu=true},modifier=Modifier.fillMaxWidth(),enabled=preset!="ACTUAL"){Text("Länderversion: ${TestDeviceProfiles.regions[region] ?: region}")}
        if(preset!="ACTUAL")SecondaryText(variant?.let{"Belegte Modellkennung: ${it.code}${it.csc.takeIf{it.isNotBlank()}?.let{" · CSC $it"}.orEmpty()}"} ?: "Für diese Länderfassung ist keine belegte Modellkennung hinterlegt. Anwenden ist deshalb gesperrt.")
        OutlinedButton(onClick={save()},enabled=preset=="ACTUAL"||variant!=null){Text("Testprofil anwenden")}
        SecondaryText("Modell und Länderprofil sind Suchhinweise. Google Play berücksichtigt auch Konto, IP-Adresse und Verfügbarkeit. Eine Länderfreigabe und passende Hersteller-Apps lassen sich damit nicht garantieren; die Installation wird immer am echten Gerät geprüft.")
        if(message.isNotBlank())SecondaryText(message)
    }
    if(menu)GlassChoiceSheet("Testgerät",preset,listOf(GlassChoice("ACTUAL","Echtes Gerät"))+TestDeviceProfiles.presets.map{GlassChoice(it.model,it.name)},{menu=false}){preset=it}
    if(regionMenu)GlassChoiceSheet("Länderversion",region,TestDeviceProfiles.regions.map{(key,name)->GlassChoice(key,name,if(TestDeviceProfiles.resolve(preset,key)==null)"Noch keine belegte Modellkennung" else "Belegte Modellkennung vorhanden")},{regionMenu=false}){region=it}
}
