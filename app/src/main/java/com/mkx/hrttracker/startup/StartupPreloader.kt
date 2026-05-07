package com.mkx.hrttracker.startup

import android.util.Log
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupPreloader @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val userProfileRepository: UserProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
) {
    private val started = AtomicBoolean(false)
    private val snapshotReminderStarted = AtomicBoolean(false)
    private val dbReminderStarted = AtomicBoolean(false)

    fun startAfterFirstHomeFrame() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        appScope.launch(Dispatchers.IO) {
            runCatching {
                databaseHolder.get().openHelper.writableDatabase
            }.onFailure { throwable ->
                Log.w(TAG, "Database warm-up failed.", throwable)
            }

            runCatching {
                medicationGroupRepository.getGroups()
                medicationLogRepository.getEntries()
                userProfileRepository.getCurrentProfile()
                settingsRepository.getCurrentSettings()
            }.onFailure { throwable ->
                Log.w(TAG, "Repository warm-up failed.", throwable)
            }

            runCatching {
                rescheduleAllRemindersOnce()
            }.onFailure { throwable ->
                Log.w(TAG, "Reminder reschedule failed.", throwable)
            }
        }
    }

    fun startReminderRescheduleFromSnapshot(now: LocalDateTime = LocalDateTime.now()) {
        if (!snapshotReminderStarted.compareAndSet(false, true)) {
            return
        }

        appScope.launch(Dispatchers.IO) {
            runCatching {
                val snapshot = homeSnapshotRepository.readUsableHomeSnapshot(now = now)
                    ?: return@runCatching
                medicationReminderScheduler.rescheduleFromHomeSnapshot(
                    snapshot = snapshot,
                    now = now,
                )
            }.onFailure { throwable ->
                Log.w(TAG, "Snapshot reminder reschedule failed.", throwable)
            }
        }
    }

    fun startReminderRescheduleFromWarmDatabase(now: LocalDateTime = LocalDateTime.now()) {
        appScope.launch(Dispatchers.IO) {
            runCatching {
                rescheduleAllRemindersOnce(now = now)
            }.onFailure { throwable ->
                Log.w(TAG, "Reminder reschedule failed.", throwable)
            }
        }
    }

    private suspend fun rescheduleAllRemindersOnce(now: LocalDateTime = LocalDateTime.now()) {
        if (!dbReminderStarted.compareAndSet(false, true)) {
            return
        }
        medicationReminderScheduler.rescheduleAll(now = now)
    }

    private companion object {
        const val TAG = "StartupPreloader"
    }
}
