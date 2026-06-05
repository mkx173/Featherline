package com.mkx.hrttracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity

/**
 * The rounded-corner shape for a row at [index] of [count], with its four corners
 * animated so an index/count change morphs smoothly instead of snapping.
 *
 * A row inside an [HrtSection] gets a new [index]/[count] the instant a sibling
 * animates in or out, which previously swapped the static shape mid-transition and
 * made the corners blink to their final state before the expand/collapse finished.
 * Animating each corner (with Material's `fastSpatial` shape spec, the same one
 * SegmentedListItem uses for its interaction morph) lets the corners round/square in
 * lockstep with the height animation.
 *
 * Per corner: a standalone row (count <= 1) is fully [cornerShape]; the first row
 * rounds its top corners and the last row its bottom corners, with the remaining
 * corners falling back to [middleShape]; middle rows use [middleShape] throughout.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun animatedSegmentedCornerShape(
    index: Int,
    count: Int,
    cornerShape: CornerBasedShape,
    middleShape: CornerBasedShape,
): Shape {
    val density = LocalDensity.current
    // List-item corners are dp-based (shapes.large = 16.dp, the middle shape = 4.dp),
    // so corner px is size-independent and Size.Zero resolves them correctly.
    fun px(corner: CornerSize) = corner.toPx(Size.Zero, density)

    val isTop = count <= 1 || index == 0
    val isBottom = count <= 1 || index == count - 1
    val topCorners = if (isTop) cornerShape else middleShape
    val bottomCorners = if (isBottom) cornerShape else middleShape

    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val topStart by animateFloatAsState(px(topCorners.topStart), spec, label = "segTopStart")
    val topEnd by animateFloatAsState(px(topCorners.topEnd), spec, label = "segTopEnd")
    val bottomStart by animateFloatAsState(px(bottomCorners.bottomStart), spec, label = "segBottomStart")
    val bottomEnd by animateFloatAsState(px(bottomCorners.bottomEnd), spec, label = "segBottomEnd")

    return RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentedListItemShape(
    index: Int,
    count: Int,
    cornerShape: CornerBasedShape = MaterialTheme.shapes.large,
): Shape {
    val defaultShape = ListItemDefaults.shapes().shape
    if (defaultShape !is CornerBasedShape) return defaultShape
    return animatedSegmentedCornerShape(index, count, cornerShape, defaultShape)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentedListItemShapes(
    index: Int,
    count: Int,
    cornerShape: CornerBasedShape = MaterialTheme.shapes.large,
    pressedShape: Shape? = null,
): ListItemShapes {
    val defaultShapes = if (pressedShape != null) {
        ListItemDefaults.shapes(pressedShape = pressedShape)
    } else {
        ListItemDefaults.shapes()
    }

    val defaultBaseShape = defaultShapes.shape
    if (defaultBaseShape !is CornerBasedShape) return defaultShapes

    // The base shape carries the segmented (and now animated) corners; the other
    // interaction shapes stay at their defaults so SegmentedListItem keeps its own
    // pressed/hovered/focused morph. While the corners are settled the returned
    // ListItemShapes is value-equal across recompositions, so that interaction morph
    // is preserved; only an index/count change drives the corner animation.
    return defaultShapes.copy(
        shape = animatedSegmentedCornerShape(index, count, cornerShape, defaultBaseShape),
    )
}
