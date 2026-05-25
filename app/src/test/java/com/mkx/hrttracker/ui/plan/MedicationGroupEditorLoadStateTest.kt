package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
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
class MedicationGroupEditorLoadStateTest {
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
        every { settingsRepository.stockIntroPromptShownFlow } returns MutableStateFlow(true)
        coEvery { settingsRepository.getCurrentSettings() } returns settingsStateFlow.value
        coEvery { settingsRepository.peekNextGroupNameIndex() } returns 1
        coEvery { settingsRepository.consumeNextGroupNameIndex() } returns 1
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicationGroupRepository.getCachedGroup(any()) } returns null
        every { medicineRepository.observeAllActive() } returns MutableStateFlow(
            listOf(editorEstradiolMedicine),
        )
        coEvery { medicineRepository.getByUuid(editorEstradiolMedicine.uuid) } returns
            editorEstradiolMedicine
        every {
            context.getString(R.string.default_group_name_format, any())
        } answers {
            val formatArgs = invocation.args[1] as Array<*>
            "Group ${formatArgs.first()}"
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun newGroupOpenUsesStablePeekedDefaultNameWithoutConsumingCounter() = runTest {
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())

        val firstOpen = newViewModel()
        advanceUntilIdle()
        val secondOpen = newViewModel()
        advanceUntilIdle()

        assertEquals("Group 1", firstOpen.uiState.value.defaultGroupName)
        assertEquals("Group 1", secondOpen.uiState.value.defaultGroupName)
        coVerify(exactly = 2) { settingsRepository.peekNextGroupNameIndex() }
        coVerify(exactly = 0) { settingsRepository.consumeNextGroupNameIndex() }
    }

    @Test
    fun savingNewGroupWithDefaultNameConsumesCounterAndNextOpenAdvances() = runTest {
        var nextIndex = 1
        val savedGroupUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001")
        val capturedName = slot<String>()
        coEvery { settingsRepository.peekNextGroupNameIndex() } coAnswers { nextIndex }
        coEvery { settingsRepository.consumeNextGroupNameIndex() } coAnswers { nextIndex++ }
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = any(),
                name = capture(capturedName),
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

        val firstOpen = newViewModel()
        advanceUntilIdle()
        addExistingMedicineSlot(firstOpen)

        firstOpen.saveGroup()
        advanceUntilIdle()

        val secondOpen = newViewModel()
        advanceUntilIdle()

        assertEquals("Group 1", capturedName.captured)
        assertEquals("Group 2", secondOpen.uiState.value.defaultGroupName)
        coVerify(exactly = 1) { settingsRepository.consumeNextGroupNameIndex() }
    }

    @Test
    fun savingNewGroupWithCustomNameDoesNotConsumeCounter() = runTest {
        var nextIndex = 1
        val savedGroupUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002")
        val capturedName = slot<String>()
        coEvery { settingsRepository.peekNextGroupNameIndex() } coAnswers { nextIndex }
        coEvery { settingsRepository.consumeNextGroupNameIndex() } coAnswers { nextIndex++ }
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = any(),
                name = capture(capturedName),
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

        val firstOpen = newViewModel()
        advanceUntilIdle()
        firstOpen.updateGroupName("Custom group")
        addExistingMedicineSlot(firstOpen)

        firstOpen.saveGroup()
        advanceUntilIdle()

        val secondOpen = newViewModel()
        advanceUntilIdle()

        assertEquals("Custom group", capturedName.captured)
        assertEquals("Group 1", secondOpen.uiState.value.defaultGroupName)
        coVerify(exactly = 0) { settingsRepository.consumeNextGroupNameIndex() }
    }

    @Test
    fun editModeDoesNotTouchGroupNameCounterOnOpenOrSave() = runTest {
        val groupUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000003")
        val group = testMedicationGroup(groupUuid)
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
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
        advanceUntilIdle()

        coVerify(exactly = 0) { settingsRepository.peekNextGroupNameIndex() }
        coVerify(exactly = 0) { settingsRepository.consumeNextGroupNameIndex() }
    }

    @Test
    fun pendingReplacementGroupId_restoredFromSavedStateHandleOnConstruction() = runTest {
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())

        val archivedGroupId = "12345678-1234-1234-1234-123456789abc"
        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf("pendingReplacementGroupId" to archivedGroupId)
            ),
            appTimeSource = appTimeSource,
        )

        // Restored synchronously at construction so the recreate-from-old surface
        // works on the first frame after process death recovery.
        assertEquals(archivedGroupId, viewModel.uiState.value.pendingReplacementGroupId)
        assertEquals(archivedGroupId, viewModel.uiState.value.recreatedFromGroupId)
    }

    @Test
    fun editingGroup_startsInLoadingStateUntilPersistedGroupLoads() = runTest {
        val groupUuid = UUID.fromString("3c13fbf7-95f5-4c20-8f2d-f902fd82afd2")
        val group = testMedicationGroup(groupUuid)
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group

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

        assertEquals(groupUuid.toString(), viewModel.uiState.value.editingGroupId)
        assertTrue(viewModel.uiState.value.isLoadingGroupForEditing)
        assertTrue(viewModel.uiState.value.medications.isEmpty())

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingGroupForEditing)
        assertEquals(group.name, viewModel.uiState.value.groupName)
        assertEquals(1, viewModel.uiState.value.medications.size)
    }

    @Test
    fun editingGroup_usesCachedGroupWithoutLoadingState() = runTest {
        val groupUuid = UUID.fromString("64df58d1-b7c7-45bf-bc6d-a7c99d4ff96f")
        val group = testMedicationGroup(groupUuid)
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))

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

        assertEquals(groupUuid.toString(), viewModel.uiState.value.editingGroupId)
        assertFalse(viewModel.uiState.value.isLoadingGroupForEditing)
        assertEquals(group.name, viewModel.uiState.value.groupName)
        assertEquals(1, viewModel.uiState.value.medications.size)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingGroupForEditing)
        assertEquals(group.name, viewModel.uiState.value.groupName)
    }

    @Test
    fun editingFreshForwardOnlyGroup_withoutLineage_allowsStartDateEdits() = runTest {
        val groupUuid = UUID.fromString("512e3056-d3dc-4972-a62d-4a3d3150fe47")
        val group = testMedicationGroup(
            groupUuid = groupUuid,
            includePastScheduledSlots = false,
        )
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))

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

        assertFalse(viewModel.uiState.value.areScheduleShapeFieldsLocked)
        assertFalse(viewModel.uiState.value.isScheduleStartDateLocked)
        assertNull(viewModel.uiState.value.recreatedFromGroupId)
        assertTrue(viewModel.uiState.value.canEditBackfillOption)

        viewModel.updateIncludePastScheduledSlots(true)

        assertEquals(true, viewModel.uiState.value.includePastScheduledSlots)
        assertTrue(viewModel.uiState.value.canCreatePastScheduledSlotRecords)

        viewModel.updateCreatePastScheduledSlotRecords(true)

        assertTrue(viewModel.uiState.value.createPastScheduledSlotRecords)

        viewModel.updateIncludePastScheduledSlots(false)

        assertEquals(false, viewModel.uiState.value.includePastScheduledSlots)
        assertFalse(viewModel.uiState.value.canCreatePastScheduledSlotRecords)
        assertFalse(viewModel.uiState.value.createPastScheduledSlotRecords)

        viewModel.updateSinceDate(LocalDate.of(2026, 3, 31))

        assertEquals(LocalDate.of(2026, 3, 31), viewModel.uiState.value.sinceDate)

        viewModel.updateSinceDate(LocalDate.of(2026, 4, 20))

        assertEquals(LocalDate.of(2026, 4, 20), viewModel.uiState.value.sinceDate)
    }

    @Test
    fun editingFreshForwardOnlyGroup_withExistingRecords_disablesBackfillOption() = runTest {
        val groupUuid = UUID.fromString("24e07d37-f507-4d95-99e6-6ab234efef24")
        val group = testMedicationGroup(
            groupUuid = groupUuid,
            includePastScheduledSlots = false,
        )
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T00:00:00Z"),
                )
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

        assertEquals(1, viewModel.uiState.value.relatedEntryCount)
        assertEquals(0, viewModel.uiState.value.plannedEntryCount)
        assertFalse(viewModel.uiState.value.canEditBackfillOption)

        viewModel.updateIncludePastScheduledSlots(true)

        assertEquals(false, viewModel.uiState.value.includePastScheduledSlots)
    }

    @Test
    fun editingRecreatedGroup_withoutOwnRecords_allowsTodayAndFutureStartDates() = runTest {
        val parentGroupUuid = UUID.fromString("11e03ed2-5569-44ea-98e0-f810dedcddde")
        val groupUuid = UUID.fromString("ad94244c-34cf-4ce2-85f2-769d63787208")
        val group = testMedicationGroup(
            groupUuid = groupUuid,
            includePastScheduledSlots = false,
            recreatedFromGroupUuid = parentGroupUuid,
        )
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))

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

        assertFalse(viewModel.uiState.value.areScheduleShapeFieldsLocked)
        assertFalse(viewModel.uiState.value.isScheduleStartDateLocked)
        assertEquals(parentGroupUuid.toString(), viewModel.uiState.value.recreatedFromGroupId)
        assertFalse(viewModel.uiState.value.canEditBackfillOption)
        assertFalse(viewModel.uiState.value.canCreatePastScheduledSlotRecords)

        viewModel.updateIncludePastScheduledSlots(true)

        assertEquals(false, viewModel.uiState.value.includePastScheduledSlots)

        viewModel.updateSinceDate(LocalDate.of(2026, 4, 20))

        assertEquals(LocalDate.of(2026, 4, 1), viewModel.uiState.value.sinceDate)

        viewModel.updateSinceDate(LocalDate.of(2026, 4, 25))

        assertEquals(LocalDate.of(2026, 4, 25), viewModel.uiState.value.sinceDate)

        viewModel.updateSinceDate(LocalDate.of(2026, 4, 26))

        assertEquals(LocalDate.of(2026, 4, 26), viewModel.uiState.value.sinceDate)
    }

    @Test
    fun editingRecreatedGroup_withOwnRecord_locksStartDate() = runTest {
        val parentGroupUuid = UUID.fromString("b19caa39-e89b-4129-aadf-98f68a57098a")
        val groupUuid = UUID.fromString("4af5e17b-19a5-4b0a-842c-533f0260b871")
        val group = testMedicationGroup(
            groupUuid = groupUuid,
            includePastScheduledSlots = false,
            recreatedFromGroupUuid = parentGroupUuid,
        )
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    medicine = editorEstradiolMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T00:00:00Z"),
                )
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

        assertEquals(1, viewModel.uiState.value.relatedEntryCount)
        assertTrue(viewModel.uiState.value.isScheduleStartDateLocked)

        viewModel.updateSinceDate(LocalDate.of(2026, 4, 26))

        assertEquals(LocalDate.of(2026, 4, 1), viewModel.uiState.value.sinceDate)
    }

    @Test
    fun editingArchivedRecreatedGroup_locksStartDate() = runTest {
        val parentGroupUuid = UUID.fromString("2216b694-fdbd-446c-8174-5b9edce24eec")
        val groupUuid = UUID.fromString("ef91bda3-eccd-4928-a670-c476be327046")
        val group = testMedicationGroup(
            groupUuid = groupUuid,
            includePastScheduledSlots = false,
            recreatedFromGroupUuid = parentGroupUuid,
        ).copy(
            archivedAt = Instant.parse("2026-04-24T00:00:00Z"),
        )
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))

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

        assertTrue(viewModel.uiState.value.isArchived)
        assertTrue(viewModel.uiState.value.isScheduleStartDateLocked)

        viewModel.updateSinceDate(LocalDate.of(2026, 4, 26))

        assertEquals(LocalDate.of(2026, 4, 1), viewModel.uiState.value.sinceDate)
    }

    @Test
    fun editingGroup_countsCurrentAndFuturePlannedSlotsOncePerScheduledTime() = runTest {
        val groupUuid = UUID.fromString("7b6ddd41-0b53-460c-a83f-780715304478")
        val group = testMedicationGroup(groupUuid = groupUuid)
        val futureSlot = LocalDateTime.of(2026, 4, 25, 11, 0)
        val currentSlot = LocalDateTime.of(2026, 4, 25, 10, 0)
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    uuid = UUID.fromString("8aebd8c3-9431-4b93-86c7-444ac75759ab"),
                    medicine = editorEstradiolMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T01:30:00Z"),
                    scheduledFor = futureSlot,
                ),
                testMedicationLogEntry(
                    uuid = UUID.fromString("0a374d56-982b-44d5-8569-0cd824d34210"),
                    medicine = editorEstradiolMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T01:31:00Z"),
                    scheduledFor = futureSlot,
                ),
                testMedicationLogEntry(
                    uuid = UUID.fromString("dcd1f6c7-b47b-41a2-9343-c7bb8a530802"),
                    medicine = editorEstradiolMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T00:00:00Z"),
                    scheduledFor = currentSlot,
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

        assertEquals(3, viewModel.uiState.value.plannedEntryCount)
        assertEquals(2, viewModel.uiState.value.currentOrFuturePlannedSlotCount)
    }

    @Test
    fun duplicateArchivedGroup_prefillsNewDraftWithNewNameAndColor() = runTest {
        val groupUuid = UUID.fromString("8c9c40fd-4487-4a90-b75a-4f04032519fd")
        val group = testMedicationGroup(
            groupUuid = groupUuid,
            notificationsEnabled = false,
        ).copy(
            archivedAt = Instant.parse("2026-04-20T00:00:00Z"),
        )
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))

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

        assertTrue(viewModel.uiState.value.isArchived)

        viewModel.duplicateArchivedGroup()

        assertNull(viewModel.uiState.value.editingGroupId)
        assertNull(viewModel.uiState.value.pendingReplacementGroupId)
        assertFalse(viewModel.uiState.value.isArchived)
        assertFalse(viewModel.uiState.value.isScheduleStartDateLocked)
        assertEquals(true, viewModel.uiState.value.includePastScheduledSlots)
        assertEquals("Group", viewModel.uiState.value.groupName)
        assertEquals(MedicationGroupColorKey.TEAL, viewModel.uiState.value.groupColorKey)
        assertEquals(false, viewModel.uiState.value.notificationsEnabled)
        assertEquals(LocalDate.of(2026, 4, 25), viewModel.uiState.value.sinceDate)
        assertEquals(1, viewModel.uiState.value.scrollToTopRequestVersion)
        assertNull(viewModel.uiState.value.medications.single().persistedMedicationId)

        viewModel.updateSinceDate(LocalDate.of(2026, 5, 1))

        assertEquals(LocalDate.of(2026, 5, 1), viewModel.uiState.value.sinceDate)
    }

    @Test
    fun newGroup_defaultsScheduleToNextHalfHour() = runTest {
        val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 25, 23, 31))
        val expectedDefaultDate = LocalDate.of(2026, 4, 26)
        val expectedDefaultTime = LocalTime.of(0, 0)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())

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

        assertEquals(expectedDefaultDate, viewModel.uiState.value.sinceDate)
        assertEquals(setOf(expectedDefaultDate.dayOfWeek), viewModel.uiState.value.weeklyDaysOfWeek)
        assertEquals(expectedDefaultTime, viewModel.uiState.value.weeklyTime)
        assertEquals(
            listOf(expectedDefaultTime),
            viewModel.uiState.value.dailyTimes.map(MedicationGroupScheduleTimeUiState::time),
        )
    }

    @Test
    fun newGroup_createPastRecordsOption_defaultsOffAndResetsWhenBackfillDisabled() = runTest {
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())

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

        assertTrue(viewModel.uiState.value.includePastScheduledSlots)
        assertTrue(viewModel.uiState.value.canCreatePastScheduledSlotRecords)
        assertFalse(viewModel.uiState.value.createPastScheduledSlotRecords)

        viewModel.updateCreatePastScheduledSlotRecords(true)

        assertTrue(viewModel.uiState.value.createPastScheduledSlotRecords)

        viewModel.updateIncludePastScheduledSlots(false)

        assertFalse(viewModel.uiState.value.includePastScheduledSlots)
        assertFalse(viewModel.uiState.value.canCreatePastScheduledSlotRecords)
        assertFalse(viewModel.uiState.value.createPastScheduledSlotRecords)

        viewModel.updateIncludePastScheduledSlots(true)

        assertTrue(viewModel.uiState.value.includePastScheduledSlots)
        assertTrue(viewModel.uiState.value.canCreatePastScheduledSlotRecords)
        assertFalse(viewModel.uiState.value.createPastScheduledSlotRecords)
    }

    @Test
    fun newGroupPastScheduleOption_resolvesDoNotShowWhenBackfillDisabled() {
        val newGroupState = MedicationGroupEditorUiState(
            includePastScheduledSlots = false,
        )

        assertFalse(newGroupState.canCreatePastScheduledSlotRecords)
        assertEquals(PastScheduleOption.DO_NOT_SHOW, resolvePastScheduleOption(newGroupState))
        assertTrue(
            shouldShowGeneratePastScheduledSlotRecordsOption(
                uiState = newGroupState,
                isNewGroupCreationFlow = true,
                isFinishingAfterSave = false,
            )
        )
    }

    @Test
    fun editingRecordlessGroupCreatePastRecordsOption_isShown() {
        val editingGroupState = MedicationGroupEditorUiState(
            editingGroupId = "a0438203-d2d6-45ac-8d55-b31040588cca",
            includePastScheduledSlots = true,
        )

        assertTrue(editingGroupState.canEditBackfillOption)
        assertTrue(editingGroupState.canCreatePastScheduledSlotRecords)
        assertTrue(
            shouldShowGeneratePastScheduledSlotRecordsOption(
                uiState = editingGroupState,
                isNewGroupCreationFlow = false,
                isFinishingAfterSave = false,
            )
        )
    }

    @Test
    fun editingFreshGroupCreatePastRecordsOption_isShownButDisabledWhenRecordsExist() {
        val editingGroupState = MedicationGroupEditorUiState(
            editingGroupId = "a0438203-d2d6-45ac-8d55-b31040588cca",
            includePastScheduledSlots = true,
            relatedEntryCount = 1,
        )

        assertFalse(editingGroupState.canEditBackfillOption)
        assertFalse(editingGroupState.canCreatePastScheduledSlotRecords)
        assertTrue(
            shouldShowGeneratePastScheduledSlotRecordsOption(
                uiState = editingGroupState,
                isNewGroupCreationFlow = false,
                isFinishingAfterSave = false,
            )
        )
    }

    @Test
    fun existingGroupSaveInProgress_disablesPastScheduleOptionsImmediately() {
        val savingExistingGroupState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            createPastScheduledSlotRecords = true,
            isSaving = true,
        )

        val selectorState = resolvePastScheduleSelectorState(
            uiState = savingExistingGroupState,
            isNewGroupCreationFlow = false,
            lockedMessage = null,
        )

        assertEquals(PastScheduleOption.SHOW_AND_GENERATE_RECORDS, selectorState.selectedOption)
        assertTrue(selectorState.showGeneratePastRecordsOption)
    }

    @Test
    fun saveCompletion_keepsPastScheduleOptionsEnabledWhileNavigationStarts() {
        val finishingExistingGroupState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            createPastScheduledSlotRecords = true,
            isSaved = true,
        )

        val selectorState = resolvePastScheduleSelectorState(
            uiState = finishingExistingGroupState,
            isNewGroupCreationFlow = false,
            lockedMessage = null,
        )

        assertEquals(PastScheduleOption.SHOW_AND_GENERATE_RECORDS, selectorState.selectedOption)
        assertTrue(selectorState.showGeneratePastRecordsOption)
        assertTrue(selectorState.enabled)
        assertTrue(selectorState.interactive)
    }

    @Test
    fun saveCompletion_disablesSaveActionWhileNavigationStarts() {
        val savedExistingGroupState = saveableMedicationGroupEditorState(
            isSaved = true,
        )

        assertTrue(
            shouldDisableMedicationGroupEditorSaveAction(
                uiState = savedExistingGroupState,
            )
        )
    }

    @Test
    fun editableExistingGroup_enablesPastScheduleOptionsOutsideActionProgress() {
        val editingGroupState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            createPastScheduledSlotRecords = true,
        )

        val selectorState = resolvePastScheduleSelectorState(
            uiState = editingGroupState,
            isNewGroupCreationFlow = false,
            lockedMessage = null,
        )

        assertEquals(PastScheduleOption.SHOW_AND_GENERATE_RECORDS, selectorState.selectedOption)
        assertTrue(selectorState.showGeneratePastRecordsOption)
        assertTrue(selectorState.enabled)
        assertTrue(selectorState.interactive)
    }

    @Test
    fun deleteOrArchiveCompletionLatch_keepsPastScheduleOptionsEnabledAfterDeletedStateIsConsumed() {
        val consumedDeletedGroupState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            createPastScheduledSlotRecords = true,
            isDeleted = false,
            isFinishingAfterDeleteOrArchive = true,
        )

        val selectorState = resolvePastScheduleSelectorState(
            uiState = consumedDeletedGroupState,
            isNewGroupCreationFlow = false,
            lockedMessage = null,
        )

        assertEquals(PastScheduleOption.SHOW_AND_GENERATE_RECORDS, selectorState.selectedOption)
        assertTrue(selectorState.showGeneratePastRecordsOption)
        assertTrue(selectorState.enabled)
        assertTrue(selectorState.interactive)
    }

    @Test
    fun deleteOrArchiveCompletion_disablesSaveActionWhileNavigationStarts() {
        val deletedGroupState = saveableMedicationGroupEditorState(
            isDeleted = true,
        )

        assertTrue(
            shouldDisableMedicationGroupEditorSaveAction(
                uiState = deletedGroupState,
            )
        )
    }

    @Test
    fun editableExistingGroup_keepsSaveActionEnabledOutsideActionProgress() {
        val editingGroupState = saveableMedicationGroupEditorState()

        assertFalse(
            shouldDisableMedicationGroupEditorSaveAction(
                uiState = editingGroupState,
            )
        )
    }

    @Test
    fun deleteOrArchiveCompletion_preservesRecordPresentationInUiState() {
        val previousState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            isDeleting = true,
            relatedEntryCount = 4,
            plannedEntryCount = 2,
            isArchived = false,
            scheduleTimeOrderError = true,
        )
        val nextState = previousState.copy(
            isDeleting = false,
            isDeleted = true,
            isFinishingAfterDeleteOrArchive = true,
            relatedEntryCount = 0,
            plannedEntryCount = 0,
            isArchived = true,
            scheduleTimeOrderError = false,
        )

        val resolvedState = resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
            previousState = previousState,
            nextState = nextState,
        )

        assertEquals(4, resolvedState.relatedEntryCount)
        assertEquals(2, resolvedState.plannedEntryCount)
        assertFalse(resolvedState.isArchived)
        assertTrue(resolvedState.scheduleTimeOrderError)
        assertTrue(resolvedState.isDeleted)
        assertTrue(resolvedState.isFinishingAfterDeleteOrArchive)
    }

    @Test
    fun editableUpdate_usesNextRecordPresentationInUiState() {
        val previousState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            relatedEntryCount = 4,
            plannedEntryCount = 2,
            isArchived = false,
            scheduleTimeOrderError = true,
        )
        val nextState = previousState.copy(
            relatedEntryCount = 0,
            plannedEntryCount = 0,
            isArchived = true,
            scheduleTimeOrderError = false,
        )

        val resolvedState = resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
            previousState = previousState,
            nextState = nextState,
        )

        assertEquals(0, resolvedState.relatedEntryCount)
        assertEquals(0, resolvedState.plannedEntryCount)
        assertTrue(resolvedState.isArchived)
        assertFalse(resolvedState.scheduleTimeOrderError)
    }

    @Test
    fun deleteOrArchiveProgress_keepsRecordPresentationOnPreviousUiState() {
        val previousState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            isArchived = false,
            relatedEntryCount = 4,
            plannedEntryCount = 2,
            scheduleTimeOrderError = true,
        )
        val nextState = previousState.copy(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            isDeleting = true,
            isArchived = true,
            relatedEntryCount = 0,
            plannedEntryCount = 0,
            scheduleTimeOrderError = false,
        )

        val resolvedState = resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
            previousState = previousState,
            nextState = nextState,
        )

        assertEquals(4, resolvedState.relatedEntryCount)
        assertEquals(2, resolvedState.plannedEntryCount)
        assertFalse(resolvedState.isArchived)
        assertTrue(resolvedState.scheduleTimeOrderError)
        assertTrue(resolvedState.isDeleting)
    }

    @Test
    fun deleteOrArchiveCompletionLatch_keepsRecordPresentationOnPreviousUiState() {
        val previousState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            isArchived = false,
            relatedEntryCount = 3,
            plannedEntryCount = 1,
            scheduleTimeOrderError = true,
        )
        val consumedDeletedGroupState = previousState.copy(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            isDeleted = false,
            isFinishingAfterDeleteOrArchive = true,
            isArchived = true,
            relatedEntryCount = 0,
            plannedEntryCount = 0,
            scheduleTimeOrderError = false,
        )

        val resolvedState = resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
            previousState = previousState,
            nextState = consumedDeletedGroupState,
        )

        assertEquals(3, resolvedState.relatedEntryCount)
        assertEquals(1, resolvedState.plannedEntryCount)
        assertFalse(resolvedState.isArchived)
        assertTrue(resolvedState.scheduleTimeOrderError)
        assertTrue(resolvedState.isFinishingAfterDeleteOrArchive)
    }

    @Test
    fun editableExistingGroup_usesNextRecordPresentationOutsideActionProgress() {
        val previousState = MedicationGroupEditorUiState(
            relatedEntryCount = 3,
            plannedEntryCount = 1,
            isArchived = false,
            scheduleTimeOrderError = true,
        )
        val liveEditingState = previousState.copy(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            isArchived = true,
            relatedEntryCount = 0,
            plannedEntryCount = 0,
            scheduleTimeOrderError = false,
        )

        val resolvedState = resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
            previousState = previousState,
            nextState = liveEditingState,
        )

        assertEquals(0, resolvedState.relatedEntryCount)
        assertEquals(0, resolvedState.plannedEntryCount)
        assertTrue(resolvedState.isArchived)
        assertFalse(resolvedState.scheduleTimeOrderError)
    }

    @Test
    fun saveCompletion_keepsRecordPresentationOnPreviousUiState() {
        val previousState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            relatedEntryCount = 2,
            plannedEntryCount = 1,
            isArchived = false,
            scheduleTimeOrderError = true,
        )
        val savedGroupState = previousState.copy(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            isSaved = true,
            relatedEntryCount = 0,
            plannedEntryCount = 0,
            scheduleTimeOrderError = false,
        )

        val resolvedState = resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
            previousState = previousState,
            nextState = savedGroupState,
        )

        assertEquals(2, resolvedState.relatedEntryCount)
        assertEquals(1, resolvedState.plannedEntryCount)
        assertFalse(resolvedState.isArchived)
        assertTrue(resolvedState.scheduleTimeOrderError)
        assertTrue(resolvedState.isSaved)
    }

    @Test
    fun stableRecordPresentation_keepsDerivedLockedAndBackfillStateStable() {
        val previousState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            relatedEntryCount = 4,
            plannedEntryCount = 2,
            isArchived = false,
            scheduleTimeOrderError = true,
        )
        val liveActionState = previousState.copy(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            isArchiving = true,
            isArchived = true,
            relatedEntryCount = 0,
            plannedEntryCount = 0,
            scheduleTimeOrderError = false,
        )

        val presentedState = resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
            previousState = previousState,
            nextState = liveActionState,
        )

        assertEquals(4, presentedState.relatedEntryCount)
        assertEquals(2, presentedState.plannedEntryCount)
        assertFalse(presentedState.isArchived)
        assertTrue(presentedState.scheduleTimeOrderError)
        assertTrue(presentedState.isLocked)
        assertFalse(presentedState.canEditBackfillOption)
    }

    @Test
    fun newGroupSaveCompletion_keepsNewGroupOnlyOptionsStableUntilNavigation() {
        val savedNewGroupState = MedicationGroupEditorUiState(
            editingGroupId = "7ab632ac-e447-4d6d-bce0-38460d9cb826",
            includePastScheduledSlots = true,
            isSaved = true,
        )

        assertTrue(savedNewGroupState.canCreatePastScheduledSlotRecords)
        assertTrue(
            shouldShowGeneratePastScheduledSlotRecordsOption(
                uiState = savedNewGroupState,
                isNewGroupCreationFlow = true,
                isFinishingAfterSave = true,
            )
        )
        assertFalse(
            shouldRenderMedicationGroupEditorAsEditing(
                uiState = savedNewGroupState,
                isNewGroupCreationFlow = true,
            )
        )
    }

    @Test
    fun newGroupSaveCompletionBeforeLatch_keepsNewGroupOnlyOptionsStableUntilNavigation() {
        val savedNewGroupState = MedicationGroupEditorUiState(
            editingGroupId = "7ab632ac-e447-4d6d-bce0-38460d9cb826",
            includePastScheduledSlots = true,
            isSaved = true,
        )

        assertFalse(
            shouldRenderMedicationGroupEditorAsEditing(
                uiState = savedNewGroupState,
                isNewGroupCreationFlow = true,
            )
        )
    }

    @Test
    fun existingRecordlessGroupSaveCompletion_showsCreatePastRecordsOption() {
        val savedExistingGroupState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            isSaved = true,
        )

        assertTrue(
            shouldShowGeneratePastScheduledSlotRecordsOption(
                uiState = savedExistingGroupState,
                isNewGroupCreationFlow = false,
                isFinishingAfterSave = true,
            )
        )
        assertTrue(
            shouldRenderMedicationGroupEditorAsEditing(
                uiState = savedExistingGroupState,
                isNewGroupCreationFlow = false,
            )
        )
    }

    @Test
    fun existingGroupSaveCompletion_withGeneratedHistorySelected_keepsEditPresentationStable() {
        val savedExistingGroupState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            createPastScheduledSlotRecords = true,
            isSaved = true,
            plannedEntryCount = 1,
        )

        assertTrue(
            shouldRenderMedicationGroupEditorAsEditing(
                uiState = savedExistingGroupState,
                isNewGroupCreationFlow = false,
            )
        )
        assertTrue(
            shouldSuppressMedicationGroupEditorLockedStateDuringGeneratedHistorySave(
                uiState = savedExistingGroupState,
                isNewGroupCreationFlow = false,
            )
        )
    }

    @Test
    fun archivedPagePresentation_clearsAfterDuplicateDraftIsCreated() {
        val duplicatedDraftState = MedicationGroupEditorUiState(
            editingGroupId = null,
            isArchived = false,
        )

        assertFalse(
            shouldUseArchivedMedicationGroupEditorPresentation(
                uiState = duplicatedDraftState,
                openedFromArchivedGroupsPage = true,
            )
        )
    }

    @Test
    fun archivedPagePresentation_isKeptForArchivedGroup() {
        val archivedGroupState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            isArchived = true,
        )

        assertTrue(
            shouldUseArchivedMedicationGroupEditorPresentation(
                uiState = archivedGroupState,
                openedFromArchivedGroupsPage = true,
            )
        )
    }

    @Test
    fun duplicatedArchivedGroupSaveCompletion_keepsAddPresentationUntilNavigation() {
        val savedDuplicatedDraftState = MedicationGroupEditorUiState(
            editingGroupId = "7ab632ac-e447-4d6d-bce0-38460d9cb826",
            isArchived = false,
            isSaved = true,
        )
        val isNewGroupCreationFlow = resolveMedicationGroupEditorIsNewGroupCreationFlow(
            uiState = savedDuplicatedDraftState,
            startedAsNewGroupCreationFlow = false,
            openedFromArchivedGroupsPage = true,
        )

        assertTrue(isNewGroupCreationFlow)
        assertFalse(
            shouldRenderMedicationGroupEditorAsEditing(
                uiState = savedDuplicatedDraftState,
                isNewGroupCreationFlow = isNewGroupCreationFlow,
            )
        )
    }

    @Test
    fun saveNavigationTarget_returnsPlanForDuplicatedDraftFromArchivedGroups() {
        val duplicatedDraftState = MedicationGroupEditorUiState(
            editingGroupId = "7ab632ac-e447-4d6d-bce0-38460d9cb826",
            isArchived = false,
            isSaved = true,
        )

        assertEquals(
            MedicationGroupEditorSaveNavigationTarget.PLAN,
            resolveMedicationGroupEditorSaveNavigationTarget(
                uiState = duplicatedDraftState,
                openedFromArchivedGroupsPage = true,
            )
        )
    }

    @Test
    fun saveNavigationTarget_returnsBackForRegularEditorSave() {
        val savedRegularState = MedicationGroupEditorUiState(
            editingGroupId = "7ab632ac-e447-4d6d-bce0-38460d9cb826",
            isArchived = false,
            isSaved = true,
        )

        assertEquals(
            MedicationGroupEditorSaveNavigationTarget.BACK,
            resolveMedicationGroupEditorSaveNavigationTarget(
                uiState = savedRegularState,
                openedFromArchivedGroupsPage = false,
            )
        )
    }

    @Test
    fun existingGroupSaveCompletionBeforeLatch_withGeneratedHistorySelected_keepsEditPresentationStable() {
        val savedExistingGroupState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            createPastScheduledSlotRecords = true,
            isSaved = true,
            plannedEntryCount = 1,
        )

        assertTrue(
            shouldSuppressMedicationGroupEditorLockedStateDuringGeneratedHistorySave(
                uiState = savedExistingGroupState,
                isNewGroupCreationFlow = false,
            )
        )
    }

    @Test
    fun existingGroupSaveInProgress_withGeneratedHistorySelected_keepsEditPresentationStable() {
        val savingExistingGroupState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            createPastScheduledSlotRecords = true,
            isSaving = true,
            plannedEntryCount = 1,
        )

        assertTrue(
            shouldRenderMedicationGroupEditorAsEditing(
                uiState = savingExistingGroupState,
                isNewGroupCreationFlow = false,
            )
        )
        assertTrue(
            shouldSuppressMedicationGroupEditorLockedStateDuringGeneratedHistorySave(
                uiState = savingExistingGroupState,
                isNewGroupCreationFlow = false,
            )
        )
    }

    @Test
    fun existingGroupSaveInProgress_withGeneratedHistorySelected_keepsDeleteRecordsSupportTextStable() {
        val savingExistingGroupState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            createPastScheduledSlotRecords = true,
            isSaving = true,
            relatedEntryCount = 1,
        )

        assertFalse(
            shouldShowMedicationGroupDeleteRelatedRecordsAsAvailable(
                uiState = savingExistingGroupState,
                isNewGroupCreationFlow = false,
            )
        )
    }

    @Test
    fun existingGroupWithRelatedRecords_showsDeleteRecordsAsAvailableOutsideGeneratedHistorySave() {
        val editingGroupState = MedicationGroupEditorUiState(
            editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
            includePastScheduledSlots = true,
            relatedEntryCount = 1,
        )

        assertTrue(
            shouldShowMedicationGroupDeleteRelatedRecordsAsAvailable(
                uiState = editingGroupState,
                isNewGroupCreationFlow = false,
            )
        )
    }

    @Test
    fun pastScheduleSection_forFreshGroupShowsOnlyWhenStartHasPastSlots() {
        val today = LocalDate.of(2026, 4, 25)
        val now = LocalDateTime.of(today, LocalTime.of(10, 0))

        assertFalse(
            shouldShowPastScheduleSection(
                uiState = MedicationGroupEditorUiState(
                    sinceDate = today.plusDays(1),
                    dailyTimes = listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0))),
                ),
                referenceTime = now,
            )
        )
        assertFalse(
            shouldShowPastScheduleSection(
                uiState = MedicationGroupEditorUiState(
                    sinceDate = today,
                    dailyTimes = listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(11, 0))),
                ),
                referenceTime = now,
            )
        )
        assertFalse(
            shouldShowPastScheduleSection(
                uiState = MedicationGroupEditorUiState(
                    sinceDate = today,
                    dailyTimes = listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(10, 0))),
                ),
                referenceTime = now,
            )
        )
        assertTrue(
            shouldShowPastScheduleSection(
                uiState = MedicationGroupEditorUiState(
                    sinceDate = today,
                    dailyTimes = listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0))),
                ),
                referenceTime = now,
            )
        )
        assertTrue(
            shouldShowPastScheduleSection(
                uiState = MedicationGroupEditorUiState(
                    sinceDate = today.minusDays(1),
                    dailyTimes = listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(11, 0))),
                ),
                referenceTime = now,
            )
        )
    }

    @Test
    fun pastScheduleSection_forRecreatedAndArchivedGroupsFollowsSpecialRules() {
        val now = LocalDateTime.of(2026, 4, 25, 10, 0)

        assertTrue(
            shouldShowPastScheduleSection(
                uiState = MedicationGroupEditorUiState(
                    editingGroupId = "5f34363b-6f89-4527-91b1-b99faf4bd8fd",
                    recreatedFromGroupId = "8d36ba04-4240-4427-972a-1ca05b7bcc69",
                    sinceDate = LocalDate.of(2026, 4, 30),
                ),
                referenceTime = now,
            )
        )
        assertTrue(
            shouldShowRecreatedPastScheduleMessage(
                MedicationGroupEditorUiState(
                    editingGroupId = "5f34363b-6f89-4527-91b1-b99faf4bd8fd",
                    recreatedFromGroupId = "8d36ba04-4240-4427-972a-1ca05b7bcc69",
                )
            )
        )
        assertFalse(
            shouldShowPastScheduleSection(
                uiState = MedicationGroupEditorUiState(
                    editingGroupId = "5f34363b-6f89-4527-91b1-b99faf4bd8fd",
                    recreatedFromGroupId = "8d36ba04-4240-4427-972a-1ca05b7bcc69",
                    isArchived = true,
                ),
                referenceTime = now,
            )
        )
    }

    @Test
    fun recreatedStartDatePastLimitMessage_requiresActiveRecreatedGroupWithoutLinkedEntries() {
        assertTrue(
            shouldShowRecreatedStartDatePastLimitMessage(
                MedicationGroupEditorUiState(
                    editingGroupId = "5f34363b-6f89-4527-91b1-b99faf4bd8fd",
                    recreatedFromGroupId = "8d36ba04-4240-4427-972a-1ca05b7bcc69",
                    relatedEntryCount = 0,
                )
            )
        )
        assertFalse(
            shouldShowRecreatedStartDatePastLimitMessage(
                MedicationGroupEditorUiState(
                    editingGroupId = "5f34363b-6f89-4527-91b1-b99faf4bd8fd",
                    recreatedFromGroupId = "8d36ba04-4240-4427-972a-1ca05b7bcc69",
                    relatedEntryCount = 1,
                )
            )
        )
        assertFalse(
            shouldShowRecreatedStartDatePastLimitMessage(
                MedicationGroupEditorUiState(
                    editingGroupId = "5f34363b-6f89-4527-91b1-b99faf4bd8fd",
                    relatedEntryCount = 0,
                )
            )
        )
        assertFalse(
            shouldShowRecreatedStartDatePastLimitMessage(
                MedicationGroupEditorUiState(
                    editingGroupId = "5f34363b-6f89-4527-91b1-b99faf4bd8fd",
                    recreatedFromGroupId = "8d36ba04-4240-4427-972a-1ca05b7bcc69",
                    relatedEntryCount = 0,
                    isArchived = true,
                )
            )
        )
    }

    private fun newViewModel(): MedicationGroupEditorViewModel {
        return MedicationGroupEditorViewModel(
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
    }

    private fun addExistingMedicineSlot(
        viewModel: MedicationGroupEditorViewModel,
    ) {
        viewModel.showAddMedicationEditor()
        val localId = requireNotNull(viewModel.uiState.value.editingMedication).localId
        viewModel.onEditingMedicineSelected(localId, editorEstradiolMedicine.uuid)
        viewModel.saveEditingMedication()
    }
}

private fun testMedicationGroup(
    groupUuid: UUID,
    notificationsEnabled: Boolean = true,
    includePastScheduledSlots: Boolean = true,
    recreatedFromGroupUuid: UUID? = null,
): MedicationGroup {
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
        notificationsEnabled = notificationsEnabled,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-02T00:00:00Z"),
        includePastScheduledSlots = includePastScheduledSlots,
        recreatedFromGroupUuid = recreatedFromGroupUuid,
    )
}

private val editorEstradiolMedicine: Medicine = testMedicine(
    uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000001"),
    key = MedicationKey.ESTRADIOL,
)

private fun saveableMedicationGroupEditorState(
    isSaved: Boolean = false,
    isDeleted: Boolean = false,
): MedicationGroupEditorUiState {
    return MedicationGroupEditorUiState(
        editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95",
        groupName = "Morning meds",
        medications = listOf(
            MedicationGroupMedicationItemUiState(
                resolvedMedicine = editorEstradiolMedicine,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            )
        ),
        isSaved = isSaved,
        isDeleted = isDeleted,
    )
}
