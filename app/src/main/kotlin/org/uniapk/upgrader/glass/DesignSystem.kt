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
@Composable fun motionAllowed():Boolean {
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    var revision by remember{mutableIntStateOf(0)}
    DisposableEffect(lifecycle){val o=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_RESUME)revision++};lifecycle.addObserver(o);onDispose{lifecycle.removeObserver(o)}}
    val appearance=LocalAppearance.current
    val cpu by ComputeBudget.state.collectAsState()
    return remember(appearance.reduceMotion,revision,cpu){
        !appearance.reduceMotion&&cpu>1&&runCatching{
            Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f&&
                !(context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        }.getOrDefault(false)
    }
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
