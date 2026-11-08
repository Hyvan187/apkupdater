package org.uniapk.upgrader.glass

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.viewModelScope
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.repository.UpdatesQuery
import org.uniapk.upgrader.util.*
import org.uniapk.upgrader.viewmodel.UpdatesViewModel
import org.uniapk.upgrader.worker.UpdatesWorker
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
    private fun attachPrepared(vm:UpdatesViewModel,u:AppUpdate,file:File,digest:String,published:Boolean=true){
        val report=ApkAnalyzer.Report(Candidate(u.packageName,u.versionCode,versionName=u.version),u.verdict,digest,emptySet(),37,null)
        val type=Class.forName("org.uniapk.upgrader.viewmodel.UpdatesViewModel"+"$"+"Checked")
        val ctor=type.declaredConstructors.first{it.parameterCount==4}.apply{isAccessible=true}
        val field=vm.javaClass.getDeclaredField("verified").apply{isAccessible=true}
        @Suppress("UNCHECKED_CAST") val cache=field.get(vm) as MutableMap<String,Any>
        cache["${u.artifactKey}:${u.oldVersionCode}"]=ctor.newInstance(file,report,1L,published)
    }
    @Test fun deletingReadyCopiesMovesAllVariantsBackToFindings()=runBlocking{
        val provider=ArchiveTestProvider().also{it.attachInfo(context,android.content.pm.ProviderInfo().apply{authority="media"});org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",it)}
        val file=File(context.cacheDir,"ready-delete.apk").apply{writeText("synthetic bytes")};DownloadArchive(context,prefs).publish(file,"org.example.update","2");val digest=prefs.downloadArchive.get().keys.single();prefs.autoDownloadReceipts.put(mapOf("receipt" to digest))
        val ready=app().copy(fileReady=true,sha256=digest,verifiedAt=1,verdict=Verdict(Compatibility.COMPATIBLE,emptyList()));val other=ready.copy(source=AptoideSource,artifactKey="other-ready-source")
        val vm=model(UpdatesQuery{flowOf(listOf(ready,other))});vm.refresh().join();attachPrepared(vm,ready,file,digest);attachPrepared(vm,other,file,digest);vm.discardPrepared(ready).join()
        assertFalse(file.exists());assertTrue(provider.files.isEmpty());assertTrue(prefs.autoDownloadReceipts.get().isEmpty());assertEquals(2,vm.state().value.updates().size);assertTrue(vm.state().value.updates().all{it.verdict.status==Compatibility.UNKNOWN&&!it.fileReady&&it.sha256==null});assertEquals(Compatibility.UNKNOWN,vm.state().value.updates().appGroups().single().status);assertTrue(releaseKey(ready) in prefs.discardedDownloads.get());assertTrue(vm.deletingPackages.value.isEmpty());assertEquals(0,requests.get())
    }
    @Test fun externallyRemovedPublicApkInvalidatesPrivateReadyCopyWithoutNetwork()=runBlocking{
        val provider=ArchiveTestProvider().also{it.attachInfo(context,android.content.pm.ProviderInfo().apply{authority="media"});org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",it)}
        val file=File(context.cacheDir,"ready-external.apk").apply{writeText("synthetic bytes")};val uri=DownloadArchive(context,prefs).publish(file,"org.example.update","2");val digest=prefs.downloadArchive.get().keys.single();val ready=app().copy(fileReady=true,sha256=digest,verifiedAt=1,verdict=Verdict(Compatibility.COMPATIBLE,emptyList()))
        val vm=model(UpdatesQuery{flowOf(listOf(ready))});vm.refresh().join();attachPrepared(vm,ready,file,digest);provider.delete(uri,null,null);vm.reconcilePreparedFiles();assertFalse(file.exists());assertFalse(vm.state().value.updates().single().fileReady);assertEquals(Compatibility.UNKNOWN,vm.state().value.updates().single().verdict.status);assertEquals(0,requests.get());assertEquals(0,resolutions.get())
    }
    @Test fun expiredPrivateCachePreservesKeptPublicCopy()=runBlocking{
        val provider=ArchiveTestProvider().also{it.attachInfo(context,android.content.pm.ProviderInfo().apply{authority="media"});org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",it)};prefs.keepDownloadedApks.put(true)
        val file=File(context.cacheDir,"ready-expired.apk").apply{writeText("synthetic bytes")};val uri=DownloadArchive(context,prefs).publish(file,"org.example.update","2");val digest=prefs.downloadArchive.get().keys.single();val ready=app().copy(fileReady=true,sha256=digest,verifiedAt=1,verdict=Verdict(Compatibility.COMPATIBLE,emptyList()))
        val vm=model(UpdatesQuery{flowOf(listOf(ready))});vm.refresh().join();attachPrepared(vm,ready,file,digest);file.delete();vm.reconcilePreparedFiles();assertTrue(provider.files.getValue(uri).exists());assertTrue(prefs.downloadArchive.get().containsKey(digest));assertFalse(vm.state().value.updates().single().fileReady);assertEquals(0,requests.get())
    }
    @Test fun criticalInstallAsksForRiskAcknowledgementBeforeInstallerOrNetwork()=runBlocking{
        val ready=app().copy(packageName="com.google.android.gms",system=true,fileReady=true,sha256="fixture",verifiedAt=1,verdict=Verdict(Compatibility.COMPATIBLE,emptyList()));val vm=model(UpdatesQuery{flowOf(listOf(ready))});vm.refresh().join();vm.install(ready,object:UriHandler{override fun openUri(uri:String){error("No browser")}})
        withTimeout(5_000){while(vm.riskConfirmation.value==null)delay(10)};assertEquals(ready,vm.riskConfirmation.value);assertEquals(0,requests.get());assertEquals(0,resolutions.get());vm.dismissRisk();assertNull(vm.riskConfirmation.value)
    }

}
