package com.focussessions.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that flushes the SyncQueue when the device is online.
 * Annotated with @HiltWorker so Hilt provides its dependencies.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            syncManager.fullSync()
            Result.success()
        } catch (e: Exception) {
            // Retry up to 3 times with exponential back-off
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }

    companion object {
        /** Enqueue a one-time immediate sync (e.g., after session complete). */
        fun scheduleOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("focus_sync_once", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
