package org.uniapk.upgrader.glass

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Shared durations; no blur/shader interpolation or independent infinite transition per card. */
object UniMotion {
    const val SHORT=180
    const val STANDARD=280
    fun duration(allowed:Boolean)=if(allowed)STANDARD else 0
    fun <T> spec(allowed:Boolean)=tween<T>(duration(allowed),easing=FastOutSlowInEasing)
}
private val LocalMotionAllowed=staticCompositionLocalOf{true}
@Composable fun motionAllowed()=LocalMotionAllowed.current&&!LocalAppearance.current.reduceMotion

/** One system observer for the scene, rather than one listener per glass card. */
@Composable fun MotionEnvironment(content:@Composable ()->Unit){
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val power=remember(context){context.getSystemService(Context.POWER_SERVICE) as PowerManager}
    var revision by remember{mutableIntStateOf(0)}
    DisposableEffect(context){
        val receiver=object:android.content.BroadcastReceiver(){override fun onReceive(c:Context,i:android.content.Intent){revision++}}
        androidx.core.content.ContextCompat.registerReceiver(context,receiver,android.content.IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer=object:android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())){override fun onChange(selfChange:Boolean){revision++}}
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose{context.unregisterReceiver(receiver);context.contentResolver.unregisterContentObserver(observer)}
    }
    val systemAllowed=remember(revision,state){runCatching{!power.isPowerSaveMode&&Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f}.getOrDefault(false)}
    val cpu by ComputeBudget.state.collectAsState()
    CompositionLocalProvider(LocalMotionAllowed provides (systemAllowed&&cpu>1&&state.isAtLeast(Lifecycle.State.RESUMED)&&!LocalAppearance.current.reduceMotion)){content()}
}

enum class SettingsCategory(val title:String,val summary:String){
    GENERAL("Allgemein","Sprache, Startansicht, Schnellzugriff und Berechtigungen"),
    APPEARANCE("Erscheinungsbild","Design, Farbpalette, Glas, Icons und Leistung"),
    UPDATES("Updates & Versionen","Quellen, Versionsfilter, Downloads und Installation"),
    SYSTEM("Erweitert / System","Cache, Verlauf, Diagnose und App-Informationen")
}
object StartViews {
    val routes=listOf("overview","updates","apps")
    val labels=listOf("Startübersicht","Updates","Installierte Apps")
    fun valid(route:String)=route.takeIf{it in routes} ?: "overview"
}
