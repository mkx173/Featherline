package com.mkx.hrttracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R

private const val SNACKBAR_DURATION_MS = 5_000
private val SnackbarProgressHeight = 4.dp

// Custom post-log stock snackbar: keeps the vanilla Material3 layout but nudges
// CJK text up (cjkTextOffset), shows a bottom-edge progress bar, and
// self-dismisses after 5s. The single Animatable both drives the bar (1f -> 0f)
// and triggers dismissal when it reaches 0f. The host shows this with
// SnackbarDuration.Indefinite so only this countdown controls dismissal.
@Composable
internal fun HrtSnackbar(snackbarData: SnackbarData) {
    val visuals = snackbarData.visuals
    val message = visuals.message
    val actionLabel = visuals.actionLabel

    val progress = remember(snackbarData) { Animatable(1f) }
    LaunchedEffect(snackbarData) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = SNACKBAR_DURATION_MS,
                easing = LinearEasing,
            ),
        )
        snackbarData.dismiss()
    }

    Box(modifier = Modifier.clip(SnackbarDefaults.shape)) {
        Snackbar(
            action = actionLabel?.let { label ->
                {
                    TextButton(
                        onClick = { snackbarData.performAction() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = SnackbarDefaults.actionContentColor,
                        ),
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.cjkTextOffset(label),
                        )
                    }
                }
            },
            dismissAction = if (visuals.withDismissAction) {
                {
                    IconButton(onClick = { snackbarData.dismiss() }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(
                                R.string.stock_snackbar_action_dismiss,
                            ),
                        )
                    }
                }
            } else {
                null
            },
        ) {
            Text(
                text = message,
                modifier = Modifier.cjkTextOffset(message),
            )
        }

        // Depleting bar pinned to the bottom edge; the Box clip gives it the
        // snackbar's rounded corners. trackColor matches the container so only
        // the depleting accent portion is visible.
        LinearProgressIndicator(
            progress = { progress.value },
            color = SnackbarDefaults.actionContentColor,
            trackColor = SnackbarDefaults.color,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(SnackbarProgressHeight),
        )
    }
}
