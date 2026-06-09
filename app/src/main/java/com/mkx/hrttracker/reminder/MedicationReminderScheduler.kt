package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationReminderScheduler private constructor(
    private val context: Context,
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduleStore: ReminderScheduleStore,
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
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
        diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    ) : this(
        context = context,
        medicationGroupRepository = medicationGroupRepository,
        medicationLogRepository = medicationLogRepository,
        settingsRepository = settingsRepository,
        reminderScheduleStore = reminderScheduleStore,
        medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
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
        diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
        sdkInt: Int,
    ) : this(
        context = context,
        medicationGroupRepository = medicationGroupRepository,
        medicationLogRepository = medicationLogRepository,
        settingsRepository = settingsRepository,
        reminderScheduleStore = reminderScheduleStore,
        medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
        diagnosticsLogger = diagnosticsLogger,
        sdkIntProvider = { sdkInt },
    )

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()) {
        diagnosticsLogger.info(TAG, "reminder_reschedule_all_start now=$now")
        val groups = medicationGroupRepository.getGroups()
        val groupUuids = groups.mapTo(mutableSetOf()) { group -> group.uuid }
        val storedGroupUuids = reminderScheduleStore.getScheduledGroupUuids()
        val uuidsToCancel = storedGroupUuids + groupUuids

        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_all_disabled groups=${groups.size} " +
                        "stored=${storedGroupUuids.size} cancel=${uuidsToCancel.size}"
            )
            uuidsToCancel.forEach(::cancelReminderAlarm)
            reminderScheduleStore.replaceScheduledGroupUuids(emptySet())
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_all_complete groups=${groups.size} entries=0 plans=0 stored=0"
            )
            return
        }

        val entries = medicationLogRepository.getScheduledGroupEntriesSince(now)
        val plans = buildNextMedicationReminderPlans(
            groups = groups,
            entries = entries,
            now = now
        )

        uuidsToCancel.forEach(::cancelReminderAlarm)
        plans.forEach { plan ->
            scheduleReminder(plan)
        }
        reminderScheduleStore.replaceScheduledGroupUuids(
            plans.mapTo(mutableSetOf(), MedicationReminderPlan::groupUuid)
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
    }

    suspend fun rescheduleFromHomeSnapshot(
        snapshot: HomeSnapshotRecord,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_from_snapshot_start now=$now " +
                    "generation=${snapshot.generation} groups=${snapshot.activeGroups.size} " +
                    "snapshotEntries=${snapshot.scheduleEntries.size}"
        )
        val groups = snapshot.activeGroups
        val groupUuids = groups.mapTo(mutableSetOf()) { group -> group.uuid }
        val storedGroupUuids = reminderScheduleStore.getScheduledGroupUuids()
        val uuidsToCancel = storedGroupUuids + groupUuids

        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_from_snapshot_disabled groups=${groups.size} " +
                        "stored=${storedGroupUuids.size} cancel=${uuidsToCancel.size}"
            )
            uuidsToCancel.forEach(::cancelReminderAlarm)
            reminderScheduleStore.replaceScheduledGroupUuids(emptySet())
            diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_from_snapshot_complete groups=${groups.size} entries=0 plans=0 stored=0"
            )
            return
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
        )

        uuidsToCancel.forEach(::cancelReminderAlarm)
        plans.forEach { plan ->
            scheduleReminder(plan)
        }
        reminderScheduleStore.replaceScheduledGroupUuids(
            plans.mapTo(mutableSetOf(), MedicationReminderPlan::groupUuid)
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
    }

    suspend fun rescheduleGroup(
        groupUuid: UUID,
        after: LocalDateTime = LocalDateTime.now()
    ) {
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_start groupUuid=$groupUuid after=$after"
        )
        cancelReminder(groupUuid)

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
            now = after
        ).firstOrNull() ?: return diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_skipped reason=no_plan groupUuid=$groupUuid entries=${entries.size}"
        )

        scheduleReminder(plan)
        reminderScheduleStore.addScheduledGroupUuid(plan.groupUuid)
        diagnosticsLogger.info(
            TAG,
            "reminder_reschedule_group_complete groupUuid=${plan.groupUuid} " +
                    "scheduledAt=${plan.scheduledAt} scheduleTimeUuid=${plan.scheduleTimeUuid} entries=${entries.size}"
        )
    }

    suspend fun cancelReminder(groupUuid: UUID) {
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

internal fun reminderIntentData(groupUuid: UUID): Uri {
    return "$REMINDER_INTENT_URI_PREFIX/$groupUuid".toUri()
}

private const val REMINDER_REQUEST_CODE = 0
private const val REMINDER_INTENT_URI_PREFIX = "hrttracker://medication-reminder"
const val ACTION_MEDICATION_REMINDER = "com.mkx.hrttracker.action.MEDICATION_REMINDER"
const val EXTRA_GROUP_UUID = "groupUuid"
const val EXTRA_SCHEDULE_TIME_UUID = "scheduleTimeUuid"
const val EXTRA_SCHEDULED_AT = "scheduledAt"
