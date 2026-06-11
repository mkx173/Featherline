package com.mkx.hrttracker.ui.catalog

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.StockReceived
import com.mkx.hrttracker.data.repository.StockRecount
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.ui.catalog.stock.AdjustStockSheet
import com.mkx.hrttracker.ui.catalog.stock.OpenContainerEditDialog
import com.mkx.hrttracker.ui.catalog.stock.StockSection
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.MedicationCard
import com.mkx.hrttracker.ui.components.NavigationLockEffect
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.pinnedTopAppBarScrollBehavior
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.LocalSheetDismissFocusRequester
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.medication.PatchSpecKind
import com.mkx.hrttracker.ui.medication.hasRawMassDoseField
import com.mkx.hrttracker.ui.medication.medicinePreparationSummary
import com.mkx.hrttracker.ui.medication.shortLabelRes
import com.mkx.hrttracker.ui.plan.RegimenGroupCard
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.labelRes
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private val WARN_AT_OPTIONS = listOf(0, 7, 14, 21, 28)

@Composable
fun MedicineDetailScreen(
    onNavigateBack: () -> Unit,
    // Pops the page after a confirmed archive; returns whether the pop landed
    // (the retained archive result is cleared only on true).
    onArchiveExit: () -> Boolean,
    onGroupClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicineDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val saveLockedMessage = stringResource(R.string.medicine_locked_by_logs)
    val saveCollisionMessage = stringResource(R.string.medicine_already_exists)
    val saveFailureMessage = stringResource(R.string.medicine_save_failure)
    val archiveBlockedMessage = stringResource(R.string.medicine_archive_blocked_by_groups)
    val archiveFailureMessage = stringResource(R.string.medicine_archive_failure)
    val stockMutationFailureMessage = stringResource(R.string.medicine_stock_update_failure)

    // Hold the navigation lock while the archive write is in flight. The confirm
    // dialog closes (releasing its own lock) before the write runs, so without
    // this a back press or same-tab chrome tap would destroy this entry and
    // cancel the user-confirmed archive mid-write.
    NavigationLockEffect(active = uiState.archiveInProgress)

    LaunchedEffect(uiState.saveResult) {
        val result = uiState.saveResult ?: return@LaunchedEffect
        val message = when (result) {
            MedicineDetailSaveResult.SUCCESS -> null
            MedicineDetailSaveResult.FAILURE_LOCKED -> saveLockedMessage
            MedicineDetailSaveResult.FAILURE_IDENTITY_COLLISION -> saveCollisionMessage
            MedicineDetailSaveResult.FAILURE_OTHER -> saveFailureMessage
        }
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        viewModel.clearSaveResult()
    }

    LaunchedEffect(uiState.archiveResult) {
        when (uiState.archiveResult) {
            MedicineArchiveResult.SUCCESS -> {
                // The finishing pop fires unconditionally: the nav lock above
                // holds through the archive write, so no competing navigation
                // can be in flight, and NavController pops safely below
                // RESUMED. The retained result is cleared only after a
                // confirmed pop, so the empty-stack edge re-fires on restore
                // instead of stranding the page.
                if (onArchiveExit()) {
                    viewModel.clearArchiveResult()
                }
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

    LaunchedEffect(uiState.stockMutationResult) {
        when (uiState.stockMutationResult) {
            MedicineStockMutationResult.FAILURE -> {
                Toast.makeText(context, stockMutationFailureMessage, Toast.LENGTH_SHORT).show()
                viewModel.clearStockMutationResult()
            }
            // The detail VM never emits SUCCESS — it signals success by flipping
            // showAdjustSheet false. The branch exists only because the opt-in
            // flow shares this result type.
            MedicineStockMutationResult.SUCCESS,
            null,
                -> Unit
        }
    }

    MedicineDetailScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onGroupClick = onGroupClick,
        onDisplayNameChange = viewModel::updateDisplayNameText,
        onSaveAll = { preparation, unit ->
            viewModel.saveAll(preparation, unit)
        },
        onOpenAdjustSheet = viewModel::openAdjustSheet,
        onOpenOptIn = viewModel::openOptIn,
        onCloseAdjustSheet = viewModel::closeAdjustSheet,
        onOpenOpenContainerDialog = viewModel::openOpenContainerDialog,
        onCloseOpenContainerDialog = viewModel::closeOpenContainerDialog,
        onSubmitOpenContainerAmount = { amount -> viewModel.submitOpenContainerAmount(amount) },
        onOpenDisableConfirmation = viewModel::openDisableConfirmation,
        onCloseDisableConfirmation = viewModel::closeDisableConfirmation,
        onSubmitRecount = { recount -> viewModel.submitRecount(recount) },
        onSubmitReceived = { received -> viewModel.submitReceived(received) },
        previewRunway = viewModel::previewRunway,
        onSubmitWarnAt = { days -> viewModel.submitWarnAt(days) },
        onConfirmDisableTracking = { viewModel.confirmDisableTracking() },
        onArchive = { viewModel.archive() },
        modifier = modifier,
    )
}

// Whether the medicine exposes an editable user-facing name in the detail
// editor. Catalog and custom medicines both edit a display-name override — a
// pure presentation field that resolves ahead of the catalog/identity name —
// so renaming is never blocked by logs. The PATCH_OFF singleton is immutable.
internal fun medicineDetailExposesEditableName(
    selection: MedicineSelection,
): Boolean {
    return selection !is MedicineSelection.PatchOff
}

internal fun adjustSheetStockProjectionForDisplay(
    isStockProjectionFrozen: Boolean,
    stockProjection: MedicineStockProjection?,
    frozenStockProjection: MedicineStockProjection?,
): MedicineStockProjection? {
    return if (isStockProjectionFrozen) {
        frozenStockProjection
    } else {
        stockProjection
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineDetailScreenContent(
    uiState: MedicineDetailUiState,
    onNavigateBack: () -> Unit,
    onGroupClick: (UUID) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSaveAll: (MedicinePreparation?, MedicineDisplayDoseUnit?) -> Unit,
    onOpenAdjustSheet: (AdjustSheetTab) -> Unit,
    onOpenOptIn: () -> Unit,
    onCloseAdjustSheet: () -> Unit,
    onOpenOpenContainerDialog: () -> Unit,
    onCloseOpenContainerDialog: () -> Unit,
    onSubmitOpenContainerAmount: (Double) -> Unit,
    onOpenDisableConfirmation: () -> Unit,
    onCloseDisableConfirmation: () -> Unit,
    onSubmitRecount: (StockRecount) -> Unit,
    onSubmitReceived: (StockReceived) -> Unit,
    previewRunway: (MedicineStock) -> RunwayProjection?,
    onSubmitWarnAt: (Int) -> Unit,
    onConfirmDisableTracking: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(
        lazyListState = listState,
        state = topAppBarState,
    )
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val adjustSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var editSheetOpen by remember { mutableStateOf(false) }
    var adjustSheetRendered by remember { mutableStateOf(false) }
    var retainedAdjustSheetProjection by remember {
        mutableStateOf<MedicineStockProjection?>(null)
    }
    var retainedAdjustSheetTab by remember { mutableStateOf(AdjustSheetTab.RECEIVED) }
    var retainedAdjustSheetReceivedOnly by remember { mutableStateOf(false) }
    var isAdjustSheetStockProjectionFrozen by remember { mutableStateOf(false) }
    var frozenAdjustSheetStockProjection by remember {
        mutableStateOf<MedicineStockProjection?>(null)
    }
    var archiveConfirmOpen by remember { mutableStateOf(false) }
    val stockProjection = uiState.stockProjection

    LaunchedEffect(
        uiState.showAdjustSheet,
        stockProjection,
        uiState.adjustSheetActiveTab,
        uiState.pendingEnableTracking,
        isAdjustSheetStockProjectionFrozen,
    ) {
        if (
            uiState.showAdjustSheet &&
            stockProjection != null &&
            !isAdjustSheetStockProjectionFrozen
        ) {
            retainedAdjustSheetProjection = stockProjection
            retainedAdjustSheetTab = uiState.adjustSheetActiveTab
            retainedAdjustSheetReceivedOnly = uiState.pendingEnableTracking
            adjustSheetRendered = true
        }
    }

    // Assumes no reopen mid-close: if showAdjustSheet went false->true while
    // this hide is still animating, the in-flight hide could complete and run
    // onHidden (adjustSheetRendered = false), dropping the just-reopened sheet,
    // and this effect's keys wouldn't have changed on the second transition to
    // re-render it. Gated in practice by user interaction (reopen needs a tap,
    // the animating sheet covers the screen) plus the single-flight submit guard.
    LaunchedEffect(uiState.showAdjustSheet, adjustSheetRendered) {
        if (!uiState.showAdjustSheet && adjustSheetRendered) {
            hideBottomSheet(scope, adjustSheetState) {
                adjustSheetRendered = false
                retainedAdjustSheetProjection = null
                isAdjustSheetStockProjectionFrozen = false
                frozenAdjustSheetStockProjection = null
            }
        }
    }

    LaunchedEffect(uiState.stockMutationResult) {
        if (uiState.stockMutationResult == MedicineStockMutationResult.FAILURE) {
            isAdjustSheetStockProjectionFrozen = false
            frozenAdjustSheetStockProjection = null
        }
    }

    val appLocale = rememberAppLocale()
    val today = remember { LocalDate.now() }
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior, listState),
                title = {
                    val title = stringResource(R.string.medicine_detail_title)
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
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.paddingBehindTopAppBar(innerPadding)) {
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

            // The PATCH_OFF singleton is immutable: no display name, no
            // preparation editor, no archive (the PK simulator and the slot
            // picker both depend on a single, always-present row).
            val isPatchOff = medicine.selection is MedicineSelection.PatchOff
            val canEditMedicine = medicineDetailExposesEditableName(medicine.selection)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = appContentPaddingValuesBehindTopAppBar(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "medicine-header") {
                    MedicineHeaderCard(
                        medicine = medicine,
                        onEditClick = if (canEditMedicine) {
                            { editSheetOpen = true }
                        } else {
                            null
                        },
                    )
                }

                if (!isPatchOff && stockProjection != null) {
                    item(key = "medicine-stock") {
                        Column {
                            StockSection(
                                projection = stockProjection,
                                onOptInClick = onOpenOptIn,
                                onEditOpenContainer = onOpenOpenContainerDialog,
                                onDisableTracking = onOpenDisableConfirmation,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (stockProjection.medicine.stock.trackingEnabled) {
                                Spacer(Modifier.height(8.dp))
                                HrtSection(title = null) {
                                    item {
                                        PreferenceSegmentedListItem(
                                            title = stringResource(R.string.stock_adjust_row_label),
                                            onClick = { onOpenAdjustSheet(AdjustSheetTab.RECEIVED) },
                                            leadingContent = {
                                                Box(
                                                    modifier = Modifier.size(24.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_box),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            trailingContent = {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                        )
                                    }
                                    item {
                                        var warnAtMenuExpanded by remember { mutableStateOf(false) }
                                        val warnAtDays =
                                            stockProjection.medicine.stock.warnAtDaysRemaining
                                        val warnAtValueText = if (warnAtDays <= 0) {
                                            stringResource(R.string.stock_warnat_value_off)
                                        } else {
                                            stringResource(
                                                R.string.stock_warnat_value_days,
                                                warnAtDays,
                                            )
                                        }
                                        Box {
                                            PreferenceSegmentedListItem(
                                                title = stringResource(
                                                    R.string.stock_warnat_row_title,
                                                ),
                                                supportingText = warnAtValueText,
                                                onClick = { warnAtMenuExpanded = true },
                                                leadingContent = {
                                                    Box(
                                                        modifier = Modifier.size(24.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(
                                                                R.drawable.ic_production_quantity_limits,
                                                            ),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                },
                                            )
                                            HrtDropdownMenu(
                                                expanded = warnAtMenuExpanded,
                                                onDismissRequest = {
                                                    warnAtMenuExpanded = false
                                                },
                                                modifier = Modifier.width(IntrinsicSize.Min),
                                                items = WARN_AT_OPTIONS.map { days ->
                                                    val text = if (days <= 0) {
                                                        stringResource(
                                                            R.string.stock_warnat_value_off,
                                                        )
                                                    } else {
                                                        stringResource(
                                                            R.string.stock_warnat_value_days,
                                                            days,
                                                        )
                                                    }
                                                    HrtDropdownMenuItem(
                                                        text = text,
                                                        onClick = { onSubmitWarnAt(days) },
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "linked-groups") {
                    // Same medicine can appear in multiple slots of one group;
                    // collapse to one card per group so the screen shows the
                    // group context rather than repeated rows.
                    val linkedGroups = uiState.linkedActiveSlots
                        .distinctBy { it.group.uuid }
                        .map { it.group }
                    HrtSection(title = stringResource(R.string.medicine_linked_groups)) {
                        if (linkedGroups.isEmpty()) {
                            item {
                                SupportMessageListItem(
                                    text = stringResource(R.string.medicine_linked_groups_empty),
                                    painter = painterResource(R.drawable.ic_info),
                                )
                            }
                        } else {
                            linkedGroups.forEach { group ->
                                item {
                                    RegimenGroupCard(
                                        group = group,
                                        remindersEnabled = false,
                                        hasNotificationAccess = false,
                                        appLocale = appLocale,
                                        dateFormatter = dateFormatter,
                                        timeFormatter = timeFormatter,
                                        upcomingOccurrences = emptyList(),
                                        today = today,
                                        onClick = { onGroupClick(group.uuid) },
                                        showNotificationIcon = false,
                                        showChevron = true,
                                        showUpcomingSection = false,
                                        firstDayOfWeek = uiState.firstDayOfWeek,
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isPatchOff) {
                    item(key = "danger-zone") {
                        HrtSection(title = stringResource(R.string.group_danger_zone_title)) {
                            item {
                                ArchiveAction(
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
            }
        }
    }

    val liveAdjustSheetProjection = if (uiState.showAdjustSheet) {
        stockProjection
    } else {
        retainedAdjustSheetProjection
    }
    val adjustSheetProjection = adjustSheetStockProjectionForDisplay(
        isStockProjectionFrozen = isAdjustSheetStockProjectionFrozen,
        stockProjection = liveAdjustSheetProjection,
        frozenStockProjection = frozenAdjustSheetStockProjection,
    )
    val adjustSheetTab = if (uiState.showAdjustSheet) {
        uiState.adjustSheetActiveTab
    } else {
        retainedAdjustSheetTab
    }
    val adjustSheetReceivedOnly = if (uiState.showAdjustSheet) {
        uiState.pendingEnableTracking
    } else {
        retainedAdjustSheetReceivedOnly
    }
    if (adjustSheetRendered && adjustSheetProjection != null) {
        AdjustStockSheet(
            projection = adjustSheetProjection,
            initialTab = adjustSheetTab,
            receivedOnly = adjustSheetReceivedOnly,
            onRecount = { recount ->
                frozenAdjustSheetStockProjection = adjustSheetProjection
                isAdjustSheetStockProjectionFrozen = true
                onSubmitRecount(recount)
            },
            onReceived = { received ->
                frozenAdjustSheetStockProjection = adjustSheetProjection
                isAdjustSheetStockProjectionFrozen = true
                onSubmitReceived(received)
            },
            previewRunway = previewRunway,
            sheetState = adjustSheetState,
            onDismissRequest = onCloseAdjustSheet,
            onCloseClick = {
                hideBottomSheet(scope, adjustSheetState, onCloseAdjustSheet)
            },
        )
    }

    if (uiState.showOpenContainerDialog && stockProjection != null) {
        OpenContainerEditDialog(
            preparation = stockProjection.medicine.preparation,
            currentAmount = stockProjection.medicine.stock.openContainerAmount,
            onSubmit = onSubmitOpenContainerAmount,
            onDismiss = onCloseOpenContainerDialog,
        )
    }

    if (uiState.showDisableConfirmation) {
        HazeAlertDialog(
            onDismissRequest = onCloseDisableConfirmation,
            title = { Text(stringResource(R.string.stock_disable_title)) },
            text = { Text(stringResource(R.string.stock_disable_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDisableTracking) {
                    Text(stringResource(R.string.stock_disable_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCloseDisableConfirmation) {
                    Text(stringResource(R.string.stock_cancel))
                }
            },
        )
    }

    if (editSheetOpen) {
        val editingMedicine = uiState.medicine
        if (editingMedicine == null) {
            editSheetOpen = false
        } else {
            MedicineEditSheet(
                medicine = editingMedicine,
                displayName = uiState.displayNameText,
                onDisplayNameChange = onDisplayNameChange,
                isLocked = uiState.isLocked,
                sheetState = editSheetState,
                onDismissRequest = { editSheetOpen = false },
                onCloseClick = {
                    hideBottomSheet(scope, editSheetState) { editSheetOpen = false }
                },
                onSave = { preparation, displayDoseUnit ->
                    hideBottomSheet(scope, editSheetState) { editSheetOpen = false }
                    onSaveAll(preparation, displayDoseUnit)
                },
            )
        }
    }

    if (archiveConfirmOpen) {
        HazeAlertDialog(
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
private fun MedicineHeaderCard(
    medicine: Medicine,
    onEditClick: (() -> Unit)?,
) {
    val trailingContent: (@Composable () -> Unit)? =
        if (medicine.selection is MedicineSelection.PatchOff) {
            val message = stringResource(R.string.medicine_patch_off_detail_summary)
            val content: @Composable () -> Unit = {
                PatchOffInfoTooltipIcon(message = message)
            }
            content
        } else {
            onEditClick?.let { editClick ->
                val content: @Composable () -> Unit = {
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                    ) {
                        IconButton(onClick = editClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.medicine_edit_action),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                content
            }
        }
    MedicationCard(
        medicine = medicine,
        doseInstruction = com.mkx.hrttracker.model.medication.DoseInstruction.Noop,
        applicationType = inferApplicationType(medicine),
        medicationCount = 1,
        groupColorKey = null,
        onClick = null,
        // Match the medicine manager row: the detail page describes the
        // medicine, not an entry, so replace the default route/dose line
        // with just the preparation summary. Appending it as extra text
        // doubles the concentration (default already emits "40 mg/mL"
        // for multi-use vials via doseInstructionSummary).
        supportingTextOverride = medicinePreparationSummary(medicine),
        leadingIconAsForm = true,
        trailingContent = trailingContent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatchOffInfoTooltipIcon(message: String) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = tooltipState.isVisible) {
        tooltipState.dismiss()
    }
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Below,
            ),
            tooltip = {
                RichTooltip {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            },
            state = tooltipState,
        ) {
            IconButton(
                onClick = {
                    if (tooltipState.isVisible) {
                        tooltipState.dismiss()
                    } else {
                        scope.launch { tooltipState.show() }
                    }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = message,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ArchiveAction(
    canArchive: Boolean,
    linkedActiveGroupCount: Int,
    onArchiveClick: () -> Unit,
) {
    // No "already archived" branch on purpose: after a successful archive
    // the medicine briefly emits isArchived=true before the screen pops, and
    // swapping the button for a notice in that window causes a visible
    // flash. The detail page is only opened for active medicines, so the
    // post-archive transient is the only path that would have rendered it.
    val supportText = if (!canArchive) {
        pluralStringResource(
            R.plurals.medicine_archive_blocked_count,
            linkedActiveGroupCount,
            linkedActiveGroupCount,
        )
    } else {
        null
    }
    // Matches the group editor's ArchiveMedicationGroupCard styling
    // (onSurfaceVariant tint, no error-container background) — archiving
    // here is a normal lifecycle action, not a destructive danger-zone op.
    PreferenceSegmentedListItem(
        title = stringResource(R.string.medicine_archive_action),
        enabled = canArchive,
        onClick = onArchiveClick,
        supportingText = supportText,
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_archive),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = if (canArchive) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineEditSheet(
    medicine: Medicine,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    isLocked: Boolean,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    onSave: (MedicinePreparation?, MedicineDisplayDoseUnit?) -> Unit,
) {
    val isCustom = medicine.selection is MedicineSelection.Custom
    // Catalog medicines override their fixed catalog name; custom medicines
    // override their identity name. Either way the user-facing name is the
    // editable display-name field.
    val showsDisplayName = medicineDetailExposesEditableName(medicine.selection)
    val initialDraft = remember(medicine.uuid, medicine.preparation, medicine.displayDoseUnit) {
        medicine.preparation.toPreparationDraft(
            displayDoseUnit = if (isCustom) medicine.displayDoseUnit
            else MedicineDisplayDoseUnit.MG,
        )
    }
    var draft by remember(initialDraft) { mutableStateOf(initialDraft) }
    val candidate = remember(draft) { draft.toPreparationOrNull() }
    // When the medicine is locked we can still rename the catalog display
    // name, so the sheet remains useful; the preparation half is replaced
    // by a locked notice and updatePreparation is skipped on Save.
    val canSave = if (isLocked) showsDisplayName else candidate != null

    // IME Next chain across the visible editable fields: the rename field (when
    // shown) flows into the preparation's numeric fields, and the last field
    // ends with Done. A locked medicine disables the preparation fields, so they
    // drop out of the chain and the rename field terminates it. Reuses
    // CreateMedicineField + nextField/imeActionFor from CreateMedicineSheet.
    val focusRequesters = remember {
        CreateMedicineField.entries.associateWith { FocusRequester() }
    }
    val editFields = buildList {
        if (showsDisplayName) add(CreateMedicineField.DISPLAY_NAME)
        if (!isLocked) addAll(preparationEditFields(draft))
    }

    MedicationEditorSheetScaffold(
        title = stringResource(R.string.medicine_edit_title),
        sheetState = sheetState,
        confirmButtonText = stringResource(R.string.save),
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        fillAvailableHeight = false,
        isSaving = false,
        onConfirm = {
            if (!canSave) return@MedicationEditorSheetScaffold
            val preparationToSave = if (isLocked) null else candidate
            onSave(preparationToSave, draft.displayDoseUnit)
        },
    ) {
        if (isLocked) {
            SupportMessageListItem(
                text = stringResource(R.string.medicine_locked_by_logs),
                painter = painterResource(R.drawable.ic_lock),
                leadingIconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                titleColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        }
        if (showsDisplayName) {
            // Placeholder is the name an empty override falls back to: the
            // catalog default for catalog medicines, the typed identity name
            // for custom ones.
            val defaultName = when (val selection = medicine.selection) {
                is MedicineSelection.Catalog -> stringResource(selection.medicationKey.labelRes)
                is MedicineSelection.Custom -> selection.medicationName
                // The detail screen hides the edit action for the singleton, so
                // this branch only exists for when-exhaustiveness.
                is MedicineSelection.PatchOff -> ""
            }
            EditDisplayNameField(
                defaultName = defaultName,
                displayName = displayName,
                onDisplayNameChange = onDisplayNameChange,
                focusRequester = focusRequesters.getValue(CreateMedicineField.DISPLAY_NAME),
                imeAction = imeActionFor(editFields, CreateMedicineField.DISPLAY_NAME),
                onImeNext = {
                    nextField(editFields, CreateMedicineField.DISPLAY_NAME)
                        ?.let { focusRequesters.getValue(it).requestFocus() }
                },
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        }
        PreparationEditorFields(
            draft = draft,
            onDraftChange = { draft = it },
            showsUnitPicker = isCustom &&
                    draft.preparationType.hasRawMassDoseField(draft.patchSpecKind),
            enabled = !isLocked,
            focusRequesters = focusRequesters,
            editFields = editFields,
        )
    }
}

// Mirrors CreateMedicineSheet.DisplayNameField: the medicine's fallback name as
// the placeholder (so an empty input keeps the medicine using its default name)
// and label/leading-icon affordances that read as one row. No supporting hint —
// the placeholder already communicates the fallback name.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDisplayNameField(
    defaultName: String,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onImeNext: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // Park on the sheet's non-text anchor instead of clearing focus: on API 26
    // clearFocus jumps focus back to the first field rather than dismissing.
    val dismissFocusAnchor = LocalSheetDismissFocusRequester.current
    val dismissKeyboard: () -> Unit = {
        dismissFocusAnchor?.requestFocus() ?: focusManager.clearFocus()
    }
    val displayNameState = rememberTextFieldState(initialText = displayName)
    val currentDisplayName by rememberUpdatedState(displayName)
    val currentOnDisplayNameChange by rememberUpdatedState(onDisplayNameChange)

    LaunchedEffect(displayNameState, displayName) {
        if (displayNameState.text.toString() != displayName) {
            displayNameState.setTextAndPlaceCursorAtEnd(displayName)
        }
    }

    LaunchedEffect(displayNameState) {
        snapshotFlow { displayNameState.text.toString() }.collect { value ->
            if (value != currentDisplayName) {
                currentOnDisplayNameChange(value)
            }
        }
    }

    OutlinedTextField(
        state = displayNameState,
        labelPosition = displayNameFieldLabelPosition(),
        label = { Text(text = stringResource(R.string.medicine_display_name)) },
        placeholder = { Text(text = defaultName) },
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
            if (imeAction == ImeAction.Next) onImeNext() else dismissKeyboard()
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreparationEditorFields(
    draft: MedicinePreparationDraftUiState,
    onDraftChange: (MedicinePreparationDraftUiState) -> Unit,
    showsUnitPicker: Boolean,
    focusRequesters: Map<CreateMedicineField, FocusRequester>,
    editFields: List<CreateMedicineField>,
    enabled: Boolean = true,
) {
    // Advances the IME to the next field in the chain; the last field has a Done
    // action instead, so this never runs there.
    val onImeNextFor: (CreateMedicineField) -> () -> Unit = { field ->
        {
            nextField(editFields, field)
                ?.let { focusRequesters.getValue(it).requestFocus() }
        }
    }
    // Identity is fixed for an existing medicine — the sheet never offers a
    // preparation-type switch; we only edit the numeric fields of the
    // preparation the medicine already declared. (Switching preparation type
    // would change the identityKey and is better modeled as "create a new
    // medicine" than "edit this one".)
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))) {
        val rawMassUnit = if (showsUnitPicker) {
            draft.displayDoseUnit.shortLabelRes()
        } else {
            R.string.unit_mg
        }
        when (draft.preparationType) {
            MedicinePreparationType.PILL,
            MedicinePreparationType.CAPSULE -> {
                val isCapsule = draft.preparationType == MedicinePreparationType.CAPSULE
                Column {
                    NumericInputField(
                        value = draft.pillStrengthMg,
                        label = fieldLabelWithUnit(
                            if (isCapsule) R.string.field_capsule_strength_mg else R.string.field_pill_strength_mg,
                            rawMassUnit,
                        ),
                        suffix = stringResource(rawMassUnit),
                        leadingIconRes = R.drawable.ic_medication,
                        enabled = enabled,
                        onValueChange = { onDraftChange(draft.copy(pillStrengthMg = it)) },
                        focusRequester = focusRequesters.getValue(CreateMedicineField.PILL_STRENGTH),
                        imeAction = imeActionFor(editFields, CreateMedicineField.PILL_STRENGTH),
                        onImeNext = onImeNextFor(CreateMedicineField.PILL_STRENGTH),
                    )
                    PreparationDoseUnitPicker(
                        selectedUnit = draft.displayDoseUnit,
                        onUnitSelected = { unit ->
                            onDraftChange(draft.copy(displayDoseUnit = unit))
                        },
                        visible = showsUnitPicker,
                        enabled = enabled,
                    )
                }
            }

            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> {
                Column {
                    NumericInputField(
                        value = draft.singleUseVialStrengthMg,
                        label = fieldLabelWithUnit(
                            R.string.field_single_use_vial_strength_mg,
                            rawMassUnit
                        ),
                        suffix = stringResource(rawMassUnit),
                        leadingIconRes = R.drawable.ic_vaccines,
                        enabled = enabled,
                        onValueChange = { onDraftChange(draft.copy(singleUseVialStrengthMg = it)) },
                        focusRequester = focusRequesters.getValue(CreateMedicineField.VIAL_STRENGTH),
                        imeAction = imeActionFor(editFields, CreateMedicineField.VIAL_STRENGTH),
                        onImeNext = onImeNextFor(CreateMedicineField.VIAL_STRENGTH),
                    )
                    PreparationDoseUnitPicker(
                        selectedUnit = draft.displayDoseUnit,
                        onUnitSelected = { unit ->
                            onDraftChange(draft.copy(displayDoseUnit = unit))
                        },
                        visible = showsUnitPicker,
                        enabled = enabled,
                    )
                }
            }

            MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> {
                NumericInputField(
                    value = draft.concentrationMgPerMl,
                    label = fieldLabelWithUnit(
                        R.string.field_concentration_mg_per_ml,
                        R.string.unit_mg_per_ml
                    ),
                    suffix = stringResource(R.string.unit_mg_per_ml),
                    leadingIconRes = R.drawable.ic_humidity_percentage,
                    enabled = enabled,
                    onValueChange = { onDraftChange(draft.copy(concentrationMgPerMl = it)) },
                    focusRequester = focusRequesters.getValue(CreateMedicineField.CONCENTRATION_MG_PER_ML),
                    imeAction = imeActionFor(
                        editFields,
                        CreateMedicineField.CONCENTRATION_MG_PER_ML
                    ),
                    onImeNext = onImeNextFor(CreateMedicineField.CONCENTRATION_MG_PER_ML),
                )
                NumericInputField(
                    value = draft.vialVolumeMl,
                    label = fieldLabelWithUnit(R.string.field_vial_volume_ml, R.string.unit_ml),
                    suffix = stringResource(R.string.unit_ml),
                    leadingIconRes = R.drawable.ic_fluid,
                    enabled = enabled,
                    onValueChange = { onDraftChange(draft.copy(vialVolumeMl = it)) },
                    focusRequester = focusRequesters.getValue(CreateMedicineField.VIAL_VOLUME_ML),
                    imeAction = imeActionFor(editFields, CreateMedicineField.VIAL_VOLUME_ML),
                    onImeNext = onImeNextFor(CreateMedicineField.VIAL_VOLUME_ML),
                )
            }

            MedicinePreparationType.GEL_SACHET -> {
                NumericInputField(
                    value = draft.gelConcentrationPercent,
                    label = fieldLabelWithUnit(
                        R.string.field_gel_concentration_percent,
                        R.string.unit_percent
                    ),
                    suffix = stringResource(R.string.unit_percent),
                    leadingIconRes = R.drawable.ic_humidity_percentage,
                    enabled = enabled,
                    onValueChange = { onDraftChange(draft.copy(gelConcentrationPercent = it)) },
                    focusRequester = focusRequesters.getValue(CreateMedicineField.GEL_PERCENT),
                    imeAction = imeActionFor(editFields, CreateMedicineField.GEL_PERCENT),
                    onImeNext = onImeNextFor(CreateMedicineField.GEL_PERCENT),
                )
                NumericInputField(
                    value = draft.sachetWeightGrams,
                    label = fieldLabelWithUnit(
                        R.string.field_sachet_weight_grams,
                        R.string.unit_grams
                    ),
                    suffix = stringResource(R.string.unit_grams),
                    leadingIconRes = R.drawable.ic_weight,
                    enabled = enabled,
                    onValueChange = { onDraftChange(draft.copy(sachetWeightGrams = it)) },
                    focusRequester = focusRequesters.getValue(CreateMedicineField.SACHET_WEIGHT),
                    imeAction = imeActionFor(editFields, CreateMedicineField.SACHET_WEIGHT),
                    onImeNext = onImeNextFor(CreateMedicineField.SACHET_WEIGHT),
                )
            }

            MedicinePreparationType.GEL_CONTAINER -> {
                NumericInputField(
                    value = draft.gelConcentrationPercent,
                    label = fieldLabelWithUnit(
                        R.string.field_gel_concentration_percent,
                        R.string.unit_percent
                    ),
                    suffix = stringResource(R.string.unit_percent),
                    leadingIconRes = R.drawable.ic_humidity_percentage,
                    enabled = enabled,
                    onValueChange = { onDraftChange(draft.copy(gelConcentrationPercent = it)) },
                    focusRequester = focusRequesters.getValue(CreateMedicineField.GEL_PERCENT),
                    imeAction = imeActionFor(editFields, CreateMedicineField.GEL_PERCENT),
                    onImeNext = onImeNextFor(CreateMedicineField.GEL_PERCENT),
                )
                NumericInputField(
                    value = draft.containerWeightGrams,
                    label = fieldLabelWithUnit(
                        R.string.field_container_weight_grams,
                        R.string.unit_grams
                    ),
                    suffix = stringResource(R.string.unit_grams),
                    leadingIconRes = R.drawable.ic_weight,
                    enabled = enabled,
                    onValueChange = { onDraftChange(draft.copy(containerWeightGrams = it)) },
                    focusRequester = focusRequesters.getValue(CreateMedicineField.CONTAINER_WEIGHT),
                    imeAction = imeActionFor(editFields, CreateMedicineField.CONTAINER_WEIGHT),
                    onImeNext = onImeNextFor(CreateMedicineField.CONTAINER_WEIGHT),
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
                    enabled = enabled,
                )
                when (draft.patchSpecKind) {
                    PatchSpecKind.TOTAL_MG -> {
                        Column {
                            NumericInputField(
                                value = draft.patchTotalMg,
                                label = fieldLabelWithUnit(
                                    R.string.field_patch_total_dosage_mg,
                                    rawMassUnit
                                ),
                                suffix = stringResource(rawMassUnit),
                                leadingIconRes = R.drawable.ic_chronic,
                                enabled = enabled,
                                onValueChange = {
                                    onDraftChange(draft.copy(patchTotalMg = it))
                                },
                                focusRequester = focusRequesters.getValue(CreateMedicineField.PATCH_TOTAL_MG),
                                imeAction = imeActionFor(
                                    editFields,
                                    CreateMedicineField.PATCH_TOTAL_MG
                                ),
                                onImeNext = onImeNextFor(CreateMedicineField.PATCH_TOTAL_MG),
                            )
                            PreparationDoseUnitPicker(
                                selectedUnit = draft.displayDoseUnit,
                                onUnitSelected = { unit ->
                                    onDraftChange(draft.copy(displayDoseUnit = unit))
                                },
                                visible = showsUnitPicker,
                                enabled = enabled,
                            )
                        }
                    }

                    PatchSpecKind.RELEASE_RATE -> NumericInputField(
                        value = draft.patchReleaseRateMcgPerDay,
                        label = fieldLabelWithUnit(
                            R.string.field_patch_release_rate,
                            R.string.unit_mcg_day
                        ),
                        suffix = stringResource(R.string.unit_mcg_day),
                        leadingIconRes = R.drawable.ic_speed,
                        enabled = enabled,
                        onValueChange = {
                            onDraftChange(draft.copy(patchReleaseRateMcgPerDay = it))
                        },
                        focusRequester = focusRequesters.getValue(CreateMedicineField.PATCH_RELEASE_RATE),
                        imeAction = imeActionFor(
                            editFields,
                            CreateMedicineField.PATCH_RELEASE_RATE
                        ),
                        onImeNext = onImeNextFor(CreateMedicineField.PATCH_RELEASE_RATE),
                    )
                }
            }

            // The PATCH_OFF singleton's preparation has no editable fields;
            // the detail screen hides the entire edit sheet for it (see
            // MedicineDetailScreenContent), so this branch only exists for
            // when-exhaustiveness.
            MedicinePreparationType.PATCH_OFF -> Unit
        }
    }
}

// Ordered IME-chain fields for a preparation draft, keyed by CreateMedicineField
// so MedicineEditSheet can reuse nextField/imeActionFor. Mirrors the field order
// rendered by PreparationEditorFields for each preparation type.
private fun preparationEditFields(
    draft: MedicinePreparationDraftUiState,
): List<CreateMedicineField> = when (draft.preparationType) {
    MedicinePreparationType.PILL,
    MedicinePreparationType.CAPSULE -> listOf(CreateMedicineField.PILL_STRENGTH)

    MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
        listOf(CreateMedicineField.VIAL_STRENGTH)

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

    MedicinePreparationType.PATCH_OFF -> emptyList()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreparationDoseUnitPicker(
    selectedUnit: MedicineDisplayDoseUnit,
    onUnitSelected: (MedicineDisplayDoseUnit) -> Unit,
    visible: Boolean,
    enabled: Boolean = true,
) {
    if (!visible) {
        return
    }
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
                enabled = enabled,
            )
        }
    }
}

// "Tablet strength" + "mg" → "Tablet strength (mg)". Used as the field label
// so the user can tell what unit they're typing in without scanning to the
// trailing suffix.
@Composable
private fun fieldLabelWithUnit(
    @androidx.annotation.StringRes labelRes: Int,
    @androidx.annotation.StringRes unitRes: Int,
): String {
    return "${stringResource(labelRes)} (${stringResource(unitRes)})"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumericInputField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    suffix: String? = null,
    @androidx.annotation.DrawableRes leadingIconRes: Int? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeNext: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    // Park on the sheet's non-text anchor instead of clearing focus: on API 26
    // clearFocus lets the window re-grant focus to a field, reopening the IME.
    val dismissFocusAnchor = LocalSheetDismissFocusRequester.current
    val dismissKeyboard: () -> Unit = {
        dismissFocusAnchor?.requestFocus() ?: focusManager.clearFocus()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
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
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeNext?.invoke() ?: dismissKeyboard() },
            onDone = { dismissKeyboard() },
        ),
    )
}

private fun inferApplicationType(medicine: Medicine): MedicationApplicationType {
    return when (medicine.preparation) {
        is MedicinePreparation.Pill -> MedicationApplicationType.ORAL
        is MedicinePreparation.Capsule -> MedicationApplicationType.ORAL
        is MedicinePreparation.InjectionSingleUseVial,
        is MedicinePreparation.InjectionMultiUseVial -> MedicationApplicationType.INJECTION

        is MedicinePreparation.GelSachet,
        is MedicinePreparation.GelContainer -> MedicationApplicationType.GEL

        is MedicinePreparation.Patch -> MedicationApplicationType.PATCH_ON
        // Singleton row in the manager renders the patch-off icon.
        is MedicinePreparation.PatchOff -> MedicationApplicationType.PATCH_OFF
    }
}

private fun MedicinePreparation.toPreparationDraft(
    displayDoseUnit: MedicineDisplayDoseUnit =
        MedicineDisplayDoseUnit.MG,
): MedicinePreparationDraftUiState {
    val base = MedicinePreparationDraftUiState(
        preparationType = type,
        displayDoseUnit = displayDoseUnit,
    )
    return when (this) {
        is MedicinePreparation.Pill -> base.copy(
            pillStrengthMg = displayDoseUnit.fromMg(strengthMgPerTablet).toEditableString(),
        )

        is MedicinePreparation.Capsule -> base.copy(
            pillStrengthMg = displayDoseUnit.fromMg(strengthMgPerCapsule).toEditableString(),
        )

        is MedicinePreparation.InjectionSingleUseVial -> base.copy(
            singleUseVialStrengthMg = displayDoseUnit.fromMg(strengthMgPerVial).toEditableString(),
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
                    patchTotalMg = displayDoseUnit.fromMg(spec.valueMg).toEditableString(),
                )

            is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay ->
                base.copy(
                    patchSpecKind = PatchSpecKind.RELEASE_RATE,
                    patchReleaseRateMcgPerDay = spec.valueMcgPerDay.toEditableString(),
                )
        }

        // The singleton has no numeric fields to seed.
        is MedicinePreparation.PatchOff -> base
    }
}

private fun MedicinePreparationDraftUiState.toPreparationOrNull(): MedicinePreparation? {
    // Raw-mass values are typed in displayDoseUnit; the model stores mg.
    val toMg: (Double) -> Double = { displayDoseUnit.toMg(it) }
    return runCatching {
        when (preparationType) {
            MedicinePreparationType.PILL -> MedicinePreparation.Pill(
                strengthMgPerTablet = toMg(pillStrengthMg.toPositiveDoubleOrThrow()),
            )

            MedicinePreparationType.CAPSULE -> MedicinePreparation.Capsule(
                strengthMgPerCapsule = toMg(pillStrengthMg.toPositiveDoubleOrThrow()),
            )

            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
                MedicinePreparation.InjectionSingleUseVial(
                    strengthMgPerVial = toMg(singleUseVialStrengthMg.toPositiveDoubleOrThrow()),
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
                        valueMg = toMg(patchTotalMg.toPositiveDoubleOrThrow()),
                    )

                    PatchSpecKind.RELEASE_RATE ->
                        MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                            valueMcgPerDay = patchReleaseRateMcgPerDay.toPositiveDoubleOrThrow(),
                        )
                },
            )

            // The singleton's preparation is immutable; the dialog never
            // reaches this branch because the detail screen hides the edit
            // action for the singleton.
            MedicinePreparationType.PATCH_OFF -> MedicinePreparation.PatchOff
        }
    }.getOrNull()
}

internal fun String.toPositiveDoubleOrThrow(): Double {
    val value = trim().replace(',', '.').toDouble()
    require(value > 0.0 && value.isFinite())
    return value
}

private fun Double.toEditableString(): String {
    return java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
}
