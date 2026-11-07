package com.apkupdater.glass

import android.content.*
import android.hardware.*
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

enum class MotionMode(val label:String){NONE("Keine"),CIRCLES("Kreise"),RINGS("Ringe"),NET("Netz"),SPACE("Weltraum"),SHAPES("Formen"),SNOW("Schnee"),GRID("Raster"),PARTICLES("Partikel"),LAVA("Lavalampe"),RANDOM("Zufällig")}
object MotionPolicy {
    fun active(resumed:Boolean,reduced:Boolean,systemMotion:Boolean,powerSave:Boolean)=resumed && !reduced && systemMotion && !powerSave
    fun mode(index:Int,seasonal:Boolean,month:Int,seed:Int):MotionMode {
        val requested=MotionMode.entries.getOrElse(index){MotionMode.NONE}
        if(requested!=MotionMode.RANDOM)return requested
        if(seasonal && month==12)return MotionMode.SNOW
        if(seasonal && month==1)return MotionMode.PARTICLES
        return MotionMode.entries[1+Math.floorMod(seed,9)]
    }
    fun count(mode:MotionMode,density:Float):Int {
        val d=density.coerceIn(0f,1f)
        return when(mode){MotionMode.LAVA->(3+d*4).toInt();MotionMode.NET->(14+d*26).toInt();MotionMode.CIRCLES,MotionMode.RINGS,MotionMode.SHAPES->(8+d*18).toInt();else->(24+d*80).toInt()}
    }
    fun point(index:Int,seed:Int):Offset {
        val offset=Math.floorMod(seed,997)/997f
        fun fraction(v:Float)=v-floor(v)
        return Offset(fraction(index*.61803399f+offset),fraction(index*.75487767f+.27f))
    }
    fun greeting(month:Int,day:Int)=when{month==12&&day in 24..26->"Frohe Feiertage";month==1&&day==1->"Frohes neues Jahr";else->""}
}
data class MotionStatus(val mode:MotionMode=MotionMode.NONE,val active:Boolean=false,val reason:String="")
object MotionRuntime {
    private val mutable=MutableStateFlow(MotionStatus())
    val state=mutable.asStateFlow()
    fun report(status:MotionStatus){mutable.value=status}
}
val LocalBackgroundFrameRate=staticCompositionLocalOf { 30 }

@Composable fun MotionBackground(a:Appearance,modifier:Modifier=Modifier.fillMaxSize()) {
    val context=LocalContext.current;val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var saver by remember { mutableStateOf(power.isPowerSaveMode) }
    DisposableEffect(power) {
        val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(i.action==PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)saver=power.isPowerSaveMode}}
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),Context.RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION")context.registerReceiver(receiver,IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose{context.unregisterReceiver(receiver)}
    }
    val systemMotion=runCatching { Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f }.getOrDefault(true)
    val active=MotionPolicy.active(lifecycle.isAtLeast(Lifecycle.State.RESUMED),a.reduceMotion,systemMotion,saver)
    val seed=remember { kotlin.random.Random.nextInt() };val month=java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)+1
    val mode=remember(a.motion,a.seasonal,month,seed){MotionPolicy.mode(a.motion,a.seasonal,month,seed)}
    val reason=when { mode==MotionMode.NONE->"Keine Animation gewählt";a.reduceMotion->"Animationen reduzieren ist aktiv";!systemMotion->"Android hat Animationen deaktiviert";saver->"Durch Energiesparmodus pausiert";!active->"App im Hintergrund";else->"Aktiv" }
    SideEffect { MotionRuntime.report(MotionStatus(mode,active&&mode!=MotionMode.NONE,reason)) }
    var seconds by remember { mutableFloatStateOf(0f) };var tilt by remember { mutableStateOf(Offset.Zero) }
    val fps=LocalBackgroundFrameRate.current.coerceIn(20,60)
    LaunchedEffect(active,mode,fps) {
        if(active&&mode!=MotionMode.NONE) {
            var previous=0L
            while(true) { withFrameNanos { now -> if(previous!=0L)seconds+=(now-previous).coerceAtMost(100_000_000L)/1_000_000_000f;previous=now };delay((1000L/fps)-1) }
        }
    }
    DisposableEffect(active,a.parallax) {
        val manager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val listener=object:SensorEventListener {
            override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
            override fun onSensorChanged(event:SensorEvent){tilt=Offset((-event.values[0]/9.81f).coerceIn(-1f,1f),(event.values[1]/9.81f).coerceIn(-1f,1f))}
        }
        val sensor=manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if(active&&a.parallax&&sensor!=null)manager.registerListener(listener,sensor,SensorManager.SENSOR_DELAY_UI) else tilt=Offset.Zero
        onDispose { manager.unregisterListener(listener) }
    }
    val count=MotionPolicy.count(mode,a.density)
    val positions=remember(mode,count,seed){List(count){MotionPolicy.point(it,seed)}}
    val color=if(a.customAccent)Color(a.accent.toInt()) else MaterialTheme.colorScheme.primary
    if(mode==MotionMode.NONE&&!a.parallax)return
    Canvas(modifier) {
        if(size.width<=0f||size.height<=0f)return@Canvas
        val unit=1.dp.toPx();val t=seconds*(.25f+a.speed.coerceIn(.1f,1f)*1.6f)
        val shift=if(a.parallax&&active)tilt*(12*unit) else Offset.Zero
        fun wrap(value:Float,range:Float)=((value%range)+range)%range
        fun point(i:Int):Offset {val p=positions[i];return Offset(wrap(p.x*size.width+t*(5+(i%4))*unit+shift.x,size.width),wrap(p.y*size.height+t*(3+(i%3))*unit+shift.y,size.height))}
        when(mode) {
            MotionMode.CIRCLES,MotionMode.RINGS->positions.indices.forEach { i -> val p=point(i);val radius=(16+i%5*8)*unit;drawCircle(color.copy(alpha=.18f),radius,p,style=Stroke(if(mode==MotionMode.RINGS)1.3f*unit else 4f*unit));if(mode==MotionMode.CIRCLES)drawCircle(color.copy(alpha=.035f),radius,p) }
            MotionMode.NET->{
                val points=positions.indices.map(::point)
                points.forEachIndexed { i,p ->
                    for(j in i+1..minOf(points.lastIndex,i+8))if((points[j]-p).getDistance()<min(size.width,size.height)*.38f)drawLine(color.copy(alpha=.13f),p,points[j],.7f*unit)
                    drawCircle(color.copy(alpha=.32f),1.6f*unit,p)
                }
            }
            MotionMode.SPACE,MotionMode.PARTICLES->positions.indices.forEach { i -> drawCircle(color.copy(alpha=if(i%3==0).42f else .23f),(if(mode==MotionMode.SPACE).8f+i%3*.4f else 1.2f+i%4*.5f)*unit,point(i)) }
            MotionMode.SNOW->positions.indices.forEach { i -> val p=point(i);drawCircle(Color.White.copy(alpha=.36f),(1.1f+i%4*.45f)*unit,Offset(p.x,wrap(p.y+t*18*unit,size.height))) }
            MotionMode.GRID->{
                val gap=(56-a.density.coerceIn(0f,1f)*24)*unit
                val dx=wrap(t*5*unit+shift.x,gap);val dy=wrap(t*3*unit+shift.y,gap)
                for(i in -1..ceil(size.width/gap).toInt())drawLine(color.copy(alpha=.09f),Offset(i*gap+dx,0f),Offset(i*gap+dx,size.height),.6f*unit)
                for(i in -1..ceil(size.height/gap).toInt())drawLine(color.copy(alpha=.09f),Offset(0f,i*gap+dy),Offset(size.width,i*gap+dy),.6f*unit)
            }
            MotionMode.SHAPES->{val path=Path();positions.indices.forEach { i ->path.reset();val p=point(i);for(j in 0..4){val angle=j*2*PI/5+t*.15f;val q=p+Offset(cos(angle).toFloat(),sin(angle).toFloat())*((12+i%4*5)*unit);if(j==0)path.moveTo(q.x,q.y)else path.lineTo(q.x,q.y)};path.close();drawPath(path,color.copy(alpha=.2f),style=Stroke(1.2f*unit)) }}
            MotionMode.LAVA->positions.indices.forEach { i -> val p=point(i)+Offset(sin(t+i)*18*unit,cos(t*.7f+i)*24*unit);val radius=max(size.width,size.height)*(.22f+(i%3)*.04f);drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.18f),Color.Transparent),p,radius),radius,p) }
            MotionMode.NONE->if(a.parallax&&!a.oled){val p=center+shift*3f;val radius=max(size.width,size.height)*.55f;drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.08f),Color.Transparent),p,radius),radius,p)}
            else->Unit
        }
    }
}
