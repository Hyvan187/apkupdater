package org.uniapk.upgrader.repository

import android.util.Log
import org.uniapk.upgrader.data.ui.AppUpdate
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.util.combine
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
) : UpdatesQuery {

    override fun updates(progressId: Long?) = flow<List<AppUpdate>> {
        appsRepository.getApps().collect { result ->
            result.onSuccess { apps ->
                org.uniapk.upgrader.diagnostics.AppDiagnostics.info("scan.app_inventory",mapOf("operationId" to progressId?.let{"scan-$it"},"visibleApps" to apps.size,"scope" to prefs.scanScope.get(),"detectedRegion" to org.uniapk.upgrader.glass.DeviceDetection(context,prefs).region().name,"comparisonRegion" to prefs.manualRegion.get(),"modelSimulation" to prefs.simulatedModel.get().isNotBlank()))
                val filtered = apps.filter { !it.ignored && org.uniapk.upgrader.glass.AppExclusions.allowed(it.packageName,prefs) }.filter {
                    org.uniapk.upgrader.glass.CategoryPolicy.matches(org.uniapk.upgrader.glass.AppScope.entries.getOrElse(prefs.scanScope.get()) { org.uniapk.upgrader.glass.AppScope.ALL }, it.system, it.packageName)
                }
                org.uniapk.upgrader.glass.SourceHealth.reset(progressId?.let{"scan-$it"} ?: org.uniapk.upgrader.diagnostics.DiagnosticContext.current()["operationId"]?.toString())
                if(filtered.isEmpty()){emit(emptyList());return@collect}
                val sources = mutableListOf<Pair<String, Flow<List<AppUpdate>>>>()
                if (prefs.useApkMirror.get()) sources.add("ApkMirror" to apkMirrorRepository.updates(filtered))
                if (prefs.useGitHub.get()) sources.add("GitHub" to gitHubRepository.updates(filtered))
                if (prefs.useFdroid.get()) sources.add("F-Droid (Main)" to fdroidRepository.updates(filtered))
                if (prefs.useIzzy.get()) sources.add("F-Droid (Izzy)" to izzyRepository.updates(filtered))
                if (prefs.useAptoide.get()) sources.add("Aptoide" to aptoideRepository.updates(filtered))
                if (prefs.useApkPure.get()) sources.add("ApkPure" to apkPureRepository.updates(filtered))
                if (prefs.useGitLab.get()) sources.add("GitLab" to gitLabRepository.updates(filtered))
                if (prefs.usePlay.get()) sources.add("Play" to playRepository.updates(filtered))

                progressId?.let { org.uniapk.upgrader.glass.ScanProgress.sources(it,sources.size) }
                if (sources.isNotEmpty()) {
                    sources.map { (name, source) -> org.uniapk.upgrader.glass.SourceHealth.observe(name, emptyList<AppUpdate>(), source.flowOn(org.uniapk.upgrader.glass.ComputeBudget.dispatcher()),progressId) }
                        .combine { updates ->
                            val detection = org.uniapk.upgrader.glass.DeviceDetection(context, prefs)
                            val assessed = updates.flatMap { it }.filter { org.uniapk.upgrader.glass.AppExclusions.allowed(it.packageName,prefs) }.filterNot { u -> org.uniapk.upgrader.glass.InstalledUpdatePolicy.sameDisplayVersion(u.version,apps.firstOrNull{it.packageName==u.packageName}?.version.orEmpty()) }.map { u ->
                                val installed = apps.firstOrNull { it.packageName == u.packageName }
                                u.copy(system = installed?.system ?: detection.isSystem(u.packageName), verdict = org.uniapk.upgrader.glass.CompatibilityEngine.evaluate(
                                    detection.profile(),
                                    org.uniapk.upgrader.glass.InstalledPackage(u.packageName, installed?.versionCode ?: u.oldVersionCode, detection.isRomSensitive(u.packageName), installed?.version ?: u.oldVersion, installed?.targetSdk, installed?.system ?: detection.isSystem(u.packageName)),
                                    org.uniapk.upgrader.glass.Candidate(u.packageName, u.versionCode.takeIf { it > 0 }, minSdk = u.minSdk, abis = u.abis, versionName = u.version, targetSdk = u.targetSdk)
                                ))
                            }
                            // No hash means identity is unproven. Keep regional/ABI variants separate.

                            emit(assessed.filter { org.uniapk.upgrader.glass.ReleasePolicy.allowed(it, prefs) }.filter { it.id !in prefs.ignoredVersions.get() && org.uniapk.upgrader.glass.releaseKey(it) !in prefs.ignoredReleases.get() }.distinctBy { it.artifactKey })
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

