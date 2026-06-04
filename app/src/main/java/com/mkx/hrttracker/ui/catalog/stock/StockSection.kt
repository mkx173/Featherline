package com.mkx.hrttracker.ui.catalog.stock

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.HrtSectionHeader
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.StockStatusIndicator
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.segmentedListItemShape
import com.mkx.hrttracker.ui.components.stockCountPluralQuantity
import com.mkx.hrttracker.ui.components.stockInventoryUnitRes
import com.mkx.hrttracker.ui.components.stockUnitNounPluralForUnitRes
import com.mkx.hrttracker.ui.components.stockRateUnitRes
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
        HrtSectionHeader(
            text = stringResource(R.string.stock_section_title),
            trailing = {
                HeaderOverflowMenu(
                    preparation = projection.medicine.preparation,
                    // Hide the edit-container item until a vial/container is open;
                    // before promotion there's nothing to edit. Disable tracking is
                    // always available while StockSection is rendered.
                    onEditOpenContainer = onEditOpenContainer
                        .takeIf { projection.medicine.stock.openContainerAmount != null },
                    onDisableTracking = onDisableTracking,
                )
            },
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
private fun HeaderOverflowMenu(
    preparation: MedicinePreparation,
    onEditOpenContainer: (() -> Unit)?,
    onDisableTracking: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val editOpenLabel = stringResource(editActionRes(preparation))
    val disableLabel = stringResource(R.string.stock_disable_menu_action)
    // Size the overflow affordance to the section title's line height so it never
    // exceeds the title and therefore never makes HrtSectionHeader taller than a
    // text-only section header (HrtSectionHeader pads top/bottom around its
    // tallest child, so a larger button would add header height). Falls back to
    // fontSize when lineHeight is Unspecified, and scales with the user's
    // font-size setting. Min interactive size is suppressed below so the button
    // can match the title rather than the 48dp touch-target default.
    val titleStyle = MaterialTheme.typography.titleSmall
    val density = LocalDensity.current
    val iconSize = with(density) {
        titleStyle.lineHeight.takeOrElse { titleStyle.fontSize }.toDp()
    }
    val buttonSize = iconSize
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
                    iconRes = R.drawable.ic_humidity_mid_big,
                    label = stringResource(R.string.stock_row_label_current_vial),
                    trailingCount = stockSectionCountText(
                        numerator = stock.openContainerAmount,
                        denominator = preparation.vialVolumeMl,
                        unitRes = R.string.stock_unit_ml,
                    ),
                    index = index++,
                    count = totalRows,
                    progress = computeProgress(
                        numerator = stock.openContainerAmount,
                        denominator = preparation.vialVolumeMl,
                    ),
                    progressState = projection.state,
                )
            }
            StockRowCard(
                iconRes = R.drawable.ic_inventory_2,
                label = stringResource(R.string.stock_row_label_stock),
                trailingCount = stockSectionCountText(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                    unitRes = R.string.stock_unit_vials,
                ),
                // The sealed count is just an integer; its color stays neutral
                // even when overall stock is low or out, otherwise the user
                // reads a healthy "3" as if it were dangerous. The status chip
                // and stock gauges carry the state color instead.
                index = index++,
                count = totalRows,
                progress = computeProgress(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                ),
                progressState = projection.state,
                iconSize = 22.dp
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
                    iconRes = R.drawable.ic_humidity_mid_big,
                    label = stringResource(R.string.stock_row_label_current_container),
                    trailingCount = stockSectionCountText(
                        numerator = stock.openContainerAmount,
                        denominator = preparation.containerWeightGrams,
                        unitRes = R.string.stock_unit_g,
                    ),
                    index = index++,
                    count = totalRows,
                    progress = computeProgress(
                        numerator = stock.openContainerAmount,
                        denominator = preparation.containerWeightGrams,
                    ),
                    progressState = projection.state,
                )
            }
            StockRowCard(
                iconRes = R.drawable.ic_inventory_2,
                label = stringResource(R.string.stock_row_label_stock),
                trailingCount = stockSectionCountText(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                    unitRes = R.string.stock_unit_containers,
                ),
                index = index++,
                count = totalRows,
                progress = computeProgress(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                ),
                progressState = projection.state,
                iconSize = 22.dp
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
                trailingCount = stockSectionCountText(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                    unitRes = stockInventoryUnitRes(preparation),
                ),
                index = 0,
                count = 2,
                progress = computeProgress(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                ),
                progressState = projection.state,
                iconSize = 22.dp
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
    trailingCount: StockSectionCountText,
    index: Int,
    count: Int,
    progress: Float? = null,
    progressState: MedicineStockState = MedicineStockState.HEALTHY,
    iconSize: Dp = 24.dp
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = segmentedListItemShape(index = index, count = count),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(iconSize),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).alignByBaseline().cjkTextOffset(label),
                    )
                    val trailingCountText = trailingCount.resolve()
                    Text(
                        text = trailingCountText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.alignByBaseline().cjkTextOffset(trailingCountText)
                    )
                }
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    FuelGauge(
                        progress = progress,
                        state = progressState,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    )
                }
            }
        }
    }
}

internal data class StockSectionCountText(
    val numeratorText: String,
    val denominatorText: String?,
    @param:StringRes val unitRes: Int? = null,
    val pluralCount: Double? = null,
) {
    val valueText: String
        get() = if (denominatorText == null) {
            numeratorText
        } else {
            "$numeratorText / $denominatorText"
        }
}

@Composable
private fun StockSectionCountText.resolve(): String {
    val countText = if (denominatorText == null) {
        stringResource(R.string.stock_row_count_only, numeratorText)
    } else {
        stringResource(R.string.stock_row_count_over_total, numeratorText, denominatorText)
    }
    val unitRes = unitRes ?: return countText
    val pluralRes = stockUnitNounPluralForUnitRes(unitRes)
    val unit = if (pluralRes != null && pluralCount != null) {
        pluralStringResource(pluralRes, stockCountPluralQuantity(pluralCount))
    } else {
        stringResource(unitRes)
    }
    return stringResource(
        R.string.stock_row_count_with_unit,
        countText,
        unit,
    )
}

@Composable
private fun FuelGauge(
    progress: Float,
    state: MedicineStockState,
    modifier: Modifier = Modifier,
) {
    val colors = stockSectionProgressIndicatorColors(
        state = state,
        primary = MaterialTheme.colorScheme.primary,
        tertiary = MaterialTheme.colorScheme.tertiary,
        error = MaterialTheme.colorScheme.error,
        secondary = MaterialTheme.colorScheme.secondary,
        secondaryContainer = MaterialTheme.colorScheme.secondaryContainer,
    )
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        color = colors.color,
        trackColor = colors.trackColor,
        modifier = modifier.height(8.dp),
    )
}

internal data class StockSectionProgressIndicatorColors(
    val color: Color,
    val trackColor: Color,
)

internal fun stockSectionProgressIndicatorColors(
    state: MedicineStockState,
    primary: Color,
    tertiary: Color,
    error: Color,
    secondary: Color,
    secondaryContainer: Color,
): StockSectionProgressIndicatorColors {
    return when (state) {
        MedicineStockState.USER_LOW -> StockSectionProgressIndicatorColors(
            color = tertiary,
            trackColor = secondaryContainer,
        )
        MedicineStockState.IMMINENT,
        MedicineStockState.OUT -> StockSectionProgressIndicatorColors(
            color = error,
            trackColor = secondaryContainer,
        )
        MedicineStockState.NO_RUNWAY,
        MedicineStockState.UNTRACKED -> StockSectionProgressIndicatorColors(
            color = secondary,
            trackColor = secondaryContainer,
        )
        MedicineStockState.HEALTHY -> StockSectionProgressIndicatorColors(
            color = primary,
            trackColor = secondaryContainer,
        )
    }
}

internal fun stockSectionShowsStatusIndicator(state: MedicineStockState): Boolean {
    return when (state) {
        MedicineStockState.HEALTHY,
        MedicineStockState.UNTRACKED -> false
        MedicineStockState.USER_LOW,
        MedicineStockState.IMMINENT,
        MedicineStockState.OUT,
        MedicineStockState.NO_RUNWAY -> true
    }
}

@Composable
private fun RunwayRowCard(
    projection: MedicineStockProjection,
    index: Int,
    count: Int,
) {
    val runway = projection.runway
    val iconRes = when (runway) {
        is RunwayProjection.Days,
        RunwayProjection.BeyondHorizon -> R.drawable.ic_insights
        RunwayProjection.NoSchedule -> R.drawable.ic_help
    }
    val titleText = when (runway) {
        is RunwayProjection.Days -> pluralStringResource(
            R.plurals.stock_runway_days_remaining,
            runway.days,
            runway.days,
        )
        RunwayProjection.BeyondHorizon -> stringResource(R.string.stock_runway_more_than_one_year)
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = titleText,
                    modifier = Modifier.cjkTextOffset(titleText),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitleText != null) {
                    Text(
                        text = subtitleText,
                        modifier = Modifier.cjkTextOffset(subtitleText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (stockSectionShowsStatusIndicator(projection.state)) {
                Spacer(Modifier.width(12.dp))
                StockStatusIndicator(
                    projection = projection,
                )
            }
        }
    }
}

@Composable
private fun OptInCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HrtSection(
        title = stringResource(R.string.stock_section_title),
        modifier = modifier,
    ) {
        item {
            PreferenceSegmentedListItem(
                title = stringResource(R.string.stock_optin_title),
                onClick = onClick,
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_inventory),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

@Composable
private fun rateLabel(projection: MedicineStockProjection): String? {
    val dosesPerDay = projection.dosesPerDayMagnitude
    if (dosesPerDay <= 0.0) return null
    val unitRes = stockRateUnitRes(projection.medicine.preparation) ?: return null
    // Sub-once-a-day cadence reads better as a weekly rate: a multi-use vial
    // at 0.4 mL/wk is more legible than 0.06 mL/day.
    val perDay = dosesPerDay >= 0.5
    val rateValue = if (perDay) dosesPerDay else dosesPerDay * 7
    // Agree the unit noun with the displayed rate (e.g. "1 tablet / day", not
    // "1 tablets / day"). mL/g have no plural form and fall back to the label.
    val pluralRes = stockUnitNounPluralForUnitRes(unitRes)
    val unit = if (pluralRes != null) {
        pluralStringResource(pluralRes, stockCountPluralQuantity(rateValue))
    } else {
        stringResource(unitRes)
    }
    return stringResource(
        if (perDay) R.string.stock_rate_per_day else R.string.stock_rate_per_week,
        formatRate(rateValue),
        unit,
    )
}

internal fun stockUnitRes(preparation: MedicinePreparation): Int? = stockRateUnitRes(preparation)

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

internal fun stockSectionCountText(
    numerator: Double?,
    denominator: Double?,
    @StringRes unitRes: Int?,
): StockSectionCountText {
    val denominatorText = denominator?.let(::formatCount)
    return StockSectionCountText(
        numeratorText = formatCount(numerator),
        denominatorText = denominatorText,
        unitRes = unitRes,
        pluralCount = denominator ?: numerator,
    )
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
