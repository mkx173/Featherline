package com.mkx.hrttracker.startup

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.DatabaseKeyUnavailableException
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
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val started = AtomicBoolean(false)
    private val snapshotReminderStarted = AtomicBoolean(false)
    private val dbReminderStarted = AtomicBoolean(false)

    // True once the startup open finds the database key lost. MainActivity toasts and closes
    // the app on it; sticky, so a recreated or warm-relaunched activity does too.
    private val _databaseKeyLost = MutableStateFlow(false)
    val databaseKeyLost: StateFlow<Boolean> = _databaseKeyLost.asStateFlow()

    fun startAfterFirstHomeFrame() {
        if (!started.compareAndSet(false, true)) {
            diagnosticsLogger.info(
                TAG,
                "startup_after_first_home_frame_skipped reason=already_started"
            )
            return
        }

        diagnosticsLogger.info(TAG, "startup_after_first_home_frame_enqueued")
        appScope.launch(Dispatchers.IO) {
            diagnosticsLogger.info(TAG, "startup_after_first_home_frame_start")
            diagnosticsLogger.info(TAG, "database_warmup_start")
            runCatching {
                // openAndSignal, not get(): this is the normal in-app cold-start open, and
                // a failure here must flip DatabaseHolder.openFailed or the journal's
                // seeded flow (Milestones with no snapshot) waits forever.
                databaseHolder.openAndSignal()
            }.onSuccess {
                diagnosticsLogger.info(TAG, "database_warmup_complete")
            }.onFailure { throwable ->
                diagnosticsLogger.warning(TAG, "database_warmup_failed", throwable)
                // The key is gone for good: only clearing the app's data opens a database
                // again.
                if (throwable is DatabaseKeyUnavailableException) {
                    _databaseKeyLost.value = true
                }
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

            rescheduleAllReminders()
            diagnosticsLogger.info(TAG, "startup_after_first_home_frame_complete")
        }
    }

    fun startReminderRescheduleFromSnapshot() {
        if (!snapshotReminderStarted.compareAndSet(false, true)) {
            diagnosticsLogger.info(
                TAG,
                "snapshot_reminder_reschedule_skipped reason=already_started"
            )
            return
        }

        diagnosticsLogger.info(TAG, "snapshot_reminder_reschedule_enqueued")
        appScope.launch(Dispatchers.IO) {
            diagnosticsLogger.info(TAG, "snapshot_reminder_reschedule_start")
            // A data change, such as a restore, committed without its reconcile:
            // the snapshot can predate it, and only rescheduleAll reconciles the
            // reminders on screen. Room opens early for it, but only then.
            if (runCatching { medicationReminderScheduler.isReconcilePending() }.getOrDefault(false)) {
                diagnosticsLogger.info(
                    TAG,
                    "snapshot_reminder_reschedule_skipped reason=reconcile_pending"
                )
                rescheduleAllReminders()
                return@launch
            }
            runCatching {
                val snapshot = homeSnapshotRepository.currentState()?.homeData
                    ?: return@runCatching diagnosticsLogger.info(
                        TAG,
                        "snapshot_reminder_reschedule_no_usable_snapshot"
                    )
                diagnosticsLogger.info(
                    TAG,
                    "snapshot_reminder_reschedule_using_snapshot " +
                            "${snapshot.reminderDiagnosticSummary()}"
                )
                // Turned down once Home data changed since it was read: Room has the newer plans.
                if (!medicationReminderScheduler.rescheduleFromHomeSnapshot(snapshot = snapshot)) {
                    diagnosticsLogger.info(TAG, "snapshot_reminder_reschedule_skipped reason=stale")
                    rescheduleAllReminders()
                    return@runCatching
                }
                diagnosticsLogger.info(TAG, "snapshot_reminder_reschedule_complete")
            }.onFailure { throwable ->
                diagnosticsLogger.warning(
                    TAG,
                    "snapshot_reminder_reschedule_failed",
                    throwable
                )
            }
        }
    }

    fun startReminderRescheduleFromWarmDatabase() {
        diagnosticsLogger.info(TAG, "warm_database_reminder_reschedule_enqueued")
        appScope.launch(Dispatchers.IO) {
            diagnosticsLogger.info(TAG, "warm_database_reminder_reschedule_start")
            rescheduleAllReminders()
            diagnosticsLogger.info(TAG, "warm_database_reminder_reschedule_complete")
        }
    }

    private suspend fun rescheduleAllReminders() {
        runCatching {
            rescheduleAllRemindersOnce()
        }.onFailure { throwable ->
            diagnosticsLogger.warning(TAG, "reminder_reschedule_failed", throwable)
        }
    }

    private suspend fun rescheduleAllRemindersOnce() {
        if (!dbReminderStarted.compareAndSet(false, true)) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_skipped reason=already_started"
            )
            return
        }
        // A pass that starts with a reconcile owed reposts every reminder on screen itself.
        diagnosticsLogger.info(TAG, "reminder_reschedule_start")
        medicationReminderScheduler.rescheduleAll()
        diagnosticsLogger.info(TAG, "reminder_reschedule_complete")
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
