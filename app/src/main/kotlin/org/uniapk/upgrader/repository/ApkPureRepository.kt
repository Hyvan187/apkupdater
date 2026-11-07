package org.uniapk.upgrader.repository

import android.util.Log
import org.uniapk.upgrader.data.apkpure.AppInfoForUpdate
import org.uniapk.upgrader.data.apkpure.AppUpdateResponse
import org.uniapk.upgrader.data.apkpure.DeviceHeader
import org.uniapk.upgrader.data.apkpure.GetAppUpdate
import org.uniapk.upgrader.data.apkpure.toAppUpdate
import org.uniapk.upgrader.data.ui.AppInstalled
import org.uniapk.upgrader.data.ui.getApp
import org.uniapk.upgrader.data.ui.getSignature
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.service.ApkPureService
import com.google.gson.Gson
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow


class ApkPureRepository(
    gson: Gson,
    private val service: ApkPureService,
    private val prefs: Prefs
) {

    private val header = gson.toJson(DeviceHeader())

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val info = apps.map { AppInfoForUpdate(it.packageName, it.versionCode) }
        val r = service.getAppUpdate(header, GetAppUpdate(info))
        val updates = r.app_update_response
            .filter { filterSignature(it.sign, apps.getSignature(it.package_name)) }
            .filter { filterAlpha(it) }
            .filter { filterBeta(it) }
            .map { it.toAppUpdate(apps.getApp(it.package_name)) }
        emit(updates)
    }.catch {
        if (it is kotlinx.coroutines.CancellationException) throw it
        org.uniapk.upgrader.glass.SourceHealth.failed("ApkPure", it)
        Log.e("ApkPureRepository", it.message, it)
        emit(emptyList())
    }

    suspend fun search(text: String) = flow {
        val response = service.search(header, text)
        val info = response.data.data.mapNotNull { d ->
            d.data.firstOrNull()?.takeIf { !it.ad }?.app_info?.let {
                AppInfoForUpdate(it.package_name, 0L, false)
            }
        }
        val r = service.getAppUpdate(header, GetAppUpdate(info))
        val updates = r.app_update_response
            .filter { filterAlpha(it) }
            .filter { filterBeta(it) }
            .map { it.toAppUpdate(null) }
        emit(Result.success(updates))
    }.catch {
        if (it is kotlinx.coroutines.CancellationException) throw it
        org.uniapk.upgrader.glass.SourceHealth.failed("ApkPure", it)
        Log.e("ApkPureRepository", it.message, it)
        emit(Result.failure(it))
    }

    private fun filterAlpha(update: AppUpdateResponse) = when {
        (prefs.ignoreAlpha.get() || prefs.ignorePreRelease.get()) && update.version_name.contains("alpha", true) -> false
        else -> true
    }

    private fun filterBeta(update: AppUpdateResponse) = when {
        (prefs.ignoreBeta.get() || prefs.ignorePreRelease.get()) && update.version_name.contains("beta", true) -> false
        else -> true
    }

    private fun filterSignature(signatures: List<String>, signature: String) = when {
        signatures.isEmpty() -> true
        signatures.contains(signature) -> true
        else -> false
    }

}
