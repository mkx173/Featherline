package com.mkx.hrttracker.ui.calibration

import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import java.util.UUID

/** Fixtures shared by the `@Preview`s of the PK calibration components. */

internal fun previewPkRouteRow(
    route: PkCalibrationRoute,
    displayState: PkRouteCalibrationDisplayState = PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
    reasons: Set<PkCalibrationReason> = emptySet(),
    supportingLabCount: Int = 0,
    unreviewedOutlierLabIds: Set<UUID> = emptySet(),
    confidence: PkCalibrationRouteConfidence? = null,
): PkCalibrationRouteRowUiState {
    return PkCalibrationRouteRowUiState(
        route = route,
        displayState = displayState,
        reasons = reasons,
        supportingLabCount = supportingLabCount,
        unreviewedOutlierLabIds = unreviewedOutlierLabIds,
        confidence = confidence,
    )
}

/** Adjusted row with a clean fit. */
internal val previewPkCalibratedRow = previewPkRouteRow(
    route = PkCalibrationRoute.GEL,
    displayState = PkRouteCalibrationDisplayState.LAB_CALIBRATED,
    supportingLabCount = 3,
    confidence = PkCalibrationRouteConfidence.HIGH,
)

/** Adjusted row whose posterior meets the full-calibration threshold but raised a warning. */
internal val previewPkProvisionalMediumRow = previewPkRouteRow(
    route = PkCalibrationRoute.PATCH,
    displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
    reasons = setOf(PkCalibrationReason.UNCERTAIN),
    supportingLabCount = 1,
    confidence = PkCalibrationRouteConfidence.MEDIUM,
)

/** Adjusted row that disagrees with a lab: every warning reason at once. */
internal val previewPkProvisionalLowRow = previewPkRouteRow(
    route = PkCalibrationRoute.INJECTION,
    displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
    reasons = setOf(
        PkCalibrationReason.UNREVIEWED_OUTLIER,
        PkCalibrationReason.SCALE_OUTSIDE_USUAL_RANGE,
        PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS,
        PkCalibrationReason.RESIDUAL_FIT_POOR,
    ),
    supportingLabCount = 2,
    unreviewedOutlierLabIds = setOf(UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a")),
    confidence = PkCalibrationRouteConfidence.LOW,
)

internal val previewPkPopulationRows: List<PkCalibrationRouteRowUiState> =
    PkCalibrationRoute.entries.map { route -> previewPkRouteRow(route) }

internal val previewPkNumericFailureRows: List<PkCalibrationRouteRowUiState> =
    PkCalibrationRoute.entries.map { route ->
        previewPkRouteRow(route, PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE)
    }

/** Injection LOW, patch MEDIUM, gel HIGH; oral and sublingual population. */
internal val previewPkMixedRows: List<PkCalibrationRouteRowUiState> =
    PkCalibrationRoute.entries.map { route ->
        when (route) {
            PkCalibrationRoute.INJECTION -> previewPkProvisionalLowRow
            PkCalibrationRoute.PATCH -> previewPkProvisionalMediumRow
            PkCalibrationRoute.GEL -> previewPkCalibratedRow
            else -> previewPkRouteRow(route)
        }
    }

internal fun previewPkUiState(
    globalState: PkCalibrationGlobalState = PkCalibrationGlobalState.READY,
    routeRows: List<PkCalibrationRouteRowUiState> = previewPkPopulationRows,
    effectivePromotedRoutes: List<PkCalibrationRoute> = emptyList(),
    limitedConfidence: Boolean = false,
): PkCalibrationUiState {
    val adjusted = effectivePromotedRoutes.isNotEmpty()
    return PkCalibrationUiState(
        globalState = globalState,
        heroKind = if (adjusted) PkCalibrationHeroKind.ADJUSTED else PkCalibrationHeroKind.POPULATION,
        limitedConfidence = limitedConfidence,
        routeRows = if (globalState == PkCalibrationGlobalState.READY) routeRows else emptyList(),
        effectivePromotedRoutes = effectivePromotedRoutes,
        ignoredLabs = emptyMap(),
        renderState = if (adjusted) {
            PkCalibrationRenderState.PERSONALIZED
        } else {
            PkCalibrationRenderState.POPULATION
        },
        bandState = if (adjusted) {
            PkCalibrationBandState.READY
        } else {
            PkCalibrationBandState.NOT_APPLICABLE_POPULATION
        },
    )
}

/** Fit adjusted two routes with no warnings. */
internal val previewPkAdjustedUiState = previewPkUiState(
    routeRows = PkCalibrationRoute.entries.map { route ->
        if (route == PkCalibrationRoute.GEL || route == PkCalibrationRoute.INJECTION) {
            previewPkCalibratedRow.copy(route = route)
        } else {
            previewPkRouteRow(route)
        }
    },
    effectivePromotedRoutes = listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.GEL),
)

/** Fit adjusted three routes, one of them still provisional. */
internal val previewPkProvisionalUiState = previewPkUiState(
    routeRows = previewPkMixedRows,
    effectivePromotedRoutes = listOf(
        PkCalibrationRoute.INJECTION,
        PkCalibrationRoute.PATCH,
        PkCalibrationRoute.GEL,
    ),
    limitedConfidence = true,
)
