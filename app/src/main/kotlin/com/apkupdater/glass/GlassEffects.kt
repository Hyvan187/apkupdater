package com.apkupdater.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Immutable effect instances are reused instead of recompiling one shader per settings card. */
object GlassEffects {
    private val cache=object:LinkedHashMap<Pair<Int,Boolean>,androidx.compose.ui.graphics.RenderEffect>(16,.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Pair<Int,Boolean>,androidx.compose.ui.graphics.RenderEffect>?)=size>16}
    @RequiresApi(31) @Synchronized fun get(radius:Int,refraction:Boolean):androidx.compose.ui.graphics.RenderEffect = cache.getOrPut(radius to refraction) {
        val blur=RenderEffect.createBlurEffect(radius.toFloat(),radius.toFloat(),Shader.TileMode.CLAMP)
        if(Build.VERSION.SDK_INT>=33&&refraction) {
            val shader=RuntimeShader("""
                uniform shader scene;
                half4 main(float2 p) {
                    float2 d=float2(sin(p.y*.02),cos(p.x*.018))*2.5;
                    half4 c=scene.eval(p+d);
                    c.r=scene.eval(p+d*1.6).r;
                    c.b=scene.eval(p+d*.65).b;
                    return c;
                }
            """.trimIndent())
            RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader,"scene"),blur).asComposeRenderEffect()
        } else blur.asComposeRenderEffect()
    }
}
