package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class MedicationGroupSlotFulfillmentTest {
    @Test
    fun isSlotFulfilled_requires_the_full_stored_count_for_matching_medications() {
        val group = medicationGroup(
            uuid = UUID.fromString("6c7cf2ef-f59d-4ec6-bad9-fb0f80ec4ebb"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 16),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("e5f15765-159c-4c25-bffd-73a198a0f932"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    ),
                    count = 2
                )
            )
        )
        val scheduledFor = LocalDateTime.of(2026, 4, 16, 9, 0)
        val firstEntry = groupEntry(
            groupUuid = group.uuid,
            details = estradiolDetails(
                applicationType = MedicationApplicationType.ORAL,
                dose = 2.0
            ),
            appliedAt = scheduledFor.plusMinutes(2),
            scheduledFor = scheduledFor
        )
        val secondEntry = groupEntry(
            groupUuid = group.uuid,
            details = estradiolDetails(
                applicationType = MedicationApplicationType.ORAL,
                dose = 2.0
            ),
            appliedAt = scheduledFor.plusMinutes(5),
            scheduledFor = scheduledFor
        )

        assertEquals(
            false,
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(firstEntry)
            )
        )
        assertEquals(
            true,
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(firstEntry, secondEntry)
            )
        )
    }

    @Test
    fun isSlotFulfilled_accepts_single_counted_log_row_when_its_count_meets_requirement() {
        val group = medicationGroup(
            uuid = UUID.fromString("71a82ba5-4f60-455f-b2b2-acd98265f933"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 16),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("2f81db0f-22d8-4326-8667-fbf27d6560f8"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    ),
                    count = 2
                )
            )
        )
        val scheduledFor = LocalDateTime.of(2026, 4, 16, 9, 0)

        assertEquals(
            false,
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(
                    groupEntry(
                        groupUuid = group.uuid,
                        details = group.medications.single().details,
                        appliedAt = scheduledFor.plusMinutes(2),
                        scheduledFor = scheduledFor,
                        count = 1
                    )
                )
            )
        )
        assertEquals(
            true,
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(
                    groupEntry(
                        groupUuid = group.uuid,
                        details = group.medications.single().details,
                        appliedAt = scheduledFor.plusMinutes(2),
                        scheduledFor = scheduledFor,
                        count = 2
                    )
                )
            )
        )
    }

    @Test
    fun isSlotFulfilled_uses_strict_schedule_fulfillment_window() {
        val group = medicationGroup(
            uuid = UUID.fromString("570b5e05-5409-4979-971e-140f26eba0fd"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 16),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(7, 0), LocalTime.of(9, 0), LocalTime.of(11, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("3334cfa5-a2e8-4f82-889f-4f9a7d45e6b7"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                )
            )
        )
        val scheduledFor = LocalDateTime.of(2026, 4, 16, 9, 0)

        assertEquals(
            false,
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(
                    groupEntry(
                        groupUuid = group.uuid,
                        details = group.medications.single().details,
                        appliedAt = scheduledFor.plusHours(1),
                        scheduledFor = scheduledFor
                    )
                )
            )
        )
        assertEquals(
            true,
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(
                    groupEntry(
                        groupUuid = group.uuid,
                        details = group.medications.single().details,
                        appliedAt = scheduledFor.plusHours(1).minusSeconds(1),
                        scheduledFor = scheduledFor
                    )
                )
            )
        )
        assertEquals(
            false,
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(
                    groupEntry(
                        groupUuid = group.uuid,
                        details = group.medications.single().details,
                        appliedAt = scheduledFor.minusHours(1),
                        scheduledFor = scheduledFor
                    )
                )
            )
        )
        assertEquals(
            true,
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(
                    groupEntry(
                        groupUuid = group.uuid,
                        details = group.medications.single().details,
                        appliedAt = scheduledFor.minusHours(1).plusSeconds(1),
                        scheduledFor = scheduledFor
                    )
                )
            )
        )
        assertEquals(
            false,
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(
                    groupEntry(
                        groupUuid = group.uuid,
                        details = group.medications.single().details,
                        appliedAt = scheduledFor.minusHours(1).minusMinutes(1),
                        scheduledFor = scheduledFor
                    )
                )
            )
        )
        assertEquals(
            false,
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(
                    groupEntry(
                        groupUuid = group.uuid,
                        details = group.medications.single().details,
                        appliedAt = scheduledFor.plusHours(1).plusMinutes(1),
                        scheduledFor = scheduledFor
                    )
                )
            )
        )
    }

    @Test
    fun isSlotFulfilled_recognizes_cross_timezone_entry() {
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val scheduledFor = LocalDateTime.of(2026, 4, 16, 8, 0)

        val appliedAtTokyo = scheduledFor.atZone(tokyoZone).toInstant()

        val group = medicationGroup(
            uuid = UUID.fromString("6e4a4fb6-f4db-4bc4-8ce5-74232e216ea4"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("cfdee6a7-89a6-4592-b581-19677f58e5e4"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                )
            )
        )

        val entry = MedicationLogEntry(
            uuid = UUID.randomUUID(),
            details = group.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = appliedAtTokyo,
            appliedAtTimeZoneId = tokyoZone.id,
            scheduledFor = scheduledFor
        )

        assertTrue(
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(entry),
            )
        )
    }

    @Test
    fun isSlotFulfilled_rejects_cross_timezone_entry_outside_fulfillment_window() {
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val scheduledFor = LocalDateTime.of(2026, 4, 16, 8, 0)

        val appliedAtLate = LocalDateTime.of(2026, 4, 16, 10, 1)
            .atZone(tokyoZone).toInstant()

        val group = medicationGroup(
            uuid = UUID.fromString("2d2205a1-27ae-409c-a1ce-bbc99454ccaa"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(10, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("22ff3796-065b-4d46-973a-3b4ea9da6b90"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                )
            )
        )

        val entry = MedicationLogEntry(
            uuid = UUID.randomUUID(),
            details = group.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = appliedAtLate,
            appliedAtTimeZoneId = tokyoZone.id,
            scheduledFor = scheduledFor
        )

        assertFalse(
            isSlotFulfilled(
                group = group,
                date = scheduledFor.toLocalDate(),
                time = scheduledFor.toLocalTime(),
                entries = listOf(entry),
            )
        )
    }

    private fun medicationGroup(
        uuid: UUID,
        schedule: MedicationGroupSchedule,
        medications: List<MedicationGroupMedication>
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = "Test group",
            schedule = schedule,
            medications = medications,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
    }

    private fun groupEntry(
        groupUuid: UUID,
        details: MedicationDetails,
        appliedAt: LocalDateTime,
        scheduledFor: LocalDateTime? = null,
        count: Int = 1
    ): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.randomUUID(),
            details = details,
            dosageMgAsEstradiol = estradiolEquivalent(details),
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor,
            count = count
        )
    }

    private fun medication(
        uuid: UUID,
        details: MedicationDetails,
        count: Int = 1,
    ): MedicationGroupMedication {
        return MedicationGroupMedication(
            uuid = uuid,
            details = details,
            count = count
        )
    }

    private fun estradiolDetails(
        applicationType: MedicationApplicationType,
        dose: Double,
        key: MedicationKey = MedicationKey.ESTRADIOL,
    ): MedicationDetails {
        return testCatalogMedicationDetails(
            key = key,
            applicationType = applicationType,
            dose = MedicationDose.MgAsMedicine(dose)
        )
    }

    private fun estradiolEquivalent(details: MedicationDetails): Double? {
        return when (details.selection) {
            is MedicationSelection.Catalog -> when (details.selection.medicationKey) {
                MedicationKey.ESTRADIOL,
                MedicationKey.ESTRADIOL_GEL -> when (val dose = details.dose) {
                    is MedicationDose.MgAsMedicine -> dose.valueMg
                    is MedicationDose.GelEquivalentEstradiolMg -> dose.valueMg
                    else -> null
                }

                MedicationKey.ESTRADIOL_VALERATE -> when (val dose = details.dose) {
                    is MedicationDose.MgAsMedicine -> dose.valueMg
                    else -> null
                }

                else -> null
            }

            is MedicationSelection.Custom -> null
        }
    }
}
