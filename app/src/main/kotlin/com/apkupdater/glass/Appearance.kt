package com.apkupdater.glass

import com.apkupdater.prefs.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Appearance(
    val theme: Int = 1, val dynamic: Boolean = false,
    val accent: Long = 0xffc8b9ff, val background: Long = 0xff080b13,
    val customAccent: Boolean = false, val customBackground: Boolean = false,
    val quality: Int = 2, val reduceMotion: Boolean = false,
    val blur: Boolean = true, val refraction: Boolean = true, val progressiveBlur: Boolean = false,
    val opacity: Float = .46f, val font: Int = 0, val textScale: Float = 1f
)
object ColorInput {
    fun parse(text: String): Long? = text.removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLongOrNull(16)?.let { it or 0xff000000L }
    fun hex(value: Long) = "%06X".format(value and 0xffffff)
}
class AppearanceStore(private val prefs: Prefs) {
    private val mutable = MutableStateFlow(Appearance(
        theme = prefs.theme.get(), dynamic = prefs.dynamicColors.get(), accent = prefs.accentColor.get(), background = prefs.backgroundColor.get(),
        customAccent = prefs.customAccent.get(), customBackground = prefs.customBackground.get(), quality = prefs.effectLevel.get().coerceIn(0,3),
        reduceMotion = prefs.reduceMotion.get(), blur = prefs.blurEnabled.get(), refraction = prefs.refractionEnabled.get(), progressiveBlur = prefs.progressiveBlur.get(),
        opacity = prefs.glassOpacity.get() / 100f, font = prefs.fontChoice.get(), textScale = prefs.textScale.get() / 100f
    ))
    val state: StateFlow<Appearance> = mutable
    fun update(change: (Appearance) -> Appearance) {
        val v = change(mutable.value)
        prefs.theme.put(v.theme); prefs.dynamicColors.put(v.dynamic); prefs.accentColor.put(v.accent); prefs.backgroundColor.put(v.background)
        prefs.customAccent.put(v.customAccent); prefs.customBackground.put(v.customBackground); prefs.effectLevel.put(v.quality)
        prefs.reduceMotion.put(v.reduceMotion); prefs.blurEnabled.put(v.blur); prefs.refractionEnabled.put(v.refraction); prefs.progressiveBlur.put(v.progressiveBlur)
        prefs.glassOpacity.put((v.opacity * 100).toInt()); prefs.fontChoice.put(v.font); prefs.textScale.put((v.textScale * 100).toInt())
        mutable.value = v
    }
    fun reset() = update { Appearance() }
}
