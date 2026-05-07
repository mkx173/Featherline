package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationReminderSnoozeScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val snoozeStore: MedicationReminderSnoozeStore,
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    suspend fun snoozeSlots(
        slots: List<MedicationReminderSlot>,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<MedicationReminderSnoozeRecord> {
        if (slots.isEmpty()) {
            return emptyList()
        }

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
        return nextRecords
    }

    suspend fun clearSnoozesForSlots(slots: List<MedicationReminderSlot>) {
        if (slots.isEmpty()) {
            return
        }

        val existingRecords = snoozeStore.getSnoozeRecords()
        val affectedSnoozeTimes = existingRecords
            .filter { record -> record.slot in slots }
            .map(MedicationReminderSnoozeRecord::snoozeAt)
            .toSet()
        if (affectedSnoozeTimes.isEmpty()) {
            return
        }

        val affectedRecords = existingRecords.filter { record ->
            record.snoozeAt in affectedSnoozeTimes
        }
        affectedRecords
            .groupBy(MedicationReminderSnoozeRecord::snoozeAt)
            .values
            .forEach(::cancelSnoozeBundle)

        val remainingRecords = existingRecords.filterNot { record -> record.slot in slots }
        snoozeStore.replaceSnoozeRecords(remainingRecords)
        remainingRecords
            .filter { record -> record.snoozeAt in affectedSnoozeTimes }
            .groupBy(MedicationReminderSnoozeRecord::snoozeAt)
            .values
            .forEach(::scheduleSnoozeBundle)
    }

    suspend fun clearAllSnoozes() {
        val existingRecords = snoozeStore.getSnoozeRecords()
        existingRecords
            .groupBy(MedicationReminderSnoozeRecord::snoozeAt)
            .values
            .forEach(::cancelSnoozeBundle)
        snoozeStore.replaceSnoozeRecords(emptyList())
    }

    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()) {
        val records = snoozeStore.getSnoozeRecords()
        val futureRecords = records.filter { record -> record.snoozeAt.isAfter(now) }
        if (futureRecords.size != records.size) {
            snoozeStore.replaceSnoozeRecords(futureRecords)
        }
        scheduleSnoozeRecords(futureRecords)
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
    return Uri.parse("$REMINDER_SNOOZE_URI_PREFIX/$snoozeAt/$bundleUuid")
}

private const val REMINDER_SNOOZE_REQUEST_CODE = 0
private const val REMINDER_SNOOZE_URI_PREFIX = "hrttracker://medication-reminder-snooze"
