package com.mkx.hrttracker.ui.medicine

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.MedicalDisclaimerSets
import com.mkx.hrttracker.ui.medication.DoseInstructionForm
import com.mkx.hrttracker.ui.medication.MedicationCountTextField
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.medication.MedicationLogAppliedAtFields
import com.mkx.hrttracker.ui.medication.inferredOrSelectedPreparationType
import com.mkx.hrttracker.ui.medication.requiresEditableDoseInstructionForm
import com.mkx.hrttracker.ui.medication.resolvedApplicationTypeForDose
import com.mkx.hrttracker.ui.medication.supportsMedicationCountEditor
import com.mkx.hrttracker.ui.medication.stepMedicationCount
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMedicineSlotSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onGroupSlotResolved: (MedicineSlotResult, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    mode: NewMedicineSlotSheetMode = NewMedicineSlotSheetMode.GROUP_SLOT,
    onManualLogSaved: (() -> Unit) -> Unit = { consumeSavedState -> consumeSavedState() },
    onManualLogSaveFailure: () -> Unit = { },
    viewModel: NewMedicineSlotViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isManualLogMode = mode == NewMedicineSlotSheetMode.MANUAL_LOG
    val isSheetLocked = uiState.isSaving || uiState.isSaved
    val appLocale = rememberAppLocale()
    val today = remember { LocalDate.now() }
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val activePreparationType = uiState.medicineDraft.inferredOrSelectedPreparationType()
        ?: uiState.doseInstructionDraft.preparationType
    val applicationType = resolvedApplicationTypeForDose(
        preparationType = activePreparationType,
        doseInstructionDraft = uiState.doseInstructionDraft,
    )

    LaunchedEffect(isManualLogMode, uiState.slotResult) {
        if (!isManualLogMode) {
            uiState.slotResult?.let { slotResult ->
                onGroupSlotResolved(slotResult, viewModel::consumeSavedState)
            }
        }
    }

    LaunchedEffect(isManualLogMode, uiState.isSaved) {
        if (isManualLogMode && uiState.isSaved) {
            onManualLogSaved(viewModel::consumeSavedState)
        }
    }

    LaunchedEffect(isManualLogMode, uiState.manualLogSaveResult) {
        if (
            isManualLogMode &&
            uiState.manualLogSaveResult == MedicineSlotDraftSaveResult.FAILURE
        ) {
            onManualLogSaveFailure()
            viewModel.consumeManualLogSaveResult()
        }
    }

    MedicationEditorSheetScaffold(
        modifier = modifier,
        title = stringResource(R.string.create_medicine_title),
        sheetState = sheetState,
        confirmButtonText = stringResource(R.string.save),
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        fillAvailableHeight = true,
        isSaving = isSheetLocked,
        disclaimerKinds = MedicalDisclaimerSets.medicationEditor,
        onConfirm = {
            when (mode) {
                NewMedicineSlotSheetMode.GROUP_SLOT -> viewModel.saveGroupSlot()
                NewMedicineSlotSheetMode.MANUAL_LOG -> viewModel.saveManualLog()
            }
        },
    ) {
        CreateMedicineResultText(saveResult = uiState.createSaveResult)
        CreateMedicineForm(
            medicineDraft = uiState.medicineDraft,
            onMedicineDraftChange = viewModel::updateMedicineDraft,
            errorMessageRes = uiState.errorMessageRes,
            readOnly = isSheetLocked,
            enabled = !isSheetLocked,
        )

        if (
            requiresEditableDoseInstructionForm(activePreparationType)
        ) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            DoseInstructionForm(
                medicineDraft = uiState.medicineDraft,
                doseInstructionDraft = uiState.doseInstructionDraft,
                activePreparationType = activePreparationType,
                onDoseInstructionDraftChange = viewModel::updateDoseInstructionDraft,
                errorMessageRes = uiState.errorMessageRes,
                enabled = !isSheetLocked,
            )
        }

        if (applicationType.supportsMedicationCountEditor()) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            MedicationCountTextField(
                value = uiState.countText,
                onValueChange = viewModel::updateCountText,
                onDecreaseClick = {
                    viewModel.updateCountText(
                        stepMedicationCount(
                            applicationType = applicationType,
                            countText = uiState.countText,
                            delta = -1,
                        ).toString(),
                    )
                },
                onIncreaseClick = {
                    viewModel.updateCountText(
                        stepMedicationCount(
                            applicationType = applicationType,
                            countText = uiState.countText,
                            delta = 1,
                        ).toString(),
                    )
                },
                enabled = !isSheetLocked,
                errorMessageRes = uiState.errorMessageRes
                    ?.takeIf { it == R.string.validation_count_required },
            )
        }

        if (isManualLogMode) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            MedicationLogAppliedAtFields(
                appliedDate = uiState.appliedDate,
                appliedTime = uiState.appliedTime,
                appliedDateText = dateFormatter(uiState.appliedDate),
                appliedTimeText = uiState.appliedTime.format(timeFormatter),
                appliedZoneId = uiState.appliedZoneId,
                onAppliedDateChange = viewModel::updateAppliedDate,
                onAppliedTimeChange = viewModel::updateAppliedTime,
                enabled = !isSheetLocked,
            )
        }
    }
}

enum class NewMedicineSlotSheetMode {
    GROUP_SLOT,
    MANUAL_LOG,
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun canHideNewMedicineSlotSheet(
    value: SheetValue,
    isSlotLocked: Boolean,
    allowCompletionHide: Boolean,
): Boolean {
    return value != SheetValue.Hidden ||
        !isSlotLocked ||
        allowCompletionHide
}
