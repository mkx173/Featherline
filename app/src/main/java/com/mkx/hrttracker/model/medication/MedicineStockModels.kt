package com.mkx.hrttracker.model.medication

/**
 * Stock state attached to a medicine when tracking is enabled.
 *
 * For pool preparations (tablets, capsules, patches, sachets, single-use vials):
 * - unitsRemaining = current units in the pool
 * - openContainerAmount is always null
 *
 * For container preparations (multi-use vial, gel container):
 * - unitsRemaining = sealed units count
 * - openContainerAmount = current open mL or g
 */
data class MedicineStock(
    val trackingEnabled: Boolean = false,
    val unitsRemaining: Double? = null,
    val unitsLastTotal: Double? = null,
    val openContainerAmount: Double? = null,
    val warnAtDaysRemaining: Int = 14,
    val generation: Long = 0L,
)

enum class MedicineStockState {
    HEALTHY,
    LOW,
    OUT,
    UNTRACKED,
    NO_RUNWAY,
}

/** Derived projection per medicine for stock UI consumption. */
data class MedicineStockProjection(
    val medicine: Medicine,
    /** Per-day consumption rate, in the medicine's stock unit. 0 when not in any active group. */
    val dosesPerDayMagnitude: Double,
    /** Total stock units across pool + open + sealed-times-containerSize. 0 when untracked. */
    val totalStockUnits: Double,
    /** Stock-unit-aware "days remaining" or null when undefined (no rate, or untracked). */
    val runwayDays: Double?,
    val state: MedicineStockState,
)
