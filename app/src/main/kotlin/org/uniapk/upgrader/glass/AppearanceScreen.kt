package org.uniapk.upgrader.glass
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
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import kotlin.math.*
val LocalAppearance=staticCompositionLocalOf{Appearance()}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun AppearanceScreen(onBack:()->Unit) {
 val context=androidx.compose.ui.platform.LocalContext.current
 val store=koinInject<AppearanceStore>();val a by store.state.collectAsStateWithLifecycle();var picking by remember{mutableStateOf<String?>(null)}
 DisposableEffect(store) { onDispose { store.commitPreview() } }
 CompositionLocalProvider(LocalEffectLevel provides a.quality.coerceAtMost(1)) {
 Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=LocalDeviceLayout.current.horizontalPadding)) {
  
  LazyColumn(Modifier.weight(1f).navigationBarsPadding(),contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(spacing(14))) {
   item{SettingsHero("Design und Leistung","Design, Glas, Bewegung und Dichte",UniIcons.Palette,onBack)}
   item{Text("Design",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)}
   item{GlassPanel{ThemePreviewChoices(a){theme,oled->store.update{it.copy(theme=theme,oled=oled,customBackground=false)}};AppearanceToggle("Android-Design folgen","Verwendet das helle oder dunkle Systemdesign.",a.theme==0&&!a.oled){v->store.update{it.copy(theme=if(v)0 else 1,oled=false,customBackground=false)}}}}
   item{Text("Farbpalette",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)}
   item{GlassPanel{PaletteChoices(a){v->store.update{it.copy(palette=v,dynamic=false,customAccent=false)}}}}
   item{GlassPanel{AppearanceToggle("Kompaktmodus","Kleinere Karten, kürzere Zeilen und engere Abstände in der gesamten App. Bedienelemente bleiben gut erreichbar.",a.compact){v->store.update{it.copy(compact=v)}}}}
   item{DockSettings(store,a)}
   item{LauncherIconSettings(store,a)}
   item{GlassPanel(effectLevel=a.quality){HelpTitle("Empfohlenes Liquid Glass","Setzt nur Qualität, Glastransparenz, Unschärfe und Brechung auf ausgewogene Werte. Deine Farben bleiben erhalten.");SecondaryText("Standard · 54 % Glastransparenz · Unschärfe und Brechung an");OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={store.recommendedGlass()}){Text("Empfohlene Glaswerte anwenden")}}}
   item{GlassPanel{GlassTransparencyControl(store,a)}}
   item{GlassPanel{
    HelpTitle("Farben","Systemfarben folgen dem Android-Hintergrund. Eigene Farben ersetzen die gewählte Palette.")
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
    ValueSlider("Effektqualität: ${listOf("Aus","Reduziert","Standard","Hoch")[a.quality]}",a.quality.toFloat(),2f,{v->store.preview{it.copy(quality=v.toInt())}},0f..3f,2,"Standard ist ausgewogen. Hoch kostet mehr Grafikleistung; Aus deaktiviert Unschärfe und Brechung. Die Transparenz bleibt einstellbar.",finished={store.commitPreview()})
   }}
   item{GlassPanel{
    HelpTitle("Hintergrundanimation","Optionale Effekte hinter den Bildschirmen. Sie pausieren außerhalb der aktiven App, bei reduziertem Bewegungssystem oder Energiesparmodus. OLED bleibt als Grundfarbe schwarz.")
    val motionStatus by MotionRuntime.state.collectAsStateWithLifecycle()
    SecondaryText("${motionStatus.mode.label} · ${motionStatus.reason}")
    var motionMenu by remember{mutableStateOf(false)}
    OutlinedButton(onClick={motionMenu=true},modifier=Modifier.fillMaxWidth()){Text(MotionMode.entries.getOrElse(a.motion){MotionMode.NONE}.label)}
    if(motionMenu)GlassChoiceSheet("Hintergrundanimation",a.motion.toString(),MotionMode.entries.map{GlassChoice(it.ordinal.toString(),it.label)},{motionMenu=false}){value->store.update{it.copy(motion=value.toInt())}}
    AppearanceToggle("Parallax-Effekt","Sanfte Verschiebung beim Neigen; nur bei aktivem Bildschirm.",a.parallax){v->store.update{it.copy(parallax=v)}}
    AppearanceToggle("Saisonale Designs","Bei Zufällig sind saisonale Effekte möglich. Eine fest ausgewählte Animation bleibt aktiv.",a.seasonal){v->store.update{it.copy(seasonal=v)}}
    ValueSlider("Geschwindigkeit: ${(a.speed*100).toInt()} %",a.speed,.5f,{v->store.preview{it.copy(speed=v)}},.1f..1f,body="Langsame Bewegung ist ruhiger; Standard: 50 %.",finished={store.commitPreview()})
    ValueSlider("Effektdichte: ${(a.density*100).toInt()} %",a.density,.35f,{v->store.preview{it.copy(density=v)}},0f..1f,body="Wirkt auf alle Effekte: weniger Elemente, Bänder oder Spalten. 0 % blendet die Animation aus.",finished={store.commitPreview()})
   }}
   item{GlassPanel{
    HelpTitle("Animationsfarben","Originalfarben des Effekts oder eigene Farben. Betrifft nur die Hintergrundanimation.")
    var menu by remember{mutableStateOf(false)};OutlinedButton(onClick={menu=true}){Text(listOf("Passend zum Effekt","Einfarbig","Zwei Farben")[a.motionPalette])}
    if(menu)GlassChoiceSheet("Animationsfarben",a.motionPalette.toString(),listOf("Passend zum Effekt","Einfarbig","Zwei Farben").mapIndexed{i,t->GlassChoice(i.toString(),t)},{menu=false}){v->store.update{it.copy(motionPalette=v.toInt())}}
    if(a.motionPalette>0)ColorSetting("Animationsfarbe",Color(a.motionColor.toInt())){picking="motion"}
    if(a.motionPalette==2)ColorSetting("Zweite Animationsfarbe",Color(a.motionSecondary.toInt())){picking="motionSecondary"}
   }}
   item{ComputeSettings()}
   item{GlassPanel{
    HelpTitle("Ladeanzeige","Gilt für Suche, Downloads und Prüfungen. Prozent erscheinen nur bei bekanntem Fortschritt.")
    var menu by remember{mutableStateOf(false)};OutlinedButton(onClick={menu=true}){Text(ProgressPolicy.style(a.progressStyle).label)}
    if(menu)GlassChoiceSheet("Ladeanzeige",a.progressStyle.toString(),ProgressStyle.entries.map{GlassChoice(it.ordinal.toString(),it.label)},{menu=false}){v->store.update{it.copy(progressStyle=v.toInt())}}
    SecondaryText("Vorschau · 65 %");UniProgress(.65f,label="Vorschau: 65 Prozent",style=a.progressStyle)
   }}
   item{GlassPanel{
    HelpTitle("Schrift","Es werden verfügbare Systemschriften verwendet. Die Textgröße wird mit Androids Schriftgröße kombiniert.")
    var menu by remember{mutableStateOf(false)};OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={menu=true}){Text(listOf("Systemschrift","Sans Serif","Serif","Monospace")[a.font.coerceIn(0,3)])}
    if(menu)GlassChoiceSheet("Schrift",a.font.toString(),listOf("Systemschrift","Sans Serif","Serif","Monospace").mapIndexed{i,t->GlassChoice(i.toString(),t)},{menu=false}){id->store.update{it.copy(font=id.toInt())}}
    ValueSlider("Zusätzliche Textgröße: ${(a.textScale*100).toInt()} %",a.textScale,1f,{v->store.preview{it.copy(textScale=v)}},.85f..1.3f,body="100 % entspricht der Android-Schriftgröße ohne zusätzlichen Faktor.",finished={store.commitPreview()})
   }}
   item{Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={LauncherIcons.select(context,0);store.reset()},modifier=Modifier.weight(1f)){Text("Alle Darstellungswerte auf Standard zurücksetzen")};HelpButton("Darstellung zurücksetzen","Stellt das dunkle violett-blaue Standarddesign wieder her. Updatequellen, Downloads und Favoriten bleiben erhalten.")}}
  }
 }
 }
 picking?.let{target->
 val title=when(target){"accent"->"Akzentfarbe";"motion"->"Animationsfarbe";"motionSecondary"->"Zweite Animationsfarbe";else->"Hintergrundfarbe"}
 val initial=when(target){"accent"->a.accent;"motion"->a.motionColor;"motionSecondary"->a.motionSecondary;else->a.background}
 val alpha=when(target){"accent"->a.accentAlpha;"background"->a.backgroundAlpha;else->1f}
 ColorPickerDialog(title,initial,alpha,{picking=null},showAlpha=target in listOf("accent","background")){color,opacity->store.update{when(target){"accent"->it.copy(accent=color,accentAlpha=opacity,customAccent=true,dynamic=false);"motion"->it.copy(motionColor=color);"motionSecondary"->it.copy(motionSecondary=color);else->it.copy(background=color,backgroundAlpha=opacity,customBackground=true)}};picking=null}}

}
@Composable fun AppearanceToggle(title:String,description:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().padding(vertical=spacing(8)),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyMedium);SecondaryText(description)};Switch(checked,onChange);if(HelpTopics.technical(title))HelpButton(title,description)}}
@Composable private fun ColorSetting(label:String,color:Color,onClick:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=spacing(10)),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(30.dp).background(color,CircleShape));Spacer(Modifier.width(12.dp));Text(label,Modifier.weight(1f))}}
@Composable fun ColorPickerDialog(title:String,initial:Long,initialAlpha:Float=1f,onDismiss:()->Unit,showAlpha:Boolean=true,onSave:(Long,Float)->Unit){
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
  if(showAlpha)ValueSlider("Deckkraft: ${(alpha*100).toInt()} %",alpha,1f,{alpha=it},0f..1f,body="100 % ist deckend. Niedrigere Werte mischen mit dem Hintergrund.")
  Box(Modifier.fillMaxWidth().height(28.dp).background(Color((parsed?:initial).toInt()).copy(alpha=alpha),CircleShape))
 }},confirmButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={parsed?.let{onSave(it,alpha)}},enabled=parsed!=null){Text("Übernehmen")}},dismissButton={TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick=onDismiss){Text("Abbrechen")}})
}

@Composable fun GlassTransparencyControl(store: AppearanceStore, appearance: Appearance) {
    val language = LocalDisplayLanguage.current
    val percent = kotlin.math.round((1f - appearance.opacity) * 100).toInt()
    Text("Glastransparenz: $percent %", style = MaterialTheme.typography.titleSmall)
    SecondaryText("Für Karten, Menüs, Dialoge und Navigation. Höher zeigt mehr Hintergrund; Text und Symbole bleiben deckend. Unschärfe bleibt separat einstellbar.")
    Slider(value = 1f - appearance.opacity,
        onValueChange = { transparency -> store.preview { it.copy(opacity = 1f - transparency) } },
        onValueChangeFinished = { store.commitPreview() },
        modifier = Modifier.semantics { contentDescription = UiEnglish.translate("Glastransparenz", language) }, valueRange = 0f..1f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SecondaryText("0 % · deckend")
        SecondaryText("100 % · transparent")
    }
    TextButton(onClick = { store.update { it.copy(opacity = .46f) } }) { Text("Standard: 54 %") }
}
