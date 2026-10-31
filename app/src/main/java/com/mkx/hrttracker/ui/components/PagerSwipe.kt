// Adapted from horizontalPagerSwipeOverride in miuix's PagerGestureUtils.kt
// (https://github.com/compose-miuix-ui/miuix, v0.9.4),
// Copyright 2026 compose-miuix-ui contributors, Apache License 2.0.

package com.mkx.hrttracker.ui.components

import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/** The spring a pager page settles with, after a swipe or a tab tap: soft, barely overshooting. */
val PagerSpring: SpringSpec<Float> = spring(
    stiffness = 322.2f,
    dampingRatio = 32.31f / (2f * sqrt(322.2f)),
    visibilityThreshold = 0.5f,
)

/** Slides to [page] with [PagerSpring], taking over from a swipe still settling. */
suspend fun PagerState.springToPage(page: Int) {
    scroll(MutatePriority.UserInput) { settle(this, page, initialVelocity = 0f) }
}

/**
 * Swipes the pager sideways from anywhere on it, even while a page's list is still flinging,
 * which the pager's own swipe can't do: a moving list takes the next touch for itself. Touch
 * takes the pages over where they are, and a release settles them with the finger's speed.
 * A touch that begins on [exemptFromPagerSwipe] content is left to that content, and so is one
 * that only moves after a long press.
 *
 * Give the pager userScrollEnabled = false and [PagerSwipeNestedScrollConnection] as its
 * pageNestedScrollConnection, so its own swipe doesn't compete. Swipes are reported to
 * [dragInteractions] as drags. While not [enabled], new touches are left alone, but a swipe
 * already settling still lands on its page.
 */
@Composable
fun Modifier.pagerSwipe(
    state: PagerState,
    dragInteractions: MutableInteractionSource,
    exemption: PagerSwipeExemption,
    enabled: Boolean = true,
): Modifier {
    val currentEnabled by rememberUpdatedState(enabled)
    // Finger to the right scrolls back, unless right to left.
    val scrollSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    return pointerInput(state, dragInteractions, exemption, scrollSign) {
        swipe(state, dragInteractions, exemption, scrollSign) { currentEnabled }
    }
}

private suspend fun PointerInputScope.swipe(
    state: PagerState,
    dragInteractions: MutableInteractionSource,
    exemption: PagerSwipeExemption,
    scrollSign: Float,
    enabled: () -> Boolean,
) {
    coroutineScope {
        val velocityTracker = VelocityTracker()
        val maxVelocity = viewConfiguration.maximumFlingVelocity
        var motion: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!enabled()) return@awaitEachGesture
            exemption.pointer = null
            // The touch that closes a menu, which a tap only does.
            val closingMenu = exemption.openMenus > 0
            val minFlingVelocity = 400.dp.toPx()
            val events = Channel<SwipeEvent>(Channel.UNLIMITED)
            motion?.cancel()
            // Drag and settle under one scroll, so the next touch stops both where they are.
            motion = launch(start = CoroutineStart.UNDISPATCHED) {
                state.scroll(MutatePriority.UserInput) {
                    for (event in events) {
                        when (event) {
                            is SwipeEvent.Delta -> scrollBy(event.value)
                            is SwipeEvent.End -> {
                                val position = state.currentPage + state.currentPageOffsetFraction
                                val target = when {
                                    event.velocity > minFlingVelocity -> ceil(position).toInt()
                                    event.velocity < -minFlingVelocity -> floor(position).toInt()
                                    else -> position.roundToInt()
                                }
                                state.settle(this, target, event.velocity)
                                break
                            }
                        }
                    }
                }
            }

            var pointerId = down.id
            var accumulated = Offset.Zero
            var drag: DragInteraction.Start? = null
            var releaseVelocity = 0f
            velocityTracker.resetTracking()
            velocityTracker.addPointerInputChange(down)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.fastFirstOrNull { it.id == pointerId } ?: break
                    if (change.isConsumed || exemption.pointer == down.id) break
                    // A second finger before the swipe starts is a pinch, not a swipe.
                    if (drag == null && event.changes.count { it.pressed } > 1) break
                    if (!change.pressed) {
                        // Another finger still down carries the swipe on.
                        val next = event.changes.fastFirstOrNull { it.pressed }
                        if (next != null) {
                            pointerId = next.id
                            velocityTracker.resetTracking()
                            velocityTracker.addPointerInputChange(next)
                            continue
                        }
                        velocityTracker.addPointerInputChange(change)
                        if (drag != null || closingMenu) change.consume()
                        if (drag != null) {
                            releaseVelocity = velocityTracker
                                .calculateVelocity(Velocity(maxVelocity, maxVelocity)).x * scrollSign
                        }
                        break
                    }
                    velocityTracker.addPointerInputChange(change)
                    val delta = change.position - change.previousPosition
                    if (drag == null) {
                        accumulated += delta
                        val x = abs(accumulated.x)
                        val y = abs(accumulated.y)
                        if (
                            (x > viewConfiguration.touchSlop || y > viewConfiguration.touchSlop) &&
                            change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis
                        ) {
                            // Moving only after a long press, such as extending a text
                            // selection: the content's own drag.
                            break
                        } else if (x > viewConfiguration.touchSlop && x > y) {
                            drag = DragInteraction.Start().also(dragInteractions::tryEmit)
                            change.consume()
                            // Keep the movement past the slop.
                            val overSlop = accumulated.x - sign(accumulated.x) * viewConfiguration.touchSlop
                            events.trySend(SwipeEvent.Delta(overSlop * scrollSign))
                        } else if (y > viewConfiguration.touchSlop) {
                            // Vertical: the page's own scrolling takes it.
                            break
                        }
                    } else {
                        change.consume()
                        events.trySend(SwipeEvent.Delta(delta.x * scrollSign))
                    }
                }
            } finally {
                events.trySend(SwipeEvent.End(releaseVelocity))
                events.close()
                drag?.let { dragInteractions.tryEmit(DragInteraction.Stop(it)) }
            }
        }
    }
}

/** Leaves vertical fling velocity to the pages' ancestors and keeps the horizontal rest. */
object PagerSwipeNestedScrollConnection : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(available.x, 0f)
}

/**
 * Redraws this node, and so a Haze blur on it, as [state]'s pages move. The pages slide as
 * cached layers without redrawing, and Haze only re-reads where its sources sit when it draws.
 */
fun Modifier.redrawWhilePagesMove(state: PagerState?): Modifier =
    if (state == null) this else drawBehind { state.currentPage + state.currentPageOffsetFraction }

/** Which touch content inside a [pagerSwipe] pager has claimed for its own sideways drags. */
class PagerSwipeExemption {
    internal var pointer: PointerId? = null

    // Menus open over the pages, see [pagerMenuPopupProperties].
    internal var openMenus = 0

    /**
     * Takes the touch of [pointer] from the pager, as long as the pager hasn't started swiping
     * with it yet, for a gesture recognized after touch down, such as a long press.
     */
    fun claim(pointer: PointerId) {
        this.pointer = pointer
    }
}

/** The exemption of the [pagerSwipe] pager around, while it swipes. */
val LocalPagerSwipeExemption = compositionLocalOf<PagerSwipeExemption?> { null }

/**
 * Popup properties for a menu that lets touch outside it reach [pagerSwipe] pages beneath, so a
 * swipe turns the pages as the menu closes, while a tap only closes it, as on any other menu.
 * Outside such pages, or while they don't swipe, those of a plain focusable popup.
 */
@Composable
fun pagerMenuPopupProperties(expanded: Boolean): PopupProperties {
    val exemption = LocalPagerSwipeExemption.current ?: return PopupProperties(focusable = true)
    if (expanded) {
        DisposableEffect(exemption) {
            exemption.openMenus++
            onDispose { exemption.openMenus-- }
        }
    }
    // Focusable for Back and keys, but no longer touch modal: a touch outside goes to the
    // window beneath, and the popup only hears of it to close.
    return PopupProperties(flags = FLAG_WATCH_OUTSIDE_TOUCH or FLAG_NOT_TOUCH_MODAL)
}

/**
 * Keeps sideways drags that begin here, such as a chart's pan, from swiping the surrounding
 * [pagerSwipe] pager, whenever [enabled] says so at touch down. The pager sees the touch
 * first, so this marks it on touch down, before the pager acts on any movement.
 */
@Composable
fun Modifier.exemptFromPagerSwipe(enabled: () -> Boolean = { true }): Modifier {
    val exemption = LocalPagerSwipeExemption.current ?: return this
    val currentEnabled by rememberUpdatedState(enabled)
    return pointerInput(exemption) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (currentEnabled()) exemption.claim(down.id)
        }
    }
}

private sealed interface SwipeEvent {
    data class Delta(val value: Float) : SwipeEvent
    data class End(val velocity: Float) : SwipeEvent
}

private suspend fun PagerState.settle(scope: ScrollScope, page: Int, initialVelocity: Float) = with(scope) {
    if (pageCount == 0) return@with
    val target = page.coerceIn(0, pageCount - 1)
    updateTargetPage(target)
    val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
    if (pageSize > 0) {
        val distance = (target - currentPage - currentPageOffsetFraction) * pageSize
        var previous = 0f
        animate(0f, distance, initialVelocity, PagerSpring) { value, _ ->
            previous += scrollBy(value - previous)
        }
    }
    // Inside this scroll, so a later touch can't land the pages on a stale target.
    updateCurrentPage(target)
}
