package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock

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
    val split = deductContainerTotal(
        open = fields.openContainerAmount ?: 0.0,
        sealed = fields.unitsRemaining ?: 0.0,
        capacity = capacity,
        dose = requestedDose,
    )
    return fields.copy(
        unitsRemaining = split.sealed,
        openContainerAmount = split.open,
    )
}

private fun MedicinePreparation.containerCapacityOrNull(): Double? {
    return when (this) {
        is MedicinePreparation.InjectionMultiUseVial -> vialVolumeMl
        is MedicinePreparation.GelContainer -> containerWeightGrams
        else -> null
    }
}
