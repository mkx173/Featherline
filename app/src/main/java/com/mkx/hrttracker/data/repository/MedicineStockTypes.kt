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
)

/** Received: incremental top-up. Does not bump stockGeneration. */
data class StockReceived(
    /** Pool: amount received; Container: sealed units received. */
    val unitsReceived: Double,
)
