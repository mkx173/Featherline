package com.mkx.hrttracker.model.pk

import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.solvers.BisectionSolver
import org.hipparchus.linear.Array2DRowRealMatrix
import org.hipparchus.linear.ArrayRealVector
import org.hipparchus.linear.CholeskyDecomposition
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
) {
    /** Population share w_ir = d_ir / D_i; gates never read fitted shares. */
    fun populationShare(routeIndex: Int): Double =
        drugByRoutePgml[routeIndex] / totalDrugPgml
}

/**
 * Joint Student-t objective over all route log-scales, with
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

    /** Active coordinates in canonical order. */
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
        /**
         * Null on empty or duplicate-id evidence. Values are trusted: the
         * evidence adapter only includes labs with a positive observation and
         * a positive, finite population decomposition.
         */
        fun fromEvidence(
            evidence: List<PkCalibrationIncludedLab>,
            rLog: Double,
        ): PkJointStudentTObjective? {
            if (evidence.isEmpty()) return null
            if (evidence.distinctBy(PkCalibrationIncludedLab::resultId).size != evidence.size) {
                return null
            }
            val points = evidence
                .sortedBy { item -> item.resultId.toString() }
                .map { item ->
                    PkJointLabPoint(
                        resultId = item.resultId,
                        logObservedPgml = ln(item.observedPgml),
                        drugByRoutePgml = PkCalibrationRoute.entries.map { route ->
                            item.breakdown.byRouteDrugPgml.getValue(route)
                        },
                        totalDrugPgml = item.breakdown.totalDrugPgml,
                        logTotalDrugPgml = ln(item.breakdown.totalDrugPgml),
                    )
                }
            return PkJointStudentTObjective(points, rLog)
        }
    }
}

/** Converged joint MAP with its Laplace covariance over the active block. */
internal class PkJointFit(
    val beta: DoubleArray,
    val activeRouteIndices: List<Int>,
    /** Symmetric covariance over [activeRouteIndices], canonical order. */
    val covariance: Array<DoubleArray>,
    /** More than one distinct local minimum; [beta] is the best-valued one. */
    val ambiguous: Boolean,
)

/**
 * Deterministic multi-start damped Newton.
 *
 * Starts are beta = 0 plus, per active route, the 1-D conditional MAP found by
 * grid + bisection restricted to that coordinate (others held at 0), plus
 * every pairwise combination (b_i*, b_j*) of those conditional minima:
 * axis-aligned starts alone often miss the basin of a mode where two routes
 * are jointly displaced. A mode requiring three or more routes to move jointly
 * can still be missed; the ambiguity check is best-effort beyond pairwise
 * coupling. The 1-D search interval is the a-priori stationary bound
 * intersected with the [-20, 20] numeric guard; the guard itself applies to
 * the converged MAP, where it is a data-sanity check.
 *
 * Terminal outcomes: the best-valued distinct positive-definite minimum is
 * the MAP; none is numeric failure; two or more separated by over
 * JOINT_MODE_DISTINCT_TOL still fit the best one but flag the result as
 * ambiguous (a POSTERIOR_MODE_AMBIGUOUS warning on every fitted route).
 */
internal object PkJointMapSolver {
    private const val MAX_LINE_SEARCH_HALVINGS = 60

    /** Null is a numeric failure. */
    fun fit(objective: PkJointStudentTObjective): PkJointFit? {
        if (objective.activeRouteIndices.isEmpty()) return null
        // Every 1-D conditional minimum seeds a start: a route whose
        // restricted objective is bimodal must surface both basins so the
        // joint search can flag POSTERIOR_MODE_AMBIGUOUS. A failed 1-D
        // enumeration is a numeric failure, never an unseeded route — an
        // unseeded search would report whatever single basin it stumbles into
        // as a confident fit. An EMPTY minima list gets the same treatment:
        // the prior makes the restricted objective coercive, so no interior
        // minimum means the scan window was clamped below the stationary
        // bound (or the roots degenerated numerically) and the route's basins
        // are unreachable.
        val active = objective.activeRouteIndices
        val conditionalsByPosition = ArrayList<List<Double>>(active.size)
        for (routeIndex in active) {
            val conditionals = conditionalStartBetas(objective, routeIndex)
            if (conditionals.isNullOrEmpty()) return null
            conditionalsByPosition += conditionals
        }

        // Duplicate starts (a conditional minimum at ~0 reproduces an axis or
        // zero start) would each burn a full redundant Newton polish.
        val starts = ArrayList<DoubleArray>()
        fun addStart(vararg entries: Pair<Int, Double>) {
            val start = DoubleArray(objective.routeCount)
            for ((routeIndex, beta) in entries) start[routeIndex] = beta
            if (starts.none { existing ->
                    supNormDifference(existing, start) <=
                        PkCalibrationDefaults.JOINT_MODE_NUMERIC_TOL
                }
            ) {
                starts += start
            }
        }
        addStart()
        for (position in active.indices) {
            for (conditional in conditionalsByPosition[position]) {
                addStart(active[position] to conditional)
            }
        }
        // Pairwise coupled starts: (b_i*, b_j*) for every active route pair
        // and every combination of their 1-D conditional minima.
        for (first in active.indices) {
            for (second in first + 1 until active.size) {
                for (firstBeta in conditionalsByPosition[first]) {
                    for (secondBeta in conditionalsByPosition[second]) {
                        addStart(active[first] to firstBeta, active[second] to secondBeta)
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
        if (converged.isEmpty()) return null

        // Deterministic clustering: candidates ordered by objective value then
        // coordinates, so each cluster's representative is fixed as its
        // best-valued member and the verdict cannot depend on start order.
        val candidates = converged.sortedWith(
            compareBy<Pair<DoubleArray, Double>>({ (_, value) -> value })
                .thenComparator { (left, _), (right, _) -> compareBetas(left, right) }
        )
        val minima = ArrayList<Pair<DoubleArray, Double>>()
        for ((point, value) in candidates) {
            val hessian = objective.hessian(point)
                ?: return null
            if (choleskyOrNull(hessian) == null) continue
            if (minima.none { (existing, _) ->
                    supNormDifference(existing, point) <=
                        PkCalibrationDefaults.JOINT_MODE_DISTINCT_TOL
                }
            ) {
                minima += point to value
            }
        }
        if (minima.isEmpty()) return null
        // Sorted by objective value: the first cluster representative is the MAP.
        val beta = minima.first().first
        if (beta.any { value ->
                !value.isFinite() ||
                        abs(value) > PkCalibrationDefaults.GLOBAL_SEARCH_NUMERIC_GUARD_ABS_BETA
            }
        ) {
            return null
        }
        val hessian = objective.hessian(beta) ?: return null
        val decomposition = choleskyOrNull(hessian)
            ?: return null
        val inverse = runCatching { decomposition.solver.inverse }.getOrNull()
            ?: return null
        val size = objective.activeRouteIndices.size
        val covariance = Array(size) { DoubleArray(size) }
        for (row in 0 until size) {
            for (column in row until size) {
                val value = inverse.getEntry(row, column)
                if (!value.isFinite()) return null
                covariance[row][column] = value
                covariance[column][row] = value
            }
            if (covariance[row][row] <= 0.0) return null
        }
        return PkJointFit(
            beta = beta,
            activeRouteIndices = objective.activeRouteIndices,
            covariance = covariance,
            ambiguous = minima.size > 1,
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
                    if (stepNorm <= PkCalibrationDefaults.JOINT_STEP_TOL) {
                        return beta.takeIf { point -> stepExitConverged(objective, point) }
                    }
                    break
                }
                stepScale /= 2.0
            }
            if (!accepted) {
                // No descent along the damped direction: converged when the
                // full step is already below tolerance, otherwise this start
                // fails and another start must find the mode.
                return if (supNorm(direction) <= PkCalibrationDefaults.JOINT_STEP_TOL &&
                    stepExitConverged(objective, beta)
                ) {
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

    /**
     * A step-tolerance exit is only a convergence when the gradient is small
     * enough that the point provably sits within JOINT_MODE_NUMERIC_TOL of its
     * basin minimum (|J - J*| <= g^2 / (2 * priorPrecision) ~= 5e-14 at
     * g = JOINT_STEP_EXIT_GRAD_TOL). A stalled line search with a large
     * gradient fails the start instead.
     */
    private fun stepExitConverged(
        objective: PkJointStudentTObjective,
        beta: DoubleArray,
    ): Boolean {
        val gradient = objective.gradient(beta) ?: return false
        return supNorm(gradient) <= PkCalibrationDefaults.JOINT_STEP_EXIT_GRAD_TOL
    }

    /** Deterministic total order on converged points, independent of start order. */
    private fun compareBetas(left: DoubleArray, right: DoubleArray): Int {
        for (index in left.indices) {
            val comparison = left[index].compareTo(right[index])
            if (comparison != 0) return comparison
        }
        return 0
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

/** Per-route warning diagnostics computed at the joint MAP. */
internal data class PkJointRouteDiagnostics(
    val supportingLabCount: Int,
    val fittedBeta: Double,
    val betaPosteriorSd: Double,
    val drugSignalLogRange: Double,
    val robustRmseLog: Double,
    val minStudentTWeight: Double?,
    val unreviewedOutlierLabIds: Set<UUID>,
)

/** Maps one evidence pool to exactly five route results. */
object PkCalibrationSolver {
    fun solve(evidence: PkCalibrationEvidencePool): PkCalibrationResult {
        val rLog = evidence.input.config.rLog
        val supportingByRoute = supportingLabIdsByRoute(evidence.included)
        val ignored = evidence.ignored

        if (evidence.included.isEmpty()) {
            return PkCalibrationResult(
                globalState = PkCalibrationGlobalState.READY,
                routeResults = PkCalibrationRoute.entries.map(::populationRow),
                ignoredLabs = ignored,
            )
        }
        val objective = PkJointStudentTObjective.fromEvidence(evidence.included, rLog)
            ?: return globalNumericFailure(ignored)
        val fit = PkJointMapSolver.fit(objective) ?: return globalNumericFailure(ignored)
        val diagnosticsByRoute = routeDiagnostics(
            objective = objective,
            fit = fit,
            supportingByRoute = supportingByRoute,
        ) ?: return globalNumericFailure(ignored)
        val routeResults = PkCalibrationRoute.entries.map { route ->
            val diagnostics = diagnosticsByRoute[route] ?: return@map populationRow(route)
            classifyRoute(route, diagnostics, rLog, fit.ambiguous)
                ?: return globalNumericFailure(ignored)
        }
        return PkCalibrationResult(
            globalState = PkCalibrationGlobalState.READY,
            routeResults = routeResults,
            promotedBetaCovariance = promotedCovariance(routeResults, fit),
            ignoredLabs = ignored,
        )
    }

    /** S_r = { i in E : d_ir >= 0.2 * D_i }, population decomposition only. */
    private fun supportingLabIdsByRoute(
        included: List<PkCalibrationIncludedLab>,
    ): Map<PkCalibrationRoute, Set<UUID>> {
        val result = linkedMapOf<PkCalibrationRoute, MutableSet<UUID>>()
        for (route in PkCalibrationRoute.entries) {
            result[route] = linkedSetOf()
        }
        for (item in included) {
            val breakdown = item.breakdown
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

        // Every active route (some included lab has d_ir > 0) gets a row;
        // inactive routes are absent and stay population.
        val result = linkedMapOf<PkCalibrationRoute, PkJointRouteDiagnostics>()
        for ((routeIndex, route) in PkCalibrationRoute.entries.withIndex()) {
            val activePosition = fit.activeRouteIndices.indexOf(routeIndex)
            if (activePosition < 0) continue
            val supportingIds = supportingByRoute.getValue(route)
            val variance = fit.covariance[activePosition][activePosition]
            if (!variance.isFinite() || variance <= 0.0) return null
            val posteriorSd = sqrt(variance)
                .takeIf { value -> value.isFinite() && value > 0.0 } ?: return null

            // Share-weighted robust RMSE over every lab with a positive
            // population share on this route, canonical UUID order.
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
            val rmse = if (weightSum <= 0.0) {
                0.0
            } else {
                sqrt(weightedSquaredResidualSum / weightSum)
                    .takeIf { value -> value.isFinite() && value >= 0.0 } ?: return null
            }

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
                if (weight < PkCalibrationDefaults.OUTLIER_WEIGHT_MIN) {
                    unreviewed += point.resultId
                }
            }
            // No supporting lab: no contrast and no outlier attribution.
            val logRange = if (supportingIds.isEmpty()) 0.0 else maximumLogTotal - minimumLogTotal
            if (!logRange.isFinite() || logRange < 0.0) return null

            result[route] = PkJointRouteDiagnostics(
                supportingLabCount = supportingIds.size,
                fittedBeta = fit.beta[routeIndex].normalizePositiveZero(),
                betaPosteriorSd = posteriorSd,
                drugSignalLogRange = logRange,
                robustRmseLog = rmse,
                minStudentTWeight = minimumWeight.takeIf(Double::isFinite),
                unreviewedOutlierLabIds = unreviewed,
            )
        }
        return result
    }

    /**
     * Warn-only classification: every active route shows its fitted beta.
     * Each threshold that trips is a reason on the row; the state is
     * LAB_CALIBRATED only when no reason fired.
     */
    internal fun classifyRoute(
        route: PkCalibrationRoute,
        diagnostics: PkJointRouteDiagnostics,
        rLog: Double,
        ambiguous: Boolean,
    ): PkRouteCalibrationResult? {
        val fittedBeta = diagnostics.fittedBeta
        val posteriorSd = diagnostics.betaPosteriorSd
        val scale = exp(fittedBeta)
        if (!scale.isFinite() || scale <= 0.0 || posteriorSd <= 0.0) return null
        val cap = PkCalibrationDefaults.DISPLAY_SCALE_CAP_BY_ROUTE.getValue(route)
        val isExtreme = scale < PkCalibrationDefaults.EXTREME_SCALE_CORE_MIN ||
                scale > PkCalibrationDefaults.EXTREME_SCALE_CORE_MAX

        val reasons = linkedSetOf<PkCalibrationReason>()
        if (diagnostics.supportingLabCount == 0) {
            reasons += PkCalibrationReason.NO_SUPPORTING_LABS
        }
        if (scale !in cap || (isExtreme && diagnostics.supportingLabCount <
                    PkCalibrationDefaults.MIN_SUPPORTING_LABS_FOR_EXTREME_SCALE)
        ) {
            reasons += PkCalibrationReason.SCALE_OUTSIDE_USUAL_RANGE
        }
        if (diagnostics.robustRmseLog > PkCalibrationDefaults.robustRmseLogMaxForPromotion(rLog)) {
            reasons += PkCalibrationReason.RESIDUAL_FIT_POOR
        }
        if (diagnostics.unreviewedOutlierLabIds.isNotEmpty()) {
            reasons += PkCalibrationReason.UNREVIEWED_OUTLIER
        }
        if (diagnostics.drugSignalLogRange < PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN ||
            posteriorSd > PkCalibrationDefaults.ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION
        ) {
            reasons += PkCalibrationReason.UNCERTAIN
        }
        if (ambiguous) reasons += PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS

        return PkRouteCalibrationResult(
            route = route,
            displayState = if (reasons.isEmpty()) {
                PkRouteCalibrationDisplayState.LAB_CALIBRATED
            } else {
                PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
            },
            reasons = reasons,
            fittedBeta = fittedBeta,
            betaPosteriorSd = posteriorSd,
            supportingLabCount = diagnostics.supportingLabCount,
            minStudentTWeight = diagnostics.minStudentTWeight,
            unreviewedOutlierLabIds = diagnostics.unreviewedOutlierLabIds,
        )
    }

    private fun promotedCovariance(
        routeResults: List<PkRouteCalibrationResult>,
        fit: PkJointFit,
    ): PkCalibrationPromotedCovariance? {
        val promoted = routeResults
            .filter { routeResult -> routeResult.displayState.isAdjusted }
            .map(PkRouteCalibrationResult::route)
        if (promoted.isEmpty()) return null
        val positions = promoted.map { route ->
            fit.activeRouteIndices.indexOf(PkCalibrationRoute.entries.indexOf(route))
        }
        return PkCalibrationPromotedCovariance(
            routes = promoted,
            values = List(promoted.size) { row ->
                List(promoted.size) { column ->
                    fit.covariance[positions[row]][positions[column]]
                }
            },
        )
    }

    private fun populationRow(route: PkCalibrationRoute): PkRouteCalibrationResult {
        return PkRouteCalibrationResult(
            route = route,
            displayState = PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
        )
    }

    private fun globalNumericFailure(
        ignored: Map<UUID, PkCalibrationLabIgnoreReason>,
    ): PkCalibrationResult {
        return PkCalibrationResult(
            globalState = PkCalibrationGlobalState.READY,
            routeResults = PkCalibrationRoute.entries.map { route ->
                PkRouteCalibrationResult(
                    route = route,
                    displayState = PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
                )
            },
            ignoredLabs = ignored,
        )
    }
}
