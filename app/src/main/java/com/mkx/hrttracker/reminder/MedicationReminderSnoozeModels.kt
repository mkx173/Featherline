package com.mkx.hrttracker.reminder

import java.time.LocalDateTime

data class MedicationReminderSnoozeRecord(
    val slot: MedicationReminderSlot,
    val snoozeAt: LocalDateTime,
    val snoozeCount: Int,
)

internal fun buildNextSnoozeRecords(
    slots: List<MedicationReminderSlot>,
    existingRecords: List<MedicationReminderSnoozeRecord>,
    snoozeAt: LocalDateTime,
    maxSnoozeCount: Int = MAX_REMINDER_SNOOZE_COUNT,
): List<MedicationReminderSnoozeRecord> {
    val existingRecordsBySlot = existingRecords.associateBy(MedicationReminderSnoozeRecord::slot)
    return slots.distinct()
        .mapNotNull { slot ->
            val nextCount = existingRecordsBySlot[slot]?.snoozeCount
                ?.plus(1)
                ?: 1
            if (nextCount <= maxSnoozeCount) {
                MedicationReminderSnoozeRecord(
                    slot = slot,
                    snoozeAt = snoozeAt,
                    snoozeCount = nextCount,
                )
            } else {
                null
            }
        }
}

internal const val MAX_REMINDER_SNOOZE_COUNT = 4
internal const val REMINDER_SNOOZE_MINUTES = 15L
