package org.uniapk.upgrader.diagnostics

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.zip.ZipInputStream

class DiagnosticQualityTest {
    private fun files(bytes:ByteArray):Map<String,String> {
        val result=linkedMapOf<String,String>()
        ZipInputStream(bytes.inputStream()).use { zip -> while(true){val e=zip.nextEntry ?: break;result[e.name]=zip.readBytes().toString(Charsets.UTF_8)} }
        return result
    }
    @Test fun redactsQuotedJsonCredentialsAndUnlabelledBearerValues() {
        val input="""{"Authorization":"Bearer HEADERSECRET","password":"PASSSECRET","access_token":"TOKENSECRET"} Bearer OTHERSECRET at 192.168.1.4"""
        val output=DiagnosticRedaction.text(input)
        for(secret in listOf("HEADERSECRET","PASSSECRET","TOKENSECRET","OTHERSECRET","192.168.1.4"))assertFalse(output,output.contains(secret))
    }
    @Test fun numericAndBooleanFieldsStayMachineReadableWhileIdentifiersAreRedacted() {
        val dir=Files.createTempDirectory("log-quality").toFile()
        try {
            val store=DiagnosticStore(dir)
            store.append(DiagnosticLevel.INFO,"fields",mapOf("bytes" to 123L,"active" to true,"imei" to 123456789012345L,"missing" to null))
            val fields=store.snapshot().single()["fields"].asJsonObject
            assertTrue(fields["bytes"].asJsonPrimitive.isNumber);assertTrue(fields["active"].asJsonPrimitive.isBoolean)
            assertEquals("[redacted]",fields["imei"].asString);assertFalse(fields.has("missing"))
        } finally {dir.deleteRecursively()}
    }
    @Test fun shortExportKeepsWholeUtf8RecordsAndReportsActualCount() {
        val dir=Files.createTempDirectory("log-quality").toFile()
        try {
            val store=DiagnosticStore(dir)
            repeat(200){store.append(DiagnosticLevel.ERROR,"unicode",mapOf("a" to "ü".repeat(1000),"b" to "ß".repeat(1000)),IOException("Connection reset"))}
            val out=ByteArrayOutputStream();store.export(out,DiagnosticExport.SHORT,emptyMap());val data=files(out.toByteArray())
            val lines=data.getValue("diagnostics-short.txt").lines().filter { it.isNotBlank() }
            val meta=JsonParser.parseString(data.getValue("metadata.json")).asJsonObject
            assertEquals(2,meta["format"].asInt);assertEquals(lines.size,meta["records"].asInt)
            assertTrue(lines.size<200);assertTrue(data.getValue("diagnostics-short.txt").toByteArray().size<=256*1024)
            assertTrue(lines.all { it.matches(Regex("^\\d{4}-.* ERROR unicode .*")) });assertFalse(data.values.joinToString().contains('\uFFFD'))
        } finally {dir.deleteRecursively()}
    }
    @Test fun validJsonWithoutRequiredFieldsCannotBreakExports() {
        val dir=Files.createTempDirectory("log-quality").toFile()
        try {
            val store=DiagnosticStore(dir);store.append(DiagnosticLevel.INFO,"good")
            java.io.File(dir,"events-0.jsonl").appendText("{}\n{\"time\":123}\n{broken\n")
            val out=ByteArrayOutputStream();store.export(out,DiagnosticExport.FULL,emptyMap())
            assertEquals(1,JsonParser.parseString(files(out.toByteArray()).getValue("metadata.json")).asJsonObject["records"].asInt)
        } finally {dir.deleteRecursively()}
    }
    @Test fun fullExportContainsStableTimeCauseAndSequenceInformation() {
        val dir=Files.createTempDirectory("log-quality").toFile()
        try {
            val store=DiagnosticStore(dir)
            store.append(DiagnosticLevel.ERROR,"network.failure",mapOf("operationId" to "scan-1"),IOException("request failed",SocketTimeoutException("timeout for https://private.invalid/?token=SECRET")))
            store.append(DiagnosticLevel.INFO,"finished")
            val events=store.snapshot();val first=events.first()
            assertEquals(2,first["schema"].asInt);assertTrue(first["timestamp"].asString.endsWith('Z'))
            assertEquals(2,first["causes"].asJsonArray.size());assertEquals("TIMEOUT",first["errorCategory"].asString)
            assertFalse(first.toString().contains("SECRET"));assertEquals(1L,first["sequence"].asLong);assertEquals(2L,events.last()["sequence"].asLong)
        } finally {dir.deleteRecursively()}
    }
    @Test fun cancelledRequestsAreNotClassifiedAsProviderFailures() {
        assertEquals("CANCELLED",DiagnosticFailure.category(IOException("Canceled")))
        assertEquals("CANCELLED",DiagnosticFailure.category(IOException("socket closed"),true))
        assertEquals("HTTP2_RESET",DiagnosticFailure.category(IOException("stream was reset: CANCEL")))
        assertEquals("TIMEOUT",DiagnosticFailure.category(SocketTimeoutException()))
    }
    @Test fun expectedProcessExitsAreInformationalAndCrashesAreErrors() {
        assertEquals("PACKAGE_UPDATED",ExitReason.name(16));assertEquals(DiagnosticLevel.INFO,ExitReason.level(16))
        assertEquals("USER_REQUESTED",ExitReason.name(10));assertEquals(DiagnosticLevel.INFO,ExitReason.level(10))
        for(reason in listOf(4,5,6,7))assertEquals(DiagnosticLevel.ERROR,ExitReason.level(reason))
    }
    @Test fun cancelledNetworkCallEmitsOneCancellationWithTimingAndNoError() {
        val events=mutableListOf<Pair<String,Map<String,Any?>>>()
        val diagnostics=DiagnosticNetworkInterceptor { _,name,fields,error -> events+=name to fields;assertNull(error) }
        val client=OkHttpClient.Builder().addInterceptor(diagnostics).addInterceptor { chain -> chain.call().cancel();throw IOException("Canceled") }.build()
        try {client.newCall(Request.Builder().url("https://fixture.invalid/private?token=SECRET").build()).execute();fail("Expected cancellation") } catch(_:IOException){}
        assertEquals(listOf("network.started","network.cancelled"),events.map { it.first })
        assertTrue(events.last().second.containsKey("elapsedMs"));assertEquals("CANCELLED",events.last().second["category"])
        assertFalse(events.toString().contains("SECRET"));assertFalse(events.toString().contains("private"))
    }
    @Test fun retrofitHandoffPreservesOperationIdWithoutThreadLocalLeakage()=runBlocking {
        val captured=mutableListOf<Map<String,Any?>>()
        val client=OkHttpClient.Builder().addInterceptor(DiagnosticNetworkInterceptor { _,_,fields,_ -> captured+=fields }).addInterceptor { throw IOException("Canceled") }.build()
        val call=withContext(DiagnosticContext.element(mapOf("operationId" to "scan-fixture","source" to "Play"))) {
            DiagnosticCallFactory(client).newCall(Request.Builder().url("https://fixture.invalid/").build())
        }
        assertTrue(DiagnosticContext.current().isEmpty())
        try {call.execute()}catch(_:IOException){}
        assertEquals("scan-fixture",captured.first()["operationId"]);assertEquals("Play",captured.first()["source"])
        assertEquals(captured.first()["requestId"],captured.last()["requestId"])
    }
}
