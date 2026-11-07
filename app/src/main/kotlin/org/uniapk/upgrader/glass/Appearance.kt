package org.uniapk.upgrader.glass

import org.uniapk.upgrader.prefs.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Appearance(
    val theme: Int = 1, val dynamic: Boolean = false,
    val accent: Long = 0xffc8b9ff, val background: Long = 0xff080b13,
    val customAccent: Boolean = false, val customBackground: Boolean = false,
    val quality: Int = 2, val reduceMotion: Boolean = false,
    val blur: Boolean = true, val refraction: Boolean = true, val progressiveBlur: Boolean = false,
    val oled: Boolean=false,val motion: Int=0,val speed: Float=.5f,val density: Float=.35f,val parallax: Boolean=false,val seasonal: Boolean=false,val accentAlpha: Float=1f,val backgroundAlpha: Float=1f,
    val opacity: Float = .46f, val font: Int = 0, val textScale: Float = 1f
)
object ColorInput {
    fun parse(text: String): Long? = text.removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLongOrNull(16)?.let { it or 0xff000000L }
    fun hex(value: Long) = "%06X".format(value and 0xffffff)
}
class AppearanceStore(private val prefs: Prefs) {
    private val mutable = MutableStateFlow(Appearance(
        oled=prefs.oledMode.get(),motion=prefs.backgroundMotion.get(),speed=prefs.motionSpeed.get()/100f,density=prefs.motionDensity.get()/100f,parallax=prefs.parallax.get(),seasonal=prefs.seasonal.get(),accentAlpha=prefs.accentOpacity.get()/100f,backgroundAlpha=prefs.backgroundOpacity.get()/100f,
        theme = prefs.theme.get(), dynamic = prefs.dynamicColors.get(), accent = prefs.accentColor.get(), background = prefs.backgroundColor.get(),
        customAccent = prefs.customAccent.get(), customBackground = prefs.customBackground.get(), quality = prefs.effectLevel.get().coerceIn(0,3),
        reduceMotion = prefs.reduceMotion.get(), blur = prefs.blurEnabled.get(), refraction = prefs.refractionEnabled.get(), progressiveBlur = prefs.progressiveBlur.get(),
        opacity = prefs.glassOpacity.get().coerceIn(0, 100) / 100f, font = prefs.fontChoice.get(), textScale = prefs.textScale.get() / 100f
    ))
    val state: StateFlow<Appearance> = mutable
    private var persisted = mutable.value
    fun preview(change:(Appearance)->Appearance) { mutable.value=change(mutable.value).let { it.copy(opacity=it.opacity.coerceIn(0f,1f)) } }
    fun commitPreview() = update { it }
    fun update(change: (Appearance) -> Appearance) {
        val old = persisted
        val v = change(mutable.value).let { it.copy(opacity = it.opacity.coerceIn(0f, 1f)) }
        // A slider changes one encrypted preference, not every appearance key.
        if (old.oled != v.oled) prefs.oledMode.put(v.oled)
        if (old.motion != v.motion) prefs.backgroundMotion.put(v.motion)
        if (old.speed != v.speed) prefs.motionSpeed.put((v.speed * 100).toInt())
        if (old.density != v.density) prefs.motionDensity.put((v.density * 100).toInt())
        if (old.parallax != v.parallax) prefs.parallax.put(v.parallax)
        if (old.seasonal != v.seasonal) prefs.seasonal.put(v.seasonal)
        if (old.accentAlpha != v.accentAlpha) prefs.accentOpacity.put((v.accentAlpha * 100).toInt())
        if (old.backgroundAlpha != v.backgroundAlpha) prefs.backgroundOpacity.put((v.backgroundAlpha * 100).toInt())
        if (old.theme != v.theme) prefs.theme.put(v.theme)
        if (old.dynamic != v.dynamic) prefs.dynamicColors.put(v.dynamic)
        if (old.accent != v.accent) prefs.accentColor.put(v.accent)
        if (old.background != v.background) prefs.backgroundColor.put(v.background)
        if (old.customAccent != v.customAccent) prefs.customAccent.put(v.customAccent)
        if (old.customBackground != v.customBackground) prefs.customBackground.put(v.customBackground)
        if (old.quality != v.quality) prefs.effectLevel.put(v.quality)
        if (old.reduceMotion != v.reduceMotion) prefs.reduceMotion.put(v.reduceMotion)
        if (old.blur != v.blur) prefs.blurEnabled.put(v.blur)
        if (old.refraction != v.refraction) prefs.refractionEnabled.put(v.refraction)
        if (old.progressiveBlur != v.progressiveBlur) prefs.progressiveBlur.put(v.progressiveBlur)
        if (old.opacity != v.opacity) prefs.glassOpacity.put(kotlin.math.round(v.opacity * 100).toInt())
        if (old.font != v.font) prefs.fontChoice.put(v.font)
        if (old.textScale != v.textScale) prefs.textScale.put((v.textScale * 100).toInt())
        mutable.value = v
        persisted = v
    }
    fun recommendedGlass() = update { it.copy(quality=2,opacity=.46f,blur=true,refraction=true,progressiveBlur=false) }
    fun reset() = update { Appearance() }
}
