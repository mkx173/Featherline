package com.mkx.hrttracker.startup

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.util.AppDiagnosticsLogger
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
    private val medicineRepository: MedicineRepository,
    private val userProfileRepository: UserProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val started = AtomicBoolean(false)
    private val snapshotReminderStarted = AtomicBoolean(false)
    private val dbReminderStarted = AtomicBoolean(false)

    fun startAfterFirstHomeFrame() {
        if (!started.compareAndSet(false, true)) {
            diagnosticsLogger.info(TAG, "startup_after_first_home_frame_skipped reason=already_started")
            return
        }

        diagnosticsLogger.info(TAG, "startup_after_first_home_frame_enqueued")
        appScope.launch(Dispatchers.IO) {
            diagnosticsLogger.info(TAG, "startup_after_first_home_frame_start")
            diagnosticsLogger.info(TAG, "database_warmup_start")
            runCatching {
                databaseHolder.get().openHelper.writableDatabase
            }.onSuccess {
                diagnosticsLogger.info(TAG, "database_warmup_complete")
            }.onFailure { throwable ->
                diagnosticsLogger.warning(TAG, "database_warmup_failed", throwable)
            }

            diagnosticsLogger.info(TAG, "repository_warmup_start")
            runCatching {
                medicationGroupRepository.getGroups()
                medicationLogRepository.getEntries()
                userProfileRepository.getCurrentProfile()
                settingsRepository.getCurrentSettings()
            }.onSuccess {
                diagnosticsLogger.info(TAG, "repository_warmup_complete")
            }.onFailure { throwable ->
                diagnosticsLogger.warning(TAG, "repository_warmup_failed", throwable)
            }

            backfillPatchOffSingletonIfNeeded()

            rescheduleAllSnoozes()
            rescheduleAllReminders()
            diagnosticsLogger.info(TAG, "startup_after_first_home_frame_complete")
        }
    }

    fun startReminderRescheduleFromSnapshot(now: LocalDateTime = LocalDateTime.now()) {
        if (!snapshotReminderStarted.compareAndSet(false, true)) {
            diagnosticsLogger.info(TAG, "snapshot_reminder_reschedule_skipped reason=already_started now=$now")
            return
        }

        diagnosticsLogger.info(TAG, "snapshot_reminder_reschedule_enqueued now=$now")
        appScope.launch(Dispatchers.IO) {
            diagnosticsLogger.info(TAG, "snapshot_reminder_reschedule_start now=$now")
            rescheduleAllSnoozes(now = now)
            runCatching {
                val snapshot = homeSnapshotRepository.readUsableHomeSnapshot(now = now)
                    ?: return@runCatching diagnosticsLogger.info(
                        TAG,
                        "snapshot_reminder_reschedule_no_usable_snapshot now=$now"
                    )
                diagnosticsLogger.info(
                    TAG,
                    "snapshot_reminder_reschedule_using_snapshot " +
                        "now=$now ${snapshot.reminderDiagnosticSummary()}"
                )
                medicationReminderScheduler.rescheduleFromHomeSnapshot(
                    snapshot = snapshot,
                    now = now,
                )
                diagnosticsLogger.info(TAG, "snapshot_reminder_reschedule_complete now=$now")
            }.onFailure { throwable ->
                diagnosticsLogger.warning(TAG, "snapshot_reminder_reschedule_failed now=$now", throwable)
            }
        }
    }

    fun startReminderRescheduleFromWarmDatabase(now: LocalDateTime = LocalDateTime.now()) {
        diagnosticsLogger.info(TAG, "warm_database_reminder_reschedule_enqueued now=$now")
        appScope.launch(Dispatchers.IO) {
            diagnosticsLogger.info(TAG, "warm_database_reminder_reschedule_start now=$now")
            rescheduleAllSnoozes(now = now)
            rescheduleAllReminders(now = now)
            diagnosticsLogger.info(TAG, "warm_database_reminder_reschedule_complete now=$now")
        }
    }

    private suspend fun rescheduleAllReminders(now: LocalDateTime = LocalDateTime.now()) {
        runCatching {
            rescheduleAllRemindersOnce(now = now)
        }.onFailure { throwable ->
            diagnosticsLogger.warning(TAG, "reminder_reschedule_failed now=$now", throwable)
        }
    }

    private suspend fun rescheduleAllRemindersOnce(now: LocalDateTime) {
        if (!dbReminderStarted.compareAndSet(false, true)) {
            diagnosticsLogger.info(TAG, "reminder_reschedule_skipped reason=already_started now=$now")
            return
        }
        diagnosticsLogger.info(TAG, "reminder_reschedule_start now=$now")
        medicationReminderScheduler.rescheduleAll(now = now)
        diagnosticsLogger.info(TAG, "reminder_reschedule_complete now=$now")
    }

    // The PATCH_OFF singleton is auto-created when a NEW patch medicine is
    // created (MedicineRepository.findOrCreateForCatalog/Custom hook). Users
    // who installed a build with patch medicines but BEFORE the singleton
    // shipped wouldn't otherwise see "Patch off" in the manager until they
    // created another patch. Back-fill on first launch so the singleton
    // appears for them too. findOrCreatePatchOff is idempotent, so this is a
    // no-op once it exists.
    private suspend fun backfillPatchOffSingletonIfNeeded() {
        runCatching {
            val medicines = medicineRepository.getAll()
            val hasPatchMedicine = medicines.any { medicine ->
                medicine.preparation is MedicinePreparation.Patch
            }
            val hasSingleton = medicines.any { medicine ->
                medicine.selection is MedicineSelection.PatchOff
            }
            if (hasPatchMedicine && !hasSingleton) {
                diagnosticsLogger.info(TAG, "patch_off_singleton_backfill_creating")
                medicineRepository.findOrCreatePatchOff()
                diagnosticsLogger.info(TAG, "patch_off_singleton_backfill_complete")
            }
        }.onFailure { throwable ->
            diagnosticsLogger.warning(TAG, "patch_off_singleton_backfill_failed", throwable)
        }
    }

    private suspend fun rescheduleAllSnoozes(now: LocalDateTime = LocalDateTime.now()) {
        runCatching {
            diagnosticsLogger.info(TAG, "snooze_reschedule_start now=$now")
            medicationReminderSnoozeScheduler.rescheduleAll(now = now)
            diagnosticsLogger.info(TAG, "snooze_reschedule_complete now=$now")
        }.onFailure { throwable ->
            diagnosticsLogger.warning(TAG, "snooze_reschedule_failed now=$now", throwable)
        }
    }

    private companion object {
        const val TAG = "StartupPreloader"
    }
}

private fun HomeSnapshotRecord.reminderDiagnosticSummary(): String {
    return runCatching {
        "generation=$generation groups=${activeGroups.size} scheduleEntries=${scheduleEntries.size}"
    }.getOrElse { throwable ->
        "summaryUnavailable=${throwable.javaClass.simpleName}"
    }
}
