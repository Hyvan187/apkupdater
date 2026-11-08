package org.uniapk.upgrader.glass
import android.app.Application
import android.content.pm.*
import android.net.Uri
import com.kryptoprefs.preferences.KryptoBuilder
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.util.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=Application::class)
class RC3InstallerTest {
 private val context get()=RuntimeEnvironment.getApplication()
 private lateinit var prefs:Prefs
 private lateinit var installer:SessionInstaller
 private val target="org.example.installer"
 private fun fixture(name:String)=checkNotNull(javaClass.getResourceAsStream("/apksets/$name"))
 @Before fun setup(){
  prefs=Prefs(KryptoBuilder.nocrypt(context,"rc3-installer-${System.nanoTime()}"),false)
  val appContext=context
  val provider=ArchiveTestProvider().apply{attachInfo(appContext,ProviderInfo().apply{authority="media"})};ShadowContentResolver.registerProviderInternal("media",provider)
  installer=SessionInstaller(context,InstallLog(),prefs,HistoryStore(prefs)){_,_->PackageInfo().apply{packageName="org.example.apkset";versionName="2.0";longVersionCode=2;applicationInfo=ApplicationInfo().apply{packageName="org.example.apkset";minSdkVersion=23;targetSdkVersion=37}}}
  val pi=PackageInfo().apply{packageName=target;applicationInfo=ApplicationInfo().apply{packageName=target;nonLocalizedLabel="Fixture installer"}}
  Shadows.shadowOf(context.packageManager).installPackage(pi)
  for(f in InstallFormat.entries)for(mime in f.mimeTypes){val info=ResolveInfo().apply{activityInfo=ActivityInfo().apply{packageName=target;name="$target.OpenActivity";exported=true;enabled=true;applicationInfo=pi.applicationInfo};nonLocalizedLabel="Fixture installer"};Shadows.shadowOf(context.packageManager).addResolveInfoForIntent(InstallerChoices.intent(Uri.parse("content://placeholder/checked.${f.extension}"),mime),info)}
 }
 @Test fun signedSingleApkCanBeHandedOffAndDoesNotClaimSuccess()=runBlocking{val done=installer.install(1,"org.example.apkset",fixture("base.apk"),"Fixture",target);assertFalse(done);assertTrue(prefs.updateHistory.get().isEmpty());val f=File(prefs.pendingInstalls.get().single().stagedPath);assertTrue(f.exists());assertEquals(InstallFormat.APK,InstallerChoices.format(f));assertEquals(target,Shadows.shadowOf(context).nextStartedActivity.component!!.packageName)}
 @Test fun oneApkInsideArchiveIsUnwrappedInsteadOfBeingRejected()=runBlocking{val archive=File(context.cacheDir,"single.zip");ZipOutputStream(archive.outputStream()).use{z->z.putNextEntry(ZipEntry("base.apk"));fixture("base.apk").use{it.copyTo(z)};z.closeEntry()};assertFalse(installer.install(2,"org.example.apkset",archive.inputStream(),"Fixture",target));ZipFile(File(prefs.pendingInstalls.get().single().stagedPath)).use{assertNotNull(it.getEntry("AndroidManifest.xml"))}}
 @Test fun validatedSplitSetIsPassedAsApksWithEveryPart()=runBlocking{assertFalse(installer.install(3,"org.example.apkset",fixture("complete.apks"),"Fixture",target));val staged=File(prefs.pendingInstalls.get().single().stagedPath);assertEquals("apks",staged.extension);ZipFile(staged).use{z->assertNotNull(z.getEntry("base.apk"));assertNotNull(z.getEntry("split-1.apk"));assertEquals(2,z.size())};assertTrue(prefs.updateHistory.get().isEmpty())}
 @Test fun changedSigningIdentityRemainsBlockedForExternalInstaller()=runBlocking{try{installer.install(4,"org.example.apkset",fixture("wrong-signer.apks"),"Fixture",target);fail("Mismatched signers must remain blocked")}catch(_:IllegalArgumentException){};assertTrue(prefs.pendingInstalls.get().isEmpty());assertNull(Shadows.shadowOf(context).nextStartedActivity)}
}
