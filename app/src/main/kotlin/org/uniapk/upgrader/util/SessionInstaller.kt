package org.uniapk.upgrader.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import org.uniapk.upgrader.data.ui.AppInstallProgress
import org.uniapk.upgrader.data.ui.AppInstallStatus
import org.uniapk.upgrader.glass.ApkAnalyzer
import org.uniapk.upgrader.glass.Compatibility
import org.uniapk.upgrader.glass.DeviceDetection
import org.uniapk.upgrader.prefs.Prefs
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A single guarded path. Every install is re-inspected from the actual downloaded bytes. */
class SessionInstaller(private val context: Context, private val installLog: InstallLog, private val prefs: Prefs, private val history: org.uniapk.upgrader.glass.HistoryStore,private val archiveMetadata:((File,Int)->android.content.pm.PackageInfo?)?=null) {
    companion object { const val INSTALL_ACTION = "org.uniapk.upgrader.INSTALL" }
    private val mutex = Mutex()
    fun inspect(file: File, packageName: String) = org.uniapk.upgrader.glass.AppCache.use {
        try { org.uniapk.upgrader.glass.ApkSetInspector(context, DeviceDetection(context, prefs),ApkAnalyzer(context,DeviceDetection(context,prefs),archiveMetadata)).inspect(file, packageName).also { org.uniapk.upgrader.diagnostics.AppDiagnostics.debug("apk.checked", mapOf("package" to packageName, "status" to it.verdict.status.name)) } }
        catch(e: Exception) { org.uniapk.upgrader.diagnostics.AppDiagnostics.error("apk.check_failed",mapOf("package" to packageName),e); throw e }
    }
    suspend fun install(id: Int, packageName: String, stream: InputStream, source: String="Direktdownload",choice:String=prefs.installerPackage.get()) = install(id, packageName, listOf(stream),source,choice)
    suspend fun install(id: Int, packageName: String, streams: List<InputStream>, source: String="Direktdownload",choice:String=prefs.installerPackage.get()) = mutex.withLock {
        val lease = org.uniapk.upgrader.glass.AppCache.begin()
        val dir = File(context.cacheDir, "preflight-${randomUUID()}")
        try {
            check(dir.mkdirs())
            org.uniapk.upgrader.diagnostics.AppDiagnostics.info("install.started",mapOf("package" to packageName,"parts" to streams.size))
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
            val inspector = org.uniapk.upgrader.glass.ApkSetInspector(context, DeviceDetection(context, prefs),ApkAnalyzer(context,DeviceDetection(context,prefs),archiveMetadata))
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
            require(org.uniapk.upgrader.glass.ReleasePolicy.allowed(report.candidate.versionName,prefs.ignoreAlpha.get(),prefs.ignoreBeta.get(),prefs.ignorePreRelease.get())) { "Vorabversion durch deine Filter ausgeblendet." }
            require(report.verdict.installAllowed) {
                report.verdict.evidence.filter { it.status != Compatibility.COMPATIBLE }.joinToString("; ") { "${it.property}: ${it.detail}" }
            }
            val published=org.uniapk.upgrader.glass.DownloadArchive(context,prefs).publish(file,packageName,report.candidate.versionName)
            val publishedDigest=prefs.downloadArchive.get().entries.firstOrNull { it.value==published.toString() }?.key
            val chosen=choice
            require(chosen!=org.uniapk.upgrader.glass.InstallerChoices.ASK){"Bitte zuerst einen Installer im Auswahlfenster wählen."}
            if(chosen!="SYSTEM") {
                val external=if(prepared.files.size==1)prepared.files.single() else File(dir,"verified.apks").also{archive->
                    java.util.zip.ZipOutputStream(archive.outputStream()).use{zip->prepared.files.forEachIndexed{index,apk->zip.putNextEntry(java.util.zip.ZipEntry(if(index==0)"base.apk" else "split-$index.apk"));apk.inputStream().use{it.copyTo(zip)};zip.closeEntry()}}
                }
                val staged=org.uniapk.upgrader.glass.InstallerChoices.handoff(context,external,packageName,chosen)
                history.pending(org.uniapk.upgrader.glass.PendingInstall(packageName,appName,beforeName,report.candidate.versionName,beforeCode,report.candidate.versionCode ?: -1L,source,System.currentTimeMillis(),staged.absolutePath,installer=chosen,downloadDigest=publishedDigest))
                org.uniapk.upgrader.diagnostics.AppDiagnostics.info("install.external_handoff",mapOf("installer" to chosen,"format" to org.uniapk.upgrader.glass.InstallerChoices.format(external).name))
                false
            } else {
                val pending = org.uniapk.upgrader.glass.PendingInstall(packageName,appName,beforeName,report.candidate.versionName,beforeCode,report.candidate.versionCode ?: -1L,source,System.currentTimeMillis(),installer="Android PackageInstaller",downloadDigest=publishedDigest)
                // Persist before commit: a self-update can terminate this process.
                history.pending(pending)
                try { commit(id, packageName, prepared.files) }
                catch (e: Exception) { history.cancelPending(pending); throw e }
                val expected=requireNotNull(report.candidate.versionCode)
                val actual = org.uniapk.upgrader.glass.awaitInstalledVersion(expected) {
                    try {
                        val info=context.packageManager.getPackageInfo(packageName,0)
                        org.uniapk.upgrader.glass.ActualInstalledVersion(if(Build.VERSION.SDK_INT>=28)info.longVersionCode else @Suppress("DEPRECATION")info.versionCode.toLong(),info.versionName.orEmpty())
                    } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
                    catch (e: Exception) {
                        org.uniapk.upgrader.diagnostics.AppDiagnostics.error("install.version_query_failed",mapOf("package" to packageName),e)
                        null
                    }
                }
                history.complete(pending, actual)
                org.uniapk.upgrader.glass.DownloadArchive(context,prefs).installed(file)
                installLog.emitStatus(AppInstallStatus(true,id,true))
                true
            }
            }
        } catch (e: Exception) {
            if(e is InstallAbortedException)org.uniapk.upgrader.diagnostics.AppDiagnostics.info("install.cancelled") else org.uniapk.upgrader.diagnostics.AppDiagnostics.error("install.failed", mapOf("package" to packageName, "cancelled" to (e is kotlinx.coroutines.CancellationException)), e)
            installLog.emitStatus(AppInstallStatus(false,id,true))
            throw e
        } finally {
            streams.forEach { runCatching { it.close() } }
            dir.deleteRecursively()
            lease.close()
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
                            val status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
                            val statusMessage=org.uniapk.upgrader.diagnostics.DiagnosticRedaction.text(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
                            org.uniapk.upgrader.diagnostics.AppDiagnostics.info("install.android_status",mapOf("status" to status,"message" to statusMessage))
                            when (status) {
                                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                    val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                                    if (confirm == null) continuation.resumeWithException(IllegalStateException("Android liefert keinen Bestätigungsdialog"))
                                    else runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { continuation.resumeWithException(it) }
                                }
                                PackageInstaller.STATUS_SUCCESS -> { success = true; continuation.resume(Unit) }
                                PackageInstaller.STATUS_FAILURE_ABORTED -> continuation.resumeWithException(InstallAbortedException())
                                else -> continuation.resumeWithException(IllegalStateException("Android-Installerstatus $status: ${statusMessage.ifBlank{"Installation verweigert"}}"))
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
            // Success is emitted only after the installed package version has been confirmed.
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
    fun checkPermission(choice:String=prefs.installerPackage.get()):Boolean {
        if(choice!="SYSTEM")return true
        if(Build.VERSION.SDK_INT>=26&&!context.packageManager.canRequestPackageInstalls()){
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return false
        }
        return true
    }
    suspend fun installXapk(id: Int, packageName: String, stream: InputStream) {
        install(id, packageName, stream, "APK-Set")
    }
}
