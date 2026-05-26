package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Composable
fun StockStatusIndicator(
    projection: MedicineStockProjection,
    modifier: Modifier = Modifier,
    showGauge: Boolean = true,
) {
    val palette = stockChipPalette(projection.state)
    val gaugeInputs = stockStatusFuelGaugeInputs(projection)
    if (palette == null && (!showGauge || !gaugeInputs.visible)) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (palette != null) {
            StockChip(palette = palette)
        }
        if (showGauge && gaugeInputs.visible) {
            LinearProgressIndicator(
                progress = { gaugeInputs.progress() },
                color = palette?.contentColor ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )
        }
    }
}

@Composable
private fun StockChip(palette: StockChipPalette) {
    Surface(
        color = palette.containerColor,
        contentColor = palette.contentColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            palette.iconRes?.let { iconRes ->
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = stringResource(palette.labelRes),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private data class StockChipPalette(
    val containerColor: Color,
    val contentColor: Color,
    val labelRes: Int,
    val iconRes: Int?,
)

@Composable
private fun stockChipPalette(state: MedicineStockState): StockChipPalette? {
    return when (state) {
        MedicineStockState.OUT -> StockChipPalette(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            labelRes = R.string.stock_chip_out,
            iconRes = R.drawable.ic_warning,
        )
        MedicineStockState.IMMINENT -> StockChipPalette(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            labelRes = R.string.stock_chip_imminent,
            iconRes = R.drawable.ic_warning,
        )
        MedicineStockState.USER_LOW -> StockChipPalette(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            labelRes = R.string.stock_chip_low,
            iconRes = R.drawable.ic_notifications,
        )
        MedicineStockState.NO_RUNWAY -> StockChipPalette(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            labelRes = R.string.stock_chip_no_runway,
            iconRes = R.drawable.ic_help,
        )
        MedicineStockState.HEALTHY,
        MedicineStockState.UNTRACKED -> null
    }
}

internal data class StockFuelGaugeInputs(
    val numerator: Double,
    val denominator: Double,
    val visible: Boolean,
) {
    fun progress(): Float {
        if (!visible || denominator <= 0.0) return 0f
        return (numerator / denominator).toFloat().coerceIn(0f, 1f)
    }
}

internal fun stockStatusFuelGaugeInputs(
    projection: MedicineStockProjection,
): StockFuelGaugeInputs {
    if (!projection.medicine.stock.trackingEnabled ||
        projection.state == MedicineStockState.UNTRACKED ||
        projection.state == MedicineStockState.NO_RUNWAY
    ) {
        return StockFuelGaugeInputs(
            numerator = 0.0,
            denominator = 0.0,
            visible = false,
        )
    }
    val stock = projection.medicine.stock
    return when (val preparation = projection.medicine.preparation) {
        is MedicinePreparation.InjectionMultiUseVial -> {
            StockFuelGaugeInputs(
                numerator = stock.openContainerAmount ?: 0.0,
                denominator = preparation.vialVolumeMl,
                visible = stock.openContainerAmount != null,
            )
        }
        is MedicinePreparation.GelContainer -> {
            StockFuelGaugeInputs(
                numerator = stock.openContainerAmount ?: 0.0,
                denominator = preparation.containerWeightGrams,
                visible = stock.openContainerAmount != null,
            )
        }
        else -> {
            StockFuelGaugeInputs(
                numerator = stock.unitsRemaining ?: 0.0,
                denominator = stock.unitsLastTotal ?: 0.0,
                visible = stock.unitsRemaining != null &&
                    stock.unitsLastTotal != null &&
                    stock.unitsLastTotal > 0.0,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 260)
@Composable
private fun StockStatusIndicatorPoolPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StockStatusIndicator(previewProjection(MedicineStockState.OUT))
            StockStatusIndicator(previewProjection(MedicineStockState.IMMINENT))
            StockStatusIndicator(previewProjection(MedicineStockState.USER_LOW))
            StockStatusIndicator(previewProjection(MedicineStockState.HEALTHY))
        }
    }
}

@Preview(showBackground = true, widthDp = 260)
@Composable
private fun StockStatusIndicatorContainerPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StockStatusIndicator(previewProjection(MedicineStockState.OUT, container = true))
            StockStatusIndicator(previewProjection(MedicineStockState.IMMINENT, container = true))
            StockStatusIndicator(previewProjection(MedicineStockState.USER_LOW, container = true))
            StockStatusIndicator(previewProjection(MedicineStockState.HEALTHY, container = true))
        }
    }
}

private fun previewProjection(
    state: MedicineStockState,
    container: Boolean = false,
): MedicineStockProjection {
    val key = MedicationKey.ESTRADIOL
    val preparation = if (container) {
        MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 5.0,
        )
    } else {
        MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
    }
    val medicine = Medicine(
        uuid = UUID.fromString("00000000-0000-0000-0000-000000000021"),
        selection = MedicineSelection.Catalog(key),
        category = key.category,
        preparation = preparation,
        displayName = null,
        identityKey = MedicineIdentityKey.catalog(key, preparation),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null,
        stock = if (container) {
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 2.0,
                unitsLastTotal = 3.0,
                openContainerAmount = 2.0,
                warnAtDaysRemaining = 14,
            )
        } else {
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 4.0,
                unitsLastTotal = 10.0,
                warnAtDaysRemaining = 14,
            )
        },
    )
    return MedicineStockProjection(
        medicine = medicine,
        dosesPerDayMagnitude = 1.0,
        totalStockUnits = 4.0,
        runway = RunwayProjection.Days(
            days = 7,
            lastFulfillable = LocalDate.of(2026, 1, 8),
        ),
        intervalDays = null,
        maxPerAdministration = 1.0,
        state = state,
    )
}
