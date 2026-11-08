package org.uniapk.upgrader.glass

import android.app.Application
import android.content.*
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import com.kryptoprefs.preferences.KryptoBuilder
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.prefs.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

class FolderTestProvider:ContentProvider(){
    var writable=true;var missing=false;var denied=false
    override fun onCreate()=true
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):android.database.Cursor {
        if(denied)throw SecurityException("Fixture revoked")
        val columns=projection ?: arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return MatrixCursor(columns).apply{if(!missing)addRow(columns.map<String,Any?>{when(it){DocumentsContract.Document.COLUMN_DISPLAY_NAME->"APK Updates";DocumentsContract.Document.COLUMN_FLAGS->if(writable)DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else 0;DocumentsContract.Document.COLUMN_MIME_TYPE->DocumentsContract.Document.MIME_TYPE_DIR;else->null}}.toTypedArray())}
    }
    override fun getType(uri:Uri)=DocumentsContract.Document.MIME_TYPE_DIR
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
    override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
}
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[37],application=Application::class)
class RC2UsabilityTest{
    private val context get()=RuntimeEnvironment.getApplication()
    private fun prefs()=Prefs(KryptoBuilder.nocrypt(context,"usability-${System.nanoTime()}"),false)
    private fun update(pkg:String="org.example",system:Boolean=false)=AppUpdate("Fixture",pkg,"2","1",2,1,GitHubSource,system=system)
    private fun ready(u:AppUpdate)=u.copy(fileReady=true,verifiedAt=1,sha256="hash",verdict=Verdict(Compatibility.COMPATIBLE,emptyList()))
    private fun folderProvider()=FolderTestProvider().also{it.attachInfo(context,android.content.pm.ProviderInfo().apply{authority="com.android.externalstorage.documents"});ShadowContentResolver.registerProviderInternal("com.android.externalstorage.documents",it)}
    private fun archiveFixture():Triple<Prefs,DownloadArchive,ArchiveTestProvider>{val provider=ArchiveTestProvider().also{it.attachInfo(context,android.content.pm.ProviderInfo().apply{authority="media"});ShadowContentResolver.registerProviderInternal("media",it)};val p=prefs();return Triple(p,DownloadArchive(context,p),provider)}
    @Test fun folderLocationShowsActualSubdirectory(){assertEquals("Interner Speicher / Download/APK Updates",FolderAccess.location(Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FAPK%20Updates"),"APK Updates"))}
    @Test fun defaultFolderDoesNotPretendToBeExplicitlyChosen(){val s=FolderAccess.status(context,prefs());assertFalse(s.selected);assertFalse(s.writable);assertTrue(s.location.contains("UniAPK"))}
    @Test fun writableFolderSelectionPersistsNameAndAccess(){folderProvider();val p=prefs();val uri=Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FAPK");val s=FolderAccess.select(context,p,uri);assertTrue(s.writable);assertEquals("APK Updates",s.name);assertEquals(uri.toString(),p.downloadTree.get());assertTrue(DownloadFolderPolicy.valid(context,p))}
    @Test fun readOnlyFolderDoesNotReplacePreviousChoice(){folderProvider().writable=false;val p=prefs();p.downloadTree.put("previous");try{FolderAccess.select(context,p,Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ARO"));fail()}catch(_:IllegalStateException){};assertEquals("previous",p.downloadTree.get())}
    @Test fun revokedFolderIsNotReportedAsWritable(){val provider=folderProvider();val p=prefs();FolderAccess.select(context,p,Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FAPK"));provider.denied=true;val s=FolderAccess.status(context,p);assertTrue(s.selected);assertFalse(s.writable);assertTrue(s.location.contains("Download/APK"))}
    @Test fun lowerRiskRequiresActuallyPreparedBytes(){assertEquals(RiskLevel.ELEVATED,UpdateRisk.assess(update()).level);assertEquals(RiskLevel.LOWER,UpdateRisk.assess(ready(update())).level)}
    @Test fun googleUserAppIsNotAChangedSystemCategory(){assertEquals("Allgemeine App",UpdateRisk.assess(update("com.google.android.apps.photos")).category);assertEquals("Google-System-App",UpdateRisk.assess(update("com.google.android.apps.photos",true)).category)}
    @Test fun coreServicesRequireRiskAcknowledgement(){val r=UpdateRisk.assess(ready(update("com.google.android.gms",true)));assertEquals(RiskLevel.HIGH,r.level);assertTrue(r.requiresConfirmation)}
    @Test fun vendorSystemComponentsHaveHigherRiskThanOrdinaryVendorApps(){assertEquals(RiskLevel.HIGH,UpdateRisk.assess(ready(update("com.oplus.fixture",true))).level);assertEquals(RiskLevel.LOWER,UpdateRisk.assess(ready(update("com.oplus.fixture"))).level)}
    @Test fun riskAcknowledgementCannotOverrideIncompatibility(){val r=UpdateRisk.assess(ready(update("com.android.systemui",true)).copy(verdict=Verdict(Compatibility.INCOMPATIBLE,listOf(Evidence("Signatur","Passt nicht",Compatibility.INCOMPATIBLE)))));assertEquals(RiskLevel.BLOCKED,r.level);assertFalse(r.requiresConfirmation)}
    @Test fun prereleasesRemainElevatedWhenTechnicallyCompatible(){assertEquals(RiskLevel.ELEVATED,UpdateRisk.assess(ready(update().copy(version="2-beta1",prerelease=true))).level)}
    @Test fun discardedUpdateReturnsToFindingsWithStableIdentity(){val original=ready(update());val reset=PreparedUpdatePolicy.reset(original,"Removed");assertEquals(Compatibility.UNKNOWN,reset.verdict.status);assertFalse(reset.fileReady);assertNull(reset.sha256);assertNull(reset.verifiedAt);assertEquals(original.artifactKey,reset.artifactKey);assertEquals(original.version,reset.version)}
    @Test fun densityControlsAllConcreteEffects(){for(m in MotionMode.entries.filter{it!=MotionMode.NONE&&it!=MotionMode.RANDOM}){assertEquals(m.name,0,MotionPolicy.count(m,0f));assertTrue(m.name,MotionPolicy.count(m,1f)>MotionPolicy.count(m,.1f))}}
    @Test fun matrixIsAppendedWithoutReassigningSavedEffects(){assertEquals(15,MotionMode.CONFETTI.ordinal);assertEquals(16,MotionMode.MATRIX.ordinal)}
    @Test fun colorsAndProgressStylePersistAndReset(){val p=prefs();val store=AppearanceStore(p);store.update{it.copy(motionPalette=2,motionColor=0xffff0000,motionSecondary=0xff0000ff,progressStyle=ProgressStyle.WAVE.ordinal)};val a=AppearanceStore(p).state.value;assertEquals(2,a.motionPalette);assertEquals(0xffff0000L,a.motionColor);assertEquals(ProgressStyle.WAVE.ordinal,a.progressStyle);store.reset();assertEquals(Appearance(),AppearanceStore(p).state.value)}
    @Test fun unknownOrInvalidProgressNeverProducesAFakePercentage(){assertNull(ProgressPolicy.fraction(null));assertNull(ProgressPolicy.fraction(Float.NaN));assertNull(ProgressPolicy.fraction(Float.POSITIVE_INFINITY));assertEquals(0f,ProgressPolicy.fraction(-.5f));assertEquals(1f,ProgressPolicy.fraction(2f));assertEquals(ProgressStyle.LINE,ProgressPolicy.style(999))}
    @Test fun removingManagedCopyPreservesInputAndClearsReceipt(){val(p,a,provider)=archiveFixture();val input=File(context.cacheDir,"prepared.apk").apply{writeText("fixture")};val uri=a.publish(input,"org.example","2");val hash=p.downloadArchive.get().keys.single();p.autoDownloadReceipts.put(mapOf("receipt" to hash));assertEquals(ArchivePresence.PRESENT,a.presence(hash));assertTrue(a.removeDigest(hash));assertFalse(provider.files.containsKey(uri));assertTrue(input.exists());assertTrue(p.autoDownloadReceipts.get().isEmpty());assertTrue(p.downloadArchive.get().isEmpty())}
    @Test fun externalDeletionIsRecognisedFromMetadata(){val(p,a,provider)=archiveFixture();val input=File(context.cacheDir,"external.apk").apply{writeText("fixture")};val uri=a.publish(input,"org.example","2");val hash=p.downloadArchive.get().keys.single();provider.delete(uri,null,null);assertEquals(ArchivePresence.MISSING,a.presence(hash));assertTrue(a.removeDigest(hash));assertTrue(p.downloadArchive.get().isEmpty())}
    @Test fun revokedAccessIsNotTreatedAsFileDeletion(){val(p,a,provider)=archiveFixture();val input=File(context.cacheDir,"revoked.apk").apply{writeText("fixture")};a.publish(input,"org.example","2");val hash=p.downloadArchive.get().keys.single();provider.denied=true;assertEquals(ArchivePresence.UNAVAILABLE,a.presence(hash));assertFalse(a.removeDigest(hash));assertTrue(p.downloadArchive.get().containsKey(hash))}
    @Test fun localReportIncludesSystemRiskWithoutRawStatusEnums(){val report=ApkAnalyzer.Report(Candidate("com.google.android.gms",2,versionName="2"),Verdict(Compatibility.COMPATIBLE,emptyList()),"hash",emptySet(),37,null,system=true);val text=report.text();assertTrue(text.contains("Hohes Risiko"));assertTrue(text.contains("Google-System-App"));assertFalse(text.contains("COMPATIBLE"))}
    @Test fun localReportKeepsUnknownEvidenceUnknown(){val report=ApkAnalyzer.Report(Candidate("org.example",2,versionName="2"),Verdict(Compatibility.UNKNOWN,listOf(Evidence("Signatur","Ungeklärt",Compatibility.UNKNOWN))),"hash",emptySet(),37,null);assertTrue(report.text().contains("Noch offen"));assertTrue(report.text().contains("APK noch nicht vollständig geprüft."))}

}
