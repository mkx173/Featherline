package com.mkx.hrttracker.ui.catalog

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.reminder.PostLogStockWarning
import com.mkx.hrttracker.ui.medication.ActualAmountRulerCard
import com.mkx.hrttracker.ui.components.MedicalDisclaimerSets
import com.mkx.hrttracker.ui.medication.DoseInstructionForm
import com.mkx.hrttracker.ui.medication.MedicationCountTextField
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.medication.MedicationLogAppliedAtFields
import com.mkx.hrttracker.ui.medication.doseInstructionHasTextField
import com.mkx.hrttracker.ui.medication.exceedsDoseWarningThreshold
import com.mkx.hrttracker.ui.medication.inferredOrSelectedPreparationType
import com.mkx.hrttracker.ui.medication.parseMedicationCountText
import com.mkx.hrttracker.ui.medication.requiresEditableDoseInstructionForm
import com.mkx.hrttracker.ui.medication.resolvedApplicationTypeForDose
import com.mkx.hrttracker.ui.medication.stepMedicationCount
import com.mkx.hrttracker.ui.medication.supportsMedicationCountEditor
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import java.time.LocalDate

/**
 * Bottom sheet that creates a catalog `Medicine` and then captures dose details.
 *
 * Opened from: the medicine manager and onboarding create-then-dose flows.
 * Hosted by: MedicinesScreen / OnboardingScreen.
 * Produces: a catalog `Medicine` first, then either a regimen
 *   `MedicineSlotResult` or a saved history `MedicationLog`.
 * Identity: fully editable while creating the catalog medicine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMedicineThenDoseSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onGroupSlotResolved: (MedicineSlotResult, java.util.UUID?, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    mode: CreateMedicineThenDoseSheetMode = CreateMedicineThenDoseSheetMode.GROUP_SLOT,
    onManualLogSaved: (PostLogStockWarning?, java.util.UUID?, () -> Unit) -> Unit = { _, _, consumeSavedState ->
        consumeSavedState()
    },
    onManualLogSaveFailure: () -> Unit = { },
    viewModel: NewMedicineSlotViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isManualLogMode = mode == CreateMedicineThenDoseSheetMode.MANUAL_LOG
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

    val context = LocalContext.current
    val alreadyExistsMessage = stringResource(R.string.medicine_already_exists)
    val createFailureMessage = stringResource(R.string.medicine_save_failure)
    LaunchedEffect(uiState.createSaveResult) {
        val message = when (uiState.createSaveResult) {
            CreateMedicineSaveResult.FAILURE_IDENTITY_COLLISION -> alreadyExistsMessage
            CreateMedicineSaveResult.FAILURE_OTHER -> createFailureMessage
            CreateMedicineSaveResult.SUCCESS,
            null -> null
        }
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeCreateSaveResult()
        }
    }

    LaunchedEffect(isManualLogMode, uiState.slotResult) {
        if (!isManualLogMode) {
            uiState.slotResult?.let { slotResult ->
                onGroupSlotResolved(
                    slotResult,
                    uiState.createdMedicineUuid,
                    viewModel::consumeSavedState,
                )
            }
        }
    }

    LaunchedEffect(isManualLogMode, uiState.isSaved) {
        if (isManualLogMode && uiState.isSaved) {
            onManualLogSaved(
                uiState.postLogStockWarning,
                uiState.createdMedicineUuid,
                viewModel::consumeSavedState,
            )
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

    // The committed delta lags the ruler while it scrolls (settle-debounced), so
    // swallow Save taps until the ruler settles rather than persisting a stale
    // amount. The ruler resets this to false on dispose.
    var isActualAmountRulerScrolling by remember { mutableStateOf(false) }

    MedicationEditorSheetScaffold(
        modifier = modifier,
        title = stringResource(
            if (isManualLogMode) R.string.add_entry else R.string.create_medicine_title,
        ),
        sheetState = sheetState,
        confirmButtonText = stringResource(R.string.save),
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        fillAvailableHeight = true,
        isSaving = isSheetLocked,
        disclaimerKinds = MedicalDisclaimerSets.medicationEditor,
        onConfirm = {
            when (mode) {
                CreateMedicineThenDoseSheetMode.GROUP_SLOT -> viewModel.saveGroupSlot()
                CreateMedicineThenDoseSheetMode.MANUAL_LOG ->
                    if (!isActualAmountRulerScrolling) viewModel.saveManualLog()
            }
        },
    ) {
        // The dose instruction form may render a text field (volumeMl /
        // weightGrams) just below the create-medicine fields. When it does,
        // share a FocusRequester so the create form's last IME Next jumps
        // into that field instead of dismissing the keyboard.
        val doseFollowUpRequester = remember { FocusRequester() }
        val extendImeChainIntoDoseForm = doseInstructionHasTextField(activePreparationType)
        // This sheet has no medicine summary card, so the high-dose warning that
        // normally rides the card's trailing slot is surfaced on the strength
        // field instead. Gated on the dose instruction form being present.
        val showStrengthDoseWarning = requiresEditableDoseInstructionForm(activePreparationType) &&
            uiState.medicineDraft.exceedsDoseWarningThreshold(
                uiState.doseInstructionDraft,
                parseMedicationCountText(uiState.countText),
            )
        // Controls stay visually enabled while a save is in flight; the VM
        // ignores draft mutations during the lock, text fields stay read-
        // only to avoid input flicker, and the sheet's dismissal lock keeps
        // the form in view until ROOM finishes.
        CreateMedicineForm(
            medicineDraft = uiState.medicineDraft,
            onMedicineDraftChange = viewModel::updateMedicineDraft,
            errorMessageRes = uiState.errorMessageRes,
            readOnly = isSheetLocked,
            followUpFocusRequester = doseFollowUpRequester.takeIf { extendImeChainIntoDoseForm },
            showStrengthDoseWarning = showStrengthDoseWarning,
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
                textFieldFocusRequester = doseFollowUpRequester.takeIf { extendImeChainIntoDoseForm },
            )
        }

        if (applicationType.supportsMedicationCountEditor(activePreparationType)) {
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
                            preparationType = activePreparationType,
                        ).toString(),
                    )
                },
                onIncreaseClick = {
                    viewModel.updateCountText(
                        stepMedicationCount(
                            applicationType = applicationType,
                            countText = uiState.countText,
                            delta = 1,
                            preparationType = activePreparationType,
                        ).toString(),
                    )
                },
                errorMessageRes = uiState.errorMessageRes
                    ?.takeIf { it == R.string.validation_count_required },
            )
        }

        if (isManualLogMode) {
            if (uiState.allowsActualDoseDelta && uiState.effectiveActualAmount != null) {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
                ActualAmountRulerCard(
                    preparationType = activePreparationType,
                    allowsActualDoseDelta = uiState.allowsActualDoseDelta,
                    plannedAmount = uiState.scheduledNativeAmount,
                    doseAmountDelta = uiState.doseAmountDelta,
                    isSaving = isSheetLocked,
                    onDoseAmountDeltaChange = { viewModel.setDoseAmountDelta(it) },
                    onScrollingChange = { isActualAmountRulerScrolling = it },
                )
            }
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            MedicationLogAppliedAtFields(
                appliedDate = uiState.appliedDate,
                appliedTime = uiState.appliedTime,
                appliedDateText = dateFormatter(uiState.appliedDate),
                appliedTimeText = uiState.appliedTime.format(timeFormatter),
                appliedZoneId = uiState.appliedZoneId,
                onAppliedDateChange = viewModel::updateAppliedDate,
                onAppliedTimeChange = viewModel::updateAppliedTime,
            )
        }
    }
}

enum class CreateMedicineThenDoseSheetMode {
    GROUP_SLOT,
    MANUAL_LOG,
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun canHideCreateMedicineThenDoseSheet(
    value: SheetValue,
    isSlotLocked: Boolean,
    allowCompletionHide: Boolean,
): Boolean {
    return value != SheetValue.Hidden ||
        !isSlotLocked ||
        allowCompletionHide
}
