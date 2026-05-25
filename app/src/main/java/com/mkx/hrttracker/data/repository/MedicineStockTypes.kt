package com.mkx.hrttracker.data.repository

/** Returned by MedicineStockMutator.resolveDeductionForInsert; caller stamps onto the log. */
internal data class StockDeductionStamp(
    val deductionUnits: Double,
    val generation: Long,
)

/** Captured from an existing log entry before delete-time refund evaluation. */
internal data class MedicationLogStockSnapshot(
    val deductionUnits: Double?,
    val generation: Long?,
)

/** Recount: absolute correction. Bumps stockGeneration. */
data class StockRecount(
    /** Pool: "Now have"; Container: sealed count. */
    val unitsRemaining: Double,
    /** Pool: "Out of total" (defaults to unitsRemaining if null); Container: ignored. */
    val unitsLastTotal: Double? = null,
    /** Container only: open mL / g. Ignored for pool types. */
    val openContainerAmount: Double? = null,
)

/** Received: incremental top-up. Does not bump stockGeneration. */
data class StockReceived(
    /** Pool: amount received; Container: sealed units received. */
    val unitsReceived: Double,
    /** Container only: optional "open container remaining" to declare in same gesture. */
    val openContainerAmount: Double? = null,
)

/** Discard: incremental reduction. Does not bump stockGeneration. */
sealed interface StockDiscard {
    /** Pool: amount to subtract. */
    data class FromPool(val units: Double) : StockDiscard

    /** Container: amount to subtract from openContainerAmount. */
    data class FromOpenContainer(val amount: Double) : StockDiscard

    /** Container: sealed units to subtract from stockUnitsRemaining. */
    data class FromSealed(val units: Double) : StockDiscard
}
