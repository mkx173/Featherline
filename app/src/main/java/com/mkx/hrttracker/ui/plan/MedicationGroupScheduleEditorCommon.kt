package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowRightAlt
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
        onClick = { },
        enabled = true,
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
        onClick = if (fieldEnabled) onClick else {
            {}
        },
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
                style = MaterialTheme.typography.labelSmall,
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
                imageVector = Icons.AutoMirrored.Rounded.ArrowRightAlt,
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
    index: Int = 0,
    count: Int = 1,
) {
    PreferenceSegmentedListItem(
        title = stringResource(R.string.group_notifications_reminder),
        supportingText = stringResource(R.string.group_notifications_summary),
        index = index,
        count = count,
        modifier = Modifier.alpha(if (toggleEnabled) 1f else 0.72f),
        enabled = toggleEnabled,
        onClick = { onToggle(!enabled) },
        leadingContent = {
            Icon(
                painter = painterResource(
                    if (enabled) {
                        R.drawable.ic_notifications
                    } else {
                        R.drawable.ic_notifications_off
                    }
                ),
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
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
    enabled: Boolean,
    onClick: () -> Unit,
    index: Int = 0,
    count: Int = 2,
) {
    DangerZoneListItem(
        label = stringResource(R.string.delete_group_related_records),
        enabled = enabled,
        onClick = onClick,
        iconPainter = painterResource(R.drawable.ic_delete_history),
        index = index,
        count = count,
        supportText = if (!enabled) stringResource(R.string.delete_group_related_records_disabled) else null
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArchiveMedicationGroupCard(
    enabled: Boolean,
    onClick: () -> Unit,
    index: Int = 0,
    count: Int = 1,
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
internal fun DuplicateMedicationGroupCard(
    enabled: Boolean,
    onClick: () -> Unit,
    index: Int = 0,
    count: Int = 1,
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
    enabled: Boolean,
    onClick: () -> Unit,
    index: Int = 1,
    count: Int = 2,
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
                icon = rememberVectorPainter(Icons.Rounded.AccessTime),
                onClick = {},
            )
        }
    }
}
