package com.mkx.hrttracker.ui.medicine

import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineIdentityCollisionException
import com.mkx.hrttracker.data.repository.MedicineLockedException
import com.mkx.hrttracker.data.repository.MedicineReferencedByActiveGroupException
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicine
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicineDetailViewModelTest {

    private val medicineRepository: MedicineRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Why this matters: archive must not silently no-op when a live group
    // still references the medicine — losing this guard would corrupt the
    // foreign-key invariant the repo defends with
    // `MedicineReferencedByActiveGroupException`. The state must expose the
    // linked groups so the UI can block the button BEFORE submitting.
    @Test
    fun archiveDisabledWhenActiveGroupsReferenceMedicine() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(testGroupReferencingMedicine(medicine = medicine)),
        )
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.archive()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.linkedActiveSlots.size)
        coVerify(exactly = 0) { medicineRepository.archive(any(), any()) }
    }

    // Why this matters: the detail screen must show the exact slot that
    // links a medicine into each group, not just the group name. Otherwise
    // two groups with different routes/doses are indistinguishable.
    @Test
    fun linkedActiveSlotsIncludeDoseAndApplicationForEachReferencingGroup() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000008")
        val medicine = testMedicine(uuid = medicineUuid)
        val oralDose = DoseInstruction.TabletFraction(numerator = 1, denominator = 2)
        val sublingualDose = DoseInstruction.TabletFraction(numerator = 1, denominator = 4)
        val morningGroup = testGroupReferencingMedicine(
            groupUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"),
            medicine = medicine,
        ).copy(
            name = "Morning",
            medications = listOf(
                testMedicationGroupMedication(
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = oralDose,
                    count = 2,
                ),
            ),
        )
        val eveningGroup = testGroupReferencingMedicine(
            groupUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"),
            medicine = medicine,
        ).copy(
            name = "Evening",
            medications = listOf(
                testMedicationGroupMedication(
                    medicine = medicine,
                    applicationType = MedicationApplicationType.SUBLINGUAL,
                    doseInstruction = sublingualDose,
                ),
            ),
        )
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(morningGroup, eveningGroup),
        )
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        val rows = viewModel.uiState.value.linkedActiveSlots
        assertEquals(2, rows.size)
        assertEquals("Evening", rows[0].group.name)
        assertEquals(sublingualDose, rows[0].doseInstruction)
        assertEquals(MedicationApplicationType.SUBLINGUAL, rows[0].applicationType)
        assertEquals("Morning", rows[1].group.name)
        assertEquals(oralDose, rows[1].doseInstruction)
        assertEquals(MedicationApplicationType.ORAL, rows[1].applicationType)
        assertEquals(2, rows[1].count)
    }

    // Why this matters: archived groups should not block archive/delete
    // affordances or appear as active links on a medicine detail page.
    @Test
    fun linkedActiveSlotsExcludeArchivedGroups() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000009")
        val medicine = testMedicine(uuid = medicineUuid)
        val activeGroup = testGroupReferencingMedicine(
            groupUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000003"),
            medicine = medicine,
        ).copy(name = "Active")
        val archivedGroup = testGroupReferencingMedicine(
            groupUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000004"),
            medicine = medicine,
            archivedAt = Instant.EPOCH,
        ).copy(name = "Archived")
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(activeGroup, archivedGroup),
        )
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        val rows = viewModel.uiState.value.linkedActiveSlots
        assertEquals(1, rows.size)
        assertEquals("Active", rows.single().group.name)
    }

    // Why this matters: one group can intentionally reference the same
    // medicine more than once, with different route/dose instructions. The
    // detail screen must preserve both slots instead of collapsing by group.
    @Test
    fun linkedActiveSlotsIncludeMultipleSlotsFromSameGroup() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000010")
        val medicine = testMedicine(uuid = medicineUuid)
        val oralDose = DoseInstruction.TabletFraction(numerator = 1, denominator = 1)
        val sublingualDose = DoseInstruction.TabletFraction(numerator = 1, denominator = 2)
        val group = testGroupReferencingMedicine(
            groupUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000005"),
            medicine = medicine,
        ).copy(
            name = "Split dose",
            medications = listOf(
                testMedicationGroupMedication(
                    medicine = medicine,
                    applicationType = MedicationApplicationType.SUBLINGUAL,
                    doseInstruction = sublingualDose,
                ),
                testMedicationGroupMedication(
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = oralDose,
                ),
            ),
        )
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        val rows = viewModel.uiState.value.linkedActiveSlots
        assertEquals(2, rows.size)
        assertEquals(MedicationApplicationType.ORAL, rows[0].applicationType)
        assertEquals(oralDose, rows[0].doseInstruction)
        assertEquals(MedicationApplicationType.SUBLINGUAL, rows[1].applicationType)
        assertEquals(sublingualDose, rows[1].doseInstruction)
        assertEquals(listOf(group.uuid, group.uuid), rows.map { it.group.uuid })
    }

    // Why this matters: when no active group blocks archive, the repo call
    // must run AND a SUCCESS event must surface so the screen can close. A
    // regression that drops the success signal would leave the user stuck.
    @Test
    fun archiveSucceedsWhenNoActiveGroupsReferenceMedicine() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.archive(medicineUuid, any()) } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.archive()
        advanceUntilIdle()

        assertEquals(
            MedicineArchiveResult.SUCCESS,
            viewModel.uiState.value.archiveResult,
        )
        coVerify(exactly = 1) { medicineRepository.archive(medicineUuid, any()) }
    }

    // Why this matters: the repo can still throw
    // MedicineReferencedByActiveGroupException because a group may have been
    // added between observation tick and submit. A regression that lets the
    // exception escape uncaught would crash the screen.
    @Test
    fun archiveFailureExceptionSurfacedAsFailure() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.archive(medicineUuid, any()) } throws
            MedicineReferencedByActiveGroupException(medicineUuid)

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.archive()
        advanceUntilIdle()

        assertEquals(
            MedicineArchiveResult.FAILURE_REFERENCED_BY_ACTIVE_GROUP,
            viewModel.uiState.value.archiveResult,
        )
    }

    // Why this matters: locked medicines (referenced by historical logs)
    // must reject `updatePreparation`. Surfacing the locked exception as a
    // typed save result lets the UI show a meaningful error instead of a
    // generic "unable to save".
    @Test
    fun updatePreparationFailsWithLockedExceptionSurfacesLockedResult() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns true
        coEvery { medicineRepository.updatePreparation(medicineUuid, any(), any()) } throws
            MedicineLockedException(medicineUuid)

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.savePreparation(MedicinePreparation.Pill(strengthMgPerTablet = 4.0))
        advanceUntilIdle()

        assertEquals(
            MedicineDetailSaveResult.FAILURE_LOCKED,
            viewModel.uiState.value.saveResult,
        )
    }

    // Why this matters: `updatePreparation` produces a new identityKey; if a
    // medicine with that identityKey already exists the repo throws. The UI
    // must distinguish this from a generic failure so the message can guide
    // the user (e.g., "merge with the existing record instead").
    @Test
    fun updatePreparationFailsWithIdentityCollisionSurfacesCollisionResult() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.updatePreparation(medicineUuid, any(), any()) } throws
            MedicineIdentityCollisionException("C|ESTRADIOL|PILL|strengthMgPerTablet=4")

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.savePreparation(MedicinePreparation.Pill(strengthMgPerTablet = 4.0))
        advanceUntilIdle()

        assertEquals(
            MedicineDetailSaveResult.FAILURE_IDENTITY_COLLISION,
            viewModel.uiState.value.saveResult,
        )
    }

    // Why this matters: a successful preparation update + display-name save
    // must both call through to the repository AND broadcast SUCCESS so the
    // screen can refresh / dismiss its editor.
    @Test
    fun savePreparationAndDisplayNameSuccessSurfacesSuccessResult() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.updatePreparation(medicineUuid, any(), any()) } just Runs
        coEvery { medicineRepository.setDisplayName(medicineUuid, "Display name", any()) } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.updateDisplayNameText("Display name")
        viewModel.saveDisplayName()
        viewModel.savePreparation(MedicinePreparation.Pill(strengthMgPerTablet = 8.0))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            medicineRepository.setDisplayName(medicineUuid, "Display name", any())
        }
        coVerify(exactly = 1) {
            medicineRepository.updatePreparation(medicineUuid, any(), any())
        }
        assertEquals(
            MedicineDetailSaveResult.SUCCESS,
            viewModel.uiState.value.saveResult,
        )
    }

    // Why this matters: a blank display-name save must clear the stored
    // value (pass null), not write the literal empty string — clearing is
    // the only way for the user to revert to the catalog/custom default
    // name once they've assigned a display name.
    @Test
    fun saveDisplayNameBlankClearsToNull() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000006")
        val medicine = testMedicine(uuid = medicineUuid, displayName = "Old name")
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.setDisplayName(medicineUuid, null, any()) } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.updateDisplayNameText("   ")
        viewModel.saveDisplayName()
        advanceUntilIdle()

        coVerify(exactly = 1) { medicineRepository.setDisplayName(medicineUuid, null, any()) }
    }

    // Why this matters: the lock state is the editor's gate-keeper — if it
    // doesn't reflect what the repo says, the user can begin editing fields
    // that will never save and that's a worse experience than disabling
    // them up-front. Locking is on by default for any medicine that has any
    // historical log entries.
    @Test
    fun lockStateReflectsRepositoryIsLocked() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000007")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns true

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLocked)
        assertNotNull(viewModel.uiState.value.medicine)
    }
}
