package com.mkx.hrttracker.model.pk

import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.solvers.BisectionSolver
import java.util.Collections
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One canonical, post-guard route observation for the scalar v9 objective. */
internal data class PkStudentTPoint(
    val resultId: UUID,
    val qLogRatio: Double,
    val logTotalDrugPgml: Double,
    val effectiveDisposition: PkCalibrationEffectiveDisposition,
)

/** Pure Student-t route objective with canonical UUID-ordered arithmetic. */
internal class PkRouteStudentTObjective private constructor(
    val points: List<PkStudentTPoint>,
    val rLog: Double,
) {
    private val sqrtRLog = sqrt(rLog)
    private val nuRLog = PkCalibrationDefaults.STUDENT_T_NU * rLog
    private val priorVariance = PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD *
            PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD
    private val priorPrecision = 1.0 / priorVariance

    fun objective(beta: Double): Double? {
        if (!beta.isFinite()) return null
        var sum = 0.0
        for (point in points) {
            val residual = point.qLogRatio - beta
            val z = residual / sqrtRLog
            val zSquared = z * z
            val term = ((PkCalibrationDefaults.STUDENT_T_NU + 1.0) / 2.0) *
                    ln1p(zSquared / PkCalibrationDefaults.STUDENT_T_NU)
            if (!term.isFinite()) return null
            sum += term
            if (!sum.isFinite()) return null
        }
        val scaledBeta = beta / PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD
        val priorTerm = 0.5 * (scaledBeta * scaledBeta)
        if (!priorTerm.isFinite()) return null
        sum += priorTerm
        return sum.takeIf(Double::isFinite)
    }

    fun score(beta: Double): Double? {
        if (!beta.isFinite()) return null
        var sum = 0.0
        for (point in points) {
            val residual = point.qLogRatio - beta
            val residualSquared = residual * residual
            val denominator = nuRLog + residualSquared
            val numerator = (PkCalibrationDefaults.STUDENT_T_NU + 1.0) * residual
            val term = -(numerator / denominator)
            if (!term.isFinite()) return null
            sum += term
            if (!sum.isFinite()) return null
        }
        val priorTerm = beta / priorVariance
        if (!priorTerm.isFinite()) return null
        sum += priorTerm
        return sum.takeIf(Double::isFinite)
    }

    fun hessian(beta: Double): Double? {
        if (!beta.isFinite()) return null
        var sum = 0.0
        for (point in points) {
            val residual = point.qLogRatio - beta
            val residualSquared = residual * residual
            val denominator = nuRLog + residualSquared
            val denominatorSquared = denominator * denominator
            val numerator = (PkCalibrationDefaults.STUDENT_T_NU + 1.0) *
                    (nuRLog - residualSquared)
            val term = numerator / denominatorSquared
            if (!term.isFinite()) return null
            sum += term
            if (!sum.isFinite()) return null
        }
        sum += priorPrecision
        return sum.takeIf(Double::isFinite)
    }

    fun diagnostics(beta: Double): PkRouteFitDiagnostics? {
        val hessian = hessian(beta)?.takeIf { value -> value > 0.0 } ?: return null
        val variance = (1.0 / hessian).takeIf { value -> value.isFinite() && value > 0.0 }
            ?: return null
        val posteriorSd = sqrt(variance).takeIf { value -> value.isFinite() && value > 0.0 }
            ?: return null
        val uncertaintyReduction = (
                1.0 - posteriorSd / PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD
                ).coerceIn(0.0, 1.0)

        var weightedSquaredResidualSum = 0.0
        var weightSum = 0.0
        var robustFitHessian = 0.0
        var minimumWeight = Double.POSITIVE_INFINITY
        val weightsByResultId = linkedMapOf<UUID, Double>()
        for (point in points) {
            val residual = point.qLogRatio - beta
            val z = residual / sqrtRLog
            val zSquared = z * z
            val weight = (PkCalibrationDefaults.STUDENT_T_NU + 1.0) /
                    (PkCalibrationDefaults.STUDENT_T_NU + zSquared)
            val weightedSquaredResidual = weight * (residual * residual)
            if (!weight.isFinite() || weight <= 0.0 || !weightedSquaredResidual.isFinite()) {
                return null
            }
            weightsByResultId[point.resultId] = weight
            minimumWeight = min(minimumWeight, weight)
            weightSum += weight
            weightedSquaredResidualSum += weightedSquaredResidual
            robustFitHessian += weight / rLog
            if (!weightSum.isFinite() || !weightedSquaredResidualSum.isFinite() ||
                !robustFitHessian.isFinite()
            ) {
                return null
            }
        }
        robustFitHessian += priorPrecision
        if (!robustFitHessian.isFinite() || robustFitHessian <= 0.0) return null
        if (weightSum <= 0.0) return null
        val rmse = sqrt(weightedSquaredResidualSum / weightSum)
            .takeIf { value -> value.isFinite() && value >= 0.0 } ?: return null

        var minimumLogTotal = points.first().logTotalDrugPgml
        var maximumLogTotal = minimumLogTotal
        for (index in 1 until points.size) {
            val logTotal = points[index].logTotalDrugPgml
            minimumLogTotal = min(minimumLogTotal, logTotal)
            maximumLogTotal = max(maximumLogTotal, logTotal)
        }
        val logRange = maximumLogTotal - minimumLogTotal
        if (!logRange.isFinite() || logRange < 0.0) return null

        val unreviewed = linkedSetOf<UUID>()
        for (point in points) {
            val weight = weightsByResultId.getValue(point.resultId)
            if (weight < PkCalibrationDefaults.OUTLIER_WEIGHT_MIN &&
                point.effectiveDisposition != PkCalibrationEffectiveDisposition.ACCEPTED
            ) {
                unreviewed += point.resultId
            }
        }
        return PkRouteFitDiagnostics(
            fittedBeta = beta.normalizePositiveZero(),
            posteriorHessian = hessian,
            robustFitHessian = robustFitHessian,
            laplaceVarianceBeta = variance,
            betaPosteriorSd = posteriorSd,
            betaUncertaintyReduction = uncertaintyReduction,
            drugSignalLogRange = logRange,
            robustRmseLog = rmse,
            minStudentTWeight = minimumWeight,
            studentTWeightByResultId = immutableMap(weightsByResultId),
            unreviewedOutlierLabIds = immutableSet(unreviewed),
        )
    }

    companion object {
        fun create(points: List<PkStudentTPoint>, rLog: Double): PkRouteStudentTObjective? {
            if (points.isEmpty() || !rLog.isFinite() || rLog <= 0.0) return null
            if (points.map(PkStudentTPoint::resultId).distinct().size != points.size) return null
            if (points.any { point ->
                    !point.qLogRatio.isFinite() || !point.logTotalDrugPgml.isFinite()
                }
            ) {
                return null
            }
            val canonical = points.sortedBy { point -> point.resultId.toString().lowercase() }
            return PkRouteStudentTObjective(immutableList(canonical), rLog)
        }

        fun fromEvidence(
            evidence: List<PkCalibrationLabEvidence>,
            rLog: Double,
        ): PkRouteStudentTObjective? {
            val points = ArrayList<PkStudentTPoint>(evidence.size)
            for (item in evidence) {
                if (item.state != PkCalibrationLabEvidenceState.INCLUDED) return null
                val attributable = item.routeAttributableObservedPgml
                    ?.takeIf { value -> value.isFinite() && value > 0.0 } ?: return null
                val dominant = item.dominantRouteDrugPgml
                    ?.takeIf { value -> value.isFinite() && value > 0.0 } ?: return null
                val total = item.totalDrugPgml
                    ?.takeIf { value -> value.isFinite() && value > 0.0 } ?: return null
                val q = ln(attributable) - ln(dominant)
                val logTotal = ln(total)
                if (!q.isFinite() || !logTotal.isFinite()) return null
                points += PkStudentTPoint(
                    resultId = item.resultId,
                    qLogRatio = q,
                    logTotalDrugPgml = logTotal,
                    effectiveDisposition = item.effectiveDisposition,
                )
            }
            return create(points, rLog)
        }
    }
}

internal data class PkRouteFitDiagnostics(
    val fittedBeta: Double,
    val posteriorHessian: Double,
    val robustFitHessian: Double,
    val laplaceVarianceBeta: Double,
    val betaPosteriorSd: Double,
    val betaUncertaintyReduction: Double,
    val drugSignalLogRange: Double,
    val robustRmseLog: Double,
    val minStudentTWeight: Double,
    val studentTWeightByResultId: Map<UUID, Double>,
    val unreviewedOutlierLabIds: Set<UUID>,
)

/**
 * Deterministic grid + bisection stationary search (v10.0 §A1).
 *
 * Every stationary point of the smooth, coercive route objective lies in
 * I_stat = [min(0, min q), max(0, max q)]. J' is evaluated on a uniform grid
 * whose node spacing is at most GRID_STEP_LOG; each strict sign change (and
 * each exact node zero) is refined with the pinned Hipparchus bisection solver
 * and classified by the sign of J''. Exactly one positive-curvature minimum is
 * required: none is route-local numeric failure, two or more are
 * POSTERIOR_MODE_AMBIGUOUS. The certified enclosure apparatus this replaces is
 * recorded in the v10.0 amendment; the accepted residual risk is a root pair
 * closer than one grid step, far below the ~sqrt(R_LOG) basin width.
 */
internal sealed interface PkRouteMapFitResult {
    data class Fitted(val diagnostics: PkRouteFitDiagnostics) : PkRouteMapFitResult
    data object PosteriorModeAmbiguous : PkRouteMapFitResult
    data object NumericFailure : PkRouteMapFitResult
}

internal object PkRouteMapSolver {
    fun fit(objective: PkRouteStudentTObjective): PkRouteMapFitResult {
        var minimumQ = 0.0
        var maximumQ = 0.0
        for (point in objective.points) {
            minimumQ = min(minimumQ, point.qLogRatio)
            maximumQ = max(maximumQ, point.qLogRatio)
        }
        val guard = PkCalibrationDefaults.GLOBAL_SEARCH_NUMERIC_GUARD_ABS_BETA
        if (!minimumQ.isFinite() || !maximumQ.isFinite() ||
            minimumQ < -guard || maximumQ > guard
        ) {
            return PkRouteMapFitResult.NumericFailure
        }

        val roots = findStationaryRoots(objective, minimumQ, maximumQ)
            ?: return PkRouteMapFitResult.NumericFailure
        var minimumBeta: Double? = null
        for (root in roots) {
            val hessian = objective.hessian(root) ?: return PkRouteMapFitResult.NumericFailure
            when {
                hessian > 0.0 -> {
                    if (minimumBeta != null) return PkRouteMapFitResult.PosteriorModeAmbiguous
                    minimumBeta = root
                }
                hessian < 0.0 -> Unit
                else -> return PkRouteMapFitResult.NumericFailure
            }
        }
        val beta = minimumBeta ?: return PkRouteMapFitResult.NumericFailure
        val diagnostics = objective.diagnostics(beta)
            ?: return PkRouteMapFitResult.NumericFailure
        return PkRouteMapFitResult.Fitted(diagnostics)
    }

    /** Ascending stationary-point locations, or null on any non-finite evaluation. */
    private fun findStationaryRoots(
        objective: PkRouteStudentTObjective,
        minimumQ: Double,
        maximumQ: Double,
    ): List<Double>? {
        // I_stat collapses to a point only when every q is zero.
        if (minimumQ == maximumQ) return listOf(0.0)
        val width = maximumQ - minimumQ
        if (!width.isFinite() || width <= 0.0) return null
        val segments = max(
            PkCalibrationDefaults.GRID_MIN_NODES,
            ceil(width / PkCalibrationDefaults.GRID_STEP_LOG).toInt(),
        )
        val roots = ArrayList<Double>(2)
        var previousBeta = minimumQ
        var previousScore = objective.score(previousBeta) ?: return null
        if (previousScore == 0.0) roots += previousBeta
        for (index in 1..segments) {
            val beta = if (index == segments) {
                maximumQ
            } else {
                minimumQ + width * index / segments
            }
            val score = objective.score(beta) ?: return null
            if (score == 0.0) {
                roots += beta
            } else if (previousScore != 0.0 && (previousScore < 0.0) != (score < 0.0)) {
                val refined = refineBracket(objective, previousBeta, beta) ?: return null
                roots += refined
            }
            previousBeta = beta
            previousScore = score
        }
        return roots
    }

    private fun refineBracket(
        objective: PkRouteStudentTObjective,
        lower: Double,
        upper: Double,
    ): Double? {
        // Hipparchus 4.0.3 API: BisectionSolver(absAccuracy).solve(maxEval, f, min, max).
        val refined = runCatching {
            BisectionSolver(PkCalibrationDefaults.STATIONARY_ROOT_BETA_ABS_TOL).solve(
                PkCalibrationDefaults.STATIONARY_ROOT_MAX_EVAL,
                UnivariateFunction { beta -> objective.score(beta) ?: Double.NaN },
                lower,
                upper,
            )
        }.getOrNull() ?: return null
        return refined.takeIf { value -> value.isFinite() && value in lower..upper }
    }
}

internal object PkRouteCalibrationSolver {
    fun solve(
        routeEvidence: PkCalibrationRouteEvidence,
        rLog: Double,
    ): PkRouteCalibrationResult {
        if (!hasValidEvidenceShape(routeEvidence)) {
            return numericFailure(routeEvidence)
        }
        if (routeEvidence.dominantCandidateLabCount == 0) {
            return requireNotNull(
                PkRouteCalibrationResult.create(
                    route = routeEvidence.route,
                    displayState =
                        PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS,
                    reasons = setOf(PkCalibrationReason.NO_DOMINANT_LABS),
                    dominantCandidateLabCount = 0,
                    dominantLabCount = 0,
                )
            )
        }
        if (routeEvidence.dominantLabCount <
            PkCalibrationDefaults.MIN_DOMINANT_LABS_FOR_PROMOTION
        ) {
            return requireNotNull(
                PkRouteCalibrationResult.create(
                    route = routeEvidence.route,
                    displayState = PkRouteCalibrationDisplayState
                        .POPULATION_INSUFFICIENT_DOMINANT_LABS,
                    reasons = setOf(PkCalibrationReason.INSUFFICIENT_DOMINANT_LABS),
                    dominantCandidateLabCount = routeEvidence.dominantCandidateLabCount,
                    dominantLabCount = routeEvidence.dominantLabCount,
                )
            )
        }

        val objective = PkRouteStudentTObjective.fromEvidence(routeEvidence.included, rLog)
            ?: return numericFailure(routeEvidence)
        return when (val fit = PkRouteMapSolver.fit(objective)) {
            PkRouteMapFitResult.NumericFailure -> numericFailure(routeEvidence)
            PkRouteMapFitResult.PosteriorModeAmbiguous -> requireNotNull(
                PkRouteCalibrationResult.create(
                    route = routeEvidence.route,
                    displayState = PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                    reasons = setOf(PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS),
                    dominantCandidateLabCount = routeEvidence.dominantCandidateLabCount,
                    dominantLabCount = routeEvidence.dominantLabCount,
                )
            )

            is PkRouteMapFitResult.Fitted -> classify(routeEvidence, fit.diagnostics, rLog)
        }
    }

    internal fun classify(
        routeEvidence: PkCalibrationRouteEvidence,
        diagnostics: PkRouteFitDiagnostics,
        rLog: Double,
    ): PkRouteCalibrationResult {
        val scale = exp(diagnostics.fittedBeta)
        if (!scale.isFinite() || scale <= 0.0 ||
            !diagnostics.posteriorHessian.isFinite() || diagnostics.posteriorHessian <= 0.0
        ) {
            return numericFailure(routeEvidence)
        }
        val cap = PkCalibrationDefaults.DISPLAY_SCALE_CAP_BY_ROUTE[routeEvidence.route]
            ?: return numericFailure(routeEvidence)
        val outsideCap = scale < cap.minInclusive || scale > cap.maxInclusive
        val atCapBoundary = scale == cap.minInclusive || scale == cap.maxInclusive
        val isExtreme = scale < PkCalibrationDefaults.EXTREME_SCALE_CORE_MIN ||
                scale > PkCalibrationDefaults.EXTREME_SCALE_CORE_MAX
        val lacksExtremeCount = isExtreme &&
                routeEvidence.dominantLabCount <
                PkCalibrationDefaults.MIN_DOMINANT_LABS_FOR_EXTREME_SCALE
        val poorFit = diagnostics.robustRmseLog >
                PkCalibrationDefaults.robustRmseLogMaxForPromotion(rLog)
        val hasUnreviewedOutlier = diagnostics.unreviewedOutlierLabIds.isNotEmpty()
        val insufficientContrast = diagnostics.drugSignalLogRange <
                PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN
        val posteriorTooWide = diagnostics.betaPosteriorSd >
                PkCalibrationDefaults.ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION

        val reasons = linkedSetOf<PkCalibrationReason>()
        if (outsideCap) reasons += PkCalibrationReason.DISPLAY_SCALE_EXCEEDED
        if (atCapBoundary) reasons += PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY
        if (lacksExtremeCount) {
            reasons += PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_DOMINANT_LABS
        }
        if (poorFit) reasons += PkCalibrationReason.RESIDUAL_FIT_POOR
        if (hasUnreviewedOutlier) reasons += PkCalibrationReason.UNREVIEWED_OUTLIER
        if (insufficientContrast) {
            reasons += PkCalibrationReason.INSUFFICIENT_DRUG_SIGNAL_CONTRAST
        }
        if (posteriorTooWide) reasons += PkCalibrationReason.POSTERIOR_SD_TOO_WIDE

        val provisionalGatesPass = !outsideCap && !lacksExtremeCount && !poorFit &&
                !hasUnreviewedOutlier
        val displayState = when {
            outsideCap -> PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED
            lacksExtremeCount ->
                PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS

            poorFit || hasUnreviewedOutlier ->
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE

            provisionalGatesPass && !insufficientContrast && !posteriorTooWide ->
                PkRouteCalibrationDisplayState.LAB_CALIBRATED

            provisionalGatesPass ->
                PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL

            else -> PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE
        }
        val promoted = displayState == PkRouteCalibrationDisplayState.LAB_CALIBRATED ||
                displayState == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
        return PkRouteCalibrationResult.create(
            route = routeEvidence.route,
            fittedBeta = diagnostics.fittedBeta,
            displayBeta = if (promoted) diagnostics.fittedBeta else 0.0,
            betaPosteriorSd = diagnostics.betaPosteriorSd,
            betaUncertaintyReduction = diagnostics.betaUncertaintyReduction,
            laplaceVarianceBeta = diagnostics.laplaceVarianceBeta,
            displayState = displayState,
            reasons = reasons,
            dominantCandidateLabCount = routeEvidence.dominantCandidateLabCount,
            dominantLabCount = routeEvidence.dominantLabCount,
            drugSignalLogRange = diagnostics.drugSignalLogRange,
            robustRmseLog = diagnostics.robustRmseLog,
            minStudentTWeight = diagnostics.minStudentTWeight,
            atDisplayCapBoundary = atCapBoundary,
            unreviewedOutlierLabIds = diagnostics.unreviewedOutlierLabIds,
            rLog = rLog,
        ) ?: numericFailure(routeEvidence)
    }

    private fun hasValidEvidenceShape(routeEvidence: PkCalibrationRouteEvidence): Boolean {
        return runCatching {
            PkCalibrationRouteEvidence.create(
                route = routeEvidence.route,
                dominantCandidates = routeEvidence.dominantCandidates,
                included = routeEvidence.included,
            ) != null
        }.getOrDefault(false)
    }

    private fun numericFailure(
        routeEvidence: PkCalibrationRouteEvidence,
    ): PkRouteCalibrationResult {
        val candidateCount = runCatching { routeEvidence.dominantCandidates.size }
            .getOrDefault(0)
            .coerceAtLeast(0)
        val includedCount = runCatching { routeEvidence.included.size }
            .getOrDefault(0)
            .coerceIn(0, candidateCount)
        return requireNotNull(
            PkRouteCalibrationResult.create(
                route = routeEvidence.route,
                displayState = PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
                reasons = setOf(PkCalibrationReason.NUMERIC_FAILURE),
                dominantCandidateLabCount = candidateCount,
                dominantLabCount = includedCount,
            )
        )
    }
}

/** Maps a valid, globally-ready evidence partition to exactly five route results. */
object PkCalibrationSolver {
    fun solve(
        evidence: PkCalibrationEvidencePartition,
    ): PkCalibrationResult? {
        val config = evidence.config
        val rLog = config.rLog
        if (!config.isSolverEligible() || rLog == null) {
            return null
        }
        if (evidence.routeEvidence.map(PkCalibrationRouteEvidence::route) !=
            PkCalibrationRoute.entries
        ) {
            return null
        }
        val routeResults = evidence.routeEvidence.map { routeEvidence ->
            PkRouteCalibrationSolver.solve(routeEvidence, rLog)
        }
        val promotedRoutes = routeResults.asSequence()
            .filter { routeResult ->
                routeResult.displayState == PkRouteCalibrationDisplayState.LAB_CALIBRATED ||
                        routeResult.displayState ==
                        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
            }
            .map(PkRouteCalibrationResult::route)
            .toList()
        val nonZeroBetas = linkedMapOf<PkCalibrationRoute, Double>()
        for (routeResult in routeResults) {
            if (routeResult.route in promotedRoutes && routeResult.displayBeta != 0.0) {
                nonZeroBetas[routeResult.route] = routeResult.displayBeta
            }
        }
        val displayParams = PkPersonalParams.create(nonZeroBetas) ?: return null
        return PkCalibrationResult.create(
            globalState = PkCalibrationGlobalState.READY,
            routeResults = routeResults,
            promotedRoutes = promotedRoutes,
            displayParams = displayParams,
            forwardModelVersion = evidence.canonicalInput.forwardModelVersion,
            calibrationModelVersion = evidence.canonicalInput.calibrationModelVersion,
        )
    }

    /** Atomic result/evidence pair used to issue a render-capable engine evaluation. */
    internal fun solveBound(
        evidence: PkCalibrationEvidencePartition,
    ): PkCalibrationBoundSolution? {
        val result = solve(evidence) ?: return null
        return PkCalibrationBoundSolution(
            result = result,
            evidence = evidence,
            proof = PkCalibrationSolverBindingProof,
        )
    }
}

/** Cannot be forged from independently selected fit and evidence artifacts. */
internal class PkCalibrationBoundSolution internal constructor(
    val result: PkCalibrationResult,
    val evidence: PkCalibrationEvidencePartition,
    proof: Any,
) {
    init {
        require(proof === PkCalibrationSolverBindingProof)
    }
}

private object PkCalibrationSolverBindingProof

private fun Double.normalizePositiveZero(): Double = if (this == 0.0) 0.0 else this

private fun <K, V> immutableMap(source: Map<K, V>): Map<K, V> {
    return Collections.unmodifiableMap(LinkedHashMap(source))
}

private fun <T> immutableList(source: List<T>): List<T> {
    return Collections.unmodifiableList(ArrayList(source))
}

private fun <T> immutableSet(source: Set<T>): Set<T> {
    return Collections.unmodifiableSet(LinkedHashSet(source))
}
