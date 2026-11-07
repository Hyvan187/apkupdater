package org.uniapk.upgrader.viewmodel

import androidx.lifecycle.viewModelScope
import org.uniapk.upgrader.R
import org.uniapk.upgrader.data.snack.TextSnack
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.glass.ApkAnalyzer
import org.uniapk.upgrader.glass.*
import android.content.Context
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.repository.UpdatesRepository
import org.uniapk.upgrader.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class UpdatesViewModel(
    private val updatesRepository: org.uniapk.upgrader.repository.UpdatesQuery,
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
    private val processState=MutableStateFlow<PackageProcess?>(null)
    val process:StateFlow<PackageProcess?> = processState
    fun hideProcess(){processState.value=null}
    private val categoryState=MutableStateFlow(-1)
    val selectedCategory:StateFlow<Int> = categoryState
    fun selectCategory(index:Int){categoryState.value=index.coerceIn(0,2)}
    private var scanJob: Job? = null
    private var batchJob: Job? = null
    private val operationMutex = UpdateOperations.mutex
    fun cancelVerification(){batchJob?.cancel();processState.update{it?.copy(phase=PackagePhase.CANCELLED,message="Abbruch angefordert; temporäre Dateien werden entfernt.")}}
    private data class Checked(val file: File, val report: ApkAnalyzer.Report,val checkedAt:Long=System.currentTimeMillis())
    private val verified = ConcurrentHashMap<String, Checked>()
    private val installedFacts=ConcurrentHashMap<String,ActualInstalledVersion>()
    private val removedPackages:MutableSet<String> = java.util.Collections.newSetFromMap(ConcurrentHashMap<String,Boolean>())
    private val verifying: MutableSet<Int> = java.util.Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private fun identity(u: AppUpdate) = "${u.artifactKey}:${u.oldVersionCode}"
    init {
        selection.scope.drop(1).onEach { refresh(debounceMillis=350) }.launchIn(viewModelScope)
        subscribeToInstallStatus()
        subscribeToInstallProgress { progress -> state.update { UpdatesUiState.Success(it.mutableUpdates().setProgress(progress)) } }
    }
    fun invalidateVerificationCache() {
        verified.clear()
        setSuccess(state.value.updates().map { if(it.verifiedAt != null) it.copy(verdict=Verdict(Compatibility.UNKNOWN,listOf(Evidence("Prüfdatei","Cache geleert; APK vor Installation erneut prüfen.",Compatibility.UNKNOWN))),sha256=null,verifiedAt=null,updateOwner=null,fileReady=false) else it })
    }
    suspend fun reconcileInstalledUpdates(packageName:String?=null) = withContext(Dispatchers.IO) {
        val current=state.value.updates()
        val names=current.map{it.packageName}.toSet()+installedFacts.keys+removedPackages+listOfNotNull(packageName)
        for(pkg in names) {
            try {
                val info=context.packageManager.getPackageInfo(pkg,0)
                val code=if(android.os.Build.VERSION.SDK_INT>=28)info.longVersionCode else @Suppress("DEPRECATION")info.versionCode.toLong()
                val actual=ActualInstalledVersion(code,info.versionName.orEmpty())
                installedFacts[pkg]=actual;removedPackages.remove(pkg)
                if(current.any{it.packageName==pkg && it.oldVersionCode!=code}) {
                    verified.entries.filter{it.value.report.candidate.packageName==pkg}.forEach{entry->verified.remove(entry.key)?.file?.delete()}
                    org.uniapk.upgrader.diagnostics.AppDiagnostics.info("installed.version_changed",mapOf("package" to pkg,"versionCode" to code))
                }
            } catch(e:android.content.pm.PackageManager.NameNotFoundException){installedFacts.remove(pkg);removedPackages.add(pkg)}
            catch(e:Exception){org.uniapk.upgrader.diagnostics.AppDiagnostics.error("installed.reconcile_failed",mapOf("package" to pkg),e)}
        }
        if(state.value is UpdatesUiState.Success)setSuccess(state.value.updates())
    }
    fun state(): StateFlow<UpdatesUiState> = state
    fun refresh(load: Boolean = true, debounceMillis:Long=0): Job {
        val previous = scanJob
        previous?.cancel()
        return viewModelScope.launch(Dispatchers.IO) {
            previous?.join()
            if(debounceMillis>0)delay(debounceMillis)
            operationMutex.withLock {
            state.value = UpdatesUiState.Loading
            val progressId = ScanProgress.begin()
            var scanLease: java.io.Closeable? = null
            try {
                AppCache.maintain(context)
                scanLease = AppCache.begin()
                updatesRepository.updates(progressId).collect { found ->
                    found.map{it.packageName}.distinct().forEach{pkg->runCatching{context.packageManager.getPackageInfo(pkg,0)}.getOrNull()?.let{info->installedFacts[pkg]=ActualInstalledVersion(if(android.os.Build.VERSION.SDK_INT>=28)info.longVersionCode else @Suppress("DEPRECATION")info.versionCode.toLong(),info.versionName.orEmpty())}}
                    val previous=state.value.updates().associateBy{it.artifactKey}
                    val withEvidence = found.map{u->u.copy(isInstalling=previous[u.artifactKey]?.isInstalling ?: false,verificationError=previous[u.artifactKey]?.verificationError)}.filter { ReleasePolicy.allowed(it,prefs) }.map { u -> verified[identity(u)]?.takeIf { it.file.exists() }?.report?.let { report -> u.copy(verdict = report.verdict, sha256 = report.sha256, updateOwner=report.owner, version = report.candidate.versionName, versionCode = report.candidate.versionCode ?: u.versionCode, minSdk = report.candidate.minSdk, targetSdk = report.targetSdk, abis = report.candidate.abis, verifiedAt = verified[identity(u)]?.checkedAt,fileReady=true) } ?: u }
                    setSuccess(withEvidence)
                }
                if(state.value is UpdatesUiState.Loading) state.value = UpdatesUiState.Success(emptyList())
                prefs.lastCheck.put(System.currentTimeMillis())
                if(state.value.updates().isEmpty()&&SourceHealth.flow.value.any{!it.webOnly}&&SourceHealth.flow.value.none{it.problem!=null}){if(prefs.quietSince.get()==0L)prefs.quietSince.put(System.currentTimeMillis())}else prefs.quietSince.put(0)
                AutomaticDownloads.run(context,prefs,state.value.updates(),downloader,installer){u,file,report->
                    while(verified.isNotEmpty()&&verified.values.sumOf{it.file.length()}+file.length()>ResourcePolicy.READY_BYTES){val oldest=verified.entries.minByOrNull{it.value.checkedAt} ?: break;verified.remove(oldest.key)?.file?.delete()}
                    verified.put(identity(u),Checked(file,report))?.file?.delete()
                    setSuccess(state.value.updates().map{if(it.artifactKey==u.artifactKey)it.withInspection(report,System.currentTimeMillis()) else it});true
                }
                ScanProgress.finish(progressId)
            } catch(e: CancellationException) { ScanProgress.finish(progressId,ScanPhase.CANCELLED); throw e } catch(e: Exception) {
                ScanProgress.finish(progressId,ScanPhase.FAILED)
                org.uniapk.upgrader.diagnostics.AppDiagnostics.error("scan.failed",error=e)
                state.value = UpdatesUiState.Error
                snackBar.snackBar(viewModelScope, TextSnack(e.message ?: "Die Updatequellen konnten nicht abgefragt werden."))
            } finally { scanLease?.close() }
            }
        }.also { scanJob = it }
    }
    fun verify(update: AppUpdate): Job {
        processState.value=PackageProcess(update)
        return startVerification {
        val progressId = ScanProgress.begin("explicit_single_check")
        ScanProgress.verification(progressId, 1)
        ScanProgress.checking(progressId, update.name)
        try {
            ScanProgress.checked(progressId, withContext(org.uniapk.upgrader.diagnostics.DiagnosticContext.element(mapOf("operationId" to "scan-$progressId"))){verifyCandidate(update,org.uniapk.upgrader.util.DownloadTrigger.SINGLE_CHECK)})
            ScanProgress.finish(progressId)
        } catch(e: CancellationException) {
            ScanProgress.finish(progressId, ScanPhase.CANCELLED)
            processState.update{it?.copy(phase=PackagePhase.CANCELLED,message="Teilweise geladene Dateien wurden entfernt.")}
            throw e
        }
        }
    }
    fun verifyAll(): Job = startVerification {
        val progressId = ScanProgress.begin("explicit_batch_check")
        val plan = AutoCheckPolicy.plan(state.value.updates(), prefs.preferredSources.get(),
            prefs.autoVerifyLimitMiB.get()*1024L*1024, prefs.autoCheckCount.get(),
            ResourcePolicy.budget(context.cacheDir.usableSpace,prefs.autoCheckBudgetMiB.get()*1024L*1024), retryErrors=true)
        ScanProgress.verification(progressId, plan.size)
        try {
            for (candidate in plan) {
                ensureActive()
                ScanProgress.checking(progressId, candidate.name)
                ScanProgress.checked(progressId, withContext(org.uniapk.upgrader.diagnostics.DiagnosticContext.element(mapOf("operationId" to "scan-$progressId"))){verifyCandidate(candidate,org.uniapk.upgrader.util.DownloadTrigger.BATCH_CHECK)})
            }
            ScanProgress.finish(progressId)
        } catch(e: CancellationException) {
            ScanProgress.finish(progressId, ScanPhase.CANCELLED)
            throw e
        }
    }
    private fun startVerification(block: suspend CoroutineScope.() -> Unit): Job {
        val previous = batchJob
        previous?.cancel()
        return viewModelScope.launch(Dispatchers.IO) {
            previous?.join()
            operationMutex.withLock { block() }
        }.also { batchJob = it }
    }
    private suspend fun verifyCandidate(update: AppUpdate,trigger:org.uniapk.upgrader.util.DownloadTrigger): Boolean {
        if(update.verdict.status == Compatibility.INCOMPATIBLE || !verifying.add(update.id)) return false
        val supported = update.link is Link.Url || update.link is Link.Xapk || update.link is Link.Play
        if(!supported || update.source == ApkMirrorSource) {
            verifying.remove(update.id)
            snackBar.snackBar(viewModelScope, TextSnack("Für dieses Archiv ist noch keine vollständige Paketprüfung verfügbar."))
            return false
        }
        org.uniapk.upgrader.diagnostics.AppDiagnostics.info("user.apk_download_requested",mapOf("package" to update.packageName,"source" to update.source.name,"trigger" to trigger.name))
        mark(update.id, true)
        var file: File? = null
        var lease: java.io.Closeable? = null
        try {
            lease = AppCache.begin()
            require(ResourcePolicy.budget(context.cacheDir.usableSpace,prefs.autoVerifyLimitMiB.get()*ResourcePolicy.MIB)>0){"Zu wenig freier Speicher: Für sichere Downloads und Installationskopien muss mindestens 1 GiB Reserve verfügbar bleiben."}
            file = downloader.download(update.link, trigger, ResourcePolicy.budget(context.cacheDir.usableSpace,prefs.autoVerifyLimitMiB.get()*ResourcePolicy.MIB)){read,total->if(trigger==org.uniapk.upgrader.util.DownloadTrigger.SINGLE_CHECK)processState.update{it?.copy(bytes=read,total=total)}}
            currentCoroutineContext().ensureActive()
            if(trigger==org.uniapk.upgrader.util.DownloadTrigger.SINGLE_CHECK)processState.update{it?.copy(phase=PackagePhase.INSPECT)}
            val report = withContext(ComputeBudget.dispatcher()){installer.inspect(file, update.packageName)}
            require(ReleasePolicy.allowed(update.copy(version=report.candidate.versionName),prefs)) { "Vorabversion durch deine Filter ausgeblendet." }
            if(trigger!=org.uniapk.upgrader.util.DownloadTrigger.BATCH_CHECK)runCatching { DownloadArchive(context,prefs).publish(file,update.packageName,report.candidate.versionName) }.onFailure { snackBar.snackBar(viewModelScope,TextSnack("Öffentliche Kopie nicht gespeichert: ${it.message}")) }
            // Bound private verification cache; old evidence may require re-checking before installation.
            if(verified.values.sumOf{it.file.length()}+file.length()>ResourcePolicy.READY_BYTES){
                verified.entries.sortedBy{it.value.file.lastModified()}.forEach { entry ->
                    if(verified.values.sumOf{it.file.length()}+file.length()>ResourcePolicy.READY_BYTES)verified.remove(entry.key)?.file?.delete()
                }
            }
            val retain=trigger!=org.uniapk.upgrader.util.DownloadTrigger.BATCH_CHECK
            if(retain){verified.put(identity(update), Checked(file, report))?.file?.delete();file = null}
            val verdict=if(!retain&&report.verdict.installAllowed)Verdict(Compatibility.UNKNOWN,report.verdict.evidence+Evidence("Prüfdatei","Temporäre Sammelprüfung abgeschlossen; Prüfdatei gelöscht. Zum Installieren ausdrücklich erneut laden.",Compatibility.UNKNOWN)) else report.verdict
            setSuccess(state.value.updates().map { if(it.id == update.id) it.copy(verdict = verdict, fileReady=retain, sha256 = report.sha256, updateOwner=report.owner, version = report.candidate.versionName, versionCode = report.candidate.versionCode ?: it.versionCode, minSdk = report.candidate.minSdk, targetSdk = report.targetSdk, abis = report.candidate.abis, verifiedAt = System.currentTimeMillis(), verificationError = null, isInstalling = false) else it })
            if(trigger==org.uniapk.upgrader.util.DownloadTrigger.SINGLE_CHECK){val updated=state.value.updates().firstOrNull{it.artifactKey==update.artifactKey} ?: update.copy(verdict=report.verdict,fileReady=retain);processState.update{it?.copy(update=updated,phase=if(report.verdict.installAllowed)PackagePhase.READY else PackagePhase.FAILED,evidence=report.verdict.evidence,message=report.verdict.summary)}}
            return true
        } catch(e: CancellationException) { throw e } catch(e: Exception) {
            val message = SourceHealth.describe(e)
            if(trigger==org.uniapk.upgrader.util.DownloadTrigger.SINGLE_CHECK)processState.update{it?.copy(phase=PackagePhase.FAILED,message=message)}
            state.update { s -> UpdatesUiState.Success(s.updates().map { if(it.id == update.id) it.copy(verificationError = message) else it }) }
            if(e !is AutomaticSizeLimit && e !is InspectionException) SourceHealth.failed(update.source.name, e)
            snackBar.snackBar(viewModelScope, TextSnack(message))
            return false
        } finally { file?.delete(); verifying.remove(update.id); mark(update.id, false); lease?.close() }
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
    override fun finishInstall(id: Int) = viewModelScope.launch(Dispatchers.IO) { reconcileInstalledUpdates() }
    override fun downloadAndRootInstall(update: AppUpdate) = viewModelScope.launch { snackBar.snackBar(viewModelScope, TextSnack("Root-Installationen sind deaktiviert.")) }
    override fun downloadAndInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
        operationMutex.withLock {
        if(!update.verdict.installAllowed) { snackBar.snackBar(viewModelScope, TextSnack("Diese APK hat keine Installationsfreigabe.")); return@launch }
        if(processState.value==null)processState.value=PackageProcess(update,PackagePhase.READY,evidence=update.verdict.evidence)
        val checked = verified[identity(update)]
        if(checked == null || !checked.file.exists()) {
            verified.remove(identity(update))
            processState.update{it?.copy(phase=PackagePhase.FAILED,update=update.copy(fileReady=false,verdict=Verdict(Compatibility.UNKNOWN,emptyList())),message="Die temporäre Prüfdatei fehlt. Bitte ausdrücklich erneut laden und vorbereiten.")}
            setSuccess(state.value.updates().map { if(it.id==update.id)it.copy(verdict=Verdict(Compatibility.UNKNOWN,listOf(Evidence("Prüfdatei","Prüfdatei fehlt. APK ausdrücklich erneut herunterladen und prüfen.",Compatibility.UNKNOWN))),verifiedAt=null,sha256=null,fileReady=false) else it })
            snackBar.snackBar(viewModelScope,TextSnack("Prüfdatei fehlt. Bitte die APK über die Quellenauswahl erneut herunterladen."));return@launch
        }
        if(!installer.checkPermission()) {processState.update{it?.copy(phase=PackagePhase.READY,message="Bitte die Android-Erlaubnis für diese Quelle erteilen, zurückkehren und Jetzt installieren erneut drücken.")};return@launch}
        mark(update.id, true)
        var installLease: java.io.Closeable? = null
        try {
            installLease = AppCache.begin()
            val report = installer.inspect(checked.file, update.packageName)
            require(ReleasePolicy.allowed(update.copy(version=report.candidate.versionName),prefs)) { "Vorabversion durch deine Filter ausgeblendet." }
            require(report.sha256 == checked.report.sha256) { "Die geprüfte Datei wurde verändert." }
            require(report.verdict.installAllowed) { report.verdict.summary }
            processState.update{it?.copy(phase=PackagePhase.INSTALLING,message="Android fordert deine Bestätigung an.")}
            installer.install(update.id, update.packageName, checked.file.inputStream(),update.source.name)
            reconcileInstalledUpdates(update.packageName)
            if(prefs.installerPackage.get()=="SYSTEM"){
                verified.remove(identity(update))?.file?.delete()
                processState.update{it?.copy(phase=PackagePhase.DONE,message="Die installierte Version wurde bestätigt.")}
            }
        } catch(e: CancellationException) { throw e } catch(e: Exception) {processState.update{it?.copy(phase=PackagePhase.FAILED,message=e.message ?: "Installation verweigert")};snackBar.snackBar(viewModelScope, TextSnack(e.message ?: "Installation verweigert")) }
        finally { mark(update.id, false); installLease?.close() }
        }
    }
    override fun sendInstallSnack(log: AppInstallStatus) {
        if(log.snack) state.value.updates().find { it.id == log.id }?.let { app -> snackBar.snackBar(viewModelScope, TextSnack(stringer.get(if(log.success) R.string.install_success else R.string.install_failure, app.name))) }
    }
    private fun setSuccess(updates: List<AppUpdate>) {
        state.update {
            val filtered = updates.filter { !InstalledUpdatePolicy.sameDisplayVersion(it.version,it.oldVersion) }.filter { it.packageName !in removedPackages }.mapNotNull { update ->
                val actual=installedFacts[update.packageName]
                if(actual==null) update else InstalledUpdatePolicy.reconcile(update,actual)
            }.filter { ReleasePolicy.allowed(it,prefs) }.filter { it.id !in prefs.ignoredVersions.get() && releaseKey(it) !in prefs.ignoredReleases.get() }.distinctBy { it.artifactKey }
            UpdatesUiState.Success(filtered)
        }
        val filtered = state.value.updates()
        badger.changeUpdatesBadge(filtered.appGroups().count { it.status == Compatibility.COMPATIBLE }.takeIf { it > 0 }?.toString().orEmpty())
    }
    override fun onCleared() { verified.values.forEach { it.file.delete() }; verified.clear(); batchJob?.cancel(); super.onCleared() }
}
