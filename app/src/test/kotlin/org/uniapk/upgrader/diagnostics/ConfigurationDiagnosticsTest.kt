package org.uniapk.upgrader.diagnostics

import android.app.Application
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Looper
import com.google.gson.*
import com.kryptoprefs.preferences.KryptoBuilder
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.uniapk.upgrader.glass.*
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.settings.*
import java.io.File
import java.time.Duration
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=Application::class)
class ConfigurationDiagnosticsTest {
 private val c get()=RuntimeEnvironment.getApplication()
 private lateinit var p:Prefs
 private lateinit var provider:BackupTestProvider
 private val uri=Uri.parse("content://org.example.backup/diagnostics")
 @Before fun initialize()=runBlocking{
  AppDiagnostics.initialize(c);AppDiagnostics.configure(enabled=true,verbose=false,maxFiles=5);AppDiagnostics.clear()
  p=Prefs(KryptoBuilder.nocrypt(c,"config-diagnostics-${java.util.UUID.randomUUID()}"),false).also{it.migrateDefaults();it.migratePalette()}
  provider=BackupTestProvider().apply{file=File(c.cacheDir,"diagnostic-fixture.zip");attachInfo(c,ProviderInfo().apply{authority="org.example.backup"})}
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("org.example.backup",provider)
  AppDiagnostics.observeSettings(p)
 }
 private fun exported(kind:DiagnosticExport):Map<String,String> =runBlocking{AppDiagnostics.export(c,uri,kind);ZipFile(provider.file).use{z->z.entries().asSequence().associate{it.name to z.getInputStream(it).reader().readText()}}}
 @Test fun bothExportsContainCurrentMachineReadableConfigurationAndRuntime(){for(kind in DiagnosticExport.entries){AppDiagnostics.configure(verbose=kind==DiagnosticExport.FULL);p.glassOpacity.put(37);p.theme.put(3);p.motionDensity.put(78);val zip=exported(kind);assertTrue(zip.containsKey("settings.json"));assertTrue(zip.containsKey("runtime.json"));val root=JsonParser.parseString(zip.getValue("settings.json")).asJsonObject;val appearance=root.getAsJsonObject("settings").getAsJsonObject("appearance");assertEquals(37,appearance["glassOpacity"].asInt);assertEquals(78,appearance["motionDensity"].asInt);assertEquals("Dusk",root.getAsJsonObject("readableState")["theme"].asString)}}
 @Test fun credentialsAndPrivateFolderUrisNeverAppearInEitherDiagnostic(){p.githubToken.put("TEST_ONLY_GITHUB_SECRET_123");p.apkMirrorKey.put("TEST_ONLY_MIRROR_SECRET_123");p.downloadTree.put("content://org.example.documents/tree/PrivatePersonalFolder");for(kind in DiagnosticExport.entries){AppDiagnostics.configure(verbose=kind==DiagnosticExport.FULL);val zip=exported(kind);val all=zip.values.joinToString();assertFalse(all.contains("TEST_ONLY_GITHUB_SECRET_123"));assertFalse(all.contains("TEST_ONLY_MIRROR_SECRET_123"));assertFalse(all.contains("PrivatePersonalFolder"));val sources=JsonParser.parseString(zip.getValue("settings.json")).asJsonObject.getAsJsonObject("settings").getAsJsonObject("sources");assertTrue(sources["githubAccessConfigured"].asBoolean);assertTrue(sources["apkMirrorAccessConfigured"].asBoolean)}}
 @Test fun generalExportRemovesPrivateListsEvenFromPreviouslyDetailedEvents(){AppDiagnostics.configure(verbose=true);p.favorites.put(listOf("org.example.privatefavorite"));p.ignoredApps.put(listOf("org.example.privateignored"));p.skippedReleases.put(listOf(SkippedRelease("org.example.privateskipped","2",2)));AppDiagnostics.captureSettings("fixture_detailed",force=true);AppDiagnostics.configure(verbose=false);val all=exported(DiagnosticExport.SHORT).values.joinToString();for(name in listOf("org.example.privatefavorite","org.example.privateignored","org.example.privateskipped"))assertFalse(name,all.contains(name));assertTrue(all.contains("favoritesCount"))}
 @Test fun detailedExportIncludesOnlySelectedListsAndMarksHistoryTruncation(){AppDiagnostics.configure(verbose=true);p.favorites.put((1..15).map{"org.example.favorite$it"});AppDiagnostics.captureSettings("fixture_lists",true);val zip=exported(DiagnosticExport.FULL);val root=JsonParser.parseString(zip.getValue("settings.json")).asJsonObject;assertEquals(15,root.getAsJsonObject("settings").getAsJsonObject("lists").getAsJsonArray("favorites").size());val lines=zip.getValue("diagnostics-full.jsonl").lines().filter{it.isNotBlank()}.map{JsonParser.parseString(it).asJsonObject};assertTrue(lines.any{it["event"].asString=="settings.snapshot"&&it.getAsJsonObject("fields")["section"]?.asString=="lists"&&it.getAsJsonObject("fields").getAsJsonObject("values")["favoritesTruncated"]?.asBoolean==true})}
 @Test fun disabledRecordingStillAllowsAnExplicitCurrentSnapshot(){AppDiagnostics.configure(enabled=false);p.compactMode.put(true);val zip=exported(DiagnosticExport.SHORT);val root=JsonParser.parseString(zip.getValue("settings.json")).asJsonObject;assertTrue(root.getAsJsonObject("settings").getAsJsonObject("appearance")["compactMode"].asBoolean);assertFalse(root.getAsJsonObject("diagnostics")["enabled"].asBoolean)}
 @Test fun configurationChangesAreTimestampedAndGrouped(){p.glassOpacity.put(22);p.flushSettings();shadowOf(Looper.getMainLooper()).idle();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));val zip=exported(DiagnosticExport.FULL);val lines=zip.getValue("diagnostics-full.jsonl").lines().filter{it.isNotBlank()}.map{JsonParser.parseString(it).asJsonObject};val events=lines.filter{it["event"].asString=="settings.snapshot"&&it.getAsJsonObject("fields")["reason"]?.asString=="changed"};assertTrue("Change observer did not capture a configuration checkpoint",events.isNotEmpty());assertTrue(events.all{it.getAsJsonObject("fields").has("configurationId")&&it.getAsJsonObject("fields").has("capturedAt")});assertTrue(events.any{it.getAsJsonObject("fields")["section"].asString=="appearance"&&it.getAsJsonObject("fields").getAsJsonObject("values")["glassOpacity"].asInt==22})}
 @Test fun nestedStructuredFieldsAreRedactedRecursivelyAndKeepTheirTypes(){val fields=JsonObject().apply{addProperty("enabled",true);addProperty("count",3);add("inner",JsonObject().apply{addProperty("password","PRIVATE_PASSWORD");addProperty("packageName","org.example.private")})};val safe=DiagnosticRedaction.json(fields,false).asJsonObject;assertTrue(safe["enabled"].asJsonPrimitive.isBoolean);assertEquals(3,safe["count"].asInt);assertEquals("[redacted]",safe.getAsJsonObject("inner")["password"].asString);assertFalse(safe.getAsJsonObject("inner").has("packageName"))}
 @Test fun runtimeExplainsOperationWithoutExposingPackagesInGeneralMode(){DiagnosticRuntime.process=PackageProcess(org.uniapk.upgrader.data.ui.AppUpdate("Fixture","org.example.operation","2","1",2,1,org.uniapk.upgrader.data.ui.GitHubSource),PackagePhase.DOWNLOAD,42,100);try{val basic=DiagnosticSnapshot.runtime(c,p,false);assertEquals(42L,basic.getAsJsonObject("operation")["bytes"].asLong);assertFalse(basic.toString().contains("org.example.operation"));val detailed=DiagnosticSnapshot.runtime(c,p,true);assertEquals("org.example.operation",detailed.getAsJsonObject("operation")["packageName"].asString)}finally{DiagnosticRuntime.process=null}}
 @Test fun encryptedBackupAndDiagnosticExportStaySeparate(){val doc=SettingsBackup.snapshot(c,p);p.gitlabToken.put("TEST_ONLY_RESTORED_CREDENTIAL");val current=SettingsBackup.snapshot(c,p);val bytes=BackupCodec.seal(current.toString().toByteArray(),"Test-passphrase-2026".toCharArray());assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("TEST_ONLY_RESTORED_CREDENTIAL"));val zip=exported(DiagnosticExport.FULL);assertFalse(zip.values.joinToString().contains("TEST_ONLY_RESTORED_CREDENTIAL"));assertFalse(zip.containsKey("backup.json"));assertTrue(doc.getAsJsonObject("settings").has("gitlabToken"))}
}
