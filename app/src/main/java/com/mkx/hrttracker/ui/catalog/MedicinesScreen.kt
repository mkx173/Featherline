package com.mkx.hrttracker.ui.catalog

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.reminder.PostLogStockWarning
import com.mkx.hrttracker.ui.catalog.stock.AdjustStockSheet
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.MedicationCardWithStockSubcard
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.hazeChrome
import com.mkx.hrttracker.ui.components.hazeTopAppBarColors
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.dismissInputAndRunWhenHidden
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.inputIsVisibleOrAnimating
import com.mkx.hrttracker.ui.medication.medicinePreparationSummary
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.labelRes
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

internal sealed interface MedicineManagerLaunchMode {
    data object Manager : MedicineManagerLaunchMode
    data object ManualLog : MedicineManagerLaunchMode
    data class GroupSlot(val resultKey: String) : MedicineManagerLaunchMode
    data object OnboardingStockOptIn : MedicineManagerLaunchMode
}

internal sealed interface MedicineManagerAddNewTarget {
    data object CreateMedicine : MedicineManagerAddNewTarget
    data class NewMedicineSlot(
        val mode: CreateMedicineThenDoseSheetMode,
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
            MedicineManagerAddNewTarget.NewMedicineSlot(CreateMedicineThenDoseSheetMode.GROUP_SLOT)

        MedicineManagerLaunchMode.ManualLog ->
            MedicineManagerAddNewTarget.NewMedicineSlot(CreateMedicineThenDoseSheetMode.MANUAL_LOG)
    }
}

@StringRes
internal fun medicineManagerTitle(
    launchMode: MedicineManagerLaunchMode,
): Int {
    return when (launchMode) {
        MedicineManagerLaunchMode.Manager,
        MedicineManagerLaunchMode.OnboardingStockOptIn -> R.string.medicines_title

        MedicineManagerLaunchMode.ManualLog -> R.string.medicine_manager_manual_log_title
        is MedicineManagerLaunchMode.GroupSlot -> R.string.medicine_picker_select_medicine
    }
}

internal fun medicineManagerNeedsSectionTopSpacing(sectionIndex: Int): Boolean {
    return sectionIndex > 0
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

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
internal fun MedicinesScreen(
    onNavigateBack: () -> Unit,
    onMedicineClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    launchMode: MedicineManagerLaunchMode = MedicineManagerLaunchMode.Manager,
    onSlotResolved: (MedicineSlotResult) -> Unit = { },
    onManualLogSaved: (PostLogStockWarning?) -> Unit = { },
    onNewMedicineCreated: (UUID) -> Unit = { },
    stockNudgeEnabled: Boolean = true,
    onSetStockNudgeEnabled: (Boolean) -> Unit = { },
    viewModel: MedicinesViewModel = hiltViewModel(),
    createMedicineViewModel: CreateMedicineViewModel = hiltViewModel(),
    slotDraftViewModel: MedicineSlotDraftViewModel = hiltViewModel(),
    newMedicineSlotViewModel: NewMedicineSlotViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stockOptInResult by viewModel.stockOptInResult.collectAsStateWithLifecycle()
    val slotDraftUiState by slotDraftViewModel.uiState.collectAsStateWithLifecycle()
    val newMedicineSlotUiState by newMedicineSlotViewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val inputVisibleOrAnimatingState = rememberUpdatedState(
        inputIsVisibleOrAnimating(
            imeVisible = WindowInsets.isImeVisible,
            imeBottom = WindowInsets.ime.getBottom(density),
            imeAnimationTargetBottom = WindowInsets.imeAnimationTarget.getBottom(density),
        )
    )
    val isManualSlotLocked = launchMode == MedicineManagerLaunchMode.ManualLog &&
            (slotDraftUiState.isSaving || slotDraftUiState.isSaved)
    val isNewSlotLocked = newMedicineSlotUiState.isSaving || newMedicineSlotUiState.isSaved
    val isManualSlotLockedState = rememberUpdatedState(isManualSlotLocked)
    val isNewSlotLockedState = rememberUpdatedState(isNewSlotLocked)
    val allowManualSlotCompletionHideState = remember { mutableStateOf(false) }
    var showCreateMedicineSheet by rememberSaveable { mutableStateOf(false) }
    var showCreateMedicineThenDoseSheet by rememberSaveable { mutableStateOf(false) }
    // The slot-result flow keeps the picked medicine in this state while the
    // dose sheet is up; clearing it dismisses the sheet.
    var pendingSlotMedicineUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingStockOptInUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var isStockOptInProjectionFrozen by remember { mutableStateOf(false) }
    var frozenStockOptInProjection by remember { mutableStateOf<MedicineStockProjection?>(null) }
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
    val createMedicineThenDoseSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            canHideCreateMedicineThenDoseSheet(
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
    val awaitInputHidden: suspend () -> Unit = {
        snapshotFlow { inputVisibleOrAnimatingState.value }
            .filter { !it }
            .first()
    }

    // Reset the shared draft when the sheet opens so a previously-dismissed
    // attempt doesn't leak into the next one.
    LaunchedEffect(showCreateMedicineSheet) {
        if (showCreateMedicineSheet) createMedicineViewModel.reset()
    }

    LaunchedEffect(showCreateMedicineThenDoseSheet) {
        if (showCreateMedicineThenDoseSheet) newMedicineSlotViewModel.reset()
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
                is MedicineManagerAddNewTarget.NewMedicineSlot -> showCreateMedicineThenDoseSheet =
                    true
            }
        },
        showOnboardingBanner = launchMode == MedicineManagerLaunchMode.OnboardingStockOptIn,
        showAddNewButton = launchMode != MedicineManagerLaunchMode.OnboardingStockOptIn,
        showStockNudgeMenu = launchMode == MedicineManagerLaunchMode.Manager,
        stockNudgeEnabled = stockNudgeEnabled,
        onSetStockNudgeEnabled = onSetStockNudgeEnabled,
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
            onCreated = { createdUuid ->
                // Manager mode opens the newly-created medicine's detail page so
                // the user can immediately set stock / fine-tune it.
                scope.launch {
                    dismissInputAndRunWhenHidden(
                        focusManager = focusManager,
                        keyboardController = keyboardController,
                        isInputVisible = { inputVisibleOrAnimatingState.value },
                        awaitInputHidden = awaitInputHidden,
                    ) {
                        hideBottomSheet(scope, createMedicineSheetState) {
                            showCreateMedicineSheet = false
                            scope.launch {
                                dismissInputAndRunWhenHidden(
                                    focusManager = focusManager,
                                    keyboardController = keyboardController,
                                    isInputVisible = { inputVisibleOrAnimatingState.value },
                                    awaitInputHidden = awaitInputHidden,
                                ) {
                                    onMedicineClick(createdUuid)
                                }
                            }
                        }
                    }
                }
            },
            viewModel = createMedicineViewModel,
        )
    }

    if (showCreateMedicineThenDoseSheet) {
        CreateMedicineThenDoseSheet(
            sheetState = createMedicineThenDoseSheetState,
            onDismissRequest = {
                if (!isNewSlotLockedState.value) showCreateMedicineThenDoseSheet = false
            },
            onCloseClick = {
                if (!isNewSlotLockedState.value) {
                    hideBottomSheet(scope, createMedicineThenDoseSheetState) {
                        showCreateMedicineThenDoseSheet = false
                    }
                }
            },
            onGroupSlotResolved = { slotResult, createdMedicineUuid, consumeSavedState ->
                allowManualSlotCompletionHideState.value = true
                hideBottomSheet(scope, createMedicineThenDoseSheetState) {
                    showCreateMedicineThenDoseSheet = false
                    allowManualSlotCompletionHideState.value = false
                    consumeSavedState()
                    createdMedicineUuid?.let(onNewMedicineCreated)
                    onSlotResolved(slotResult)
                }
            },
            mode = (medicineManagerAddNewTarget(launchMode) as? MedicineManagerAddNewTarget.NewMedicineSlot)
                ?.mode
                ?: error("CreateMedicineThenDoseSheet is not used in manager mode."),
            onManualLogSaved = { warning, createdMedicineUuid, consumeSavedState ->
                allowManualSlotCompletionHideState.value = true
                hideBottomSheet(scope, createMedicineThenDoseSheetState) {
                    showCreateMedicineThenDoseSheet = false
                    allowManualSlotCompletionHideState.value = false
                    consumeSavedState()
                    createdMedicineUuid?.let(onNewMedicineCreated)
                    onManualLogSaved(warning)
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
        ExistingMedicineDoseSheet(
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
            onManualLogSaved = { warning, consumeSavedState ->
                allowManualSlotCompletionHideState.value = true
                hideBottomSheet(scope, slotDraftSheetState) {
                    pendingSlotMedicineUuid = null
                    allowManualSlotCompletionHideState.value = false
                    consumeSavedState()
                    onManualLogSaved(warning)
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
                    isStockOptInProjectionFrozen = false
                    frozenStockOptInProjection = null
                }
                viewModel.clearStockOptInResult()
            }

            MedicineStockMutationResult.FAILURE -> {
                isStockOptInProjectionFrozen = false
                frozenStockOptInProjection = null
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
        // Freeze the projection while the enable mutation is in flight so the
        // "after" preview doesn't jump to the post-mutation stock before the
        // sheet finishes closing.
        val displayOptInProjection = adjustSheetStockProjectionForDisplay(
            isStockProjectionFrozen = isStockOptInProjectionFrozen,
            stockProjection = pendingOptInProjection,
            frozenStockProjection = frozenStockOptInProjection,
        ) ?: pendingOptInProjection
        AdjustStockSheet(
            projection = displayOptInProjection,
            initialTab = AdjustSheetTab.RECEIVED,
            receivedOnly = true,
            previewRunway = { hypothetical ->
                viewModel.previewRunwayFor(displayOptInProjection.medicine.uuid, hypothetical)
            },
            onRecount = { },
            onReceived = { received ->
                // Don't dismiss here: the sheet closes only after the enable
                // succeeds (observed via stockOptInResult below), so a failed
                // enable keeps the sheet open and surfaces a toast instead of
                // silently closing.
                frozenStockOptInProjection = displayOptInProjection
                isStockOptInProjectionFrozen = true
                viewModel.enableTrackingFromReceived(
                    medicineUuid = displayOptInProjection.medicine.uuid,
                    currentUnitsRemaining = displayOptInProjection.medicine.stock.unitsRemaining
                        ?: 0.0,
                    received = received,
                )
            },
            sheetState = stockOptInSheetState,
            onDismissRequest = {
                pendingStockOptInUuid = null
                isStockOptInProjectionFrozen = false
                frozenStockOptInProjection = null
            },
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
    showStockNudgeMenu: Boolean = false,
    stockNudgeEnabled: Boolean = true,
    onSetStockNudgeEnabled: (Boolean) -> Unit = { },
) {
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    var showOverflowMenu by rememberSaveable { mutableStateOf(false) }
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
                }.hazeChrome(),
                title = {
                    val title = stringResource(titleRes)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-1.5).dp),
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
                colors = hazeTopAppBarColors(),
                actions = {
                    if (showStockNudgeMenu) {
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.main_more_options),
                                )
                            }
                            HrtDropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                items = listOf(
                                    HrtDropdownMenuItem(
                                        text = stringResource(R.string.stock_nudge_menu_label),
                                        onClick = { onSetStockNudgeEnabled(!stockNudgeEnabled) },
                                        trailingIcon = {
                                            Checkbox(
                                                checked = stockNudgeEnabled,
                                                onCheckedChange = null,
                                            )
                                        },
                                    ),
                                ),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.paddingBehindTopAppBar(innerPadding)) {
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
                contentPadding = appContentPaddingValuesBehindTopAppBar(innerPadding),
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
                    item(key = "section-${section.category.name}") {
                        MedicineManagerSectionTopSpacing(sectionIndex = sectionIndex)
                        HrtSection(
                            title = stringResource(section.category.labelRes),
                            topPadding = sectionIndex != 0,
                        ) {
                            section.medicines.forEach { medicineItem ->
                                item {
                                    MedicineRow(
                                        item = medicineItem,
                                        onClick = { onMedicineClick(medicineItem.medicine.uuid) },
                                        showOnboardingChevron = showOnboardingBanner,
                                    )
                                }
                            }
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
private fun MedicineRow(
    item: MedicineListItem,
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

private fun previewMedicine(): Medicine {
    val key = com.mkx.hrttracker.model.medication.MedicationKey.ESTRADIOL
    val preparation = MedicinePreparation.Pill(
        strengthMgPerTablet = 2.0,
    )
    return Medicine(
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
