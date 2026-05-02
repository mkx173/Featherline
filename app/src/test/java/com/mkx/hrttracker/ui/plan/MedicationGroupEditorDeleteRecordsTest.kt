package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationGroupEditorDeleteRecordsTest {
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
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
                    details = testMedicationDetails(),
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                ),
                testMedicationLogEntry(
                    details = testMedicationDetails(),
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T00:00:00Z"),
                ),
            )
        )

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
                    details = testMedicationDetails(),
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                ),
                testMedicationLogEntry(
                    details = testMedicationDetails(),
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T00:00:00Z"),
                ),
                testMedicationLogEntry(
                    details = testMedicationDetails(),
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
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.relatedEntryCount)

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
                    details = testMedicationDetails(),
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
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
                    details = testMedicationDetails(),
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                )
            )
        )
        coEvery { medicationGroupRepository.deleteGroupAndRelatedEntries(groupUuid) } returns Unit
        every { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
        verify(exactly = 1) { medicationReminderScheduler.cancelReminder(groupUuid) }
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
        every { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
        verify(exactly = 1) { medicationReminderScheduler.cancelReminder(groupUuid) }
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
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
        verify(exactly = 0) { medicationReminderScheduler.cancelReminder(groupUuid) }
    }

    @Test
    fun archiveAndRecreateGroup_archivesOriginalAndKeepsCopiedPlanUnsavedInEditor() = runTest {
        val groupUuid = UUID.fromString("015d4963-1e43-4d6f-9e58-d390fb182a7c")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery { medicationGroupRepository.archiveGroup(groupUuid, any()) } returns Unit
        every { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
        assertNull(viewModel.uiState.value.editingGroupId)
        assertEquals(groupUuid.toString(), viewModel.uiState.value.pendingReplacementGroupId)
        assertEquals(groupUuid.toString(), viewModel.uiState.value.recreatedFromGroupId)
        assertEquals(LocalDate.of(2026, 4, 25), viewModel.uiState.value.sinceDate)
        assertTrue(viewModel.uiState.value.isScheduleStartDateLocked)
        assertFalse(viewModel.uiState.value.canEditBackfillOption)
        assertEquals(group.name, viewModel.uiState.value.groupName)
        assertEquals(false, viewModel.uiState.value.isArchived)
        assertEquals(0, viewModel.uiState.value.relatedEntryCount)
        assertEquals(0, viewModel.uiState.value.plannedEntryCount)
        assertEquals(
            ArchiveAndRecreateMedicationGroupResult.SUCCESS,
            viewModel.uiState.value.archiveAndRecreateMedicationGroupResult,
        )
        assertNull(viewModel.uiState.value.medications.single().persistedMedicationId)

        viewModel.updateSinceDate(LocalDate.of(2026, 4, 24))

        assertEquals(LocalDate.of(2026, 4, 25), viewModel.uiState.value.sinceDate)

        viewModel.updateSinceDate(LocalDate.of(2026, 4, 26))

        assertEquals(LocalDate.of(2026, 4, 25), viewModel.uiState.value.sinceDate)
        coVerify(exactly = 1) { medicationGroupRepository.archiveGroup(groupUuid, any()) }
        coVerify(exactly = 0) {
            medicationGroupRepository.saveGroup(
                uuid = any(),
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
        verify(exactly = 1) { medicationReminderScheduler.cancelReminder(groupUuid) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun saveGroup_afterArchiveAndRecreate_savesDraftAsReplacement() = runTest {
        val groupUuid = UUID.fromString("0e093bec-554e-46c3-91df-cb90e0e09cdb")
        val savedGroupUuid = UUID.fromString("cb37018a-4569-4ee8-8e1d-ecdbd9b47e1b")
        val group = testMedicationGroup(groupUuid)
        val savedMedications = slot<List<MedicationGroupMedicationInput>>()

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        coEvery { medicationGroupRepository.archiveGroup(groupUuid, any()) } returns Unit
        every { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit
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
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.archiveAndRecreateGroup()
        advanceUntilIdle()

        viewModel.saveGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(savedGroupUuid.toString(), viewModel.uiState.value.editingGroupId)
        assertNull(viewModel.uiState.value.pendingReplacementGroupId)
        assertEquals(groupUuid.toString(), viewModel.uiState.value.recreatedFromGroupId)
        assertEquals(true, viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = capture(savedMedications),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = groupUuid,
                now = any(),
            )
        }
        assertNull(savedMedications.captured.single().uuid)
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) }
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
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()
        viewModel.showAddMedicationEditor()
        viewModel.updateEditingMedicationDraft { draft -> draft.copy(doseMg = "2") }
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
                details = testMedicationDetails(),
                count = 1,
            )
        ),
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-02T00:00:00Z"),
    )
}

private fun testMedicationDetails(): MedicationDetails {
    return MedicationDetails(
        category = MedicationKey.ESTRADIOL.category,
        applicationType = MedicationApplicationType.ORAL,
        selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL),
        dose = MedicationDose.MgAsMedicine(2.0),
    )
}
