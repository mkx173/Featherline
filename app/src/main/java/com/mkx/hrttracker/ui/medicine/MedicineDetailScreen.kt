package com.mkx.hrttracker.ui.medicine

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.MedicationCard
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValues
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.medication.PatchSpecKind
import com.mkx.hrttracker.ui.medication.medicationEntrySupportingText
import com.mkx.hrttracker.ui.medication.medicinePreparationSummary
import java.util.UUID

@Composable
fun MedicineDetailScreen(
    onNavigateBack: () -> Unit,
    onGroupClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicineDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val saveSuccessMessage = stringResource(R.string.medicine_save_success)
    val saveLockedMessage = stringResource(R.string.medicine_locked_by_logs)
    val saveCollisionMessage = stringResource(R.string.medicine_save_identity_collision)
    val saveFailureMessage = stringResource(R.string.medicine_save_failure)
    val archiveBlockedMessage = stringResource(R.string.medicine_archive_blocked_by_groups)
    val archiveFailureMessage = stringResource(R.string.medicine_archive_failure)

    LaunchedEffect(uiState.saveResult) {
        val result = uiState.saveResult ?: return@LaunchedEffect
        val message = when (result) {
            MedicineDetailSaveResult.SUCCESS -> saveSuccessMessage
            MedicineDetailSaveResult.FAILURE_LOCKED -> saveLockedMessage
            MedicineDetailSaveResult.FAILURE_IDENTITY_COLLISION -> saveCollisionMessage
            MedicineDetailSaveResult.FAILURE_OTHER -> saveFailureMessage
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearSaveResult()
    }

    LaunchedEffect(uiState.archiveResult) {
        when (uiState.archiveResult) {
            MedicineArchiveResult.SUCCESS -> {
                viewModel.clearArchiveResult()
                onNavigateBack()
            }
            MedicineArchiveResult.FAILURE_REFERENCED_BY_ACTIVE_GROUP -> {
                Toast.makeText(context, archiveBlockedMessage, Toast.LENGTH_SHORT).show()
                viewModel.clearArchiveResult()
            }
            MedicineArchiveResult.FAILURE_OTHER -> {
                Toast.makeText(context, archiveFailureMessage, Toast.LENGTH_SHORT).show()
                viewModel.clearArchiveResult()
            }
            null -> Unit
        }
    }

    MedicineDetailScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onGroupClick = onGroupClick,
        onDisplayNameChange = viewModel::updateDisplayNameText,
        onSaveDisplayName = viewModel::saveDisplayName,
        onSavePreparation = viewModel::savePreparation,
        onArchive = { viewModel.archive() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineDetailScreenContent(
    uiState: MedicineDetailUiState,
    onNavigateBack: () -> Unit,
    onGroupClick: (UUID) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSaveDisplayName: () -> Unit,
    onSavePreparation: (MedicinePreparation) -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState,
    )
    var preparationEditorOpen by remember { mutableStateOf(false) }
    var archiveConfirmOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    listState.animateScrollToItem(0)
                },
                title = {
                    val title = stringResource(R.string.medicine_detail_title)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-2).dp),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    HrtButton(
                        text = stringResource(R.string.save),
                        onClick = onSaveDisplayName,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.padding(innerPadding)) {
            val medicine = uiState.medicine
            if (medicine == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
                return@AppContentContainer
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = appContentPaddingValues(),
            ) {
                item(key = "medicine-header") {
                    MedicineHeaderCard(medicine = medicine)
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                }

                item(key = "display-name") {
                    DisplayNameSection(
                        displayName = uiState.displayNameText,
                        isLocked = false,
                        onValueChange = onDisplayNameChange,
                    )
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                }

                item(key = "preparation") {
                    PreparationSection(
                        medicine = medicine,
                        isLocked = uiState.isLocked,
                        onEditClick = { preparationEditorOpen = true },
                    )
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                }

                item(key = "linked-groups-header") {
                    SectionHeader(text = stringResource(R.string.medicine_linked_groups))
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
                }

                if (uiState.linkedActiveSlots.isEmpty()) {
                    item(key = "linked-groups-empty") {
                        SupportMessageListItem(
                            text = stringResource(R.string.medicine_linked_groups_empty),
                            painter = painterResource(R.drawable.ic_info),
                        )
                    }
                } else {
                    uiState.linkedActiveSlots.forEachIndexed { index, row ->
                        item(key = "linked-slot-${row.group.uuid}-${row.applicationType}-$index") {
                            LinkedSlotListItem(
                                row = row,
                                medicine = medicine,
                                onClick = { onGroupClick(row.group.uuid) },
                                index = index,
                                itemCount = uiState.linkedActiveSlots.size,
                            )
                            Spacer(
                                modifier = Modifier.height(
                                    dimensionResource(R.dimen.list_segment_gap),
                                ),
                            )
                        }
                    }
                }

                item(key = "archive-action") {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_large)))
                    ArchiveAction(
                        isArchived = medicine.isArchived,
                        canArchive = uiState.linkedActiveSlots.isEmpty(),
                        linkedActiveGroupCount = uiState.linkedActiveSlots
                            .distinctBy { it.group.uuid }
                            .size,
                        onArchiveClick = { archiveConfirmOpen = true },
                    )
                }
            }
        }
    }

    if (preparationEditorOpen) {
        PreparationEditorDialog(
            medicine = uiState.medicine ?: return,
            onDismiss = { preparationEditorOpen = false },
            onSave = { preparation ->
                preparationEditorOpen = false
                onSavePreparation(preparation)
            },
        )
    }

    if (archiveConfirmOpen) {
        AlertDialog(
            onDismissRequest = { archiveConfirmOpen = false },
            title = { Text(text = stringResource(R.string.medicine_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.medicine_archive_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        archiveConfirmOpen = false
                        onArchive()
                    },
                ) {
                    Text(text = stringResource(R.string.medicine_archive_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { archiveConfirmOpen = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LinkedSlotListItem(
    row: LinkedSlotRow,
    medicine: Medicine,
    onClick: () -> Unit,
    index: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    PreferenceSegmentedListItem(
        title = row.group.name,
        supportingText = medicationEntrySupportingText(
            medicine = medicine,
            doseInstruction = row.doseInstruction,
            applicationType = row.applicationType,
            count = row.count,
        ),
        index = index,
        count = itemCount,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun MedicineHeaderCard(medicine: Medicine) {
    MedicationCard(
        medicine = medicine,
        doseInstruction = com.mkx.hrttracker.model.medication.DoseInstruction.Noop,
        applicationType = inferApplicationType(medicine),
        medicationCount = 1,
        groupColorKey = null,
        onClick = { },
        extraSupportingText = medicinePreparationSummary(medicine),
    )
}

@Composable
private fun DisplayNameSection(
    displayName: String,
    isLocked: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column {
        SectionHeader(text = stringResource(R.string.medicine_display_name))
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        OutlinedTextField(
            value = displayName,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.medicine_display_name)) },
            supportingText = {
                Text(text = stringResource(R.string.medicine_display_name_hint))
            },
            singleLine = true,
            enabled = !isLocked,
        )
    }
}

@Composable
private fun PreparationSection(
    medicine: Medicine,
    isLocked: Boolean,
    onEditClick: () -> Unit,
) {
    Column {
        SectionHeader(text = stringResource(R.string.medicine_preparation))
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        Text(
            text = medicinePreparationSummary(medicine),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (isLocked) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            Text(
                text = stringResource(R.string.medicine_locked_by_logs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        HrtFilledTonalButton(
            text = stringResource(R.string.medicine_preparation_edit),
            onClick = onEditClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLocked,
        )
    }
}

@Composable
private fun ArchiveAction(
    isArchived: Boolean,
    canArchive: Boolean,
    linkedActiveGroupCount: Int,
    onArchiveClick: () -> Unit,
) {
    if (isArchived) {
        Text(
            text = stringResource(R.string.medicine_already_archived),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (!canArchive) {
        Text(
            text = stringResource(
                R.string.medicine_archive_blocked_count,
                linkedActiveGroupCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
    }
    HrtButton(
        text = stringResource(R.string.medicine_archive_action),
        onClick = onArchiveClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = canArchive,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreparationEditorDialog(
    medicine: Medicine,
    onDismiss: () -> Unit,
    onSave: (MedicinePreparation) -> Unit,
) {
    val initialDraft = remember(medicine.uuid, medicine.preparation) {
        medicine.preparation.toPreparationDraft()
    }
    var draft by remember(initialDraft) { mutableStateOf(initialDraft) }
    val candidate = remember(draft) { draft.toPreparationOrNull() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.medicine_preparation_edit)) },
        text = {
            PreparationEditorFields(
                draft = draft,
                onDraftChange = { draft = it },
            )
        },
        confirmButton = {
            TextButton(
                enabled = candidate != null,
                onClick = { candidate?.let(onSave) },
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreparationEditorFields(
    draft: MedicinePreparationDraftUiState,
    onDraftChange: (MedicinePreparationDraftUiState) -> Unit,
) {
    // Identity is fixed for an existing medicine — the dialog never offers a
    // preparation-type switch; we only edit the numeric fields of the
    // preparation the medicine already declared. (Switching preparation type
    // would change the identityKey and is better modeled as "create a new
    // medicine" than "edit this one".)
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))) {
        when (draft.preparationType) {
            MedicinePreparationType.PILL -> NumericInputField(
                value = draft.pillStrengthMg,
                label = stringResource(R.string.field_pill_strength_mg),
                suffix = stringResource(R.string.unit_mg),
                leadingIconRes = R.drawable.ic_medication,
                onValueChange = { onDraftChange(draft.copy(pillStrengthMg = it)) },
            )

            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> NumericInputField(
                value = draft.singleUseVialStrengthMg,
                label = stringResource(R.string.field_single_use_vial_strength_mg),
                suffix = stringResource(R.string.unit_mg),
                leadingIconRes = R.drawable.ic_vaccines,
                onValueChange = { onDraftChange(draft.copy(singleUseVialStrengthMg = it)) },
            )

            MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> {
                NumericInputField(
                    value = draft.concentrationMgPerMl,
                    label = stringResource(R.string.field_concentration_mg_per_ml),
                    suffix = stringResource(R.string.unit_mg_per_ml),
                    leadingIconRes = R.drawable.ic_vaccines,
                    onValueChange = { onDraftChange(draft.copy(concentrationMgPerMl = it)) },
                )
                NumericInputField(
                    value = draft.vialVolumeMl,
                    label = stringResource(R.string.field_vial_volume_ml),
                    suffix = stringResource(R.string.unit_ml),
                    leadingIconRes = R.drawable.ic_water_drops,
                    onValueChange = { onDraftChange(draft.copy(vialVolumeMl = it)) },
                )
            }

            MedicinePreparationType.GEL_SACHET -> {
                NumericInputField(
                    value = draft.gelConcentrationPercent,
                    label = stringResource(R.string.field_gel_concentration_percent),
                    suffix = stringResource(R.string.unit_percent),
                    leadingIconRes = R.drawable.ic_humidity_percentage,
                    onValueChange = { onDraftChange(draft.copy(gelConcentrationPercent = it)) },
                )
                NumericInputField(
                    value = draft.sachetWeightGrams,
                    label = stringResource(R.string.field_sachet_weight_grams),
                    suffix = stringResource(R.string.unit_grams),
                    leadingIconRes = R.drawable.ic_weight,
                    onValueChange = { onDraftChange(draft.copy(sachetWeightGrams = it)) },
                )
            }

            MedicinePreparationType.GEL_CONTAINER -> {
                NumericInputField(
                    value = draft.gelConcentrationPercent,
                    label = stringResource(R.string.field_gel_concentration_percent),
                    suffix = stringResource(R.string.unit_percent),
                    leadingIconRes = R.drawable.ic_humidity_percentage,
                    onValueChange = { onDraftChange(draft.copy(gelConcentrationPercent = it)) },
                )
                NumericInputField(
                    value = draft.containerWeightGrams,
                    label = stringResource(R.string.field_container_weight_grams),
                    suffix = stringResource(R.string.unit_grams),
                    leadingIconRes = R.drawable.ic_weight,
                    onValueChange = { onDraftChange(draft.copy(containerWeightGrams = it)) },
                )
            }

            MedicinePreparationType.PATCH -> {
                Text(
                    text = stringResource(R.string.field_patch_spec_kind),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConnectedButtonGroup(
                    options = PatchSpecKind.entries,
                    selectedOption = draft.patchSpecKind,
                    optionLabel = { kind ->
                        stringResource(
                            when (kind) {
                                PatchSpecKind.TOTAL_MG -> R.string.field_patch_spec_total_mg
                                PatchSpecKind.RELEASE_RATE ->
                                    R.string.field_patch_spec_release_rate
                            },
                        )
                    },
                    onOptionSelected = { kind ->
                        onDraftChange(draft.copy(patchSpecKind = kind))
                    },
                )
                when (draft.patchSpecKind) {
                    PatchSpecKind.TOTAL_MG -> NumericInputField(
                        value = draft.patchTotalMg,
                        label = stringResource(R.string.field_patch_total_dosage_mg),
                        suffix = stringResource(R.string.unit_mg),
                        leadingIconRes = R.drawable.ic_medication,
                        onValueChange = { onDraftChange(draft.copy(patchTotalMg = it)) },
                    )

                    PatchSpecKind.RELEASE_RATE -> NumericInputField(
                        value = draft.patchReleaseRateMcgPerDay,
                        label = stringResource(R.string.field_patch_release_rate),
                        suffix = stringResource(R.string.unit_mcg_day),
                        leadingIconRes = R.drawable.ic_total_dissolved_solids,
                        onValueChange = {
                            onDraftChange(draft.copy(patchReleaseRateMcgPerDay = it))
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumericInputField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    suffix: String? = null,
    @androidx.annotation.DrawableRes leadingIconRes: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label) },
        suffix = suffix?.let { { Text(text = it) } },
        leadingIcon = leadingIconRes?.let { iconRes ->
            {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                )
            }
        },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
        ),
    )
}

private fun inferApplicationType(medicine: Medicine): MedicationApplicationType {
    return when (medicine.preparation) {
        is MedicinePreparation.Pill -> MedicationApplicationType.ORAL
        is MedicinePreparation.InjectionSingleUseVial,
        is MedicinePreparation.InjectionMultiUseVial -> MedicationApplicationType.INJECTION
        is MedicinePreparation.GelSachet,
        is MedicinePreparation.GelContainer -> MedicationApplicationType.GEL
        is MedicinePreparation.Patch -> MedicationApplicationType.PATCH_ON
    }
}

private fun MedicinePreparation.toPreparationDraft(): MedicinePreparationDraftUiState {
    val base = MedicinePreparationDraftUiState(preparationType = type)
    return when (this) {
        is MedicinePreparation.Pill -> base.copy(
            pillStrengthMg = strengthMgPerTablet.toEditableString(),
        )

        is MedicinePreparation.InjectionSingleUseVial -> base.copy(
            singleUseVialStrengthMg = strengthMgPerVial.toEditableString(),
        )

        is MedicinePreparation.InjectionMultiUseVial -> base.copy(
            concentrationMgPerMl = concentrationMgPerMl.toEditableString(),
            vialVolumeMl = vialVolumeMl.toEditableString(),
        )

        is MedicinePreparation.GelSachet -> base.copy(
            gelConcentrationPercent = concentrationPercent.toEditableString(),
            sachetWeightGrams = sachetWeightGrams.toEditableString(),
        )

        is MedicinePreparation.GelContainer -> base.copy(
            gelConcentrationPercent = concentrationPercent.toEditableString(),
            containerWeightGrams = containerWeightGrams.toEditableString(),
        )

        is MedicinePreparation.Patch -> when (val spec = specification) {
            is MedicinePreparation.PatchSpecification.TotalMg ->
                base.copy(
                    patchSpecKind = PatchSpecKind.TOTAL_MG,
                    patchTotalMg = spec.valueMg.toEditableString(),
                )

            is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay ->
                base.copy(
                    patchSpecKind = PatchSpecKind.RELEASE_RATE,
                    patchReleaseRateMcgPerDay = spec.valueMcgPerDay.toEditableString(),
                )
        }
    }
}

private fun MedicinePreparationDraftUiState.toPreparationOrNull(): MedicinePreparation? {
    return runCatching {
        when (preparationType) {
            MedicinePreparationType.PILL -> MedicinePreparation.Pill(
                strengthMgPerTablet = pillStrengthMg.toPositiveDoubleOrThrow(),
            )

            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
                MedicinePreparation.InjectionSingleUseVial(
                    strengthMgPerVial = singleUseVialStrengthMg.toPositiveDoubleOrThrow(),
                )

            MedicinePreparationType.INJECTION_MULTI_USE_VIAL ->
                MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = concentrationMgPerMl.toPositiveDoubleOrThrow(),
                    vialVolumeMl = vialVolumeMl.toPositiveDoubleOrThrow(),
                )

            MedicinePreparationType.GEL_SACHET -> MedicinePreparation.GelSachet(
                concentrationPercent = gelConcentrationPercent.toPositiveDoubleOrThrow(),
                sachetWeightGrams = sachetWeightGrams.toPositiveDoubleOrThrow(),
            )

            MedicinePreparationType.GEL_CONTAINER -> MedicinePreparation.GelContainer(
                concentrationPercent = gelConcentrationPercent.toPositiveDoubleOrThrow(),
                containerWeightGrams = containerWeightGrams.toPositiveDoubleOrThrow(),
            )

            MedicinePreparationType.PATCH -> MedicinePreparation.Patch(
                specification = when (patchSpecKind) {
                    PatchSpecKind.TOTAL_MG -> MedicinePreparation.PatchSpecification.TotalMg(
                        valueMg = patchTotalMg.toPositiveDoubleOrThrow(),
                    )

                    PatchSpecKind.RELEASE_RATE ->
                        MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                            valueMcgPerDay = patchReleaseRateMcgPerDay.toPositiveDoubleOrThrow(),
                        )
                },
            )
        }
    }.getOrNull()
}

private fun String.toPositiveDoubleOrThrow(): Double {
    val value = trim().toDouble()
    require(value > 0.0 && value.isFinite())
    return value
}

private fun String.toPositiveDoubleOrNull(): Double? {
    val value = trim().toDoubleOrNull() ?: return null
    return value.takeIf { it > 0.0 && it.isFinite() }
}

private fun Double.toEditableString(): String {
    return java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
}
