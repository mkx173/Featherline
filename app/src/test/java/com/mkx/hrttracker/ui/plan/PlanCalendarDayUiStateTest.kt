package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class PlanCalendarDayUiStateTest {
    @Test
    fun buildPlanCalendarDayUiState_marks_none_unplanned_scheduled_partial_and_fulfilled_days() {
        val dailyGroup = medicationGroup(
            uuid = UUID.fromString("73fa49ec-b396-4179-b834-e0d783f2defd"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 2,
                since = LocalDate.of(2026, 4, 14),
                weeklyDayOfWeek = null,
                times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("04c5a0af-3961-41d3-b1b2-b03517958167"),
                    route = RouteOfAdministration.ORAL,
                    name = "Estradiol",
                    dosage = 2.0
                ),
                medication(
                    uuid = UUID.fromString("9dd76ef6-3b5d-4f44-9ec8-351599bdc604"),
                    route = RouteOfAdministration.TOPICAL,
                    name = "Progesterone",
                    dosage = 100.0
                )
            )
        )
        val weeklyGroup = medicationGroup(
            uuid = UUID.fromString("e2cb5a31-5f23-4dae-8b55-b7f38cdb1e08"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 4, 14),
                weeklyDayOfWeek = LocalDate.of(2026, 4, 17).dayOfWeek,
                times = listOf(LocalTime.of(8, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("cc516764-c2f6-478a-8faa-c23f7de339b1"),
                    route = RouteOfAdministration.INTRAMUSCULAR,
                    name = "Estradiol valerate",
                    dosage = 5.0
                )
            )
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(dailyGroup, weeklyGroup),
            entries = listOf(
                manualEntry(
                    route = RouteOfAdministration.ORAL,
                    medicineName = "Estradiol",
                    dosage = 2.0,
                    appliedAt = LocalDateTime.of(2026, 4, 15, 9, 0)
                ),
                groupEntry(
                    groupUuid = dailyGroup.uuid,
                    route = RouteOfAdministration.ORAL,
                    medicineName = "Estradiol",
                    dosage = 2.0,
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 0)
                ),
                groupEntry(
                    groupUuid = dailyGroup.uuid,
                    route = RouteOfAdministration.TOPICAL,
                    medicineName = "Progesterone",
                    dosage = 100.0,
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 0)
                ),
                manualEntry(
                    route = RouteOfAdministration.ORAL,
                    medicineName = "Estradiol",
                    dosage = 2.0,
                    appliedAt = LocalDateTime.of(2026, 4, 16, 21, 0)
                ),
                manualEntry(
                    route = RouteOfAdministration.TOPICAL,
                    medicineName = "Progesterone",
                    dosage = 100.0,
                    appliedAt = LocalDateTime.of(2026, 4, 16, 21, 0)
                ),
                groupEntry(
                    groupUuid = dailyGroup.uuid,
                    route = RouteOfAdministration.ORAL,
                    medicineName = "Estradiol",
                    dosage = 2.0,
                    appliedAt = LocalDateTime.of(2026, 4, 18, 9, 0)
                ),
                groupEntry(
                    groupUuid = dailyGroup.uuid,
                    route = RouteOfAdministration.TOPICAL,
                    medicineName = "Progesterone",
                    dosage = 100.0,
                    appliedAt = LocalDateTime.of(2026, 4, 18, 9, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 15),
            endDate = LocalDate.of(2026, 4, 19)
        )

        assertEquals(PlanCalendarDayStatus.UNPLANNED, dayStates.getValue(LocalDate.of(2026, 4, 15)).status)
        assertEquals(PlanCalendarDayStatus.FULFILLED, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
        assertEquals(PlanCalendarDayStatus.SCHEDULED, dayStates.getValue(LocalDate.of(2026, 4, 17)).status)
        assertEquals(PlanCalendarDayStatus.PARTIAL, dayStates.getValue(LocalDate.of(2026, 4, 18)).status)
        assertEquals(PlanCalendarDayStatus.NONE, dayStates.getValue(LocalDate.of(2026, 4, 19)).status)
    }

    @Test
    fun buildPlanCalendarDayUiState_counts_one_fulfilled_occurrence_per_matching_time() {
        val group = medicationGroup(
            uuid = UUID.fromString("9f532a81-f4b3-4927-9db1-0a86751df861"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 16),
                weeklyDayOfWeek = null,
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("1ec9afde-6af0-4c77-ae15-f629e8e43e86"),
                    route = RouteOfAdministration.ORAL,
                    name = "Estradiol",
                    dosage = 2.0
                )
            )
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                groupEntry(
                    groupUuid = group.uuid,
                    route = RouteOfAdministration.ORAL,
                    medicineName = "Estradiol",
                    dosage = 2.0,
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 0)
                ),
                groupEntry(
                    groupUuid = group.uuid,
                    route = RouteOfAdministration.ORAL,
                    medicineName = "Estradiol",
                    dosage = 2.0,
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 16)
        )

        assertEquals(PlanCalendarDayStatus.FULFILLED, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
    }

    @Test
    fun buildPlanCalendarDayUiState_requires_full_group_match_for_manual_entries() {
        val group = medicationGroup(
            uuid = UUID.fromString("bb94b15b-35fe-4f9d-a8ba-3b423480e2ca"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 16),
                weeklyDayOfWeek = null,
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("65c31b89-bf9a-4c2e-975a-2e63a87472c8"),
                    route = RouteOfAdministration.ORAL,
                    name = "Estradiol",
                    dosage = 2.0
                ),
                medication(
                    uuid = UUID.fromString("d560da79-a743-4ba0-a992-8fc10bd58b13"),
                    route = RouteOfAdministration.TOPICAL,
                    name = "Progesterone",
                    dosage = 100.0
                )
            )
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                manualEntry(
                    route = RouteOfAdministration.ORAL,
                    medicineName = "Estradiol",
                    dosage = 2.0,
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 16)
        )

        assertEquals(PlanCalendarDayStatus.SCHEDULED, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
    }

    @Test
    fun buildPlanCalendarDayUiState_marks_unplanned_when_day_has_off_schedule_group_record() {
        val group = medicationGroup(
            uuid = UUID.fromString("6a6fc487-44d5-4979-b3bb-87cbc2df3f15"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 4, 14),
                weeklyDayOfWeek = LocalDate.of(2026, 4, 18).dayOfWeek,
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("2ef68ea5-c921-416f-96e7-dc1a3c75eb7a"),
                    route = RouteOfAdministration.ORAL,
                    name = "Estradiol",
                    dosage = 2.0
                )
            )
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                groupEntry(
                    groupUuid = group.uuid,
                    route = RouteOfAdministration.ORAL,
                    medicineName = "Estradiol",
                    dosage = 2.0,
                    appliedAt = LocalDateTime.of(2026, 4, 17, 9, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 17),
            endDate = LocalDate.of(2026, 4, 17)
        )

        assertEquals(PlanCalendarDayStatus.UNPLANNED, dayStates.getValue(LocalDate.of(2026, 4, 17)).status)
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
        route: RouteOfAdministration,
        medicineName: String,
        dosage: Double,
        appliedAt: LocalDateTime
    ): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.randomUUID(),
            routeOfAdministration = route,
            medicineName = medicineName,
            dosageMgAsMedicine = dosage,
            dosageMgAsEstradiol = dosage,
            sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
            sourceGroupUuid = groupUuid,
            appliedAt = appliedAt.atZone(ZoneId.systemDefault()).toInstant()
        )
    }

    private fun manualEntry(
        route: RouteOfAdministration,
        medicineName: String,
        dosage: Double,
        appliedAt: LocalDateTime
    ): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.randomUUID(),
            routeOfAdministration = route,
            medicineName = medicineName,
            dosageMgAsMedicine = dosage,
            dosageMgAsEstradiol = dosage,
            sourceType = MedicationLogEntrySourceType.MANUAL,
            sourceGroupUuid = null,
            appliedAt = appliedAt.atZone(ZoneId.systemDefault()).toInstant()
        )
    }

    private fun medication(
        uuid: UUID,
        route: RouteOfAdministration,
        name: String,
        dosage: Double
    ): MedicationGroupMedication {
        return MedicationGroupMedication(
            uuid = uuid,
            routeOfAdministration = route,
            medicineName = name,
            dosageMgAsMedicine = dosage
        )
    }
}
