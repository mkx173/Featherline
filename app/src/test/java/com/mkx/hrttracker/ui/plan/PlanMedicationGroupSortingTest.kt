package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class PlanMedicationGroupSortingTest {
    @Test
    fun sortPlanMedicationGroups_ordersGroupsByOldestCreationTimeFirst() {
        val oldestGroup = medicationGroup(
            uuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            createdAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val newestGroup = medicationGroup(
            uuid = UUID.fromString("00000000-0000-0000-0000-000000000003"),
            createdAt = Instant.parse("2026-04-03T00:00:00Z")
        )
        val middleGroup = medicationGroup(
            uuid = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            createdAt = Instant.parse("2026-04-02T00:00:00Z")
        )

        assertEquals(
            listOf(oldestGroup, middleGroup, newestGroup),
            sortPlanMedicationGroups(listOf(newestGroup, oldestGroup, middleGroup))
        )
    }

    private fun medicationGroup(
        uuid: UUID,
        createdAt: Instant
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = "Group $uuid",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = emptyList(),
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }
}
