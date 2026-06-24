package com.mkx.hrttracker.ui.components

/**
 * Hysteresis state for a selection-mode FAB that hides on downward scroll and re-shows on
 * upward scroll. Promoted from History so the All Notes screen can reuse the exact behaviour.
 */
internal data class SelectionFabScrollState(
    val visible: Boolean = true,
    val accumulatedDownScrollPx: Int = 0,
    val accumulatedUpScrollPx: Int = 0,
)

internal fun updateSelectionFabScrollState(
    state: SelectionFabScrollState,
    previousIndex: Int,
    previousOffset: Int,
    index: Int,
    offset: Int,
    estimatedItemSizePx: Int,
    hideThresholdPx: Int,
    showThresholdPx: Int,
): SelectionFabScrollState {
    val resolvedHideThresholdPx = hideThresholdPx.coerceAtLeast(1)
    val resolvedShowThresholdPx = showThresholdPx.coerceAtLeast(1)
    val deltaPx = selectionFabScrollDeltaPx(
        previousIndex = previousIndex,
        previousOffset = previousOffset,
        index = index,
        offset = offset,
        estimatedItemSizePx = estimatedItemSizePx,
    )

    return when {
        deltaPx > 0 -> {
            val accumulatedDownScrollPx = state.accumulatedDownScrollPx + deltaPx
            SelectionFabScrollState(
                visible = if (accumulatedDownScrollPx >= resolvedHideThresholdPx) {
                    false
                } else {
                    state.visible
                },
                accumulatedDownScrollPx = if (accumulatedDownScrollPx >= resolvedHideThresholdPx) {
                    0
                } else {
                    accumulatedDownScrollPx
                },
                accumulatedUpScrollPx = 0,
            )
        }

        deltaPx < 0 -> {
            val accumulatedUpScrollPx = state.accumulatedUpScrollPx - deltaPx
            SelectionFabScrollState(
                visible = if (accumulatedUpScrollPx >= resolvedShowThresholdPx) {
                    true
                } else {
                    state.visible
                },
                accumulatedDownScrollPx = 0,
                accumulatedUpScrollPx = if (accumulatedUpScrollPx >= resolvedShowThresholdPx) {
                    0
                } else {
                    accumulatedUpScrollPx
                },
            )
        }

        else -> state
    }
}

private fun selectionFabScrollDeltaPx(
    previousIndex: Int,
    previousOffset: Int,
    index: Int,
    offset: Int,
    estimatedItemSizePx: Int,
): Int {
    // Crossing an item boundary doesn't say how far the user scrolled — a 3px nudge can
    // increment the index. Estimate the real pixel delta from the average item size, but
    // clamp it to the direction the index change proves, so a wrong estimate (item sizes
    // vary a lot around the calendar) can never flip a small scroll's direction or let a
    // single boundary cross instantly satisfy the accumulation thresholds.
    val estimatedDeltaPx = (index - previousIndex) * estimatedItemSizePx + (offset - previousOffset)
    return when {
        index > previousIndex -> estimatedDeltaPx.coerceAtLeast(1)
        index < previousIndex -> estimatedDeltaPx.coerceAtMost(-1)
        else -> estimatedDeltaPx
    }
}
