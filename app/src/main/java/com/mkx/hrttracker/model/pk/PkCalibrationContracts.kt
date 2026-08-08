package com.mkx.hrttracker.model.pk

import java.util.Collections
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

        /** Unknown identifiers fail closed instead of being guessed or coerced. */
        fun fromStableId(stableId: String): PkCalibrationRoute? = byStableId[stableId]
    }
}

/** Canonical contribution mapping shared by forward evaluation and review digests. */
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
            if (routeLogScale.keys.any { route -> route !in PkCalibrationRoute.entries }) {
                return null
            }
            if (canonical.isEmpty()) return Population
            return PkPersonalParams(
                routeLogScale = immutableMap(canonical),
                thetaKGlobal = 0.0,
            )
        }

        /**
         * Stable-id ingress for persistence/import boundaries. Any unknown id
         * rejects the entire value so callers can remain on population.
         */
        fun fromStableIds(
            routeLogScaleByStableId: Map<String, Double>,
            thetaKGlobal: Double = 0.0,
        ): PkPersonalParams? {
            val resolved = linkedMapOf<PkCalibrationRoute, Double>()
            for ((stableId, beta) in routeLogScaleByStableId) {
                val route = PkCalibrationRoute.fromStableId(stableId) ?: return null
                if (resolved.put(route, beta) != null) return null
            }
            return create(resolved, thetaKGlobal)
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
                byRouteDrugPgml = immutableMap(canonical),
                totalDrugPgml = total.normalizePositiveZero(),
            )
        }
    }
}

enum class PkCalibrationGlobalState {
    READY,
    SCOPE_NOT_CONFIRMED,
    SHARED_INPUT_INVALID,
    SHARED_NUMERIC_FAILURE,
}

enum class PkRouteCalibrationDisplayState {
    POPULATION_NO_DOMINANT_LABS,
    POPULATION_INSUFFICIENT_DOMINANT_LABS,
    POPULATION_LOW_CONFIDENCE,
    POPULATION_DISPLAY_CAP_EXCEEDED,
    POPULATION_NUMERIC_FAILURE,
    LAB_ADJUSTED_PROVISIONAL,
    LAB_CALIBRATED,
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

enum class PkCalibrationReason {
    SCOPE_NOT_CONFIRMED,
    SHARED_INPUT_INVALID,
    INVALID_NONPOSITIVE_E2,
    NO_DOMINANT_LABS,
    INSUFFICIENT_DOMINANT_LABS,
    EXTREME_SCALE_REQUIRES_THREE_DOMINANT_LABS,
    INSUFFICIENT_DRUG_SIGNAL_CONTRAST,
    POSTERIOR_SD_TOO_WIDE,
    DISPLAY_SCALE_EXCEEDED,
    DISPLAY_SCALE_AT_BOUNDARY,
    POSTERIOR_MODE_AMBIGUOUS,
    RESIDUAL_FIT_POOR,
    UNREVIEWED_OUTLIER,
    BAND_NUMERIC_FAILURE,
    NUMERIC_FAILURE,
}

/** Canonical SHA-256 digest value used at trust and cache boundaries. */
@ConsistentCopyVisibility
data class CanonicalDigest private constructor(
    val schema: String,
    val algorithm: String,
    val hexLower: String,
) {
    companion object {
        private val Sha256Hex = Regex("[0-9a-f]{64}")

        fun create(
            schema: String,
            algorithm: String,
            hexLower: String,
        ): CanonicalDigest? {
            if (schema.isBlank()) return null
            if (algorithm != "SHA-256") return null
            if (!Sha256Hex.matches(hexLower)) return null
            return CanonicalDigest(schema, algorithm, hexLower)
        }
    }
}

@ConsistentCopyVisibility
data class PkRouteCalibrationResult private constructor(
    val route: PkCalibrationRoute,
    val fittedBeta: Double?,
    val displayBeta: Double,
    val betaPosteriorSd: Double?,
    val betaUncertaintyReduction: Double?,
    val laplaceVarianceBeta: Double?,
    val displayState: PkRouteCalibrationDisplayState,
    val reasons: Set<PkCalibrationReason>,
    val dominantCandidateLabCount: Int,
    val dominantLabCount: Int,
    val drugSignalLogRange: Double?,
    val robustRmseLog: Double?,
    val minStudentTWeight: Double?,
    val atDisplayCapBoundary: Boolean,
    val unreviewedOutlierLabIds: Set<UUID>,
) {
    companion object {
        fun create(
            route: PkCalibrationRoute,
            fittedBeta: Double? = null,
            displayBeta: Double = 0.0,
            betaPosteriorSd: Double? = null,
            betaUncertaintyReduction: Double? = null,
            laplaceVarianceBeta: Double? = null,
            displayState: PkRouteCalibrationDisplayState,
            reasons: Set<PkCalibrationReason> = emptySet(),
            dominantCandidateLabCount: Int = 0,
            dominantLabCount: Int = 0,
            drugSignalLogRange: Double? = null,
            robustRmseLog: Double? = null,
            minStudentTWeight: Double? = null,
            atDisplayCapBoundary: Boolean = false,
            unreviewedOutlierLabIds: Set<UUID> = emptySet(),
            rLog: Double? = null,
        ): PkRouteCalibrationResult? {
            if (!isFiniteOrNull(fittedBeta)) return null
            if (!displayBeta.isFinite()) return null
            if (!isFiniteNonNegativeOrNull(betaPosteriorSd)) return null
            if (!isFiniteNonNegativeOrNull(betaUncertaintyReduction)) return null
            if (!isFiniteNonNegativeOrNull(laplaceVarianceBeta)) return null
            if (!isFiniteNonNegativeOrNull(drugSignalLogRange)) return null
            if (!isFiniteNonNegativeOrNull(robustRmseLog)) return null
            val rmseGate = if (robustRmseLog != null) {
                val noise = rLog?.takeIf { value -> value.isFinite() && value > 0.0 }
                    ?: return null
                PkCalibrationDefaults.robustRmseLogMaxForPromotion(noise)
                    .takeIf { value -> value.isFinite() && value > 0.0 } ?: return null
            } else {
                null
            }
            val maximumStudentTWeight =
                (PkCalibrationDefaults.STUDENT_T_NU + 1.0) /
                        PkCalibrationDefaults.STUDENT_T_NU
            if (minStudentTWeight != null &&
                (!minStudentTWeight.isFinite() ||
                        minStudentTWeight !in 0.0..maximumStudentTWeight)
            ) {
                return null
            }
            if (dominantCandidateLabCount < 0 ||
                dominantLabCount !in 0..dominantCandidateLabCount
            ) {
                return null
            }

            val isPopulation = displayState.isPopulationState()
            if (isPopulation && displayBeta.toBits() != 0.0.toBits()) return null
            if (isPopulation) {
                val hasFitDiagnostics = betaPosteriorSd != null ||
                        betaUncertaintyReduction != null ||
                        laplaceVarianceBeta != null ||
                        drugSignalLogRange != null ||
                        robustRmseLog != null ||
                        minStudentTWeight != null ||
                        atDisplayCapBoundary ||
                        unreviewedOutlierLabIds.isNotEmpty()
                val numericFailureReasons = setOf(PkCalibrationReason.NUMERIC_FAILURE)
                if (PkCalibrationReason.NUMERIC_FAILURE in reasons) {
                    if (displayState !=
                        PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE
                    ) {
                        return null
                    }
                    if (reasons != numericFailureReasons ||
                        fittedBeta != null ||
                        hasFitDiagnostics
                    ) {
                        return null
                    }
                } else if (dominantCandidateLabCount == 0) {
                    if (displayState !=
                        PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS ||
                        reasons != setOf(PkCalibrationReason.NO_DOMINANT_LABS) ||
                        fittedBeta != null ||
                        hasFitDiagnostics
                    ) {
                        return null
                    }
                } else if (
                    dominantLabCount <
                    PkCalibrationDefaults.MIN_DOMINANT_LABS_FOR_PROMOTION
                ) {
                    if (displayState !=
                        PkRouteCalibrationDisplayState
                            .POPULATION_INSUFFICIENT_DOMINANT_LABS ||
                        reasons != setOf(PkCalibrationReason.INSUFFICIENT_DOMINANT_LABS) ||
                        fittedBeta != null ||
                        hasFitDiagnostics
                    ) {
                        return null
                    }
                } else if (fittedBeta == null) {
                    if (displayState !=
                        PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE ||
                        reasons != setOf(PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS) ||
                        hasFitDiagnostics
                    ) {
                        return null
                    }
                } else {
                    val fittedScale = exp(fittedBeta)
                    if (!fittedScale.isFinite() || fittedScale <= 0.0) return null
                    if (betaPosteriorSd != null && betaPosteriorSd <= 0.0) return null
                    if (laplaceVarianceBeta != null && laplaceVarianceBeta <= 0.0) return null

                    val scaleCap = PkCalibrationDefaults.DISPLAY_SCALE_CAP_BY_ROUTE[route]
                        ?: return null
                    val isOutsideCap =
                        fittedScale !in scaleCap.minInclusive..scaleCap.maxInclusive
                    val isAtCapBoundary = fittedScale == scaleCap.minInclusive ||
                            fittedScale == scaleCap.maxInclusive
                    val lacksExtremeCount = (
                            fittedScale < PkCalibrationDefaults.EXTREME_SCALE_CORE_MIN ||
                                    fittedScale >
                                    PkCalibrationDefaults.EXTREME_SCALE_CORE_MAX
                            ) &&
                            dominantLabCount <
                            PkCalibrationDefaults.MIN_DOMINANT_LABS_FOR_EXTREME_SCALE
                    val hasPoorFit = robustRmseLog != null && rmseGate != null &&
                            robustRmseLog > rmseGate
                    val hasUnreviewedOutlier = unreviewedOutlierLabIds.isNotEmpty()
                    val hasInsufficientContrast = drugSignalLogRange != null &&
                            drugSignalLogRange <
                            PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN
                    val hasWidePosterior = betaPosteriorSd != null &&
                            betaPosteriorSd >
                            PkCalibrationDefaults
                                .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION

                    val applicableReasons = linkedSetOf<PkCalibrationReason>()
                    if (isOutsideCap) {
                        applicableReasons += PkCalibrationReason.DISPLAY_SCALE_EXCEEDED
                    }
                    if (isAtCapBoundary) {
                        applicableReasons += PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY
                    }
                    if (lacksExtremeCount) {
                        applicableReasons +=
                            PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_DOMINANT_LABS
                    }
                    if (hasPoorFit) {
                        applicableReasons += PkCalibrationReason.RESIDUAL_FIT_POOR
                    }
                    if (hasUnreviewedOutlier) {
                        applicableReasons += PkCalibrationReason.UNREVIEWED_OUTLIER
                    }
                    if (hasInsufficientContrast) {
                        applicableReasons +=
                            PkCalibrationReason.INSUFFICIENT_DRUG_SIGNAL_CONTRAST
                    }
                    if (hasWidePosterior) {
                        applicableReasons += PkCalibrationReason.POSTERIOR_SD_TOO_WIDE
                    }
                    if (reasons != applicableReasons) return null
                    if (atDisplayCapBoundary != isAtCapBoundary) return null

                    val expectedDisplayState = when {
                        isOutsideCap ->
                            PkRouteCalibrationDisplayState
                                .POPULATION_DISPLAY_CAP_EXCEEDED
                        lacksExtremeCount ->
                            PkRouteCalibrationDisplayState
                                .POPULATION_INSUFFICIENT_DOMINANT_LABS
                        hasPoorFit || hasUnreviewedOutlier ->
                            PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE
                        else -> null
                    }
                    if (displayState != expectedDisplayState) return null
                }
            } else {
                val displayScale = exp(displayBeta)
                if (!displayScale.isFinite() || displayScale <= 0.0) return null
                val certifiedBeta = fittedBeta ?: return null
                if (certifiedBeta.toBits() != displayBeta.normalizePositiveZero().toBits()) {
                    return null
                }
                val posteriorSd = betaPosteriorSd ?: return null
                if (posteriorSd <= 0.0) return null
                val varianceBeta = laplaceVarianceBeta ?: return null
                if (varianceBeta <= 0.0) return null

                val scaleCap = PkCalibrationDefaults.DISPLAY_SCALE_CAP_BY_ROUTE[route]
                    ?: return null
                if (displayScale !in scaleCap.minInclusive..scaleCap.maxInclusive) return null
                val isAtCapBoundary = displayScale == scaleCap.minInclusive ||
                        displayScale == scaleCap.maxInclusive
                if (atDisplayCapBoundary != isAtCapBoundary) return null

                val requiredLabCount = if (
                    displayScale < PkCalibrationDefaults.EXTREME_SCALE_CORE_MIN ||
                    displayScale > PkCalibrationDefaults.EXTREME_SCALE_CORE_MAX
                ) {
                    PkCalibrationDefaults.MIN_DOMINANT_LABS_FOR_EXTREME_SCALE
                } else {
                    PkCalibrationDefaults.MIN_DOMINANT_LABS_FOR_PROMOTION
                }
                if (dominantLabCount < requiredLabCount) return null

                val rmseLog = robustRmseLog ?: return null
                if (rmseLog > (rmseGate ?: return null)) return null
                if (unreviewedOutlierLabIds.isNotEmpty()) return null
                if (PkCalibrationReason.UNREVIEWED_OUTLIER in reasons) return null

                val hasInsufficientContrast = drugSignalLogRange != null &&
                        drugSignalLogRange <
                        PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN
                val hasWidePosterior = posteriorSd >
                        PkCalibrationDefaults
                            .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION
                val applicableReasons = linkedSetOf<PkCalibrationReason>()
                if (isAtCapBoundary) {
                    applicableReasons += PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY
                }
                if (hasInsufficientContrast) {
                    applicableReasons +=
                        PkCalibrationReason.INSUFFICIENT_DRUG_SIGNAL_CONTRAST
                }
                if (hasWidePosterior) {
                    applicableReasons += PkCalibrationReason.POSTERIOR_SD_TOO_WIDE
                }
                if (reasons != applicableReasons) return null

                val hasFullCalibrationGateFailure =
                    hasInsufficientContrast || hasWidePosterior
                if ((displayState ==
                        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL) !=
                    hasFullCalibrationGateFailure
                ) {
                    return null
                }
                if (displayState == PkRouteCalibrationDisplayState.LAB_CALIBRATED &&
                    drugSignalLogRange == null
                ) {
                    return null
                }
            }

            return PkRouteCalibrationResult(
                route = route,
                fittedBeta = fittedBeta,
                displayBeta = displayBeta.normalizePositiveZero(),
                betaPosteriorSd = betaPosteriorSd,
                betaUncertaintyReduction = betaUncertaintyReduction,
                laplaceVarianceBeta = laplaceVarianceBeta,
                displayState = displayState,
                reasons = immutableSet(reasons),
                dominantCandidateLabCount = dominantCandidateLabCount,
                dominantLabCount = dominantLabCount,
                drugSignalLogRange = drugSignalLogRange,
                robustRmseLog = robustRmseLog,
                minStudentTWeight = minStudentTWeight,
                atDisplayCapBoundary = atDisplayCapBoundary,
                unreviewedOutlierLabIds = immutableSet(unreviewedOutlierLabIds),
            )
        }
    }
}

@ConsistentCopyVisibility
data class PkCurvePoint private constructor(
    val epochMillis: Long,
    val concentrationPgMl: Double,
) {
    companion object {
        fun create(epochMillis: Long, concentrationPgMl: Double): PkCurvePoint? {
            if (!concentrationPgMl.isFinite() || concentrationPgMl < 0.0) return null
            return PkCurvePoint(epochMillis, concentrationPgMl.normalizePositiveZero())
        }
    }
}

@ConsistentCopyVisibility
data class PkPredictiveBandKnot private constructor(
    val epochMillis: Long,
    val p025Pgml: Double,
    val p158655254Pgml: Double,
    val p50Pgml: Double,
    val p841344746Pgml: Double,
    val p975Pgml: Double,
) {
    companion object {
        fun create(
            epochMillis: Long,
            p025Pgml: Double,
            p158655254Pgml: Double,
            p50Pgml: Double,
            p841344746Pgml: Double,
            p975Pgml: Double,
        ): PkPredictiveBandKnot? {
            val quantiles = listOf(
                p025Pgml,
                p158655254Pgml,
                p50Pgml,
                p841344746Pgml,
                p975Pgml,
            )
            if (quantiles.any { value -> !value.isFinite() || value < 0.0 }) return null
            if (quantiles.zipWithNext().any { (left, right) -> left > right }) return null
            return PkPredictiveBandKnot(
                epochMillis = epochMillis,
                p025Pgml = p025Pgml.normalizePositiveZero(),
                p158655254Pgml = p158655254Pgml.normalizePositiveZero(),
                p50Pgml = p50Pgml.normalizePositiveZero(),
                p841344746Pgml = p841344746Pgml.normalizePositiveZero(),
                p975Pgml = p975Pgml.normalizePositiveZero(),
            )
        }
    }
}

@ConsistentCopyVisibility
data class PkCalibrationResult private constructor(
    val globalState: PkCalibrationGlobalState,
    val globalReasons: Set<PkCalibrationReason>,
    val routeResults: List<PkRouteCalibrationResult>,
    val promotedRoutes: List<PkCalibrationRoute>,
    val displayParams: PkPersonalParams,
    val forwardModelVersion: String,
    val calibrationModelVersion: String,
) {
    companion object {
        fun create(
            globalState: PkCalibrationGlobalState,
            globalReasons: Set<PkCalibrationReason> = emptySet(),
            routeResults: List<PkRouteCalibrationResult> = emptyList(),
            promotedRoutes: List<PkCalibrationRoute> = emptyList(),
            displayParams: PkPersonalParams = PkPersonalParams.population(),
            forwardModelVersion: String,
            calibrationModelVersion: String,
        ): PkCalibrationResult? {
            if (forwardModelVersion.isBlank() || calibrationModelVersion.isBlank()) return null
            if (globalState != PkCalibrationGlobalState.READY) {
                if (routeResults.isNotEmpty() || promotedRoutes.isNotEmpty()) return null
                if (displayParams != PkPersonalParams.population()) return null
            } else {
                val expectedRoutes = PkCalibrationRoute.entries
                if (routeResults.map(PkRouteCalibrationResult::route) != expectedRoutes) return null

                val expectedPromotedRoutes = routeResults
                    .filter { routeResult -> !routeResult.displayState.isPopulationState() }
                    .map(PkRouteCalibrationResult::route)
                if (promotedRoutes != expectedPromotedRoutes) return null
                if (!promotedRoutes.isCanonicalRouteSubset()) return null

                val expectedNonZeroBetas = linkedMapOf<PkCalibrationRoute, Double>()
                for (routeResult in routeResults) {
                    if (routeResult.route in promotedRoutes && routeResult.displayBeta != 0.0) {
                        expectedNonZeroBetas[routeResult.route] = routeResult.displayBeta
                    }
                }
                if (displayParams.routeLogScale != expectedNonZeroBetas) return null
            }

            return PkCalibrationResult(
                globalState = globalState,
                globalReasons = immutableSet(globalReasons),
                routeResults = immutableList(routeResults),
                promotedRoutes = immutableList(promotedRoutes),
                displayParams = displayParams,
                forwardModelVersion = forwardModelVersion,
                calibrationModelVersion = calibrationModelVersion,
            )
        }
    }
}

@ConsistentCopyVisibility
data class PkCalibrationRenderResult private constructor(
    val domainDigest: CanonicalDigest,
    val renderState: PkCalibrationRenderState,
    val renderReasons: Set<PkCalibrationReason>,
    val effectivePromotedRoutes: List<PkCalibrationRoute>,
    val effectiveDisplayParams: PkPersonalParams,
    val routeRenderFallbacks: List<PkCalibrationRoute>,
    val centralCurve: List<PkCurvePoint>,
    val bandState: PkCalibrationBandState,
    val bandReasons: Set<PkCalibrationReason>,
    val bandKnots: List<PkPredictiveBandKnot>,
) {
    companion object {
        fun create(
            domainDigest: CanonicalDigest,
            renderState: PkCalibrationRenderState,
            renderReasons: Set<PkCalibrationReason> = emptySet(),
            effectivePromotedRoutes: List<PkCalibrationRoute> = emptyList(),
            effectiveDisplayParams: PkPersonalParams = PkPersonalParams.population(),
            routeRenderFallbacks: List<PkCalibrationRoute> = emptyList(),
            centralCurve: List<PkCurvePoint>,
            bandState: PkCalibrationBandState,
            bandReasons: Set<PkCalibrationReason> = emptySet(),
            bandKnots: List<PkPredictiveBandKnot> = emptyList(),
        ): PkCalibrationRenderResult? {
            if (domainDigest.schema != PK_CALIBRATION_RENDER_DOMAIN_DIGEST_SCHEMA) return null
            if (!effectivePromotedRoutes.isCanonicalRouteSubset()) return null
            if (!routeRenderFallbacks.isCanonicalRouteSubset()) return null
            if (effectivePromotedRoutes.any(routeRenderFallbacks::contains)) return null
            if (effectiveDisplayParams.routeLogScale.keys.any { route ->
                    route !in effectivePromotedRoutes
                }
            ) {
                return null
            }
            if (!centralCurve.hasStrictlyIncreasingTimes(PkCurvePoint::epochMillis)) return null
            if (!bandKnots.hasStrictlyIncreasingTimes(PkPredictiveBandKnot::epochMillis)) return null

            val isCentralUnavailable = renderState == PkCalibrationRenderState.NUMERIC_UNAVAILABLE
            if (centralCurve.isEmpty() != isCentralUnavailable) return null
            if (isCentralUnavailable) {
                if (PkCalibrationReason.NUMERIC_FAILURE !in renderReasons) return null
                if (effectivePromotedRoutes.isNotEmpty()) return null
                if (effectiveDisplayParams != PkPersonalParams.population()) return null
                if (routeRenderFallbacks.isNotEmpty()) return null
                if (bandState != PkCalibrationBandState.NOT_APPLICABLE_POPULATION) return null
            } else if (renderState == PkCalibrationRenderState.PERSONALIZED) {
                if (effectivePromotedRoutes.isEmpty()) return null
            } else if (effectivePromotedRoutes.isNotEmpty()) {
                return null
            }
            if (!isCentralUnavailable && PkCalibrationReason.NUMERIC_FAILURE in renderReasons) {
                return null
            }

            when (bandState) {
                PkCalibrationBandState.NOT_APPLICABLE_POPULATION -> {
                    if (effectivePromotedRoutes.isNotEmpty() || bandKnots.isNotEmpty()) return null
                    if (bandReasons.isNotEmpty()) return null
                }

                PkCalibrationBandState.READY -> {
                    if (effectivePromotedRoutes.isEmpty() || bandKnots.isEmpty()) return null
                    if (bandReasons.isNotEmpty()) return null
                }

                PkCalibrationBandState.NUMERIC_UNAVAILABLE -> {
                    if (renderState != PkCalibrationRenderState.PERSONALIZED) return null
                    if (PkCalibrationReason.BAND_NUMERIC_FAILURE !in bandReasons) return null
                    if (bandKnots.isNotEmpty()) return null
                    if (effectivePromotedRoutes.isEmpty()) return null
                }
            }

            return PkCalibrationRenderResult(
                domainDigest = domainDigest,
                renderState = renderState,
                renderReasons = immutableSet(renderReasons),
                effectivePromotedRoutes = immutableList(effectivePromotedRoutes),
                effectiveDisplayParams = effectiveDisplayParams,
                routeRenderFallbacks = immutableList(routeRenderFallbacks),
                centralCurve = immutableList(centralCurve),
                bandState = bandState,
                bandReasons = immutableSet(bandReasons),
                bandKnots = immutableList(bandKnots),
            )
        }
    }
}

private fun PkRouteCalibrationDisplayState.isPopulationState(): Boolean {
    return this != PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL &&
            this != PkRouteCalibrationDisplayState.LAB_CALIBRATED
}

private fun List<PkCalibrationRoute>.isCanonicalRouteSubset(): Boolean {
    if (distinct().size != size) return false
    return this == PkCalibrationRoute.entries.filter(::contains)
}

private fun <T> List<T>.hasStrictlyIncreasingTimes(epochMillis: (T) -> Long): Boolean {
    return zipWithNext().none { (left, right) -> epochMillis(left) >= epochMillis(right) }
}

private fun isFiniteOrNull(value: Double?): Boolean = value == null || value.isFinite()

private fun isFiniteNonNegativeOrNull(value: Double?): Boolean {
    return value == null || (value.isFinite() && value >= 0.0)
}

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
