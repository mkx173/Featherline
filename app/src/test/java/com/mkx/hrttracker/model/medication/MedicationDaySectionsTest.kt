package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.MedicationDayBoundary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class MedicationDaySectionsTest {
    @Test
    fun aNightOwlOffPlanRecordStaysOnItsNightWhenAllGroupsLeavesItsPlanOut() {
        val medicine = testMedicine()
        val group = MedicationGroup(
            uuid = UUID.fromString("3f2e1d0c-0000-4000-8000-000000000001"),
            name = "Evening",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 10, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(1, 0)),
                dayBoundary = MedicationDayBoundary.FourAm,
            ),
            medications = listOf(testMedicationGroupMedication(medicine = medicine)),
            createdAt = Instant.parse("2026-10-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
        )
        // An extra medicine taken with Thursday night's 01:00 dose, at 01:05 on Friday:
        // off the plan, so it is listed on its own.
        val record = testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.SPIRONOLACTONE),
            sourceGroupUuid = group.uuid,
            appliedAt = testInstant(LocalDateTime.of(2026, 10, 30, 1, 5)),
            scheduledFor = LocalDateTime.of(2026, 10, 30, 1, 0),
        )

        val sections = buildMedicationDaySections(
            source = MedicationDaySectionSource(
                groups = listOf(group),
                entries = listOf(record),
                allGroups = emptyList(),
            ),
            now = LocalDateTime.of(2026, 10, 30, 2, 0),
        )

        assertEquals(listOf(record), sections.todaySchedule.unplannedEntries)
    }
}
