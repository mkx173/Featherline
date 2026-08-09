package com.mkx.hrttracker.model.pk

import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.solvers.BisectionSolver
import org.hipparchus.linear.Array2DRowRealMatrix
import org.hipparchus.linear.ArrayRealVector
import org.hipparchus.linear.CholeskyDecomposition
import java.util.Collections
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One canonical included-lab observation for the v10 joint objective. */
internal data class PkJointLabPoint(
    val resultId: UUID,
    val logObservedPgml: Double,
    /** Population drug decomposition in canonical route order. */
    val drugByRoutePgml: List<Double>,
    val totalDrugPgml: Double,
    val logTotalDrugPgml: Double,
    val effectiveDisposition: PkCalibrationEffectiveDisposition,
) {
    /** Population share w_ir = d_ir / D_i; gates never read fitted shares. */
    fun populationShare(routeIndex: Int): Double =
        drugByRoutePgml[routeIndex] / totalDrugPgml
}

/**
 * v10.0 §A10.2 joint Student-t objective over all route log-scales, with
 * canonical UUID-ordered accumulation:
 *
 *   m_i(beta) = sum_r e^{beta_r} d_ir,   r_i = log y_i - log m_i,   z = r/sqrt(R)
 *   J(beta)   = sum_i rho_nu(z_i) + sum_r beta_r^2 / (2 sigma_s^2)
 *
 * Gradient and Hessian use the model shares s_ir = e^{beta_r} d_ir / m_i.
 * Inactive routes (d_ir = 0 for every lab) are never fitted; beta stays 0.
 */
internal class PkJointStudentTObjective private constructor(
    val points: List<PkJointLabPoint>,
    val rLog: Double,
) {
    private val sqrtRLog = sqrt(rLog)
    private val priorVariance = PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD *
            PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD
    private val priorPrecision = 1.0 / priorVariance

    val routeCount = PkCalibrationRoute.entries.size

    /** Active coordinates in canonical order (§A10.1). */
    val activeRouteIndices: List<Int> = (0 until routeCount).filter { routeIndex ->
        points.any { point -> point.drugByRoutePgml[routeIndex] > 0.0 }
    }

    fun objective(beta: DoubleArray): Double? {
        if (beta.size != routeCount || beta.any { value -> !value.isFinite() }) return null
        var sum = 0.0
        for (point in points) {
            val mean = meanAt(point, beta) ?: return null
            val z = (point.logObservedPgml - ln(mean)) / sqrtRLog
            if (!z.isFinite()) return null
            val term = ((PkCalibrationDefaults.STUDENT_T_NU + 1.0) / 2.0) *
                    ln1p(z * z / PkCalibrationDefaults.STUDENT_T_NU)
            if (!term.isFinite()) return null
            sum += term
            if (!sum.isFinite()) return null
        }
        for (routeIndex in 0 until routeCount) {
            val scaledBeta = beta[routeIndex] / PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD
            sum += 0.5 * (scaledBeta * scaledBeta)
            if (!sum.isFinite()) return null
        }
        return sum
    }

    /** Gradient over the active coordinates, in [activeRouteIndices] order. */
    fun gradient(beta: DoubleArray): DoubleArray? {
        if (beta.size != routeCount || beta.any { value -> !value.isFinite() }) return null
        val result = DoubleArray(activeRouteIndices.size)
        for (point in points) {
            val mean = meanAt(point, beta) ?: return null
            val z = (point.logObservedPgml - ln(mean)) / sqrtRLog
            if (!z.isFinite()) return null
            val psi = psi(z) ?: return null
            for (position in activeRouteIndices.indices) {
                val routeIndex = activeRouteIndices[position]
                val share = exp(beta[routeIndex]) * point.drugByRoutePgml[routeIndex] / mean
                if (!share.isFinite()) return null
                result[position] -= psi * share / sqrtRLog
                if (!result[position].isFinite()) return null
            }
        }
        for (position in activeRouteIndices.indices) {
            result[position] += beta[activeRouteIndices[position]] * priorPrecision
            if (!result[position].isFinite()) return null
        }
        return result
    }

    /** Hessian over the active coordinates, symmetric by construction. */
    fun hessian(beta: DoubleArray): Array<DoubleArray>? {
        if (beta.size != routeCount || beta.any { value -> !value.isFinite() }) return null
        val size = activeRouteIndices.size
        val result = Array(size) { DoubleArray(size) }
        val shares = DoubleArray(size)
        for (point in points) {
            val mean = meanAt(point, beta) ?: return null
            val z = (point.logObservedPgml - ln(mean)) / sqrtRLog
            if (!z.isFinite()) return null
            val psi = psi(z) ?: return null
            val psiPrime = psiPrime(z) ?: return null
            for (position in activeRouteIndices.indices) {
                val routeIndex = activeRouteIndices[position]
                shares[position] = exp(beta[routeIndex]) *
                        point.drugByRoutePgml[routeIndex] / mean
                if (!shares[position].isFinite()) return null
            }
            for (row in 0 until size) {
                for (column in row until size) {
                    val delta = if (row == column) 1.0 else 0.0
                    val term = psiPrime * shares[row] * shares[column] / rLog -
                            psi * shares[row] * (delta - shares[column]) / sqrtRLog
                    if (!term.isFinite()) return null
                    result[row][column] += term
                    if (!result[row][column].isFinite()) return null
                }
            }
        }
        for (row in 0 until size) {
            result[row][row] += priorPrecision
            for (column in row + 1 until size) {
                result[column][row] = result[row][column]
            }
        }
        if (result.any { row -> row.any { value -> !value.isFinite() } }) return null
        return result
    }

    fun residual(point: PkJointLabPoint, beta: DoubleArray): Double? {
        val mean = meanAt(point, beta) ?: return null
        return (point.logObservedPgml - ln(mean)).takeIf(Double::isFinite)
    }

    private fun meanAt(point: PkJointLabPoint, beta: DoubleArray): Double? {
        var mean = 0.0
        for (routeIndex in 0 until routeCount) {
            mean += exp(beta[routeIndex]) * point.drugByRoutePgml[routeIndex]
            if (!mean.isFinite()) return null
        }
        return mean.takeIf { value -> value > 0.0 }
    }

    private fun psi(z: Double): Double? {
        val value = (PkCalibrationDefaults.STUDENT_T_NU + 1.0) * z /
                (PkCalibrationDefaults.STUDENT_T_NU + z * z)
        return value.takeIf(Double::isFinite)
    }

    private fun psiPrime(z: Double): Double? {
        val denominator = PkCalibrationDefaults.STUDENT_T_NU + z * z
        val value = (PkCalibrationDefaults.STUDENT_T_NU + 1.0) *
                (PkCalibrationDefaults.STUDENT_T_NU - z * z) /
                (denominator * denominator)
        return value.takeIf(Double::isFinite)
    }

    companion object {
        fun create(points: List<PkJointLabPoint>, rLog: Double): PkJointStudentTObjective? {
            if (points.isEmpty() || !rLog.isFinite() || rLog <= 0.0) return null
            if (points.map(PkJointLabPoint::resultId).distinct().size != points.size) {
                return null
            }
            for (point in points) {
                if (!point.logObservedPgml.isFinite() ||
                    !point.logTotalDrugPgml.isFinite() ||
                    !point.totalDrugPgml.isFinite() || point.totalDrugPgml <= 0.0 ||
                    point.drugByRoutePgml.size != PkCalibrationRoute.entries.size ||
                    point.drugByRoutePgml.any { value -> !value.isFinite() || value < 0.0 }
                ) {
                    return null
                }
            }
            val canonical = points.sortedBy { point -> point.resultId.toString().lowercase() }
            return PkJointStudentTObjective(immutableList(canonical), rLog)
        }

        fun fromEvidence(
            evidence: List<PkCalibrationLabEvidence>,
            rLog: Double,
        ): PkJointStudentTObjective? {
            val points = ArrayList<PkJointLabPoint>(evidence.size)
            for (item in evidence) {
                if (item.state != PkCalibrationLabEvidenceState.INCLUDED) return null
                val observed = item.observedPgml
                    ?.takeIf { value -> value.isFinite() && value > 0.0 } ?: return null
                val breakdown = item.breakdown ?: return null
                val total = breakdown.totalDrugPgml
                    .takeIf { value -> value.isFinite() && value > 0.0 } ?: return null
                val logObserved = ln(observed)
                val logTotal = ln(total)
                if (!logObserved.isFinite() || !logTotal.isFinite()) return null
                points += PkJointLabPoint(
                    resultId = item.resultId,
                    logObservedPgml = logObserved,
                    drugByRoutePgml = PkCalibrationRoute.entries.map { route ->
                        breakdown.byRouteDrugPgml.getValue(route)
                    },
                    totalDrugPgml = total,
                    logTotalDrugPgml = logTotal,
                    effectiveDisposition = item.effectiveDisposition,
                )
            }
            return create(points, rLog)
        }
    }
}

/** Converged joint MAP with its Laplace covariance over the active block. */
internal class PkJointFit(
    val beta: DoubleArray,
    val activeRouteIndices: List<Int>,
    /** Symmetric covariance over [activeRouteIndices], canonical order. */
    val covariance: Array<DoubleArray>,
)

internal sealed interface PkJointFitOutcome {
    data class Fitted(val fit: PkJointFit) : PkJointFitOutcome
    data object PosteriorModeAmbiguous : PkJointFitOutcome
    data object NumericFailure : PkJointFitOutcome
}

/**
 * v10.0 §A10.3 deterministic multi-start damped Newton.
 *
 * Starts are beta = 0 plus, per active route, the 1-D conditional MAP found by
 * the §A1 grid + bisection restricted to that coordinate (others held at 0),
 * plus every pairwise combination (b_i*, b_j*) of those conditional minima for
 * each active route pair (Option A, 2026-08-09): axis-aligned starts alone
 * cannot reach a mode where two routes move together. Ceiling: a mode
 * requiring three or more routes to move jointly can still be missed — the
 * ambiguity gate is best-effort beyond pairwise coupling, an accepted
 * limitation. The 1-D search interval is the a-priori stationary bound
 * intersected with the [-20, 20] numeric guard; the guard itself applies to
 * the converged MAP, where it is a data-sanity check. Exactly one distinct
 * positive-definite minimum is required: none is numeric failure, two or more
 * is a global POSTERIOR_MODE_AMBIGUOUS. Failure granularity is deliberately
 * global — the safe direction is every route back at population.
 */
internal object PkJointMapSolver {
    private const val MAX_LINE_SEARCH_HALVINGS = 60

    fun fit(objective: PkJointStudentTObjective): PkJointFitOutcome {
        if (objective.activeRouteIndices.isEmpty()) {
            return PkJointFitOutcome.NumericFailure
        }
        // Every 1-D conditional minimum seeds a start: a route whose
        // restricted objective is bimodal must surface both basins so the
        // joint search can flag POSTERIOR_MODE_AMBIGUOUS. A failed 1-D
        // enumeration is a numeric failure, never an empty start list — an
        // unseeded search would report whatever single basin it stumbles into
        // as a confident fit.
        val conditionalsByRoute = linkedMapOf<Int, List<Double>>()
        for (routeIndex in objective.activeRouteIndices) {
            conditionalsByRoute[routeIndex] = conditionalStartBetas(objective, routeIndex)
                ?: return PkJointFitOutcome.NumericFailure
        }

        val starts = ArrayList<DoubleArray>()
        starts += DoubleArray(objective.routeCount)
        for ((routeIndex, conditionals) in conditionalsByRoute) {
            for (conditional in conditionals) {
                val start = DoubleArray(objective.routeCount)
                start[routeIndex] = conditional
                starts += start
            }
        }
        // Pairwise coupled starts: (b_i*, b_j*) for every active route pair
        // and every combination of their 1-D conditional minima.
        val active = objective.activeRouteIndices
        for (first in active.indices) {
            for (second in first + 1 until active.size) {
                for (firstBeta in conditionalsByRoute.getValue(active[first])) {
                    for (secondBeta in conditionalsByRoute.getValue(active[second])) {
                        val start = DoubleArray(objective.routeCount)
                        start[active[first]] = firstBeta
                        start[active[second]] = secondBeta
                        starts += start
                    }
                }
            }
        }

        val converged = ArrayList<Pair<DoubleArray, Double>>()
        for (start in starts) {
            val point = newtonPolish(objective, start) ?: continue
            val value = objective.objective(point) ?: continue
            converged += point to value
        }
        if (converged.isEmpty()) return PkJointFitOutcome.NumericFailure

        val minima = ArrayList<Pair<DoubleArray, Double>>()
        for ((point, value) in converged) {
            val hessian = objective.hessian(point)
                ?: return PkJointFitOutcome.NumericFailure
            if (choleskyOrNull(hessian) == null) continue
            val existingIndex = minima.indexOfFirst { (existing, _) ->
                supNormDifference(existing, point) <=
                        PkCalibrationDefaults.JOINT_MODE_DISTINCT_TOL
            }
            if (existingIndex < 0) {
                minima += point to value
            } else {
                // Dedup failsafe (Option A): two converged points separated by
                // more than the numeric tolerance yet under the distinctness
                // tolerance, with meaningfully different objective values, are
                // numerically inconsistent for a smooth objective — fail
                // instead of silently merging a near-distinct mode.
                val (existing, existingValue) = minima[existingIndex]
                if (supNormDifference(existing, point) >
                    PkCalibrationDefaults.JOINT_MODE_NUMERIC_TOL &&
                    abs(value - existingValue) >
                    PkCalibrationDefaults.JOINT_MODE_NUMERIC_TOL
                ) {
                    return PkJointFitOutcome.NumericFailure
                }
                if (value < existingValue) {
                    minima[existingIndex] = point to value
                }
            }
        }
        if (minima.isEmpty()) return PkJointFitOutcome.NumericFailure
        if (minima.size > 1) return PkJointFitOutcome.PosteriorModeAmbiguous

        val beta = minima.single().first
        if (beta.any { value ->
                !value.isFinite() ||
                        abs(value) > PkCalibrationDefaults.GLOBAL_SEARCH_NUMERIC_GUARD_ABS_BETA
            }
        ) {
            return PkJointFitOutcome.NumericFailure
        }
        val hessian = objective.hessian(beta) ?: return PkJointFitOutcome.NumericFailure
        val decomposition = choleskyOrNull(hessian)
            ?: return PkJointFitOutcome.NumericFailure
        val inverse = runCatching { decomposition.solver.inverse }.getOrNull()
            ?: return PkJointFitOutcome.NumericFailure
        val size = objective.activeRouteIndices.size
        val covariance = Array(size) { DoubleArray(size) }
        for (row in 0 until size) {
            for (column in row until size) {
                val value = inverse.getEntry(row, column)
                if (!value.isFinite()) return PkJointFitOutcome.NumericFailure
                covariance[row][column] = value
                covariance[column][row] = value
            }
            if (covariance[row][row] <= 0.0) return PkJointFitOutcome.NumericFailure
        }
        return PkJointFitOutcome.Fitted(
            PkJointFit(
                beta = beta,
                activeRouteIndices = objective.activeRouteIndices,
                covariance = covariance,
            )
        )
    }

    private fun newtonPolish(
        objective: PkJointStudentTObjective,
        start: DoubleArray,
    ): DoubleArray? {
        var beta = start.copyOf()
        var currentValue = objective.objective(beta) ?: return null
        repeat(PkCalibrationDefaults.JOINT_MAX_ITER) {
            val gradient = objective.gradient(beta) ?: return null
            if (supNorm(gradient) <= PkCalibrationDefaults.JOINT_GRAD_TOL) return beta

            val hessian = objective.hessian(beta) ?: return null
            val direction = choleskyOrNull(hessian)?.let { decomposition ->
                runCatching {
                    decomposition.solver.solve(
                        ArrayRealVector(DoubleArray(gradient.size) { index ->
                            -gradient[index]
                        })
                    ).toArray()
                }.getOrNull()
            } ?: DoubleArray(gradient.size) { index -> -gradient[index] }
            if (direction.any { value -> !value.isFinite() }) return null

            var stepScale = 1.0
            var accepted = false
            for (halving in 0 until MAX_LINE_SEARCH_HALVINGS) {
                val candidate = beta.copyOf()
                for (position in objective.activeRouteIndices.indices) {
                    candidate[objective.activeRouteIndices[position]] +=
                        stepScale * direction[position]
                }
                val candidateValue = objective.objective(candidate)
                if (candidateValue != null && candidateValue < currentValue) {
                    val stepNorm = supNorm(
                        DoubleArray(direction.size) { index ->
                            stepScale * direction[index]
                        }
                    )
                    beta = candidate
                    currentValue = candidateValue
                    accepted = true
                    if (stepNorm <= PkCalibrationDefaults.JOINT_STEP_TOL) return beta
                    break
                }
                stepScale /= 2.0
            }
            if (!accepted) {
                // No descent along the damped direction: converged when the
                // full step is already below tolerance, otherwise this start
                // fails and another start must find the mode.
                return if (supNorm(direction) <= PkCalibrationDefaults.JOINT_STEP_TOL) {
                    beta
                } else {
                    null
                }
            }
        }
        return null
    }

    /**
     * All 1-D conditional minima on one coordinate (others at 0), used only to
     * seed the joint Newton search. Every stationary point of the restricted
     * objective lies within |beta| <= sigma_s^2 * n * (nu+1) / (2 sqrt(nu) sqrt(R));
     * the scan interval is that bound intersected with the numeric guard.
     */
    private fun conditionalStartBetas(
        objective: PkJointStudentTObjective,
        routeIndex: Int,
    ): List<Double>? {
        val priorVariance = PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD *
                PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD
        val stationaryBound = priorVariance * objective.points.size *
                (PkCalibrationDefaults.STUDENT_T_NU + 1.0) /
                (2.0 * sqrt(PkCalibrationDefaults.STUDENT_T_NU) * sqrt(objective.rLog))
        if (!stationaryBound.isFinite() || stationaryBound <= 0.0) return null
        val halfWidth = min(
            stationaryBound,
            PkCalibrationDefaults.GLOBAL_SEARCH_NUMERIC_GUARD_ABS_BETA,
        )

        fun restrictedGradient(value: Double): Double? {
            val beta = DoubleArray(objective.routeCount)
            beta[routeIndex] = value
            val gradient = objective.gradient(beta) ?: return null
            return gradient[objective.activeRouteIndices.indexOf(routeIndex)]
        }

        fun restrictedObjective(value: Double): Double? {
            val beta = DoubleArray(objective.routeCount)
            beta[routeIndex] = value
            return objective.objective(beta)
        }

        fun restrictedCurvature(value: Double): Double? {
            val beta = DoubleArray(objective.routeCount)
            beta[routeIndex] = value
            val hessian = objective.hessian(beta) ?: return null
            val position = objective.activeRouteIndices.indexOf(routeIndex)
            return hessian[position][position]
        }

        val width = 2.0 * halfWidth
        val segments = max(
            PkCalibrationDefaults.GRID_MIN_NODES,
            ceil(width / PkCalibrationDefaults.GRID_STEP_LOG).toInt(),
        )
        val roots = ArrayList<Double>(2)
        var previousBeta = -halfWidth
        var previousScore = restrictedGradient(previousBeta) ?: return null
        if (previousScore == 0.0) roots += previousBeta
        for (index in 1..segments) {
            val beta = if (index == segments) {
                halfWidth
            } else {
                -halfWidth + width * index / segments
            }
            val score = restrictedGradient(beta) ?: return null
            if (score == 0.0) {
                roots += beta
            } else if (previousScore != 0.0 && (previousScore < 0.0) != (score < 0.0)) {
                val refined = runCatching {
                    BisectionSolver(PkCalibrationDefaults.STATIONARY_ROOT_BETA_ABS_TOL).solve(
                        PkCalibrationDefaults.STATIONARY_ROOT_MAX_EVAL,
                        UnivariateFunction { value ->
                            restrictedGradient(value) ?: Double.NaN
                        },
                        previousBeta,
                        beta,
                    )
                }.getOrNull()
                    ?.takeIf { value -> value.isFinite() && value in previousBeta..beta }
                    ?: return null
                roots += refined
            }
            previousBeta = beta
            previousScore = score
        }

        val minima = ArrayList<Double>(2)
        for (root in roots) {
            val curvature = restrictedCurvature(root) ?: return null
            if (curvature <= 0.0) continue
            if (restrictedObjective(root) == null) return null
            minima += root
        }
        return minima
    }

    private fun choleskyOrNull(matrix: Array<DoubleArray>): CholeskyDecomposition? {
        return runCatching {
            CholeskyDecomposition(Array2DRowRealMatrix(matrix))
        }.getOrNull()
    }

    private fun supNorm(values: DoubleArray): Double {
        var result = 0.0
        for (value in values) result = max(result, abs(value))
        return result
    }

    private fun supNormDifference(left: DoubleArray, right: DoubleArray): Double {
        var result = 0.0
        for (index in left.indices) result = max(result, abs(left[index] - right[index]))
        return result
    }
}

/** Per-route gate diagnostics computed at the joint MAP (v10.0 §A10.4). */
internal data class PkJointRouteDiagnostics(
    val supportingLabCount: Int,
    val fittedBeta: Double?,
    val laplaceVarianceBeta: Double?,
    val betaPosteriorSd: Double?,
    val betaUncertaintyReduction: Double?,
    val drugSignalLogRange: Double?,
    val robustRmseLog: Double?,
    val minStudentTWeight: Double?,
    val unreviewedOutlierLabIds: Set<UUID>,
)

/** Maps a valid, globally-ready evidence pool to exactly five route results. */
object PkCalibrationSolver {
    fun solve(evidence: PkCalibrationEvidencePool): PkCalibrationResult? {
        val config = evidence.config
        val rLog = config.rLog
        if (!config.isSolverEligible() || rLog == null) {
            return null
        }
        val supportingByRoute = supportingLabIdsByRoute(evidence.included)

        val routeResults: List<PkRouteCalibrationResult>
        var covariance: PkCalibrationPromotedCovariance? = null
        if (evidence.included.isEmpty()) {
            routeResults = PkCalibrationRoute.entries.map { route ->
                populationCountRow(route, supportingLabCount = 0)
            }
        } else {
            val objective = PkJointStudentTObjective.fromEvidence(evidence.included, rLog)
                ?: return globalNumericFailure(evidence, supportingByRoute)
            when (val outcome = PkJointMapSolver.fit(objective)) {
                PkJointFitOutcome.NumericFailure ->
                    return globalNumericFailure(evidence, supportingByRoute)

                PkJointFitOutcome.PosteriorModeAmbiguous -> {
                    routeResults = PkCalibrationRoute.entries.map { route ->
                        ambiguousRow(
                            route,
                            supportingByRoute.getValue(route).size,
                        )
                    }
                }

                is PkJointFitOutcome.Fitted -> {
                    val diagnosticsByRoute = routeDiagnostics(
                        objective = objective,
                        fit = outcome.fit,
                        supportingByRoute = supportingByRoute,
                    ) ?: return globalNumericFailure(evidence, supportingByRoute)
                    routeResults = PkCalibrationRoute.entries.map { route ->
                        classifyRoute(
                            route = route,
                            diagnostics = diagnosticsByRoute.getValue(route),
                            rLog = rLog,
                        ) ?: return globalNumericFailure(evidence, supportingByRoute)
                    }
                    covariance = promotedCovariance(routeResults, outcome.fit)
                    val promotedCount = routeResults.count { routeResult ->
                        !routeResult.displayState.isPopulationDisplayState()
                    }
                    if ((covariance == null) != (promotedCount == 0)) {
                        return globalNumericFailure(evidence, supportingByRoute)
                    }
                }
            }
        }

        val promotedRoutes = routeResults.asSequence()
            .filter { routeResult -> !routeResult.displayState.isPopulationDisplayState() }
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
            promotedBetaCovariance = covariance,
            forwardModelVersion = evidence.canonicalInput.forwardModelVersion,
            calibrationModelVersion = evidence.canonicalInput.calibrationModelVersion,
        )
    }

    /** Atomic result/evidence pair used to issue a render-capable engine evaluation. */
    internal fun solveBound(
        evidence: PkCalibrationEvidencePool,
    ): PkCalibrationBoundSolution? {
        val result = solve(evidence) ?: return null
        return PkCalibrationBoundSolution(
            result = result,
            evidence = evidence,
            proof = PkCalibrationSolverBindingProof,
        )
    }

    /** S_r = { i in E : d_ir >= 0.2 * D_i }, population decomposition only. */
    private fun supportingLabIdsByRoute(
        included: List<PkCalibrationLabEvidence>,
    ): Map<PkCalibrationRoute, Set<UUID>> {
        val result = linkedMapOf<PkCalibrationRoute, MutableSet<UUID>>()
        for (route in PkCalibrationRoute.entries) {
            result[route] = linkedSetOf()
        }
        for (item in included) {
            val breakdown = item.breakdown ?: continue
            val total = breakdown.totalDrugPgml
            for (route in PkCalibrationRoute.entries) {
                val contribution = breakdown.byRouteDrugPgml.getValue(route)
                if (contribution >= PkCalibrationDefaults.PROMOTION_SUPPORT_SHARE_MIN * total) {
                    result.getValue(route) += item.resultId
                }
            }
        }
        return result
    }

    private fun routeDiagnostics(
        objective: PkJointStudentTObjective,
        fit: PkJointFit,
        supportingByRoute: Map<PkCalibrationRoute, Set<UUID>>,
    ): Map<PkCalibrationRoute, PkJointRouteDiagnostics>? {
        val residualByResultId = linkedMapOf<UUID, Double>()
        val weightByResultId = linkedMapOf<UUID, Double>()
        val sqrtRLog = sqrt(objective.rLog)
        for (point in objective.points) {
            val residual = objective.residual(point, fit.beta) ?: return null
            val z = residual / sqrtRLog
            val weight = (PkCalibrationDefaults.STUDENT_T_NU + 1.0) /
                    (PkCalibrationDefaults.STUDENT_T_NU + z * z)
            if (!weight.isFinite() || weight <= 0.0) return null
            residualByResultId[point.resultId] = residual
            weightByResultId[point.resultId] = weight
        }

        val result = linkedMapOf<PkCalibrationRoute, PkJointRouteDiagnostics>()
        for ((routeIndex, route) in PkCalibrationRoute.entries.withIndex()) {
            val supportingIds = supportingByRoute.getValue(route)
            if (supportingIds.size < PkCalibrationDefaults.MIN_SUPPORTING_LABS_FOR_PROMOTION) {
                result[route] = PkJointRouteDiagnostics(
                    supportingLabCount = supportingIds.size,
                    fittedBeta = null,
                    laplaceVarianceBeta = null,
                    betaPosteriorSd = null,
                    betaUncertaintyReduction = null,
                    drugSignalLogRange = null,
                    robustRmseLog = null,
                    minStudentTWeight = null,
                    unreviewedOutlierLabIds = emptySet(),
                )
                continue
            }
            val activePosition = fit.activeRouteIndices.indexOf(routeIndex)
            if (activePosition < 0) return null
            val variance = fit.covariance[activePosition][activePosition]
            if (!variance.isFinite() || variance <= 0.0) return null
            val posteriorSd = sqrt(variance)
                .takeIf { value -> value.isFinite() && value > 0.0 } ?: return null
            val uncertaintyReduction = (
                    1.0 - posteriorSd / PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD
                    ).coerceIn(0.0, 1.0)

            // Share-weighted robust RMSE over every lab with a positive
            // population share on this route (§A10.4), canonical UUID order.
            var weightedSquaredResidualSum = 0.0
            var weightSum = 0.0
            for (point in objective.points) {
                val share = point.populationShare(routeIndex)
                if (!share.isFinite() || share <= 0.0) continue
                val residual = residualByResultId.getValue(point.resultId)
                val weight = weightByResultId.getValue(point.resultId) * share
                weightSum += weight
                weightedSquaredResidualSum += weight * residual * residual
                if (!weightSum.isFinite() || !weightedSquaredResidualSum.isFinite()) {
                    return null
                }
            }
            if (weightSum <= 0.0) return null
            val rmse = sqrt(weightedSquaredResidualSum / weightSum)
                .takeIf { value -> value.isFinite() && value >= 0.0 } ?: return null

            var minimumLogTotal = Double.POSITIVE_INFINITY
            var maximumLogTotal = Double.NEGATIVE_INFINITY
            var minimumWeight = Double.POSITIVE_INFINITY
            val unreviewed = linkedSetOf<UUID>()
            for (point in objective.points) {
                if (point.resultId !in supportingIds) continue
                minimumLogTotal = min(minimumLogTotal, point.logTotalDrugPgml)
                maximumLogTotal = max(maximumLogTotal, point.logTotalDrugPgml)
                val weight = weightByResultId.getValue(point.resultId)
                minimumWeight = min(minimumWeight, weight)
                if (weight < PkCalibrationDefaults.OUTLIER_WEIGHT_MIN &&
                    point.effectiveDisposition != PkCalibrationEffectiveDisposition.ACCEPTED
                ) {
                    unreviewed += point.resultId
                }
            }
            val logRange = maximumLogTotal - minimumLogTotal
            if (!logRange.isFinite() || logRange < 0.0) return null
            if (!minimumWeight.isFinite()) return null

            result[route] = PkJointRouteDiagnostics(
                supportingLabCount = supportingIds.size,
                fittedBeta = fit.beta[routeIndex].normalizePositiveZero(),
                laplaceVarianceBeta = variance,
                betaPosteriorSd = posteriorSd,
                betaUncertaintyReduction = uncertaintyReduction,
                drugSignalLogRange = logRange,
                robustRmseLog = rmse,
                minStudentTWeight = minimumWeight,
                unreviewedOutlierLabIds = immutableSet(unreviewed),
            )
        }
        return result
    }

    internal fun classifyRoute(
        route: PkCalibrationRoute,
        diagnostics: PkJointRouteDiagnostics,
        rLog: Double,
    ): PkRouteCalibrationResult? {
        if (diagnostics.supportingLabCount == 0) {
            return populationCountRow(route, supportingLabCount = 0)
        }
        if (diagnostics.supportingLabCount <
            PkCalibrationDefaults.MIN_SUPPORTING_LABS_FOR_PROMOTION
        ) {
            return populationCountRow(route, diagnostics.supportingLabCount)
        }
        val fittedBeta = diagnostics.fittedBeta ?: return null
        val posteriorSd = diagnostics.betaPosteriorSd ?: return null
        val variance = diagnostics.laplaceVarianceBeta ?: return null
        val rmse = diagnostics.robustRmseLog ?: return null
        val logRange = diagnostics.drugSignalLogRange ?: return null

        val scale = exp(fittedBeta)
        if (!scale.isFinite() || scale <= 0.0 || variance <= 0.0 || posteriorSd <= 0.0) {
            return null
        }
        val cap = PkCalibrationDefaults.DISPLAY_SCALE_CAP_BY_ROUTE[route] ?: return null
        val outsideCap = scale < cap.minInclusive || scale > cap.maxInclusive
        val atCapBoundary = scale == cap.minInclusive || scale == cap.maxInclusive
        val isExtreme = scale < PkCalibrationDefaults.EXTREME_SCALE_CORE_MIN ||
                scale > PkCalibrationDefaults.EXTREME_SCALE_CORE_MAX
        val lacksExtremeCount = isExtreme &&
                diagnostics.supportingLabCount <
                PkCalibrationDefaults.MIN_SUPPORTING_LABS_FOR_EXTREME_SCALE
        val poorFit = rmse > PkCalibrationDefaults.robustRmseLogMaxForPromotion(rLog)
        val hasUnreviewedOutlier = diagnostics.unreviewedOutlierLabIds.isNotEmpty()
        val insufficientContrast = logRange <
                PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN
        val posteriorTooWide = posteriorSd >
                PkCalibrationDefaults.ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION

        val reasons = linkedSetOf<PkCalibrationReason>()
        if (outsideCap) reasons += PkCalibrationReason.DISPLAY_SCALE_EXCEEDED
        if (atCapBoundary) reasons += PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY
        if (lacksExtremeCount) {
            reasons += PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_SUPPORTING_LABS
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
                PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_SUPPORTING_LABS

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
            route = route,
            fittedBeta = fittedBeta,
            displayBeta = if (promoted) fittedBeta else 0.0,
            betaPosteriorSd = posteriorSd,
            betaUncertaintyReduction = diagnostics.betaUncertaintyReduction,
            laplaceVarianceBeta = variance,
            displayState = displayState,
            reasons = reasons,
            supportingLabCount = diagnostics.supportingLabCount,
            drugSignalLogRange = logRange,
            robustRmseLog = rmse,
            minStudentTWeight = diagnostics.minStudentTWeight,
            atDisplayCapBoundary = atCapBoundary,
            unreviewedOutlierLabIds = diagnostics.unreviewedOutlierLabIds,
            rLog = rLog,
        )
    }

    private fun promotedCovariance(
        routeResults: List<PkRouteCalibrationResult>,
        fit: PkJointFit,
    ): PkCalibrationPromotedCovariance? {
        val promoted = routeResults.filter { routeResult ->
            !routeResult.displayState.isPopulationDisplayState()
        }.map(PkRouteCalibrationResult::route)
        if (promoted.isEmpty()) return null
        val positions = promoted.map { route ->
            fit.activeRouteIndices.indexOf(PkCalibrationRoute.entries.indexOf(route))
                .takeIf { position -> position >= 0 } ?: return null
        }
        val values = List(promoted.size) { row ->
            List(promoted.size) { column ->
                fit.covariance[positions[row]][positions[column]]
            }
        }
        return PkCalibrationPromotedCovariance.create(routes = promoted, values = values)
    }

    private fun populationCountRow(
        route: PkCalibrationRoute,
        supportingLabCount: Int,
    ): PkRouteCalibrationResult {
        return requireNotNull(
            if (supportingLabCount == 0) {
                PkRouteCalibrationResult.create(
                    route = route,
                    displayState = PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS,
                    reasons = setOf(PkCalibrationReason.NO_SUPPORTING_LABS),
                    supportingLabCount = 0,
                )
            } else {
                PkRouteCalibrationResult.create(
                    route = route,
                    displayState = PkRouteCalibrationDisplayState
                        .POPULATION_INSUFFICIENT_SUPPORTING_LABS,
                    reasons = setOf(PkCalibrationReason.INSUFFICIENT_SUPPORTING_LABS),
                    supportingLabCount = supportingLabCount,
                )
            }
        )
    }

    private fun ambiguousRow(
        route: PkCalibrationRoute,
        supportingLabCount: Int,
    ): PkRouteCalibrationResult {
        return requireNotNull(
            PkRouteCalibrationResult.create(
                route = route,
                displayState = PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                reasons = setOf(PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS),
                supportingLabCount = supportingLabCount,
            )
        )
    }

    private fun globalNumericFailure(
        evidence: PkCalibrationEvidencePool,
        supportingByRoute: Map<PkCalibrationRoute, Set<UUID>>,
    ): PkCalibrationResult? {
        val routeResults = PkCalibrationRoute.entries.map { route ->
            requireNotNull(
                PkRouteCalibrationResult.create(
                    route = route,
                    displayState = PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
                    reasons = setOf(PkCalibrationReason.NUMERIC_FAILURE),
                    supportingLabCount = supportingByRoute.getValue(route).size,
                )
            )
        }
        return PkCalibrationResult.create(
            globalState = PkCalibrationGlobalState.READY,
            routeResults = routeResults,
            promotedRoutes = emptyList(),
            displayParams = PkPersonalParams.population(),
            promotedBetaCovariance = null,
            forwardModelVersion = evidence.canonicalInput.forwardModelVersion,
            calibrationModelVersion = evidence.canonicalInput.calibrationModelVersion,
        )
    }
}

private fun PkRouteCalibrationDisplayState.isPopulationDisplayState(): Boolean {
    return this != PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL &&
            this != PkRouteCalibrationDisplayState.LAB_CALIBRATED
}

/** Cannot be forged from independently selected fit and evidence artifacts. */
internal class PkCalibrationBoundSolution internal constructor(
    val result: PkCalibrationResult,
    val evidence: PkCalibrationEvidencePool,
    proof: Any,
) {
    init {
        require(proof === PkCalibrationSolverBindingProof)
    }
}

private object PkCalibrationSolverBindingProof

private fun Double.normalizePositiveZero(): Double = if (this == 0.0) 0.0 else this

private fun <T> immutableList(source: List<T>): List<T> {
    return Collections.unmodifiableList(ArrayList(source))
}

private fun <T> immutableSet(source: Set<T>): Set<T> {
    return Collections.unmodifiableSet(LinkedHashSet(source))
}
