package com.mkx.hrttracker.model.pk

import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.integration.gauss.GaussIntegratorFactory
import org.hipparchus.analysis.solvers.BisectionSolver
import org.hipparchus.distribution.continuous.TDistribution
import java.util.TreeSet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Pure central-curve and predictive-band rendering for one exact chart domain. */
object PkCalibrationRenderer {
    /**
     * [doseEvents] defaults to the fitted history; Home passes logged plus
     * planned doses so the band follows the same projected curve the chart
     * draws. The fit (and its covariance) is unaffected either way.
     */
    fun render(
        evaluation: PkCalibrationEvaluation,
        domain: PkChartDomain,
        doseEvents: List<PkDoseEvent> = evaluation.evidence?.input?.doseEvents.orEmpty(),
    ): PkCalibrationRenderResult? {
        val evidence = evaluation.evidence ?: return null
        val result = evaluation.result
        val input = evidence.input
        val forwardModel = if (doseEvents === input.doseEvents) {
            evidence.forwardModel
        } else {
            PkE2ForwardModel.create(doseEvents, input.weightKg) ?: return NumericUnavailable
        }

        val knots = TreeSet(domain.knotEpochMillis)
        for (event in doseEvents) {
            val epochMillis = input.originEpochMillis +
                    (event.timeH * MILLIS_PER_HOUR).roundToLong()
            if (epochMillis in domain.rangeStartEpochMillis..domain.rangeEndEpochMillis) {
                knots += epochMillis
            }
        }

        val population = ArrayList<Pair<Long, PkForwardBreakdown>>(knots.size)
        for (epochMillis in knots) {
            val breakdown = forwardModel.breakdownAt(
                epochDifferenceHours(epochMillis, input.originEpochMillis)
            ) ?: return NumericUnavailable
            population += epochMillis to breakdown
        }

        // Only supported promoted routes are applied (see displayParams); one
        // with zero contribution throughout this range is absent from
        // render-level promotion (the hero names only routes that shape the
        // visible curve).
        val effectivePromotedRoutes = result.supportedPromotedRoutes.filter { route ->
            population.any { (_, breakdown) -> breakdown.byRouteDrugPgml.getValue(route) > 0.0 }
        }
        val effectiveParams = PkPersonalParams.create(
            effectivePromotedRoutes.associateWith(result.displayParams::logScaleFor)
        ) ?: return NumericUnavailable

        val centralKnots = ArrayList<CentralKnot>(population.size)
        for ((epochMillis, breakdown) in population) {
            val contributions = PkCalibrationRoute.entries.associateWith { route ->
                breakdown.byRouteDrugPgml.getValue(route) * effectiveParams.scaleFor(route)
            }
            val total = contributions.values.sum()
            if (!total.isFinite()) return NumericUnavailable
            centralKnots += CentralKnot(epochMillis, contributions, total)
        }
        val centralCurve = centralKnots.map { knot -> PkCurvePoint(knot.epochMillis, knot.totalDrugPgml) }

        if (effectivePromotedRoutes.isEmpty()) {
            return PkCalibrationRenderResult(
                renderState = PkCalibrationRenderState.POPULATION,
                centralCurve = centralCurve,
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            )
        }

        val bandKnots = buildPredictiveBand(
            centralKnots = centralKnots,
            effectivePromotedRoutes = effectivePromotedRoutes,
            promotedBetaCovariance = result.promotedBetaCovariance,
            rLog = input.config.rLog,
        )
        return PkCalibrationRenderResult(
            renderState = PkCalibrationRenderState.PERSONALIZED,
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

    private fun buildPredictiveBand(
        centralKnots: List<CentralKnot>,
        effectivePromotedRoutes: List<PkCalibrationRoute>,
        promotedBetaCovariance: PkCalibrationPromotedCovariance?,
        rLog: Double,
    ): List<PkPredictiveBandKnot>? {
        val covariance = promotedBetaCovariance ?: return null
        val lawCache = HashMap<Long, List<Double>>()
        val result = ArrayList<PkPredictiveBandKnot>()
        for (knot in centralKnots) {
            val hasPromotedContribution = effectivePromotedRoutes.any { route ->
                knot.byRouteDrugPgml.getValue(route) > 0.0
            }
            if (!hasPromotedContribution) continue
            val median = knot.totalDrugPgml
            if (!median.isFinite() || median <= 0.0) return null
            val effectiveVariance = PkPredictiveBandMath.effectiveVariance(
                totalPgml = median,
                contributionByRoutePgml = knot.byRouteDrugPgml,
                covariance = covariance,
                promotedRoutes = effectivePromotedRoutes,
            ) ?: return null
            // ponytail: the law is solved per distinct variance; quantizing to
            // three significant digits (<0.5% variance error, invisible at
            // chart scale) keeps dense multi-route knot grids to a few solves.
            val quantizedVariance = quantizeVariance(effectiveVariance)
            val law = lawCache.getOrPut(quantizedVariance.toBits()) {
                PkPredictiveBandMath.logQuantiles(quantizedVariance, rLog) ?: return null
            }
            val logMedian = ln(median)
            val q = law.map { offset -> exp(logMedian + offset) }
            if (q.any { value -> !value.isFinite() || value <= 0.0 }) return null
            result += PkPredictiveBandKnot(knot.epochMillis, q[0], q[1], q[2], q[3], q[4])
        }
        return result.takeIf(List<PkPredictiveBandKnot>::isNotEmpty)
    }
}

private fun quantizeVariance(variance: Double): Double {
    if (variance <= 0.0) return 0.0
    val scale = 10.0.pow(floor(log10(variance)) - 2)
    return (variance / scale).roundToLong() * scale
}

private data class CentralKnot(
    val epochMillis: Long,
    val byRouteDrugPgml: Map<PkCalibrationRoute, Double>,
    val totalDrugPgml: Double,
)

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
                val component = runCatching {
                    studentT.cumulativeProbability(standardized)
                }.getOrNull() ?: return null
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

        val quantile = runCatching {
            BisectionSolver(PkCalibrationDefaults.BAND_ROOT_X_ABS_TOL).solve(
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
