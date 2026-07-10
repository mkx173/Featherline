package com.mkx.hrttracker.ui.journal

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.BuildConfig
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.MilestoneUnit
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.FlipSlot
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.HrtSectionHeader
import com.mkx.hrttracker.ui.components.LocalAppContentBottomInset
import com.mkx.hrttracker.ui.components.MonthPickerDialog
import com.mkx.hrttracker.ui.components.ScrollToTopSignalEffect
import com.mkx.hrttracker.ui.components.SelectionFabScrollState
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.hrtSection
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.pinnedTopAppBarScrollBehavior
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.components.updateSelectionFabScrollState
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.monthHeaderFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.widget.AnchorShortcutManager
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.YearMonth

private const val JournalScreenListTestTag = "journal-screen-list"

@Composable
fun JournalScreen(
    onOpenMilestones: () -> Unit,
    onAddDate: () -> Unit,
    onOpenAllNotes: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
    viewModel: JournalViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveFailedMessage = stringResource(R.string.journal_note_save_failed)
    val deleteFailedMessage = stringResource(R.string.journal_note_delete_failed)
    LaunchedEffect(uiState.noteMutationError) {
        when (uiState.noteMutationError) {
            JournalNoteMutation.SAVE -> {
                Toast.makeText(context, saveFailedMessage, Toast.LENGTH_SHORT).show()
                viewModel.consumeNoteMutationError()
            }
            JournalNoteMutation.DELETE -> {
                Toast.makeText(context, deleteFailedMessage, Toast.LENGTH_SHORT).show()
                viewModel.consumeNoteMutationError()
            }
            null -> Unit
        }
    }

    JournalScreenContent(
        uiState = uiState,
        onOpenMilestones = onOpenMilestones,
        onAddDate = onAddDate,
        onOpenAllNotes = onOpenAllNotes,
        onSaveTodayNote = viewModel::saveTodayNote,
        onSaveNote = viewModel::saveNote,
        onDeleteTodayNote = viewModel::deleteTodayNote,
        onDeleteNote = viewModel::deleteNote,
        onAddDebugNotes = viewModel::addDebugSampleNotes,
        scrollToTopSignal = scrollToTopSignal,
        noteSaveFailureToken = uiState.noteSaveFailureToken,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreenContent(
    uiState: JournalUiState,
    onOpenMilestones: () -> Unit,
    onAddDate: () -> Unit = onOpenMilestones,
    onOpenAllNotes: () -> Unit,
    onSaveTodayNote: (String) -> Unit,
    onSaveNote: (LocalDate, String) -> Unit,
    onDeleteTodayNote: () -> Unit = { },
    onDeleteNote: (LocalDate) -> Unit = { },
    onAddDebugNotes: () -> Unit = { },
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
    noteSaveFailureToken: Int = 0,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(lazyListState = listState)
    val timelineNotes = uiState.recentNotes.filter { it.date != uiState.today }
    // One controller shared by the Today composer and the timeline rows: a single editor stays
    // open at a time, and it resets to closed when this screen leaves composition.
    val editorController = rememberNoteEditorController()
    ScrollToTopSignalEffect(
        signal = scrollToTopSignal,
        topAppBarState = scrollBehavior.state,
        listState = listState,
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBar(
                title = {
                    val title = stringResource(R.string.tab_journal)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-1.5).dp),
                    )
                },
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior, listState),
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
                modifier = Modifier
                    .fillMaxSize()
                    // Shrink the list above the keyboard so the focused editor can scroll into the
                    // visible area (paired with bringWholeFieldIntoView on the editor card).
                    .imePadding()
                    .testTag(JournalScreenListTestTag),
                contentPadding = appContentPaddingValuesBehindTopAppBar(innerPadding),
            ) {
                item(key = "journal-milestones", contentType = "journal-section") {
                    HrtSection(
                        title = stringResource(R.string.journal_milestones_section),
                        topPadding = false,
                    ) {
                        if (uiState.hasAnchors) {
                            item {
                                JournalHeroCard(
                                    hero = uiState.pinnedAnchors.first(),
                                    today = uiState.today,
                                    onClick = onOpenMilestones,
                                )
                            }
                            val subsequentAnchors = uiState.pinnedAnchors.drop(1)
                            if (subsequentAnchors.isNotEmpty()) {
                                item {
                                    PinnedDatesCard(
                                        today = uiState.today,
                                        anchors = subsequentAnchors,
                                        onClick = onOpenMilestones,
                                    )
                                }
                            }
                        } else if (uiState.hasTrackedDates) {
                            item {
                                EmptyPinnedMilestonesCard(onClick = onOpenMilestones)
                            }
                        } else {
                            item {
                                EmptyMilestonesCard(
                                    onOpenMilestones = onOpenMilestones,
                                    onAddDate = onAddDate,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                }

                // The Today composer is its own standalone section (count 1 -> fully
                // rounded). The recent notes — or the empty-state message — form a
                // separate section below it following the normal hrtSection grouping.
                hrtSection(
                    key = "journal-today",
                    header = {
                        HrtSectionHeader(
                            text = stringResource(R.string.journal_notes_section),
                            trailingAlignByBaseline = true,
                            trailing = {
                                Text(
                                    text = stringResource(R.string.journal_notes_window_meta).uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    // Debug-only: long-press this meta label to seed sample notes
                                    // across the recent window, previous months, and prior years.
                                    // No ripple — it's a hidden gesture on a plain text label.
                                    modifier = if (BuildConfig.DEBUG) {
                                        Modifier.combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {},
                                            onLongClick = onAddDebugNotes,
                                        )
                                            .alignByBaseline()
                                    } else {
                                        Modifier.alignByBaseline()
                                    },
                                )
                            },
                        )
                    },
                ) {
                    item(key = "journal-today-composer") {
                        TodayComposer(
                            today = uiState.today,
                            note = uiState.todayNote,
                            onSave = onSaveTodayNote,
                            onDelete = onDeleteTodayNote,
                            editorController = editorController,
                            saveFailureToken = noteSaveFailureToken,
                        )
                    }
                }

                // Recent past notes form the rail; today lives in the composer above.
                if (timelineNotes.isNotEmpty()) {
                    item(key = "journal-notes-list-gap") {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
                    }
                    hrtSection(key = "journal-notes-list") {
                        item(key = "journal-notes-timeline") {
                            NotesTimeline(
                                notes = timelineNotes,
                                today = uiState.today,
                                onSave = onSaveNote,
                                onDelete = onDeleteNote,
                                editorController = editorController,
                                saveFailureToken = noteSaveFailureToken,
                            )
                        }
                    }
                }

                // One terminal always closes the notes area so it never looks unfinished once
                // the first note is written: "see all" when older notes exist beyond the
                // window, otherwise an end card — the full empty hint when nothing is saved, or
                // a quiet "no earlier notes" once today's or any recent note exists.
                if (uiState.olderNotesCount > 0) {
                    item(key = "journal-see-all-notes", contentType = "journal-action") {
                        HrtFilledTonalButton(
                            text = stringResource(R.string.journal_see_all_notes),
                            onClick = onOpenAllNotes,
                            trailingIcon = Icons.Rounded.ChevronRight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = dimensionResource(R.dimen.padding_small)),
                        )
                    }
                } else {
                    item(key = "journal-notes-end-gap") {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
                    }
                    hrtSection(key = "journal-notes-end") {
                        item(key = "journal-notes-end-item") {
                            if (uiState.todayNote == null && timelineNotes.isEmpty()) {
                                SupportMessageListItem(
                                    text = stringResource(R.string.journal_no_notes),
                                    painter = painterResource(R.drawable.ic_info),
                                )
                            } else {
                                SupportMessageListItem(
                                    text = stringResource(R.string.journal_no_earlier_notes),
                                    painter = painterResource(R.drawable.ic_info),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestonesScreen(
    onNavigateBack: () -> Unit,
    openAddDateOnLaunch: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: MilestonesViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isAddDateSheetOpen by remember { mutableStateOf(false) }
    var editingAnchor by remember { mutableStateOf<AnchorRowUiState?>(null) }
    var heroBackgroundDialogTargetId by remember { mutableStateOf<String?>(null) }
    val activeEditingAnchor = editingAnchor
    val closeDateSheet = {
        isAddDateSheetOpen = false
        editingAnchor = null
    }

    var isPinSheetOpen by remember { mutableStateOf(false) }

    // Arriving via the journal "Add a date" CTA opens the sheet straight away. The rememberSaveable
    // guard makes this a true one-shot: it won't reopen on configuration change or after the user
    // dismisses the sheet without leaving the screen.
    var addDateLaunchConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (openAddDateOnLaunch && !addDateLaunchConsumed) {
            addDateLaunchConsumed = true
            isAddDateSheetOpen = true
        }
    }

    // Edit mode lives in the Activity-scoped ViewModel, so it would otherwise persist
    // across navigation. Reset it when leaving the screen so re-entry starts in view mode.
    DisposableEffect(Unit) {
        onDispose { viewModel.exitEditMode() }
    }

    val context = LocalContext.current
    val saveFailedMessage = stringResource(R.string.journal_date_save_failed)
    val deleteFailedMessage = stringResource(R.string.journal_date_delete_failed)
    LaunchedEffect(uiState.dateMutationError) {
        when (uiState.dateMutationError) {
            MilestoneMutation.SAVE -> {
                Toast.makeText(context, saveFailedMessage, Toast.LENGTH_SHORT).show()
                viewModel.consumeDateMutationError()
            }
            MilestoneMutation.DELETE -> {
                Toast.makeText(context, deleteFailedMessage, Toast.LENGTH_SHORT).show()
                viewModel.consumeDateMutationError()
            }
            null -> Unit
        }
    }

    MilestonesScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onToggleEdit = viewModel::toggleEditMode,
        onSetPinned = viewModel::setPinned,
        onReorder = viewModel::reorderPinned,
        onAddDate = { isAddDateSheetOpen = true },
        onUpdateDate = { anchor -> editingAnchor = anchor },
        onOpenHeroBackground = {
            heroBackgroundDialogTargetId = uiState.hero?.id
        },
        onPinFolderIcon = { isPinSheetOpen = true },
        modifier = modifier,
    )

    if (isAddDateSheetOpen || activeEditingAnchor != null) {
        // Default the toggle: a new date pins only when nothing is pinned yet; editing a date
        // reflects its current pin state. Either way the user can flip it in the sheet.
        // Captured once when the sheet opens (keyed like the sheet's own `pinned` state) so a
        // tray change under the open sheet can't desync the `pinned != initiallyPinned`
        // comparison in onConfirm.
        val initiallyPinned = remember(activeEditingAnchor?.id, isAddDateSheetOpen) {
            if (activeEditingAnchor == null) {
                uiState.pinnedTray.isEmpty()
            } else {
                uiState.pinnedTray.any { it.id == activeEditingAnchor.id }
            }
        }
        AddDateSheet(
            today = uiState.today,
            anchor = activeEditingAnchor,
            initiallyPinned = initiallyPinned,
            onDismissRequest = closeDateSheet,
            onConfirm = { name, icon, date, paletteKey, pinned ->
                if (activeEditingAnchor == null) {
                    viewModel.addDate(
                        name = name,
                        icon = icon,
                        date = date,
                        paletteKey = paletteKey,
                        pinned = pinned,
                    )
                } else {
                    viewModel.updateDate(
                        id = activeEditingAnchor.id,
                        name = name,
                        icon = icon,
                        date = date,
                        paletteKey = paletteKey,
                    )
                    if (pinned != initiallyPinned) {
                        viewModel.setPinned(activeEditingAnchor.id, pinned)
                    }
                }
            },
            onDelete = activeEditingAnchor?.let { anchor ->
                { viewModel.deleteDate(anchor.id) }
            },
        )
    }

    if (isPinSheetOpen) {
        AnchorSelectorSheet(
            title = stringResource(R.string.anchor_pin_folder_icon),
            anchors = uiState.anchors,
            today = uiState.today,
            onDismissRequest = { isPinSheetOpen = false },
            onSelect = { anchorId ->
                uiState.anchors.firstOrNull { it.id == anchorId }?.let { anchor ->
                    AnchorShortcutManager.pin(context, anchor)
                }
            },
        )
    }

    HeroBackgroundDialogHost(
        hero = uiState.hero,
        targetHeroId = heroBackgroundDialogTargetId,
        onTargetHeroIdChange = { heroBackgroundDialogTargetId = it },
        onSetHeroBackground = viewModel::setHeroBackground,
    )
}

@Composable
internal fun HeroBackgroundDialogHost(
    hero: AnchorRowUiState?,
    targetHeroId: String?,
    onTargetHeroIdChange: (String?) -> Unit,
    onSetHeroBackground: (String, HeroBackground) -> Unit,
) {
    val target = hero?.takeIf { it.id == targetHeroId }
    LaunchedEffect(hero?.id, targetHeroId) {
        if (targetHeroId != null && target == null) {
            onTargetHeroIdChange(null)
        }
    }

    if (target != null) {
        HeroBackgroundDialog(
            current = target.heroBackground,
            dateColorKey = target.palette,
            onConfirm = { background ->
                onSetHeroBackground(target.id, background)
                onTargetHeroIdChange(null)
            },
            onDismissRequest = { onTargetHeroIdChange(null) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestonesScreenContent(
    uiState: MilestonesUiState,
    onNavigateBack: () -> Unit,
    onToggleEdit: () -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    onAddDate: () -> Unit,
    onUpdateDate: (AnchorRowUiState) -> Unit,
    onOpenHeroBackground: () -> Unit,
    onPinFolderIcon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(lazyListState = listState)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBar(
                title = {
                    val title = stringResource(R.string.journal_since_you_started)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-1.5).dp),
                    )
                },
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior, listState),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    HrtFilledTonalButton(
                        text = stringResource(
                            if (uiState.isEditMode) {
                                R.string.journal_done
                            } else {
                                R.string.journal_edit
                            }
                        ),
                        onClick = onToggleEdit,
                        modifier = Modifier.padding(end = 8.dp),
                        enabled = uiState.timeline.isNotEmpty(),
                    )
                    // Pin-folder is the only overflow item, and pinning is a silent no-op on
                    // launchers that don't support it, so hide the whole overflow when unsupported.
                    val context = LocalContext.current
                    val pinFolderSupported = remember { AnchorShortcutManager.isSupported(context) }
                    if (pinFolderSupported) {
                        var overflowExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.more_options),
                                )
                            }
                            HrtDropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false },
                                items = listOf(
                                    HrtDropdownMenuItem(
                                        text = stringResource(R.string.anchor_pin_folder_icon),
                                        onClick = {
                                            overflowExpanded = false
                                            onPinFolderIcon()
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
                item(key = "milestones-pinned", contentType = "journal-section") {
                    Column {
                        val pinnedTitle = stringResource(R.string.journal_pinned_section)
                        HrtSectionHeader(
                            topPadding = false,
                            text = pinnedTitle,
                            trailing = if (uiState.hero != null) {
                                {
                                    val titleStyle = MaterialTheme.typography.titleSmall
                                    val density = LocalDensity.current
                                    val iconSize = with(density) {
                                        titleStyle.lineHeight.takeOrElse { titleStyle.fontSize }.toDp()
                                    }
                                    CompositionLocalProvider(
                                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                                    ) {
                                        IconButton(
                                            onClick = onOpenHeroBackground,
                                            modifier = Modifier
                                                .size(iconSize)
                                                // Same font-metric issue as cjkTextOffset: the
                                                // bottom-aligned icon sits above the title glyphs.
                                                // Nudge the icon down (title stays constant).
                                                .cjkTextOffset(text = pinnedTitle, amount = 2.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_wand_stars),
                                                contentDescription = stringResource(
                                                    R.string.journal_hero_background_action,
                                                ),
                                                // Glyph tracks the button (title line height) so it
                                                // scales with the user's font-size setting.
                                                modifier = Modifier.size(iconSize * 0.85f),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        )
                        HrtSection(title = null) {
                            item {
                                PinnedTray(
                                    anchors = uiState.pinnedTray,
                                    heroNextMilestone = uiState.heroNextMilestone,
                                    isEditMode = uiState.isEditMode,
                                    onReorder = onReorder,
                                    onSetPinned = onSetPinned,
                                    today = uiState.today,
                                )
                            }
                        }
                    }
                }

                item(key = "milestones-pinned-timeline-spacer", contentType = "journal-spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item(key = "milestones-timeline", contentType = "journal-section") {
                    HrtSection(title = stringResource(R.string.journal_timeline_section)) {
                        item {
                            MilestonesTimeline(
                                nodes = uiState.timeline,
                                todayDividerIndex = uiState.todayDividerIndex,
                                isEditMode = uiState.isEditMode,
                                onSetPinned = onSetPinned,
                                onUpdateDate = onUpdateDate,
                                today = uiState.today,
                            )
                        }
                    }
                }

                item(key = "milestones-add-date", contentType = "journal-action") {
                    HrtButton(
                        text = stringResource(R.string.journal_add_date),
                        onClick = onAddDate,
                        modifier = Modifier.fillMaxWidth().padding(top = dimensionResource(R.dimen.padding_small)),
                        icon = Icons.Rounded.Add
                    )
                }
            }
        }
    }
}

@Composable
fun AllNotesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AllNotesViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveFailedMessage = stringResource(R.string.journal_note_save_failed)
    val deleteFailedMessage = stringResource(R.string.journal_note_delete_failed)
    LaunchedEffect(uiState.noteMutationError) {
        when (uiState.noteMutationError) {
            JournalNoteMutation.SAVE -> {
                Toast.makeText(context, saveFailedMessage, Toast.LENGTH_SHORT).show()
                viewModel.consumeNoteMutationError()
            }
            JournalNoteMutation.DELETE -> {
                Toast.makeText(context, deleteFailedMessage, Toast.LENGTH_SHORT).show()
                viewModel.consumeNoteMutationError()
            }
            null -> Unit
        }
    }
    val deleteSelectedSuccessCount = uiState.deleteSelectedSuccessCount
    LaunchedEffect(deleteSelectedSuccessCount) {
        if (deleteSelectedSuccessCount != null) {
            Toast.makeText(
                context,
                context.resources.getQuantityString(
                    R.plurals.journal_delete_selected_notes_success,
                    deleteSelectedSuccessCount,
                    deleteSelectedSuccessCount,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            viewModel.consumeDeleteSelectedSuccess()
        }
    }

    AllNotesScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSaveNote = viewModel::saveNote,
        onDeleteNote = viewModel::deleteNote,
        noteSaveFailureToken = uiState.noteSaveFailureToken,
        onToggleSelection = viewModel::toggleSelection,
        onEnterSelection = viewModel::toggleSelection,
        onSelectAll = viewModel::selectDates,
        onCancelSelection = viewModel::clearSelection,
        onDeleteSelected = viewModel::deleteSelectedNotes,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AllNotesScreenContent(
    uiState: AllNotesUiState,
    onNavigateBack: () -> Unit,
    onSaveNote: (LocalDate, String) -> Unit,
    onDeleteNote: (LocalDate) -> Unit = { },
    modifier: Modifier = Modifier,
    noteSaveFailureToken: Int = 0,
    onToggleSelection: (LocalDate) -> Unit = {},
    onEnterSelection: (LocalDate) -> Unit = {},
    onSelectAll: (Set<LocalDate>) -> Unit = {},
    onCancelSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(lazyListState = listState)
    // Shared across every month's timeline so a single note edits at a time, reset on navigation.
    val editorController = rememberNoteEditorController()
    val appLocale = rememberAppLocale()

    DisposableEffect(Unit) {
        onDispose { onCancelSelection() }
    }

    // Month filter (UI-only over the already-grouped notes): null shows every month. The picker is
    // bounded to the months that actually have notes, so an active filter always lands on a
    // non-empty month; a stale selection (its last note deleted) collapses back to "all".
    val availableMonths = remember(uiState.monthGroups) { uiState.monthGroups.map { it.month } }
    var selectedMonth by remember { mutableStateOf<YearMonth?>(null) }
    var isMonthPickerVisible by remember { mutableStateOf(false) }
    val activeMonth = selectedMonth?.takeIf { it in availableMonths }
    val displayedGroups = if (activeMonth == null) {
        uiState.monthGroups
    } else {
        uiState.monthGroups.filter { it.month == activeMonth }
    }

    if (isMonthPickerVisible && availableMonths.isNotEmpty()) {
        MonthPickerDialog(
            availableMonths = availableMonths,
            selectedMonth = activeMonth ?: availableMonths.max(),
            title = stringResource(R.string.journal_filter_by_month),
            appLocale = appLocale,
            onDismiss = { isMonthPickerVisible = false },
            onConfirm = { month ->
                isMonthPickerVisible = false
                selectedMonth = month
            },
        )
    }

    val density = LocalDensity.current
    val selectionFabHideThresholdPx = remember(density) { with(density) { 48.dp.roundToPx() } }
    val selectionFabShowThresholdPx = remember(density) { with(density) { 24.dp.roundToPx() } }

    var isDeleteSelectedConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var isSelectionFabVisible by remember { mutableStateOf(true) }

    // Latched count so the title doesn't blink to 0 during the 220ms flip-out (mirror History).
    val displayedSelectedNoteCount = remember { mutableIntStateOf(uiState.selectedDates.size) }
    if (uiState.isSelectionMode) {
        displayedSelectedNoteCount.intValue = uiState.selectedDates.size
    }

    // All dates currently displayed (respecting an active month filter), for select-all + its enablement.
    val displayedDates = remember(uiState.monthGroups, activeMonth) {
        displayedGroups.flatMap { it.notes }.map { it.date }.toSet()
    }
    val selectAllEnabled = remember { mutableStateOf(false) }
    if (uiState.isSelectionMode) {
        selectAllEnabled.value = displayedDates.any { it !in uiState.selectedDates }
    }

    // Entering selection mode finishes any open editor so a row is never both editing and selectable.
    LaunchedEffect(uiState.isSelectionMode) {
        if (uiState.isSelectionMode) {
            editorController.finish(editorController.activeEditorId)
        }
    }

    // Scroll-hide the FAB while in selection mode (mirror History HistoryScreen.kt:435-476).
    LaunchedEffect(listState, uiState.isSelectionMode, selectionFabHideThresholdPx, selectionFabShowThresholdPx) {
        if (!uiState.isSelectionMode) {
            isSelectionFabVisible = true
            return@LaunchedEffect
        }
        var fabScrollState = SelectionFabScrollState(visible = true)
        isSelectionFabVisible = true
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                fabScrollState = updateSelectionFabScrollState(
                    state = fabScrollState,
                    previousIndex = previousIndex,
                    previousOffset = previousOffset,
                    index = index,
                    offset = offset,
                    estimatedItemSizePx = if (visibleItems.isEmpty()) 0 else visibleItems.sumOf { it.size } / visibleItems.size,
                    hideThresholdPx = selectionFabHideThresholdPx,
                    showThresholdPx = selectionFabShowThresholdPx,
                )
                isSelectionFabVisible = fabScrollState.visible
                previousIndex = index
                previousOffset = offset
            }
    }

    BackHandler(enabled = uiState.isSelectionMode) { onCancelSelection() }

    // Gate on a non-empty selection so the dialog auto-dismisses if the selection drains out from
    // under it (e.g. the selected note vanished while away), instead of showing "Delete 0 notes?".
    if (isDeleteSelectedConfirmationVisible && uiState.selectedDates.isNotEmpty()) {
        HazeAlertDialog(
            onDismissRequest = {
                if (!uiState.isDeletingSelected) isDeleteSelectedConfirmationVisible = false
            },
            title = { Text(text = stringResource(R.string.journal_delete_selected_notes_title)) },
            text = {
                Text(
                    text = pluralStringResource(
                        R.plurals.journal_delete_selected_notes_confirmation,
                        uiState.selectedDates.size,
                        uiState.selectedDates.size,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (uiState.isDeletingSelected) return@TextButton
                        isDeleteSelectedConfirmationVisible = false
                        onDeleteSelected()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete_entries_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!uiState.isDeletingSelected) isDeleteSelectedConfirmationVisible = false
                    },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (uiState.selectedDates.isNotEmpty() && !uiState.isDeletingSelected) {
                        isDeleteSelectedConfirmationVisible = true
                    }
                },
                modifier = Modifier
                    .padding(bottom = LocalAppContentBottomInset.current)
                    .animateFloatingActionButton(
                        visible = uiState.isSelectionMode && isSelectionFabVisible,
                        alignment = Alignment.BottomEnd,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.journal_delete_selected_notes_fab),
                )
            }
        },
        topBar = {
            HazeTopAppBar(
                title = {
                    FlipSlot(
                        flipped = uiState.isSelectionMode,
                        contentAlignment = Alignment.CenterStart,
                        front = {
                            val title = stringResource(R.string.journal_all_notes)
                            Text(
                                text = title,
                                modifier = Modifier.cjkTextOffset(title, amount = (-1.5).dp),
                            )
                        },
                        back = {
                            val title = pluralStringResource(
                                R.plurals.journal_selected_notes_title,
                                displayedSelectedNoteCount.intValue,
                                displayedSelectedNoteCount.intValue,
                            )
                            Text(
                                text = title,
                                modifier = Modifier.cjkTextOffset(title, amount = (-1.5).dp),
                            )
                        },
                    )
                },
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior, listState),
                navigationIcon = {
                    FlipSlot(
                        flipped = uiState.isSelectionMode,
                        front = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.navigate_back),
                                )
                            }
                        },
                        back = {
                            IconButton(onClick = onCancelSelection) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.journal_cancel_selection),
                                )
                            }
                        },
                    )
                },
                actions = {
                    FlipSlot(
                        flipped = uiState.isSelectionMode,
                        contentAlignment = Alignment.CenterEnd,
                        front = {
                            if (uiState.monthGroups.isNotEmpty()) {
                                // Flip between "filter" (tap to pick a month) and "filter off" (tap to
                                // clear), mirroring the History top bar's coin-flip action.
                                FlipSlot(
                                    flipped = activeMonth != null,
                                    contentAlignment = Alignment.CenterEnd,
                                    front = {
                                        IconButton(onClick = { isMonthPickerVisible = true }) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_filter_list),
                                                contentDescription =
                                                    stringResource(R.string.journal_filter_by_month),
                                            )
                                        }
                                    },
                                    back = {
                                        IconButton(onClick = { selectedMonth = null }) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_filter_list_off),
                                                contentDescription =
                                                    stringResource(R.string.journal_clear_month_filter),
                                            )
                                        }
                                    },
                                )
                            }
                        },
                        back = {
                            IconButton(
                                enabled = selectAllEnabled.value,
                                onClick = { onSelectAll(displayedDates) },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SelectAll,
                                    contentDescription = stringResource(R.string.journal_select_all),
                                )
                            }
                        },
                    )
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
                modifier = Modifier
                    .fillMaxSize()
                    // Lift the list above the keyboard so a focused note editor scrolls into view.
                    .imePadding(),
                contentPadding = appContentPaddingValuesBehindTopAppBar(innerPadding),
            ) {
                if (displayedGroups.isEmpty()) {
                    item(key = "all-notes-empty", contentType = "journal-empty") {
                        EmptyAllNotesCard()
                    }
                } else {
                    displayedGroups.forEachIndexed { groupIndex, group ->
                        item(
                            key = "all-notes-header-${group.month}",
                            contentType = "journal-month-header",
                        ) {
                            AllNotesMonthHeader(
                                monthLabel = allNotesMonthLabel(group.month),
                                noteCount = group.notes.size,
                            )
                        }

                        itemsIndexed(
                            items = group.notes,
                            key = { _, note -> "all-notes-${note.id}" },
                            contentType = { _, _ -> "journal-note-row" },
                        ) { index, note ->
                            AllNotesNoteRow(
                                note = note,
                                index = index,
                                count = group.notes.size,
                                controller = editorController,
                                onSave = onSaveNote,
                                onDelete = onDeleteNote,
                                saveFailureToken = noteSaveFailureToken,
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = note.date in uiState.selectedDates,
                                onToggleSelection = onToggleSelection,
                                onEnterSelection = onEnterSelection,
                            )
                            if (index < group.notes.lastIndex) {
                                Spacer(
                                    modifier = Modifier.height(
                                        dimensionResource(R.dimen.list_segment_gap)
                                    )
                                )
                            }
                        }

                        if (groupIndex < displayedGroups.lastIndex) {
                            item(key = "all-notes-gap-${group.month}") {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Month section header for the All Notes list, matching the calibration screen's: an uppercased
// month label, a hairline divider filling the row, and the month's note count on the trailing end.
@Composable
private fun AllNotesMonthHeader(
    monthLabel: String,
    noteCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = monthLabel.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        Text(
            text = noteCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun allNotesMonthLabel(month: YearMonth): String {
    val appLocale = rememberAppLocale()
    val formatter = remember(appLocale) {
        monthHeaderFormatter(appLocale, currentYear = Int.MIN_VALUE)
    }
    return formatter(month.atDay(1))
}

// ---- Previews ----

// Sample state for the "Since you started" previews: a hero on a long-running medication
// (pinned, shown on Home), a second past pin, and a future goal that only appears in the
// timeline. todayDividerIndex = 2 places the Today node before the future "Surgery" node.
private fun previewMilestonesUiState(isEditMode: Boolean): MilestonesUiState {
    val estradiol = AnchorRowUiState(
        id = "estradiol", name = "On estradiol", icon = AnchorIcon.MEDICATION,
        palette = MedicationGroupColorKey.ROSE, date = LocalDate.of(2024, 4, 1),
        dayMagnitude = 807, isFuture = false,
    )
    val injection = AnchorRowUiState(
        id = "injection", name = "First injection", icon = AnchorIcon.VACCINES,
        palette = MedicationGroupColorKey.INDIGO, date = LocalDate.of(2026, 3, 1),
        dayMagnitude = 108, isFuture = false,
    )
    val surgery = AnchorRowUiState(
        id = "surgery", name = "Surgery", icon = AnchorIcon.FLAG,
        palette = MedicationGroupColorKey.SAGE, date = LocalDate.of(2026, 9, 15),
        dayMagnitude = 90, isFuture = true,
    )
    return MilestonesUiState(
        isLoading = false,
        today = LocalDate.of(2026, 6, 17),
        hero = estradiol,
        heroNextMilestone = NextMilestoneUiState(remainingDays = 193, value = 1000, unit = MilestoneUnit.DAYS),
        pinnedTray = listOf(estradiol, injection),
        timeline = listOf(
            TimelineNodeUiState(anchor = estradiol, isPinned = true),
            TimelineNodeUiState(anchor = injection, isPinned = false),
            TimelineNodeUiState(anchor = surgery, isPinned = false),
        ),
        todayDividerIndex = 2,
        isEditMode = isEditMode,
    )
}

@Preview(name = "Since you started – page", showBackground = true, widthDp = 420, heightDp = 940)
@Composable
private fun MilestonesScreenContentPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MilestonesScreenContent(
            uiState = previewMilestonesUiState(isEditMode = false),
            onNavigateBack = {},
            onToggleEdit = {},
            onSetPinned = { _, _ -> },
            onReorder = {},
            onAddDate = {},
            onUpdateDate = {},
            onOpenHeroBackground = {},
            onPinFolderIcon = {},
        )
    }
}

@Preview(name = "Pinned section – view", showBackground = true, widthDp = 420)
@Composable
private fun PinnedSectionViewPreview() {
    MilestonesSectionPreview { state ->
        HrtSection(title = stringResource(R.string.journal_pinned_section)) {
            item {
                PinnedTray(
                    anchors = state.pinnedTray,
                    heroNextMilestone = state.heroNextMilestone,
                    isEditMode = state.isEditMode,
                    onReorder = {},
                    onSetPinned = { _, _ -> },
                    today = state.today,
                )
            }
        }
    }
}

@Preview(name = "Pinned section – edit", showBackground = true, widthDp = 420)
@Composable
private fun PinnedSectionEditPreview() {
    MilestonesSectionPreview(isEditMode = true) { state ->
        HrtSection(title = stringResource(R.string.journal_pinned_section)) {
            item {
                PinnedTray(
                    anchors = state.pinnedTray,
                    heroNextMilestone = state.heroNextMilestone,
                    isEditMode = state.isEditMode,
                    onReorder = {},
                    onSetPinned = { _, _ -> },
                    today = state.today,
                )
            }
        }
    }
}

@Preview(name = "Timeline section", showBackground = true, widthDp = 420)
@Composable
private fun MilestonesTimelineSectionPreview() {
    MilestonesSectionPreview { state ->
        HrtSection(title = stringResource(R.string.journal_timeline_section)) {
            item {
                MilestonesTimeline(
                    nodes = state.timeline,
                    todayDividerIndex = state.todayDividerIndex,
                    isEditMode = state.isEditMode,
                    onSetPinned = { _, _ -> },
                    onUpdateDate = {},
                    today = state.today,
                )
            }
        }
    }
}

// Themed surface + content padding shared by the single-section previews.
@Composable
private fun MilestonesSectionPreview(
    isEditMode: Boolean = false,
    content: @Composable (MilestonesUiState) -> Unit,
) {
    HrtTrackerTheme(dynamicColor = false) {
        Surface {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                content(previewMilestonesUiState(isEditMode = isEditMode))
            }
        }
    }
}
