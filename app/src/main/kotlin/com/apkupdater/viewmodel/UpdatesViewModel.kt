package com.apkupdater.viewmodel

import android.content.pm.PackageInstaller
import androidx.lifecycle.viewModelScope
import com.apkupdater.R
import com.apkupdater.data.snack.TextSnack
import com.apkupdater.data.ui.AppInstallStatus
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.UpdatesUiState
import com.apkupdater.data.ui.removeId
import com.apkupdater.data.ui.setIsInstalling
import com.apkupdater.data.ui.setProgress
import com.apkupdater.prefs.Prefs
import com.apkupdater.repository.UpdatesRepository
import com.apkupdater.util.Badger
import com.apkupdater.util.Downloader
import com.apkupdater.util.InstallLog
import com.apkupdater.util.SessionInstaller
import com.apkupdater.util.SnackBar
import com.apkupdater.util.Stringer
import com.apkupdater.util.launchWithMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex


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

	private val mutex = Mutex()
	private val state = MutableStateFlow<UpdatesUiState>(UpdatesUiState.Loading)

	init {
		subscribeToInstallStatus()
		subscribeToInstallProgress { progress ->
			state.value = UpdatesUiState.Success(state.value.mutableUpdates().setProgress(progress))
		}
	}

	fun state(): StateFlow<UpdatesUiState> = state

	fun refresh(load: Boolean = true) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		if (load) state.value = UpdatesUiState.Loading
		badger.changeUpdatesBadge("")
		updatesRepository.updates().collect {
			setSuccess(it)
		}
	}

    fun verify(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
        val url = (update.link as? com.apkupdater.data.ui.Link.Url)?.link
        if (url == null) {
            snackBar.snackBar(viewModelScope, TextSnack("Dieses Format kann noch nicht vollständig geprüft werden."))
            return@launch
        }
        state.value = UpdatesUiState.Success(state.value.mutableUpdates().setIsInstalling(update.id, true))
        try {
            val file = downloader.download(url)
            val report = try { installer.inspect(file, update.packageName) } finally { file.delete() }
            val result = state.value.mutableUpdates().map {
                if (it.id == update.id) it.copy(verdict = report.verdict, sha256 = report.sha256, isInstalling = false) else it
            }
            setSuccess(result)
        } catch (e: Exception) {
            state.value = UpdatesUiState.Success(state.value.mutableUpdates().setIsInstalling(update.id, false))
            snackBar.snackBar(viewModelScope, TextSnack(e.message ?: "APK-Prüfung fehlgeschlagen"))
        }
    }

	fun installAll() = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		if(installer.checkPermission()) {
			state.value.updates().filter { it.verdict.installAllowed }.forEach { update ->
				if (state.value.updates().any { it.id == update.id && it.isInstalling }) return@forEach
				state.value = UpdatesUiState.Success(state.value.mutableUpdates().setIsInstalling(update.id, true))
				viewModelScope.launch(Dispatchers.IO) {
					downloadAndInstall(update.id, update.packageName, update.link)
				}
			}
		}
	}

	fun ignoreVersion(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val ignored = prefs.ignoredVersions.get().toMutableList()
		if (ignored.contains(id)) ignored.remove(id) else ignored.add(id)
		prefs.ignoredVersions.put(ignored)
		setSuccess(state.value.mutableUpdates())
	}

	override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		state.value = UpdatesUiState.Success(state.value.mutableUpdates().setIsInstalling(id, false))
		installer.finish()
	}

	override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		setSuccess(state.value.mutableUpdates().removeId(id))
		installer.finish()
	}

	override fun downloadAndRootInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		state.value = UpdatesUiState.Success(state.value.mutableUpdates().setIsInstalling(update.id, true))
		downloadAndRootInstall(update.id, update.link)
	}

	override fun downloadAndInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		if(installer.checkPermission()) {
			state.value = UpdatesUiState.Success(state.value.mutableUpdates().setIsInstalling(update.id, true))
			downloadAndInstall(update.id, update.packageName, update.link)
		}
	}

	override fun sendInstallSnack(log: AppInstallStatus) {
		if (log.snack) {
			state.value.updates().find { log.id == it.id }?.let { app ->
				val message = if (log.success) R.string.install_success else R.string.install_failure
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(message, app.name)))
			}
		}
	}

	private fun List<AppUpdate>.filterIgnoredVersions(ignoredVersions: List<Int>) = this
		.filter { !ignoredVersions.contains(it.id) }

	private fun setSuccess(updates: List<AppUpdate>) = updates.groupBy {
        if (it.sha256 != null) "${it.packageName}:${it.versionCode}:${it.sha256}" else "unknown:${it.id}"
    }.values.map { group -> group.first().copy(additionalSources = group.flatMap { listOf(it.source.name) + it.additionalSources }.distinct()) }
		.filterIgnoredVersions(prefs.ignoredVersions.get())
		.let {
			state.value = UpdatesUiState.Success(it)
			badger.changeUpdatesBadge(it.size.toString())
		}

}
