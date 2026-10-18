package com.mkx.hrttracker.model.pk

import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.integration.gauss.GaussIntegratorFactory
import org.hipparchus.analysis.solvers.BrentSolver
import org.hipparchus.distribution.continuous.TDistribution
import java.util.TreeSet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Pure central-curve and predictive-band rendering for one exact chart domain. */
object PkCalibrationRenderer {
    /**
     * The render of a stored fit: [doseEvents] are relative to its
     * [originEpochMillis], and [weightKg] and [config] are the ones it was
     * solved with. Home passes logged plus planned doses so the band follows
     * the same projected curve the chart draws; the fit (and its covariance)
     * is unaffected either way. [statusRange] is the visible part of [domain]
     * the status describes; Home's domain, its projection window, runs past
     * its chart. Null unless [result] is READY; a numeric render failure is a
     * NUMERIC_UNAVAILABLE result.
     */
    fun render(
        result: PkCalibrationResult,
        originEpochMillis: Long,
        weightKg: Double,
        config: PkCalibrationConfig,
        domain: PkChartDomain,
        doseEvents: List<PkDoseEvent>,
        statusRange: LongRange = domain.rangeStartEpochMillis..domain.rangeEndEpochMillis,
    ): PkCalibrationRenderResult? {
        if (result.globalState != PkCalibrationGlobalState.READY) return null
        val model = PkE2ForwardModel.create(doseEvents, weightKg) ?: return NumericUnavailable

        val knots = TreeSet(domain.knotEpochMillis)
        for (event in doseEvents) {
            val epochMillis = originEpochMillis +
                    (event.timeH * MILLIS_PER_HOUR).roundToLong()
            if (epochMillis in domain.rangeStartEpochMillis..domain.rangeEndEpochMillis) {
                knots += epochMillis
            }
        }

        val population = ArrayList<Pair<Long, PkForwardBreakdown>>(knots.size)
        for (epochMillis in knots) {
            val breakdown = model.breakdownAt(
                epochDifferenceHours(epochMillis, originEpochMillis)
            ) ?: return NumericUnavailable
            population += epochMillis to breakdown
        }

        // Applied to the curve: every supported promoted route (see
        // displayParams) with a positive contribution somewhere in this range,
        // so the central curve equals the Home simulation.
        val appliedRoutes = result.supportedPromotedRoutes.filter { route ->
            population.any { (_, breakdown) -> breakdown.byRouteDrugPgml.getValue(route) > 0.0 }
        }
        val effectiveParams = PkPersonalParams.create(
            appliedRoutes.associateWith(result.displayParams::logScaleFor)
        ) ?: return NumericUnavailable

        fun centralKnot(epochMillis: Long, breakdown: PkForwardBreakdown): CentralKnot? {
            val contributions = PkCalibrationRoute.entries.associateWith { route ->
                breakdown.byRouteDrugPgml.getValue(route) * effectiveParams.scaleFor(route)
            }
            val total = contributions.values.sum()
            if (!total.isFinite()) return null
            return CentralKnot(epochMillis, contributions, total)
        }
        val centralKnots = ArrayList<CentralKnot>(population.size)
        for ((epochMillis, breakdown) in population) {
            centralKnots += centralKnot(epochMillis, breakdown) ?: return NumericUnavailable
        }
        val centralCurve = centralKnots.map { knot -> PkCurvePoint(knot.epochMillis, knot.totalDrugPgml) }

        // A calibrated route shapes the estimate where it is at least the
        // support share of the drawn signal. An exponential tail stays
        // positive for months after a route is dropped, so "any positive
        // contribution" would call a gel curve calibrated by an old oral fit,
        // and shade it. The status names the routes that shape some knot of
        // the visible range: calibrated exactly when a band shows there, and
        // fixed for the range rather than drifting with the clock.
        val effectivePromotedRoutes = appliedRoutes.filter { route ->
            centralKnots.any { knot -> knot.epochMillis in statusRange && knot.isShapedBy(route) }
        }
        // The curve, not the status: an applied route scales the whole range
        // even where it is too small a share to shape or shade.
        val renderState = if (appliedRoutes.isEmpty()) {
            PkCalibrationRenderState.POPULATION
        } else {
            PkCalibrationRenderState.PERSONALIZED
        }

        val shapedKnots = segmentShapedKnots(centralKnots) { knot -> appliedRoutes.any(knot::isShapedBy) }
        if (shapedKnots.isEmpty()) {
            return PkCalibrationRenderResult(
                renderState = renderState,
                effectivePromotedRoutes = effectivePromotedRoutes,
                effectiveDisplayParams = effectiveParams,
                centralCurve = centralCurve,
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            )
        }
        val bandKnots = buildPredictiveBand(
            shapedKnots = shapedKnots,
            appliedRoutes = appliedRoutes,
            promotedBetaCovariance = result.promotedBetaCovariance,
            rLog = config.rLog,
        )
        return PkCalibrationRenderResult(
            renderState = renderState,
            effectivePromotedRoutes = effectivePromotedRoutes,
            effectiveDisplayParams = effectiveParams,
            centralCurve = centralCurve,
            bandState = if (bandKnots == null) {
                PkCalibrationBandState.NUMERIC_UNAVAILABLE
            } else {
                PkCalibrationBandState.READY
            },
            bandKnots = bandKnots.orEmpty(),
        )
    }

    private val NumericUnavailable = PkCalibrationRenderResult(
        renderState = PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
        centralCurve = emptyList(),
        bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
    )

    /**
     * Shaped knots in curve order, each tagged with its contiguous run: a run
     * ends at every unshaped knot between two shaped ones, so the drawer can
     * leave that span unshaded instead of guessing the gap from knot spacing
     * (which is nonuniform, so a one- or two-knot hole is invisible to it).
     */
    internal fun <T> segmentShapedKnots(
        knots: List<T>,
        isShaped: (T) -> Boolean,
    ): List<Pair<T, Int>> {
        val shaped = ArrayList<Pair<T, Int>>()
        var segment = -1
        var previousShaped = false
        for (knot in knots) {
            val knotShaped = isShaped(knot)
            if (knotShaped && !previousShaped) segment++
            if (knotShaped) shaped += knot to segment
            previousShaped = knotShaped
        }
        return shaped
    }

    /** One band knot per shaped knot; null is a numeric failure. */
    private fun buildPredictiveBand(
        shapedKnots: List<Pair<CentralKnot, Int>>,
        appliedRoutes: List<PkCalibrationRoute>,
        promotedBetaCovariance: PkCalibrationPromotedCovariance?,
        rLog: Double,
    ): List<PkPredictiveBandKnot>? {
        val covariance = promotedBetaCovariance ?: return null
        val lawCache = HashMap<Long, List<Double>>()
        val result = ArrayList<PkPredictiveBandKnot>()
        for ((knot, segment) in shapedKnots) {
            val median = knot.totalDrugPgml
            if (!median.isFinite() || median <= 0.0) return null
            val effectiveVariance = PkPredictiveBandMath.effectiveVariance(
                totalPgml = median,
                contributionByRoutePgml = knot.byRouteDrugPgml,
                covariance = covariance,
                promotedRoutes = appliedRoutes,
            ) ?: return null
            // ponytail: the law is solved per distinct variance. Along a
            // multi-route curve the promoted share, and so the variance, differs
            // at nearly every knot; three significant digits gave one solve per
            // knot. The law depends on variance + rLog, so that sum is put on a
            // geometric grid (0.5% steps, <0.25% in SD, the same accuracy as
            // before): a few hundred solves at most for any knot count, and
            // knots where the calibrated share is small all share one law.
            val quantizedVariance = quantizeVariance(effectiveVariance, rLog)
            val law = lawCache.getOrPut(quantizedVariance.toBits()) {
                PkPredictiveBandMath.logQuantiles(quantizedVariance, rLog) ?: return null
            }
            val logMedian = ln(median)
            val q = law.map { offset -> exp(logMedian + offset) }
            if (q.any { value -> !value.isFinite() || value <= 0.0 }) return null
            result += PkPredictiveBandKnot(knot.epochMillis, q[0], q[1], q[2], q[3], q[4], segment)
        }
        return result.takeIf(List<PkPredictiveBandKnot>::isNotEmpty)
    }
}

private fun quantizeVariance(variance: Double, rLog: Double): Double {
    if (variance <= 0.0) return 0.0
    val step = ln(VARIANCE_GRID_RATIO)
    val total = exp((ln(variance + rLog) / step).roundToLong() * step)
    return (total - rLog).coerceAtLeast(0.0)
}

private const val VARIANCE_GRID_RATIO = 1.005

private data class CentralKnot(
    val epochMillis: Long,
    /** Contributions with the applied route scales, summing to [totalDrugPgml]. */
    val byRouteDrugPgml: Map<PkCalibrationRoute, Double>,
    val totalDrugPgml: Double,
) {
    /** The route is at least the support share of the drawn signal here. */
    fun isShapedBy(route: PkCalibrationRoute): Boolean =
        totalDrugPgml > 0.0 &&
            byRouteDrugPgml.getValue(route) >=
                PkCalibrationDefaults.PROMOTION_SUPPORT_SHARE_MIN * totalDrugPgml
}

/** v9 delta-Laplace aggregate observation-predictive law. */
internal object PkPredictiveBandMath {
    private val probabilities = listOf(0.025, 0.158655254, 0.5, 0.841344746, 0.975)
    private val studentT = TDistribution(PkCalibrationDefaults.STUDENT_T_NU)
    internal val hermiteRule by lazy { hermiteRule(PkCalibrationDefaults.BAND_GH_NODES) }

    /** Five symmetric log-offsets from the median, in [probabilities] order. */
    fun logQuantiles(varianceBeta: Double, rLog: Double): List<Double>? {
        if (!varianceBeta.isFinite() || varianceBeta < 0.0) return null
        if (!rLog.isFinite() || rLog <= 0.0) return null
        val rule = runCatching { hermiteRule }.getOrNull() ?: return null
        // The centered Gaussian + centered Student-t law is exactly symmetric:
        // solve the two upper quantiles and reflect.
        val upperOneSigma = solveLogQuantile(probabilities[3], varianceBeta, rLog, rule)
            ?: return null
        val upperNinetyFive = solveLogQuantile(probabilities[4], varianceBeta, rLog, rule)
            ?: return null
        return listOf(-upperNinetyFive, -upperOneSigma, 0.0, upperOneSigma, upperNinetyFive)
            .takeIf { it.zipWithNext().none { (left, right) -> left > right } }
    }

    /**
     * Delta aggregation V_eff = alpha' Sigma alpha over the
     * promoted block, alpha_r = d log m / d beta_r. A tiny negative from
     * floating-point accumulation clamps to zero.
     */
    internal fun effectiveVariance(
        totalPgml: Double,
        contributionByRoutePgml: Map<PkCalibrationRoute, Double>,
        covariance: PkCalibrationPromotedCovariance,
        promotedRoutes: Collection<PkCalibrationRoute>,
    ): Double? {
        if (!totalPgml.isFinite() || totalPgml <= 0.0) return null
        val orderedRoutes = PkCalibrationRoute.entries.filter(promotedRoutes::contains)
        val alphaByRoute = orderedRoutes.associateWith { route ->
            val contribution = contributionByRoutePgml[route]
                ?.takeIf { value -> value.isFinite() && value >= 0.0 }
                ?: return null
            contribution / totalPgml
        }
        var aggregate = 0.0
        for (row in orderedRoutes) {
            for (column in orderedRoutes) {
                val entry = covariance.covariance(row, column) ?: return null
                aggregate += alphaByRoute.getValue(row) * alphaByRoute.getValue(column) * entry
                if (!aggregate.isFinite()) return null
            }
        }
        if (aggregate < 0.0) {
            aggregate = if (aggregate >= -NEGATIVE_VARIANCE_CLAMP_TOL) 0.0 else return null
        }
        return aggregate.normalizePositiveZero()
    }

    private const val NEGATIVE_VARIANCE_CLAMP_TOL = 1e-12

    private fun solveLogQuantile(
        probability: Double,
        varianceBeta: Double,
        rLog: Double,
        rule: PkHermiteRule,
    ): Double? {
        val sqrtTwoVariance = sqrt(2.0 * varianceBeta)
        val sqrtObservationVariance = sqrt(rLog)
        var evaluations = 0
        fun cdf(logValue: Double): Double? {
            if (!logValue.isFinite() || evaluations >= PkCalibrationDefaults.BAND_ROOT_MAX_EVAL) {
                return null
            }
            evaluations += 1
            var total = 0.0
            for (index in rule.nodes.indices) {
                val eta = sqrtTwoVariance * rule.nodes[index]
                val standardized = (logValue - eta) / sqrtObservationVariance
                val component = studentTCdf(standardized) ?: return null
                total += rule.weights[index] * component
            }
            return total.takeIf { value -> value in 0.0..1.0 }
        }

        var halfWidth = PkCalibrationDefaults.BAND_ROOT_INITIAL_HALF_WIDTH_LOG
        while (true) {
            val lowerCdf = cdf(-halfWidth) ?: return null
            val upperCdf = cdf(halfWidth) ?: return null
            if (lowerCdf <= probability && upperCdf >= probability) break
            if (halfWidth >= PkCalibrationDefaults.BAND_ROOT_MAX_HALF_WIDTH_LOG) return null
            halfWidth = min(
                halfWidth * 2.0,
                PkCalibrationDefaults.BAND_ROOT_MAX_HALF_WIDTH_LOG,
            )
        }

        // Brent: the CDF is smooth and monotone, so it converges in a handful
        // of evaluations where bisection needed ~30 (each one a 32-node sum
        // of Student-t CDFs). The final CDF check below is unchanged.
        val quantile = runCatching {
            BrentSolver(PkCalibrationDefaults.BAND_ROOT_X_ABS_TOL).solve(
                PkCalibrationDefaults.BAND_ROOT_MAX_EVAL - evaluations - 1,
                UnivariateFunction { logValue -> (cdf(logValue) ?: Double.NaN) - probability },
                -halfWidth,
                halfWidth,
            )
        }.getOrNull()?.takeIf(Double::isFinite) ?: return null
        val finalCdf = cdf(quantile) ?: return null
        return quantile.takeIf {
            abs(finalCdf - probability) <= PkCalibrationDefaults.BAND_ROOT_CDF_TOL
        }
    }

    /**
     * Student-t CDF at the fixed nu. For nu = 4 the closed form
     * F(t) = 1/2 + sin(theta)/2 * (1 + cos^2(theta)/2), with
     * sin(theta) = t / sqrt(4 + t^2), replaces the regularized-beta library
     * call that dominated every quadrature node; other nu fall back to it.
     */
    internal fun studentTCdf(x: Double): Double? {
        if (!x.isFinite()) return null
        if (PkCalibrationDefaults.STUDENT_T_NU != 4.0) {
            return runCatching { studentT.cumulativeProbability(x) }.getOrNull()
        }
        val denominator = 4.0 + x * x
        val sine = x / sqrt(denominator)
        val cosineSquared = 4.0 / denominator
        return (0.5 + 0.5 * sine * (1.0 + 0.5 * cosineSquared)).coerceIn(0.0, 1.0)
    }

    private fun hermiteRule(nodeCount: Int): PkHermiteRule {
        val integrator = GaussIntegratorFactory().hermite(nodeCount)
        val pointsAndWeights = (0 until integrator.numberOfPoints)
            .map { index -> integrator.getPoint(index) to integrator.getWeight(index) / sqrt(PI) }
            .sortedBy(Pair<Double, Double>::first)
        return PkHermiteRule(
            nodes = pointsAndWeights.map { it.first },
            weights = pointsAndWeights.map { it.second },
        )
    }
}

internal data class PkHermiteRule(
    val nodes: List<Double>,
    val weights: List<Double>,
)
