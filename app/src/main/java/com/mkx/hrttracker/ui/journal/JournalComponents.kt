package com.mkx.hrttracker.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import java.time.LocalDate

@Composable
fun MilestonesStackCard(
    anchors: List<AnchorRowUiState>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorSegmentedListItem(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))) {
            anchors.take(3).forEach { anchor ->
                Text(
                    text = anchor.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = anchor.dayCountLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun EmptyMilestonesCard(
    onAddDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorSegmentedListItem(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                text = stringResource(R.string.journal_no_dates),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            HrtButton(
                text = stringResource(R.string.journal_add_date),
                onClick = onAddDate,
            )
        }
    }
}

@Composable
fun TodayComposer(
    today: LocalDate,
    note: Note?,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceSegmentedListItem(
        modifier = modifier,
        title = stringResource(R.string.journal_today),
        supportingText = note?.text ?: stringResource(R.string.journal_write_about_today),
    )
}

@Composable
fun NotesTimeline(
    notes: List<Note>,
    today: LocalDate,
    onSave: (LocalDate, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (notes.isEmpty()) {
            EmptyRecentNotesCard()
        } else {
            notes.forEach { note ->
                NoteTimelineRow(
                    note = note,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
fun NoteTimelineRow(
    note: Note,
    onSave: (LocalDate, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceSegmentedListItem(
        modifier = modifier,
        title = note.text,
        supportingText = note.date.toString(),
    )
}

@Composable
private fun EmptyRecentNotesCard(
    modifier: Modifier = Modifier,
) {
    PreferenceSegmentedListItem(
        modifier = modifier,
        title = stringResource(R.string.journal_notes_window_meta),
        supportingText = stringResource(R.string.journal_write_about_today),
    )
}

private fun AnchorRowUiState.dayCountLabel(): String {
    return if (isFuture) {
        "in $dayMagnitude days"
    } else {
        "$dayMagnitude days"
    }
}
