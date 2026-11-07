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

class SearchRepository(
    private val apkMirrorRepository: ApkMirrorRepository,
    private val fdroidRepository: FdroidRepository,
    private val izzyRepository: FdroidRepository,
    private val aptoideRepository: AptoideRepository,
    private val gitHubRepository: GitHubRepository,
    private val apkPureRepository: ApkPureRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val prefs: Prefs
) {

    fun search(text: String) = flow {
        com.apkupdater.diagnostics.AppDiagnostics.info("search.started",mapOf("queryLength" to text.length))
        val sources = mutableListOf<Flow<Result<List<AppUpdate>>>>()
        if (prefs.useApkMirror.get()) sources.add(apkMirrorRepository.search(text))
        if (prefs.useFdroid.get()) sources.add(fdroidRepository.search(text))
        if (prefs.useIzzy.get()) sources.add(izzyRepository.search(text))
        if (prefs.useAptoide.get()) sources.add(aptoideRepository.search(text))
        if (prefs.useGitHub.get()) sources.add(gitHubRepository.search(text))
        if (prefs.useApkPure.get()) sources.add(apkPureRepository.search(text))
        if (prefs.useGitLab.get()) sources.add(gitLabRepository.search(text))
        if (prefs.usePlay.get()) sources.add(playRepository.search(text))

        if (sources.isNotEmpty()) {
            sources.map { it.flowOn(Dispatchers.IO) }.combine { updates ->
                val result = updates.filter { it.isSuccess }.mapNotNull { it.getOrNull() }
                com.apkupdater.diagnostics.AppDiagnostics.debug("search.results",mapOf("respondedProviders" to updates.size,"failedProviders" to updates.count{it.isFailure},"candidates" to result.sumOf{it.size}))
                emit(Result.success(result.flatten().filter { com.apkupdater.glass.ReleasePolicy.allowed(it, prefs) }.sortedBy { it.name }))
            }.collect()
        } else {
            emit(Result.success(emptyList()))
        }
        com.apkupdater.diagnostics.AppDiagnostics.info("search.finished",mapOf("outcome" to "COMPLETE"))
    }.catch {
        if(it is kotlinx.coroutines.CancellationException){com.apkupdater.diagnostics.AppDiagnostics.info("search.finished",mapOf("outcome" to "CANCELLED"));throw it}
        emit(Result.failure(it))
        com.apkupdater.diagnostics.AppDiagnostics.error("search.finished",mapOf("outcome" to "FAILED"),it)
    }.flowOn(com.apkupdater.diagnostics.DiagnosticContext.element(mapOf("operationId" to com.apkupdater.diagnostics.DiagnosticContext.nextId("search"))))

}
