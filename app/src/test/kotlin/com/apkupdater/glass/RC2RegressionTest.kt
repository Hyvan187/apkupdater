package com.apkupdater.glass

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.viewModelScope
import com.apkupdater.data.ui.*
import com.apkupdater.prefs.Prefs
import com.apkupdater.repository.UpdatesQuery
import com.apkupdater.util.*
import com.apkupdater.viewmodel.UpdatesViewModel
import com.apkupdater.worker.UpdatesWorker
import com.kryptoprefs.preferences.KryptoBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37], application=Application::class)
class RC2RegressionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var prefs: Prefs
    private lateinit var downloader: Downloader
    private val requests = AtomicInteger()
    private val resolutions = AtomicInteger()
    private val models = mutableListOf<UpdatesViewModel>()
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs = Prefs(KryptoBuilder.nocrypt(context,"rc2-tests"), false)
        val client=OkHttpClient.Builder().addInterceptor { requests.incrementAndGet(); error("Unexpected APK request") }.build()
        downloader=Downloader(client,client,client,File(context.cacheDir,"downloads").apply { mkdirs() })
    }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; stopKoin(); Dispatchers.resetMain() }
    private fun app(source: Source=PlaySource, code: Long=2) = AppUpdate("Fixture","org.example.update",code.toString(),"1",code,1,source,
        link=Link.Play { resolutions.incrementAndGet(); error("Unexpected Play resolution") })
    private fun model(query: UpdatesQuery): UpdatesViewModel {
        val log=InstallLog()
        return UpdatesViewModel(query,SessionInstaller(context,log,prefs,HistoryStore(prefs)),prefs,Badger(),downloader,SnackBar(),Stringer(context),log,AppSelection(prefs),context).also { models+=it }
    }
    private fun installed(code: Long) {
        Shadows.shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName="org.example.update"; longVersionCode=code;versionName=code.toString()
            applicationInfo=ApplicationInfo().apply { packageName="org.example.update" }
        })
    }
    @Test fun legacyAutomaticDownloadSettingIsDisabledPersistently() {
        prefs.autoVerify.put(true);prefs.explicitDownloadsSchema.put(0)
        prefs.migrateDefaults()
        val reloaded=Prefs(KryptoBuilder.nocrypt(context,"rc2-tests"),false)
        assertFalse(reloaded.autoVerify.get());assertEquals(1,reloaded.explicitDownloadsSchema.get())
        prefs.usePlay.put(false);prefs.migrateDefaults();assertFalse(prefs.usePlay.get())
    }
    @Test fun refreshCannotDownloadEvenWithLegacyFlagAndUnmeteredNetwork() = runBlocking {
        prefs.autoVerify.put(true);prefs.autoVerifyWifiOnly.put(false)
        val vm=model(UpdatesQuery { flowOf(listOf(app())) })
        vm.refresh().join()
        assertEquals(1,vm.state().value.updates().size)
        assertEquals(0,resolutions.get());assertEquals(0,requests.get())
    }
    @Test fun workerCannotResolveOrDownloadApksEvenWithLegacyFlag() = runBlocking {
        prefs.autoVerify.put(true);prefs.autoVerifyWifiOnly.put(false)
        startKoin { modules(module {
            single { prefs };single { AppSelection(prefs) }
            single<UpdatesQuery> { UpdatesQuery { flowOf(listOf(app())) } }
            single { UpdatesNotification(context) }
            single { downloader }
        }) }
        val worker=androidx.work.testing.TestListenableWorkerBuilder<UpdatesWorker>(context).build()
        assertEquals(androidx.work.ListenableWorker.Result.success(),worker.doWork())
        assertEquals(0,resolutions.get());assertEquals(0,requests.get())
    }
    @Test fun installedVersionRemovesEverySourceAndLateProviderEmission() = runBlocking {
        installed(1)
        val first=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val candidates=listOf(app(),app(AptoideSource),app(ApkPureSource,3))
        val vm=model(UpdatesQuery { flow { emit(candidates);first.complete(Unit);release.await();emit(candidates) } })
        val job=vm.refresh();withTimeout(5_000){first.await()}
        installed(2);vm.reconcileInstalledUpdates()
        assertEquals(listOf(3L),vm.state().value.updates().map { it.versionCode })
        assertEquals(2L,vm.state().value.updates().single().oldVersionCode)
        release.complete(Unit);job.join()
        assertEquals(listOf(3L),vm.state().value.updates().map { it.versionCode })
        assertEquals(0,requests.get())
    }
    @Test fun unchangedInstalledVersionKeepsUpdatesAfterCancelledInstaller() = runBlocking {
        installed(1);val vm=model(UpdatesQuery { flowOf(listOf(app(),app(AptoideSource))) })
        vm.refresh().join();vm.reconcileInstalledUpdates()
        assertEquals(2,vm.state().value.updates().size)
    }
    @Test fun missingVerificationFileNeverCausesSilentRedownload() = runBlocking {
        val ready=app().copy(verdict=Verdict(Compatibility.COMPATIBLE,emptyList()),verifiedAt=1)
        val vm=model(UpdatesQuery { flowOf(listOf(ready)) });vm.refresh().join()
        vm.install(ready,object:UriHandler { override fun openUri(uri:String) { error("Unexpected browser") } })
        withTimeout(5_000) { while(vm.state().value.updates().single().verifiedAt!=null) delay(10) }
        assertEquals(Compatibility.UNKNOWN,vm.state().value.updates().single().verdict.status)
        assertEquals(0,resolutions.get());assertEquals(0,requests.get())
    }
    @Test fun refreshingCannotReplaceAnActiveVerificationProgressJob() = runBlocking {
        val entered=CompletableDeferred<Unit>();val release=java.util.concurrent.CountDownLatch(1)
        val candidate=app().copy(link=Link.Play { entered.complete(Unit);release.await(5,java.util.concurrent.TimeUnit.SECONDS);error("Fixture failure") })
        val vm=model(UpdatesQuery { flowOf(listOf(candidate)) });vm.refresh().join()
        val verify=vm.verify(candidate);withTimeout(5_000){entered.await()}
        val id=ScanProgress.state.value.id;val refresh=vm.refresh()
        try { delay(50);assertEquals(id,ScanProgress.state.value.id);assertEquals(ScanPhase.VERIFYING,ScanProgress.state.value.phase) }
        finally { release.countDown() }
        verify.join();refresh.join()
        assertTrue(ScanProgress.state.value.id>id)
    }
    @Test fun individualVerificationIsCancellableWithoutFetchingAnyPart() = runBlocking {
        val entered=CompletableDeferred<Unit>();val release=java.util.concurrent.CountDownLatch(1)
        val candidate=app().copy(link=Link.Play { entered.complete(Unit);release.await(5,java.util.concurrent.TimeUnit.SECONDS);emptyList() })
        val vm=model(UpdatesQuery { flowOf(listOf(candidate)) });vm.refresh().join()
        val task=vm.verify(candidate);withTimeout(5_000){entered.await()}
        vm.cancelVerification();release.countDown();task.join()
        assertTrue(task.isCancelled);assertEquals(ScanPhase.CANCELLED,ScanProgress.state.value.phase)
        assertEquals(0,requests.get());assertFalse(vm.state().value.updates().single().isInstalling)
    }
    @Test fun failedAttemptIsShownAsFailedRatherThanVerified() = runBlocking {
        val vm=model(UpdatesQuery { flowOf(listOf(app())) });vm.refresh().join();vm.verify(app()).join()
        assertEquals(1,ScanProgress.state.value.completed);assertEquals(1,ScanProgress.state.value.failedChecks)
        assertNull(vm.state().value.updates().single().verifiedAt)
    }
    @Test fun staleAndDuplicateSourceCallbacksDoNotCorruptProgress() {
        val old=ScanProgress.begin();ScanProgress.sources(old,2)
        ScanProgress.sourceFinished(old,"A",false);ScanProgress.sourceFinished(old,"A",false)
        assertEquals(1,ScanProgress.state.value.completed)
        val fresh=ScanProgress.begin();ScanProgress.sources(fresh,1)
        ScanProgress.sourceFinished(old,"B",true);ScanProgress.finish(old)
        assertEquals(fresh,ScanProgress.state.value.id);assertEquals(0,ScanProgress.state.value.completed)
        ScanProgress.sourceFinished(fresh,"C",true);assertEquals(setOf("C"),ScanProgress.state.value.failedSources)
        ScanProgress.finish(fresh)
    }
    @Test fun transientMissingPackageIsRetriedUntilExpectedVersionAppears() = runTest {
        var calls=0
        val actual=awaitInstalledVersion(2) { calls++;if(calls<=2)null else ActualInstalledVersion(2,"2") }
        assertEquals(2L,actual.code);assertEquals(3,calls)
    }
    @Test fun oldVersionCanNeverBeReportedAsSuccessfulInstallation() = runTest {
        try { awaitInstalledVersion(2,400) { ActualInstalledVersion(1,"1") };fail("Old version accepted") }
        catch (_: TimeoutCancellationException) { }
    }
    @Test fun pendingSelfUpdateSurvivesRestartAndIsRecordedOnlyOnce() {
        installed(2)
        val pending=PendingInstall("org.example.update","Fixture","1","2",1,2,"Play",System.currentTimeMillis(),installer="Android PackageInstaller")
        HistoryStore(prefs).pending(pending)
        val restarted=HistoryStore(prefs);restarted.reconcile(context);restarted.reconcile(context)
        assertEquals(1,restarted.state.value.size);assertTrue(prefs.pendingInstalls.get().isEmpty())
        assertTrue(restarted.state.value.single().installer.startsWith("Android PackageInstaller"))
    }
    @Test fun explicitNativeFailureCannotCreateSuccessHistory() {
        installed(1)
        val history=HistoryStore(prefs);val pending=PendingInstall("org.example.update","Fixture","1","2",1,2,"Play",1)
        history.pending(pending);history.cancelPending(pending);history.reconcile(context)
        assertTrue(history.state.value.isEmpty());assertTrue(prefs.pendingInstalls.get().isEmpty())
    }
    @Test fun cacheLeaseProtectsActiveFilesAndPreservesPendingHandoffs() = runBlocking {
        val download=File(context.cacheDir,"downloads/file.apk").apply { parentFile?.mkdirs();writeText("fixture") }
        val pending=File(context.cacheDir,"external-installer/pending.apk").apply { parentFile?.mkdirs();writeText("keep") }
        val diagnostic=File(context.filesDir,"diagnostics/events-0.jsonl").apply { parentFile?.mkdirs();writeText("keep") }
        val http=okhttp3.Cache(File(context.cacheDir,"http"),1024)
        val lease=AppCache.begin()
        try { try { AppCache.clear(context,http);fail("Active cache cleared") } catch (_: IllegalStateException) {} ;assertTrue(download.exists()) }
        finally { lease.close() }
        AppCache.clear(context,http);http.close()
        assertFalse(download.exists());assertTrue(pending.exists());assertTrue(diagnostic.exists())
    }
    @Test fun layoutPrefersConfiguredWindowAndKeepsSystemFontScale() {
        val layout=DeviceLayout.resolve(480,320,720,1440,3200,1.8f)
        assertEquals(320,layout.widthDp);assertEquals(720,layout.heightDp);assertEquals(1.8f,layout.fontScale)
        assertEquals(320,DeviceLayout.resolve(480,0,0,960,2160,1f).widthDp)
        assertEquals(915,DeviceLayout.resolve(320,915,412,1080,2400,1f).widthDp)
    }
    @Test fun manualRegionNeverOverridesActualInstallationRegion() {
        val selection=RegionOverview(Region.IN,"EU")
        assertEquals("EU",selection.comparison);assertEquals(Region.IN,selection.effective);assertTrue(selection.mismatch)
        assertEquals(Region.UNKNOWN,RegionOverview(Region.UNKNOWN,"IN").effective)
    }
    @Test fun risksAreDerivedFromConcreteEvidenceAndBlockedFallbackRemainsBlocked() {
        val u=app().copy(verdict=Verdict(Compatibility.INCOMPATIBLE,listOf(Evidence("Signatur","Falscher Signierer",Compatibility.INCOMPATIBLE),Evidence("Region","Nicht belegt",Compatibility.UNKNOWN))))
        val risk=TechnicalAssessment.from(u)
        assertEquals(listOf("Signatur"),risk.conflicts.map { it.property });assertEquals(listOf("Region"),risk.missing.map { it.property })
        assertTrue(risk.details().contains("Falscher Signierer"));assertTrue(TechnicalAssessment.from(u.copy(verdict=Verdict(Compatibility.INCOMPATIBLE,emptyList()))).conflicts.isNotEmpty())
    }
    @Test fun appearancePreviewCommitsAndDoesNotOverwriteOtherSettings() {
        val store=AppearanceStore(prefs);store.preview { it.copy(speed=.8f,density=.7f,opacity=.2f,motion=MotionMode.NET.ordinal) }
        assertEquals(.46f,AppearanceStore(prefs).state.value.opacity,.001f)
        store.commitPreview();val actual=AppearanceStore(prefs).state.value
        assertEquals(.8f,actual.speed,.001f);assertEquals(.7f,actual.density,.001f);assertEquals(.2f,actual.opacity,.001f)
        assertEquals(MotionMode.NET.ordinal,actual.motion);assertEquals(prefs.theme.get(),actual.theme)
    }
    @Test fun explicitAnimationChoiceAlwaysWinsOverSeasonalRandom() {
        MotionMode.entries.filter { it!=MotionMode.RANDOM }.forEach { mode -> assertEquals(mode,MotionPolicy.mode(mode.ordinal,true,12,23)) }
        assertEquals(MotionMode.SNOW,MotionPolicy.mode(MotionMode.RANDOM.ordinal,true,12,23))
        assertTrue(MotionPolicy.count(MotionMode.NET,1f)>MotionPolicy.count(MotionMode.NET,0f))
    }
}
