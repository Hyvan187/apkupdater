package com.apkupdater.viewmodel

import androidx.lifecycle.viewModelScope
import com.apkupdater.R
import com.apkupdater.data.snack.TextSnack
import com.apkupdater.data.ui.*
import com.apkupdater.glass.ApkAnalyzer
import com.apkupdater.glass.*
import android.content.Context
import com.apkupdater.prefs.Prefs
import com.apkupdater.repository.UpdatesRepository
import com.apkupdater.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
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
    installLog: InstallLog,
    private val selection: AppSelection,
    private val context: Context
) : InstallViewModel(downloader, installer, prefs, snackBar, stringer, installLog) {
    private val state = MutableStateFlow<UpdatesUiState>(UpdatesUiState.Loading)
    private var scanJob: Job? = null
    private var batchJob: Job? = null
    fun cancelVerification(){batchJob?.cancel()}
    private data class Checked(val file: File, val report: ApkAnalyzer.Report,val checkedAt:Long=System.currentTimeMillis())
    private val verified = ConcurrentHashMap<String, Checked>()
    private val verifying: MutableSet<Int> = java.util.Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private fun identity(u: AppUpdate) = "${u.artifactKey}:${u.oldVersionCode}"
    init {
        selection.scope.drop(1).onEach { refresh() }.launchIn(viewModelScope)
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
                    val previous=state.value.updates().associateBy{it.artifactKey}
                    val withEvidence = found.map{u->u.copy(isInstalling=previous[u.artifactKey]?.isInstalling ?: false,verificationError=previous[u.artifactKey]?.verificationError)}.filter { ReleasePolicy.allowed(it,prefs) }.map { u -> verified[identity(u)]?.takeIf { it.file.exists() }?.report?.let { report -> u.copy(verdict = report.verdict, sha256 = report.sha256, updateOwner=report.owner, version = report.candidate.versionName, versionCode = report.candidate.versionCode ?: u.versionCode, minSdk = report.candidate.minSdk, targetSdk = report.targetSdk, abis = report.candidate.abis, verifiedAt = verified[identity(u)]?.checkedAt) } ?: u }
                    setSuccess(withEvidence)
                }
                if(state.value is UpdatesUiState.Loading) state.value = UpdatesUiState.Success(emptyList())
                if(AutomaticChecks.allowed(context, prefs.autoVerify.get(), prefs.autoVerifyWifiOnly.get())) {
                    AutoCheckPolicy.plan(state.value.updates(), prefs.preferredSources.get(), prefs.autoVerifyLimitMiB.get() * 1024L * 1024,prefs.autoCheckCount.get(),prefs.autoCheckBudgetMiB.get()*1024L*1024).forEach {
                        ensureActive(); verifyCandidate(it, automatic = true)
                    }
                }
            } catch(e: CancellationException) { throw e } catch(e: Exception) {
                state.value = UpdatesUiState.Error
                snackBar.snackBar(viewModelScope, TextSnack(e.message ?: "Die Updatequellen konnten nicht abgefragt werden."))
            }
        }.also { scanJob = it }
    }
    fun verify(update: AppUpdate): Job = viewModelScope.launch(Dispatchers.IO) { verifyCandidate(update) }
    fun verifyAll(): Job { batchJob?.cancel();return viewModelScope.launch(Dispatchers.IO) {
        AutoCheckPolicy.plan(state.value.updates(), prefs.preferredSources.get(), 512L*1024*1024,Int.MAX_VALUE,retryErrors=true).forEach { ensureActive(); verifyCandidate(it) }
    }
    .also{batchJob=it}
    }
    private suspend fun verifyCandidate(update: AppUpdate, automatic: Boolean = false) {
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
            require(context.cacheDir.usableSpace > 256L*1024*1024){"Zu wenig freier Speicher für eine sichere APK-Prüfung."}
            file = downloader.download(url, minOf(if(automatic) prefs.autoVerifyLimitMiB.get() * 1024L * 1024 else 512L * 1024 * 1024, context.cacheDir.usableSpace-128L*1024*1024))
            currentCoroutineContext().ensureActive()
            val report = installer.inspect(file, update.packageName)
            require(ReleasePolicy.allowed(update.copy(version=report.candidate.versionName),prefs)) { "Vorabversion durch deine Filter ausgeblendet." }
            if(!automatic) runCatching { DownloadArchive(context,prefs).publish(file,update.packageName,report.candidate.versionName) }.onFailure { snackBar.snackBar(viewModelScope,TextSnack("Öffentliche Kopie nicht gespeichert: ${it.message}")) }
            // Bound private verification cache; old evidence may require re-checking before installation.
            if(verified.values.sumOf{it.file.length()}+file.length()>768L*1024*1024){
                verified.entries.sortedBy{it.value.file.lastModified()}.forEach { entry ->
                    if(verified.values.sumOf{it.file.length()}+file.length()>768L*1024*1024)verified.remove(entry.key)?.file?.delete()
                }
            }
            verified.put(identity(update), Checked(file, report))?.file?.delete()
            file = null
            state.update { s -> UpdatesUiState.Success(s.updates().map { if(it.id == update.id) it.copy(verdict = report.verdict, sha256 = report.sha256, updateOwner=report.owner, version = report.candidate.versionName, versionCode = report.candidate.versionCode ?: it.versionCode, minSdk = report.candidate.minSdk, targetSdk = report.targetSdk, abis = report.candidate.abis, verifiedAt = System.currentTimeMillis(), verificationError = null, isInstalling = false) else it }) }
        } catch(e: CancellationException) { throw e } catch(e: Exception) {
            val message = SourceHealth.describe(e)
            state.update { s -> UpdatesUiState.Success(s.updates().map { if(it.id == update.id) it.copy(verificationError = message) else it }) }
            if(e !is AutomaticSizeLimit && e !is InspectionException) SourceHealth.failed(update.source.name, e)
            if(!automatic) snackBar.snackBar(viewModelScope, TextSnack(message))
        } finally { file?.delete(); verifying.remove(update.id); mark(update.id, false) }
    }
    private fun mark(id: Int, active: Boolean) = state.update { UpdatesUiState.Success(it.mutableUpdates().setIsInstalling(id, active)) }
    fun installAll() = viewModelScope.launch(Dispatchers.IO) { state.value.updates().filter { it.verdict.installAllowed }.forEach { downloadAndInstall(it).join() } }
    fun prefer(update: AppUpdate) { prefs.preferredSources.put(prefs.preferredSources.get() + (update.packageName to update.source.name)) }
    fun preferred(packageName: String) = prefs.preferredSources.get()[packageName]
    fun ignoreRelease(update: AppUpdate) {
        prefs.ignoredReleases.put((prefs.ignoredReleases.get() + releaseKey(update)).distinct())
        setSuccess(state.value.updates())
    }
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
        if(checked == null || !checked.file.exists()) { verifyCandidate(update); snackBar.snackBar(viewModelScope, TextSnack("Prüfdatei erneut geladen. Bitte die angezeigte Version kontrollieren und Installation erneut bestätigen.")); return@launch }
        if(!installer.checkPermission()) return@launch
        mark(update.id, true)
        try {
            val report = installer.inspect(checked.file, update.packageName)
            require(ReleasePolicy.allowed(update.copy(version=report.candidate.versionName),prefs)) { "Vorabversion durch deine Filter ausgeblendet." }
            require(report.sha256 == checked.report.sha256) { "Die geprüfte Datei wurde verändert." }
            require(report.verdict.installAllowed) { report.verdict.summary }
            installer.install(update.id, update.packageName, checked.file.inputStream(),update.source.name)
        } catch(e: CancellationException) { throw e } catch(e: Exception) { snackBar.snackBar(viewModelScope, TextSnack(e.message ?: "Installation verweigert")) }
        finally { mark(update.id, false) }
    }
    override fun sendInstallSnack(log: AppInstallStatus) {
        if(log.snack) state.value.updates().find { it.id == log.id }?.let { app -> snackBar.snackBar(viewModelScope, TextSnack(stringer.get(if(log.success) R.string.install_success else R.string.install_failure, app.name))) }
    }
    private fun setSuccess(updates: List<AppUpdate>) {
        val filtered = updates.filter { ReleasePolicy.allowed(it,prefs) }.filter { it.id !in prefs.ignoredVersions.get() && releaseKey(it) !in prefs.ignoredReleases.get() }
            .distinctBy { it.artifactKey }
        state.value = UpdatesUiState.Success(filtered)
        badger.changeUpdatesBadge(filtered.appGroups().count { it.status == Compatibility.COMPATIBLE }.takeIf { it > 0 }?.toString().orEmpty())
    }
    override fun onCleared() { verified.values.forEach { it.file.delete() }; verified.clear(); batchJob?.cancel(); super.onCleared() }
}
