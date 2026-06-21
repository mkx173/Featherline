package com.mkx.hrttracker.ui.journal

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.PrideFlag
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme

private val SwatchTouchTargetSize = 48.dp
private val SwatchVisualSize = 44.dp
// Selection ring, scaled 1.5x off ColorPaletteSwatch's 2dp/2dp to suit the larger swatch.
private val SwatchSelectionRingWidth = 3.dp
private val SwatchSelectionRingInset = 3.dp
// A swatch reads as a circle when idle and morphs to a rounded square once picked. Corners are
// expressed as a percent so the same shape fits both the 48dp touch target and the 44dp visual.
private const val SwatchCircleCornerPercent = 50
private const val SwatchSelectedCornerPercent = 40

// The spring gives the circle->rounded-square morph a brief, lively settle on selection.
@Composable
private fun swatchShape(selected: Boolean): RoundedCornerShape {
    val cornerPercent by animateIntAsState(
        targetValue = if (selected) SwatchSelectedCornerPercent else SwatchCircleCornerPercent,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "swatchCornerPercent",
    )
    return RoundedCornerShape(cornerPercent)
}

@Composable
fun FlagSwatch(
    flag: PrideFlag,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Show the flag's real colours, and only the seeds that actually bloom: paletteSeeds drops the
    // pure neutrals the wash never renders (Trans -> blue + pink, Agender -> green + grey), in
    // their original hex so the chip reads as the flag rather than the normalised wash tones.
    val strips = remember(flag) {
        HeroBackgroundColors.paletteSeeds(flag.seeds).map { Color(it) }
    }
    val description = stringResource(prideFlagLabelRes(flag))
    val shape = swatchShape(selected)
    Box(
        modifier = modifier
            .size(SwatchTouchTargetSize)
            .clip(shape) // clip the tap ripple to the morphing swatch
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(SwatchVisualSize)
                .then(if (selected) Modifier.aspectRatio(1f) else Modifier)
                .clip(shape),
        ) {
            Row(Modifier.matchParentSize()) {
                strips.forEach { color ->
                    Box(Modifier.weight(1f).fillMaxHeight().background(color))
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(SwatchSelectionRingInset)
                        .border(
                            SwatchSelectionRingWidth,
                            MaterialTheme.colorScheme.surfaceContainer,
                            shape,
                        ),
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
    val fill = MaterialTheme.colorScheme.secondaryContainer
    val mark = MaterialTheme.colorScheme.onSecondaryContainer
    val ringColor = MaterialTheme.colorScheme.surfaceContainer
    val shape = swatchShape(selected)
    Box(
        modifier = modifier
            .size(SwatchTouchTargetSize)
            .clip(shape) // clip the tap ripple to the morphing swatch
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(SwatchVisualSize)
                .then(if (selected) Modifier.aspectRatio(1f) else Modifier)
                .clip(shape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_block),
                contentDescription = null,
                tint = mark,
                modifier = Modifier.size(24.dp),
            )
            if (selected) {
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(SwatchSelectionRingInset)
                        .border(SwatchSelectionRingWidth, ringColor, shape),
                )
            }
        }
    }
}

@Composable
fun DateColorSwatch(
    colorKey: MedicationGroupColorKey?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.journal_hero_background_date_color)
    val colorScheme = rememberMedicationGroupColorScheme(colorKey = colorKey)
    val fill = colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.surfaceContainer
    val shape = swatchShape(selected)
    Box(
        modifier = modifier
            .size(SwatchTouchTargetSize)
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(SwatchVisualSize)
                .then(if (selected) Modifier.aspectRatio(1f) else Modifier)
                .clip(shape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(SwatchSelectionRingInset)
                        .border(SwatchSelectionRingWidth, ringColor, shape),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroBackgroundDialog(
    current: HeroBackground,
    dateColorKey: MedicationGroupColorKey?,
    onConfirm: (HeroBackground) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var selected by remember(current) { mutableStateOf(current) }
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
                    selected = selected == HeroBackground.None,
                    onClick = { selected = HeroBackground.None },
                )
                DateColorSwatch(
                    colorKey = dateColorKey,
                    selected = selected == HeroBackground.DateColor,
                    onClick = { selected = HeroBackground.DateColor },
                )
                PrideFlag.entries.forEach { flag ->
                    val option = HeroBackground.Flag(flag)
                    FlagSwatch(
                        flag = flag,
                        selected = selected == option,
                        onClick = { selected = option },
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
