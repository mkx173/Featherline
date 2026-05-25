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
