package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
 * [ScrollToTopSignalEffect] exists because a `LaunchedEffect(signal)` cancels the
 * in-flight scroll on every signal change: a second tap on the navigation bar killed the
 * animation mid-frame and restarted a fresh 300ms tween over the few remaining pixels —
 * a visible jerk, then a crawl. These tests pin down that a mid-flight tap is absorbed
 * (the original animation finishes on schedule) and that a user drag preempting the
 * scroll doesn't kill the listener.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScrollToTopSignalEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var signal by mutableIntStateOf(0)
    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope

    private fun setListWithEffect() {
        composeRule.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            ScrollToTopSignalEffect(
                signal = signal,
                topAppBarState = rememberTopAppBarState(),
                listState = listState,
            )
            LazyColumn(state = listState, modifier = Modifier.height(300.dp)) {
                repeat(40) { index ->
                    item(key = "item-$index") { Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
            }
        }
    }

    @Test
    fun midFlightSignalIsAbsorbedWithoutRestartingTheTween() {
        setListWithEffect()
        composeRule.runOnIdle { scope.launch { listState.scrollToItem(30) } }
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { signal++ }
        // ~190ms into the 300ms tween…
        repeat(12) {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeByFrame()
        }
        // …a second tap lands. If it restarted the tween, finishing would need another
        // full 300ms (~19 frames); absorption finishes on the original schedule.
        composeRule.runOnIdle { signal++ }
        repeat(12) {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }

    @Test
    fun dragPreemptionDoesNotKillTheListener() {
        setListWithEffect()
        composeRule.runOnIdle { scope.launch { listState.scrollToItem(30) } }
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { signal++ }
        repeat(5) {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeByFrame()
        }
        // A user drag preempts the scroll mutex, cancelling the animation from inside.
        composeRule.runOnIdle {
            scope.launch { listState.scroll(MutatePriority.UserInput) { } }
        }
        repeat(2) {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeByFrame()
        }
        val stalled = composeRule.runOnIdle {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
        check(stalled != 0 to 0) { "drag preemption should have stopped the scroll mid-way" }

        // The effect must still be listening: a new tap scrolls to the top.
        composeRule.runOnIdle { signal++ }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }
}
