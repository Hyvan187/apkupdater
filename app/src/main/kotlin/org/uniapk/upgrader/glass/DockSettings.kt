package org.uniapk.upgrader.glass
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class DockStyle(val label:String){PILL("Pill"),CLASSIC("Classic"),LABELED("Labeled"),EXPRESSIVE("Expressive"),SHAPE_SHIFT("Shape-shift")}
enum class DockSize(val label:String,val fraction:Float){COMPACT("Compact",.72f),MEDIUM("85 %",.85f),LARGE("Large",1f)}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun DockSettings(store:AppearanceStore,a:Appearance){GlassPanel{
 HelpTitle("Navigationsleiste","Gestaltung und Breite der unteren Navigation. Die Bildschirmränder und Android-Gesten bleiben frei.")
 var menu by remember{mutableStateOf(false)}
 OutlinedButton(onClick={menu=true}){Text(DockStyle.entries[a.dockStyle].label)}
 if(menu)GlassChoiceSheet("Dock-Stil",a.dockStyle.toString(),DockStyle.entries.map{GlassChoice(it.ordinal.toString(),it.label)},{menu=false}){v->store.update{it.copy(dockStyle=v.toInt())}}
 FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){DockSize.entries.forEachIndexed{i,v->FilterChip(a.dockSize==i,{store.update{it.copy(dockSize=i)}},label={Text(v.label)})}}
 DockNavigation("overview",null,Modifier.fillMaxWidth(),{})
}}
