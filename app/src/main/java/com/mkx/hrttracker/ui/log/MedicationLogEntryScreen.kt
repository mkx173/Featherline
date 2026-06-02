package com.mkx.hrttracker.ui.log

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.reminder.PostLogStockWarning
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.DoseInstructionDraftUiState
import com.mkx.hrttracker.ui.medication.MedicationLogEntryEditorSheet
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import com.mkx.hrttracker.ui.medication.stockMutationPreviewDoseMagnitude
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationLogEntryScreen(
    entryIds: List<String>,
    modifier: Modifier = Modifier,
    quickLogRequest: MedicationLogEntryQuickLogRequest? = null,
    editSnapshot: MedicationLogEntryEditSnapshot? = null,
    onDismissRequest: () -> Unit,
    onEntrySaved: (PostLogStockWarning?) -> Unit,
    viewModel: MedicationLogEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSheetLockedState = rememberUpdatedState(
        uiState.isSaving || uiState.isDeleting
    )
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            value != SheetValue.Hidden || !isSheetLockedState.value
        },
    )
    BackHandler(enabled = isSheetLockedState.value) { }
    val scope = rememberCoroutineScope()

    LaunchedEffect(entryIds, quickLogRequest, editSnapshot) {
        when {
            quickLogRequest != null -> viewModel.initializeQuickLog(
                groupId = quickLogRequest.groupId,
                scheduleTimeUuid = quickLogRequest.scheduleTimeUuid,
                scheduledFor = quickLogRequest.scheduledFor,
                medicine = quickLogRequest.medicine,
                applicationType = quickLogRequest.applicationType,
                doseInstruction = quickLogRequest.doseInstruction,
                medicationCount = quickLogRequest.medicationCount,
                sourceGroupName = quickLogRequest.sourceGroupName,
                sourceGroupColorKey = quickLogRequest.sourceGroupColorKey,
                sourceGroupPreviousScheduledFor = quickLogRequest.sourceGroupPreviousScheduledFor,
                sourceGroupNextScheduledFor = quickLogRequest.sourceGroupNextScheduledFor,
            )
            else -> viewModel.initialize(
                entryIds = entryIds,
                editSnapshot = editSnapshot,
            )
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            val warning = uiState.postLogStockWarning
            hideBottomSheet(scope, sheetState) {
                viewModel.consumeSavedState()
                viewModel.consumePostLogStockWarning()
                onEntrySaved(warning)
            }
        }
    }

    val context = LocalContext.current
    val saveEntryFailureMessage = stringResource(R.string.save_entry_failure)
    val deleteEntryFailureMessage = stringResource(R.string.delete_entry_failure)
    LaunchedEffect(uiState.saveEntryResult) {
        when (uiState.saveEntryResult) {
            SaveEntryResult.FAILURE -> {
                Toast.makeText(
                    context,
                    saveEntryFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                viewModel.consumeSaveEntryResult()
            }

            null -> Unit
        }
    }

    LaunchedEffect(uiState.deleteEntryResult) {
        when (uiState.deleteEntryResult) {
            DeleteEntryResult.FAILURE -> {
                Toast.makeText(
                    context,
                    deleteEntryFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                viewModel.consumeDeleteEntryResult()
            }

            null -> Unit
        }
    }

    val crossZoneSavedFormat = stringResource(R.string.cross_timezone_saved_toast)
    LaunchedEffect(uiState.savedCrossZoneZoneText) {
        val zoneText = uiState.savedCrossZoneZoneText ?: return@LaunchedEffect
        Toast.makeText(
            context,
            crossZoneSavedFormat.format(zoneText),
            Toast.LENGTH_SHORT,
        ).show()
        viewModel.consumeCrossZoneToast()
    }

    MedicationLogEntryScreenBody(
        modifier = modifier,
        uiState = uiState,
        sheetState = sheetState,
        isSheetLocked = isSheetLockedState.value,
        onDismissRequest = onDismissRequest,
        onCloseClick = {
            hideBottomSheet(scope, sheetState, onDismissRequest)
        },
        onAppliedDateChange = viewModel::updateAppliedDate,
        onAppliedTimeChange = viewModel::updateAppliedTime,
        onDoseAmountDeltaChange = viewModel::setDoseAmountDelta,
        onDeleteClick = viewModel::deleteEntry,
        onSaveClick = viewModel::saveEntry,
        onSaveAfterFulfillmentWarningClick = viewModel::saveEntryAfterFulfillmentWarning,
        onScheduleFulfillmentWarningDismiss = viewModel::dismissScheduleFulfillmentWarning,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationLogEntryScreenBody(
    modifier: Modifier = Modifier,
    uiState: MedicationLogEntryUiState,
    sheetState: SheetState,
    isSheetLocked: Boolean,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onAppliedDateChange: (LocalDate) -> Unit,
    onAppliedTimeChange: (LocalTime) -> Unit,
    onDoseAmountDeltaChange: (Double?) -> Unit,
    onDeleteClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveAfterFulfillmentWarningClick: () -> Unit,
    onScheduleFulfillmentWarningDismiss: () -> Unit,
) {
    var isDeleteConfirmationVisible by remember(uiState.canDelete) { mutableStateOf(false) }
    // Live actual amount streamed from the ruler while the user scrubs, so the
    // stock subcard's after-mutation amount tracks the scrub in real time rather
    // than waiting for the debounced delta commit. Ignored unless the ruler is
    // active; the ruler re-emits on (re)composition, so it never goes stale.
    var liveActualAmount by remember { mutableStateOf<Double?>(null) }
    // The committed delta lags the ruler while it scrolls (settle-debounced), so
    // swallow Save taps until the ruler settles rather than persisting a stale
    // amount. The ruler resets this to false on dispose.
    var isActualAmountRulerScrolling by remember { mutableStateOf(false) }
    val previewDoseMagnitude = remember(
        uiState.isEditing,
        uiState.resolvedMedicine,
        uiState.doseInstructionDraft,
        uiState.countText,
        uiState.allowsActualDoseDelta,
        uiState.effectiveActualAmount,
        liveActualAmount,
    ) {
        medicationLogEntryPreviewDoseMagnitude(
            isEditing = uiState.isEditing,
            medicine = uiState.resolvedMedicine,
            doseInstructionDraft = uiState.doseInstructionDraft,
            countText = uiState.countText,
            allowsActualDoseDelta = uiState.allowsActualDoseDelta,
            effectiveActualAmount = if (uiState.allowsActualDoseDelta) {
                liveActualAmount ?: uiState.effectiveActualAmount
            } else {
                uiState.effectiveActualAmount
            },
        )
    }

    MedicationLogEntryEditorSheet(
        modifier = modifier,
        sheetState = sheetState,
        title = stringResource(if (uiState.isEditing) R.string.edit_entry else R.string.add_entry),
        confirmButtonText = stringResource(R.string.save),
        onDismissRequest = {
            if (!isSheetLocked) {
                onDismissRequest()
            }
        },
        onCloseClick = {
            if (!isSheetLocked) {
                onCloseClick()
            }
        },
        medicineDraft = uiState.medicineDraft,
        doseInstructionDraft = uiState.doseInstructionDraft,
        lockedMedicine = uiState.resolvedMedicine,
        selectedStockProjection = uiState.selectedStockProjection,
        stockMutationPreviewDoseMagnitude = previewDoseMagnitude,
        allowsActualDoseDelta = uiState.allowsActualDoseDelta,
        showActualDoseDeltaReadOnly = uiState.showActualDoseDeltaReadOnly,
        doseAmountDelta = uiState.doseAmountDelta,
        scheduledDoseAmount = uiState.scheduledNativeAmount,
        effectiveActualAmount = uiState.effectiveActualAmount,
        sourceGroupName = uiState.sourceGroupName,
        sourceGroupColorKey = uiState.sourceGroupColorKey,
        sourceGroupScheduledFor = uiState.scheduledFor,
        sourceGroupScheduleOffsetOutsideFulfillmentWindow = uiState.shouldWarnScheduleWillNotBeFulfilled(
            LocalDateTime.of(uiState.appliedDate, uiState.appliedTime)
        ),
        countText = uiState.countText,
        appliedDate = uiState.appliedDate,
        appliedTime = uiState.appliedTime,
        appliedZoneId = uiState.appliedZoneId,
        onAppliedDateChange = onAppliedDateChange,
        onAppliedTimeChange = onAppliedTimeChange,
        onDoseAmountDeltaChange = { delta ->
            if (!uiState.isSaving && !uiState.isDeleting && !uiState.isSaved) {
                onDoseAmountDeltaChange(delta)
            }
        },
        onLiveActualAmountChange = { liveActualAmount = it },
        onScrollingChange = { isActualAmountRulerScrolling = it },
        plannedDoseAmount = uiState.scheduledNativeAmount,
        isSaving = uiState.isSaving || uiState.isDeleting || uiState.isSaved,
        destructiveButtonText = if (uiState.canDelete) {
            stringResource(R.string.delete_entries_confirm)
        } else {
            null
        },
        onDestructiveAction = if (uiState.canDelete) {
            { isDeleteConfirmationVisible = true }
        } else {
            null
        },
        onConfirm = { if (!isActualAmountRulerScrolling) onSaveClick() }
    )

    if (isDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDeleting) {
                    isDeleteConfirmationVisible = false
                }
            },
            title = { Text(text = stringResource(R.string.delete_entry_title)) },
            text = {
                Text(
                    text = stringResource(R.string.delete_editing_entry_confirmation)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (uiState.isDeleting) return@TextButton
                        isDeleteConfirmationVisible = false
                        onDeleteClick()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete_entries_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (uiState.isDeleting) return@TextButton
                        isDeleteConfirmationVisible = false
                    },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.isScheduleFulfillmentWarningVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSaving) {
                    onScheduleFulfillmentWarningDismiss()
                }
            },
            title = {
                Text(text = stringResource(R.string.schedule_fulfillment_warning_title))
            },
            text = {
                Text(text = stringResource(R.string.schedule_fulfillment_warning_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!uiState.isSaving) onSaveAfterFulfillmentWarningClick()
                    },
                ) {
                    Text(text = stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!uiState.isSaving) onScheduleFulfillmentWarningDismiss()
                    },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

internal fun medicationLogEntryPreviewDoseMagnitude(
    isEditing: Boolean,
    medicine: Medicine?,
    doseInstructionDraft: DoseInstructionDraftUiState?,
    countText: String,
    allowsActualDoseDelta: Boolean,
    effectiveActualAmount: Double?,
): Double? {
    if (isEditing) {
        return null
    }
    if (
        allowsActualDoseDelta &&
        effectiveActualAmount != null &&
        effectiveActualAmount.isFinite()
    ) {
        when (medicine?.preparation) {
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

@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationLogEntryScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MedicationLogEntryScreenBody(
            uiState = MedicationLogEntryUiState(
                editingEntryIds = listOf("f16ec8a7-5115-410a-b12d-f376fdb6f76b"),
                medicineDraft = defaultMedicineDraft(),
                appliedDate = LocalDate.of(2026, 4, 16),
                appliedTime = LocalTime.of(21, 15),
            ),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            isSheetLocked = false,
            onDismissRequest = { },
            onCloseClick = { },
            onAppliedDateChange = { },
            onAppliedTimeChange = { },
            onDoseAmountDeltaChange = { },
            onDeleteClick = { },
            onSaveClick = { },
            onSaveAfterFulfillmentWarningClick = { },
            onScheduleFulfillmentWarningDismiss = { },
        )
    }
}
