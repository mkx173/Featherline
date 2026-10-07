package com.mkx.hrttracker.ui.pkcalibrationdebug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Non-debug builds carry no harness; the Settings entry that navigates here is DEBUG-gated. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun PkCalibrationDebugScreenEntry(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) = Unit
