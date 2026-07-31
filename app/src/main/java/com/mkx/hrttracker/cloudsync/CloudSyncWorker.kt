package com.mkx.hrttracker.cloudsync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

class CloudSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CloudSyncWorkerEntryPoint::class.java,
        )
        return when (val result = entryPoint.cloudSyncCoordinator().syncNow()) {
            is CloudSyncResult.Failed -> if (result.retryable) Result.retry() else Result.failure()
            else -> Result.success()
        }
    }
}
