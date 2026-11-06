package com.apkupdater.util

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream


class Downloader(
    private val client: OkHttpClient,
    private val apkPureClient: OkHttpClient,
    private val auroraClient: OkHttpClient,
    private val dir: File
) {

    fun download(url: String, maxBytes: Long = 512L * 1024 * 1024): File {
        val file = File(dir, randomUUID())
        try {
            requireNotNull(downloadStream(url)) { "Quelle nicht erreichbar oder HTTP-Fehler" }.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
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
