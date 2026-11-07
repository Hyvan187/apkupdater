package org.uniapk.upgrader.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.repository.UpdatesRepository
import org.uniapk.upgrader.glass.*
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.util.Downloader
import org.uniapk.upgrader.util.SessionInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.withLock
import org.uniapk.upgrader.util.UpdatesNotification
import org.uniapk.upgrader.util.millisUntilHour
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

    private val updatesRepository: org.uniapk.upgrader.repository.UpdatesQuery by inject()
    private val notification: UpdatesNotification by inject()

    private val downloader:Downloader by inject()
    private val installer:SessionInstaller by inject()
    private val workerPrefs:Prefs by inject()
    private val selection: AppSelection by inject()

    override suspend fun doWork(): Result = UpdateOperations.mutex.withLock {
        kotlinx.coroutines.withContext(org.uniapk.upgrader.diagnostics.DiagnosticContext.element(mapOf("operationId" to org.uniapk.upgrader.diagnostics.DiagnosticContext.nextId("worker")))) {
        val lease=AppCache.begin()
        org.uniapk.upgrader.diagnostics.AppDiagnostics.info("background.scan.started")
        try {
        selection.scope.value // Apply legacy category migration before querying.
        var candidates = emptyList<AppUpdate>()
        updatesRepository.updates(null).collect { candidates = it }
        if(workerPrefs.autoDownloadUpdates.get())AutomaticDownloads.run(applicationContext,workerPrefs,candidates,downloader,installer)
        workerPrefs.lastCheck.put(System.currentTimeMillis())
        val ready = candidates.filter { it.verdict.status != Compatibility.INCOMPATIBLE }.map { it.packageName }.toMutableSet()
        if(ready.isNotEmpty()) notification.showUpdateNotification(ready.size)
        org.uniapk.upgrader.diagnostics.AppDiagnostics.info("background.scan.completed",mapOf("candidates" to candidates.size,"ready" to ready.size))
        return@withContext Result.success()
        } catch(e:CancellationException){org.uniapk.upgrader.diagnostics.AppDiagnostics.info("background.scan.cancelled");throw e}
        catch(e:Exception){org.uniapk.upgrader.diagnostics.AppDiagnostics.error("background.scan.failed",error=e);throw e}
        finally { lease.close() }
        }
    }
}
