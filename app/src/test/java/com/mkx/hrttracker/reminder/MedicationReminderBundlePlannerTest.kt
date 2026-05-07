package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class MedicationReminderBundlePlannerTest {
    @Test
    fun buildMedicationReminderBundle_merges_unfulfilled_groups_at_the_same_time() {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val firstGroup = medicationGroup(
            uuid = UUID.fromString("ea12a7fa-9077-442f-b322-988a48195515"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
        )
        val secondGroup = medicationGroup(
            uuid = UUID.fromString("d2ad4f5c-3b06-4216-8e68-39ce32d8473d"),
            name = "Spiro",
            time = LocalTime.of(9, 0),
        )
        val laterGroup = medicationGroup(
            uuid = UUID.fromString("2c50fd72-26ad-4100-9ac9-db801fa35fd5"),
            name = "Evening",
            time = LocalTime.of(21, 0),
        )

        val bundle = buildMedicationReminderBundle(
            scheduledAt = scheduledAt,
            groups = listOf(firstGroup, laterGroup, secondGroup),
            entries = emptyList(),
        )

        assertNotNull(bundle)
        assertEquals(scheduledAt, bundle?.scheduledAt)
        assertEquals(
            listOf(firstGroup.uuid, secondGroup.uuid),
            bundle?.slots?.map(MedicationReminderSlot::groupUuid)
        )
        assertEquals(
            listOf(firstGroup.schedule.timeSlots.first().uuid, secondGroup.schedule.timeSlots.first().uuid),
            bundle?.slots?.map(MedicationReminderSlot::scheduleTimeUuid)
        )
    }

    @Test
    fun buildMedicationReminderBundle_excludes_fulfilled_same_time_slots() {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val fulfilledGroup = medicationGroup(
            uuid = UUID.fromString("72cf6aa3-e17b-4181-8459-83f559efdb14"),
            name = "Already logged",
            time = LocalTime.of(9, 0),
        )
        val unfulfilledGroup = medicationGroup(
            uuid = UUID.fromString("9923419b-b152-49ef-a362-7f508c65b405"),
            name = "Still due",
            time = LocalTime.of(9, 0),
        )

        val bundle = buildMedicationReminderBundle(
            scheduledAt = scheduledAt,
            groups = listOf(fulfilledGroup, unfulfilledGroup),
            entries = listOf(
                scheduledEntry(
                    groupUuid = fulfilledGroup.uuid,
                    appliedAt = scheduledAt.plusMinutes(5),
                    scheduledFor = scheduledAt,
                )
            ),
        )

        assertEquals(
            listOf(unfulfilledGroup.uuid),
            bundle?.slots?.map(MedicationReminderSlot::groupUuid)
        )
    }

    private fun medicationGroup(
        uuid: UUID,
        name: String,
        time: LocalTime,
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = name,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(time),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0),
                    )
                )
            ),
            notificationsEnabled = true,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
    }

    private fun scheduledEntry(
        groupUuid: UUID,
        appliedAt: LocalDateTime,
        scheduledFor: LocalDateTime,
    ): MedicationLogEntry {
        return testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0),
            ),
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor,
        )
    }
}
