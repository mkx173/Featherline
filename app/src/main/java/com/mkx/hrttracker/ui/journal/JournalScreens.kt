package com.mkx.hrttracker.ui.journal

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.MilestoneUnit
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.HrtSectionHeader
import com.mkx.hrttracker.ui.components.ScrollToTopSignalEffect
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.hrtSection
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.pinnedTopAppBarScrollBehavior
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.monthHeaderFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDate
import java.time.YearMonth

private const val JournalScreenListTestTag = "journal-screen-list"

@Composable
fun JournalScreen(
    onOpenMilestones: () -> Unit,
    onOpenAllNotes: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
    viewModel: JournalViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    JournalScreenContent(
        uiState = uiState,
        onOpenMilestones = onOpenMilestones,
        onOpenAllNotes = onOpenAllNotes,
        onSaveTodayNote = viewModel::saveTodayNote,
        onSaveNote = viewModel::saveNote,
        onDeleteTodayNote = viewModel::deleteTodayNote,
        onDeleteNote = viewModel::deleteNote,
        scrollToTopSignal = scrollToTopSignal,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreenContent(
    uiState: JournalUiState,
    onOpenMilestones: () -> Unit,
    onOpenAllNotes: () -> Unit,
    onSaveTodayNote: (String) -> Unit,
    onSaveNote: (LocalDate, String) -> Unit,
    onDeleteTodayNote: () -> Unit = { },
    onDeleteNote: (LocalDate) -> Unit = { },
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
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
                title = { Text(text = stringResource(R.string.tab_journal)) },
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
                                EmptyMilestonesCard(onAddDate = onOpenMilestones)
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
                            trailing = {
                                Text(
                                    text = stringResource(R.string.journal_notes_window_meta),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,

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
                        HrtOutlinedButton(
                            text = pluralStringResource(
                                R.plurals.journal_see_all_notes_earlier,
                                uiState.olderNotesCount,
                                uiState.olderNotesCount,
                            ),
                            onClick = onOpenAllNotes,
                            trailingIcon = Icons.Rounded.ChevronRight,
                            modifier = Modifier
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
                                    supportingText = stringResource(R.string.journal_all_notes_empty),
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

@Composable
fun MilestonesScreen(
    onNavigateBack: () -> Unit,
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

    // Edit mode lives in the Activity-scoped ViewModel, so it would otherwise persist
    // across navigation. Reset it when leaving the screen so re-entry starts in view mode.
    DisposableEffect(Unit) {
        onDispose { viewModel.exitEditMode() }
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
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(lazyListState = listState)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBar(
                title = { Text(text = stringResource(R.string.journal_since_you_started)) },
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
                        HrtSectionHeader(
                            topPadding = false,
                            text = stringResource(R.string.journal_pinned_section),
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
                                            modifier = Modifier.size(iconSize),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_wand_stars),
                                                contentDescription = stringResource(
                                                    R.string.journal_hero_background_action,
                                                ),
                                                modifier = Modifier.size(iconSize),
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

    AllNotesScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSaveNote = viewModel::saveNote,
        onDeleteNote = viewModel::deleteNote,
        today = uiState.today,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllNotesScreenContent(
    uiState: AllNotesUiState,
    onNavigateBack: () -> Unit,
    onSaveNote: (LocalDate, String) -> Unit,
    onDeleteNote: (LocalDate) -> Unit = { },
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(lazyListState = listState)
    // Shared across every month's timeline so a single note edits at a time, reset on navigation.
    val editorController = rememberNoteEditorController()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBar(
                title = { Text(text = stringResource(R.string.journal_all_notes)) },
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior, listState),
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
                if (uiState.monthGroups.isEmpty()) {
                    item(key = "all-notes-empty", contentType = "journal-empty") {
                        EmptyAllNotesCard()
                    }
                } else {
                    uiState.monthGroups.forEachIndexed { index, group ->
                        item(
                            key = "all-notes-${group.month}",
                            contentType = "journal-month-section",
                        ) {
                            HrtSection(
                                title = allNotesMonthLabel(group.month),
                                topPadding = index != 0,
                                headerTrailing = {
                                    Text(
                                        text = group.notes.size.toString(),
                                        modifier = Modifier.alignByBaseline(),
                                    )
                                },
                                headerTrailingAlignByBaseline = true,
                            ) {
                                item {
                                    NotesTimeline(
                                        notes = group.notes,
                                        today = today,
                                        onSave = onSaveNote,
                                        onDelete = onDeleteNote,
                                        editorController = editorController,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
