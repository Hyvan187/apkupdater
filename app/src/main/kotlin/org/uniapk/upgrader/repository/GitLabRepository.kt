package org.uniapk.upgrader.repository

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import org.uniapk.upgrader.data.gitlab.GitLabApps
import org.uniapk.upgrader.data.gitlab.GitLabRelease
import org.uniapk.upgrader.data.ui.AppInstalled
import org.uniapk.upgrader.data.ui.AppUpdate
import org.uniapk.upgrader.data.ui.GitLabSource
import org.uniapk.upgrader.data.ui.Link
import org.uniapk.upgrader.data.ui.getApp
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.service.GitLabService
import org.uniapk.upgrader.util.combine
import org.uniapk.upgrader.util.filterVersionTag
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow


class GitLabRepository(
    private val service: GitLabService,
    private val prefs: Prefs
) {

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()
        GitLabApps.forEach { app ->
            apps.find { it.packageName == app.packageName }?.let {
                checks.add(checkApp(apps, app.user, app.repo, app.packageName, it.version, null))
            }
        }
        if (checks.isEmpty()) {
            emit(emptyList())
        } else {
            checks.combine { all -> emit(all.flatMap { it }) }.collect()
        }
    }

    private suspend fun checkApp(
        apps: List<AppInstalled>?,
        user: String,
        repo: String,
        packageName: String,
        currentVersion: String,
        extra: Regex?
    ) = flow {
        val releases = service.getReleases(user, repo).filter { org.uniapk.upgrader.glass.ReleasePolicy.allowed(it.tag_name,prefs.ignoreAlpha.get(),prefs.ignoreBeta.get(),prefs.ignorePreRelease.get()) }
            .filter { Version(filterVersionTag(it.tag_name)) > Version(currentVersion) }

        if (releases.isNotEmpty()) {
            val app = apps?.getApp(packageName)
            emit(listOf(
                AppUpdate(
                name = repo,
                packageName = packageName,
                version = releases[0].tag_name,
                oldVersion = app?.version ?: "?",
                versionCode = 0L,
                oldVersionCode = app?.versionCode ?: 0L,
                source = GitLabSource,
                link = Link.Url(getApkUrl(packageName, releases[0])),
                whatsNew = releases[0].description,
                iconUri = if (apps == null) releases[0].author.avatar_url.toUri() else Uri.EMPTY
            )))
        } else {
            emit(emptyList())
        }
    }.catch {
        if (it is kotlinx.coroutines.CancellationException) throw it
        org.uniapk.upgrader.glass.SourceHealth.failed("GitLab", it)
        emit(emptyList())
        Log.e("GitLabRepository", "Error fetching releases for $packageName.", it)
    }

    suspend fun search(text: String) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()

        GitLabApps.forEach { app ->
            if (app.repo.contains(text, true) || app.user.contains(text, true) || app.packageName.contains(text, true)) {
                checks.add(checkApp(null, app.user, app.repo, app.packageName, "?", null))
            }
        }

        if (checks.isEmpty()) {
            emit(Result.success(emptyList()))
        } else {
            checks.combine { all ->
                val r = all.flatMap { it }
                emit(Result.success(r))
            }.collect()
        }
    }.catch {
        if (it is kotlinx.coroutines.CancellationException) throw it
        org.uniapk.upgrader.glass.SourceHealth.failed("GitLab", it)
        emit(Result.failure(it))
        Log.e("GitLabRepository", "Error searching.", it)
    }

    private fun getApkUrl(
        packageName: String,
        release: GitLabRelease
    ): String {
        // TODO: Take into account arch
        val source = release.assets.sources.find { it.url.endsWith(".apk", true) }
        if (source != null) return source.url

        val link = release.assets.links.find { it.url.endsWith(".apk", true) }
        if (link != null) return link.url

        return ""
    }

}
