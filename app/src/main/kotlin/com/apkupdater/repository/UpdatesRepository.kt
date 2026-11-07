package com.apkupdater.repository

import android.util.Log
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.prefs.Prefs
import com.apkupdater.util.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers


class UpdatesRepository(
    private val appsRepository: AppsRepository,
    private val apkMirrorRepository: ApkMirrorRepository,
    private val gitHubRepository: GitHubRepository,
    private val fdroidRepository: FdroidRepository,
    private val izzyRepository: FdroidRepository,
    private val aptoideRepository: AptoideRepository,
    private val apkPureRepository: ApkPureRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val context: android.content.Context,
    private val prefs: Prefs
) {

    fun updates() = flow<List<AppUpdate>> {
        appsRepository.getApps().collect { result ->
            result.onSuccess { apps ->
                val filtered = apps.filter { !it.ignored }.filter {
                    com.apkupdater.glass.CategoryPolicy.matches(com.apkupdater.glass.AppScope.entries.getOrElse(prefs.scanScope.get()) { com.apkupdater.glass.AppScope.ALL }, it.system, it.packageName)
                }
                com.apkupdater.glass.SourceHealth.reset()
                val sources = mutableListOf<Pair<String, Flow<List<AppUpdate>>>>()
                if (prefs.useApkMirror.get()) sources.add("ApkMirror" to apkMirrorRepository.updates(filtered))
                if (prefs.useGitHub.get()) sources.add("GitHub" to gitHubRepository.updates(filtered))
                if (prefs.useFdroid.get()) sources.add("F-Droid (Main)" to fdroidRepository.updates(filtered))
                if (prefs.useIzzy.get()) sources.add("F-Droid (Izzy)" to izzyRepository.updates(filtered))
                if (prefs.useAptoide.get()) sources.add("Aptoide" to aptoideRepository.updates(filtered))
                if (prefs.useApkPure.get()) sources.add("ApkPure" to apkPureRepository.updates(filtered))
                if (prefs.useGitLab.get()) sources.add("GitLab" to gitLabRepository.updates(filtered))
                if (prefs.usePlay.get()) sources.add("Play" to playRepository.updates(filtered))

                if (sources.isNotEmpty()) {
                    sources.map { (name, source) -> com.apkupdater.glass.SourceHealth.observe(name, emptyList<AppUpdate>(), source.flowOn(Dispatchers.IO)) }
                        .combine { updates ->
                            val detection = com.apkupdater.glass.DeviceDetection(context, prefs)
                            val assessed = updates.flatMap { it }.map { u ->
                                val installed = apps.firstOrNull { it.packageName == u.packageName }
                                u.copy(system = installed?.system ?: detection.isSystem(u.packageName), verdict = com.apkupdater.glass.CompatibilityEngine.evaluate(
                                    detection.profile(),
                                    com.apkupdater.glass.InstalledPackage(u.packageName, installed?.versionCode ?: u.oldVersionCode, detection.isRomSensitive(u.packageName), installed?.version ?: u.oldVersion, installed?.targetSdk, installed?.system ?: detection.isSystem(u.packageName)),
                                    com.apkupdater.glass.Candidate(u.packageName, u.versionCode.takeIf { it > 0 }, minSdk = u.minSdk, abis = u.abis, versionName = u.version, targetSdk = u.targetSdk)
                                ))
                            }
                            // No hash means identity is unproven. Keep regional/ABI variants separate.
                            prefs.lastCheck.put(System.currentTimeMillis())
                            emit(assessed.filter { com.apkupdater.glass.ReleasePolicy.allowed(it, prefs) }.filter { it.id !in prefs.ignoredVersions.get() && com.apkupdater.glass.releaseKey(it) !in prefs.ignoredReleases.get() }.distinctBy { it.artifactKey })
                        }
                        .collect()
                } else {
                    emit(emptyList())
                }
            }.onFailure {
                throw it
            }
        }
    }.catch {
        throw it
    }

}
