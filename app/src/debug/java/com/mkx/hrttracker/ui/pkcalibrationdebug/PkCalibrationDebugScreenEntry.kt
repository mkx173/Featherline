package com.mkx.hrttracker.ui.pkcalibrationdebug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Debug build: the Settings > "Calibration (debug)" route renders the harness. */
@Composable
fun PkCalibrationDebugScreenEntry(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    PkCalibrationDebugScreen(onNavigateBack = onNavigateBack, modifier = modifier)
}
