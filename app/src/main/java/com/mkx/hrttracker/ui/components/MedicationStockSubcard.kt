package com.mkx.hrttracker.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.text.NumberFormat
import java.util.Locale

internal enum class MedicationStockSubcardTone {
    HEALTHY,
    WARNING,
    ERROR,
    NEUTRAL,
}

internal enum class MedicationStockSubcardRowKind(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val contentDescriptionRes: Int,
    @param:StringRes val labelRes: Int,
    val iconSize: Dp
) {
    STOCK_POOL(
        iconRes = R.drawable.ic_inventory_2,
        contentDescriptionRes = R.string.stock_subcard_cd_stock_pool,
        labelRes = R.string.stock_row_label_stock,
        iconSize = 20.dp
    ),
    OPEN_VIAL(
        iconRes = R.drawable.ic_humidity_mid,
        contentDescriptionRes = R.string.stock_subcard_cd_open_vial,
        labelRes = R.string.stock_row_label_current_vial,
        iconSize = 24.dp
    ),
    OPEN_CONTAINER(
        iconRes = R.drawable.ic_humidity_mid,
        contentDescriptionRes = R.string.stock_subcard_cd_open_container,
        labelRes = R.string.stock_row_label_current_container,
        iconSize = 24.dp
    ),
}

internal data class MedicationStockSubcardSealedSupplement(
    val countText: String,
    val chipText: String,
    @param:DrawableRes val iconRes: Int,
    val pluralQuantity: Int,
    @param:PluralsRes val unitPluralRes: Int,
)

internal data class MedicationStockSubcardText(
    @param:StringRes val resId: Int,
    val intArg: Int? = null,
)

internal data class MedicationStockSubcardRowModel(
    val kind: MedicationStockSubcardRowKind,
    val valueText: String,
    @param:StringRes val valueUnitRes: Int? = null,
    val progress: Float,
    val sealedSupplement: MedicationStockSubcardSealedSupplement? = null,
) {
    @get:DrawableRes
    val iconRes: Int get() = kind.iconRes

    @get:StringRes
    val contentDescriptionRes: Int get() = kind.contentDescriptionRes

    @get:StringRes
    val labelRes: Int get() = kind.labelRes

    val iconSize: Dp get() = kind.iconSize
}

internal data class MedicationStockSubcardModel(
    @param:StringRes val chipLabelRes: Int?,
    val tone: MedicationStockSubcardTone,
    val runwayText: MedicationStockSubcardText?,
    val rows: List<MedicationStockSubcardRowModel>,
) {
    val showsHeader: Boolean get() = chipLabelRes != null || runwayText != null
}

internal fun medicationStockSubcardModel(
    projection: MedicineStockProjection?,
): MedicationStockSubcardModel? {
    projection ?: return null
    val medicine = projection.medicine
    val stock = medicine.stock
    if (!stock.trackingEnabled || projection.state == MedicineStockState.UNTRACKED) return null
    if (medicine.preparation is MedicinePreparation.PatchOff) return null

    val rows = stockSubcardRows(projection)
    if (rows.isEmpty()) return null

    return MedicationStockSubcardModel(
        chipLabelRes = stockSubcardChipLabelRes(projection.state),
        tone = stockSubcardTone(projection.state),
        runwayText = stockSubcardRunwayText(projection.runway),
        rows = rows,
    )
}

@Composable
internal fun MedicationStockSubcard(
    projection: MedicineStockProjection,
    containerColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val model = remember(projection) { medicationStockSubcardModel(projection) } ?: return
    val chipColors = stockSubcardChipColors(model.tone)
    val iconContainerColor = stockSubcardIconContainerColor(
        containerColor = containerColor,
        surfaceContainer = MaterialTheme.colorScheme.surfaceContainer,
        surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh,
        surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    val progressIndicatorColors = stockSubcardProgressIndicatorColors(
        tone = model.tone,
        primary = MaterialTheme.colorScheme.primary,
        primaryContainer = MaterialTheme.colorScheme.primaryContainer,
        tertiary = MaterialTheme.colorScheme.tertiary,
        tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer,
        error = MaterialTheme.colorScheme.error,
        errorContainer = MaterialTheme.colorScheme.errorContainer,
        secondary = MaterialTheme.colorScheme.secondary,
        secondaryContainer = MaterialTheme.colorScheme.secondaryContainer,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (model.showsHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    model.chipLabelRes?.let { chipLabelRes ->
                        StockSubcardChip(
                            chipLabelRes = chipLabelRes,
                            colors = chipColors,
                        )
                    }
                    model.runwayText?.let { runwayText ->
                        if (model.chipLabelRes != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        val runwayTextString = runwayText.resolve()
                        Text(
                            text = runwayTextString,
                            modifier = Modifier.weight(1f).cjkTextOffset(runwayTextString),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            StockSubcardMetrics(
                rows = model.rows,
                iconContainerColor = iconContainerColor,
                progressIndicatorColors = progressIndicatorColors,
            )
        }
    }
}

@Composable
private fun StockSubcardChip(
    @StringRes chipLabelRes: Int,
    colors: StockSubcardChipColors,
) {
    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = CircleShape,
    ) {
        val chipLabelText = stringResource(chipLabelRes)
        Text(
            text = chipLabelText,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).cjkTextOffset(chipLabelText),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StockSubcardMetrics(
    rows: List<MedicationStockSubcardRowModel>,
    iconContainerColor: Color,
    progressIndicatorColors: StockSubcardProgressIndicatorColors,
) {
    rows.firstOrNull()?.let { row ->
        StockSubcardMetricCell(
            row = row,
            iconContainerColor = iconContainerColor,
            progressIndicatorColors = progressIndicatorColors,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StockSubcardMetricCell(
    row: MedicationStockSubcardRowModel,
    iconContainerColor: Color,
    progressIndicatorColors: StockSubcardProgressIndicatorColors,
    modifier: Modifier = Modifier,
) {
    val metricTextStyle = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    val metricLineHeight = with(density) {
        metricTextStyle.lineHeight.takeOrElse { metricTextStyle.fontSize }.toDp()
    }

    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            color = iconContainerColor,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.aspectRatio(1f).width(24.dp).fillMaxHeight()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(row.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(row.iconSize),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { row.progress },
                color = progressIndicatorColors.color,
                trackColor = progressIndicatorColors.trackColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(6.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val labelText = stringResource(row.labelRes)
                Text(
                    text = labelText,
                    modifier = Modifier.weight(1f).cjkTextOffset(labelText),
                    style = metricTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = 6.dp,
                        alignment = Alignment.End,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.sealedSupplement?.let { supplement ->
                        StockSubcardSealedChip(
                            supplement = supplement,
                            rowKind = row.kind,
                            lineHeight = metricLineHeight,
                        )
                    }
                    Text(
                        text = row.resolvedValueText(),
                        modifier = Modifier.weight(1f, fill = false),
                        style = metricTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationStockSubcardRowModel.resolvedValueText(): String {
    val unitRes = valueUnitRes ?: return valueText
    return stringResource(
        R.string.stock_row_count_with_unit,
        valueText,
        stringResource(unitRes),
    )
}

@Composable
private fun StockSubcardSealedChip(
    supplement: MedicationStockSubcardSealedSupplement,
    rowKind: MedicationStockSubcardRowKind,
    lineHeight: Dp,
) {
    val unitText = pluralStringResource(
        supplement.unitPluralRes,
        supplement.pluralQuantity,
    )
    val contentDescription = stringResource(
        R.string.stock_subcard_sealed_chip_content_description,
        supplement.chipText,
        unitText,
    )
    val chipHeight = lineHeight + 2.dp
    val iconSize = chipHeight - 6.dp

    Surface(
        modifier = Modifier
            .height(chipHeight)
            .widthIn(max = 72.dp)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
            },
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(supplement.iconRes),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
            Text(
                text = supplement.chipText,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MedicationStockSubcardText.resolve(): String {
    return if (intArg == null) {
        stringResource(resId)
    } else {
        stringResource(resId, intArg)
    }
}

internal data class StockSubcardChipColors(
    val container: Color,
    val content: Color,
)

internal data class StockSubcardProgressIndicatorColors(
    val color: Color,
    val trackColor: Color,
)

internal fun stockSubcardIconContainerColor(
    containerColor: Color,
    surfaceContainer: Color,
    surfaceContainerHigh: Color,
    surfaceContainerHighest: Color,
): Color {
    return when (containerColor) {
        surfaceContainer -> surfaceContainerHigh
        surfaceContainerHigh -> surfaceContainerHighest
        else -> surfaceContainerHighest
    }
}

internal fun stockSubcardProgressIndicatorColors(
    tone: MedicationStockSubcardTone,
    primary: Color,
    primaryContainer: Color,
    tertiary: Color,
    tertiaryContainer: Color,
    error: Color,
    errorContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
): StockSubcardProgressIndicatorColors {
    return when (tone) {
        MedicationStockSubcardTone.HEALTHY -> StockSubcardProgressIndicatorColors(
            color = primary,
            trackColor = primaryContainer,
        )
        MedicationStockSubcardTone.WARNING -> StockSubcardProgressIndicatorColors(
            color = tertiary,
            trackColor = tertiaryContainer,
        )
        MedicationStockSubcardTone.ERROR -> StockSubcardProgressIndicatorColors(
            color = error,
            trackColor = errorContainer,
        )
        MedicationStockSubcardTone.NEUTRAL -> StockSubcardProgressIndicatorColors(
            color = secondary,
            trackColor = secondaryContainer,
        )
    }
}

@Composable
private fun stockSubcardChipColors(
    tone: MedicationStockSubcardTone,
): StockSubcardChipColors {
    return when (tone) {
        MedicationStockSubcardTone.HEALTHY -> StockSubcardChipColors(
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        MedicationStockSubcardTone.WARNING -> StockSubcardChipColors(
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        MedicationStockSubcardTone.ERROR -> StockSubcardChipColors(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
        MedicationStockSubcardTone.NEUTRAL -> StockSubcardChipColors(
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun stockSubcardRows(
    projection: MedicineStockProjection,
): List<MedicationStockSubcardRowModel> {
    val stock = projection.medicine.stock
    return when (val preparation = projection.medicine.preparation) {
        is MedicinePreparation.InjectionMultiUseVial -> listOf(
            stock.openContainerAmount?.let { openAmount ->
                openContainerStockSubcardRow(
                    kind = MedicationStockSubcardRowKind.OPEN_VIAL,
                    openAmount = openAmount,
                    capacity = preparation.vialVolumeMl,
                    valueUnitRes = R.string.stock_unit_ml,
                    sealedCount = stock.unitsRemaining,
                    sealedUnitPluralRes = R.plurals.stock_subcard_unit_vials,
                )
            } ?: stockPoolSubcardRow(stock, preparation),
        )

        is MedicinePreparation.GelContainer -> listOf(
            stock.openContainerAmount?.let { openAmount ->
                openContainerStockSubcardRow(
                    kind = MedicationStockSubcardRowKind.OPEN_CONTAINER,
                    openAmount = openAmount,
                    capacity = preparation.containerWeightGrams,
                    valueUnitRes = R.string.stock_unit_g,
                    sealedCount = stock.unitsRemaining,
                    sealedUnitPluralRes = R.plurals.stock_subcard_unit_containers,
                )
            } ?: stockPoolSubcardRow(stock, preparation),
        )

        is MedicinePreparation.PatchOff -> emptyList()

        else -> listOf(stockPoolSubcardRow(stock, preparation))
    }
}

private fun openContainerStockSubcardRow(
    kind: MedicationStockSubcardRowKind,
    openAmount: Double,
    capacity: Double,
    @StringRes valueUnitRes: Int,
    sealedCount: Double?,
    @PluralsRes sealedUnitPluralRes: Int,
): MedicationStockSubcardRowModel {
    return MedicationStockSubcardRowModel(
        kind = kind,
        valueText = compactStockValueText(
            numerator = openAmount,
            denominator = capacity,
        ),
        valueUnitRes = valueUnitRes,
        progress = stockSubcardProgress(
            numerator = openAmount,
            denominator = capacity,
        ),
        sealedSupplement = stockSubcardSealedSupplement(
            sealedCount = sealedCount,
            unitPluralRes = sealedUnitPluralRes,
        ),
    )
}

private fun stockPoolSubcardRow(
    stock: MedicineStock,
    preparation: MedicinePreparation,
): MedicationStockSubcardRowModel {
    return MedicationStockSubcardRowModel(
        kind = MedicationStockSubcardRowKind.STOCK_POOL,
        valueText = compactStockValueText(
            numerator = stock.unitsRemaining,
            denominator = stock.unitsLastTotal,
        ),
        valueUnitRes = stockInventoryUnitRes(preparation),
        progress = stockSubcardProgress(
            numerator = stock.unitsRemaining,
            denominator = stock.unitsLastTotal,
        ),
    )
}

private fun stockSubcardSealedSupplement(
    sealedCount: Double?,
    @PluralsRes unitPluralRes: Int,
): MedicationStockSubcardSealedSupplement? {
    val resolvedCount = sealedCount?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    val countText = formatStockSubcardCount(resolvedCount)
    return MedicationStockSubcardSealedSupplement(
        countText = countText,
        chipText = "+$countText",
        iconRes = R.drawable.ic_inventory_2,
        pluralQuantity = stockSubcardPluralQuantity(resolvedCount),
        unitPluralRes = unitPluralRes,
    )
}

private fun stockSubcardPluralQuantity(value: Double): Int {
    return if (value == 1.0) 1 else 2
}

@StringRes
private fun stockSubcardChipLabelRes(state: MedicineStockState): Int? {
    return when (state) {
        MedicineStockState.HEALTHY -> R.string.stock_subcard_chip_in_stock
        MedicineStockState.USER_LOW -> R.string.stock_subcard_chip_low
        MedicineStockState.IMMINENT -> R.string.stock_subcard_chip_almost_out
        MedicineStockState.OUT -> R.string.stock_subcard_chip_out
        MedicineStockState.NO_RUNWAY -> null
        MedicineStockState.UNTRACKED -> null
    }
}

private fun stockSubcardTone(state: MedicineStockState): MedicationStockSubcardTone {
    return when (state) {
        MedicineStockState.HEALTHY -> MedicationStockSubcardTone.HEALTHY
        MedicineStockState.USER_LOW -> MedicationStockSubcardTone.WARNING
        MedicineStockState.IMMINENT,
        MedicineStockState.OUT,
        -> MedicationStockSubcardTone.ERROR
        MedicineStockState.NO_RUNWAY,
        MedicineStockState.UNTRACKED,
        -> MedicationStockSubcardTone.NEUTRAL
    }
}

private fun stockSubcardRunwayText(
    runway: RunwayProjection,
): MedicationStockSubcardText? {
    return when (runway) {
        is RunwayProjection.Days -> MedicationStockSubcardText(
            resId = R.string.stock_subcard_runway_days,
            intArg = runway.days,
        )
        RunwayProjection.BeyondHorizon -> MedicationStockSubcardText(
            resId = R.string.stock_subcard_runway_more_than_one_year,
        )
        RunwayProjection.NoSchedule -> null
    }
}

internal fun stockSubcardProgress(
    numerator: Double?,
    denominator: Double?,
): Float {
    val resolvedNumerator = numerator?.takeIf { it.isFinite() } ?: return 0f
    val resolvedDenominator = denominator?.takeIf { it.isFinite() } ?: return 0f
    if (resolvedDenominator <= 0.0) return 0f
    return (resolvedNumerator / resolvedDenominator).toFloat().coerceIn(0f, 1f)
}

internal fun compactStockValueText(
    numerator: Double?,
    denominator: Double?,
): String {
    val numeratorText = formatStockSubcardCount(numerator)
    if (denominator == null || !denominator.isFinite() || denominator <= 0.0) {
        return numeratorText
    }
    return "$numeratorText / ${formatStockSubcardCount(denominator)}"
}

private fun formatStockSubcardCount(value: Double?): String {
    val resolved = value?.takeIf { it.isFinite() } ?: return "-"
    return NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(resolved)
}

@Preview(
    name = "Medication Stock Subcard",
    showBackground = true,
    widthDp = 420
)
@Composable
private fun MedicationStockSubcardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MedicationStockSubcard(
                projection = stockSubcardPreviewProjection(
                    preparation = MedicinePreparation.GelContainer(
                        concentrationPercent = 0.06,
                        containerWeightGrams = 80.0,
                    ),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 3.0,
                        unitsLastTotal = 4.0,
                        openContainerAmount = 41.26,
                    ),
                    totalStockUnits = 281.26,
                    runwayDays = 111,
                    state = MedicineStockState.HEALTHY,
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            MedicationStockSubcard(
                projection = stockSubcardPreviewProjection(
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 84.0,
                        unitsLastTotal = 84.0,
                    ),
                    totalStockUnits = 84.0,
                    runwayDays = null,
                    state = MedicineStockState.NO_RUNWAY,
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
        }
    }
}

@Preview(
    name = "Medication Stock Subcard Alert States",
    showBackground = true,
    widthDp = 420
)
@Composable
private fun MedicationStockSubcardAlertStatesPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MedicationStockSubcard(
                projection = stockSubcardPreviewProjection(
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 8.0,
                        unitsLastTotal = 84.0,
                    ),
                    totalStockUnits = 8.0,
                    runwayDays = 21,
                    state = MedicineStockState.USER_LOW,
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            MedicationStockSubcard(
                projection = stockSubcardPreviewProjection(
                    preparation = MedicinePreparation.GelContainer(
                        concentrationPercent = 0.06,
                        containerWeightGrams = 80.0,
                    ),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 0.0,
                        unitsLastTotal = 2.0,
                        openContainerAmount = 4.0,
                    ),
                    totalStockUnits = 4.0,
                    runwayDays = 3,
                    state = MedicineStockState.IMMINENT,
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            MedicationStockSubcard(
                projection = stockSubcardPreviewProjection(
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 0.0,
                        unitsLastTotal = 84.0,
                    ),
                    totalStockUnits = 0.0,
                    runwayDays = 0,
                    state = MedicineStockState.OUT,
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
        }
    }
}

private fun stockSubcardPreviewProjection(
    preparation: MedicinePreparation,
    stock: MedicineStock,
    totalStockUnits: Double,
    runwayDays: Int?,
    state: MedicineStockState,
): MedicineStockProjection {
    val medicine = stockSubcardPreviewMedicine(preparation = preparation, stock = stock)
    return MedicineStockProjection(
        medicine = medicine,
        dosesPerDayMagnitude = 1.0,
        totalStockUnits = totalStockUnits,
        runway = if (runwayDays == null) {
            RunwayProjection.NoSchedule
        } else {
            RunwayProjection.Days(
                days = runwayDays,
                lastFulfillable = java.time.LocalDate.of(2026, 5, 27).plusDays(runwayDays.toLong()),
            )
        },
        intervalDays = null,
        maxPerAdministration = 1.0,
        state = state,
    )
}

private fun stockSubcardPreviewMedicine(
    preparation: MedicinePreparation,
    stock: MedicineStock,
): Medicine {
    val medicationKey = when (preparation) {
        is MedicinePreparation.GelContainer -> MedicationKey.ESTRADIOL_GEL
        else -> MedicationKey.ESTRADIOL
    }
    return Medicine(
        uuid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000101"),
        selection = MedicineSelection.Catalog(medicationKey),
        category = medicationKey.category,
        preparation = preparation,
        displayName = null,
        identityKey = MedicineIdentityKey.catalog(medicationKey, preparation),
        createdAt = java.time.Instant.EPOCH,
        updatedAt = java.time.Instant.EPOCH,
        archivedAt = null,
        stock = stock,
    )
}
