package com.mkx.hrttracker.ui.catalog.stock

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.components.HrtDropdownAnchor
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.segmentedListItemShape
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

@Composable
fun StockSection(
    projection: MedicineStockProjection,
    onAdjustClick: () -> Unit,
    onOptInClick: () -> Unit,
    onEditOpenContainer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!projection.medicine.stock.trackingEnabled) {
        OptInCard(onClick = onOptInClick, modifier = modifier)
        return
    }

    Column(modifier = modifier) {
        SectionHeader(
            projection = projection,
            onEditOpenContainer = onEditOpenContainer,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.list_segment_gap),
            ),
        ) {
            StockRows(
                projection = projection,
                onAdjustClick = onAdjustClick,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    projection: MedicineStockProjection,
    onEditOpenContainer: () -> Unit,
) {
    val showOverflow = projection.medicine.stock.openContainerAmount != null
    // Matches MedicineDetailScreen.SectionHeader / EditorSectionHeader: 6dp
    // bottom row padding + 4dp all-sides text padding for the same vertical
    // rhythm across detail-page sections.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.stock_section_title).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
            when (projection.state) {
                MedicineStockState.LOW -> StatusChip(
                    label = stringResource(R.string.stock_chip_low),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )

                MedicineStockState.OUT -> StatusChip(
                    label = stringResource(R.string.stock_chip_out),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )

                else -> Unit
            }
        }
        if (showOverflow) {
            HeaderOverflowMenu(
                preparation = projection.medicine.preparation,
                onEditOpenContainer = onEditOpenContainer,
            )
        }
    }
}

@Composable
private fun HeaderOverflowMenu(
    preparation: MedicinePreparation,
    onEditOpenContainer: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val editOpenLabel = stringResource(editActionRes(preparation))
    // Match the title text's vertical box (line-height + the 4.dp Text padding
    // applied above/below) so the overflow doesn't make the header taller than
    // it would be without one. Falls back to fontSize when lineHeight is
    // Unspecified, and scales with the user's font-size setting.
    val titleStyle = MaterialTheme.typography.titleSmall
    val density = LocalDensity.current
    val iconSize = with(density) {
        titleStyle.lineHeight.takeOrElse { titleStyle.fontSize }.toDp()
    }
    val buttonSize = iconSize + 8.dp
    Row {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
        ) {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.stock_section_more_options),
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HrtDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchor = HrtDropdownAnchor.EndAlignedBelow,
            items = listOf(
                HrtDropdownMenuItem(
                    text = editOpenLabel,
                    onClick = onEditOpenContainer,
                ),
            ),
        )
    }
}

@Composable
private fun StatusChip(
    label: String,
    container: Color,
    content: Color,
) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StockRows(
    projection: MedicineStockProjection,
    onAdjustClick: () -> Unit,
) {
    val preparation = projection.medicine.preparation
    val stock = projection.medicine.stock

    when (preparation) {
        is MedicinePreparation.InjectionMultiUseVial -> {
            val showOpenRow = stock.openContainerAmount != null
            val totalRows = if (showOpenRow) 3 else 2
            var index = 0
            if (showOpenRow) {
                StockRowCard(
                    iconRes = R.drawable.ic_humidity_mid,
                    label = stringResource(R.string.stock_row_label_current_vial),
                    trailingCount = stringResource(
                        R.string.stock_row_count_volume_ml,
                        formatCount(stock.openContainerAmount),
                        formatCount(preparation.vialVolumeMl),
                    ),
                    trailingState = projection.state,
                    index = index++,
                    count = totalRows,
                    progress = computeProgress(
                        numerator = stock.openContainerAmount,
                        denominator = preparation.vialVolumeMl,
                    ),
                )
            }
            StockRowCard(
                iconRes = R.drawable.ic_inventory_2,
                label = stringResource(R.string.stock_row_label_stock),
                trailingCount = formatCount(stock.unitsRemaining),
                // The sealed count is just an integer; its color stays neutral
                // even when overall stock is low or out, otherwise the user
                // reads a healthy "3" as if it were dangerous. The runway row
                // below carries the state coloring instead.
                trailingState = MedicineStockState.HEALTHY,
                index = index++,
                count = totalRows,
            )
            RunwayRowCard(
                projection = projection,
                onAdjustClick = onAdjustClick,
                index = index,
                count = totalRows,
            )
        }

        is MedicinePreparation.GelContainer -> {
            val showOpenRow = stock.openContainerAmount != null
            val totalRows = if (showOpenRow) 3 else 2
            var index = 0
            if (showOpenRow) {
                StockRowCard(
                    iconRes = R.drawable.ic_humidity_mid,
                    label = stringResource(R.string.stock_row_label_current_container),
                    trailingCount = stringResource(
                        R.string.stock_row_count_volume_g,
                        formatCount(stock.openContainerAmount),
                        formatCount(preparation.containerWeightGrams),
                    ),
                    trailingState = projection.state,
                    index = index++,
                    count = totalRows,
                    progress = computeProgress(
                        numerator = stock.openContainerAmount,
                        denominator = preparation.containerWeightGrams,
                    ),
                )
            }
            StockRowCard(
                iconRes = R.drawable.ic_inventory_2,
                label = stringResource(R.string.stock_row_label_stock),
                trailingCount = formatCount(stock.unitsRemaining),
                trailingState = MedicineStockState.HEALTHY,
                index = index++,
                count = totalRows,
            )
            RunwayRowCard(
                projection = projection,
                onAdjustClick = onAdjustClick,
                index = index,
                count = totalRows,
            )
        }

        else -> {
            StockRowCard(
                iconRes = poolIconRes(preparation),
                label = stringResource(R.string.stock_row_label_stock),
                trailingCount = poolCountText(stock.unitsRemaining, stock.unitsLastTotal),
                trailingState = projection.state,
                index = 0,
                count = 2,
                progress = computeProgress(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                ),
            )
            RunwayRowCard(
                projection = projection,
                onAdjustClick = onAdjustClick,
                index = 1,
                count = 2,
            )
        }
    }
}

@Composable
private fun StockRowCard(
    iconRes: Int,
    label: String,
    trailingCount: String,
    trailingState: MedicineStockState,
    index: Int,
    count: Int,
    progress: Float? = null,
) {
    val trailingColor = when (trailingState) {
        MedicineStockState.LOW -> MaterialTheme.colorScheme.tertiary
        MedicineStockState.OUT -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = segmentedListItemShape(index = index, count = count),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ConstraintLayout(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            val (iconRef, labelRef, countRef, progressRef) = createRefs()

            // Icon vertically centers against the label-and-gauge group so the
            // gauge sits below the count without dragging the icon down with it.
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .constrainAs(iconRef) {
                        start.linkTo(parent.start)
                        top.linkTo(labelRef.top)
                        bottom.linkTo(
                            if (progress != null) progressRef.bottom else labelRef.bottom,
                        )
                    },
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.constrainAs(labelRef) {
                    start.linkTo(iconRef.end, margin = 12.dp)
                    top.linkTo(parent.top)
                    end.linkTo(countRef.start, margin = 12.dp)
                    width = Dimension.fillToConstraints
                },
            )

            Text(
                text = trailingCount,
                style = MaterialTheme.typography.titleMedium,
                color = trailingColor,
                modifier = Modifier.constrainAs(countRef) {
                    end.linkTo(parent.end)
                    top.linkTo(labelRef.top)
                    bottom.linkTo(labelRef.bottom)
                },
            )

            if (progress != null) {
                FuelGauge(
                    progress = progress,
                    state = trailingState,
                    modifier = Modifier.constrainAs(progressRef) {
                        start.linkTo(labelRef.start)
                        end.linkTo(parent.end)
                        top.linkTo(labelRef.bottom, margin = 10.dp)
                        width = Dimension.fillToConstraints
                    },
                )
            }
        }
    }
}

@Composable
private fun FuelGauge(
    progress: Float,
    state: MedicineStockState,
    modifier: Modifier = Modifier,
) {
    val color = when (state) {
        MedicineStockState.LOW -> MaterialTheme.colorScheme.tertiary
        MedicineStockState.OUT -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        color = color,
        modifier = modifier.height(8.dp),
    )
}

@Composable
private fun RunwayRowCard(
    projection: MedicineStockProjection,
    onAdjustClick: () -> Unit,
    index: Int,
    count: Int,
) {
    val runwayDays = projection.runwayDays
    val isWarn = projection.state == MedicineStockState.LOW ||
        projection.state == MedicineStockState.OUT
    val titleColor = when {
        isWarn -> MaterialTheme.colorScheme.tertiary
        runwayDays == null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconColor = if (isWarn) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconRes = if (runwayDays == null) R.drawable.ic_info else R.drawable.ic_schedule
    val titleText = if (runwayDays == null) {
        stringResource(R.string.stock_runway_unknown_title)
    } else {
        stringResource(
            R.string.stock_runway_days_remaining,
            floor(runwayDays).toInt(),
        )
    }
    val subtitleText = if (runwayDays == null) {
        stringResource(R.string.stock_runway_unknown_body)
    } else {
        rateLabel(projection)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = segmentedListItemShape(index = index, count = count),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    fontWeight = FontWeight.Medium,
                )
                if (subtitleText != null) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onAdjustClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.stock_adjust_button))
            }
        }
    }
}

@Composable
private fun OptInCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.stock_optin_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.stock_optin_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onClick) {
                Text(stringResource(R.string.stock_optin_button))
            }
        }
    }
}

@Composable
private fun rateLabel(projection: MedicineStockProjection): String? {
    val dosesPerDay = projection.dosesPerDayMagnitude
    if (dosesPerDay <= 0.0) return null
    val unitRes = stockUnitRes(projection.medicine.preparation) ?: return null
    val unit = stringResource(unitRes)
    // Sub-once-a-day cadence reads better as a weekly rate: a multi-use vial
    // at 0.4 mL/wk is more legible than 0.06 mL/day.
    return if (dosesPerDay >= 0.5) {
        stringResource(R.string.stock_rate_per_day, formatRate(dosesPerDay), unit)
    } else {
        stringResource(R.string.stock_rate_per_week, formatRate(dosesPerDay * 7), unit)
    }
}

private fun stockUnitRes(preparation: MedicinePreparation): Int? = when (preparation) {
    is MedicinePreparation.Pill -> R.string.stock_unit_tablets
    is MedicinePreparation.Capsule -> R.string.stock_unit_capsules
    is MedicinePreparation.Patch -> R.string.stock_unit_patches
    is MedicinePreparation.GelSachet -> R.string.stock_unit_sachets
    is MedicinePreparation.InjectionSingleUseVial -> R.string.stock_unit_vials
    is MedicinePreparation.InjectionMultiUseVial -> R.string.stock_unit_ml
    is MedicinePreparation.GelContainer -> R.string.stock_unit_g
    is MedicinePreparation.PatchOff -> null
}

private fun formatRate(value: Double): String {
    // Treat anything within 0.05 of an integer as whole so "1.0 tablets/day"
    // reads as "1 tablets/day" — the trailing decimal is just float noise.
    return if (abs(value - value.toLong()) < 0.05) {
        value.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
}

private fun computeProgress(
    numerator: Double?,
    denominator: Double?,
): Float {
    val resolvedNumerator = numerator ?: return 0f
    val resolvedDenominator = denominator ?: return 0f
    if (resolvedDenominator <= 0.0) return 0f
    return (resolvedNumerator / resolvedDenominator).toFloat().coerceIn(0f, 1f)
}

private fun formatCount(value: Double?): String {
    val resolved = value ?: return "-"
    return if (resolved == resolved.toLong().toDouble()) {
        resolved.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", resolved)
    }
}

@Composable
private fun poolCountText(units: Double?, lastTotal: Double?): String {
    return if (lastTotal != null) {
        stringResource(
            R.string.stock_row_count_over_total,
            formatCount(units),
            formatCount(lastTotal),
        )
    } else {
        stringResource(R.string.stock_row_count_only, formatCount(units))
    }
}

private fun poolIconRes(preparation: MedicinePreparation): Int = when (preparation) {
    // All sealed/pool preparations read as "inventory" — boxes of tablets,
    // strips of patches, batches of sachets — so they share the inventory_2
    // glyph rather than each carrying a unique preparation icon.
    is MedicinePreparation.Pill,
    is MedicinePreparation.Capsule,
    is MedicinePreparation.Patch,
    is MedicinePreparation.GelSachet,
    is MedicinePreparation.InjectionSingleUseVial -> R.drawable.ic_inventory_2
    // Container preparations and PatchOff route through dedicated row paths
    // upstream; this fallback only fires if a new preparation type is added
    // without a matching pool icon mapping.
    else -> R.drawable.ic_inventory_2
}

// Multi-use vials read as "vial" everywhere; gel containers and any future
// container preparations read as "container". HeaderOverflowMenu only renders
// for container preparations, so non-container branches don't appear here.
private fun editActionRes(preparation: MedicinePreparation): Int = when (preparation) {
    is MedicinePreparation.InjectionMultiUseVial -> R.string.stock_current_edit_action_vial
    else -> R.string.stock_current_edit_action_container
}
