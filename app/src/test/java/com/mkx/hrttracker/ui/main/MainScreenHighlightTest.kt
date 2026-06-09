package com.mkx.hrttracker.ui.main

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenHighlightTest {
    @Test
    fun doseRowHighlightPreFlashDelay_isSeventyFiveMillis() {
        assertEquals(75L, doseRowHighlightPreFlashDelayMillis())
    }

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
    fun runDoseRowHighlightLifecycle_waitsForScrollSettleThenDelays75MillisBeforeFlashReady() =
        runTest {
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
                    preFlashDelayMillis = 75L,
                    clearDelayMillis = 0,
                    consumeHighlightRequest = { events += "consume" },
                )
            }

            runCurrent()

            assertEquals(
                listOf("flashReady=false", "layoutFrame", "waitScrollSettled"),
                events,
            )

            // The row is now in view, but the flash must wait out the pre-flash delay
            // rather than firing the instant the scroll settles.
            scrollSettled.complete(Unit)
            runCurrent()
            assertEquals(
                listOf("flashReady=false", "layoutFrame", "waitScrollSettled"),
                events,
            )

            advanceTimeBy(50L)
            runCurrent()
            assertFalse(
                "flash fired before the pre-flash delay elapsed",
                events.contains("flashReady=true"),
            )

            advanceTimeBy(25L)
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

    @Test
    fun runDoseRowHighlightLifecycle_consumesRequest_whenCancelledBeforeClearDelayElapses() =
        runTest {
            // Leaving the Home tab disposes MainScreen, cancelling this coroutine
            // mid clear-delay. The request must still be consumed so it cannot
            // replay the highlight when the user returns to the tab.
            val events = mutableListOf<String>()
            val job = launch {
                runDoseRowHighlightLifecycle(
                    setFlashReady = { ready -> events += "flashReady=$ready" },
                    awaitFirstLayoutFrame = { },
                    awaitScrollSettled = { },
                    preFlashDelayMillis = 0L,
                    clearDelayMillis = 10_000L,
                    consumeHighlightRequest = { events += "consume" },
                )
            }

            runCurrent()
            advanceTimeBy(1_000L)
            runCurrent()
            assertFalse("request consumed before clear delay elapsed", events.contains("consume"))

            job.cancel()
            job.join()

            assertTrue("request not consumed after cancellation", events.contains("consume"))
        }

    private fun doseRowHighlightPreFlashDelayMillis(): Long {
        val field = Class.forName("com.mkx.hrttracker.ui.main.MainScreenKt")
            .getDeclaredField("DoseRowHighlightPreFlashDelayMillis")
        field.isAccessible = true
        return field.getLong(null)
    }
}
