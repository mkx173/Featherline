package com.mkx.hrttracker.ui.catalog

import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicineIdentityCollisionException
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationForm
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.ui.medication.changeForm
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MedicineCreateActionsTest {
    private val medicineRepository: MedicineRepository = mockk()

    @Test
    fun createMedicineFromDraft_validCatalogDraftReturnsCreatedMedicine() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000101"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        coEvery {
            medicineRepository.findOrCreateForCatalog(
                MedicationKey.ESTRADIOL,
                MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                any(),
            )
        } coAnswers {
            val now = invocation.args[2] as Instant
            medicine.copy(createdAt = now, updatedAt = now)
        }

        val result = createMedicineFromDraft(
            medicineRepository = medicineRepository,
            draft = defaultMedicineDraft().copy(
                selectionKind = MedicationSelectionKind.CATALOG,
                medicationKey = MedicationKey.ESTRADIOL,
                pillStrengthMg = "2",
            ),
        )

        val success = result as MedicineCreateResult.Success
        assertEquals(medicine.uuid, success.medicine.uuid)
        assertTrue(success.createdNew)
        coVerify(exactly = 1) {
            medicineRepository.findOrCreateForCatalog(
                MedicationKey.ESTRADIOL,
                MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                any(),
            )
        }
    }

    @Test
    fun createMedicineFromDraft_activeExistingCatalogDraftReturnsNotCreated() = runTest {
        val existing = testMedicine(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000102"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        coEvery {
            medicineRepository.findOrCreateForCatalog(
                MedicationKey.ESTRADIOL,
                MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                any(),
            )
        } returns existing

        val result = createMedicineFromDraft(
            medicineRepository = medicineRepository,
            draft = defaultMedicineDraft().copy(
                selectionKind = MedicationSelectionKind.CATALOG,
                medicationKey = MedicationKey.ESTRADIOL,
                pillStrengthMg = "2",
            ),
        )

        assertEquals(
            MedicineCreateResult.Success(
                medicine = existing,
                createdNew = false,
            ),
            result,
        )
    }

    @Test
    fun createMedicineFromDraft_patchWithoutRequiredFieldReturnsValidationErrorWithoutRepositoryCall() =
        runTest {
            val result = createMedicineFromDraft(
                medicineRepository = medicineRepository,
                draft = defaultMedicineDraft().changeForm(MedicinePreparationForm.PATCH),
            )

            assertEquals(
                MedicineCreateResult.ValidationError(R.string.validation_patch_total_required),
                result,
            )
            coVerify(exactly = 0) { medicineRepository.findOrCreateForCatalog(any(), any(), any()) }
            coVerify(exactly = 0) {
                medicineRepository.findOrCreateForCustom(any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun createMedicineFromDraft_identityCollisionMapsToCollisionFailure() = runTest {
        coEvery {
            medicineRepository.findOrCreateForCatalog(any(), any(), any())
        } throws MedicineIdentityCollisionException("identity")

        val result = createMedicineFromDraft(
            medicineRepository = medicineRepository,
            draft = defaultMedicineDraft().copy(
                pillStrengthMg = "2",
            ),
        )

        assertSame(
            CreateMedicineSaveResult.FAILURE_IDENTITY_COLLISION,
            (result as MedicineCreateResult.SaveFailure).saveResult,
        )
    }

    @Test
    fun createMedicineFromDraft_repositoryCancellationIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")
        coEvery {
            medicineRepository.findOrCreateForCatalog(any(), any(), any())
        } throws cancellation

        try {
            createMedicineFromDraft(
                medicineRepository = medicineRepository,
                draft = defaultMedicineDraft().copy(
                    pillStrengthMg = "2",
                ),
            )
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertSame(cancellation, exception)
        }
    }
}
