package org.uniapk.upgrader.glass

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.*

enum class StatusTone{SUCCESS,WARNING,ERROR,INFO}
@Composable fun StatusLine(tone:StatusTone,title:String,detail:String="") {
    val color=when(tone){StatusTone.SUCCESS->safeStatusColor(true);StatusTone.WARNING->safeStatusColor(false);StatusTone.ERROR->MaterialTheme.colorScheme.error;StatusTone.INFO->MaterialTheme.colorScheme.primary}
    val icon=when(tone){StatusTone.SUCCESS->Icons.Default.CheckCircle;StatusTone.WARNING->Icons.Default.Warning;StatusTone.ERROR->Icons.Default.Close;StatusTone.INFO->Icons.Default.Info}
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(icon,null,Modifier.size(21.dp),tint=color);Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.labelLarge,color=color);if(detail.isNotBlank())SecondaryText(detail)}}
}
enum class ProgressStyle(val label:String){LINE("Linie"),SEGMENTS("Segmente"),WAVE("Welle"),RING("Ring")}
object ProgressPolicy {
    fun fraction(value:Float?)=value?.takeIf{it.isFinite()}?.coerceIn(0f,1f)
    fun style(value:Int)=ProgressStyle.entries.getOrElse(value){ProgressStyle.WAVE}
}
@Composable fun UniProgress(progress:Float?=null,modifier:Modifier=Modifier.fillMaxWidth(),label:String="Vorgang läuft",style:Int=LocalAppearance.current.progressStyle) {
    val a=LocalAppearance.current;val kind=ProgressPolicy.style(style);val value=ProgressPolicy.fraction(progress);val context=LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val scale=runCatching{Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)}.getOrDefault(1f)
    val power=context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
    val allowed=motionAllowed()
    val moving=(value==null || kind==ProgressStyle.WAVE && value>0f && value<1f)&&allowed&&scale>0&&lifecycle.isAtLeast(Lifecycle.State.RESUMED)&&!power.isPowerSaveMode
    val visualValue by animateFloatAsState(value ?: 0f,UniMotion.spec(allowed),label="download-fraction")
    var phase by remember{mutableFloatStateOf(0f)}
    LaunchedEffect(moving){if(moving)while(true){val interval=1000L/ComputeBudget.fps().coerceIn(12,30);delay(interval);phase=(phase+interval/1800f)%1f}}
    val color=MaterialTheme.colorScheme.primary;val track=color.copy(alpha=.14f)
    val accessible=if(value==null)modifier.progressSemantics() else modifier.progressSemantics(value)
    Canvas(accessible.height(if(kind==ProgressStyle.RING)32.dp else if(kind==ProgressStyle.WAVE)24.dp else 14.dp).semantics{contentDescription=label}) {
        val unit=1.dp.toPx();val middle=size.height/2;val start=if(value==null)if(moving)phase else .25f else 0f;val end=if(value==null)start+.22f else visualValue
        when(kind){
            ProgressStyle.RING->{val d=min(size.height,size.width)-4*unit;val offset=Offset((size.width-d)/2,(size.height-d)/2);drawArc(track,-90f,360f,false,offset,Size(d,d),style=Stroke(3*unit,cap=StrokeCap.Round));drawArc(color,if(value==null)start*360-90 else -90f,if(value==null)90f else value*360,false,offset,Size(d,d),style=Stroke(3*unit,cap=StrokeCap.Round))}
            ProgressStyle.SEGMENTS->{val gap=3*unit;val width=((size.width-gap*11)/12).coerceAtLeast(1f);repeat(12){i->val f=i/12f;val filled=if(value==null)((f-start+1)%1)<.25f else f<end;drawRoundRect(if(filled)color else track,Offset(i*(width+gap),middle-3*unit),Size(width,6*unit),CornerRadius(3*unit))}}
            ProgressStyle.WAVE->{
                val bg=Path();val fg=Path();var previous=false
                for(i in 0..128){val f=i/128f;val x=f*size.width;val y=middle+WaveMotion.offset(f,if(moving)phase else 0f)*unit
                    if(i==0)bg.moveTo(x,y) else bg.lineTo(x,y)
                    val chosen=if(value==null)((f-start+1)%1)<.28f else f<=end
                    if(chosen){if(!previous)fg.moveTo(x,y) else fg.lineTo(x,y)};previous=chosen
                }
                drawPath(bg,track,style=Stroke(5*unit,cap=StrokeCap.Round))
                drawPath(fg,color.copy(alpha=.12f),style=Stroke(9*unit,cap=StrokeCap.Round))
                drawPath(fg,color,style=Stroke(3*unit,cap=StrokeCap.Round))
                drawPath(fg,androidx.compose.ui.graphics.Color.White.copy(alpha=.22f),style=Stroke(.75f*unit,cap=StrokeCap.Round))
            }
            ProgressStyle.LINE->{drawRoundRect(track,Offset(0f,middle-3*unit),Size(size.width,6*unit),CornerRadius(3*unit));if(value!=null){if(value>0)drawRoundRect(color,Offset(0f,middle-3*unit),Size(size.width*value,6*unit),CornerRadius(3*unit))}else{val left=start*size.width;drawRoundRect(color,Offset(left,middle-3*unit),Size(min(.22f*size.width,size.width-left),6*unit),CornerRadius(3*unit));if(end>1)drawRoundRect(color,Offset(0f,middle-3*unit),Size((end-1)*size.width,6*unit),CornerRadius(3*unit))}}
        }
    }
}

/** Spatial wave changes over time, rather than moving a highlight along a static sine. */
object WaveMotion {
    fun offset(position:Float,phase:Float)=(sin(position*4*PI-phase*2*PI)*2.6+sin(position*7*PI+phase*2*PI)*.6).toFloat()
}
