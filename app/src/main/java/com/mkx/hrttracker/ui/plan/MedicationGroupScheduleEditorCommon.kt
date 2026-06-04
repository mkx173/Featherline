package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.DangerZoneListItem
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IntervalStepperCard(
    label: String,
    value: Int,
    unit: String,
    onDecreaseClick: () -> Unit,
    onIncreaseClick: () -> Unit,
    enabled: Boolean = true,
    locked: Boolean = false,
    index: Int = 0,
    count: Int = 1
) {
    val controlsEnabled = enabled && !locked

    EditorSegmentedListItem(
        index = index,
        count = count,
        overlineContent = {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_repeat),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = if (!controlsEnabled) {
            null
        } else {
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    StepperCircleButton(
                        icon = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.decrease_schedule_interval),
                        enabled = value > 1,
                        onClick = onDecreaseClick
                    )
                    StepperCircleButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.increase_schedule_interval),
                        enabled = true,
                        onClick = onIncreaseClick
                    )
                }
            }
        },
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline()
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

@Composable
private fun StepperCircleButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun EditorFieldRow(
    label: String,
    value: String,
    icon: Painter,
    onClick: () -> Unit,
    originalValue: String? = null,
    enabled: Boolean = true,
    locked: Boolean = false,
    index: Int = 0,
    count: Int = 1
) {
    val fieldEnabled = enabled && !locked
    EditorSegmentedListItem(
        index = index,
        count = count,
        // Null onClick takes EditorSegmentedListItem's static path so the
        // row drops its ripple when locked or otherwise not editable.
        onClick = if (fieldEnabled) onClick else null,
        enabled = true,
        leadingContent = {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        overlineContent = {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = if (!fieldEnabled) {
            null
        } else {
            {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
    ) {
        ChangedScheduleTimeText(
            value = value,
            originalValue = originalValue,
        )
    }
}

@Composable
internal fun ChangedScheduleTimeText(
    value: String,
    originalValue: String?,
) {
    val changedOriginalValue = originalValue?.takeIf { it != value }
    if (changedOriginalValue == null) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = changedOriginalValue,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right_alt),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScheduleTimeRow(
    formattedTime: String,
    formattedOriginalTime: String?,
    onClick: () -> Unit,
    enabled: Boolean,
    index: Int = 0,
    count: Int = 1,
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        // Null onClick takes the static surface path so a locked / archived
        // schedule renders the row without a ripple.
        onClick = if (enabled) onClick else null,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        cornerShape = MaterialTheme.shapes.medium,
        pressedShape = MaterialTheme.shapes.medium,
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingContent = if (enabled) {
            {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            null
        },
    ) {
        ChangedScheduleTimeText(
            value = formattedTime,
            originalValue = formattedOriginalTime,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LockedScheduleTimeNoteRow(
    index: Int = 0,
    count: Int = 1,
) {
    val text = stringResource(R.string.group_locked_time_note)
    EditorSegmentedListItem(
        index = index,
        count = count,
        cornerShape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_edit_calendar),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
        },
        modifier = Modifier.padding(top = dimensionResource(R.dimen.list_segment_gap)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

internal fun parseScheduleInterval(value: String): Int {
    return value.toIntOrNull()?.takeIf { it > 0 } ?: 1
}

internal fun incrementScheduleInterval(value: String): String {
    return (parseScheduleInterval(value) + 1).toString()
}

internal fun decrementScheduleInterval(value: String): String {
    return maxOf(1, parseScheduleInterval(value) - 1).toString()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NotificationsCard(
    enabled: Boolean,
    toggleEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    index: Int? = null,
    count: Int? = null,
) {
    PreferenceSegmentedListItem(
        title = stringResource(R.string.group_notifications_reminder),
        supportingText = stringResource(R.string.group_notifications_summary),
        index = index,
        count = count,
        enabled = toggleEnabled,
        // Null onClick drops the row's ripple when the toggle itself isn't
        // tappable (e.g., viewing an archived group).
        onClick = if (toggleEnabled) {
            { onToggle(!enabled) }
        } else {
            null
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_notifications),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(if (toggleEnabled) 1f else 0.72f)
            )
        },
        trailingContent = {
            Checkbox(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = toggleEnabled
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeleteMedicationGroupRecordsCard(
    enabled: Boolean = true,
    onClick: () -> Unit,
    index: Int? = null,
    count: Int? = null,
) {
    DangerZoneListItem(
        label = stringResource(R.string.delete_group_related_records),
        enabled = enabled,
        onClick = onClick,
        iconPainter = painterResource(R.drawable.ic_delete_history),
        index = index,
        count = count,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArchiveMedicationGroupCard(
    enabled: Boolean = true,
    onClick: () -> Unit,
    index: Int? = null,
    count: Int? = null,
) {
    PreferenceSegmentedListItem(
        title = stringResource(R.string.archive_medication_group),
        index = index,
        count = count,
        enabled = enabled,
        onClick = onClick,
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_archive),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp).alpha(if (enabled) 1f else 0.72f),
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(if (enabled) 1f else 0.72f)
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DuplicateMedicationGroupCard(
    enabled: Boolean = true,
    onClick: () -> Unit,
    index: Int? = null,
    count: Int? = null,
) {
    PreferenceSegmentedListItem(
        title = stringResource(R.string.duplicate_medication_group),
        index = index,
        count = count,
        enabled = enabled,
        onClick = onClick,
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_content_copy),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeleteMedicationGroupCard(
    enabled: Boolean = true,
    onClick: () -> Unit,
    index: Int? = null,
    count: Int? = null,
) {
    DangerZoneListItem(
        label = stringResource(R.string.delete_medication_group),
        enabled = enabled,
        onClick = onClick,
        index = index,
        count = count
    )
}

@Preview(showBackground = true)
@Composable
private fun IntervalStepperCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            IntervalStepperCard(
                label = "Repeat every",
                value = 3,
                unit = "days",
                onDecreaseClick = {},
                onIncreaseClick = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeleteMedicationGroupCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            DeleteMedicationGroupCard(
                enabled = true,
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationsCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            NotificationsCard(
                enabled = true,
                toggleEnabled = true,
                onToggle = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditorFieldRowPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            EditorFieldRow(
                label = "Time",
                value = "08:00",
                icon = painterResource(R.drawable.ic_schedule),
                onClick = {},
            )
        }
    }
}
