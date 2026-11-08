package org.uniapk.upgrader.glass
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
@Composable fun IgnoredAppsScreen(onBack:()->Unit){
 val store=koinInject<IgnoredAppStore>();val ignored by store.state.collectAsStateWithLifecycle();val context=LocalContext.current
 var query by remember{mutableStateOf("")}
 val names by produceState<Map<String,String>>(emptyMap(),ignored){value=withContext(Dispatchers.IO){ignored.associateWith{pkg->runCatching{context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg,0)).toString()}.getOrDefault(pkg)}}}
 LazyColumn(Modifier.fillMaxSize().statusBarsPadding(),contentPadding=ScreenPadding,verticalArrangement=Arrangement.spacedBy(spacing(12))){
  item{ScreenHeader("Ignorierte Apps","VON UPDATES AUSGESCHLOSSEN",onBack=onBack)}
  item{SecondaryText("Diese Apps werden weder bei manuellen Updateabfragen noch im Hintergrund geprüft oder automatisch geladen. Wieder zulassen wirkt bei der nächsten Suche.")}
  item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Ignorierte Apps durchsuchen")},singleLine=true)}
  if(ignored.isEmpty())item{StateMessage("Keine Apps ignoriert","Über App dauerhaft ignorieren in einer Updatekarte kannst du eine App ausschließen.")}
  items(ignored.filter{it.contains(query,true)||names[it].orEmpty().contains(query,true)}.sortedBy{names[it]?:it},key={it}){pkg->GlassPanel{Text(names[pkg]?:pkg,style=MaterialTheme.typography.titleSmall);if(names[pkg]!=pkg)SecondaryText(pkg);TextButton(onClick={store.set(pkg,false)}){Text("Updates wieder zulassen")}}}
 }
}

