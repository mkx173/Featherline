package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.previousScheduledForBefore
import com.mkx.hrttracker.model.settings.PersistentReminderSettings
import java.time.LocalDateTime

/**
 * One pending dose's place in its repeat chain: when it is re-posted next, and
 * how many repeats have already followed its first reminder.
 */
data class MedicationReminderRepeatRecord(
    val slot: MedicationReminderSlot,
    val repeatAt: LocalDateTime,
    val repeatCount: Int,
)

/**
 * The next link of each slot's chain at [repeatAt]. A slot that already had
 * [maxRepeats] repeats drops out; null [maxRepeats] never drops one.
 */
internal fun buildNextRepeatRecords(
    slots: List<MedicationReminderSlot>,
    existingRecords: List<MedicationReminderRepeatRecord>,
    repeatAt: LocalDateTime,
    maxRepeats: Int?,
): List<MedicationReminderRepeatRecord> {
    val existingRecordsBySlot = existingRecords.associateBy(MedicationReminderRepeatRecord::slot)
    return slots.distinct()
        .mapNotNull { slot ->
            val nextCount = (existingRecordsBySlot[slot]?.repeatCount ?: 0) + 1
            if (maxRepeats == null || nextCount <= maxRepeats) {
                MedicationReminderRepeatRecord(
                    slot = slot,
                    repeatAt = repeatAt,
                    repeatCount = nextCount,
                )
            } else {
                null
            }
        }
}

/**
 * The full-screen alarm these settings ask a reminder for [slots] for, or null
 * for a plain one: off, or none of its plans take part in persistent reminders.
 */
internal fun PersistentReminderSettings.doseReminderAlarm(
    slots: List<MedicationReminderSlot>,
): DoseReminderAlarm? =
    if (fullScreenAlarmEnabled && slots.any { slot -> appliesTo(slot.groupUuid) }) {
        DoseReminderAlarm(playsSound = alarmSoundEnabled)
    } else {
        null
    }

/** Whether a reminder for [slots] repeats until logged: repeats on, and one of its plans takes part. */
internal fun PersistentReminderSettings.repeatsAny(slots: List<MedicationReminderSlot>): Boolean =
    repeatEnabled && slots.any { slot -> appliesTo(slot.groupUuid) }

/**
 * Whether [group] has had another dose fall due after [slot] by [now]. That
 * dose gets its own reminder, which takes over from this one's repeats, as the
 * reminder receiver does for a late delivery.
 */
internal fun MedicationGroup.isDueAgainSince(
    slot: MedicationReminderSlot,
    now: LocalDateTime,
): Boolean = previousScheduledForBefore(now)?.isAfter(slot.scheduledAt) == true
