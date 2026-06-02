package com.mkx.hrttracker.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals

/**
 * Snackbar visuals for the stock-tracking nudge. Carries [onDismissTapped] so the
 * host can distinguish an explicit X-tap (which counts toward opt-out) from the
 * 5s auto-timeout (which does not). [HrtSnackbar] invokes [onDismissTapped] only
 * from the dismiss button, never from the countdown.
 */
class StockNudgeVisuals(
    override val message: String,
    override val actionLabel: String?,
    val onDismissTapped: () -> Unit,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = true
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}
