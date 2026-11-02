package com.signal.reader.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.signal.reader.data.local.SignalDatabase
import com.signal.reader.data.repository.FeedRepository
import java.util.concurrent.TimeUnit

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.d("StoaRefreshWorker", "Starting background feed refresh worker")
            val db = SignalDatabase.getInstance(applicationContext)
            val repository = FeedRepository(db)
            val newCount = repository.refreshAllFollowedSources()
            repository.pruneOldUnsavedArticles()
            android.util.Log.d("StoaRefreshWorker", "Completed background feed refresh: $newCount new articles")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("StoaRefreshWorker", "Background refresh failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "signal_background_feed_refresh"

        fun schedule(
            context: Context,
            intervalMinutes: Long = 60,
            wifiOnly: Boolean = false,
            enabled: Boolean = true
        ) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val safeInterval = maxOf(15L, intervalMinutes)
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(safeInterval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
