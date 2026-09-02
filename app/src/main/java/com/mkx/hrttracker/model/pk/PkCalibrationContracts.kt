package com.mkx.hrttracker.model.pk

import java.util.UUID
import kotlin.math.exp

/**
 * Stable calibration route identity. Declaration order is the canonical order
 * used by every calibration map, result, and deterministic accumulation.
 *
 * This is deliberately separate from [PkRoute], which describes dose events
 * and also contains the mass-free PATCH_REMOVE event.
 */
enum class PkCalibrationRoute(val stableId: String) {
    INJECTION("injection"),
    PATCH("patch"),
    GEL("gel"),
    ORAL("oral"),
    SUBLINGUAL("sublingual");

    companion object {
        private val byStableId = entries.associateBy(PkCalibrationRoute::stableId)

        fun fromStableId(stableId: String): PkCalibrationRoute? = byStableId[stableId]
    }
}

/** Canonical contribution mapping shared by forward evaluation and calibration. */
fun PkRoute.calibrationRoute(): PkCalibrationRoute? {
    return when (this) {
        PkRoute.INJECTION -> PkCalibrationRoute.INJECTION
        PkRoute.PATCH_APPLY -> PkCalibrationRoute.PATCH
        PkRoute.PATCH_REMOVE -> null
        PkRoute.GEL -> PkCalibrationRoute.GEL
        PkRoute.ORAL -> PkCalibrationRoute.ORAL
        PkRoute.SUBLINGUAL -> PkCalibrationRoute.SUBLINGUAL
    }
}

fun PkDoseEvent.calibrationRoute(): PkCalibrationRoute? = route.calibrationRoute()

/**
 * Route-isolated exposure parameters. Values are log scales (beta), never raw
 * multiplicative scales. Exact zero entries are omitted canonically.
 */
@ConsistentCopyVisibility
data class PkPersonalParams private constructor(
    val routeLogScale: Map<PkCalibrationRoute, Double>,
    val thetaKGlobal: Double,
) {
    fun logScaleFor(route: PkCalibrationRoute): Double = routeLogScale[route] ?: 0.0

    internal fun scaleFor(route: PkCalibrationRoute): Double {
        val beta = routeLogScale[route] ?: return 1.0
        return exp(beta)
    }

    companion object {
        private val Population = PkPersonalParams(
            routeLogScale = emptyMap(),
            thetaKGlobal = 0.0,
        )

        fun population(): PkPersonalParams = Population

        /**
         * Builds canonical v9 parameters. Calibration currently permits only
         * the normalized exact +0.0 future clearance hook.
         */
        fun create(
            routeLogScale: Map<PkCalibrationRoute, Double> = emptyMap(),
            thetaKGlobal: Double = 0.0,
        ): PkPersonalParams? {
            if (thetaKGlobal.toBits() != 0.0.toBits()) return null

            val canonical = linkedMapOf<PkCalibrationRoute, Double>()
            for (route in PkCalibrationRoute.entries) {
                val beta = routeLogScale[route] ?: continue
                if (!beta.isFinite()) return null
                if (beta == 0.0) continue
                val scale = exp(beta)
                if (!scale.isFinite() || scale <= 0.0) return null
                canonical[route] = beta
            }
            if (canonical.isEmpty()) return Population
            return PkPersonalParams(
                routeLogScale = canonical.toMap(),
                thetaKGlobal = 0.0,
            )
        }
    }
}

/** One-point, five-route drug-only E2 decomposition in pg/mL. */
@ConsistentCopyVisibility
data class PkForwardBreakdown private constructor(
    val byRouteDrugPgml: Map<PkCalibrationRoute, Double>,
    val totalDrugPgml: Double,
) {
    companion object {
        /**
         * Accepts only a canonical ordered map with all five routes. Total is
         * computed here, left-to-right in that same order.
         */
        fun create(
            byRouteDrugPgml: Map<PkCalibrationRoute, Double>,
        ): PkForwardBreakdown? {
            if (byRouteDrugPgml.keys.toList() != PkCalibrationRoute.entries) return null

            val canonical = linkedMapOf<PkCalibrationRoute, Double>()
            var total = 0.0
            for (route in PkCalibrationRoute.entries) {
                val raw = byRouteDrugPgml[route] ?: return null
                if (!raw.isFinite() || raw < 0.0) return null
                val contribution = raw.normalizePositiveZero()
                canonical[route] = contribution
                total += contribution
                if (!total.isFinite() || total < 0.0) return null
            }
            return PkForwardBreakdown(
                byRouteDrugPgml = canonical.toMap(),
                totalDrugPgml = total.normalizePositiveZero(),
            )
        }
    }
}

enum class PkCalibrationGlobalState {
    READY,
    /** No estradiol dose has been logged: there is no modeled curve to adjust. */
    NO_DOSE_HISTORY,
    /** Zero E2 labs exist. */
    NO_USABLE_LABS,
    /** The forward model or the joint fit did not complete; population is shown. */
    NUMERIC_FAILURE,
}

/**
 * Warn-only: a route any included lab touches always shows its fitted
 * adjustment. LAB_ADJUSTED_PROVISIONAL carries one or more warning
 * [PkCalibrationReason]s the user should read; LAB_CALIBRATED is a fit that
 * raised none.
 */
enum class PkRouteCalibrationDisplayState {
    /** No included lab has any modeled signal from this route. */
    POPULATION_NO_LAB_SIGNAL,
    POPULATION_NUMERIC_FAILURE,
    LAB_ADJUSTED_PROVISIONAL,
    LAB_CALIBRATED;

    val isAdjusted: Boolean
        get() = this == LAB_ADJUSTED_PROVISIONAL || this == LAB_CALIBRATED
}

enum class PkCalibrationRenderState {
    POPULATION,
    PERSONALIZED,
    NUMERIC_UNAVAILABLE,
}

enum class PkCalibrationBandState {
    NOT_APPLICABLE_POPULATION,
    READY,
    NUMERIC_UNAVAILABLE,
}

/** Warning reasons on an adjusted route row. The user reads them; nothing is withheld. */
enum class PkCalibrationReason {
    /** No included lab draws ≥20% of its modeled signal from this route. */
    NO_SUPPORTING_LABS,
    /** Outside the route's usual range, or a large shift backed by fewer than three labs. */
    SCALE_OUTSIDE_USUAL_RANGE,
    /** Posterior still wide, or the supporting labs sit at similar modeled signal levels. */
    UNCERTAIN,
    POSTERIOR_MODE_AMBIGUOUS,
    RESIDUAL_FIT_POOR,
    UNREVIEWED_OUTLIER,
}

/** Why one lab was set aside by the fit; always surfaced on its row. */
enum class PkCalibrationLabIgnoreReason {
    /** Non-positive value inside a drug window: log-undefined. */
    NON_POSITIVE_VALUE,
    /** Modeled drug-attributable E2 below the informative floor at collection time. */
    BELOW_INFORMATIVE_SIGNAL,
    /** The forward model could not be evaluated at the collection time. */
    NUMERIC_FAILURE,
}

data class PkRouteCalibrationResult(
    val route: PkCalibrationRoute,
    val displayState: PkRouteCalibrationDisplayState,
    val reasons: Set<PkCalibrationReason> = emptySet(),
    /** Log scale; present exactly when [displayState] is adjusted. */
    val fittedBeta: Double? = null,
    val betaPosteriorSd: Double? = null,
    val supportingLabCount: Int = 0,
    val minStudentTWeight: Double? = null,
    val unreviewedOutlierLabIds: Set<UUID> = emptySet(),
) {
    init {
        require((fittedBeta != null) == displayState.isAdjusted)
        require((displayState == PkRouteCalibrationDisplayState.LAB_CALIBRATED) ==
                (displayState.isAdjusted && reasons.isEmpty()))
    }
}

data class PkCurvePoint(
    val epochMillis: Long,
    val concentrationPgMl: Double,
)

data class PkPredictiveBandKnot(
    val epochMillis: Long,
    val p025Pgml: Double,
    val p158655254Pgml: Double,
    val p50Pgml: Double,
    val p841344746Pgml: Double,
    val p975Pgml: Double,
)

/**
 * v10.0 §A10.5: the symmetric Laplace covariance block over the promoted
 * routes, indexed in canonical route order.
 */
data class PkCalibrationPromotedCovariance(
    val routes: List<PkCalibrationRoute>,
    val values: List<List<Double>>,
) {
    fun covariance(first: PkCalibrationRoute, second: PkCalibrationRoute): Double? {
        val row = routes.indexOf(first)
        val column = routes.indexOf(second)
        if (row < 0 || column < 0) return null
        return values[row][column]
    }
}

data class PkCalibrationResult(
    val globalState: PkCalibrationGlobalState,
    /** Exactly five rows in canonical route order when READY, empty otherwise. */
    val routeResults: List<PkRouteCalibrationResult> = emptyList(),
    val promotedBetaCovariance: PkCalibrationPromotedCovariance? = null,
    /** Labs the fit set aside, each with the reason shown on its row. */
    val ignoredLabs: Map<UUID, PkCalibrationLabIgnoreReason> = emptyMap(),
) {
    val promotedRoutes: List<PkCalibrationRoute>
        get() = routeResults.filter { it.displayState.isAdjusted }.map { it.route }

    /**
     * Adjusted routes with at least one supporting lab: the only ones applied
     * to the drawn curve and band, and the only ones the hero and status body
     * name. A route fitted from a negligible share still shows its fitted
     * adjustment on its row, with the no-supporting-labs warning, but is not
     * applied: the hero would otherwise call a personalized curve "population".
     */
    val supportedPromotedRoutes: List<PkCalibrationRoute>
        get() = supportedAdjustedRows.map { it.route }

    /** Parameters applied to the curve: [supportedPromotedRoutes] only. */
    val displayParams: PkPersonalParams
        get() = requireNotNull(
            PkPersonalParams.create(
                supportedAdjustedRows.associate { it.route to requireNotNull(it.fittedBeta) }
            )
        )

    private val supportedAdjustedRows: List<PkRouteCalibrationResult>
        get() = routeResults.filter { it.displayState.isAdjusted && it.supportingLabCount > 0 }
}

data class PkCalibrationRenderResult(
    val renderState: PkCalibrationRenderState,
    /** Promoted routes with a positive contribution somewhere in this chart range. */
    val effectivePromotedRoutes: List<PkCalibrationRoute> = emptyList(),
    val effectiveDisplayParams: PkPersonalParams = PkPersonalParams.population(),
    val centralCurve: List<PkCurvePoint>,
    val bandState: PkCalibrationBandState,
    val bandKnots: List<PkPredictiveBandKnot> = emptyList(),
)
