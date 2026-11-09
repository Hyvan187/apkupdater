package com.apkupdater.glass
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import kotlin.math.*
val LocalAppearance=staticCompositionLocalOf{Appearance()}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun AppearanceScreen(onBack:()->Unit) {
 val store=koinInject<AppearanceStore>();val a by store.state.collectAsStateWithLifecycle();var picking by remember{mutableStateOf<String?>(null)}
 Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=20.dp)) {
  ScreenHeader("Erscheinungsbild","FARBEN, GLAS UND BEWEGUNG",onBack=onBack)
  LazyColumn(Modifier.weight(1f).navigationBarsPadding(),contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
   item{GlassPanel{HelpTitle("Empfohlenes Liquid Glass","Setzt nur Qualität, Glasdeckkraft, Unschärfe und Brechung auf ausgewogene Werte. Deine Farben bleiben erhalten.");SecondaryText("Standard · 46 % Glasdeckkraft · Unschärfe und Brechung an");OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={store.recommendedGlass()}){Text("Empfohlene Glaswerte anwenden")}}}
   item{GlassPanel{
    HelpTitle("Design","System folgt Android. Hell und Dunkel bleiben fest ausgewählt. OLED erzwingt einen schwarzen Hintergrund.")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("System","Hell","Dunkel").forEachIndexed{i,t->val v=listOf(0,2,1)[i];FilterChip(a.theme==v,{store.update{it.copy(theme=v)}},label={Text(t)})}}
    AppearanceToggle("OLED-Optimierung","Schwarzer Hintergrund; statische Farblichtflächen entfallen.",a.oled){v->store.update{it.copy(oled=v)}}
    AppearanceToggle("Systemfarben","Akzente aus deinem Android-Design.",a.dynamic){v->store.update{it.copy(dynamic=v,customAccent=false)}}
    ColorSetting("Button- und Akzentfarbe",if(a.customAccent)Color(a.accent.toInt()) else MaterialTheme.colorScheme.primary){picking="accent"}
    ColorSetting("Hintergrundfarbe",if(a.customBackground)Color(a.background.toInt()) else MaterialTheme.colorScheme.background){picking="background"}
   }}
   item{GlassPanel{
    HelpTitle("Glas und Bewegung","Effekte sind rein optisch. Aus ist der sparsamste Modus; Standard ist die Empfehlung. Die Systemvorgabe für reduzierte Animationen wird berücksichtigt.")
    AppearanceToggle("Animationen reduzieren","Pausiert Hintergrundbewegung und Parallaxe.",a.reduceMotion){v->store.update{it.copy(reduceMotion=v)}}
    AppearanceToggle("Hintergrundunschärfe","Weichzeichnet den hinter Glas liegenden Inhalt.",a.blur){v->store.update{it.copy(blur=v)}}
    AppearanceToggle("Flüssiges Glas","Lichtbrechung ab Android 13 bei Standard oder Hoch.",a.refraction){v->store.update{it.copy(refraction=v)}}
    AppearanceToggle("Stärkere Navigationsunschärfe","Die schwebende Leiste erhält mehr Unschärfe.",a.progressiveBlur){v->store.update{it.copy(progressiveBlur=v)}}
    ValueSlider("Effektqualität: ${listOf("Aus","Reduziert","Standard","Hoch")[a.quality]}",a.quality.toFloat(),2f,{v->store.update{it.copy(quality=v.toInt())}},0f..3f,2,"Standard ist ausgewogen. Hoch kostet mehr Grafikleistung; Aus nutzt feste Flächen.")
    ValueSlider("Glasdeckkraft: ${(a.opacity*100).toInt()} %",a.opacity,.46f,{v->store.update{it.copy(opacity=v)}},.15f.. .95f,body="Niedrig zeigt mehr Hintergrund. Höher verbessert die Lesbarkeit. Standard: 46 %.")
   }}
   item{GlassPanel{
    HelpTitle("Hintergrundanimation","Optionale Effekte hinter den Bildschirmen. Sie pausieren außerhalb der aktiven App, bei reduziertem Bewegungssystem oder Energiesparmodus. OLED bleibt als Grundfarbe schwarz.")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){MotionMode.entries.forEach{m->FilterChip(a.motion==m.ordinal,{store.update{it.copy(motion=m.ordinal)}},label={Text(m.label)})}}
    AppearanceToggle("Parallax-Effekt","Sanfte Verschiebung beim Neigen; nur bei aktivem Bildschirm.",a.parallax){v->store.update{it.copy(parallax=v)}}
    AppearanceToggle("Saisonale Designs","Lokale Winter- und Jahreswechsel-Effekte; keine Standortabfrage.",a.seasonal){v->store.update{it.copy(seasonal=v)}}
    ValueSlider("Geschwindigkeit: ${(a.speed*100).toInt()} %",a.speed,.5f,{v->store.update{it.copy(speed=v)}},.1f..1f,body="Langsame Bewegung ist ruhiger; Standard: 50 %.")
    ValueSlider("Effektdichte: ${(a.density*100).toInt()} %",a.density,.35f,{v->store.update{it.copy(density=v)}},.1f..1f,body="Anzahl der sichtbaren Elemente. Weniger spart Grafikarbeit; Standard: 35 %.")
   }}
   item{GlassPanel{
    HelpTitle("Schrift","Es werden verfügbare Systemschriften verwendet. Die Textgröße wird mit Androids Schriftgröße kombiniert.")
    var menu by remember{mutableStateOf(false)};OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={menu=true}){Text(listOf("Systemschrift","Sans Serif","Serif","Monospace")[a.font.coerceIn(0,3)])}
    DropdownMenu(menu,{menu=false}){listOf("Systemschrift","Sans Serif","Serif","Monospace").forEachIndexed{i,t->DropdownMenuItem(text={Text(t)},onClick={store.update{it.copy(font=i)};menu=false})}}
    ValueSlider("Zusätzliche Textgröße: ${(a.textScale*100).toInt()} %",a.textScale,1f,{v->store.update{it.copy(textScale=v)}},.85f..1.3f,body="100 % entspricht der Android-Schriftgröße ohne zusätzlichen Faktor.")
   }}
   item{Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={store.reset()},modifier=Modifier.weight(1f)){Text("Alle Darstellungswerte auf Standard zurücksetzen")};HelpButton("Darstellung zurücksetzen","Stellt das dunkle violett-blaue Standarddesign wieder her. Updatequellen, Downloads und Favoriten bleiben erhalten.")}}
  }
 }
 picking?.let{target->ColorPickerDialog(if(target=="accent")"Buttonfarbe" else "Hintergrundfarbe",if(target=="accent")a.accent else a.background,if(target=="accent")a.accentAlpha else a.backgroundAlpha,{picking=null}){color,alpha->store.update{if(target=="accent")it.copy(accent=color,accentAlpha=alpha,customAccent=true,dynamic=false) else it.copy(background=color,backgroundAlpha=alpha,customBackground=true)};picking=null}}
}
@Composable fun AppearanceToggle(title:String,description:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyMedium);SecondaryText(description)};Switch(checked,onChange);HelpButton(title,description)}}
@Composable private fun ColorSetting(label:String,color:Color,onClick:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(30.dp).background(color,CircleShape));Spacer(Modifier.width(12.dp));Text(label,Modifier.weight(1f));HelpButton(label,"Wähle Farbton und Sättigung im Kreis oder gib den RGB-HEX-Code ein. Deckkraft mischt die Farbe mit der App-Fläche; Android zeigt nicht andere Apps hinter dem Fenster.")}}
@Composable fun ColorPickerDialog(title:String,initial:Long,initialAlpha:Float=1f,onDismiss:()->Unit,onSave:(Long,Float)->Unit){
 val hsv=remember{FloatArray(3).also{AndroidColor.colorToHSV(initial.toInt(),it)}}
 var hue by remember{mutableFloatStateOf(hsv[0])};var saturation by remember{mutableFloatStateOf(hsv[1])};var brightness by remember{mutableFloatStateOf(hsv[2])};var alpha by remember{mutableFloatStateOf(initialAlpha)};var hex by remember{mutableStateOf(ColorInput.hex(initial))}
 fun updateHex(){hex=ColorInput.hex(AndroidColor.HSVToColor(floatArrayOf(hue,saturation,brightness)).toLong())}
 val parsed=ColorInput.parse(hex)
 AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState())){
  Canvas(Modifier.fillMaxWidth().height(200.dp).pointerInput(Unit){detectTapGestures{p->val c=Offset(size.width/2f,size.height/2f);val d=p-c;hue=((atan2(d.y,d.x)*180/PI).toFloat()+360)%360;saturation=(d.getDistance()/(min(size.width,size.height)/2f)).coerceIn(0f,1f);updateHex()}}.pointerInput(Unit){detectDragGestures{change,_->val c=Offset(size.width/2f,size.height/2f);val d=change.position-c;hue=((atan2(d.y,d.x)*180/PI).toFloat()+360)%360;saturation=(d.getDistance()/(min(size.width,size.height)/2f)).coerceIn(0f,1f);updateHex();change.consume()}}){
   val radius=min(size.width,size.height)/2;val c=center
   drawCircle(Brush.sweepGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red),c),radius,c)
   drawCircle(Brush.radialGradient(listOf(Color.White,Color.Transparent),c,radius),radius,c)
   val angle=hue*PI/180;val p=c+Offset(cos(angle).toFloat(),sin(angle).toFloat())*radius*saturation
   drawCircle(Color.Black,8.dp.toPx(),p,style=androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()));drawCircle(Color.White,6.dp.toPx(),p,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
  }
  OutlinedTextField(hex,{v->hex=v.removePrefix("#").take(6).uppercase();ColorInput.parse(hex)?.let{AndroidColor.colorToHSV(it.toInt(),hsv);hue=hsv[0];saturation=hsv[1];brightness=hsv[2]}},label={Text("HEX-Farbe")},prefix={Text("#")},singleLine=true,isError=parsed==null)
  ValueSlider("Helligkeit",brightness,1f,{brightness=it;updateHex()},.05f..1f,body="Helligkeit der ausgewählten Farbe.")
  ValueSlider("Deckkraft: ${(alpha*100).toInt()} %",alpha,1f,{alpha=it},0f..1f,body="100 % ist deckend. Niedrigere Werte mischen mit dem Hintergrund.")
  Box(Modifier.fillMaxWidth().height(28.dp).background(Color((parsed?:initial).toInt()).copy(alpha=alpha),CircleShape))
 }},confirmButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={parsed?.let{onSave(it,alpha)}},enabled=parsed!=null){Text("Übernehmen")}},dismissButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick=onDismiss){Text("Abbrechen")}})
}
