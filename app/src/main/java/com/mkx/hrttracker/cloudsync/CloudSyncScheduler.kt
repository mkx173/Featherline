package com.mkx.hrttracker.cloudsync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun schedule(interval: CloudSyncInterval) {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(
            interval.days,
            TimeUnit.DAYS,
        )
            .setInitialDelay(interval.days, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    internal companion object {
        const val WORK_NAME = "google_drive_database_sync"
    }
}
