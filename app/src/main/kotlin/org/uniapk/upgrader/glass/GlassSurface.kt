package org.uniapk.upgrader.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/** The background and full scrolling content have distinct layers to avoid recursive capture. */
class GlassBackdrop(val layer: GraphicsLayer, var origin: Offset = Offset.Zero)
private val LocalSceneBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }
val LocalEffectLevel = staticCompositionLocalOf { 2 }

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}
// Sheets and dialogs have separate windows. Use screen coordinates for both layers.
private fun LayoutCoordinates.screenPosition(view: android.view.View): Offset {
    val screen = IntArray(2)
    val window = IntArray(2)
    view.rootView.getLocationOnScreen(screen)
    view.rootView.getLocationInWindow(window)
    return positionInWindow() + Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
}
@Composable
fun Modifier.captureGlass(backdrop: GlassBackdrop): Modifier {
    val view = LocalView.current
    return onGloballyPositioned { backdrop.origin = it.screenPosition(view) }.drawWithContent {
    backdrop.layer.record { this@drawWithContent.drawContent() }
    drawLayer(backdrop.layer)
    }
}

@Composable
fun GlassScene(content: @Composable () -> Unit) {
    val appearance = LocalAppearance.current
    val backdrop = rememberGlassBackdrop()
    val dark = MaterialTheme.colorScheme.background.luminance() < .2f
    val accent = MaterialTheme.colorScheme.primary
    CompositionLocalProvider(LocalSceneBackdrop provides backdrop, LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().captureGlass(backdrop)) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(if(appearance.oled) Color.Black else if(appearance.customBackground) lerp(if(dark) Color(0xff080b13) else Color(0xffedf0f7),Color(appearance.background.toInt()),appearance.backgroundAlpha) else if (dark) Color(0xff080b13) else Color(0xffedf0f7))
                val points = listOf(Offset(.95f * size.width, .16f * size.height), Offset(-.2f * size.width, .6f * size.height), Offset(.85f * size.width, .98f * size.height))
                val colors = if(appearance.customBackground) listOf(accent, accent, accent) else listOf(Color(0xff6d4bd1), Color(0xff166986), Color(0xff9b7733))
                if(!appearance.oled) points.zip(colors).forEach { (center, color) ->
                    drawCircle(Brush.radialGradient(listOf(color.copy(if(dark) .27f else .12f), Color.Transparent), center, size.width * .95f), size.width * .95f, center)
                }
            }
            MotionBackground(appearance)
            }
            content()
        }
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    effectLevel: Int = LocalEffectLevel.current,
    backdrop: GlassBackdrop? = null,
    compact: Boolean = false,
    pill: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val appearance = LocalAppearance.current
    val view = LocalView.current
    val sample = backdrop ?: LocalSceneBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf(Offset(80f, 12f)) }
    val scale by animateFloatAsState(if (pressed && effectLevel > 0 && !appearance.reduceMotion) .985f else 1f, label = "glass-pressure")
    val shape = RoundedCornerShape(if(pill) 100.dp else 28.dp)
    val dark = MaterialTheme.colorScheme.background.luminance() < .2f
    val density=androidx.compose.ui.platform.LocalDensity.current.density
    val effect = remember(effectLevel,appearance.blur,appearance.refraction,appearance.progressiveBlur,backdrop,density) {
        if(Build.VERSION.SDK_INT>=31 && effectLevel>0 && appearance.blur) {
            val radius=((when(effectLevel){1->8f;2->14f;else->18f})*density.coerceAtMost(2.5f)*(if(appearance.progressiveBlur&&backdrop!=null)1.4f else 1f)).toInt().coerceAtLeast(1)
            GlassEffects.get(radius,effectLevel>=2&&appearance.refraction)
        } else null
    }
    val foreground = MaterialTheme.colorScheme.onSurface
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
        .pointerInput(effectLevel) {
            awaitPointerEventScope { while(true) { val e = awaitPointerEvent(); pressed = e.changes.any { it.pressed }; e.changes.firstOrNull()?.let { highlight = it.position } } }
        }.onGloballyPositioned { pos = it.screenPosition(view) }
        .border(.7.dp, Brush.linearGradient(listOf(Color.White.copy(if(dark) .40f else .9f), Color.White.copy(.025f), Color.White.copy(.13f), Color.White.copy(.28f))), shape)) {
        if(sample != null && effect != null) Box(Modifier.matchParentSize().graphicsLayer { renderEffect = effect }.drawWithContent {
            drawContext.canvas.save()
            val offset = sample.origin - pos
            drawContext.canvas.translate(offset.x, offset.y)
            drawLayer(sample.layer)
            drawContext.canvas.restore()
        })
        // Tint opacity must also work in light mode and without RenderEffect support.
        val tint = if(dark) Color(0xff111723) else Color.White
        Box(Modifier.matchParentSize().background(tint.copy(alpha = appearance.opacity.coerceIn(0f, 1f))))
        if(effectLevel > 0) Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.linearGradient(listOf(Color.White.copy(.065f * appearance.opacity), Color.Transparent, Color.White.copy(.02f * appearance.opacity))))
            if(pressed && !appearance.reduceMotion) drawRect(Brush.radialGradient(listOf(Color.White.copy(.12f), Color.Transparent), highlight, size.width * .7f))
        }
        CompositionLocalProvider(LocalContentColor provides foreground) {
            Column(Modifier.fillMaxWidth().padding(if(compact) 8.dp else 18.dp), content = content)
        }
    }
}
