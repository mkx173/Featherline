package com.mkx.hrttracker.ui.navigation

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.mkx.hrttracker.ui.components.LocalModalHostShown
import com.mkx.hrttracker.ui.components.LocalPagerSwipeExemption
import com.mkx.hrttracker.ui.components.PagerSwipeExemption
import com.mkx.hrttracker.ui.components.PagerSwipeNestedScrollConnection
import com.mkx.hrttracker.ui.components.pagerSwipe
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The four top-level pages side by side, swiped between or scrolled by the navigation bar.
 *
 * Every page stays composed once it has been shown, so a switch only moves pages instead of
 * composing one from scratch in a single frame. The pages not shown yet are composed one per
 * frame shortly after launch, so the first visit to each is cheap too. Covered by a sub page,
 * the pages leave the composition with their destination, and on the way back they are composed
 * again the same way, but straight away.
 *
 * Pages swipe sideways even while a page's list is still scrolling, see [pagerSwipe].
 *
 * A page off screen is held at [Lifecycle.State.STARTED]: its flows keep it current, so it never
 * slides into view stale or stuck loading, but only the pages on screen are resumed. A dialog or
 * sheet left open on a page sliding off screen is dismissed.
 */
@Composable
internal fun TopLevelPager(
    state: PagerState,
    dragInteractions: MutableInteractionSource,
    swipeEnabled: Boolean,
    pageContent: @Composable (Screen) -> Unit,
) {
    var prewarmedPages by remember { mutableIntStateOf(0) }
    // Kept with the destination while a sub page covers it, unlike the pages themselves.
    var prewarmedBefore by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (!prewarmedBefore) delay(PrewarmDelayMillis)
        // Nearest pages first, each in a frame of its own with an idle one after it.
        while (prewarmedPages < topLevelNavigationItems.size) {
            prewarmedPages++
            withFrameNanos { }
            withFrameNanos { }
        }
        prewarmedBefore = true
    }

    val swipeExemption = remember { PagerSwipeExemption() }
    CompositionLocalProvider(LocalPagerSwipeExemption provides swipeExemption) {
        HorizontalPager(
            state = state,
            modifier = Modifier.pagerSwipe(state, dragInteractions, swipeExemption, enabled = swipeEnabled),
            // Keeps every page's slot composed whichever page is shown.
            beyondViewportPageCount = topLevelNavigationItems.lastIndex,
            // pagerSwipe swipes the pages instead.
            userScrollEnabled = false,
            pageNestedScrollConnection = PagerSwipeNestedScrollConnection,
            overscrollEffect = null,
            key = { page -> topLevelNavigationItems[page].screen.route },
        ) { page ->
            val visible by remember(state, page) {
                derivedStateOf {
                    page == state.currentPage ||
                            state.layoutInfo.visiblePagesInfo.any { info -> info.index == page }
                }
            }
            val prewarmed by remember(state, page) {
                derivedStateOf { abs(page - state.currentPage) < prewarmedPages }
            }
            var shown by remember { mutableStateOf(false) }
            if (visible && !shown) SideEffect { shown = true }
            // A field focused on a page swiped away would keep the keyboard up and take typing.
            var hasFocus by remember { mutableStateOf(false) }
            val focusManager = LocalFocusManager.current
            LaunchedEffect(visible) {
                if (!visible && hasFocus) focusManager.clearFocus(force = true)
            }

            LifecycleCapped(if (visible) Lifecycle.State.RESUMED else Lifecycle.State.STARTED) {
                BackHandledOnlyWhen(visible) {
                    // Its own layer, so a swipe only moves the page instead of redrawing it. Off
                    // screen, it is hidden from accessibility as a destination left behind was.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer()
                            .then(if (visible) Modifier else Modifier.clearAndSetSemantics { })
                            .onFocusChanged { state -> hasFocus = state.hasFocus }
                            // Nor can keys or a D-pad move focus into it.
                            .focusProperties { onEnter = { if (!visible) cancelFocusChange() } }
                            .focusGroup(),
                    ) {
                        if (visible || shown || prewarmed) {
                            CompositionLocalProvider(LocalModalHostShown provides visible) {
                                pageContent(topLevelNavigationItems[page].screen)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ponytail: a fixed wait for launch to settle, not an idle signal; measure if pages still
// compose while the first screen is busy.
private const val PrewarmDelayMillis = 1_000L

// Off screen, a page stays started for its data, which would leave its back handlers live and
// let a hidden editor take Back. They stay registered, on a child dispatcher off unless [enabled].
@Composable
private fun BackHandledOnlyWhen(enabled: Boolean, content: @Composable () -> Unit) {
    val parent = LocalNavigationEventDispatcherOwner.current ?: return content()
    val owner = rememberNavigationEventDispatcherOwner(enabled = enabled, parent = parent)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner, content = content)
}

// Provides a lifecycle that follows the surrounding one but never goes above [maxState].
@Composable
private fun LifecycleCapped(maxState: Lifecycle.State, content: @Composable () -> Unit) {
    val parent = LocalLifecycleOwner.current
    val owner = remember(parent) { CappedLifecycleOwner(parent) }
    SideEffect { owner.maxState = maxState }
    DisposableEffect(owner) {
        parent.lifecycle.addObserver(owner)
        onDispose { owner.detach() }
    }
    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}

private class CappedLifecycleOwner(private val parent: LifecycleOwner) :
    LifecycleOwner, LifecycleEventObserver {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    var maxState = Lifecycle.State.STARTED
        set(value) {
            field = value
            sync()
        }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) = sync()

    fun detach() {
        parent.lifecycle.removeObserver(this)
        moveTo(Lifecycle.State.DESTROYED)
    }

    private fun sync() = moveTo(minOf(parent.lifecycle.currentState, maxState))

    private fun moveTo(state: Lifecycle.State) {
        val current = registry.currentState
        // DESTROYED is final, and a lifecycle never started cannot be destroyed.
        if (current == Lifecycle.State.DESTROYED) return
        if (state == Lifecycle.State.DESTROYED && current == Lifecycle.State.INITIALIZED) return
        registry.currentState = state
    }
}
