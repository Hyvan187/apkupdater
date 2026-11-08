package org.uniapk.upgrader.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Local choices remain distinct from optional Android wallpaper colors.
enum class AppearancePalettes(val label:String,val accent:Long,val secondary:Long,val lightAccent:Long){
 ORION("Orion",0xffc8b9ff,0xffb5e7a2,0xff5b45ba),OCEAN("Ocean",0xff8bd3ff,0xffffa3b5,0xff16678e),FOREST("Forest",0xff88d99a,0xffffca80,0xff276a39),SUNSET("Sunset",0xffffb68f,0xffc4a5ff,0xff934b24),ROSE("Rose",0xffffacd0,0xff83ded7,0xff943b67)
}
@Composable fun SettingsHero(title:String,subtitle:String,icon:ImageVector,onBack:()->Unit,showBack:Boolean=true){
 val primary=MaterialTheme.colorScheme.primary
 Box(Modifier.fillMaxWidth().heightIn(min=if(LocalAppearance.current.compact)148.dp else 198.dp).clip(RoundedCornerShape(bottomStart=30.dp,bottomEnd=30.dp)).background(Brush.verticalGradient(listOf(primary.copy(alpha=.23f),Color.Transparent)))){
  Canvas(Modifier.matchParentSize()){
   val path=Path();val cx=size.width*.89f;val cy=size.height*.25f;val radius=size.height*.65f
   repeat(81){i->val a=i*2*kotlin.math.PI/80;val r=radius*(1+.15*kotlin.math.sin(a*6));val x=cx+kotlin.math.cos(a).toFloat()*r.toFloat();val y=cy+kotlin.math.sin(a).toFloat()*r.toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};path.close();drawPath(path,primary.copy(alpha=.08f))
  }
  Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=spacing(12))){
   Row(verticalAlignment=Alignment.CenterVertically){if(showBack)IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,"Zurück")}else Spacer(Modifier.size(48.dp));Spacer(Modifier.weight(1f));Icon(icon,null,Modifier.size(34.dp),tint=primary)}
   Spacer(Modifier.height(spacing(14)));Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Medium);Spacer(Modifier.height(6.dp));SecondaryText(subtitle)
  }
 }
}
@Composable fun ThemePreviewChoices(a:Appearance,choose:(Int,Boolean)->Unit){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  listOf("Light","Dusk","Dark","OLED").forEachIndexed{i,label->
   val selected=if(i==3)a.oled else !a.oled&&a.theme==listOf(2,3,1,1)[i]
   Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable{choose(listOf(2,3,1,1)[i],i==3)}.padding(3.dp),horizontalAlignment=Alignment.CenterHorizontally){
    val bg=listOf(Color(0xfff8f5ff),Color(0xff292735),Color(0xff101116),Color.Black)[i]
    Box(Modifier.fillMaxWidth().height(if(a.compact)102.dp else 126.dp).border(if(selected)3.dp else 1.dp,if(selected)MaterialTheme.colorScheme.primary else Color(0xff656573),RoundedCornerShape(19.dp)).padding(4.dp).clip(RoundedCornerShape(16.dp)).background(bg)){
     Column(Modifier.fillMaxSize().padding(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
      Box(Modifier.fillMaxWidth().height(9.dp).background(if(i==0)Color(0xffe5e1ec)else Color(0xff474550),CircleShape))
      Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){Box(Modifier.weight(1f).height(28.dp).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(7.dp)));Box(Modifier.weight(1f).height(28.dp).background(MaterialTheme.colorScheme.secondary,RoundedCornerShape(7.dp)))}
      Box(Modifier.fillMaxWidth(.8f).height(6.dp).background(Color(0xff97939f),CircleShape));Box(Modifier.fillMaxWidth(.55f).height(5.dp).background(Color(0xff686575),CircleShape));Spacer(Modifier.weight(1f));Box(Modifier.fillMaxWidth().height(13.dp).background(if(i==0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,CircleShape))
     }
     if(selected)Icon(Icons.Default.Check,"Ausgewählt",Modifier.align(Alignment.TopEnd).size(22.dp).background(MaterialTheme.colorScheme.primary,CircleShape),tint=MaterialTheme.colorScheme.onPrimary)
    }
    Text(label,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=8.dp))
   }
  }
 }
}
@Composable fun PaletteChoices(a:Appearance,choose:(Int)->Unit){
 Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(18.dp)){
  AppearancePalettes.entries.forEachIndexed{i,p->Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable{choose(i)}.padding(3.dp),horizontalAlignment=Alignment.CenterHorizontally){
   Canvas(Modifier.size(if(a.compact)58.dp else 66.dp).border(if(a.palette==i&&!a.dynamic&&!a.customPalette)3.dp else 0.dp,MaterialTheme.colorScheme.primary,CircleShape)){
    drawArc(Color(p.accent),180f,180f,true);drawArc(Color(p.secondary),0f,90f,true);drawArc(Color(0xff343346),90f,90f,true)
   };Text(p.label,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=8.dp))
  }}
 }
}
@Composable fun SettingsCategoryCard(category:SettingsCategory,icon:ImageVector,onClick:()->Unit){
 GlassPanel(Modifier.fillMaxWidth().clickable(onClick=onClick)){
  Row(Modifier.heightIn(min=if(LocalAppearance.current.compact)58.dp else 72.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
   Icon(icon,null,Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=.1f),RoundedCornerShape(15.dp)).padding(10.dp),tint=MaterialTheme.colorScheme.primary)
   Column(Modifier.weight(1f)){Text(category.title,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(4.dp));SecondaryText(category.summary)}
   Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}

@Composable fun PinnedSettingsBack(onBack:()->Unit,modifier:Modifier=Modifier){
 IconButton(onClick=onBack,modifier=modifier.size(48.dp).background(MaterialTheme.colorScheme.surface.copy(alpha=.86f),CircleShape)){Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,"Zurück")}
}
