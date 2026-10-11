package com.mkx.hrttracker.model.pk

import kotlin.math.sqrt

/**
 * Open evidence constants for route calibration: the informative-signal floor
 * (a lab whose modeled drug-attributable E2 is below it has no usable
 * log-residual) and the log-observation-noise anchor.
 */
data class PkCalibrationConfig(
    val drugMinInformativePgml: Double,
    val rLog: Double,
) {
    init {
        require(drugMinInformativePgml.isFinite() && drugMinInformativePgml > 0.0)
        require(rLog.isFinite() && rLog > 0.0)
    }

    companion object {
        /**
         * R_LOG = 0.0225 is ~15% log-SD from published assay CV plus the
         * collection-timing budget; D_min = 5 pg/mL of population
         * drug-attributable E2 for a lab to count as informative.
         */
        val Default = PkCalibrationConfig(drugMinInformativePgml = 5.0, rLog = 0.0225)
    }
}

/** Solver and warning constants that do not depend on open field evidence. */
object PkCalibrationDefaults {
    const val STUDENT_T_NU = 4.0
    const val ROUTE_LOG_SCALE_PRIOR_SD = 0.30

    /**
     * A lab supports route r when its population share d_ir/D_i is at least
     * this value. Support only feeds warnings; every route a lab
     * touches is fitted and shown.
     */
    const val PROMOTION_SUPPORT_SHARE_MIN = 0.2

    /** Warn-only thresholds: each adds a warning reason to the route row. */
    const val MIN_SUPPORTING_LABS_FOR_EXTREME_SCALE = 3
    const val EXTREME_SCALE_CORE_MIN = 0.5
    const val EXTREME_SCALE_CORE_MAX = 2.0
    const val DRUG_SIGNAL_LOG_RANGE_MIN = 0.6931471805599453
    const val ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION = 0.20
    const val ROBUST_RMSE_GATE_FACTOR = 2.0
    const val OUTLIER_WEIGHT_MIN = 0.25

    /** The RMSE warning threshold scales with the observation-noise model. */
    fun robustRmseLogMaxForPromotion(rLog: Double): Double =
        ROBUST_RMSE_GATE_FACTOR * sqrt(rLog)

    const val GLOBAL_SEARCH_NUMERIC_GUARD_ABS_BETA = 20.0
    const val GRID_STEP_LOG = 1e-3
    const val GRID_MIN_NODES = 16
    const val STATIONARY_ROOT_BETA_ABS_TOL = 1e-12
    const val STATIONARY_ROOT_MAX_EVAL = 200

    /** Joint multi-start damped Newton. */
    const val JOINT_GRAD_TOL = 1e-10
    const val JOINT_STEP_TOL = 1e-12
    const val JOINT_MAX_ITER = 100
    const val JOINT_MODE_DISTINCT_TOL = 1e-6
    const val JOINT_MODE_NUMERIC_TOL = 1e-9
    const val JOINT_STEP_EXIT_GRAD_TOL = 1e-6

    const val BAND_GH_NODES = 32
    const val BAND_ROOT_X_ABS_TOL = 1e-8
    const val BAND_ROOT_CDF_TOL = 1e-8
    const val BAND_ROOT_MAX_EVAL = 200
    const val BAND_ROOT_INITIAL_HALF_WIDTH_LOG = 1.0
    const val BAND_ROOT_MAX_HALF_WIDTH_LOG = 64.0

    /** Usual multiplicative range per route; outside it the row warns. */
    val DISPLAY_SCALE_CAP_BY_ROUTE: Map<PkCalibrationRoute, ClosedFloatingPointRange<Double>> =
        mapOf(
            PkCalibrationRoute.INJECTION to 0.5..2.0,
            PkCalibrationRoute.PATCH to 0.5..2.0,
            PkCalibrationRoute.GEL to 0.25..3.0,
            PkCalibrationRoute.ORAL to 0.5..2.0,
            PkCalibrationRoute.SUBLINGUAL to 0.25..3.0,
        )
}
