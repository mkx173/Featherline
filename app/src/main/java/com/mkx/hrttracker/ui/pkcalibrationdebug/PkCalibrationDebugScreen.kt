package com.mkx.hrttracker.ui.pkcalibrationdebug

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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mkx.hrttracker.BuildConfig
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.HazeTopAppBar
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
                    IconButton(onClick = onNavigateBack) {
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
    val scenario = uiState.scenario
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag(PkCalibrationDebugBodyTag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.forcedStateActive) {
            Text(
                text = "Forced state active — Home & Calibration show fixture data",
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                modifier = Modifier.testTag(PkCalibrationDebugResetTag),
                onClick = { viewModel.resetForcedState() },
            ) {
                Text("Reset forced state")
            }
        } else {
            Text("No forced state — pick a preset or control below to force one")
        }
        uiState.loadFailure?.let { reason ->
            Text(
                text = "Fixture load failed: $reason",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text("Presets")
        PkCalibrationDebugPreset.entries.forEach { preset ->
            DebugControlButton(
                label = preset.name,
                onClick = { viewModel.applyPreset(preset) },
            )
        }

        Text("Force global state")
        PkCalibrationGlobalState.entries.forEach { globalState ->
            DebugControlButton(
                label = globalState.name,
                onClick = { viewModel.selectGlobalState(globalState) },
            )
        }

        Text("Route display states")
        PkCalibrationRoute.entries.forEach { route ->
            Text(route.name)
            PkRouteCalibrationDisplayState.entries.forEach { displayState ->
                DebugControlButton(
                    label = "${route.name}: ${displayState.name}",
                    onClick = { viewModel.selectRouteState(route, displayState) },
                )
            }
        }

        Text("Route render fallback")
        DebugControlButton(
            label = "Route render fallback: none",
            onClick = { viewModel.setRouteRenderFallback(null) },
        )
        PkCalibrationRoute.entries.forEach { route ->
            DebugControlButton(
                label = "Route render fallback: ${route.name}",
                onClick = { viewModel.setRouteRenderFallback(route) },
            )
        }

        DebugControlButton(
            label = "Band unavailable: ${scenario?.bandUnavailable == true}",
            onClick = {
                viewModel.setBandUnavailable(!(scenario?.bandUnavailable ?: false))
            },
        )
        DebugControlButton(
            label = "Central unavailable: ${scenario?.centralUnavailable == true}",
            onClick = {
                viewModel.setCentralUnavailable(!(scenario?.centralUnavailable ?: false))
            },
        )

        Text("Outlier route")
        DebugControlButton(
            label = "Outlier route: none",
            onClick = { viewModel.setOutlierRoute(null) },
        )
        PkCalibrationRoute.entries.forEach { route ->
            DebugControlButton(
                label = "Outlier route: ${route.name}",
                onClick = { viewModel.setOutlierRoute(route) },
            )
        }

        DebugControlButton(
            label = "Nonpositive input: ${scenario?.nonPositiveInput == true}",
            onClick = {
                viewModel.setNonPositiveInput(!(scenario?.nonPositiveInput ?: false))
            },
        )

        if (uiState.applicableActionCommands.isNotEmpty()) {
            Text("Fixture review actions")
        }
        uiState.applicableActionCommands.forEach { command ->
            Button(
                modifier = Modifier.testTag(PkCalibrationDebugReviewActionTag),
                onClick = { viewModel.performReviewAction(command) },
            ) {
                Text("${command.action.buttonLabel()} ${command.resultId}")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DebugControlButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.testTag(PkCalibrationDebugControlTag),
        onClick = onClick,
    ) {
        Text(label)
    }
}

private fun PkCalibrationDebugReviewAction.buttonLabel(): String = when (this) {
    PkCalibrationDebugReviewAction.EXCLUDE -> "Exclude"
    PkCalibrationDebugReviewAction.REINCLUDE -> "Re-include"
}

internal const val PkCalibrationDebugBodyTag = "pk-calibration-debug-body"
internal const val PkCalibrationDebugControlTag = "pk-calibration-control"
internal const val PkCalibrationDebugReviewActionTag = "pk-calibration-review-action"
internal const val PkCalibrationDebugResetTag = "pk-calibration-reset"
