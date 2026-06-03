package com.mkx.hrttracker.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/**
 * A vertical (x-axis) card-flip between two faces, driven by [flipped]. The
 * visible face swaps at the halfway point of the rotation, so the outgoing face
 * rotates edge-on (0° → 90°) and the incoming face rotates back in (-90° → 0°),
 * reading as a single coin-flip rather than a crossfade.
 *
 * Either face may render nothing (e.g. an empty trailing slot), in which case
 * that half of the flip is simply blank — the [front] content flips out to
 * reveal the [back] content, or vice versa.
 */
@Composable
fun FlipSlot(
    flipped: Boolean,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (flipped) 1f else 0f,
        animationSpec = tween(
            durationMillis = FlipSlotDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "flip-slot",
    )
    val face = flipSlotFace(progress)
    val rotationX = flipSlotRotationX(progress = progress, face = face)
    val density = LocalDensity.current.density

    Box(
        modifier = modifier.graphicsLayer {
            this.rotationX = rotationX
            cameraDistance = FlipSlotCameraDistance * density
        },
        contentAlignment = contentAlignment,
    ) {
        when (face) {
            FlipSlotFace.FRONT -> front()
            FlipSlotFace.BACK -> back()
        }
    }
}

internal enum class FlipSlotFace {
    FRONT,
    BACK,
}

private const val FlipSlotHalfwayProgress = 0.5f
private const val FlipSlotQuarterTurnDegrees = 90f

internal fun flipSlotFace(progress: Float): FlipSlotFace {
    return if (progress.coerceIn(0f, 1f) < FlipSlotHalfwayProgress) {
        FlipSlotFace.FRONT
    } else {
        FlipSlotFace.BACK
    }
}

internal fun flipSlotRotationX(
    progress: Float,
    face: FlipSlotFace,
): Float {
    val coercedProgress = progress.coerceIn(0f, 1f)
    return when (face) {
        FlipSlotFace.FRONT -> {
            val faceProgress = (coercedProgress / FlipSlotHalfwayProgress)
                .coerceIn(0f, 1f)
            FlipSlotQuarterTurnDegrees * faceProgress
        }
        FlipSlotFace.BACK -> {
            val faceProgress =
                ((coercedProgress - FlipSlotHalfwayProgress) / FlipSlotHalfwayProgress)
                    .coerceIn(0f, 1f)
            -FlipSlotQuarterTurnDegrees + FlipSlotQuarterTurnDegrees * faceProgress
        }
    }
}

private const val FlipSlotDurationMillis = 220
private const val FlipSlotCameraDistance = 12f
