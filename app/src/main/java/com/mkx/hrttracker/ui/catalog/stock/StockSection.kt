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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import java.util.Locale
import kotlin.math.floor

@Composable
fun StockSection(
    projection: MedicineStockProjection,
    onAdjustClick: () -> Unit,
    onOptInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!projection.medicine.stock.trackingEnabled) {
        OptInCard(onClick = onOptInClick, modifier = modifier)
        return
    }

    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            projection = projection,
            onAdjustClick = onAdjustClick,
        )
        StockRows(projection = projection)
    }
}

@Composable
private fun SectionHeader(
    projection: MedicineStockProjection,
    onAdjustClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile()
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.stock_section_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(8.dp))
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
        TextButton(onClick = onAdjustClick) {
            Text(stringResource(R.string.stock_adjust_button))
        }
    }
}

@Composable
private fun IconTile() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(36.dp),
    ) {
        androidx.compose.material3.Icon(
            painter = painterResource(R.drawable.ic_pill),
            contentDescription = null,
            modifier = Modifier.padding(8.dp),
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
            ContainerStockRows(
                sealedLabel = stringResource(
                    R.string.stock_row_sealed_vials,
                    formatCount(stock.unitsRemaining),
                ),
                openLabel = stringResource(
                    R.string.stock_row_open_vial,
                    formatCount(stock.openContainerAmount),
                    formatCount(preparation.vialVolumeMl),
                ),
                openProgress = computeProgress(
                    numerator = stock.openContainerAmount,
                    denominator = preparation.vialVolumeMl,
                ),
                projection = projection,
            )
        }

        is MedicinePreparation.GelContainer -> {
            ContainerStockRows(
                sealedLabel = stringResource(
                    R.string.stock_row_sealed_pumps,
                    formatCount(stock.unitsRemaining),
                ),
                openLabel = stringResource(
                    R.string.stock_row_open_pump,
                    formatCount(stock.openContainerAmount),
                    formatCount(preparation.containerWeightGrams),
                ),
                openProgress = computeProgress(
                    numerator = stock.openContainerAmount,
                    denominator = preparation.containerWeightGrams,
                ),
                projection = projection,
            )
        }

        else -> {
            PoolStockRow(
                label = poolLabelForPreparation(
                    preparation = preparation,
                    units = stock.unitsRemaining,
                    lastTotal = stock.unitsLastTotal,
                ),
                progress = computeProgress(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                ),
                projection = projection,
            )
        }
    }
}

@Composable
private fun PoolStockRow(
    label: String,
    progress: Float,
    projection: MedicineStockProjection,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FuelGauge(progress = progress, state = projection.state)
        RunwaySubCard(projection = projection)
    }
}

@Composable
private fun ContainerStockRows(
    sealedLabel: String,
    openLabel: String,
    openProgress: Float,
    projection: MedicineStockProjection,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(sealedLabel, style = MaterialTheme.typography.bodyLarge)
        Text(openLabel, style = MaterialTheme.typography.bodyLarge)
        FuelGauge(progress = openProgress, state = projection.state)
        RunwaySubCard(projection = projection)
    }
}

@Composable
private fun FuelGauge(
    progress: Float,
    state: MedicineStockState,
) {
    val color = when (state) {
        MedicineStockState.LOW -> MaterialTheme.colorScheme.tertiary
        MedicineStockState.OUT -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
    )
}

@Composable
private fun RunwaySubCard(projection: MedicineStockProjection) {
    val runwayDays = projection.runwayDays
    val label = when {
        projection.state == MedicineStockState.NO_RUNWAY || runwayDays == null -> {
            stringResource(R.string.stock_runway_unknown_title)
        }

        else -> {
            stringResource(
                R.string.stock_runway_days_remaining,
                floor(runwayDays).toInt(),
            )
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (projection.state == MedicineStockState.NO_RUNWAY || runwayDays == null) {
                Text(
                    text = stringResource(R.string.stock_runway_unknown_body),
                    style = MaterialTheme.typography.bodySmall,
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
private fun poolLabelForPreparation(
    preparation: MedicinePreparation,
    units: Double?,
    lastTotal: Double?,
): String {
    val countText = if (lastTotal != null) {
        stringResource(
            R.string.stock_row_count_over_total,
            formatCount(units),
            formatCount(lastTotal),
        )
    } else {
        stringResource(R.string.stock_row_count_only, formatCount(units))
    }
    return when (preparation) {
        is MedicinePreparation.Pill -> {
            stringResource(R.string.stock_row_tablets, countText)
        }

        is MedicinePreparation.Capsule -> {
            stringResource(R.string.stock_row_capsules, countText)
        }

        is MedicinePreparation.Patch -> {
            stringResource(R.string.stock_row_patches, countText)
        }

        is MedicinePreparation.GelSachet -> {
            stringResource(R.string.stock_row_sachets, countText)
        }

        is MedicinePreparation.InjectionSingleUseVial -> {
            stringResource(R.string.stock_row_vials_single, countText)
        }

        else -> countText
    }
}
