package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * A 2×5 grid of palette swatches over [MedicationGroupColorKey.assignmentOrder].
 * [selectedColorKey] may be null (nothing selected); callers that always have a
 * colour pass a non-null value. Selection carves a small inner ring rather than a
 * check, so the swatch reads as a framed chip.
 */
@Composable
fun ColorPaletteSwatchGrid(
    selectedColorKey: MedicationGroupColorKey?,
    onColorSelected: (MedicationGroupColorKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = MedicationGroupColorKey.assignmentOrder
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ordered.chunked(5).forEach { rowKeys ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowKeys.forEach { key ->
                    val absoluteIndex = ordered.indexOf(key) + 1
                    val scheme = rememberMedicationGroupColorScheme(colorKey = key)
                    val isSelected = key == selectedColorKey
                    val swatchDescription = stringResource(
                        if (isSelected) {
                            R.string.group_color_picker_swatch_selected_content_description
                        } else {
                            R.string.group_color_picker_swatch_content_description
                        },
                        absoluteIndex,
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                            .clickable { onColorSelected(key) }
                            .semantics { contentDescription = swatchDescription },
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(2.5.dp)
                                    .border(
                                        width = 2.5.dp,
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
