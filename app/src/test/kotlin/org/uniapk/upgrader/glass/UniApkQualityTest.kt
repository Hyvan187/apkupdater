package org.uniapk.upgrader.glass

import android.app.Application
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.diagnostics.*
import org.uniapk.upgrader.prefs.Prefs
import com.kryptoprefs.preferences.KryptoBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=Application::class)
class UniApkQualityTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun prefs()=Prefs(KryptoBuilder.nocrypt(context,"uniapk-quality-${System.nanoTime()}"),false)
    @Test fun productNamesAreIndependentOfRegionalCodes(){assertTrue(TestDeviceProfiles.presets.any{it.name=="OnePlus 15"});assertFalse(TestDeviceProfiles.presets.any{it.name.contains("CPH")});assertEquals("CPH2745",TestDeviceProfiles.resolve("oneplus15","IN")?.code);assertEquals("CPH2747",TestDeviceProfiles.resolve("oneplus15","EU")?.code)}
    @Test fun unsupportedCountryHasNoInventedCode(){assertNull(TestDeviceProfiles.resolve("oneplus16","EU"));assertNotNull(TestDeviceProfiles.resolve("findx10","IN"))}
    @Test fun profileActuallySetsModelBrandAndCountryHint(){val props=java.util.Properties();props.setProperty("Build.MODEL","ACTUAL");TestDeviceProfiles.apply(props,"findx10","IN");assertEquals("CPH2911",props.getProperty("Build.MODEL"));assertEquals("OPPO",props.getProperty("Build.MANUFACTURER"));assertEquals("en_IN",props.getProperty("Locale"));assertEquals("Asia/Kolkata",props.getProperty("TimeZone"))}
    @Test fun unsupportedProfileFailsBeforeAnySubstitution(){val props=java.util.Properties();try{TestDeviceProfiles.apply(props,"oneplus16","EU");fail()}catch(_:IllegalArgumentException){};assertTrue(props.isEmpty)}
    @Test fun actualProfileDoesNotModifyNativeProperties(){val p=java.util.Properties();p.setProperty("Build.MODEL","actual");TestDeviceProfiles.apply(p,"ACTUAL","CN");assertEquals(1,p.size);assertEquals("actual",p.getProperty("Build.MODEL"))}
    @Test fun samsungActiveCscWinsOverPackagePath(){val r=RomDetector.detect(mapOf("ro.boot.sales_code" to "INS","persist.sys.omc_path" to "/prism/etc/carriers/single/EUX"));assertEquals(Region.IN,r.region);assertFalse(r.conflict)}
    @Test fun samsungOmcPathIsAUsefulFallback(){assertEquals(Region.EU,RomDetector.detect(mapOf("persist.sys.omc_path" to "/prism/etc/carriers/single/EUX")).region)}
    @Test fun contradictoryActiveSamsungSignalsAreNotGuessed(){assertTrue(RomDetector.detect(mapOf("ro.boot.sales_code" to "INS","ro.csc.sales_code" to "EUX")).conflict)}
    @Test fun xiaomiFirmwareAndOtherVendorsAreDetected(){assertEquals(Region.EU,RomDetector.detect(mapOf("ro.build.version.incremental" to "OS3.0.1.0.WOCEUXM")).region);assertEquals(Region.IN,RomDetector.detect(mapOf("ro.carrier" to "retin")).region);assertEquals(Region.CN,RomDetector.detect(mapOf("ro.hw.country" to "hw/cn")).region)}
    @Test fun modelAndLanguageDoNotPretendToBeFirmwareEvidence(){assertEquals(Region.UNKNOWN,RomDetector.detect(mapOf("ro.product.model" to "SM-S911B","persist.sys.locale" to "de-DE")).region)}
    @Test fun sameVisibleVersionIsNotOfferedEvenWithDifferentBuildCode(){val u=AppUpdate("Fixture","org.example","v1.2.3","1.2.3",999,100,GitHubSource);assertNull(InstalledUpdatePolicy.reconcile(u,ActualInstalledVersion(100,"1.2.3")))}
    @Test fun genuinelyNewVisibleVersionRemains(){val u=AppUpdate("Fixture","org.example","1.2.4","1.2.3",101,100,GitHubSource);assertNotNull(InstalledUpdatePolicy.reconcile(u,ActualInstalledVersion(100,"1.2.3")))}
    @Test fun automaticDownloadsAreOffAndRequireAnExplicitFolder(){val p=prefs();p.migrateDefaults();assertFalse(p.autoDownloadUpdates.get());assertFalse(DownloadFolderPolicy.valid(context,p));assertEquals(512,p.autoVerifyLimitMiB.get());assertEquals(2048,p.autoCheckBudgetMiB.get());assertEquals(10,p.autoCheckCount.get())}
    @Test fun lowFreeSpaceCannotConsumeTheReserve(){assertEquals(0L,ResourcePolicy.budget(700*ResourcePolicy.MIB,2048*ResourcePolicy.MIB));val b=ResourcePolicy.budget(10*1024*ResourcePolicy.MIB,2048*ResourcePolicy.MIB);assertTrue(b<=2048*ResourcePolicy.MIB);assertTrue(b<=10*1024*ResourcePolicy.MIB/5)}
    @Test fun cpuConcurrencyIsActuallyClamped(){assertEquals(1,ResourcePolicy.parallelism(0,8));assertEquals(4,ResourcePolicy.parallelism(20,4));ComputeBudget.set(1);assertEquals(15,ComputeBudget.fps());assertNotNull(ComputeBudget.dispatcher());ComputeBudget.set(4)}
    @Test fun timeoutWithClosedSocketIsClassifiedAsTimeout(){val e=java.io.InterruptedIOException("timeout").apply{initCause(java.net.SocketException("Socket closed"))};assertEquals("TIMEOUT",DiagnosticFailure.category(e));assertTrue(SourceHealth.describe(e).startsWith("Zeitüberschreitung"))}
    @Test fun generalExportRemovesProfilesEvenFromEarlierDetailedRecords(){val input=JsonParser.parseString("""{"time":1,"level":"ERROR","event":"install.failed","message":"private","frames":["private"],"causes":[],"fields":{"package":"org.private.example","model":"private","status":4}}""").asJsonObject;val general=DiagnosticPrivacy.generalRecord(input);assertFalse(general.toString().contains("private"));assertEquals(4,general.getAsJsonObject("fields")["status"].asInt);assertTrue(input.toString().contains("private"))}
    @Test fun retentionCountChangesImmediatelyWithoutGrowingForever(){val dir=File(context.cacheDir,"quality-ring-${System.nanoTime()}");val store=DiagnosticStore(dir,512,10);repeat(100){store.append(DiagnosticLevel.ERROR,"fixture.$it",mapOf("detail" to "x".repeat(80)))};store.limitSegments(3);assertTrue(dir.listFiles().orEmpty().size<=3);assertTrue(store.size()<=3*512);dir.deleteRecursively()}
    @Test fun staleCacheFilesAreRemovedButPendingInstallerFilesRemain(){val d=File(context.cacheDir,"downloads").apply{mkdirs()};val stale=File(d,"stale").apply{writeText("data");setLastModified(System.currentTimeMillis()-2*ResourcePolicy.TEMP_AGE)};val pending=File(context.cacheDir,"external-installer/pending").apply{requireNotNull(parentFile).mkdirs();writeText("pending")};AppCache.maintain(context);assertFalse(stale.exists());assertTrue(pending.exists());pending.delete()}
    @Test fun activeCacheLeasePreventsMaintenanceDeletion(){val d=File(context.cacheDir,"downloads").apply{mkdirs()};val f=File(d,"active").apply{writeText("data");setLastModified(1)};AppCache.begin().use{AppCache.maintain(context);assertTrue(f.exists())};f.delete()}
    @Test fun apiCredentialsAreHostScopedAndNeverSentOverHttp(){val p=prefs();p.githubToken.put("test-secret");p.apkMirrorKey.put("mirror-secret");fun inspect(url:String):Request{val req=Request.Builder().url(url).build();var result=req;val chain=java.lang.reflect.Proxy.newProxyInstance(Interceptor.Chain::class.java.classLoader,arrayOf(Interceptor.Chain::class.java)){_,m,args->when(m.name){"request"->req;"proceed"->{result=args!![0] as Request;Response.Builder().request(result).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("".toResponseBody()).build()};else->error(m.name)}} as Interceptor.Chain;SourceCredentials{p}.intercept(chain).close();return result};assertEquals("Bearer test-secret",inspect("https://api.github.com/releases").header("Authorization"));assertEquals("Basic mirror-secret",inspect("https://www.apkmirror.com/wp-json/").header("Authorization"));assertNull(inspect("https://api.github.com.evil.example/").header("Authorization"));assertNull(inspect("http://api.github.com/").header("Authorization"))}
    @Test fun mirrorWebLinkUsesExactEncodedPackageQuery(){val u=ApkMirrorWeb.search("org.example.app");assertTrue(u.startsWith("https://www.apkmirror.com/"));assertTrue(u.contains("s=org.example.app"))}
    @Test fun firstPublicGithubCatalogAppIsNotAccidentallySkipped()=runBlocking {
        val first=org.uniapk.upgrader.data.github.GitHubApps.first();var calls=0
        val service=object:org.uniapk.upgrader.service.GitHubService{override suspend fun getReleases(user:String,repo:String):List<org.uniapk.upgrader.data.github.GitHubRelease>{calls++;return emptyList()}}
        org.uniapk.upgrader.repository.GitHubRepository(service,prefs()).updates(listOf(AppInstalled("Fixture",first.packageName,"1",1))).collect{}
        assertEquals(1,calls)
    }

    @Test fun keylessMirrorIsExplicitWebModeAndMakesNoApiCall()=runBlocking {
        var calls=0;val service=object:org.uniapk.upgrader.service.ApkMirrorService{override suspend fun appExists(request:org.uniapk.upgrader.data.apkmirror.AppExistsRequest):org.uniapk.upgrader.data.apkmirror.AppExistsResponse{calls++;return org.uniapk.upgrader.data.apkmirror.AppExistsResponse()}}
        SourceHealth.reset();org.uniapk.upgrader.repository.ApkMirrorRepository(service,prefs(),context.packageManager).updates(listOf(AppInstalled("Fixture","org.example","1",1))).collect{}
        assertEquals(0,calls);assertTrue(SourceHealth.flow.value.single().webOnly);assertNull(SourceHealth.flow.value.single().problem)
    }
    @Test fun generalCaptureDoesNotPersistArbitraryExceptionMessages(){val dir=File(context.cacheDir,"general-capture-${System.nanoTime()}");val store=DiagnosticStore(dir);store.append(DiagnosticLevel.ERROR,"fixture",error=IllegalStateException("private free text"),detailed=false);assertFalse(dir.walkTopDown().filter{it.isFile}.joinToString{it.readText()}.contains("private free text"));dir.deleteRecursively()}

    @Test fun automaticReceiptsDoNotDependOnEphemeralPlayClosuresButRespectDestinationAndRegion(){val one=AppUpdate("Fixture","org.example","2","1",2,1,PlaySource,link=Link.Play{emptyList()});val two=one.copy(link=Link.Play{emptyList()});val key=AutomaticDownloads.receiptKey(one,"oneplus15","IN","folder-a");assertEquals(key,AutomaticDownloads.receiptKey(two,"oneplus15","IN","folder-a"));assertNotEquals(key,AutomaticDownloads.receiptKey(two,"oneplus15","EU","folder-a"));assertNotEquals(key,AutomaticDownloads.receiptKey(two,"oneplus15","IN","folder-b"))}

    @Test fun mirrorWebFallbackCannotInheritAnotherSourcesFileApproval(){val original=AppUpdate("Fixture","org.example","2","1",2,1,GitHubSource,verdict=Verdict(Compatibility.COMPATIBLE,emptyList()),sha256="verified-hash",verifiedAt=1,fileReady=true,abis=setOf("arm64-v8a"),minSdk=23);val web=ApkMirrorWeb.option(original);assertEquals(ApkMirrorSource,web.source);assertEquals(Compatibility.UNKNOWN,web.verdict.status);assertNull(web.sha256);assertNull(web.verifiedAt);assertNull(web.abis);assertFalse(web.fileReady);assertNotEquals(original.artifactKey,web.artifactKey)}

}
