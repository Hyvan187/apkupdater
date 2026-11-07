package org.uniapk.upgrader.util

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.ensureActive

enum class DownloadTrigger { SINGLE_CHECK, BATCH_CHECK, SEARCH_INSTALL, AUTOMATIC_SCAN }

class Downloader(
    private val client: OkHttpClient,
    private val apkPureClient: OkHttpClient,
    private val auroraClient: OkHttpClient,
    private val dir: File
) {

    suspend fun download(link: org.uniapk.upgrader.data.ui.Link, trigger:DownloadTrigger, maxBytes: Long = 512L * 1024 * 1024, progress:(Long,Long?)->Unit = {_,_->}): File {
        val task = kotlinx.coroutines.currentCoroutineContext()
        return org.uniapk.upgrader.glass.AppCache.use {
        task.ensureActive()
        val id=org.uniapk.upgrader.diagnostics.DiagnosticContext.nextId("download")
        val started=System.nanoTime()
        org.uniapk.upgrader.diagnostics.AppDiagnostics.info("download.started",mapOf("downloadId" to id,"trigger" to trigger.name,"maxBytes" to maxBytes,"format" to if(link is org.uniapk.upgrader.data.ui.Link.Play)"PLAY_SET" else "DIRECT"))
        try {
        val result=when(link) {
            is org.uniapk.upgrader.data.ui.Link.Url -> download(link.link,maxBytes,{ task.ensureActive() },task[kotlinx.coroutines.Job],progress)
            is org.uniapk.upgrader.data.ui.Link.Xapk -> download(link.link,maxBytes,{ task.ensureActive() },task[kotlinx.coroutines.Job],progress)
            is org.uniapk.upgrader.data.ui.Link.Play -> {
                org.uniapk.upgrader.diagnostics.AppDiagnostics.info("play.delivery.requested",mapOf("downloadId" to id))
                val parts=link.getInstallFiles()
                task.ensureActive()
                org.uniapk.upgrader.diagnostics.AppDiagnostics.info("play.delivery.resolved",mapOf("downloadId" to id,"parts" to parts.size,"bases" to parts.count{it.type==com.aurora.gplayapi.data.models.PlayFile.Type.BASE},"splits" to parts.count{it.type==com.aurora.gplayapi.data.models.PlayFile.Type.SPLIT}))
                require(parts.size in 1..128 && parts.count { it.type==com.aurora.gplayapi.data.models.PlayFile.Type.BASE }==1) { "Play liefert kein vollständiges, eindeutiges APK-Set." }
                require(parts.all { it.type==com.aurora.gplayapi.data.models.PlayFile.Type.BASE || it.type==com.aurora.gplayapi.data.models.PlayFile.Type.SPLIT }) { "Nicht unterstützter Play-Paketbestandteil." }
                val files=mutableListOf<File>();var result:File?=null
                try {
                    var total=0L
                    for(part in parts) {
                        task.ensureActive()
                        require(total < maxBytes) { "Play-Paketset überschreitet das Prüflimit." }
                        val expected=parts.takeIf{ps->ps.all{it.size>0}}?.sumOf{it.size};val before=total
                        val file=download(part.url,maxBytes-total,{ task.ensureActive() },task[kotlinx.coroutines.Job]){read,_->progress(before+read,expected)}
                        files+=file;total+=file.length()
                        require(part.size <= 0 || file.length() == part.size) { "Play liefert einen unvollständigen APK-Bestandteil." }
                        if (part.sha256.isNotBlank()) {
                            val digest = java.security.MessageDigest.getInstance("SHA-256")
                            file.inputStream().use { input -> val bytes=ByteArray(65536); while(true) { val n=input.read(bytes); if(n<0)break; digest.update(bytes,0,n) } }
                            val actual=digest.digest()
                            val expected=part.sha256.trim()
                            val matches=if(expected.matches(Regex("[0-9a-fA-F]{64}"))) actual.joinToString("") { "%02x".format(it) }.equals(expected,true)
                                else runCatching { java.security.MessageDigest.isEqual(actual,android.util.Base64.decode(expected,android.util.Base64.URL_SAFE)) }.getOrDefault(false)
                            require(matches) { "Play-Prüfsumme stimmt nicht überein." }
                        }
                    }
                    if(files.size==1) { files.single().also { result=it } }
                    else {
                        val archive=File(dir,"${randomUUID()}.apks");result=archive
                        java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
                            zip.setLevel(0)
                            files.forEachIndexed { index,file ->
                                zip.putNextEntry(java.util.zip.ZipEntry(if(parts[index].type==com.aurora.gplayapi.data.models.PlayFile.Type.BASE)"base.apk" else "split-$index.apk"))
                                file.inputStream().use { it.copyTo(zip) };zip.closeEntry()
                            }
                        }
                        require(archive.length()<=512L*1024*1024) { "Play-Archiv überschreitet das sichere Dateilimit." }
                        archive
                    }
                } catch(e:Exception) { result?.delete();throw e }
                finally { files.filter { it!=result }.forEach { it.delete() } }
            }
            else -> error("Diese Quelle liefert keine prüfbare Datei.")
        }
        org.uniapk.upgrader.diagnostics.AppDiagnostics.info("download.finished",mapOf("downloadId" to id,"trigger" to trigger.name,"bytes" to result.length(),"elapsedMs" to (System.nanoTime()-started)/1_000_000))
        result
        } catch(e:kotlinx.coroutines.CancellationException) { org.uniapk.upgrader.diagnostics.AppDiagnostics.info("download.cancelled",mapOf("downloadId" to id));throw e }
        catch(e:Exception) { if(task[kotlinx.coroutines.Job]?.isActive==false){org.uniapk.upgrader.diagnostics.AppDiagnostics.info("download.cancelled",mapOf("downloadId" to id));task.ensureActive()};org.uniapk.upgrader.diagnostics.AppDiagnostics.error("download.failed",mapOf("downloadId" to id,"category" to org.uniapk.upgrader.diagnostics.DiagnosticFailure.category(e)),e);throw e }
        }
    }

    private fun download(url:String,maxBytes:Long=512L*1024*1024,checkCancelled:()->Unit={},parentJob:kotlinx.coroutines.Job?=null,progress:(Long,Long?)->Unit={_,_->}):File {
        check(dir.exists()||dir.mkdirs()){ "Downloadcache ist nicht verfügbar." }
        val file=File(dir,randomUUID())
        val c=when { url.contains("apkpure")->apkPureClient;url.contains("aurora")->auroraClient;else->client }
        val call=c.newBuilder().callTimeout(10,java.util.concurrent.TimeUnit.MINUTES).readTimeout(60,java.util.concurrent.TimeUnit.SECONDS).build().newCall(org.uniapk.upgrader.diagnostics.DiagnosticContext.tag(downloadRequest(url)))
        val cancellation=kotlinx.coroutines.Job(parentJob)
        val hook=cancellation.invokeOnCompletion{if(cancellation.isCancelled)call.cancel()}
        try {
            checkCancelled()
            call.execute().use{response->
                if(!response.isSuccessful)throw org.uniapk.upgrader.glass.DownloadHttpException(response.code)
                require(response.request.url.isHttps){"Unsichere Downloadumleitung."}
                val expected=response.body.contentLength().takeIf{it>0}
                if(expected!=null&&expected>maxBytes)throw org.uniapk.upgrader.glass.AutomaticSizeLimit()
                progress(0,expected)
                response.body.byteStream().use{input->file.outputStream().use{out->
                    val bytes=ByteArray(65536);var total=0L;var last=0L
                    while(true){checkCancelled();val n=input.read(bytes);if(n<0)break;total+=n
                        if(total>maxBytes)throw org.uniapk.upgrader.glass.AutomaticSizeLimit()
                        require(dir.usableSpace>128L*1024*1024){"Speicherreserve erreicht. Download beendet."}
                        out.write(bytes,0,n);val now=System.nanoTime();if(now-last>150_000_000){progress(total,expected);last=now}
                    }
                    require(expected==null||total==expected){"Der Download ist unvollständig."};progress(total,expected)
                }}
            };return file
        }catch(e:Exception){call.cancel();file.delete();throw e}
        finally{hook.dispose();cancellation.complete()}
    }

    private fun downloadRequest(url: String): Request {
        require(url.startsWith("https://", true)) { "Downloads erfordern HTTPS" }
        return Request.Builder().url(url).build()
    }

}
