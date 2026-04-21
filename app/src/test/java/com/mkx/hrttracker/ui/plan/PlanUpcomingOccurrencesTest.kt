package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class PlanUpcomingOccurrencesTest {
    @Test
    fun buildNextOccurrencesByGroup_includes_past_slots_on_today() {
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
            limit = 3
        ).getValue(group.uuid)

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 18, 8, 0),
                LocalDateTime.of(2026, 4, 18, 20, 0),
                LocalDateTime.of(2026, 4, 19, 8, 0)
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
            limit = 3
        ).getValue(group.uuid)

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 18, 8, 0),
                LocalDateTime.of(2026, 4, 19, 8, 0),
                LocalDateTime.of(2026, 4, 19, 20, 0)
            ),
            upcoming
        )
    }

    private fun medicationGroup(schedule: MedicationGroupSchedule): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString("ef6bec56-adbf-46f3-bcec-a10ff54fe5eb"),
            name = "Test group",
            schedule = schedule,
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("7c5db940-377c-485e-b43a-e2ab09be3e7a"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0)
                    )
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
    }

    private fun scheduledEntry(
        groupUuid: UUID,
        scheduledFor: LocalDateTime
    ): MedicationLogEntry {
        return testMedicationLogEntry(
            uuid = UUID.fromString("24a11fa0-01eb-4990-8c5d-832e5f8ecb50"),
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            dosageMgAsEstradiol = 2.0,
            sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(scheduledFor),
            scheduledFor = scheduledFor
        )
    }
}
