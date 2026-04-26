package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import org.junit.Assert.assertEquals
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
        assertTrue(schedule.scheduledEntries[0].isPastDue)
        assertTrue(schedule.scheduledEntries[1].isDueSoon)
        assertFalse(schedule.scheduledEntries[1].isPastDue)
        assertEquals(MedicationGroupColorKey.TEAL, schedule.scheduledEntries[0].groupColorKey)
    }

    @Test
    fun buildPlanDaySchedule_does_not_mark_fulfilled_slot_as_past_due() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            )
        )
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)
        val fulfilledEntry = com.mkx.hrttracker.model.medication.testMedicationLogEntry(
            details = group.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = scheduledFor.plusMinutes(3).toLocalDate().atTime(9, 3)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant(),
            scheduledFor = scheduledFor
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = listOf(fulfilledEntry),
            now = LocalDateTime.of(2026, 4, 18, 10, 0)
        )

        assertTrue(schedule.scheduledEntries.single().isFulfilled)
        assertFalse(schedule.scheduledEntries.single().isPastDue)
        assertFalse(schedule.scheduledEntries.single().isDueSoon)
    }

    @Test
    fun buildPlanDaySchedule_collapses_duplicate_matching_medications_into_one_counted_entry() {
        val sharedDetails = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(2.0)
        )
        val group = MedicationGroup(
            uuid = UUID.fromString("3ff64a14-e1fd-4900-b804-f298d9e5e504"),
            name = "Test group",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("43f8f777-86eb-4193-94b8-cf5a3441bc3f"),
                    details = sharedDetails
                ),
                testMedicationGroupMedication(
                    uuid = UUID.fromString("37d67438-f3a9-42ba-a3b8-57a005063b0f"),
                    details = sharedDetails
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 8, 0)
        )

        assertEquals(1, schedule.scheduledEntries.size)
        assertEquals(2, schedule.scheduledEntries.single().medication.count)
    }

    @Test
    fun buildPlanDaySchedule_uses_single_counted_log_row_for_fulfillment_progress() {
        val group = MedicationGroup(
            uuid = UUID.fromString("77365b1a-aa5d-427e-9313-0c56241ecbaa"),
            name = "Test group",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("258ae865-c7d2-44ef-8cf2-3257451f57d1"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0)
                    ),
                    count = 2
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val countedEntryId = UUID.fromString("5a09f5d0-4f91-44d7-8f51-9ff0e9b1498a")
        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = listOf(
                com.mkx.hrttracker.model.medication.testMedicationLogEntry(
                    uuid = countedEntryId,
                    details = group.medications.single().details,
                    dosageMgAsEstradiol = 2.0,
                    sourceGroupUuid = group.uuid,
                    appliedAt = LocalDateTime.of(2026, 4, 18, 9, 3)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant(),
                    scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0),
                    count = 2
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 10, 0)
        )

        val scheduledEntry = schedule.scheduledEntries.single()
        assertEquals(2, scheduledEntry.loggedCount)
        assertEquals(listOf(countedEntryId), scheduledEntry.fulfillingEntryUuids)
        assertTrue(scheduledEntry.isFulfilled)
    }

    @Test
    fun plannedEntryEditorIds_returns_single_backing_id_for_counted_fulfilled_entry() {
        val countedEntryId = UUID.fromString("5a09f5d0-4f91-44d7-8f51-9ff0e9b1498a")
        val scheduledEntry = PlanDayScheduleEntry(
            groupUuid = UUID.fromString("77365b1a-aa5d-427e-9313-0c56241ecbaa"),
            groupName = "Test group",
            groupColorKey = MedicationGroupColorKey.TEAL,
            scheduledTime = LocalTime.of(9, 0),
            medication = testMedicationGroupMedication(
                uuid = UUID.fromString("258ae865-c7d2-44ef-8cf2-3257451f57d1"),
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(2.0)
                ),
                count = 2
            ),
            fulfillingEntryUuids = listOf(countedEntryId),
            loggedCount = 2,
            isFulfilled = true,
            isDueSoon = false,
            isPastDue = false
        )

        assertEquals(setOf(countedEntryId), plannedEntryEditorIds(scheduledEntry))
    }

    private fun medicationGroup(schedule: MedicationGroupSchedule): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString("77365b1a-aa5d-427e-9313-0c56241ecbaa"),
            name = "Test group",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = schedule,
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("258ae865-c7d2-44ef-8cf2-3257451f57d1"),
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
}
