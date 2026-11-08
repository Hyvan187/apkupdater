package org.uniapk.upgrader.glass
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.uniapk.upgrader.R
object LauncherIcons {
 val names=listOf("Original","Black","White","Milk","Transparent")
 val labels=listOf("Original","Schwarz","Weiß","Milchweiß","Transparent")
 val resources=listOf(R.mipmap.ic_store,R.mipmap.ic_uniapk_black,R.mipmap.ic_uniapk_white,R.mipmap.ic_uniapk_milk,R.mipmap.ic_uniapk_transparent)
 fun select(context:Context,index:Int){
  require(index in names.indices);val pm=context.packageManager
  val components=names.map{ComponentName(context,"${context.packageName}.Launcher$it")}
  if(Build.VERSION.SDK_INT>=33)pm.setComponentEnabledSettings(components.mapIndexed{i,c->PackageManager.ComponentEnabledSetting(c,if(i==index)PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP)})
  else{pm.setComponentEnabledSetting(components[index],PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);components.forEachIndexed{i,c->if(i!=index)pm.setComponentEnabledSetting(c,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP)}}
 }
}
@Composable fun LauncherIconSettings(store:AppearanceStore,a:Appearance){
 val context=LocalContext.current;var error by remember{mutableStateOf<String?>(null)}
 GlassPanel{
  HelpTitle("App-Symbol","Original oder vier zusätzliche Varianten. Form und Systemfarben passen sich über adaptive und monochrome Symbole an unterstützte Launcher an. Transparenz und die Aktualisierung vorhandener Verknüpfungen bestimmt der Launcher.")
  Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)){
   LauncherIcons.names.forEachIndexed{i,_->Column(Modifier.width(78.dp).clickable{runCatching{LauncherIcons.select(context,i);store.update{it.copy(iconStyle=i)};error=null}.onFailure{error="App-Symbol konnte nicht geändert werden."}},horizontalAlignment=Alignment.CenterHorizontally){
    Image(painterResource(LauncherIcons.resources[i]),LauncherIcons.labels[i],Modifier.size(58.dp))
    Text(LauncherIcons.labels[i],style=MaterialTheme.typography.labelSmall)
    RadioButton(a.iconStyle==i,onClick={runCatching{LauncherIcons.select(context,i);store.update{it.copy(iconStyle=i)};error=null}.onFailure{error="App-Symbol konnte nicht geändert werden."}})
   }}
  }
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 }
}
