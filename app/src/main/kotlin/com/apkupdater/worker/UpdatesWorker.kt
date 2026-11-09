package com.apkupdater.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apkupdater.prefs.Prefs
import com.apkupdater.repository.UpdatesRepository
import com.apkupdater.glass.*
import com.apkupdater.data.ui.*
import com.apkupdater.util.Downloader
import com.apkupdater.util.SessionInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import com.apkupdater.util.UpdatesNotification
import com.apkupdater.util.millisUntilHour
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.random.Random


class UpdatesWorker(
    context: Context,
    workerParams: WorkerParameters
): CoroutineWorker(context, workerParams), KoinComponent {

    companion object: KoinComponent {
        private const val TAG = "UpdatesWorker"
        private val prefs: Prefs by inject()

        fun cancel(workManager: WorkManager) = workManager.cancelUniqueWork(TAG)

        fun launch(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<UpdatesWorker>(getDays(), TimeUnit.DAYS)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(
                    if (prefs.wifiOnly.get()) androidx.work.NetworkType.UNMETERED else androidx.work.NetworkType.CONNECTED
                ).build())
                .setInitialDelay(
                    millisUntilHour(prefs.alarmHour.get()) + randomDelay(),
                    TimeUnit.MILLISECONDS
                ).build()
            workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        private fun randomDelay() = if (prefs.useApkMirror.get())
            Random.nextLong(0, 59 * 60 * 1_000)
        else
            Random.nextLong(-5 * 60 * 1_000, 5 * 60 * 1_000)

        private fun getDays() = when(prefs.alarmFrequency.get()) {
            0 -> 1L
            1 -> 3L
            2 -> 7L
            else -> 1L
        }
    }

    private val updatesRepository: UpdatesRepository by inject()
    private val notification: UpdatesNotification by inject()

    private val selection: AppSelection by inject()
    private val downloader: Downloader by inject()
    private val installer: SessionInstaller by inject()

    override suspend fun doWork(): Result {
        selection.scope.value // Apply legacy category migration before querying.
        var candidates = emptyList<AppUpdate>()
        updatesRepository.updates().collect { candidates = it }
        val ready = candidates.filter { it.verdict.installAllowed }.map { it.packageName }.toMutableSet()
        if(AutomaticChecks.allowed(applicationContext, prefs.autoVerify.get(), prefs.autoVerifyWifiOnly.get())) {
            val limit = prefs.autoVerifyLimitMiB.get() * 1024L * 1024
            for(candidate in AutoCheckPolicy.plan(candidates, prefs.preferredSources.get(), limit,prefs.autoCheckCount.get(),prefs.autoCheckBudgetMiB.get()*1024L*1024)) {
                currentCoroutineContext().ensureActive()
                var file: java.io.File? = null
                try {
                    file = downloader.download((candidate.link as Link.Url).link, limit)
                    val report=installer.inspect(file,candidate.packageName)
                    if(report.verdict.installAllowed && ReleasePolicy.allowed(candidate.copy(version=report.candidate.versionName),prefs)) ready.add(candidate.packageName)
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { if(e !is AutomaticSizeLimit) SourceHealth.failed(candidate.source.name, e) }
                finally { file?.delete() }
            }
        }
        if(ready.isNotEmpty()) notification.showUpdateNotification(ready.size)
        return Result.success()
    }
}
