package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Modifier.topAppBarScrollToTop(
    scrollBehavior: TopAppBarScrollBehavior,
    onScrollToTop: suspend () -> Unit,
): Modifier {
    val currentOnScrollToTop by rememberUpdatedState(onScrollToTop)
    val scope = rememberCoroutineScope()

    return this.pointerInput(scrollBehavior) {
        detectTapGestures(
            onDoubleTap = {
                scope.launch {
                    // No offset reset here: programmatic scrolls dispatch no
                    // nested-scroll deltas, but pinnedTopAppBarScrollBehavior settles
                    // the stale contentOffset once the content lands at the start —
                    // eased, so the overlap-driven bar chrome dissolves instead of
                    // snapping off.
                    currentOnScrollToTop()
                }
            }
        )
    }
}
