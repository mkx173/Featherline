package com.mkx.hrttracker.ui.components

import androidx.compose.runtime.compositionLocalOf

/** Position of a row within its segmented group. Drives the rounded-corner shape. */
data class SegmentPosition(val index: Int, val count: Int)

/**
 * Set by [HrtSection] for each visible row so [EditorSegmentedListItem] /
 * [PreferenceSegmentedListItem] can default their index/count. Null outside a
 * section (rows then fall back to index 0 / count 1 = a standalone card).
 */
val LocalSegmentPosition = compositionLocalOf<SegmentPosition?> { null }

/**
 * Assigns each row a [SegmentPosition] from its order among the *counted*
 * (visible) rows. Hidden rows (false) get null and are excluded from the count,
 * so adding/removing/conditionally hiding a row needs no manual renumbering.
 */
internal fun segmentPositionsFor(counted: List<Boolean>): List<SegmentPosition?> {
    val total = counted.count { it }
    var index = 0
    return counted.map { visible ->
        if (visible) SegmentPosition(index++, total) else null
    }
}
