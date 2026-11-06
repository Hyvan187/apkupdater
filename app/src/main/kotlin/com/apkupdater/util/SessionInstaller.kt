package com.apkupdater.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import com.apkupdater.data.ui.AppInstallProgress
import com.apkupdater.data.ui.AppInstallStatus
import com.apkupdater.glass.ApkAnalyzer
import com.apkupdater.glass.Compatibility
import com.apkupdater.glass.DeviceDetection
import com.apkupdater.prefs.Prefs
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A single guarded path. Every install is re-inspected from the actual downloaded bytes. */
class SessionInstaller(private val context: Context, private val installLog: InstallLog, private val prefs: Prefs) {
    companion object { const val INSTALL_ACTION = "com.hyvan187.apkupdater.INSTALL" }
    private val mutex = Mutex()
    fun inspect(file: File, packageName: String) = ApkAnalyzer(context, DeviceDetection(context, prefs)).analyze(file, packageName)
    suspend fun install(id: Int, packageName: String, stream: InputStream) = install(id, packageName, listOf(stream))
    suspend fun install(id: Int, packageName: String, streams: List<InputStream>) = mutex.withLock {
        val dir = File(context.cacheDir, "preflight-${randomUUID()}").apply { mkdirs() }
        try {
            require(streams.size == 1) { "Split-Sets sind bis zur vollständigen Abhängigkeitsprüfung gesperrt." }
            val file = File(dir, "base.apk")
            streams.single().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        total += n
                        require(total <= 512L * 1024 * 1024) { "APK überschreitet das Prüflimit von 512 MiB." }
                        output.write(buffer, 0, n)
                        installLog.emitProgress(AppInstallProgress(id, total))
                    }
                }
            }
            val report = inspect(file, packageName)
            require(com.apkupdater.glass.ReleasePolicy.allowed(report.candidate.versionName,prefs.ignoreAlpha.get(),prefs.ignoreBeta.get(),prefs.ignorePreRelease.get())) { "Vorabversion durch deine Filter ausgeblendet." }
            require(report.verdict.installAllowed) {
                report.verdict.evidence.filter { it.status != Compatibility.COMPATIBLE }.joinToString("; ") { "${it.property}: ${it.detail}" }
            }
            com.apkupdater.glass.DownloadArchive(context,prefs).publish(file,packageName,report.candidate.versionName)
            commit(id, packageName, file)
            com.apkupdater.glass.DownloadArchive(context,prefs).installed(file)
        } finally {
            streams.forEach { runCatching { it.close() } }
            dir.deleteRecursively()
        }
    }
    private suspend fun commit(id: Int, packageName: String, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val sessionId = installer.createSession(params)
        val action = "$INSTALL_ACTION.${randomUUID()}"
        var registered: BroadcastReceiver? = null
        var success = false
        try {
            withTimeout(180_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(c: Context, intent: Intent) {
                            if (intent.action != action || !continuation.isActive) return
                            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                    val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                                    if (confirm == null) continuation.resumeWithException(IllegalStateException("Android liefert keinen Bestätigungsdialog"))
                                    else runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { continuation.resumeWithException(it) }
                                }
                                PackageInstaller.STATUS_SUCCESS -> { success = true; continuation.resume(Unit) }
                                else -> continuation.resumeWithException(IllegalStateException(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Android hat die Installation verweigert"))
                            }
                        }
                    }
                    registered = receiver
                    if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
                    else @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(receiver, IntentFilter(action))
                    try {
                        installer.openSession(sessionId).use { session ->
                            session.openWrite("base.apk", 0, file.length()).use { out ->
                                file.inputStream().use { it.copyTo(out) }
                                session.fsync(out)
                            }
                            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                            val pending = PendingIntent.getBroadcast(context, sessionId, Intent(action).setPackage(context.packageName), flags)
                            session.commit(pending.intentSender)
                        }
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                    continuation.invokeOnCancellation { runCatching { installer.abandonSession(sessionId) } }
                }
            }
            installLog.emitStatus(AppInstallStatus(true, id, true))
        } finally {
            registered?.let { runCatching { context.unregisterReceiver(it) } }
            if (!success) runCatching { installer.abandonSession(sessionId) }
        }
    }
    fun rootInstall(file: File): Boolean {
        file.delete()
        error("Root-Installation ist in Glass deaktiviert.")
    }
    fun finish() = Unit
    fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        return true
    }
    suspend fun installXapk(id: Int, packageName: String, stream: InputStream) {
        stream.close()
        error("APKM, APKS und XAPK bleiben bis zur vollständigen Split-Prüfung gesperrt.")
    }
}
