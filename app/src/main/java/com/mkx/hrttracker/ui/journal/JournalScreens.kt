package com.mkx.hrttracker.ui.journal

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.MilestoneUnit
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.HrtSectionHeader
import com.mkx.hrttracker.ui.components.ScrollToTopSignalEffect
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
                    CircularProgressIndicator()
                }
                return@AppContentContainer
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
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
                                MilestonesStackCard(
                                    today = uiState.today,
                                    anchors = uiState.pinnedAnchors,
                                    onClick = onOpenMilestones,
                                )
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
                }

                hrtSection(
                    key = "journal-notes",
                    header = {
                        HrtSectionHeader(
                            text = stringResource(R.string.journal_notes_section),
                            trailing = {
                                Text(text = stringResource(R.string.journal_notes_window_meta))
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
                        )
                    }
                    if (timelineNotes.isEmpty()) {
                        item(key = "journal-notes-empty") {
                            EmptyRecentNotesCard()
                        }
                    } else {
                        timelineNotes.forEach { note ->
                            item(key = "note-${note.id}") {
                                NoteTimelineRow(
                                    note = note,
                                    onSave = onSaveNote,
                                    onDelete = onDeleteNote,
                                    today = uiState.today,
                                )
                            }
                        }
                    }
                }

                if (uiState.olderNotesCount > 0) {
                    item(key = "journal-see-all-notes", contentType = "journal-action") {
                        HrtOutlinedButton(
                            text = pluralStringResource(
                                R.plurals.journal_see_all_notes_earlier,
                                uiState.olderNotesCount,
                                uiState.olderNotesCount,
                            ),
                            onClick = onOpenAllNotes,
                            modifier = Modifier
                                .padding(top = dimensionResource(R.dimen.padding_small)),
                        )
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
    val activeEditingAnchor = editingAnchor
    val closeDateSheet = {
        isAddDateSheetOpen = false
        editingAnchor = null
    }

    MilestonesScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onToggleEdit = viewModel::toggleEditMode,
        onSetPinned = viewModel::setPinned,
        onReorder = viewModel::reorderPinned,
        onAddDate = { isAddDateSheetOpen = true },
        onUpdateDate = { anchor -> editingAnchor = anchor },
        modifier = modifier,
    )

    if (isAddDateSheetOpen || activeEditingAnchor != null) {
        AddDateSheet(
            today = uiState.today,
            anchor = activeEditingAnchor,
            onDismissRequest = closeDateSheet,
            onConfirm = { name, icon, date, paletteKey ->
                if (activeEditingAnchor == null) {
                    viewModel.addDate(
                        name = name,
                        icon = icon,
                        date = date,
                        paletteKey = paletteKey,
                    )
                } else {
                    viewModel.updateDate(
                        id = activeEditingAnchor.id,
                        name = name,
                        icon = icon,
                        date = date,
                        paletteKey = paletteKey,
                    )
                }
            },
            onDelete = activeEditingAnchor?.let { anchor ->
                { viewModel.deleteDate(anchor.id) }
            },
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
                    TextButton(onClick = onToggleEdit) {
                        Text(
                            text = stringResource(
                                if (uiState.isEditMode) {
                                    R.string.journal_done
                                } else {
                                    R.string.journal_edit
                                }
                            )
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
                    CircularProgressIndicator()
                }
                return@AppContentContainer
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = appContentPaddingValuesBehindTopAppBar(innerPadding),
            ) {
                item(key = "milestones-pinned", contentType = "journal-section") {
                    HrtSection(title = stringResource(R.string.journal_pinned_section)) {
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
                    HrtFilledTonalButton(
                        text = stringResource(R.string.journal_add_date),
                        onClick = onAddDate,
                        modifier = Modifier.padding(top = dimensionResource(R.dimen.padding_small)),
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
                    CircularProgressIndicator()
                }
                return@AppContentContainer
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
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
                                group.notes.forEach { note ->
                                    item {
                                        NoteTimelineRow(
                                            note = note,
                                            today = today,
                                            onSave = onSaveNote,
                                            onDelete = onDeleteNote,
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
