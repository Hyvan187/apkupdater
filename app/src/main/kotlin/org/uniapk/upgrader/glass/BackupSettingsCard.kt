package org.uniapk.upgrader.glass

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.uniapk.upgrader.diagnostics.AppDiagnostics
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.settings.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable fun BackupSettingsCard(){
    val context=LocalContext.current;val p=koinInject<Prefs>();val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)};var status by remember{mutableStateOf("")}
    var passwordRequest by remember{mutableStateOf<Pair<Uri,Boolean>?>(null)}
    var preview by remember{mutableStateOf<BackupPreview?>(null)};var detailedConsent by remember{mutableStateOf(false)}
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->if(uri!=null)passwordRequest=uri to true}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)passwordRequest=uri to false}
    fun failure(e:Exception){status=if(e is BackupException)e.message.orEmpty() else "Die Datei konnte nicht verarbeitet werden. Bitte Zugriff und freien Speicher prüfen.";AppDiagnostics.error("backup.operation.failed",mapOf("failure" to e.javaClass.simpleName))}
    GlassPanel {
        HelpTitle("Backup & Wiederherstellung","Sichert alle App-Einstellungen und deine selbst gepflegten Listen: Design, Quellen, Prüflimits, Download- und Installerwahl, Geräteprofile, Favoriten, ignorierte Apps, übersprungene Versionen und Diagnoseoptionen. Eigene API-Zugänge sind enthalten; das Backup ist deshalb immer passwortgeschützt.",showHelp=true)
        SecondaryText("Die Datei wird nur an dem von dir gewählten Ort gespeichert. Das Passwort wird nicht gespeichert und kann nicht zurückgesetzt werden.")
        SecondaryText("Keine APK-Dateien, Caches, Protokolle, Installationsverläufe oder kurzlebigen Store-Anmeldesitzungen. Android-Berechtigungen und Ordnerfreigaben werden nicht übertragen.")
        Button(onClick={create.launch("UniAPK-settings-${SimpleDateFormat("yyyyMMdd-HHmm",Locale.ROOT).format(Date())}.uniapkbackup")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Einstellungen sichern")}
        OutlinedButton(onClick={open.launch(arrayOf("*/*"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Backup wiederherstellen")}
        if(busy){UniProgress(label="Backup wird verarbeitet");SecondaryText("Verschlüsselung und Prüfung laufen. Bitte kurz warten.")}
        if(status.isNotBlank())SecondaryText(status)
    }
    passwordRequest?.let{(uri,export)->BackupPasswordDialog(export,onDismiss={
        passwordRequest=null
        if(export)scope.launch(Dispatchers.IO){runCatching{android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri)}}
    }){password->
        passwordRequest=null;busy=true;status=""
        scope.launch{
            try{
                if(export){SettingsBackup.export(context,p,uri,password);status="Verschlüsseltes Einstellungsbackup gespeichert."}
                else{preview=SettingsBackup.inspect(context,p,uri,password);detailedConsent=false}
            }catch(e:Exception){failure(e)}finally{password.fill('\u0000');busy=false}
        }
    }}
    preview?.let{checked->AlertDialog(onDismissRequest={if(!busy)preview=null},title={Text("Einstellungen wiederherstellen?")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())){
        Text("Backup aus UniAPK ${checked.version}",style=MaterialTheme.typography.titleMedium)
        SecondaryText("Erstellt: ${DateFormat.getDateTimeInstance().format(Date(checked.createdAt))}")
        SecondaryText("${checked.settingsCount} Einstellungen · ${checked.credentials} eigene API-Zugänge")
        Spacer(Modifier.height(12.dp));Text("Deine aktuellen Einstellungen und Listen werden ersetzt. Heruntergeladene Dateien und der Installationsverlauf bleiben erhalten.")
        SecondaryText("Die App übernimmt das Design sofort. Ein gespeicherter Installer muss auf diesem Gerät vorhanden sein. Automatische Downloads warten, falls die Ordnerfreigabe fehlt.")
        if(checked.detailedDiagnostics){
            Spacer(Modifier.height(12.dp));Text("Dieses Backup aktiviert erweiterte Diagnose.",color=MaterialTheme.colorScheme.error)
            SecondaryText("Sie kann technische Geräte-/Displaydaten, beteiligte Paketnamen und deine selbst gepflegten App-Listen erfassen. Zugangsdaten und private Dateipfade bleiben ausgeschlossen.")
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(detailedConsent,{detailedConsent=it});Text("Erweiterte Diagnose zulassen",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)}
        }
    }},confirmButton={Button(onClick={
        busy=true;preview=null
        scope.launch{try{status=SettingsBackup.restore(context,p,checked)}catch(e:Exception){failure(e)}finally{busy=false}}
    },enabled=!busy&&(!checked.detailedDiagnostics||detailedConsent)){Text("Wiederherstellen")}},dismissButton={TextButton(onClick={preview=null},enabled=!busy){Text("Abbrechen")}})}
}

@Composable private fun BackupPasswordDialog(creating:Boolean,onDismiss:()->Unit,onConfirm:(CharArray)->Unit){
    var password by remember{mutableStateOf("")};var confirmation by remember{mutableStateOf("")}
    val valid=if(creating)password.length in 12..256&&password==confirmation else password.isNotEmpty()
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(creating)"Backup-Passwort festlegen" else "Backup entsperren")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())){
        SecondaryText(if(creating)"Mindestens 12 Zeichen. Bewahre das Passwort sicher auf; ohne dieses Passwort kann das Backup nicht wiederhergestellt werden." else "Gib das Passwort ein, mit dem dieses Backup erstellt wurde. Die Prüfung verändert noch keine Einstellungen.")
        OutlinedTextField(password,{password=it.take(256)},label={Text("Backup-Passwort")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),singleLine=true,modifier=Modifier.fillMaxWidth())
        if(creating)OutlinedTextField(confirmation,{confirmation=it.take(256)},label={Text("Passwort wiederholen")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),singleLine=true,modifier=Modifier.fillMaxWidth())
        if(creating&&password.isNotEmpty()&&password.length<12)SecondaryText("Noch ${12-password.length} Zeichen bis zur Mindestlänge.")
        if(creating&&confirmation.isNotEmpty()&&password!=confirmation)SecondaryText("Die Passwörter stimmen noch nicht überein.")
    }},confirmButton={Button(onClick={val copy=password.toCharArray();password="";confirmation="";onConfirm(copy)},enabled=valid){Text(if(creating)"Backup erstellen" else "Entsperren und prüfen")}},dismissButton={TextButton(onClick={password="";confirmation="";onDismiss()}){Text("Abbrechen")}})
}
