package org.uniapk.upgrader.glass

import android.app.Activity
import android.content.Intent
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.net.Uri
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.uniapk.upgrader.diagnostics.AppDiagnostics
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.settings.*
import org.uniapk.upgrader.ui.activity.MainActivity
import org.uniapk.upgrader.ui.theme.AppTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=GlassTestApp::class,qualifiers="de-w363dp-h792dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RC5BackupUiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val p get()=GlobalContext.get().get<Prefs>()
 private lateinit var provider:BackupTestProvider
 private val uri=Uri.parse("content://org.example.backup/settings")
 @Before fun setup(){val app=compose.activity.applicationContext;provider=BackupTestProvider().apply{file=File(app.cacheDir,"settings-test.uniapkbackup");attachInfo(app,ProviderInfo().apply{authority="org.example.backup"})};org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("org.example.backup",provider);AppDiagnostics.configure(enabled=true,verbose=false)}
 @After fun cleanup(){stopKoin()}
 private fun content(){compose.runOnUiThread{compose.activity.setContent{val a by GlobalContext.get().get<AppearanceStore>().state.collectAsState();CompositionLocalProvider(LocalAppearance provides a){AppTheme(a.theme!=2){GlassScene{Column(Modifier.fillMaxSize().padding(16.dp)){BackupSettingsCard()}}}}}}}
 private fun pickerResult(action:String){compose.runOnUiThread{val started=checkNotNull(shadowOf(compose.activity).nextStartedActivityForResult);assertEquals(action,started.intent.action);shadowOf(compose.activity).receiveResult(started.intent,Activity.RESULT_OK,Intent().setData(uri))}}
 private fun awaitPreview(){try{compose.waitUntil(20000){compose.onAllNodesWithText("Einstellungen wiederherstellen?").fetchSemanticsNodes().isNotEmpty()}}catch(e:Throwable){val dump=compose.onRoot(useUnmergedTree=true).printToString();File("build/reports/restore-debug.txt").writeText(dump);throw AssertionError("Preview missing; UI=${dump.takeLast(7000)}",e)}}
 private fun shot(name:String){compose.waitForIdle();compose.runOnUiThread{val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();val window=if(dialog?.isShowing==true)checkNotNull(dialog.window) else compose.activity.window;val v=window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));val f=File("build/reports/glass-screenshots/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun backupIsReachableInsideSystemWithoutAThirdMenuLevel(){compose.onNodeWithContentDescription("Einstellungen").performClick();compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Erweitert / System"));compose.onNodeWithText("Erweitert / System").performClick();compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Backup wiederherstellen"));compose.onNodeWithText("Backup wiederherstellen").assertIsDisplayed();shot("50-backup-settings")}
 @Test fun creatingBackupRequiresMatchingPasswordAndWritesAnEncryptedFile(){content();compose.onNodeWithText("Einstellungen sichern").performClick();pickerResult(Intent.ACTION_CREATE_DOCUMENT);compose.onNodeWithText("Backup erstellen").assertIsNotEnabled();compose.onNodeWithText("Backup-Passwort").performTextInput("Test-password-2026");compose.onNodeWithText("Passwort wiederholen").performTextInput("Mismatch-value");compose.onNodeWithText("Backup erstellen").assertIsNotEnabled();compose.onNodeWithText("Passwort wiederholen").performTextReplacement("Test-password-2026");compose.onNodeWithText("Backup erstellen").performClick();compose.waitUntil(20000){provider.file.exists()&&provider.file.length()>56};compose.waitUntil(20000){compose.onAllNodesWithText("Verschlüsseltes Einstellungsbackup gespeichert.").fetchSemanticsNodes().isNotEmpty()};assertFalse(provider.file.readBytes().toString(Charsets.ISO_8859_1).contains("Test-password-2026"));shot("51-backup-created")}
 @Test fun restoreIsPreviewedBeforeItChangesAnySetting(){val doc=SettingsBackup.snapshot(compose.activity,p);doc.getAsJsonObject("settings").addProperty("compactMode",true);provider.file.writeBytes(BackupCodec.seal(doc.toString().toByteArray(),"Test-password-2026".toCharArray()));content();compose.onNodeWithText("Backup wiederherstellen").performClick();pickerResult(Intent.ACTION_OPEN_DOCUMENT);compose.onNodeWithText("Backup-Passwort").performTextInput("Test-password-2026");compose.onNodeWithText("Entsperren und prüfen").performClick();awaitPreview();assertFalse(p.compactMode.get());shot("52-backup-restore-preview");compose.onNodeWithText("Wiederherstellen").performClick();compose.waitUntil(20000){compose.onAllNodesWithText("Einstellungen wiederhergestellt.",substring=true).fetchSemanticsNodes().isNotEmpty()};compose.runOnIdle{assertTrue(p.compactMode.get());assertTrue(GlobalContext.get().get<AppearanceStore>().state.value.compact)}}
 @Test fun restoringDetailedDiagnosticsRequiresExplicitConsent(){val doc=SettingsBackup.snapshot(compose.activity,p);doc.getAsJsonObject("diagnostics").addProperty("verbose",true);provider.file.writeBytes(BackupCodec.seal(doc.toString().toByteArray(),"Test-password-2026".toCharArray()));content();compose.onNodeWithText("Backup wiederherstellen").performClick();pickerResult(Intent.ACTION_OPEN_DOCUMENT);compose.onNodeWithText("Backup-Passwort").performTextInput("Test-password-2026");compose.onNodeWithText("Entsperren und prüfen").performClick();compose.waitUntil(20000){compose.onAllNodesWithText("Einstellungen wiederherstellen?").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("Wiederherstellen").assertIsNotEnabled();compose.onAllNodes(isToggleable()).onLast().performClick();compose.onNodeWithText("Wiederherstellen").assertIsEnabled();shot("53-backup-detailed-consent");compose.onNodeWithText("Abbrechen").performClick();assertFalse(AppDiagnostics.state.value.verbose)}
 @Test fun wrongPasswordNeverReachesRestoreConfirmation(){provider.file.writeBytes(BackupCodec.seal(SettingsBackup.snapshot(compose.activity,p).toString().toByteArray(),"Test-password-2026".toCharArray()));val before=SettingsRegistry(p).capture();content();compose.onNodeWithText("Backup wiederherstellen").performClick();pickerResult(Intent.ACTION_OPEN_DOCUMENT);compose.onNodeWithText("Backup-Passwort").performTextInput("Wrong-password-2026");compose.onNodeWithText("Entsperren und prüfen").performClick();compose.waitUntil(20000){compose.onAllNodesWithText("Passwort falsch",substring=true).fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("Einstellungen wiederherstellen?").assertDoesNotExist();assertEquals(before,SettingsRegistry(p).capture())}
}
