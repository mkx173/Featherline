package com.mkx.hrttracker.ui.catalog

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.ui.catalog.stock.AdjustStockSheet
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.MedicationCardWithStockSubcard
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValues
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.hideBottomSheet
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
    data object OnboardingStockOptIn : MedicineManagerLaunchMode
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
        MedicineManagerLaunchMode.Manager,
        MedicineManagerLaunchMode.OnboardingStockOptIn ->
            MedicineManagerAddNewTarget.CreateMedicine
        is MedicineManagerLaunchMode.GroupSlot ->
            MedicineManagerAddNewTarget.NewMedicineSlot(NewMedicineSlotSheetMode.GROUP_SLOT)
        MedicineManagerLaunchMode.ManualLog ->
            MedicineManagerAddNewTarget.NewMedicineSlot(NewMedicineSlotSheetMode.MANUAL_LOG)
    }
}

@StringRes
internal fun medicineManagerTitle(
    launchMode: MedicineManagerLaunchMode,
): Int {
    return when (launchMode) {
        MedicineManagerLaunchMode.Manager,
        MedicineManagerLaunchMode.OnboardingStockOptIn -> R.string.medicines_title
        MedicineManagerLaunchMode.ManualLog,
        is MedicineManagerLaunchMode.GroupSlot -> R.string.medicine_picker_select_medicine
    }
}

internal fun medicineManagerNeedsSectionTopSpacing(sectionIndex: Int): Boolean {
    return sectionIndex > 0
}

internal fun medicineManagerNeedsRowBottomGap(index: Int, itemCount: Int): Boolean {
    return index < itemCount - 1
}

internal enum class MedicineManagerTrailingContentKind {
    NONE,
    REFERENCE_COUNT,
    CHEVRON,
}

internal fun medicineManagerTrailingContentKind(
    referenceCount: Int,
    showOnboardingChevron: Boolean = false,
): MedicineManagerTrailingContentKind {
    if (showOnboardingChevron) {
        return MedicineManagerTrailingContentKind.CHEVRON
    }
    return if (referenceCount > 0) {
        MedicineManagerTrailingContentKind.REFERENCE_COUNT
    } else {
        MedicineManagerTrailingContentKind.NONE
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MedicinesScreen(
    onNavigateBack: () -> Unit,
    onMedicineClick: (UUID) -> Unit,
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
    val stockOptInResult by viewModel.stockOptInResult.collectAsStateWithLifecycle()
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
    var pendingStockOptInUuid by rememberSaveable { mutableStateOf<String?>(null) }
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
    val stockOptInSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val saveEntryFailureMessage = stringResource(R.string.save_entry_failure)
    val stockOptInFailureMessage = stringResource(R.string.medicine_stock_update_failure)

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
                MedicineManagerLaunchMode.OnboardingStockOptIn -> {
                    pendingStockOptInUuid = medicineUuid.toString()
                }
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

    MedicinesScreenContent(
        uiState = uiState,
        titleRes = medicineManagerTitle(launchMode),
        onNavigateBack = onNavigateBack,
        onMedicineClick = handleMedicineTap,
        onAddNewMedicine = {
            when (medicineManagerAddNewTarget(launchMode)) {
                MedicineManagerAddNewTarget.CreateMedicine -> showCreateMedicineSheet = true
                is MedicineManagerAddNewTarget.NewMedicineSlot -> showNewMedicineSlotSheet = true
            }
        },
        showOnboardingBanner = launchMode == MedicineManagerLaunchMode.OnboardingStockOptIn,
        showAddNewButton = launchMode != MedicineManagerLaunchMode.OnboardingStockOptIn,
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

    val pendingMedicineItem = pendingSlotMedicineUuid?.let { uuid ->
        uiState.findMedicineItemByUuid(runCatching { UUID.fromString(uuid) }.getOrNull())
    }
    if (pendingMedicineItem != null) {
        MedicineSlotDraftSheet(
            medicine = pendingMedicineItem.medicine,
            selectedStockProjection = pendingMedicineItem.stockProjection,
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
                MedicineManagerLaunchMode.OnboardingStockOptIn,
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

    val pendingOptInItem = pendingStockOptInUuid?.let { uuid ->
        uiState.findMedicineItemByUuid(runCatching { UUID.fromString(uuid) }.getOrNull())
    }
    val pendingOptInProjection = pendingOptInItem?.stockProjection

    LaunchedEffect(
        launchMode,
        pendingStockOptInUuid,
        pendingOptInProjection,
        uiState.isLoading,
    ) {
        if (pendingStockOptInUuid == null) return@LaunchedEffect
        if (launchMode != MedicineManagerLaunchMode.OnboardingStockOptIn) {
            pendingStockOptInUuid = null
            return@LaunchedEffect
        }
        if (!uiState.isLoading && pendingOptInProjection == null) {
            pendingStockOptInUuid = null
        }
    }

    LaunchedEffect(stockOptInResult) {
        when (stockOptInResult) {
            MedicineStockMutationResult.SUCCESS -> {
                hideBottomSheet(scope, stockOptInSheetState) {
                    pendingStockOptInUuid = null
                }
                viewModel.clearStockOptInResult()
            }
            MedicineStockMutationResult.FAILURE -> {
                Toast.makeText(context, stockOptInFailureMessage, Toast.LENGTH_SHORT).show()
                viewModel.clearStockOptInResult()
            }
            null -> Unit
        }
    }

    if (
        launchMode == MedicineManagerLaunchMode.OnboardingStockOptIn &&
        pendingOptInProjection != null
    ) {
        AdjustStockSheet(
            projection = pendingOptInProjection,
            initialTab = AdjustSheetTab.RECEIVED,
            receivedOnly = true,
            previewRunway = { hypothetical ->
                viewModel.previewRunwayFor(pendingOptInProjection.medicine.uuid, hypothetical)
            },
            onRecount = { },
            onReceived = { received ->
                // Don't dismiss here: the sheet closes only after the enable
                // succeeds (observed via stockOptInResult below), so a failed
                // enable keeps the sheet open and surfaces a toast instead of
                // silently closing.
                viewModel.enableTrackingFromReceived(
                    medicineUuid = pendingOptInProjection.medicine.uuid,
                    currentUnitsRemaining = pendingOptInProjection.medicine.stock.unitsRemaining ?: 0.0,
                    received = received,
                )
            },
            sheetState = stockOptInSheetState,
            onDismissRequest = { pendingStockOptInUuid = null },
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

internal fun MedicinesUiState.findMedicineItemByUuid(
    uuid: UUID?,
): MedicineListItem? {
    uuid ?: return null
    activeSections.forEach { section ->
        section.medicines.firstOrNull { it.medicine.uuid == uuid }?.let { return it }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicinesScreenContent(
    uiState: MedicinesUiState,
    @StringRes titleRes: Int = R.string.medicines_title,
    onNavigateBack: () -> Unit,
    onMedicineClick: (UUID) -> Unit,
    onAddNewMedicine: () -> Unit,
    showOnboardingBanner: Boolean,
    showAddNewButton: Boolean,
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
                    val title = stringResource(titleRes)
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
                if (showOnboardingBanner) {
                    item(key = "onboarding-stock-banner") {
                        SupportMessageListItem(
                            text = stringResource(R.string.onboarding_stock_optin_banner),
                            painter = painterResource(R.drawable.ic_box),
                            modifier = Modifier.padding(bottom = 16.dp),
                            leadingIconSize = 22.dp
                        )
                    }
                }

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
                            topPadding = sectionIndex != 0
                        )
                    }
                    section.medicines.forEachIndexed { index, item ->
                        item(key = "medicine-${item.medicine.uuid}") {
                            MedicineRow(
                                item = item,
                                index = index,
                                itemCount = section.medicines.size,
                                onClick = { onMedicineClick(item.medicine.uuid) },
                                showOnboardingChevron = showOnboardingBanner,
                            )
                            MedicineManagerRowBottomGap(
                                index = index,
                                itemCount = section.medicines.size,
                            )
                        }
                    }
                }

                if (showAddNewButton) {
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
    topPadding: Boolean = true,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = if (topPadding) MedicineManagerSectionHeaderTopPaddingDp.dp else 0.dp,
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
    modifier: Modifier = Modifier,
    showOnboardingChevron: Boolean = false,
) {
    val medicine = item.medicine
    val applicationType = inferApplicationTypeForMedicine(medicine)
    val stockProjection = item.stockProjection?.takeUnless {
        medicine.preparation is MedicinePreparation.PatchOff
    }
    MedicationCardWithStockSubcard(
        medicine = medicine,
        doseInstruction = DoseInstruction.Noop,
        applicationType = applicationType,
        medicationCount = 1,
        groupColorKey = null,
        stockProjection = stockProjection,
        onClick = onClick,
        modifier = modifier,
        trailingContent = medicineRowTrailingContent(
            referenceCount = item.activeGroupReferenceCount,
            showOnboardingChevron = showOnboardingChevron,
        ),
        supportingTextOverride = medicinePreparationSummary(medicine),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        stockSubcardContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        leadingIconAsForm = true,
        index = index,
        itemCount = itemCount,
    )
}

private fun medicineRowTrailingContent(
    referenceCount: Int,
    showOnboardingChevron: Boolean,
): (@Composable () -> Unit)? {
    return when (
        medicineManagerTrailingContentKind(
            referenceCount = referenceCount,
            showOnboardingChevron = showOnboardingChevron,
        )
    ) {
        MedicineManagerTrailingContentKind.NONE -> null
        MedicineManagerTrailingContentKind.REFERENCE_COUNT -> {
            {
                ReferenceCountChip(count = referenceCount)
            }
        }
        MedicineManagerTrailingContentKind.CHEVRON -> {
            {
                MedicineRowChevron()
            }
        }
    }
}

@Composable
private fun MedicineRowChevron() {
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp),
    )
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

@Preview(name = "Onboarding stock opt-in banner", showBackground = true, widthDp = 420)
@Composable
private fun OnboardingStockOptInBannerPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        SupportMessageListItem(
            text = stringResource(R.string.onboarding_stock_optin_banner),
            painter = painterResource(R.drawable.ic_info),
        )
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
            onAddNewMedicine = { },
            showOnboardingBanner = false,
            showAddNewButton = true,
        )
    }
}

@Preview(name = "Onboarding stock opt-in", showBackground = true, widthDp = 420, heightDp = 760)
@Composable
private fun MedicinesScreenOnboardingPreview() {
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
            onAddNewMedicine = { },
            showOnboardingBanner = true,
            showAddNewButton = false,
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
