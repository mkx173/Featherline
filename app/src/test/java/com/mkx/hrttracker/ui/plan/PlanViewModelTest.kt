package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.settings.FirstDayOfWeekOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settingsRepository.settingsState } returns MutableStateFlow(
            SettingsState(firstDayOfWeekOption = FirstDayOfWeekOption.MONDAY)
        )
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun clockTickAcrossWeekBoundary_withoutSelectionMovesImplicitDisplayToToday() = runTest {
        val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 26, 23, 59))
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(medicationGroup(times = listOf(LocalTime.of(8, 0))))
        )

        val viewModel = PlanViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 4, 13), viewModel.uiState.value.calendarStartDate)
        assertEquals(LocalDate.of(2026, 5, 3), viewModel.uiState.value.calendarEndDate)
        assertEquals(null, viewModel.uiState.value.selectedDate)
        assertEquals(LocalDate.of(2026, 4, 26), viewModel.uiState.value.daySchedule.date)

        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 4, 27, 0, 0))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 4, 20), viewModel.uiState.value.calendarStartDate)
        assertEquals(LocalDate.of(2026, 5, 10), viewModel.uiState.value.calendarEndDate)
        assertEquals(null, viewModel.uiState.value.selectedDate)
        assertEquals(LocalDate.of(2026, 4, 27), viewModel.uiState.value.daySchedule.date)
        assertEquals(1, viewModel.uiState.value.daySchedule.scheduledEntries.size)
    }

    @Test
    fun clockTickPastScheduledTime_updatesSelectedDayPastDueState() = runTest {
        val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 18, 7, 59))
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(medicationGroup(times = listOf(LocalTime.of(8, 0))))
        )

        val viewModel = PlanViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.daySchedule.scheduledEntries.single().isPastDue)

        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 4, 18, 8, 1))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.daySchedule.scheduledEntries.single().isDueSoon)
        assertFalse(viewModel.uiState.value.daySchedule.scheduledEntries.single().isPastDue)

        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 4, 18, 9, 1))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.daySchedule.scheduledEntries.single().isPastDue)
    }

    @Test
    fun clockTickAcrossWeekBoundary_keepsSelectionOutsideNewCalendarRange() = runTest {
        val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 26, 23, 59))
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(medicationGroup(times = listOf(LocalTime.of(8, 0))))
        )

        val viewModel = PlanViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        val selectedDate = LocalDate.of(2026, 4, 13)
        viewModel.toggleSelectedDate(selectedDate)
        advanceUntilIdle()

        assertEquals(selectedDate, viewModel.uiState.value.selectedDate)
        assertEquals(selectedDate, viewModel.uiState.value.daySchedule.date)

        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 4, 27, 0, 0))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 4, 20), viewModel.uiState.value.calendarStartDate)
        assertEquals(LocalDate.of(2026, 5, 10), viewModel.uiState.value.calendarEndDate)
        assertEquals(selectedDate, viewModel.uiState.value.selectedDate)
        assertEquals(selectedDate, viewModel.uiState.value.daySchedule.date)
        assertEquals(1, viewModel.uiState.value.daySchedule.scheduledEntries.size)
        assertEquals(PlanCalendarDayStatus.MISSED, viewModel.uiState.value.calendarDays[selectedDate]?.status)
    }

    @Test
    fun archivedRecordsAreShownAsPlannedRowsWithoutAddingArchivedGroupToRegimen() = runTest {
        val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 18, 10, 0))
        val settingsState = MutableStateFlow(SettingsState(showArchivedGroupRecords = true, firstDayOfWeekOption = FirstDayOfWeekOption.MONDAY))
        every { settingsRepository.settingsState } returns settingsState
        val archivedGroup = medicationGroup(times = listOf(LocalTime.of(8, 0))).copy(
            archivedAt = Instant.parse("2026-04-18T09:00:00Z"),
        )
        val archivedEntry = testMedicationLogEntry(
            details = archivedGroup.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = archivedGroup.uuid,
            appliedAt = Instant.parse("2026-04-18T00:05:00Z"),
            scheduledFor = LocalDateTime.of(2026, 4, 18, 8, 0),
        )
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(archivedGroup))
        every { medicationLogRepository.observeEntries() } returns flowOf(listOf(archivedEntry))

        val viewModel = PlanViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(listOf(archivedEntry.uuid), uiState.entries.map { it.uuid })
        assertTrue(uiState.medicationGroups.isEmpty())
        assertEquals(listOf(archivedGroup.uuid), uiState.scheduleMedicationGroups.map { it.uuid })
        assertEquals(1, uiState.daySchedule.scheduledEntries.size)
        assertTrue(uiState.daySchedule.scheduledEntries.single().isFulfilled)
        assertTrue(uiState.daySchedule.unplannedEntries.isEmpty())
        assertEquals(
            PlanCalendarDayStatus.FULFILLED,
            uiState.calendarDays.getValue(LocalDate.of(2026, 4, 18)).status,
        )
    }

    @Test
    fun hideArchivedGroupRecordsSettingHidesArchivedRecordsFromPlan() = runTest {
        val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 18, 10, 0))
        val settingsState = MutableStateFlow(SettingsState(showArchivedGroupRecords = false, firstDayOfWeekOption = FirstDayOfWeekOption.MONDAY))
        every { settingsRepository.settingsState } returns settingsState
        val archivedGroup = medicationGroup(times = listOf(LocalTime.of(8, 0))).copy(
            archivedAt = Instant.parse("2026-04-18T09:00:00Z"),
        )
        val archivedEntry = testMedicationLogEntry(
            details = archivedGroup.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = archivedGroup.uuid,
            appliedAt = Instant.parse("2026-04-18T00:05:00Z"),
            scheduledFor = LocalDateTime.of(2026, 4, 18, 8, 0),
        )
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(archivedGroup))
        every { medicationLogRepository.observeEntries() } returns flowOf(listOf(archivedEntry))

        val viewModel = PlanViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue(uiState.entries.isEmpty())
        assertTrue(uiState.medicationGroups.isEmpty())
        assertTrue(uiState.scheduleMedicationGroups.isEmpty())
        assertTrue(uiState.daySchedule.scheduledEntries.isEmpty())
        assertEquals(
            PlanCalendarDayStatus.NONE,
            uiState.calendarDays.getValue(LocalDate.of(2026, 4, 18)).status,
        )
    }

    @Test
    fun archivedMissedSlotsAreShownAfterTheyArePastDueButFutureArchivedSlotsAreHidden() = runTest {
        val now = LocalDateTime.of(2026, 4, 18, 10, 0)
        val appTimeSource = FakeAppTimeSource(now)
        val archivedGroup = medicationGroup(
            times = listOf(LocalTime.of(8, 0), LocalTime.of(21, 0))
        ).copy(
            archivedAt = now.atZone(ZoneId.systemDefault()).toInstant(),
            archivedAtLocal = now,
        )
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(archivedGroup))
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())

        val viewModel = PlanViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(listOf(LocalTime.of(8, 0)), uiState.daySchedule.scheduledEntries.map { it.scheduledTime })
        assertTrue(uiState.daySchedule.scheduledEntries.single().isPastDue)
        assertEquals(
            PlanCalendarDayStatus.MISSED,
            uiState.calendarDays.getValue(LocalDate.of(2026, 4, 18)).status,
        )
    }

    @Test
    fun recreatedGroupCreatedBetweenSlotsDoesNotShowPastSlotFromActiveCopy() = runTest {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 18, 10, 0)
        val createdAt = now.atZone(zoneId).toInstant()
        val originalGroupUuid = UUID.fromString("71c62a73-2476-4bfc-8297-837926b3b0e4")
        val recreatedGroupUuid = UUID.fromString("b2dd4935-bfb8-4f36-8d48-0d422f8bd809")
        val appTimeSource = FakeAppTimeSource(now)
        val baseGroup = medicationGroup(times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)))
        val archivedOriginalGroup = baseGroup.copy(
            uuid = originalGroupUuid,
            archivedAt = createdAt,
            archivedAtLocal = now,
        )
        val recreatedGroup = baseGroup.copy(
            uuid = recreatedGroupUuid,
            schedule = baseGroup.schedule.copy(
                timeSlots = listOf(
                    MedicationGroupScheduleTime(
                        uuid = UUID.randomUUID(),
                        time = LocalTime.of(9, 0),
                        effectiveFrom = now,
                    ),
                    MedicationGroupScheduleTime(
                        uuid = UUID.randomUUID(),
                        time = LocalTime.of(21, 0),
                        effectiveFrom = now,
                    ),
                ),
            ),
            createdAt = createdAt,
            updatedAt = createdAt,
            archivedAt = null,
            includePastScheduledSlots = false,
        )
        val fulfilledOriginalSlot = testMedicationLogEntry(
            details = archivedOriginalGroup.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = originalGroupUuid,
            appliedAt = LocalDateTime.of(2026, 4, 18, 9, 5)
                .atZone(zoneId)
                .toInstant(),
            scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0),
        )
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(archivedOriginalGroup, recreatedGroup)
        )
        every { medicationLogRepository.observeEntries() } returns flowOf(listOf(fulfilledOriginalSlot))

        val viewModel = PlanViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(listOf(fulfilledOriginalSlot.uuid), uiState.entries.map { it.uuid })
        assertEquals(listOf(recreatedGroupUuid), uiState.medicationGroups.map { it.uuid })
        assertEquals(
            listOf(originalGroupUuid, recreatedGroupUuid),
            uiState.daySchedule.scheduledEntries.map { it.groupUuid },
        )
        assertEquals(
            listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)),
            uiState.daySchedule.scheduledEntries.map { it.scheduledTime },
        )
        assertTrue(uiState.daySchedule.scheduledEntries.first().isFulfilled)
        assertFalse(uiState.daySchedule.scheduledEntries.last().isFulfilled)
        assertTrue(uiState.daySchedule.unplannedEntries.isEmpty())
    }

    private fun TestScope.startUiStateCollection(viewModel: PlanViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private fun medicationGroup(times: List<LocalTime>): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString("ec6ed1ff-9a50-4f40-894b-80801f6a611d"),
            name = "Estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = times,
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("3f7a3c0b-c104-45cf-a0c1-ea95fd9871a8"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0),
                    )
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
    }
}
