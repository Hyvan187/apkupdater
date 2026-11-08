package org.uniapk.upgrader.ui.theme

import androidx.compose.animation.animateColorAsState
import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import org.uniapk.upgrader.glass.AppearancePalettes
import org.uniapk.upgrader.glass.LocalAppearance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import org.uniapk.upgrader.util.isDark

@Composable
fun AppTheme(darkTheme: Boolean, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val a = LocalAppearance.current
    val effectiveDark = a.oled || darkTheme
    val dusk=a.theme==3&&!a.oled
    val rawSeed=org.uniapk.upgrader.glass.PaletteModel.seed(a)
    // Palette changes flow through the same roles; theme brightness changes start a fresh palette.
    val seed=key(effectiveDark,dusk,a.oled,a.dynamic){animateColorAsState(rawSeed,org.uniapk.upgrader.glass.UniMotion.spec(org.uniapk.upgrader.glass.motionAllowed()),label="base-palette").value}
    val localScheme=remember(seed,effectiveDark,dusk,a.oled){org.uniapk.upgrader.glass.PaletteModel.scheme(seed,effectiveDark,dusk,a.oled)}
    val scheme=if(dynamicColor&&Build.VERSION.SDK_INT>=31){
        val system=if(effectiveDark)dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        if(a.oled)system.copy(background=Color.Black) else if(dusk)org.uniapk.upgrader.glass.PaletteModel.scheme(system.primary,true,true) else system
    }else localScheme
    val family = when(a.font) { 1 -> FontFamily.SansSerif; 2 -> FontFamily.Serif; 3 -> FontFamily.Monospace; else -> FontFamily.Default }
    fun scaled(style: androidx.compose.ui.text.TextStyle) = style.copy(fontFamily = family, fontSize = style.fontSize * a.textScale * (if(a.compact).96f else 1f), lineHeight = style.lineHeight * a.textScale * (if(a.compact).96f else 1f))
    val typography = Typography().let { t -> t.copy(
        headlineLarge = scaled(t.headlineLarge), headlineMedium = scaled(t.headlineMedium), headlineSmall = scaled(t.headlineSmall),
        titleLarge = scaled(t.titleLarge), titleMedium = scaled(t.titleMedium), titleSmall = scaled(t.titleSmall),
        bodyLarge = scaled(t.bodyLarge), bodyMedium = scaled(t.bodyMedium), bodySmall = scaled(t.bodySmall),
        labelLarge = scaled(t.labelLarge), labelMedium = scaled(t.labelMedium), labelSmall = scaled(t.labelSmall)) }
    val view = LocalView.current
    if(!view.isInEditMode) SideEffect {
        val activity = view.context as Activity
        WindowCompat.getInsetsController(activity.window, view).apply { isAppearanceLightStatusBars = !effectiveDark; isAppearanceLightNavigationBars = !effectiveDark }
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
fun ColorScheme.statusBarColor() = surfaceColorAtElevation(3.dp)
fun isDarkTheme(theme: Int): Boolean = when(theme) { 1 -> true; 2 -> false; else -> isDark() }

