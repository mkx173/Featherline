package com.mkx.hrttracker.ui.main

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenHighlightTest {
    @Test
    fun awaitDoseRowHighlightScrollSettled_waitsForIdleStableFrames() = runTest {
        val frameStates = listOf(
            true to 10,
            false to 20,
            false to 20,
            false to 20,
        )
        var frameIndex = 0
        var scrollInProgress = true
        var scrollValue = 0

        awaitDoseRowHighlightScrollSettled(
            isScrollInProgress = { scrollInProgress },
            scrollValue = { scrollValue },
            awaitFrame = {
                val (nextInProgress, nextValue) = frameStates[frameIndex]
                frameIndex += 1
                scrollInProgress = nextInProgress
                scrollValue = nextValue
            },
            stableFrameCount = 2,
        )

        assertEquals(4, frameIndex)
    }

    @Test
    fun runDoseRowHighlightLifecycle_waitsForScrollSettleBeforeFlashReady() = runTest {
        val scrollSettled = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val job = launch {
            runDoseRowHighlightLifecycle(
                setFlashReady = { ready -> events += "flashReady=$ready" },
                awaitFirstLayoutFrame = { events += "layoutFrame" },
                awaitScrollSettled = {
                    events += "waitScrollSettled"
                    scrollSettled.await()
                },
                clearDelayMillis = 0,
                consumeHighlightRequest = { events += "consume" },
            )
        }

        runCurrent()

        assertEquals(
            listOf("flashReady=false", "layoutFrame", "waitScrollSettled"),
            events,
        )

        scrollSettled.complete(Unit)
        runCurrent()

        assertEquals(
            listOf(
                "flashReady=false",
                "layoutFrame",
                "waitScrollSettled",
                "flashReady=true",
                "consume",
            ),
            events,
        )

        job.cancel()
    }
}
