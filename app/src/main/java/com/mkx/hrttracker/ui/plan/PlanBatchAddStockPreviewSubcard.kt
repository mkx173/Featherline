package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.ui.components.HrtPill
import com.mkx.hrttracker.ui.components.HrtPillSize
import com.mkx.hrttracker.ui.components.stockInventoryCountText
import com.mkx.hrttracker.ui.components.stockRateUnitRes
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.text.NumberFormat

/**
 * Minimal before -> after stock preview shown under each medicine card on the
 * batch-add screen. Unlike the shared [com.mkx.hrttracker.ui.components.MedicationStockSubcard]
 * it has no status chip, runway, or progress bar — it only states the projected
 * stock change so a power user can see the result before saving:
 *  - count preparations: "10 tablets → 8 tablets"
 *  - container preparations: "2 vials + 0.5 mL → 1 vial + 0.75 mL"
 */
@Composable
internal fun PlanBatchAddStockPreviewSubcard(
    preparation: MedicinePreparation,
    beforeStock: MedicineStock,
    afterStock: MedicineStock,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val beforeText = stockPreviewAmountText(context, preparation, beforeStock)
    val afterText = stockPreviewAmountText(context, preparation, afterStock)

    HrtPill(
        label = "$beforeText → $afterText",
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        size = HrtPillSize.Medium,
        icon = { Icon(painterResource(R.drawable.ic_inventory_2), null, iconModifier) },
        modifier = modifier,
    )
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
): String {
    val primaryCount = stock.unitsRemaining ?: 0.0
    val primaryText = stockInventoryCountText(context, preparation, primaryCount)
        ?: stockPreviewNumber(primaryCount)

    val isContainer = preparation is MedicinePreparation.InjectionMultiUseVial ||
        preparation is MedicinePreparation.GelContainer
    if (!isContainer) return primaryText

    val openAmount = stock.openContainerAmount ?: 0.0
    val openUnitRes = stockRateUnitRes(preparation)
    val openText = if (openUnitRes != null) {
        context.getString(
            R.string.stock_row_count_with_unit,
            stockPreviewNumber(openAmount),
            context.getString(openUnitRes),
        )
    } else {
        stockPreviewNumber(openAmount)
    }
    return "$primaryText + $openText"
}

private fun stockPreviewNumber(value: Double): String {
    return NumberFormat.getInstance().apply { maximumFractionDigits = 2 }.format(value)
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
            )
        }
    }
}
