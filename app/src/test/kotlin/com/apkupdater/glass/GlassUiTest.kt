package com.apkupdater.glass

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.apkupdater.application.App
import com.apkupdater.prefs.Prefs
import com.apkupdater.ui.activity.MainActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

class GlassTestApp : App() {
    override fun onCreate() {
        super.onCreate()
        val p = GlobalContext.get().get<Prefs>()
        p.useGitHub.put(false); p.useGitLab.put(false); p.useFdroid.put(false); p.useIzzy.put(false)
        p.useApkMirror.put(false); p.useApkPure.put(false); p.useAptoide.put(false); p.usePlay.put(false)
        p.autoVerify.put(false); p.enableAlarm.put(false); p.theme.put(1)
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = GlassTestApp::class, qualifiers = "w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @After fun cleanup() { stopKoin() }
    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            var result = -1
            android.view.PixelCopy.request(compose.activity.window, bitmap, { result = it }, android.os.Handler(android.os.Looper.getMainLooper()))
            org.junit.Assert.assertEquals(android.view.PixelCopy.SUCCESS, result)
        }
        val dir = File("build/reports/glass-screenshots").apply { mkdirs() }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val image = java.awt.image.BufferedImage(bitmap.width, bitmap.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, bitmap.width, bitmap.height, pixels, 0, bitmap.width)
        javax.imageio.ImageIO.write(image, "png", File(dir, "$name.png"))
    }
    @Test fun android17DashboardNavigationAndSettingsRender() {
        compose.onNodeWithText("Alles im Blick.").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Updates suchen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Updates suchen").assertIsDisplayed()
        screenshot("01-dashboard")
        compose.onNodeWithText("Updates").performClick()
        compose.onNodeWithText("Keine freigegebenen Updates").assertIsDisplayed()
        screenshot("02-updates")
        compose.onNodeWithText("Blockiert 0").performClick()
        compose.onNodeWithText("Keine blockierten Varianten").assertIsDisplayed()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Einstellungen").assertIsDisplayed()
        compose.onNodeWithText("Updateprüfung").assertIsDisplayed()
        screenshot("03-settings")
        compose.onNodeWithText("ROM: IN").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithContentDescription("Apps suchen").performClick()
        compose.onNodeWithText("Apps entdecken").assertIsDisplayed()
        screenshot("04-search")
    }
    @Test
    @Config(qualifiers = "w320dp-h720dp-xhdpi")
    fun compactNavigationKeepsAllMainActionsVisible() {
        compose.onNodeWithText("Start").assertIsDisplayed()
        compose.onNodeWithText("Updates").assertIsDisplayed()
        compose.onNodeWithText("Apps").assertIsDisplayed()
        compose.onNodeWithContentDescription("Apps suchen").assertIsDisplayed()
        screenshot("05-compact-dashboard")
    }
    @Test
    fun lightThemeRendersReadableDashboard() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Erscheinungsbild öffnen"))
        compose.onNodeWithText("Erscheinungsbild öffnen").performClick()
        compose.onNodeWithText("Hell").assertIsDisplayed()
        compose.onNodeWithText("Hell").performClick()
        screenshot("08-appearance-light")
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Alles im Blick.").assertIsDisplayed()
        screenshot("06-light-dashboard")
    }
    @Test
    fun documentsUiDowngradeCardCannotOfferDownloadOrInstallation() {
        val installed = InstalledPackage("com.google.android.documentsui", 374200020, false, "17", 37, true)
        val verdict = CompatibilityEngine.evaluate(DeviceProfile(37, setOf("arm64-v8a"), Region.IN), installed,
            Candidate(installed.packageName, 990090000, versionName = "14"))
        val update = com.apkupdater.data.ui.AppUpdate("DocumentsUI", installed.packageName, "14", "17", 990090000, 374200020,
            com.apkupdater.data.ui.ApkPureSource, verdict = verdict)
        compose.runOnUiThread {
            compose.activity.setContent {
                com.apkupdater.ui.theme.AppTheme(true) {
                    GlassScene { Column(Modifier.fillMaxSize().padding(20.dp)) {
                        Text("DocumentsUI · Regressionstest", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(20.dp))
                        UpdateCard(update, verify = { error("Blocked APK must not download") }, install = { error("Blocked APK must not install") }, ignore = {}, source = {})
                    } }
                }
            }
        }
        compose.onNodeWithText("Abgelehnt").assertIsDisplayed()
        compose.onNodeWithText("APK prüfen").assertDoesNotExist()
        compose.onNodeWithText("Geprüfte APK installieren").assertDoesNotExist()
        screenshot("07-documentsui-blocked")
    }

    @Test fun groupedCardSelectsProviderBeforeManualDownload() {
        var checked: com.apkupdater.data.ui.AppUpdate? = null
        val first = com.apkupdater.data.ui.AppUpdate("Adobe Scan", "com.adobe.scan", "26.10.29", "26.08.01", 261029, 260801, com.apkupdater.data.ui.ApkPureSource,
            link = com.apkupdater.data.ui.Link.Url("https://example.org/scan.apk"))
        val second = first.copy(source = com.apkupdater.data.ui.AptoideSource, artifactKey = "aptoide-scan")
        compose.runOnUiThread { compose.activity.setContent {
            com.apkupdater.ui.theme.AppTheme(true) { GlassScene { Column(Modifier.fillMaxSize().padding(20.dp)) {
                UpdateGroupCard(UpdateGroup(first.packageName, listOf(first,second)), null, { checked = it }, {}, {}, {})
            } } }
        } }
        compose.onAllNodesWithText("Adobe Scan").assertCountEquals(1)
        compose.onNodeWithText("Quelle wählen und prüfen").performClick()
        org.junit.Assert.assertNull(checked)
        compose.onNodeWithText("Aptoide").performClick()
        screenshot("09-source-picker")
        compose.onNodeWithText("Auswahl herunterladen und prüfen").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(com.apkupdater.data.ui.AptoideSource, checked?.source) }
    }
    @Test fun appearanceColorPersistsAndCategorySelectionIsShared() {
        val koin = GlobalContext.get()
        val store = koin.get<AppearanceStore>()
        compose.runOnIdle { store.update { it.copy(accent = 0xff55bbff, background = 0xff121020, customAccent = true, customBackground = true) } }
        compose.runOnIdle {
            val loaded = AppearanceStore(koin.get<Prefs>()).state.value
            org.junit.Assert.assertEquals(0xff55bbff, loaded.accent)
            org.junit.Assert.assertEquals(0xff121020, loaded.background)
        }
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Allgemeine").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(AppScope.GENERAL, koin.get<AppSelection>().scope.value) }
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithText("Allgemeine").assertIsSelected()
        screenshot("10-custom-colors-categories")
    }

}
