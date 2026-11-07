package com.apkupdater.glass

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

data class DeviceLayout(val dpi: Int = 160, val widthDp: Int = 412, val heightDp: Int = 915, val fontScale: Float = 1f) {
    val horizontalPadding get() = if (widthDp < 360) 12.dp else if (widthDp >= 600) 24.dp else 20.dp
    val contentMaxWidth get() = if (widthDp >= 840) 1000.dp else 840.dp
    companion object {
        fun resolve(dpi: Int, windowWidthDp: Int, windowHeightDp: Int, widthPixels: Int, heightPixels: Int, fontScale: Float): DeviceLayout {
            val effectiveDpi = dpi.takeIf { it > 0 } ?: 160
            val density = effectiveDpi / 160f
            return DeviceLayout(effectiveDpi, windowWidthDp.takeIf { it > 0 } ?: (widthPixels / density).toInt().coerceAtLeast(1), windowHeightDp.takeIf { it > 0 } ?: (heightPixels / density).toInt().coerceAtLeast(1), fontScale)
        }
    }
}
val LocalDeviceLayout = staticCompositionLocalOf { DeviceLayout() }
@Composable fun DeviceLayoutProvider(content: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    val metrics = LocalContext.current.resources.displayMetrics
    val density = LocalDensity.current
    val profile = DeviceLayout.resolve(config.densityDpi, config.screenWidthDp, config.screenHeightDp, metrics.widthPixels, metrics.heightPixels, density.fontScale)
    CompositionLocalProvider(LocalDeviceLayout provides profile, content = content)
}
