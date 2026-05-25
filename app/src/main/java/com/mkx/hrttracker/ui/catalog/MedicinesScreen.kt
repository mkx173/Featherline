package com.mkx.hrttracker.ui.catalog

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValues
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.medicationEntryTitle
import com.mkx.hrttracker.ui.medication.medicinePreparationIconRes
import com.mkx.hrttracker.ui.medication.medicinePreparationSummary
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.labelRes
import java.util.UUID

internal const val MedicineManagerSectionHeaderTopPaddingDp = 4
internal const val MedicineManagerSectionHeaderBottomPaddingDp = 10

internal sealed interface MedicineManagerLaunchMode {
    data object Manager : MedicineManagerLaunchMode
    data object ManualLog : MedicineManagerLaunchMode
    data class GroupSlot(val resultKey: String) : MedicineManagerLaunchMode
}

internal sealed interface MedicineManagerAddNewTarget {
    data object CreateMedicine : MedicineManagerAddNewTarget
    data class NewMedicineSlot(
        val mode: NewMedicineSlotSheetMode,
    ) : MedicineManagerAddNewTarget
}

internal fun medicineManagerLaunchMode(
    slotResultKey: String?,
    manualLogResultKey: String,
): MedicineManagerLaunchMode {
    return when {
        slotResultKey == null -> MedicineManagerLaunchMode.Manager
        slotResultKey == manualLogResultKey -> MedicineManagerLaunchMode.ManualLog
        else -> MedicineManagerLaunchMode.GroupSlot(slotResultKey)
    }
}

internal fun medicineManagerAddNewTarget(
    launchMode: MedicineManagerLaunchMode,
): MedicineManagerAddNewTarget {
    return when (launchMode) {
        MedicineManagerLaunchMode.Manager -> MedicineManagerAddNewTarget.CreateMedicine
        is MedicineManagerLaunchMode.GroupSlot ->
            MedicineManagerAddNewTarget.NewMedicineSlot(NewMedicineSlotSheetMode.GROUP_SLOT)
        MedicineManagerLaunchMode.ManualLog ->
            MedicineManagerAddNewTarget.NewMedicineSlot(NewMedicineSlotSheetMode.MANUAL_LOG)
    }
}

internal fun medicineManagerNeedsSectionTopSpacing(sectionIndex: Int): Boolean {
    return sectionIndex > 0
}

internal fun medicineManagerNeedsRowBottomGap(index: Int, itemCount: Int): Boolean {
    return index < itemCount - 1
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MedicinesScreen(
    onNavigateBack: () -> Unit,
    onMedicineClick: (UUID) -> Unit,
    onTrackClick: (UUID) -> Unit = onMedicineClick,
    modifier: Modifier = Modifier,
    launchMode: MedicineManagerLaunchMode = MedicineManagerLaunchMode.Manager,
    onSlotResolved: (MedicineSlotResult) -> Unit = { },
    onManualLogSaved: () -> Unit = { },
    viewModel: MedicinesViewModel = hiltViewModel(),
    createMedicineViewModel: CreateMedicineViewModel = hiltViewModel(),
    slotDraftViewModel: MedicineSlotDraftViewModel = hiltViewModel(),
    newMedicineSlotViewModel: NewMedicineSlotViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val slotDraftUiState by slotDraftViewModel.uiState.collectAsStateWithLifecycle()
    val newMedicineSlotUiState by newMedicineSlotViewModel.uiState.collectAsStateWithLifecycle()
    val isManualSlotLocked = launchMode == MedicineManagerLaunchMode.ManualLog &&
        (slotDraftUiState.isSaving || slotDraftUiState.isSaved)
    val isNewSlotLocked = newMedicineSlotUiState.isSaving || newMedicineSlotUiState.isSaved
    val isManualSlotLockedState = rememberUpdatedState(isManualSlotLocked)
    val isNewSlotLockedState = rememberUpdatedState(isNewSlotLocked)
    val allowManualSlotCompletionHideState = remember { mutableStateOf(false) }
    var showCreateMedicineSheet by rememberSaveable { mutableStateOf(false) }
    var showNewMedicineSlotSheet by rememberSaveable { mutableStateOf(false) }
    // The slot-result flow keeps the picked medicine in this state while the
    // dose sheet is up; clearing it dismisses the sheet.
    var pendingSlotMedicineUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val createMedicineSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val slotDraftSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            canHideManualSlotSheet(
                value = value,
                isManualSlotLocked = isManualSlotLockedState.value,
                allowManualSlotCompletionHide = allowManualSlotCompletionHideState.value,
            )
        },
    )
    val newMedicineSlotSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            canHideNewMedicineSlotSheet(
                value = value,
                isSlotLocked = isNewSlotLockedState.value,
                allowCompletionHide = allowManualSlotCompletionHideState.value,
            )
        },
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val saveEntryFailureMessage = stringResource(R.string.save_entry_failure)

    // Reset the shared draft when the sheet opens so a previously-dismissed
    // attempt doesn't leak into the next one.
    LaunchedEffect(showCreateMedicineSheet) {
        if (showCreateMedicineSheet) createMedicineViewModel.reset()
    }

    LaunchedEffect(showNewMedicineSlotSheet) {
        if (showNewMedicineSlotSheet) newMedicineSlotViewModel.reset()
    }

    val handleMedicineTap: (UUID) -> Unit = remember(
        launchMode,
        slotDraftViewModel,
        onMedicineClick,
    ) {
        { medicineUuid ->
            when (launchMode) {
                MedicineManagerLaunchMode.Manager -> onMedicineClick(medicineUuid)
                is MedicineManagerLaunchMode.GroupSlot,
                MedicineManagerLaunchMode.ManualLog -> {
                    if (launchMode == MedicineManagerLaunchMode.ManualLog) {
                        slotDraftViewModel.resetManualLogDraft()
                    }
                    pendingSlotMedicineUuid = medicineUuid.toString()
                }
            }
        }
    }
    val handleTrackTap: (UUID) -> Unit = remember(
        launchMode,
        handleMedicineTap,
        onTrackClick,
    ) {
        { medicineUuid ->
            if (launchMode == MedicineManagerLaunchMode.Manager) {
                onTrackClick(medicineUuid)
            } else {
                handleMedicineTap(medicineUuid)
            }
        }
    }

    MedicinesScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onMedicineClick = handleMedicineTap,
        onTrackClick = handleTrackTap,
        onAddNewMedicine = {
            when (medicineManagerAddNewTarget(launchMode)) {
                MedicineManagerAddNewTarget.CreateMedicine -> showCreateMedicineSheet = true
                is MedicineManagerAddNewTarget.NewMedicineSlot -> showNewMedicineSlotSheet = true
            }
        },
        modifier = modifier,
    )

    if (showCreateMedicineSheet) {
        CreateMedicineSheet(
            sheetState = createMedicineSheetState,
            onDismissRequest = { showCreateMedicineSheet = false },
            onCloseClick = {
                hideBottomSheet(scope, createMedicineSheetState) {
                    showCreateMedicineSheet = false
                }
            },
            onCreated = { _ ->
                // Manager mode stays in the medicine manager after creation; the UUID is intentionally ignored.
                hideBottomSheet(scope, createMedicineSheetState) {
                    showCreateMedicineSheet = false
                }
            },
            viewModel = createMedicineViewModel,
        )
    }

    if (showNewMedicineSlotSheet) {
        NewMedicineSlotSheet(
            sheetState = newMedicineSlotSheetState,
            onDismissRequest = {
                if (!isNewSlotLockedState.value) showNewMedicineSlotSheet = false
            },
            onCloseClick = {
                if (!isNewSlotLockedState.value) {
                    hideBottomSheet(scope, newMedicineSlotSheetState) {
                        showNewMedicineSlotSheet = false
                    }
                }
            },
            onGroupSlotResolved = { slotResult, consumeSavedState ->
                allowManualSlotCompletionHideState.value = true
                hideBottomSheet(scope, newMedicineSlotSheetState) {
                    showNewMedicineSlotSheet = false
                    allowManualSlotCompletionHideState.value = false
                    consumeSavedState()
                    onSlotResolved(slotResult)
                }
            },
            mode = (medicineManagerAddNewTarget(launchMode) as? MedicineManagerAddNewTarget.NewMedicineSlot)
                ?.mode
                ?: error("NewMedicineSlotSheet is not used in manager mode."),
            onManualLogSaved = { consumeSavedState ->
                allowManualSlotCompletionHideState.value = true
                hideBottomSheet(scope, newMedicineSlotSheetState) {
                    showNewMedicineSlotSheet = false
                    allowManualSlotCompletionHideState.value = false
                    consumeSavedState()
                    onManualLogSaved()
                }
            },
            onManualLogSaveFailure = {
                Toast.makeText(context, saveEntryFailureMessage, Toast.LENGTH_SHORT).show()
            },
            viewModel = newMedicineSlotViewModel,
        )
    }

    val pendingMedicine = pendingSlotMedicineUuid?.let { uuid ->
        uiState.findMedicineByUuid(runCatching { UUID.fromString(uuid) }.getOrNull())
    }
    if (pendingMedicine != null) {
        MedicineSlotDraftSheet(
            medicine = pendingMedicine,
            sheetState = slotDraftSheetState,
            onDismissRequest = {
                if (!isManualSlotLocked) {
                    pendingSlotMedicineUuid = null
                }
            },
            onCloseClick = {
                if (!isManualSlotLocked) {
                    hideBottomSheet(scope, slotDraftSheetState) {
                        pendingSlotMedicineUuid = null
                    }
                }
            },
            onConfirm = { slotResult ->
                hideBottomSheet(scope, slotDraftSheetState) {
                    pendingSlotMedicineUuid = null
                    onSlotResolved(slotResult)
                }
            },
            mode = when (launchMode) {
                MedicineManagerLaunchMode.ManualLog -> MedicineSlotDraftMode.MANUAL_LOG
                MedicineManagerLaunchMode.Manager,
                is MedicineManagerLaunchMode.GroupSlot -> MedicineSlotDraftMode.GROUP_SLOT
            },
            onManualLogSaved = { consumeSavedState ->
                allowManualSlotCompletionHideState.value = true
                hideBottomSheet(scope, slotDraftSheetState) {
                    pendingSlotMedicineUuid = null
                    allowManualSlotCompletionHideState.value = false
                    consumeSavedState()
                    onManualLogSaved()
                }
            },
            onManualLogSaveFailure = {
                Toast.makeText(
                    context,
                    saveEntryFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            },
            viewModel = slotDraftViewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun canHideManualSlotSheet(
    value: SheetValue,
    isManualSlotLocked: Boolean,
    allowManualSlotCompletionHide: Boolean,
): Boolean {
    return value != SheetValue.Hidden ||
        !isManualSlotLocked ||
        allowManualSlotCompletionHide
}

private fun MedicinesUiState.findMedicineByUuid(
    uuid: UUID?,
): com.mkx.hrttracker.model.medication.Medicine? {
    uuid ?: return null
    activeSections.forEach { section ->
        section.medicines.firstOrNull { it.medicine.uuid == uuid }?.let { return it.medicine }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicinesScreenContent(
    uiState: MedicinesUiState,
    onNavigateBack: () -> Unit,
    onMedicineClick: (UUID) -> Unit,
    onTrackClick: (UUID) -> Unit,
    onAddNewMedicine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState,
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    listState.animateScrollToItem(0)
                },
                title = {
                    val title = stringResource(R.string.medicines_title)
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
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isLoading) {
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
                if (uiState.activeSections.isEmpty()) {
                    item(key = "empty-state") {
                        SupportMessageListItem(
                            text = stringResource(R.string.medicines_empty_state),
                            painter = painterResource(R.drawable.ic_info),
                        )
                    }
                }

                uiState.activeSections.forEachIndexed { sectionIndex, section ->
                    item(key = "header-${section.category.name}") {
                        MedicineManagerSectionTopSpacing(sectionIndex = sectionIndex)
                        MedicineManagerSectionTitle(
                            text = stringResource(section.category.labelRes).uppercase(),
                        )
                    }
                    section.medicines.forEachIndexed { index, item ->
                        item(key = "medicine-${item.medicine.uuid}") {
                            MedicineRow(
                                item = item,
                                index = index,
                                itemCount = section.medicines.size,
                                onClick = { onMedicineClick(item.medicine.uuid) },
                                onTrackClick = { onTrackClick(item.medicine.uuid) },
                            )
                            MedicineManagerRowBottomGap(
                                index = index,
                                itemCount = section.medicines.size,
                            )
                        }
                    }
                }

                item(key = "add-new-medicine") {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
                    HrtButton(
                        text = stringResource(R.string.medicine_picker_add_new_medicine),
                        onClick = onAddNewMedicine,
                        icon = Icons.Rounded.Add,
                        iconContentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicineManagerSectionTopSpacing(sectionIndex: Int) {
    if (medicineManagerNeedsSectionTopSpacing(sectionIndex)) {
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
    }
}

@Composable
private fun MedicineManagerRowBottomGap(
    index: Int,
    itemCount: Int,
) {
    if (medicineManagerNeedsRowBottomGap(index = index, itemCount = itemCount)) {
        Spacer(
            modifier = Modifier.height(
                dimensionResource(R.dimen.list_segment_gap),
            ),
        )
    }
}

@Composable
private fun MedicineManagerSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = MedicineManagerSectionHeaderTopPaddingDp.dp,
                bottom = MedicineManagerSectionHeaderBottomPaddingDp.dp,
            ),
    )
}

@Composable
private fun MedicineRow(
    item: MedicineListItem,
    index: Int,
    itemCount: Int,
    onClick: () -> Unit,
    onTrackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val medicine = item.medicine
    val applicationType = inferApplicationTypeForMedicine(medicine)
    val stockProjection = item.stockProjection?.takeUnless {
        medicine.preparation is MedicinePreparation.PatchOff
    }
    val medicationName = medicationEntryTitle(medicine, applicationType)
    val supportingText = medicinePreparationSummary(medicine)
    val applicationTypeLabel = stringResource(applicationType.labelRes)

    EditorSegmentedListItem(
        onClick = onClick,
        index = index,
        count = itemCount,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(medicinePreparationIconRes(medicine.preparation)),
                        contentDescription = applicationTypeLabel,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicationName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.cjkTextOffset(medicationName),
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.cjkTextOffset(supportingText),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stockProjection != null) {
                    MedicineRowFuelGauge(
                        projection = stockProjection,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth(),
                    )
                }
            }
            MedicineRowTrailingContent(
                referenceCount = item.activeGroupReferenceCount,
                projection = stockProjection,
                onTrackClick = onTrackClick,
            )
        }
    }
}

@Composable
private fun MedicineRowTrailingContent(
    referenceCount: Int,
    projection: MedicineStockProjection?,
    onTrackClick: () -> Unit,
) {
    if (referenceCount <= 0 && projection == null) return
    Spacer(modifier = Modifier.width(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (referenceCount > 0) {
            ReferenceCountChip(count = referenceCount)
        }
        projection?.let {
            MedicineRowTrailingStock(
                projection = it,
                onTrackClick = onTrackClick,
            )
        }
    }
}

@Composable
private fun MedicineRowTrailingStock(
    projection: MedicineStockProjection,
    onTrackClick: () -> Unit,
) {
    when (projection.state) {
        MedicineStockState.UNTRACKED -> {
            OutlinedButton(
                onClick = onTrackClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.stock_manager_track_chip),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        MedicineStockState.NO_RUNWAY -> {
            Text(
                text = stringResource(R.string.stock_no_schedule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MedicineStockState.OUT -> {
            Text(
                text = stringResource(R.string.stock_chip_out),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        MedicineStockState.LOW -> {
            Text(
                text = stringResource(R.string.stock_chip_low),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        MedicineStockState.HEALTHY -> {
            val days = projection.runwayDays?.toInt()
            Text(
                text = if (days != null) {
                    stringResource(R.string.stock_runway_days_remaining, days)
                } else {
                    stringResource(R.string.stock_runway_unknown_title)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MedicineRowFuelGauge(
    projection: MedicineStockProjection,
    modifier: Modifier = Modifier,
) {
    if (!medicineManagerShowsFuelGauge(projection)) return
    val color = when (projection.state) {
        MedicineStockState.OUT -> MaterialTheme.colorScheme.error
        MedicineStockState.LOW -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    LinearProgressIndicator(
        progress = { medicineManagerFuelGaugeProgress(projection) },
        color = color,
        modifier = modifier.height(4.dp),
    )
}

internal fun medicineManagerShowsFuelGauge(projection: MedicineStockProjection): Boolean {
    return when (projection.state) {
        MedicineStockState.HEALTHY,
        MedicineStockState.LOW,
        MedicineStockState.OUT -> true
        MedicineStockState.UNTRACKED,
        MedicineStockState.NO_RUNWAY -> false
    }
}

internal fun medicineManagerFuelGaugeProgress(projection: MedicineStockProjection): Float {
    if (!medicineManagerShowsFuelGauge(projection)) return 0f
    val runwayDays = projection.runwayDays ?: return 0f
    val buffer = projection.medicine.stock.warnAtDaysRemaining * 3.0
    if (buffer <= 0.0) return 0f
    return (runwayDays / buffer).toFloat().coerceIn(0f, 1f)
}

@Composable
private fun ReferenceCountChip(count: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * The list view shows one row per medicine, so we pick a single representative
 * application type for the row icon. Catalog medicines have a canonical route
 * that follows from the medication key + preparation; custom medicines fall
 * back to ORAL/INJECTION/etc. by preparation type.
 */
private fun inferApplicationTypeForMedicine(
    medicine: Medicine,
): MedicationApplicationType {
    return when (medicine.preparation) {
        is MedicinePreparation.Pill ->
            MedicationApplicationType.ORAL

        is MedicinePreparation.Capsule ->
            MedicationApplicationType.ORAL

        is MedicinePreparation.InjectionSingleUseVial,
        is MedicinePreparation.InjectionMultiUseVial ->
            MedicationApplicationType.INJECTION

        is MedicinePreparation.GelSachet,
        is MedicinePreparation.GelContainer ->
            MedicationApplicationType.GEL

        is MedicinePreparation.Patch ->
            MedicationApplicationType.PATCH_ON

        // Singleton row in the manager renders with the patch-off icon.
        is MedicinePreparation.PatchOff ->
            MedicationApplicationType.PATCH_OFF
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 760)
@Composable
private fun MedicinesScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MedicinesScreenContent(
            uiState = MedicinesUiState(
                activeSections = listOf(
                    MedicineCategorySection(
                        category = MedicationCategory.ESTRADIOL,
                        medicines = listOf(
                            MedicineListItem(
                                medicine = previewMedicine(),
                                activeGroupReferenceCount = 2,
                            ),
                        ),
                    ),
                ),
            ),
            onNavigateBack = { },
            onMedicineClick = { },
            onTrackClick = { },
            onAddNewMedicine = { },
        )
    }
}

private fun previewMedicine(): com.mkx.hrttracker.model.medication.Medicine {
    val key = com.mkx.hrttracker.model.medication.MedicationKey.ESTRADIOL
    val preparation = com.mkx.hrttracker.model.medication.MedicinePreparation.Pill(
        strengthMgPerTablet = 2.0,
    )
    return com.mkx.hrttracker.model.medication.Medicine(
        uuid = UUID.fromString("00000000-0000-0000-0000-000000000010"),
        selection = com.mkx.hrttracker.model.medication.MedicineSelection.Catalog(key),
        category = key.category,
        preparation = preparation,
        displayName = null,
        identityKey = com.mkx.hrttracker.model.medication.MedicineIdentityKey.catalog(
            key, preparation,
        ),
        createdAt = java.time.Instant.EPOCH,
        updatedAt = java.time.Instant.EPOCH,
        archivedAt = null,
        stock = com.mkx.hrttracker.model.medication.MedicineStock(),
    )
}
