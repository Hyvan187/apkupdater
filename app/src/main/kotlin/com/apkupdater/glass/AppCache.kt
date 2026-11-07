package com.apkupdater.glass

import android.content.Context
import coil3.SingletonImageLoader
import com.apkupdater.diagnostics.AppDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import java.io.Closeable

/** Clearing never removes settings, diagnostic history, public downloads or pending handoffs. */
object AppCache {
    private val gate = Any()
    private var active = 0
    private var clearing = false
    fun begin(): Closeable = synchronized(gate) {
        check(!clearing) { "Der Cache wird gerade geleert. Bitte kurz warten." }
        active++
        object : Closeable {
            private var closed = false
            override fun close() = synchronized(gate) { if (!closed) { closed = true; active-- }; Unit }
        }
    }
    inline fun <T> use(block: () -> T): T = begin().use { block() }
    suspend fun clear(context: Context, http: Cache): Long = withContext(Dispatchers.IO) {
        synchronized(gate) {
            check(active == 0 && !clearing) { "Eine Prüfung oder Installation läuft. Bitte zuerst abschließen oder abbrechen." }
            clearing = true
        }
        try {
            val files = java.io.File(context.cacheDir, "downloads")
            val before = runCatching { http.size() }.getOrDefault(0) + files.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            http.evictAll()
            check(!files.exists() || files.deleteRecursively()) { "Nicht alle Prüfdateien konnten entfernt werden." }
            files.mkdirs()
            // Only app-owned temporary names; never external-installer handoffs or arbitrary files.
            context.cacheDir.listFiles()?.filter {
                it.name.startsWith("apk-set-") || it.name.startsWith("preflight-") || it.name.startsWith("glass-local-")
            }?.forEach { check(it.deleteRecursively()) { "Eine temporäre Prüfdatei konnte nicht entfernt werden." } }
            val loader = SingletonImageLoader.get(context.applicationContext)
            loader.memoryCache?.clear(); loader.diskCache?.clear()
            AppDiagnostics.info("cache.cleared", mapOf("bytesAtLeast" to before))
            before
        } finally { synchronized(gate) { clearing = false } }
    }
}
