package org.uniapk.upgrader.settings

import android.app.Application
import com.google.gson.*
import com.kryptoprefs.preferences.KryptoBuilder
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.uniapk.upgrader.diagnostics.AppDiagnostics
import org.uniapk.upgrader.glass.*
import org.uniapk.upgrader.prefs.Prefs

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=Application::class)
class SettingsBackupTest {
 private val context get()=RuntimeEnvironment.getApplication()
 private fun prefs()=Prefs(KryptoBuilder.nocrypt(context,"backup-${java.util.UUID.randomUUID()}"),false).also{it.migrateDefaults();it.migratePalette()}
 private fun password()="Fixture-passphrase-ä🔒".toCharArray()
 private fun rejected(block:()->Unit){try{block();fail("Invalid backup accepted")}catch(_:IllegalArgumentException){}catch(_:BackupException){}catch(_:java.io.IOException){}}
 @Before fun initialize(){AppDiagnostics.initialize(context);AppDiagnostics.configure(enabled=false,verbose=false,maxFiles=5)}
 @Test fun everyPreferenceIsClassifiedSoNewSettingsCannotSilentlyDisappear(){val properties=Prefs::class.java.declaredFields.filter{!it.isSynthetic&&!java.lang.reflect.Modifier.isStatic(it.modifiers)&&it.name!="storage"}.map{it.name}.toSet();val definitions=SettingsRegistry(prefs()).definitions.map{it.name};assertEquals(definitions.size,definitions.toSet().size);assertEquals(properties,definitions.toSet()+SettingsRegistry.nonSettings)}
 @Test fun allSettingsRoundTripIncludingDesignListsAndCredentials(){val p=prefs();p.theme.put(3);p.glassOpacity.put(31);p.motionColor.put(0xff12ab34);p.motionDensity.put(79);p.dockStyle.put(4);p.cpuTasks.put(3);p.startView.put("apps");p.quickAccess.put(emptyList());p.favorites.put(listOf("org.example.favorite"));p.ignoredApps.put(listOf("org.example.ignored"));p.skippedReleases.put(listOf(SkippedRelease("org.example.skipped","2",200)));p.githubToken.put("TEST_ONLY_GITHUB_CREDENTIAL");p.apkMirrorKey.put("TEST_ONLY_MIRROR_CREDENTIAL")
  val document=SettingsBackup.snapshot(context,p);val cipher=BackupCodec.seal(document.toString().toByteArray(),password());assertFalse(cipher.toString(Charsets.ISO_8859_1).contains("TEST_ONLY_GITHUB_CREDENTIAL"));val q=prefs();val plain=BackupCodec.open(cipher,password());val preview=SettingsBackup.validate(BackupJson.parse(plain.toString(Charsets.UTF_8)),q);SettingsBackup.transaction(q,preview);assertEquals(document["settings"],SettingsRegistry(q).capture());assertEquals(2,preview.credentials)
 }
 @Test fun wrongPasswordAndCiphertextTamperingAreRejected(){val original=BackupCodec.seal("{\"fixture\":true}".toByteArray(),password());rejected{BackupCodec.open(original,"A different password".toCharArray())};val changed=original.clone();changed[changed.lastIndex]=(changed.last().toInt() xor 1).toByte();rejected{BackupCodec.open(changed,password())}}
 @Test fun headerAndIterationsAreAuthenticatedAndBounded(){val b=BackupCodec.seal("{}".toByteArray(),password());val changed=b.clone();changed[15]=(changed[15].toInt() xor 2).toByte();rejected{BackupCodec.open(changed,password())};val huge=b.clone();java.nio.ByteBuffer.wrap(huge).putInt(8,Int.MAX_VALUE);rejected{BackupCodec.open(huge,password())}}
 @Test fun randomizedSaltAndNoncePreventRepeatedCiphertexts(){val plain="same settings".toByteArray();val a=BackupCodec.seal(plain,password());val b=BackupCodec.seal(plain,password());assertFalse(a.contentEquals(b));assertArrayEquals(plain,BackupCodec.open(a,password()));assertArrayEquals(plain,BackupCodec.open(b,password()))}
 @Test fun modernAndLegacyPbkdf2ImplementationsMatchForUnicode(){val salt=ByteArray(16){it.toByte()};assertArrayEquals(BackupCodec.derive(password(),salt,1000,false),BackupCodec.derive(password(),salt,1000,true))}
 @Test fun pbkdf2MatchesPublishedSha256Vector(){val value=BackupCodec.derive("password".toCharArray(),"salt".toByteArray(),1,false).joinToString(""){"%02x".format(it)};assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",value)}
 @Test @Config(sdk=[23]) fun android6UsesPortableSha256Fallback(){val input="portable settings".toByteArray();val encrypted=BackupCodec.seal(input,password());assertArrayEquals(input,BackupCodec.open(encrypted,password()))}
 @Test fun oversizedAndTruncatedFilesAreRejectedBeforeKdf(){rejected{BackupCodec.open(ByteArray(8),password())};rejected{BackupCodec.read(ByteArray(BackupCodec.MAX_FILE+1).inputStream())}}
 @Test fun creationRequiresAStrongEnoughPassphrase(){rejected{BackupCodec.seal("{}".toByteArray(),"short".toCharArray())}}
 @Test fun duplicateJsonKeysTrailingDataAndDeepNestingAreRejected(){for(s in listOf("{\"a\":1,\"a\":2}","{} {}","{\"a\":".repeat(14)+"0"+"}".repeat(14))){rejected{BackupJson.parse(s)}}}
 @Test fun wrongAppOrSchemaCannotWriteAnyPreference(){val p=prefs();val before=SettingsRegistry(p).capture();for(key in listOf("application","format")){val d=SettingsBackup.snapshot(context,p);if(key=="format")d.addProperty(key,99)else d.addProperty(key,"org.example.other");rejected{SettingsBackup.transaction(p,BackupPreview(d,0,0,"",0,false))};assertEquals(before,SettingsRegistry(p).capture());assertEquals("",p.restoreJournal.get())}}
 @Test fun unknownMissingOrMalformedValuesAreRejectedBeforeWrites(){val p=prefs();val initial=SettingsBackup.snapshot(context,p);for(change in 0..3){val d=initial.deepCopy();val values=d.getAsJsonObject("settings");when(change){0->values.addProperty("unknownSetting",true);1->values.remove("theme");2->values.addProperty("glassOpacity",101);else->values.addProperty("compactMode","true")};rejected{SettingsBackup.validate(d,p)}}}
 @Test fun dangerousOrInvalidImportedOptionsAreRejected(){val p=prefs();for((key,value) in listOf("rootInstall" to JsonPrimitive(true),"autoVerify" to JsonPrimitive(true),"installerPackage" to JsonPrimitive("https://invalid.example/installer"),"downloadTree" to JsonPrimitive("file:///storage/private"),"cpuTasks" to JsonPrimitive(999999))){val d=SettingsBackup.snapshot(context,p);d.getAsJsonObject("settings").add(key,value);rejected{SettingsBackup.validate(d,p)}}}
 @Test fun listsAndProfileMappingsAreValidated(){val p=prefs();val d=SettingsBackup.snapshot(context,p);d.getAsJsonObject("settings").add("favorites",JsonArray().apply{add("../../bad")});rejected{SettingsBackup.validate(d,p)};val e=SettingsBackup.snapshot(context,p);e.getAsJsonObject("settings").addProperty("simulatedPreset","unknown-preset");rejected{SettingsBackup.validate(e,p)}}
 @Test fun runtimeSessionsFilesAndHistoryAreNotRestoredAsSettings(){val p=prefs();p.lastCheck.put(42);val doc=SettingsBackup.snapshot(context,p);val text=doc.toString();for(name in listOf("playAuthData","lastCheck","readyRecords","updateHistory","restoreJournal","pendingInstalls","downloadArchive"))assertFalse(name,text.contains('"'+name+'"'));p.lastCheck.put(99);SettingsBackup.transaction(p,SettingsBackup.validate(doc,p));assertEquals(99L,p.lastCheck.get())}
 @Test fun simulatedWriteFailureRollsAllPreferencesBack(){val p=prefs();val before=SettingsRegistry(p).capture();val d=SettingsBackup.snapshot(context,p);d.getAsJsonObject("settings").addProperty("startView","apps");d.getAsJsonObject("settings").addProperty("paletteSeed",0xff7722aaL);d.getAsJsonObject("settings").addProperty("githubToken","TEST_ONLY_NEW_CREDENTIAL");try{SettingsBackup.transaction(p,SettingsBackup.validate(d,p)){if(it==18)throw java.io.IOException("Injected write failure")};fail("Failure must propagate")}catch(_:BackupException){};assertEquals(before,SettingsRegistry(p).capture());assertEquals("",p.restoreJournal.get())}
 @Test fun unfinishedTransactionIsRecoveredBeforeNextStartup(){val p=prefs();val before=SettingsBackup.snapshot(context,p);p.restoreJournal.put(before.toString());p.flushSettings();p.theme.put(2);p.favorites.put(listOf("org.example.uncommitted"));SettingsBackup.recover(p);assertEquals(before["settings"],SettingsRegistry(p).capture());assertEquals("",p.restoreJournal.get())}
 @Test fun diagnosticSettingsAreRestoredWithTheConfiguration(){val p=prefs();AppDiagnostics.configure(enabled=false,verbose=true,maxFiles=3);val snapshot=SettingsBackup.snapshot(context,p);AppDiagnostics.configure(enabled=true,verbose=false,maxFiles=10);SettingsBackup.transaction(p,SettingsBackup.validate(snapshot,p));assertFalse(AppDiagnostics.state.value.enabled);assertTrue(AppDiagnostics.state.value.verbose);assertEquals(3,AppDiagnostics.state.value.maxFiles)}
 @Test fun decodedPreviewCannotBypassValidationWhenModifiedLater(){val p=prefs();val preview=SettingsBackup.validate(SettingsBackup.snapshot(context,p),p);preview.document.getAsJsonObject("settings").addProperty("effectLevel",999);rejected{SettingsBackup.transaction(p,preview)};assertEquals(2,p.effectLevel.get())}
}
