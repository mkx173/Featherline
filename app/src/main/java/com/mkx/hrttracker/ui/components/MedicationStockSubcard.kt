package com.mkx.hrttracker.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
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
) {
    STOCK_POOL(
        iconRes = R.drawable.ic_inventory_2,
        contentDescriptionRes = R.string.stock_subcard_cd_stock_pool,
    ),
    OPEN_VIAL(
        iconRes = R.drawable.ic_humidity_mid,
        contentDescriptionRes = R.string.stock_subcard_cd_open_vial,
    ),
    OPEN_CONTAINER(
        iconRes = R.drawable.ic_humidity_mid,
        contentDescriptionRes = R.string.stock_subcard_cd_open_container,
    ),
}

internal data class MedicationStockSubcardSealedSupplement(
    val countText: String,
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
    val progress: Float,
    val sealedSupplement: MedicationStockSubcardSealedSupplement? = null,
) {
    @get:DrawableRes
    val iconRes: Int get() = kind.iconRes

    @get:StringRes
    val contentDescriptionRes: Int get() = kind.contentDescriptionRes
}

internal data class MedicationStockSubcardModel(
    @param:StringRes val chipLabelRes: Int,
    val tone: MedicationStockSubcardTone,
    val runwayText: MedicationStockSubcardText,
    val rows: List<MedicationStockSubcardRowModel>,
)

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

internal const val MedicationStockSubcardTestTag = "medication-stock-subcard"
internal const val MedicationStockSubcardRowTestTagPrefix = "medication-stock-subcard-row"

@Composable
internal fun MedicationStockSubcard(
    projection: MedicineStockProjection,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color? = null,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val model = remember(projection) { medicationStockSubcardModel(projection) } ?: return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MedicationStockSubcardTestTag),
        color = containerColor,
        shape = shape,
        border = borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StockSubcardChip(model = model)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = model.runwayText.resolve(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StockSubcardMetrics(rows = model.rows)
        }
    }
}

@Composable
private fun StockSubcardChip(model: MedicationStockSubcardModel) {
    val colors = stockSubcardChipColors(model.tone)
    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(model.chipLabelRes),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StockSubcardMetrics(rows: List<MedicationStockSubcardRowModel>) {
    rows.firstOrNull()?.let { row ->
        StockSubcardMetricCell(
            row = row,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StockSubcardMetricCell(
    row: MedicationStockSubcardRowModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag("$MedicationStockSubcardRowTestTagPrefix-${row.kind.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(22.dp),
        ) {
            Icon(
                painter = painterResource(row.iconRes),
                contentDescription = stringResource(row.contentDescriptionRes),
                modifier = Modifier.padding(4.dp),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { row.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stockSubcardRowValueText(row),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun stockSubcardRowValueText(
    row: MedicationStockSubcardRowModel,
): String {
    val supplement = row.sealedSupplement ?: return row.valueText
    return stringResource(
        R.string.stock_subcard_value_with_sealed,
        row.valueText,
        supplement.countText,
        pluralStringResource(
            supplement.unitPluralRes,
            supplement.pluralQuantity,
        ),
    )
}

@Composable
private fun MedicationStockSubcardText.resolve(): String {
    return if (intArg == null) {
        stringResource(resId)
    } else {
        stringResource(resId, intArg)
    }
}

private data class StockSubcardChipColors(
    val container: Color,
    val content: Color,
)

@Composable
private fun stockSubcardChipColors(
    tone: MedicationStockSubcardTone,
): StockSubcardChipColors {
    return when (tone) {
        MedicationStockSubcardTone.HEALTHY -> StockSubcardChipColors(
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
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
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    capacitySuffix = " mL",
                    sealedCount = stock.unitsRemaining,
                    sealedUnitPluralRes = R.plurals.stock_subcard_unit_vials,
                )
            } ?: stockPoolSubcardRow(stock),
        )

        is MedicinePreparation.GelContainer -> listOf(
            stock.openContainerAmount?.let { openAmount ->
                openContainerStockSubcardRow(
                    kind = MedicationStockSubcardRowKind.OPEN_CONTAINER,
                    openAmount = openAmount,
                    capacity = preparation.containerWeightGrams,
                    capacitySuffix = " g",
                    sealedCount = stock.unitsRemaining,
                    sealedUnitPluralRes = R.plurals.stock_subcard_unit_containers,
                )
            } ?: stockPoolSubcardRow(stock),
        )

        is MedicinePreparation.PatchOff -> emptyList()

        else -> listOf(stockPoolSubcardRow(stock))
    }
}

private fun openContainerStockSubcardRow(
    kind: MedicationStockSubcardRowKind,
    openAmount: Double,
    capacity: Double,
    capacitySuffix: String,
    sealedCount: Double?,
    @PluralsRes sealedUnitPluralRes: Int,
): MedicationStockSubcardRowModel {
    return MedicationStockSubcardRowModel(
        kind = kind,
        valueText = compactStockValueText(
            numerator = openAmount,
            denominator = capacity,
            suffix = capacitySuffix,
        ),
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
): MedicationStockSubcardRowModel {
    return MedicationStockSubcardRowModel(
        kind = MedicationStockSubcardRowKind.STOCK_POOL,
        valueText = compactStockValueText(
            numerator = stock.unitsRemaining,
            denominator = stock.unitsLastTotal,
        ),
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
    return MedicationStockSubcardSealedSupplement(
        countText = formatStockSubcardCount(resolvedCount),
        pluralQuantity = stockSubcardPluralQuantity(resolvedCount),
        unitPluralRes = unitPluralRes,
    )
}

private fun stockSubcardPluralQuantity(value: Double): Int {
    return if (value == 1.0) 1 else 2
}

@StringRes
private fun stockSubcardChipLabelRes(state: MedicineStockState): Int {
    return when (state) {
        MedicineStockState.HEALTHY -> R.string.stock_subcard_chip_in_stock
        MedicineStockState.USER_LOW -> R.string.stock_subcard_chip_low
        MedicineStockState.IMMINENT -> R.string.stock_subcard_chip_almost_out
        MedicineStockState.OUT -> R.string.stock_subcard_chip_out
        MedicineStockState.NO_RUNWAY -> R.string.stock_subcard_chip_unknown
        MedicineStockState.UNTRACKED -> R.string.stock_subcard_chip_unknown
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
): MedicationStockSubcardText {
    return when (runway) {
        is RunwayProjection.Days -> MedicationStockSubcardText(
            resId = R.string.stock_subcard_runway_days,
            intArg = runway.days,
        )
        RunwayProjection.BeyondHorizon -> MedicationStockSubcardText(
            resId = R.string.stock_subcard_runway_plenty,
        )
        RunwayProjection.NoSchedule -> MedicationStockSubcardText(
            resId = R.string.stock_subcard_runway_unknown,
        )
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
    suffix: String = "",
): String {
    val numeratorText = formatStockSubcardCount(numerator)
    if (denominator == null || !denominator.isFinite() || denominator <= 0.0) {
        return "$numeratorText$suffix"
    }
    return "$numeratorText / ${formatStockSubcardCount(denominator)}$suffix"
}

private fun formatStockSubcardCount(value: Double?): String {
    val resolved = value?.takeIf { it.isFinite() } ?: return "-"
    return NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(resolved)
}
