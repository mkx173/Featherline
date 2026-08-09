package com.mkx.hrttracker.model.pk

import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.integration.gauss.GaussIntegratorFactory
import org.hipparchus.analysis.solvers.BisectionSolver
import org.hipparchus.distribution.continuous.TDistribution
import java.util.TreeSet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

private const val PK_CALIBRATION_FORWARD_RENDER_SNAPSHOT_DIGEST_SCHEMA =
    "hrttracker.calibration-forward-render-snapshot/v1"
private const val PK_CALIBRATION_RENDER_ARTIFACT_VERSION =
    "hrttracker.calibration-render/v1"
private const val MILLIS_PER_HOUR = 3_600_000.0

/** Pure central-curve and predictive-band rendering for one exact chart domain. */
object PkCalibrationRenderer {
    fun render(
        evaluation: PkCalibrationEngineEvaluation,
        domain: PkChartDomain,
    ): PkCalibrationRenderResult? {
        val evidence = evaluation.readyEvidence ?: return null
        val calibrationResult = evaluation.result
        val input = evidence.canonicalInput
        val knotEpochMillis = exactRenderKnots(domain, input)
        val domainDigest = renderDomainDigest(
            domain = domain,
            knotEpochMillis = knotEpochMillis,
            input = input,
        )
        if (knotEpochMillis.size > MaxChartDomainKnots) {
            return numericUnavailable(domainDigest)
        }

        val weightKg = input.resolvedCurrentWeightKg
            .takeIf { value -> value.isFinite() && value > 0.0 }
            ?: return numericUnavailable(domainDigest)
        if (!input.hasConsistentForwardTimes()) return numericUnavailable(domainDigest)
        val forwardModel = PkE2ForwardModel.create(
            events = input.medicationEvents.canonicalForwardOrder()
                .map { source -> source.event },
            bodyWeightKg = weightKg,
        ) ?: return numericUnavailable(domainDigest)

        val populationKnots = ArrayList<PopulationKnot>(knotEpochMillis.size)
        for (epochMillis in knotEpochMillis) {
            val timeH = epochDifferenceHours(
                epochMillis = epochMillis,
                originEpochMillis = input.forwardTimeOriginEpochMillis,
            ) ?: return numericUnavailable(domainDigest)
            val population = forwardModel.breakdownAt(timeH)
                ?: return numericUnavailable(domainDigest)
            populationKnots += PopulationKnot(epochMillis, population)
        }

        val routeRenderFallbacks = ArrayList<PkCalibrationRoute>()
        val effectivePromotedRoutes = ArrayList<PkCalibrationRoute>()
        for (route in calibrationResult.promotedRoutes) {
            val scale = calibrationResult.displayParams.scaleFor(route)
            var hasPositiveContribution = false
            var routeFailed = false
            for (knot in populationKnots) {
                val populationContribution = knot.population.byRouteDrugPgml.getValue(route)
                val scaledContribution = populationContribution * scale
                if (!scaledContribution.isFinite() || scaledContribution < 0.0 ||
                    (populationContribution > 0.0 && scaledContribution == 0.0)
                ) {
                    routeFailed = true
                    break
                }
                if (scaledContribution > 0.0) hasPositiveContribution = true
            }
            when {
                routeFailed -> routeRenderFallbacks += route
                hasPositiveContribution -> effectivePromotedRoutes += route
                // A fit-promoted route with zero contribution throughout this exact range is
                // absent from render-level promotion, not reported as a numeric fallback.
            }
        }

        val effectiveBetas = linkedMapOf<PkCalibrationRoute, Double>()
        for (route in effectivePromotedRoutes) {
            calibrationResult.displayParams.routeLogScale[route]?.let { beta ->
                effectiveBetas[route] = beta
            }
        }
        val effectiveParams = PkPersonalParams.create(effectiveBetas)
            ?: return numericUnavailable(domainDigest)

        val centralKnots = ArrayList<CentralKnot>(populationKnots.size)
        val centralCurve = ArrayList<PkCurvePoint>(populationKnots.size)
        for (knot in populationKnots) {
            val effectiveContributions = linkedMapOf<PkCalibrationRoute, Double>()
            var total = 0.0
            for (route in PkCalibrationRoute.entries) {
                val populationContribution = knot.population.byRouteDrugPgml.getValue(route)
                val contribution = if (route in effectivePromotedRoutes) {
                    populationContribution * effectiveParams.scaleFor(route)
                } else {
                    populationContribution
                }
                if (!contribution.isFinite() || contribution < 0.0 ||
                    (populationContribution > 0.0 && contribution == 0.0)
                ) {
                    return numericUnavailable(domainDigest)
                }
                effectiveContributions[route] = contribution.normalizePositiveZero()
                total += contribution
                if (!total.isFinite() || total < 0.0) {
                    return numericUnavailable(domainDigest)
                }
            }
            val normalizedTotal = total.normalizePositiveZero()
            centralKnots += CentralKnot(
                epochMillis = knot.epochMillis,
                byRouteDrugPgml = effectiveContributions,
                totalDrugPgml = normalizedTotal,
            )
            centralCurve += requireNotNull(
                PkCurvePoint.create(knot.epochMillis, normalizedTotal)
            )
        }

        if (effectivePromotedRoutes.isEmpty()) {
            return requireNotNull(
                PkCalibrationRenderResult.create(
                    domainDigest = domainDigest,
                    renderState = PkCalibrationRenderState.POPULATION,
                    effectiveDisplayParams = PkPersonalParams.population(),
                    routeRenderFallbacks = routeRenderFallbacks,
                    centralCurve = centralCurve,
                    bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
                )
            )
        }

        val bandKnots = buildPredictiveBand(
            centralKnots = centralKnots,
            effectivePromotedRoutes = effectivePromotedRoutes,
            promotedBetaCovariance = calibrationResult.promotedBetaCovariance,
            rLog = input.config.rLog,
        )
        val bandState = if (bandKnots == null) {
            PkCalibrationBandState.NUMERIC_UNAVAILABLE
        } else {
            PkCalibrationBandState.READY
        }
        return requireNotNull(
            PkCalibrationRenderResult.create(
                domainDigest = domainDigest,
                renderState = PkCalibrationRenderState.PERSONALIZED,
                effectivePromotedRoutes = effectivePromotedRoutes,
                effectiveDisplayParams = effectiveParams,
                routeRenderFallbacks = routeRenderFallbacks,
                centralCurve = centralCurve,
                bandState = bandState,
                bandReasons = if (bandState == PkCalibrationBandState.NUMERIC_UNAVAILABLE) {
                    setOf(PkCalibrationReason.BAND_NUMERIC_FAILURE)
                } else {
                    emptySet()
                },
                bandKnots = bandKnots.orEmpty(),
            )
        )
    }

    private fun buildPredictiveBand(
        centralKnots: List<CentralKnot>,
        effectivePromotedRoutes: List<PkCalibrationRoute>,
        promotedBetaCovariance: PkCalibrationPromotedCovariance?,
        rLog: Double?,
    ): List<PkPredictiveBandKnot>? {
        val observationVariance = rLog
            ?.takeIf { value -> value.isFinite() && value > 0.0 }
            ?: return null
        val covariance = promotedBetaCovariance ?: return null
        if (effectivePromotedRoutes.any { route -> route !in covariance.routes }) {
            return null
        }

        val lawCache = HashMap<BandLawKey, BandLogQuantiles>()
        val result = ArrayList<PkPredictiveBandKnot>()
        for (knot in centralKnots) {
            val hasPositivePromotedContribution = effectivePromotedRoutes.any { route ->
                knot.byRouteDrugPgml.getValue(route) > 0.0
            }
            if (!hasPositivePromotedContribution) continue
            val median = knot.totalDrugPgml
            if (!median.isFinite() || median <= 0.0) return null

            val effectiveVariance = PkPredictiveBandMath.effectiveVariance(
                totalPgml = median,
                contributionByRoutePgml = knot.byRouteDrugPgml,
                covariance = covariance,
                promotedRoutes = effectivePromotedRoutes,
            ) ?: return null
            val lawKey = BandLawKey(effectiveVariance.toBits(), observationVariance.toBits())
            val law = lawCache[lawKey] ?: PkPredictiveBandMath.logQuantiles(
                varianceBeta = effectiveVariance,
                rLog = observationVariance,
            )?.also { quantiles -> lawCache[lawKey] = quantiles } ?: return null
            val quantiles = law.validatedRawQuantiles(median) ?: return null
            result += PkPredictiveBandKnot.create(
                epochMillis = knot.epochMillis,
                p025Pgml = quantiles[0],
                p158655254Pgml = quantiles[1],
                p50Pgml = quantiles[2],
                p841344746Pgml = quantiles[3],
                p975Pgml = quantiles[4],
            ) ?: return null
        }
        return result.takeIf(List<PkPredictiveBandKnot>::isNotEmpty)
    }
}

private data class PopulationKnot(
    val epochMillis: Long,
    val population: PkForwardBreakdown,
)

private data class CentralKnot(
    val epochMillis: Long,
    val byRouteDrugPgml: Map<PkCalibrationRoute, Double>,
    val totalDrugPgml: Double,
)

private data class BandLawKey(
    val varianceBits: Long,
    val rLogBits: Long,
)

internal data class BandLogQuantiles(
    val gh16: List<Double>,
    val gh32: List<Double>,
) {
    fun validatedRawQuantiles(medianPgml: Double): List<Double>? {
        if (!medianPgml.isFinite() || medianPgml <= 0.0) return null
        val logMedian = ln(medianPgml)
        if (!logMedian.isFinite()) return null
        val q16 = gh16.map { offset -> exp(logMedian + offset) }
        val q32 = gh32.map { offset -> exp(logMedian + offset) }
        if (q16.any { value -> !value.isFinite() || value <= 0.0 } ||
            q32.any { value -> !value.isFinite() || value <= 0.0 }
        ) {
            return null
        }
        if (!q16.isOrdered() || !q32.isOrdered()) return null
        for (index in q16.indices) {
            if (!bandQuadratureConverges(q16[index], q32[index])) return null
        }
        return q16
    }
}

/** v9 delta-Laplace aggregate observation-predictive law. */
internal object PkPredictiveBandMath {
    private val probabilities = listOf(
        0.025,
        0.158655254,
        0.5,
        0.841344746,
        0.975,
    )
    private val studentT = TDistribution(PkCalibrationDefaults.STUDENT_T_NU)
    private val gh16 by lazy { hermiteRule(PkCalibrationDefaults.BAND_GH_NODES) }
    private val gh32 by lazy {
        hermiteRule(PkCalibrationDefaults.BAND_GH_REFINEMENT_NODES)
    }

    fun logQuantiles(
        varianceBeta: Double,
        rLog: Double,
    ): BandLogQuantiles? {
        if (!varianceBeta.isFinite() || varianceBeta < 0.0) return null
        if (!rLog.isFinite() || rLog <= 0.0) return null
        val rule16 = runCatching { gh16 }.getOrNull() ?: return null
        val rule32 = runCatching { gh32 }.getOrNull() ?: return null
        val q16 = symmetricLogQuantiles(varianceBeta, rLog, rule16) ?: return null
        val q32 = symmetricLogQuantiles(varianceBeta, rLog, rule32) ?: return null
        if (!q16.isOrdered() || !q32.isOrdered()) return null
        return BandLogQuantiles(q16, q32)
    }

    /**
     * v10.0 §A10.5 delta aggregation V_eff = alpha' Sigma alpha over the
     * promoted block. The alphas are exactly d log m / d beta_r, so this is
     * the v9 delta expansion with the joint covariance replacing the
     * independence assumption. The exact quadratic form is non-negative;
     * a tiny negative from floating-point accumulation clamps to zero.
     */
    internal fun effectiveVariance(
        totalPgml: Double,
        contributionByRoutePgml: Map<PkCalibrationRoute, Double>,
        covariance: PkCalibrationPromotedCovariance,
        promotedRoutes: Collection<PkCalibrationRoute>,
    ): Double? {
        if (!totalPgml.isFinite() || totalPgml <= 0.0) return null
        val orderedRoutes = PkCalibrationRoute.entries.filter(promotedRoutes::contains)
        val alphaByRoute = linkedMapOf<PkCalibrationRoute, Double>()
        for (route in orderedRoutes) {
            val contribution = contributionByRoutePgml[route]
                ?.takeIf { value -> value.isFinite() && value >= 0.0 }
                ?: return null
            val alpha = contribution / totalPgml
            if (!alpha.isFinite() || alpha < 0.0) return null
            alphaByRoute[route] = alpha
        }
        var aggregate = 0.0
        for (row in orderedRoutes) {
            for (column in orderedRoutes) {
                val entry = covariance.covariance(row, column) ?: return null
                val term = alphaByRoute.getValue(row) * alphaByRoute.getValue(column) * entry
                if (!term.isFinite()) return null
                aggregate += term
                if (!aggregate.isFinite()) return null
            }
        }
        if (aggregate < 0.0) {
            aggregate = if (aggregate >= -NEGATIVE_VARIANCE_CLAMP_TOL) 0.0 else return null
        }
        return aggregate.normalizePositiveZero()
    }

    private const val NEGATIVE_VARIANCE_CLAMP_TOL = 1e-12

    /** Read-only numerical seam for independent fixed-vector parity tests. */
    internal fun hermiteRuleForValidation(nodeCount: Int): PkHermiteRule? {
        return when (nodeCount) {
            PkCalibrationDefaults.BAND_GH_NODES -> runCatching { gh16 }.getOrNull()
            PkCalibrationDefaults.BAND_GH_REFINEMENT_NODES ->
                runCatching { gh32 }.getOrNull()
            else -> null
        }
    }

    private fun symmetricLogQuantiles(
        varianceBeta: Double,
        rLog: Double,
        rule: PkHermiteRule,
    ): List<Double>? {
        // The centered Gaussian + centered Student-t law is exactly symmetric. Solving the
        // two upper quantiles and reflecting them avoids redundant CDF work without changing
        // the specified predictive distribution or its numerical tolerances.
        val upperOneSigma = solveLogQuantile(
            probabilities[3],
            varianceBeta,
            rLog,
            rule,
        ) ?: return null
        val upperNinetyFive = solveLogQuantile(
            probabilities[4],
            varianceBeta,
            rLog,
            rule,
        ) ?: return null
        return listOf(
            -upperNinetyFive,
            -upperOneSigma,
            0.0,
            upperOneSigma,
            upperNinetyFive,
        )
    }

    private fun solveLogQuantile(
        probability: Double,
        varianceBeta: Double,
        rLog: Double,
        rule: PkHermiteRule,
    ): Double? {
        val sqrtTwoVariance = sqrt(2.0 * varianceBeta)
        val sqrtObservationVariance = sqrt(rLog)
        if (!sqrtTwoVariance.isFinite() || !sqrtObservationVariance.isFinite() ||
            sqrtObservationVariance <= 0.0
        ) {
            return null
        }
        val evaluationBudget = EvaluationBudget()
        fun cdf(logValue: Double): Double? {
            if (!logValue.isFinite() || !evaluationBudget.take()) return null
            var total = 0.0
            for (index in rule.nodes.indices) {
                val eta = sqrtTwoVariance * rule.nodes[index]
                if (!eta.isFinite()) return null
                val standardized = (logValue - eta) / sqrtObservationVariance
                val component = runCatching {
                    studentT.cumulativeProbability(standardized)
                }.getOrNull() ?: return null
                if (!component.isFinite() || component !in 0.0..1.0) return null
                total += rule.weights[index] * component
                if (!total.isFinite()) return null
            }
            return total.takeIf { value -> value in 0.0..1.0 }
        }

        var halfWidth = PkCalibrationDefaults.BAND_ROOT_INITIAL_HALF_WIDTH_LOG
        var lower: Double
        var upper: Double
        var lowerCdf: Double
        var upperCdf: Double
        while (true) {
            lower = -halfWidth
            upper = halfWidth
            lowerCdf = cdf(lower) ?: return null
            upperCdf = cdf(upper) ?: return null
            if (lowerCdf <= probability && upperCdf >= probability) break
            if (halfWidth >= PkCalibrationDefaults.BAND_ROOT_MAX_HALF_WIDTH_LOG) return null
            halfWidth = min(
                halfWidth * PkCalibrationDefaults.BAND_ROOT_BRACKET_EXPANSION,
                PkCalibrationDefaults.BAND_ROOT_MAX_HALF_WIDTH_LOG,
            )
        }

        val lowerResidual = lowerCdf - probability
        val upperResidual = upperCdf - probability
        if (!lowerResidual.isFinite() || !upperResidual.isFinite() ||
            lowerResidual > 0.0 || upperResidual < 0.0
        ) {
            return null
        }

        // Application code owns the adaptive monotone bracket, the shared evaluation cap,
        // and the CDF-residual gate. The pinned Hipparchus boundary owns only bracketed
        // coordinate refinement. Reserve one CDF evaluation for the required residual check.
        val solverMaxEval = evaluationBudget.remainingCount() - 1
        if (solverMaxEval <= 0) return null
        val quantile = runCatching {
            BisectionSolver(PkCalibrationDefaults.BAND_ROOT_X_ABS_TOL).solve(
                solverMaxEval,
                UnivariateFunction { logValue ->
                    val value = cdf(logValue)
                    if (value == null) Double.NaN else value - probability
                },
                lower,
                upper,
            )
        }.getOrNull()?.takeIf(Double::isFinite) ?: return null
        if (quantile !in lower..upper) return null
        val finalCdf = cdf(quantile) ?: return null
        val finalResidual = abs(finalCdf - probability)
        return quantile.takeIf {
            finalResidual.isFinite() &&
                    finalResidual <= PkCalibrationDefaults.BAND_ROOT_CDF_TOL
        }
    }

    private fun hermiteRule(nodeCount: Int): PkHermiteRule {
        // Hipparchus 4.0.3 core API: hermite(n), getPoint(i), and getWeight(i).
        val integrator = GaussIntegratorFactory().hermite(nodeCount)
        val pointsAndWeights = (0 until integrator.numberOfPoints)
            .map { index -> integrator.getPoint(index) to integrator.getWeight(index) }
            .sortedBy(Pair<Double, Double>::first)
        val nodes = ArrayList<Double>(nodeCount)
        val weights = ArrayList<Double>(nodeCount)
        for ((point, rawWeight) in pointsAndWeights) {
            val normalizedWeight = rawWeight / sqrt(PI)
            require(point.isFinite())
            require(normalizedWeight.isFinite() && normalizedWeight > 0.0)
            nodes += point
            weights += normalizedWeight
        }
        require(nodes.size == nodeCount)
        return PkHermiteRule(nodes, weights)
    }
}

internal data class PkHermiteRule(
    val nodes: List<Double>,
    val weights: List<Double>,
)

internal fun bandQuadratureConverges(gh16Raw: Double, gh32Raw: Double): Boolean {
    if (!gh16Raw.isFinite() || !gh32Raw.isFinite()) return false
    val error = abs(gh16Raw - gh32Raw)
    val tolerance = PkCalibrationDefaults.BAND_VALIDATE_ABS_PGML +
            PkCalibrationDefaults.BAND_VALIDATE_REL * abs(gh32Raw)
    return error.isFinite() && error <= tolerance
}

private class EvaluationBudget {
    private var count = 0

    fun take(): Boolean {
        if (count >= PkCalibrationDefaults.BAND_ROOT_MAX_EVAL) return false
        count += 1
        return true
    }

    fun remainingCount(): Int = PkCalibrationDefaults.BAND_ROOT_MAX_EVAL - count
}

private fun exactRenderKnots(
    domain: PkChartDomain,
    input: PkCalibrationCanonicalInputSnapshot,
): List<Long> {
    val knots = TreeSet<Long>()
    knots += domain.knotEpochMillis
    for (source in input.medicationEvents) {
        if (source.epochMillis in domain.rangeStartEpochMillis..domain.rangeEndEpochMillis) {
            knots += source.epochMillis
        }
    }
    return knots.toList()
}

private fun renderDomainDigest(
    domain: PkChartDomain,
    knotEpochMillis: List<Long>,
    input: PkCalibrationCanonicalInputSnapshot,
): CanonicalDigest {
    val forwardSnapshotDigest = canonicalDigest(
        schema = PK_CALIBRATION_FORWARD_RENDER_SNAPSHOT_DIGEST_SCHEMA,
        payload = linkedMapOf(
            "schema" to PK_CALIBRATION_FORWARD_RENDER_SNAPSHOT_DIGEST_SCHEMA,
            "forwardTimeOriginEpochMillis" to input.forwardTimeOriginEpochMillis.toString(),
            "resolvedCurrentWeightBits" to input.resolvedCurrentWeightKg.rawBinary64Bits(),
            "forwardModelVersion" to input.forwardModelVersion,
            "medicationEvents" to input.medicationEvents
                .canonicalForwardOrder()
                .map(PkCalibrationMedicationEventSource::forwardRenderPayload),
        ),
    )
    return canonicalDigest(
        schema = PK_CALIBRATION_RENDER_DOMAIN_DIGEST_SCHEMA,
        payload = linkedMapOf(
            "schema" to PK_CALIBRATION_RENDER_DOMAIN_DIGEST_SCHEMA,
            "renderArtifactVersion" to PK_CALIBRATION_RENDER_ARTIFACT_VERSION,
            "chartGridVersion" to domain.chartGridVersion,
            "rangeStartEpochMillis" to domain.rangeStartEpochMillis,
            "rangeEndEpochMillis" to domain.rangeEndEpochMillis,
            "projectionEndEpochMillis" to domain.projectionEndEpochMillis,
            "samplingIntervalMillis" to domain.samplingIntervalMillis,
            "knotEpochMillis" to knotEpochMillis,
            "forwardSnapshotDigest" to linkedMapOf(
                "schema" to forwardSnapshotDigest.schema,
                "algorithm" to forwardSnapshotDigest.algorithm,
                "hexLower" to forwardSnapshotDigest.hexLower,
            ),
            "calibrationModelVersion" to input.calibrationModelVersion,
        ),
    )
}

private fun PkCalibrationMedicationEventSource.forwardRenderPayload(): Map<String, Any?> {
    return linkedMapOf(
        "epochMillis" to epochMillis,
        "eventTypeId" to eventTypeId,
        "eventUuid" to event.id.toString().lowercase(),
        "sourceGroupUuid" to event.sourceGroupUuid?.toString()?.lowercase(),
        "hormoneId" to hormoneId,
        "routeId" to routeId,
        "compoundId" to compoundId,
        "timeHoursBits" to event.timeH.rawBinary64Bits(),
        "doseMgBits" to event.doseMg.rawBinary64Bits(),
        "releaseRateMcgPerDayBits" to event.releaseRateMcgPerDay?.rawBinary64Bits(),
        "areaCm2Bits" to event.areaCm2?.rawBinary64Bits(),
        "sublingualThetaBits" to event.sublingualTheta?.rawBinary64Bits(),
    )
}

private fun List<PkCalibrationMedicationEventSource>.canonicalForwardOrder():
        List<PkCalibrationMedicationEventSource> {
    return sortedWith(
        compareBy<PkCalibrationMedicationEventSource>(
            PkCalibrationMedicationEventSource::epochMillis,
            PkCalibrationMedicationEventSource::eventTypeId,
        ).thenBy { source -> source.event.id.toString().lowercase() }
    )
}

private fun PkCalibrationCanonicalInputSnapshot.hasConsistentForwardTimes(): Boolean {
    return medicationEvents.all { source ->
        val expected = epochDifferenceHours(
            epochMillis = source.epochMillis,
            originEpochMillis = forwardTimeOriginEpochMillis,
        ) ?: return@all false
        expected.normalizePositiveZero().toBits() ==
                source.event.timeH.normalizePositiveZero().toBits()
    }
}

private fun epochDifferenceHours(epochMillis: Long, originEpochMillis: Long): Double? {
    val difference = runCatching { Math.subtractExact(epochMillis, originEpochMillis) }
        .getOrNull() ?: return null
    return (difference / MILLIS_PER_HOUR).takeIf(Double::isFinite)
}

private fun numericUnavailable(domainDigest: CanonicalDigest): PkCalibrationRenderResult {
    return requireNotNull(
        PkCalibrationRenderResult.create(
            domainDigest = domainDigest,
            renderState = PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
            renderReasons = setOf(PkCalibrationReason.NUMERIC_FAILURE),
            centralCurve = emptyList(),
            bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
        )
    )
}

private fun List<Double>.isOrdered(): Boolean {
    return zipWithNext().none { (left, right) -> left > right }
}

private fun Double.rawBinary64Bits(): String {
    return toBits().toULong().toString(16).padStart(16, '0')
}

private fun Double.normalizePositiveZero(): Double = if (this == 0.0) 0.0 else this
