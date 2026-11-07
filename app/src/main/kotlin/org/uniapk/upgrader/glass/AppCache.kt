package org.uniapk.upgrader.glass

import android.content.Context
import coil3.SingletonImageLoader
import org.uniapk.upgrader.diagnostics.AppDiagnostics
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
    private fun removable(context:Context)=context.cacheDir.listFiles().orEmpty().filter { it.name in setOf("downloads","http","image_cache") || it.name.startsWith("apk-set-") || it.name.startsWith("preflight-") || it.name.startsWith("glass-local-") }
    fun bytes(context:Context):Long=removable(context).sumOf { root -> if(root.isFile)root.length() else root.walkTopDown().filter{it.isFile}.sumOf{it.length()} }
    fun maintain(context:Context) = synchronized(gate) {
        if(active>0||clearing)return@synchronized
        val now=System.currentTimeMillis()
        val files=java.io.File(context.cacheDir,"downloads").listFiles().orEmpty().filter{it.isFile}.sortedBy{it.lastModified()}
        var total=files.sumOf{it.length()}
        files.forEach { if(now-it.lastModified()>ResourcePolicy.TEMP_AGE || total>ResourcePolicy.READY_BYTES){val length=it.length();if(it.delete())total-=length} }
        removable(context).filter{it.name!="downloads"&&it.name!="http"&&it.name!="image_cache"&&now-it.lastModified()>ResourcePolicy.TEMP_AGE}.forEach{it.deleteRecursively()}
    }
    suspend fun clear(context:Context,http:Cache):Long=withContext(Dispatchers.IO) {
        synchronized(gate){check(active==0&&!clearing){"Ein Download, eine Prüfung oder Installation läuft. Bitte zuerst abschließen oder abbrechen."};clearing=true}
        try {
            val before=bytes(context)
            http.evictAll()
            val loader=SingletonImageLoader.get(context.applicationContext);loader.memoryCache?.clear();loader.diskCache?.clear()
            removable(context).filter{it.name!="http"&&it.name!="image_cache"}.forEach{check(it.deleteRecursively()){ "Eine Prüfdatei konnte nicht gelöscht werden." }}
            java.io.File(context.cacheDir,"downloads").mkdirs()
            val freed=(before-bytes(context)).coerceAtLeast(0)
            AppDiagnostics.info("cache.cleared",mapOf("freedBytes" to freed));freed
        } finally{synchronized(gate){clearing=false}}
    }
}
