package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
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
        every { settingsRepository.settingsState } returns MutableStateFlow(SettingsState())
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
