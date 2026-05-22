package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerifyOrder
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
        coEvery { settingsRepository.nextGroupNameIndex() } returns 1
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
    fun saveGroup_resolvesMedicinesBeforeSavingAndPassesResolvedUuids() = runTest {
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

        // The existing-medicine slot needs the picker to know that this
        // medicine is selectable. Without it, `selectEditingExistingMedicine`
        // is a no-op (it only operates on `activeMedicines`).
        every { medicineRepository.observeAllActive() } returns flowOf(
            listOf(existingCustomMedicine)
        )
        coEvery { medicineRepository.getByUuid(existingMedicineUuid) } returns existingCustomMedicine
        coEvery {
            medicineRepository.findOrCreateForCatalog(
                medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            )
        } returns resolvedCatalogMedicine
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
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

        // Slot #1: a brand-new catalog draft (no selectedMedicineUuid) — its
        // pill strength matches the catalog medicine the repository will
        // create. Save flow must call findOrCreateForCatalog and pass the
        // returned UUID into the input.
        viewModel.showAddMedicationEditor()
        viewModel.updateEditingMedicineDraft { draft ->
            draft.copy(
                medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                pillStrengthMg = "2",
            )
        }
        viewModel.saveEditingMedication()

        // Slot #2: an existing-medicine selection — `selectEditingExistingMed
        // icine` snaps the draft to the chosen active medicine via
        // `medicineDraftFromMedicine`, which sets `selectedMedicineUuid`. The
        // save path's `resolveMedicineForDraft` sees that and calls
        // `getByUuid` instead of find-or-create, propagating the existing
        // UUID into the input.
        viewModel.showAddMedicationEditor()
        viewModel.selectEditingExistingMedicine(existingMedicineUuid)
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

        // Ordering invariant: both resolution methods complete before saveGroup
        // is invoked. coVerifyOrder fails if saveGroup precedes either call.
        coVerifyOrder {
            medicineRepository.findOrCreateForCatalog(
                medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            )
            medicineRepository.getByUuid(existingMedicineUuid)
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
    }
}
