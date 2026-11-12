package com.mkx.hrttracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.settings.ReminderRepeats
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.PickerWheel
import com.swmansion.kmpwheelpicker.rememberWheelPickerState

/** The Repeat reminders row's value: "Off", or "3 × 15 min". */
@Composable
internal fun reminderRepeatsLabel(repeats: ReminderRepeats): String =
    if (repeats.count == 0) {
        stringResource(R.string.settings_reminder_repeats_off)
    } else {
        stringResource(R.string.settings_reminder_repeats_value, repeats.count, repeats.intervalMinutes)
    }

/** Two wheels, in the month picker's design: how many repeats, and how far apart. */
@Composable
internal fun ReminderRepeatsPickerDialog(
    repeats: ReminderRepeats,
    onDismiss: () -> Unit,
    onConfirm: (ReminderRepeats) -> Unit,
) {
    val counts = ReminderRepeats.COUNTS
    val intervals = ReminderRepeats.INTERVALS_MINUTES
    val countState = rememberWheelPickerState(
        itemCount = counts.size,
        initialIndex = counts.indexOf(repeats.count).coerceAtLeast(0),
    )
    val intervalState = rememberWheelPickerState(
        itemCount = intervals.size,
        initialIndex = intervals.indexOf(repeats.intervalMinutes).coerceAtLeast(0),
    )
    val off = stringResource(R.string.settings_reminder_repeats_off)
    val countLabels = counts.map { count -> if (count == 0) off else count.toString() }
    val intervalLabels = intervals.map { minutes ->
        stringResource(R.string.settings_reminder_repeats_interval_value, minutes)
    }

    HazeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_reminder_repeats)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(R.string.settings_reminder_repeats_count, R.string.settings_reminder_repeats_interval)
                        .forEach { label ->
                            Text(
                                text = stringResource(label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PickerWheel(options = countLabels, state = countState, modifier = Modifier.weight(1f))
                    PickerWheel(options = intervalLabels, state = intervalState, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        ReminderRepeats(
                            count = counts[countState.index],
                            intervalMinutes = intervals[intervalState.index],
                        )
                    )
                }
            ) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}
