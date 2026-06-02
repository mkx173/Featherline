package com.mkx.hrttracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R

private const val SNACKBAR_DURATION_MS = 5_000
private val SnackbarProgressSize = 20.dp
private val SnackbarProgressStroke = 2.dp

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

    // The Snackbar(snackbarData) convenience overload applies a 12dp margin; the
    // slot overload we use does not, so add it back. The slot Snackbar keeps its
    // own shadow elevation since nothing clips it.
    Snackbar(
        modifier = Modifier.padding(12.dp),
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.size(SnackbarProgressSize),
                color = MaterialTheme.colorScheme.inversePrimary,
                trackColor = MaterialTheme.colorScheme.secondary,
                strokeWidth = SnackbarProgressStroke,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp
            )
            Text(
                text = message,
                modifier = Modifier.cjkTextOffset(message),
            )
        }
    }
}
