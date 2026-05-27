package com.mkx.hrttracker.data.repository

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
