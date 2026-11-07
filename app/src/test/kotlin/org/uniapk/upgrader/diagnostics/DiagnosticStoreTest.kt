package org.uniapk.upgrader.diagnostics

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.ZipInputStream

class DiagnosticStoreTest {
    private fun entries(bytes: ByteArray): Map<String,String> {
        val result=linkedMapOf<String,String>()
        ZipInputStream(bytes.inputStream()).use { zip -> while(true) { val e=zip.nextEntry ?: break;result[e.name]=zip.readBytes().toString(Charsets.UTF_8) } }
        return result
    }
    @Test fun secretsLinksEmailPathsAndHeadersNeverReachStoredOrExportedRecords() {
        val dir=Files.createTempDirectory("diagnostics-test").toFile()
        try {
            val store=DiagnosticStore(dir)
            store.append(DiagnosticLevel.ERROR,"network.failure",mapOf("access_token" to "TOPSECRET","detail" to "Authorization: Bearer HEADERSECRET\nhttps://example.org/file?token=URLSECRET person@example.org /data/user/0/private/file token=FIELDSECRET"))
            for(kind in DiagnosticExport.entries) {
                val output=ByteArrayOutputStream();store.export(output,kind,mapOf("password" to "METASECRET"))
                val all=entries(output.toByteArray()).values.joinToString()
                listOf("TOPSECRET","HEADERSECRET","URLSECRET","FIELDSECRET","METASECRET","person@example.org","/data/user/0/").forEach { assertFalse("Leaked $it",all.contains(it)) }
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun ringRotationHasStrictByteAndSegmentLimits() {
        val dir=Files.createTempDirectory("diagnostics-test").toFile()
        try {
            val store=DiagnosticStore(dir,512,3)
            repeat(100) { store.append(DiagnosticLevel.INFO,"event.$it",mapOf("detail" to "x".repeat(100))) }
            assertTrue(store.size()<=1536);assertTrue(dir.listFiles().orEmpty().size<=3)
            assertEquals("event.99",store.snapshot().last()["event"].asString)
        } finally { dir.deleteRecursively() }
    }
    @Test fun oversizedFallbackCannotLeakRawEventOrMalformedRecords() {
        val dir=Files.createTempDirectory("diagnostics-test").toFile()
        try {
            val store=DiagnosticStore(dir,256,2)
            store.append(DiagnosticLevel.INFO,"https://example.org/private?token=RAWSECRET",(0..24).associate { "field$it" to "x".repeat(1024) })
            assertTrue(store.size()<=256);assertFalse(dir.listFiles().orEmpty().joinToString { it.readText() }.contains("RAWSECRET"))
            assertEquals("diagnostic.record_truncated",store.snapshot().single()["event"].asString)
        } finally { dir.deleteRecursively() }
    }
    @Test fun retentionRemovesOldRecordsEvenInsideRecentlyWrittenSegment() {
        val dir=Files.createTempDirectory("diagnostics-test").toFile();var now=1_800_000_000_000L
        try {
            val store=DiagnosticStore(dir,4096,2) { now }
            store.append(DiagnosticLevel.INFO,"old")
            now+=6L*24*60*60*1000;store.append(DiagnosticLevel.INFO,"recent")
            now+=2L*24*60*60*1000;store.append(DiagnosticLevel.INFO,"today")
            assertEquals(listOf("recent","today"),store.snapshot().map { it["event"].asString })
            assertFalse(dir.listFiles().orEmpty().joinToString { it.readText() }.contains("\"old\""))
        } finally { dir.deleteRecursively() }
    }
    @Test fun shortExportExcludesDebugAndIsBoundedToTwoHundredRecords() {
        val dir=Files.createTempDirectory("diagnostics-test").toFile()
        try {
            val store=DiagnosticStore(dir)
            repeat(250) { store.append(DiagnosticLevel.INFO,"event.$it") };store.append(DiagnosticLevel.DEBUG,"private.debug")
            val short=ByteArrayOutputStream();store.export(short,DiagnosticExport.SHORT,emptyMap())
            val data=entries(short.toByteArray());assertEquals(200,data.getValue("diagnostics-short.txt").lines().count { it.isNotBlank() });assertFalse(data.values.joinToString().contains("private.debug"))
            val full=ByteArrayOutputStream();store.export(full,DiagnosticExport.FULL,emptyMap())
            assertEquals(251,entries(full.toByteArray()).getValue("diagnostics-full.jsonl").lines().count { it.isNotBlank() })
            store.clear();assertEquals(0L,store.size())
        } finally { dir.deleteRecursively() }
    }
    @Test fun fullQueueCannotSilentlyDiscardFlushBarrier() {
        DiagnosticWriter(1).use { writer ->
            val started=CountDownLatch(1);val release=CountDownLatch(1)
            assertTrue(writer.execute { started.countDown();release.await(3,TimeUnit.SECONDS) })
            assertTrue(started.await(1,TimeUnit.SECONDS));assertTrue(writer.execute {})
            assertFalse(writer.execute {})
            try { writer.flush(40);fail("Barrier unexpectedly accepted in full queue") } catch (_: TimeoutException) {}
            release.countDown();writer.flush(1000)
        }
    }
    @Test fun successfulFlushWaitsForAllPreviouslyAcceptedRecords() {
        DiagnosticWriter().use { writer ->
            val completed=java.util.concurrent.atomic.AtomicInteger()
            repeat(100) { assertTrue(writer.execute { completed.incrementAndGet() }) }
            writer.flush();assertEquals(100,completed.get())
        }
    }
}
