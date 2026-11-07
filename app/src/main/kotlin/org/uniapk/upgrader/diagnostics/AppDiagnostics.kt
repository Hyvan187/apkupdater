package org.uniapk.upgrader.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import org.uniapk.upgrader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class DiagnosticConfig(val enabled: Boolean = true, val verbose: Boolean = false,val maxFiles:Int=5)

/** One app-local recorder for foreground operations, workers and unhandled crashes. */
object AppDiagnostics {
    private val mutable = MutableStateFlow(DiagnosticConfig())
    val state = mutable.asStateFlow()
    private val writer = DiagnosticWriter()
    @Volatile private var store: DiagnosticStore? = null
    @Volatile private var application: Application? = null
    @Volatile private var ioFailed = false
    private val dropped = java.util.concurrent.atomic.AtomicLong()
    private val droppedTotal = java.util.concurrent.atomic.AtomicLong()

    @Synchronized fun initialize(app: Application) {
        if (application === app && store != null) return
        val first = application == null
        application = app
        store = DiagnosticStore(File(app.filesDir, "diagnostics"))
        val settings = app.getSharedPreferences("diagnostics.settings", Context.MODE_PRIVATE)
        mutable.value = DiagnosticConfig(settings.getBoolean("enabled", true), settings.getBoolean("verbose", false),settings.getInt("maxFiles",5).coerceIn(1,10))
        store?.limitSegments(mutable.value.maxFiles)
        if (first) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                try {
                    // Preserve Android's crash handling even if storage or the writer is stuck.
                    if (mutable.value.enabled) runCatching { writer.barrier(250) {
                        store?.append(DiagnosticLevel.ERROR, "crash.uncaught", mapOf("thread" to thread.name), error, detailed = mutable.value.verbose, durable = true)
                    } }
                } finally { previous?.uncaughtException(thread, error) }
            }
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                private var visible = 0
                override fun onActivityStarted(activity: Activity) { if (visible++ == 0) info("app.foreground") }
                override fun onActivityStopped(activity: Activity) { if (--visible <= 0) { visible = 0; info("app.background") } }
                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
        }
        info("app.start", metadata())
        if (Build.VERSION.SDK_INT >= 30) writer.execute {
            runCatching {
                val last = settings.getLong("lastExit", 0)
                val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val exits = manager.getHistoricalProcessExitReasons(app.packageName, 0, 8).filter { it.timestamp > last }
                exits.sortedBy { it.timestamp }.forEach { exit ->
                    if (mutable.value.enabled) store?.append(ExitReason.level(exit.reason), "app.previous_exit", mapOf("reason" to exit.reason, "reasonName" to ExitReason.name(exit.reason), "status" to exit.status, "exitTime" to exit.timestamp, "pssKiB" to exit.pss, "rssKiB" to exit.rss))
                }
                exits.maxOfOrNull { it.timestamp }?.let { settings.edit().putLong("lastExit", it).apply() }
            }.onFailure { debug("diagnostic.exit_history_unavailable", mapOf("exception" to it.javaClass.simpleName)) }
        }
    }
    fun configure(enabled: Boolean = state.value.enabled, verbose: Boolean = state.value.verbose,maxFiles:Int=state.value.maxFiles) {
        mutable.value = DiagnosticConfig(enabled, verbose,maxFiles.coerceIn(1,10))
        application?.getSharedPreferences("diagnostics.settings", Context.MODE_PRIVATE)?.edit()?.putBoolean("enabled", enabled)?.putBoolean("verbose", verbose)?.putInt("maxFiles",state.value.maxFiles)?.apply()
        writer.execute {store?.limitSegments(state.value.maxFiles)}
        if (enabled) info("diagnostic.configuration", mapOf("verbose" to verbose))
    }
    fun debug(event: String, fields: Map<String, Any?> = emptyMap()) = record(DiagnosticLevel.DEBUG, event, fields)
    fun info(event: String, fields: Map<String, Any?> = emptyMap()) = record(DiagnosticLevel.INFO, event, fields)
    fun error(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) = record(DiagnosticLevel.ERROR, event, fields, error)
    fun warn(event: String, fields: Map<String, Any?> = emptyMap()) = record(DiagnosticLevel.WARN, event, fields)
    private fun record(level: DiagnosticLevel, event: String, fields: Map<String, Any?>, error: Throwable? = null) {
        if (!state.value.enabled || level == DiagnosticLevel.DEBUG && !state.value.verbose) return
        val capturedFields = DiagnosticPrivacy.fields(DiagnosticContext.current() + fields,state.value.verbose)
        if (!writer.execute {
            val config = state.value
            if (config.enabled && (level != DiagnosticLevel.DEBUG || config.verbose)) {
                runCatching {
                    val lost=dropped.getAndSet(0)
                    if(lost>0)store?.append(DiagnosticLevel.WARN,"diagnostic.events_dropped",mapOf("count" to lost))
                    store?.append(level, event, capturedFields, error, config.verbose)
                }.onFailure { ioFailed = true }
            }
        }) { ioFailed = true; dropped.incrementAndGet();droppedTotal.incrementAndGet() }
    }
    private fun metadata(): Map<String, String> = buildMap {
        put("application",BuildConfig.APPLICATION_ID);put("version",BuildConfig.VERSION_NAME);put("versionCode",BuildConfig.VERSION_CODE.toString())
        put("buildType",BuildConfig.BUILD_TYPE);put("buildFingerprint",BuildConfig.BUILD_FINGERPRINT)
        put("androidApi",Build.VERSION.SDK_INT.toString());put("androidRelease",Build.VERSION.RELEASE)
        put("securityPatch",Build.VERSION.SECURITY_PATCH);put("abis",Build.SUPPORTED_ABIS.joinToString())
        put("manufacturer",Build.MANUFACTURER);put("model",Build.MODEL);put("session",DiagnosticContext.session)
        put("queuedEventsDropped",droppedTotal.get().toString());put("timeZone",java.util.TimeZone.getDefault().id)
        application?.let { app ->
            put("packagedAbis",runCatching { java.util.zip.ZipFile(app.applicationInfo.sourceDir).use { zip->zip.entries().asSequence().filter{it.name.startsWith("lib/") && it.name.endsWith(".so")}.map{it.name.split('/')[1]}.toSet().sorted().joinToString().ifBlank{"JAVA_ONLY"} } }.getOrDefault("UNKNOWN"))
            val config=app.resources.configuration
            put("densityDpi",config.densityDpi.toString());put("fontScale",config.fontScale.toString())
            put("widthDp",config.screenWidthDp.toString());put("heightDp",config.screenHeightDp.toString())
            put("language",if(Build.VERSION.SDK_INT>=24)config.locales[0].toLanguageTag() else @Suppress("DEPRECATION")config.locale.toLanguageTag())
        }
        org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<org.uniapk.upgrader.prefs.Prefs>()?.let{prefs->
            put("selectedProfile",prefs.simulatedPreset.get());put("selectedProfileRegion",prefs.simulatedRegion.get());put("selectedProfileModel",prefs.simulatedModel.get())
        }
        networkFields().forEach { (key,value)->put(key,value.toString()) }
    }.filterKeys{state.value.verbose || it in DiagnosticPrivacy.generalMetadata}
    fun networkFields(): Map<String,Any?> = runCatching {
        val app=application ?: return emptyMap()
        val manager=app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val capabilities=manager.getNetworkCapabilities(manager.activeNetwork)
        mapOf("connected" to (capabilities!=null),"metered" to manager.isActiveNetworkMetered,
            "transport" to when {
                capabilities==null->"NONE"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)->"VPN"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)->"WIFI"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)->"CELLULAR"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)->"ETHERNET"
                else->"OTHER"
            })
    }.getOrDefault(mapOf("transport" to "UNKNOWN"))
    private fun flush() { writer.flush() }
    suspend fun storageBytes(): Long = withContext(Dispatchers.IO) { store?.size() ?: 0L }
    fun storageProblem() = ioFailed
    suspend fun clear() = withContext(Dispatchers.IO) { flush(); store?.clear(); ioFailed = false; dropped.set(0);droppedTotal.set(0) }
    suspend fun export(context: Context, uri: Uri, kind: DiagnosticExport) = withContext(Dispatchers.IO) {
        info("diagnostic.export", mapOf("type" to kind.name)); flush()
        val target = requireNotNull(store) { "Diagnose wurde noch nicht initialisiert." }
        requireNotNull(context.contentResolver.openOutputStream(uri, "w")) { "Exportdatei kann nicht geöffnet werden." }.use { target.export(it, kind, metadata() + ("captureLevel" to if (state.value.verbose) "VERBOSE" else "STANDARD")) }
    }
}

/** Logs no headers, query strings or bodies. Request IDs survive Retrofit's worker handoff. */
class DiagnosticNetworkInterceptor(
    private val recorder: (DiagnosticLevel,String,Map<String,Any?>,Throwable?) -> Unit = { level,event,fields,error ->
        when(level) {
            DiagnosticLevel.DEBUG->AppDiagnostics.debug(event,fields)
            DiagnosticLevel.INFO->AppDiagnostics.info(event,fields)
            DiagnosticLevel.WARN->AppDiagnostics.warn(event,fields)
            DiagnosticLevel.ERROR->AppDiagnostics.error(event,fields,error)
        }
    }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request=chain.request();val started=System.nanoTime()
        val trace=request.tag(DiagnosticRequest::class.java) ?: DiagnosticRequest(DiagnosticContext.nextId("request"),emptyMap())
        val fields=trace.fields+mapOf("requestId" to trace.id,"host" to request.url.host,"method" to request.method)+AppDiagnostics.networkFields()
        recorder(DiagnosticLevel.DEBUG,"network.started",fields,null)
        return try {
            val response=chain.proceed(request)
            val result=fields+mapOf("status" to response.code,"elapsedMs" to (System.nanoTime()-started)/1_000_000)
            recorder(if(response.code>=400)DiagnosticLevel.WARN else DiagnosticLevel.DEBUG,if(response.code>=400)"network.http_error" else "network.response",result,null)
            response
        } catch(e:Exception) {
            val category=DiagnosticFailure.category(e,chain.call().isCanceled())
            val result=fields+mapOf("category" to category,"elapsedMs" to (System.nanoTime()-started)/1_000_000)
            recorder(if(category=="CANCELLED")DiagnosticLevel.DEBUG else DiagnosticLevel.ERROR,if(category=="CANCELLED")"network.cancelled" else "network.failure",result,if(category=="CANCELLED")null else e)
            throw e
        }
    }
}

object ExitReason {
    fun name(reason:Int)=when(reason) {
        0->"UNKNOWN";1->"EXIT_SELF";2->"SIGNALED";3->"LOW_MEMORY";4->"CRASH";5->"CRASH_NATIVE";6->"ANR";7->"INITIALIZATION_FAILURE";8->"PERMISSION_CHANGE";9->"EXCESSIVE_RESOURCE_USAGE";10->"USER_REQUESTED";11->"USER_STOPPED";12->"DEPENDENCY_DIED";13->"OTHER";14->"FREEZER";15->"PACKAGE_STATE_CHANGE";16->"PACKAGE_UPDATED";else->"UNKNOWN_$reason"
    }
    fun level(reason:Int)=when(reason) {4,5,6,7->DiagnosticLevel.ERROR;1,8,10,11,15,16->DiagnosticLevel.INFO;else->DiagnosticLevel.WARN}
}
