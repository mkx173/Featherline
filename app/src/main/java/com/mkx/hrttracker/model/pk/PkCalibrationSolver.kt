package com.mkx.hrttracker.model.pk

import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.solvers.BisectionSolver
import java.util.ArrayDeque
import java.util.Collections
import java.util.UUID
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
) : PkCertifiedStationaryFunction {
    private val sqrtRLog = sqrt(rLog)
    private val nuRLog = PkCalibrationDefaults.STUDENT_T_NU * rLog
    private val priorVariance = PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD *
            PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD
    private val priorPrecision = 1.0 / priorVariance

    override val stationaryDomain: OutwardInterval
        get() {
            var minimumQ = points.first().qLogRatio
            var maximumQ = minimumQ
            for (index in 1 until points.size) {
                val q = points[index].qLogRatio
                minimumQ = min(minimumQ, q)
                maximumQ = max(maximumQ, q)
            }
            return requireNotNull(
                OutwardInterval.create(min(0.0, minimumQ), max(0.0, maximumQ))
            )
        }

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

    override fun score(beta: Double): Double? {
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

    override fun hessian(beta: Double): Double? {
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

    override fun scoreInterval(beta: OutwardInterval): OutwardInterval? {
        var sum = OutwardInterval.singleton(0.0) ?: return null
        val nuR = OutwardInterval.singleton(nuRLog) ?: return null
        val negativeNuPlusOne = OutwardInterval.singleton(
            -(PkCalibrationDefaults.STUDENT_T_NU + 1.0)
        ) ?: return null
        for (point in points) {
            val residual = OutwardInterval.singleton(point.qLogRatio)?.subtract(beta)
                ?: return null
            val residualSquared = residual.square() ?: return null
            val denominator = nuR.add(residualSquared) ?: return null
            val numerator = negativeNuPlusOne.multiply(residual) ?: return null
            val term = numerator.divideByStrictlyPositive(denominator) ?: return null
            sum = sum.add(term) ?: return null
        }
        val prior = beta.divideByStrictlyPositive(
            OutwardInterval.singleton(priorVariance) ?: return null
        ) ?: return null
        return sum.add(prior)
    }

    override fun hessianInterval(beta: OutwardInterval): OutwardInterval? {
        var sum = OutwardInterval.singleton(0.0) ?: return null
        val nuR = OutwardInterval.singleton(nuRLog) ?: return null
        val nuPlusOne = OutwardInterval.singleton(
            PkCalibrationDefaults.STUDENT_T_NU + 1.0
        ) ?: return null
        for (point in points) {
            val residual = OutwardInterval.singleton(point.qLogRatio)?.subtract(beta)
                ?: return null
            val residualSquared = residual.square() ?: return null
            val denominator = nuR.add(residualSquared) ?: return null
            val denominatorSquared = denominator.square() ?: return null
            val difference = nuR.subtract(residualSquared) ?: return null
            val numerator = nuPlusOne.multiply(difference) ?: return null
            val term = numerator.divideByStrictlyPositive(denominatorSquared)
                ?: return null
            sum = sum.add(term) ?: return null
        }
        return sum.add(OutwardInterval.singleton(priorPrecision) ?: return null)
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

/** Minimal outward-rounded interval operations audited by the v9 certificate. */
@ConsistentCopyVisibility
internal data class OutwardInterval private constructor(
    val lower: Double,
    val upper: Double,
) {
    val containsZero: Boolean get() = lower <= 0.0 && upper >= 0.0
    val isStrictlyPositive: Boolean get() = lower > 0.0
    val isStrictlyNegative: Boolean get() = upper < 0.0

    /** Certified upper bound on this interval's real width. */
    fun widthUpperBound(): Double? {
        if (lower == upper) return 0.0
        val rawWidth = upper - lower
        if (!rawWidth.isFinite() || rawWidth <= 0.0) return null
        return Math.nextUp(rawWidth).takeIf { value -> value.isFinite() && value > 0.0 }
    }

    /** Certified lower bound on the real gap after [previous]. */
    fun separationLowerBoundAfter(previous: OutwardInterval): Double? {
        val rawSeparation = lower - previous.upper
        if (!rawSeparation.isFinite()) return null
        return Math.nextDown(rawSeparation).takeIf(Double::isFinite)
    }

    fun midpoint(): Double? {
        val difference = upper - lower
        if (!difference.isFinite() || difference <= 0.0) return null
        val midpoint = lower + difference / 2.0
        return midpoint.takeIf { value -> value.isFinite() && value > lower && value < upper }
    }

    fun contains(value: Double): Boolean = value.isFinite() && value in lower..upper

    fun add(other: OutwardInterval): OutwardInterval? {
        return rounded(lower + other.lower, upper + other.upper)
    }

    fun subtract(other: OutwardInterval): OutwardInterval? {
        return rounded(lower - other.upper, upper - other.lower)
    }

    fun multiply(other: OutwardInterval): OutwardInterval? {
        val products = doubleArrayOf(
            lower * other.lower,
            lower * other.upper,
            upper * other.lower,
            upper * other.upper,
        )
        if (products.any { value -> !value.isFinite() }) return null
        var rawLower = products[0]
        var rawUpper = products[0]
        for (index in 1 until products.size) {
            rawLower = min(rawLower, products[index])
            rawUpper = max(rawUpper, products[index])
        }
        return rounded(rawLower, rawUpper)
    }

    fun square(): OutwardInterval? {
        val lowerSquared = lower * lower
        val upperSquared = upper * upper
        if (!lowerSquared.isFinite() || !upperSquared.isFinite()) return null
        val rawLower = if (containsZero) 0.0 else min(lowerSquared, upperSquared)
        val rawUpper = max(lowerSquared, upperSquared)
        return rounded(rawLower, rawUpper)
    }

    fun divideByStrictlyPositive(denominator: OutwardInterval): OutwardInterval? {
        if (!denominator.isStrictlyPositive) return null
        val quotients = doubleArrayOf(
            lower / denominator.lower,
            lower / denominator.upper,
            upper / denominator.lower,
            upper / denominator.upper,
        )
        if (quotients.any { value -> !value.isFinite() }) return null
        var rawLower = quotients[0]
        var rawUpper = quotients[0]
        for (index in 1 until quotients.size) {
            rawLower = min(rawLower, quotients[index])
            rawUpper = max(rawUpper, quotients[index])
        }
        return rounded(rawLower, rawUpper)
    }

    companion object {
        fun create(lower: Double, upper: Double): OutwardInterval? {
            if (!lower.isFinite() || !upper.isFinite() || lower > upper) return null
            return OutwardInterval(lower, upper)
        }

        fun singleton(value: Double): OutwardInterval? {
            if (!value.isFinite()) return null
            return OutwardInterval(value, value)
        }

        private fun rounded(rawLower: Double, rawUpper: Double): OutwardInterval? {
            if (!rawLower.isFinite() || !rawUpper.isFinite() || rawLower > rawUpper) return null
            val lower = Math.nextDown(rawLower)
            val upper = Math.nextUp(rawUpper)
            if (!lower.isFinite() || !upper.isFinite() || lower > upper) return null
            return OutwardInterval(lower, upper)
        }
    }
}

internal interface PkCertifiedStationaryFunction {
    val stationaryDomain: OutwardInterval
    fun score(beta: Double): Double?
    fun hessian(beta: Double): Double?
    fun scoreInterval(beta: OutwardInterval): OutwardInterval?
    fun hessianInterval(beta: OutwardInterval): OutwardInterval?
}

internal enum class PkStationaryRootKind {
    MINIMUM,
    MAXIMUM,
}

internal data class PkCertifiedStationaryRoot(
    val kind: PkStationaryRootKind,
    val enclosure: OutwardInterval,
    val refinedBeta: Double,
)

internal sealed interface PkStationaryCertificateResult {
    data class Covered(val roots: List<PkCertifiedStationaryRoot>) :
        PkStationaryCertificateResult

    data object NumericFailure : PkStationaryCertificateResult
}

internal data class PkStationaryCertificateBudget(
    val maxCells: Int,
    val maxRefinementEvaluations: Int,
) {
    init {
        require(maxCells > 0)
        require(maxRefinementEvaluations > 0)
    }

    companion object {
        val Production = PkStationaryCertificateBudget(
            maxCells = PkCalibrationDefaults.STATIONARY_INTERVAL_MAX_CELLS_PER_ROUTE,
            maxRefinementEvaluations = PkCalibrationDefaults.STATIONARY_ROOT_MAX_EVAL,
        )
    }
}

/** Exhaustive outward-interval stationary-root certificate from normative v9 §5.1. */
internal object PkStationaryRootCertificate {
    fun certify(
        function: PkCertifiedStationaryFunction,
        budget: PkStationaryCertificateBudget = PkStationaryCertificateBudget.Production,
    ): PkStationaryCertificateResult {
        val domain = function.stationaryDomain
        if (domain.lower < -PkCalibrationDefaults.GLOBAL_SEARCH_NUMERIC_GUARD_ABS_BETA ||
            domain.upper > PkCalibrationDefaults.GLOBAL_SEARCH_NUMERIC_GUARD_ABS_BETA
        ) {
            return PkStationaryCertificateResult.NumericFailure
        }
        val counter = CellCounter(budget.maxCells)
        if (domain.lower == domain.upper) {
            return certifySingleton(function, domain, counter)
        }

        val pending = ArrayDeque<OutwardInterval>()
        pending.add(domain)
        val roots = mutableListOf<PkCertifiedStationaryRoot>()
        while (pending.isNotEmpty()) {
            if (!counter.consume()) return PkStationaryCertificateResult.NumericFailure
            val cell = pending.removeLast()
            val gradient = function.scoreInterval(cell)
                ?: return PkStationaryCertificateResult.NumericFailure
            if (!gradient.containsZero) continue
            val hessian = function.hessianInterval(cell)
                ?: return PkStationaryCertificateResult.NumericFailure
            val leftGradient = function.scoreInterval(
                OutwardInterval.singleton(cell.lower)
                    ?: return PkStationaryCertificateResult.NumericFailure
            )
                ?: return PkStationaryCertificateResult.NumericFailure
            val rightGradient = function.scoreInterval(
                OutwardInterval.singleton(cell.upper)
                    ?: return PkStationaryCertificateResult.NumericFailure
            )
                ?: return PkStationaryCertificateResult.NumericFailure

            val crossingKind = crossingKind(hessian, leftGradient, rightGradient)
            if (crossingKind != null) {
                val root = refineCertifiedBracket(
                    function = function,
                    initial = cell,
                    kind = crossingKind,
                    counter = counter,
                    maxRefinementEvaluations = budget.maxRefinementEvaluations,
                ) ?: return PkStationaryCertificateResult.NumericFailure
                roots += root
                continue
            }
            if (isCertifiedMonotoneRootFree(hessian, leftGradient, rightGradient)) {
                continue
            }

            val midpoint = cell.midpoint()
                ?: return PkStationaryCertificateResult.NumericFailure
            val midpointGradient = function.scoreInterval(
                OutwardInterval.singleton(midpoint)
                    ?: return PkStationaryCertificateResult.NumericFailure
            )
                ?: return PkStationaryCertificateResult.NumericFailure
            if (midpointGradient.containsZero) {
                val root = certifyBoundaryRoot(
                    function = function,
                    parent = cell,
                    boundary = midpoint,
                    counter = counter,
                    maxRefinementEvaluations = budget.maxRefinementEvaluations,
                ) ?: return PkStationaryCertificateResult.NumericFailure
                roots += root
                if (cell.lower < root.enclosure.lower) {
                    pending.add(
                        requireNotNull(
                            OutwardInterval.create(cell.lower, root.enclosure.lower)
                        )
                    )
                }
                if (root.enclosure.upper < cell.upper) {
                    pending.add(
                        requireNotNull(
                            OutwardInterval.create(root.enclosure.upper, cell.upper)
                        )
                    )
                }
                continue
            }

            pending.add(requireNotNull(OutwardInterval.create(midpoint, cell.upper)))
            pending.add(requireNotNull(OutwardInterval.create(cell.lower, midpoint)))
        }

        val orderedRoots = roots.sortedBy { root -> root.enclosure.lower }
        for (index in 1 until orderedRoots.size) {
            val previous = orderedRoots[index - 1].enclosure
            val current = orderedRoots[index].enclosure
            val separationLowerBound = current.separationLowerBoundAfter(previous)
            if (separationLowerBound == null ||
                separationLowerBound <=
                PkCalibrationDefaults.STATIONARY_ROOT_MIN_SEPARATION
            ) {
                return PkStationaryCertificateResult.NumericFailure
            }
        }
        return PkStationaryCertificateResult.Covered(immutableList(orderedRoots))
    }

    private fun certifySingleton(
        function: PkCertifiedStationaryFunction,
        domain: OutwardInterval,
        counter: CellCounter,
    ): PkStationaryCertificateResult {
        if (!counter.consume()) return PkStationaryCertificateResult.NumericFailure
        val gradient = function.scoreInterval(domain)
            ?: return PkStationaryCertificateResult.NumericFailure
        val hessian = function.hessianInterval(domain)
            ?: return PkStationaryCertificateResult.NumericFailure
        val pointGradient = function.score(domain.lower)
            ?: return PkStationaryCertificateResult.NumericFailure
        if (!gradient.containsZero || !hessian.isStrictlyPositive || pointGradient != 0.0) {
            return PkStationaryCertificateResult.NumericFailure
        }
        return PkStationaryCertificateResult.Covered(
            listOf(
                PkCertifiedStationaryRoot(
                    kind = PkStationaryRootKind.MINIMUM,
                    enclosure = domain,
                    refinedBeta = domain.lower.normalizePositiveZero(),
                )
            )
        )
    }

    private fun crossingKind(
        hessian: OutwardInterval,
        leftGradient: OutwardInterval,
        rightGradient: OutwardInterval,
    ): PkStationaryRootKind? {
        return when {
            hessian.isStrictlyPositive && leftGradient.isStrictlyNegative &&
                    rightGradient.isStrictlyPositive -> PkStationaryRootKind.MINIMUM

            hessian.isStrictlyNegative && leftGradient.isStrictlyPositive &&
                    rightGradient.isStrictlyNegative -> PkStationaryRootKind.MAXIMUM

            else -> null
        }
    }

    private fun isCertifiedMonotoneRootFree(
        hessian: OutwardInterval,
        leftGradient: OutwardInterval,
        rightGradient: OutwardInterval,
    ): Boolean {
        if (hessian.isStrictlyPositive) {
            return leftGradient.isStrictlyPositive || rightGradient.isStrictlyNegative
        }
        if (hessian.isStrictlyNegative) {
            return leftGradient.isStrictlyNegative || rightGradient.isStrictlyPositive
        }
        return false
    }

    private fun refineCertifiedBracket(
        function: PkCertifiedStationaryFunction,
        initial: OutwardInterval,
        kind: PkStationaryRootKind,
        counter: CellCounter,
        maxRefinementEvaluations: Int,
    ): PkCertifiedStationaryRoot? {
        var bracket = initial
        while (true) {
            val width = bracket.widthUpperBound() ?: return null
            if (width <= PkCalibrationDefaults.STATIONARY_ROOT_ENCLOSURE_BETA_TOL) break
            if (!counter.consume()) return null
            val midpoint = bracket.midpoint() ?: return null
            val midpointGradient = function.scoreInterval(
                OutwardInterval.singleton(midpoint) ?: return null
            )
                ?: return null
            if (midpointGradient.containsZero) {
                bracket = shrinkAroundBoundaryRoot(
                    function = function,
                    initial = bracket,
                    boundary = midpoint,
                    kind = kind,
                    counter = counter,
                ) ?: return null
                break
            }
            bracket = when (kind) {
                PkStationaryRootKind.MINIMUM -> when {
                    midpointGradient.isStrictlyNegative -> requireNotNull(
                        OutwardInterval.create(midpoint, bracket.upper)
                    )

                    midpointGradient.isStrictlyPositive -> requireNotNull(
                        OutwardInterval.create(bracket.lower, midpoint)
                    )

                    else -> return null
                }

                PkStationaryRootKind.MAXIMUM -> when {
                    midpointGradient.isStrictlyPositive -> requireNotNull(
                        OutwardInterval.create(midpoint, bracket.upper)
                    )

                    midpointGradient.isStrictlyNegative -> requireNotNull(
                        OutwardInterval.create(bracket.lower, midpoint)
                    )

                    else -> return null
                }
            }
        }
        return refineWithHipparchus(
            function = function,
            bracket = bracket,
            kind = kind,
            maxRefinementEvaluations = maxRefinementEvaluations,
        )
    }

    private fun certifyBoundaryRoot(
        function: PkCertifiedStationaryFunction,
        parent: OutwardInterval,
        boundary: Double,
        counter: CellCounter,
        maxRefinementEvaluations: Int,
    ): PkCertifiedStationaryRoot? {
        var left = midpoint(parent.lower, boundary) ?: return null
        var right = midpoint(boundary, parent.upper) ?: return null
        while (true) {
            if (!counter.consume()) return null
            val candidate = OutwardInterval.create(left, right) ?: return null
            val hessian = function.hessianInterval(candidate) ?: return null
            val leftGradient = function.scoreInterval(
                OutwardInterval.singleton(left) ?: return null
            )
                ?: return null
            val rightGradient = function.scoreInterval(
                OutwardInterval.singleton(right) ?: return null
            )
                ?: return null
            val kind = crossingKind(hessian, leftGradient, rightGradient)
            if (kind != null) {
                return refineCertifiedBracket(
                    function = function,
                    initial = candidate,
                    kind = kind,
                    counter = counter,
                    maxRefinementEvaluations = maxRefinementEvaluations,
                )
            }
            left = midpoint(left, boundary) ?: return null
            right = midpoint(boundary, right) ?: return null
        }
    }

    private fun shrinkAroundBoundaryRoot(
        function: PkCertifiedStationaryFunction,
        initial: OutwardInterval,
        boundary: Double,
        kind: PkStationaryRootKind,
        counter: CellCounter,
    ): OutwardInterval? {
        var bracket = initial
        while ((bracket.widthUpperBound() ?: return null) >
            PkCalibrationDefaults.STATIONARY_ROOT_ENCLOSURE_BETA_TOL
        ) {
            if (!counter.consume()) return null
            val left = midpoint(bracket.lower, boundary) ?: return null
            val right = midpoint(boundary, bracket.upper) ?: return null
            val candidate = OutwardInterval.create(left, right) ?: return null
            val hessian = function.hessianInterval(candidate) ?: return null
            val leftGradient = function.scoreInterval(
                OutwardInterval.singleton(left) ?: return null
            )
                ?: return null
            val rightGradient = function.scoreInterval(
                OutwardInterval.singleton(right) ?: return null
            )
                ?: return null
            if (crossingKind(hessian, leftGradient, rightGradient) != kind) return null
            bracket = candidate
        }
        return bracket
    }

    private fun refineWithHipparchus(
        function: PkCertifiedStationaryFunction,
        bracket: OutwardInterval,
        kind: PkStationaryRootKind,
        maxRefinementEvaluations: Int,
    ): PkCertifiedStationaryRoot? {
        val width = bracket.widthUpperBound() ?: return null
        if (width > PkCalibrationDefaults.STATIONARY_ROOT_ENCLOSURE_BETA_TOL) return null
        val gradient = function.scoreInterval(bracket) ?: return null
        val hessian = function.hessianInterval(bracket) ?: return null
        val leftGradient = function.scoreInterval(
            OutwardInterval.singleton(bracket.lower) ?: return null
        )
            ?: return null
        val rightGradient = function.scoreInterval(
            OutwardInterval.singleton(bracket.upper) ?: return null
        )
            ?: return null
        if (!gradient.containsZero ||
            crossingKind(hessian, leftGradient, rightGradient) != kind
        ) {
            return null
        }

        // Hipparchus 4.0.3 API: BisectionSolver(absAccuracy).solve(maxEval, f, min, max).
        // The outward checks above are the certificate; Hipparchus only refines its report.
        val refined = runCatching {
            BisectionSolver(PkCalibrationDefaults.STATIONARY_ROOT_BETA_ABS_TOL).solve(
                maxRefinementEvaluations,
                UnivariateFunction { beta -> function.score(beta) ?: Double.NaN },
                bracket.lower,
                bracket.upper,
            )
        }.getOrNull()?.takeIf(Double::isFinite) ?: return null
        if (!bracket.contains(refined)) return null
        return PkCertifiedStationaryRoot(
            kind = kind,
            enclosure = bracket,
            refinedBeta = refined.normalizePositiveZero(),
        )
    }

    private fun midpoint(lower: Double, upper: Double): Double? {
        if (!lower.isFinite() || !upper.isFinite() || lower >= upper) return null
        val difference = upper - lower
        if (!difference.isFinite()) return null
        return (lower + difference / 2.0).takeIf { value ->
            value.isFinite() && value > lower && value < upper
        }
    }

    private class CellCounter(private val maximum: Int) {
        private var consumed = 0

        fun consume(): Boolean {
            if (consumed >= maximum) return false
            consumed += 1
            return true
        }
    }
}

internal sealed interface PkRouteMapFitResult {
    data class Certified(val diagnostics: PkRouteFitDiagnostics) : PkRouteMapFitResult
    data object PosteriorModeAmbiguous : PkRouteMapFitResult
    data object NumericFailure : PkRouteMapFitResult
}

internal sealed interface PkMinimumSelection {
    data class Unique(val root: PkCertifiedStationaryRoot) : PkMinimumSelection
    data object Ambiguous : PkMinimumSelection
    data object NumericFailure : PkMinimumSelection
}

internal object PkRouteMapSolver {
    fun fit(
        objective: PkRouteStudentTObjective,
        budget: PkStationaryCertificateBudget = PkStationaryCertificateBudget.Production,
    ): PkRouteMapFitResult {
        val certificate = PkStationaryRootCertificate.certify(objective, budget)
        if (certificate !is PkStationaryCertificateResult.Covered) {
            return PkRouteMapFitResult.NumericFailure
        }
        val selection = selectMinimum(certificate)
        if (selection == PkMinimumSelection.NumericFailure) {
            return PkRouteMapFitResult.NumericFailure
        }
        if (selection == PkMinimumSelection.Ambiguous) {
            return PkRouteMapFitResult.PosteriorModeAmbiguous
        }
        val root = (selection as PkMinimumSelection.Unique).root
        val diagnostics = objective.diagnostics(root.refinedBeta)
            ?: return PkRouteMapFitResult.NumericFailure
        return PkRouteMapFitResult.Certified(diagnostics)
    }

    fun selectMinimum(
        certificate: PkStationaryCertificateResult.Covered,
    ): PkMinimumSelection {
        val minima = certificate.roots.filter { root ->
            root.kind == PkStationaryRootKind.MINIMUM
        }
        return when (minima.size) {
            0 -> PkMinimumSelection.NumericFailure
            1 -> PkMinimumSelection.Unique(minima.single())
            else -> PkMinimumSelection.Ambiguous
        }
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

            is PkRouteMapFitResult.Certified -> classify(routeEvidence, fit.diagnostics, rLog)
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
            scopeDecisionDigest = evidence.canonicalInput.scopeDecisionDigest,
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
