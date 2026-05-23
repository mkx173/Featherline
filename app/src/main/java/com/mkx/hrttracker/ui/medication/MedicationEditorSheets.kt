package com.mkx.hrttracker.ui.medication

import androidx.annotation.DrawableRes
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
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.MedicalDisclaimerKind
import com.mkx.hrttracker.ui.components.MedicalDisclaimerSets
import com.mkx.hrttracker.ui.components.MedicalDisclaimerText
import com.mkx.hrttracker.ui.components.MedicationCard
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
    resolvedMedicine: Medicine?,
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
        disclaimerKinds = MedicalDisclaimerSets.medicationEditor,
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
    resolvedMedicine: Medicine?,
    canEditMedicationIdentity: Boolean,
    lockedMedicine: Medicine?,
    sourceGroupName: String? = null,
    sourceGroupColorKey: MedicationGroupColorKey? = null,
    sourceGroupScheduledFor: LocalDateTime? = null,
    sourceGroupScheduleOffsetOutsideFulfillmentWindow: Boolean = false,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    onDoseInstructionDraftChange: ((DoseInstructionDraftUiState) -> DoseInstructionDraftUiState) -> Unit,
    onOpenMedicinePicker: () -> Unit,
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
            MedicationEditorContent(
                medicineDraft = medicineDraft,
                doseInstructionDraft = doseInstructionDraft,
                resolvedMedicine = resolvedMedicine,
                canEditMedicationIdentity = true,
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
internal fun MedicationEditorSheetScaffold(
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
// Medication editor content — slot/log dose fields plus routed picker summary.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MedicationEditorContent(
    medicineDraft: MedicinePickerUiState,
    doseInstructionDraft: DoseInstructionDraftUiState?,
    resolvedMedicine: Medicine?,
    canEditMedicationIdentity: Boolean,
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
    val isPatchOff = medicineDraft.applicationType == MedicationApplicationType.PATCH_OFF
    // Once a medicine is resolved, lock the route picker to options compatible
    // with its preparation type (PILL → oral/sublingual; everything else →
    // single fixed route). PATCH_OFF is unaffected — it has no medicine.
    val applicationTypeOptions = if (resolvedMedicine != null && !isPatchOff) {
        applicationTypesCompatibleWithPreparation(resolvedMedicine.preparation.type)
    } else {
        MedicationCatalog.applicationTypesFor(medicineDraft.category)
    }

    // Category is fixed by the medicine — switching to a different category
    // would orphan the resolved medicine (changeCategory clears the selection).
    // Hide the picker entirely once a medicine is in hand; PATCH_OFF slots keep
    // it because they carry no medicine and the user must still choose ANTIANDROGEN
    // vs ESTRADIOL etc.
    if (resolvedMedicine == null) {
        EditorSectionLabel(stringResource(R.string.field_medication_category))
        ConnectedButtonGroup(
            options = editorMedicationCategories(),
            selectedOption = medicineDraft.category,
            optionLabel = { category -> stringResource(category.labelRes) },
            onOptionSelected = { category ->
                onMedicineDraftChange { it.changeCategory(category) }
            },
            enabled = canEditMedicationIdentity && !isSaving,
        )
    }

    if (applicationTypeOptions.size > 1) {
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

        EditorSectionLabel(stringResource(R.string.field_medication_application))
        ConnectedButtonGroup(
            options = applicationTypeOptions,
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
            enabled = canEditMedicationIdentity && !isSaving,
        )
    }

    if (isPatchOff) {
        // PATCH_OFF carries no medicine — no identity, preparation, or dose.
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            readOnly = true,
            label = { Text(text = stringResource(R.string.field_dosage_mg)) },
            placeholder = { Text(text = stringResource(R.string.medication_editor_patch_off_hint)) },
        )
        return
    }

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

    MedicationSummaryHeader(
        medicine = resolvedMedicine,
        applicationType = medicineDraft.applicationType,
        doseInstructionDraft = doseInstructionDraft,
        countText = countText,
        canOpenMedicinePicker = canRepickMedicine && !isSaving,
        onOpenMedicinePicker = onOpenMedicinePicker,
        errorMessageRes = errorMessageRes,
    )

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

    // The dose instruction form must render whenever the route requires per-
    // instruction dose data (VolumeMl for INJECTION_MULTI_USE_VIAL,
    // WeightGrams for GEL_CONTAINER, TabletFraction for PILL), regardless of
    // whether the user is creating a new medicine or selecting an existing one.
    // Routes whose dose is fully determined by the medicine (WholeUnit /
    // patch-off Noop) need no editable dose form. See
    // DoseInstructionDraftUiState.validationErrorRes(): the editor is otherwise
    // unsaveable when the user picks an existing multi-use vial / gel container.
    val resolvedCount = remember(countText) { parseMedicationCountText(countText) }
    val showsDoseWarning = remember(medicineDraft, doseInstructionDraft, resolvedCount) {
        doseInstructionDraft != null &&
            medicineDraft.exceedsDoseWarningThreshold(doseInstructionDraft, resolvedCount)
    }
    if (doseInstructionDraft != null &&
        requiresEditableDoseInstructionForm(doseInstructionDraft.preparationType)
    ) {
        DoseInstructionForm(
            doseInstructionDraft = doseInstructionDraft,
            onDoseInstructionDraftChange = onDoseInstructionDraftChange,
            errorMessageRes = errorMessageRes,
            showsDoseWarning = showsDoseWarning,
        )
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
private fun MedicationSummaryHeader(
    medicine: Medicine?,
    applicationType: MedicationApplicationType,
    doseInstructionDraft: DoseInstructionDraftUiState?,
    countText: String,
    canOpenMedicinePicker: Boolean,
    onOpenMedicinePicker: () -> Unit,
    errorMessageRes: Int?,
) {
    EditorSectionLabel(stringResource(R.string.field_medication))
    val resolvedCount = remember(countText) { parseMedicationCountText(countText) }
    val doseInstruction = doseInstructionDraft?.toDoseInstructionOrNull()
        ?: com.mkx.hrttracker.model.medication.DoseInstruction.Noop
    if (medicine != null) {
        MedicationCard(
            medicine = medicine,
            doseInstruction = doseInstruction,
            applicationType = applicationType,
            medicationCount = resolvedCount.coerceAtLeast(1),
            groupColorKey = null,
            onClick = onOpenMedicinePicker,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            enabled = canOpenMedicinePicker,
            // Match the medicine manager row: describe the medicine
            // (preparation summary) instead of the in-flight entry (route +
            // dose + count). The dose form below covers the rest.
            supportingTextOverride = medicinePreparationSummary(medicine),
        )
    } else {
        EditorSegmentedListItem(
            onClick = onOpenMedicinePicker,
            index = 0,
            count = 1,
            enabled = canOpenMedicinePicker,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            leadingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Label,
                    contentDescription = null,
                )
            },
            supportingContent = if (
                errorMessageRes == R.string.validation_medication_selection_required
            ) {
                {
                    Text(
                        text = stringResource(R.string.validation_medication_selection_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.medicine_picker_select_medicine),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun DoseInstructionForm(
    doseInstructionDraft: DoseInstructionDraftUiState,
    onDoseInstructionDraftChange: ((DoseInstructionDraftUiState) -> DoseInstructionDraftUiState) -> Unit,
    errorMessageRes: Int?,
    showsDoseWarning: Boolean = false,
) {
    when (doseInstructionDraft.preparationType) {
        MedicinePreparationType.PILL -> {
            val current = doseInstructionDraft.selectedTabletFractionOption()
            EditorSectionLabel(stringResource(R.string.field_dose_tablet_fraction))
            ConnectedButtonGroup(
                options = TabletFractionOption.entries,
                selectedOptions = setOf(current),
                optionLabel = { option -> option.label() },
                onOptionToggled = { selected ->
                    onDoseInstructionDraftChange {
                        it.selectTabletFraction(selected)
                    }
                },
                layout = ConnectedButtonGroupLayout.ROW,
                expandOptions = true,
            )
            if (showsDoseWarning) {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_xsmall)))
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.PATCH -> Unit // whole-unit dose; no input needed.

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> NumericField(
            value = doseInstructionDraft.volumeMl,
            label = stringResource(R.string.field_dose_volume_ml),
            suffix = stringResource(R.string.unit_ml),
            leadingIconRes = R.drawable.ic_water_drops,
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
            leadingIconRes = R.drawable.ic_weight,
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
        enabled = false,
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
    @DrawableRes leadingIconRes: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    showWarningIcon: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
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
            resolvedMedicine = null,
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
            resolvedMedicine = null,
            canEditMedicationIdentity = false,
            lockedMedicine = null,
            sourceGroupName = "Nightly estradiol",
            sourceGroupColorKey = MedicationGroupColorKey.INDIGO,
            sourceGroupScheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0),
            onMedicineDraftChange = { },
            onDoseInstructionDraftChange = { },
            onOpenMedicinePicker = { },
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
