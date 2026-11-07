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
class SessionInstaller(private val context: Context, private val installLog: InstallLog, private val prefs: Prefs, private val history: com.apkupdater.glass.HistoryStore) {
    companion object { const val INSTALL_ACTION = "com.hyvan187.apkupdater.INSTALL" }
    private val mutex = Mutex()
    fun inspect(file: File, packageName: String) = com.apkupdater.glass.ApkSetInspector(context, DeviceDetection(context, prefs)).inspect(file, packageName)
    suspend fun install(id: Int, packageName: String, stream: InputStream, source: String="Direktdownload") = install(id, packageName, listOf(stream),source)
    suspend fun install(id: Int, packageName: String, streams: List<InputStream>, source: String="Direktdownload") = mutex.withLock {
        val dir = File(context.cacheDir, "preflight-${randomUUID()}").apply { mkdirs() }
        try {
            require(streams.size in 1..128) { "Ein Paket benötigt zwischen 1 und 128 APK-Bestandteile." }
            var total = 0L
            val staged = streams.mapIndexed { index, input ->
                File(dir, "input-$index.apk").also { file ->
                    input.use { source -> file.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val n = source.read(buffer); if (n < 0) break
                            total += n
                            require(total <= 512L * 1024 * 1024) { "APK-Set überschreitet das Prüflimit von 512 MiB." }
                            require(dir.usableSpace > 64L * 1024 * 1024) { "Zu wenig freier Speicher für die Installation." }
                            output.write(buffer, 0, n)
                            installLog.emitProgress(AppInstallProgress(id, total))
                        }
                    } }
                }
            }
            val inspector = com.apkupdater.glass.ApkSetInspector(context, DeviceDetection(context, prefs))
            inspector.prepareFiles(staged, packageName).use { prepared ->
            val file = if (staged.size == 1) staged.single() else File(dir, "package.apks").also { archive ->
                java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
                    prepared.files.forEachIndexed { index, apk ->
                        zip.putNextEntry(java.util.zip.ZipEntry(if(index == 0) "base.apk" else "split-$index.apk"))
                        apk.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
            }
            val before=runCatching{context.packageManager.getPackageInfo(packageName,0)}.getOrNull()
            val beforeCode=before?.let{if(Build.VERSION.SDK_INT>=28)it.longVersionCode else @Suppress("DEPRECATION")it.versionCode.toLong()} ?: -1L
            val beforeName=before?.versionName.orEmpty()
            val appName=before?.applicationInfo?.let{runCatching{context.packageManager.getApplicationLabel(it).toString()}.getOrNull()} ?: packageName
            val report = prepared.report
            require(com.apkupdater.glass.ReleasePolicy.allowed(report.candidate.versionName,prefs.ignoreAlpha.get(),prefs.ignoreBeta.get(),prefs.ignorePreRelease.get())) { "Vorabversion durch deine Filter ausgeblendet." }
            require(report.verdict.installAllowed) {
                report.verdict.evidence.filter { it.status != Compatibility.COMPATIBLE }.joinToString("; ") { "${it.property}: ${it.detail}" }
            }
            com.apkupdater.glass.DownloadArchive(context,prefs).publish(file,packageName,report.candidate.versionName)
            val chosen=prefs.installerPackage.get()
            if(chosen!="SYSTEM") {
                require(prepared.files.size == 1 && prepared.files.single() == file) { "APK-Archive und APK-Sets benötigen den Android PackageInstaller. Bitte diesen in den Einstellungen auswählen." }
                val staged=com.apkupdater.glass.InstallerChoices.handoff(context,prepared.files.single(),packageName,chosen)
                history.pending(com.apkupdater.glass.PendingInstall(packageName,appName,beforeName,report.candidate.versionName,beforeCode,report.candidate.versionCode ?: -1L,source,System.currentTimeMillis(),staged.absolutePath))
            } else {
                commit(id, packageName, prepared.files)
                history.record(com.apkupdater.glass.InstallHistory(packageName,appName,beforeName,report.candidate.versionName,beforeCode,report.candidate.versionCode ?: -1L,source,"Android PackageInstaller",System.currentTimeMillis(),true))
                com.apkupdater.glass.DownloadArchive(context,prefs).installed(file)
            }
            }
        } finally {
            streams.forEach { runCatching { it.close() } }
            dir.deleteRecursively()
        }
    }
    private suspend fun commit(id: Int, packageName: String, files: List<File>) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(files.sumOf { it.length() })
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
                            files.forEachIndexed { index, file ->
                                session.openWrite(if(index == 0) "base.apk" else "split-$index.apk", 0, file.length()).use { out ->
                                    file.inputStream().use { it.copyTo(out) }
                                    session.fsync(out)
                                }
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
        if(prefs.installerPackage.get()!="SYSTEM") {
            val available=com.apkupdater.glass.InstallerChoices.available(context).any{it.first==prefs.installerPackage.get()}
            if(!available)android.os.Handler(android.os.Looper.getMainLooper()).post{android.widget.Toast.makeText(context,"Gewählter Installer nicht verfügbar. Bitte Einstellungen prüfen.",android.widget.Toast.LENGTH_LONG).show()}
            return available
        }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        return true
    }
    suspend fun installXapk(id: Int, packageName: String, stream: InputStream) {
        install(id, packageName, stream, "APK-Set")
    }
}
