package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.WeakHashMap

/**
 * One scroll-to-top may run per app bar at a time. The double-tap modifier and the
 * navigation signal effect are independent coroutines, so without this guard a tap on
 * one entry point mid-flight would preempt the other's animation through the scroll
 * mutex and restart the tween — the jerk-then-crawl this file exists to prevent.
 * Weak keys let abandoned states be collected.
 */
@OptIn(ExperimentalMaterial3Api::class)
private val scrollToTopInFlight: MutableSet<TopAppBarState> =
    Collections.newSetFromMap(WeakHashMap())

/**
 * The one home for "scroll back to the top and re-pin the top app bar": the shared
 * 300ms tween plus the contentOffset/heightOffset reset. Every entry point (app bar
 * double-tap, navigation scroll-to-top signal) must go through these so the animation
 * and the app bar reset cannot drift apart per screen. A call while a scroll-to-top is
 * already running for this app bar is absorbed: the in-flight animation finishes.
 */
@OptIn(ExperimentalMaterial3Api::class)
suspend fun TopAppBarState.scrollToTop(listState: LazyListState) {
    scrollToTopGuarded { listState.animateScrollToTop() }
}

@OptIn(ExperimentalMaterial3Api::class)
suspend fun TopAppBarState.scrollToTop(scrollState: ScrollState) {
    scrollToTopGuarded { scrollState.animateScrollToTop() }
}

@OptIn(ExperimentalMaterial3Api::class)
private suspend fun TopAppBarState.scrollToTopGuarded(animate: suspend () -> Unit) {
    if (!scrollToTopInFlight.add(this)) return
    try {
        animate()
        contentOffset = 0f
        heightOffset = 0f
    } finally {
        scrollToTopInFlight.remove(this)
    }
}

/**
 * Runs [TopAppBarState.scrollToTop] whenever [signal] changes after initial composition.
 *
 * Deliberately NOT a `LaunchedEffect(signal)`: re-keying on the signal cancels the
 * in-flight animation and restarts the fixed 300ms tween over the few pixels that
 * remain, so a second tap on the navigation bar made the scroll jerk and then crawl.
 * Watching the signal from a stable effect instead lets a tap that lands mid-flight be
 * absorbed by the animation already running toward the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollToTopSignalEffect(
    signal: Int,
    topAppBarState: TopAppBarState,
    listState: LazyListState,
) {
    ScrollToTopSignalEffect(signal) { topAppBarState.scrollToTop(listState) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollToTopSignalEffect(
    signal: Int,
    topAppBarState: TopAppBarState,
    scrollState: ScrollState,
) {
    ScrollToTopSignalEffect(signal) { topAppBarState.scrollToTop(scrollState) }
}

@Composable
private fun ScrollToTopSignalEffect(
    signal: Int,
    onScrollToTop: suspend () -> Unit,
) {
    val currentSignal by rememberUpdatedState(signal)
    val currentOnScrollToTop by rememberUpdatedState(onScrollToTop)
    LaunchedEffect(Unit) {
        var handledSignal = currentSignal
        snapshotFlow { currentSignal }.collect { tapped ->
            if (tapped == handledSignal) return@collect
            try {
                currentOnScrollToTop()
            } catch (cause: CancellationException) {
                // A user drag preempted the scroll mutex; the effect itself is still
                // alive, so keep listening. Rethrows if the effect really was cancelled.
                currentCoroutineContext().ensureActive()
            }
            // Taps that landed while the animation ran were satisfied by it.
            handledSignal = currentSignal
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Modifier.topAppBarScrollToTop(
    scrollBehavior: TopAppBarScrollBehavior,
    listState: LazyListState,
): Modifier = topAppBarScrollToTop(scrollBehavior) {
    scrollBehavior.state.scrollToTop(listState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Modifier.topAppBarScrollToTop(
    scrollBehavior: TopAppBarScrollBehavior,
    scrollState: ScrollState,
): Modifier = topAppBarScrollToTop(scrollBehavior) {
    scrollBehavior.state.scrollToTop(scrollState)
}

// Private so screens cannot pass an arbitrary scroll lambda and silently fork the
// unified animation; the typed overloads above are the public surface.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Modifier.topAppBarScrollToTop(
    scrollBehavior: TopAppBarScrollBehavior,
    onScrollToTop: suspend () -> Unit,
): Modifier {
    val currentOnScrollToTop by rememberUpdatedState(onScrollToTop)
    val scope = rememberCoroutineScope()

    return this.pointerInput(scrollBehavior) {
        detectTapGestures(
            onDoubleTap = {
                scope.launch {
                    currentOnScrollToTop()
                }
            }
        )
    }
}
