package org.uniapk.upgrader.repository

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import org.uniapk.upgrader.data.apkmirror.AppExistsRequest
import org.uniapk.upgrader.data.apkmirror.AppExistsResponseApk
import org.uniapk.upgrader.data.apkmirror.AppExistsResponseData
import org.uniapk.upgrader.data.apkmirror.toAppUpdate
import org.uniapk.upgrader.data.ui.ApkMirrorSource
import org.uniapk.upgrader.data.ui.AppInstalled
import org.uniapk.upgrader.data.ui.AppUpdate
import org.uniapk.upgrader.data.ui.Link
import org.uniapk.upgrader.data.ui.getApp
import org.uniapk.upgrader.data.ui.getPackageNames
import org.uniapk.upgrader.data.ui.getSignature
import org.uniapk.upgrader.data.ui.getVersionCode
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.service.ApkMirrorService
import org.uniapk.upgrader.util.combine
import org.uniapk.upgrader.util.isAndroidTv
import org.uniapk.upgrader.util.orFalse
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import org.jsoup.Jsoup


class ApkMirrorRepository(
    private val service: ApkMirrorService,
    private val prefs: Prefs,
    packageManager: PackageManager
) {

    private val arch = when {
        Build.SUPPORTED_ABIS.contains("x86") -> "x86"
        Build.SUPPORTED_ABIS.contains("x86_64") -> "x86"
        Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "arm"
        Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "arm"
        else -> "arm"
    }

    private val isAndroidTV = packageManager.isAndroidTv()
    private val api = Build.VERSION.SDK_INT

    suspend fun updates(apps: List<AppInstalled>) = flow {
        if(prefs.apkMirrorKey.get().isBlank()){
            org.uniapk.upgrader.glass.SourceHealth.webMode("ApkMirror");org.uniapk.upgrader.diagnostics.AppDiagnostics.info("source.web_mode",mapOf("source" to "ApkMirror"));emit(emptyList());return@flow
        }
        org.uniapk.upgrader.glass.ProviderCooldown.active("ApkMirror")?.let { block ->
            org.uniapk.upgrader.diagnostics.AppDiagnostics.info("source.backoff",mapOf("source" to "ApkMirror","status" to block.status,"retryAfterMs" to block.until-System.currentTimeMillis()))
            org.uniapk.upgrader.glass.SourceHealth.unavailable("ApkMirror",org.uniapk.upgrader.glass.SourceHealth.describe(org.uniapk.upgrader.glass.DownloadHttpException(block.status))+" · API-Pause; bei Bedarf ausdrücklich erneut abfragen.")
            emit(emptyList());return@flow
        }
        val found=mutableListOf<AppUpdate>()
        emit(emptyList())
        for(chunk in apps.chunked(100)) {
            appExists(chunk.getPackageNames()).collect { data -> found+=parseUpdates(data,apps);emit(found.toList()) }
            val problem=org.uniapk.upgrader.glass.SourceHealth.flow.value.firstOrNull{it.name=="ApkMirror"}?.problem.orEmpty()
            if(listOf("HTTP 401","HTTP 403","HTTP 404","HTTP 429").any{problem.startsWith(it)})break
        }
    }

    suspend fun search(text: String) = flow {
        val baseUrl = "https://www.apkmirror.com"
        val query = java.net.URLEncoder.encode(text.trim().take(200), "UTF-8")
        val doc = Jsoup.connect("$baseUrl/?post_type=app_release&searchtype=app&s=$query")
            .timeout(15_000).maxBodySize(2 * 1024 * 1024).get()
        val result = doc.select("div.appRow").mapNotNull { row ->
            val link = row.selectFirst("h5.appRowTitle a[href]") ?: return@mapNotNull null
            val href = link.absUrl("href")
            if (!href.startsWith("https://www.apkmirror.com/")) return@mapNotNull null
            val title = link.attr("title").ifBlank { link.text() }
            if (title.isBlank()) return@mapNotNull null
            AppUpdate(
                name = title, link = Link.Url(href),
                iconUri = row.selectFirst("img")?.absUrl("src")?.takeIf { it.startsWith("https://") }?.toUri() ?: android.net.Uri.EMPTY,
                version = "?", oldVersion = "?", versionCode = 0L, oldVersionCode = 0L,
                source = ApkMirrorSource, packageName = row.selectFirst("a.byDeveloper")?.text().orEmpty()
            )
        }
        emit(Result.success(result))
    }.catch {
        if (it is kotlinx.coroutines.CancellationException) throw it
        org.uniapk.upgrader.glass.SourceHealth.failed("ApkMirror", it)
        emit(Result.failure(it))
        Log.e("ApkMirrorRepository", "Error searching.", it)
    }

    private fun appExists(apps: List<String>) = flow {
        var last: Exception?=null
        for(attempt in 0..1){
            try{emit(service.appExists(AppExistsRequest(apps,buildIgnoreList())).data);return@flow}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){last=e;if(attempt==0 && (e is java.net.UnknownHostException || e is java.net.SocketTimeoutException))kotlinx.coroutines.delay(700) else throw e}
        }
        throw requireNotNull(last)
    }.catch {
        if (it is kotlinx.coroutines.CancellationException) throw it
        org.uniapk.upgrader.glass.SourceHealth.failed("ApkMirror", it)
        val status=when(it){is retrofit2.HttpException->it.code();is org.uniapk.upgrader.glass.DownloadHttpException->it.statusCode;else->0}
        org.uniapk.upgrader.glass.ProviderCooldown.note("ApkMirror",status)
        emit(emptyList())
        Log.e("ApkMirrorRepository", "Error getting updates.", it)
    }

    private fun parseUpdates(updates: List<AppExistsResponseData>, apps: List<AppInstalled>)
    = updates
        .filter { it.exists == true }
        .flatMap { data ->
            data.apks
                .filter { it.versionCode > apps.getVersionCode(data.pname) }
                .mapNotNull { apk -> apps.getApp(data.pname)?.let { apk.toAppUpdate(it, data.release) } }
        }

    private fun filterSignature(apk: AppExistsResponseApk, signature: String?) = when {
        apk.signaturesSha1.isNullOrEmpty() -> true
        apk.signaturesSha1.contains(signature) -> true
        else -> false
    }

    private fun filterArch(app: AppExistsResponseApk) = when {
        app.arches.isEmpty() -> true
        app.arches.contains("universal") || app.arches.contains("noarch") -> true
        app.arches.find { a -> Build.SUPPORTED_ABIS.contains(a) } != null -> true
        app.arches.find { a -> a.contains(arch) } != null -> true
        else -> false
    }

    private fun filterAndroidTv(apk: AppExistsResponseApk): Boolean {
        if (!isAndroidTV) {
            // Filter out standalone AndroidTV apps if we are not an AndroidTV device
            if(apk.capabilities?.contains("leanback_standalone").orFalse()) {
                return false
            }
        } else {
            // Filter out apps that don't have leanback if we are an AndroidTV device
            return (apk.capabilities?.contains("leanback_standalone").orFalse()
                    || apk.capabilities?.contains("leanback").orFalse())
        }
        return true
    }

    private fun filterWearOS(apk: AppExistsResponseApk): Boolean {
        // For the moment filter out all standalone Wear OS apps
        if (apk.capabilities?.contains("wear_standalone").orFalse()) {
            return false
        }
        return true
    }

    private fun filterMinApi(apk: AppExistsResponseApk) = runCatching {
        when {
            apk.minapi.toInt() > api -> false
            else -> true
        }
    }.getOrDefault(true)

    private fun buildIgnoreList() = mutableListOf<String>().apply {
        if (prefs.ignoreAlpha.get() || prefs.ignorePreRelease.get()) add("alpha")
        if (prefs.ignoreBeta.get() || prefs.ignorePreRelease.get()) add("beta")
    }

}
