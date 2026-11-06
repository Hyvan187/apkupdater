package com.apkupdater.glass

import android.os.Build
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

private data class Backdrop(val layer: GraphicsLayer, val origin: MutableState<Offset>)
private val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** The same recorded scene is sampled in each glass panel. Text is never blurred. */
@Composable
fun GlassScene(content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val origin = remember { mutableStateOf(Offset.Zero) }
    CompositionLocalProvider(LocalBackdrop provides Backdrop(layer, origin)) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin.value = it.positionInRoot() }
                .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
                drawRect(Brush.linearGradient(listOf(Color(0xff080d1d), Color(0xff17244d), Color(0xff211439))))
                drawCircle(Brush.radialGradient(listOf(Color(0xff5754bd), Color.Transparent), Offset(size.width * .85f, size.height * .24f), size.width * .8f), size.width * .8f, Offset(size.width * .85f, size.height * .24f))
                drawCircle(Brush.radialGradient(listOf(Color(0xff126d89), Color.Transparent), Offset(size.width * .12f, size.height * .75f), size.width * .7f), size.width * .7f, Offset(size.width * .12f, size.height * .75f))
            }
            content()
        }
    }
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, effectLevel: Int = 2, content: @Composable ColumnScope.() -> Unit) {
    val backdrop = LocalBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf(Offset(80f, 30f)) }
    val scale by animateFloatAsState(if (pressed && effectLevel > 0) .99f else 1f, label = "glass-pressure")
    val shape = RoundedCornerShape(26.dp)
    val effect = remember(effectLevel) {
        if (Build.VERSION.SDK_INT >= 31 && effectLevel > 0) {
            val blur = RenderEffect.createBlurEffect(if (effectLevel == 1) 8f else 18f, if (effectLevel == 1) 8f else 18f, Shader.TileMode.CLAMP)
            if (Build.VERSION.SDK_INT >= 33 && effectLevel >= 3) {
                val shader = RuntimeShader("""
                    uniform shader scene;
                    half4 main(float2 p) {
                        float2 shift = float2(sin(p.y * 0.025), cos(p.x * 0.025)) * 2.0;
                        return scene.eval(p + shift);
                    }
                """.trimIndent())
                RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "scene"), blur).asComposeRenderEffect()
            } else blur.asComposeRenderEffect()
        } else null
    }
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
        .pointerInput(effectLevel) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    pressed = event.changes.any { it.pressed }
                    event.changes.firstOrNull()?.let { highlight = it.position }
                }
            }
        }.onGloballyPositioned { pos = it.positionInRoot() }
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.48f), Color.White.copy(.06f), Color(0xffa6c5ff).copy(.32f))), shape)) {
        if (backdrop != null && effectLevel > 0) {
            Box(Modifier.matchParentSize().graphicsLayer { renderEffect = effect }
                .drawWithContent {
                    val delta = backdrop.origin.value - pos
                    drawContext.canvas.save()
                    drawContext.canvas.translate(delta.x, delta.y)
                    drawLayer(backdrop.layer)
                    drawContext.canvas.restore()
                })
        }
        if (effectLevel > 0) Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.radialGradient(listOf(Color.White.copy(if (pressed) .18f else .07f), Color.Transparent), highlight, size.width * .8f))
        }
        Column(Modifier.background(if (effectLevel == 0) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface.copy(.45f))
            .fillMaxWidth().padding(20.dp), content = content)
    }
}
