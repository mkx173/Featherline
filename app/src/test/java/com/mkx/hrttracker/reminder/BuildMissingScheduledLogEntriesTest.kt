package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class BuildMissingScheduledLogEntriesTest {

    @Test
    fun returns_entries_for_active_group_even_when_notificationsEnabled_is_false() {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            notificationsEnabled = false,
            medicationCount = 1,
        )
        val slot = group.toReminderSlot(scheduledAt)

        val result = buildMissingScheduledLogEntries(
            group = group,
            slot = slot,
            entries = emptyList(),
            appliedAt = scheduledAt.plusMinutes(1),
        )

        assertEquals(1, result.size)
        assertEquals(group.uuid, result.first().sourceGroupUuid)
    }

    @Test
    fun returns_empty_list_for_inactive_group() {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            notificationsEnabled = true,
            medicationCount = 1,
            archivedAt = Instant.parse("2026-04-10T00:00:00Z"),
        )
        val slot = group.toReminderSlot(scheduledAt)

        val result = buildMissingScheduledLogEntries(
            group = group,
            slot = slot,
            entries = emptyList(),
            appliedAt = scheduledAt.plusMinutes(1),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun returns_empty_list_when_slot_already_fully_logged() {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("33333333-3333-3333-3333-333333333333"),
            notificationsEnabled = false,
            medicationCount = 2,
        )
        val slot = group.toReminderSlot(scheduledAt)
        val templateMedication = group.medications.first()
        val existingEntry = testMedicationLogEntry(
            medicine = templateMedication.medicine,
            applicationType = templateMedication.applicationType,
            doseInstruction = templateMedication.doseInstruction,
            sourceGroupUuid = group.uuid,
            appliedAt = testInstant(scheduledAt.plusMinutes(1)),
            scheduledFor = scheduledAt,
            count = 2,
            scheduleTimeUuid = group.schedule.timeSlots.first().uuid,
        )

        val result = buildMissingScheduledLogEntries(
            group = group,
            slot = slot,
            entries = listOf(existingEntry),
            appliedAt = scheduledAt.plusMinutes(5),
        )

        assertTrue(result.isEmpty())
    }

    // --- helpers ---

    private fun medicationGroup(
        uuid: UUID,
        notificationsEnabled: Boolean,
        medicationCount: Int,
        archivedAt: Instant? = null,
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = "Test Group",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    medicine = testMedicine(key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.WholeUnit,
                    count = medicationCount,
                )
            ),
            notificationsEnabled = notificationsEnabled,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
            archivedAt = archivedAt,
        )
    }

    private fun MedicationGroup.toReminderSlot(scheduledAt: LocalDateTime): MedicationReminderSlot {
        return MedicationReminderSlot(
            groupUuid = uuid,
            scheduledAt = scheduledAt,
            scheduleTimeUuid = schedule.timeSlots.first().uuid,
        )
    }
}
