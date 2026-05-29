package com.mkx.hrttracker.ui.catalog

import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineIdentityCollisionException
import com.mkx.hrttracker.data.repository.MedicineLockedException
import com.mkx.hrttracker.data.repository.MedicineReferencedByActiveGroupException
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.StockReceived
import com.mkx.hrttracker.data.repository.StockRecount
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicineDetailViewModelTest {

    private val medicineRepository: MedicineRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val stockRepository: MedicineStockRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { stockRepository.observeProjections() } returns flowOf(emptyList())
        every { stockRepository.getCachedProjection(any()) } returns null
        every { medicineRepository.getCachedActiveMedicine(any()) } returns null
        every { medicationGroupRepository.getCachedGroups() } returns null
        every { settingsRepository.settingsState } returns MutableStateFlow(SettingsState())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun detailStateUsesMedicineByUuidInsteadOfActiveAndArchivedLists() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000020")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        assertEquals(medicine, viewModel.uiState.value.medicine)
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
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(testGroupReferencingMedicine(medicine = medicine)),
        )
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
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
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(morningGroup, eveningGroup),
        )
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
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
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(activeGroup, archivedGroup),
        )
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
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
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
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
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.archive(medicineUuid, any()) } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
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
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.archive(medicineUuid, any()) } throws
            MedicineReferencedByActiveGroupException(medicineUuid)

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
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
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns true
        coEvery {
            medicineRepository.updatePreparation(medicineUuid, any(), any(), any())
        } throws MedicineLockedException(medicineUuid)

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
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

    @Test
    fun savePreparation_acceptsCapsulePreparation() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000009")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery {
            medicineRepository.updatePreparation(medicineUuid, any(), any(), any())
        } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.savePreparation(MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            medicineRepository.updatePreparation(
                medicineUuid,
                MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
                null,
                any(),
            )
        }
    }

    // Why this matters: `updatePreparation` produces a new identityKey; if a
    // medicine with that identityKey already exists the repo throws. The UI
    // must distinguish this from a generic failure so the message can guide
    // the user (e.g., "merge with the existing record instead").
    @Test
    fun updatePreparationFailsWithIdentityCollisionSurfacesCollisionResult() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery {
            medicineRepository.updatePreparation(medicineUuid, any(), any(), any())
        } throws MedicineIdentityCollisionException("C|ESTRADIOL|PILL|strengthMgPerTablet=4")

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
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
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery {
            medicineRepository.updatePreparation(medicineUuid, any(), any(), any())
        } just Runs
        coEvery { medicineRepository.setDisplayName(medicineUuid, "Display name", any()) } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
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
            medicineRepository.updatePreparation(medicineUuid, any(), any(), any())
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
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.setDisplayName(medicineUuid, null, any()) } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
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
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns true

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLocked)
        assertNotNull(viewModel.uiState.value.medicine)
    }

    @Test
    fun stockProjectionForCurrentMedicineIsExposed() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000011")
        val medicine = testMedicine(uuid = medicineUuid)
        val projection = MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = 1.0,
            totalStockUnits = 30.0,
            runway = RunwayProjection.Days(days = 30, lastFulfillable = LocalDate.of(2026, 1, 31)),
            intervalDays = null,
            maxPerAdministration = 1.0,
            state = MedicineStockState.HEALTHY,
        )
        val otherProjection = projection.copy(
            medicine = testMedicine(uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000012")),
        )
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { stockRepository.observeProjections() } returns flowOf(listOf(otherProjection, projection))
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        assertEquals(projection, viewModel.uiState.value.stockProjection)
    }

    @Test
    fun disableTrackingClosesConfirmationBeforeRepositoryMutationCanUpdateStock() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000021")
        val trackedMedicine = testMedicine(
            uuid = medicineUuid,
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 10.0,
                unitsLastTotal = 10.0,
            ),
        )
        val untrackedMedicine = trackedMedicine.copy(stock = MedicineStock())
        val stockProjections = MutableStateFlow(
            listOf(stockProjection(trackedMedicine, totalStockUnits = 10.0)),
        )
        lateinit var viewModel: MedicineDetailViewModel
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(trackedMedicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { stockRepository.observeProjections() } returns stockProjections
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.disableTracking(medicineUuid, any()) } coAnswers {
            assertEquals(false, viewModel.uiState.value.showDisableConfirmation)
            stockProjections.value = listOf(
                stockProjection(
                    medicine = untrackedMedicine,
                    totalStockUnits = 0.0,
                    state = MedicineStockState.UNTRACKED,
                ),
            )
        }

        viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.openDisableConfirmation()
        assertEquals(true, viewModel.uiState.value.showDisableConfirmation)

        viewModel.confirmDisableTracking()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.showDisableConfirmation)
        assertEquals(false, viewModel.uiState.value.stockProjection?.medicine?.stock?.trackingEnabled)
    }

    @Test
    fun archiveKeepsStockProjectionWhileRepositoryEmitsArchivedStateBeforeNavigation() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000022")
        val medicine = testMedicine(
            uuid = medicineUuid,
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 10.0,
                unitsLastTotal = 10.0,
            ),
        )
        val archivedMedicine = medicine.copy(
            archivedAt = Instant.parse("2026-01-02T00:00:00Z"),
            stock = MedicineStock(),
        )
        val projection = stockProjection(medicine, totalStockUnits = 10.0)
        val medicineFlow = MutableStateFlow<Medicine?>(medicine)
        val stockProjections = MutableStateFlow(listOf(projection))
        val allowArchiveToReturn = CompletableDeferred<Unit>()
        lateinit var viewModel: MedicineDetailViewModel
        every { medicineRepository.observeByUuid(medicineUuid) } returns medicineFlow
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { stockRepository.observeProjections() } returns stockProjections
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.archive(medicineUuid, any()) } coAnswers {
            medicineFlow.value = archivedMedicine
            stockProjections.value = emptyList()
            allowArchiveToReturn.await()
        }

        viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.archive()
        advanceUntilIdle()

        assertEquals(projection, viewModel.uiState.value.stockProjection)
        assertEquals(null, viewModel.uiState.value.archiveResult)

        allowArchiveToReturn.complete(Unit)
        advanceUntilIdle()

        assertEquals(MedicineArchiveResult.SUCCESS, viewModel.uiState.value.archiveResult)
        assertEquals(projection, viewModel.uiState.value.stockProjection)
    }

    @Test
    fun optInReceivedSubmissionEnablesTrackingInsteadOfApplyingReceived() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000013")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery {
            medicineRepository.enableTracking(
                medicineUuid,
                60.0,
                null,
                60.0,
                any(),
            )
        } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        viewModel.openOptIn()
        assertEquals(true, viewModel.uiState.value.showAdjustSheet)
        assertEquals(AdjustSheetTab.RECEIVED, viewModel.uiState.value.adjustSheetActiveTab)
        assertEquals(true, viewModel.uiState.value.pendingEnableTracking)

        viewModel.submitReceived(StockReceived(unitsReceived = 60.0))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            medicineRepository.enableTracking(
                medicineUuid,
                60.0,
                null,
                60.0,
                any(),
            )
        }
        coVerify(exactly = 0) { medicineRepository.applyReceived(any(), any(), any()) }
        assertEquals(false, viewModel.uiState.value.showAdjustSheet)
        assertEquals(false, viewModel.uiState.value.pendingEnableTracking)
    }

    @Test
    fun optInReceivedSubmissionAddsToPreservedDisabledStock() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000014")
        val medicine = testMedicine(
            uuid = medicineUuid,
            stock = MedicineStock(
                trackingEnabled = false,
                unitsRemaining = 60.0,
                unitsLastTotal = 60.0,
            ),
        )
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { stockRepository.observeProjections() } returns flowOf(
            listOf(
                MedicineStockProjection(
                    medicine = medicine,
                    dosesPerDayMagnitude = 0.0,
                    totalStockUnits = 0.0,
                    runway = RunwayProjection.NoSchedule,
                    intervalDays = null,
                    maxPerAdministration = 0.0,
                    state = MedicineStockState.UNTRACKED,
                )
            )
        )
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery {
            medicineRepository.enableTracking(
                medicineUuid,
                65.0,
                null,
                65.0,
                any(),
            )
        } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        viewModel.openOptIn()
        viewModel.submitReceived(StockReceived(unitsReceived = 5.0))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            medicineRepository.enableTracking(
                medicineUuid,
                65.0,
                null,
                65.0,
                any(),
            )
        }
    }

    @Test
    fun receivedStockMutationFailureSurfacesFailureAndKeepsAdjustSheetOpen() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000023")
        val medicine = testMedicine(
            uuid = medicineUuid,
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 10.0,
                unitsLastTotal = 10.0,
            ),
        )
        val failure = IllegalStateException("stock write failed")
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.applyReceived(medicineUuid, any(), any()) } throws failure

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.openAdjustSheet()

        viewModel.submitReceived(StockReceived(unitsReceived = 5.0))
        advanceUntilIdle()

        assertEquals(
            MedicineStockMutationResult.FAILURE,
            viewModel.uiState.value.stockMutationResult,
        )
        assertEquals(true, viewModel.uiState.value.showAdjustSheet)
    }

    @Test
    fun receivedStockMutationIgnoresSecondSubmitWhileFirstSubmitIsInFlight() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000024")
        val medicine = testMedicine(
            uuid = medicineUuid,
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 10.0,
                unitsLastTotal = 10.0,
            ),
        )
        val mutationStarted = CompletableDeferred<Unit>()
        val finishMutation = CompletableDeferred<Unit>()
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.applyReceived(medicineUuid, any(), any()) } coAnswers {
            mutationStarted.complete(Unit)
            finishMutation.await()
        }

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()
        viewModel.openAdjustSheet()

        viewModel.submitReceived(StockReceived(unitsReceived = 5.0))
        advanceUntilIdle()
        mutationStarted.await()
        viewModel.submitReceived(StockReceived(unitsReceived = 5.0))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            medicineRepository.applyReceived(medicineUuid, StockReceived(unitsReceived = 5.0), any())
        }
        assertEquals(true, viewModel.uiState.value.showAdjustSheet)

        finishMutation.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.showAdjustSheet)
    }

    @Test
    fun receivedStockMutationIsIgnoredAfterAdjustSheetStartsClosing() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000025")
        val medicine = testMedicine(
            uuid = medicineUuid,
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 10.0,
                unitsLastTotal = 10.0,
            ),
        )
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.applyReceived(medicineUuid, any(), any()) } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        viewModel.openAdjustSheet()
        viewModel.closeAdjustSheet()
        viewModel.submitReceived(StockReceived(unitsReceived = 5.0))
        advanceUntilIdle()

        coVerify(exactly = 0) { medicineRepository.applyReceived(any(), any(), any()) }
        assertEquals(false, viewModel.uiState.value.showAdjustSheet)
    }

    @Test
    fun recountStockMutationIsIgnoredAfterAdjustSheetStartsClosing() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000026")
        val medicine = testMedicine(
            uuid = medicineUuid,
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 10.0,
                unitsLastTotal = 10.0,
            ),
        )
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery { medicineRepository.applyRecount(medicineUuid, any(), any()) } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        viewModel.openAdjustSheet()
        viewModel.closeAdjustSheet()
        viewModel.submitRecount(StockRecount(unitsRemaining = 5.0))
        advanceUntilIdle()

        coVerify(exactly = 0) { medicineRepository.applyRecount(any(), any(), any()) }
        assertEquals(false, viewModel.uiState.value.showAdjustSheet)
    }

    @Test
    fun optInRecountSubmissionDoesNotMutateStock() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000015")
        val medicine = testMedicine(uuid = medicineUuid)
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        viewModel.openOptIn()
        viewModel.submitRecount(StockRecount(unitsRemaining = 10.0))
        advanceUntilIdle()

        coVerify(exactly = 0) { medicineRepository.applyRecount(any(), any(), any()) }
        coVerify(exactly = 0) { medicineRepository.enableTracking(any(), any(), any(), any(), any()) }
        assertEquals(false, viewModel.uiState.value.showAdjustSheet)
        assertEquals(false, viewModel.uiState.value.pendingEnableTracking)
    }

    @Test
    fun containerOptInReceivedSubmissionDelegatesAutoOpenToRepository() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000017")
        val medicine = testMedicine(
            uuid = medicineUuid,
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 10.0,
                vialVolumeMl = 5.0,
            ),
            stock = MedicineStock(
                trackingEnabled = false,
                unitsRemaining = 1.0,
                openContainerAmount = 2.0,
            ),
        )
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { stockRepository.observeProjections() } returns flowOf(
            listOf(
                MedicineStockProjection(
                    medicine = medicine,
                    dosesPerDayMagnitude = 0.0,
                    totalStockUnits = 0.0,
                    runway = RunwayProjection.NoSchedule,
                    intervalDays = null,
                    maxPerAdministration = 0.0,
                    state = MedicineStockState.UNTRACKED,
                )
            )
        )
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false
        coEvery {
            medicineRepository.enableTracking(
                medicineUuid,
                3.0,
                null,
                3.0,
                any(),
            )
        } just Runs

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString()),
            ),
        )
        advanceUntilIdle()

        viewModel.openOptIn()
        viewModel.submitReceived(StockReceived(unitsReceived = 2.0))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            medicineRepository.enableTracking(
                medicineUuid,
                3.0,
                null,
                3.0,
                any(),
            )
        }
    }

    @Test
    fun openOptInRouteFlagAutoOpensReceivedSheetForUntrackedMedicine() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000018")
        val medicine = testMedicine(uuid = medicineUuid)
        val savedStateHandle = SavedStateHandle(
            mapOf(
                MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString(),
                MedicineDetailViewModel.OPEN_OPT_IN_ARG to true,
            ),
        )
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { stockRepository.observeProjections() } returns flowOf(
            listOf(
                MedicineStockProjection(
                    medicine = medicine,
                    dosesPerDayMagnitude = 0.0,
                    totalStockUnits = 0.0,
                    runway = RunwayProjection.NoSchedule,
                    intervalDays = null,
                    maxPerAdministration = 0.0,
                    state = MedicineStockState.UNTRACKED,
                )
            )
        )
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = savedStateHandle,
        )
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.showAdjustSheet)
        assertEquals(AdjustSheetTab.RECEIVED, viewModel.uiState.value.adjustSheetActiveTab)
        assertEquals(true, viewModel.uiState.value.pendingEnableTracking)
        assertEquals(false, savedStateHandle[MedicineDetailViewModel.OPEN_OPT_IN_ARG])
    }

    @Test
    fun openOptInRouteFlagDoesNotAutoOpenForTrackedMedicine() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000019")
        val medicine = testMedicine(
            uuid = medicineUuid,
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 10.0,
                unitsLastTotal = 10.0,
            ),
        )
        val savedStateHandle = SavedStateHandle(
            mapOf(
                MedicineDetailViewModel.MEDICINE_ID_ARG to medicineUuid.toString(),
                MedicineDetailViewModel.OPEN_OPT_IN_ARG to true,
            ),
        )
        every { medicineRepository.observeByUuid(medicineUuid) } returns flowOf(medicine)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { stockRepository.observeProjections() } returns flowOf(
            listOf(
                MedicineStockProjection(
                    medicine = medicine,
                    dosesPerDayMagnitude = 0.0,
                    totalStockUnits = 10.0,
                    runway = RunwayProjection.NoSchedule,
                    intervalDays = null,
                    maxPerAdministration = 0.0,
                    state = MedicineStockState.NO_RUNWAY,
                )
            )
        )
        coEvery { medicineRepository.isLocked(medicineUuid) } returns false

        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = savedStateHandle,
        )
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.showAdjustSheet)
        assertEquals(false, viewModel.uiState.value.pendingEnableTracking)
        assertEquals(false, savedStateHandle[MedicineDetailViewModel.OPEN_OPT_IN_ARG])
    }

    private fun stockProjection(
        medicine: Medicine,
        totalStockUnits: Double,
        state: MedicineStockState = MedicineStockState.NO_RUNWAY,
    ): MedicineStockProjection {
        return MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = 0.0,
            totalStockUnits = totalStockUnits,
            runway = RunwayProjection.NoSchedule,
            intervalDays = null,
            maxPerAdministration = 0.0,
            state = state,
        )
    }
}
