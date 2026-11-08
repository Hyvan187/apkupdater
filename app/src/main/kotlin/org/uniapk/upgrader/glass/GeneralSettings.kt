package org.uniapk.upgrader.glass

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.WorkManager
import org.koin.compose.koinInject
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.worker.UpdatesWorker

@Composable fun GeneralSettings(){
    val p=koinInject<Prefs>();var menu by remember{mutableStateOf(false)};var start by remember{mutableStateOf(StartViews.valid(p.startView.get()))}
    GlassPanel {
        Text("Startansicht",style=MaterialTheme.typography.titleSmall)
        SecondaryText("Diese Ansicht öffnet sich beim nächsten App-Start.")
        OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth()){Text(StartViews.labels[StartViews.routes.indexOf(start)])}
        if(menu)GlassChoiceSheet("Startansicht",start,StartViews.routes.mapIndexed{i,id->GlassChoice(id,StartViews.labels[i])},{menu=false}){start=it;p.startView.put(it)}
    }
}
@Composable fun QuickAccessSettings(){
    val p=koinInject<Prefs>();var selected by remember{mutableStateOf(p.quickAccess.get())}
    GlassPanel {
        Text("Schnellzugriff",style=MaterialTheme.typography.titleSmall)
        SecondaryText("Wähle die Schalter für die Schnellstart-Leiste auf der Startübersicht.")
        listOf("stable" to "Nur stabile Versionen","background" to "Hintergrundsuche").forEach{(id,label)->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){
                Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                Checkbox(id in selected,{checked->selected=if(checked)(selected+id).distinct() else selected-id;p.quickAccess.put(selected)})
            }
        }
        if(selected.isEmpty())SecondaryText("Die Schnellstart-Leiste ist ausgeblendet.")
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun QuickAccessBar(onFiltersChanged:()->Unit){
    val p=koinInject<Prefs>();val context=LocalContext.current;val selected=p.quickAccess.get()
    val filters=koinInject<ReleaseFilterStore>();val flags by filters.state.collectAsState()
    var background by remember{mutableStateOf(p.enableAlarm.get())}
    if(selected.isNotEmpty())GlassPanel(compact=true){
        Text("Schnellzugriff",style=MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if("stable" in selected)FilterChip(flags.preview,{filters.change(preview=!flags.preview);onFiltersChanged()},label={Text("Nur stabile Versionen")})
            if("background" in selected)FilterChip(background,{background=!background;p.enableAlarm.put(background);if(background)UpdatesWorker.launch(WorkManager.getInstance(context)) else UpdatesWorker.cancel(WorkManager.getInstance(context))},label={Text("Hintergrundsuche")})
        }
    }
}

/** Kept inline in Allgemein, so permissions never add a third settings level. */
@Composable fun PermissionAccessSettings(){
    val c=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    var snapshot by remember{mutableStateOf(PermissionPolicy.snapshot(c))};var message by remember{mutableStateOf("")}
    val activity=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){snapshot=PermissionPolicy.snapshot(c)}
    val notification=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){snapshot=PermissionPolicy.snapshot(c)}
    DisposableEffect(lifecycle){val o=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_RESUME)snapshot=PermissionPolicy.snapshot(c)};lifecycle.addObserver(o);onDispose{lifecycle.removeObserver(o)}}
    fun open(i:Intent){runCatching{activity.launch(i)}.onFailure{message="Android-Einstellung nicht erreichbar. Bitte die App-Info öffnen."}}
    GlassPanel {
        Text("Berechtigungen",style=MaterialTheme.typography.titleSmall)
        StatusLine(if(snapshot.install)StatusTone.SUCCESS else StatusTone.WARNING,"Apps installieren",if(snapshot.install)"Freigegeben" else "Vor der ersten Installation erforderlich")
        OutlinedButton(onClick={open(if(Build.VERSION.SDK_INT>=26)Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${c.packageName}")) else Intent(Settings.ACTION_SECURITY_SETTINGS))}){Text("Installationsfreigabe verwalten")}
        StatusLine(if(snapshot.notifications)StatusTone.SUCCESS else StatusTone.WARNING,"Benachrichtigungen",if(snapshot.notifications)"Erlaubt" else "Optional · ausgeschaltet")
        OutlinedButton(onClick={if(Build.VERSION.SDK_INT>=33&&!snapshot.notifications)notification.launch(Manifest.permission.POST_NOTIFICATIONS) else open(if(Build.VERSION.SDK_INT>=26)Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}")))}){Text("Benachrichtigungen verwalten")}
        SecondaryText("Die reine Suche funktioniert ohne diese Freigaben. Den APK-Ordner wählst du unter Updates & Versionen. Zugriff auf alle Dateien, Kontakte oder Standort wird nicht benötigt.")
        if(message.isNotBlank())SecondaryText(message)
    }
}
