package com.apkupdater.glass
import com.apkupdater.data.ui.*
import com.apkupdater.prefs.Prefs
import com.apkupdater.data.apkmirror.*
import com.apkupdater.repository.ApkMirrorRepository
import com.apkupdater.service.ApkMirrorService
import com.kryptoprefs.preferences.KryptoBuilder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=android.app.Application::class)
class StoreFeaturesTest {
 private lateinit var prefs:Prefs
 @Before fun setup(){prefs=Prefs(KryptoBuilder.nocrypt(RuntimeEnvironment.getApplication(),"store-features-test"),false)}
 private fun app(i:Int=0,bytes:Long=0,status:Compatibility=Compatibility.UNKNOWN)=AppUpdate("App $i","com.example.app$i","2","1",2,1,ApkPureSource,link=Link.Url("https://example.org/$i.apk",bytes),verdict=Verdict(status,emptyList()))
 @Test fun budgetBoundsUnknownFileSizes(){assertEquals(2,AutoCheckPolicy.plan((0..10).map{app(it)},maxBytes=128,maxApps=12,budgetBytes=256).size)}
 @Test fun knownSizesUseAggregateBudget(){assertEquals(4,AutoCheckPolicy.plan((0..10).map{app(it,64)},maxBytes=128,maxApps=12,budgetBytes=256).size)}
 @Test fun manualAllChecksAreNotLimitedToSixOrTwelve(){assertEquals(30,AutoCheckPolicy.plan((0..29).map{app(it)},maxBytes=512,maxApps=Int.MAX_VALUE,retryErrors=true).size)}
 @Test fun countLimitIsRespected(){assertEquals(3,AutoCheckPolicy.plan((0..20).map{app(it)},maxBytes=128,maxApps=3).size)}
 @Test fun manualCanRetryErrors(){assertEquals(1,AutoCheckPolicy.plan(listOf(app().copy(verificationError="DNS")),maxBytes=512,maxApps=Int.MAX_VALUE,retryErrors=true).size);assertTrue(AutoCheckPolicy.plan(listOf(app().copy(verificationError="DNS")),maxBytes=512).isEmpty())}
 @Test fun blockedNeverEntersBatch(){assertTrue(AutoCheckPolicy.plan(listOf(app(status=Compatibility.INCOMPATIBLE)),maxBytes=512,maxApps=Int.MAX_VALUE,retryErrors=true).isEmpty())}
 @Test fun favoritesPersistAcrossStoreInstances(){val f=FavoriteStore(prefs);f.toggle("com.example");assertTrue("com.example" in FavoriteStore(prefs).state.value);f.toggle("com.example");assertTrue(f.state.value.isEmpty())}
 @Test fun historyPersistsAndClearDoesNotChangeFavorites(){prefs.favorites.put(listOf("com.example"));val h=HistoryStore(prefs);h.record(InstallHistory("com.example","Example","1","2",1,2,"GitHub","Android",1,true));assertEquals(1,HistoryStore(prefs).state.value.size);h.clear();assertEquals(listOf("com.example"),prefs.favorites.get());assertTrue(h.state.value.isEmpty())}
 @Test fun externalPendingIsNotSuccessBeforeVersionChange(){val h=HistoryStore(prefs);h.pending(PendingInstall("not.installed","Unknown","1","2",1,2,"GitHub",System.currentTimeMillis()));h.reconcile(RuntimeEnvironment.getApplication());assertTrue(h.state.value.isEmpty());assertEquals(1,prefs.pendingInstalls.get().size)}
 @Test fun profileOverridesCannotChangeInstallationFacts(){val actual=DeviceProfile(37,setOf("arm64-v8a"),Region.IN);assertEquals(actual,ProfilePolicy.actualProfile(actual,"CN","Other model"))}
 @Test fun compatibleSystemAppStillHasElevatedRisk(){assertEquals("Erhöht",RiskPolicy.risk(app(status=Compatibility.COMPATIBLE).copy(system=true)).level)}
 @Test fun ordinaryCompatibleAppDoesNotPromiseNoRisk(){assertEquals("Restrisiko",RiskPolicy.risk(app(status=Compatibility.COMPATIBLE)).level)}
 @Test fun unknownNeverBecomesVerifiedBySource(){assertEquals("Ungeklärt",RiskPolicy.risk(app().copy(source=GitHubSource)).level)}
 @Test fun dnsAnd403HaveDifferentAdvice(){assertTrue(NetworkDiagnostics.hints("DNS-Fehler").contains("privates DNS"));assertTrue(NetworkDiagnostics.hints("HTTP 403").contains("nicht umgangen"))}
 @Test fun animationsStopForAccessibilityBackgroundAndPowerSave(){assertFalse(MotionPolicy.active(false,false,true,false));assertFalse(MotionPolicy.active(true,true,true,false));assertFalse(MotionPolicy.active(true,false,false,false));assertFalse(MotionPolicy.active(true,false,true,true));assertTrue(MotionPolicy.active(true,false,true,false))}
 @Test fun randomAnimationIsValidAndSeasonalIsLocal(){assertNotEquals(MotionMode.RANDOM,MotionPolicy.mode(10,false,7,12));assertEquals(MotionMode.SNOW,MotionPolicy.mode(0,true,12,1));assertEquals("Frohes neues Jahr",MotionPolicy.greeting(1,1))}
 @Test fun allScreenshotAnimationModesExist(){assertEquals(listOf("Keine","Kreise","Ringe","Netz","Weltraum","Formen","Schnee","Raster","Partikel","Lavalampe","Zufällig"),MotionMode.entries.map{it.label})}
 @Test fun defaultSourceFlagsAreOn(){assertTrue(prefs.useApkMirror.get());assertTrue(prefs.usePlay.get());assertTrue(prefs.useFdroid.get());assertTrue(prefs.useGitHub.get())}
 @Test fun apkmirrorRetriesDnsOnlyOnce()=runBlocking {
  var calls=0
  val service=object:ApkMirrorService {override suspend fun appExists(request:AppExistsRequest):AppExistsResponse{calls++;if(calls==1)throw java.net.UnknownHostException();return AppExistsResponse()}}
  SourceHealth.reset();val repo=ApkMirrorRepository(service,prefs,RuntimeEnvironment.getApplication().packageManager)
  repo.updates(listOf(AppInstalled("Example","com.example","1",1))).toList()
  assertEquals(2,calls);assertFalse(SourceHealth.flow.value.any{it.problem!=null})
 }
 @Test fun apkmirrorDoesNotRetryForbidden()=runBlocking {
  var calls=0
  val service=object:ApkMirrorService {override suspend fun appExists(request:AppExistsRequest):AppExistsResponse{calls++;throw DownloadHttpException(403)}}
  SourceHealth.reset();ApkMirrorRepository(service,prefs,RuntimeEnvironment.getApplication().packageManager).updates(listOf(AppInstalled("Example","com.example","1",1))).toList()
  assertEquals(1,calls);assertTrue(SourceHealth.flow.value.any{it.problem?.contains("403")==true})
 }
 @Test fun networkStatePermissionIsDeclared(){val c=RuntimeEnvironment.getApplication();@Suppress("DEPRECATION") val info=c.packageManager.getPackageInfo(c.packageName,android.content.pm.PackageManager.GET_PERMISSIONS);assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.ACCESS_NETWORK_STATE"))}
 @Test fun externalHistoryRequiresMatchingObservedVersion(){
  val c=RuntimeEnvironment.getApplication();val pkg=android.content.pm.PackageInfo().apply{packageName="com.example.pending";longVersionCode=2;versionName="2";applicationInfo=android.content.pm.ApplicationInfo().apply{packageName="com.example.pending"}}
  org.robolectric.Shadows.shadowOf(c.packageManager).installPackage(pkg)
  val h=HistoryStore(prefs);h.pending(PendingInstall(pkg.packageName,"Pending","1","2",1,2,"GitHub",System.currentTimeMillis()));h.reconcile(c)
  assertEquals(1,h.state.value.size);assertTrue(h.state.value.single().installer.contains("Paketstand bestätigt"));assertTrue(prefs.pendingInstalls.get().isEmpty())
 }
 @Test fun anotherObservedVersionDoesNotClaimHandoffSuccess(){
  val c=RuntimeEnvironment.getApplication();val pkg=android.content.pm.PackageInfo().apply{packageName="com.example.pending";longVersionCode=3;applicationInfo=android.content.pm.ApplicationInfo().apply{packageName="com.example.pending"}}
  org.robolectric.Shadows.shadowOf(c.packageManager).installPackage(pkg)
  val h=HistoryStore(prefs);h.pending(PendingInstall(pkg.packageName,"Pending","1","2",1,2,"GitHub",System.currentTimeMillis()));h.reconcile(c)
  assertTrue(h.state.value.isEmpty());assertEquals(1,prefs.pendingInstalls.get().size)
 }

 @Test fun differentUpdateOwnerRaisesRiskWithoutGrantingCompatibility(){val u=app(status=Compatibility.COMPATIBLE).copy(updateOwner="com.android.vending");assertEquals("Erhöht",RiskPolicy.risk(u).level);assertTrue(u.verdict.installAllowed)}

}
