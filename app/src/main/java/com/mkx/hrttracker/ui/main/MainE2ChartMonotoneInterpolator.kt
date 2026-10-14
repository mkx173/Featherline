package com.mkx.hrttracker.ui.main

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import kotlin.math.abs
import kotlin.math.sign

// Monotone cubic (Fritsch–Carlson, as in d3's curveMonotoneX). Catmull–Rom
// overshoots at the near-vertical rise of a sublingual dose, dipping the line
// below the dose marker and the x-axis; a monotone curve never leaves the
// y-range of the two points it joins.
internal object MonotoneCubicInterpolator : LineCartesianLayer.Interpolator {
    override val visiblePadding: Int = 1

    override fun interpolate(
        context: CartesianDrawingContext,
        path: Path,
        points: List<Offset>,
        visibleIndexRange: IntRange,
    ) {
        if (visibleIndexRange.isEmpty()) return
        path.moveTo(points[visibleIndexRange.first].x, points[visibleIndexRange.first].y)
        for (index in visibleIndexRange.first + 1..visibleIndexRange.last) {
            val p1 = points[index - 1]
            val p2 = points[index]
            val third = (p2.x - p1.x) / 3f
            path.cubicTo(
                p1.x + third,
                p1.y + points.tangentAt(index - 1) * third,
                p2.x - third,
                p2.y - points.tangentAt(index) * third,
                p2.x,
                p2.y,
            )
        }
    }

    private fun List<Offset>.tangentAt(index: Int): Float {
        val point = this[index]
        val previous = getOrNull(index - 1)
        val next = getOrNull(index + 1)
        return when {
            previous != null && next != null -> monotoneTangent(
                dx0 = point.x - previous.x,
                dy0 = point.y - previous.y,
                dx1 = next.x - point.x,
                dy1 = next.y - point.y,
            )
            previous != null -> secant(point.x - previous.x, point.y - previous.y)
            next != null -> secant(next.x - point.x, next.y - point.y)
            else -> 0f
        }
    }

    private fun secant(dx: Float, dy: Float): Float = if (dx == 0f) 0f else dy / dx
}

// Tangent at a point joining a left segment (dx0, dy0) and a right one
// (dx1, dy1): flat at a local extremum or step, otherwise capped at twice the
// shallower secant, which keeps both adjacent Bézier segments monotone.
internal fun monotoneTangent(dx0: Float, dy0: Float, dx1: Float, dy1: Float): Float {
    if (dx0 == 0f || dx1 == 0f) return 0f
    val s0 = dy0 / dx0
    val s1 = dy1 / dx1
    if (s0 * s1 <= 0f) return 0f
    val weighted = (s0 * abs(dx1) + s1 * abs(dx0)) / (abs(dx0) + abs(dx1))
    return sign(s0) * minOf(2 * abs(s0), 2 * abs(s1), abs(weighted))
}
