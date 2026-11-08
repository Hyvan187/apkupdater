package org.uniapk.upgrader.repository

import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import android.util.Log
import org.uniapk.upgrader.BuildConfig
import org.uniapk.upgrader.data.github.GitHubApps
import org.uniapk.upgrader.data.github.GitHubRelease
import org.uniapk.upgrader.data.github.GitHubReleaseAsset
import org.uniapk.upgrader.data.ui.AppInstalled
import org.uniapk.upgrader.data.ui.AppUpdate
import org.uniapk.upgrader.data.ui.GitHubSource
import org.uniapk.upgrader.data.ui.Link
import org.uniapk.upgrader.data.ui.getApp
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.service.GitHubService
import org.uniapk.upgrader.util.combine
import org.uniapk.upgrader.util.filterVersionTag
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.util.Scanner


class GitHubRepository(
    private val service: GitHubService,
    private val prefs: Prefs
) {

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()

        GitHubApps.forEach { app ->
            run {
                apps.find { it.packageName == app.packageName }?.let {
                    checks.add(checkApp(apps, app.user, app.repo, app.packageName, it.version, app.extra))
                }
            }
        }

        if(checks.isEmpty()) emit(emptyList()) else checks.combine { all ->
            emit(all.flatMap { it })
        }.collect()
    }.catch {
        if (it is kotlinx.coroutines.CancellationException) throw it
        org.uniapk.upgrader.glass.SourceHealth.failed("GitHub", it)
        emit(emptyList())
        Log.e("GitHubRepository", "Error fetching releases.", it)
    }

    suspend fun search(text: String) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()

        GitHubApps.forEach { app ->
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
        org.uniapk.upgrader.glass.SourceHealth.failed("GitHub", it)
        emit(Result.failure(it))
        Log.e("GitHubRepository", "Error searching.", it)
    }

    private fun checkApp(
        apps: List<AppInstalled>?,
        user: String,
        repo: String,
        packageName: String,
        currentVersion: String,
        extra: Regex?
    ) = flow {
        if(!org.uniapk.upgrader.glass.AppExclusions.allowed(packageName,prefs)){emit(emptyList());return@flow}
        val r = org.uniapk.upgrader.glass.SourceRetry.once { service.getReleases(user, repo) }
        val releases = if (packageName == "org.uniapk.upgrader.ci") {
            // TODO: Find a better way to do this
            r.filter { it.name.contains("CI-Release-3.x")}
        } else {
            r.filter { filterPreRelease(it) }.filter { findApkAsset(it.assets).isNotEmpty() }
        }

        if (releases.isNotEmpty() && Version(filterVersionTag(releases[0].tag_name)) > Version(currentVersion)) {
            val app = apps?.getApp(packageName)
            emit(listOf(AppUpdate(
                name = repo,
                packageName = packageName,
                version = releases[0].tag_name,
                prerelease = releases[0].prerelease,
                oldVersion = app?.version ?: "?",
                versionCode = 0L,
                oldVersionCode = app?.versionCode ?: 0L,
                source = GitHubSource,
                link = findApkAssetArch(releases[0].assets, extra).let { Link.Url(it.browser_download_url, it.size) },
                whatsNew = releases[0].body,
                iconUri = if (apps == null) releases[0].author.avatar_url.toUri() else Uri.EMPTY
            )))
        } else {
            emit(emptyList())
        }
    }.catch {
        if (it is kotlinx.coroutines.CancellationException) throw it
        org.uniapk.upgrader.glass.SourceHealth.failed("GitHub", it)
        emit(emptyList())
        Log.e("GitHubRepository", "Error fetching releases for $packageName.", it)
    }

    private fun getVersions(name: String) = runCatching {
        val scanner = Scanner(name)
        val version = scanner.next()
        val versionCode = scanner.next().trim('(', ')').toLong()
        Pair(version, versionCode)
    }.getOrDefault(Pair(name, 0L))

    private fun filterPreRelease(release: GitHubRelease) = when {
        !org.uniapk.upgrader.glass.ReleasePolicy.allowed(release.tag_name + " " + release.name, prefs.ignoreAlpha.get(), prefs.ignoreBeta.get(), prefs.ignorePreRelease.get(), release.prerelease) -> false
        else -> true
    }

    private fun findApkAsset(assets: List<GitHubReleaseAsset>) = assets
        .filter { it.browser_download_url.endsWith(".apk", true) }
        .maxByOrNull { it.size }
        ?.browser_download_url
        .orEmpty()

    private fun findApkAssetArch(
        assets: List<GitHubReleaseAsset>,
        extra: Regex?
    ): GitHubReleaseAsset {
        val apks = assets
            .filter { it.browser_download_url.endsWith(".apk", true) }
            .filter { filterExtra(it, extra) }

        when {
            apks.isEmpty() -> return GitHubReleaseAsset(0L, "")
            apks.size == 1 -> return apks.first()
            else -> {
                // Try to match exact arch
                Build.SUPPORTED_ABIS.forEach { arch ->
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains(arch, true)) {
                            return apk
                        }
                    }
                }
                // Try to match arm64
                if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("arm64", true)) {
                            return apk
                        }
                    }
                }
                // Try to match x64
                if (Build.SUPPORTED_ABIS.contains("x86_64")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("x64", true)) {
                            return apk
                        }
                    }
                }
                // Try to match arm
                if (Build.SUPPORTED_ABIS.contains("armeabi-v7a")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("arm", true)) {
                            return apk
                        }
                    }
                }
                // If no match, return biggest apk in the hope it's universal
                return apks.maxByOrNull { it.size } ?: GitHubReleaseAsset(0L, "")
            }
        }
    }

    private fun filterExtra(asset: GitHubReleaseAsset, extra: Regex?) = when(extra) {
        null -> true
        else -> asset.browser_download_url.matches(extra)
    }

}

