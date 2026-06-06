package com.mkx.hrttracker.data.repository

data class SimulatedStock(
    val open: Double,
    val sealed: Double,
    val containerCapacity: Double,
    val isContainer: Boolean,
)

fun SimulatedStock.applyDose(perDose: Double): SimulatedStock? {
    if (perDose <= 0.0) return this
    if (!isContainer) {
        return if (open + FLOAT_EPSILON >= perDose) {
            copy(open = (open - perDose).coerceAtLeast(0.0).zeroIfTiny())
        } else {
            null
        }
    }
    if (open + FLOAT_EPSILON >= perDose) {
        return copy(open = (open - perDose).coerceAtLeast(0.0).zeroIfTiny())
    }
    if (sealed + FLOAT_EPSILON < 1.0 || containerCapacity + FLOAT_EPSILON < perDose) {
        return null
    }
    // Exact-split: the open dreg is consumed first, so only the residual
    // (perDose - open) is drawn from the freshly cracked unit, carrying the dreg
    // forward instead of discarding it.
    return copy(
        open = (containerCapacity - (perDose - open)).coerceAtLeast(0.0).zeroIfTiny(),
        sealed = (sealed - 1.0).coerceAtLeast(0.0).zeroIfTiny(),
    )
}

fun simulateNDoses(
    state: SimulatedStock,
    n: Int,
    perDose: Double,
): Int {
    if (n <= 0 || perDose <= 0.0) return 0
    var current = state
    var fulfilled = 0
    repeat(n) {
        current = current.applyDose(perDose) ?: return fulfilled
        fulfilled += 1
    }
    return fulfilled
}
