package org.uniapk.upgrader.viewmodel

import android.util.Log
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.uniapk.upgrader.R
import org.uniapk.upgrader.data.snack.TextSnack
import org.uniapk.upgrader.data.ui.ApkMirrorSource
import org.uniapk.upgrader.data.ui.AppInstallProgress
import org.uniapk.upgrader.data.ui.AppInstallStatus
import org.uniapk.upgrader.data.ui.AppUpdate
import org.uniapk.upgrader.data.ui.Link
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.util.Downloader
import org.uniapk.upgrader.util.InstallLog
import org.uniapk.upgrader.util.SessionInstaller
import org.uniapk.upgrader.util.SnackBar
import org.uniapk.upgrader.util.Stringer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach


abstract class InstallViewModel(
    private val downloader: Downloader,
    private val installer: SessionInstaller,
    private val prefs: Prefs,
    private val snackBar: SnackBar,
    private val stringer: Stringer,
    private val installLog: InstallLog
): ViewModel() {

    fun install(update: AppUpdate, uriHandler: UriHandler) {
        if(!org.uniapk.upgrader.glass.ReleasePolicy.allowed(update,prefs)) { snackBar.snackBar(viewModelScope,TextSnack("Vorabversion durch deine Filter ausgeblendet.")); return }
        when (update.source) {
            ApkMirrorSource -> uriHandler.openUri((update.link as Link.Url).link)
            else -> {
                if (false) {
                    downloadAndRootInstall(update)
                } else {
                    downloadAndInstall(update)
                }
            }
        }
    }

    protected fun subscribeToInstallStatus() = installLog.status().onEach {
        sendInstallSnack(it)
        if (it.success) {
            finishInstall(it.id).join()
        } else {
            installLog.emitProgress(AppInstallProgress(it.id, 0L))
            cancelInstall(it.id).join()
        }
    }.launchIn(viewModelScope)

    protected fun subscribeToInstallProgress(
        block: (AppInstallProgress) -> Unit
    ) = installLog.progress().onEach {
        block(it)
    }.launchIn(viewModelScope)

    protected fun downloadAndRootInstall(id: Int, link: Link) = cancelInstall(id)

    protected suspend fun downloadAndInstall(id: Int, packageName: String, link: Link) {
        var file: java.io.File? = null
        val lease = org.uniapk.upgrader.glass.AppCache.begin()
        try {
            installLog.emitProgress(AppInstallProgress(id, 0L))
            org.uniapk.upgrader.diagnostics.AppDiagnostics.info("user.apk_download_requested",mapOf("package" to packageName,"trigger" to "SEARCH_INSTALL"))
            file = downloader.download(link,org.uniapk.upgrader.util.DownloadTrigger.SEARCH_INSTALL)
            installer.install(id, packageName, file.inputStream())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            org.uniapk.upgrader.diagnostics.AppDiagnostics.error("install.download_failed", mapOf("package" to packageName), e)
            snackBar.snackBar(viewModelScope, TextSnack(e.message ?: "Installation verweigert"))
        } finally {
            file?.delete()
            lease.close()
            cancelInstall(id)
        }
    }

    protected abstract fun sendInstallSnack(log: AppInstallStatus)
    protected abstract fun downloadAndInstall(update: AppUpdate): Job
    protected abstract fun downloadAndRootInstall(update: AppUpdate): Job
    protected abstract fun cancelInstall(id: Int): Job
    protected abstract fun finishInstall(id: Int): Job
}
