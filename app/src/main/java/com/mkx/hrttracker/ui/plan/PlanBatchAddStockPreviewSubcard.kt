package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.isContainerTopology
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.formatStockCount
import com.mkx.hrttracker.ui.components.HrtPill
import com.mkx.hrttracker.ui.components.HrtPillSize
import com.mkx.hrttracker.ui.components.stockInventoryCountText
import com.mkx.hrttracker.ui.components.stockRateUnitRes
import com.mkx.hrttracker.ui.components.stockSubcardChipColors
import com.mkx.hrttracker.ui.components.stockSubcardChipLabelRes
import com.mkx.hrttracker.ui.components.stockSubcardRunwayText
import com.mkx.hrttracker.ui.components.stockSubcardTone
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDate
import java.util.Locale

/**
 * Compact stock preview shown under each medicine card on the batch-add screen,
 * rendered as a [FlowRow] of pills:
 *  - projected stock change ("10 tablets → 8 tablets", or
 *    "2 vials + 0.5 mL → 1 vial + 0.75 mL" for containers)
 *  - current stock status (e.g. "Low", "Out")
 *  - current days remaining (e.g. "5 days")
 *
 * The status/runway pills reuse the shared stock mappings
 * ([stockSubcardChipLabelRes], [stockSubcardRunwayText]) so their copy stays in
 * sync with [com.mkx.hrttracker.ui.components.MedicationStockSubcard].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanBatchAddStockPreviewSubcard(
    preparation: MedicinePreparation,
    beforeStock: MedicineStock,
    afterStock: MedicineStock,
    stockState: MedicineStockState,
    runway: RunwayProjection,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appLocale = rememberAppLocale()
    val beforeText = stockPreviewAmountText(context, preparation, beforeStock, appLocale)
    val afterText = stockPreviewAmountText(context, preparation, afterStock, appLocale)
    val statusLabel = stockSubcardChipLabelRes(stockState)?.let { stringResource(it) }
    val runwayModel = stockSubcardRunwayText(runway)
    val daysLabel = when {
        runwayModel == null -> null
        runwayModel.pluralResId != null && runwayModel.intArg != null ->
            pluralStringResource(runwayModel.pluralResId, runwayModel.intArg, runwayModel.intArg)

        runwayModel.resId != null -> stringResource(runwayModel.resId)
        else -> null
    }

    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    // The status and days-remaining pills carry severity color (tertiary for
    // low, error for almost-out/out), reusing the shared chip tone so they match
    // MedicationStockSubcard; only the before-to-after stock pill stays neutral.
    val statusColors = stockSubcardChipColors(stockSubcardTone(stockState))

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (statusLabel != null) {
            HrtPill(
                label = statusLabel,
                containerColor = statusColors.container,
                contentColor = statusColors.content,
                size = HrtPillSize.Medium,
                icon = { Icon(painterResource(R.drawable.ic_shopping_cart), null, iconModifier) },
            )
        }
        if (daysLabel != null) {
            HrtPill(
                label = daysLabel,
                containerColor = statusColors.container,
                contentColor = statusColors.content,
                size = HrtPillSize.Medium,
                icon = { Icon(painterResource(R.drawable.ic_calendar_clock), null, iconModifier) },
            )
        }
        HrtPill(
            label = "$beforeText → $afterText",
            containerColor = containerColor,
            size = HrtPillSize.Medium,
            icon = { Icon(painterResource(R.drawable.ic_inventory_2), null, iconModifier) },
        )
    }
}

/**
 * "2 vials + 0.5 mL" for container preparations (sealed count + open amount),
 * or "10 tablets" for count preparations. Falls back to a bare number for
 * preparations without an inventory unit (e.g. a patch-off, which never tracks).
 */
private fun stockPreviewAmountText(
    context: Context,
    preparation: MedicinePreparation,
    stock: MedicineStock,
    locale: Locale,
): String {
    val primaryCount = stock.unitsRemaining ?: 0.0
    val primaryText = stockInventoryCountText(context, preparation, primaryCount)
        ?: stockPreviewNumber(primaryCount, locale)

    val isContainer = preparation.type.isContainerTopology()
    if (!isContainer) return primaryText

    val openAmount = stock.openContainerAmount ?: 0.0
    val openUnitRes = stockRateUnitRes(preparation)
    val openText = if (openUnitRes != null) {
        context.getString(
            R.string.stock_row_count_with_unit,
            stockPreviewNumber(openAmount, locale),
            context.getString(openUnitRes),
        )
    } else {
        stockPreviewNumber(openAmount, locale)
    }
    return "$primaryText + $openText"
}

internal fun stockPreviewNumber(value: Double, locale: Locale): String {
    return formatStockCount(value, locale)
}

@Preview(
    name = "Batch Add Stock Preview Subcard",
    showBackground = true,
    widthDp = 420,
)
@Composable
private fun PlanBatchAddStockPreviewSubcardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Count preparation: a pool of tablets.
            PlanBatchAddStockPreviewSubcard(
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                beforeStock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 10.0,
                    unitsLastTotal = 10.0,
                ),
                afterStock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 8.0,
                    unitsLastTotal = 10.0,
                ),
                stockState = MedicineStockState.HEALTHY,
                runway = RunwayProjection.Days(
                    days = 30,
                    lastFulfillable = LocalDate.of(2026, 5, 1)
                ),
            )
            // Multi-use vial: open amount + sealed vials; the batch cracks a vial.
            PlanBatchAddStockPreviewSubcard(
                preparation = MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = 20.0,
                    vialVolumeMl = 1.0,
                ),
                beforeStock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 2.0,
                    unitsLastTotal = 2.0,
                    openContainerAmount = 0.5,
                ),
                afterStock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 1.0,
                    unitsLastTotal = 2.0,
                    openContainerAmount = 0.75,
                ),
                stockState = MedicineStockState.USER_LOW,
                runway = RunwayProjection.Days(
                    days = 6,
                    lastFulfillable = LocalDate.of(2026, 4, 16)
                ),
            )
            // Gel container: open grams + sealed containers.
            PlanBatchAddStockPreviewSubcard(
                preparation = MedicinePreparation.GelContainer(
                    concentrationPercent = 0.06,
                    containerWeightGrams = 80.0,
                ),
                beforeStock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 3.0,
                    unitsLastTotal = 4.0,
                    openContainerAmount = 41.26,
                ),
                afterStock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 2.0,
                    unitsLastTotal = 4.0,
                    openContainerAmount = 20.0,
                ),
                stockState = MedicineStockState.OUT,
                runway = RunwayProjection.Days(
                    days = 0,
                    lastFulfillable = LocalDate.of(2026, 4, 10)
                ),
            )
        }
    }
}
