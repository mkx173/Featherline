package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PlanBatchAddViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectGroup_resetsRangeToSelectedGroupDefault() = runTest {
        val firstGroup = medicationGroup(
            uuid = UUID.fromString("09b7f93e-0196-47f5-bb55-3581ee5d0ee7"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(details = estradiolDetails())),
        )
        val secondGroup = medicationGroup(
            uuid = UUID.fromString("99857191-b93d-4920-a06e-9dc44127e93a"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 20),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(21, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(details = estradiolDetails())),
        )
        val viewModel = planBatchAddViewModel(groups = listOf(firstGroup, secondGroup))
        advanceUntilIdle()

        viewModel.selectGroup(firstGroup.uuid)
        advanceUntilIdle()
        viewModel.updateStartDate(LocalDate.of(2026, 4, 1))
        viewModel.updateEndDate(LocalDate.of(2026, 4, 2))
        advanceUntilIdle()

        viewModel.selectGroup(secondGroup.uuid)
        advanceUntilIdle()

        assertEquals(secondGroup.uuid, viewModel.uiState.value.selectedGroupUuid)
        assertEquals(secondGroup.schedule.since, viewModel.uiState.value.startDate)
        assertEquals(viewModel.uiState.value.today, viewModel.uiState.value.endDate)
    }

    @Test
    fun buildPlanBatchAddEntries_adds_beforePlanStartAsManual_and_afterPlanStartAsScheduled() {
        val groupUuid = UUID.fromString("3b877888-5d32-4b3a-86ab-422b9d29d5f1")
        val group = medicationGroup(
            uuid = groupUuid,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(details = estradiolDetails(), count = 2)),
        )

        val entries = buildPlanBatchAddEntries(
            group = group,
            startDate = LocalDate.of(2026, 4, 9),
            endDate = LocalDate.of(2026, 4, 10),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(2, entries.size)
        assertNull(entries[0].sourceGroupUuid)
        assertNull(entries[0].scheduledFor)
        assertEquals(2, entries[0].count)
        assertEquals(Instant.parse("2026-04-09T09:00:00Z"), entries[0].appliedAt)
        assertEquals(groupUuid, entries[1].sourceGroupUuid)
        assertEquals(LocalDateTime.of(2026, 4, 10, 9, 0), entries[1].scheduledFor)
    }

    @Test
    fun buildPlanBatchAddEntries_skipsSlotsBeforeCreationWhenScheduleHistoryDisabled() {
        val groupUuid = UUID.fromString("0f7391f1-3278-4e44-83a9-613de4b49bc3")
        val group = medicationGroup(
            uuid = groupUuid,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0), LocalTime.of(11, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(details = estradiolDetails())),
        ).copy(
            createdAt = Instant.parse("2026-04-10T10:00:00Z"),
            updatedAt = Instant.parse("2026-04-10T10:00:00Z"),
            includePastScheduledSlots = false,
        )

        val entries = buildPlanBatchAddEntries(
            group = group,
            startDate = LocalDate.of(2026, 4, 10),
            endDate = LocalDate.of(2026, 4, 10),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(1, entries.size)
        assertEquals(groupUuid, entries.single().sourceGroupUuid)
        assertEquals(LocalDateTime.of(2026, 4, 10, 11, 0), entries.single().scheduledFor)
    }

    @Test
    fun buildPlanBatchAddEntries_extendsWeeklyIntervalBackwardForManualBackfill() {
        val groupUuid = UUID.fromString("c1930158-390a-4fa5-8cd7-76d3e5911a68")
        val group = medicationGroup(
            uuid = groupUuid,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 2,
                since = LocalDate.of(2026, 4, 15),
                weeklyDaysOfWeek = setOf(DayOfWeek.WEDNESDAY),
                times = listOf(LocalTime.of(10, 30)),
            ),
            medications = listOf(testMedicationGroupMedication(details = estradiolDetails())),
        )

        val entries = buildPlanBatchAddEntries(
            group = group,
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 4, 15),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(2, entries.size)
        assertNull(entries[0].sourceGroupUuid)
        assertEquals(Instant.parse("2026-04-01T10:30:00Z"), entries[0].appliedAt)
        assertEquals(groupUuid, entries[1].sourceGroupUuid)
        assertEquals(LocalDateTime.of(2026, 4, 15, 10, 30), entries[1].scheduledFor)
    }

    @Test
    fun buildPlanBatchAddEntries_createsOneRecordPerMedicationForEachOccurrence() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(details = estradiolDetails(), count = 3),
                testMedicationGroupMedication(details = spironolactoneDetails(), count = 1),
            ),
        )

        val entries = buildPlanBatchAddEntries(
            group = group,
            startDate = LocalDate.of(2026, 4, 10),
            endDate = LocalDate.of(2026, 4, 10),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(2, entries.size)
        assertEquals(listOf(3, 1), entries.map { entry -> entry.count })
    }

    @Test
    fun buildPlanBatchAddEntries_skipsPlannedSlotWhenRecordAlreadyExists() {
        val groupUuid = UUID.fromString("865b9574-199f-4bbb-a90b-07b2738bd1ee")
        val existingSlot = LocalDateTime.of(2026, 4, 10, 9, 0)
        val remainingSlot = LocalDateTime.of(2026, 4, 10, 21, 0)
        val group = medicationGroup(
            uuid = groupUuid,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(existingSlot.toLocalTime(), remainingSlot.toLocalTime()),
            ),
            medications = listOf(
                testMedicationGroupMedication(details = estradiolDetails(), count = 3),
                testMedicationGroupMedication(details = spironolactoneDetails(), count = 1),
            ),
        )
        val existingEntry = testMedicationLogEntry(
            details = estradiolDetails(),
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(existingSlot),
            scheduledFor = existingSlot,
        )

        val plan = buildPlanBatchAddEntryPlan(
            group = group,
            existingEntries = listOf(existingEntry),
            startDate = LocalDate.of(2026, 4, 10),
            endDate = LocalDate.of(2026, 4, 10),
            zoneId = ZoneId.of("UTC"),
        )

        val entries = plan.entries
        assertEquals(2, entries.size)
        assertEquals(2, plan.skippedEntryCount)
        assertEquals(listOf(remainingSlot, remainingSlot), entries.map { entry -> entry.scheduledFor })
    }

    @Test
    fun planBatchAddUiState_disablesConfirmWhenNoGroupIsSelected() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(details = estradiolDetails())),
        )
        val entry = buildPlanBatchAddEntries(
            group = group,
            startDate = LocalDate.of(2026, 4, 10),
            endDate = LocalDate.of(2026, 4, 10),
            zoneId = ZoneId.of("UTC"),
        ).single()

        assertEquals(
            false,
            PlanBatchAddUiState(
                selectedGroupUuid = null,
                entriesToAdd = listOf(entry),
            ).canConfirm
        )
        assertEquals(
            true,
            PlanBatchAddUiState(
                selectedGroupUuid = group.uuid,
                entriesToAdd = listOf(entry),
            ).canConfirm
        )
    }

    private fun medicationGroup(
        uuid: UUID = UUID.fromString("621f4792-93d2-4d42-aa86-fab9d5c968b1"),
        schedule: MedicationGroupSchedule,
        medications: List<MedicationGroupMedication>,
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = schedule,
            medications = medications,
            notificationsEnabled = true,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
    }

    private fun estradiolDetails() = testCatalogMedicationDetails(
        key = MedicationKey.ESTRADIOL,
        applicationType = MedicationApplicationType.ORAL,
        dose = MedicationDose.MgAsMedicine(2.0),
    )

    private fun spironolactoneDetails() = testCatalogMedicationDetails(
        key = MedicationKey.SPIRONOLACTONE,
        applicationType = MedicationApplicationType.ORAL,
        dose = MedicationDose.MgAsMedicine(100.0),
    )

    private fun planBatchAddViewModel(groups: List<MedicationGroup>): PlanBatchAddViewModel {
        val medicationGroupRepository = mockk<MedicationGroupRepository>()
        val medicationLogRepository = mockk<MedicationLogRepository>()
        val settingsRepository = mockk<SettingsRepository>()

        every { medicationGroupRepository.observeGroups() } returns flowOf(groups)
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { settingsRepository.settingsState } returns MutableStateFlow(
            SettingsState(remindersEnabled = true)
        )

        return PlanBatchAddViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = mockk<MedicationReminderScheduler>(relaxed = true),
            settingsRepository = settingsRepository,
        )
    }
}
