package com.apkupdater.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.apkupdater.util.isDark

@Composable
fun AppTheme(darkTheme: Boolean, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if(darkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        darkTheme -> darkColorScheme(primary = Color(0xffc8b9ff), onPrimary = Color(0xff25164a), secondary = Color(0xff8cdae2), background = Color(0xff080b13), onBackground = Color(0xfff4f3fb), surface = Color(0xff121724), onSurface = Color(0xfff4f3fb), onSurfaceVariant = Color(0xffb9bfce), outline = Color(0xff4c5266))
        else -> lightColorScheme(primary = Color(0xff5f43ae), background = Color(0xffedf0f7), onBackground = Color(0xff172033), surface = Color.White, onSurface = Color(0xff172033), onSurfaceVariant = Color(0xff50596d))
    }
    val view = LocalView.current
    if(!view.isInEditMode) SideEffect {
        val activity = view.context as Activity
        WindowCompat.getInsetsController(activity.window, view).apply { isAppearanceLightStatusBars = !darkTheme; isAppearanceLightNavigationBars = !darkTheme }
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
fun ColorScheme.statusBarColor() = surfaceColorAtElevation(3.dp)
fun isDarkTheme(theme: Int): Boolean = when(theme) { 1 -> true; 2 -> false; else -> isDark() }
