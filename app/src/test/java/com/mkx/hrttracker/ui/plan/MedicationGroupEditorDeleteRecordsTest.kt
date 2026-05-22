package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogEntryInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationGroupEditorDeleteRecordsTest {
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicineRepository: MedicineRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler =
        mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 25, 10, 0))
    private lateinit var settingsStateFlow: MutableStateFlow<SettingsState>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settingsStateFlow = MutableStateFlow(SettingsState(remindersEnabled = true))
        every { settingsRepository.settingsState } returns settingsStateFlow
        coEvery { settingsRepository.getCurrentSettings() } returns settingsStateFlow.value
        coEvery { settingsRepository.nextGroupNameIndex() } returns 1
        every { medicationGroupRepository.getCachedGroup(any()) } returns null
        every {
            context.getString(R.string.default_group_name_format, any())
        } returns "Group 1"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadGroupForEditing_preservesRelatedEntryCountComputedBeforeGroupLoads() = runTest {
        val groupUuid = UUID.fromString("52004338-1f91-4b9b-b2ab-8f3b88d72117")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } coAnswers {
            delay(1)
            group
        }
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                ),
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T00:00:00Z"),
                ),
            )
        )

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingGroupForEditing)
        assertEquals(2, viewModel.uiState.value.relatedEntryCount)
    }

    @Test
    fun deleteRelatedEntries_updatesUiStateWithSuccessResult() = runTest {
        val groupUuid = UUID.fromString("1fd2a0a4-6ddd-48e0-bc6d-d5824d8832f8")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                    scheduledFor = LocalDateTime.of(2026, 4, 26, 9, 0),
                ),
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T00:00:00Z"),
                    scheduledFor = LocalDateTime.of(2026, 4, 25, 9, 0),
                ),
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    sourceGroupUuid = null,
                    appliedAt = Instant.parse("2026-04-24T00:00:00Z"),
                ),
            )
        )
        coEvery { medicationLogRepository.deleteEntriesForGroup(groupUuid) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.relatedEntryCount)
        assertEquals(2, viewModel.uiState.value.plannedEntryCount)

        viewModel.showDeleteRelatedEntriesConfirmation()
        assertEquals(true, viewModel.uiState.value.isDeleteRelatedEntriesConfirmationVisible)

        viewModel.deleteRelatedEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeletingRelatedEntries)
        assertEquals(
            DeleteRelatedEntriesResult.SUCCESS,
            viewModel.uiState.value.deleteRelatedEntriesResult,
        )
        assertEquals(false, viewModel.uiState.value.isDeleteRelatedEntriesConfirmationVisible)
        assertEquals(0, viewModel.uiState.value.relatedEntryCount)
        assertEquals(0, viewModel.uiState.value.plannedEntryCount)
        assertFalse(viewModel.uiState.value.isLocked)

        viewModel.consumeDeleteRelatedEntriesResult()
        assertNull(viewModel.uiState.value.deleteRelatedEntriesResult)
        coVerify(exactly = 1) { medicationLogRepository.deleteEntriesForGroup(groupUuid) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun deleteRelatedEntries_whenSchedulerFails_stillReportsSuccess() = runTest {
        val groupUuid = UUID.fromString("1a7b1eb7-79f8-43a6-80ee-86821fc60fa3")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                )
            )
        )
        coEvery { medicationLogRepository.deleteEntriesForGroup(groupUuid) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } throws RuntimeException("schedule failed")

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.showDeleteRelatedEntriesConfirmation()
        viewModel.deleteRelatedEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeletingRelatedEntries)
        assertEquals(
            DeleteRelatedEntriesResult.SUCCESS,
            viewModel.uiState.value.deleteRelatedEntriesResult,
        )
        assertEquals(0, viewModel.uiState.value.relatedEntryCount)
        coVerify(exactly = 1) { medicationLogRepository.deleteEntriesForGroup(groupUuid) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun deleteGroupAndRelatedEntries_deletesGroupRecordsAndMarksEditorDeleted() = runTest {
        val groupUuid = UUID.fromString("9bd82f30-b6eb-495e-b894-48a1779fd5d7")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                )
            )
        )
        coEvery { medicationGroupRepository.deleteGroupAndRelatedEntries(groupUuid) } returns Unit
        coEvery { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.showDeleteConfirmation()
        assertEquals(true, viewModel.uiState.value.isDeleteConfirmationVisible)

        viewModel.deleteGroupAndRelatedEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
        assertEquals(true, viewModel.uiState.value.isDeleted)

        coVerify(exactly = 1) { medicationGroupRepository.deleteGroupAndRelatedEntries(groupUuid) }
        coVerify(exactly = 1) { medicationReminderScheduler.cancelReminder(groupUuid) }
    }

    @Test
    fun deleteGroup_emitsCompletionEventBeforeReminderCleanupCompletes() = runTest {
        val groupUuid = UUID.fromString("3db3d6ae-dc74-4dd5-beb1-b0d26189c2b3")
        val group = testMedicationGroup(groupUuid)
        val completionEvents = mutableListOf<MedicationGroupEditorCompletionEvent>()
        var reminderCleanupStarted = false

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery { medicationGroupRepository.deleteGroup(groupUuid) } returns Unit
        coEvery { medicationReminderScheduler.cancelReminder(groupUuid) } coAnswers {
            reminderCleanupStarted = true
            delay(1)
            Unit
        }

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        backgroundScope.launch {
            viewModel.completionEvents.collect { completionEvents += it }
        }
        advanceUntilIdle()

        viewModel.deleteGroup()
        runCurrent()

        assertEquals(
            listOf(MedicationGroupEditorCompletionEvent.DELETE_OR_ARCHIVE_COMPLETED),
            completionEvents,
        )
        assertTrue(reminderCleanupStarted)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertTrue(viewModel.uiState.value.isDeleted)

        advanceUntilIdle()

        coVerify(exactly = 1) { medicationReminderScheduler.cancelReminder(groupUuid) }
    }

    @Test
    fun deleteGroup_allowsArchivedGroupDeletion() = runTest {
        val groupUuid = UUID.fromString("d461a04b-0319-4daa-9ee7-42f87b91fd49")
        val group = testMedicationGroup(groupUuid).copy(
            archivedAt = Instant.parse("2026-04-20T00:00:00Z"),
        )

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery { medicationGroupRepository.deleteGroup(groupUuid) } returns Unit
        coEvery { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isArchived)

        viewModel.showDeleteConfirmation()
        assertEquals(true, viewModel.uiState.value.isDeleteConfirmationVisible)

        viewModel.deleteGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
        assertEquals(true, viewModel.uiState.value.isDeleted)

        coVerify(exactly = 1) { medicationGroupRepository.deleteGroup(groupUuid) }
        coVerify(exactly = 1) { medicationReminderScheduler.cancelReminder(groupUuid) }
    }

    @Test
    fun deleteGroup_whenRepositoryFails_updatesUiStateWithFailureResult() = runTest {
        val groupUuid = UUID.fromString("853c2f2c-016a-4dbb-9048-d198ad65ce25")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery { medicationGroupRepository.deleteGroup(groupUuid) } throws RuntimeException("delete failed")

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.showDeleteConfirmation()
        viewModel.deleteGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
        assertFalse(viewModel.uiState.value.isDeleted)
        assertEquals(
            DeleteMedicationGroupResult.FAILURE,
            viewModel.uiState.value.deleteMedicationGroupResult,
        )

        viewModel.consumeDeleteMedicationGroupResult()
        assertNull(viewModel.uiState.value.deleteMedicationGroupResult)
        coVerify(exactly = 1) { medicationGroupRepository.deleteGroup(groupUuid) }
        coVerify(exactly = 0) { medicationReminderScheduler.cancelReminder(groupUuid) }
    }

    @Test
    fun archiveActions_areIgnoredWhileSaving() = runTest {
        val groupUuid = UUID.fromString("b06ccf6d-cf66-44cb-86c4-fd801b015d49")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = groupUuid,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        } coAnswers {
            delay(1)
            groupUuid
        }
        coEvery { medicationReminderScheduler.rescheduleGroup(groupUuid, any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.saveGroup()
        runCurrent()

        assertTrue(viewModel.uiState.value.isSaving)

        viewModel.showArchiveConfirmation()
        viewModel.archiveGroup()

        assertFalse(viewModel.uiState.value.isArchiveConfirmationVisible)
        coVerify(exactly = 0) { medicationGroupRepository.archiveGroup(any(), any()) }

        advanceUntilIdle()
    }

    @Test
    fun archiveGroup_whenFuturePlannedSlotExists_doesNotArchive() = runTest {
        val groupUuid = UUID.fromString("64f15c5a-77f7-42f1-bcd2-95240273c78d")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T01:30:00Z"),
                    scheduledFor = LocalDateTime.of(2026, 4, 25, 11, 0),
                )
            )
        )
        coEvery { medicationGroupRepository.archiveGroup(groupUuid, any()) } returns Unit
        coEvery { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.showArchiveConfirmation()
        viewModel.archiveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isArchiveConfirmationVisible)
        assertEquals(1, viewModel.uiState.value.currentOrFuturePlannedSlotCount)
        coVerify(exactly = 0) { medicationGroupRepository.archiveGroup(groupUuid, any()) }
        coVerify(exactly = 0) { medicationReminderScheduler.cancelReminder(groupUuid) }
    }

    @Test
    fun archiveGroup_whenCurrentMinutePlannedSlotExists_doesNotArchive() = runTest {
        val groupUuid = UUID.fromString("ef7ca208-5fa6-4d39-9d06-d728715d2f5a")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T01:00:00Z"),
                    scheduledFor = LocalDateTime.of(2026, 4, 25, 10, 0),
                )
            )
        )
        coEvery { medicationGroupRepository.archiveGroup(groupUuid, any()) } returns Unit
        coEvery { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.showArchiveConfirmation()
        viewModel.archiveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isArchiveConfirmationVisible)
        assertEquals(1, viewModel.uiState.value.currentOrFuturePlannedSlotCount)
        coVerify(exactly = 0) { medicationGroupRepository.archiveGroup(groupUuid, any()) }
        coVerify(exactly = 0) { medicationReminderScheduler.cancelReminder(groupUuid) }
    }

    @Test
    fun archiveAndRecreateGroup_whenFuturePlannedSlotExists_doesNotArchiveOrSave() = runTest {
        val groupUuid = UUID.fromString("9d69e824-6a14-4736-871b-197e41012a82")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T01:30:00Z"),
                    scheduledFor = LocalDateTime.of(2026, 4, 25, 11, 0),
                )
            )
        )
        coEvery { medicationGroupRepository.archiveGroup(groupUuid, any()) } returns Unit
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = groupUuid,
                now = any(),
            )
        } returns UUID.fromString("d3a56e2b-19d3-4f07-bd5e-374c1e646cb0")

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.showArchiveConfirmation()
        viewModel.archiveAndRecreateGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isArchiveConfirmationVisible)
        assertNull(viewModel.uiState.value.archiveAndRecreateMedicationGroupResult)
        coVerify(exactly = 0) { medicationGroupRepository.archiveGroup(groupUuid, any()) }
        coVerify(exactly = 0) {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = groupUuid,
                now = any(),
            )
        }
    }

    @Test
    fun archiveGroup_whenPlannedSlotsArePast_archivesGroup() = runTest {
        val groupUuid = UUID.fromString("9cf46787-85fa-40c2-b21a-35d95df8534c")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T00:00:00Z"),
                    scheduledFor = LocalDateTime.of(2026, 4, 25, 9, 0),
                ),
            )
        )
        coEvery { medicationGroupRepository.archiveGroup(groupUuid, any()) } returns Unit
        coEvery { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.showArchiveConfirmation()
        viewModel.archiveGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isArchiveConfirmationVisible)
        assertEquals(0, viewModel.uiState.value.currentOrFuturePlannedSlotCount)
        coVerify(exactly = 1) { medicationGroupRepository.archiveGroup(groupUuid, any()) }
        coVerify(exactly = 1) { medicationReminderScheduler.cancelReminder(groupUuid) }
    }

    @Test
    fun archiveAndRecreateGroup_archivesOriginalAndImmediatelySavesReplacement() = runTest {
        val groupUuid = UUID.fromString("015d4963-1e43-4d6f-9e58-d390fb182a7c")
        val savedGroupUuid = UUID.fromString("cb37018a-4569-4ee8-8e1d-ecdbd9b47e1b")
        val originalGroup = testMedicationGroup(groupUuid)
        val savedGroup = originalGroup.copy(
            uuid = savedGroupUuid,
            includePastScheduledSlots = false,
            recreatedFromGroupUuid = groupUuid,
            createdAt = Instant.parse("2026-04-25T10:00:00Z"),
            updatedAt = Instant.parse("2026-04-25T10:00:00Z"),
        )
        val savedMedications = slot<List<MedicationGroupMedicationInput>>()
        val savedNotificationsEnabled = slot<Boolean>()
        val savedIncludePastScheduledSlots = slot<Boolean>()

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(originalGroup))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns originalGroup
        coEvery { medicationGroupRepository.getGroup(savedGroupUuid) } returns savedGroup
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery { medicationGroupRepository.archiveGroup(groupUuid, any()) } returns Unit
        coEvery { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = groupUuid,
                now = any(),
            )
        } returns savedGroupUuid
        coEvery { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        val initialScrollToTopVersion = viewModel.uiState.value.scrollToTopRequestVersion

        viewModel.archiveAndRecreateGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecreatingAfterArchive)
        assertEquals(savedGroupUuid.toString(), viewModel.uiState.value.editingGroupId)
        assertNull(viewModel.uiState.value.pendingReplacementGroupId)
        assertEquals(groupUuid.toString(), viewModel.uiState.value.recreatedFromGroupId)
        assertFalse(viewModel.uiState.value.isScheduleStartDateLocked)
        assertFalse(viewModel.uiState.value.canEditBackfillOption)
        assertFalse(viewModel.uiState.value.canCreatePastScheduledSlotRecords)
        assertEquals(originalGroup.name, viewModel.uiState.value.groupName)
        assertFalse(viewModel.uiState.value.isArchived)
        assertEquals(
            ArchiveAndRecreateMedicationGroupResult.SUCCESS,
            viewModel.uiState.value.archiveAndRecreateMedicationGroupResult,
        )
        assertEquals(
            initialScrollToTopVersion + 1,
            viewModel.uiState.value.scrollToTopRequestVersion,
        )
        coVerify(exactly = 1) { medicationGroupRepository.archiveGroup(groupUuid, any()) }
        coVerify(exactly = 1) {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = capture(savedMedications),
                notificationsEnabled = capture(savedNotificationsEnabled),
                includePastScheduledSlots = capture(savedIncludePastScheduledSlots),
                replacesGroupUuid = groupUuid,
                now = any(),
            )
        }
        assertNull(savedMedications.captured.single().uuid)
        assertEquals(originalGroup.notificationsEnabled, savedNotificationsEnabled.captured)
        assertFalse(savedIncludePastScheduledSlots.captured)
        coVerify(exactly = 1) { medicationReminderScheduler.cancelReminder(groupUuid) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) }
    }

    @Test
    fun archiveAndRecreateGroup_discardsUnsavedEditorChangesFromArchivedGroup() = runTest {
        val groupUuid = UUID.fromString("da9fe950-f945-4ddd-8382-73dd860a5a74")
        val savedGroupUuid = UUID.fromString("d3a466a7-f3b6-4b3f-a611-5224f72d2fc9")
        val originalGroup = testMedicationGroup(groupUuid)
        val savedName = slot<String>()
        val savedSchedule = slot<com.mkx.hrttracker.data.repository.MedicationGroupScheduleInput>()
        val savedGroup = originalGroup.copy(
            uuid = savedGroupUuid,
            recreatedFromGroupUuid = groupUuid,
            includePastScheduledSlots = false,
            createdAt = Instant.parse("2026-04-25T10:00:00Z"),
            updatedAt = Instant.parse("2026-04-25T10:00:00Z"),
        )

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(originalGroup))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns originalGroup
        coEvery { medicationGroupRepository.getGroup(savedGroupUuid) } returns savedGroup
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery { medicationGroupRepository.archiveGroup(groupUuid, any()) } returns Unit
        coEvery { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = capture(savedName),
                colorKey = any(),
                schedule = capture(savedSchedule),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = groupUuid,
                now = any(),
            )
        } returns savedGroupUuid
        coEvery { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        val dailyTimeLocalId = viewModel.uiState.value.dailyTimes.single().localId
        viewModel.updateGroupName("Unsaved changed name")
        viewModel.updateDailyTime(dailyTimeLocalId, LocalTime.of(13, 0))

        viewModel.archiveAndRecreateGroup()
        advanceUntilIdle()

        assertEquals(originalGroup.name, savedName.captured)
        assertEquals(originalGroup.schedule.times, savedSchedule.captured.times)
    }

    @Test
    fun archiveAndRecreateGroup_whenSaveFails_reportsFailureAfterArchive() = runTest {
        val groupUuid = UUID.fromString("0e093bec-554e-46c3-91df-cb90e0e09cdb")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery { medicationGroupRepository.archiveGroup(groupUuid, any()) } returns Unit
        coEvery { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = groupUuid,
                now = any(),
            )
        } throws RuntimeException("save failed")

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.archiveAndRecreateGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecreatingAfterArchive)
        assertEquals(
            ArchiveAndRecreateMedicationGroupResult.FAILURE,
            viewModel.uiState.value.archiveAndRecreateMedicationGroupResult,
        )
        coVerify(exactly = 1) { medicationGroupRepository.archiveGroup(groupUuid, any()) }
    }

    @Test
    fun saveGroup_whenRepositoryFails_updatesUiStateWithFailureResult() = runTest {
        val groupUuid = UUID.fromString("8cb1973c-f22c-405a-8251-60194c022d47")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = groupUuid,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        } throws RuntimeException("save failed")

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.saveGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals(
            SaveMedicationGroupResult.FAILURE,
            viewModel.uiState.value.saveMedicationGroupResult,
        )

        viewModel.consumeSaveMedicationGroupResult()
        assertNull(viewModel.uiState.value.saveMedicationGroupResult)
        coVerify(exactly = 1) {
            medicationGroupRepository.saveGroup(
                uuid = groupUuid,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleGroup(any()) }
    }

    @Test
    fun saveNewGroup_whenSchedulerFails_marksSavedAndStoresReturnedGroupId() = runTest {
        val savedGroupUuid = UUID.fromString("c8ca8614-dce8-4f12-afd8-f75ab4db249c")

        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        } returns savedGroupUuid
        coEvery {
            medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any())
        } throws RuntimeException("schedule failed")

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()
        viewModel.showAddMedicationEditor()
        viewModel.updateEditingMedicineDraft { draft -> draft.copy(pillStrengthMg = "2") }
        viewModel.saveEditingMedication()

        viewModel.saveGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(true, viewModel.uiState.value.isSaved)
        assertEquals(savedGroupUuid.toString(), viewModel.uiState.value.editingGroupId)
        assertNull(viewModel.uiState.value.saveMedicationGroupResult)
        coVerify(exactly = 1) {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) }
    }

    @Test
    fun saveGroup_passesExactCurrentInstantForRepositoryTimestamps() = runTest {
        val savedGroupUuid = UUID.fromString("93b421c9-c8ff-4d8f-85ca-c5ebbb8ad915")
        val saveInstant = Instant.parse("2026-04-25T01:00:42.123Z")
        val capturedNow = slot<Instant>()
        appTimeSource.setCurrentInstant(saveInstant)

        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = capture(capturedNow),
            )
        } returns savedGroupUuid
        coEvery { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()
        viewModel.showAddMedicationEditor()
        viewModel.updateEditingMedicineDraft { draft -> draft.copy(pillStrengthMg = "2") }
        viewModel.saveEditingMedication()

        viewModel.saveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(saveInstant, capturedNow.captured)
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) }
    }

    @Test
    fun saveNewGroup_withCreatePastRecordsEnabled_addsPastScheduledRecords() = runTest {
        val savedGroupUuid = UUID.fromString("c697a076-3fb4-4dd4-a02b-39a3ec86fa4a")
        val savedEntries = slot<Collection<MedicationLogEntryInput>>()
        val savedGroup = testMedicationGroup(savedGroupUuid).copy(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 23),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            createdAt = Instant.parse("2026-04-25T01:00:00Z"),
            updatedAt = Instant.parse("2026-04-25T01:00:00Z"),
        )

        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        } returns savedGroupUuid
        coEvery { medicationGroupRepository.getGroup(savedGroupUuid) } returns savedGroup
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { medicationLogRepository.saveNewEntries(capture(savedEntries)) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()
        viewModel.showAddMedicationEditor()
        viewModel.updateEditingMedicineDraft { draft -> draft.copy(pillStrengthMg = "2") }
        viewModel.saveEditingMedication()
        viewModel.updateSinceDate(LocalDate.of(2026, 4, 23))
        viewModel.updateCreatePastScheduledSlotRecords(true)

        viewModel.saveGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isSaved)
        assertTrue(viewModel.uiState.value.createPastScheduledSlotRecords)
        assertNull(viewModel.uiState.value.createPastScheduledSlotRecordsResult)
        assertEquals(3, viewModel.uiState.value.createdPastScheduledSlotRecordCount)
        assertEquals(savedGroupUuid.toString(), viewModel.uiState.value.editingGroupId)

        val entries = savedEntries.captured.toList()
        assertEquals(3, entries.size)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 23, 9, 0),
                LocalDateTime.of(2026, 4, 24, 9, 0),
                LocalDateTime.of(2026, 4, 25, 9, 0),
            ),
            entries.map { entry -> entry.scheduledFor },
        )
        assertEquals(
            listOf(savedGroupUuid, savedGroupUuid, savedGroupUuid),
            entries.map { entry -> entry.sourceGroupUuid },
        )
        coVerify(exactly = 1) { medicationLogRepository.getEntries() }
        coVerify(exactly = 1) { medicationLogRepository.saveNewEntries(any()) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) }
    }

    @Test
    fun saveNewGroup_withCreatePastRecordsEnabled_emitsCompletionEventBeforeRecordGenerationCompletes() = runTest {
        val savedGroupUuid = UUID.fromString("466d303d-4627-4a1e-bd53-4f24e6b63e49")
        val savedGroup = testMedicationGroup(savedGroupUuid).copy(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 23),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            createdAt = Instant.parse("2026-04-25T01:00:00Z"),
            updatedAt = Instant.parse("2026-04-25T01:00:00Z"),
        )
        val completionEvents = mutableListOf<MedicationGroupEditorCompletionEvent>()
        var recordGenerationStarted = false

        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        } returns savedGroupUuid
        coEvery { medicationGroupRepository.getGroup(savedGroupUuid) } returns savedGroup
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { medicationLogRepository.saveNewEntries(any()) } coAnswers {
            recordGenerationStarted = true
            delay(1)
            Unit
        }
        coEvery { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) } returns Unit
        var callbackResult: CreatePastScheduledSlotRecordsResult? = null
        var callbackSavedRecordCount: Int? = null

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(),
            appTimeSource = appTimeSource,
        )
        backgroundScope.launch {
            viewModel.completionEvents.collect { completionEvents += it }
        }
        advanceUntilIdle()
        viewModel.showAddMedicationEditor()
        viewModel.updateEditingMedicineDraft { draft -> draft.copy(pillStrengthMg = "2") }
        viewModel.saveEditingMedication()
        viewModel.updateSinceDate(LocalDate.of(2026, 4, 23))
        viewModel.updateCreatePastScheduledSlotRecords(true)

        viewModel.saveGroup { saveState ->
            callbackResult = saveState.result
            callbackSavedRecordCount = saveState.savedRecordCount
        }
        runCurrent()

        assertTrue(recordGenerationStarted)
        assertEquals(
            listOf(MedicationGroupEditorCompletionEvent.SAVE_COMPLETED),
            completionEvents,
        )
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.createdPastScheduledSlotRecordCount)
        assertEquals(savedGroupUuid.toString(), viewModel.uiState.value.editingGroupId)
        assertNull(callbackSavedRecordCount)

        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.createdPastScheduledSlotRecordCount)
        assertNull(callbackResult)
        assertEquals(3, callbackSavedRecordCount)
        coVerify(exactly = 1) { medicationLogRepository.saveNewEntries(any()) }
    }

    @Test
    fun saveExistingRecordlessGroup_withCreatePastRecordsEnabled_addsPastScheduledRecords() = runTest {
        val groupUuid = UUID.fromString("cdd609a1-05bd-42f7-9ea5-8cc3e6c0c85c")
        val savedEntries = slot<Collection<MedicationLogEntryInput>>()
        val group = testMedicationGroup(groupUuid).copy(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 23),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            createdAt = Instant.parse("2026-04-20T01:00:00Z"),
            updatedAt = Instant.parse("2026-04-20T01:00:00Z"),
        )

        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = groupUuid,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        } returns groupUuid
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { medicationLogRepository.saveNewEntries(capture(savedEntries)) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleGroup(groupUuid, any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()
        viewModel.updateCreatePastScheduledSlotRecords(true)

        viewModel.saveGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isSaved)
        assertTrue(viewModel.uiState.value.createPastScheduledSlotRecords)
        assertNull(viewModel.uiState.value.createPastScheduledSlotRecordsResult)
        assertEquals(3, viewModel.uiState.value.createdPastScheduledSlotRecordCount)
        assertEquals(groupUuid.toString(), viewModel.uiState.value.editingGroupId)

        val entries = savedEntries.captured.toList()
        assertEquals(3, entries.size)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 23, 9, 0),
                LocalDateTime.of(2026, 4, 24, 9, 0),
                LocalDateTime.of(2026, 4, 25, 9, 0),
            ),
            entries.map { entry -> entry.scheduledFor },
        )
        assertEquals(
            listOf(groupUuid, groupUuid, groupUuid),
            entries.map { entry -> entry.sourceGroupUuid },
        )
        coVerify(exactly = 1) { medicationLogRepository.getEntries() }
        coVerify(exactly = 1) { medicationLogRepository.saveNewEntries(any()) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(groupUuid, any()) }
    }

    @Test
    fun saveNewGroup_withCreatePastRecordsEnabledButNoPastSlots_skipsRecordGeneration() = runTest {
        val savedGroupUuid = UUID.fromString("8b08852a-3ff8-4b52-bbd6-310fdb656b3b")

        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        } returns savedGroupUuid
        coEvery { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()
        viewModel.showAddMedicationEditor()
        viewModel.updateEditingMedicineDraft { draft -> draft.copy(pillStrengthMg = "2") }
        viewModel.saveEditingMedication()
        viewModel.updateCreatePastScheduledSlotRecords(true)

        viewModel.saveGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isSaved)
        assertTrue(viewModel.uiState.value.createPastScheduledSlotRecords)
        assertNull(viewModel.uiState.value.createPastScheduledSlotRecordsResult)
        assertNull(viewModel.uiState.value.createdPastScheduledSlotRecordCount)
        assertEquals(savedGroupUuid.toString(), viewModel.uiState.value.editingGroupId)

        coVerify(exactly = 0) { medicationLogRepository.getEntries() }
        coVerify(exactly = 0) { medicationLogRepository.saveNewEntries(any()) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) }
    }

    @Test
    fun saveNewGroup_whenCreatePastRecordsFails_marksGroupSavedAndReportsRecordFailure() = runTest {
        val savedGroupUuid = UUID.fromString("668d7a23-c755-4a20-81c9-5eaf70a96d7a")
        val savedGroup = testMedicationGroup(savedGroupUuid).copy(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 23),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            createdAt = Instant.parse("2026-04-25T01:00:00Z"),
            updatedAt = Instant.parse("2026-04-25T01:00:00Z"),
        )

        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        } returns savedGroupUuid
        coEvery { medicationGroupRepository.getGroup(savedGroupUuid) } returns savedGroup
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { medicationLogRepository.saveNewEntries(any()) } throws RuntimeException("record failed")
        coEvery { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) } returns Unit
        var callbackResult: CreatePastScheduledSlotRecordsResult? = null
        var callbackSavedRecordCount: Int? = null

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()
        viewModel.showAddMedicationEditor()
        viewModel.updateEditingMedicineDraft { draft -> draft.copy(pillStrengthMg = "2") }
        viewModel.saveEditingMedication()
        viewModel.updateSinceDate(LocalDate.of(2026, 4, 23))
        viewModel.updateCreatePastScheduledSlotRecords(true)

        viewModel.saveGroup { saveState ->
            callbackResult = saveState.result
            callbackSavedRecordCount = saveState.savedRecordCount
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(
            CreatePastScheduledSlotRecordsResult.FAILURE,
            viewModel.uiState.value.createPastScheduledSlotRecordsResult,
        )
        assertNull(viewModel.uiState.value.createdPastScheduledSlotRecordCount)
        assertEquals(savedGroupUuid.toString(), viewModel.uiState.value.editingGroupId)
        assertEquals(CreatePastScheduledSlotRecordsResult.FAILURE, callbackResult)
        assertNull(callbackSavedRecordCount)
        coVerify(exactly = 1) { medicationLogRepository.saveNewEntries(any()) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) }
    }
}

private fun testMedicationGroup(groupUuid: UUID): MedicationGroup {
    return MedicationGroup(
        uuid = groupUuid,
        name = "Group",
        colorKey = MedicationGroupColorKey.ROSE,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(9, 0)),
        ),
        medications = listOf(
            MedicationGroupMedication(
                uuid = UUID.fromString("2fd98ab6-f411-43bc-9a87-d943b42ff54b"),
                medicine = editorEstradiolMedicine,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                count = 1,
            )
        ),
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-02T00:00:00Z"),
    )
}

// Single shared estradiol medicine: log entries and group medications both
// reference the same UUID so related-entry counting/aggregation lines up the
// way the ViewModel expects.
private val editorEstradiolMedicine: Medicine = testMedicine(
    uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000001"),
    key = MedicationKey.ESTRADIOL,
    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
)
