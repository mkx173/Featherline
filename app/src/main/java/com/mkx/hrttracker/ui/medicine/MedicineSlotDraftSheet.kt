package com.mkx.hrttracker.ui.medicine

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.ui.components.MedicalDisclaimerSets
import com.mkx.hrttracker.ui.medication.DoseInstructionDraftUiState
import com.mkx.hrttracker.ui.medication.MedicationEditorContent
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.medication.MedicinePickerUiState
import com.mkx.hrttracker.ui.medication.changeApplicationType
import com.mkx.hrttracker.ui.medication.inferredOrSelectedPreparationType
import com.mkx.hrttracker.ui.medication.medicationCountValidationErrorRes
import com.mkx.hrttracker.ui.medication.medicineDraftFromMedicine
import com.mkx.hrttracker.ui.medication.resolveMedicationCountTextAfterDraftChange
import com.mkx.hrttracker.ui.medication.resolvedMedicationCountForSave
import com.mkx.hrttracker.ui.medication.selectedMedicineValidationErrorRes
import com.mkx.hrttracker.ui.medication.stepMedicationCount
import com.mkx.hrttracker.ui.medication.toDoseInstruction
import com.mkx.hrttracker.ui.medication.validationErrorRes

/**
 * Dose-instruction sheet hosted by the medicine manager when a picker tap
 * needs to return a complete slot (route + dose + count). The sheet starts
 * pre-filled from the resolved medicine and refuses to save until the draft
 * validates; only then does the result flow back to the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineSlotDraftSheet(
    medicine: Medicine,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onConfirm: (MedicineSlotResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Per-medicine remembered drafts: tapping a different card replaces the
    // medicine and starts the form fresh.
    var medicineDraft by remember(medicine.uuid) {
        mutableStateOf(
            medicineDraftFromMedicine(
                medicine = medicine,
                applicationType = MedicationApplicationType.ORAL,
            )
        )
    }
    var doseInstructionDraft by remember(medicine.uuid) {
        mutableStateOf(
            DoseInstructionDraftUiState(
                applicationType = medicineDraft.applicationType,
                preparationType = medicine.preparation.type,
            )
        )
    }
    var countText by remember(medicine.uuid) { mutableStateOf("1") }
    var errorMessageRes: Int? by remember(medicine.uuid) { mutableStateOf(null) }

    MedicationEditorSheetScaffold(
        modifier = modifier,
        title = stringResource(R.string.add_medication_to_group),
        sheetState = sheetState,
        confirmButtonText = stringResource(R.string.save),
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        fillAvailableHeight = false,
        isSaving = false,
        disclaimerKinds = MedicalDisclaimerSets.medicationEditor,
        onConfirm = {
            val error = medicineDraft.selectedMedicineValidationErrorRes()
                ?: doseInstructionDraft.validationErrorRes()
                ?: medicationCountValidationErrorRes(
                    applicationType = medicineDraft.applicationType,
                    countText = countText,
                )
            if (error != null) {
                errorMessageRes = error
                return@MedicationEditorSheetScaffold
            }
            val applicationType = medicineDraft.applicationType
            val resolvedDose = if (applicationType == MedicationApplicationType.PATCH_OFF) {
                DoseInstruction.Noop
            } else {
                doseInstructionDraft.toDoseInstruction()
            }
            onConfirm(
                MedicineSlotResult(
                    medicineUuid = medicine.uuid,
                    applicationType = applicationType,
                    doseInstruction = resolvedDose,
                    count = resolvedMedicationCountForSave(
                        applicationType = applicationType,
                        countText = countText,
                    ),
                )
            )
        },
    ) {
        MedicationEditorContent(
            medicineDraft = medicineDraft,
            doseInstructionDraft = doseInstructionDraft,
            resolvedMedicine = medicine,
            // The user can still pick between compatible routes (oral/sublingual
            // for a pill), so identity-pickers stay active — but the summary
            // header is not tappable since the manager itself is the re-pick UI.
            canEditMedicationIdentity = true,
            canRepickMedicine = false,
            onMedicineDraftChange = { transform ->
                val previousDraft = medicineDraft
                val updatedDraft = transform(previousDraft)
                medicineDraft = updatedDraft
                doseInstructionDraft = doseInstructionDraft.copy(
                    applicationType = updatedDraft.applicationType,
                    preparationType = updatedDraft.inferredOrSelectedPreparationType()
                        ?: doseInstructionDraft.preparationType,
                )
                countText = resolveMedicationCountTextAfterDraftChange(
                    previousDraft = previousDraft,
                    updatedDraft = updatedDraft,
                    currentCountText = countText,
                )
                errorMessageRes = null
            },
            onDoseInstructionDraftChange = { transform ->
                doseInstructionDraft = transform(doseInstructionDraft)
                errorMessageRes = null
            },
            // Identity is locked, so re-picking from inside the sheet is a no-op.
            onOpenMedicinePicker = { },
            countText = countText,
            onCountTextChange = { value ->
                countText = value
                errorMessageRes = null
            },
            onDecreaseCountClick = {
                countText = stepMedicationCount(
                    applicationType = medicineDraft.applicationType,
                    countText = countText,
                    delta = -1,
                ).toString()
            },
            onIncreaseCountClick = {
                countText = stepMedicationCount(
                    applicationType = medicineDraft.applicationType,
                    countText = countText,
                    delta = 1,
                ).toString()
            },
            errorMessageRes = errorMessageRes,
            isSaving = false,
        )
    }
}
