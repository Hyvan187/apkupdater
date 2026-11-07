package com.apkupdater.glass

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.apkupdater.data.ui.Link
import com.apkupdater.prefs.Prefs
import com.apkupdater.util.Downloader
import com.aurora.gplayapi.data.models.PlayFile
import com.kryptoprefs.preferences.KryptoBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=Application::class)
class PlayDownloadTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun bytes(name:String)=checkNotNull(javaClass.getResourceAsStream("/apksets/$name")).use { it.readBytes() }
    private fun inspector(): ApkSetInspector {
        val detection=DeviceDetection(context,Prefs(KryptoBuilder.nocrypt(context,"play-set-test"),false))
        val analyzer=ApkAnalyzer(context,detection) { _,_ -> PackageInfo().apply {
            packageName="org.example.apkset";versionName="2.0";longVersionCode=2
            applicationInfo=ApplicationInfo().apply { packageName="org.example.apkset";minSdkVersion=23;targetSdkVersion=37 }
        } }
        return ApkSetInspector(context,detection,analyzer)
    }
    private fun part(name:String,type:PlayFile.Type)=PlayFile(name=name,type=type,url="https://fixture.invalid/$name",size=bytes(name).size.toLong())
    private fun downloader(dir:File,request:(String)->ByteArray):Downloader {
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(request(chain.request().url.encodedPath.removePrefix("/")).toResponseBody()).build()
        }.build()
        return Downloader(client,client,client,dir)
    }
    @Test fun onlyExplicitDownloadResolvesPlayAndDownloadsEntireSignedSet()=runBlocking {
        val dir=File(context.cacheDir,"play-test").apply{mkdirs()};var lazyCalls=0;var requests=0
        val link=Link.Play { lazyCalls++;listOf(part("base.apk",PlayFile.Type.BASE),part("feature.apk",PlayFile.Type.SPLIT)) }
        assertEquals(0,lazyCalls)
        val archive=downloader(dir) { requests++;bytes(it) }.download(link,com.apkupdater.util.DownloadTrigger.SINGLE_CHECK)
        try {
            assertEquals(1,lazyCalls);assertEquals(2,requests)
            ZipFile(archive).use { assertEquals(listOf("base.apk","split-1.apk"),it.entries().asSequence().map { e->e.name }.toList()) }
            assertTrue(inspector().inspect(archive,"org.example.apkset").verdict.installAllowed)
            assertEquals(1,dir.listFiles().orEmpty().size)
        } finally { dir.deleteRecursively() }
    }
    @Test fun loneRequiredBaseCannotPassInspection()=runBlocking {
        val dir=File(context.cacheDir,"play-test").apply{mkdirs()}
        val archive=downloader(dir,::bytes).download(Link.Play { listOf(part("requires-feature.apk",PlayFile.Type.BASE)) },com.apkupdater.util.DownloadTrigger.SINGLE_CHECK)
        try { assertFalse(inspector().inspect(archive,"org.example.apkset").verdict.installAllowed) }
        finally { dir.deleteRecursively() }
    }
    @Test fun mismatchedSetSignaturesAreRejectedAfterActualDownload()=runBlocking {
        val dir=File(context.cacheDir,"play-test").apply{mkdirs()}
        val archive=downloader(dir,::bytes).download(Link.Play { listOf(part("base.apk",PlayFile.Type.BASE),part("wrong-signer.apk",PlayFile.Type.SPLIT)) },com.apkupdater.util.DownloadTrigger.SINGLE_CHECK)
        try { try { inspector().inspect(archive,"org.example.apkset");fail("Mixed signatures accepted") } catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("Signierer"))} }
        finally { dir.deleteRecursively() }
    }
    @Test fun duplicateBasesAreRejectedBeforeAnyBytesAreRequested()=runBlocking {
        val dir=File(context.cacheDir,"play-test").apply{mkdirs()};var requests=0
        try {
            try { downloader(dir){ requests++;bytes(it) }.download(Link.Play { listOf(part("base.apk",PlayFile.Type.BASE),part("base.apk",PlayFile.Type.BASE)) },com.apkupdater.util.DownloadTrigger.SINGLE_CHECK);fail("Duplicate bases accepted") } catch(_:IllegalArgumentException){}
            assertEquals(0,requests);assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun aggregateSizeLimitRemovesEveryTemporaryPart()=runBlocking {
        val dir=File(context.cacheDir,"play-test").apply{mkdirs()}
        try {
            try { downloader(dir,::bytes).download(Link.Play { listOf(part("base.apk",PlayFile.Type.BASE),part("feature.apk",PlayFile.Type.SPLIT)) },com.apkupdater.util.DownloadTrigger.SINGLE_CHECK,bytes("base.apk").size.toLong()+20);fail("Aggregate size limit ignored") } catch(_:AutomaticSizeLimit){}
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun truncatedPlayPartIsRejectedAndRemoved()=runBlocking {
        val dir=File(context.cacheDir,"play-test").apply{mkdirs()}
        try {
            try { downloader(dir){ bytes(it).copyOf(20) }.download(Link.Play { listOf(part("base.apk",PlayFile.Type.BASE)) },com.apkupdater.util.DownloadTrigger.SINGLE_CHECK);fail("Truncated file accepted") } catch(_:IllegalArgumentException){}
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun providedPlayDigestMustMatchActualBytes()=runBlocking {
        val dir=File(context.cacheDir,"play-test").apply{mkdirs()}
        try {
            val p=part("base.apk",PlayFile.Type.BASE).copy(sha256="00".repeat(32))
            try { downloader(dir,::bytes).download(Link.Play { listOf(p) },com.apkupdater.util.DownloadTrigger.SINGLE_CHECK);fail("Wrong digest accepted") } catch(_:IllegalArgumentException){}
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun urlSafeBase64PlayDigestIsAccepted()=runBlocking {
        val dir=File(context.cacheDir,"play-test").apply{mkdirs()}
        try {
            val digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes("base.apk"))
            val p=part("base.apk",PlayFile.Type.BASE).copy(sha256=android.util.Base64.encodeToString(digest,android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))
            val file=downloader(dir,::bytes).download(Link.Play { listOf(p) },com.apkupdater.util.DownloadTrigger.SINGLE_CHECK)
            assertArrayEquals(bytes("base.apk"),file.readBytes())
        } finally { dir.deleteRecursively() }
    }
    @Test fun supportDestinationIsUnchangedWithoutStoringRawLinkInTest() {
        val digest=java.security.MessageDigest.getInstance("SHA-256").digest(SupportLink.uri().toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals("3587e5af0ebc1488728690188aad7958f87653162419d974d4d0dd34997c2a30",digest)
    }
}
