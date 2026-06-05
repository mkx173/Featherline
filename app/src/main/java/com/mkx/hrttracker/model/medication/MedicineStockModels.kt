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
    USER_LOW,
    IMMINENT,
    OUT,
    UNTRACKED,
    NO_RUNWAY,
}

/**
 * Low-stock urgency rank for detecting a *new or worsened* warning: higher is
 * more urgent, 0 means not a low-stock warning state. Shared by the home
 * low-stock auto-expand and the post-log "only warn when it got worse" check.
 */
fun MedicineStockState.lowStockSeverityRank(): Int {
    return when (this) {
        MedicineStockState.USER_LOW -> 1
        MedicineStockState.IMMINENT -> 2
        MedicineStockState.OUT -> 3
        MedicineStockState.HEALTHY,
        MedicineStockState.UNTRACKED,
        MedicineStockState.NO_RUNWAY -> 0
    }
}

/** Derived projection per medicine for stock UI consumption. */
data class MedicineStockProjection(
    val medicine: Medicine,
    /** Per-day consumption rate, in the medicine's stock unit. 0 when not in any active group. */
    val dosesPerDayMagnitude: Double,
    /** Total stock units across pool + open + sealed-times-containerSize. 0 when untracked. */
    val totalStockUnits: Double,
    /** Scheduled-dose-aware runway projection. */
    val runway: RunwayProjection,
    /** Largest date gap between upcoming scheduled administration dates inside the horizon. */
    val intervalDays: Int?,
    /** Largest stock-unit demand for one scheduled administration. */
    val maxPerAdministration: Double,
    val state: MedicineStockState,
)
