package com.mkx.hrttracker.ui.catalog

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationDoseAssistPreset
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicinePreparationForm
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.MedicalDisclaimerSets
import com.mkx.hrttracker.ui.medication.DoseAssistPresetRow
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.medication.MedicationEditorSectionLabel
import com.mkx.hrttracker.ui.medication.MedicationNumericField
import com.mkx.hrttracker.ui.medication.MedicinePickerUiState
import com.mkx.hrttracker.ui.medication.PatchSpecKind
import com.mkx.hrttracker.ui.medication.activeDoseAssistPresets
import com.mkx.hrttracker.ui.medication.ambiguousPreparationTypes
import com.mkx.hrttracker.ui.medication.applyDoseAssistPreset
import com.mkx.hrttracker.ui.medication.availableCatalogKeys
import com.mkx.hrttracker.ui.medication.changeCategory
import com.mkx.hrttracker.ui.medication.changeForm
import com.mkx.hrttracker.ui.medication.changeMedicationKey
import com.mkx.hrttracker.ui.medication.changePreparationType
import com.mkx.hrttracker.ui.medication.editorMedicationCategories
import com.mkx.hrttracker.ui.medication.inferredOrSelectedPreparationType
import com.mkx.hrttracker.ui.medication.medicinePreparationFormIconRes
import com.mkx.hrttracker.ui.medication.medicationEditorFieldLabelWithUnit
import com.mkx.hrttracker.ui.medication.preparationTypeLabelRes
import com.mkx.hrttracker.ui.medication.requiresCustomName
import com.mkx.hrttracker.ui.medication.requiresPreparationTypeSelection
import com.mkx.hrttracker.ui.medication.shortLabelRes
import com.mkx.hrttracker.ui.medication.showsCustomDoseUnitPicker
import com.mkx.hrttracker.ui.medication.supportsCatalogSelection
import com.mkx.hrttracker.util.labelRes
import java.util.UUID

/**
 * Bottom sheet that creates a catalog `Medicine` and nothing else.
 *
 * Opened from: the medicine manager ("add medicine").
 * Hosted by: MedicinesScreen.
 * Produces: a catalog `Medicine` (CreateMedicineSaveResult); never returns a
 *   regimen slot and never writes a history log.
 * Identity: fully editable - this sheet is the identity editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMedicineSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onCreated: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateMedicineViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CreateMedicineSheetContent(
        uiState = uiState,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        onDraftChange = viewModel::updateDraft,
        onCreateClick = { viewModel.create(onCreated) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateMedicineSheetContent(
    uiState: CreateMedicineUiState,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSheetLocked = uiState.isSaving || uiState.isSaved
    MedicationEditorSheetScaffold(
        modifier = modifier,
        title = stringResource(R.string.create_medicine_title),
        sheetState = sheetState,
        confirmButtonText = stringResource(R.string.create_medicine_action),
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        fillAvailableHeight = true,
        isSaving = isSheetLocked,
        disclaimerKinds = MedicalDisclaimerSets.medicationEditor,
        onConfirm = onCreateClick,
    ) {
        CreateMedicineResultText(saveResult = uiState.saveResult)
        CreateMedicineForm(
            medicineDraft = uiState.draft,
            onMedicineDraftChange = onDraftChange,
            errorMessageRes = uiState.errorMessageRes,
            // Keep the focused field locked after success while the sheet
            // closes, matching the create-then-dose flow and avoiding an IME
            // reopen during the outgoing animation.
            readOnly = isSheetLocked,
        )
    }
}

@Composable
internal fun CreateMedicineResultText(saveResult: CreateMedicineSaveResult?) {
    val messageRes = when (saveResult) {
        CreateMedicineSaveResult.FAILURE_IDENTITY_COLLISION ->
            R.string.medicine_already_exists

        CreateMedicineSaveResult.FAILURE_OTHER -> R.string.medicine_save_failure
        CreateMedicineSaveResult.SUCCESS,
        null -> null
    } ?: return
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = dimensionResource(R.dimen.padding_small)),
    )
}

@Composable
internal fun CreateMedicineForm(
    medicineDraft: MedicinePickerUiState,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    errorMessageRes: Int?,
    readOnly: Boolean,
    followUpFocusRequester: FocusRequester? = null,
    // Surfaces the high-dose warning on the strength field for flows that have
    // no medicine summary card to host it (CreateMedicineThenDoseSheet). Callers that
    // render the card leave this false and keep the warning in the card.
    showStrengthDoseWarning: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    // One requester per slot in CreateMedicineField; the set is fixed so the
    // map doesn't need to follow draft mutations. editableFields(...) is
    // recomputed each draft change and decides which requester each visible
    // text field should advance to next.
    val focusRequesters = remember {
        CreateMedicineField.entries.associateWith { FocusRequester() }
    }
    val editableFields = editableFields(medicineDraft)

    MedicationEditorSectionLabel(stringResource(R.string.field_medication_category), topPadding = false)
    ConnectedButtonGroup(
        options = editorMedicationCategories(),
        selectedOption = medicineDraft.category,
        optionLabel = { category -> stringResource(category.labelRes) },
        onOptionSelected = { category ->
            onMedicineDraftChange { it.changeCategory(category) }
        },
    )

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

    MedicationEditorSectionLabel(stringResource(R.string.field_preparation_form))
    PreparationFormButtonGroup(
        options = MedicationCatalog.preparationFormsFor(medicineDraft.category),
        selectedOption = medicineDraft.form,
        onOptionSelected = { form ->
            onMedicineDraftChange { it.changeForm(form) }
        },
    )

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

    val catalogKeys = medicineDraft.availableCatalogKeys()
    if (medicineDraft.supportsCatalogSelection() &&
        medicineDraft.selectionKind == MedicationSelectionKind.CATALOG &&
        catalogKeys.size > 1
    ) {
        MedicationEditorSectionLabel(stringResource(R.string.field_medication))
        ConnectedButtonGroup(
            options = catalogKeys,
            selectedOption = medicineDraft.medicationKey ?: catalogKeys.first(),
            optionLabel = { medicationKey: MedicationKey ->
                stringResource(medicationKey.labelRes)
            },
            onOptionSelected = { medicationKey ->
                onMedicineDraftChange { it.changeMedicationKey(medicationKey) }
            },
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    // Display-name override sits in the identity section for catalog medicines,
    // mirroring where the custom-name field appears for custom medicines.
    if (medicineDraft.selectionKind == MedicationSelectionKind.CATALOG &&
        !medicineDraft.requiresCustomName()
    ) {
        DisplayNameField(
            medicineDraft = medicineDraft,
            onMedicineDraftChange = onMedicineDraftChange,
            readOnly = readOnly,
            focusRequester = focusRequesters.getValue(CreateMedicineField.DISPLAY_NAME),
            imeAction = imeActionFor(editableFields, CreateMedicineField.DISPLAY_NAME),
            onImeNext = {
                nextField(editableFields, CreateMedicineField.DISPLAY_NAME)
                    ?.let { focusRequesters.getValue(it).requestFocus() }
                    ?: focusManager.clearFocus()
            },
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    if (medicineDraft.requiresCustomName()) {
        val customNameIme = imeActionFor(editableFields, CreateMedicineField.CUSTOM_NAME)
        OutlinedTextField(
            value = medicineDraft.customMedicationName,
            onValueChange = { value ->
                onMedicineDraftChange { it.copy(customMedicationName = value) }
            },
            readOnly = readOnly,
            isError = errorMessageRes == R.string.validation_name_required,
            label = { Text(text = stringResource(R.string.field_medication_name)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Label,
                    contentDescription = null,
                )
            },
            supportingText = if (errorMessageRes == R.string.validation_name_required) {
                {
                    Text(
                        text = stringResource(R.string.validation_name_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequesters.getValue(CreateMedicineField.CUSTOM_NAME)),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = customNameIme),
            keyboardActions = KeyboardActions(
                onNext = {
                    nextField(editableFields, CreateMedicineField.CUSTOM_NAME)
                        ?.let { focusRequesters.getValue(it).requestFocus() }
                        ?: focusManager.clearFocus()
                },
                onDone = { focusManager.clearFocus() },
            ),
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    NewMedicinePreparationForm(
        medicineDraft = medicineDraft,
        onMedicineDraftChange = onMedicineDraftChange,
        errorMessageRes = errorMessageRes,
        readOnly = readOnly,
        focusRequesters = focusRequesters,
        editableFields = editableFields,
        followUpFocusRequester = followUpFocusRequester,
        showStrengthDoseWarning = showStrengthDoseWarning,
    )
}

@Composable
private fun DisplayNameField(
    medicineDraft: MedicinePickerUiState,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    readOnly: Boolean,
    focusRequester: FocusRequester,
    imeAction: ImeAction = ImeAction.Done,
    onImeNext: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val displayNameState = rememberTextFieldState(initialText = medicineDraft.displayName)
    val currentDisplayName by rememberUpdatedState(medicineDraft.displayName)
    val currentOnMedicineDraftChange by rememberUpdatedState(onMedicineDraftChange)

    LaunchedEffect(displayNameState, medicineDraft.displayName) {
        if (displayNameState.text.toString() != medicineDraft.displayName) {
            displayNameState.setTextAndPlaceCursorAtEnd(medicineDraft.displayName)
        }
    }

    LaunchedEffect(displayNameState) {
        snapshotFlow { displayNameState.text.toString() }.collect { value ->
            if (value != currentDisplayName) {
                currentOnMedicineDraftChange { it.copy(displayName = value) }
            }
        }
    }

    // Catalog medicines only: the placeholder shows the catalog key label so
    // an empty input keeps the medicine using that name everywhere it appears.
    val defaultName = medicineDraft.medicationKey
        ?.let { stringResource(it.labelRes) }
        .orEmpty()
    OutlinedTextField(
        state = displayNameState,
        readOnly = readOnly,
        labelPosition = displayNameFieldLabelPosition(),
        label = { Text(text = stringResource(R.string.medicine_display_name)) },
        placeholder = if (defaultName.isNotEmpty()) {
            { Text(text = defaultName) }
        } else {
            null
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Label,
                contentDescription = null,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        onKeyboardAction = {
            if (imeAction == ImeAction.Next) {
                onImeNext?.invoke() ?: focusManager.clearFocus()
            } else {
                focusManager.clearFocus()
            }
        },
    )
}

internal fun displayNameFieldLabelPosition(): TextFieldLabelPosition {
    return TextFieldLabelPosition.Attached(alwaysMinimize = true)
}

@Composable
private fun NewMedicinePreparationForm(
    medicineDraft: MedicinePickerUiState,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    errorMessageRes: Int?,
    readOnly: Boolean,
    focusRequesters: Map<CreateMedicineField, FocusRequester>,
    editableFields: List<CreateMedicineField>,
    followUpFocusRequester: FocusRequester? = null,
    showStrengthDoseWarning: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val onImeNextFor: (CreateMedicineField) -> () -> Unit = { field ->
        {
            val nextInForm = nextField(editableFields, field)
            when {
                nextInForm != null -> focusRequesters.getValue(nextInForm).requestFocus()
                followUpFocusRequester != null -> followUpFocusRequester.requestFocus()
                else -> focusManager.clearFocus()
            }
        }
    }
    // The IME action chain extends past the form boundary when a follow-up
    // requester is supplied (e.g. a dose instruction text field rendered just
    // below this form), so the last editable field shows Next instead of Done.
    val imeActionForField: (CreateMedicineField) -> ImeAction = { field ->
        if (nextField(editableFields, field) == null && followUpFocusRequester == null) {
            ImeAction.Done
        } else {
            ImeAction.Next
        }
    }
    if (medicineDraft.requiresPreparationTypeSelection()) {
        MedicationEditorSectionLabel(stringResource(R.string.field_preparation_type))
        val options = ambiguousPreparationTypes(medicineDraft.form)
        ConnectedButtonGroup(
            options = options,
            selectedOption = medicineDraft.preparationType ?: options.first(),
            optionLabel = { preparationType ->
                stringResource(preparationTypeLabelRes(preparationType))
            },
            onOptionSelected = { preparationType ->
                onMedicineDraftChange { it.changePreparationType(preparationType) }
            },
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    val rawMassUnit = if (medicineDraft.showsCustomDoseUnitPicker()) {
        medicineDraft.customDoseUnit.shortLabelRes()
    } else {
        R.string.unit_mg
    }
    val doseAssistPresets = medicineDraft.activeDoseAssistPresets()
    when (medicineDraft.inferredOrSelectedPreparationType() ?: return) {
        MedicinePreparationType.PILL,
        MedicinePreparationType.CAPSULE -> {
            val isCapsule = medicineDraft.inferredOrSelectedPreparationType() == MedicinePreparationType.CAPSULE
            val strengthErrorRes = if (isCapsule) {
                R.string.validation_capsule_strength_required
            } else {
                R.string.validation_pill_strength_required
            }
            MedicationNumericField(
                value = medicineDraft.pillStrengthMg,
                label = if (isCapsule) {
                    medicationEditorFieldLabelWithUnit(R.string.field_capsule_strength_mg, rawMassUnit)
                } else {
                    medicationEditorFieldLabelWithUnit(R.string.field_pill_strength_mg, rawMassUnit)
                },
                suffix = stringResource(rawMassUnit),
                leadingIconRes = R.drawable.ic_medication,
                readOnly = readOnly,
                isError = errorMessageRes == strengthErrorRes,
                errorMessageRes = strengthErrorRes.takeIf { errorMessageRes == it },
                showWarningIcon = showStrengthDoseWarning,
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(pillStrengthMg = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.PILL_STRENGTH),
                imeAction = imeActionForField(CreateMedicineField.PILL_STRENGTH),
                onImeNext = onImeNextFor(CreateMedicineField.PILL_STRENGTH),
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets
                    .filterIsInstance<MedicationDoseAssistPreset.MgAsMedicine>(),
                onPresetClick = { preset ->
                    onMedicineDraftChange { it.applyDoseAssistPreset(preset) }
                },
            )
            CustomDoseUnitPickerForRawMassField(
                medicineDraft = medicineDraft,
                onMedicineDraftChange = onMedicineDraftChange,
            )
        }

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> {
            MedicationNumericField(
                value = medicineDraft.singleUseVialStrengthMg,
                label = medicationEditorFieldLabelWithUnit(R.string.field_single_use_vial_strength_mg, rawMassUnit),
                suffix = stringResource(rawMassUnit),
                leadingIconRes = R.drawable.ic_vaccines,
                readOnly = readOnly,
                isError = errorMessageRes == R.string.validation_vial_strength_required,
                errorMessageRes = R.string.validation_vial_strength_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(singleUseVialStrengthMg = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.VIAL_STRENGTH),
                imeAction = imeActionForField(CreateMedicineField.VIAL_STRENGTH),
                onImeNext = onImeNextFor(CreateMedicineField.VIAL_STRENGTH),
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets
                    .filterIsInstance<MedicationDoseAssistPreset.MgAsMedicine>(),
                onPresetClick = { preset ->
                    onMedicineDraftChange { it.applyDoseAssistPreset(preset) }
                },
            )
            CustomDoseUnitPickerForRawMassField(
                medicineDraft = medicineDraft,
                onMedicineDraftChange = onMedicineDraftChange,
            )
        }

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> {
            MedicationNumericField(
                value = medicineDraft.concentrationMgPerMl,
                label = medicationEditorFieldLabelWithUnit(R.string.field_concentration_mg_per_ml, R.string.unit_mg_per_ml),
                suffix = stringResource(R.string.unit_mg_per_ml),
                leadingIconRes = R.drawable.ic_humidity_percentage,
                readOnly = readOnly,
                isError = errorMessageRes == R.string.validation_concentration_required,
                errorMessageRes = R.string.validation_concentration_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(concentrationMgPerMl = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.CONCENTRATION_MG_PER_ML),
                imeAction = imeActionForField(CreateMedicineField.CONCENTRATION_MG_PER_ML),
                onImeNext = onImeNextFor(CreateMedicineField.CONCENTRATION_MG_PER_ML),
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets
                    .filterIsInstance<MedicationDoseAssistPreset.MultiUseVialConcentrationMgPerMl>(),
                onPresetClick = { preset ->
                    onMedicineDraftChange { it.applyDoseAssistPreset(preset) }
                },
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            MedicationNumericField(
                value = medicineDraft.vialVolumeMl,
                label = medicationEditorFieldLabelWithUnit(R.string.field_vial_volume_ml, R.string.unit_ml),
                suffix = stringResource(R.string.unit_ml),
                leadingIconRes = R.drawable.ic_fluid,
                readOnly = readOnly,
                isError = errorMessageRes == R.string.validation_vial_volume_required,
                errorMessageRes = R.string.validation_vial_volume_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(vialVolumeMl = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.VIAL_VOLUME_ML),
                imeAction = imeActionForField(CreateMedicineField.VIAL_VOLUME_ML),
                onImeNext = onImeNextFor(CreateMedicineField.VIAL_VOLUME_ML),
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets
                    .filterIsInstance<MedicationDoseAssistPreset.MultiUseVialVolumeMl>(),
                onPresetClick = { preset ->
                    onMedicineDraftChange { it.applyDoseAssistPreset(preset) }
                },
            )
        }

        MedicinePreparationType.GEL_SACHET -> {
            MedicationNumericField(
                value = medicineDraft.gelConcentrationPercent,
                label = medicationEditorFieldLabelWithUnit(R.string.field_gel_concentration_percent, R.string.unit_percent),
                suffix = stringResource(R.string.unit_percent),
                leadingIconRes = R.drawable.ic_humidity_percentage,
                readOnly = readOnly,
                isError = errorMessageRes == R.string.validation_gel_concentration_required,
                errorMessageRes = R.string.validation_gel_concentration_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(gelConcentrationPercent = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.GEL_PERCENT),
                imeAction = imeActionForField(CreateMedicineField.GEL_PERCENT),
                onImeNext = onImeNextFor(CreateMedicineField.GEL_PERCENT),
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets
                    .filterIsInstance<MedicationDoseAssistPreset.GelPercent>(),
                onPresetClick = { preset ->
                    onMedicineDraftChange { it.applyDoseAssistPreset(preset) }
                },
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            MedicationNumericField(
                value = medicineDraft.sachetWeightGrams,
                label = medicationEditorFieldLabelWithUnit(R.string.field_sachet_weight_grams, R.string.unit_grams),
                suffix = stringResource(R.string.unit_grams),
                leadingIconRes = R.drawable.ic_weight,
                readOnly = readOnly,
                isError = errorMessageRes == R.string.validation_sachet_weight_required,
                errorMessageRes = R.string.validation_sachet_weight_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(sachetWeightGrams = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.SACHET_WEIGHT),
                imeAction = imeActionForField(CreateMedicineField.SACHET_WEIGHT),
                onImeNext = onImeNextFor(CreateMedicineField.SACHET_WEIGHT),
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets
                    .filterIsInstance<MedicationDoseAssistPreset.GelWeightGrams>(),
                onPresetClick = { preset ->
                    onMedicineDraftChange { it.applyDoseAssistPreset(preset) }
                },
            )
        }

        MedicinePreparationType.GEL_CONTAINER -> {
            MedicationNumericField(
                value = medicineDraft.gelConcentrationPercent,
                label = medicationEditorFieldLabelWithUnit(R.string.field_gel_concentration_percent, R.string.unit_percent),
                suffix = stringResource(R.string.unit_percent),
                leadingIconRes = R.drawable.ic_humidity_percentage,
                readOnly = readOnly,
                isError = errorMessageRes == R.string.validation_gel_concentration_required,
                errorMessageRes = R.string.validation_gel_concentration_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(gelConcentrationPercent = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.GEL_PERCENT),
                imeAction = imeActionForField(CreateMedicineField.GEL_PERCENT),
                onImeNext = onImeNextFor(CreateMedicineField.GEL_PERCENT),
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets
                    .filterIsInstance<MedicationDoseAssistPreset.GelPercent>(),
                onPresetClick = { preset ->
                    onMedicineDraftChange { it.applyDoseAssistPreset(preset) }
                },
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            MedicationNumericField(
                value = medicineDraft.containerWeightGrams,
                label = medicationEditorFieldLabelWithUnit(R.string.field_container_weight_grams, R.string.unit_grams),
                suffix = stringResource(R.string.unit_grams),
                leadingIconRes = R.drawable.ic_weight,
                readOnly = readOnly,
                isError = errorMessageRes == R.string.validation_container_weight_required,
                errorMessageRes = R.string.validation_container_weight_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(containerWeightGrams = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.CONTAINER_WEIGHT),
                imeAction = imeActionForField(CreateMedicineField.CONTAINER_WEIGHT),
                onImeNext = onImeNextFor(CreateMedicineField.CONTAINER_WEIGHT),
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets
                    .filterIsInstance<MedicationDoseAssistPreset.GelContainerSizeGrams>(),
                onPresetClick = { preset ->
                    onMedicineDraftChange { it.applyDoseAssistPreset(preset) }
                },
            )
        }

        MedicinePreparationType.PATCH -> {
            MedicationEditorSectionLabel(stringResource(R.string.field_patch_spec_kind))
            ConnectedButtonGroup(
                options = PatchSpecKind.entries,
                selectedOption = medicineDraft.patchSpecKind,
                optionLabel = { kind ->
                    stringResource(
                        when (kind) {
                            PatchSpecKind.TOTAL_MG -> R.string.field_patch_spec_total_mg
                            PatchSpecKind.RELEASE_RATE -> R.string.field_patch_spec_release_rate
                        },
                    )
                },
                onOptionSelected = { kind ->
                    onMedicineDraftChange { it.copy(patchSpecKind = kind) }
                },
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            when (medicineDraft.patchSpecKind) {
                PatchSpecKind.TOTAL_MG -> {
                    MedicationNumericField(
                        value = medicineDraft.patchTotalMg,
                        label = medicationEditorFieldLabelWithUnit(R.string.field_patch_total_dosage_mg, rawMassUnit),
                        suffix = stringResource(rawMassUnit),
                        leadingIconRes = R.drawable.ic_chronic,
                        readOnly = readOnly,
                        isError = errorMessageRes == R.string.validation_patch_total_required,
                        errorMessageRes = R.string.validation_patch_total_required
                            .takeIf { errorMessageRes == it },
                        onValueChange = { value ->
                            onMedicineDraftChange { it.copy(patchTotalMg = value) }
                        },
                        focusRequester = focusRequesters.getValue(CreateMedicineField.PATCH_TOTAL_MG),
                        imeAction = imeActionForField(CreateMedicineField.PATCH_TOTAL_MG),
                        onImeNext = onImeNextFor(CreateMedicineField.PATCH_TOTAL_MG),
                    )
                    DoseAssistPresetRow(
                        presets = doseAssistPresets
                            .filterIsInstance<MedicationDoseAssistPreset.PatchTotalMg>(),
                        onPresetClick = { preset ->
                            onMedicineDraftChange { it.applyDoseAssistPreset(preset) }
                        },
                    )
                    CustomDoseUnitPickerForRawMassField(
                        medicineDraft = medicineDraft,
                        onMedicineDraftChange = onMedicineDraftChange,
                    )
                }

                PatchSpecKind.RELEASE_RATE -> {
                    MedicationNumericField(
                        value = medicineDraft.patchReleaseRateMcgPerDay,
                        label = medicationEditorFieldLabelWithUnit(
                            R.string.field_patch_release_rate,
                            R.string.unit_mcg_day,
                        ),
                        suffix = stringResource(R.string.unit_mcg_day),
                        leadingIconRes = R.drawable.ic_speed,
                        readOnly = readOnly,
                        isError = errorMessageRes == R.string.validation_patch_release_rate_required,
                        errorMessageRes = R.string.validation_patch_release_rate_required
                            .takeIf { errorMessageRes == it },
                        onValueChange = { value ->
                            onMedicineDraftChange { it.copy(patchReleaseRateMcgPerDay = value) }
                        },
                        focusRequester = focusRequesters.getValue(CreateMedicineField.PATCH_RELEASE_RATE),
                        imeAction = imeActionForField(CreateMedicineField.PATCH_RELEASE_RATE),
                        onImeNext = onImeNextFor(CreateMedicineField.PATCH_RELEASE_RATE),
                    )
                    DoseAssistPresetRow(
                        presets = doseAssistPresets
                            .filterIsInstance<MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay>(),
                        onPresetClick = { preset ->
                            onMedicineDraftChange { it.applyDoseAssistPreset(preset) }
                        },
                    )
                }
            }
        }

        // The PATCH_OFF singleton is never created from this sheet — it's
        // auto-spawned when a patch medicine is created. Guard with Unit so
        // the when stays exhaustive without rendering an empty form.
        MedicinePreparationType.PATCH_OFF -> Unit
    }
}

@Composable
private fun PreparationFormButtonGroup(
    options: List<MedicinePreparationForm>,
    selectedOption: MedicinePreparationForm,
    onOptionSelected: (MedicinePreparationForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConnectedButtonGroup(
        modifier = modifier.fillMaxWidth(),
        options = options,
        selectedOption = selectedOption,
        optionLabel = { form -> stringResource(preparationFormLabelRes(form)) },
        optionLeadingContent = { form ->
            Icon(
                painter = painterResource(medicinePreparationFormIconRes(form)),
                contentDescription = null,
            )
        },
        onOptionSelected = onOptionSelected,
    )
}

@StringRes
private fun preparationFormLabelRes(form: MedicinePreparationForm): Int {
    return when (form) {
        MedicinePreparationForm.TABLET -> R.string.medicine_preparation_form_tablet
        MedicinePreparationForm.CAPSULE -> R.string.medicine_preparation_form_capsule
        MedicinePreparationForm.INJECTION -> R.string.medicine_preparation_form_injection
        MedicinePreparationForm.GEL -> R.string.medicine_preparation_form_gel
        MedicinePreparationForm.PATCH -> R.string.medicine_preparation_form_patch
    }
}

@Composable
private fun CustomDoseUnitPickerForRawMassField(
    medicineDraft: MedicinePickerUiState,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
) {
    if (!medicineDraft.showsCustomDoseUnitPicker()) {
        return
    }
    CustomDoseUnitPicker(
        selectedUnit = medicineDraft.customDoseUnit,
        onUnitSelected = { unit ->
            onMedicineDraftChange { it.copy(customDoseUnit = unit) }
        },
    )
}

// Three-segment picker for the custom-medicine raw-mass unit. It sits below
// the raw-mass field so the field label and compact picker read as one unit.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomDoseUnitPicker(
    selectedUnit: MedicineDisplayDoseUnit,
    onUnitSelected: (MedicineDisplayDoseUnit) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_calibration_unit_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
        ) {
            ConnectedButtonGroup(
                options = MedicineDisplayDoseUnit.entries,
                selectedOption = selectedUnit,
                optionLabel = { unit -> stringResource(unit.shortLabelRes()) },
                onOptionSelected = onUnitSelected,
                layout = ConnectedButtonGroupLayout.ROW,
                applyCjkTextOffset = false,
            )
        }
    }
}

// The set of editable text fields in CreateMedicineSheet, in render order.
// editableFields(draft) returns the subset visible for the current draft;
// imeActionFor and nextField use that to decide each field's IME action and
// where to jump on Next.
internal enum class CreateMedicineField {
    CUSTOM_NAME,
    DISPLAY_NAME,
    PILL_STRENGTH,
    VIAL_STRENGTH,
    CONCENTRATION_MG_PER_ML,
    VIAL_VOLUME_ML,
    GEL_PERCENT,
    SACHET_WEIGHT,
    CONTAINER_WEIGHT,
    PATCH_TOTAL_MG,
    PATCH_RELEASE_RATE,
}

internal fun editableFields(draft: MedicinePickerUiState): List<CreateMedicineField> {
    val fields = mutableListOf<CreateMedicineField>()
    if (draft.requiresCustomName()) {
        fields += CreateMedicineField.CUSTOM_NAME
    } else if (draft.selectionKind == MedicationSelectionKind.CATALOG) {
        // Display-name override sits in the identity section alongside (and
        // mutually exclusive with) the custom-name field.
        fields += CreateMedicineField.DISPLAY_NAME
    }
    when (draft.inferredOrSelectedPreparationType()) {
        MedicinePreparationType.PILL,
        MedicinePreparationType.CAPSULE -> fields += CreateMedicineField.PILL_STRENGTH
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
            fields += CreateMedicineField.VIAL_STRENGTH
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> {
            fields += CreateMedicineField.CONCENTRATION_MG_PER_ML
            fields += CreateMedicineField.VIAL_VOLUME_ML
        }
        MedicinePreparationType.GEL_SACHET -> {
            fields += CreateMedicineField.GEL_PERCENT
            fields += CreateMedicineField.SACHET_WEIGHT
        }
        MedicinePreparationType.GEL_CONTAINER -> {
            fields += CreateMedicineField.GEL_PERCENT
            fields += CreateMedicineField.CONTAINER_WEIGHT
        }
        MedicinePreparationType.PATCH -> when (draft.patchSpecKind) {
            PatchSpecKind.TOTAL_MG -> fields += CreateMedicineField.PATCH_TOTAL_MG
            PatchSpecKind.RELEASE_RATE -> fields += CreateMedicineField.PATCH_RELEASE_RATE
        }
        // The PATCH_OFF singleton isn't user-creatable; the picker never
        // surfaces it, so there are no editable fields for it.
        MedicinePreparationType.PATCH_OFF -> Unit
        null -> Unit
    }
    return fields
}

internal fun createMedicineRequiredFields(draft: MedicinePickerUiState): List<CreateMedicineField> {
    return when (draft.inferredOrSelectedPreparationType()) {
        MedicinePreparationType.PILL,
        MedicinePreparationType.CAPSULE -> listOf(CreateMedicineField.PILL_STRENGTH)
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> listOf(CreateMedicineField.VIAL_STRENGTH)
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> listOf(
            CreateMedicineField.CONCENTRATION_MG_PER_ML,
            CreateMedicineField.VIAL_VOLUME_ML,
        )
        MedicinePreparationType.GEL_SACHET -> listOf(
            CreateMedicineField.GEL_PERCENT,
            CreateMedicineField.SACHET_WEIGHT,
        )
        MedicinePreparationType.GEL_CONTAINER -> listOf(
            CreateMedicineField.GEL_PERCENT,
            CreateMedicineField.CONTAINER_WEIGHT,
        )
        MedicinePreparationType.PATCH -> when (draft.patchSpecKind) {
            PatchSpecKind.TOTAL_MG -> listOf(CreateMedicineField.PATCH_TOTAL_MG)
            PatchSpecKind.RELEASE_RATE -> listOf(CreateMedicineField.PATCH_RELEASE_RATE)
        }
        MedicinePreparationType.PATCH_OFF,
        null -> emptyList()
    }
}

internal fun imeActionFor(
    editableFields: List<CreateMedicineField>,
    field: CreateMedicineField,
): ImeAction {
    return if (nextField(editableFields, field) == null) ImeAction.Done else ImeAction.Next
}

internal fun nextField(
    editableFields: List<CreateMedicineField>,
    field: CreateMedicineField,
): CreateMedicineField? {
    val index = editableFields.indexOf(field).takeIf { it >= 0 } ?: return null
    return editableFields.getOrNull(index + 1)
}
