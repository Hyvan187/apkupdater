package org.uniapk.upgrader.glass
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.uniapk.upgrader.prefs.Prefs
import com.kryptoprefs.preferences.KryptoBuilder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
class ArchiveTestProvider : ContentProvider() {
 var denied=false
 val files=mutableMapOf<Uri,File>();var lastValues: ContentValues?=null;var published=false
 override fun onCreate()=true
 override fun insert(uri: Uri,values: ContentValues?): Uri { lastValues=values;val result=Uri.parse("content://media/external/downloads/${files.size+1}");files[result]=File.createTempFile("archive-",".apk",requireNotNull(context).cacheDir);return result }
 override fun openFile(uri: Uri,mode: String)=ParcelFileDescriptor.open(files[uri] ?: throw java.io.FileNotFoundException("Fixture missing"),ParcelFileDescriptor.parseMode(mode))
 override fun update(uri: Uri,values: ContentValues?,selection: String?,args: Array<out String>?): Int {published=true;return 1}
 override fun delete(uri: Uri,selection: String?,args: Array<out String>?): Int {if(denied)throw SecurityException("Fixture revoked");return if(files.remove(uri)?.delete()==true)1 else 0}
 override fun query(uri: Uri,projection: Array<out String>?,selection: String?,args: Array<out String>?,sort: String?): Cursor {if(denied)throw SecurityException("Fixture revoked");val columns=projection ?: arrayOf(android.provider.OpenableColumns.DISPLAY_NAME);return android.database.MatrixCursor(columns).apply{files[uri]?.takeIf{it.exists()}?.let{f->addRow(columns.map<String,Any?>{if(it==android.provider.OpenableColumns.DISPLAY_NAME)f.name else f.length()}.toTypedArray())}}}
 override fun getType(uri: Uri)="application/vnd.android.package-archive"
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=android.app.Application::class)
class DownloadArchiveTest {
 private lateinit var provider: ArchiveTestProvider;private lateinit var prefs: Prefs;private lateinit var archive: DownloadArchive;private lateinit var input: File
 @Before fun setup() {val c=RuntimeEnvironment.getApplication();provider=ArchiveTestProvider();provider.attachInfo(c,android.content.pm.ProviderInfo().apply{authority="media"});ShadowContentResolver.registerProviderInternal("media",provider);prefs=Prefs(KryptoBuilder.nocrypt(c,"archive-test"),false);archive=DownloadArchive(c,prefs);input=File(c.cacheDir,"checked.apk").apply{writeText("exact-private-test-bytes")} }
 @Test fun copyPreservesPrivateBytesAndUsesDownloadFolder() {val u=archive.publish(input,"com.example","1.0");assertEquals(input.readText(),provider.files[u]!!.readText());assertEquals("Download/UniAPK",provider.lastValues!!.getAsString("relative_path"));assertTrue(provider.published);assertTrue(input.exists())}
 @Test fun copiesAreReusedByHash() {assertEquals(archive.publish(input,"com.example","1.0"),archive.publish(input,"com.example","1.0"));assertEquals(1,provider.files.size)}
 @Test fun successDeletesOnlyPublicCopyWhenDisabled() {prefs.keepDownloadedApks.put(false);archive.publish(input,"com.example","1.0");archive.installed(input);assertTrue(provider.files.isEmpty());assertTrue(input.exists());assertTrue(prefs.downloadArchive.get().isEmpty())}
 @Test fun keepOptionRetainsPublicCopy() {prefs.keepDownloadedApks.put(true);archive.publish(input,"com.example","1.0");archive.installed(input);assertEquals(1,provider.files.size)}
 @Test fun failuresNeverTriggerDeletion() {assertFalse(DownloadNames.deleteAfterSuccess(false,false));assertFalse(DownloadNames.deleteAfterSuccess(false,true));assertTrue(DownloadNames.deleteAfterSuccess(true,false))}
 @Test fun unsafeNamesCannotEscapeFolder() {val n=DownloadNames.name("com.example","../../bad/version","abcdef0123");assertFalse(n.contains('/'));assertTrue(n.endsWith("abcdef01.apk"))}
 @Test fun confirmedSelfUpdateCleansPublicCopyAfterProcessRestart() {
  val context=RuntimeEnvironment.getApplication()
  archive.publish(input,"org.example.update","2")
  val digest=prefs.downloadArchive.get().keys.single()
  val pending=PendingInstall("org.example.update","Fixture","1","2",1,2,"Play",System.currentTimeMillis(),installer="Android PackageInstaller",downloadDigest=digest)
  HistoryStore(prefs).pending(pending)
  org.robolectric.Shadows.shadowOf(context.packageManager).installPackage(android.content.pm.PackageInfo().apply {
   packageName="org.example.update";longVersionCode=2;versionName="2";applicationInfo=android.content.pm.ApplicationInfo().apply{packageName="org.example.update"}
  })
  val restarted=HistoryStore(prefs);restarted.reconcile(context)
  assertEquals(1,restarted.state.value.size);assertTrue(provider.files.isEmpty());assertTrue(input.exists())
 }
}
