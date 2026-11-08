package org.uniapk.upgrader.glass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.uniapk.upgrader.prefs.Prefs
import org.koin.compose.koinInject

@Composable fun SourceApiSettings(onChanged:()->Unit) {
    val prefs=koinInject<Prefs>();var mirror by remember{mutableStateOf(prefs.apkMirrorKey.get())};var github by remember{mutableStateOf(prefs.githubToken.get())};var gitlab by remember{mutableStateOf(prefs.gitlabToken.get())};var saved by remember{mutableStateOf(false)}
    GlassPanel {
        HelpTitle("Optionale API-Zugänge","APKMirror: eigenen zulässigen API-Schlüssel eintragen. Kein aktuell freigegebener allgemeiner Schlüssel ist voreingestellt. Ohne Schlüssel öffnet die Quellenauswahl die Websuche zur jeweiligen App. GitHub/GitLab funktionieren für öffentliche Releases auch ohne Token; eigene Tokens können Zugriffsgrenzen erhöhen. Die Werte werden verschlüsselt nur auf diesem Gerät gespeichert und nicht protokolliert.")
        OutlinedTextField(mirror,{mirror=it.take(2048);saved=false},label={Text("APKMirror API-Schlüssel")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(github,{github=it.take(2048);saved=false},label={Text("GitHub-Token · optional")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(gitlab,{gitlab=it.take(2048);saved=false},label={Text("GitLab-Token · optional")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedButton(onClick={prefs.apkMirrorKey.put(mirror.trim());prefs.githubToken.put(github.trim());prefs.gitlabToken.put(gitlab.trim());ProviderCooldown.retry("ApkMirror");saved=true;onChanged()}){Text("Zugänge speichern")}
        if(saved)SecondaryText("Gespeichert. Leere Felder entfernen den jeweiligen Zugang.")
        SecondaryText("F-Droid, IzzyOnDroid und Aptoide benötigen keinen persönlichen Schlüssel. Google Play verwendet die vorhandene anonyme Aurora-Anmeldung; die Länderfreigabe bleibt vom Store abhängig. APKPure verwendet seine vorhandene Anbieteranbindung.")
    }
}
@Composable fun HiddenVersions(onChanged:()->Unit) {
    val prefs=koinInject<Prefs>();val context=LocalContext.current
    var entries by remember{mutableStateOf(SkippedVersions.entries(prefs))};var legacy by remember{mutableStateOf(prefs.ignoredVersions.get())};var open by remember{mutableStateOf(false)}
    OutlinedButton(onClick={entries=SkippedVersions.entries(prefs);legacy=prefs.ignoredVersions.get();open=true}){Text("Ausgeblendete Versionen (${entries.size+legacy.size})")}
    if(open)GlassMenuSheet(onDismiss={open=false}) {
        Text("Ausgeblendete Versionen",style=MaterialTheme.typography.titleMedium)
        if(entries.isEmpty()&&legacy.isEmpty())SecondaryText("Keine Versionen ausgeblendet.")
        LazyColumn(Modifier.weight(1f,fill=false).heightIn(max=420.dp)) {
            items(entries.distinct(),key={it}){key->val pkg=key.packageName;val name=remember(pkg){runCatching{context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg,0)).toString()}.getOrDefault(pkg)}
                Column(Modifier.fillMaxWidth().padding(vertical=spacing(8))){Text(name);SecondaryText("$pkg · ${key.version}${if(key.code>0)" · Build ${key.code}" else " · ohne Build-Code"}");TextButton(onClick={SkippedVersions.restore(key,prefs);entries=SkippedVersions.entries(prefs);onChanged()}){Text("Wieder einblenden")}}
            }
            items(legacy,key={it}){id->Row{Text("Älterer Eintrag: $id",Modifier.weight(1f));TextButton(onClick={legacy=legacy-id;prefs.ignoredVersions.put(legacy);onChanged()}){Text("Einblenden")}}}
        }
        TextButton(onClick={open=false}){Text("Schließen")}
    }
}
@Composable fun ComputeSettings() {
    val context=LocalContext.current
    val prefs=koinInject<Prefs>();val current by ComputeBudget.state.collectAsState();var requested by remember{mutableIntStateOf(prefs.cpuTasks.get())};var menu by remember{mutableStateOf(false)}
    GlassPanel {
        HelpTitle("Leistungsbudget","Begrenzt gleichzeitig ausgeführte rechenintensive UniAPK-Aufgaben. Android verteilt UI-, Netzwerk- und Grafikthreads weiterhin selbst auf die CPU-Kerne. Dies ist keine feste Kernzuweisung oder prozentuale Gesamtauslastungsgrenze. Weniger Aufgaben reduzieren zusätzlich Bildrate und Dichte des Hintergrunds.")
        OutlinedButton(onClick={menu=true}){Text(if(requested==0)"Automatisch · Aufgabenlimit: $current" else "CPU-Aufgabenlimit: $current")}
        SecondaryText("Verfügbare logische Prozessoren: ${Runtime.getRuntime().availableProcessors()} · Hintergrund bis ${ComputeBudget.fps()} Bilder/s")
    }
    if(menu)GlassChoiceSheet("CPU-Aufgaben",requested.toString(),listOf(GlassChoice("0","Automatisch","Passt Aufgaben und Effekte an Energiesparmodus, verfügbaren Speicher und Temperatur an."))+(1..Runtime.getRuntime().availableProcessors().coerceAtLeast(1)).map{GlassChoice(it.toString(),"$it ${if(it==1)"Aufgabe" else "Aufgaben"}")},{menu=false}){val n=it.toInt();requested=n;prefs.cpuTasks.put(n);ComputeBudget.set(n);ComputeBudget.refresh(context)}
}

