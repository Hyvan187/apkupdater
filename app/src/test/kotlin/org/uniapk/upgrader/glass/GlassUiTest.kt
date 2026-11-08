package org.uniapk.upgrader.glass

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.uniapk.upgrader.application.App
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.ui.activity.MainActivity
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

open class GlassTestApp : App() {
    // JVM UI tests have no Android Keystore. Device security is tested separately.
    override fun preferenceStorage() = com.kryptoprefs.preferences.KryptoBuilder.nocrypt(this, "ui-test-preferences")
    override fun onCreate() {
        super.onCreate()
        val p = GlobalContext.get().get<Prefs>()
        p.useGitHub.put(false); p.useGitLab.put(false); p.useFdroid.put(false); p.useIzzy.put(false)
        p.useApkMirror.put(false); p.useApkPure.put(false); p.useAptoide.put(false); p.usePlay.put(false)
        p.permissionSetupVersion.put(1); p.autoVerify.put(false); p.enableAlarm.put(false); p.theme.put(1)
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = GlassTestApp::class, qualifiers = "de-w412dp-h915dp-xhdpi")
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
        compose.onNodeWithText("Übersicht").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Nach Updates suchen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nach Updates suchen").assertIsDisplayed()
        screenshot("01-dashboard")
        compose.onNodeWithText("Updates").performClick()
        compose.onNodeWithText("Keine vorbereiteten APKs").assertIsDisplayed()
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
    @Config(qualifiers = "de-w320dp-h720dp-xhdpi")
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
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Design anpassen"))
        compose.onNodeWithText("Design anpassen").performClick()
        compose.onNodeWithText("Hell").assertIsDisplayed()
        compose.onNodeWithText("Hell").performClick()
        screenshot("08-appearance-light")
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Übersicht").assertIsDisplayed()
        screenshot("06-light-dashboard")
    }
    @Test
    fun documentsUiDowngradeCardCannotOfferDownloadOrInstallation() {
        val installed = InstalledPackage("com.google.android.documentsui", 374200020, false, "17", 37, true)
        val verdict = CompatibilityEngine.evaluate(DeviceProfile(37, setOf("arm64-v8a"), Region.IN), installed,
            Candidate(installed.packageName, 990090000, versionName = "14"))
        val update = org.uniapk.upgrader.data.ui.AppUpdate("DocumentsUI", installed.packageName, "14", "17", 990090000, 374200020,
            org.uniapk.upgrader.data.ui.ApkPureSource, verdict = verdict)
        compose.runOnUiThread {
            compose.activity.setContent {
                org.uniapk.upgrader.ui.theme.AppTheme(true) {
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
        var checked: org.uniapk.upgrader.data.ui.AppUpdate? = null
        val first = org.uniapk.upgrader.data.ui.AppUpdate("Adobe Scan", "com.adobe.scan", "26.10.29", "26.08.01", 261029, 260801, org.uniapk.upgrader.data.ui.ApkPureSource,
            link = org.uniapk.upgrader.data.ui.Link.Url("https://example.org/scan.apk"))
        val second = first.copy(source = org.uniapk.upgrader.data.ui.AptoideSource, artifactKey = "aptoide-scan")
        compose.runOnUiThread { compose.activity.setContent {
            org.uniapk.upgrader.ui.theme.AppTheme(true) { GlassScene { Column(Modifier.fillMaxSize().padding(20.dp)) {
                UpdateGroupCard(UpdateGroup(first.packageName, listOf(first,second)), null, { checked = it }, {}, {}, {})
            } } }
        } }
        compose.onAllNodesWithText("Adobe Scan").assertCountEquals(1)
        compose.onNodeWithText("Quelle wählen · APK vorbereiten").performClick()
        org.junit.Assert.assertNull(checked)
        compose.onNodeWithText("Aptoide").performClick()
        screenshot("09-source-picker")
        compose.onNodeWithText("Auswahl herunterladen und prüfen").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(org.uniapk.upgrader.data.ui.AptoideSource, checked?.source) }
    }
    @Test fun playPickerAndReportsNeverResolveOrDownloadFiles() {
        var resolutions=0
        var selected: org.uniapk.upgrader.data.ui.AppUpdate?=null
        val play=org.uniapk.upgrader.data.ui.AppUpdate("Play fixture","org.example.play","2","1",2,1,org.uniapk.upgrader.data.ui.PlaySource,
            link=org.uniapk.upgrader.data.ui.Link.Play { resolutions++;error("Reading metadata must not resolve downloads") })
        compose.runOnUiThread { compose.activity.setContent {
            org.uniapk.upgrader.ui.theme.AppTheme(true) { GlassScene { Column(Modifier.fillMaxSize().padding(20.dp)) {
                UpdateGroupCard(UpdateGroup(play.packageName,listOf(play)),null,{selected=it},{},{},{})
            } } }
        } }
        compose.onNodeWithContentDescription("Hilfe: Technische Risiken").performClick()
        compose.onNodeWithText("Verstanden").performClick()
        compose.onNodeWithText("Play fixture").performClick()
        compose.onNodeWithText("Technische Details").performClick()
        compose.onNodeWithText("Schließen").performClick()
        compose.onNodeWithContentDescription("Hilfe: Prüfverfahren").performClick()
        compose.onNodeWithText("Verstanden").performClick()
        compose.onNodeWithText("Play fixture").performClick()
        compose.onNodeWithText("Quelle wählen · APK vorbereiten").performClick()
        compose.onNodeWithText("Play").performClick()
        compose.onNodeWithText("Paketformat derzeit nicht prüfbar").assertDoesNotExist()
        compose.onNodeWithText("Auswahl herunterladen und prüfen").assertIsEnabled()
        compose.runOnIdle { org.junit.Assert.assertNull(selected);org.junit.Assert.assertEquals(0,resolutions) }
        compose.onNodeWithText("Auswahl herunterladen und prüfen").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(org.uniapk.upgrader.data.ui.PlaySource,selected?.source);org.junit.Assert.assertEquals(0,resolutions) }
    }

    @Test fun everyAnimationModeDrawsAcrossFullPortraitWindow() = assertAnimationsCoverWindow()
    @Test @Config(qualifiers="de-w915dp-h412dp-xhdpi")
    fun everyAnimationModeDrawsAcrossFullLandscapeWindow() = assertAnimationsCoverWindow()
    private fun assertAnimationsCoverWindow() {
        for(mode in MotionMode.entries.filter { it !in listOf(MotionMode.NONE,MotionMode.RANDOM) }) {
            compose.runOnUiThread { compose.activity.setContent {
                androidx.compose.runtime.CompositionLocalProvider(LocalAppearance provides Appearance(oled=true,motion=mode.ordinal,density=1f,reduceMotion=true,customAccent=true,accent=0xffffffff)) {
                    org.uniapk.upgrader.ui.theme.AppTheme(true) { GlassScene {} }
                }
            } }
            compose.waitForIdle()
            compose.runOnUiThread {
                val view=compose.activity.window.decorView
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                android.view.PixelCopy.request(compose.activity.window,bitmap,{},android.os.Handler(android.os.Looper.getMainLooper()))
                var lower=0;var right=0
                for(y in bitmap.height/2 until bitmap.height-40 step 2) for(x in 8 until bitmap.width-8 step 2) {
                    val p=bitmap.getPixel(x,y)
                    if(android.graphics.Color.red(p)>8) { lower++;if(x>bitmap.width/2)right++ }
                }
                bitmap.recycle()
                org.junit.Assert.assertTrue("${mode.name} misses lower half",lower>10)
                org.junit.Assert.assertTrue("${mode.name} misses lower-right quadrant",right>5)
            }
        }
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

    @Test fun singleAppearanceEntryAndRecommendedResetWork() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Design und Leistung"))
        compose.onAllNodesWithText("Design und Leistung").assertCountEquals(1)
        compose.onNodeWithText("Design anpassen").performClick()
        compose.onAllNodesWithText("Design und Leistung").assertCountEquals(1)
        val store=GlobalContext.get().get<AppearanceStore>()
        compose.runOnIdle { store.update{it.copy(quality=0,opacity=.9f,blur=false,accent=0xff123456,customAccent=true)} }
        compose.onNodeWithText("Empfohlene Glaswerte anwenden").performClick()
        compose.runOnIdle {org.junit.Assert.assertEquals(2,store.state.value.quality);org.junit.Assert.assertTrue(store.state.value.blur);org.junit.Assert.assertEquals(.46f,store.state.value.opacity);org.junit.Assert.assertTrue(store.state.value.customAccent)}
        screenshot("11-recommended-appearance")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Alle Darstellungswerte auf Standard zurücksetzen"))
        compose.onNodeWithText("Alle Darstellungswerte auf Standard zurücksetzen").performClick()
        compose.runOnIdle {org.junit.Assert.assertEquals(Appearance(),store.state.value)}
    }
    @Test @Config(qualifiers="de-w320dp-h720dp-xhdpi") fun googleScopeFitsCompactScreen() {
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithText("Google-System-Apps").assertIsDisplayed().performClick()
        compose.runOnIdle {org.junit.Assert.assertEquals(AppScope.GOOGLE_SYSTEM,GlobalContext.get().get<AppSelection>().scope.value)}
        screenshot("12-google-system-compact")
    }

    @Test fun cachedBetaDisappearsImmediatelyWhenFilterChanges() {
        val koin=GlobalContext.get()
        val filters=koin.get<ReleaseFilterStore>()
        val vm=koin.get<org.uniapk.upgrader.viewmodel.UpdatesViewModel>()
        val beta=org.uniapk.upgrader.data.ui.AppUpdate("Beta Fixture","com.example.beta","2.0-beta1","1.0",2,1,org.uniapk.upgrader.data.ui.ApkPureSource)
        compose.runOnUiThread {
            filters.change(alpha=false,beta=false,preview=false)
            val field=vm.javaClass.getDeclaredField("state").apply{isAccessible=true}
            @Suppress("UNCHECKED_CAST")
            val state=field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<org.uniapk.upgrader.data.ui.UpdatesUiState>
            state.value=org.uniapk.upgrader.data.ui.UpdatesUiState.Success(listOf(beta))
            compose.activity.setContent { org.uniapk.upgrader.ui.theme.AppTheme(true) { GlassScene { GlassUpdatesScreen(vm) } } }
        }
        compose.onNodeWithText("Beta Fixture").assertIsDisplayed()
        compose.runOnIdle { filters.change(beta=true) }
        compose.onNodeWithText("Beta Fixture").assertDoesNotExist()
    }

    @Test fun helpExplainsBlockedWithoutInstallingAnything() {
        compose.onNodeWithText("Updates").performClick()
        compose.onNodeWithContentDescription("Hilfe: Was bedeuten die Bereiche?").performClick()
        compose.onNodeWithText(HelpText.BLOCKED,substring=true).assertIsDisplayed()
        compose.onNodeWithText(HelpText.CHECK,substring=true).assertIsDisplayed()
        compose.onNodeWithText("Verstanden").performClick()
    }
    @Test fun settingsKeepHelpForTechnicalOptionsOnly() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithContentDescription("Hilfe: Erscheinungsbild").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hilfe: Sprache").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hilfe: App-Auswahl").performClick()
        compose.onNodeWithText("Verstanden").assertIsDisplayed().performClick()
        screenshot("14-selective-info-buttons")
    }
    @Test @Config(qualifiers = "de-w915dp-h412dp-xhdpi") fun landscapeKeepsNavigationAndSettingsUsable() {
        compose.onNodeWithText("Start").assertIsDisplayed()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Gerät, ROM und Testprofil"))
        compose.onNodeWithText("Gerät, ROM und Testprofil").assertIsDisplayed()
        screenshot("15-landscape-settings")
    }
    @Test fun appearanceOffersAllAnimationModesAndOled() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Design anpassen").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("OLED-Optimierung"))
        compose.onNodeWithText("OLED-Optimierung").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Hintergrundanimation"))
        compose.onNodeWithText("Keine").performClick()
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Lavalampe"))
        compose.onNodeWithText("Lavalampe").assertIsDisplayed().performClick()
        compose.onNodeWithText("Lavalampe").assertIsDisplayed().performClick()
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Konfetti"))
        compose.onNodeWithText("Konfetti").assertIsDisplayed().performClick()
        compose.runOnIdle{org.junit.Assert.assertEquals(MotionMode.CONFETTI.ordinal,org.koin.core.context.GlobalContext.get().get<Prefs>().backgroundMotion.get())}
        screenshot("13-animation-options")
    }
    @Test fun colorWheelHasCodeAndOpacityControl() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Design anpassen").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Button- und Akzentfarbe"))
        compose.onNodeWithText("Button- und Akzentfarbe").performClick()
        compose.onNodeWithText("HEX-Farbe").assertIsDisplayed()
        compose.onNodeWithText("Deckkraft: 100 %").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").performClick()
    }
    @Test fun donationAndLegalAreSeparateFromProductPresentation() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("☕ Like my project? Buy me a coffee :)"))
        compose.onNodeWithText("☕ Like my project? Buy me a coffee :)").assertIsDisplayed()
        compose.onNodeWithText("APKUpdater",substring=true).assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Lizenzen und Rechtliches"))
        compose.onNodeWithText("Lizenzen und Rechtliches").performClick()
        compose.onNodeWithText("Schließen").performClick()
    }

    @Test fun englishCoreControlsRenderWithExplicitAppLocale() {
        compose.runOnUiThread {compose.activity.setContent { androidx.compose.runtime.CompositionLocalProvider(LocalDisplayLanguage provides "en") { org.uniapk.upgrader.ui.theme.AppTheme(true) {GlassScene { androidx.compose.foundation.layout.Column {Text("Einstellungen");Text("Alle verfügbaren APKs prüfen");HelpButton("Check",HelpText.CHECK)} } } } }}
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Check all available APKs").assertIsDisplayed()
    }

}
