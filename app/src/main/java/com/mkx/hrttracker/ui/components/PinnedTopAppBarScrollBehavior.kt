package com.mkx.hrttracker.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * [TopAppBarDefaults.pinnedScrollBehavior] that also recovers when the content
 * shrinks back to its start without a scroll event.
 *
 * M3's PinnedScrollBehavior accumulates [TopAppBarState.contentOffset] purely from
 * nested-scroll deltas. When the scrolled content shrinks underneath it (e.g.
 * deleting every entry on the plan page) the list snaps to the start without
 * dispatching any scroll, the stale offset keeps `overlappedFraction` at 1, and the
 * bar stays in its scrolled state until the user scrolls manually. The
 * lazyListState/scrollState overloads added in M3 1.5 only handle the inverse
 * (pre-scrolled) case, so snap the offset back ourselves whenever the content sits
 * at its start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun pinnedTopAppBarScrollBehavior(
    lazyListState: LazyListState,
    state: TopAppBarState = rememberTopAppBarState(),
): TopAppBarScrollBehavior {
    ResetContentOffsetWhenAtStart(state) { !lazyListState.canScrollBackward }
    return TopAppBarDefaults.pinnedScrollBehavior(lazyListState = lazyListState, state = state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun pinnedTopAppBarScrollBehavior(
    scrollState: ScrollState,
    state: TopAppBarState = rememberTopAppBarState(),
): TopAppBarScrollBehavior {
    ResetContentOffsetWhenAtStart(state) { !scrollState.canScrollBackward }
    return TopAppBarDefaults.pinnedScrollBehavior(scrollState = scrollState, state = state)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResetContentOffsetWhenAtStart(
    state: TopAppBarState,
    isContentAtStart: () -> Boolean,
) {
    val currentIsContentAtStart by rememberUpdatedState(isContentAtStart)
    val settleSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(state) {
        snapshotFlow { currentIsContentAtStart() && state.contentOffset != 0f }
            .collectLatest { stale ->
                if (stale) {
                    // Ease the offset home instead of assigning it: the bar chrome is
                    // driven by overlappedFraction (computed from this offset), so the
                    // settle dissolves the chrome once the content has landed instead
                    // of snapping it off. The stale offset can be arbitrarily deep —
                    // programmatic scroll-to-top dispatches no nested-scroll deltas —
                    // so clamp the start to the fully-overlapped limit for a
                    // constant-length fade. A real scroll flips the condition and
                    // cancels the settle via collectLatest.
                    animate(
                        initialValue = state.contentOffset.coerceAtLeast(state.heightOffsetLimit),
                        targetValue = 0f,
                        animationSpec = settleSpec,
                    ) { value, _ -> state.contentOffset = value }
                }
            }
    }
}
