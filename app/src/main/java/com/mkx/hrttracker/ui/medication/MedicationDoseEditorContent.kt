package com.mkx.hrttracker.ui.medication

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationDoseAssistPreset
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.MedicationCardWithStockSubcard
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.util.labelRes
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Medication editor content — slot/log dose fields plus routed picker summary.
// ---------------------------------------------------------------------------

@Composable
internal fun MedicationEditorContent(
    medicineDraft: MedicinePickerUiState,
    doseInstructionDraft: DoseInstructionDraftUiState?,
    resolvedMedicine: Medicine,
    canEditMedicationIdentity: Boolean,
    selectedStockProjection: MedicineStockProjection? = null,
    stockMutationPreviewDoseMagnitude: Double? = null,
    previewPostMutationState: ((MedicineStock) -> MedicineStockState?)? = null,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    onDoseInstructionDraftChange: ((DoseInstructionDraftUiState) -> DoseInstructionDraftUiState) -> Unit,
    onOpenMedicinePicker: () -> Unit,
    countText: String,
    onCountTextChange: (String) -> Unit,
    onDecreaseCountClick: () -> Unit,
    onIncreaseCountClick: () -> Unit,
    errorMessageRes: Int?,
    isSaving: Boolean,
    // Defaults to canEditMedicationIdentity so existing callers keep their
    // behavior; the medicine-manager-hosted dose sheet sets this to false
    // since re-tapping a card in the manager itself is the re-pick UI.
    canRepickMedicine: Boolean = canEditMedicationIdentity,
) {
    val activePreparationType = resolvedMedicine.preparation.type
    val applicationType = doseInstructionDraft
        ?.let { resolvedApplicationTypeForDose(activePreparationType, it) }
        ?: medicineDraft.catalogFilterApplicationType
    val isPatchOff = applicationType == MedicationApplicationType.PATCH_OFF

    if (isPatchOff) {
        MedicationSummaryHeader(
            medicine = resolvedMedicine,
            applicationType = applicationType,
            doseInstructionDraft = doseInstructionDraft,
            countText = countText,
            canOpenMedicinePicker = canRepickMedicine,
            onOpenMedicinePicker = { if (!isSaving) onOpenMedicinePicker() },
            selectedStockProjection = selectedStockProjection,
            stockMutationPreviewDoseMagnitude = stockMutationPreviewDoseMagnitude,
            previewPostMutationState = previewPostMutationState,
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        MedicationNumericField(
            value = "",
            label = stringResource(R.string.medication_editor_patch_off_hint),
            leadingIconRes = medicationApplicationOutlinedIconRes(MedicationApplicationType.PATCH_OFF),
            enabled = false,
            readOnly = true,
            onValueChange = {},
        )
        return
    }

    val summaryTrailingIndicator = remember(medicineDraft, doseInstructionDraft, countText) {
        medicationSummaryTrailingIndicator(
            medicineDraft = medicineDraft,
            doseInstructionDraft = doseInstructionDraft,
            countText = countText,
        )
    }
    MedicationSummaryHeader(
        medicine = resolvedMedicine,
        applicationType = applicationType,
        doseInstructionDraft = doseInstructionDraft,
        countText = countText,
        canOpenMedicinePicker = canRepickMedicine,
        onOpenMedicinePicker = { if (!isSaving) onOpenMedicinePicker() },
        selectedStockProjection = selectedStockProjection,
        stockMutationPreviewDoseMagnitude = stockMutationPreviewDoseMagnitude,
        previewPostMutationState = previewPostMutationState,
        trailingIndicator = summaryTrailingIndicator,
    )

    // The dose instruction form must render whenever the route requires per-
    // instruction dose data (VolumeMl for INJECTION_MULTI_USE_VIAL,
    // WeightGrams for GEL_CONTAINER, TabletFraction for PILL), regardless of
    // whether the user is creating a new medicine or selecting an existing one.
    // Routes whose dose is fully determined by the medicine (WholeUnit /
    // patch-off Noop) need no editable dose form. See
    // DoseInstructionDraftUiState.validationErrorRes(): the editor is otherwise
    // unsaveable when the user picks an existing multi-use vial / gel container.
    if (doseInstructionDraft != null &&
        requiresEditableDoseInstructionForm(activePreparationType)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        DoseInstructionForm(
            medicineDraft = medicineDraft,
            doseInstructionDraft = doseInstructionDraft,
            activePreparationType = activePreparationType,
            onDoseInstructionDraftChange = { transform ->
                if (!isSaving) onDoseInstructionDraftChange(transform)
            },
            errorMessageRes = errorMessageRes,
        )
    }

    if (applicationType.supportsMedicationCountEditor(activePreparationType)) {
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        MedicationCountTextField(
            value = countText,
            onValueChange = { if (!isSaving) onCountTextChange(it) },
            onDecreaseClick = { if (!isSaving) onDecreaseCountClick() },
            onIncreaseClick = { if (!isSaving) onIncreaseCountClick() },
            errorMessageRes = errorMessageRes
                ?.takeIf { it == R.string.validation_count_required },
        )
    }
}

@Composable
private fun MedicationSummaryHeader(
    medicine: Medicine,
    applicationType: MedicationApplicationType,
    doseInstructionDraft: DoseInstructionDraftUiState?,
    countText: String,
    canOpenMedicinePicker: Boolean,
    onOpenMedicinePicker: () -> Unit,
    selectedStockProjection: MedicineStockProjection? = null,
    stockMutationPreviewDoseMagnitude: Double? = null,
    previewPostMutationState: ((MedicineStock) -> MedicineStockState?)? = null,
    trailingIndicator: MedicationSummaryTrailingIndicator? = null,
) {
    MedicationEditorSectionLabel(stringResource(R.string.field_medication), topPadding = false)
    val resolvedCount = remember(countText) { parseMedicationCountText(countText) }
    val doseInstruction = doseInstructionDraft?.toDoseInstructionOrNull() ?: DoseInstruction.Noop
    Spacer(modifier = Modifier.height(2.dp))
    MedicationCardWithStockSubcard(
        medicine = medicine,
        doseInstruction = doseInstruction,
        applicationType = applicationType,
        medicationCount = resolvedCount.coerceAtLeast(1),
        groupColorKey = null,
        stockProjection = selectedStockProjection.takeIf {
            medicationSummaryShouldShowStockSubcard(
                hasMedicine = true,
                hasStockProjection = it != null,
            )
        },
        stockMutationPreviewDoseMagnitude = stockMutationPreviewDoseMagnitude,
        previewPostMutationState = previewPostMutationState,
        onClick = onOpenMedicinePicker,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        enabled = canOpenMedicinePicker,
        trailingContent = medicationSummaryTrailingContent(trailingIndicator),
        // Match the medicine manager row: describe the medicine
        // (preparation summary) instead of the in-flight entry (route +
        // dose + count). The dose form below covers the rest.
        supportingTextOverride = medicinePreparationSummary(medicine),
        leadingIconAsForm = true,
    )
}

internal fun medicationSummaryShouldShowStockSubcard(
    hasMedicine: Boolean,
    hasStockProjection: Boolean,
): Boolean {
    return hasMedicine && hasStockProjection
}

@Composable
internal fun DoseInstructionForm(
    medicineDraft: MedicinePickerUiState,
    doseInstructionDraft: DoseInstructionDraftUiState,
    activePreparationType: MedicinePreparationType = doseInstructionDraft.preparationType,
    onDoseInstructionDraftChange: ((DoseInstructionDraftUiState) -> DoseInstructionDraftUiState) -> Unit,
    errorMessageRes: Int?,
    textFieldFocusRequester: FocusRequester? = null,
) {
    if (activePreparationType == MedicinePreparationType.PILL) {
        val availableRoutes = MedicationCatalog.tabletRoutesFor(medicineDraft.category)
        val currentRoute = when (doseInstructionDraft.applicationType) {
            MedicationApplicationType.ORAL,
            MedicationApplicationType.SUBLINGUAL -> doseInstructionDraft.applicationType

            MedicationApplicationType.INJECTION,
            MedicationApplicationType.GEL,
            MedicationApplicationType.PATCH_ON,
            MedicationApplicationType.PATCH_OFF -> MedicationApplicationType.ORAL
        }
        // Coerce drafts created with SUBLINGUAL into ORAL when the category no
        // longer supports SUBLINGUAL (e.g. user switched the medicine to an
        // antiandrogen). Without this the saved slot would carry a route the
        // user can no longer see in the editor.
        LaunchedEffect(availableRoutes, doseInstructionDraft.applicationType) {
            if (doseInstructionDraft.applicationType !in availableRoutes &&
                availableRoutes.isNotEmpty()
            ) {
                onDoseInstructionDraftChange {
                    it.copy(applicationType = availableRoutes.first())
                }
            }
        }
        if (availableRoutes.size >= 2) {
            MedicationEditorSectionLabel(stringResource(R.string.field_medication_application))
            TabletRouteRow(
                availableRoutes = availableRoutes,
                applicationType = currentRoute,
                onApplicationTypeChange = { route ->
                    onDoseInstructionDraftChange {
                        it.copy(applicationType = route)
                    }
                },
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        }
    }

    when (activePreparationType) {
        MedicinePreparationType.PILL -> {
            val current = doseInstructionDraft.selectedTabletFractionOption()
            val options = TabletFractionOption.entries
            val currentIndex = options.indexOf(current).coerceAtLeast(0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.field_dose_tablet_fraction),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = current.label(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = currentIndex.toFloat(),
                onValueChange = { value ->
                    val selected = options[
                        value.roundToInt().coerceIn(0, options.size - 1)
                    ]
                    if (selected != current) {
                        onDoseInstructionDraftChange { it.selectTabletFraction(selected) }
                    }
                },
                valueRange = 0f..(options.size - 1).toFloat(),
                // Slider.steps counts intermediate stops between the endpoints.
                steps = options.size - 2,
            )
        }

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
        MedicinePreparationType.DEPOT_INJECTION,
        MedicinePreparationType.CAPSULE,
        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.PATCH,
            // PATCH_OFF emits a Noop dose; no per-instruction form to render.
        MedicinePreparationType.PATCH_OFF,
        MedicinePreparationType.IMPORTED_INJECTION,
        MedicinePreparationType.IMPORTED_GEL -> Unit // whole-unit/import-only dose; no input needed.

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> MedicationNumericField(
            value = doseInstructionDraft.volumeMl,
            label = medicationEditorFieldLabelWithUnit(
                R.string.field_dose_volume_ml,
                R.string.unit_ml,
            ),
            suffix = stringResource(R.string.unit_ml),
            leadingIconRes = R.drawable.ic_water_drops,
            isError = errorMessageRes == R.string.validation_dose_volume_required,
            errorMessageRes = R.string.validation_dose_volume_required
                .takeIf { errorMessageRes == it },
            onValueChange = { value ->
                onDoseInstructionDraftChange { it.copy(volumeMl = value) }
            },
            focusRequester = textFieldFocusRequester,
        )

        MedicinePreparationType.GEL_CONTAINER -> {
            MedicationNumericField(
                value = doseInstructionDraft.weightGrams,
                label = medicationEditorFieldLabelWithUnit(
                    R.string.field_dose_weight_grams,
                    R.string.unit_grams,
                ),
                suffix = stringResource(R.string.unit_grams),
                leadingIconRes = R.drawable.ic_weight,
                isError = errorMessageRes == R.string.validation_dose_weight_required,
                errorMessageRes = R.string.validation_dose_weight_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onDoseInstructionDraftChange { it.copy(weightGrams = value) }
                },
                focusRequester = textFieldFocusRequester,
            )
            DoseAssistPresetRow(
                presets = activeDoseAssistPresets(
                    medicineDraft = medicineDraft,
                    doseInstructionDraft = doseInstructionDraft,
                ),
                onPresetClick = { preset ->
                    onDoseInstructionDraftChange { it.applyDoseAssistPreset(preset) }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TabletRouteRow(
    availableRoutes: List<MedicationApplicationType>,
    applicationType: MedicationApplicationType,
    onApplicationTypeChange: (MedicationApplicationType) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConnectedButtonGroup(
        modifier = modifier.fillMaxWidth(),
        options = availableRoutes,
        selectedOption = applicationType,
        optionLabel = { route -> stringResource(route.labelRes) },
        optionLeadingContent = { route ->
            MedicationApplicationIcon(
                applicationType = route,
                contentDescription = null,
                modifier = Modifier.size(ToggleButtonDefaults.IconSize),
            )
        },
        onOptionSelected = onApplicationTypeChange,
    )
}

internal fun preparationTypeLabelRes(preparationType: MedicinePreparationType): Int {
    return when (preparationType) {
        MedicinePreparationType.PILL -> R.string.preparation_type_pill
        MedicinePreparationType.CAPSULE -> R.string.preparation_type_capsule
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
            R.string.preparation_type_injection_single_use_vial

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL ->
            R.string.preparation_type_injection_multi_use_vial

        MedicinePreparationType.DEPOT_INJECTION -> R.string.medication_application_injection

        MedicinePreparationType.GEL_SACHET -> R.string.preparation_type_gel_sachet
        MedicinePreparationType.GEL_CONTAINER -> R.string.preparation_type_gel_container
        MedicinePreparationType.IMPORTED_INJECTION ->
            R.string.preparation_type_injection_single_use_vial

        MedicinePreparationType.IMPORTED_GEL -> R.string.preparation_type_gel_container
        MedicinePreparationType.PATCH -> R.string.preparation_type_patch
        // Surfaced only on the PATCH_OFF singleton's read-only summary; the
        // create-medicine picker never offers it.
        MedicinePreparationType.PATCH_OFF -> R.string.medicine_patch_off_name
    }
}

internal fun DoseInstructionDraftUiState.toDoseInstructionOrNull():
        DoseInstruction? {
    return runCatching { toDoseInstruction() }.getOrNull()
}

@Composable
private fun medicationSummaryTrailingContent(
    trailingIndicator: MedicationSummaryTrailingIndicator?,
): (@Composable () -> Unit)? {
    return when (trailingIndicator) {
        MedicationSummaryTrailingIndicator.DOSE_WARNING -> {
            {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = stringResource(R.string.medication_editor_dose_warning),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        null -> null
    }
}

// ---------------------------------------------------------------------------
// Dose assist and count fields.
// ---------------------------------------------------------------------------

@Composable
internal fun DoseAssistPresetRow(
    presets: List<MedicationDoseAssistPreset>,
    onPresetClick: (MedicationDoseAssistPreset) -> Unit,
) {
    if (presets.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
        ) {
            presets.forEach { preset ->
                val label = doseAssistPresetLabel(preset)
                AssistChip(
                    onClick = { onPresetClick(preset) },
                    label = {
                        Text(
                            text = label,
                            modifier = Modifier.cjkTextOffset(label),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun doseAssistPresetLabel(preset: MedicationDoseAssistPreset): String {
    return when (preset) {
        is MedicationDoseAssistPreset.MgAsMedicine -> stringResource(
            R.string.medication_editor_dose_assist_mg,
            preset.valueMg,
        )

        is MedicationDoseAssistPreset.GelPercent -> stringResource(
            R.string.medication_editor_dose_assist_percent,
            preset.percent,
        )

        is MedicationDoseAssistPreset.GelWeightGrams -> stringResource(
            R.string.medication_editor_dose_assist_grams,
            preset.weightGrams,
        )

        is MedicationDoseAssistPreset.GelContainerSizeGrams -> stringResource(
            R.string.medication_editor_dose_assist_grams,
            preset.weightGrams,
        )

        is MedicationDoseAssistPreset.MultiUseVialConcentrationMgPerMl -> stringResource(
            R.string.medication_editor_dose_assist_mg_per_ml,
            preset.mgPerMl,
        )

        is MedicationDoseAssistPreset.MultiUseVialVolumeMl -> stringResource(
            R.string.medication_editor_dose_assist_ml,
            preset.volumeMl,
        )

        is MedicationDoseAssistPreset.PatchTotalMg -> stringResource(
            R.string.medication_editor_dose_assist_mg,
            preset.valueMg,
        )

        is MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay -> stringResource(
            R.string.medication_editor_dose_assist_mcg_day,
            preset.valueMcgPerDay,
        )
    }
}

@Composable
internal fun MedicationCountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onDecreaseClick: () -> Unit,
    onIncreaseClick: () -> Unit,
    @StringRes errorMessageRes: Int? = null,
) {
    val focusManager = LocalFocusManager.current
    // On API 26, clearing focus in a sheet makes the platform jump focus back
    // to the first text field; parking on the sheet's non-text anchor instead
    // dismisses the IME without the jump. Falls back to clearFocus off-sheet.
    val dismissFocusAnchor = LocalSheetDismissFocusRequester.current
    val dismissKeyboard: () -> Unit = {
        dismissFocusAnchor?.requestFocus() ?: focusManager.clearFocus()
    }
    val stepBaseCount = countStepBase(value)
    var textFieldValue by remember(value) {
        mutableStateOf(
            TextFieldValue(text = value, selection = TextRange(value.length)),
        )
    }
    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { updatedValue ->
            val sanitizedValue = sanitizeMedicationCountText(updatedValue.text)
            val selection = TextRange(
                start = updatedValue.selection.start.coerceIn(0, sanitizedValue.length),
                end = updatedValue.selection.end.coerceIn(0, sanitizedValue.length),
            )
            textFieldValue = updatedValue.copy(text = sanitizedValue, selection = selection)
            onValueChange(sanitizedValue)
        },
        label = { Text(text = stringResource(R.string.field_count)) },
        leadingIcon = {
            Icon(imageVector = Icons.Rounded.Tag, contentDescription = null)
        },
        isError = errorMessageRes != null,
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp),
            ) {
                IconButton(
                    onClick = onDecreaseClick,
                    enabled = stepBaseCount > 1,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.decrease_medication_count),
                    )
                }
                IconButton(onClick = onIncreaseClick) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.increase_medication_count),
                    )
                }
            }
        },
        supportingText = errorMessageRes?.let { messageRes ->
            {
                Text(
                    text = stringResource(messageRes),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
    )
}
