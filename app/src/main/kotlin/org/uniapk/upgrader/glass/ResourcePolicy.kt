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
    private var requested=0
    fun automatic(available:Int,lowRam:Boolean,powerSave:Boolean,thermal:Int):Int=when{powerSave||thermal>=4->1;lowRam||thermal>=2->minOf(2,available.coerceAtLeast(1));else->minOf(4,available.coerceAtLeast(1))}
    fun set(value:Int){requested=value.coerceAtLeast(0);mutable.value=if(requested==0)automatic(Runtime.getRuntime().availableProcessors(),false,false,0) else ResourcePolicy.parallelism(requested,Runtime.getRuntime().availableProcessors())}
    fun refresh(context:Context){if(requested!=0)return;val power=context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager;val low=(context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice;mutable.value=automatic(Runtime.getRuntime().availableProcessors(),low,power.isPowerSaveMode,if(android.os.Build.VERSION.SDK_INT>=29)power.currentThermalStatus else 0)}
    fun dispatcher()=dispatchers.getOrPut(mutable.value){Dispatchers.Default.limitedParallelism(mutable.value)}
    fun fps()=when(mutable.value){1->15;2->24;else->30}
}
object DownloadFolderPolicy {
    fun isTree(uri:android.net.Uri)=if(android.os.Build.VERSION.SDK_INT>=24)android.provider.DocumentsContract.isTreeUri(uri) else uri.scheme=="content" && uri.pathSegments.firstOrNull()=="tree" && runCatching{android.provider.DocumentsContract.getTreeDocumentId(uri).isNotBlank()}.getOrDefault(false)
    fun valid(context:Context,prefs:Prefs)=prefs.downloadTree.get().takeIf{it.isNotBlank()}?.let { raw ->
        FolderAccess.status(context,raw).writable
    } ?: false
}

