package org.uniapk.upgrader.glass

import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.uniapk.upgrader.R
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.ui.activity.MainActivity
import org.uniapk.upgrader.ui.theme.AppTheme
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
import java.io.File

class PermissionFirstRunTestApp:GlassTestApp(){override fun onCreate(){super.onCreate();GlobalContext.get().get<Prefs>().permissionSetupVersion.put(0)}}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=GlassTestApp::class,qualifiers="de-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RC2UsabilityUiTest{
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @After fun cleanup(){stopKoin()}
    private fun content(body:@androidx.compose.runtime.Composable ()->Unit){compose.runOnUiThread{compose.activity.setContent{AppTheme(true){GlassScene{Column(Modifier.fillMaxSize().padding(20.dp)){body()}}}}}}
    private fun screenshot(name:String){compose.waitForIdle();compose.runOnUiThread{val v=compose.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);android.view.PixelCopy.request(compose.activity.window,b,{},android.os.Handler(android.os.Looper.getMainLooper()));val f=File("build/reports/glass-screenshots/$name.png");requireNotNull(f.parentFile).mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
    @Test @Config(application=PermissionFirstRunTestApp::class) fun firstLaunchChecklistCanContinueWithoutForcedPermissions(){compose.onNodeWithText("UNIAPK EINRICHTEN").assertIsDisplayed();compose.onNodeWithText("Apps installieren").assertIsDisplayed();compose.onNodeWithText("Benachrichtigungen").assertIsDisplayed();screenshot("20-permissions-first-run");compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Weiter zur App"));compose.onNodeWithText("Weiter zur App").performClick();compose.onNodeWithText("Übersicht").assertIsDisplayed();compose.runOnIdle{assertEquals(1,GlobalContext.get().get<Prefs>().permissionSetupVersion.get())}}
    @Test fun permissionsCanBeReopenedFromSettings(){compose.onNodeWithContentDescription("Einstellungen").performClick();compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Berechtigungen"));compose.onNodeWithText("Berechtigungen").performClick();compose.onNodeWithText("ZUGRIFFE VERWALTEN").assertIsDisplayed();compose.onNodeWithContentDescription("Zurück").performClick();compose.onNodeWithText("Einstellungen").assertIsDisplayed()}
    @Test fun performanceAndProgressAreUnderAppearance(){compose.onNodeWithContentDescription("Einstellungen").performClick();compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Design und Leistung"));compose.onNodeWithText("Design und Leistung").performClick();compose.onNodeWithText("Design und Leistung").assertIsDisplayed();compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Leistungsbudget"));compose.onNodeWithText("Leistungsbudget").assertIsDisplayed();compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Ladeanzeige"));compose.onNodeWithText("Linie").performClick();compose.onNodeWithText("Ring").performClick();compose.runOnIdle{assertEquals(ProgressStyle.RING.ordinal,GlobalContext.get().get<AppearanceStore>().state.value.progressStyle)};screenshot("21-appearance-performance-progress")}
    @Test fun matrixAndCustomColorsCanBeSelected(){compose.onNodeWithContentDescription("Einstellungen").performClick();compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Design und Leistung"));compose.onNodeWithText("Design und Leistung").performClick();compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Hintergrundanimation"));compose.onNodeWithText("Keine").performClick();compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Matrix"));compose.onNodeWithText("Matrix").performClick();compose.runOnIdle{assertEquals(MotionMode.MATRIX.ordinal,GlobalContext.get().get<AppearanceStore>().state.value.motion)};compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Animationsfarben"));compose.onNodeWithText("Passend zum Effekt").performClick();compose.onNodeWithText("Zwei Farben").performClick();compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Animationsfarbe"));compose.onNodeWithText("Animationsfarbe").performClick();compose.onNodeWithText("HEX-Farbe").assertIsDisplayed();compose.onNodeWithText("Abbrechen").performClick();screenshot("22-animation-colors")}
    @Test fun deletionRequiresConfirmationAndNeverInstalls(){val u=AppUpdate("Prepared fixture","org.example.fixture","2","1",2,1,GitHubSource,fileReady=true,verifiedAt=1,sha256="fixture",verdict=Verdict(Compatibility.COMPATIBLE,emptyList()));var removed=false;content{UpdateGroupCard(UpdateGroup(u.packageName,listOf(u)),null,{}, {error("No installation")},{},{},discard={removed=true})};compose.onNodeWithText("Vorbereitete APKs löschen").performClick();assertFalse(removed);compose.onNodeWithText("APKs löschen").performClick();compose.runOnIdle{assertTrue(removed)}}
    @Test fun criticalRiskIsShownWithoutApprovingUnknownBytes(){val u=AppUpdate("Core fixture","com.google.android.gms","2","1",2,1,GitHubSource,system=true);content{RiskSummary(u)};compose.onNodeWithText("Hohes Risiko").assertIsDisplayed();compose.onNodeWithText("Google-System-App").assertIsDisplayed();compose.onNodeWithText("APK noch nicht vollständig geprüft.",substring=true).assertIsDisplayed()}
    @Test fun everyProgressStyleExposesActualOrIndeterminateProgress(){content{CompositionLocalProvider(LocalAppearance provides Appearance(reduceMotion=true)){for(s in ProgressStyle.entries){Text(s.label);UniProgress(.4f,label="Fortschritt ${s.name}",style=s.ordinal);UniProgress(label="Unbestimmt ${s.name}",style=s.ordinal)}}};for(s in ProgressStyle.entries){compose.onNodeWithContentDescription("Fortschritt ${s.name}").assertIsDisplayed().assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(.4f,0f..1f));compose.onNodeWithContentDescription("Unbestimmt ${s.name}").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)};screenshot("23-progress-styles")}
    @Test fun freestandingForegroundFitsMultipleLauncherMasks(){
        val c=compose.activity;val foreground=BitmapFactory.decodeResource(c.resources,R.drawable.uniapk_mark);assertEquals(0,Color.alpha(foreground.getPixel(0,0)));assertEquals(0,Color.alpha(foreground.getPixel(foreground.width-1,foreground.height-1)))
        val result=Bitmap.createBitmap(720,280,Bitmap.Config.ARGB_8888);val canvas=Canvas(result);canvas.drawColor(Color.rgb(24,24,32));val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=22f}
        repeat(3){i->canvas.save();canvas.translate(12f+i*240f,0f);val path=Path().apply{when(i){0->addCircle(108f,108f,108f,Path.Direction.CW);1->addRoundRect(RectF(0f,0f,216f,216f),50f,50f,Path.Direction.CW);else->addRect(0f,0f,216f,216f,Path.Direction.CW)}};canvas.save();canvas.clipPath(path);val icon=c.getDrawable(R.mipmap.ic_store) as AdaptiveIconDrawable;icon.setBounds(0,0,216,216);icon.background.draw(canvas);icon.foreground.draw(canvas);canvas.restore();canvas.drawText(listOf("Rund","Abgerundet","Quadratisch")[i],12f,254f,paint);canvas.restore()}
        val file=File("build/reports/glass-screenshots/24-launcher-masks.png");requireNotNull(file.parentFile).mkdirs();file.outputStream().use{result.compress(Bitmap.CompressFormat.PNG,100,it)}
        repeat(3){i->var visible=0;for(y in 20..195)for(x in 20..195){val p=result.getPixel(12+i*240+x,y);if(Color.green(p)>70||Color.blue(p)>100)visible++};assertTrue("Logo too small in mask $i",visible>3500)};foreground.recycle();result.recycle()
    }
}

