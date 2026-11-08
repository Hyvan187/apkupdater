package org.uniapk.upgrader.glass

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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

enum class MotionMode(val label:String){NONE("Keine"),CIRCLES("Kreise"),RINGS("Ringe"),NET("Netz"),SPACE("Weltraum"),SHAPES("Formen"),SNOW("Schnee"),GRID("Raster"),PARTICLES("Partikel"),LAVA("Lavalampe"),RANDOM("Zufällig"),AURORA("Polarlicht"),NEBULA("Farbnebel"),PRISM("Prismen"),WAVES("Farbwellen"),CONFETTI("Konfetti"),MATRIX("Matrix"),FIREFLIES("Leuchtwürmchen")}
object MotionPolicy {
    fun active(resumed:Boolean,reduced:Boolean,systemMotion:Boolean,powerSave:Boolean)=resumed && !reduced && systemMotion && !powerSave
    fun mode(index:Int,seasonal:Boolean,month:Int,seed:Int):MotionMode {
        val requested=MotionMode.entries.getOrElse(index){MotionMode.NONE}
        if(requested!=MotionMode.RANDOM)return requested
        if(seasonal && month==12)return MotionMode.SNOW
        if(seasonal && month==1)return MotionMode.PARTICLES
        val modes=MotionMode.entries.filter{it!=MotionMode.NONE&&it!=MotionMode.RANDOM};return modes[Math.floorMod(seed,modes.size)]
    }
    fun count(mode:MotionMode,density:Float):Int {
        val d=density.coerceIn(0f,1f)
        if(d==0f)return 0
        return when(mode){MotionMode.NONE->0;MotionMode.LAVA->(2+d*6).toInt();MotionMode.NET->(8+d*36).toInt();MotionMode.AURORA,MotionMode.NEBULA->(2+d*7).toInt();MotionMode.WAVES->(2+d*12).toInt();MotionMode.MATRIX->(5+d*27).toInt();MotionMode.GRID->(8+d*24).toInt();MotionMode.CIRCLES,MotionMode.RINGS,MotionMode.SHAPES,MotionMode.PRISM->(5+d*28).toInt();else->(12+d*100).toInt()}
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
    val reason=when { a.density<=0f->"Effektdichte ist 0 %";mode==MotionMode.NONE->"Keine Animation gewählt";a.reduceMotion->"Animationen reduzieren ist aktiv";!systemMotion->"Android hat Animationen deaktiviert";saver->"Durch Energiesparmodus pausiert";!active->"App im Hintergrund";else->"Aktiv" }
    SideEffect { MotionRuntime.report(MotionStatus(mode,active&&mode!=MotionMode.NONE&&a.density>0f,reason)) }
    var seconds by remember { mutableFloatStateOf(0f) };var tilt by remember { mutableStateOf(Offset.Zero) }
    val cpu by ComputeBudget.state.collectAsState()
    val fps=minOf(LocalBackgroundFrameRate.current,ComputeBudget.fps()).coerceIn(10,60)
    LaunchedEffect(active,mode,fps,a.density>0f) {
        if(active&&mode!=MotionMode.NONE&&a.density>0f) {
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
    val count=MotionPolicy.count(mode,a.density*minOf(1f,cpu/4f))
    val positions=remember(mode,count,seed){List(count){MotionPolicy.point(it,seed)}}
    val accent=if(a.customAccent)Color(a.accent.toInt()) else MaterialTheme.colorScheme.primary
    val palette=when(a.motionPalette){1->listOf(Color(a.motionColor.toInt()));2->listOf(Color(a.motionColor.toInt()),Color(a.motionSecondary.toInt()));else->when(mode){MotionMode.MATRIX->listOf(Color(0xff47ed91));MotionMode.SNOW,MotionMode.SPACE->listOf(Color(0xffe5edff));MotionMode.LAVA->listOf(Color(0xffff8e72),Color(0xffa36cff));MotionMode.FIREFLIES->listOf(Color(0xffd5ff77),Color(0xff83f4b1));MotionMode.AURORA->listOf(Color(0xff62fba3),Color(0xff39d5a9),Color(0xffb08bff));MotionMode.NEBULA,MotionMode.WAVES->listOf(Color(0xffad64ff),Color(0xff25cde8),Color(0xffff6ca8),Color(0xff6bf4ac));MotionMode.PRISM,MotionMode.CONFETTI->listOf(Color(0xffed737e),Color(0xffffcf65),Color(0xff65e1ba),Color(0xff70b6ff),Color(0xffb688f7));else->listOf(accent)}}
    val color=palette.first()
    val glyphPaint=remember{android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{typeface=android.graphics.Typeface.MONOSPACE}}
    if(mode==MotionMode.NONE&&!a.parallax)return
    Canvas(modifier) {
        if(size.width<=0f||size.height<=0f)return@Canvas
        val unit=1.dp.toPx();val t=seconds*(.25f+a.speed.coerceIn(.1f,1f)*1.6f)
        val shift=if(a.parallax&&active)tilt*(12*unit) else Offset.Zero
        fun wrap(value:Float,range:Float)=((value%range)+range)%range
        fun point(i:Int):Offset {val p=positions[i];return Offset(wrap(p.x*size.width+t*(5+(i%4))*unit+shift.x,size.width),wrap(p.y*size.height+t*(3+(i%3))*unit+shift.y,size.height))}
        fun tint(i:Int)=palette[Math.floorMod(i,palette.size)]
        if(count==0&&mode!=MotionMode.NONE)return@Canvas
        when(mode){
            MotionMode.CIRCLES->positions.indices.forEach{i->val p=point(i);val radius=(10+i%6*6)*unit;drawCircle(tint(i).copy(alpha=.09f),radius,p);drawCircle(tint(i).copy(alpha=.18f),radius,p,style=Stroke(.7f*unit))}
            MotionMode.RINGS->positions.indices.forEach{i->val p=point(i);val radius=(16+i%5*8)*unit;drawCircle(tint(i).copy(alpha=.22f),radius,p,style=Stroke(1.5f*unit));drawCircle(tint(i).copy(alpha=.08f),radius+5*unit,p,style=Stroke(.5f*unit))}
            MotionMode.NET->{val points=positions.indices.map(::point);points.forEachIndexed{i,p->for(j in i+1..minOf(points.lastIndex,i+8))if((points[j]-p).getDistance()<min(size.width,size.height)*.32f)drawLine(tint(i).copy(alpha=.15f),p,points[j],.7f*unit);drawCircle(tint(i).copy(alpha=.4f),1.7f*unit,p)}}
            MotionMode.SPACE->positions.indices.forEach{i->val p=positions[i];val angle=p.x*2*PI;val radius=((p.y+t*.045f)%1f).pow(2);val edge=hypot(size.width,size.height)*.55f;val direction=Offset(cos(angle).toFloat(),sin(angle).toFloat());val star=center+direction*radius*edge+shift;drawLine(tint(i).copy(alpha=.35f),star-direction*(1+radius*9)*unit,star,(.6f+radius)*unit)}
            MotionMode.PARTICLES->positions.indices.forEach{i->val p=point(i);drawCircle(tint(i).copy(alpha=.12f),4*unit,p);drawCircle(tint(i).copy(alpha=.5f),(1+i%3*.6f)*unit,p)}
            MotionMode.SNOW->positions.indices.forEach{i->val v=positions[i];val p=Offset(wrap(v.x*size.width+sin(t*.4f+i)*12*unit+shift.x,size.width),wrap(v.y*size.height+t*(9+i%5*3)*unit+shift.y,size.height));val radius=(1.5f+i%4)*unit;if(i%3==0)repeat(3){j->val angle=j*PI/3;val d=Offset(cos(angle).toFloat(),sin(angle).toFloat())*radius;drawLine(tint(i).copy(alpha=.4f),p-d,p+d,.7f*unit)}else drawCircle(tint(i).copy(alpha=.32f),radius*.5f,p)}
            MotionMode.GRID->{val gap=size.width/count.coerceAtLeast(1);val dx=wrap(t*3*unit+shift.x,gap);val dy=wrap(t*2*unit+shift.y,gap);for(i in -1..ceil(size.width/gap).toInt())drawLine(color.copy(alpha=.13f),Offset(i*gap+dx,0f),Offset(i*gap+dx,size.height),.6f*unit);for(i in -1..ceil(size.height/gap).toInt())drawLine(tint(i).copy(alpha=.13f),Offset(0f,i*gap+dy),Offset(size.width,i*gap+dy),.6f*unit)}
            MotionMode.SHAPES->positions.indices.forEach{i->val path=Path();val p=point(i);val corners=3+i%4;repeat(corners){j->val angle=j*2*PI/corners+t*.12f;val q=p+Offset(cos(angle).toFloat(),sin(angle).toFloat())*((10+i%4*5)*unit);if(j==0)path.moveTo(q.x,q.y)else path.lineTo(q.x,q.y)};path.close();drawPath(path,tint(i).copy(alpha=.22f),style=Stroke(1.2f*unit))}
            MotionMode.LAVA->positions.indices.forEach{i->val p=Offset(size.width*(.15f+positions[i].x*.7f)+sin(t*.25f+i)*size.width*.12f,wrap(positions[i].y*size.height-t*(7+i%3)*unit,size.height));val radius=min(size.width,size.height)*(.13f+i%3*.035f);val path=Path();repeat(33){j->val angle=j*2*PI/32;val r=radius*(1+.12f*sin(angle*3+t*.5f+i).toFloat());val v=p+Offset(cos(angle).toFloat(),sin(angle).toFloat())*r;if(j==0)path.moveTo(v.x,v.y)else path.lineTo(v.x,v.y)};path.close();drawPath(path,Brush.radialGradient(listOf(tint(i).copy(alpha=.26f),tint(i).copy(alpha=.05f)),p,radius*1.2f))}
            MotionMode.NEBULA->positions.indices.forEach{i->val p=Offset(positions[i].x*size.width+sin(t*.2f+i)*size.width*.12f,positions[i].y*size.height+cos(t*.17f+i)*size.height*.1f)+shift;val radius=max(size.width,size.height)*(.34f+i%3*.06f);drawCircle(Brush.radialGradient(listOf(tint(i).copy(alpha=.19f),Color.Transparent),p,radius),radius,p)}
            MotionMode.AURORA->{
                val bands=(1+(count-2).coerceAtLeast(0)/3).coerceAtMost(3)
                repeat(bands){b->
                    fun edge(u:Float)=size.height*(.23f+b*.19f)+sin(u*5.7f+t*.12f+b*.8f)*size.height*.1f+sin(u*12.5f-t*.09f+b)*size.height*.028f-u*size.height*.08f
                    val crest=Path();for(x in 0..128){val u=x/128f;val y=edge(u);if(x==0)crest.moveTo(0f,y)else crest.lineTo(u*size.width,y)}
                    drawPath(crest,tint(b).copy(alpha=.025f),style=Stroke(24*unit));drawPath(crest,tint(b).copy(alpha=.05f),style=Stroke(11*unit));drawPath(crest,tint(b).copy(alpha=.19f),style=Stroke(1.8f*unit))
                    val rays=(30+a.density*100).toInt()
                    repeat(rays){x->val u=(x+.3f*sin(x*2.7f+b))/rays;val px=u*size.width;val y=edge(u);val breath=.5f+.5f*sin(u*9+t*.16f+b);val length=size.height*(.12f+.18f*breath)*(.8f+.2f*sin(x*1.7f+t*.3f).pow(2));val opacity=.035f+.065f*(.5f+.5f*sin(x*1.31f+t*.23f+b))
                        drawLine(Brush.verticalGradient(listOf(tint(b).copy(alpha=opacity),tint(b).copy(alpha=opacity*.3f),Color.Transparent),y,y+length),Offset(px,y),Offset(px+sin(t*.14f+b+u*4)*16*unit,y+length),size.width/rays*1.8f)
                    }
                }
            }
            MotionMode.FIREFLIES->positions.indices.forEach{i->val origin=positions[i];val p=Offset(wrap(origin.x*size.width+sin(t*.25f+i*1.7f)*(10+i%5*5)*unit+cos(t*.12f+i)*8*unit,size.width),wrap(origin.y*size.height+cos(t*.21f+i*.9f)*(12+i%4*7)*unit,size.height))+shift;val pulse=(.18f+.82f*((sin(t*(.6f+i%3*.13f)+i*2.4f)+1)/2).pow(3));val radius=(6+i%3*2)*unit;drawCircle(Brush.radialGradient(listOf(tint(i).copy(alpha=.35f*pulse),Color.Transparent),p,radius),radius,p);drawCircle(tint(i).copy(alpha=.9f*pulse),(.8f+i%3*.3f)*unit,p)}
            MotionMode.WAVES->positions.indices.forEach{i->val path=Path();val base=size.height*(i+1)/(count+1);for(x in 0..64){val px=size.width*x/64f;val py=base+sin(x*.12f+t*.5f+i*.5f)*size.height*.035f;if(x==0)path.moveTo(px,py)else path.lineTo(px,py)};drawPath(path,tint(i).copy(alpha=.28f),style=Stroke((1+i%3)*unit))}
            MotionMode.PRISM->positions.indices.forEach{i->val p=point(i);val radius=(12+i%4*5)*unit;rotate(t*4+i*31f,p){val path=Path();path.moveTo(p.x,p.y-radius);path.lineTo(p.x+radius*.87f,p.y+radius*.5f);path.lineTo(p.x-radius*.87f,p.y+radius*.5f);path.close();drawPath(path,Brush.linearGradient(listOf(tint(i).copy(alpha=.02f),tint(i+1).copy(alpha=.23f)),p-Offset(radius,radius),p+Offset(radius,radius)));drawPath(path,tint(i).copy(alpha=.32f),style=Stroke(.8f*unit))}}
            MotionMode.CONFETTI->positions.indices.forEach{i->val v=positions[i];val p=Offset(wrap(v.x*size.width+sin(t*.4f+i)*10*unit,size.width),wrap(v.y*size.height+t*(14+i%8)*unit,size.height))+shift;rotate(t*(8+i%4*4)+i*37f,p){drawRoundRect(tint(i).copy(alpha=.48f),p,androidx.compose.ui.geometry.Size((2+i%3)*unit,(5+i%4)*unit),androidx.compose.ui.geometry.CornerRadius(.7f*unit))}}
            MotionMode.MATRIX->{val chars="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";val font=14*unit;glyphPaint.textSize=font;positions.indices.forEach{i->val x=(i+.5f)*size.width/count;val tail=10+i%7;val head=wrap(positions[i].y*size.height+t*(22+i%5*8)*unit,size.height+tail*font);repeat(tail){j->val y=head-j*font;if(y in 0f..size.height){val c=if(j==0)androidx.compose.ui.graphics.lerp(tint(i),Color.White,.65f)else tint(i);glyphPaint.color=c.copy(alpha=if(j==0).7f else .46f*(1-j.toFloat()/tail)).toArgb();drawContext.canvas.nativeCanvas.drawText(chars[Math.floorMod(i*17+j*13+(t*5).toInt(),chars.length)].toString(),x,y,glyphPaint)}}}}
            MotionMode.NONE->if(a.parallax&&!a.oled){val p=center+shift*3f;val radius=max(size.width,size.height)*.55f;drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.08f),Color.Transparent),p,radius),radius,p)}
            else->Unit
        }
    }
}

