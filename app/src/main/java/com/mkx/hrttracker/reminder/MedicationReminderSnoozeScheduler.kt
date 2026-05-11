package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class MedicationReminderSnoozeScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val snoozeStore: MedicationReminderSnoozeStore,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    suspend fun snoozeSlots(
        slots: List<MedicationReminderSlot>,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<MedicationReminderSnoozeRecord> {
        if (slots.isEmpty()) {
            diagnosticsLogger.info(TAG, "snooze_slots_skipped reason=empty_slots now=$now")
            return emptyList()
        }

        diagnosticsLogger.info(TAG, "snooze_slots_start slots=${slots.size} now=$now")
        val existingRecords = snoozeStore.getSnoozeRecords()
        val snoozeAt = now.plusMinutes(REMINDER_SNOOZE_MINUTES)
        val nextRecords = buildNextSnoozeRecords(
            slots = slots,
            existingRecords = existingRecords,
            snoozeAt = snoozeAt,
        )
        val updatedRecords = existingRecords
            .filterNot { record -> record.slot in slots }
            .plus(nextRecords)

        snoozeStore.replaceSnoozeRecords(updatedRecords)
        scheduleSnoozeRecords(nextRecords)
        diagnosticsLogger.info(
            TAG,
            "snooze_slots_complete slots=${slots.size} scheduled=${nextRecords.size} " +
                "existing=${existingRecords.size} stored=${updatedRecords.size} snoozeAt=$snoozeAt"
        )
        return nextRecords
    }

    suspend fun clearSnoozesForSlots(slots: List<MedicationReminderSlot>) {
        if (slots.isEmpty()) {
            diagnosticsLogger.info(TAG, "snooze_clear_slots_skipped reason=empty_slots")
            return
        }

        diagnosticsLogger.info(TAG, "snooze_clear_slots_start slots=${slots.size}")
        clearSnoozesMatching { record -> record.slot in slots }
    }

    suspend fun clearStaleSnoozesForGroup(group: MedicationGroup) {
        diagnosticsLogger.info(TAG, "snooze_clear_stale_group_start groupUuid=${group.uuid}")
        val slotTimesByUuid = group.schedule.timeSlots
            .associate { slot -> slot.uuid to slot.time }
        clearSnoozesMatching { record ->
            val slot = record.slot
            slot.groupUuid == group.uuid &&
                !slot.matchesCurrentScheduleTime(slotTimesByUuid)
        }
    }

    /**
     * Clear every snooze record tied to [groupUuid], regardless of whether the slot
     * still matches the group's current schedule. Use after archive or delete to
     * avoid waking the device for a slot whose owning group no longer exists.
     */
    suspend fun clearSnoozesForGroup(groupUuid: UUID) {
        diagnosticsLogger.info(TAG, "snooze_clear_group_start groupUuid=$groupUuid")
        clearSnoozesMatching { record -> record.slot.groupUuid == groupUuid }
    }

    private suspend fun clearSnoozesMatching(
        shouldClear: (MedicationReminderSnoozeRecord) -> Boolean,
    ) {
        val existingRecords = snoozeStore.getSnoozeRecords()
        val affectedSnoozeTimes = existingRecords
            .filter(shouldClear)
            .map(MedicationReminderSnoozeRecord::snoozeAt)
            .toSet()
        if (affectedSnoozeTimes.isEmpty()) {
            diagnosticsLogger.info(
                TAG,
                "snooze_clear_matching_complete cleared=0 existing=${existingRecords.size}"
            )
            return
        }

        val affectedRecords = existingRecords.filter { record ->
            record.snoozeAt in affectedSnoozeTimes
        }
        affectedRecords
            .groupBy(MedicationReminderSnoozeRecord::snoozeAt)
            .values
            .forEach(::cancelSnoozeBundle)

        val remainingRecords = existingRecords.filterNot(shouldClear)
        snoozeStore.replaceSnoozeRecords(remainingRecords)
        remainingRecords
            .filter { record -> record.snoozeAt in affectedSnoozeTimes }
            .groupBy(MedicationReminderSnoozeRecord::snoozeAt)
            .values
            .forEach(::scheduleSnoozeBundle)
        diagnosticsLogger.info(
            TAG,
            "snooze_clear_matching_complete cleared=${existingRecords.size - remainingRecords.size} " +
                "affectedBundles=${affectedSnoozeTimes.size} stored=${remainingRecords.size}"
        )
    }

    suspend fun clearAllSnoozes() {
        diagnosticsLogger.info(TAG, "snooze_clear_all_start")
        val existingRecords = snoozeStore.getSnoozeRecords()
        existingRecords
            .groupBy(MedicationReminderSnoozeRecord::snoozeAt)
            .values
            .forEach(::cancelSnoozeBundle)
        snoozeStore.replaceSnoozeRecords(emptyList())
        diagnosticsLogger.info(
            TAG,
            "snooze_clear_all_complete cleared=${existingRecords.size} " +
                "bundles=${existingRecords.groupBy(MedicationReminderSnoozeRecord::snoozeAt).size}"
        )
    }

    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()) {
        diagnosticsLogger.info(TAG, "snooze_reschedule_all_start now=$now")
        val records = snoozeStore.getSnoozeRecords()
        val futureRecords = records.filter { record -> record.snoozeAt.isAfter(now) }
        if (futureRecords.size != records.size) {
            snoozeStore.replaceSnoozeRecords(futureRecords)
            diagnosticsLogger.info(
                TAG,
                "snooze_reschedule_all_pruned expired=${records.size - futureRecords.size} " +
                    "remaining=${futureRecords.size}"
            )
        }
        scheduleSnoozeRecords(futureRecords)
        diagnosticsLogger.info(
            TAG,
            "snooze_reschedule_all_complete records=${futureRecords.size} " +
                "bundles=${futureRecords.groupBy(MedicationReminderSnoozeRecord::snoozeAt).size}"
        )
    }

    suspend fun getSnoozeRecords(): List<MedicationReminderSnoozeRecord> {
        return snoozeStore.getSnoozeRecords()
    }

    private fun scheduleSnoozeRecords(records: List<MedicationReminderSnoozeRecord>) {
        records
            .groupBy(MedicationReminderSnoozeRecord::snoozeAt)
            .values
            .forEach(::scheduleSnoozeBundle)
    }

    private fun scheduleSnoozeBundle(records: List<MedicationReminderSnoozeRecord>) {
        if (records.isEmpty()) {
            return
        }

        val triggerAtMillis = records.first().snoozeAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val pendingIntent = buildSnoozePendingIntent(records)

        diagnosticsLogger.info(
            TAG,
            "snooze_alarm_schedule snoozeAt=${records.first().snoozeAt} " +
                "slots=${records.size} triggerAtMillis=$triggerAtMillis"
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    private fun cancelSnoozeBundle(records: List<MedicationReminderSnoozeRecord>) {
        if (records.isEmpty()) {
            return
        }
        diagnosticsLogger.info(
            TAG,
            "snooze_alarm_cancel snoozeAt=${records.first().snoozeAt} slots=${records.size}"
        )
        alarmManager.cancel(buildSnoozePendingIntent(records))
    }

    private fun buildSnoozePendingIntent(
        records: List<MedicationReminderSnoozeRecord>,
    ): PendingIntent {
        val slots = records.map(MedicationReminderSnoozeRecord::slot)
        val intent = Intent(context, MedicationReminderActionReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER_SNOOZE_ALARM
            data = snoozeIntentData(records)
            putStringArrayListExtra(
                EXTRA_REMINDER_SLOTS,
                ArrayList(slots.map(MedicationReminderSlot::toStorageValue)),
            )
        }

        return PendingIntent.getBroadcast(
            context,
            REMINDER_SNOOZE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private fun snoozeIntentData(records: List<MedicationReminderSnoozeRecord>): Uri {
    val snoozeAt = records.firstOrNull()?.snoozeAt ?: LocalDateTime.MIN
    val slotsKey = records
        .map { record -> record.slot.toStorageValue() }
        .sorted()
        .joinToString(separator = ";")
    val bundleUuid = UUID.nameUUIDFromBytes(slotsKey.toByteArray(StandardCharsets.UTF_8))
    return "$REMINDER_SNOOZE_URI_PREFIX/$snoozeAt/$bundleUuid".toUri()
}

private fun MedicationReminderSlot.matchesCurrentScheduleTime(
    slotTimesByUuid: Map<UUID, LocalTime>,
): Boolean {
    val scheduleTimeUuid = scheduleTimeUuid ?: return false
    val currentTime = slotTimesByUuid[scheduleTimeUuid] ?: return false
    return currentTime == scheduledAt.toLocalTime()
}

private const val REMINDER_SNOOZE_REQUEST_CODE = 0
private const val REMINDER_SNOOZE_URI_PREFIX = "hrttracker://medication-reminder-snooze"
private const val TAG = "MedicationReminderSnoozeScheduler"
