package com.mkx.hrttracker.ui.calibration

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationDefaults
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import java.util.UUID

/** Hero presentation kind (UI handoff v9.0 §5.1). */
enum class PkCalibrationHeroKind {
    POPULATION,
    ADJUSTED,
}

/** Adjusted-group membership (handoff §5 table: the two `LAB_*` states). */
val PkRouteCalibrationDisplayState.isAdjusted: Boolean
    get() = this == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL ||
        this == PkRouteCalibrationDisplayState.LAB_CALIBRATED

/**
 * Coarse per-route confidence for adjusted routes (user decisions,
 * 2026-08-12), anchored to existing thresholds only.
 *
 * Consistency comes first: an outlier supporting the route means the fit
 * disagrees with a data point — never better than LOW, regardless of
 * posterior sharpness. Otherwise: HIGH is a route that raised no warning
 * (LAB_CALIBRATED); MEDIUM is provisional whose posterior already meets the
 * full-calibration sd threshold; LOW is provisional with a wider posterior.
 */
enum class PkCalibrationRouteConfidence { LOW, MEDIUM, HIGH }

fun pkRouteCalibrationConfidence(
    routeResult: PkRouteCalibrationResult,
): PkCalibrationRouteConfidence? {
    if (!routeResult.displayState.isAdjusted) return null
    val minWeight = routeResult.minStudentTWeight
    if (minWeight != null && minWeight < PkCalibrationDefaults.OUTLIER_WEIGHT_MIN) {
        return PkCalibrationRouteConfidence.LOW
    }
    return when {
        routeResult.displayState == PkRouteCalibrationDisplayState.LAB_CALIBRATED ->
            PkCalibrationRouteConfidence.HIGH

        (routeResult.betaPosteriorSd ?: return null) <=
            PkCalibrationDefaults.ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION ->
            PkCalibrationRouteConfidence.MEDIUM

        else -> PkCalibrationRouteConfidence.LOW
    }
}

/**
 * Fit-level route row for the calibration status surface. Deliberately carries
 * no diagnostic fit fields — `fittedBeta`, `displayBeta`, posterior/variance,
 * RMSE, weights, and contrast never reach the view layer (handoff §5.4). The
 * exclusion is enforced by reflection in `PkCalibrationUiStateTest`.
 */
data class PkCalibrationRouteRowUiState(
    val route: PkCalibrationRoute,
    val displayState: PkRouteCalibrationDisplayState,
    val reasons: Set<PkCalibrationReason>,
    val supportingLabCount: Int,
    val unreviewedOutlierLabIds: Set<UUID>,
    /** Coarse tier for adjusted routes; null on population rows. */
    val confidence: PkCalibrationRouteConfidence?,
) {
    /** A route whose fit raised a warning the user should read. */
    val hasWarning: Boolean
        get() = displayState == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL &&
            reasons.any { reason -> reason in WarningReasons }
}

private val WarningReasons = setOf(
    PkCalibrationReason.DISPLAY_SCALE_EXCEEDED,
    PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_SUPPORTING_LABS,
    PkCalibrationReason.RESIDUAL_FIT_POOR,
    PkCalibrationReason.UNREVIEWED_OUTLIER,
    PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS,
)

/**
 * The single validated view consumed by the Home hero, chart, and calibration
 * status surfaces. Built only by [pkCalibrationUiState]; screens must never
 * infer state from raw fitted parameters (handoff §3).
 */
data class PkCalibrationUiState(
    val globalState: PkCalibrationGlobalState,
    val globalReasons: Set<PkCalibrationReason>,
    val heroKind: PkCalibrationHeroKind,
    /** True when an effective promoted route is still provisional (§5.1). */
    val limitedConfidence: Boolean,
    /** Exactly five rows in canonical route order when READY, empty otherwise. */
    val routeRows: List<PkCalibrationRouteRowUiState>,
    /** Routes driving the hero for the current render, canonical order. */
    val effectivePromotedRoutes: List<PkCalibrationRoute>,
    /** Non-positive labs in a drug window; ignored by the fit and flagged on their row. */
    val invalidNonpositiveLabIds: Set<UUID>,
    val renderState: PkCalibrationRenderState,
    val bandState: PkCalibrationBandState,
    val routeRenderFallbacks: List<PkCalibrationRoute>,
)

/**
 * [PkCalibrationUiState] plus the per-lab review context the lab list needs
 * (excluded dispositions come from the same atomic evaluation snapshot).
 */
data class PkCalibrationScreenState(
    val ui: PkCalibrationUiState,
    val excludedResultIds: Set<UUID>,
)

/** Review affordance shown as a footer on one lab panel row (§4.2, §10). */
sealed interface PkCalibrationLabRowFlag {
    val resultId: UUID

    data class InvalidNonPositive(override val resultId: UUID) : PkCalibrationLabRowFlag

    data class UnreviewedOutlier(
        override val resultId: UUID,
        /** Every route whose fit this lab disagrees with. */
        val affectedRoutes: List<PkCalibrationRoute>,
    ) : PkCalibrationLabRowFlag

    data class Excluded(override val resultId: UUID) : PkCalibrationLabRowFlag
}

/**
 * Derives the per-panel review footer, keyed by panel uuid. Explicit exclusion
 * wins; the invalid non-positive footer comes from the engine's per-lab
 * classification, so a non-positive value drawn in a no-drug window
 * (engine-classified Unassigned) is never flagged.
 */
fun pkCalibrationLabRowFlags(
    state: PkCalibrationScreenState,
    panels: List<BloodTestPanel>,
): Map<UUID, PkCalibrationLabRowFlag> {
    val outlierRoutes = linkedMapOf<UUID, MutableList<PkCalibrationRoute>>()
    state.ui.routeRows.forEach { row ->
        row.unreviewedOutlierLabIds.forEach { resultId ->
            outlierRoutes.getOrPut(resultId) { mutableListOf() }.add(row.route)
        }
    }
    val flags = linkedMapOf<UUID, PkCalibrationLabRowFlag>()
    panels.forEach { panel ->
        val e2Result = panel.results.firstOrNull { result ->
            (result.analyte as? BloodTestResultAnalyte.Builtin)?.key == BloodAnalyteKey.E2
        } ?: return@forEach
        val resultId = e2Result.uuid
        val flag = when {
            resultId in state.excludedResultIds ->
                PkCalibrationLabRowFlag.Excluded(resultId)

            resultId in state.ui.invalidNonpositiveLabIds ->
                PkCalibrationLabRowFlag.InvalidNonPositive(resultId)

            resultId in outlierRoutes ->
                PkCalibrationLabRowFlag.UnreviewedOutlier(
                    resultId = resultId,
                    affectedRoutes = outlierRoutes.getValue(resultId),
                )

            else -> null
        }
        if (flag != null) {
            flags[panel.uuid] = flag
        }
    }
    return flags
}

/**
 * Contract → UI projection (Phase-2 plan §2.1). Status rows come from the fit
 * result only; the render result contributes render/band/fallback state and
 * narrows the hero's effective promotion — it never rewrites a route row
 * (handoff §11, §13.5). [render] is null for every non-READY evaluation and
 * when no chart domain exists; the hero then uses fit-level promotion.
 */
fun pkCalibrationUiState(
    result: PkCalibrationResult,
    render: PkCalibrationRenderResult?,
): PkCalibrationUiState {
    val ready = result.globalState == PkCalibrationGlobalState.READY
    val effectivePromoted = when {
        !ready -> emptyList()
        render == null -> result.promotedRoutes
        else -> render.effectivePromotedRoutes
    }
    val provisionalRoutes = result.routeResults
        .filter { routeResult ->
            routeResult.displayState ==
                PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
        }
        .map { routeResult -> routeResult.route }
    return PkCalibrationUiState(
        globalState = result.globalState,
        globalReasons = result.globalReasons,
        heroKind = if (effectivePromoted.isEmpty()) {
            PkCalibrationHeroKind.POPULATION
        } else {
            PkCalibrationHeroKind.ADJUSTED
        },
        limitedConfidence = effectivePromoted.any(provisionalRoutes::contains),
        routeRows = result.routeResults.map { routeResult ->
            PkCalibrationRouteRowUiState(
                route = routeResult.route,
                displayState = routeResult.displayState,
                reasons = routeResult.reasons,
                supportingLabCount = routeResult.supportingLabCount,
                unreviewedOutlierLabIds = routeResult.unreviewedOutlierLabIds,
                confidence = pkRouteCalibrationConfidence(routeResult),
            )
        },
        effectivePromotedRoutes = effectivePromoted,
        invalidNonpositiveLabIds = result.invalidNonpositiveLabIds,
        renderState = render?.renderState ?: PkCalibrationRenderState.POPULATION,
        bandState = render?.bandState ?: PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
        routeRenderFallbacks = render?.routeRenderFallbacks.orEmpty(),
    )
}
