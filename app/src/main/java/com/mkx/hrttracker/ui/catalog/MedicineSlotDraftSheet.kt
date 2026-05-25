package com.mkx.hrttracker.ui.catalog

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.ui.medication.DoseInstructionDraftUiState
import com.mkx.hrttracker.ui.medication.MedicationEditorContent
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.medication.MedicationLogAppliedAtFields
import com.mkx.hrttracker.ui.medication.inferredOrSelectedPreparationType
import com.mkx.hrttracker.ui.medication.medicationCountValidationErrorRes
import com.mkx.hrttracker.ui.medication.medicineDraftFromMedicine
import com.mkx.hrttracker.ui.medication.resolveMedicationCountTextAfterDraftChange
import com.mkx.hrttracker.ui.medication.resolvedApplicationTypeForDose
import com.mkx.hrttracker.ui.medication.resolvedMedicationCountForSave
import com.mkx.hrttracker.ui.medication.selectedMedicineValidationErrorRes
import com.mkx.hrttracker.ui.medication.stepMedicationCount
import com.mkx.hrttracker.ui.medication.toDoseInstruction
import com.mkx.hrttracker.ui.medication.validationErrorRes
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import java.time.LocalDate

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
    mode: MedicineSlotDraftMode = MedicineSlotDraftMode.GROUP_SLOT,
    onManualLogSaved: (() -> Unit) -> Unit = { consumeSavedState -> consumeSavedState() },
    onManualLogSaveFailure: () -> Unit = { },
    viewModel: MedicineSlotDraftViewModel = hiltViewModel(),
) {
    val manualLogUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isManualLogMode = mode == MedicineSlotDraftMode.MANUAL_LOG
    val isSaving = isManualLogMode && (manualLogUiState.isSaving || manualLogUiState.isSaved)
    val appLocale = rememberAppLocale()
    val today = remember { LocalDate.now() }
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)

    LaunchedEffect(isManualLogMode, manualLogUiState.isSaved) {
        if (isManualLogMode && manualLogUiState.isSaved) {
            onManualLogSaved(viewModel::consumeSavedState)
        }
    }

    LaunchedEffect(isManualLogMode, manualLogUiState.saveResult) {
        if (isManualLogMode && manualLogUiState.saveResult == MedicineSlotDraftSaveResult.FAILURE) {
            onManualLogSaveFailure()
            viewModel.consumeSaveResult()
        }
    }

    // Per-medicine remembered drafts: tapping a different card replaces the
    // medicine and starts the form fresh.
    var medicineDraft by remember(medicine.uuid) {
        mutableStateOf(
            medicineDraftFromMedicine(
                medicine = medicine,
                applicationType = initialApplicationTypeForSlotDraft(medicine),
            )
        )
    }
    var doseInstructionDraft by remember(medicine.uuid) {
        mutableStateOf(
            DoseInstructionDraftUiState(
                applicationType = initialApplicationTypeForSlotDraft(medicine),
                preparationType = medicine.preparation.type,
            )
        )
    }
    var countText by remember(medicine.uuid) { mutableStateOf("1") }
    var errorMessageRes: Int? by remember(medicine.uuid) { mutableStateOf(null) }

    MedicationEditorSheetScaffold(
        modifier = modifier,
        title = stringResource(
            if (isManualLogMode) R.string.add_entry else R.string.add_medication_to_group,
        ),
        sheetState = sheetState,
        confirmButtonText = stringResource(R.string.save),
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        fillAvailableHeight = false,
        isSaving = isSaving,
        // Identity is locked here and the dose-instruction form exposes no
        // preset chips, so the medication-editor disclaimer about preset
        // values being illustrative has nothing to caveat in this sheet.
        disclaimerKinds = emptyList(),
        onConfirm = {
            val preparationType = medicine.preparation.type
            val error = medicineDraft.selectedMedicineValidationErrorRes()
                ?: doseInstructionDraft.validationErrorRes()
                ?: medicationCountValidationErrorRes(
                    applicationType = resolvedApplicationTypeForDose(
                        preparationType = preparationType,
                        doseInstructionDraft = doseInstructionDraft,
                    ),
                    countText = countText,
                    preparationType = preparationType,
                )
            if (error != null) {
                errorMessageRes = error
                return@MedicationEditorSheetScaffold
            }
            val applicationType = resolvedApplicationTypeForDose(
                preparationType = preparationType,
                doseInstructionDraft = doseInstructionDraft,
            )
            val resolvedDose = if (applicationType == MedicationApplicationType.PATCH_OFF) {
                DoseInstruction.Noop
            } else {
                doseInstructionDraft.toDoseInstruction()
            }
            val resolvedCount = resolvedMedicationCountForSave(
                applicationType = applicationType,
                countText = countText,
                preparationType = preparationType,
            )
            val slotResult = MedicineSlotResult(
                medicineUuid = medicine.uuid,
                applicationType = applicationType,
                doseInstruction = resolvedDose,
                count = resolvedCount,
            )
            when (mode) {
                MedicineSlotDraftMode.GROUP_SLOT -> onConfirm(slotResult)
                MedicineSlotDraftMode.MANUAL_LOG -> viewModel.saveManualLog(
                    medicineUuid = medicine.uuid,
                    applicationType = applicationType,
                    doseInstruction = resolvedDose,
                    count = resolvedCount,
                )
            }
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
                    applicationType = resolvedApplicationTypeForDose(
                        preparationType = medicine.preparation.type,
                        doseInstructionDraft = doseInstructionDraft,
                    ),
                    countText = countText,
                    delta = -1,
                    preparationType = medicine.preparation.type,
                ).toString()
            },
            onIncreaseCountClick = {
                countText = stepMedicationCount(
                    applicationType = resolvedApplicationTypeForDose(
                        preparationType = medicine.preparation.type,
                        doseInstructionDraft = doseInstructionDraft,
                    ),
                    countText = countText,
                    delta = 1,
                    preparationType = medicine.preparation.type,
                ).toString()
            },
            errorMessageRes = errorMessageRes,
            isSaving = isSaving,
        )

        if (isManualLogMode) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            MedicationLogAppliedAtFields(
                appliedDate = manualLogUiState.appliedDate,
                appliedTime = manualLogUiState.appliedTime,
                appliedDateText = dateFormatter(manualLogUiState.appliedDate),
                appliedTimeText = manualLogUiState.appliedTime.format(timeFormatter),
                appliedZoneId = manualLogUiState.appliedZoneId,
                onAppliedDateChange = viewModel::updateAppliedDate,
                onAppliedTimeChange = viewModel::updateAppliedTime,
            )
        }
    }
}

enum class MedicineSlotDraftMode {
    GROUP_SLOT,
    MANUAL_LOG,
}

internal fun initialApplicationTypeForSlotDraft(
    medicine: Medicine,
): MedicationApplicationType {
    return when (medicine.preparation.type) {
        MedicinePreparationType.PATCH_OFF -> MedicationApplicationType.PATCH_OFF
        else -> MedicationApplicationType.ORAL
    }
}
