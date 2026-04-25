package com.mkx.hrttracker.ui.settings

import android.graphics.drawable.Icon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

@Composable
internal fun CalibrationEditorCard(
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    content: @Composable () -> Unit,
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = {}
    ) {
        Box(
            modifier = modifier,
        ) {
            content()
        }
    }
}

@Composable
internal fun CalibrationDateTimeCard(
    dateLabel: String,
    timeLabel: String,
    timeSinceLastEstradiolDoseMillis: Long?,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    CalibrationEditorCard(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            ) {
                CalibrationMetadataChip(
                    label = stringResource(R.string.settings_calibration_date_label).uppercase(),
                    value = dateLabel,
                    icon = Icons.Rounded.CalendarMonth,
                    onClick = onDateClick,
                    modifier = Modifier.weight(1f),
                )
                CalibrationMetadataChip(
                    label = stringResource(R.string.settings_calibration_time_label).uppercase(),
                    value = timeLabel,
                    icon = Icons.Rounded.AccessTime,
                    onClick = onTimeClick,
                    modifier = Modifier.weight(1f),
                )
            }
            timeSinceLastEstradiolDoseMillis?.let { elapsedMillis ->
                CalibrationElapsedEstradiolDosePill(elapsedMillis = elapsedMillis)
            }
        }
    }
}

@Composable
internal fun CalibrationElapsedEstradiolDosePill(
    elapsedMillis: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_labs),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(
                    R.string.settings_calibration_last_e2_elapsed,
                    calibrationElapsedDurationLabel(elapsedMillis)
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
internal fun CalibrationNotesCard(
    notes: String,
    onNotesChange: (String) -> Unit,
    onNotesCommit: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var wasFocused by remember { mutableStateOf(false) }
    CalibrationEditorCard(
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        wasFocused = true
                    } else if (wasFocused) {
                        wasFocused = false
                        onNotesCommit()
                    }
                },
            label = {
                Text(text = stringResource(R.string.settings_calibration_notes_label))
            },
            minLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    wasFocused = false
                    onNotesCommit()
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            ),
        )
    }
}

@Composable
internal fun CalibrationAnalyteCard(
    analyteKey: BloodAnalyteKey,
    valueText: String,
    unit: BloodUnitKey,
    defaultUnit: BloodUnitKey,
    originalUnit: BloodUnitKey?,
    onValueChange: (String) -> Unit,
    onUnitChange: (BloodUnitKey) -> Unit,
    onRemoveClick: () -> Unit,
    index: Int = 0,
    count: Int = 1,
) {
    CalibrationEditorCard(
        index = index,
        count = count
    ) {
        var latchedValueText by remember(analyteKey) { mutableStateOf(valueText) }
        var latchedUnit by remember(analyteKey) { mutableStateOf(unit) }
        var editedSinceLatch by remember(analyteKey) { mutableStateOf(false) }
        val rangeStatus = calibrationRangeStatus(
            analyteKey = analyteKey,
            valueText = latchedValueText,
            unit = latchedUnit,
        )
        val isError = if (editedSinceLatch) {
            false
        } else {
            val trimmed = latchedValueText.trim()
            trimmed.isNotEmpty() && parseCalibrationNumericInput(trimmed) == null
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(calibrationAnalyteFullNameRes(analyteKey)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val abbreviation = calibrationAnalyteLabel(analyteKey)
                        val target = calibrationTargetLabel(analyteKey, unit)
                        Text(
                            text = if (target != null) "$abbreviation · $target" else abbreviation,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rangeStatus?.let { status ->
                        CalibrationRangeStatusChip(status = status)
                    }
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                    ) {
                        IconButton(
                            onClick = onRemoveClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.remove_time),
                            )
                        }
                    }
                }
            }

            var wasFocused by remember { mutableStateOf(false) }
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = valueText,
                onValueChange = { newValue ->
                    editedSinceLatch = true
                    onValueChange(newValue)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            wasFocused = true
                        } else if (wasFocused) {
                            latchedValueText = valueText
                            latchedUnit = unit
                            editedSinceLatch = false
                        }
                    },
                isError = isError,
                label = {
                    Text(text = stringResource(R.string.settings_calibration_value_label))
                },
                suffix = {
                    Text(text = calibrationUnitLabel(unit))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
            )

            val allowedUnits = remember(analyteKey) {
                calibrationAllowedUnitsFor(analyteKey)
            }
            val defaultUnitValueLabel = remember(valueText, analyteKey, unit, defaultUnit) {
                calibrationValueInPreferredUnitLabel(
                    analyteKey = analyteKey,
                    valueText = valueText,
                    unit = unit,
                    preferredUnit = defaultUnit,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (defaultUnitValueLabel != null) {
                    Text(
                        text = defaultUnitValueLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                ConnectedButtonGroup(
                    modifier = Modifier.wrapContentWidth(),
                    options = allowedUnits,
                    selectedOption = unit,
                    optionLabel = { option -> calibrationUnitLabel(option) },
                    optionIcon = { option ->
                        if (option == originalUnit) {
                            Icons.Rounded.Edit
                        } else {
                            null
                        }
                    },
                    onOptionSelected = onUnitChange,
                    layout = ConnectedButtonGroupLayout.ROW,
                )
            }
        }
    }
}

@Composable
private fun CalibrationRangeStatusChip(status: CalibrationRangeStatus) {
    val icon = when (status) {
        CalibrationRangeStatus.ABOVE -> Icons.Rounded.ArrowDropUp
        CalibrationRangeStatus.BELOW -> Icons.Rounded.ArrowDropDown
        CalibrationRangeStatus.IN_RANGE -> Icons.Rounded.Circle
    }
    val labelRes = when (status) {
        CalibrationRangeStatus.ABOVE -> R.string.settings_calibration_range_status_above
        CalibrationRangeStatus.BELOW -> R.string.settings_calibration_range_status_below
        CalibrationRangeStatus.IN_RANGE -> R.string.settings_calibration_range_status_in_range
    }
    val iconSize = if (status == CalibrationRangeStatus.IN_RANGE) 8.dp else 18.dp
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalibrationMetadataChip(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        overlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
        },
        onClick = onClick,
        modifier = modifier,
        shapes = ListItemDefaults.shapes(
            shape = MaterialTheme.shapes.medium,
            pressedShape = MaterialTheme.shapes.medium
        )
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun CalibrationDateTimeCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        CalibrationDateTimeCard(
            dateLabel = "Apr 24, 2026",
            timeLabel = "9:30 AM",
            timeSinceLastEstradiolDoseMillis = 34_200_000L,
            onDateClick = { },
            onTimeClick = { },
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun CalibrationNotesCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        CalibrationNotesCard(
            notes = "Trough draw before morning dose.",
            onNotesChange = { },
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun CalibrationAnalyteCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        CalibrationAnalyteCard(
            analyteKey = BloodAnalyteKey.T,
            valueText = "1.1",
            unit = BloodUnitKey.NMOL_L,
            defaultUnit = BloodUnitKey.NG_DL,
            originalUnit = BloodUnitKey.NG_DL,
            onValueChange = { },
            onUnitChange = { },
            onRemoveClick = { },
        )
    }
}
