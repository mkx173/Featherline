package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock
import kotlin.math.abs

internal data class StockDeductionFields(
    val unitsRemaining: Double?,
    val unitsLastTotal: Double?,
    val openContainerAmount: Double?,
)

internal fun MedicineStock.toStockDeductionFields(): StockDeductionFields {
    return StockDeductionFields(
        unitsRemaining = unitsRemaining,
        unitsLastTotal = unitsLastTotal,
        openContainerAmount = openContainerAmount,
    )
}

internal fun StockDeductionFields.applyTo(base: MedicineStock): MedicineStock {
    return base.copy(
        unitsRemaining = unitsRemaining,
        unitsLastTotal = unitsLastTotal,
        openContainerAmount = openContainerAmount,
    )
}

internal fun deductInsertedDoseStock(
    preparation: MedicinePreparation,
    stock: MedicineStock,
    requestedDose: Double,
): MedicineStock {
    if (!stock.trackingEnabled) return stock
    val fields = deductInsertedDoseStock(
        preparationType = preparation.type,
        containerCapacity = preparation.containerCapacityOrNull(),
        fields = stock.toStockDeductionFields(),
        requestedDose = requestedDose,
    )
    return fields.applyTo(stock)
}

internal fun deductInsertedDoseStock(
    preparationType: MedicinePreparationType,
    containerCapacity: Double?,
    fields: StockDeductionFields,
    requestedDose: Double,
): StockDeductionFields {
    if (preparationType == MedicinePreparationType.PATCH_OFF) return fields
    return if (preparationType.isContainerTopology()) {
        deductContainerInsertedDoseStock(
            containerCapacity = containerCapacity,
            fields = fields,
            requestedDose = requestedDose,
        )
    } else {
        deductPoolInsertedDoseStock(fields = fields, requestedDose = requestedDose)
    }
}

private fun deductPoolInsertedDoseStock(
    fields: StockDeductionFields,
    requestedDose: Double,
): StockDeductionFields {
    val remaining = fields.unitsRemaining ?: 0.0
    val actuallyDeducted = minOf(requestedDose, remaining).coerceAtLeast(0.0)
    return fields.copy(
        unitsRemaining = (remaining - actuallyDeducted).zeroIfTiny(),
        openContainerAmount = null,
    )
}

private fun deductContainerInsertedDoseStock(
    containerCapacity: Double?,
    fields: StockDeductionFields,
    requestedDose: Double,
): StockDeductionFields {
    val capacity = containerCapacity?.takeIf { it.isFinite() && it > 0.0 } ?: return fields
    val open = fields.openContainerAmount ?: 0.0
    val sealed = fields.unitsRemaining ?: 0.0
    val dose = requestedDose.coerceAtLeast(0.0)

    val newSealed: Double
    val newOpen: Double
    when {
        hasSufficientOpenAmount(open = open, dose = dose) -> {
            newSealed = sealed
            newOpen = (open - dose).zeroIfTiny().coerceAtLeast(0.0)
        }
        sealed >= 1.0 -> {
            newSealed = sealed - 1.0
            newOpen = maxOf(0.0, capacity - dose)
        }
        else -> {
            newSealed = sealed
            newOpen = 0.0
        }
    }

    val (normalizedOpen, normalizedSealed) = normalizeOpenContainer(
        open = newOpen.zeroIfTiny(),
        sealed = newSealed,
        capacity = capacity,
    )
    return fields.copy(
        unitsRemaining = normalizedSealed.zeroIfTiny(),
        openContainerAmount = normalizedOpen,
    )
}

private fun MedicinePreparation.containerCapacityOrNull(): Double? {
    return when (this) {
        is MedicinePreparation.InjectionMultiUseVial -> vialVolumeMl
        is MedicinePreparation.GelContainer -> containerWeightGrams
        else -> null
    }
}

private fun hasSufficientOpenAmount(open: Double, dose: Double): Boolean {
    if (dose <= 0.0 || open >= dose) return true
    return open > STOCK_DEDUCTION_FLOAT_EPSILON && dose - open <= STOCK_DEDUCTION_FLOAT_EPSILON
}

private const val STOCK_DEDUCTION_FLOAT_EPSILON = 1e-9

private fun Double.zeroIfTiny(): Double {
    return if (abs(this) <= STOCK_DEDUCTION_FLOAT_EPSILON) 0.0 else this
}
