package com.mkx.hrttracker.ui.medication

import androidx.annotation.StringRes
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparationType

/** Whether a picker preparation change rebuilds the dose draft or only updates its preparation type. */
enum class MedicationDoseResetPolicy {
    RESET_ON_PREPARATION_CHANGE,
    KEEP_EXISTING_DOSE,
}

data class MedicationDoseDraft(
    val medicineDraft: MedicinePickerUiState,
    val doseInstructionDraft: DoseInstructionDraftUiState,
    val countText: String,
    val resolvedMedicine: Medicine? = null,
    @param:StringRes val errorMessageRes: Int? = null,
)

fun MedicationDoseDraft.applyMedicinePicker(
    transform: (MedicinePickerUiState) -> MedicinePickerUiState,
    resetPolicy: MedicationDoseResetPolicy = MedicationDoseResetPolicy.RESET_ON_PREPARATION_CHANGE,
): MedicationDoseDraft {
    val updatedDraft = transform(medicineDraft)
    val updatedPreparationType = updatedDraft.inferredOrSelectedPreparationType()
    val shouldResetDoseDraft =
        resetPolicy == MedicationDoseResetPolicy.RESET_ON_PREPARATION_CHANGE &&
            medicineDraft.inferredOrSelectedPreparationType() != updatedPreparationType
    return copy(
        medicineDraft = updatedDraft,
        resolvedMedicine = if (updatedDraft.selectedMedicineUuid == null) null else resolvedMedicine,
        doseInstructionDraft = if (shouldResetDoseDraft) {
            updatedDraft.toDoseInstructionDraft()
        } else {
            doseInstructionDraft.copy(
                preparationType = updatedPreparationType ?: doseInstructionDraft.preparationType,
            )
        },
        countText = resolveMedicationCountTextAfterDraftChange(
            previousDraft = medicineDraft,
            updatedDraft = updatedDraft,
            currentCountText = countText,
        ),
        errorMessageRes = null,
    )
}

fun MedicationDoseDraft.withCountText(countText: String): MedicationDoseDraft =
    copy(countText = sanitizeMedicationCountText(countText), errorMessageRes = null)

fun MedicationDoseDraft.validatedWith(
    preparationType: MedicinePreparationType,
    validateMedicineDraft: (MedicinePickerUiState) -> Int?,
): MedicationDoseDraft {
    val applicationType: MedicationApplicationType =
        resolvedApplicationTypeForDose(
            preparationType = preparationType,
            doseInstructionDraft = doseInstructionDraft,
        )
    val error = validateMedicineDraft(medicineDraft)
        ?: doseInstructionDraft.validationErrorRes()
        ?: medicationCountValidationErrorRes(
            applicationType = applicationType,
            countText = countText,
            preparationType = preparationType,
        )
    return copy(errorMessageRes = error)
}
