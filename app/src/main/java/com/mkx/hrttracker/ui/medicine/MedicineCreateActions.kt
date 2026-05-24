package com.mkx.hrttracker.ui.medicine

import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicineIdentityCollisionException
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.ui.medication.MedicinePickerUiState
import com.mkx.hrttracker.ui.medication.toNewMedicineRequest
import com.mkx.hrttracker.ui.medication.validationErrorRes
import kotlinx.coroutines.CancellationException

internal sealed interface MedicineCreateResult {
    data class Success(val medicine: Medicine) : MedicineCreateResult
    data class ValidationError(@param:StringRes val messageRes: Int) : MedicineCreateResult
    data class SaveFailure(val saveResult: CreateMedicineSaveResult) : MedicineCreateResult
}

internal fun validateMedicineDraftForCreate(draft: MedicinePickerUiState): Int? {
    if (draft.catalogFilterApplicationType == MedicationApplicationType.PATCH_OFF) {
        return R.string.validation_preparation_type_required
    }
    return draft.validationErrorRes()
}

internal suspend fun createMedicineFromDraft(
    medicineRepository: MedicineRepository,
    draft: MedicinePickerUiState,
): MedicineCreateResult {
    validateMedicineDraftForCreate(draft)?.let { error ->
        return MedicineCreateResult.ValidationError(error)
    }

    return runCatching {
        val request = draft.toNewMedicineRequest()
        when (request.selectionKind) {
            MedicationSelectionKind.CATALOG -> medicineRepository.findOrCreateForCatalog(
                medicationKey = checkNotNull(request.medicationKey),
                preparation = request.preparation,
            )

            MedicationSelectionKind.CUSTOM -> medicineRepository.findOrCreateForCustom(
                customMedicationName = checkNotNull(request.customMedicationName),
                displayName = request.displayName,
                category = request.category,
                preparation = request.preparation,
                displayDoseUnit = request.displayDoseUnit,
            )
        }
    }.fold(
        onSuccess = { medicine -> MedicineCreateResult.Success(medicine) },
        onFailure = { error ->
            if (error is CancellationException) {
                throw error
            }
            MedicineCreateResult.SaveFailure(
                when (error) {
                    is MedicineIdentityCollisionException ->
                        CreateMedicineSaveResult.FAILURE_IDENTITY_COLLISION

                    else -> CreateMedicineSaveResult.FAILURE_OTHER
                },
            )
        },
    )
}
