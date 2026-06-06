package com.mkx.hrttracker.ui.catalog

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.reminder.PostLogStockWarning
import com.mkx.hrttracker.ui.medication.ActualAmountRulerCard
import com.mkx.hrttracker.ui.medication.DoseInstructionDraftUiState
import com.mkx.hrttracker.ui.medication.MedicationDoseDraft
import com.mkx.hrttracker.ui.medication.MedicationDoseResetPolicy
import com.mkx.hrttracker.ui.medication.MedicationEditorContent
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.medication.MedicationLogAppliedAtFields
import com.mkx.hrttracker.ui.medication.applyMedicinePicker
import com.mkx.hrttracker.ui.medication.effectiveActualDoseAmount
import com.mkx.hrttracker.ui.medication.medicationCountValidationErrorRes
import com.mkx.hrttracker.ui.medication.medicineDraftFromMedicine
import com.mkx.hrttracker.ui.medication.resolvedApplicationTypeForDose
import com.mkx.hrttracker.ui.medication.resolvedMedicationCountForSave
import com.mkx.hrttracker.ui.medication.selectedMedicineValidationErrorRes
import com.mkx.hrttracker.ui.medication.stepMedicationCount
import com.mkx.hrttracker.ui.medication.stockMutationPreviewDoseMagnitude
import com.mkx.hrttracker.ui.medication.toDoseInstruction
import com.mkx.hrttracker.ui.medication.validationErrorRes
import com.mkx.hrttracker.ui.medication.withCountText
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import java.time.LocalDate

/**
 * Bottom sheet that starts from an existing catalog `Medicine` and captures dose details.
 *
 * Opened from: medicine-picking flows that already resolved a catalog medicine.
 * Hosted by: MedicinesScreen.
 * Produces: either a regimen `MedicineSlotResult` or a saved history `MedicationLog`;
 *   never creates a catalog medicine.
 * Identity: locked to the provided `Medicine`; only dose/log details are editable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExistingMedicineDoseSheet(
    medicine: Medicine,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onConfirm: (MedicineSlotResult) -> Unit,
    modifier: Modifier = Modifier,
    mode: MedicineSlotDraftMode = MedicineSlotDraftMode.GROUP_SLOT,
    selectedStockProjection: MedicineStockProjection? = null,
    onManualLogSaved: (PostLogStockWarning?, () -> Unit) -> Unit = { _, consumeSavedState ->
        consumeSavedState()
    },
    onManualLogSaveFailure: () -> Unit = { },
    viewModel: MedicineSlotDraftViewModel = hiltViewModel(),
) {
    val manualLogUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isManualLogMode = mode == MedicineSlotDraftMode.MANUAL_LOG
    val isSaving = isManualLogMode && (manualLogUiState.isSaving || manualLogUiState.isSaved)
    var isStockProjectionFrozen by remember(medicine.uuid) { mutableStateOf(false) }
    var frozenStockProjection: MedicineStockProjection? by remember(medicine.uuid) {
        mutableStateOf(null)
    }
    val appLocale = rememberAppLocale()
    val today = remember { LocalDate.now() }
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)

    LaunchedEffect(isManualLogMode, manualLogUiState.isSaved) {
        if (isManualLogMode && manualLogUiState.isSaved) {
            onManualLogSaved(manualLogUiState.postLogStockWarning, viewModel::consumeSavedState)
        }
    }

    LaunchedEffect(isManualLogMode, manualLogUiState.saveResult) {
        if (isManualLogMode && manualLogUiState.saveResult == MedicineSlotDraftSaveResult.FAILURE) {
            isStockProjectionFrozen = false
            frozenStockProjection = null
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
    var doseAmountDelta: Double? by remember(medicine.uuid) { mutableStateOf(null) }
    var errorMessageRes: Int? by remember(medicine.uuid) { mutableStateOf(null) }
    val resolvedApplicationType = resolvedApplicationTypeForDose(
        preparationType = medicine.preparation.type,
        doseInstructionDraft = doseInstructionDraft,
    )
    val scheduledNativeAmount = remember(medicine, doseInstructionDraft) {
        manualLogScheduledNativeAmount(
            preparation = medicine.preparation,
            doseInstruction = runCatching { doseInstructionDraft.toDoseInstruction() }.getOrNull(),
        )
    }
    val allowsActualDoseDelta = isManualLogMode &&
        manualLogAllowsActualDoseDelta(
            preparationType = medicine.preparation.type,
            applicationType = resolvedApplicationType,
        ) &&
        scheduledNativeAmount != null
    val effectiveActualAmount = scheduledNativeAmount?.let { scheduledAmount ->
        effectiveActualDoseAmount(
            scheduledAmount = scheduledAmount,
            doseAmountDelta = doseAmountDelta,
        )
    }
    val previewDoseMagnitude = remember(
        isManualLogMode,
        medicine,
        doseInstructionDraft,
        countText,
        allowsActualDoseDelta,
        effectiveActualAmount,
    ) {
        if (isManualLogMode) {
            manualLogPreviewDoseMagnitude(
                medicine = medicine,
                doseInstructionDraft = doseInstructionDraft,
                countText = countText,
                allowsActualDoseDelta = allowsActualDoseDelta,
                effectiveActualAmount = effectiveActualAmount,
            )
        } else {
            null
        }
    }
    val previewPostMutationState: ((MedicineStock) -> MedicineStockState?) =
        remember(medicine.uuid, viewModel) {
            { hypothetical: MedicineStock -> viewModel.previewStateFor(medicine.uuid, hypothetical) }
        }
    val displayedStockProjection = slotDraftStockProjectionForDisplay(
        mode = mode,
        isStockProjectionFrozen = isStockProjectionFrozen,
        selectedStockProjection = selectedStockProjection,
        frozenStockProjection = frozenStockProjection,
    )
    // The committed delta lags the ruler while it scrolls (settle-debounced), so
    // swallow Save taps until the ruler settles rather than persisting a stale
    // amount. The ruler resets this to false on dispose.
    var isActualAmountRulerScrolling by remember { mutableStateOf(false) }

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
            val applicationType = resolvedApplicationType
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
                MedicineSlotDraftMode.MANUAL_LOG -> {
                    if (isActualAmountRulerScrolling) return@MedicationEditorSheetScaffold
                    frozenStockProjection = displayedStockProjection
                    isStockProjectionFrozen = true
                    viewModel.saveManualLog(
                        medicineUuid = medicine.uuid,
                        applicationType = applicationType,
                        doseInstruction = resolvedDose,
                        count = resolvedCount,
                        doseAmountDelta = doseAmountDelta.takeIf { allowsActualDoseDelta },
                    )
                }
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
            selectedStockProjection = displayedStockProjection,
            stockMutationPreviewDoseMagnitude = previewDoseMagnitude,
            previewPostMutationState = previewPostMutationState,
            onMedicineDraftChange = { transform ->
                val reduced = MedicationDoseDraft(
                    medicineDraft = medicineDraft,
                    doseInstructionDraft = doseInstructionDraft,
                    countText = countText,
                    resolvedMedicine = medicine,
                ).applyMedicinePicker(
                    transform = transform,
                    resetPolicy = MedicationDoseResetPolicy.KEEP_EXISTING_DOSE,
                )
                medicineDraft = reduced.medicineDraft
                doseInstructionDraft = reduced.doseInstructionDraft
                countText = reduced.countText
                doseAmountDelta = null
                errorMessageRes = reduced.errorMessageRes
            },
            onDoseInstructionDraftChange = { transform ->
                doseInstructionDraft = transform(doseInstructionDraft)
                doseAmountDelta = null
                errorMessageRes = null
            },
            // Identity is locked, so re-picking from inside the sheet is a no-op.
            onOpenMedicinePicker = { },
            countText = countText,
            onCountTextChange = { value ->
                val reduced = MedicationDoseDraft(
                    medicineDraft = medicineDraft,
                    doseInstructionDraft = doseInstructionDraft,
                    countText = countText,
                    resolvedMedicine = medicine,
                ).withCountText(value)
                countText = reduced.countText
                errorMessageRes = reduced.errorMessageRes
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
            if (allowsActualDoseDelta && effectiveActualAmount != null) {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
                ActualAmountRulerCard(
                    modifier = Modifier.padding(top = 8.dp),
                    preparationType = medicine.preparation.type,
                    allowsActualDoseDelta = allowsActualDoseDelta,
                    plannedAmount = scheduledNativeAmount,
                    doseAmountDelta = doseAmountDelta,
                    isSaving = isSaving,
                    onDoseAmountDeltaChange = { doseAmountDelta = it },
                    onScrollingChange = { isActualAmountRulerScrolling = it },
                )
            }
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

internal fun slotDraftStockProjectionForDisplay(
    mode: MedicineSlotDraftMode,
    isStockProjectionFrozen: Boolean,
    selectedStockProjection: MedicineStockProjection?,
    frozenStockProjection: MedicineStockProjection?,
): MedicineStockProjection? {
    return if (mode == MedicineSlotDraftMode.MANUAL_LOG && isStockProjectionFrozen) {
        frozenStockProjection
    } else {
        selectedStockProjection
    }
}

internal fun initialApplicationTypeForSlotDraft(
    medicine: Medicine,
): MedicationApplicationType {
    return when (medicine.preparation.type) {
        MedicinePreparationType.PATCH_OFF -> MedicationApplicationType.PATCH_OFF
        else -> MedicationApplicationType.ORAL
    }
}

internal fun manualLogPreviewDoseMagnitude(
    medicine: Medicine,
    doseInstructionDraft: DoseInstructionDraftUiState,
    countText: String,
    allowsActualDoseDelta: Boolean,
    effectiveActualAmount: Double?,
): Double? {
    if (
        allowsActualDoseDelta &&
        effectiveActualAmount != null &&
        effectiveActualAmount.isFinite()
    ) {
        when (medicine.preparation) {
            is MedicinePreparation.InjectionMultiUseVial,
            is MedicinePreparation.GelContainer -> {
                return effectiveActualAmount.takeIf { it >= 0.0 }
            }

            else -> Unit
        }
    }
    return stockMutationPreviewDoseMagnitude(
        medicine = medicine,
        doseInstructionDraft = doseInstructionDraft,
        countText = countText,
    )
}

internal fun manualLogScheduledNativeAmount(
    preparation: MedicinePreparation,
    doseInstruction: DoseInstruction?,
): Double? {
    return when (preparation) {
        is MedicinePreparation.InjectionSingleUseVial ->
            if (doseInstruction == DoseInstruction.WholeUnit) {
                preparation.strengthMgPerVial
            } else {
                null
            }

        is MedicinePreparation.InjectionMultiUseVial ->
            (doseInstruction as? DoseInstruction.VolumeMl)?.valueMl

        is MedicinePreparation.GelContainer ->
            (doseInstruction as? DoseInstruction.WeightGrams)?.valueGrams

        else -> null
    }
}

internal fun manualLogAllowsActualDoseDelta(
    preparationType: MedicinePreparationType,
    applicationType: MedicationApplicationType,
): Boolean {
    // Manual logs ask for the dose directly, so multi-use vial (mL) and gel
    // container (g) already capture the actual amount — no delta stepper needed.
    // Single-use vials (ampules) have no amount field; the +/- mg delta is the
    // only way to record drawing slightly more or less than the nominal vial.
    return preparationType == MedicinePreparationType.INJECTION_SINGLE_USE_VIAL &&
        applicationType == MedicationApplicationType.INJECTION
}
