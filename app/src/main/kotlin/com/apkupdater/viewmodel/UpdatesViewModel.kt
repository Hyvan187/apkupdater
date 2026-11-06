package com.apkupdater.viewmodel

import androidx.lifecycle.viewModelScope
import com.apkupdater.R
import com.apkupdater.data.snack.TextSnack
import com.apkupdater.data.ui.*
import com.apkupdater.glass.ApkAnalyzer
import com.apkupdater.glass.Compatibility
import com.apkupdater.prefs.Prefs
import com.apkupdater.repository.UpdatesRepository
import com.apkupdater.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class UpdatesViewModel(
    private val updatesRepository: UpdatesRepository,
    private val installer: SessionInstaller,
    private val prefs: Prefs,
    private val badger: Badger,
    private val downloader: Downloader,
    private val snackBar: SnackBar,
    private val stringer: Stringer,
    installLog: InstallLog
) : InstallViewModel(downloader, installer, prefs, snackBar, stringer, installLog) {
    private val state = MutableStateFlow<UpdatesUiState>(UpdatesUiState.Loading)
    private var scanJob: Job? = null
    private data class Checked(val file: File, val report: ApkAnalyzer.Report)
    private val verified = ConcurrentHashMap<String, Checked>()
    private val verifying: MutableSet<Int> = java.util.Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private fun identity(u: AppUpdate) = "${u.artifactKey}:${u.oldVersionCode}"
    init {
        subscribeToInstallStatus()
        subscribeToInstallProgress { progress -> state.update { UpdatesUiState.Success(it.mutableUpdates().setProgress(progress)) } }
    }
    fun state(): StateFlow<UpdatesUiState> = state
    fun refresh(load: Boolean = true): Job {
        val previous = scanJob
        previous?.cancel()
        return viewModelScope.launch(Dispatchers.IO) {
            previous?.join()
            state.value = UpdatesUiState.Loading
            try {
                updatesRepository.updates().collect { found ->
                    val withEvidence = found.map { u -> verified[identity(u)]?.takeIf { it.file.exists() }?.report?.let { report -> u.copy(verdict = report.verdict, sha256 = report.sha256, version = report.candidate.versionName, versionCode = report.candidate.versionCode ?: u.versionCode) } ?: u }
                    setSuccess(withEvidence)
                }
                if(state.value is UpdatesUiState.Loading) state.value = UpdatesUiState.Success(emptyList())
            } catch(e: CancellationException) { throw e } catch(e: Exception) {
                state.value = UpdatesUiState.Error
                snackBar.snackBar(viewModelScope, TextSnack(e.message ?: "Die Updatequellen konnten nicht abgefragt werden."))
            }
        }.also { scanJob = it }
    }
    fun verify(update: AppUpdate): Job = viewModelScope.launch(Dispatchers.IO) { verifyCandidate(update) }
    fun verifyAll(): Job = viewModelScope.launch(Dispatchers.IO) {
        state.value.updates().filter { it.verdict.status == Compatibility.UNKNOWN && it.source != ApkMirrorSource && it.link is Link.Url }.forEach { ensureActive(); verifyCandidate(it) }
    }
    private suspend fun verifyCandidate(update: AppUpdate) {
        if(update.verdict.status == Compatibility.INCOMPATIBLE || !verifying.add(update.id)) return
        val url = (update.link as? Link.Url)?.link
        if(url == null || update.source == ApkMirrorSource) {
            verifying.remove(update.id)
            snackBar.snackBar(viewModelScope, TextSnack("Für dieses Archiv ist noch keine vollständige Paketprüfung verfügbar."))
            return
        }
        mark(update.id, true)
        var file: File? = null
        try {
            file = downloader.download(url)
            currentCoroutineContext().ensureActive()
            val report = installer.inspect(file, update.packageName)
            verified.put(identity(update), Checked(file, report))?.file?.delete()
            file = null
            state.update { s -> UpdatesUiState.Success(s.updates().map { if(it.id == update.id) it.copy(verdict = report.verdict, sha256 = report.sha256, version = report.candidate.versionName, versionCode = report.candidate.versionCode ?: it.versionCode, isInstalling = false) else it }) }
        } catch(e: CancellationException) { throw e } catch(e: Exception) {
            snackBar.snackBar(viewModelScope, TextSnack(e.message ?: "Die APK konnte nicht geprüft werden."))
        } finally { file?.delete(); verifying.remove(update.id); mark(update.id, false) }
    }
    private fun mark(id: Int, active: Boolean) = state.update { UpdatesUiState.Success(it.mutableUpdates().setIsInstalling(id, active)) }
    fun installAll() = viewModelScope.launch(Dispatchers.IO) { state.value.updates().filter { it.verdict.installAllowed }.forEach { downloadAndInstall(it).join() } }
    fun ignoreVersion(id: Int) = viewModelScope.launch {
        prefs.ignoredVersions.put((prefs.ignoredVersions.get() + id).distinct())
        setSuccess(state.value.updates())
    }
    override fun cancelInstall(id: Int) = viewModelScope.launch { mark(id, false) }
    override fun finishInstall(id: Int) = viewModelScope.launch { setSuccess(state.value.updates().filter { it.id != id }) }
    override fun downloadAndRootInstall(update: AppUpdate) = viewModelScope.launch { snackBar.snackBar(viewModelScope, TextSnack("Root-Installationen sind deaktiviert.")) }
    override fun downloadAndInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
        if(!update.verdict.installAllowed) { snackBar.snackBar(viewModelScope, TextSnack("Diese APK hat keine Installationsfreigabe.")); return@launch }
        val checked = verified[identity(update)]
        if(checked == null || !checked.file.exists()) { snackBar.snackBar(viewModelScope, TextSnack("Bitte die tatsächliche APK erneut prüfen.")); return@launch }
        if(!installer.checkPermission()) return@launch
        mark(update.id, true)
        try {
            val report = installer.inspect(checked.file, update.packageName)
            require(report.sha256 == checked.report.sha256) { "Die geprüfte Datei wurde verändert." }
            require(report.verdict.installAllowed) { report.verdict.summary }
            installer.install(update.id, update.packageName, checked.file.inputStream())
        } catch(e: CancellationException) { throw e } catch(e: Exception) { snackBar.snackBar(viewModelScope, TextSnack(e.message ?: "Installation verweigert")) }
        finally { mark(update.id, false) }
    }
    override fun sendInstallSnack(log: AppInstallStatus) {
        if(log.snack) state.value.updates().find { it.id == log.id }?.let { app -> snackBar.snackBar(viewModelScope, TextSnack(stringer.get(if(log.success) R.string.install_success else R.string.install_failure, app.name))) }
    }
    private fun setSuccess(updates: List<AppUpdate>) {
        val filtered = updates.filter { it.id !in prefs.ignoredVersions.get() }
            .groupBy { if(it.sha256 == null) "unknown:${it.id}" else "${it.packageName}:${it.versionCode}:${it.sha256}" }
            .values.map { group -> group.first().copy(additionalSources = group.flatMap { listOf(it.source.name) + it.additionalSources }.distinct()) }
        state.value = UpdatesUiState.Success(filtered)
        badger.changeUpdatesBadge(filtered.count { it.verdict.installAllowed }.takeIf { it > 0 }?.toString().orEmpty())
    }
    override fun onCleared() { verified.values.forEach { it.file.delete() }; verified.clear(); super.onCleared() }
}
