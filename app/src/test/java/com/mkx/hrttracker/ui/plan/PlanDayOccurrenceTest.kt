package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class PlanDayOccurrenceTest {
    @Test
    fun buildPlanDaySchedule_marks_upcoming_slot_within_one_hour_as_due_soon() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0), LocalTime.of(10, 30))
            )
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 10, 0)
        )

        assertFalse(schedule.scheduledEntries[0].isDueSoon)
        assertTrue(schedule.scheduledEntries[1].isDueSoon)
    }

    private fun medicationGroup(schedule: MedicationGroupSchedule): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString("77365b1a-aa5d-427e-9313-0c56241ecbaa"),
            name = "Test group",
            schedule = schedule,
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("258ae865-c7d2-44ef-8cf2-3257451f57d1"),
                    routeOfAdministration = RouteOfAdministration.ORAL,
                    medicineName = "Estradiol",
                    dosageMgAsMedicine = 2.0
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
    }
}
