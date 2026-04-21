package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testCustomMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
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
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("04c5a0af-3961-41d3-b1b2-b03517958167"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                ),
                medication(
                    uuid = UUID.fromString("9dd76ef6-3b5d-4f44-9ec8-351599bdc604"),
                    details = progesteroneDetails(100.0)
                )
            )
        )
        val weeklyGroup = medicationGroup(
            uuid = UUID.fromString("e2cb5a31-5f23-4dae-8b55-b7f38cdb1e08"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 4, 14),
                weeklyDaysOfWeek = setOf(LocalDate.of(2026, 4, 17).dayOfWeek),
                times = listOf(LocalTime.of(8, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("cc516764-c2f6-478a-8faa-c23f7de339b1"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.INJECTION,
                        dose = 5.0,
                        key = MedicationKey.ESTRADIOL_VALERATE
                    )
                )
            )
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(dailyGroup, weeklyGroup),
            entries = listOf(
                manualEntry(
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    ),
                    appliedAt = LocalDateTime.of(2026, 4, 15, 9, 0)
                ),
                groupEntry(
                    groupUuid = dailyGroup.uuid,
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    ),
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 0),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 9, 0)
                ),
                groupEntry(
                    groupUuid = dailyGroup.uuid,
                    details = progesteroneDetails(100.0),
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 0),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 9, 0)
                ),
                groupEntry(
                    groupUuid = dailyGroup.uuid,
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    ),
                    appliedAt = LocalDateTime.of(2026, 4, 16, 21, 0),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 21, 0)
                ),
                groupEntry(
                    groupUuid = dailyGroup.uuid,
                    details = progesteroneDetails(100.0),
                    appliedAt = LocalDateTime.of(2026, 4, 16, 21, 0),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 21, 0)
                ),
                groupEntry(
                    groupUuid = dailyGroup.uuid,
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    ),
                    appliedAt = LocalDateTime.of(2026, 4, 18, 9, 0),
                    scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)
                ),
                groupEntry(
                    groupUuid = dailyGroup.uuid,
                    details = progesteroneDetails(100.0),
                    appliedAt = LocalDateTime.of(2026, 4, 18, 9, 0),
                    scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)
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
    fun buildPlanCalendarDayUiState_counts_each_scheduled_slot_at_most_once() {
        val group = medicationGroup(
            uuid = UUID.fromString("9f532a81-f4b3-4927-9db1-0a86751df861"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 16),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("1ec9afde-6af0-4c77-ae15-f629e8e43e86"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                )
            )
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                groupEntry(
                    groupUuid = group.uuid,
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    ),
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 0),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 9, 0)
                ),
                groupEntry(
                    groupUuid = group.uuid,
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    ),
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 0),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 9, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 16)
        )

        assertEquals(PlanCalendarDayStatus.FULFILLED, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
    }

    @Test
    fun buildPlanCalendarDayUiState_does_not_treat_manual_entries_as_fulfillment() {
        val group = medicationGroup(
            uuid = UUID.fromString("bb94b15b-35fe-4f9d-a8ba-3b423480e2ca"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 16),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("65c31b89-bf9a-4c2e-975a-2e63a87472c8"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                ),
                medication(
                    uuid = UUID.fromString("d560da79-a743-4ba0-a992-8fc10bd58b13"),
                    details = progesteroneDetails(100.0)
                )
            )
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                manualEntry(
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    ),
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 0)
                ),
                manualEntry(
                    details = progesteroneDetails(100.0),
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
                weeklyDaysOfWeek = setOf(LocalDate.of(2026, 4, 18).dayOfWeek),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("2ef68ea5-c921-416f-96e7-dc1a3c75eb7a"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                )
            )
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                groupEntry(
                    groupUuid = group.uuid,
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    ),
                    appliedAt = LocalDateTime.of(2026, 4, 17, 9, 0),
                    scheduledFor = null
                )
            ),
            startDate = LocalDate.of(2026, 4, 17),
            endDate = LocalDate.of(2026, 4, 17)
        )

        assertEquals(PlanCalendarDayStatus.UNPLANNED, dayStates.getValue(LocalDate.of(2026, 4, 17)).status)
    }

    @Test
    fun buildPlanCalendarDayUiState_does_not_match_patch_on_group_with_patch_off_log() {
        val group = medicationGroup(
            uuid = UUID.fromString("83e7f55f-9b9e-4bd2-b83d-87602cfd434e"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 16),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("3de7dca1-d35f-48e9-a6d0-f1ba2e7fe41a"),
                    details = MedicationDetails(
                        category = MedicationCategory.ESTRADIOL,
                        applicationType = MedicationApplicationType.PATCH_ON,
                        selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL_PATCH),
                        dose = MedicationDose.PatchReleaseRateMcgPerDay(100.0)
                    )
                )
            )
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                MedicationLogEntry(
                    uuid = UUID.fromString("711ef972-70f4-4fd6-b186-391e1ffb3c29"),
                    details = MedicationDetails(
                        category = MedicationCategory.ESTRADIOL,
                        applicationType = MedicationApplicationType.PATCH_OFF,
                        selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL_PATCH),
                        dose = MedicationDose.None
                    ),
                    dosageMgAsEstradiol = null,
                    sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
                    sourceGroupUuid = group.uuid,
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 0)
                        .atZone(ZoneId.systemDefault())
                        .toInstant(),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 9, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 16)
        )

        assertEquals(PlanCalendarDayStatus.SCHEDULED, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
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
        scheduledFor: LocalDateTime? = null
    ): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.randomUUID(),
            details = details,
            dosageMgAsEstradiol = estradiolEquivalent(details),
            sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor
        )
    }

    private fun manualEntry(
        details: MedicationDetails,
        appliedAt: LocalDateTime
    ): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.randomUUID(),
            details = details,
            dosageMgAsEstradiol = estradiolEquivalent(details),
            sourceType = MedicationLogEntrySourceType.MANUAL,
            sourceGroupUuid = null,
            appliedAt = testInstant(appliedAt)
        )
    }

    private fun medication(
        uuid: UUID,
        details: MedicationDetails
    ): MedicationGroupMedication {
        return MedicationGroupMedication(
            uuid = uuid,
            details = details
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

    private fun progesteroneDetails(dose: Double): MedicationDetails {
        return testCustomMedicationDetails(
            medicationName = "Progesterone",
            dose = MedicationDose.MgAsMedicine(dose)
        )
    }

    private fun estradiolEquivalent(details: MedicationDetails): Double? {
        return when (details.selection) {
            is MedicationSelection.Catalog -> when ((details.selection as MedicationSelection.Catalog).medicationKey) {
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
