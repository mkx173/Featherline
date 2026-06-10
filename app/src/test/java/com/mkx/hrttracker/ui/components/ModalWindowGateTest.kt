package com.mkx.hrttracker.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Route content stays composed and interactive while its exit transition
 * runs, so a tap can open a dialog/sheet after navigation has already begun.
 * The modal window renders above everything — it would blink over the
 * destination page and vanish when the outgoing route is disposed. The gate
 * holds a modal back when it is first composed below RESUMED, but must never
 * hide a modal that was already showing (e.g. when the activity pauses behind
 * a system dialog).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ModalWindowGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class TestOwner(initialState: Lifecycle.State) : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = initialState }
        override val lifecycle: Lifecycle get() = registry
        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    private var lastAllowed: Boolean? = null

    private fun setGateProbe(owner: TestOwner) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                lastAllowed = rememberModalWindowAllowed()
            }
        }
    }

    @Test
    fun modalShowsImmediately_whenHostIsResumed() {
        setGateProbe(TestOwner(Lifecycle.State.RESUMED))
        composeRule.waitForIdle()

        assertEquals(true, lastAllowed)
    }

    @Test
    fun modalIsHeldBack_belowResumed_andShowsOnResume() {
        // Below RESUMED at first composition = the host route is animating out
        // (or its activity is paused): don't blink the window. If the host
        // comes back instead of being disposed, show the modal then.
        val owner = TestOwner(Lifecycle.State.STARTED)
        setGateProbe(owner)
        composeRule.waitForIdle()
        assertEquals(false, lastAllowed)

        composeRule.runOnIdle { owner.moveTo(Lifecycle.State.RESUMED) }
        composeRule.waitForIdle()
        assertEquals(true, lastAllowed)
    }

    @Test
    fun modalAlreadyShown_staysShown_whenHostDropsBelowResumed() {
        val owner = TestOwner(Lifecycle.State.RESUMED)
        setGateProbe(owner)
        composeRule.waitForIdle()
        assertEquals(true, lastAllowed)

        composeRule.runOnIdle { owner.moveTo(Lifecycle.State.STARTED) }
        composeRule.waitForIdle()
        assertEquals(true, lastAllowed)
    }
}
