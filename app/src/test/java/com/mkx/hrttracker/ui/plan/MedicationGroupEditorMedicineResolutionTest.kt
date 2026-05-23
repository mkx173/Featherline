package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationGroupEditorMedicineResolutionTest {
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicineRepository: MedicineRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk(relaxed = true)
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
        coEvery { settingsRepository.peekNextGroupNameIndex() } returns 1
        coEvery { settingsRepository.consumeNextGroupNameIndex() } returns 1
        every { medicationGroupRepository.getCachedGroup(any()) } returns null
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicineRepository.observeAllActive() } returns flowOf(emptyList())
        every {
            context.getString(R.string.default_group_name_format, any())
        } returns "Group 1"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Fix 3 intent: the save path passes `medications.map { it.toInput() }` as
    // an argument to `medicationGroupRepository.saveGroup(...)`. Because
    // `toInput()` is `suspend` and calls `medicineRepository.findOrCreateForX`,
    // each medicine is resolved BEFORE saveGroup runs and the resolved UUID is
    // threaded into the input passed to saveGroup. A future refactor that
    // hoists the map into a non-suspend block or reorders the calls would
    // silently regress this. The test encodes that ordering invariant + the
    // identity propagation, not just "both methods were called."
    @Test
    fun saveGroup_usesPickerResolvedMedicineUuids() = runTest {
        val savedGroupUuid = UUID.fromString("1efb8bda-7c43-49d6-a3bc-2d4f1c2f4afa")
        val catalogMedicineUuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000001")
        val existingMedicineUuid = UUID.fromString("bbbb0000-0000-0000-0000-000000000002")
        val resolvedCatalogMedicine = testMedicine(
            uuid = catalogMedicineUuid,
            key = MedicationKey.ESTRADIOL_VALERATE,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val existingCustomMedicine = testCustomMedicine(
            uuid = existingMedicineUuid,
            medicationName = "My medication",
            category = MedicationCategory.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 4.0),
        )
        val savedMedications = slot<List<MedicationGroupMedicationInput>>()

        every { medicineRepository.observeAllActive() } returns MutableStateFlow(
            listOf(resolvedCatalogMedicine, existingCustomMedicine)
        )
        coEvery { medicineRepository.getByUuid(catalogMedicineUuid) } returns resolvedCatalogMedicine
        coEvery { medicineRepository.getByUuid(existingMedicineUuid) } returns existingCustomMedicine
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = any(),
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = capture(savedMedications),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        } returns savedGroupUuid

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

        // hasSaveableMedicationGroupContent requires a non-blank user-entered
        // group name (defaultGroupName alone doesn't satisfy the guard).
        // Without this, saveGroup() short-circuits and isSaved stays false.
        viewModel.updateGroupName("Resolution test group")

        // Slot #1: picker result for an existing catalog medicine.
        viewModel.showAddMedicationEditor()
        val firstSlotId = requireNotNull(viewModel.uiState.value.editingMedication).localId
        viewModel.onEditingMedicineSelected(firstSlotId, catalogMedicineUuid)
        viewModel.saveEditingMedication()

        // Slot #2: separate picker result for a different slot. The first slot
        // must not be overwritten by the second slot's result.
        viewModel.showAddMedicationEditor()
        val secondSlotId = requireNotNull(viewModel.uiState.value.editingMedication).localId
        viewModel.onEditingMedicineSelected(secondSlotId, existingMedicineUuid)
        viewModel.saveEditingMedication()

        viewModel.saveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)

        // Resolved UUIDs reach the input the repository sees — encodes that
        // "medicine resolution precedes group save" by checking the resolved
        // identity is threaded through. If toInput() ran after saveGroup, the
        // captured list would carry null medicineUuids.
        val capturedUuids = savedMedications.captured.map(MedicationGroupMedicationInput::medicineUuid)
        assertEquals(2, capturedUuids.size)
        assertTrue(
            "expected both catalog ($catalogMedicineUuid) and existing ($existingMedicineUuid) UUIDs",
            capturedUuids.containsAll(listOf(catalogMedicineUuid, existingMedicineUuid)),
        )

    }

    @Test
    fun pickerResultForSlotUpdatesOnlyThatEditingSlot() = runTest {
        val firstMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000010"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val secondMedicine = testMedicine(
            uuid = UUID.fromString("bbbb0000-0000-0000-0000-000000000020"),
            key = MedicationKey.ESTRADIOL_VALERATE,
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 20.0,
                vialVolumeMl = 5.0,
            ),
        )
        every { medicineRepository.observeAllActive() } returns MutableStateFlow(
            listOf(firstMedicine, secondMedicine)
        )
        coEvery { medicineRepository.getByUuid(firstMedicine.uuid) } returns firstMedicine
        coEvery { medicineRepository.getByUuid(secondMedicine.uuid) } returns secondMedicine

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
        val firstSlotId = requireNotNull(viewModel.uiState.value.editingMedication).localId
        viewModel.onEditingMedicineSelected(firstSlotId, firstMedicine.uuid)
        viewModel.saveEditingMedication()

        viewModel.showAddMedicationEditor()
        val secondSlotId = requireNotNull(viewModel.uiState.value.editingMedication).localId
        viewModel.onEditingMedicineSelected(secondSlotId, secondMedicine.uuid)

        val medications = viewModel.uiState.value.medications
        val editingMedication = requireNotNull(viewModel.uiState.value.editingMedication)
        assertEquals(listOf(firstMedicine.uuid), medications.mapNotNull { it.resolvedMedicine?.uuid })
        assertEquals(secondMedicine.uuid, editingMedication.resolvedMedicine?.uuid)
        assertEquals(MedicationApplicationType.INJECTION, editingMedication.medicineDraft.applicationType)
        assertEquals(
            MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
            editingMedication.doseInstructionDraft.preparationType,
        )
    }

    @Test
    fun pickerResultDoesNotRepointExistingSlot() = runTest {
        val firstMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000030"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val secondMedicine = testMedicine(
            uuid = UUID.fromString("bbbb0000-0000-0000-0000-000000000040"),
            key = MedicationKey.ESTRADIOL_VALERATE,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 4.0),
        )
        every { medicineRepository.observeAllActive() } returns MutableStateFlow(
            listOf(firstMedicine, secondMedicine)
        )
        coEvery { medicineRepository.getByUuid(firstMedicine.uuid) } returns firstMedicine
        coEvery { medicineRepository.getByUuid(secondMedicine.uuid) } returns secondMedicine

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
        val slotId = requireNotNull(viewModel.uiState.value.editingMedication).localId
        viewModel.onEditingMedicineSelected(slotId, firstMedicine.uuid)
        viewModel.saveEditingMedication()
        viewModel.showMedicationEditor(slotId)

        viewModel.onEditingMedicineSelected(slotId, secondMedicine.uuid)
        advanceUntilIdle()

        val editingMedication = requireNotNull(viewModel.uiState.value.editingMedication)
        assertEquals(firstMedicine.uuid, editingMedication.resolvedMedicine?.uuid)
    }

    // Encodes the picker-first add flow: "+ Add medication" navigates straight
    // to the manager (no empty sheet), and the host calls
    // beginAddMedicationWithMedicine on return. Failing this means a future
    // refactor reintroduced an empty editor state, or stopped resolving the
    // medicine, breaking the pre-filled-sheet promise.
    @Test
    fun beginAddMedicationWithMedicine_opensSheetPreFilled() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000050"),
            key = MedicationKey.ESTRADIOL_VALERATE,
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 20.0,
                vialVolumeMl = 5.0,
            ),
        )
        every { medicineRepository.observeAllActive() } returns MutableStateFlow(listOf(medicine))
        coEvery { medicineRepository.getByUuid(medicine.uuid) } returns medicine

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

        // Precondition: nothing open. Host generates a localId before navigating
        // to the picker, then hands it back here with the picked uuid.
        assertEquals(null, viewModel.uiState.value.editingMedication)
        val newSlotLocalId = UUID.randomUUID().toString()

        viewModel.beginAddMedicationWithMedicine(newSlotLocalId, medicine.uuid)
        advanceUntilIdle()

        val editingMedication = requireNotNull(viewModel.uiState.value.editingMedication)
        assertEquals(newSlotLocalId, editingMedication.localId)
        assertEquals(medicine.uuid, editingMedication.resolvedMedicine?.uuid)
        // The route is forced to the medicine's preparation, not left at the
        // default — this is what makes the pre-filled sheet actually usable.
        assertEquals(MedicationApplicationType.INJECTION, editingMedication.medicineDraft.applicationType)
        assertEquals(
            MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
            editingMedication.doseInstructionDraft.preparationType,
        )
    }

    // Guards against the host accidentally re-opening a fresh editor over an
    // already-open one (e.g., a stale savedStateHandle result firing after the
    // user has manually opened a different slot). The re-pick path
    // (onEditingMedicineSelected) is the right call shape there.
    @Test
    fun beginAddMedicationWithMedicine_isNoOpWhenEditorAlreadyOpen() = runTest {
        val firstMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000060"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val secondMedicine = testMedicine(
            uuid = UUID.fromString("bbbb0000-0000-0000-0000-000000000070"),
            key = MedicationKey.ESTRADIOL_VALERATE,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 4.0),
        )
        every { medicineRepository.observeAllActive() } returns MutableStateFlow(
            listOf(firstMedicine, secondMedicine)
        )
        coEvery { medicineRepository.getByUuid(firstMedicine.uuid) } returns firstMedicine
        coEvery { medicineRepository.getByUuid(secondMedicine.uuid) } returns secondMedicine

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
        val openSlotId = requireNotNull(viewModel.uiState.value.editingMedication).localId
        viewModel.onEditingMedicineSelected(openSlotId, firstMedicine.uuid)

        viewModel.beginAddMedicationWithMedicine(UUID.randomUUID().toString(), secondMedicine.uuid)
        advanceUntilIdle()

        val editingMedication = requireNotNull(viewModel.uiState.value.editingMedication)
        assertEquals(openSlotId, editingMedication.localId)
        assertEquals(firstMedicine.uuid, editingMedication.resolvedMedicine?.uuid)
    }
}
