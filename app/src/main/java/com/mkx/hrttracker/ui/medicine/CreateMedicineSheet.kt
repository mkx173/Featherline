package com.mkx.hrttracker.ui.medicine

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.MedicalDisclaimerSets
import com.mkx.hrttracker.ui.medication.MedicationApplicationIcon
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.medication.MedicinePickerUiState
import com.mkx.hrttracker.ui.medication.PatchSpecKind
import com.mkx.hrttracker.ui.medication.ambiguousPreparationTypes
import com.mkx.hrttracker.ui.medication.availableCatalogKeys
import com.mkx.hrttracker.ui.medication.changeApplicationType
import com.mkx.hrttracker.ui.medication.changeCategory
import com.mkx.hrttracker.ui.medication.changeMedicationKey
import com.mkx.hrttracker.ui.medication.changePreparationType
import com.mkx.hrttracker.ui.medication.editorMedicationCategories
import com.mkx.hrttracker.ui.medication.inferredOrSelectedPreparationType
import com.mkx.hrttracker.ui.medication.preparationTypeLabelRes
import com.mkx.hrttracker.ui.medication.requiresCustomName
import com.mkx.hrttracker.ui.medication.requiresPreparationTypeSelection
import com.mkx.hrttracker.ui.medication.supportsCatalogSelection
import com.mkx.hrttracker.util.labelRes
import java.util.UUID

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
    MedicationEditorSheetScaffold(
        modifier = modifier,
        title = stringResource(R.string.create_medicine_title),
        sheetState = sheetState,
        confirmButtonText = stringResource(R.string.create_medicine_action),
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        fillAvailableHeight = true,
        isSaving = uiState.isSaving,
        disclaimerKinds = MedicalDisclaimerSets.medicationEditor,
        onConfirm = onCreateClick,
    ) {
        CreateMedicineResultText(saveResult = uiState.saveResult)
        CreateMedicineForm(
            medicineDraft = uiState.draft,
            onMedicineDraftChange = onDraftChange,
            errorMessageRes = uiState.errorMessageRes,
            enabled = !uiState.isSaving,
        )
    }
}

@Composable
private fun CreateMedicineResultText(saveResult: CreateMedicineSaveResult?) {
    val messageRes = when (saveResult) {
        CreateMedicineSaveResult.FAILURE_IDENTITY_COLLISION ->
            R.string.medicine_save_identity_collision

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
private fun CreateMedicineForm(
    medicineDraft: MedicinePickerUiState,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    errorMessageRes: Int?,
    enabled: Boolean,
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

    EditorSectionLabel(stringResource(R.string.field_medication_category))
    ConnectedButtonGroup(
        options = editorMedicationCategories(),
        selectedOption = medicineDraft.category,
        optionLabel = { category -> stringResource(category.labelRes) },
        onOptionSelected = { category ->
            onMedicineDraftChange { it.changeCategory(category) }
        },
        enabled = enabled,
    )

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

    EditorSectionLabel(stringResource(R.string.field_medication_application))
    ApplicationTypeButtonGroup(
        options = createMedicineApplicationTypesFor(medicineDraft.category),
        selectedOption = medicineDraft.applicationType,
        onOptionSelected = { applicationType ->
            onMedicineDraftChange { it.changeApplicationType(applicationType) }
        },
        enabled = enabled,
    )

    if (medicineDraft.applicationType == MedicationApplicationType.PATCH_OFF) {
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        Text(
            text = stringResource(R.string.medication_editor_patch_off_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

    val catalogKeys = medicineDraft.availableCatalogKeys()
    if (medicineDraft.supportsCatalogSelection() &&
        medicineDraft.selectionKind == MedicationSelectionKind.CATALOG &&
        catalogKeys.size > 1
    ) {
        EditorSectionLabel(stringResource(R.string.field_medication))
        ConnectedButtonGroup(
            options = catalogKeys,
            selectedOption = medicineDraft.medicationKey ?: catalogKeys.first(),
            optionLabel = { medicationKey: MedicationKey ->
                stringResource(medicationKey.labelRes)
            },
            onOptionSelected = { medicationKey ->
                onMedicineDraftChange { it.changeMedicationKey(medicationKey) }
            },
            enabled = enabled,
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
            enabled = enabled,
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
        enabled = enabled,
        focusRequesters = focusRequesters,
        editableFields = editableFields,
    )

    // Custom medicines already have a user-typed name; the display-name field
    // only makes sense for catalog medicines whose default name is the
    // catalog key label.
    if (medicineDraft.selectionKind == MedicationSelectionKind.CATALOG &&
        !medicineDraft.requiresCustomName()
    ) {
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        DisplayNameField(
            medicineDraft = medicineDraft,
            onMedicineDraftChange = onMedicineDraftChange,
            enabled = enabled,
            focusRequester = focusRequesters.getValue(CreateMedicineField.DISPLAY_NAME),
        )
    }
}

@Composable
private fun DisplayNameField(
    medicineDraft: MedicinePickerUiState,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
) {
    val focusManager = LocalFocusManager.current
    // Catalog medicines only: the placeholder shows the catalog key label so
    // an empty input keeps the medicine using that name everywhere it appears.
    val defaultName = medicineDraft.medicationKey
        ?.let { stringResource(it.labelRes) }
        .orEmpty()
    OutlinedTextField(
        value = medicineDraft.displayName,
        onValueChange = { value ->
            onMedicineDraftChange { it.copy(displayName = value) }
        },
        enabled = enabled,
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
        supportingText = {
            Text(text = stringResource(R.string.medicine_display_name_hint))
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}

@Composable
private fun NewMedicinePreparationForm(
    medicineDraft: MedicinePickerUiState,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    errorMessageRes: Int?,
    enabled: Boolean,
    focusRequesters: Map<CreateMedicineField, FocusRequester>,
    editableFields: List<CreateMedicineField>,
) {
    val focusManager = LocalFocusManager.current
    val onImeNextFor: (CreateMedicineField) -> () -> Unit = { field ->
        {
            nextField(editableFields, field)
                ?.let { focusRequesters.getValue(it).requestFocus() }
                ?: focusManager.clearFocus()
        }
    }
    if (medicineDraft.requiresPreparationTypeSelection()) {
        EditorSectionLabel(stringResource(R.string.field_preparation_type))
        val options = ambiguousPreparationTypes(medicineDraft.applicationType)
        ConnectedButtonGroup(
            options = options,
            selectedOption = medicineDraft.preparationType ?: options.first(),
            optionLabel = { preparationType ->
                stringResource(preparationTypeLabelRes(preparationType))
            },
            onOptionSelected = { preparationType ->
                onMedicineDraftChange { it.changePreparationType(preparationType) }
            },
            enabled = enabled,
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    when (medicineDraft.inferredOrSelectedPreparationType() ?: return) {
        MedicinePreparationType.PILL -> NumericField(
            value = medicineDraft.pillStrengthMg,
            label = stringResource(R.string.field_pill_strength_mg),
            suffix = stringResource(R.string.unit_mg),
            leadingIconRes = R.drawable.ic_medication,
            enabled = enabled,
            isError = errorMessageRes == R.string.validation_pill_strength_required,
            errorMessageRes = R.string.validation_pill_strength_required
                .takeIf { errorMessageRes == it },
            onValueChange = { value ->
                onMedicineDraftChange { it.copy(pillStrengthMg = value) }
            },
            focusRequester = focusRequesters.getValue(CreateMedicineField.PILL_STRENGTH),
            imeAction = imeActionFor(editableFields, CreateMedicineField.PILL_STRENGTH),
            onImeNext = onImeNextFor(CreateMedicineField.PILL_STRENGTH),
        )

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> NumericField(
            value = medicineDraft.singleUseVialStrengthMg,
            label = stringResource(R.string.field_single_use_vial_strength_mg),
            suffix = stringResource(R.string.unit_mg),
            leadingIconRes = R.drawable.ic_vaccines,
            enabled = enabled,
            isError = errorMessageRes == R.string.validation_vial_strength_required,
            errorMessageRes = R.string.validation_vial_strength_required
                .takeIf { errorMessageRes == it },
            onValueChange = { value ->
                onMedicineDraftChange { it.copy(singleUseVialStrengthMg = value) }
            },
            focusRequester = focusRequesters.getValue(CreateMedicineField.VIAL_STRENGTH),
            imeAction = imeActionFor(editableFields, CreateMedicineField.VIAL_STRENGTH),
            onImeNext = onImeNextFor(CreateMedicineField.VIAL_STRENGTH),
        )

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> {
            NumericField(
                value = medicineDraft.concentrationMgPerMl,
                label = stringResource(R.string.field_concentration_mg_per_ml),
                suffix = stringResource(R.string.unit_mg_per_ml),
                leadingIconRes = R.drawable.ic_humidity_percentage,
                enabled = enabled,
                isError = errorMessageRes == R.string.validation_concentration_required,
                errorMessageRes = R.string.validation_concentration_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(concentrationMgPerMl = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.CONCENTRATION_MG_PER_ML),
                imeAction = imeActionFor(editableFields, CreateMedicineField.CONCENTRATION_MG_PER_ML),
                onImeNext = onImeNextFor(CreateMedicineField.CONCENTRATION_MG_PER_ML),
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            NumericField(
                value = medicineDraft.vialVolumeMl,
                label = stringResource(R.string.field_vial_volume_ml),
                suffix = stringResource(R.string.unit_ml),
                leadingIconRes = R.drawable.ic_fluid,
                enabled = enabled,
                isError = errorMessageRes == R.string.validation_vial_volume_required,
                errorMessageRes = R.string.validation_vial_volume_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(vialVolumeMl = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.VIAL_VOLUME_ML),
                imeAction = imeActionFor(editableFields, CreateMedicineField.VIAL_VOLUME_ML),
                onImeNext = onImeNextFor(CreateMedicineField.VIAL_VOLUME_ML),
            )
        }

        MedicinePreparationType.GEL_SACHET -> {
            NumericField(
                value = medicineDraft.gelConcentrationPercent,
                label = stringResource(R.string.field_gel_concentration_percent),
                suffix = stringResource(R.string.unit_percent),
                leadingIconRes = R.drawable.ic_humidity_percentage,
                enabled = enabled,
                isError = errorMessageRes == R.string.validation_gel_concentration_required,
                errorMessageRes = R.string.validation_gel_concentration_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(gelConcentrationPercent = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.GEL_PERCENT),
                imeAction = imeActionFor(editableFields, CreateMedicineField.GEL_PERCENT),
                onImeNext = onImeNextFor(CreateMedicineField.GEL_PERCENT),
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            NumericField(
                value = medicineDraft.sachetWeightGrams,
                label = stringResource(R.string.field_sachet_weight_grams),
                suffix = stringResource(R.string.unit_grams),
                leadingIconRes = R.drawable.ic_weight,
                enabled = enabled,
                isError = errorMessageRes == R.string.validation_sachet_weight_required,
                errorMessageRes = R.string.validation_sachet_weight_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(sachetWeightGrams = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.SACHET_WEIGHT),
                imeAction = imeActionFor(editableFields, CreateMedicineField.SACHET_WEIGHT),
                onImeNext = onImeNextFor(CreateMedicineField.SACHET_WEIGHT),
            )
        }

        MedicinePreparationType.GEL_CONTAINER -> {
            NumericField(
                value = medicineDraft.gelConcentrationPercent,
                label = stringResource(R.string.field_gel_concentration_percent),
                suffix = stringResource(R.string.unit_percent),
                leadingIconRes = R.drawable.ic_humidity_percentage,
                enabled = enabled,
                isError = errorMessageRes == R.string.validation_gel_concentration_required,
                errorMessageRes = R.string.validation_gel_concentration_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(gelConcentrationPercent = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.GEL_PERCENT),
                imeAction = imeActionFor(editableFields, CreateMedicineField.GEL_PERCENT),
                onImeNext = onImeNextFor(CreateMedicineField.GEL_PERCENT),
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            NumericField(
                value = medicineDraft.containerWeightGrams,
                label = stringResource(R.string.field_container_weight_grams),
                suffix = stringResource(R.string.unit_grams),
                leadingIconRes = R.drawable.ic_weight,
                enabled = enabled,
                isError = errorMessageRes == R.string.validation_container_weight_required,
                errorMessageRes = R.string.validation_container_weight_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(containerWeightGrams = value) }
                },
                focusRequester = focusRequesters.getValue(CreateMedicineField.CONTAINER_WEIGHT),
                imeAction = imeActionFor(editableFields, CreateMedicineField.CONTAINER_WEIGHT),
                onImeNext = onImeNextFor(CreateMedicineField.CONTAINER_WEIGHT),
            )
        }

        MedicinePreparationType.PATCH -> {
            EditorSectionLabel(stringResource(R.string.field_patch_spec_kind))
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
                enabled = enabled,
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            when (medicineDraft.patchSpecKind) {
                PatchSpecKind.TOTAL_MG -> NumericField(
                    value = medicineDraft.patchTotalMg,
                    label = stringResource(R.string.field_patch_total_dosage_mg),
                    suffix = stringResource(R.string.unit_mg),
                    leadingIconRes = R.drawable.ic_chronic,
                    enabled = enabled,
                    isError = errorMessageRes == R.string.validation_patch_total_required,
                    errorMessageRes = R.string.validation_patch_total_required
                        .takeIf { errorMessageRes == it },
                    onValueChange = { value ->
                        onMedicineDraftChange { it.copy(patchTotalMg = value) }
                    },
                    focusRequester = focusRequesters.getValue(CreateMedicineField.PATCH_TOTAL_MG),
                    imeAction = imeActionFor(editableFields, CreateMedicineField.PATCH_TOTAL_MG),
                    onImeNext = onImeNextFor(CreateMedicineField.PATCH_TOTAL_MG),
                )

                PatchSpecKind.RELEASE_RATE -> NumericField(
                    value = medicineDraft.patchReleaseRateMcgPerDay,
                    label = stringResource(R.string.field_patch_release_rate),
                    suffix = stringResource(R.string.unit_mcg_day),
                    leadingIconRes = R.drawable.ic_speed,
                    enabled = enabled,
                    isError = errorMessageRes == R.string.validation_patch_release_rate_required,
                    errorMessageRes = R.string.validation_patch_release_rate_required
                        .takeIf { errorMessageRes == it },
                    onValueChange = { value ->
                        onMedicineDraftChange { it.copy(patchReleaseRateMcgPerDay = value) }
                    },
                    focusRequester = focusRequesters.getValue(CreateMedicineField.PATCH_RELEASE_RATE),
                    imeAction = imeActionFor(editableFields, CreateMedicineField.PATCH_RELEASE_RATE),
                    onImeNext = onImeNextFor(CreateMedicineField.PATCH_RELEASE_RATE),
                )
            }
        }
    }
}

private fun createMedicineApplicationTypesFor(
    category: com.mkx.hrttracker.model.medication.MedicationCategory,
): List<MedicationApplicationType> {
    return MedicationCatalog.applicationTypesFor(category)
        .filterNot { it == MedicationApplicationType.PATCH_OFF }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ApplicationTypeButtonGroup(
    options: List<MedicationApplicationType>,
    selectedOption: MedicationApplicationType,
    onOptionSelected: (MedicationApplicationType) -> Unit,
    enabled: Boolean,
) {
    // MedicationApplicationIcon wraps the icon in a Box(modifier), so the
    // modifier must constrain both axes — a height-only modifier lets the Box
    // grow to fill the ToggleButton's row, pushing the label off-screen and
    // forcing the FlowRow to wrap each button onto its own line.
    ConnectedButtonGroup(
        options = options,
        selectedOption = selectedOption,
        // PATCH_OFF is filtered out of the create-medicine picker, so "Patch
        // on" reads as an action label without a matching "off" counterpart.
        // Use the preparation name ("Patch") in this context instead.
        optionLabel = { applicationType ->
            if (applicationType == MedicationApplicationType.PATCH_ON) {
                stringResource(R.string.preparation_type_patch)
            } else {
                stringResource(applicationType.labelRes)
            }
        },
        optionLeadingContent = { applicationType ->
            MedicationApplicationIcon(
                applicationType = applicationType,
                contentDescription = null,
                modifier = Modifier.size(ToggleButtonDefaults.IconSize),
            )
        },
        onOptionSelected = onOptionSelected,
        enabled = enabled,
    )
}

@Composable
private fun EditorSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun NumericField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    @StringRes errorMessageRes: Int? = null,
    @DrawableRes leadingIconRes: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    showWarningIcon: Boolean = false,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeNext: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        isError = isError,
        label = { Text(text = label) },
        suffix = suffix?.let { suffixText -> { Text(text = suffixText) } },
        leadingIcon = leadingIconRes?.let { iconRes ->
            {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                )
            }
        },
        trailingIcon = if (showWarningIcon) {
            {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = stringResource(R.string.medication_editor_dose_warning),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        } else {
            null
        },
        supportingText = errorMessageRes?.let { messageRes ->
            {
                Text(
                    text = stringResource(messageRes),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = {
                onImeNext?.invoke() ?: focusManager.clearFocus()
            },
            onDone = { focusManager.clearFocus() },
        ),
    )
}

// The set of editable text fields in CreateMedicineSheet, in render order.
// editableFields(draft) returns the subset visible for the current draft;
// imeActionFor and nextField use that to decide each field's IME action and
// where to jump on Next.
internal enum class CreateMedicineField {
    CUSTOM_NAME,
    PILL_STRENGTH,
    VIAL_STRENGTH,
    CONCENTRATION_MG_PER_ML,
    VIAL_VOLUME_ML,
    GEL_PERCENT,
    SACHET_WEIGHT,
    CONTAINER_WEIGHT,
    PATCH_TOTAL_MG,
    PATCH_RELEASE_RATE,
    DISPLAY_NAME,
}

internal fun editableFields(draft: MedicinePickerUiState): List<CreateMedicineField> {
    if (draft.applicationType == MedicationApplicationType.PATCH_OFF) {
        return emptyList()
    }
    val fields = mutableListOf<CreateMedicineField>()
    if (draft.requiresCustomName()) {
        fields += CreateMedicineField.CUSTOM_NAME
    }
    when (draft.inferredOrSelectedPreparationType()) {
        MedicinePreparationType.PILL -> fields += CreateMedicineField.PILL_STRENGTH
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
        null -> Unit
    }
    // The display-name override is the last field — only rendered for catalog
    // medicines (custom medicines already carry a user-typed name).
    if (draft.selectionKind == MedicationSelectionKind.CATALOG &&
        !draft.requiresCustomName()
    ) {
        fields += CreateMedicineField.DISPLAY_NAME
    }
    return fields
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
