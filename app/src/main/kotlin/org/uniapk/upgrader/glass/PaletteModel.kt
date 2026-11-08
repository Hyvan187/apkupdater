package org.uniapk.upgrader.glass

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** One seed produces every UI role. Foreground contrast is bounded even for black/white seeds. */
object PaletteModel {
    fun seed(a:Appearance)=Color((if(a.customPalette)a.paletteSeed else AppearancePalettes.entries[a.palette.coerceIn(0,4)].accent).toInt())
    fun contrast(a:Color,b:Color):Float {val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)}
    fun on(color:Color)=if(contrast(Color.White,color)>=contrast(Color(0xff101116),color))Color.White else Color(0xff101116)
    fun readable(color:Color,background:Color,ratio:Float=4.5f):Color {
        val target=on(background)
        for(i in 0..100){val c=lerp(color,target,i/100f);if(contrast(c,background)>=ratio)return c}
        return target
    }
    fun scheme(seed:Color,dark:Boolean,dusk:Boolean=false,oled:Boolean=false):ColorScheme {
        val base=when{oled->Color.Black;dusk->Color(0xff24232c);dark->Color(0xff090a0f);else->Color(0xfff7f7fb)}
        val background=if(oled)base else lerp(base,seed,if(dark).035f else .025f)
        val surface=lerp(if(dark)Color(0xff16171d)else Color.White,seed,if(dusk).09f else .035f)
        val foreground=readable(if(dark)Color(0xfff3f2f8)else Color(0xff181923),surface,7f)
        val secondarySeed=lerp(seed,if(dark)Color(0xffb6d8e1)else Color(0xff3f646e),.35f)
        val primary=readable(seed,surface)
        val secondary=readable(secondarySeed,surface)
        val tertiary=readable(lerp(seed,if(dark)Color(0xffe4c6c2)else Color(0xff79514c),.35f),surface)
        fun container(c:Color)=lerp(surface,c,if(dark).18f else .12f)
        val pc=container(primary);val sc=container(secondary);val tc=container(tertiary)
        val template=if(dark)darkColorScheme() else lightColorScheme()
        return template.copy(primary=primary,onPrimary=on(primary),primaryContainer=pc,onPrimaryContainer=readable(primary,pc),
            secondary=secondary,onSecondary=on(secondary),secondaryContainer=sc,onSecondaryContainer=readable(secondary,sc),
            tertiary=tertiary,onTertiary=on(tertiary),tertiaryContainer=tc,onTertiaryContainer=readable(tertiary,tc),
            background=background,onBackground=readable(foreground,background,7f),surface=surface,onSurface=foreground,
            surfaceVariant=container(primary),onSurfaceVariant=readable(lerp(foreground,surface,.24f),surface),
            surfaceTint=primary,outline=readable(lerp(foreground,surface,.6f),surface,3f),outlineVariant=lerp(surface,primary,.22f),
            inverseSurface=foreground,inverseOnSurface=on(foreground),inversePrimary=readable(seed,foreground),
            surfaceDim=lerp(surface,Color.Black,if(dark).18f else .06f),surfaceBright=lerp(surface,Color.White,if(dark).08f else .01f),
            surfaceContainerLowest=background,surfaceContainerLow=lerp(background,surface,.65f),surfaceContainer=surface,
            surfaceContainerHigh=lerp(surface,primary,.045f),surfaceContainerHighest=lerp(surface,primary,.08f))
    }
}
