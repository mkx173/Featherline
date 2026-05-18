package com.mkx.hrttracker.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    @AppScope private val appScope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return

        // 1. Enqueue the daily best-effort re-render worker (idempotent).
        val initialDelay = durationUntilLocalMidnightMillis()
        val request = PeriodicWorkRequestBuilder<WidgetDailyRefreshWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )

        // 2. Re-render whenever the E2 display unit changes (no DB, no simulation).
        appScope.launch {
            settingsRepository.homeE2DisplayUnitFlow
                .collect {
                    runCatching { HrtWidget().updateAll(context) }
                }
        }
    }

    private fun durationUntilLocalMidnightMillis(): Long {
        val now = LocalDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return ChronoUnit.MILLIS.between(now, midnight).coerceAtLeast(0L)
    }

    companion object {
        private const val WORK_NAME = "widget_daily_refresh"
    }
}
