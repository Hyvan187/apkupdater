package com.apkupdater.util

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.ensureActive


class Downloader(
    private val client: OkHttpClient,
    private val apkPureClient: OkHttpClient,
    private val auroraClient: OkHttpClient,
    private val dir: File
) {

    suspend fun download(link: com.apkupdater.data.ui.Link, maxBytes: Long = 512L * 1024 * 1024): File {
        val task = kotlinx.coroutines.currentCoroutineContext()
        return com.apkupdater.glass.AppCache.use {
        task.ensureActive()
        when(link) {
            is com.apkupdater.data.ui.Link.Url -> download(link.link,maxBytes) { task.ensureActive() }
            is com.apkupdater.data.ui.Link.Xapk -> download(link.link,maxBytes) { task.ensureActive() }
            is com.apkupdater.data.ui.Link.Play -> {
                val parts=link.getInstallFiles()
                task.ensureActive()
                require(parts.size in 1..128 && parts.count { it.type==com.aurora.gplayapi.data.models.PlayFile.Type.BASE }==1) { "Play liefert kein vollständiges, eindeutiges APK-Set." }
                require(parts.all { it.type==com.aurora.gplayapi.data.models.PlayFile.Type.BASE || it.type==com.aurora.gplayapi.data.models.PlayFile.Type.SPLIT }) { "Nicht unterstützter Play-Paketbestandteil." }
                val files=mutableListOf<File>();var result:File?=null
                try {
                    var total=0L
                    for(part in parts) {
                        task.ensureActive()
                        require(total < maxBytes) { "Play-Paketset überschreitet das Prüflimit." }
                        val file=download(part.url,maxBytes-total) { task.ensureActive() }
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
        }
    }

    fun download(url: String, maxBytes: Long = 512L * 1024 * 1024, checkCancelled: () -> Unit = {}): File {
        check(dir.exists() || dir.mkdirs()) { "Downloadcache ist nicht verfügbar." }
        val file = File(dir, randomUUID())
        try {
            requireNotNull(downloadStream(url)) { "Quelle nicht erreichbar oder HTTP-Fehler" }.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        checkCancelled()
                        val n = input.read(buffer); if (n < 0) break
                        total += n; if(total > maxBytes) throw com.apkupdater.glass.AutomaticSizeLimit()
                        out.write(buffer, 0, n)
                    }
                }
            }
            return file
        } catch (e: Exception) { file.delete(); throw e }
    }

    fun downloadStream(url: String): InputStream? {
        val c = when { url.contains("apkpure") -> apkPureClient; url.contains("aurora") -> auroraClient; else -> client }
        val response = c.newCall(downloadRequest(url)).execute()
        if(!response.isSuccessful) { val code = response.code; response.close(); throw com.apkupdater.glass.DownloadHttpException(code) }
        if(!response.request.url.isHttps) { response.close(); error("Der Download wurde auf eine unsichere Adresse umgeleitet.") }
        return response.body.byteStream()
    }

    private fun downloadRequest(url: String): Request {
        require(url.startsWith("https://", true)) { "Downloads erfordern HTTPS" }
        return Request.Builder().url(url).build()
    }

}
