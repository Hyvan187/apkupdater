package org.uniapk.upgrader.glass
import androidx.lifecycle.viewModelScope
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.util.*
import org.uniapk.upgrader.viewmodel.UpdatesViewModel
import org.uniapk.upgrader.repository.UpdatesQuery
import com.kryptoprefs.preferences.KryptoBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=Application::class)
class PackagePreparationTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var prefs:Prefs
    private lateinit var vm:UpdatesViewModel
    private lateinit var dir:File
    private var requests=0
    private var invalid=false
    private val bytes get()=checkNotNull(javaClass.getResourceAsStream("/apksets/base.apk")).use{it.readBytes()}
    private fun candidate()=AppUpdate("Fixture","org.example.apkset","2.0","1.0",2,1,GitHubSource,link=Link.Url("https://fixture.invalid/base.apk",size=bytes.size.toLong()))
    @Before fun setup(){
        Dispatchers.setMain(UnconfinedTestDispatcher());prefs=Prefs(KryptoBuilder.nocrypt(context,"preparation-${System.nanoTime()}"),false)
        val appContext=context
        val provider=ArchiveTestProvider().apply{attachInfo(appContext,android.content.pm.ProviderInfo().apply{authority="media"})};org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",provider)
        dir=File(context.cacheDir,"downloads").apply{deleteRecursively();mkdirs()}
        val client=OkHttpClient.Builder().addInterceptor{chain->requests++;Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body((if(invalid)byteArrayOf(1,2,3)else bytes).toResponseBody()).build()}.build()
        val downloader=Downloader(client,client,client,dir);val log=InstallLog()
        val installer=SessionInstaller(context,log,prefs,HistoryStore(prefs)){_,_->PackageInfo().apply{packageName="org.example.apkset";versionName="2.0";longVersionCode=2;applicationInfo=ApplicationInfo().apply{packageName="org.example.apkset";minSdkVersion=23;targetSdkVersion=37}}}
        vm=UpdatesViewModel(UpdatesQuery{flowOf(listOf(candidate()))},installer,prefs,Badger(),downloader,SnackBar(),Stringer(context),log,AppSelection(prefs),context)
    }
    @After fun cleanup(){vm.viewModelScope.cancel();dir.deleteRecursively();Dispatchers.resetMain()}
    @Test fun signedSinglePreparationOffersInstallAndDismissRetainsReadyFile()=runBlocking{
        vm.refresh().join();vm.verify(vm.state().value.updates().single()).join()
        val process=requireNotNull(vm.process.value)
        assertEquals(PackagePhase.READY,process.phase);assertTrue(process.evidence.any{it.property=="Signatur"&&it.status==Compatibility.COMPATIBLE})
        assertEquals(bytes.size.toLong(),process.bytes);assertEquals(bytes.size.toLong(),process.total)
        assertTrue(vm.state().value.updates().single().fileReady);assertEquals(1,dir.listFiles().orEmpty().size)
        vm.hideProcess();assertNull(vm.process.value);assertTrue(vm.state().value.updates().single().fileReady)
        assertFalse(prefs.downloadArchive.get().isEmpty())
    }
    @Test fun batchInspectionDeletesRealSignedBytesAndNeverPublishesCopies()=runBlocking{
        vm.refresh().join();vm.verifyAll().join()
        val update=vm.state().value.updates().single();assertEquals(1,requests);assertNotNull(update.verifiedAt);assertFalse(update.fileReady);assertEquals(Compatibility.UNKNOWN,update.verdict.status)
        assertTrue(prefs.downloadArchive.get().isEmpty());assertTrue(dir.listFiles().orEmpty().isEmpty())
    }
    @Test fun automaticFlagWithoutPersistedFolderCannotFetchAnApk()=runBlocking{
        prefs.autoDownloadUpdates.put(true);prefs.downloadTree.put("content://fixture/tree/ungranted");vm.refresh().join();assertEquals(0,requests);assertTrue(prefs.downloadArchive.get().isEmpty())
    }
    @Test fun invalidPreparationExplainsFailureAndRemovesPartialFile()=runBlocking{
        invalid=true;vm.refresh().join();vm.verify(vm.state().value.updates().single()).join();assertEquals(PackagePhase.FAILED,vm.process.value?.phase);assertTrue(vm.process.value?.message.orEmpty().isNotBlank());assertTrue(dir.listFiles().orEmpty().isEmpty());assertTrue(prefs.downloadArchive.get().isEmpty())
    }
}
