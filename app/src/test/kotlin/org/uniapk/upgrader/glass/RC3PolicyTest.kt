package org.uniapk.upgrader.glass

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo
import com.kryptoprefs.preferences.KryptoBuilder
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.data.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=Application::class)
class RC3PolicyTest {
 private val context get()=RuntimeEnvironment.getApplication()
 private fun prefs()=Prefs(KryptoBuilder.nocrypt(context,"rc3-${System.nanoTime()}"),false)
 private fun report()=ApkAnalyzer.Report(Candidate("org.example",2,versionName="2"),Verdict(Compatibility.COMPATIBLE,emptyList()),"fixture-hash",emptySet(),37,null)
 private fun zip(vararg names:String)=File(context.cacheDir,"format-${System.nanoTime()}.zip").also{file->ZipOutputStream(file.outputStream()).use{z->names.forEach{z.putNextEntry(ZipEntry(it));z.write(byteArrayOf(1));z.closeEntry()}}}
 @Test fun favoritesToggleAndPersistForBothViews(){val p=prefs();val s=FavoriteStore(p);s.toggle("org.example");assertEquals(setOf("org.example"),s.state.value);assertEquals(s.state.value,FavoriteStore(p).state.value);s.toggle("org.example");assertTrue(s.state.value.isEmpty())}
 @Test fun ignoringIsPackageWideAndReversible(){val p=prefs();val s=IgnoredAppStore(p);assertTrue(AppExclusions.allowed("org.example",p));s.set("org.example",true);assertFalse(AppExclusions.allowed("org.example",p));assertEquals(setOf("org.example"),IgnoredAppStore(p).state.value);s.set("org.example",false);assertTrue(AppExclusions.allowed("org.example",p));assertTrue(s.state.value.isEmpty())}
 @Test fun ignoringOnePackageLeavesOthersEligible(){val p=prefs();IgnoredAppStore(p).set("org.example",true);assertTrue(AppExclusions.allowed("org.other",p))}
 @Test fun appearanceChangesSurviveReload(){val p=prefs();val a=AppearanceStore(p);a.update{it.copy(compact=true,theme=3,oled=false,dockStyle=4,dockSize=0,iconStyle=3,palette=2)};assertEquals(a.state.value,AppearanceStore(p).state.value)}
 @Test fun fourThemesRemainDistinctAndExistingThemeIndicesKeepMeaning(){val p=prefs();val a=AppearanceStore(p);for(t in listOf(0,1,2,3)){a.update{it.copy(theme=t)};assertEquals(t,AppearanceStore(p).state.value.theme)};a.update{it.copy(theme=1,oled=true)};assertTrue(AppearanceStore(p).state.value.oled)}
 @Test fun automaticCpuDoesNotExceedAvailableProcessors(){for(n in 1..16)assertTrue(ComputeBudget.automatic(n,false,false,0) in 1..minOf(4,n))}
 @Test fun automaticCpuReducesForPowerSaving(){assertEquals(1,ComputeBudget.automatic(8,false,true,0))}
 @Test fun automaticCpuReducesForHeatAndLowRam(){assertEquals(1,ComputeBudget.automatic(8,false,false,4));assertEquals(2,ComputeBudget.automatic(8,false,false,2));assertEquals(2,ComputeBudget.automatic(8,true,false,0))}
 @Test fun autoCpuIsANewInstallDefaultWithoutChangingManualChoices(){val p=prefs();assertEquals(0,p.cpuTasks.get());p.cpuTasks.put(2);p.migrateDefaults();assertEquals(2,p.cpuTasks.get());assertEquals(2,ComputeBudget.state.value)}
 @Test fun firefliesAreAppendedAndRespectDensity(){assertEquals(17,MotionMode.FIREFLIES.ordinal);assertEquals(16,MotionMode.MATRIX.ordinal);assertEquals(0,MotionPolicy.count(MotionMode.FIREFLIES,0f));assertTrue(MotionPolicy.count(MotionMode.FIREFLIES,1f)>MotionPolicy.count(MotionMode.FIREFLIES,.1f))}
 @Test fun archiveFormatUsesContentInsteadOfMisleadingFilename(){assertEquals(InstallFormat.APK,InstallerChoices.format(zip("AndroidManifest.xml","classes.dex")));assertEquals(InstallFormat.SET,InstallerChoices.format(zip("base.apk","config.en.apk")))}
 @Test fun singleApkArchiveUsesApkCapableInstallerChoices(){assertEquals(InstallFormat.APK,InstallerChoices.payloadFormat(zip("base.apk")));assertEquals(InstallFormat.SET,InstallerChoices.payloadFormat(zip("base.apk","config.en.apk")))}
 @Test fun bundleIntentIncludesReadGrantAndClipData(){val uri=android.net.Uri.parse("content://test/package.apks");val i=InstallerChoices.intent(uri,"application/x-apks");assertEquals(uri,i.data);assertEquals(uri,i.clipData!!.getItemAt(0).uri);assertTrue(i.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)}
 @Test fun unsupportedInstallerDoesNotLaunchOrCopy(){try{InstallerChoices.handoff(context,zip("base.apk"),"org.example","missing.installer");fail()}catch(_:IllegalStateException){};assertNull(shadowOf(context).nextStartedActivity)}
 @Test fun allFiveLauncherOptionsEnableExactlyOneAlias(){for(i in LauncherIcons.names.indices){LauncherIcons.select(context,i);LauncherIcons.names.forEachIndexed{j,n->assertEquals(if(i==j)PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,context.packageManager.getComponentEnabledSetting(ComponentName(context,"${context.packageName}.Launcher$n")))}}}
 @Test fun readyMetadataRestoresActualPreparedFileWithoutNetwork(){val d=File(context.cacheDir,"downloads").apply{mkdirs()};val f=File(d,"resume.apk").apply{writeText("fixture")};val u=AppUpdate("Fixture","org.example","2","1",2,1,GitHubSource,link=Link.Url("https://example.org/fixture.apk"));val r=ReadyRecord.from(u,f,report(),System.currentTimeMillis(),true);assertEquals(f,r.file(context));assertTrue(r.update().fileReady);assertTrue(r.update().verdict.installAllowed);val p=prefs();p.readyRecords.put(listOf(r));assertEquals(r, p.readyRecords.get().single())}
 @Test fun readyMetadataRejectsDeletedExpiredOrEscapingPaths(){val d=File(context.cacheDir,"downloads").apply{mkdirs()};val f=File(d,"expired.apk").apply{writeText("fixture")};val r=ReadyRecord.from(AppUpdate("Fixture","org.example","2","1",2,1,GitHubSource),f,report(),System.currentTimeMillis(),false);assertNull(r.copy(fileName="../escape.apk").file(context));assertNull(r.copy(checkedAt=System.currentTimeMillis()-ResourcePolicy.TEMP_AGE-1000).file(context));f.delete();assertNull(r.file(context))}
 @Test fun sourceRetryHandlesTransientFailureOnce()=runTest{var n=0;val result=SourceRetry.once{n++;if(n==1)throw DownloadHttpException(504);42};assertEquals(42,result);assertEquals(2,n)}
 @Test fun sourceRetryNeverRepeatsDeniedOrRateLimitedRequests()=runTest{for(code in listOf(401,403,404,429)){var n=0;try{SourceRetry.once{n++;throw DownloadHttpException(code)};fail()}catch(_:DownloadHttpException){};assertEquals(1,n)}}
 @Test fun sourceRetryNeverRepeatsCancellation()=runTest{var n=0;try{SourceRetry.once{n++;throw kotlinx.coroutines.CancellationException()};fail()}catch(_:kotlinx.coroutines.CancellationException){};assertEquals(1,n)}
 @Test fun playWarningPreferenceIsExplicitAndDoesNotSpoofInstaller(){assertTrue(prefs().preferPlay.get());assertEquals("com.android.vending",PlayInstallPolicy.PLAY);assertEquals("ASK",InstallerChoices.ASK)}
}

