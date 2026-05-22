package com.mkx.hrttracker.ui.medication

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.MedicalDisclaimerKind
import com.mkx.hrttracker.ui.components.MedicalDisclaimerSets
import com.mkx.hrttracker.ui.components.MedicalDisclaimerText
import com.mkx.hrttracker.ui.components.TimePickerModal
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.formatEditorZoneLabel
import com.mkx.hrttracker.util.labelRes
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import com.mkx.hrttracker.util.rememberUses24HourTimeFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

// ---------------------------------------------------------------------------
// Public sheet entry points (Task 6 Step 5).
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDefinitionEditorSheet(
    modifier: Modifier = Modifier,
    title: String,
    sheetState: SheetState,
    confirmButtonText: String,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    medicineDraft: MedicinePickerUiState,
    doseInstructionDraft: DoseInstructionDraftUiState?,
    existingMedicines: List<Medicine>,
    canEditMedicationIdentity: Boolean,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    onDoseInstructionDraftChange: ((DoseInstructionDraftUiState) -> DoseInstructionDraftUiState) -> Unit,
    onExistingMedicineSelected: (UUID) -> Unit,
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
        disclaimerKinds = MedicalDisclaimerSets.medicationEditor,
        onConfirm = onConfirm,
    ) {
        MedicinePickerContent(
            medicineDraft = medicineDraft,
            doseInstructionDraft = doseInstructionDraft,
            existingMedicines = existingMedicines,
            canEditMedicationIdentity = canEditMedicationIdentity,
            onMedicineDraftChange = onMedicineDraftChange,
            onDoseInstructionDraftChange = onDoseInstructionDraftChange,
            onExistingMedicineSelected = onExistingMedicineSelected,
            countText = countText,
            onCountTextChange = onCountTextChange,
            onDecreaseCountClick = onDecreaseCountClick,
            onIncreaseCountClick = onIncreaseCountClick,
            errorMessageRes = errorMessageRes,
            isSaving = isSaving,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationLogEntryEditorSheet(
    modifier: Modifier = Modifier,
    title: String,
    sheetState: SheetState,
    confirmButtonText: String,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    medicineDraft: MedicinePickerUiState,
    doseInstructionDraft: DoseInstructionDraftUiState?,
    existingMedicines: List<Medicine>,
    canEditMedicationIdentity: Boolean,
    lockedMedicine: Medicine?,
    sourceGroupName: String? = null,
    sourceGroupColorKey: MedicationGroupColorKey? = null,
    sourceGroupScheduledFor: LocalDateTime? = null,
    sourceGroupScheduleOffsetOutsideFulfillmentWindow: Boolean = false,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    onDoseInstructionDraftChange: ((DoseInstructionDraftUiState) -> DoseInstructionDraftUiState) -> Unit,
    onExistingMedicineSelected: (UUID) -> Unit,
    countText: String,
    onCountTextChange: (String) -> Unit,
    onDecreaseCountClick: () -> Unit,
    onIncreaseCountClick: () -> Unit,
    appliedDate: LocalDate,
    appliedTime: LocalTime,
    appliedZoneId: ZoneId = ZoneId.systemDefault(),
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
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val sourceGroupScheduledForText = sourceGroupScheduledFor?.let { scheduledFor ->
        stringResource(
            R.string.medication_editor_original_schedule,
            listOf(
                dateFormatter(scheduledFor.toLocalDate()),
                scheduledFor.toLocalTime().format(timeFormatter),
            ).joinToString(separator = " "),
        )
    }
    val sourceGroupScheduleOffset = sourceGroupScheduledFor?.let { scheduledFor ->
        medicationLogScheduleOffset(
            scheduledFor = scheduledFor,
            appliedAt = LocalDateTime.of(appliedDate, appliedTime),
        )
    }
    val sourceGroupScheduleOffsetText = sourceGroupScheduleOffset?.let { offset ->
        stringResource(offset.labelRes, offset.value)
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
        disclaimerKinds = if (canEditMedicationIdentity) {
            MedicalDisclaimerSets.medicationEditor
        } else {
            emptyList()
        },
        onConfirm = onConfirm,
    ) {
        if (canEditMedicationIdentity) {
            MedicinePickerContent(
                medicineDraft = medicineDraft,
                doseInstructionDraft = doseInstructionDraft,
                existingMedicines = existingMedicines,
                canEditMedicationIdentity = true,
                onMedicineDraftChange = onMedicineDraftChange,
                onDoseInstructionDraftChange = onDoseInstructionDraftChange,
                onExistingMedicineSelected = onExistingMedicineSelected,
                countText = countText,
                onCountTextChange = onCountTextChange,
                onDecreaseCountClick = onDecreaseCountClick,
                onIncreaseCountClick = onIncreaseCountClick,
                errorMessageRes = errorMessageRes,
                isSaving = isSaving,
            )
        } else {
            MedicationLogEntryLinkedMedicationSummary(
                lockedMedicine = lockedMedicine,
                applicationType = medicineDraft.applicationType,
                doseInstruction = doseInstructionDraft?.toDoseInstructionOrNull(),
                countText = countText,
                sourceGroupName = sourceGroupName,
                sourceGroupColorKey = sourceGroupColorKey,
                sourceGroupScheduledForText = sourceGroupScheduledForText,
                sourceGroupScheduleOffsetText = sourceGroupScheduleOffsetText,
                sourceGroupScheduleOffsetOutsideFulfillmentWindow =
                    sourceGroupScheduleOffsetOutsideFulfillmentWindow,
            )
        }

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

        MedicationLogAppliedAtFields(
            appliedDate = appliedDate,
            appliedTime = appliedTime,
            appliedDateText = dateFormatter(appliedDate),
            appliedTimeText = appliedTime.format(timeFormatter),
            appliedZoneId = appliedZoneId,
            onAppliedDateChange = onAppliedDateChange,
            onAppliedTimeChange = onAppliedTimeChange,
        )
    }
}

// ---------------------------------------------------------------------------
// Scaffold
// ---------------------------------------------------------------------------

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
    disclaimerKinds: List<MedicalDisclaimerKind> = emptyList(),
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val navigationBarBottomPadding = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.consumeWindowInsets(WindowInsets.navigationBars),
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.systemBars.only(WindowInsetsSides.Top) },
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (fillAvailableHeight) Modifier.fillMaxSize()
                    else Modifier,
                )
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimensionResource(R.dimen.padding_large),
                    end = dimensionResource(R.dimen.padding_large),
                    bottom = dimensionResource(R.dimen.padding_large) + navigationBarBottomPadding,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 10.dp, top = 4.dp),
                )
                HrtFilledTonalButton(
                    text = stringResource(R.string.cancel),
                    onClick = onCloseClick,
                    enabled = !isSaving,
                )
            }

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

            content()

            if (disclaimerKinds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                MedicalDisclaimerText(kinds = disclaimerKinds)
            }

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

            val hasDestructiveAction = destructiveButtonText != null && onDestructiveAction != null
            if (hasDestructiveAction) {
                val destructiveAction = checkNotNull(onDestructiveAction)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimensionResource(R.dimen.padding_xsmall)),
                    horizontalArrangement = Arrangement.spacedBy(
                        dimensionResource(R.dimen.padding_small),
                    ),
                ) {
                    HrtButton(
                        text = destructiveButtonText,
                        onClick = destructiveAction,
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
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

// ---------------------------------------------------------------------------
// Picker content — existing-medicine cards + "+ New" create form.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MedicinePickerContent(
    medicineDraft: MedicinePickerUiState,
    doseInstructionDraft: DoseInstructionDraftUiState?,
    existingMedicines: List<Medicine>,
    canEditMedicationIdentity: Boolean,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    onDoseInstructionDraftChange: ((DoseInstructionDraftUiState) -> DoseInstructionDraftUiState) -> Unit,
    onExistingMedicineSelected: (UUID) -> Unit,
    countText: String,
    onCountTextChange: (String) -> Unit,
    onDecreaseCountClick: () -> Unit,
    onIncreaseCountClick: () -> Unit,
    errorMessageRes: Int?,
    isSaving: Boolean,
) {
    val isPatchOff = medicineDraft.applicationType == MedicationApplicationType.PATCH_OFF

    EditorSectionLabel(stringResource(R.string.field_medication_category))
    ConnectedButtonGroup(
        options = editorMedicationCategories(),
        selectedOption = medicineDraft.category,
        optionLabel = { category -> stringResource(category.labelRes) },
        onOptionSelected = { category ->
            onMedicineDraftChange { it.changeCategory(category) }
        },
    )

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

    EditorSectionLabel(stringResource(R.string.field_medication_application))
    ConnectedButtonGroup(
        options = MedicationCatalog.applicationTypesFor(medicineDraft.category),
        selectedOption = medicineDraft.applicationType,
        optionLabel = { applicationType -> stringResource(applicationType.labelRes) },
        optionLeadingContent = { applicationType ->
            MedicationApplicationIcon(
                applicationType = applicationType,
                contentDescription = null,
                modifier = Modifier.size(ToggleButtonDefaults.IconSize),
            )
        },
        onOptionSelected = { applicationType ->
            onMedicineDraftChange { it.changeApplicationType(applicationType) }
        },
    )

    if (isPatchOff) {
        // PATCH_OFF carries no medicine — no identity, preparation, or dose.
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
            optionLabel = { medicationKey -> stringResource(medicationKey.labelRes) },
            onOptionSelected = { medicationKey ->
                onMedicineDraftChange { it.changeMedicationKey(medicationKey) }
            },
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    if (medicineDraft.requiresCustomName()) {
        OutlinedTextField(
            value = medicineDraft.customMedicationName,
            onValueChange = { value ->
                onMedicineDraftChange {
                    it.copy(customMedicationName = value, selectedMedicineUuid = null)
                }
            },
            isError = errorMessageRes == R.string.validation_name_required,
            label = { Text(text = stringResource(R.string.field_medication_name)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Label,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    // Existing medicines for the chosen catalog/custom identity.
    val matchingMedicines = remember(existingMedicines, medicineDraft) {
        existingMedicines.filter { medicine ->
            medicineMatchesPickerIdentity(medicine, medicineDraft)
        }
    }
    if (matchingMedicines.isNotEmpty()) {
        EditorSectionLabel(stringResource(R.string.medication_picker_existing_medicines))
        Column {
            matchingMedicines.forEachIndexed { index, medicine ->
                ExistingMedicineCard(
                    medicine = medicine,
                    applicationType = medicineDraft.applicationType,
                    isSelected = medicineDraft.selectedMedicineUuid == medicine.uuid,
                    index = index,
                    itemCount = matchingMedicines.size,
                    onClick = { onExistingMedicineSelected(medicine.uuid) },
                )
            }
        }
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    // "+ New" create form — only the preparation fields required by the type.
    val showsNewForm = medicineDraft.selectedMedicineUuid == null
    if (showsNewForm) {
        if (matchingMedicines.isNotEmpty()) {
            EditorSectionLabel(stringResource(R.string.medication_picker_add_new))
        }
        NewMedicineForm(
            medicineDraft = medicineDraft,
            onMedicineDraftChange = onMedicineDraftChange,
            errorMessageRes = errorMessageRes,
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

        if (doseInstructionDraft != null) {
            DoseInstructionForm(
                doseInstructionDraft = doseInstructionDraft,
                onDoseInstructionDraftChange = onDoseInstructionDraftChange,
                errorMessageRes = errorMessageRes,
            )
        }
    }

    if (medicineDraft.showsMedicationCountEditor()) {
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        MedicationCountTextField(
            value = countText,
            onValueChange = onCountTextChange,
            onDecreaseClick = onDecreaseCountClick,
            onIncreaseClick = onIncreaseCountClick,
            enabled = !isSaving,
            errorMessageRes = errorMessageRes
                ?.takeIf { it == R.string.validation_count_required },
        )
    }
}

// A picker matches an active medicine when its identity tuple lines up: catalog
// key for CATALOG; normalized custom name for CUSTOM.
internal fun medicineMatchesPickerIdentity(
    medicine: Medicine,
    draft: MedicinePickerUiState,
): Boolean {
    return when (val selection = medicine.selection) {
        is com.mkx.hrttracker.model.medication.MedicineSelection.Catalog ->
            !draft.requiresCustomName() && selection.medicationKey == draft.medicationKey

        is com.mkx.hrttracker.model.medication.MedicineSelection.Custom ->
            draft.requiresCustomName() &&
                selection.normalizedMedicationName ==
                com.mkx.hrttracker.model.medication.normalizeCustomMedicationName(
                    draft.customMedicationName,
                )
    }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExistingMedicineCard(
    medicine: Medicine,
    applicationType: MedicationApplicationType,
    isSelected: Boolean,
    index: Int,
    itemCount: Int,
    onClick: () -> Unit,
) {
    val name = medicineDisplayName(medicine)
    val summary = medicinePreparationSummary(medicine)
    EditorSegmentedListItem(
        onClick = onClick,
        index = index,
        count = itemCount,
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedicationApplicationIcon(
                applicationType = applicationType,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.cjkTextOffset(name),
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.cjkTextOffset(summary),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NewMedicineForm(
    medicineDraft: MedicinePickerUiState,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    errorMessageRes: Int?,
) {
    // Preparation-type picker — shown only when the route is ambiguous.
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
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }

    val preparationType = medicineDraft.inferredOrSelectedPreparationType() ?: return

    when (preparationType) {
        MedicinePreparationType.PILL -> NumericField(
            value = medicineDraft.pillStrengthMg,
            label = stringResource(R.string.field_pill_strength_mg),
            suffix = stringResource(R.string.unit_mg),
            isError = errorMessageRes == R.string.validation_pill_strength_required,
            errorMessageRes = R.string.validation_pill_strength_required
                .takeIf { errorMessageRes == it },
            onValueChange = { value ->
                onMedicineDraftChange { it.copy(pillStrengthMg = value) }
            },
        )

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> NumericField(
            value = medicineDraft.singleUseVialStrengthMg,
            label = stringResource(R.string.field_single_use_vial_strength_mg),
            suffix = stringResource(R.string.unit_mg),
            isError = errorMessageRes == R.string.validation_vial_strength_required,
            errorMessageRes = R.string.validation_vial_strength_required
                .takeIf { errorMessageRes == it },
            onValueChange = { value ->
                onMedicineDraftChange { it.copy(singleUseVialStrengthMg = value) }
            },
        )

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> {
            NumericField(
                value = medicineDraft.concentrationMgPerMl,
                label = stringResource(R.string.field_concentration_mg_per_ml),
                suffix = stringResource(R.string.unit_mg),
                isError = errorMessageRes == R.string.validation_concentration_required,
                errorMessageRes = R.string.validation_concentration_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(concentrationMgPerMl = value) }
                },
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            NumericField(
                value = medicineDraft.vialVolumeMl,
                label = stringResource(R.string.field_vial_volume_ml),
                isError = errorMessageRes == R.string.validation_vial_volume_required,
                errorMessageRes = R.string.validation_vial_volume_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(vialVolumeMl = value) }
                },
            )
        }

        MedicinePreparationType.GEL_SACHET -> {
            NumericField(
                value = medicineDraft.gelConcentrationPercent,
                label = stringResource(R.string.field_gel_concentration_percent),
                suffix = stringResource(R.string.unit_percent),
                isError = errorMessageRes == R.string.validation_gel_concentration_required,
                errorMessageRes = R.string.validation_gel_concentration_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(gelConcentrationPercent = value) }
                },
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            NumericField(
                value = medicineDraft.sachetWeightGrams,
                label = stringResource(R.string.field_sachet_weight_grams),
                suffix = stringResource(R.string.unit_grams),
                isError = errorMessageRes == R.string.validation_sachet_weight_required,
                errorMessageRes = R.string.validation_sachet_weight_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(sachetWeightGrams = value) }
                },
            )
        }

        MedicinePreparationType.GEL_CONTAINER -> {
            NumericField(
                value = medicineDraft.gelConcentrationPercent,
                label = stringResource(R.string.field_gel_concentration_percent),
                suffix = stringResource(R.string.unit_percent),
                isError = errorMessageRes == R.string.validation_gel_concentration_required,
                errorMessageRes = R.string.validation_gel_concentration_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(gelConcentrationPercent = value) }
                },
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            NumericField(
                value = medicineDraft.containerWeightGrams,
                label = stringResource(R.string.field_container_weight_grams),
                suffix = stringResource(R.string.unit_grams),
                isError = errorMessageRes == R.string.validation_container_weight_required,
                errorMessageRes = R.string.validation_container_weight_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(containerWeightGrams = value) }
                },
            )
        }

        MedicinePreparationType.PATCH -> {
            NumericField(
                value = medicineDraft.patchTotalMg,
                label = stringResource(R.string.field_patch_total_dosage_mg),
                suffix = stringResource(R.string.unit_mg),
                isError = errorMessageRes == R.string.validation_patch_total_required,
                errorMessageRes = R.string.validation_patch_total_required
                    .takeIf { errorMessageRes == it },
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(patchTotalMg = value) }
                },
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            NumericField(
                value = medicineDraft.patchReleaseRateMcgPerDay,
                label = stringResource(R.string.field_patch_release_rate),
                suffix = stringResource(R.string.unit_mcg_day),
                onValueChange = { value ->
                    onMedicineDraftChange { it.copy(patchReleaseRateMcgPerDay = value) }
                },
            )
        }
    }
}

@Composable
private fun DoseInstructionForm(
    doseInstructionDraft: DoseInstructionDraftUiState,
    onDoseInstructionDraftChange: ((DoseInstructionDraftUiState) -> DoseInstructionDraftUiState) -> Unit,
    errorMessageRes: Int?,
) {
    when (doseInstructionDraft.preparationType) {
        MedicinePreparationType.PILL -> {
            // Tablets-per-dose. Numerator drives the editable field; the
            // denominator stays at 1 for whole tablets (fractions reachable via
            // decimal-aware repository round-trip — UI keeps it simple here).
            NumericField(
                value = doseInstructionDraft.tabletFractionNumerator
                    .takeIf { it > 0 }?.toString().orEmpty(),
                label = stringResource(R.string.field_dose_tablet_fraction),
                isError = errorMessageRes == R.string.validation_dose_tablet_fraction_required,
                errorMessageRes = R.string.validation_dose_tablet_fraction_required
                    .takeIf { errorMessageRes == it },
                keyboardType = KeyboardType.Number,
                onValueChange = { value ->
                    val numerator = value.filter(Char::isDigit).toIntOrNull() ?: 0
                    onDoseInstructionDraftChange {
                        it.copy(tabletFractionNumerator = numerator, tabletFractionDenominator = 1)
                    }
                },
            )
        }

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.PATCH -> Unit // whole-unit dose; no input needed.

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> NumericField(
            value = doseInstructionDraft.volumeMl,
            label = stringResource(R.string.field_dose_volume_ml),
            isError = errorMessageRes == R.string.validation_dose_volume_required,
            errorMessageRes = R.string.validation_dose_volume_required
                .takeIf { errorMessageRes == it },
            onValueChange = { value ->
                onDoseInstructionDraftChange { it.copy(volumeMl = value) }
            },
        )

        MedicinePreparationType.GEL_CONTAINER -> NumericField(
            value = doseInstructionDraft.weightGrams,
            label = stringResource(R.string.field_dose_weight_grams),
            suffix = stringResource(R.string.unit_grams),
            isError = errorMessageRes == R.string.validation_dose_weight_required,
            errorMessageRes = R.string.validation_dose_weight_required
                .takeIf { errorMessageRes == it },
            onValueChange = { value ->
                onDoseInstructionDraftChange { it.copy(weightGrams = value) }
            },
        )
    }
}

internal fun preparationTypeLabelRes(preparationType: MedicinePreparationType): Int {
    return when (preparationType) {
        MedicinePreparationType.PILL -> R.string.preparation_type_pill
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
            R.string.preparation_type_injection_single_use_vial
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL ->
            R.string.preparation_type_injection_multi_use_vial
        MedicinePreparationType.GEL_SACHET -> R.string.preparation_type_gel_sachet
        MedicinePreparationType.GEL_CONTAINER -> R.string.preparation_type_gel_container
        MedicinePreparationType.PATCH -> R.string.preparation_type_patch
    }
}

private fun DoseInstructionDraftUiState.toDoseInstructionOrNull():
    com.mkx.hrttracker.model.medication.DoseInstruction? {
    return runCatching { toDoseInstruction() }.getOrNull()
}

// ---------------------------------------------------------------------------
// Linked (locked) medication summary for group-linked log edits.
// ---------------------------------------------------------------------------

@Composable
private fun MedicationLogEntryLinkedMedicationSummary(
    lockedMedicine: Medicine?,
    applicationType: MedicationApplicationType,
    doseInstruction: com.mkx.hrttracker.model.medication.DoseInstruction?,
    countText: String,
    sourceGroupName: String?,
    sourceGroupColorKey: MedicationGroupColorKey?,
    sourceGroupScheduledForText: String?,
    sourceGroupScheduleOffsetText: String?,
    sourceGroupScheduleOffsetOutsideFulfillmentWindow: Boolean,
) {
    val groupName = sourceGroupName?.takeIf(String::isNotBlank)
    val hasGroupInfo = groupName != null && sourceGroupScheduledForText != null
    val resolvedCount = remember(countText) { parseMedicationCountText(countText) }

    if (hasGroupInfo) {
        MedicationEditorGroupInfoCard(
            groupName = checkNotNull(groupName),
            groupColorKey = sourceGroupColorKey,
            scheduledForText = checkNotNull(sourceGroupScheduledForText),
            scheduleOffsetText = sourceGroupScheduleOffsetText,
            showScheduleOffsetWarning = sourceGroupScheduleOffsetOutsideFulfillmentWindow,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
    }

    com.mkx.hrttracker.ui.components.MedicationCard(
        medicine = lockedMedicine,
        doseInstruction = doseInstruction
            ?: com.mkx.hrttracker.model.medication.DoseInstruction.Noop,
        applicationType = applicationType,
        medicationCount = resolvedCount.coerceAtLeast(1),
        groupColorKey = sourceGroupColorKey,
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        index = if (hasGroupInfo) 1 else 0,
        itemCount = if (hasGroupInfo) 2 else 1,
    )
}

@Composable
private fun MedicationLogAppliedAtFields(
    appliedDate: LocalDate,
    appliedTime: LocalTime,
    appliedDateText: String,
    appliedTimeText: String,
    appliedZoneId: ZoneId = ZoneId.systemDefault(),
    onAppliedDateChange: (LocalDate) -> Unit,
    onAppliedTimeChange: (LocalTime) -> Unit,
) {
    val uses24HourFormat = rememberUses24HourTimeFormat()
    val focusManager = LocalFocusManager.current
    var showDatePickerModal by remember { mutableStateOf(false) }

    if (showDatePickerModal) {
        DatePickerModal(
            onDateSelected = onAppliedDateChange,
            onDismiss = {
                showDatePickerModal = false
                focusManager.clearFocus()
            },
            initialSelectedDate = appliedDate,
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
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (upEvent != null) {
                        showDatePickerModal = true
                    }
                }
            },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_calendar_month),
                contentDescription = stringResource(R.string.select_date),
            )
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
            onDismiss = {
                showTimePickerModal = false
                focusManager.clearFocus()
            },
            initialTime = appliedTime,
            is24Hour = uses24HourFormat,
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
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (upEvent != null) {
                        showTimePickerModal = true
                    }
                }
            },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = stringResource(R.string.select_time),
            )
        },
        singleLine = true,
    )

    val deviceZone = remember { ZoneId.systemDefault() }
    val zoneLabelLocale = rememberAppLocale()
    val pickerInstant = remember(appliedDate, appliedTime, appliedZoneId) {
        LocalDateTime.of(appliedDate, appliedTime).atZone(appliedZoneId).toInstant()
    }
    val zoneLabel = remember(pickerInstant, appliedZoneId, deviceZone, zoneLabelLocale) {
        formatEditorZoneLabel(appliedZoneId, pickerInstant, deviceZone, zoneLabelLocale)
    }
    if (zoneLabel != null) {
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_xsmall)))
        Text(
            text = zoneLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MedicationEditorGroupInfoCard(
    modifier: Modifier = Modifier,
    groupName: String,
    groupColorKey: MedicationGroupColorKey?,
    scheduledForText: String,
    scheduleOffsetText: String? = null,
    showScheduleOffsetWarning: Boolean = false,
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(colorKey = groupColorKey)

    Column {
        EditorSegmentedListItem(
            onClick = {},
            index = 0,
            count = 2,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            trailingContent = scheduleOffsetText?.let { offsetText ->
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = offsetText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.cjkTextOffset(offsetText),
                        )
                        if (showScheduleOffsetWarning) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = stringResource(
                                    R.string.medication_editor_schedule_offset_warning,
                                ),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .padding(vertical = 4.dp)
                        .fillMaxHeight()
                        .background(
                            color = groupColorScheme.primary,
                            shape = RoundedCornerShape(3.dp),
                        ),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.cjkTextOffset(groupName),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_calendar_clock),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = scheduledForText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.cjkTextOffset(scheduledForText),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable fields
// ---------------------------------------------------------------------------

@Composable
private fun NumericField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
    @StringRes errorMessageRes: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        isError = isError,
        label = { Text(text = label) },
        suffix = suffix?.let { suffixText -> { Text(text = suffixText) } },
        supportingText = errorMessageRes?.let { messageRes ->
            {
                Text(
                    text = stringResource(messageRes),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
                    onClick = { if (enabled) onDecreaseClick() },
                    enabled = stepBaseCount > 1,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.decrease_medication_count),
                    )
                }
                IconButton(onClick = { if (enabled) onIncreaseClick() }) {
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
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}

// ---------------------------------------------------------------------------
// Schedule offset (used by Plan + tests).
// ---------------------------------------------------------------------------

internal fun medicationLogScheduleOffset(
    scheduledFor: LocalDateTime,
    appliedAt: LocalDateTime,
): MedicationLogScheduleOffset? {
    val deltaMinutes = ChronoUnit.MINUTES.between(scheduledFor, appliedAt)
    if (deltaMinutes == 0L) {
        return null
    }

    val isEarly = deltaMinutes < 0
    val absoluteMinutes = kotlin.math.abs(deltaMinutes)
    val value: Long
    @StringRes val labelRes: Int

    when {
        absoluteMinutes >= MINUTES_PER_DAY -> {
            value = absoluteMinutes / MINUTES_PER_DAY
            labelRes = if (isEarly) {
                R.string.medication_editor_schedule_offset_days_earlier
            } else {
                R.string.medication_editor_schedule_offset_days_later
            }
        }

        absoluteMinutes >= MINUTES_PER_HOUR -> {
            value = absoluteMinutes / MINUTES_PER_HOUR
            labelRes = if (isEarly) {
                R.string.medication_editor_schedule_offset_hours_earlier
            } else {
                R.string.medication_editor_schedule_offset_hours_later
            }
        }

        else -> {
            value = absoluteMinutes
            labelRes = if (isEarly) {
                R.string.medication_editor_schedule_offset_minutes_earlier
            } else {
                R.string.medication_editor_schedule_offset_minutes_later
            }
        }
    }

    return MedicationLogScheduleOffset(labelRes = labelRes, value = value)
}

internal data class MedicationLogScheduleOffset(
    @param:StringRes val labelRes: Int,
    val value: Long,
)

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(
    name = "Medication Definition Editor",
    showBackground = true,
    widthDp = 420,
    heightDp = 920,
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationDefinitionEditorSheetPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        ).copy(pillStrengthMg = "2")
        MedicationDefinitionEditorSheet(
            title = "Edit medication",
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            confirmButtonText = "Save",
            onDismissRequest = { },
            onCloseClick = { },
            medicineDraft = draft,
            doseInstructionDraft = draft.toDoseInstructionDraft(),
            existingMedicines = emptyList(),
            canEditMedicationIdentity = true,
            onMedicineDraftChange = { },
            onDoseInstructionDraftChange = { },
            onExistingMedicineSelected = { },
            countText = "2",
            onCountTextChange = { },
            onDecreaseCountClick = { },
            onIncreaseCountClick = { },
            onConfirm = { },
        )
    }
}

@Preview(
    name = "Medication Log Entry Editor",
    showBackground = true,
    widthDp = 420,
    heightDp = 720,
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationLogEntryEditorSheetPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        )
        MedicationLogEntryEditorSheet(
            title = "Add entry",
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            confirmButtonText = "Save",
            onDismissRequest = { },
            onCloseClick = { },
            medicineDraft = draft,
            doseInstructionDraft = draft.toDoseInstructionDraft(),
            existingMedicines = emptyList(),
            canEditMedicationIdentity = false,
            lockedMedicine = null,
            sourceGroupName = "Nightly estradiol",
            sourceGroupColorKey = MedicationGroupColorKey.INDIGO,
            sourceGroupScheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0),
            onMedicineDraftChange = { },
            onDoseInstructionDraftChange = { },
            onExistingMedicineSelected = { },
            countText = "1",
            onCountTextChange = { },
            onDecreaseCountClick = { },
            onIncreaseCountClick = { },
            appliedDate = LocalDate.of(2026, 4, 22),
            appliedTime = LocalTime.of(20, 30),
            onAppliedDateChange = { },
            onAppliedTimeChange = { },
            onConfirm = { },
        )
    }
}
