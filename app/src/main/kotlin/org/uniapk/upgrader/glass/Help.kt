package org.uniapk.upgrader.glass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

object HelpText {
 const val CHECK="Nur nach einer ausdrücklich gewählten Downloadaktion wird die APK in den privaten App-Speicher geladen. Die Metadatensuche und das Lesen von Prüfinformationen laden keine APK-Datei; automatische Downloads sind eine separat aktivierbare Ausnahme. Reine Sammelprüfungen löschen ihre temporären Dateien nach der Analyse. Geprüft werden Paketname, Version, Signatur, Android, Architektur, Paketbestandteile und erkennbare Abhängigkeiten. Dabei wird nichts installiert oder ausgeführt. Eindeutige APK-Sets werden gemeinsam geprüft; Variantenarchive und OBB-Dateien bleiben gesperrt. Unbekanntes bleibt offen; eine Funktionsgarantie ist nicht möglich."
 const val BLOCKED="Blockiert bedeutet: Mindestens eine geprüfte Voraussetzung passt nicht, etwa Signatur, Android-Version, Architektur oder Versionszweig. Die App bietet dafür keine Installation an."
 const val RESTORE="Entfernt nur die Liste ausgeblendeter Versionsfunde. Diese können bei der nächsten Suche wieder erscheinen. Es wird keine installierte App zurückgesetzt, deinstalliert oder auf eine ältere Version gebracht. Ignorierte Apps bleiben ignoriert."
 const val PROFILE="Wähle zuerst ein Produkt und separat Global, Europa, Indien oder China. Die App setzt nur belegte regionale Modellkennungen ein; fehlende Kombinationen bleiben gesperrt. Ein öffentlicher Modellcode bezeichnet eine Produktvariante, kein individuelles Telefon. Das Profil wird bei der nächsten Google-Play-Anmeldung verwendet. Konto-/IP-Länderfreigaben kann es nicht erzwingen. Andere Quellen unterstützen nicht zwingend Gerätesimulation. Die Installationsprüfung verwendet immer Android, Architektur, Signatur und nachgewiesene ROM des echten Geräts. Keine IMEI, Seriennummer oder persönliche Build-Fingerprints eingeben."


 const val EXTERNAL="Übergibt nur eine zuvor geprüfte APK an eine installierte App, die APK-Dateien öffnen kann. Deren Installation bleibt nutzerbestätigt. Rückmeldung und Fortschritt sind nicht immer verfügbar; der Verlauf wird erst nach bestätigtem neuem Paketstand ergänzt."
}
@Composable fun HelpButton(title: String,body: String) {
 val language=LocalDisplayLanguage.current
 var open by remember{mutableStateOf(false)}
 IconButton(onClick={open=true},modifier=Modifier.size(48.dp).semantics{contentDescription=(if(language=="en")"Help: " else "Hilfe: ")+UiEnglish.translate(title,language)}){Icon(Icons.Outlined.Info,contentDescription=null,modifier=Modifier.size(20.dp))}
 if(open)AlertDialog(onDismissRequest={open=false},title={Text(title)},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){Text(body)}},confirmButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={open=false}){Text("Verstanden")}})
}
object HelpTopics {
 fun technical(title: String) = title in setOf("Funde und vorbereitete APKs", "Gerät, ROM und Testprofil", "CPU und Glaseffekte", "Optionale API-Zugänge", "Prüfstatus", "App-Auswahl", "Wie werden APKs geprüft?", "Automatische APK-Prüfung", "Hintergrundsuche", "Quellenstatus", "Downloads und Installation", "Installer", "Region und Testprofil", "OLED-Optimierung", "Flüssiges Glas", "Parallax-Effekt", "Stärkere Navigationsunschärfe", "Google Play", "F-Droid", "Automatische APK-Prüfung nur ungetaktet", "Hintergrundsuche nur ungetaktet") || listOf("Einzellimit:", "Gesamtbudget:", "Apps pro Durchlauf:", "Effektqualität:", "Glasdeckkraft:").any(title::startsWith)
}
@Composable fun HelpTitle(title: String,body: String,showHelp: Boolean = HelpTopics.technical(title)){Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall);if(showHelp)HelpButton(title,body)}}
@Composable fun ValueSlider(title: String,value: Float,default: Float,onChange: (Float)->Unit,range: ClosedFloatingPointRange<Float>,steps: Int=0,body: String=title,finished:(()->Unit)?=null) {
 HelpTitle(title,body)
 Slider(value,onChange,valueRange=range,steps=steps,onValueChangeFinished=finished)
 TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={onChange(default);finished?.invoke()},enabled=kotlin.math.abs(value-default)>.0001f){Text("Standard wiederherstellen")}
}
