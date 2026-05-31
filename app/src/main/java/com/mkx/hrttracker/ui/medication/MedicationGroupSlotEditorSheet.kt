package com.mkx.hrttracker.ui.medication

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.time.Instant
import java.util.UUID

// Sheet entry-point map (see notes/superpowers/plans/2026-05-31-medication-sheet-refactor.md):
//   CreateMedicineSheet            — creates a catalog Medicine only. (keep)
//   CreateMedicineThenDoseSheet    — creates Medicine, then returns slot OR saves log. (keep)
//   ExistingMedicineDoseSheet      — existing Medicine, then returns slot OR saves log. (keep)
//   MedicationGroupSlotEditorSheet — edits a regimen slot in the group editor. (keep)
//   MedicationLogEntryEditorSheet  — edits/creates a history MedicationLog entry. (keep)
//   MedicationLogEntryScreenBody   — private host adapter for MedicationLogEntryEditorSheet. (keep, private)

// ---------------------------------------------------------------------------
// Group slot sheet entry point.
// ---------------------------------------------------------------------------

/**
 * Bottom sheet that edits a medication slot inside a regimen group.
 *
 * Opened from: the medication group editor when adding or editing a group slot.
 * Hosted by: MedicationGroupEditorScreen.
 * Produces: a regimen `MedicineSlotResult`; never creates a catalog [Medicine]
 *   and never writes a history `MedicationLog`.
 * Identity: editable only when [canEditMedicationIdentity] is true; otherwise
 *   locked to [resolvedMedicine].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationGroupSlotEditorSheet(
    modifier: Modifier = Modifier,
    title: String,
    sheetState: SheetState,
    confirmButtonText: String,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    medicineDraft: MedicinePickerUiState,
    doseInstructionDraft: DoseInstructionDraftUiState?,
    resolvedMedicine: Medicine,
    canEditMedicationIdentity: Boolean,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    onDoseInstructionDraftChange: ((DoseInstructionDraftUiState) -> DoseInstructionDraftUiState) -> Unit,
    onOpenMedicinePicker: () -> Unit,
    countText: String,
    onCountTextChange: (String) -> Unit,
    onDecreaseCountClick: () -> Unit,
    onIncreaseCountClick: () -> Unit,
    errorMessageRes: Int? = null,
    isSaving: Boolean = false,
    onConfirm: () -> Unit,
) {
    MedicationEditorSheetScaffold(
        modifier = modifier,
        title = title,
        sheetState = sheetState,
        confirmButtonText = confirmButtonText,
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        // Slot editor sizes to its content. Fields collapse based on whether
        // a medicine is resolved and the route's dose form, so a fixed
        // fill-max-size hole below the buttons looks broken.
        fillAvailableHeight = false,
        isSaving = isSaving,
        // MedicationEditorContent exposes no preset-dose chips, so the
        // medication-editor disclaimer about preset values being illustrative
        // has nothing to caveat in this sheet (cf. ExistingMedicineDoseSheet).
        disclaimerKinds = emptyList(),
        onConfirm = onConfirm,
    ) {
        MedicationEditorContent(
            medicineDraft = medicineDraft,
            doseInstructionDraft = doseInstructionDraft,
            resolvedMedicine = resolvedMedicine,
            canEditMedicationIdentity = canEditMedicationIdentity,
            onMedicineDraftChange = onMedicineDraftChange,
            onDoseInstructionDraftChange = onDoseInstructionDraftChange,
            onOpenMedicinePicker = onOpenMedicinePicker,
            countText = countText,
            onCountTextChange = onCountTextChange,
            onDecreaseCountClick = onDecreaseCountClick,
            onIncreaseCountClick = onIncreaseCountClick,
            errorMessageRes = errorMessageRes,
            isSaving = isSaving,
        )
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(
    name = "Medication Group Slot Editor",
    showBackground = true,
    widthDp = 420,
    heightDp = 920,
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationGroupSlotEditorSheetPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        ).copy(pillStrengthMg = "2")
        val medicine = previewMedicationEditorMedicine(
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        MedicationGroupSlotEditorSheet(
            title = "Edit medication",
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            confirmButtonText = "Save",
            onDismissRequest = { },
            onCloseClick = { },
            medicineDraft = draft,
            doseInstructionDraft = draft.toDoseInstructionDraft(),
            resolvedMedicine = medicine,
            canEditMedicationIdentity = true,
            onMedicineDraftChange = { },
            onDoseInstructionDraftChange = { },
            onOpenMedicinePicker = { },
            countText = "2",
            onCountTextChange = { },
            onDecreaseCountClick = { },
            onIncreaseCountClick = { },
            onConfirm = { },
        )
    }
}

private fun previewMedicationEditorMedicine(
    key: MedicationKey,
    preparation: MedicinePreparation,
): Medicine {
    return Medicine(
        uuid = UUID.nameUUIDFromBytes("preview-medicine-${key.name}".toByteArray()),
        selection = MedicineSelection.Catalog(key),
        category = key.category,
        preparation = preparation,
        displayName = null,
        identityKey = MedicineIdentityKey.catalog(key, preparation),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null,
        stock = MedicineStock(),
    )
}
