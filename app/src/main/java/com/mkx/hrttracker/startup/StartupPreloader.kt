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
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
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
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
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

            rescheduleAllSnoozes()
            rescheduleAllReminders()
        }
    }

    fun startReminderRescheduleFromSnapshot(now: LocalDateTime = LocalDateTime.now()) {
        if (!snapshotReminderStarted.compareAndSet(false, true)) {
            return
        }

        appScope.launch(Dispatchers.IO) {
            rescheduleAllSnoozes(now = now)
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
            rescheduleAllSnoozes(now = now)
            rescheduleAllReminders(now = now)
        }
    }

    private suspend fun rescheduleAllReminders(now: LocalDateTime = LocalDateTime.now()) {
        runCatching {
            rescheduleAllRemindersOnce(now = now)
        }.onFailure { throwable ->
            Log.w(TAG, "Reminder reschedule failed.", throwable)
        }
    }

    private suspend fun rescheduleAllRemindersOnce(now: LocalDateTime) {
        if (!dbReminderStarted.compareAndSet(false, true)) {
            return
        }
        medicationReminderScheduler.rescheduleAll(now = now)
    }

    private suspend fun rescheduleAllSnoozes(now: LocalDateTime = LocalDateTime.now()) {
        runCatching {
            medicationReminderSnoozeScheduler.rescheduleAll(now = now)
        }.onFailure { throwable ->
            Log.w(TAG, "Snooze reminder reschedule failed.", throwable)
        }
    }

    private companion object {
        const val TAG = "StartupPreloader"
    }
}
