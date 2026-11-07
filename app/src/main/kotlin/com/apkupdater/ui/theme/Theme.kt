package com.apkupdater.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import com.apkupdater.glass.LocalAppearance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.apkupdater.util.isDark

@Composable
fun AppTheme(darkTheme: Boolean, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val a = LocalAppearance.current
    val effectiveDark = if(a.oled) true else if(a.customBackground) Color(a.background.toInt()).luminance() < .3f else darkTheme
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if(effectiveDark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        effectiveDark -> darkColorScheme(primary = Color(0xffc8b9ff), onPrimary = Color(0xff25164a), secondary = Color(0xff8cdae2), background = Color(0xff080b13), onBackground = Color(0xfff4f3fb), surface = Color(0xff121724), onSurface = Color(0xfff4f3fb), onSurfaceVariant = Color(0xffb9bfce), outline = Color(0xff4c5266))
        else -> lightColorScheme(primary = Color(0xff5f43ae), background = Color(0xffedf0f7), onBackground = Color(0xff172033), surface = Color.White, onSurface = Color(0xff172033), onSurfaceVariant = Color(0xff50596d))
    }
    val accentRaw = if(a.customAccent) Color(a.accent.toInt()) else base.primary
    val accent=lerp(base.surface,accentRaw,a.accentAlpha)
    val background = if(a.oled) Color.Black else if(a.customBackground) lerp(base.background,Color(a.background.toInt()),a.backgroundAlpha) else base.background
    val scheme = base.copy(primary = accent, onPrimary = if(accent.luminance() > .4f) Color(0xff10131c) else Color.White,
        primaryContainer = lerp(base.surface, accent, .2f), background = background)
    val family = when(a.font) { 1 -> FontFamily.SansSerif; 2 -> FontFamily.Serif; 3 -> FontFamily.Monospace; else -> FontFamily.Default }
    fun scaled(style: androidx.compose.ui.text.TextStyle) = style.copy(fontFamily = family, fontSize = style.fontSize * a.textScale, lineHeight = style.lineHeight * a.textScale)
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
