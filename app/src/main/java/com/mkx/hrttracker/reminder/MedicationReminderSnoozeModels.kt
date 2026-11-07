package com.mkx.hrttracker.reminder

import java.time.Instant

data class MedicationReminderSnoozeRecord(
    val slot: MedicationReminderSlot,
    /**
     * When the snooze is due, as an instant: a zone or DST change doesn't move it. A wall-clock
     * change still does, as it moves every alarm.
     */
    val snoozeAt: Instant,
    val snoozeCount: Int,
    /** Its alarm reached the app. Not fired and past its time, it never did. */
    val fired: Boolean = false,
)

internal fun buildNextSnoozeRecords(
    slots: List<MedicationReminderSlot>,
    existingRecords: List<MedicationReminderSnoozeRecord>,
    snoozeAt: Instant,
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
