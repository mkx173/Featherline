package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class MedicationReminderScheduler private constructor(
    private val context: Context,
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduleStore: ReminderScheduleStore,
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
    private val reminderNotificationManager: ReminderNotificationManager,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    private val sdkIntProvider: () -> Int,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        medicationGroupRepository: MedicationGroupRepository,
        medicationLogRepository: MedicationLogRepository,
        settingsRepository: SettingsRepository,
        reminderScheduleStore: ReminderScheduleStore,
        medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
        reminderNotificationManager: ReminderNotificationManager,
        homeSnapshotRepository: HomeSnapshotRepository,
        diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    ) : this(
        context = context,
        medicationGroupRepository = medicationGroupRepository,
        medicationLogRepository = medicationLogRepository,
        settingsRepository = settingsRepository,
        reminderScheduleStore = reminderScheduleStore,
        medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
        reminderNotificationManager = reminderNotificationManager,
        homeSnapshotRepository = homeSnapshotRepository,
        diagnosticsLogger = diagnosticsLogger,
        sdkIntProvider = { Build.VERSION.SDK_INT },
    )

    internal constructor(
        context: Context,
        medicationGroupRepository: MedicationGroupRepository,
        medicationLogRepository: MedicationLogRepository,
        settingsRepository: SettingsRepository,
        reminderScheduleStore: ReminderScheduleStore,
        medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
        reminderNotificationManager: ReminderNotificationManager,
        homeSnapshotRepository: HomeSnapshotRepository,
        diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
        sdkInt: Int,
    ) : this(
        context = context,
        medicationGroupRepository = medicationGroupRepository,
        medicationLogRepository = medicationLogRepository,
        settingsRepository = settingsRepository,
        reminderScheduleStore = reminderScheduleStore,
        medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
        reminderNotificationManager = reminderNotificationManager,
        homeSnapshotRepository = homeSnapshotRepository,
        diagnosticsLogger = diagnosticsLogger,
        sdkIntProvider = { sdkInt },
    )

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    // ponytail: one flag. A pass between a writer's mark and its commit reads the old data
    // and still clears it; the writer's own pass after the commit reconciles, so only a
    // process death between that commit and that pass is left to a later full pass.
    /**
     * For a data change reminders must follow, before it commits: marks a
     * reconcile owed until a [rescheduleAll] completes, so startup still runs
     * one ([isReconcilePending]) if the process dies or the writer's own fails.
     * Under the passes' lock, so a pass that read the data before it doesn't clear it.
     */
    suspend fun markReconcilePending() = withReminderScheduleLock {
        reminderScheduleStore.setReconcilePending(true)
    }

    suspend fun isReconcilePending(): Boolean = reminderScheduleStore.isReconcilePending()

    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()) = withReminderScheduleLock {
        diagnosticsLogger.info(TAG, "reminder_reschedule_all_start now=$now")
        val groups = medicationGroupRepository.getGroups()
        val groupUuids = groups.mapTo(mutableSetOf()) { group -> group.uuid }
        val stored = reminderScheduleStore.getScheduledReminders()
        val storedGroupUuids = stored.keys
        val uuidsToCancel = storedGroupUuids + groupUuids

        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_all_disabled groups=${groups.size} " +
                        "stored=${storedGroupUuids.size} cancel=${uuidsToCancel.size}"
            )
            uuidsToCancel.forEach(::cancelReminderAlarm)
            reminderScheduleStore.replaceScheduledReminders(emptyMap())
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_all_complete groups=${groups.size} entries=0 plans=0 stored=0"
            )
            reconcileDoseReminderNotifications(remindersEnabled = false)
            reminderScheduleStore.setReconcilePending(false)
            return@withReminderScheduleLock
        }

        val entries = medicationLogRepository.getScheduledGroupEntriesSince(now)
        val plans = buildNextMedicationReminderPlans(
            groups = groups,
            entries = entries,
            now = now,
            armed = stored.armedTimes(),
        )

        uuidsToCancel.forEach(::cancelReminderAlarm)
        plans.forEach { plan ->
            scheduleReminder(plan)
        }
        reminderScheduleStore.replaceScheduledReminders(
            plans.associate { plan -> plan.groupUuid to plan.scheduledAt }
        )
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_all_complete groups=${groups.size} " +
                    "entries=${entries.size} plans=${plans.size} " +
                    "stored=${
                        plans.mapTo(
                            mutableSetOf(),
                            MedicationReminderPlan::groupUuid
                        ).size
                    } " +
                    "cancel=${uuidsToCancel.size}"
        )
        reconcileDoseReminderNotifications(remindersEnabled = true)
        reminderScheduleStore.setReconcilePending(false)
    }

    /**
     * Reschedules from [snapshot], which its caller read before this took the lock. False, with
     * nothing changed, once Home data changed since: that writer's own reschedule, or a full
     * pass, has the newer plans.
     */
    suspend fun rescheduleFromHomeSnapshot(
        snapshot: HomeSnapshotRecord,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean = withReminderScheduleLock {
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_from_snapshot_start now=$now " +
                    "generation=${snapshot.generation} groups=${snapshot.activeGroups.size} " +
                    "snapshotEntries=${snapshot.scheduleEntries.size}"
        )
        val currentGeneration = homeSnapshotRepository.generation.value
        if (snapshot.generation != currentGeneration) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_from_snapshot_stale generation=${snapshot.generation} " +
                        "current=$currentGeneration"
            )
            return@withReminderScheduleLock false
        }
        val groups = snapshot.activeGroups
        val groupUuids = groups.mapTo(mutableSetOf()) { group -> group.uuid }
        val stored = reminderScheduleStore.getScheduledReminders()
        val storedGroupUuids = stored.keys
        val uuidsToCancel = storedGroupUuids + groupUuids

        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_from_snapshot_disabled groups=${groups.size} " +
                        "stored=${storedGroupUuids.size} cancel=${uuidsToCancel.size}"
            )
            uuidsToCancel.forEach(::cancelReminderAlarm)
            reminderScheduleStore.replaceScheduledReminders(emptyMap())
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_from_snapshot_complete groups=${groups.size} entries=0 plans=0 stored=0"
            )
            return@withReminderScheduleLock true
        }

        val entries = snapshot.scheduleEntries.filter { entry ->
            val scheduledFor = entry.scheduledFor
            entry.sourceGroupUuid != null &&
                    scheduledFor != null &&
                    !scheduledFor.isBefore(now)
        }
        val plans = buildNextMedicationReminderPlans(
            groups = groups,
            entries = entries,
            now = now,
            armed = stored.armedTimes(),
        )

        uuidsToCancel.forEach(::cancelReminderAlarm)
        plans.forEach { plan ->
            scheduleReminder(plan)
        }
        reminderScheduleStore.replaceScheduledReminders(
            plans.associate { plan -> plan.groupUuid to plan.scheduledAt }
        )
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_from_snapshot_complete groups=${groups.size} " +
                    "entries=${entries.size} plans=${plans.size} " +
                    "stored=${
                        plans.mapTo(
                            mutableSetOf(),
                            MedicationReminderPlan::groupUuid
                        ).size
                    } " +
                    "cancel=${uuidsToCancel.size}"
        )
        true
    }

    suspend fun rescheduleGroup(
        groupUuid: UUID,
        after: LocalDateTime = LocalDateTime.now()
    ) = withReminderScheduleLock {
        // Every way out, an archived, deleted or muted plan included, leaves a
        // reminder on screen that may name doses no longer pending.
        scheduleGroupReminder(groupUuid, after)
        reconcileDoseReminderNotifications(
            remindersEnabled = settingsRepository.getCurrentSettings().remindersEnabled
        )
    }

    private suspend fun scheduleGroupReminder(groupUuid: UUID, after: LocalDateTime) {
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_start groupUuid=$groupUuid after=$after"
        )
        cancelReminderSchedule(groupUuid)

        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_group_skipped reason=master_disabled groupUuid=$groupUuid"
            )
            return
        }

        val group = medicationGroupRepository.getGroup(groupUuid)
            ?: return diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_group_skipped reason=missing_group groupUuid=$groupUuid"
            )
        medicationReminderSnoozeScheduler.clearStaleSnoozesForGroup(group)
        if (group.isArchived() || !group.notificationsEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_group_skipped reason=inactive_or_notifications_disabled " +
                        "groupUuid=$groupUuid archived=${group.isArchived()} " +
                        "notificationsEnabled=${group.notificationsEnabled}"
            )
            return
        }

        val entries = medicationLogRepository.getScheduledGroupEntriesSince(after)
        val plan = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = entries,
            now = after,
        ).firstOrNull() ?: return diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_skipped reason=no_plan groupUuid=$groupUuid entries=${entries.size}"
        )

        scheduleReminder(plan)
        reminderScheduleStore.addScheduledReminder(plan.groupUuid, plan.scheduledAt)
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_complete groupUuid=${plan.groupUuid} " +
                    "scheduledAt=${plan.scheduledAt} scheduleTimeUuid=${plan.scheduleTimeUuid} entries=${entries.size}"
        )
    }

    /**
     * Brings the dose reminders on screen in line with the plans: one whose doses
     * are all gone (dropped or moved by an edit, logged, plan archived, deleted or
     * muted) is cancelled, and one with only some gone, or whose plan's medications
     * changed, is reposted silently with what is pending now.
     */
    private suspend fun reconcileDoseReminderNotifications(remindersEnabled: Boolean) {
        if (!remindersEnabled) {
            reminderNotificationManager.cancelAllDoseReminderNotifications()
            return
        }
        val shown = reminderNotificationManager.activeDoseReminders()
        if (shown.isEmpty()) {
            return
        }
        val groups = medicationGroupRepository.getGroups()
        val entries = medicationLogRepository.getScheduledGroupEntriesSince(
            shown.values.flatMap(ShownDoseReminder::slots).minOf(MedicationReminderSlot::scheduledAt)
        )
        var cancelled = 0
        var reposted = 0
        shown.forEach { (tag, reminder) ->
            val remaining = remainingMedicationReminderBundle(reminder.slots, groups, entries)
            // Same doses can still mean other medications, counts or names, and Log
            // now would log the old ones.
            if (remaining != null && remaining.contentHash == reminder.contentHash) {
                return@forEach
            }
            // The same doses keep the tag, and posting under it replaces the old one.
            if (remaining?.notificationTag != tag) {
                reminderNotificationManager.cancelDoseReminderNotification(tag)
            }
            if (remaining == null) {
                cancelled++
                return@forEach
            }
            val recordsBySlot = medicationReminderSnoozeScheduler.getSnoozeRecords()
                .associateBy(MedicationReminderSnoozeRecord::slot)
            reminderNotificationManager.showDoseReminderNotification(
                bundle = remaining,
                // As showSnoozedReminder decides it: hidden once every dose hit the limit.
                canSnooze = remaining.slots.any { slot ->
                    (recordsBySlot[slot]?.snoozeCount ?: 0) < MAX_REMINDER_SNOOZE_COUNT
                },
                hideMedicationDetails = settingsRepository.getCurrentSettings().hideMedicationDetails,
                silent = true,
            )
            reposted++
        }
        diagnosticsLogger.info(
            TAG,
            "reminder_notifications_reconciled shown=${shown.size} cancelled=$cancelled reposted=$reposted"
        )
    }

    suspend fun cancelReminder(groupUuid: UUID) = withReminderScheduleLock {
        cancelReminderSchedule(groupUuid)
        // A deleted plan reaches only this, not a reschedule.
        reconcileDoseReminderNotifications(
            remindersEnabled = settingsRepository.getCurrentSettings().remindersEnabled
        )
    }

    private suspend fun cancelReminderSchedule(groupUuid: UUID) {
        diagnosticsLogger.info(TAG, "reminder_cancel_start groupUuid=$groupUuid")
        cancelReminderAlarm(groupUuid)
        reminderScheduleStore.removeScheduledGroupUuid(groupUuid)
        diagnosticsLogger.info(TAG, "reminder_cancel_complete groupUuid=$groupUuid")
    }

    fun canScheduleExactReminders(): Boolean {
        return canScheduleExactAlarms(alarmManager, sdkIntProvider())
    }

    private fun scheduleReminder(plan: MedicationReminderPlan) {
        val triggerAtMillis = plan.scheduledAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val pendingIntent = buildReminderPendingIntent(
            groupUuid = plan.groupUuid,
            scheduledAt = plan.scheduledAt,
            scheduleTimeUuid = plan.scheduleTimeUuid,
        )

        val exactAlarm = canScheduleExactReminders()
        diagnosticsLogger.info(
            TAG,
            "reminder_alarm_schedule groupUuid=${plan.groupUuid} " +
                    "scheduleTimeUuid=${plan.scheduleTimeUuid} scheduledAt=${plan.scheduledAt} " +
                    "triggerAtMillis=$triggerAtMillis exact=$exactAlarm"
        )

        if (exactAlarm) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (error: SecurityException) {
                // TOCTOU: the OS could revoke SCHEDULE_EXACT_ALARM between the
                // capability check and the setExact call. Fall back to inexact
                // so the alarm still fires (with reduced precision) instead of
                // the exception propagating up to BroadcastReceiver crash.
                diagnosticsLogger.warning(
                    TAG,
                    "reminder_alarm_schedule_exact_revoked groupUuid=${plan.groupUuid} " +
                            "scheduledAt=${plan.scheduledAt}",
                    error,
                )
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun buildReminderPendingIntent(
        groupUuid: UUID,
        scheduledAt: LocalDateTime? = null,
        scheduleTimeUuid: UUID? = null,
    ): PendingIntent {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            data = reminderIntentData(groupUuid)
            putExtra(EXTRA_GROUP_UUID, groupUuid.toString())
            scheduleTimeUuid?.let { value ->
                putExtra(EXTRA_SCHEDULE_TIME_UUID, value.toString())
            }
            scheduledAt?.let { value ->
                putExtra(EXTRA_SCHEDULED_AT, value.toString())
            }
        }

        return PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelReminderAlarm(groupUuid: UUID) {
        diagnosticsLogger.info(TAG, "reminder_alarm_cancel groupUuid=$groupUuid")
        alarmManager.cancel(buildReminderPendingIntent(groupUuid))
    }

    private companion object {
        const val TAG = "MedicationReminderScheduler"
    }
}

// ponytail: one process-wide lock for both schedulers; per-plan locks if passes ever queue.
private val reminderScheduleMutex = Mutex()

private object ReminderScheduleLockHeld :
    CoroutineContext.Element,
    CoroutineContext.Key<ReminderScheduleLockHeld> {
    override val key: CoroutineContext.Key<*>
        get() = this
}

/**
 * Runs [block] under the lock every alarm and schedule-store change of both schedulers takes,
 * so no two passes interleave their reads and writes. A call made under it, as rescheduleGroup
 * clearing its plan's stale snoozes, runs in the same hold. Nothing under it may wait on the
 * Home mutation gate: a restore marks a reconcile while it holds the gate.
 */
internal suspend fun <T> withReminderScheduleLock(block: suspend () -> T): T =
    if (currentCoroutineContext()[ReminderScheduleLockHeld] != null) {
        block()
    } else {
        reminderScheduleMutex.withLock { withContext(ReminderScheduleLockHeld) { block() } }
    }

internal fun reminderIntentData(groupUuid: UUID): Uri {
    return "$REMINDER_INTENT_URI_PREFIX/$groupUuid".toUri()
}

private fun Map<UUID, LocalDateTime?>.armedTimes(): Map<UUID, LocalDateTime> {
    return mapNotNull { (uuid, scheduledAt) -> scheduledAt?.let { uuid to it } }.toMap()
}

private const val REMINDER_REQUEST_CODE = 0
private const val REMINDER_INTENT_URI_PREFIX = "hrttracker://medication-reminder"
const val ACTION_MEDICATION_REMINDER = "com.mkx.hrttracker.action.MEDICATION_REMINDER"
const val EXTRA_GROUP_UUID = "groupUuid"
const val EXTRA_SCHEDULE_TIME_UUID = "scheduleTimeUuid"
const val EXTRA_SCHEDULED_AT = "scheduledAt"
