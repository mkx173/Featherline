package com.mkx.hrttracker.ui.calibration

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
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
                modifier = Modifier.fillMaxWidth()
            ) {
                CalibrationMetadataChip(
                    label = stringResource(R.string.settings_calibration_date_label).uppercase(),
                    value = dateLabel,
                    iconPainter = painterResource(R.drawable.ic_calendar_month),
                    onClick = onDateClick,
                    modifier = Modifier.weight(1f),
                )
                CalibrationMetadataChip(
                    label = stringResource(R.string.settings_calibration_time_label).uppercase(),
                    value = timeLabel,
                    iconPainter = painterResource(R.drawable.ic_schedule),
                    onClick = onTimeClick,
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
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(
                    R.string.settings_calibration_last_e2_elapsed,
                    calibrationElapsedDurationLabel(elapsedMillis)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(end = 2.dp).cjkTextOffset(
                    stringResource(
                        R.string.settings_calibration_last_e2_elapsed,
                        calibrationElapsedDurationLabel(elapsedMillis)
                    )
                )
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
                .bringWholeFieldIntoView()
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
    isError: Boolean = false,
    unit: BloodUnitKey,
    defaultUnit: BloodUnitKey,
    originalUnit: BloodUnitKey?,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Done,
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
        val rangeStatus = calibrationRangeStatus(
            analyteKey = analyteKey,
            valueText = valueText,
            unit = unit,
        )

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
                            fontWeight = FontWeight.Normal
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = valueText,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringWholeFieldIntoView()
                    .then(
                        if (focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }
                    ),
                isError = isError,
                label = {
                    Text(text = stringResource(R.string.settings_calibration_value_label))
                },
                supportingText = if (isError) {
                    {
                        Text(text = stringResource(R.string.settings_calibration_error_invalid_number))
                    }
                } else {
                    null
                },
                suffix = {
                    Text(text = calibrationUnitLabel(unit))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        if (nextFocusRequester != null) {
                            nextFocusRequester.requestFocus()
                        } else {
                            focusManager.clearFocus()
                        }
                    },
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                if (defaultUnitValueLabel != null) {
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
                                    painter = painterResource(R.drawable.ic_conversion_path),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Text(
                                text = defaultUnitValueLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = 4.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
                rangeStatus?.let { status ->
                    Spacer(modifier = Modifier.width(8.dp))
                    CalibrationRangeStatusChip(status = status)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                ConnectedButtonGroup(
                    options = allowedUnits,
                    selectedOption = unit,
                    optionLabel = { option -> calibrationUnitLabel(option) },
                    optionIcons = { option ->
                        if (option == originalUnit) {
                            listOf(Icons.Rounded.Edit)
                        } else {
                            emptyList()
                        }
                    },
                    onOptionSelected = { selectedUnit ->
                        onUnitChange(selectedUnit)
                    },
                    layout = ConnectedButtonGroupLayout.ROW,
                    applyCjkTextOffset = false
                )
            }
        }
    }
}

@Composable
internal fun CalibrationCustomAnalyteCard(
    abbreviation: String,
    name: String,
    unitLabel: String,
    valueText: String,
    isError: Boolean = false,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Done,
    onValueChange: (String) -> Unit,
    onRemoveClick: () -> Unit,
    index: Int = 0,
    count: Int = 1,
) {
    CalibrationEditorCard(
        index = index,
        count = count,
    ) {
        val focusManager = LocalFocusManager.current
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = abbreviation,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = valueText,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringWholeFieldIntoView()
                    .then(
                        if (focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }
                    ),
                isError = isError,
                label = {
                    Text(text = stringResource(R.string.settings_calibration_value_label))
                },
                supportingText = if (isError) {
                    {
                        Text(text = stringResource(R.string.settings_calibration_error_invalid_number))
                    }
                } else {
                    null
                },
                suffix = {
                    Text(text = unitLabel)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        if (nextFocusRequester != null) {
                            nextFocusRequester.requestFocus()
                        } else {
                            focusManager.clearFocus()
                        }
                    },
                    onDone = { focusManager.clearFocus() }
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                ConnectedButtonGroup(
                    modifier = Modifier.wrapContentWidth(),
                    options = listOf(unitLabel),
                    selectedOption = unitLabel,
                    optionLabel = { option -> option },
                    onOptionSelected = { },
                    layout = ConnectedButtonGroupLayout.ROW,
                )
            }
        }
    }
}

internal fun calibrationEditorAnalyteImeAction(index: Int, count: Int): ImeAction {
    return if (index >= count - 1) {
        ImeAction.Done
    } else {
        ImeAction.Next
    }
}

@Composable
private fun CalibrationRangeStatusChip(status: CalibrationRangeStatus) {
    val icon = when (status) {
        CalibrationRangeStatus.ABOVE -> painterResource(R.drawable.ic_expand_circle_up)
        CalibrationRangeStatus.BELOW -> painterResource(R.drawable.ic_expand_circle_down)
        CalibrationRangeStatus.IN_RANGE -> painterResource(R.drawable.ic_adjust)
    }
    val labelRes = when (status) {
        CalibrationRangeStatus.ABOVE -> R.string.settings_calibration_range_status_above
        CalibrationRangeStatus.BELOW -> R.string.settings_calibration_range_status_below
        CalibrationRangeStatus.IN_RANGE -> R.string.settings_calibration_range_status_in_range
    }
    val label = stringResource(labelRes)
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
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = 2.dp).cjkTextOffset(label)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalibrationMetadataChip(
    label: String,
    value: String,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 16.dp)
                .clip(MaterialTheme.shapes.small)
        ) {
            if (iconPainter != null) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.bringWholeFieldIntoView(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val responder = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect =
                Rect(Offset.Zero, size.toSize())

            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                // Let the ancestor scrollable handle the actual scroll using
                // the rect produced by calculateRectForParent (i.e. the whole
                // field), so the cursor's smaller request doesn't nudge the
                // parent past where the focus request already brought us.
            }
        }
    }
    onSizeChanged { size = it }.bringIntoViewResponder(responder)
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

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun CalibrationCustomAnalyteCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        CalibrationCustomAnalyteCard(
            abbreviation = "DHT",
            name = "DHT",
            unitLabel = "ng/dL",
            valueText = "18.4",
            onValueChange = { },
            onRemoveClick = { },
        )
    }
}
