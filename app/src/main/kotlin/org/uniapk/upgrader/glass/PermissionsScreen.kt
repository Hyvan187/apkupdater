package org.uniapk.upgrader.glass

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.uniapk.upgrader.prefs.Prefs

data class PermissionSnapshot(val install:Boolean,val notifications:Boolean)
object PermissionPolicy {
    fun snapshot(c:android.content.Context)=PermissionSnapshot(if(Build.VERSION.SDK_INT>=26)c.packageManager.canRequestPackageInstalls() else Settings.Secure.getInt(c.contentResolver,Settings.Secure.INSTALL_NON_MARKET_APPS,0)==1,NotificationManagerCompat.from(c).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||androidx.core.content.ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED))
}
@Composable fun PermissionsScreen(onBack:()->Unit,firstRun:Boolean=false) {
    val c=LocalContext.current;val prefs=koinInject<Prefs>();val scope=rememberCoroutineScope();val lifecycle=LocalLifecycleOwner.current.lifecycle
    var revision by remember{mutableIntStateOf(0)};var snapshot by remember{mutableStateOf(PermissionPolicy.snapshot(c))};var folder by remember{mutableStateOf<FolderStatus?>(null)};var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)revision++};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
    LaunchedEffect(revision){snapshot=PermissionPolicy.snapshot(c);folder=withContext(Dispatchers.IO){FolderAccess.status(c,prefs)}}
    val settings=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){revision++}
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){revision++}
    val chooseFolder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null)scope.launch{busy=true;try{folder=withContext(Dispatchers.IO){FolderAccess.select(c,prefs,uri)};message="Ordner freigegeben."}catch(e:Exception){message=e.message ?: "Ordner konnte nicht freigegeben werden."}finally{busy=false;revision++}}}
    fun open(intent:Intent){runCatching{settings.launch(intent)}.onFailure{message="Diese Systemeinstellung ist nicht erreichbar. Bitte die Android-App-Info öffnen."}}
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=LocalDeviceLayout.current.horizontalPadding)){
        ScreenHeader("Berechtigungen",if(firstRun)"UNIAPK EINRICHTEN" else "ZUGRIFFE VERWALTEN",onBack=if(firstRun)null else onBack)
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(),contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(spacing(12))){
            item{SecondaryText("Erlaube die Zugriffe, die du nutzen möchtest. Die Update-Suche funktioniert auch ohne Installations- und Benachrichtigungsfreigabe.")}
            item{GlassPanel{
                StatusLine(if(snapshot.install)StatusTone.SUCCESS else StatusTone.WARNING,"Apps installieren",if(snapshot.install)"Freigegeben" else "Für die Installation erforderlich")
                SecondaryText("Android bestätigt jede Installation weiterhin einzeln.")
                OutlinedButton(onClick={open(if(Build.VERSION.SDK_INT>=26)Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${c.packageName}")) else Intent(Settings.ACTION_SECURITY_SETTINGS))}){Text(if(snapshot.install)"Freigabe verwalten" else "Installation erlauben")}
            }}
            item{GlassPanel{
                StatusLine(if(snapshot.notifications)StatusTone.SUCCESS else StatusTone.WARNING,"Benachrichtigungen",if(snapshot.notifications)"Erlaubt" else "Optional · derzeit aus")
                SecondaryText("Meldet Ergebnisse der Hintergrundsuche. Ohne Freigabe bleiben die Ergebnisse in der App sichtbar.")
                OutlinedButton(onClick={if(Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else open(if(Build.VERSION.SDK_INT>=26)Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}")))}){Text(if(snapshot.notifications)"Benachrichtigungen verwalten" else "Benachrichtigungen erlauben")}
                if(!snapshot.notifications)TextButton(onClick={open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}")))}){Text("Android-App-Info öffnen")}
            }}
            item{GlassPanel{
                StatusLine(if(folder?.writable==true)StatusTone.SUCCESS else StatusTone.WARNING,"APK-Ordner",folder?.headline ?: "Ordner wird geprüft")
                folder?.let{SecondaryText(it.location)}
                SecondaryText("Für automatische Downloads ist ein eigener beschreibbarer Ordner nötig. Du gibst nur diesen Ordner frei.")
                OutlinedButton(onClick={chooseFolder.launch(prefs.downloadTree.get().takeIf{it.isNotBlank()}?.let(Uri::parse))},enabled=!busy){Text(if(folder?.selected==true)"Ordner ändern" else "Ordner auswählen")}
                if(busy)UniProgress(label="Ordnerfreigabe wird geprüft")
            }}
            item{GlassPanel{
                StatusLine(StatusTone.INFO,"Update-Suche und Diagnose","Keine zusätzliche Abfrage nötig")
                SecondaryText("Internetzugriff und das Lesen sichtbarer App-Versionen sind bereits deklariert. Lokale Protokolle und Prüfdateien liegen im privaten App-Speicher. Beim Export wählst du die Zieldatei selbst.")
                SecondaryText("Zugriff auf alle Dateien, Kontakte, Standort, Mikrofon oder Kamera ist dafür nicht nötig.")
            }}
            if(message.isNotBlank())item{SecondaryText(message)}
            item{Button(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text(if(firstRun)"Weiter zur App" else "Fertig")}}
        }
    }
}

