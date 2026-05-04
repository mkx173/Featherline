package com.mkx.hrttracker.ui.plan

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.segmentedListItemShapes

internal data class PastScheduleSelectorUiState(
    val selectedOption: PastScheduleOption,
    val showGeneratePastRecordsOption: Boolean,
    val enabled: Boolean,
    val lockedMessage: String? = null,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PastScheduleSelectorCard(
    state: PastScheduleSelectorUiState,
    onOptionSelected: (PastScheduleOption) -> Unit,
    index: Int = 0,
    count: Int = 1,
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = {},
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.group_past_plans_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
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
    val lockedMessage = state.lockedMessage
    if (lockedMessage != null) {
        PastScheduleLockedRow(
            text = lockedMessage,
            index = 0,
            count = 1,
        )
        return
    }

    val optionLabels = buildList {
        add(PastScheduleOption.DO_NOT_SHOW to R.string.group_past_schedule_do_not_show)
        add(PastScheduleOption.SHOW to R.string.group_past_schedule_show)
        if (state.showGeneratePastRecordsOption) {
            add(
                PastScheduleOption.SHOW_AND_GENERATE_RECORDS to
                    R.string.group_past_schedule_show_and_generate_records
            )
        }
    }
    optionLabels.forEachIndexed { optionIndex, (option, labelRes) ->
        PastScheduleOptionRow(
            title = stringResource(labelRes),
            option = option,
            selectedOption = state.selectedOption,
            enabled = state.enabled,
            index = optionIndex,
            count = optionLabels.size,
            onOptionSelected = onOptionSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PastScheduleOptionRow(
    title: String,
    option: PastScheduleOption,
    selectedOption: PastScheduleOption,
    enabled: Boolean,
    index: Int,
    count: Int,
    onOptionSelected: (PastScheduleOption) -> Unit,
) {
    SegmentedListItem(
        leadingContent = {
            Icon(
                painter = painterResource(pastScheduleOptionIconRes(option)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingContent = {
            RadioButton(
                selected = selectedOption == option,
                onClick = { onOptionSelected(option) },
                enabled = enabled,
            )
        },
        onClick = if (enabled) {
            { onOptionSelected(option) }
        } else {
            {}
        },
        enabled = true,
        shapes = segmentedListItemShapes(
            index = index,
            count = count,
            cornerShape = MaterialTheme.shapes.medium,
            pressedShape = MaterialTheme.shapes.medium
        ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
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
    SegmentedListItem(
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_error_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = {},
        enabled = true,
        shapes = segmentedListItemShapes(
            index = index,
            count = count,
            cornerShape = MaterialTheme.shapes.medium,
            pressedShape = MaterialTheme.shapes.medium
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.cjkTextOffset(text)
        )
    }
}
