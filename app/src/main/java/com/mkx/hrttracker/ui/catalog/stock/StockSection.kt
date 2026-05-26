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
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.components.HrtDropdownAnchor
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.segmentedListItemShape
import java.util.Locale

@Composable
fun StockSection(
    projection: MedicineStockProjection,
    onOptInClick: () -> Unit,
    onEditOpenContainer: () -> Unit,
    onDisableTracking: () -> Unit,
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
            onDisableTracking = onDisableTracking,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.list_segment_gap),
            ),
        ) {
            StockRows(projection = projection)
        }
    }
}

@Composable
private fun SectionHeader(
    projection: MedicineStockProjection,
    onEditOpenContainer: () -> Unit,
    onDisableTracking: () -> Unit,
) {
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
                MedicineStockState.USER_LOW,
                MedicineStockState.IMMINENT -> StatusChip(
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
        HeaderOverflowMenu(
            preparation = projection.medicine.preparation,
            // Hide the edit-container item until a vial/container is open;
            // before promotion there's nothing to edit. Disable tracking is
            // always available while StockSection is rendered.
            onEditOpenContainer = onEditOpenContainer
                .takeIf { projection.medicine.stock.openContainerAmount != null },
            onDisableTracking = onDisableTracking,
        )
    }
}

@Composable
private fun HeaderOverflowMenu(
    preparation: MedicinePreparation,
    onEditOpenContainer: (() -> Unit)?,
    onDisableTracking: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val editOpenLabel = stringResource(editActionRes(preparation))
    val disableLabel = stringResource(R.string.stock_disable_menu_action)
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
            items = buildList {
                if (onEditOpenContainer != null) {
                    add(
                        HrtDropdownMenuItem(
                            text = editOpenLabel,
                            onClick = onEditOpenContainer,
                        ),
                    )
                }
                add(
                    HrtDropdownMenuItem(
                        text = disableLabel,
                        onClick = onDisableTracking,
                    ),
                )
            },
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
private fun StockRows(projection: MedicineStockProjection) {
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
                progress = computeProgress(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                ),
            )
            RunwayRowCard(
                projection = projection,
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
                progress = computeProgress(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                ),
            )
            RunwayRowCard(
                projection = projection,
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
        MedicineStockState.USER_LOW,
        MedicineStockState.IMMINENT -> MaterialTheme.colorScheme.tertiary
        MedicineStockState.OUT -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = segmentedListItemShape(index = index, count = count),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = trailingCount,
                        style = MaterialTheme.typography.titleMedium,
                        color = trailingColor,
                    )
                }
                if (progress != null) {
                    Spacer(Modifier.height(8.dp))
                    FuelGauge(
                        progress = progress,
                        state = trailingState,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
        MedicineStockState.USER_LOW,
        MedicineStockState.IMMINENT -> MaterialTheme.colorScheme.tertiary
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
    index: Int,
    count: Int,
) {
    val runway = projection.runway
    val isWarn = projection.state == MedicineStockState.USER_LOW ||
        projection.state == MedicineStockState.IMMINENT ||
        projection.state == MedicineStockState.OUT
    val titleColor = when {
        isWarn -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconColor = if (isWarn) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconRes = when (runway) {
        is RunwayProjection.Days,
        RunwayProjection.BeyondHorizon -> R.drawable.ic_insights
        RunwayProjection.NoSchedule -> R.drawable.ic_help
    }
    val titleText = when (runway) {
        is RunwayProjection.Days -> stringResource(
            R.string.stock_runway_days_remaining,
            runway.days,
        )
        RunwayProjection.BeyondHorizon -> stringResource(R.string.stock_runway_beyond_horizon)
        RunwayProjection.NoSchedule -> stringResource(R.string.stock_runway_unknown_title)
    }
    val subtitleText = when (runway) {
        RunwayProjection.NoSchedule -> stringResource(R.string.stock_runway_unknown_body)
        is RunwayProjection.Days,
        RunwayProjection.BeyondHorizon -> rateLabel(projection)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = segmentedListItemShape(index = index, count = count),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
                )
                if (subtitleText != null) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

internal fun stockUnitRes(preparation: MedicinePreparation): Int? = when (preparation) {
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
    return trimTrailingZeros(String.format(Locale.getDefault(), "%.2f", value))
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
    return trimTrailingZeros(String.format(Locale.getDefault(), "%.2f", resolved))
}

private fun trimTrailingZeros(text: String): String {
    if (!text.contains('.')) return text
    val trimmed = text.trimEnd('0').trimEnd('.')
    return trimmed.ifEmpty { "0" }
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 420)
@Composable
private fun StockSectionTrackedPillPreview() {
    StockSectionPreviewContainer {
        StockSection(
            projection = previewProjection(
                medicine = previewMedicine(
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 18.0,
                        unitsLastTotal = 28.0,
                    ),
                ),
                dosesPerDayMagnitude = 1.0,
                totalStockUnits = 18.0,
                runwayDays = 18.0,
                state = MedicineStockState.HEALTHY,
            ),
            onOptInClick = {},
            onEditOpenContainer = {},
            onDisableTracking = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 420)
@Composable
private fun StockSectionMultiUseVialLowPreview() {
    val preparation = MedicinePreparation.InjectionMultiUseVial(
        concentrationMgPerMl = 10.0,
        vialVolumeMl = 5.0,
    )
    StockSectionPreviewContainer {
        StockSection(
            projection = previewProjection(
                medicine = previewMedicine(
                    preparation = preparation,
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 1.0,
                        unitsLastTotal = 4.0,
                        openContainerAmount = 1.5,
                    ),
                ),
                dosesPerDayMagnitude = 0.2,
                totalStockUnits = 6.5,
                runwayDays = 32.5,
                state = MedicineStockState.USER_LOW,
            ),
            onOptInClick = {},
            onEditOpenContainer = {},
            onDisableTracking = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 420)
@Composable
private fun StockSectionUntrackedPreview() {
    StockSectionPreviewContainer {
        StockSection(
            projection = previewProjection(
                medicine = previewMedicine(
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                    stock = MedicineStock(trackingEnabled = false),
                ),
                dosesPerDayMagnitude = 1.0,
                totalStockUnits = 0.0,
                runwayDays = null,
                state = MedicineStockState.UNTRACKED,
            ),
            onOptInClick = {},
            onEditOpenContainer = {},
            onDisableTracking = {},
        )
    }
}

@Composable
private fun StockSectionPreviewContainer(content: @Composable () -> Unit) {
    com.mkx.hrttracker.ui.theme.HrtTrackerTheme(dynamicColor = false) {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}
