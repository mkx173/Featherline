package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * The one home for "scroll back to the top and re-pin the top app bar": the shared
 * 300ms tween plus the contentOffset/heightOffset reset. Every entry point (app bar
 * double-tap, navigation scroll-to-top signal) must go through these so the animation
 * and the app bar reset cannot drift apart per screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
suspend fun TopAppBarState.scrollToTop(listState: LazyListState) {
    listState.animateScrollToTop()
    contentOffset = 0f
    heightOffset = 0f
}

@OptIn(ExperimentalMaterial3Api::class)
suspend fun TopAppBarState.scrollToTop(scrollState: ScrollState) {
    scrollState.animateScrollToTop()
    contentOffset = 0f
    heightOffset = 0f
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
