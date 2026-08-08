package com.mkx.hrttracker.model.pk

import java.util.Collections
import kotlin.math.sqrt

@ConsistentCopyVisibility
data class ScaleCap private constructor(
    val minInclusive: Double,
    val maxInclusive: Double,
) {
    companion object {
        fun create(minInclusive: Double, maxInclusive: Double): ScaleCap? {
            if (!minInclusive.isFinite() || !maxInclusive.isFinite()) return null
            if (minInclusive <= 0.0 || maxInclusive < minInclusive) return null
            return ScaleCap(minInclusive, maxInclusive)
        }
    }
}

/** Constants frozen by the v9 candidate that do not depend on open field evidence. */
object PkCalibrationDefaults {
    const val STUDENT_T_NU = 4.0
    const val ROUTE_LOG_SCALE_PRIOR_SD = 0.30
    const val DOMINANT_ROUTE_SHARE_MIN = 0.8
    const val MIN_DOMINANT_LABS_FOR_PROMOTION = 2
    const val MIN_DOMINANT_LABS_FOR_EXTREME_SCALE = 3
    const val EXTREME_SCALE_CORE_MIN = 0.5
    const val EXTREME_SCALE_CORE_MAX = 2.0
    const val ROUTE_ATTRIBUTABLE_MIN_FRACTION = 0.05
    const val ROUTE_ATTRIBUTABLE_MIN_PGML = 1e-12
    const val DRUG_SIGNAL_LOG_RANGE_MIN = 0.6931471805599453
    const val ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION = 0.20
    const val ROBUST_RMSE_GATE_FACTOR = 2.0
    const val OUTLIER_WEIGHT_MIN = 0.25

    /**
     * v10.0 §A3: the RMSE promotion gate scales with the observation-noise model,
     * like the Student-t outlier gate, instead of being an absolute constant.
     */
    fun robustRmseLogMaxForPromotion(rLog: Double): Double =
        ROBUST_RMSE_GATE_FACTOR * sqrt(rLog)

    const val GLOBAL_SEARCH_NUMERIC_GUARD_ABS_BETA = 20.0
    const val GRID_STEP_LOG = 1e-3
    const val GRID_MIN_NODES = 16
    const val STATIONARY_ROOT_BETA_ABS_TOL = 1e-12
    const val STATIONARY_ROOT_MAX_EVAL = 200

    const val BAND_GH_NODES = 16
    const val BAND_GH_REFINEMENT_NODES = 32
    const val BAND_ROOT_X_ABS_TOL = 1e-8
    const val BAND_ROOT_CDF_TOL = 1e-8
    const val BAND_ROOT_MAX_EVAL = 200
    const val BAND_ROOT_INITIAL_HALF_WIDTH_LOG = 1.0
    const val BAND_ROOT_BRACKET_EXPANSION = 2.0
    const val BAND_ROOT_MAX_HALF_WIDTH_LOG = 64.0
    const val BAND_VALIDATE_REL = 1e-3
    const val BAND_VALIDATE_ABS_PGML = 0.05
    const val BAND_RENDER_FILL_POINTS = 8
    const val BAND_RENDER_BUDGET_MS = 50

    val DISPLAY_SCALE_CAP_BY_ROUTE: Map<PkCalibrationRoute, ScaleCap> =
        Collections.unmodifiableMap(
            linkedMapOf(
                PkCalibrationRoute.INJECTION to requireNotNull(ScaleCap.create(0.5, 2.0)),
                PkCalibrationRoute.PATCH to requireNotNull(ScaleCap.create(0.5, 2.0)),
                PkCalibrationRoute.GEL to requireNotNull(ScaleCap.create(0.25, 3.0)),
                PkCalibrationRoute.ORAL to requireNotNull(ScaleCap.create(0.5, 2.0)),
                PkCalibrationRoute.SUBLINGUAL to requireNotNull(ScaleCap.create(0.25, 3.0)),
            )
        )
}

/**
 * Runtime evidence boundary for route calibration.
 *
 * Production is deliberately represented only by [productionDefault], which
 * is disabled and carries no guessed informative-signal or observation-noise
 * constants. Research and tests must supply both open values explicitly.
 */
@ConsistentCopyVisibility
data class PkCalibrationConfig private constructor(
    val personalizedOutputEnabled: Boolean,
    val isResearchOrTest: Boolean,
    val drugMinInformativePgml: Double?,
    val rLog: Double?,
) {
    internal fun isSolverEligible(): Boolean {
        return personalizedOutputEnabled && isResearchOrTest &&
                drugMinInformativePgml?.let { value -> value.isFinite() && value > 0.0 } == true &&
                rLog?.let { value -> value.isFinite() && value > 0.0 } == true
    }

    companion object {
        private val ProductionDefault = PkCalibrationConfig(
            personalizedOutputEnabled = false,
            isResearchOrTest = false,
            drugMinInformativePgml = null,
            rLog = null,
        )

        fun productionDefault(): PkCalibrationConfig = ProductionDefault

        fun researchOrTest(
            drugMinInformativePgml: Double,
            rLog: Double,
        ): PkCalibrationConfig? {
            if (!drugMinInformativePgml.isFinite() || drugMinInformativePgml <= 0.0) {
                return null
            }
            if (!rLog.isFinite() || rLog <= 0.0) return null
            return PkCalibrationConfig(
                personalizedOutputEnabled = true,
                isResearchOrTest = true,
                drugMinInformativePgml = drugMinInformativePgml,
                rLog = rLog,
            )
        }
    }
}
