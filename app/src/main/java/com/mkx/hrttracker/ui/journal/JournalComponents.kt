package com.mkx.hrttracker.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDate

private const val TodayComposerTextFieldTestTag = "today-composer-text-field"

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
    var isEditing by rememberSaveable(today.toString(), note?.id, note?.text) {
        mutableStateOf(false)
    }
    var draftText by rememberSaveable(today.toString(), note?.id, note?.text) {
        mutableStateOf(note?.text.orEmpty())
    }
    val currentText = note?.text.orEmpty()

    if (isEditing) {
        TodayComposerEditor(
            today = today,
            text = draftText,
            onTextChange = { draftText = it },
            onCancel = {
                draftText = currentText
                isEditing = false
            },
            onSave = {
                val text = draftText.trim()
                if (text.isNotEmpty()) {
                    onSave(text)
                    isEditing = false
                }
            },
            modifier = modifier,
        )
        return
    }

    PreferenceSegmentedListItem(
        modifier = modifier,
        title = stringResource(R.string.journal_today),
        supportingText = note?.text ?: stringResource(R.string.journal_write_about_today),
        onClick = {
            draftText = currentText
            isEditing = true
        },
    )
}

@Composable
private fun TodayComposerEditor(
    today: LocalDate,
    text: String,
    onTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSave = text.isNotBlank()
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }

    EditorSegmentedListItem(modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.journal_today),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = dateFormatter(today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TodayComposerTextFieldTestTag),
                placeholder = {
                    Text(text = stringResource(R.string.journal_write_about_today))
                },
                minLines = 3,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                HrtOutlinedButton(
                    text = stringResource(R.string.cancel),
                    onClick = onCancel,
                    compact = true,
                )
                Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
                HrtButton(
                    text = stringResource(R.string.save),
                    onClick = onSave,
                    enabled = canSave,
                    compact = true,
                )
            }
        }
    }
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
