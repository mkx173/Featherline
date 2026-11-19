package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class PlanUpcomingOccurrencesTest {
    // Shared so the slot/entry signature matches between the scheduled
    // medication and the fulfilling log entry.
    private val estradiolMedicineUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun buildNextOccurrencesByGroup_skips_past_slots_on_today() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
            )
        )

        val upcoming = buildNextOccurrencesByGroup(
            groups = listOf(group),
            entries = emptyList(),
            start = LocalDateTime.of(2026, 4, 18, 12, 0),
            limit = 3,
        ).getValue(group.uuid)

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 18, 20, 0),
                LocalDateTime.of(2026, 4, 19, 8, 0),
                LocalDateTime.of(2026, 4, 19, 20, 0)
            ),
            upcoming
        )
    }

    @Test
    fun buildNextOccurrencesByGroup_skips_future_slots_that_are_already_logged() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
            )
        )

        val upcoming = buildNextOccurrencesByGroup(
            groups = listOf(group),
            entries = listOf(
                scheduledEntry(
                    groupUuid = group.uuid,
                    scheduledFor = LocalDateTime.of(2026, 4, 18, 20, 0)
                )
            ),
            start = LocalDateTime.of(2026, 4, 18, 12, 0),
            limit = 3,
        ).getValue(group.uuid)

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 19, 8, 0),
                LocalDateTime.of(2026, 4, 19, 20, 0),
                LocalDateTime.of(2026, 4, 20, 8, 0)
            ),
            upcoming
        )
    }

    @Test
    fun buildNextOccurrencesByGroup_skips_future_slot_logged_early_outside_its_window() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
            )
        )

        // Taken at 10:00 for 20:00: 10 h early, past the 6 h on-time window.
        val upcoming = buildNextOccurrencesByGroup(
            groups = listOf(group),
            entries = listOf(
                scheduledEntry(
                    groupUuid = group.uuid,
                    scheduledFor = LocalDateTime.of(2026, 4, 18, 20, 0),
                    appliedAt = LocalDateTime.of(2026, 4, 18, 10, 0),
                )
            ),
            start = LocalDateTime.of(2026, 4, 18, 12, 0),
            limit = 3,
        ).getValue(group.uuid)

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 19, 8, 0),
                LocalDateTime.of(2026, 4, 19, 20, 0),
                LocalDateTime.of(2026, 4, 20, 8, 0)
            ),
            upcoming
        )
    }

    @Test
    fun buildNextOccurrencesByGroup_keeps_partly_logged_multi_medicine_slot() {
        val estradiolOnly = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
            )
        )
        val group = estradiolOnly.copy(
            medications = estradiolOnly.medications + testMedicationGroupMedication(
                uuid = UUID.fromString("9b0f6a3e-0d7c-4c55-9a51-3f4b2f6d1c20"),
                medicine = testMedicine(
                    uuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    key = MedicationKey.SPIRONOLACTONE,
                ),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            )
        )

        // Only the estradiol is logged (early); the spironolactone is still owed.
        val upcoming = buildNextOccurrencesByGroup(
            groups = listOf(group),
            entries = listOf(
                scheduledEntry(
                    groupUuid = group.uuid,
                    scheduledFor = LocalDateTime.of(2026, 4, 18, 20, 0),
                    appliedAt = LocalDateTime.of(2026, 4, 18, 10, 0),
                )
            ),
            start = LocalDateTime.of(2026, 4, 18, 12, 0),
            limit = 3,
        ).getValue(group.uuid)

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 18, 20, 0),
                LocalDateTime.of(2026, 4, 19, 8, 0),
                LocalDateTime.of(2026, 4, 19, 20, 0)
            ),
            upcoming
        )
    }

    @Test
    fun buildNextOccurrencesByGroup_keeps_slot_exactly_at_start_time() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(12, 0), LocalTime.of(20, 0))
            )
        )

        val upcoming = buildNextOccurrencesByGroup(
            groups = listOf(group),
            entries = emptyList(),
            start = LocalDateTime.of(2026, 4, 18, 12, 0),
            limit = 3,
        ).getValue(group.uuid)

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 18, 12, 0),
                LocalDateTime.of(2026, 4, 18, 20, 0),
                LocalDateTime.of(2026, 4, 19, 12, 0)
            ),
            upcoming
        )
    }

    // The timeout turns a scan that never ends (a plan with no medication) into a failure.
    @Test(timeout = 10_000)
    fun buildNextOccurrencesByGroup_lists_doses_months_apart_as_reminders_ring_them() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 120,
                since = LocalDate.of(2026, 1, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            )
        )

        val upcoming = buildNextOccurrencesByGroup(
            groups = listOf(group, group.copy(uuid = UUID.randomUUID(), medications = emptyList())),
            entries = emptyList(),
            start = LocalDateTime.of(2026, 1, 2, 12, 0),
            limit = 2,
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 5, 1, 9, 0), LocalDateTime.of(2026, 8, 29, 9, 0)),
            upcoming.getValue(group.uuid)
        )
        assertEquals(2, upcoming.size)
    }

    private fun medicationGroup(schedule: MedicationGroupSchedule): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString("ef6bec56-adbf-46f3-bcec-a10ff54fe5eb"),
            name = "Test group",
            schedule = schedule,
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("7c5db940-377c-485e-b43a-e2ab09be3e7a"),
                    medicine = testMedicine(
                        uuid = estradiolMedicineUuid,
                        key = MedicationKey.ESTRADIOL,
                    ),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
    }

    private fun scheduledEntry(
        groupUuid: UUID,
        scheduledFor: LocalDateTime,
        appliedAt: LocalDateTime = scheduledFor,
    ): MedicationLogEntry {
        return testMedicationLogEntry(
            uuid = UUID.fromString("24a11fa0-01eb-4990-8c5d-832e5f8ecb50"),
            medicine = testMedicine(
                uuid = estradiolMedicineUuid,
                key = MedicationKey.ESTRADIOL,
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor
        )
    }
}
