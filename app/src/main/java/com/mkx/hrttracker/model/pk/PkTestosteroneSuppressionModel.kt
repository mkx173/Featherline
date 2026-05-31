package com.mkx.hrttracker.model.pk

import kotlin.math.exp

/**
 * A simple phenomenological model for Testosterone suppression by Estradiol.
 * Baseline T is reduced depending on E2 concentration.
 *
 * E2 units: pg/mL
 * T units: ng/dL
 */
class PkTestosteroneSuppressionModel {

    // Calibration state for T suppression parameters
    // We calibrate:
    // logBaselineT: The unsuppressed baseline T level
    // logSensitivity: Sensitivity to E2 (IC50 or decay constant)
    data class SuppressState(
        val logBaselineT: Double = kotlin.math.ln(600.0), // ~600 ng/dL baseline T
        val logSensitivity: Double = kotlin.math.ln(100.0) // E2 IC50 of ~100 pg/mL
    )

    fun predictT(e2PgMl: Double, state: SuppressState): Double {
        val baselineT = exp(state.logBaselineT)
        val sensitivity = exp(state.logSensitivity)

        val floorT = 15.0 // ~15 ng/dL floor for cis females / fully suppressed
        val suppressedT = baselineT * (sensitivity / (e2PgMl + sensitivity))

        return kotlin.math.max(floorT, suppressedT)
    }
}
