package com.apkupdater.glass

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.apkupdater.prefs.Prefs
import com.apkupdater.ui.activity.MainActivity
import com.apkupdater.ui.theme.AppTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = GlassTestApp::class, qualifiers = "de-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassTransparencyUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @After fun cleanup() { stopKoin() }

    @Test fun settingsTransparencyUpdatesImmediatelyAndSurvivesStoreReload() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithContentDescription("Glastransparenz").assertIsDisplayed()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.8f) }
        compose.onNodeWithText("Glastransparenz: 80 %").assertIsDisplayed()
        compose.runOnIdle {
            val koin = GlobalContext.get()
            assertEquals(.2f, koin.get<AppearanceStore>().state.value.opacity, .001f)
            assertEquals(.2f, AppearanceStore(koin.get<Prefs>()).state.value.opacity, .001f)
        }
        compose.onNodeWithText("Standard: 54 %").performClick()
        compose.onNodeWithText("Glastransparenz: 54 %").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(.46f, AppearanceStore(GlobalContext.get().get<Prefs>()).state.value.opacity, .001f)
        }
    }

    @Test fun transparentPanelRevealsBackgroundInBothThemesWithEffectsOffAndBlurOff() {
        val backdrop = 0xffe83080.toInt()
        for (dark in listOf(true, false)) for (quality in listOf(0, 2)) {
            fun render(opacity: Float): Int {
                compose.runOnUiThread { compose.activity.setContent {
                    CompositionLocalProvider(LocalAppearance provides Appearance(opacity = opacity, blur = false, quality = quality, reduceMotion = true)) {
                        AppTheme(dark) {
                            Box(Modifier.fillMaxSize().background(Color(backdrop)), contentAlignment = Alignment.Center) {
                                GlassPanel(Modifier.size(180.dp), effectLevel = quality) { Text("Lesbarer Text") }
                            }
                        }
                    }
                } }
                compose.waitForIdle()
                var pixel = 0
                compose.runOnUiThread {
                    val view = compose.activity.window.decorView
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    var result = -1
                    android.view.PixelCopy.request(compose.activity.window, bitmap, { result = it }, android.os.Handler(android.os.Looper.getMainLooper()))
                    assertEquals(android.view.PixelCopy.SUCCESS, result)
                    pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                    bitmap.recycle()
                }
                return pixel
            }
            val opaque = render(1f)
            val transparent = render(0f)
            fun distance(first: Int, second: Int) =
                kotlin.math.abs(android.graphics.Color.red(first) - android.graphics.Color.red(second)) +
                kotlin.math.abs(android.graphics.Color.green(first) - android.graphics.Color.green(second)) +
                kotlin.math.abs(android.graphics.Color.blue(first) - android.graphics.Color.blue(second))
            assertTrue("Background hidden at 100% transparency (dark=$dark quality=$quality)", distance(backdrop, transparent) < 12)
            assertTrue("Opacity control has no visible effect (dark=$dark quality=$quality)", distance(opaque, transparent) > 80)
            compose.onNodeWithText("Lesbarer Text").assertIsDisplayed()
        }
    }

    @Test fun sourcePickerHasNoOpaqueMaterialContainerBehindGlass() {
        val store = GlobalContext.get().get<AppearanceStore>()
        val update = com.apkupdater.data.ui.AppUpdate("Transparency fixture", "com.example.transparency", "2", "1", 2, 1,
            com.apkupdater.data.ui.ApkPureSource, link = com.apkupdater.data.ui.Link.Url("https://example.org/test.apk"))
        compose.runOnIdle { store.update { it.copy(opacity = 1f, quality = 0, blur = false,
            customBackground = true, background = 0xffe83080, backgroundAlpha = 1f) } }
        compose.runOnUiThread { compose.activity.setContent {
            val appearance by store.state.collectAsState()
            CompositionLocalProvider(LocalAppearance provides appearance) {
                AppTheme(true) { GlassScene { UpdateGroupCard(UpdateGroup(update.packageName, listOf(update)), null, {}, {}, {}, {}) } }
            }
        } }
        compose.onNodeWithText("Quelle wählen und prüfen").performClick()
        fun sample(): Color {
            val bounds = compose.onNodeWithTag("glass-menu-surface").fetchSemanticsNode().boundsInRoot
            var pixel = 0
            compose.runOnUiThread {
                val window = checkNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog().window)
                val view = window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                var result = -1
                android.view.PixelCopy.request(window, bitmap, { result = it }, android.os.Handler(android.os.Looper.getMainLooper()))
                assertEquals(android.view.PixelCopy.SUCCESS, result)
                pixel = bitmap.getPixel(bounds.center.x.toInt(), bounds.top.toInt() + 12)
                bitmap.recycle()
            }
            return Color(pixel)
        }
        val opaque = sample()
        compose.runOnIdle { store.update { it.copy(opacity = 0f) } }
        val transparent = sample()
        assertTrue("Source picker still hides the background behind an opaque host (opaque=$opaque transparent=$transparent)", opaque.alpha > .9f && transparent.alpha < .5f)
        compose.onNodeWithText("Auswahl herunterladen und prüfen").assertIsDisplayed()
    }
}
