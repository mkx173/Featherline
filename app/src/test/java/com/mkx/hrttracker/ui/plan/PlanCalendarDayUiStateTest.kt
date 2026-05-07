package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
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

    private fun manualEntry(
        details: MedicationDetails,
        appliedAt: LocalDateTime,
        count: Int = 1
    ): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.randomUUID(),
            details = details,
            dosageMgAsEstradiol = estradiolEquivalent(details),
            sourceGroupUuid = null,
            appliedAt = testInstant(appliedAt),
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

    private fun progesteroneDetails(dose: Double): MedicationDetails {
        return testCustomMedicationDetails(
            medicationName = "Progesterone",
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
