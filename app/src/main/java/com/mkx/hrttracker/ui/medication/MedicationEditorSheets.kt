package com.mkx.hrttracker.ui.medication

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDoseAssistPreset
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.MedicationCard
import com.mkx.hrttracker.ui.components.TimePickerModal
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.uses24HourTimeFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationDefinitionEditorSheet(
    modifier: Modifier = Modifier,
    title: String,
    sheetState: SheetState,
    confirmButtonText: String,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    draft: MedicationDraftUiState,
    onCategoryChange: (MedicationCategory) -> Unit,
    onApplicationTypeChange: (MedicationApplicationType) -> Unit,
    onMedicationKeyChange: (MedicationKey) -> Unit,
    onCustomMedicationNameChange: (String) -> Unit,
    onDoseKindChange: (MedicationDoseKind) -> Unit,
    onCustomDoseUnitChange: (MedicationDoseUnit) -> Unit,
    onDoseMgChange: (String) -> Unit,
    onGelPercentChange: (String) -> Unit,
    onGelWeightChange: (String) -> Unit,
    onPatchReleaseRateChange: (String) -> Unit,
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
        fillAvailableHeight = true,
        isSaving = isSaving,
        onConfirm = onConfirm
    ) {
        MedicationDraftEditorContent(
            draft = draft,
            onCategoryChange = onCategoryChange,
            onApplicationTypeChange = onApplicationTypeChange,
            onMedicationKeyChange = onMedicationKeyChange,
            onCustomMedicationNameChange = onCustomMedicationNameChange,
            onDoseKindChange = onDoseKindChange,
            onCustomDoseUnitChange = onCustomDoseUnitChange,
            onDoseMgChange = onDoseMgChange,
            onGelPercentChange = onGelPercentChange,
            onGelWeightChange = onGelWeightChange,
            onPatchReleaseRateChange = onPatchReleaseRateChange,
            countText = countText,
            onCountTextChange = onCountTextChange,
            onDecreaseCountClick = onDecreaseCountClick,
            onIncreaseCountClick = onIncreaseCountClick,
            errorMessageRes = errorMessageRes,
            isSaving = isSaving,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationLogEntryEditorSheet(
    modifier: Modifier = Modifier,
    title: String,
    sheetState: SheetState,
    confirmButtonText: String,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    draft: MedicationDraftUiState,
    canEditMedicationIdentity: Boolean,
    sourceGroupName: String? = null,
    sourceGroupColorKey: MedicationGroupColorKey? = null,
    sourceGroupScheduledFor: LocalDateTime? = null,
    onCategoryChange: (MedicationCategory) -> Unit,
    onApplicationTypeChange: (MedicationApplicationType) -> Unit,
    onMedicationKeyChange: (MedicationKey) -> Unit,
    onCustomMedicationNameChange: (String) -> Unit,
    onDoseKindChange: (MedicationDoseKind) -> Unit,
    onCustomDoseUnitChange: (MedicationDoseUnit) -> Unit,
    onDoseMgChange: (String) -> Unit,
    onGelPercentChange: (String) -> Unit,
    onGelWeightChange: (String) -> Unit,
    onPatchReleaseRateChange: (String) -> Unit,
    countText: String,
    onCountTextChange: (String) -> Unit,
    onDecreaseCountClick: () -> Unit,
    onIncreaseCountClick: () -> Unit,
    appliedDate: LocalDate,
    appliedTime: LocalTime,
    onAppliedDateChange: (LocalDate) -> Unit,
    onAppliedTimeChange: (LocalTime) -> Unit,
    errorMessageRes: Int? = null,
    isSaving: Boolean = false,
    destructiveButtonText: String? = null,
    onDestructiveAction: (() -> Unit)? = null,
    onConfirm: () -> Unit,
) {
    val appLocale = rememberAppLocale()
    val today = remember { LocalDate.now() }
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    val timeFormatter = remember(appLocale) {
        localizedShortTimeFormatter(appLocale)
    }
    val sourceGroupScheduledForText = sourceGroupScheduledFor?.let { scheduledFor ->
        stringResource(
            R.string.medication_editor_original_schedule,
            listOf(
                dateFormatter(scheduledFor.toLocalDate()),
                scheduledFor.toLocalTime().format(timeFormatter)
            ).joinToString(separator = " ")
        )
    }

    MedicationEditorSheetScaffold(
        modifier = modifier,
        title = title,
        sheetState = sheetState,
        confirmButtonText = confirmButtonText,
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        fillAvailableHeight = canEditMedicationIdentity,
        isSaving = isSaving,
        destructiveButtonText = destructiveButtonText,
        onDestructiveAction = onDestructiveAction,
        onConfirm = onConfirm
    ) {
        if (canEditMedicationIdentity) {
            MedicationDraftEditorContent(
                draft = draft,
                onCategoryChange = onCategoryChange,
                onApplicationTypeChange = onApplicationTypeChange,
                onMedicationKeyChange = onMedicationKeyChange,
                onCustomMedicationNameChange = onCustomMedicationNameChange,
                onDoseKindChange = onDoseKindChange,
                onCustomDoseUnitChange = onCustomDoseUnitChange,
                onDoseMgChange = onDoseMgChange,
                onGelPercentChange = onGelPercentChange,
                onGelWeightChange = onGelWeightChange,
                onPatchReleaseRateChange = onPatchReleaseRateChange,
                countText = countText,
                onCountTextChange = onCountTextChange,
                onDecreaseCountClick = onDecreaseCountClick,
                onIncreaseCountClick = onIncreaseCountClick,
                errorMessageRes = errorMessageRes,
                isSaving = isSaving,
            )
        } else {
            MedicationLogEntryLinkedMedicationSummary(
                draft = draft,
                countText = countText,
                sourceGroupName = sourceGroupName,
                sourceGroupColorKey = sourceGroupColorKey,
                sourceGroupScheduledForText = sourceGroupScheduledForText,
            )
        }

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

        MedicationLogAppliedAtFields(
            appliedDate = appliedDate,
            appliedTime = appliedTime,
            appliedDateText = dateFormatter(appliedDate),
            appliedTimeText = appliedTime.format(timeFormatter),
            onAppliedDateChange = onAppliedDateChange,
            onAppliedTimeChange = onAppliedTimeChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationEditorSheetScaffold(
    modifier: Modifier = Modifier,
    title: String,
    sheetState: SheetState,
    confirmButtonText: String,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    fillAvailableHeight: Boolean,
    isSaving: Boolean,
    destructiveButtonText: String? = null,
    onDestructiveAction: (() -> Unit)? = null,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (fillAvailableHeight) Modifier.fillMaxSize()
                    else Modifier
                )
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimensionResource(R.dimen.padding_large),
                    end = dimensionResource(R.dimen.padding_large),
                    bottom = dimensionResource(R.dimen.padding_medium),
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 10.dp, top = 4.dp)
                )
                HrtFilledTonalButton(
                    text = stringResource(R.string.cancel),
                    onClick = onCloseClick,
                )
            }

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

            content()

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

            val hasDestructiveAction = destructiveButtonText != null && onDestructiveAction != null
            if (hasDestructiveAction) {
                val destructiveAction = checkNotNull(onDestructiveAction)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimensionResource(R.dimen.padding_xsmall)),
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
                ) {
                    HrtButton(
                        text = destructiveButtonText,
                        onClick = destructiveAction,
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                    )
                    HrtButton(
                        text = confirmButtonText,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                    )
                }
            } else {
                HrtButton(
                    text = confirmButtonText,
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimensionResource(R.dimen.padding_xsmall)),
                    enabled = !isSaving,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MedicationDraftEditorContent(
    draft: MedicationDraftUiState,
    onCategoryChange: (MedicationCategory) -> Unit,
    onApplicationTypeChange: (MedicationApplicationType) -> Unit,
    onMedicationKeyChange: (MedicationKey) -> Unit,
    onCustomMedicationNameChange: (String) -> Unit,
    onDoseKindChange: (MedicationDoseKind) -> Unit,
    onCustomDoseUnitChange: (MedicationDoseUnit) -> Unit,
    onDoseMgChange: (String) -> Unit,
    onGelPercentChange: (String) -> Unit,
    onGelWeightChange: (String) -> Unit,
    onPatchReleaseRateChange: (String) -> Unit,
    countText: String,
    onCountTextChange: (String) -> Unit,
    onDecreaseCountClick: () -> Unit,
    onIncreaseCountClick: () -> Unit,
    errorMessageRes: Int?,
    isSaving: Boolean,
) {
    val fieldErrors = remember(draft, errorMessageRes) {
        resolveMedicationEditorFieldErrors(draft, errorMessageRes)
    }
    val catalog = remember(draft.category, draft.applicationType) {
        MedicationCatalog.catalogFor(draft.category, draft.applicationType)
    }
    val doseAssistPresets = remember(draft) { draft.activeDoseAssistPresets() }
    val resolvedCount = remember(countText) { parseMedicationCountText(countText) }
    val showsDoseWarning = remember(draft, resolvedCount) {
        draft.exceedsDoseWarningThreshold(resolvedCount)
    }
    val showsCountEditor = remember(draft) {
        draft.showsMedicationCountEditor()
    }
    val customNameFocusRequester = remember { FocusRequester() }
    val doseMgFocusRequester = remember { FocusRequester() }
    val gelPercentFocusRequester = remember { FocusRequester() }
    val gelWeightFocusRequester = remember { FocusRequester() }
    val patchReleaseRateFocusRequester = remember { FocusRequester() }
    val editableTextFields = remember(draft) {
        structuredMedicationEditorEditableFields(draft)
    }
    val textFieldFocusRequesters = remember(
        customNameFocusRequester,
        doseMgFocusRequester,
        gelPercentFocusRequester,
        gelWeightFocusRequester,
        patchReleaseRateFocusRequester,
    ) {
        mapOf(
            StructuredMedicationEditorTextField.CUSTOM_NAME to customNameFocusRequester,
            StructuredMedicationEditorTextField.DOSE_MG to doseMgFocusRequester,
            StructuredMedicationEditorTextField.GEL_PERCENT to gelPercentFocusRequester,
            StructuredMedicationEditorTextField.GEL_WEIGHT to gelWeightFocusRequester,
            StructuredMedicationEditorTextField.PATCH_RELEASE_RATE to patchReleaseRateFocusRequester,
        )
    }

    Text(
        text = stringResource(R.string.field_medication_category),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
    ConnectedButtonGroup(
        options = editorMedicationCategories(),
        selectedOption = draft.category,
        optionLabel = { category -> stringResource(category.labelRes) },
        onOptionSelected = onCategoryChange,
    )

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

    Text(
        text = stringResource(R.string.field_medication_application),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
    ConnectedButtonGroup(
        options = MedicationCatalog.applicationTypesFor(draft.category),
        selectedOption = draft.applicationType,
        optionLabel = { applicationType -> stringResource(applicationType.labelRes) },
        optionLeadingContent = { applicationType ->
            MedicationApplicationIcon(
                applicationType = applicationType,
                contentDescription = null,
                modifier = Modifier.size(ToggleButtonDefaults.IconSize),
            )
        },
        onOptionSelected = onApplicationTypeChange,
    )

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

    if (draft.supportsCatalogSelection()
        && draft.selectionKind == MedicationSelectionKind.CATALOG
        && catalog.entries.mapNotNull { it.medicationKey }.size > 1) {
        val medicationOptions = catalog.entries.mapNotNull { it.medicationKey }
        if (medicationOptions.size > 1) {
            Text(
                text = stringResource(R.string.field_medication),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            ConnectedButtonGroup(
                options = medicationOptions,
                selectedOption = draft.selectedCatalogEntry().medicationKey,
                optionLabel = { medicationKey -> stringResource(medicationKey.labelRes) },
                onOptionSelected = onMedicationKeyChange,
            )

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        }
    }

    if (draft.requiresCustomName()) {
        val field = StructuredMedicationEditorTextField.CUSTOM_NAME
        val focusManager = LocalFocusManager.current
        val nextFocusRequester = structuredMedicationEditorNextField(
            editableFields = editableTextFields,
            field = field
        )?.let(textFieldFocusRequesters::getValue)
        OutlinedTextField(
            value = draft.customMedicationName,
            onValueChange = onCustomMedicationNameChange,
            isError = fieldErrors.customName != null,
            label = {
                Text(text = stringResource(R.string.field_medication_name))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Label,
                    contentDescription = null
                )
            },
            supportingText = fieldErrors.customName?.let { errorMessageRes ->
                {
                    Text(
                        text = stringResource(errorMessageRes),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(textFieldFocusRequesters.getValue(field)),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = structuredMedicationEditorImeAction(
                    editableFields = editableTextFields,
                    field = field
                )
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    if (nextFocusRequester != null) {
                        nextFocusRequester.requestFocus()
                    } else {
                        focusManager.clearFocus()
                    }
                },
                onDone = { focusManager.clearFocus() }
            )
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    if (draft.applicationType == MedicationApplicationType.GEL) {
        Text(
            text = stringResource(R.string.field_gel_application_area),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        ConnectedButtonGroup(
            options = MedicationGelApplicationArea.entries,
            selectedOption = MedicationGelApplicationArea.DEFAULT,
            optionLabel = { area -> stringResource(area.labelRes) },
            onOptionSelected = {},
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    if (draft.availableDoseKinds().size > 1) {
        Text(
            text = stringResource(R.string.field_dose_type),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        ConnectedButtonGroup(
            options = draft.availableDoseKinds(),
            selectedOption = draft.doseKind,
            optionLabel = { doseKind -> stringResource(doseKindLabelRes(doseKind)) },
            onOptionSelected = onDoseKindChange,
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    when (draft.doseKind) {
        MedicationDoseKind.MG_AS_MEDICINE -> {
            val field = StructuredMedicationEditorTextField.DOSE_MG
            DoseTextField(
                value = draft.doseMg,
                painterRes = doseFieldPainterRes(
                    applicationType = draft.applicationType,
                    doseKind = draft.doseKind
                ),
                onValueChange = onDoseMgChange,
                label = stringResource(R.string.field_dosage_mg),
                suffix = stringResource(draft.displayDoseUnit().shortLabelRes),
                errorMessageRes = fieldErrors.doseMg,
                showWarningIcon = showsDoseWarning,
                focusRequester = textFieldFocusRequesters.getValue(field),
                nextFocusRequester = structuredMedicationEditorNextField(
                    editableFields = editableTextFields,
                    field = field
                )?.let(textFieldFocusRequesters::getValue),
                imeAction = structuredMedicationEditorImeAction(
                    editableFields = editableTextFields,
                    field = field
                )
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets,
                onPresetClick = { preset ->
                    val resolvedPreset = preset as MedicationDoseAssistPreset.MgAsMedicine
                    onDoseMgChange(resolvedPreset.valueMg)
                }
            )
            if (draft.showsCustomDoseUnitSelector()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_calibration_unit_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                    ) {
                        ConnectedButtonGroup(
                            options = MedicationDoseUnit.entries,
                            selectedOption = draft.customDoseUnit,
                            optionLabel = { unit -> stringResource(unit.shortLabelRes) },
                            onOptionSelected = onCustomDoseUnitChange,
                            layout = ConnectedButtonGroupLayout.ROW,
                            applyCjkTextOffset = false,
                        )
                    }
                }
            }
        }

        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG -> {
            val field = StructuredMedicationEditorTextField.DOSE_MG
            DoseTextField(
                value = draft.doseMg,
                painterRes = doseFieldPainterRes(
                    applicationType = draft.applicationType,
                    doseKind = draft.doseKind
                ),
                onValueChange = onDoseMgChange,
                label = stringResource(R.string.field_equivalent_estradiol_mg),
                suffix = stringResource(R.string.unit_mg),
                errorMessageRes = fieldErrors.doseMg,
                focusRequester = textFieldFocusRequesters.getValue(field),
                nextFocusRequester = structuredMedicationEditorNextField(
                    editableFields = editableTextFields,
                    field = field
                )?.let(textFieldFocusRequesters::getValue),
                imeAction = structuredMedicationEditorImeAction(
                    editableFields = editableTextFields,
                    field = field
                )
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets,
                onPresetClick = { preset ->
                    val resolvedPreset =
                        preset as MedicationDoseAssistPreset.GelEquivalentEstradiolMg
                    onDoseMgChange(resolvedPreset.valueMg)
                }
            )
        }

        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT -> {
            val percentField = StructuredMedicationEditorTextField.GEL_PERCENT
            DoseTextField(
                value = draft.gelPercent,
                painterRes = doseFieldPainterRes(
                    applicationType = draft.applicationType,
                    doseKind = draft.doseKind,
                    gelField = GelDoseField.PERCENT
                ),
                onValueChange = onGelPercentChange,
                label = stringResource(R.string.field_gel_percent),
                suffix = stringResource(R.string.unit_percent),
                errorMessageRes = fieldErrors.gelPercent,
                focusRequester = textFieldFocusRequesters.getValue(percentField),
                nextFocusRequester = structuredMedicationEditorNextField(
                    editableFields = editableTextFields,
                    field = percentField
                )?.let(textFieldFocusRequesters::getValue),
                imeAction = structuredMedicationEditorImeAction(
                    editableFields = editableTextFields,
                    field = percentField
                )
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets.filterIsInstance<MedicationDoseAssistPreset.GelPercent>(),
                onPresetClick = { preset ->
                    val resolvedPreset =
                        preset as MedicationDoseAssistPreset.GelPercent
                    onGelPercentChange(resolvedPreset.percent)
                }
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            val weightField = StructuredMedicationEditorTextField.GEL_WEIGHT
            DoseTextField(
                value = draft.gelWeightGrams,
                painterRes = doseFieldPainterRes(
                    applicationType = draft.applicationType,
                    doseKind = draft.doseKind,
                    gelField = GelDoseField.WEIGHT
                ),
                onValueChange = onGelWeightChange,
                label = stringResource(R.string.field_gel_weight_grams),
                suffix = stringResource(R.string.unit_grams),
                errorMessageRes = fieldErrors.gelWeight,
                focusRequester = textFieldFocusRequesters.getValue(weightField),
                nextFocusRequester = structuredMedicationEditorNextField(
                    editableFields = editableTextFields,
                    field = weightField
                )?.let(textFieldFocusRequesters::getValue),
                imeAction = structuredMedicationEditorImeAction(
                    editableFields = editableTextFields,
                    field = weightField
                )
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets.filterIsInstance<MedicationDoseAssistPreset.GelWeightGrams>(),
                onPresetClick = { preset ->
                    val resolvedPreset =
                        preset as MedicationDoseAssistPreset.GelWeightGrams
                    onGelWeightChange(resolvedPreset.weightGrams)
                }
            )
        }

        MedicationDoseKind.PATCH_TOTAL_MG -> {
            val field = StructuredMedicationEditorTextField.DOSE_MG
            DoseTextField(
                value = draft.doseMg,
                painterRes = doseFieldPainterRes(
                    applicationType = draft.applicationType,
                    doseKind = draft.doseKind
                ),
                onValueChange = onDoseMgChange,
                label = stringResource(R.string.field_patch_total_mg),
                suffix = stringResource(R.string.unit_mg),
                errorMessageRes = fieldErrors.doseMg,
                focusRequester = textFieldFocusRequesters.getValue(field),
                nextFocusRequester = structuredMedicationEditorNextField(
                    editableFields = editableTextFields,
                    field = field
                )?.let(textFieldFocusRequesters::getValue),
                imeAction = structuredMedicationEditorImeAction(
                    editableFields = editableTextFields,
                    field = field
                )
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets,
                onPresetClick = { preset ->
                    val resolvedPreset = preset as MedicationDoseAssistPreset.PatchTotalMg
                    onDoseMgChange(resolvedPreset.valueMg)
                }
            )
        }

        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY -> {
            val field = StructuredMedicationEditorTextField.PATCH_RELEASE_RATE
            DoseTextField(
                value = draft.patchReleaseRateMcgPerDay,
                painterRes = doseFieldPainterRes(
                    applicationType = draft.applicationType,
                    doseKind = draft.doseKind
                ),
                onValueChange = onPatchReleaseRateChange,
                label = stringResource(R.string.field_patch_release_rate_mcg_day),
                suffix = stringResource(R.string.unit_mcg_day),
                errorMessageRes = fieldErrors.patchReleaseRate,
                focusRequester = textFieldFocusRequesters.getValue(field),
                nextFocusRequester = structuredMedicationEditorNextField(
                    editableFields = editableTextFields,
                    field = field
                )?.let(textFieldFocusRequesters::getValue),
                imeAction = structuredMedicationEditorImeAction(
                    editableFields = editableTextFields,
                    field = field
                )
            )
            DoseAssistPresetRow(
                presets = doseAssistPresets,
                onPresetClick = { preset ->
                    val resolvedPreset =
                        preset as MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay
                    onPatchReleaseRateChange(resolvedPreset.valueMcgPerDay)
                }
            )
        }

        MedicationDoseKind.NONE -> {
            DoseTextField(
                value = "",
                painterRes = doseFieldPainterRes(
                    applicationType = draft.applicationType,
                    doseKind = draft.doseKind
                ),
                onValueChange = {},
                label = stringResource(R.string.field_dosage_mg),
                placeholder = stringResource(R.string.medication_editor_patch_off_hint),
                enabled = false
            )
        }
    }

    if (showsCountEditor) {
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        MedicationCountTextField(
            value = countText,
            onValueChange = onCountTextChange,
            onDecreaseClick = onDecreaseCountClick,
            onIncreaseClick = onIncreaseCountClick,
            enabled = !isSaving,
            errorMessageRes = fieldErrors.count,
        )
    }
}

@Composable
private fun MedicationLogEntryLinkedMedicationSummary(
    draft: MedicationDraftUiState,
    countText: String,
    sourceGroupName: String?,
    sourceGroupColorKey: MedicationGroupColorKey?,
    sourceGroupScheduledForText: String?,
) {
    val groupName = sourceGroupName?.takeIf(String::isNotBlank)
    val hasGroupInfo = groupName != null && sourceGroupScheduledForText != null
    val resolvedCount = remember(countText) { parseMedicationCountText(countText) }

    if (hasGroupInfo) {
        MedicationEditorGroupInfoCard(
            groupName = checkNotNull(groupName),
            groupColorKey = sourceGroupColorKey,
            scheduledForText = checkNotNull(sourceGroupScheduledForText),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
    }

    MedicationCard(
        details = draft.toMedicationDetails(),
        medicationCount = resolvedCount.coerceAtLeast(1),
        groupColorKey = sourceGroupColorKey,
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        index = if (hasGroupInfo) 1 else 0,
        itemCount = if (hasGroupInfo) 2 else 1
    )
}

@Composable
private fun MedicationLogAppliedAtFields(
    appliedDate: LocalDate,
    appliedTime: LocalTime,
    appliedDateText: String,
    appliedTimeText: String,
    onAppliedDateChange: (LocalDate) -> Unit,
    onAppliedTimeChange: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    var showDatePickerModal by remember { mutableStateOf(false) }

    if (showDatePickerModal) {
        DatePickerModal(
            onDateSelected = onAppliedDateChange,
            onDismiss = { showDatePickerModal = false },
            initialSelectedDate = appliedDate
        )
    }

    OutlinedTextField(
        value = appliedDateText,
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.field_date_of_application)) },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(appliedDate) {
                awaitEachGesture {
                    // Modifier.clickable doesn't work for text fields, so observe pointer
                    // events before the text field consumes them in the Main pass.
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (upEvent != null) {
                        showDatePickerModal = true
                    }
                }
            },
        leadingIcon = {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = stringResource(R.string.select_date))
        },
        singleLine = true,
    )

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

    var showTimePickerModal by remember { mutableStateOf(false) }

    if (showTimePickerModal) {
        TimePickerModal(
            onTimeSelected = { selectedTime ->
                onAppliedTimeChange(selectedTime)
                true
            },
            onDismiss = { showTimePickerModal = false },
            initialTime = appliedTime,
            is24Hour = context.uses24HourTimeFormat()
        )
    }

    OutlinedTextField(
        value = appliedTimeText,
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.field_time_of_application)) },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(appliedTime) {
                awaitEachGesture {
                    // Modifier.clickable doesn't work for text fields, so observe pointer
                    // events before the text field consumes them in the Main pass.
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (upEvent != null) {
                        showTimePickerModal = true
                    }
                }
            },
        leadingIcon = {
            Icon(Icons.Rounded.AccessTime, contentDescription = stringResource(R.string.select_time))
        },
        singleLine = true,
    )
}

@Composable
private fun MedicationEditorGroupInfoCard(
    groupName: String,
    groupColorKey: MedicationGroupColorKey?,
    scheduledForText: String,
    modifier: Modifier = Modifier,
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(groupColorKey)

    Column {
        EditorSegmentedListItem(
            onClick = {},
            index = 0,
            count = 2,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .padding(vertical = 4.dp)
                        .fillMaxHeight()
                        .background(
                            color = groupColorScheme.primary,
                            shape = RoundedCornerShape(3.dp)
                        )
                )
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.cjkTextOffset(groupName)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_calendar_clock),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = scheduledForText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.cjkTextOffset(scheduledForText)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseTextField(
    value: String,
    @DrawableRes painterRes: Int,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String? = null,
    placeholder: String? = null,
    @StringRes errorMessageRes: Int? = null,
    showWarningIcon: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Done,
) {
    val focusManager = LocalFocusManager.current
    val resolvedValue = resolveDoseTextFieldValue(
        value = value,
        placeholder = placeholder,
        enabled = enabled
    )
    OutlinedTextField(
        value = resolvedValue,
        onValueChange = onValueChange,
        enabled = enabled,
        isError = errorMessageRes != null,
        label = { Text(text = label) },
        suffix = suffix?.let { suffixText -> { Text(text = suffixText) } },
        placeholder = resolveDoseTextFieldPlaceholder(
            value = value,
            placeholder = placeholder,
            enabled = enabled
        )?.let { placeholderText -> { Text(text = placeholderText) } },
        trailingIcon = if (showWarningIcon) {
            {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = stringResource(R.string.medication_editor_dose_warning),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        } else {
            null
        },
        leadingIcon = {
            Icon(
                painter = painterResource(painterRes),
                contentDescription = null,
            )
        },
        supportingText = errorMessageRes?.let { messageRes ->
            {
                Text(
                    text = stringResource(messageRes),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = {
                if (nextFocusRequester != null) {
                    nextFocusRequester.requestFocus()
                } else {
                    focusManager.clearFocus()
                }
            },
            onDone = { focusManager.clearFocus() }
        )
    )
}

@Composable
private fun MedicationCountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onDecreaseClick: () -> Unit,
    onIncreaseClick: () -> Unit,
    enabled: Boolean,
    @StringRes errorMessageRes: Int? = null,
) {
    val focusManager = LocalFocusManager.current
    val stepBaseCount = countStepBase(value)
    var textFieldValue by remember(value) {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
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
            textFieldValue = updatedValue.copy(
                text = sanitizedValue,
                selection = selection
            )
            onValueChange(sanitizedValue)
        },
        label = { Text(text = stringResource(R.string.field_count)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Tag,
                contentDescription = null
            )
        },
        isError = errorMessageRes != null,
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                IconButton(
                    onClick = onDecreaseClick,
                    enabled = enabled && stepBaseCount > 1,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.decrease_medication_count),
                    )
                }
                IconButton(
                    onClick = onIncreaseClick,
                    enabled = enabled,
                ) {
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
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    textFieldValue = textFieldValue.copy(
                        selection = TextRange(0, textFieldValue.text.length)
                    )
                }
            },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        )
    )
}

internal enum class StructuredMedicationEditorTextField {
    CUSTOM_NAME,
    DOSE_MG,
    GEL_PERCENT,
    GEL_WEIGHT,
    PATCH_RELEASE_RATE,
}

internal fun structuredMedicationEditorEditableFields(
    draft: MedicationDraftUiState,
): List<StructuredMedicationEditorTextField> {
    val fields = mutableListOf<StructuredMedicationEditorTextField>()
    if (draft.requiresCustomName()) {
        fields += StructuredMedicationEditorTextField.CUSTOM_NAME
    }
    when (draft.doseKind) {
        MedicationDoseKind.MG_AS_MEDICINE,
        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG,
        MedicationDoseKind.PATCH_TOTAL_MG -> {
            fields += StructuredMedicationEditorTextField.DOSE_MG
        }

        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT -> {
            fields += StructuredMedicationEditorTextField.GEL_PERCENT
            fields += StructuredMedicationEditorTextField.GEL_WEIGHT
        }

        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY -> {
            fields += StructuredMedicationEditorTextField.PATCH_RELEASE_RATE
        }

        MedicationDoseKind.NONE -> Unit
    }
    return fields
}

internal fun structuredMedicationEditorImeAction(
    editableFields: List<StructuredMedicationEditorTextField>,
    field: StructuredMedicationEditorTextField,
): ImeAction {
    return if (structuredMedicationEditorNextField(editableFields, field) == null) {
        ImeAction.Done
    } else {
        ImeAction.Next
    }
}

internal fun structuredMedicationEditorNextField(
    editableFields: List<StructuredMedicationEditorTextField>,
    field: StructuredMedicationEditorTextField,
): StructuredMedicationEditorTextField? {
    return editableFields
        .indexOf(field)
        .takeIf { it >= 0 }
        ?.let { index -> editableFields.getOrNull(index + 1) }
}

internal fun resolveDoseTextFieldValue(
    value: String,
    placeholder: String?,
    enabled: Boolean,
): String {
    return if (!enabled && value.isEmpty() && !placeholder.isNullOrEmpty()) {
        placeholder
    } else {
        value
    }
}

internal fun resolveDoseTextFieldPlaceholder(
    value: String,
    placeholder: String?,
    enabled: Boolean,
): String? {
    return if (enabled && value.isEmpty()) {
        placeholder
    } else {
        null
    }
}

@Composable
private fun DoseAssistPresetRow(
    presets: List<MedicationDoseAssistPreset>,
    onPresetClick: (MedicationDoseAssistPreset) -> Unit,
    enabled: Boolean = true,
) {
    if (presets.isEmpty() || !enabled) {
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
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified
        ) {
            presets.forEach { preset ->
                AssistChip(
                    onClick = { onPresetClick(preset) },
                    label = {
                        Text(
                            text = doseAssistPresetLabel(preset),
                            modifier = Modifier.cjkTextOffset(doseAssistPresetLabel(preset))
                        )
                    }
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
            preset.valueMg
        )

        is MedicationDoseAssistPreset.GelEquivalentEstradiolMg -> stringResource(
            R.string.medication_editor_dose_assist_mg,
            preset.valueMg
        )

        is MedicationDoseAssistPreset.GelPercent -> stringResource(
            R.string.medication_editor_dose_assist_percent,
            preset.percent,
        )

        is MedicationDoseAssistPreset.GelWeightGrams -> stringResource(
            R.string.medication_editor_dose_assist_grams,
            preset.weightGrams
        )

        is MedicationDoseAssistPreset.PatchTotalMg -> stringResource(
            R.string.medication_editor_dose_assist_mg,
            preset.valueMg
        )

        is MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay -> stringResource(
            R.string.medication_editor_dose_assist_mcg_day,
            preset.valueMcgPerDay
        )
    }
}

internal enum class GelDoseField {
    PERCENT,
    WEIGHT,
}

@DrawableRes
internal fun doseFieldPainterRes(
    applicationType: MedicationApplicationType,
    doseKind: MedicationDoseKind,
    gelField: GelDoseField? = null,
): Int {
    return when (doseKind) {
        MedicationDoseKind.MG_AS_MEDICINE -> {
            if (applicationType == MedicationApplicationType.INJECTION) {
                R.drawable.ic_vaccines
            } else {
                R.drawable.ic_medication
            }
        }

        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG -> R.drawable.ic_total_dissolved_solids
        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT -> when (gelField) {
            GelDoseField.PERCENT -> R.drawable.ic_humidity_percentage
            GelDoseField.WEIGHT -> R.drawable.ic_weight
            null -> R.drawable.ic_humidity_percentage
        }

        MedicationDoseKind.PATCH_TOTAL_MG -> R.drawable.ic_chronic
        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY -> R.drawable.ic_speed
        MedicationDoseKind.NONE -> R.drawable.ic_remove_selection
    }
}

@Composable
private fun PickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { onClick() }
        )
    }
}

private fun doseKindLabelRes(doseKind: MedicationDoseKind): Int {
    return when (doseKind) {
        MedicationDoseKind.MG_AS_MEDICINE -> R.string.dose_type_mg_as_medicine
        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG -> R.string.dose_type_gel_equivalent_estradiol_mg
        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT -> R.string.dose_type_gel_percent_and_weight
        MedicationDoseKind.PATCH_TOTAL_MG -> R.string.dose_type_patch_total_mg
        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY -> R.string.dose_type_patch_release_rate_mcg_day
        MedicationDoseKind.NONE -> R.string.medication_application_patch_off
    }
}

internal data class MedicationEditorFieldErrors(
    @param:StringRes val customName: Int? = null,
    @param:StringRes val doseMg: Int? = null,
    @param:StringRes val gelPercent: Int? = null,
    @param:StringRes val gelWeight: Int? = null,
    @param:StringRes val patchReleaseRate: Int? = null,
    @param:StringRes val count: Int? = null,
)

internal fun resolveMedicationEditorFieldErrors(
    draft: MedicationDraftUiState,
    @StringRes errorMessageRes: Int?,
): MedicationEditorFieldErrors {
    if (errorMessageRes == null) {
        return MedicationEditorFieldErrors()
    }

    val validationErrors = draft.validationErrors().toSet()
    return MedicationEditorFieldErrors(
        customName = validationErrors.firstOrNull { it == R.string.validation_name_required },
        doseMg = validationErrors.firstOrNull { it == R.string.validation_dose_required },
        gelPercent = validationErrors.firstOrNull {
            it == R.string.validation_gel_percent_required
        },
        gelWeight = validationErrors.firstOrNull {
            it == R.string.validation_gel_weight_required
        },
        patchReleaseRate = validationErrors.firstOrNull {
            it == R.string.validation_patch_release_rate_required
        },
        count = if (errorMessageRes == R.string.validation_count_required) {
            errorMessageRes
        } else {
            null
        },
    )
}

@Preview(
    name = "Medication Editor Group Info Card",
    showBackground = true,
    widthDp = 420
)
@Composable
private fun MedicationEditorGroupInfoCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MedicationEditorGroupInfoCard(
            groupName = "Nightly estradiol",
            groupColorKey = MedicationGroupColorKey.INDIGO,
            scheduledForText = stringResource(
                R.string.medication_editor_original_schedule,
                "Apr 22, 2026 21:00"
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(
    name = "Medication Definition Editor",
    showBackground = true,
    widthDp = 420,
    heightDp = 920
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationDefinitionEditorSheetPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MedicationDefinitionEditorSheet(
            title = "Edit medication",
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            confirmButtonText = "Save",
            onDismissRequest = { },
            onCloseClick = { },
            draft = defaultMedicationDraft(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.GEL
            ).changeMedicationKey(MedicationKey.ESTRADIOL_GEL).changeDoseKind(
                MedicationDoseKind.GEL_PERCENT_AND_WEIGHT
            ).copy(
                gelPercent = "0.06",
                gelWeightGrams = "2.5"
            ),
            onCategoryChange = { },
            onApplicationTypeChange = { },
            onMedicationKeyChange = { },
            onCustomMedicationNameChange = { },
            onDoseKindChange = { },
            onCustomDoseUnitChange = { },
            onDoseMgChange = { },
            onGelPercentChange = { },
            onGelWeightChange = { },
            onPatchReleaseRateChange = { },
            countText = "2",
            onCountTextChange = { },
            onDecreaseCountClick = { },
            onIncreaseCountClick = { },
            onConfirm = { }
        )
    }
}

@Preview(
    name = "Medication Log Entry Editor",
    showBackground = true,
    widthDp = 420,
    heightDp = 720
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationLogEntryEditorSheetPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MedicationLogEntryEditorSheet(
            title = "Add entry",
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            confirmButtonText = "Save",
            onDismissRequest = { },
            onCloseClick = { },
            draft = defaultMedicationDraft(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL
            ).copy(doseMg = "2"),
            canEditMedicationIdentity = false,
            sourceGroupName = "Nightly estradiol",
            sourceGroupColorKey = MedicationGroupColorKey.INDIGO,
            sourceGroupScheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0),
            onCategoryChange = { },
            onApplicationTypeChange = { },
            onMedicationKeyChange = { },
            onCustomMedicationNameChange = { },
            onDoseKindChange = { },
            onCustomDoseUnitChange = { },
            onDoseMgChange = { },
            onGelPercentChange = { },
            onGelWeightChange = { },
            onPatchReleaseRateChange = { },
            countText = "1",
            onCountTextChange = { },
            onDecreaseCountClick = { },
            onIncreaseCountClick = { },
            appliedDate = LocalDate.of(2026, 4, 22),
            appliedTime = LocalTime.of(20, 30),
            onAppliedDateChange = { },
            onAppliedTimeChange = { },
            onConfirm = { }
        )
    }
}
