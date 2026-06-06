package com.mkx.hrttracker.data.repository

data class SimulatedStock(
    val open: Double,
    val sealed: Double,
    val containerCapacity: Double,
    val isContainer: Boolean,
)

fun SimulatedStock.applyDose(perDose: Double): SimulatedStock? {
    if (perDose <= 0.0) return this
    val capacity = containerCapacity.takeIf { isContainer && it.isFinite() && it > 0.0 }
    if (capacity == null) {
        // Pool stock, or a container with no usable capacity: only the open pool
        // is drawable, so the dose is fulfillable exactly when it covers it.
        return if (open + FLOAT_EPSILON >= perDose) {
            copy(open = (open - perDose).coerceAtLeast(0.0).zeroIfTiny())
        } else {
            null
        }
    }
    // Fulfillable whenever the total on hand covers the dose, regardless of how
    // many vial boundaries it crosses. The shared total round-trip conserves
    // volume and re-splits under the strict unseal invariant.
    if (open + sealed * capacity + FLOAT_EPSILON < perDose) return null
    val split = deductContainerTotal(
        open = open,
        sealed = sealed,
        capacity = capacity,
        dose = perDose,
    )
    return copy(open = split.open, sealed = split.sealed)
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
