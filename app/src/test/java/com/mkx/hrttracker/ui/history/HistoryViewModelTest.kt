package com.mkx.hrttracker.ui.history

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val dispatcher = StandardTestDispatcher()
    private val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 26, 10, 0))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settingsRepository.settingsState } returns MutableStateFlow(SettingsState())
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
            settingsRepository = settingsRepository,
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
            settingsRepository = settingsRepository,
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
            settingsRepository = settingsRepository,
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
            settingsRepository = settingsRepository,
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
            settingsRepository = settingsRepository,
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
            settingsRepository = settingsRepository,
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
    fun resetCalendarViewport_returnsToCurrentMonthAndClearsSelectedDate() = runTest {
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        val today = appTimeSource.currentMinute.value.toLocalDate()

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.setDisplayedMonth(YearMonth.of(2026, 3), clearSelection = false)
        viewModel.toggleSelectedDate(LocalDate.of(2026, 3, 20))
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 3), viewModel.uiState.value.displayedMonth)
        assertEquals(LocalDate.of(2026, 3, 20), viewModel.uiState.value.selectedDate)

        viewModel.resetCalendarViewport()
        advanceUntilIdle()

        assertEquals(YearMonth.from(today), viewModel.uiState.value.displayedMonth)
        assertNull(viewModel.uiState.value.selectedDate)
    }

    @Test
    fun hiddenArchivedGroupRecords_areCountedForDeleteAllWarning() = runTest {
        val settingsState = MutableStateFlow(SettingsState(showArchivedGroupRecords = false))
        every { settingsRepository.settingsState } returns settingsState
        val archivedGroup = testGroup(
            uuid = UUID.fromString("5130b7b7-50dd-4a62-bd08-15f35b7863de"),
            since = LocalDate.of(2026, 3, 1),
            archivedAt = Instant.parse("2026-04-20T00:00:00Z"),
        )
        val visibleEntry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0),
            ),
            sourceGroupUuid = null,
            appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
        )
        val hiddenEntry = testMedicationLogEntry(
            details = archivedGroup.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = archivedGroup.uuid,
            appliedAt = Instant.parse("2026-04-19T00:00:00Z"),
            scheduledFor = LocalDateTime.of(2026, 4, 19, 9, 0),
        )
        every { medicationLogRepository.observeEntries() } returns flowOf(listOf(visibleEntry, hiddenEntry))
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(archivedGroup))

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(listOf(visibleEntry), viewModel.uiState.value.entries)
        assertEquals(2, viewModel.uiState.value.allEntryCount)
        assertEquals(1, viewModel.uiState.value.hiddenArchivedGroupRecordCount)
        assertFalse(viewModel.uiState.value.showArchivedGroupRecords)
    }

    @Test
    fun deleteAllEntries_deletesHiddenArchivedOnlyRecords() = runTest {
        val settingsState = MutableStateFlow(SettingsState(showArchivedGroupRecords = false))
        every { settingsRepository.settingsState } returns settingsState
        val archivedGroup = testGroup(
            uuid = UUID.fromString("0e26438c-d4b6-4ee0-a4e9-91f9affebf11"),
            since = LocalDate.of(2026, 3, 1),
            archivedAt = Instant.parse("2026-04-20T00:00:00Z"),
        )
        val hiddenEntry = testMedicationLogEntry(
            details = archivedGroup.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = archivedGroup.uuid,
            appliedAt = Instant.parse("2026-04-19T00:00:00Z"),
            scheduledFor = LocalDateTime.of(2026, 4, 19, 9, 0),
        )
        every { medicationLogRepository.observeEntries() } returns flowOf(listOf(hiddenEntry))
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(archivedGroup))
        coEvery { medicationLogRepository.deleteAllEntries() } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertEquals(1, viewModel.uiState.value.allEntryCount)
        assertEquals(1, viewModel.uiState.value.hiddenArchivedGroupRecordCount)

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
    fun setShowArchivedGroupRecords_persistsHistoryMenuOption() = runTest {
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { settingsRepository.setShowArchivedGroupRecords(false) } returns Unit

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.setShowArchivedGroupRecords(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setShowArchivedGroupRecords(false) }
    }

    @Test
    fun calendarRange_includesArchivedGroupStartWhenArchivedRecordsAreShown() = runTest {
        val archivedGroup = testGroup(
            uuid = UUID.fromString("38685781-560d-4a70-b53d-b50e939df536"),
            since = LocalDate.of(2026, 3, 1),
            archivedAt = Instant.parse("2026-04-20T00:00:00Z"),
        )
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(archivedGroup))

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 3), viewModel.uiState.value.calendarStartMonth)
    }

    @Test
    fun clockTickAcrossMonth_updatesRangeWithoutMovingDisplayedMonthOrSelection() = runTest {
        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 4, 30, 23, 59))
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 4, 30), viewModel.uiState.value.today)
        assertEquals(YearMonth.of(2026, 4), viewModel.uiState.value.calendarEndMonth)
        assertEquals(YearMonth.of(2026, 4), viewModel.uiState.value.displayedMonth)

        viewModel.toggleSelectedDate(LocalDate.of(2026, 4, 30))
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 4, 30), viewModel.uiState.value.selectedDate)

        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 5, 1, 0, 0))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 5, 1), viewModel.uiState.value.today)
        assertEquals(YearMonth.of(2026, 4), viewModel.uiState.value.calendarStartMonth)
        assertEquals(YearMonth.of(2026, 5), viewModel.uiState.value.calendarEndMonth)
        assertEquals(YearMonth.of(2026, 4), viewModel.uiState.value.displayedMonth)
        assertEquals(LocalDate.of(2026, 4, 30), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun clockTickAcrossMonthWithoutUiCollector_updatesRangeWithoutMovingDisplayedMonth() = runTest {
        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 4, 30, 23, 59))
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            appTimeSource = appTimeSource,
        )
        val collectionJob = startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 4), viewModel.uiState.value.displayedMonth)

        collectionJob.cancel()
        advanceTimeBy(5_000)
        advanceUntilIdle()

        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 5, 1, 0, 0))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 5, 1), viewModel.uiState.value.today)
        assertEquals(YearMonth.of(2026, 4), viewModel.uiState.value.calendarStartMonth)
        assertEquals(YearMonth.of(2026, 5), viewModel.uiState.value.calendarEndMonth)
        assertEquals(YearMonth.of(2026, 4), viewModel.uiState.value.displayedMonth)
    }

    private fun TestScope.startUiStateCollection(viewModel: HistoryViewModel): Job {
        return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private fun testGroup(
        uuid: UUID,
        since: LocalDate,
        archivedAt: Instant?,
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = "Archived group",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = since,
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0),
                    ),
                )
            ),
            createdAt = testInstant(since.atStartOfDay()),
            updatedAt = testInstant(since.atStartOfDay()),
            archivedAt = archivedAt,
        )
    }
}
