package com.apkupdater.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.apkupdater.BuildConfig
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

data class DiagnosticConfig(val enabled: Boolean = true, val verbose: Boolean = false)

/** One app-local recorder for foreground operations, workers and unhandled crashes. */
object AppDiagnostics {
    private val mutable = MutableStateFlow(DiagnosticConfig())
    val state = mutable.asStateFlow()
    private val writer = DiagnosticWriter()
    @Volatile private var store: DiagnosticStore? = null
    @Volatile private var application: Application? = null
    @Volatile private var ioFailed = false

    @Synchronized fun initialize(app: Application) {
        if (application === app && store != null) return
        val first = application == null
        application = app
        store = DiagnosticStore(File(app.filesDir, "diagnostics"))
        val settings = app.getSharedPreferences("diagnostics.settings", Context.MODE_PRIVATE)
        mutable.value = DiagnosticConfig(settings.getBoolean("enabled", true), settings.getBoolean("verbose", false))
        if (first) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                try {
                    // Preserve Android's crash handling even if storage or the writer is stuck.
                    if (mutable.value.enabled) runCatching { writer.barrier(250) {
                        store?.append(DiagnosticLevel.ERROR, "crash.uncaught", mapOf("thread" to thread.name), error, detailed = true, durable = true)
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
                    if (mutable.value.enabled) store?.append(DiagnosticLevel.WARN, "app.previous_exit", mapOf("reason" to exit.reason, "status" to exit.status, "exitTime" to exit.timestamp, "pssKiB" to exit.pss, "rssKiB" to exit.rss))
                }
                exits.maxOfOrNull { it.timestamp }?.let { settings.edit().putLong("lastExit", it).apply() }
            }.onFailure { debug("diagnostic.exit_history_unavailable", mapOf("exception" to it.javaClass.simpleName)) }
        }
    }
    fun configure(enabled: Boolean = state.value.enabled, verbose: Boolean = state.value.verbose) {
        mutable.value = DiagnosticConfig(enabled, verbose)
        application?.getSharedPreferences("diagnostics.settings", Context.MODE_PRIVATE)?.edit()?.putBoolean("enabled", enabled)?.putBoolean("verbose", verbose)?.apply()
        if (enabled) info("diagnostic.configuration", mapOf("verbose" to verbose))
    }
    fun debug(event: String, fields: Map<String, Any?> = emptyMap()) = record(DiagnosticLevel.DEBUG, event, fields)
    fun info(event: String, fields: Map<String, Any?> = emptyMap()) = record(DiagnosticLevel.INFO, event, fields)
    fun error(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) = record(DiagnosticLevel.ERROR, event, fields, error)
    fun warn(event: String, fields: Map<String, Any?> = emptyMap()) = record(DiagnosticLevel.WARN, event, fields)
    private fun record(level: DiagnosticLevel, event: String, fields: Map<String, Any?>, error: Throwable? = null) {
        if (!state.value.enabled || level == DiagnosticLevel.DEBUG && !state.value.verbose) return
        if (!writer.execute {
            val config = state.value
            if (config.enabled && (level != DiagnosticLevel.DEBUG || config.verbose)) {
                runCatching { store?.append(level, event, fields, error, config.verbose) }.onFailure { ioFailed = true }
            }
        }) ioFailed = true
    }
    private fun metadata(): Map<String, String> = mapOf("application" to BuildConfig.APPLICATION_ID, "version" to BuildConfig.VERSION_NAME, "versionCode" to BuildConfig.VERSION_CODE.toString(), "androidApi" to Build.VERSION.SDK_INT.toString(), "abis" to Build.SUPPORTED_ABIS.joinToString(), "manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL)
    private fun flush() { writer.flush() }
    suspend fun storageBytes(): Long = withContext(Dispatchers.IO) { store?.size() ?: 0L }
    fun storageProblem() = ioFailed
    suspend fun clear() = withContext(Dispatchers.IO) { flush(); store?.clear(); ioFailed = false }
    suspend fun export(context: Context, uri: Uri, kind: DiagnosticExport) = withContext(Dispatchers.IO) {
        info("diagnostic.export", mapOf("type" to kind.name)); flush()
        val target = requireNotNull(store) { "Diagnose wurde noch nicht initialisiert." }
        requireNotNull(context.contentResolver.openOutputStream(uri, "w")) { "Exportdatei kann nicht geöffnet werden." }.use { target.export(it, kind, metadata() + ("captureLevel" to if (state.value.verbose) "VERBOSE" else "STANDARD")) }
    }
}

/** Captures only host, verb, status and timing. Headers, query strings and bodies stay private. */
class DiagnosticNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request(); val started = System.nanoTime()
        val fields = mapOf("host" to request.url.host, "method" to request.method)
        return try {
            val response = chain.proceed(request)
            val result = fields + mapOf("status" to response.code, "elapsedMs" to (System.nanoTime() - started) / 1_000_000)
            if (response.code >= 400) AppDiagnostics.warn("network.http_error", result) else AppDiagnostics.debug("network.response", result)
            response
        } catch (e: Exception) { AppDiagnostics.error("network.failure", fields, e); throw e }
    }
}
