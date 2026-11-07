package org.uniapk.upgrader.repository

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import org.uniapk.upgrader.data.ui.AppInstalled
import org.uniapk.upgrader.data.ui.AppUpdate
import org.uniapk.upgrader.data.ui.Link
import org.uniapk.upgrader.data.ui.PlaySource
import org.uniapk.upgrader.data.ui.getPackageNames
import org.uniapk.upgrader.data.ui.getVersion
import org.uniapk.upgrader.data.ui.getVersionCode
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.util.play.NativeDeviceInfoProvider
import org.uniapk.upgrader.util.play.PlayHttpClient
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import com.google.gson.Gson
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow


class PlayRepository(
    private val context: Context,
    private val playHttpClient: PlayHttpClient,
    private val gson: Gson,
    private val prefs: Prefs
) {
    companion object {
        const val AUTH_URL = "https://auroraoss.com/api/auth"
    }

    private fun refreshAuth(): AuthData {
        Log.i("PlayRepository", "Refreshing token.")
        val selectedModel=prefs.simulatedPreset.get();val selectedRegion=prefs.simulatedRegion.get()
        val properties = NativeDeviceInfoProvider(context).getNativeDeviceProperties().apply {
            org.uniapk.upgrader.glass.TestDeviceProfiles.apply(this,selectedModel,selectedRegion)
        }
        val playResponse = playHttpClient.postAuth(AUTH_URL, gson.toJson(properties).toByteArray())
        if (playResponse.isSuccessful) {
            val received = gson.fromJson(String(playResponse.responseBytes), AuthData::class.java)
            val authData=if(selectedModel=="ACTUAL")received else received.copy(locale=java.util.Locale.forLanguageTag(when(selectedRegion){"IN"->"en-IN";"CN"->"zh-CN";"EU"->"en-GB";else->"en-US"}))
            check(prefs.simulatedPreset.get()==selectedModel && prefs.simulatedRegion.get()==selectedRegion) { "Das Testprofil wurde während der Play-Anmeldung geändert. Bitte erneut suchen." }
            require(authData.email.isNotBlank()) { "Play-Anmeldedaten sind unvollständig." }
            prefs.playAuthData.put(authData)
            prefs.lastPlayCheck.put(System.currentTimeMillis())
            return authData
        }
        throw org.uniapk.upgrader.glass.DownloadHttpException(playResponse.code)
    }

    @Synchronized private fun auth(): AuthData {
        val savedData = prefs.playAuthData.get()
        if (savedData.email.isEmpty()) {
            return refreshAuth()
        }
        if (System.currentTimeMillis() - prefs.lastPlayCheck.get() > 60 * 60 * 1_000) {
            // Update check time
            prefs.lastPlayCheck.put(System.currentTimeMillis())
            Log.i("PlayRepository", "Checking token validity.")

            // 1h has passed check if token still works
            val app = runCatching {
                AppDetailsHelper(savedData)
                    .using(playHttpClient)
                    .getAppByPackageName("com.google.android.gm")
            }.getOrElse {
                return refreshAuth()
            }

            if (app.packageName.isEmpty()) {
                return refreshAuth()
            }
            Log.i("PlayRepository", "Token still valid.")
        }
        return savedData
    }

    suspend fun search(text: String) = flow {
        if (text.contains(" ") || !text.contains(".")) {
            // Normal Search
            val authData = auth()
            val updates = SearchHelper(authData)
                .using(playHttpClient)
                .searchResults(text)
                .streamClusters.values.flatMap { it.clusterAppList }
                .take(10)
                .map { it.toAppUpdate(::getInstallFiles) }
            emit(Result.success(updates))
        } else {
            // Package Name Search
            val authData = auth()
            val update = AppDetailsHelper(authData)
                .using(playHttpClient)
                .getAppByPackageName(text)
                .toAppUpdate(::getInstallFiles)
            emit(Result.success(listOf(update)))
        }
    }.catch {
        if (it is kotlinx.coroutines.CancellationException) throw it
        org.uniapk.upgrader.glass.SourceHealth.failed("Play", it)
        emit(Result.failure(it))
        Log.e("PlayRepository", "Error searching for $text.", it)
    }

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val authData = auth()
        val details = AppDetailsHelper(authData)
            .using(playHttpClient)
            .getAppByPackageName(apps.getPackageNames())
        val updates = details
            .filter { it.versionCode > apps.getVersionCode(it.packageName) }
            .map {
                it.toAppUpdate(
                    ::getInstallFiles,
                    apps.getVersion(it.packageName),
                    apps.getVersionCode(it.packageName)
                )
            }
        emit(updates)
    }.catch {
        if (it is kotlinx.coroutines.CancellationException) throw it
        org.uniapk.upgrader.glass.SourceHealth.failed("Play", it)
        emit(emptyList())
        Log.e("PlayRepository", "Error looking for updates.", it)
    }

    private fun getInstallFiles(app: App) = PurchaseHelper(auth())
        .using(playHttpClient)
        .purchase(app.packageName, app.versionCode, app.offerType)

}

fun App.toAppUpdate(
    getInstallFiles: (App) -> List<PlayFile>,
    oldVersion: String = "",
    oldVersionCode: Long = 0L
) = AppUpdate(
    displayName,
    packageName,
    versionName,
    oldVersion,
    versionCode,
    oldVersionCode,
    PlaySource,
    iconArtwork.url.toUri(),
    Link.Play { getInstallFiles(this) },
    whatsNew = changes
)
