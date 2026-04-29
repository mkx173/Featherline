package com.mkx.hrttracker.ui.history

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val dispatcher = StandardTestDispatcher()
    private val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 26, 10, 0))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun deleteAllEntries_updatesUiStateWithSuccessResult() = runTest {
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0),
                    ),
                    sourceGroupUuid = null,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                )
            )
        )
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicationLogRepository.deleteAllEntries() } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.deleteAllEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeletingAllEntries)
        assertEquals(
            HistoryDeleteAllEntriesResult.SUCCESS,
            viewModel.uiState.value.deleteAllEntriesResult,
        )

        viewModel.consumeDeleteAllEntriesResult()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deleteAllEntriesResult)
        coVerify(exactly = 1) { medicationLogRepository.deleteAllEntries() }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun deleteAllEntries_whenSchedulerFails_stillReportsSuccess() = runTest {
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0),
                    ),
                    sourceGroupUuid = null,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                )
            )
        )
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicationLogRepository.deleteAllEntries() } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } throws RuntimeException("schedule failed")

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.deleteAllEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeletingAllEntries)
        assertEquals(
            HistoryDeleteAllEntriesResult.SUCCESS,
            viewModel.uiState.value.deleteAllEntriesResult,
        )
        coVerify(exactly = 1) { medicationLogRepository.deleteAllEntries() }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun deleteSelectedEntries_updatesUiStateWithSuccessResult() = runTest {
        val entry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0),
            ),
            sourceGroupUuid = null,
            appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
        )
        every { medicationLogRepository.observeEntries() } returns flowOf(listOf(entry))
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicationLogRepository.deleteEntries(setOf(entry.uuid)) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.toggleEntrySelection(entry.uuid)
        viewModel.showDeleteConfirmation()
        viewModel.deleteSelectedEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeletingSelectedEntries)
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertEquals(
            HistoryDeleteSelectedEntriesResult.SUCCESS,
            viewModel.uiState.value.deleteSelectedEntriesResult,
        )

        viewModel.consumeDeleteSelectedEntriesResult()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deleteSelectedEntriesResult)
        coVerify(exactly = 1) { medicationLogRepository.deleteEntries(setOf(entry.uuid)) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun deleteSelectedEntries_whenSchedulerFails_stillReportsSuccessAndClearsSelection() = runTest {
        val entry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0),
            ),
            sourceGroupUuid = null,
            appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
        )
        every { medicationLogRepository.observeEntries() } returns flowOf(listOf(entry))
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicationLogRepository.deleteEntries(setOf(entry.uuid)) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } throws RuntimeException("schedule failed")

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.toggleEntrySelection(entry.uuid)
        viewModel.showDeleteConfirmation()
        viewModel.deleteSelectedEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeletingSelectedEntries)
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertEquals(
            HistoryDeleteSelectedEntriesResult.SUCCESS,
            viewModel.uiState.value.deleteSelectedEntriesResult,
        )
        coVerify(exactly = 1) { medicationLogRepository.deleteEntries(setOf(entry.uuid)) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun deleteSelectedEntries_whenRepositoryFails_updatesUiStateWithFailureResult() = runTest {
        val entry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0),
            ),
            sourceGroupUuid = null,
            appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
        )
        every { medicationLogRepository.observeEntries() } returns flowOf(listOf(entry))
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicationLogRepository.deleteEntries(setOf(entry.uuid)) } throws RuntimeException("delete failed")

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.toggleEntrySelection(entry.uuid)
        viewModel.showDeleteConfirmation()
        viewModel.deleteSelectedEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeletingSelectedEntries)
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
        assertEquals(
            HistoryDeleteSelectedEntriesResult.FAILURE,
            viewModel.uiState.value.deleteSelectedEntriesResult,
        )

        viewModel.consumeDeleteSelectedEntriesResult()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deleteSelectedEntriesResult)
        coVerify(exactly = 1) { medicationLogRepository.deleteEntries(setOf(entry.uuid)) }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun setDisplayedMonth_withSameMonthAndClearSelection_clearsSelectedDate() = runTest {
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        val today = appTimeSource.currentMinute.value.toLocalDate()

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.toggleSelectedDate(today)
        advanceUntilIdle()

        assertEquals(today, viewModel.uiState.value.selectedDate)

        viewModel.setDisplayedMonth(YearMonth.from(today), clearSelection = true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedDate)
    }

    @Test
    fun clockTickAcrossMonth_updatesTodayAndCalendarEndMonth() = runTest {
        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 4, 30, 23, 59))
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 4, 30), viewModel.uiState.value.today)
        assertEquals(YearMonth.of(2026, 4), viewModel.uiState.value.calendarEndMonth)

        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 5, 1, 0, 0))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 5, 1), viewModel.uiState.value.today)
        assertEquals(YearMonth.of(2026, 5), viewModel.uiState.value.calendarEndMonth)
    }

    private fun TestScope.startUiStateCollection(viewModel: HistoryViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }
}
