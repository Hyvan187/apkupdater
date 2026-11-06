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

    fun download(url: String): File {
        val file = File(dir, randomUUID())
        try {
            requireNotNull(downloadStream(url)) { "Quelle nicht erreichbar oder HTTP-Fehler" }.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= 512L * 1024 * 1024) { "Download größer als 512 MiB" }
                        out.write(buffer, 0, n)
                    }
                }
            }
            return file
        } catch (e: Exception) { file.delete(); throw e }
    }

    fun downloadStream(url: String): InputStream? = runCatching {
        val c = when {
            url.contains("apkpure") -> apkPureClient
            url.contains("aurora") -> auroraClient
            else -> client
        }
        val response = c.newCall(downloadRequest(url)).execute()
        if (response.isSuccessful && response.request.url.isHttps) {
            return response.body.byteStream()
        } else {
            response.close()
            Log.e("Downloader", "Download failed with error code: ${response.code}")
        }
        return null
    }.getOrElse {
        Log.e("Downloader", "Error downloading", it)
        null
    }

    private fun downloadRequest(url: String): Request {
        require(url.startsWith("https://", true)) { "Downloads erfordern HTTPS" }
        return Request.Builder().url(url).build()
    }

}
