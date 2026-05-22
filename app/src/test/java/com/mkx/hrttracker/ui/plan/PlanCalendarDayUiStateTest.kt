package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.isEntryWithinScheduleFulfillmentWindow
import com.mkx.hrttracker.model.medication.planCalendarDate
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicine
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

        assertEquals(PlanCalendarDayStatus.OFFPLAN, dayStates.getValue(LocalDate.of(2026, 4, 15)).status)
        assertEquals(PlanCalendarDayStatus.FULFILLED, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
        assertEquals(PlanCalendarDayStatus.MISSED, dayStates.getValue(LocalDate.of(2026, 4, 17)).status)
        assertEquals(PlanCalendarDayStatus.PARTIAL, dayStates.getValue(LocalDate.of(2026, 4, 18)).status)
        assertEquals(PlanCalendarDayStatus.NONE, dayStates.getValue(LocalDate.of(2026, 4, 19)).status)
    }

    @Test
    fun buildPlanCalendarDayUiState_matches_planned_record_by_scheduled_date_when_applied_after_midnight() {
        val scheduledDate = LocalDate.of(2026, 4, 16)
        val group = medicationGroup(
            uuid = UUID.fromString("fd53d9d9-fcf3-48f4-8392-a3d13395c220"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = setOf(scheduledDate.dayOfWeek),
                times = listOf(LocalTime.of(23, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("5e9743c0-fd3f-48de-9de3-5741a57d54a3"),
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
                    details = group.medications.single().details,
                    appliedAt = LocalDateTime.of(2026, 4, 17, 0, 15),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 23, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 17)
        )

        assertEquals(PlanCalendarDayStatus.FULFILLED, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
        assertEquals(false, dayStates.getValue(LocalDate.of(2026, 4, 16)).hasOffPlanRecord)
        assertEquals(PlanCalendarDayStatus.NONE, dayStates.getValue(LocalDate.of(2026, 4, 17)).status)
        assertEquals(false, dayStates.getValue(LocalDate.of(2026, 4, 17)).hasOffPlanRecord)
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
    fun buildPlanCalendarDayUiState_skips_slots_before_group_creation_time_on_start_day() {
        val zoneId = ZoneId.of("UTC")
        val savedAt = LocalDateTime.of(2026, 4, 16, 10, 0)
        val createdAt = savedAt
            .atZone(zoneId)
            .toInstant()
        val group = medicationGroup(
            uuid = UUID.fromString("298f60c9-ecda-44dd-bf89-a3641d482711"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 16),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0), LocalTime.of(11, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTime(
                        uuid = UUID.randomUUID(),
                        time = LocalTime.of(9, 0),
                        effectiveFrom = savedAt,
                    ),
                    MedicationGroupScheduleTime(
                        uuid = UUID.randomUUID(),
                        time = LocalTime.of(11, 0),
                        effectiveFrom = savedAt,
                    ),
                ),
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("7c342897-e84c-4e7d-aa89-e4405aff05d5"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                )
            )
        ).copy(
            createdAt = createdAt,
            updatedAt = createdAt,
            includePastScheduledSlots = false,
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(group),
            entries = emptyList(),
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 16),
            zoneId = zoneId,
        )

        val state = dayStates.getValue(LocalDate.of(2026, 4, 16))
        assertEquals(1, state.expectedOccurrenceCount)
        assertEquals(PlanCalendarDayStatus.MISSED, state.status)
    }

    @Test
    fun buildPlanCalendarDayUiState_counts_logged_slot_before_group_creation_as_planned() {
        val zoneId = ZoneId.of("UTC")
        val createdAt = LocalDateTime.of(2026, 4, 18, 10, 0)
            .atZone(zoneId)
            .toInstant()
        val group = medicationGroup(
            uuid = UUID.fromString("23eeb5fa-cc2f-40c4-90fc-39171b650e33"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("e7c4380d-f7b6-475c-8c88-d438c614b76e"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                )
            )
        ).copy(
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                groupEntry(
                    groupUuid = group.uuid,
                    details = group.medications.single().details,
                    appliedAt = LocalDateTime.of(2026, 4, 17, 9, 5),
                    scheduledFor = LocalDateTime.of(2026, 4, 17, 9, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 17),
            endDate = LocalDate.of(2026, 4, 17),
            zoneId = zoneId,
        )

        val state = dayStates.getValue(LocalDate.of(2026, 4, 17))
        assertEquals(1, state.expectedOccurrenceCount)
        assertEquals(1, state.matchedOccurrenceCount)
        assertEquals(false, state.hasOffPlanRecord)
        assertEquals(PlanCalendarDayStatus.FULFILLED, state.status)
    }

    @Test
    fun buildPlanCalendarDayUiState_countsArchivedLinkedRecordWithoutFuturePlan() {
        val archivedAtInstant = Instant.parse("2026-04-16T10:00:00Z")
        val archivedGroup = medicationGroup(
            uuid = UUID.fromString("64bc428f-5cf1-4505-b68a-aa5579c1d75d"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("9433009b-cc88-4712-8d60-29d59d50864f"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                )
            )
        ).copy(
            archivedAt = archivedAtInstant,
            archivedAtLocal = archivedAtInstant.atZone(ZoneId.systemDefault()).toLocalDateTime(),
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(archivedGroup),
            entries = listOf(
                groupEntry(
                    groupUuid = archivedGroup.uuid,
                    details = archivedGroup.medications.single().details,
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 4),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 9, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 17)
        )

        assertEquals(PlanCalendarDayStatus.FULFILLED, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
        assertEquals(false, dayStates.getValue(LocalDate.of(2026, 4, 16)).hasOffPlanRecord)
        assertEquals(PlanCalendarDayStatus.NONE, dayStates.getValue(LocalDate.of(2026, 4, 17)).status)
    }

    @Test
    fun buildPlanCalendarDayUiState_marksArchivedLinkedRecordPartialBeforeArchive() {
        val archivedGroup = medicationGroup(
            uuid = UUID.fromString("ed3ce972-41e2-4593-b290-e21d5afdb31b"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("a5c93f56-4210-43e4-a675-54daf0464c72"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                ),
                medication(
                    uuid = UUID.fromString("302e017a-e85d-4596-b497-78a0657b10b4"),
                    details = progesteroneDetails(100.0)
                )
            )
        ).copy(
            archivedAt = Instant.parse("2026-04-16T10:00:00Z")
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(archivedGroup),
            entries = listOf(
                groupEntry(
                    groupUuid = archivedGroup.uuid,
                    details = archivedGroup.medications.first().details,
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 4),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 9, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 16)
        )

        assertEquals(PlanCalendarDayStatus.PARTIAL, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
        assertEquals(false, dayStates.getValue(LocalDate.of(2026, 4, 16)).hasOffPlanRecord)
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

        assertEquals(PlanCalendarDayStatus.MISSED, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
        assertEquals(true, dayStates.getValue(LocalDate.of(2026, 4, 16)).hasOffPlanRecord)
    }

    @Test
    fun buildPlanCalendarDayUiState_marks_partial_when_slot_has_some_but_not_all_group_medications_logged() {
        val group = medicationGroup(
            uuid = UUID.fromString("7994c7f8-cdc8-4d6c-b1a1-7efe31ed7418"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 16),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("99d4f335-d0c2-4e73-aebf-d32fd75b99d0"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                ),
                medication(
                    uuid = UUID.fromString("1816b815-34e0-4ce4-89ef-3b447f61c373"),
                    details = progesteroneDetails(100.0)
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
                    appliedAt = LocalDateTime.of(2026, 4, 16, 9, 2),
                    scheduledFor = LocalDateTime.of(2026, 4, 16, 9, 0)
                )
            ),
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 16)
        )

        assertEquals(0, dayStates.getValue(LocalDate.of(2026, 4, 16)).matchedOccurrenceCount)
        assertEquals(true, dayStates.getValue(LocalDate.of(2026, 4, 16)).hasMatchingScheduledRecord)
        assertEquals(PlanCalendarDayStatus.PARTIAL, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
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

        assertEquals(PlanCalendarDayStatus.OFFPLAN, dayStates.getValue(LocalDate.of(2026, 4, 17)).status)
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
                    medicine = testMedicine(key = MedicationKey.ESTRADIOL_PATCH),
                    applicationType = MedicationApplicationType.PATCH_ON,
                    doseInstruction = DoseInstruction.WholeUnit,
                )
            )
        )

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                // PATCH_OFF carries medicine = null and the noop instruction;
                // this is the one "remove all patches" event the schema allows.
                MedicationLogEntry(
                    uuid = UUID.fromString("711ef972-70f4-4fd6-b186-391e1ffb3c29"),
                    medicine = null,
                    category = MedicationCategory.ESTRADIOL,
                    applicationType = MedicationApplicationType.PATCH_OFF,
                    doseInstruction = DoseInstruction.Noop,
                    equivalentE2Mg = null,
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

        assertEquals(PlanCalendarDayStatus.MISSED, dayStates.getValue(LocalDate.of(2026, 4, 16)).status)
    }

    @Test
    fun buildPlanCalendarDayUiState_treats_far_group_entry_as_missed_not_off_plan() {
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

        val dayStates = buildPlanCalendarDayUiState(
            groups = listOf(group),
            entries = listOf(
                groupEntry(
                    groupUuid = group.uuid,
                    details = group.medications.single().details,
                    appliedAt = scheduledFor.plusHours(1).plusMinutes(1),
                    scheduledFor = scheduledFor
                )
            ),
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 16)
        )

        val dayState = dayStates.getValue(LocalDate.of(2026, 4, 16))
        assertEquals(3, dayState.expectedOccurrenceCount)
        assertEquals(0, dayState.matchedOccurrenceCount)
        assertEquals(false, dayState.hasMatchingScheduledRecord)
        assertEquals(false, dayState.hasOffPlanRecord)
        assertEquals(PlanCalendarDayStatus.MISSED, dayState.status)
    }

    @Test
    fun isEntryWithinScheduleFulfillmentWindow_uses_entry_timezone_instead_of_system_default() {
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val laZone = ZoneId.of("America/Los_Angeles")
        val scheduledFor = LocalDateTime.of(2026, 4, 16, 8, 0)

        // Record logged in Tokyo at 8:00 AM = 2026-04-15T23:00:00Z
        val appliedAtTokyo = scheduledFor.atZone(tokyoZone).toInstant()
        // The same instant in LA is 2026-04-15 4:00 PM — a different wall-clock day
        assertEquals(
            LocalDateTime.of(2026, 4, 15, 16, 0),
            appliedAtTokyo.atZone(laZone).toLocalDateTime()
        )

        val group = medicationGroup(
            uuid = UUID.fromString("90e36a1c-2e40-4ead-9f02-4f7ca0aab74b"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("fb4e7d45-92b3-4a33-bdd7-8acf24352fac"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                )
            )
        )

        val singleMedication = group.medications.single()
        val entry = MedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = singleMedication.medicine,
            category = singleMedication.medicine?.category ?: MedicationCategory.ESTRADIOL,
            applicationType = singleMedication.applicationType,
            doseInstruction = singleMedication.doseInstruction,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = appliedAtTokyo,
            appliedAtTimeZoneId = tokyoZone.id,
            scheduledFor = scheduledFor
        )

        assertTrue(
            isEntryWithinScheduleFulfillmentWindow(
                group = group,
                entry = entry,
            )
        )
    }

    @Test
    fun isEntryWithinScheduleFulfillmentWindow_rejects_entry_outside_window_in_own_timezone() {
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val scheduledFor = LocalDateTime.of(2026, 4, 16, 8, 0)

        // Applied 2 hours late — outside the 1-hour fulfillment window for a 2-hour spaced schedule
        val appliedAtLate = LocalDateTime.of(2026, 4, 16, 10, 1)
            .atZone(tokyoZone).toInstant()

        val group = medicationGroup(
            uuid = UUID.fromString("a7e1e0d1-3fc1-47a2-bc13-0ce4bb1f7d22"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(10, 0))
            ),
            medications = listOf(
                medication(
                    uuid = UUID.fromString("c333be72-87c9-463f-9f19-106fe2a2c1cf"),
                    details = estradiolDetails(
                        applicationType = MedicationApplicationType.ORAL,
                        dose = 2.0
                    )
                )
            )
        )

        val singleMedication = group.medications.single()
        val entry = MedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = singleMedication.medicine,
            category = singleMedication.medicine?.category ?: MedicationCategory.ESTRADIOL,
            applicationType = singleMedication.applicationType,
            doseInstruction = singleMedication.doseInstruction,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = appliedAtLate,
            appliedAtTimeZoneId = tokyoZone.id,
            scheduledFor = scheduledFor
        )

        assertFalse(
            isEntryWithinScheduleFulfillmentWindow(
                group = group,
                entry = entry,
            )
        )
    }

    @Test
    fun planCalendarDate_for_manual_entry_uses_entry_zone() {
        val pdt = ZoneId.of("America/Los_Angeles")
        val details = estradiolDetails(
            applicationType = MedicationApplicationType.ORAL,
            dose = 2.0,
        )
        val entry = MedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = details.medicine,
            category = details.medicine?.category ?: MedicationCategory.ESTRADIOL,
            applicationType = details.applicationType,
            doseInstruction = details.doseInstruction,
            equivalentE2Mg = details.equivalentE2Mg,
            sourceGroupUuid = null,
            appliedAt = LocalDateTime.of(2026, 5, 8, 9, 0).atZone(pdt).toInstant(),
            appliedAtTimeZoneId = "America/Los_Angeles",
            scheduledFor = null,
        )
        // Device in JST should still bucket the entry to its PDT date (May 8), not May 9.
        assertEquals(LocalDate.of(2026, 5, 8), entry.planCalendarDate(ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun planCalendarDate_for_linked_entry_uses_scheduledFor_date() {
        val pdt = ZoneId.of("America/Los_Angeles")
        val details = estradiolDetails(
            applicationType = MedicationApplicationType.ORAL,
            dose = 2.0,
        )
        val entry = MedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = details.medicine,
            category = details.medicine?.category ?: MedicationCategory.ESTRADIOL,
            applicationType = details.applicationType,
            doseInstruction = details.doseInstruction,
            equivalentE2Mg = details.equivalentE2Mg,
            sourceGroupUuid = UUID.randomUUID(),
            appliedAt = LocalDateTime.of(2026, 5, 8, 9, 0).atZone(pdt).toInstant(),
            appliedAtTimeZoneId = "America/Los_Angeles",
            scheduledFor = LocalDateTime.of(2026, 5, 8, 9, 0),
        )
        // Even with device in JST, scheduledFor date (May 8) wins over device-zone conversion.
        assertEquals(LocalDate.of(2026, 5, 8), entry.planCalendarDate(ZoneId.of("Asia/Tokyo")))
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

    // Test-local "details" holder. The production model split MedicationDetails
    // into (medicine, applicationType, doseInstruction); this triple keeps the
    // test's existing call sites concise while bridging that split.
    private data class TestMedicationDetails(
        val medicine: Medicine?,
        val applicationType: MedicationApplicationType,
        val doseInstruction: DoseInstruction,
        val equivalentE2Mg: Double?,
    )

    private val MedicationGroupMedication.details: TestMedicationDetails
        get() = TestMedicationDetails(
            medicine = medicine,
            applicationType = applicationType,
            doseInstruction = doseInstruction,
            // Best-effort PK-equivalent reconstruction for tests that thread a
            // group's "details" into a fulfillment entry. Mirrors the original
            // estradiolEquivalent helper but operates on the new identity model.
            equivalentE2Mg = when {
                medicine == null -> null
                medicine.category != MedicationCategory.ESTRADIOL -> null
                else -> when (val s = medicine.selection) {
                    is MedicineSelection.Catalog -> when (s.medicationKey) {
                        MedicationKey.ESTRADIOL,
                        MedicationKey.ESTRADIOL_GEL,
                        MedicationKey.ESTRADIOL_VALERATE -> 2.0
                        else -> null
                    }
                    is MedicineSelection.Custom -> null
                }
            },
        )

    private fun groupEntry(
        groupUuid: UUID,
        details: TestMedicationDetails,
        appliedAt: LocalDateTime,
        scheduledFor: LocalDateTime? = null,
        count: Int = 1
    ): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = details.medicine,
            category = details.medicine?.category ?: MedicationCategory.ESTRADIOL,
            applicationType = details.applicationType,
            doseInstruction = details.doseInstruction,
            equivalentE2Mg = details.equivalentE2Mg,
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor,
            count = count
        )
    }

    private fun manualEntry(
        details: TestMedicationDetails,
        appliedAt: LocalDateTime,
        count: Int = 1
    ): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = details.medicine,
            category = details.medicine?.category ?: MedicationCategory.ESTRADIOL,
            applicationType = details.applicationType,
            doseInstruction = details.doseInstruction,
            equivalentE2Mg = details.equivalentE2Mg,
            sourceGroupUuid = null,
            appliedAt = testInstant(appliedAt),
            count = count
        )
    }

    private fun medication(
        uuid: UUID,
        details: TestMedicationDetails,
        count: Int = 1,
    ): MedicationGroupMedication {
        return MedicationGroupMedication(
            uuid = uuid,
            medicine = details.medicine,
            applicationType = details.applicationType,
            doseInstruction = details.doseInstruction,
            count = count
        )
    }

    // Stable per-key medicine UUIDs so two estradiolDetails() calls produce
    // signature-equal medicines. Random per-call UUIDs would break the
    // (medicineUuid, applicationType, doseInstruction) match used by the
    // fulfillment grouping.
    private val medicineUuidsByKey: MutableMap<MedicationKey, UUID> = mutableMapOf()
    private val progesteroneMedicineUuid = UUID.fromString("22222222-2222-2222-2222-222222222222")

    private fun estradiolDetails(
        applicationType: MedicationApplicationType,
        dose: Double,
        key: MedicationKey = MedicationKey.ESTRADIOL,
    ): TestMedicationDetails {
        // The fixture's equivalentE2Mg mimics the prior `estradiolEquivalent`
        // helper: only catalog estradiol species expose an estradiol-mg figure
        // (everything else, including custom medicines, was returning null).
        val equivalent = when (key) {
            MedicationKey.ESTRADIOL,
            MedicationKey.ESTRADIOL_GEL,
            MedicationKey.ESTRADIOL_VALERATE -> dose
            else -> null
        }
        val uuid = medicineUuidsByKey.getOrPut(key) { UUID.randomUUID() }
        return TestMedicationDetails(
            medicine = testMedicine(uuid = uuid, key = key),
            applicationType = applicationType,
            doseInstruction = DoseInstruction.WholeUnit,
            equivalentE2Mg = equivalent,
        )
    }

    private fun progesteroneDetails(dose: Double): TestMedicationDetails {
        return TestMedicationDetails(
            medicine = testCustomMedicine(
                uuid = progesteroneMedicineUuid,
                medicationName = "Progesterone",
                category = MedicationCategory.CUSTOM,
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.WholeUnit,
            // Custom medicines have no PK ester data, so equivalent stays null
            // here regardless of the requested dose; the parameter is kept for
            // call-site symmetry with estradiolDetails.
            equivalentE2Mg = null.also { @Suppress("UNUSED_EXPRESSION") dose },
        )
    }

    @Suppress("unused")
    private fun customMedicineSelection(name: String): MedicineSelection =
        MedicineSelection.Custom(medicationName = name)
}
