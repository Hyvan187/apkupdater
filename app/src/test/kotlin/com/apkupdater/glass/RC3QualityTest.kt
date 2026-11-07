package com.apkupdater.glass

import android.app.Application
import com.apkupdater.BuildConfig
import com.apkupdater.data.apkmirror.AppExistsRequest
import com.apkupdater.data.apkmirror.AppExistsResponse
import com.apkupdater.data.ui.AppInstalled
import com.apkupdater.diagnostics.*
import com.apkupdater.prefs.Prefs
import com.apkupdater.repository.ApkMirrorRepository
import com.apkupdater.service.ApkMirrorService
import com.kryptoprefs.preferences.KryptoBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=Application::class)
class RC3QualityTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Test fun authFailureIsNotRetriedOnEveryAutomaticMetadataRefresh()=runBlocking {
        ProviderCooldown.retry("ApkMirror");var calls=0
        val service=object:ApkMirrorService { override suspend fun appExists(request:AppExistsRequest):AppExistsResponse { calls++;throw DownloadHttpException(401) } }
        val prefs=Prefs(KryptoBuilder.nocrypt(context,"rc3-source-test"),false)
        val repository=ApkMirrorRepository(service,prefs,context.packageManager)
        val apps=listOf(AppInstalled("Fixture","org.example.fixture","1",1))
        try {
            SourceHealth.reset();repository.updates(apps).toList();assertEquals(1,calls)
            SourceHealth.reset();repository.updates(apps).toList();assertEquals(1,calls)
            assertTrue(SourceHealth.flow.value.single().problem.orEmpty().contains("401"))
            ProviderCooldown.retry("ApkMirror");repository.updates(apps).toList();assertEquals(2,calls)
        } finally {ProviderCooldown.retry("ApkMirror")}
    }
    @Test fun cooldownExpiresAndOnlyAuthAndRateErrorsAreHeld() {
        ProviderCooldown.retry("fixture")
        ProviderCooldown.note("fixture",401,1_000);assertEquals(401,ProviderCooldown.active("fixture",2_000)?.status)
        assertNull(ProviderCooldown.active("fixture",1_000+31*60_000))
        ProviderCooldown.note("fixture",500,1_000);assertNull(ProviderCooldown.active("fixture",2_000))
        ProviderCooldown.note("fixture",429,1_000);assertNotNull(ProviderCooldown.active("fixture",2_000));ProviderCooldown.retry("fixture")
    }
    @Test fun staleSourceFailureCannotReplaceCurrentQueryState()=runBlocking {
        SourceHealth.reset("scan-new")
        withContext(DiagnosticContext.element(mapOf("operationId" to "scan-new"))) { SourceHealth.unavailable("Play","new status") }
        withContext(DiagnosticContext.element(mapOf("operationId" to "scan-old"))) { SourceHealth.failed("Play",DownloadHttpException(401)) }
        assertEquals("new status",SourceHealth.flow.value.single().problem)
        SourceHealth.reset()
    }
    @Test fun sourceCoroutineCanBeCancelledWithoutClaimingCompletion()=runBlocking {
        val entered=CompletableDeferred<Unit>();val token=ScanProgress.begin();ScanProgress.sources(token,1)
        val job=launch {SourceHealth.observe("Fixture",emptyList<Int>(),flow<List<Int>> {entered.complete(Unit);awaitCancellation()},token).collect()}
        entered.await();job.cancelAndJoin()
        assertEquals(0,ScanProgress.state.value.completed)
        ScanProgress.finish(token,ScanPhase.CANCELLED)
        assertEquals(ScanPhase.CANCELLED,ScanProgress.state.value.phase)
    }
    @Test fun brandingAndBuildFingerprintIdentifyInstalledRelease() {
        assertEquals("Liquid-APK-Store",context.getString(com.apkupdater.R.string.app_name))
        assertTrue(BuildConfig.BUILD_FINGERPRINT.matches(Regex("[0-9a-f]{64}")))
        assertEquals("0.6.0-rc3",BuildConfig.VERSION_NAME);assertEquals(1008,BuildConfig.VERSION_CODE)
    }
    @Test fun newEnglishControlsDoNotRetainGermanDownloadOrDiagnosticLabels() {
        assertEquals("Download and check APKs in a batch",UiEnglish.translate("APKs gesammelt herunterladen und prüfen","en"))
        assertEquals("Developer diagnostics",UiEnglish.translate("Entwicklerdiagnose","en"))
        assertTrue(UiEnglish.translate(HelpText.CHECK,"en").contains("explicitly"))
    }
    @Test fun diagnosticConfigurationPersistsAndBothExportsAreUsable()=runBlocking {
        val app=context as Application;AppDiagnostics.initialize(app);AppDiagnostics.configure(enabled=true,verbose=true)
        val settings=app.getSharedPreferences("diagnostics.settings",android.content.Context.MODE_PRIVATE)
        assertTrue(settings.getBoolean("enabled",false));assertTrue(settings.getBoolean("verbose",false))
        val provider=ArchiveTestProvider().apply { attachInfo(app,android.content.pm.ProviderInfo().apply{authority="media"}) }
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",provider)
        AppDiagnostics.info("quality.fixture",mapOf("token" to "SECRET"));AppDiagnostics.debug("quality.detail")
        for(kind in DiagnosticExport.entries) {
            val uri=provider.insert(android.net.Uri.parse("content://media/external/downloads"),android.content.ContentValues())
            AppDiagnostics.export(context,uri,kind)
            java.util.zip.ZipFile(provider.files.getValue(uri)).use { zip ->
                val metadata=zip.getInputStream(zip.getEntry("metadata.json")).reader().readText()
                assertTrue(metadata.contains("buildFingerprint"));assertTrue(metadata.contains("\"format\":2"))
                val name=if(kind==DiagnosticExport.FULL)"diagnostics-full.jsonl" else "diagnostics-short.txt"
                val text=zip.getInputStream(zip.getEntry(name)).reader().readText()
                assertTrue(text.contains("quality.fixture"));assertFalse(text.contains("SECRET"))
                if(kind==DiagnosticExport.SHORT)assertFalse(text.contains("quality.detail"))
            }
        }
        AppDiagnostics.configure(verbose=false)
    }
}
