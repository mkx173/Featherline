package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme

/**
 * A single circular palette swatch for [colorKey]. Selection carves a small inner ring
 * rather than a check, so the swatch reads as a framed chip. The accessibility label
 * uses the colour's 1-based position in [MedicationGroupColorKey.assignmentOrder], so
 * every container (grid, scroll row) exposes the same descriptions.
 */
@Composable
fun ColorPaletteSwatch(
    colorKey: MedicationGroupColorKey,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val absoluteIndex = MedicationGroupColorKey.assignmentOrder.indexOf(colorKey) + 1
    val scheme = rememberMedicationGroupColorScheme(colorKey = colorKey)
    val description = stringResource(
        if (selected) {
            R.string.group_color_picker_swatch_selected_content_description
        } else {
            R.string.group_color_picker_swatch_content_description
        },
        absoluteIndex,
    )
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(scheme.primary)
            .clickable { onClick() }
            .semantics { contentDescription = description },
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/**
 * A 2×5 grid of palette swatches over [MedicationGroupColorKey.assignmentOrder].
 * [selectedColorKey] may be null (nothing selected); callers that always have a
 * colour pass a non-null value.
 *
 * When [fillWidth] is true the rows stretch to the available width and distribute
 * the swatches evenly (for a full-width row beside other content); otherwise they
 * wrap their content with a fixed gap (the default, e.g. inside a tooltip).
 */
@Composable
fun ColorPaletteSwatchGrid(
    selectedColorKey: MedicationGroupColorKey?,
    onColorSelected: (MedicationGroupColorKey) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    val ordered = MedicationGroupColorKey.assignmentOrder
    val rowArrangement = if (fillWidth) {
        Arrangement.SpaceBetween
    } else {
        Arrangement.spacedBy(10.dp)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ordered.chunked(5).forEach { rowKeys ->
            Row(
                modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
                horizontalArrangement = rowArrangement,
            ) {
                rowKeys.forEach { key ->
                    ColorPaletteSwatch(
                        colorKey = key,
                        selected = key == selectedColorKey,
                        onClick = { onColorSelected(key) },
                    )
                }
            }
        }
    }
}
