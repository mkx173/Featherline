package com.mkx.hrttracker.ui.calibration

import com.mkx.hrttracker.data.repository.PkCalibrationLive
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationDefaults
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationLabIgnoreReason
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import java.util.UUID

/**
 * Coarse per-route confidence for adjusted routes, anchored to the solver's
 * existing thresholds only.
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
 * no diagnostic fit fields: beta, posterior variance, RMSE and weights never
 * reach the view layer.
 */
data class PkCalibrationRouteRowUiState(
    val route: PkCalibrationRoute,
    val displayState: PkRouteCalibrationDisplayState,
    val reasons: Set<PkCalibrationReason>,
    val supportingLabCount: Int,
    val unreviewedOutlierLabIds: Set<UUID>,
    /** Coarse tier for adjusted routes; null on population rows. */
    val confidence: PkCalibrationRouteConfidence?,
)

/**
 * The single validated view consumed by the Home hero, chart, and calibration
 * status surfaces. Built only by [pkCalibrationUiState]; screens must never
 * infer state from raw fitted parameters.
 */
data class PkCalibrationUiState(
    val globalState: PkCalibrationGlobalState,
    /** Some supported route shapes the drawn curve with a lab adjustment. */
    val adjusted: Boolean,
    /** True when an effective promoted route is still provisional. */
    val limitedConfidence: Boolean,
    /** Exactly five rows in canonical route order when READY, empty otherwise. */
    val routeRows: List<PkCalibrationRouteRowUiState>,
    /** Supported routes driving the hero for the current render, canonical order. */
    val effectivePromotedRoutes: List<PkCalibrationRoute>,
    /** Labs the fit set aside, flagged on their row with the reason. */
    val ignoredLabs: Map<UUID, PkCalibrationLabIgnoreReason>,
    val renderState: PkCalibrationRenderState,
    val bandState: PkCalibrationBandState,
) {
    /**
     * The fit did not complete: either the forward model failed (global
     * NUMERIC_FAILURE) or the joint solve failed with the lab rows kept
     * (READY with every route at POPULATION_NUMERIC_FAILURE).
     */
    val numericFailure: Boolean
        get() = globalState == PkCalibrationGlobalState.NUMERIC_FAILURE ||
            (routeRows.isNotEmpty() && routeRows.all { row ->
                row.displayState == PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE
            })
}

/**
 * [PkCalibrationUiState] plus the per-lab review context the lab list needs
 * (excluded dispositions come from the same atomic evaluation snapshot).
 */
data class PkCalibrationScreenState(
    val ui: PkCalibrationUiState,
    val excludedResultIds: Set<UUID>,
    val acceptedResultIds: Set<UUID> = emptySet(),
)

/** Builds the screen state from one live evaluation; shared by the list and the result editor. */
fun pkCalibrationScreenState(live: PkCalibrationLive): PkCalibrationScreenState {
    return PkCalibrationScreenState(
        ui = pkCalibrationUiState(live.evaluation.result, live.render),
        excludedResultIds = live.input.excludedLabIds,
        acceptedResultIds = live.input.acceptedLabIds,
    )
}

/** Per-lab review state: a chip on the list row, the queue card, and the editor's section. */
sealed interface PkCalibrationLabRowFlag {
    val resultId: UUID

    data class Ignored(
        override val resultId: UUID,
        val reason: PkCalibrationLabIgnoreReason,
    ) : PkCalibrationLabRowFlag

    data class UnreviewedOutlier(
        override val resultId: UUID,
        /** Every route whose fit this lab disagrees with. */
        val affectedRoutes: List<PkCalibrationRoute>,
    ) : PkCalibrationLabRowFlag

    data class Accepted(override val resultId: UUID) : PkCalibrationLabRowFlag

    data class Excluded(override val resultId: UUID) : PkCalibrationLabRowFlag
}

/** Flags that ask the user to act; these, and only these, fill the review queue. */
val PkCalibrationLabRowFlag.needsReview: Boolean
    get() = this is PkCalibrationLabRowFlag.UnreviewedOutlier ||
        (this is PkCalibrationLabRowFlag.Ignored &&
            reason == PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE)

/** Derives the per-panel review flag, keyed by panel uuid. */
fun pkCalibrationLabRowFlags(
    state: PkCalibrationScreenState,
    panels: List<BloodTestPanel>,
): Map<UUID, PkCalibrationLabRowFlag> {
    val flags = linkedMapOf<UUID, PkCalibrationLabRowFlag>()
    panels.forEach { panel ->
        val e2Result = panel.results.firstOrNull { result ->
            (result.analyte as? BloodTestResultAnalyte.Builtin)?.key == BloodAnalyteKey.E2
        } ?: return@forEach
        pkCalibrationLabFlag(state, e2Result.uuid)?.let { flag -> flags[panel.uuid] = flag }
    }
    return flags
}

/** One E2 result's review flag. Explicit exclusion wins, then the fit's own ignore reason. */
fun pkCalibrationLabFlag(state: PkCalibrationScreenState, resultId: UUID): PkCalibrationLabRowFlag? {
    val affectedRoutes = state.ui.routeRows
        .filter { row -> resultId in row.unreviewedOutlierLabIds }
        .map { row -> row.route }
    return when {
        resultId in state.excludedResultIds -> PkCalibrationLabRowFlag.Excluded(resultId)

        resultId in state.ui.ignoredLabs ->
            PkCalibrationLabRowFlag.Ignored(resultId, state.ui.ignoredLabs.getValue(resultId))

        resultId in state.acceptedResultIds -> PkCalibrationLabRowFlag.Accepted(resultId)

        affectedRoutes.isNotEmpty() ->
            PkCalibrationLabRowFlag.UnreviewedOutlier(resultId, affectedRoutes)

        else -> null
    }
}

/**
 * Contract → UI projection. Status rows come from the fit result only; the
 * render result contributes render/band/fallback state and narrows the hero's
 * effective promotion — it never rewrites a route row.
 * [render] is null for every non-READY evaluation and
 * when no chart domain exists; the hero then uses fit-level promotion.
 */
fun pkCalibrationUiState(
    result: PkCalibrationResult,
    render: PkCalibrationRenderResult?,
): PkCalibrationUiState {
    val ready = result.globalState == PkCalibrationGlobalState.READY
    val supported = result.supportedPromotedRoutes
    val effectivePromoted = when {
        !ready -> emptyList()
        render == null -> supported
        else -> render.effectivePromotedRoutes.filter(supported::contains)
    }
    val provisionalRoutes = result.routeResults
        .filter { routeResult ->
            routeResult.displayState ==
                PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
        }
        .map { routeResult -> routeResult.route }
    return PkCalibrationUiState(
        globalState = result.globalState,
        adjusted = effectivePromoted.isNotEmpty(),
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
        ignoredLabs = result.ignoredLabs,
        renderState = render?.renderState ?: PkCalibrationRenderState.POPULATION,
        bandState = render?.bandState ?: PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
    )
}
