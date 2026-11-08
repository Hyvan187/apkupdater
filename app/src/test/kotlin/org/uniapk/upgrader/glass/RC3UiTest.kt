package org.uniapk.upgrader.glass

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.ui.activity.MainActivity
import org.uniapk.upgrader.ui.theme.AppTheme
import org.uniapk.upgrader.viewmodel.UpdatesViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=GlassTestApp::class,qualifiers="de-w363dp-h792dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RC3UiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @After fun clean(){stopKoin()}
 private fun content(body:@Composable ()->Unit){compose.runOnUiThread{compose.activity.setContent{val a by GlobalContext.get().get<AppearanceStore>().state.collectAsState();CompositionLocalProvider(LocalAppearance provides a){AppTheme(a.theme!=2){GlassScene{body()}}}}}}
 private fun scroll(text:String){compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))}
 private fun shot(name:String){compose.waitForIdle();compose.runOnUiThread{val v=compose.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);var status=-1;android.view.PixelCopy.request(compose.activity.window,b,{status=it},android.os.Handler(android.os.Looper.getMainLooper()));assertEquals(android.view.PixelCopy.SUCCESS,status);val f=File("build/reports/glass-screenshots/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun fixture()=AppUpdate("RC3 fixture","org.example.fixture","2","1",2,1,GitHubSource,link=Link.Url("https://example.org/fixture.apk"))
 private fun viewModel(vararg u:AppUpdate):UpdatesViewModel{val vm=GlobalContext.get().get<UpdatesViewModel>();compose.runOnIdle{val field=vm.javaClass.getDeclaredField("state").apply{isAccessible=true};@Suppress("UNCHECKED_CAST") val state=field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<UpdatesUiState>;state.value=UpdatesUiState.Success(u.toList())};return vm}
 @Test fun updateStarTogglesRealFavoriteStateAndAccessibility(){val u=fixture();content{Column(Modifier.padding(16.dp)){UpdateGroupCard(UpdateGroup(u.packageName,listOf(u)),null,{},{},{},{})}};compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").assertIsOff().performClick();compose.onNodeWithContentDescription("Aus Favoriten entfernen").assertIsOn();compose.runOnIdle{assertTrue(u.packageName in GlobalContext.get().get<FavoriteStore>().state.value)};compose.onNodeWithContentDescription("Aus Favoriten entfernen").performClick();compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").assertIsOff()}
 @Test fun appCategoryShowsFavoritesWithoutHorizontalScrolling(){compose.onNodeWithText("Apps").performClick();compose.onNodeWithText("Favorisierte Apps").assertIsDisplayed().performClick();compose.onNodeWithText("Favorisierte Apps").assertIsSelected();shot("25-app-favorites")}
 @Test fun ignoredCategoryListsPackagesAndRestoresThem(){val store=GlobalContext.get().get<IgnoredAppStore>();compose.runOnIdle{store.set("org.example.ignored",true)};content{IgnoredAppsScreen{}};compose.onNodeWithText("org.example.ignored").assertExists();compose.onNodeWithText("Updates wieder zulassen").performClick();compose.onNodeWithText("Keine Apps ignoriert").assertIsDisplayed();compose.runOnIdle{assertTrue(store.state.value.isEmpty())}}
 @Test fun ignoringFromUpdateCardRemovesAllCandidates(){val u=fixture();val vm=viewModel(u,u.copy(source=ApkPureSource,artifactKey="another-source"));content{GlassUpdatesScreen(vm)};scroll("App dauerhaft ignorieren");compose.onNodeWithText("App dauerhaft ignorieren").performClick();compose.onNodeWithText("App ignorieren").performClick();compose.waitUntil(10000){GlobalContext.get().get<IgnoredAppStore>().state.value.contains(u.packageName)&&vm.state().value.updates().isEmpty()};scroll("Ignorierte Apps 1");compose.onNodeWithText("Ignorierte Apps 1").performClick();compose.onNodeWithText("Ignorierte Apps").assertIsDisplayed()}
 @Test fun preparedAppsExpandOnOverviewWithoutOpeningUpdates(){val u=fixture().copy(fileReady=true,verdict=Verdict(Compatibility.COMPATIBLE,emptyList()));val vm=viewModel(u);content{OverviewScreen(vm)};compose.onNodeWithText("Bereit zur Installation (1)").assertIsDisplayed();compose.onNodeWithText(u.name).assertIsDisplayed();compose.onNodeWithText("Installieren").assertIsDisplayed();shot("26-overview-ready-expanded")}
 @Test fun settingsDashboardMatchesCardHierarchy(){compose.onNodeWithContentDescription("Einstellungen").performClick();compose.onNodeWithText("Schnellzugriff").assertDoesNotExist();compose.onNodeWithText("Allgemein").assertIsDisplayed();shot("27-settings-cards");scroll("Erscheinungsbild");compose.onNodeWithText("Erscheinungsbild").performClick();compose.onNodeWithText("Light").assertIsDisplayed();compose.onNodeWithText("Dusk").assertIsDisplayed();compose.onNodeWithText("Dark").assertIsDisplayed();compose.onNodeWithText("OLED").assertIsDisplayed();shot("28-appearance-theme-cards")}
 @Test fun visualThemeAndCompactChoicesPersist(){content{AppearanceScreen{}};compose.onNodeWithText("Dusk").performClick();compose.runOnIdle{assertEquals(3,GlobalContext.get().get<AppearanceStore>().state.value.theme)};compose.onNodeWithText("OLED").performClick();compose.runOnIdle{assertTrue(GlobalContext.get().get<AppearanceStore>().state.value.oled)};scroll("Kompaktmodus");compose.onNode(hasText("Kompaktmodus") and hasAnySibling(isToggleable())).assertExists();val store=GlobalContext.get().get<AppearanceStore>();compose.runOnIdle{store.update{it.copy(compact=true)}};compose.runOnIdle{assertTrue(AppearanceStore(GlobalContext.get().get<Prefs>()).state.value.compact)}}
 @Test fun compactModeActuallyReducesCardHeight(){val store=GlobalContext.get().get<AppearanceStore>();content{Column(Modifier.padding(16.dp)){GlassPanel(Modifier.testTag("density-card")){Text("Compact fixture");Text("Second row")}}};val normal=compose.onNodeWithTag("density-card").fetchSemanticsNode().boundsInRoot.height;compose.runOnIdle{store.update{it.copy(compact=true)}};val compact=compose.onNodeWithTag("density-card").fetchSemanticsNode().boundsInRoot.height;assertTrue(compact<normal-8)}
 @Test fun allDockStylesAndSizesKeepAllActions(){val store=GlobalContext.get().get<AppearanceStore>();content{Box(Modifier.fillMaxSize()){DockNavigation("overview",null,Modifier.align(androidx.compose.ui.Alignment.BottomCenter),{})}};for(s in DockStyle.entries)for(z in DockSize.entries){compose.runOnIdle{store.update{it.copy(dockStyle=s.ordinal,dockSize=z.ordinal,reduceMotion=true)}};for(label in listOf("Start","Updates","Apps","Apps suchen"))compose.onNodeWithContentDescription(label).assertIsDisplayed();compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter{it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Selected)}.forEach{assertTrue(it.boundsInRoot.height>=48*compose.activity.resources.displayMetrics.density-1)}};shot("29-dock-shape-shift")}
 @Test fun automaticPerformanceIsASelectableOption(){content{Column{ComputeSettings()}};compose.onNodeWithText("Automatisch",substring=true).performClick();compose.onNodeWithText("1 Aufgabe").performClick();compose.runOnIdle{assertEquals(1,GlobalContext.get().get<Prefs>().cpuTasks.get())};compose.onNodeWithText("CPU-Aufgabenlimit: 1").performClick();compose.onNodeWithText("Automatisch").performClick();compose.runOnIdle{assertEquals(0,GlobalContext.get().get<Prefs>().cpuTasks.get())}}
 @Test fun fiveLauncherIconsCanBePreviewed(){content{LauncherIconSettings(GlobalContext.get().get(),LocalAppearance.current)};compose.onNodeWithText("Original").assertIsDisplayed();compose.onNodeWithText("Transparent").performScrollTo().assertIsDisplayed();shot("30-launcher-options")}
 @Test fun installerSettingsOfferAskEveryTime(){content{GlassSettingsScreen({}, {})};scroll("Updates & Versionen");compose.onNodeWithText("Updates & Versionen").performClick();scroll("Android PackageInstaller");compose.onNodeWithText("Android PackageInstaller").performClick();compose.onNodeWithText("Bei jeder Installation fragen").assertIsDisplayed().performClick();compose.runOnIdle{assertEquals(InstallerChoices.ASK,GlobalContext.get().get<Prefs>().installerPackage.get())}}
 @Test fun auroraAndFirefliesProduceDifferentVisibleFrames(){val store=GlobalContext.get().get<AppearanceStore>();compose.mainClock.autoAdvance=false;content{MotionBackground(LocalAppearance.current)};for(m in listOf(MotionMode.AURORA,MotionMode.FIREFLIES)){compose.runOnIdle{store.update{it.copy(motion=m.ordinal,density=.7f,reduceMotion=false)}};compose.mainClock.advanceTimeBy(1000);shot("31-effect-${m.name.lowercase()}")}}
}

