package com.mkx.hrttracker.ui.plan

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

internal data class PastScheduleSelectorUiState(
    val selectedOption: PastScheduleOption,
    val showGeneratePastRecordsOption: Boolean,
    val enabled: Boolean,
    val interactive: Boolean = enabled,
    val lockedMessage: String? = null,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PastScheduleSelectorCard(
    modifier: Modifier = Modifier,
    state: PastScheduleSelectorUiState,
    onOptionSelected: (PastScheduleOption) -> Unit,
    index: Int = 0,
    count: Int = 1,
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.group_past_plans_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp, top = 2.dp)
            )
            PastScheduleSelectorRows(
                state = state,
                onOptionSelected = onOptionSelected,
            )
        }
    }
}

@Composable
private fun PastScheduleSelectorRows(
    state: PastScheduleSelectorUiState,
    onOptionSelected: (PastScheduleOption) -> Unit,
) {
    // Remember the generate-records choice across a "Do not show" detour.
    // Switching to "Do not show" clears the underlying create flag (it can't
    // generate records for past slots that aren't shown), so without this the
    // checkbox would come back unchecked when the user re-enables "Show past".
    // Latched only while past is shown — see below.
    var rememberedGenerateRecords by rememberSaveable {
        mutableStateOf(state.selectedOption == PastScheduleOption.SHOW_AND_GENERATE_RECORDS)
    }

    val lockedMessage = state.lockedMessage
    if (lockedMessage != null) {
        PastScheduleLockedRow(
            text = lockedMessage,
            index = 0,
            count = 1,
        )
        return
    }

    // "Do not show" and "Show past" are the mutually exclusive radio choices;
    // "also generate records" is a checkbox sub-choice that stacks on top of
    // "Show past" (so that radio stays selected while it's checked). The checkbox
    // row animates in/out (reflowing the segment shapes of the rows above it) as
    // state.showGeneratePastRecordsOption toggles. That flag already requires the
    // card to be interactive (see resolvePastScheduleSelectorState), so the row
    // never appears for locked/archived groups. HrtSection owns the per-row
    // segment positions, so the rows omit index/count and inherit them.
    val showsPast = state.selectedOption == PastScheduleOption.SHOW ||
            state.selectedOption == PastScheduleOption.SHOW_AND_GENERATE_RECORDS
    val generatesRecords = state.selectedOption == PastScheduleOption.SHOW_AND_GENERATE_RECORDS
    // The checkbox reflects a real choice only while past is shown; latch it then
    // and leave it untouched under "Do not show" so re-enabling restores it.
    if (showsPast) rememberedGenerateRecords = generatesRecords
    HrtSection(title = null) {
        item {
            PastScheduleOptionRow(
                title = stringResource(R.string.group_past_schedule_do_not_show),
                option = PastScheduleOption.DO_NOT_SHOW,
                selected = state.selectedOption == PastScheduleOption.DO_NOT_SHOW,
                asCheckbox = false,
                enabled = state.enabled,
                interactive = state.interactive,
                onToggle = { onOptionSelected(PastScheduleOption.DO_NOT_SHOW) },
            )
        }
        item {
            PastScheduleOptionRow(
                title = stringResource(R.string.group_past_schedule_show),
                option = PastScheduleOption.SHOW,
                selected = showsPast,
                asCheckbox = false,
                enabled = state.enabled,
                interactive = state.interactive,
                // Selecting "Show past" restores the remembered generate-records
                // choice (so it survives a "Do not show" detour); re-selecting it
                // while already on is a no-op for the same reason.
                onToggle = {
                    onOptionSelected(
                        if (rememberedGenerateRecords) {
                            PastScheduleOption.SHOW_AND_GENERATE_RECORDS
                        } else {
                            PastScheduleOption.SHOW
                        }
                    )
                },
            )
        }
        animatedItem(visible = state.showGeneratePastRecordsOption) {
            PastScheduleOptionRow(
                title = stringResource(R.string.group_past_schedule_show_and_generate_records),
                option = PastScheduleOption.SHOW_AND_GENERATE_RECORDS,
                // Drive the checkbox from the latched value, not the live option,
                // so it holds its state through the row's collapse animation when
                // "Do not show" is picked instead of visibly unchecking mid-exit.
                selected = rememberedGenerateRecords,
                asCheckbox = true,
                enabled = state.enabled,
                interactive = state.interactive,
                // Toggling the checkbox flips between showing past slots with and
                // without generated records, keeping past slots shown either way.
                onToggle = {
                    onOptionSelected(
                        if (rememberedGenerateRecords) {
                            PastScheduleOption.SHOW
                        } else {
                            PastScheduleOption.SHOW_AND_GENERATE_RECORDS
                        }
                    )
                },
            )
        }
    }
}

// RadioButton's intrinsic layout footprint (RadioButtonTokens.IconSize 20.dp +
// 2.dp padding per side) when LocalMinimumInteractiveComponentSize is disabled.
// The checkbox is pinned to this so it aligns with the radio rows above it.
private val PastScheduleControlFootprint = 24.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PastScheduleOptionRow(
    title: String,
    option: PastScheduleOption,
    selected: Boolean,
    asCheckbox: Boolean,
    enabled: Boolean,
    interactive: Boolean,
    onToggle: () -> Unit,
) {
    EditorSegmentedListItem(
        cornerShape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onClick = if (interactive) onToggle else null,
        leadingContent = {
            Icon(
                painter = painterResource(pastScheduleOptionIconRes(option)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingContent = {
            if (asCheckbox) {
                // This editor disables LocalMinimumInteractiveComponentSize, so
                // there's no 48.dp touch-target box to center controls within and
                // each control lays out at its intrinsic size: RadioButton at
                // 24.dp (20.dp icon + 2.dp padding), Checkbox at only 18.dp. Pin
                // the checkbox into the radios' 24.dp footprint so it lines up
                // with the radio rows above it.
                Box(
                    modifier = Modifier.size(PastScheduleControlFootprint),
                    contentAlignment = Alignment.Center,
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = if (interactive) {
                            { onToggle() }
                        } else {
                            null
                        },
                        enabled = enabled,
                    )
                }
            } else {
                RadioButton(
                    selected = selected,
                    onClick = if (interactive) onToggle else null,
                    enabled = enabled,
                )
            }
        },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.cjkTextOffset(title)
        )
    }
}

@DrawableRes
private fun pastScheduleOptionIconRes(option: PastScheduleOption): Int {
    return when (option) {
        PastScheduleOption.DO_NOT_SHOW -> R.drawable.ic_calendar_today
        PastScheduleOption.SHOW -> R.drawable.ic_calendar_add_on
        PastScheduleOption.SHOW_AND_GENERATE_RECORDS -> R.drawable.ic_calendar_check
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PastScheduleLockedRow(
    text: String,
    index: Int,
    count: Int,
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        cornerShape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_calendar_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.cjkTextOffset(text)
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun PastScheduleSelectorCardPreview() {
    // The editor renders this card with LocalMinimumInteractiveComponentSize
    // disabled (no 48.dp touch-target box). Reproduce that here so the preview
    // matches the device's control sizing/alignment instead of hiding it.
    HrtTrackerTheme(dynamicColor = false) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                // Show past selected: generate-records checkbox visible but unchecked.
                PastScheduleSelectorCard(
                    state = PastScheduleSelectorUiState(
                        selectedOption = PastScheduleOption.SHOW,
                        showGeneratePastRecordsOption = true,
                        enabled = true,
                    ),
                    onOptionSelected = {},
                )
                // Show and generate selected: checkbox checked.
                PastScheduleSelectorCard(
                    state = PastScheduleSelectorUiState(
                        selectedOption = PastScheduleOption.SHOW_AND_GENERATE_RECORDS,
                        showGeneratePastRecordsOption = true,
                        enabled = true,
                    ),
                    onOptionSelected = {},
                )
                // Do not show: the generate-records row is hidden entirely.
                PastScheduleSelectorCard(
                    state = PastScheduleSelectorUiState(
                        selectedOption = PastScheduleOption.DO_NOT_SHOW,
                        showGeneratePastRecordsOption = false,
                        enabled = true,
                    ),
                    onOptionSelected = {},
                )
                // Locked (e.g. viewing a group whose past schedule can't be edited).
                PastScheduleSelectorCard(
                    state = PastScheduleSelectorUiState(
                        selectedOption = PastScheduleOption.SHOW,
                        showGeneratePastRecordsOption = false,
                        enabled = false,
                        interactive = false,
                        lockedMessage = stringResource(
                            R.string.group_past_schedule_recreated_locked_note
                        ),
                    ),
                    onOptionSelected = {},
                )
            }
        }
    }
}
