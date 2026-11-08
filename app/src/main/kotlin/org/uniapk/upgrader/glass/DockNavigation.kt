package org.uniapk.upgrader.glass
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
@Composable fun DockNavigation(route:String,backdrop:GlassBackdrop?,modifier:Modifier=Modifier,navigate:(String)->Unit){
 val a=LocalAppearance.current;val style=DockStyle.entries[a.dockStyle];val fraction=DockSize.entries[a.dockSize].fraction
 Box(modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
  GlassPanel(Modifier.fillMaxWidth(fraction).widthIn(max=540.dp),backdrop=backdrop,compact=true,pill=style in listOf(DockStyle.PILL,DockStyle.LABELED)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically){
    val routes=listOf("overview","updates","apps","search");val labels=listOf("Start","Updates","Apps","Suche");val icons=listOf(Icons.Default.Home,Icons.Default.Refresh,Icons.AutoMirrored.Filled.List,Icons.Default.Search)
    routes.forEachIndexed{i,target->val selected=target==route
     val color by animateColorAsState(if(selected)MaterialTheme.colorScheme.primary.copy(alpha=.23f)else Color.Transparent,label="dock-color")
     val shape=when(style){DockStyle.CLASSIC->RoundedCornerShape(10.dp);DockStyle.EXPRESSIVE->RoundedCornerShape(topStart=if(selected)24.dp else 10.dp,topEnd=8.dp,bottomEnd=24.dp,bottomStart=8.dp);DockStyle.SHAPE_SHIFT->RoundedCornerShape(if(selected)18.dp else 50.dp);else->CircleShape}
     Column(Modifier.weight(1f).heightIn(min=48.dp).clip(shape).background(color).clickable(role=androidx.compose.ui.semantics.Role.Tab){navigate(target)}.semantics{this.selected=selected;contentDescription=if(target=="search")"Apps suchen" else labels[i]}.padding(vertical=if(a.compact)5.dp else 8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
      Icon(icons[i],null,Modifier.size(if(style==DockStyle.EXPRESSIVE&&selected)27.dp else 23.dp),tint=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
      if(style!=DockStyle.CLASSIC&&(a.dockSize!=0||style==DockStyle.LABELED||selected))Text(labels[i],style=MaterialTheme.typography.labelSmall,maxLines=1)
      if(style==DockStyle.CLASSIC&&selected)Box(Modifier.padding(top=3.dp).size(4.dp).background(MaterialTheme.colorScheme.primary,CircleShape))
     }
    }
   }
  }
 }
}
