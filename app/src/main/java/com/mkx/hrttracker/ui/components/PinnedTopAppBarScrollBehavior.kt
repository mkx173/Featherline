package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResetContentOffsetWhenAtStart(
    state: TopAppBarState,
    isContentAtStart: () -> Boolean,
) {
    val currentIsContentAtStart by rememberUpdatedState(isContentAtStart)
    LaunchedEffect(state) {
        snapshotFlow { currentIsContentAtStart() && state.contentOffset != 0f }
            .collect { stale ->
                if (stale) {
                    state.contentOffset = 0f
                }
            }
    }
}
