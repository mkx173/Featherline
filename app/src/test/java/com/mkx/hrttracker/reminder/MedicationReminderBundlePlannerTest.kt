package com.mkx.hrttracker.reminder

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
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class MedicationReminderBundlePlannerTest {
    // Shared so the (medicineUuid, applicationType, doseInstruction) signature
    // matches between scheduled slots and the fulfilling log entry below — a
    // random per-call UUID would always mismatch and the test would never
    // exercise its actual fulfillment branch.
    private val estradiolMedicineUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
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
    fun buildMedicationReminderBundle_orders_items_by_group_createdAt_ascending() {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val newest = medicationGroup(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            name = "Newest",
            time = LocalTime.of(9, 0),
            createdAt = Instant.parse("2026-04-15T00:00:00Z"),
        )
        val oldest = medicationGroup(
            uuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            name = "Oldest",
            time = LocalTime.of(9, 0),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
        val middle = medicationGroup(
            uuid = UUID.fromString("33333333-3333-3333-3333-333333333333"),
            name = "Middle",
            time = LocalTime.of(9, 0),
            createdAt = Instant.parse("2026-04-10T00:00:00Z"),
        )

        val bundle = buildMedicationReminderBundle(
            scheduledAt = scheduledAt,
            groups = listOf(newest, oldest, middle),
            entries = emptyList(),
        )

        assertEquals(
            listOf(oldest.uuid, middle.uuid, newest.uuid),
            bundle?.items?.map { it.slot.groupUuid }
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
        createdAt: Instant = Instant.parse("2026-04-01T00:00:00Z"),
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
                    medicine = testMedicine(
                        uuid = estradiolMedicineUuid,
                        key = MedicationKey.ESTRADIOL,
                    ),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.WholeUnit,
                )
            ),
            notificationsEnabled = true,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

    private fun scheduledEntry(
        groupUuid: UUID,
        appliedAt: LocalDateTime,
        scheduledFor: LocalDateTime,
    ): MedicationLogEntry {
        return testMedicationLogEntry(
            medicine = testMedicine(
                uuid = estradiolMedicineUuid,
                key = MedicationKey.ESTRADIOL,
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.WholeUnit,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor,
        )
    }
}
