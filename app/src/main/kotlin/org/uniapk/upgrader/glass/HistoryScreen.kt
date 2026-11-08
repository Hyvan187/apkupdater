package org.uniapk.upgrader.glass
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date
@Composable fun HistoryScreen(onBack:()->Unit) {
 val store=koinInject<HistoryStore>();val entries by store.state.collectAsState();var confirm by remember{mutableStateOf(false)}
 LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(spacing(12))) {
  item {ScreenHeader("Updateverlauf","BESTÄTIGTE INSTALLATIONEN",onBack=onBack)}
  item {HelpTitle("Was wird gespeichert?","Nur über diese App gestartete und bestätigte Updates. Externe Übergaben werden beim nächsten Start anhand des installierten Versionscodes bestätigt. Keine vollständige Systemhistorie.")}
  if(entries.isEmpty())item{StateMessage("Noch keine Updates im Verlauf","Erfolgreiche Installationen erscheinen hier automatisch.")}
  items(entries,key={it.packageName+it.time}){e->GlassPanel{Text(e.name,style=MaterialTheme.typography.titleSmall);SecondaryText(e.packageName);Text("${e.fromVersion} → ${e.toVersion}");SecondaryText("${DateFormat.getDateTimeInstance().format(Date(e.time))}\n${e.source} · ${e.installer}")}}
  item{Row{TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={confirm=true},enabled=entries.isNotEmpty()){Text("Verlauf löschen")};HelpButton("Verlauf löschen","Löscht nur diese Liste. Installierte Apps und APK-Dateien bleiben erhalten.")}}
 }
 if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Verlauf löschen?")},text={Text("Alle gespeicherten Verlaufseinträge entfernen?")},confirmButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={store.clear();confirm=false}){Text("Löschen")}},dismissButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={confirm=false}){Text("Abbrechen")}})
}
