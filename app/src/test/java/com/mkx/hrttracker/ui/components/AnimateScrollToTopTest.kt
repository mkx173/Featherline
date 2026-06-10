package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [animateScrollToTop] exists because [LazyListState.animateScrollToItem] estimates the
 * distance to off-screen items from the average visible item size. On lists with one huge
 * item among small ones (Plan's week calendar vs. regimen rows) that estimate is badly
 * short, producing a visible mid-flight retarget lurch. These tests pin down that the
 * helper lands exactly at the top regardless of item-size heterogeneity.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AnimateScrollToTopTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope

    private fun setHeterogeneousList(smallItemCount: Int = 30) {
        composeRule.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            LazyColumn(state = listState, modifier = Modifier.height(300.dp)) {
                item(key = "huge") { Box(Modifier.fillMaxWidth().height(900.dp)) }
                repeat(smallItemCount) { index ->
                    item(key = "small-$index") { Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
            }
        }
    }

    @Test
    fun landsExactlyAtTopDespiteHeterogeneousItemHeights() {
        setHeterogeneousList()

        composeRule.runOnIdle { scope.launch { listState.scrollToItem(25) } }
        composeRule.waitForIdle()

        composeRule.runOnIdle { scope.launch { listState.animateScrollToTop() } }
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }

    @Test
    fun overestimatedDistanceNeverMovesAwayFromTop() {
        // The inverse skew: small items above, huge items visible. The average-visible-size
        // estimate then wildly OVERestimates the distance to the top; a snap based on that
        // pixel estimate would teleport the list to four viewports from the top — farther
        // down than the user actually was — before tweening back up. Scroll-to-top must
        // only ever move the list closer to the top, on every frame.
        composeRule.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            LazyColumn(state = listState, modifier = Modifier.height(300.dp)) {
                repeat(13) { index ->
                    item(key = "small-$index") { Box(Modifier.fillMaxWidth().height(28.dp)) }
                }
                repeat(5) { index ->
                    item(key = "huge-$index") { Box(Modifier.fillMaxWidth().height(250.dp)) }
                }
            }
        }

        composeRule.runOnIdle { scope.launch { listState.scrollToItem(13) } }
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        // Capture the baseline before launching: the coroutine runs eagerly up to its
        // first frame suspension, which is exactly where the old code teleported backward.
        var previous = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        composeRule.runOnIdle { scope.launch { listState.animateScrollToTop() } }
        repeat(60) {
            composeRule.waitForIdle()
            val current = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            val movedAwayFromTop = current.first > previous.first ||
                    (current.first == previous.first && current.second > previous.second)
            org.junit.Assert.assertFalse(
                "scroll-to-top moved away from the top: $previous -> $current",
                movedAwayFromTop
            )
            previous = current
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }

    @Test
    fun alreadyAtTopStaysAtTop() {
        setHeterogeneousList()

        composeRule.runOnIdle { scope.launch { listState.animateScrollToTop() } }
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }

    @Test
    fun scrollStateVariantLandsAtTop() {
        lateinit var scrollState: ScrollState
        composeRule.setContent {
            scrollState = rememberScrollState()
            scope = rememberCoroutineScope()
            Column(Modifier.height(300.dp).verticalScroll(scrollState)) {
                Box(Modifier.fillMaxWidth().height(2000.dp))
            }
        }

        composeRule.runOnIdle { scope.launch { scrollState.scrollTo(scrollState.maxValue) } }
        composeRule.waitForIdle()

        composeRule.runOnIdle { scope.launch { scrollState.animateScrollToTop() } }
        composeRule.waitForIdle()

        assertEquals(0, scrollState.value)
    }

    @Test
    fun emptyListIsNoOp() {
        composeRule.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            LazyColumn(state = listState, modifier = Modifier.height(300.dp)) {}
        }

        composeRule.runOnIdle { scope.launch { listState.animateScrollToTop() } }
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }
}
