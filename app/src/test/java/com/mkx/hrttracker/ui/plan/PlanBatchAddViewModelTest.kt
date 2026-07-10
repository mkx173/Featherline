package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogEntryInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.PostLogStockWarning
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
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
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
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
        assertEquals(false, viewModel.uiState.value.selectedGroupStartsInFuture)
    }

    @Test
    fun selectGroup_flagsGroupThatStartsInFuture() = runTest {
        val futureGroup = medicationGroup(
            uuid = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000099"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 5, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
        )
        val viewModel = planBatchAddViewModel(
            groups = listOf(futureGroup),
            now = LocalDateTime.of(2026, 4, 25, 12, 0),
        )
        advanceUntilIdle()

        viewModel.selectGroup(futureGroup.uuid)
        advanceUntilIdle()

        // A not-yet-started plan is flagged so the UI shows the prompt instead of
        // an inverted range, and nothing can be confirmed for it.
        assertEquals(true, viewModel.uiState.value.selectedGroupStartsInFuture)
        assertEquals(0, viewModel.uiState.value.entryCount)
        assertEquals(false, viewModel.uiState.value.canConfirm)
    }

    @Test
    fun futureGroup_resolvesToTodayRangeOnceStartDateArrives() = runTest {
        val futureGroup = medicationGroup(
            uuid = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000099"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 5, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
        )
        val timeSource = FakeAppTimeSource(initialMinute = LocalDateTime.of(2026, 4, 25, 12, 0))
        val viewModel =
            planBatchAddViewModel(groups = listOf(futureGroup), appTimeSource = timeSource)
        advanceUntilIdle()

        viewModel.selectGroup(futureGroup.uuid)
        advanceUntilIdle()

        // The clock rolls forward to the plan's start date while the screen stays open.
        timeSource.setCurrentMinute(LocalDateTime.of(2026, 5, 1, 12, 0))
        advanceUntilIdle()

        // Once the start date arrives the guard clears and the default range
        // resolves to today..today, not the stale `since..(old today)` inverted
        // range frozen when the future group was first selected.
        assertEquals(false, viewModel.uiState.value.selectedGroupStartsInFuture)
        assertEquals(LocalDate.of(2026, 5, 1), viewModel.uiState.value.startDate)
        assertEquals(LocalDate.of(2026, 5, 1), viewModel.uiState.value.endDate)
    }

    @Test
    fun selectedGroup_freezesDefaultEndDateAcrossRollover() = runTest {
        val group = medicationGroup(
            uuid = UUID.fromString("09b7f93e-0196-47f5-bb55-3581ee5d0ee7"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
        )
        val timeSource = FakeAppTimeSource(initialMinute = LocalDateTime.of(2026, 4, 25, 12, 0))
        val viewModel = planBatchAddViewModel(groups = listOf(group), appTimeSource = timeSource)
        advanceUntilIdle()

        viewModel.selectGroup(group.uuid)
        advanceUntilIdle()

        timeSource.setCurrentMinute(LocalDateTime.of(2026, 4, 26, 12, 0))
        advanceUntilIdle()

        // A selected group's default range is frozen at selection time, so a
        // date rollover advances `today` but must not drag the end date with it.
        assertEquals(LocalDate.of(2026, 4, 26), viewModel.uiState.value.today)
        assertEquals(LocalDate.of(2026, 4, 25), viewModel.uiState.value.endDate)
    }

    @Test
    fun noGroupSelected_tracksLiveDateAcrossRollover() = runTest {
        val group = medicationGroup(
            uuid = UUID.fromString("09b7f93e-0196-47f5-bb55-3581ee5d0ee7"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
        )
        val timeSource = FakeAppTimeSource(initialMinute = LocalDateTime.of(2026, 4, 25, 12, 0))
        val viewModel = planBatchAddViewModel(groups = listOf(group), appTimeSource = timeSource)
        advanceUntilIdle()

        // Select then deselect so any frozen range is cleared back to "no group".
        viewModel.selectGroup(group.uuid)
        advanceUntilIdle()
        viewModel.selectGroup(group.uuid)
        advanceUntilIdle()

        timeSource.setCurrentMinute(LocalDateTime.of(2026, 4, 26, 12, 0))
        advanceUntilIdle()

        // With no group selected the default range tracks the live date.
        assertNull(viewModel.uiState.value.selectedGroupUuid)
        assertEquals(LocalDate.of(2026, 4, 26), viewModel.uiState.value.endDate)
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
            medications = listOf(
                testMedicationGroupMedication(
                    medicine = estradiolMedicine(),
                    count = 2
                )
            ),
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
        val savedAt = LocalDateTime.of(2026, 4, 10, 10, 0)
        val group = medicationGroup(
            uuid = groupUuid,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
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
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
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
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
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
    fun buildPlanBatchAddEntries_appliesFloorDivisionAcrossBackfillWeekBoundary() {
        // Regression for the truncate-toward-zero bug: with since = Wed Apr 15
        // and selected day = Monday, the Mon two days before since (Apr 13)
        // would mis-match week 0 under integer division (-2 / 7 == 0), and
        // the Mon nine days before (Apr 6) would mis-match week -1
        // (-9 / 7 == -1) and be skipped. Floor division puts Apr 13 in
        // week -1 (skipped for interval=2) and Apr 6 in week -2 (matches),
        // preserving every-other-week parity straddling `since`.
        val groupUuid = UUID.fromString("4fe6dca6-7c4b-4be9-9b3f-83b7f50f0bd1")
        val group = medicationGroup(
            uuid = groupUuid,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 2,
                since = LocalDate.of(2026, 4, 15),
                weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY),
                times = listOf(LocalTime.of(10, 30)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
        )

        val entries = buildPlanBatchAddEntries(
            group = group,
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 4, 14),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(1, entries.size)
        assertEquals(Instant.parse("2026-04-06T10:30:00Z"), entries.single().appliedAt)
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
                testMedicationGroupMedication(medicine = estradiolMedicine(), count = 3),
                testMedicationGroupMedication(medicine = spironolactoneMedicine(), count = 1),
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
                testMedicationGroupMedication(medicine = estradiolMedicine(), count = 3),
                testMedicationGroupMedication(medicine = spironolactoneMedicine(), count = 1),
            ),
        )
        val existingEntry = testMedicationLogEntry(
            medicine = estradiolMedicine(),
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
        assertEquals(
            listOf(remainingSlot, remainingSlot),
            entries.map { entry -> entry.scheduledFor })
    }

    @Test
    fun buildPlanBatchAddEntries_skipsSlotsScheduledAfterNow() {
        val groupUuid = UUID.fromString("ee2bfd17-4873-43fb-92fd-d7f0c9cd25d2")
        val pastSlot = LocalDateTime.of(2026, 4, 10, 0, 15)
        val futureSlot = LocalDateTime.of(2026, 4, 10, 9, 0)
        val group = medicationGroup(
            uuid = groupUuid,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(pastSlot.toLocalTime(), futureSlot.toLocalTime()),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
        )

        val entries = buildPlanBatchAddEntries(
            group = group,
            startDate = LocalDate.of(2026, 4, 10),
            endDate = LocalDate.of(2026, 4, 10),
            now = LocalDateTime.of(2026, 4, 10, 0, 30),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(1, entries.size)
        assertEquals(pastSlot, entries.single().scheduledFor)
        assertEquals(groupUuid, entries.single().sourceGroupUuid)
    }

    @Test
    fun buildPlanBatchAddEntries_includesSlotScheduledExactlyNow() {
        val now = LocalDateTime.of(2026, 4, 10, 9, 0)
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = now.toLocalDate(),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(now.toLocalTime()),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
        )

        val entries = buildPlanBatchAddEntries(
            group = group,
            startDate = now.toLocalDate(),
            endDate = now.toLocalDate(),
            now = now,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(1, entries.size)
        assertEquals(now, entries.single().scheduledFor)
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
            medications = listOf(testMedicationGroupMedication(medicine = estradiolMedicine())),
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

    @Test
    fun saveSelectedRange_withDeductStockRetainsPostLogStockWarningUntilConsumed() = runTest {
        val medicine = estradiolMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 1.0,
                unitsLastTotal = 1.0,
            )
        )
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = medicine)),
        )
        val medicationGroupRepository = mockk<MedicationGroupRepository>()
        val medicationLogRepository = mockk<MedicationLogRepository>()
        val settingsRepository = mockk<SettingsRepository>()
        val medicineStockRepository = mockk<MedicineStockRepository>()

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { settingsRepository.settingsState } returns MutableStateFlow(
            SettingsState(
                remindersEnabled = true
            )
        )
        every { medicineStockRepository.getCachedProjections() } returns emptyList()
        every { medicineStockRepository.observeProjections() } returns flowOf(emptyList())
        coEvery {
            medicationLogRepository.saveBackfillEntries(
                any(),
                deductStock = true
            )
        } returns Unit
        coEvery { medicineStockRepository.projectAllOnce(any()) } returnsMany listOf(
            listOf(
                stockProjection(
                    medicine = medicine,
                    state = com.mkx.hrttracker.model.medication.MedicineStockState.HEALTHY,
                )
            ),
            listOf(
                stockProjection(
                    medicine = medicine,
                    state = com.mkx.hrttracker.model.medication.MedicineStockState.OUT,
                )
            ),
        )

        val viewModel = PlanBatchAddViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = mockk<MedicationReminderScheduler>(relaxed = true),
            settingsRepository = settingsRepository,
            medicineStockRepository = medicineStockRepository,
            appTimeSource = FakeAppTimeSource(initialMinute = LocalDateTime.of(2026, 4, 10, 12, 0)),
        )
        advanceUntilIdle()

        viewModel.selectGroup(group.uuid)
        viewModel.setDeductStock(true)
        advanceUntilIdle()

        viewModel.saveSelectedRange()
        advanceUntilIdle()

        assertEquals(
            PostLogStockWarning.Single(
                medicine,
                com.mkx.hrttracker.model.medication.MedicineStockState.OUT
            ),
            viewModel.uiState.value.postLogStockWarning,
        )
        viewModel.consumeSavedState()
        advanceUntilIdle()
        assertEquals(
            PostLogStockWarning.Single(
                medicine,
                com.mkx.hrttracker.model.medication.MedicineStockState.OUT
            ),
            viewModel.uiState.value.postLogStockWarning,
        )
        viewModel.consumePostLogStockWarning()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.postLogStockWarning)
        coVerify(exactly = 1) {
            medicationLogRepository.saveBackfillEntries(any(), deductStock = true)
        }
    }

    @Test
    fun saveSelectedRange_failureRestoresSelectedGroupAndDeductStockChoice() = runTest {
        val medicine = estradiolMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 1.0,
                unitsLastTotal = 1.0,
            )
        )
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = medicine)),
        )
        val medicationGroupRepository = mockk<MedicationGroupRepository>()
        val medicationLogRepository = mockk<MedicationLogRepository>()
        val settingsRepository = mockk<SettingsRepository>()
        val medicineStockRepository = mockk<MedicineStockRepository>()

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { settingsRepository.settingsState } returns MutableStateFlow(
            SettingsState(
                remindersEnabled = true
            )
        )
        every { medicineStockRepository.getCachedProjections() } returns emptyList()
        every { medicineStockRepository.observeProjections() } returns flowOf(emptyList())
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns emptyList()
        coEvery {
            medicationLogRepository.saveBackfillEntries(any(), deductStock = true)
        } throws RuntimeException("save failed")

        val viewModel = PlanBatchAddViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = mockk<MedicationReminderScheduler>(relaxed = true),
            settingsRepository = settingsRepository,
            medicineStockRepository = medicineStockRepository,
            appTimeSource = FakeAppTimeSource(initialMinute = LocalDateTime.of(2026, 4, 10, 12, 0)),
        )
        advanceUntilIdle()

        viewModel.selectGroup(group.uuid)
        viewModel.setDeductStock(true)
        advanceUntilIdle()
        viewModel.saveSelectedRange()
        advanceUntilIdle()

        assertEquals(group.uuid, viewModel.uiState.value.selectedGroupUuid)
        assertEquals(true, viewModel.uiState.value.deductStock)
        assertEquals(PlanBatchAddSaveResult.FAILURE, viewModel.uiState.value.saveResult)
        assertEquals(true, viewModel.uiState.value.canConfirm)
    }

    @Test
    fun saveSelectedRange_withoutDeductStockSavesWithoutStockWarning() = runTest {
        val medicine = estradiolMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 1.0,
                unitsLastTotal = 1.0,
            )
        )
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = medicine)),
        )
        val medicationGroupRepository = mockk<MedicationGroupRepository>()
        val medicationLogRepository = mockk<MedicationLogRepository>()
        val settingsRepository = mockk<SettingsRepository>()
        val medicineStockRepository = mockk<MedicineStockRepository>()

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { settingsRepository.settingsState } returns MutableStateFlow(
            SettingsState(
                remindersEnabled = true
            )
        )
        every { medicineStockRepository.getCachedProjections() } returns emptyList()
        every { medicineStockRepository.observeProjections() } returns flowOf(emptyList())
        coEvery {
            medicationLogRepository.saveBackfillEntries(
                any(),
                deductStock = false
            )
        } returns Unit

        val viewModel = PlanBatchAddViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = mockk<MedicationReminderScheduler>(relaxed = true),
            settingsRepository = settingsRepository,
            medicineStockRepository = medicineStockRepository,
            appTimeSource = FakeAppTimeSource(initialMinute = LocalDateTime.of(2026, 4, 10, 12, 0)),
        )
        advanceUntilIdle()

        viewModel.selectGroup(group.uuid)
        advanceUntilIdle()

        viewModel.saveSelectedRange()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.postLogStockWarning)
        coVerify(exactly = 1) {
            medicationLogRepository.saveBackfillEntries(any(), deductStock = false)
        }
        coVerify(exactly = 0) {
            medicineStockRepository.projectAllOnce(any())
        }
    }

    @Test
    fun saveSelectedRange_writeCompletes_evenWhenViewModelScopeIsCancelledMidWrite() = runTest {
        // Entry teardown (back press racing the 1-frame nav-lock gap) cancels
        // viewModelScope mid-write; a confirmed batch save must not be torn in
        // half (PR #60 finding 11).
        val medicine = estradiolMedicine()
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = medicine)),
        )
        val medicationGroupRepository = mockk<MedicationGroupRepository>()
        val medicationLogRepository = mockk<MedicationLogRepository>()
        val settingsRepository = mockk<SettingsRepository>()
        val medicineStockRepository = mockk<MedicineStockRepository>()

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { settingsRepository.settingsState } returns MutableStateFlow(
            SettingsState(
                remindersEnabled = true
            )
        )
        every { medicineStockRepository.getCachedProjections() } returns emptyList()
        every { medicineStockRepository.observeProjections() } returns flowOf(emptyList())
        val writeGate = CompletableDeferred<Unit>()
        var writeCompleted = false
        coEvery {
            medicationLogRepository.saveBackfillEntries(any(), deductStock = false)
        } coAnswers {
            writeGate.await()
            writeCompleted = true
        }

        val viewModel = PlanBatchAddViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = mockk<MedicationReminderScheduler>(relaxed = true),
            settingsRepository = settingsRepository,
            medicineStockRepository = medicineStockRepository,
            appTimeSource = FakeAppTimeSource(initialMinute = LocalDateTime.of(2026, 4, 10, 12, 0)),
        )
        advanceUntilIdle()

        viewModel.selectGroup(group.uuid)
        advanceUntilIdle()

        viewModel.saveSelectedRange()
        runCurrent()

        viewModel.viewModelScope.cancel()
        writeGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(writeCompleted)
    }

    @Test
    fun buildPlanBatchAddStockPreviewItems_excludesUntrackedMedicines() {
        val medicine = estradiolMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(
                trackingEnabled = false,
                unitsRemaining = null,
            )
        )
        val projection = stockProjection(
            medicine = medicine,
            state = com.mkx.hrttracker.model.medication.MedicineStockState.UNTRACKED,
        )
        val entry = MedicationLogEntryInput(
            medicineUuid = medicine.uuid,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = Instant.EPOCH,
            count = 1,
        )

        val items = buildPlanBatchAddStockPreviewItems(
            entriesToAdd = listOf(entry),
            stockProjections = listOf(projection),
        )

        assertEquals(emptyList<PlanBatchAddStockPreviewItem>(), items)
    }

    @Test
    fun buildPlanBatchAddStockPreviewItems_appliesDeductionsPerEntryIntoCumulativeAfterStock() {
        val medicine = estradiolMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 2.0,
                unitsLastTotal = 2.0,
                warnAtDaysRemaining = 14,
            )
        )
        val currentProjection = stockProjection(
            medicine = medicine,
            state = com.mkx.hrttracker.model.medication.MedicineStockState.HEALTHY,
        )
        val entries = listOf(
            MedicationLogEntryInput(
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = null,
                appliedAt = Instant.EPOCH,
                count = 1,
            ),
            MedicationLogEntryInput(
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = null,
                appliedAt = Instant.EPOCH.plusMillis(1),
                count = 1,
            ),
        )

        val items = buildPlanBatchAddStockPreviewItems(
            entriesToAdd = entries,
            stockProjections = listOf(currentProjection),
        )

        assertEquals(1, items.size)
        // before stock is the medicine's current stock; afterStock is cumulative.
        assertEquals(
            2.0,
            items.single().medicine.stock.unitsRemaining ?: error("missing before"),
            0.0
        )
        // Both 1-tablet deductions applied to one medicine: 2 - 1 - 1 = 0.
        assertEquals(0.0, items.single().afterStock.unitsRemaining ?: error("missing after"), 0.0)
    }

    @Test
    fun buildPlanBatchAddStockPreviewItems_collapsesDoseSignaturesIntoOneCardPerMedicine() {
        // A medicine logged with several dose signatures must surface as a single
        // card whose after-stock reflects the cumulative deduction of every entry
        // — not duplicate cards each repeating the medicine's final stock.
        val medicine = estradiolMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 10.0,
                unitsLastTotal = 10.0,
            )
        )
        val currentProjection = stockProjection(
            medicine = medicine,
            state = com.mkx.hrttracker.model.medication.MedicineStockState.HEALTHY,
        )
        val tabletEntry = MedicationLogEntryInput(
            medicineUuid = medicine.uuid,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = Instant.EPOCH,
            count = 1,
        )
        val sublingualEntry = tabletEntry.copy(
            applicationType = MedicationApplicationType.SUBLINGUAL,
            appliedAt = Instant.EPOCH.plusMillis(1),
        )

        val items = buildPlanBatchAddStockPreviewItems(
            entriesToAdd = listOf(tabletEntry, sublingualEntry),
            stockProjections = listOf(currentProjection),
        )

        assertEquals(1, items.size)
        // First contributing entry is the representative shown on the card.
        assertEquals(MedicationApplicationType.ORAL, items.single().applicationType)
        // Both 1-tablet deductions applied: 10 - 1 - 1 = 8.
        assertEquals(
            8.0,
            items.single().afterStock.unitsRemaining ?: error("missing stock"),
            0.0,
        )
    }

    @Test
    fun selectedGroupHasTrackedMedicine_trueWhenAnyGroupMedicineTracksStock() = runTest {
        // Drives the deduct-stock switch supporting text: when at least one
        // medicine in the group tracks stock, the switch reports the change.
        val tracked = estradiolMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 5.0,
                unitsLastTotal = 5.0,
            )
        )
        val untracked = spironolactoneMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(trackingEnabled = false),
        )
        val group = trackedMedicineGroup(medicines = listOf(tracked, untracked))
        val viewModel = planBatchAddViewModelWithProjections(
            group = group,
            projections = listOf(
                stockProjection(
                    medicine = tracked,
                    state = com.mkx.hrttracker.model.medication.MedicineStockState.HEALTHY
                ),
                stockProjection(
                    medicine = untracked,
                    state = com.mkx.hrttracker.model.medication.MedicineStockState.UNTRACKED
                ),
            ),
        )
        advanceUntilIdle()

        viewModel.selectGroup(group.uuid)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.selectedGroupHasTrackedMedicine)
    }

    @Test
    fun selectedGroupHasTrackedMedicine_falseWhenNoGroupMedicineTracksStock() = runTest {
        val untracked = spironolactoneMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(trackingEnabled = false),
        )
        val group = trackedMedicineGroup(medicines = listOf(untracked))
        val viewModel = planBatchAddViewModelWithProjections(
            group = group,
            projections = listOf(
                stockProjection(
                    medicine = untracked,
                    state = com.mkx.hrttracker.model.medication.MedicineStockState.UNTRACKED
                ),
            ),
        )
        advanceUntilIdle()

        viewModel.selectGroup(group.uuid)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.selectedGroupHasTrackedMedicine)
    }

    @Test
    fun deductStockAvailability_reportsAvailableWhenTrackedMedicineWouldChangeStock() = runTest {
        val tracked = estradiolMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 5.0,
                unitsLastTotal = 5.0,
            )
        )
        val group = trackedMedicineGroup(medicines = listOf(tracked))
        val viewModel = planBatchAddViewModelWithProjections(
            group = group,
            projections = listOf(
                stockProjection(
                    medicine = tracked,
                    state = com.mkx.hrttracker.model.medication.MedicineStockState.HEALTHY
                ),
            ),
        )
        advanceUntilIdle()

        viewModel.selectGroup(group.uuid)
        advanceUntilIdle()

        assertEquals(
            DeductStockAvailability.AVAILABLE,
            viewModel.uiState.value.deductStockAvailability
        )
    }

    @Test
    fun deductStockAvailability_distinguishesUnavailableSelectionStates() = runTest {
        val tracked = estradiolMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 5.0,
                unitsLastTotal = 5.0,
            )
        )
        val untracked = spironolactoneMedicine().copy(
            stock = com.mkx.hrttracker.model.medication.MedicineStock(trackingEnabled = false),
        )
        val selectedGroupStartsInFuture = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 5, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = tracked)),
        )
        val selectedGroupHasNoChangesYet = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(23, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = tracked)),
        )
        val selectedGroupHasNoTrackedMedicine = trackedMedicineGroup(medicines = listOf(untracked))

        val noGroupViewModel = planBatchAddViewModelWithProjections(
            group = trackedMedicineGroup(medicines = listOf(tracked)),
            projections = listOf(
                stockProjection(
                    medicine = tracked,
                    state = com.mkx.hrttracker.model.medication.MedicineStockState.HEALTHY
                ),
            ),
        )
        val futureViewModel = planBatchAddViewModelWithProjections(
            group = selectedGroupStartsInFuture,
            projections = listOf(
                stockProjection(
                    medicine = tracked,
                    state = com.mkx.hrttracker.model.medication.MedicineStockState.HEALTHY
                ),
            ),
            now = LocalDateTime.of(2026, 4, 10, 12, 0),
        )
        val noChangesViewModel = planBatchAddViewModelWithProjections(
            group = selectedGroupHasNoChangesYet,
            projections = listOf(
                stockProjection(
                    medicine = tracked,
                    state = com.mkx.hrttracker.model.medication.MedicineStockState.HEALTHY
                ),
            ),
            now = LocalDateTime.of(2026, 4, 10, 12, 0),
        )
        val noneTrackedViewModel = planBatchAddViewModelWithProjections(
            group = selectedGroupHasNoTrackedMedicine,
            projections = listOf(
                stockProjection(
                    medicine = untracked,
                    state = com.mkx.hrttracker.model.medication.MedicineStockState.UNTRACKED
                ),
            ),
        )
        advanceUntilIdle()

        futureViewModel.selectGroup(selectedGroupStartsInFuture.uuid)
        noChangesViewModel.selectGroup(selectedGroupHasNoChangesYet.uuid)
        noneTrackedViewModel.selectGroup(selectedGroupHasNoTrackedMedicine.uuid)
        advanceUntilIdle()

        assertEquals(
            DeductStockAvailability.NO_GROUP,
            noGroupViewModel.uiState.value.deductStockAvailability
        )
        assertEquals(
            DeductStockAvailability.NOT_STARTED,
            futureViewModel.uiState.value.deductStockAvailability
        )
        assertEquals(
            DeductStockAvailability.NO_CHANGES,
            noChangesViewModel.uiState.value.deductStockAvailability
        )
        assertEquals(
            DeductStockAvailability.NONE_TRACKED,
            noneTrackedViewModel.uiState.value.deductStockAvailability
        )
    }

    private fun trackedMedicineGroup(
        medicines: List<com.mkx.hrttracker.model.medication.Medicine>,
    ): MedicationGroup {
        return medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 10),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = medicines.map { medicine -> testMedicationGroupMedication(medicine = medicine) },
        )
    }

    private fun planBatchAddViewModelWithProjections(
        group: MedicationGroup,
        projections: List<com.mkx.hrttracker.model.medication.MedicineStockProjection>,
        now: LocalDateTime = LocalDateTime.of(2026, 4, 10, 12, 0),
    ): PlanBatchAddViewModel {
        val medicationGroupRepository = mockk<MedicationGroupRepository>()
        val medicationLogRepository = mockk<MedicationLogRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val medicineStockRepository = mockk<MedicineStockRepository>(relaxed = true)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { settingsRepository.settingsState } returns MutableStateFlow(
            SettingsState(
                remindersEnabled = true
            )
        )
        every { medicineStockRepository.getCachedProjections() } returns projections
        every { medicineStockRepository.observeProjections() } returns flowOf(projections)

        return PlanBatchAddViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = mockk(relaxed = true),
            settingsRepository = settingsRepository,
            appTimeSource = FakeAppTimeSource(initialMinute = now),
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

    private fun estradiolMedicine() = testMedicine(
        uuid = UUID.fromString("e2e2e2e2-0000-0000-0000-000000000000"),
        key = MedicationKey.ESTRADIOL,
        preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
    )

    private fun spironolactoneMedicine() = testMedicine(
        uuid = UUID.fromString("5a5a5a5a-0000-0000-0000-000000000000"),
        key = MedicationKey.SPIRONOLACTONE,
        preparation = MedicinePreparation.Pill(strengthMgPerTablet = 100.0),
    )

    private fun stockProjection(
        medicine: com.mkx.hrttracker.model.medication.Medicine,
        state: com.mkx.hrttracker.model.medication.MedicineStockState,
    ): com.mkx.hrttracker.model.medication.MedicineStockProjection {
        return com.mkx.hrttracker.model.medication.MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = 1.0,
            totalStockUnits = medicine.stock.unitsRemaining ?: 0.0,
            runway = com.mkx.hrttracker.model.medication.RunwayProjection.Days(
                days = 30,
                lastFulfillable = LocalDate.of(2026, 5, 1),
            ),
            intervalDays = 1,
            maxPerAdministration = 1.0,
            state = state,
        )
    }

    private fun planBatchAddViewModel(
        groups: List<MedicationGroup>,
        now: LocalDateTime = LocalDateTime.of(2026, 4, 25, 12, 0),
        appTimeSource: FakeAppTimeSource = FakeAppTimeSource(initialMinute = now),
    ): PlanBatchAddViewModel {
        val medicationGroupRepository = mockk<MedicationGroupRepository>()
        val medicationLogRepository = mockk<MedicationLogRepository>()
        val medicineStockRepository = mockk<MedicineStockRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()

        every { medicationGroupRepository.observeGroups() } returns flowOf(groups)
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicineStockRepository.getCachedProjections() } returns emptyList()
        every { medicineStockRepository.observeProjections() } returns flowOf(emptyList())
        every { settingsRepository.settingsState } returns MutableStateFlow(
            SettingsState(remindersEnabled = true)
        )

        return PlanBatchAddViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = mockk<MedicationReminderScheduler>(relaxed = true),
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
        )
    }
}
