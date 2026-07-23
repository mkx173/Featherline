package com.mkx.hrttracker.ui.pkcalibrationdebug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mkx.hrttracker.BuildConfig
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkCurvePoint
import com.mkx.hrttracker.model.pk.PkPredictiveBandKnot
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.NavigationLockEffect
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.pinnedTopAppBarScrollBehavior
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PkCalibrationDebugScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PkCalibrationDebugViewModel = hiltViewModel(),
) {
    if (!BuildConfig.DEBUG) return

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NavigationLockEffect(active = uiState.liveReviewActionInFlight)
    val scrollState = rememberScrollState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(
        scrollState = scrollState,
        state = topAppBarState,
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior, scrollState),
                title = { Text("Calibration (debug)") },
                navigationIcon = {
                    IconButton(
                        enabled = !uiState.liveReviewActionInFlight,
                        onClick = onNavigateBack,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.paddingBehindTopAppBar(innerPadding)) {
            PkCalibrationDebugBody(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(appContentPaddingValuesBehindTopAppBar(innerPadding)),
            )
        }
    }
}

@Composable
internal fun PkCalibrationDebugBody(
    uiState: PkCalibrationDebugUiState,
    viewModel: PkCalibrationDebugViewModel,
    modifier: Modifier = Modifier,
) {
    val controlsEnabled = !uiState.liveReviewActionInFlight
    val scenario = uiState.scenario
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag(PkCalibrationDebugBodyTag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("mode=${uiState.mode}")
        Text("loadState=${uiState.loadState}")
        Text("sourceUnavailableReason=${uiState.sourceUnavailableReason}")
        Text("snapshotKind=${uiState.snapshotKind}")
        Text("globalState=${uiState.globalState}")
        Text("globalReasons=${uiState.globalReasons}")
        Text("liveReviewActionInFlight=${uiState.liveReviewActionInFlight}")

        Text("Force global state")
        PkCalibrationGlobalState.entries.forEach { globalState ->
            DebugControlButton(
                label = globalState.name,
                enabled = controlsEnabled,
                onClick = { viewModel.selectGlobalState(globalState) },
            )
        }

        val routeRows = pkCalibrationDebugVisibleRouteRows(uiState)
        routeRows.forEach { routeResult ->
            Text(
                text = routeResult.rawDebugRowText(),
                modifier = Modifier.testTag(PkCalibrationDebugRouteRowTag),
            )
            if (routeResult.unreviewedOutlierLabIds.isNotEmpty()) {
                Text("outlierIds=${routeResult.unreviewedOutlierLabIds}")
            }
        }

        uiState.applicableActionCommands.forEach { command ->
            Button(
                enabled = controlsEnabled,
                modifier = Modifier.testTag(PkCalibrationDebugReviewActionTag),
                onClick = { viewModel.performReviewAction(command) },
            ) {
                Text("${command.action.buttonLabel()} ${command.resultId}")
            }
        }

        Text("renderState=${uiState.renderState}")
        Text("bandState=${uiState.bandState}")
        Text("effectiveDisplayParams=${uiState.effectiveDisplayParams}")
        Text("routeRenderFallbacks=${uiState.routeRenderFallbacks}")
        PkCalibrationDebugCurveCanvas(
            centralCurve = uiState.centralCurve,
            bandKnots = uiState.bandKnots,
        )

        Text("Action log")
        uiState.actionLog.forEach { entry ->
            Text("${entry.sequence}: ${entry.cause} -> ${entry.effect}")
        }

        Text("Presets")
        PkCalibrationDebugPreset.entries.forEach { preset ->
            DebugControlButton(
                label = preset.name,
                enabled = controlsEnabled,
                onClick = { viewModel.applyPreset(preset) },
            )
        }

        Text("Route display states")
        PkCalibrationRoute.entries.forEach { route ->
            Text(route.name)
            PkRouteCalibrationDisplayState.entries.forEach { displayState ->
                DebugControlButton(
                    label = "${route.name}: ${displayState.name}",
                    enabled = controlsEnabled,
                    onClick = { viewModel.selectRouteState(route, displayState) },
                )
            }
        }

        Text("Route render fallback")
        DebugControlButton(
            label = "Route render fallback: none",
            enabled = controlsEnabled,
            onClick = { viewModel.setRouteRenderFallback(null) },
        )
        PkCalibrationRoute.entries.forEach { route ->
            DebugControlButton(
                label = "Route render fallback: ${route.name}",
                enabled = controlsEnabled,
                onClick = { viewModel.setRouteRenderFallback(route) },
            )
        }

        DebugControlButton(
            label = "Band unavailable: ${scenario?.bandUnavailable == true}",
            enabled = controlsEnabled,
            onClick = {
                viewModel.setBandUnavailable(!(scenario?.bandUnavailable ?: false))
            },
        )
        DebugControlButton(
            label = "Central unavailable: ${scenario?.centralUnavailable == true}",
            enabled = controlsEnabled,
            onClick = {
                viewModel.setCentralUnavailable(!(scenario?.centralUnavailable ?: false))
            },
        )

        Text("Outlier route")
        DebugControlButton(
            label = "Outlier route: none",
            enabled = controlsEnabled,
            onClick = { viewModel.setOutlierRoute(null) },
        )
        PkCalibrationRoute.entries.forEach { route ->
            DebugControlButton(
                label = "Outlier route: ${route.name}",
                enabled = controlsEnabled,
                onClick = { viewModel.setOutlierRoute(route) },
            )
        }

        DebugControlButton(
            label = "Nonpositive input: ${scenario?.nonPositiveInput == true}",
            enabled = controlsEnabled,
            onClick = {
                viewModel.setNonPositiveInput(!(scenario?.nonPositiveInput ?: false))
            },
        )

        Text("Guard-dropped route")
        DebugControlButton(
            label = "Guard-dropped route: none",
            enabled = controlsEnabled,
            onClick = { viewModel.setGuardDroppedRoute(null) },
        )
        PkCalibrationRoute.entries.forEach { route ->
            DebugControlButton(
                label = "Guard-dropped route: ${route.name}",
                enabled = controlsEnabled,
                onClick = { viewModel.setGuardDroppedRoute(route) },
            )
        }

        Text("Display-cap-boundary route")
        DebugControlButton(
            label = "Display-cap-boundary route: none",
            enabled = controlsEnabled,
            onClick = { viewModel.setDisplayCapBoundaryRoute(null) },
        )
        PkCalibrationRoute.entries.forEach { route ->
            DebugControlButton(
                label = "Display-cap-boundary route: ${route.name}",
                enabled = controlsEnabled,
                onClick = { viewModel.setDisplayCapBoundaryRoute(route) },
            )
        }

        DebugControlButton(
            label = "Reset / live",
            enabled = controlsEnabled,
            onClick = { viewModel.resetToLive() },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DebugControlButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        enabled = enabled,
        modifier = Modifier.testTag(PkCalibrationDebugControlTag),
        onClick = onClick,
    ) {
        Text(label)
    }
}

@Composable
private fun PkCalibrationDebugCurveCanvas(
    centralCurve: List<PkCurvePoint>,
    bandKnots: List<PkPredictiveBandKnot>,
) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .testTag(PkCalibrationDebugCurveTag)
            .semantics { contentDescription = "Calibration curves" },
    ) {
        val geometry = pkCalibrationDebugChartGeometry(
            centralCurve = centralCurve,
            bandKnots = bandKnots,
            width = size.width,
            height = size.height,
        )
        drawThinPolyline(geometry.central, lineColor)
        drawThinPolyline(geometry.lowerBand, lineColor)
        drawThinPolyline(geometry.upperBand, lineColor)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThinPolyline(
    points: List<PkCalibrationDebugPlotPoint>,
    color: Color,
) {
    points.zipWithNext().forEach { (start, end) ->
        drawLine(
            color = color,
            start = Offset(start.x, start.y),
            end = Offset(end.x, end.y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

internal fun pkCalibrationDebugVisibleRouteRows(
    uiState: PkCalibrationDebugUiState,
): List<PkRouteCalibrationResult> {
    if (uiState.globalState != PkCalibrationGlobalState.READY) return emptyList()
    if (uiState.routeRows.map(PkRouteCalibrationResult::route) !=
        PkCalibrationRoute.entries.toList()
    ) {
        return emptyList()
    }
    return uiState.routeRows
}

private fun PkRouteCalibrationResult.rawDebugRowText(): String {
    return "$route · $displayState · displayBeta=$displayBeta · " +
            "dominantLabCount=$dominantLabCount · " +
            "dominantCandidateLabCount=$dominantCandidateLabCount · " +
            "atDisplayCapBoundary=$atDisplayCapBoundary · " +
            "hasUnreviewedOutlier=${unreviewedOutlierLabIds.isNotEmpty()}"
}

private fun PkCalibrationDebugReviewAction.buttonLabel(): String = when (this) {
    PkCalibrationDebugReviewAction.KEEP -> "Keep"
    PkCalibrationDebugReviewAction.EXCLUDE -> "Exclude"
    PkCalibrationDebugReviewAction.REINCLUDE -> "Re-include"
}

internal data class PkCalibrationDebugPlotPoint(
    val x: Float,
    val y: Float,
)

internal data class PkCalibrationDebugChartGeometry(
    val central: List<PkCalibrationDebugPlotPoint>,
    val lowerBand: List<PkCalibrationDebugPlotPoint>,
    val upperBand: List<PkCalibrationDebugPlotPoint>,
)

internal fun pkCalibrationDebugChartGeometry(
    centralCurve: List<PkCurvePoint>,
    bandKnots: List<PkPredictiveBandKnot>,
    width: Float,
    height: Float,
): PkCalibrationDebugChartGeometry {
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
        return PkCalibrationDebugChartGeometry(emptyList(), emptyList(), emptyList())
    }
    val epochMillis = centralCurve.map(PkCurvePoint::epochMillis) +
            bandKnots.map(PkPredictiveBandKnot::epochMillis)
    val concentrations = centralCurve.map(PkCurvePoint::concentrationPgMl) +
            bandKnots.flatMap { knot -> listOf(knot.p025Pgml, knot.p975Pgml) }
    if (epochMillis.isEmpty() || concentrations.isEmpty()) {
        return PkCalibrationDebugChartGeometry(emptyList(), emptyList(), emptyList())
    }
    val minEpochMillis = epochMillis.min()
    val maxEpochMillis = epochMillis.max()
    val minConcentration = concentrations.min()
    val maxConcentration = concentrations.max()

    fun point(epochMillis: Long, concentration: Double): PkCalibrationDebugPlotPoint {
        val x = if (maxEpochMillis == minEpochMillis) {
            width / 2f
        } else {
            (((epochMillis - minEpochMillis).toDouble() /
                    (maxEpochMillis - minEpochMillis).toDouble()) * width).toFloat()
        }
        val y = if (maxConcentration == minConcentration) {
            height / 2f
        } else {
            (height - (((concentration - minConcentration) /
                    (maxConcentration - minConcentration)) * height)).toFloat()
        }
        return PkCalibrationDebugPlotPoint(
            x = x.coerceIn(0f, width),
            y = y.coerceIn(0f, height),
        )
    }

    return PkCalibrationDebugChartGeometry(
        central = centralCurve.map { curvePoint ->
            point(curvePoint.epochMillis, curvePoint.concentrationPgMl)
        },
        lowerBand = bandKnots.map { knot -> point(knot.epochMillis, knot.p025Pgml) },
        upperBand = bandKnots.map { knot -> point(knot.epochMillis, knot.p975Pgml) },
    )
}

internal const val PkCalibrationDebugBodyTag = "pk-calibration-debug-body"
internal const val PkCalibrationDebugRouteRowTag = "pk-calibration-route-row"
internal const val PkCalibrationDebugControlTag = "pk-calibration-control"
internal const val PkCalibrationDebugReviewActionTag = "pk-calibration-review-action"
internal const val PkCalibrationDebugCurveTag = "pk-calibration-curve"
