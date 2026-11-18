package com.mkx.hrttracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    // Keyed on a saved seed that follows repeats only while both wheels still show it: a save
    // confirmed a moment ago can land after the picker reopened, and untouched wheels move to it,
    // while a turned wheel keeps the user's choice. The seed is saveable, so after a process death
    // turned wheels come back as they were and untouched ones follow the default, then the stored
    // repeats once it loads.
    // ponytail: "untouched" means "rests on the seed", so a wheel turned away and back follows a
    // late save again; track touches on the wheel if that matters.
    var seedCount by rememberSaveable { mutableIntStateOf(repeats.count) }
    var seedInterval by rememberSaveable { mutableIntStateOf(repeats.intervalMinutes) }
    val seedCountIndex = counts.indexOf(seedCount).coerceAtLeast(0)
    val seedIntervalIndex = intervals.indexOf(seedInterval).coerceAtLeast(0)
    val (countState, intervalState) = key(seedCount, seedInterval) {
        rememberWheelPickerState(itemCount = counts.size, initialIndex = seedCountIndex) to
            rememberWheelPickerState(itemCount = intervals.size, initialIndex = seedIntervalIndex)
    }
    // A drag moves only value and a tap sets index first, so both are checked: a wheel counts as
    // touched from the first pixel. Derived, so a drag doesn't recompose the dialog per frame.
    val untouched by remember(countState, intervalState, seedCountIndex, seedIntervalIndex) {
        derivedStateOf {
            countState.index == seedCountIndex && countState.value == seedCountIndex.toFloat() &&
                intervalState.index == seedIntervalIndex && intervalState.value == seedIntervalIndex.toFloat()
        }
    }
    if (untouched && (seedCount != repeats.count || seedInterval != repeats.intervalMinutes)) {
        SideEffect {
            seedCount = repeats.count
            seedInterval = repeats.intervalMinutes
        }
    }
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
