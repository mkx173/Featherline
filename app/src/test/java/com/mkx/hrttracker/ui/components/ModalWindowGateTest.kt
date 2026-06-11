package com.mkx.hrttracker.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 *
 * Drop-on-leave (PR #60 findings 8/9, "Rule A"): a held modal whose host route
 * is no longer the current back-stack destination raced a navigation the user
 * already won — its open state must be cleared (onDismissRequest), not kept,
 * or the modal re-presents "out of nowhere" on the next return to the page.
 * A held modal whose host is still current is settling in (tab return, config
 * change, paused activity) and shows on resume as before.
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
    private var dismissCount = 0
    private var probeComposed by mutableStateOf(true)

    private fun setGateProbe(
        owner: TestOwner,
        isHostCurrentDestination: (() -> Boolean)? = null,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalModalHostIsCurrentDestination provides isHostCurrentDestination,
            ) {
                if (probeComposed) {
                    lastAllowed = rememberModalWindowAllowed(
                        onDismissRequest = { dismissCount++ },
                    )
                }
            }
        }
    }

    @Test
    fun modalShowsImmediately_whenHostIsResumed() {
        setGateProbe(TestOwner(Lifecycle.State.RESUMED))
        composeRule.waitForIdle()

        assertEquals(true, lastAllowed)
        assertEquals(0, dismissCount)
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
        assertEquals(0, dismissCount)
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
        assertEquals(0, dismissCount)
    }

    @Test
    fun heldModal_isDroppedNotShown_whenHostIsNoLongerCurrentDestination() {
        // Open raced a navigation that already won: the host route has left
        // the top of the back stack. Clear the open state immediately — and
        // never show, even if the lifecycle later resumes (probe kept
        // composed artificially; real callers leave composition on dismiss).
        val owner = TestOwner(Lifecycle.State.STARTED)
        setGateProbe(owner, isHostCurrentDestination = { false })
        composeRule.waitForIdle()

        assertEquals(false, lastAllowed)
        assertEquals(1, dismissCount)

        composeRule.runOnIdle { owner.moveTo(Lifecycle.State.RESUMED) }
        composeRule.waitForIdle()
        assertEquals(false, lastAllowed)
        assertEquals(1, dismissCount)
    }

    @Test
    fun heldModal_showsOnResume_whenHostIsStillCurrentDestination() {
        // Still current while held = settling in (tab return / config change /
        // paused activity): keep, show on resume.
        val owner = TestOwner(Lifecycle.State.STARTED)
        setGateProbe(owner, isHostCurrentDestination = { true })
        composeRule.waitForIdle()
        assertEquals(false, lastAllowed)
        assertEquals(0, dismissCount)

        composeRule.runOnIdle { owner.moveTo(Lifecycle.State.RESUMED) }
        composeRule.waitForIdle()
        assertEquals(true, lastAllowed)
        assertEquals(0, dismissCount)
    }

    @Test
    fun heldModal_clearsItsState_whenDisposedWhileNoLongerCurrent() {
        // A navigation that wins mid-hold disposes the route at the end of its
        // exit transition without any lifecycle event the gate could observe.
        // The disposal itself must decide: no longer current = leaving, so
        // clear the open state on the way out.
        var current = true
        val owner = TestOwner(Lifecycle.State.STARTED)
        setGateProbe(owner, isHostCurrentDestination = { current })
        composeRule.waitForIdle()
        assertEquals(false, lastAllowed)
        assertEquals(0, dismissCount)

        current = false
        composeRule.runOnIdle { probeComposed = false }
        composeRule.waitForIdle()
        assertEquals(1, dismissCount)
    }

    @Test
    fun heldModal_keepsItsState_whenDisposedWhileStillCurrent() {
        // Disposal while still current is a teardown that intends to restore
        // (config change, process death): the open state must survive so the
        // modal re-shows after the restore.
        val owner = TestOwner(Lifecycle.State.STARTED)
        setGateProbe(owner, isHostCurrentDestination = { true })
        composeRule.waitForIdle()
        assertEquals(false, lastAllowed)

        composeRule.runOnIdle { probeComposed = false }
        composeRule.waitForIdle()
        assertEquals(0, dismissCount)
    }

    @Test
    fun shownModal_keepsItsState_whenDisposed() {
        // Only a *held* modal is dropped on leave; a modal that was already
        // showing is torn down by its own dismiss/confirm flows, never by the
        // gate.
        val owner = TestOwner(Lifecycle.State.RESUMED)
        setGateProbe(owner, isHostCurrentDestination = { false })
        composeRule.waitForIdle()
        assertEquals(true, lastAllowed)

        composeRule.runOnIdle { probeComposed = false }
        composeRule.waitForIdle()
        assertEquals(0, dismissCount)
    }
}
