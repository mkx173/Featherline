package com.mkx.hrttracker.ui.medicine

import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicineIdentityCollisionException
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.ui.medication.PatchSpecKind
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CreateMedicineViewModelTest {

    private val medicineRepository: MedicineRepository = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun validCatalogDraftCreatesMedicineAndReturnsUuid() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        coEvery {
            medicineRepository.findOrCreateForCatalog(
                MedicationKey.ESTRADIOL,
                MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                any(),
            )
        } returns medicine
        val viewModel = CreateMedicineViewModel(medicineRepository)
        viewModel.updateDraft {
            it.copy(
                selectionKind = MedicationSelectionKind.CATALOG,
                medicationKey = MedicationKey.ESTRADIOL,
                pillStrengthMg = "2",
            )
        }
        var createdUuid: UUID? = null

        viewModel.create { createdUuid = it }
        advanceUntilIdle()

        assertEquals(medicine.uuid, createdUuid)
        assertEquals(CreateMedicineSaveResult.SUCCESS, viewModel.uiState.value.saveResult)
        coVerify(exactly = 1) {
            medicineRepository.findOrCreateForCatalog(
                MedicationKey.ESTRADIOL,
                MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                any(),
            )
        }
    }

    @Test
    fun validCustomDraftCreatesMedicine() = runTest {
        val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 1.5)
        val medicine = testCustomMedicine(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"),
            medicationName = "My custom med",
            category = MedicationCategory.CUSTOM,
            preparation = preparation,
            displayName = "Display",
        )
        coEvery {
            medicineRepository.findOrCreateForCustom(
                "My custom med",
                "Display",
                MedicationCategory.CUSTOM,
                preparation,
                any(),
            )
        } returns medicine
        val viewModel = CreateMedicineViewModel(medicineRepository)
        viewModel.updateDraft {
            defaultMedicineDraft(category = MedicationCategory.CUSTOM).copy(
                customMedicationName = " My custom med ",
                displayName = " Display ",
                pillStrengthMg = "1.5",
            )
        }
        var createdUuid: UUID? = null

        viewModel.create { createdUuid = it }
        advanceUntilIdle()

        assertEquals(medicine.uuid, createdUuid)
        assertEquals(CreateMedicineSaveResult.SUCCESS, viewModel.uiState.value.saveResult)
        coVerify(exactly = 1) {
            medicineRepository.findOrCreateForCustom(
                "My custom med",
                "Display",
                MedicationCategory.CUSTOM,
                preparation,
                any(),
            )
        }
    }

    @Test
    fun releaseRatePatchDraftCreatesReleaseRatePatch() = runTest {
        val preparation = MedicinePreparation.Patch(
            MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(50.0),
        )
        val medicine = testMedicine(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003"),
            key = MedicationKey.ESTRADIOL_PATCH,
            preparation = preparation,
        )
        coEvery {
            medicineRepository.findOrCreateForCatalog(
                MedicationKey.ESTRADIOL_PATCH,
                preparation,
                any(),
            )
        } returns medicine
        val viewModel = CreateMedicineViewModel(medicineRepository)
        viewModel.updateDraft {
            defaultMedicineDraft(
                applicationType = MedicationApplicationType.PATCH_ON,
            ).copy(
                medicationKey = MedicationKey.ESTRADIOL_PATCH,
                patchSpecKind = PatchSpecKind.RELEASE_RATE,
                patchTotalMg = "100",
                patchReleaseRateMcgPerDay = "50",
            )
        }
        var createdUuid: UUID? = null

        viewModel.create { createdUuid = it }
        advanceUntilIdle()

        assertEquals(medicine.uuid, createdUuid)
        assertEquals(CreateMedicineSaveResult.SUCCESS, viewModel.uiState.value.saveResult)
        coVerify(exactly = 1) {
            medicineRepository.findOrCreateForCatalog(
                MedicationKey.ESTRADIOL_PATCH,
                preparation,
                any(),
            )
        }
    }

    @Test
    fun missingRequiredPreparationFieldDoesNotCallRepositoryAndSetsError() = runTest {
        val viewModel = CreateMedicineViewModel(medicineRepository)
        var createdUuid: UUID? = null

        viewModel.create { createdUuid = it }
        advanceUntilIdle()

        assertNull(createdUuid)
        assertEquals(R.string.validation_pill_strength_required, viewModel.uiState.value.errorMessageRes)
        assertNull(viewModel.uiState.value.saveResult)
        coVerify(exactly = 0) { medicineRepository.findOrCreateForCatalog(any(), any(), any()) }
        coVerify(exactly = 0) { medicineRepository.findOrCreateForCustom(any(), any(), any(), any(), any()) }
    }

    @Test
    fun patchOffDraftDoesNotCreateMedicine() = runTest {
        val viewModel = CreateMedicineViewModel(medicineRepository)
        viewModel.updateDraft {
            it.copy(applicationType = MedicationApplicationType.PATCH_OFF)
        }
        var createdUuid: UUID? = null

        viewModel.create { createdUuid = it }
        advanceUntilIdle()

        assertNull(createdUuid)
        assertEquals(
            R.string.validation_preparation_type_required,
            viewModel.uiState.value.errorMessageRes,
        )
        coVerify(exactly = 0) { medicineRepository.findOrCreateForCatalog(any(), any(), any()) }
        coVerify(exactly = 0) { medicineRepository.findOrCreateForCustom(any(), any(), any(), any(), any()) }
    }

    @Test
    fun createIgnoresSecondClickWhileSaving() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        coEvery {
            medicineRepository.findOrCreateForCatalog(
                MedicationKey.ESTRADIOL,
                MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                any(),
            )
        } returns medicine
        val viewModel = CreateMedicineViewModel(medicineRepository)
        viewModel.updateDraft {
            it.copy(
                medicationKey = MedicationKey.ESTRADIOL,
                pillStrengthMg = "2",
            )
        }
        val createdUuids = mutableListOf<UUID>()

        viewModel.create { createdUuids += it }
        viewModel.create { createdUuids += it }
        advanceUntilIdle()

        assertEquals(listOf(medicine.uuid), createdUuids)
        coVerify(exactly = 1) {
            medicineRepository.findOrCreateForCatalog(
                MedicationKey.ESTRADIOL,
                MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                any(),
            )
        }
    }

    @Test
    fun identityCollisionDoesNotReturnUuidAndSurfacesCollisionResult() = runTest {
        coEvery {
            medicineRepository.findOrCreateForCatalog(any(), any(), any())
        } throws MedicineIdentityCollisionException("identity")
        val viewModel = CreateMedicineViewModel(medicineRepository)
        viewModel.updateDraft { it.copy(pillStrengthMg = "2") }
        var createdUuid: UUID? = null

        viewModel.create { createdUuid = it }
        advanceUntilIdle()

        assertNull(createdUuid)
        assertEquals(
            CreateMedicineSaveResult.FAILURE_IDENTITY_COLLISION,
            viewModel.uiState.value.saveResult,
        )
    }

    @Test
    fun repositoryFailureDoesNotReturnUuidAndSurfacesFailureResult() = runTest {
        coEvery {
            medicineRepository.findOrCreateForCatalog(any(), any(), any())
        } throws IllegalStateException("boom")
        val viewModel = CreateMedicineViewModel(medicineRepository)
        viewModel.updateDraft { it.copy(pillStrengthMg = "2") }
        var createdUuid: UUID? = null

        viewModel.create { createdUuid = it }
        advanceUntilIdle()

        assertNull(createdUuid)
        assertEquals(CreateMedicineSaveResult.FAILURE_OTHER, viewModel.uiState.value.saveResult)
    }

    // The sheet host shares a single VM across opens; reset must wipe both the
    // draft and any leftover save-result so the next open starts clean.
    @Test
    fun reset_clearsDraftAndPriorSaveResult() = runTest {
        coEvery {
            medicineRepository.findOrCreateForCatalog(any(), any(), any())
        } throws IllegalStateException("boom")
        val viewModel = CreateMedicineViewModel(medicineRepository)
        viewModel.updateDraft { it.copy(pillStrengthMg = "2") }
        viewModel.create { }
        advanceUntilIdle()
        assertEquals(CreateMedicineSaveResult.FAILURE_OTHER, viewModel.uiState.value.saveResult)
        assertEquals("2", viewModel.uiState.value.draft.pillStrengthMg)

        viewModel.reset()

        assertNull(viewModel.uiState.value.saveResult)
        assertNull(viewModel.uiState.value.errorMessageRes)
        assertEquals("", viewModel.uiState.value.draft.pillStrengthMg)
    }
}
