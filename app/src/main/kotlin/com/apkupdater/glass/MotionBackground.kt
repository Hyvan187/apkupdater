package com.apkupdater.glass
import android.content.*
import android.hardware.*
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.*

enum class MotionMode(val label:String){NONE("Keine"),CIRCLES("Kreise"),RINGS("Ringe"),NET("Netz"),SPACE("Weltraum"),SHAPES("Formen"),SNOW("Schnee"),GRID("Raster"),PARTICLES("Partikel"),LAVA("Lavalampe"),RANDOM("Zufällig")}
object MotionPolicy {
 fun active(resumed:Boolean,reduced:Boolean,systemMotion:Boolean,powerSave:Boolean)=resumed && !reduced && systemMotion && !powerSave
 fun mode(index:Int,seasonal:Boolean,month:Int,seed:Int):MotionMode {
  if(seasonal && month==12)return MotionMode.SNOW
  if(seasonal && month==1)return MotionMode.PARTICLES
  val m=MotionMode.entries.getOrElse(index){MotionMode.NONE}
  return if(m==MotionMode.RANDOM)MotionMode.entries[1+abs(seed%9)] else m
 }
 fun greeting(month:Int,day:Int)=when{month==12 && day in 24..26->"Frohe Feiertage";month==1 && day==1->"Frohes neues Jahr";else->""}
}
@Composable fun MotionBackground(a:Appearance) {
 val context=LocalContext.current;val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
 val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager
 var saver by remember{mutableStateOf(power.isPowerSaveMode)}
 DisposableEffect(power){val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){saver=power.isPowerSaveMode}}
  if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),Context.RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION")context.registerReceiver(receiver,IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
  onDispose{context.unregisterReceiver(receiver)}
 }
 val systemMotion=runCatching{Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f}.getOrDefault(true)
 val active=MotionPolicy.active(state.isAtLeast(Lifecycle.State.RESUMED),a.reduceMotion,systemMotion,saver)
 val date=java.util.Calendar.getInstance();val seed=remember{kotlin.random.Random.nextInt()};val mode=remember(a.motion,a.seasonal){MotionPolicy.mode(a.motion,a.seasonal,date.get(java.util.Calendar.MONTH)+1,seed)}
 var seconds by remember{mutableFloatStateOf(0f)};var tilt by remember{mutableStateOf(Offset.Zero)}
 LaunchedEffect(active,mode){if(active && mode!=MotionMode.NONE){var start=0L;while(true){withFrameNanos{n->if(start==0L)start=n;seconds=(n-start)/1_000_000_000f};delay(33)}}}
 DisposableEffect(active,a.parallax){val manager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  val listener=object:SensorEventListener{override fun onAccuracyChanged(s:Sensor?,i:Int){};override fun onSensorChanged(e:SensorEvent){tilt=Offset((-e.values[0]*2).coerceIn(-18f,18f),(e.values[1]*2).coerceIn(-18f,18f))}}
  val sensor=manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
  if(active && a.parallax && sensor!=null)manager.registerListener(listener,sensor,SensorManager.SENSOR_DELAY_UI) else tilt=Offset.Zero
  onDispose{manager.unregisterListener(listener)}
 }
 if(mode==MotionMode.NONE && !a.parallax)return
 val color=if(a.customAccent)Color(a.accent.toInt()) else Color(0xffb6b1f5)
 Canvas(Modifier.fillMaxSize()){
  val t=seconds*(.12f+a.speed*.5f);val count=(10+a.density*45).toInt();val shift=if(a.parallax && active)tilt else Offset.Zero
  val points=(0 until count).map{i->val x=((i*.6180339f+t*.015f)%1f)*size.width;val y=((i*.381966f+t*.009f)%1f)*size.height;Offset(x,y)+shift}
  when(mode){
   MotionMode.CIRCLES,MotionMode.RINGS->points.take(12).forEachIndexed{i,p->drawCircle(color.copy(alpha=.05f),30f+i*9f,p,style=Stroke(if(mode==MotionMode.RINGS)2f else 10f))}
   MotionMode.NET->points.forEachIndexed{i,p->points.drop(i+1).filter{(it-p).getDistance()<size.width*.22f}.take(4).forEach{drawLine(color.copy(alpha=.09f),p,it,1f)};points.forEach{drawCircle(color.copy(alpha=.16f),3f,it)}}
   MotionMode.SPACE,MotionMode.PARTICLES->points.forEachIndexed{i,p->drawCircle(color.copy(alpha=if(i%3==0).22f else .09f),if(mode==MotionMode.SPACE)1.5f+(i%3) else 3f+(i%4),p)}
   MotionMode.SNOW->points.forEachIndexed{i,p->drawCircle(Color.White.copy(alpha=.16f),2f+i%4,Offset(p.x,(p.y+t*45f)%size.height))}
   MotionMode.GRID-> {val gap=50f;for(i in 0..(size.width/gap).toInt())drawLine(color.copy(alpha=.035f),Offset(i*gap+shift.x,0f),Offset(i*gap+shift.x,size.height));for(i in 0..(size.height/gap).toInt())drawLine(color.copy(alpha=.035f),Offset(0f,i*gap+shift.y),Offset(size.width,i*gap+shift.y))}
   MotionMode.SHAPES->points.take(16).forEachIndexed{i,p->val path=Path();for(j in 0..5){val angle=j*2*PI/5+t*.1f;val q=p+Offset(cos(angle).toFloat(),sin(angle).toFloat())*(20+i%4*10).toFloat();if(j==0)path.moveTo(q.x,q.y) else path.lineTo(q.x,q.y)};path.close();drawPath(path,color.copy(alpha=.08f),style=Stroke(2f))}
   MotionMode.LAVA->points.take(5).forEachIndexed{i,p->val c=p+Offset(sin(t+i)*40,cos(t+i)*60);drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.10f),Color.Transparent),c,size.width*.45f),size.width*.45f,c)}
   MotionMode.NONE->if(a.parallax && !a.oled){val c=center+shift*6f;drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.065f),Color.Transparent),c,size.width*.65f),size.width*.65f,c)}
   else->Unit
  }
 }
}
