package com.mkx.hrttracker.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class MedicationReminderSnoozePlannerTest {
    @Test
    fun buildNextSnoozeRecords_increments_counts_until_the_four_snooze_limit() {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 15)
        val allowedSlot = reminderSlot(
            groupUuid = UUID.fromString("ff72924e-4952-4831-8e6f-65ae9eb92110"),
            scheduledAt = scheduledAt,
        )
        val exhaustedSlot = reminderSlot(
            groupUuid = UUID.fromString("c77cf4e1-f21d-45f9-abd4-4687bf09d054"),
            scheduledAt = scheduledAt,
        )

        val records = buildNextSnoozeRecords(
            slots = listOf(allowedSlot, exhaustedSlot),
            existingRecords = listOf(
                MedicationReminderSnoozeRecord(
                    slot = allowedSlot,
                    snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 0),
                    snoozeCount = 3,
                ),
                MedicationReminderSnoozeRecord(
                    slot = exhaustedSlot,
                    snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 0),
                    snoozeCount = 4,
                ),
            ),
            snoozeAt = snoozeAt,
        )

        assertEquals(
            listOf(
                MedicationReminderSnoozeRecord(
                    slot = allowedSlot,
                    snoozeAt = snoozeAt,
                    snoozeCount = 4,
                )
            ),
            records
        )
    }

    private fun reminderSlot(
        groupUuid: UUID,
        scheduledAt: LocalDateTime,
    ): MedicationReminderSlot {
        return MedicationReminderSlot(
            groupUuid = groupUuid,
            scheduledAt = scheduledAt,
            scheduleTimeUuid = UUID.nameUUIDFromBytes(groupUuid.toString().toByteArray()),
        )
    }
}
