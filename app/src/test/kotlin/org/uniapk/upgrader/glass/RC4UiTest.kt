package org.uniapk.upgrader.glass

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.uniapk.upgrader.R
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.ui.activity.MainActivity
import org.uniapk.upgrader.ui.theme.AppTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=GlassTestApp::class,qualifiers="de-w363dp-h792dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RC4UiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @After fun close(){stopKoin()}
 private fun content(body:@Composable ()->Unit){compose.runOnUiThread{compose.activity.setContent{val a by GlobalContext.get().get<AppearanceStore>().state.collectAsState();CompositionLocalProvider(LocalAppearance provides a){AppTheme(a.theme!=2){GlassScene{body()}}}}}}
 private fun scroll(text:String)=compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
 private fun screenshot(name:String){compose.waitForIdle();compose.runOnUiThread{val v=compose.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);var status=-1;android.view.PixelCopy.request(compose.activity.window,b,{status=it},android.os.Handler(android.os.Looper.getMainLooper()));assertEquals(android.view.PixelCopy.SUCCESS,status);val f=File("build/reports/glass-screenshots/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun fixture(code:Long=200)=AppUpdate("RC4 fixture","org.example.rc4","2.0","1.0",code,100,GitHubSource,link=Link.Url("https://example.org/app.apk"))
 @Test fun settingsHaveFourGroupsAndQuickAccessIsInsideGeneral(){compose.onNodeWithContentDescription("Einstellungen").performClick();compose.onNodeWithText("Schnellzugriff").assertDoesNotExist();for(c in SettingsCategory.entries){scroll(c.title);compose.onNodeWithText(c.title).assertIsDisplayed()};screenshot("40-settings-glass");scroll("Allgemein");compose.onNodeWithText("Allgemein").performClick();scroll("Schnellzugriff");compose.onNodeWithText("Schnellzugriff").assertIsDisplayed();scroll("Berechtigungen");compose.onNodeWithText("Installationsfreigabe verwalten").assertIsDisplayed();compose.onNodeWithContentDescription("Zurück").performClick();compose.onNodeWithText("Einstellungen").assertIsDisplayed()}
 @Test fun startViewAndConfigurableShortcutsPersist(){content{GlassSettingsScreen({}, {})};compose.onNodeWithText("Allgemein").performClick();scroll("Startansicht");compose.onNodeWithText("Startübersicht").performClick();compose.onNodeWithText("Updates").performClick();compose.runOnIdle{assertEquals("updates",GlobalContext.get().get<Prefs>().startView.get())};scroll("Schnellzugriff");compose.onAllNodes(isToggleable()).onFirst().performClick();compose.runOnIdle{assertFalse("stable" in GlobalContext.get().get<Prefs>().quickAccess.get())};screenshot("41-general-shortcuts")}
 @Test fun sourceDialogCanSkipWithoutResolvingOrDownloadingAnything(){val u=fixture();var resolved=0;var checked=0;var skipped:AppUpdate?=null;val p=u.copy(link=Link.Play{resolved++;error("No file resolution when skipping")},source=PlaySource)
  content{Column(Modifier.padding(16.dp)){UpdateGroupCard(UpdateGroup(p.packageName,listOf(p)),null,{checked++},{},{skipped=it},{},ignoreApp={})}}
  compose.onNodeWithText("Quelle wählen · APK vorbereiten").performClick();compose.onAllNodesWithText("Diese Version überspringen").onLast().assertIsDisplayed().performClick();compose.runOnIdle{assertEquals(p,skipped);assertEquals(0,checked);assertEquals(0,resolved)}
 }
 @Test fun skippedVersionsCanBeRestoredFromUpdateSettings(){val p=GlobalContext.get().get<Prefs>();SkippedVersions.skip(fixture(),p);content{GlassSettingsScreen({}, {})};scroll("Updates & Versionen");compose.onNodeWithText("Updates & Versionen").performClick();scroll("Ausgeblendete Versionen (1)");compose.onNodeWithText("Ausgeblendete Versionen (1)").performClick();compose.onNodeWithText("Build 200",substring=true).assertIsDisplayed();compose.onNodeWithText("Wieder einblenden").performClick();compose.runOnIdle{assertTrue(SkippedVersions.allowed(fixture(),p))}}
 @Test fun onlyOneColorEditorControlsTheUiAndPersistsItsSeed(){content{AppearanceScreen{}};scroll("Basisfarbe anpassen");compose.onNodeWithText("Button- und Akzentfarbe").assertDoesNotExist();compose.onNodeWithText("Hintergrundfarbe").assertDoesNotExist();compose.onNodeWithText("Basisfarbe anpassen").performClick();compose.onNodeWithText("HEX-Farbe").performTextReplacement("3399CC");compose.onNodeWithText("Übernehmen").performClick();compose.runOnIdle{val a=AppearanceStore(GlobalContext.get().get<Prefs>()).state.value;assertTrue(a.customPalette);assertEquals(0xff3399ccL,a.paletteSeed)};screenshot("42-base-palette")}
 @Test fun allFourThemesRenderTheSameGlassySettingsStructure(){val store=GlobalContext.get().get<AppearanceStore>();content{GlassSettingsScreen({}, {})};for((name,theme,oled) in listOf(Triple("light",2,false),Triple("dusk",3,false),Triple("dark",1,false),Triple("oled",1,true))){compose.runOnIdle{store.update{it.copy(theme=theme,oled=oled,reduceMotion=true)}};compose.onNodeWithText("Allgemein").assertIsDisplayed();screenshot("43-settings-$name")}}
 @Test fun allFiveDetailedIconsFitThreeLauncherMasks(){val c=compose.activity;val w=5*180;val h=3*210;val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val canvas=Canvas(b);canvas.drawColor(0xff30313d.toInt());val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE;textSize=18f}
  val rasters=listOf(R.drawable.uniapk_mark,R.drawable.uniapk_mark_black,R.drawable.uniapk_mark_white,R.drawable.uniapk_mark_milk,R.drawable.uniapk_mark_transparent)
  for(i in 0..4){val raw=BitmapFactory.decodeResource(c.resources,rasters[i]);assertTrue(raw.width>=1024);assertEquals(0,android.graphics.Color.alpha(raw.getPixel(0,0)));raw.recycle()
   for(mask in 0..2){canvas.save();canvas.translate(i*180f,mask*210f);val shape=Path().apply{when(mask){0->addCircle(84f,84f,82f,Path.Direction.CW);1->addRoundRect(RectF(2f,2f,166f,166f),36f,36f,Path.Direction.CW);else->addRect(2f,2f,166f,166f,Path.Direction.CW)}};canvas.save();canvas.clipPath(shape);val icon=c.getDrawable(LauncherIcons.resources[i]) as AdaptiveIconDrawable;icon.setBounds(2,2,166,166);icon.background.draw(canvas);icon.foreground.draw(canvas);canvas.restore();canvas.drawText(LauncherIcons.labels[i],6f,190f,paint);canvas.restore()}
  };val file=File("build/reports/glass-screenshots/44-launcher-five-materials-three-masks.png");file.parentFile!!.mkdirs();file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
 }
 @Test fun knownAndUnknownWaveProgressRemainAccessibleWithReducedMotion(){val store=GlobalContext.get().get<AppearanceStore>();compose.runOnIdle{store.update{it.copy(reduceMotion=true)}};content{Column(Modifier.padding(24.dp)){GlassPanel{Text("Bekannter Fortschritt");UniProgress(.65f,label="65 Prozent",style=2);Text("Unbekannte Dauer");UniProgress(label="Warten auf Quelle",style=2)}}};compose.onNodeWithContentDescription("65 Prozent").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(.65f,0f..1f));compose.onNodeWithContentDescription("Warten auf Quelle").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate);screenshot("45-wave-progress-glass")}
}
