package com.mkx.hrttracker.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyLayoutScrollScope
import androidx.compose.foundation.lazy.LazyListState

/**
 * Smoothly scrolls to the very top of the list over a fixed duration.
 *
 * [LazyListState.animateScrollToItem] estimates the distance to off-screen items from the
 * average visible item size and spring-retargets mid-flight when the estimate is wrong,
 * which lurches visibly on lists whose item heights vary a lot (e.g. Plan's week calendar
 * next to its regimen rows). This helper instead re-reads the remaining distance every
 * frame and spreads it over the rest of a fixed-duration tween, so estimate corrections
 * are absorbed gradually and the scroll lands exactly at the top.
 */
suspend fun LazyListState.animateScrollToTop(
    animationSpec: AnimationSpec<Float> = tween(
        durationMillis = SCROLL_TO_TOP_DURATION_MILLIS,
        easing = FastOutSlowInEasing,
    ),
) {
    if (layoutInfo.totalItemsCount == 0) return
    scroll {
        val scrollScope = LazyLayoutScrollScope(this@animateScrollToTop, this)

        // From very far down, gliding the whole way would be a blur that composes every
        // item in between. Snap to within a few viewports' worth of items of the top and
        // animate the rest. Snapping by item index — not by the pixel estimate, which can
        // overshoot the real position when the visible items are larger than the ones
        // above — guarantees the snap only ever moves the list closer to the top.
        val maxAnimatedItems = layoutInfo.visibleItemsInfo.size * MAX_ANIMATED_VIEWPORTS
        if (firstVisibleItemIndex > maxAnimatedItems) {
            scrollScope.snapToItem(index = maxAnimatedItems, offset = 0)
        }

        var previousProgress = 0f
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = animationSpec,
        ) { progress, _ ->
            if (previousProgress >= 1f) return@animate
            val remaining = scrollScope.calculateDistanceTo(0).toFloat()
            val frameFraction = (progress - previousProgress) / (1f - previousProgress)
            if (remaining != 0f) {
                scrollBy(remaining * frameFraction.coerceIn(0f, 1f))
            }
            previousProgress = progress
        }
        // Correct any sub-pixel rounding left over from the per-frame scrollBy calls.
        scrollScope.snapToItem(index = 0, offset = 0)
    }
}

/** Same animation for plain scrollable columns, so every screen's scroll-to-top matches. */
suspend fun ScrollState.animateScrollToTop() {
    animateScrollTo(
        value = 0,
        animationSpec = tween(
            durationMillis = SCROLL_TO_TOP_DURATION_MILLIS,
            easing = FastOutSlowInEasing,
        ),
    )
}

private const val SCROLL_TO_TOP_DURATION_MILLIS = 300
private const val MAX_ANIMATED_VIEWPORTS = 4
