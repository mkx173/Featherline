package com.mkx.hrttracker.ui.journal

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.pinnedTopAppBarScrollBehavior
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import java.time.LocalDate

@Composable
fun JournalScreen(
    onOpenMilestones: () -> Unit,
    onOpenAllNotes: () -> Unit,
    modifier: Modifier = Modifier,
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
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(lazyListState = listState)
    val timelineNotes = uiState.recentNotes.filter { it.date != uiState.today }

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
                modifier = Modifier.fillMaxSize(),
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
                                    anchors = uiState.pinnedAnchors,
                                    onClick = onOpenMilestones,
                                )
                            }
                        } else {
                            item {
                                EmptyMilestonesCard(onAddDate = onOpenMilestones)
                            }
                        }
                    }
                }

                item(key = "journal-notes", contentType = "journal-section") {
                    HrtSection(
                        title = stringResource(R.string.journal_notes_section),
                        headerTrailing = {
                            Text(text = stringResource(R.string.journal_notes_window_meta))
                        },
                    ) {
                        item {
                            TodayComposer(
                                today = uiState.today,
                                note = uiState.todayNote,
                                onSave = onSaveTodayNote,
                            )
                        }
                        item {
                            NotesTimeline(
                                notes = timelineNotes,
                                today = uiState.today,
                                onSave = onSaveNote,
                            )
                        }
                    }
                }

                if (uiState.olderNotesCount > 0) {
                    item(key = "journal-see-all-notes", contentType = "journal-action") {
                        HrtOutlinedButton(
                            text = "${stringResource(R.string.journal_see_all_notes)} · " +
                                    "${uiState.olderNotesCount} earlier",
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
) {
    Box(modifier = modifier.fillMaxSize())
}

@Composable
fun AllNotesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize())
}
