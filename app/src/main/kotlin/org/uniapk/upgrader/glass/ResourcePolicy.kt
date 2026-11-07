package org.uniapk.upgrader.glass

import android.content.Context
import org.uniapk.upgrader.prefs.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

object ResourcePolicy {
    const val MIB=1024L*1024
    const val DEFAULT_FILE_MIB=512
    const val DEFAULT_BUDGET_MIB=2048
    const val DEFAULT_APPS=10
    const val CACHE_BYTES=256*MIB
    const val READY_BYTES=1024*MIB
    const val TEMP_AGE=60*60*1000L
    fun budget(free:Long,requested:Long)=minOf(requested,(free-1024*MIB).coerceAtLeast(0)/5,free/5)
    fun parallelism(requested:Int,available:Int)=requested.coerceIn(1,available.coerceAtLeast(1))
    fun label(bytes:Long):String=when {bytes>=1024*MIB->"%.2f GiB".format(bytes.toDouble()/(1024*MIB));bytes>=MIB->"%.1f MiB".format(bytes.toDouble()/MIB);else->"%.1f KiB".format(bytes/1024.0)}
}
/** Limits app-owned CPU work. Android still schedules UI, networking and renderer threads. */
object ComputeBudget {
    private val mutable=MutableStateFlow(minOf(4,Runtime.getRuntime().availableProcessors()).coerceAtLeast(1))
    val state=mutable.asStateFlow()
    private val dispatchers=java.util.concurrent.ConcurrentHashMap<Int,kotlinx.coroutines.CoroutineDispatcher>()
    fun set(value:Int){mutable.value=ResourcePolicy.parallelism(value,Runtime.getRuntime().availableProcessors())}
    fun dispatcher()=dispatchers.getOrPut(mutable.value){Dispatchers.Default.limitedParallelism(mutable.value)}
    fun fps()=when(mutable.value){1->15;2->24;else->30}
}
object DownloadFolderPolicy {
    fun valid(context:Context,prefs:Prefs)=prefs.downloadTree.get().takeIf{it.isNotBlank()}?.let { raw ->
        runCatching {val uri=android.net.Uri.parse(raw);context.contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isWritePermission} && android.provider.DocumentsContract.isTreeUri(uri)}.getOrDefault(false)
    } ?: false
}
