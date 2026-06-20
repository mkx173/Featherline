package com.mkx.hrttracker.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.PrideFlag
import com.mkx.hrttracker.ui.components.HazeAlertDialog

private val SwatchTouchTargetSize = 48.dp
private val SwatchVisualSize = 44.dp

@Composable
fun FlagSwatch(
    flag: PrideFlag,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val strips = remember(flag, isDark) {
        HeroBackgroundColors.swatchColors(flag.seeds, isDark).map { Color(it) }
    }
    val description = stringResource(prideFlagLabelRes(flag))
    Box(
        modifier = modifier
            .size(SwatchTouchTargetSize)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .size(SwatchVisualSize)
                .align(Alignment.Center)
                .clip(CircleShape),
        ) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                strips.forEach { color ->
                    Box(Modifier.weight(1f).fillMaxHeight().background(color))
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(2.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}

@Composable
fun NoneSwatch(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.journal_hero_background_none)
    val outline = MaterialTheme.colorScheme.outline
    val ringColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(SwatchTouchTargetSize)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .size(SwatchVisualSize)
                .align(Alignment.Center)
                .clip(CircleShape)
                .border(2.dp, outline, CircleShape)
                .drawBehind {
                    val inset = size.minDimension * 0.22f
                    drawLine(
                        color = outline,
                        start = Offset(inset, size.height - inset),
                        end = Offset(size.width - inset, inset),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                },
        )
        if (selected) {
            Box(
                Modifier
                    .size(SwatchVisualSize)
                    .align(Alignment.Center)
                    .padding(2.dp)
                    .border(2.dp, ringColor, CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroBackgroundDialog(
    current: PrideFlag?,
    onConfirm: (PrideFlag?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var selected by remember { mutableStateOf(current) }
    HazeAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.journal_hero_background_title)) },
        text = {
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NoneSwatch(
                    selected = selected == null,
                    onClick = { selected = null },
                )
                PrideFlag.entries.forEach { flag ->
                    FlagSwatch(
                        flag = flag,
                        selected = selected == flag,
                        onClick = { selected = flag },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.journal_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
