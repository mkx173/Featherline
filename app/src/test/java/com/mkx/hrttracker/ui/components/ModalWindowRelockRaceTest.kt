package com.mkx.hrttracker.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A modal held back because its host was below RESUMED when it was first composed, unlocked; the
 * activity then stops, which relocks the app (AppLockViewModel.onBackgrounded), and comes back.
 * The lock reaches the composition as in MainActivity, through collectAsStateWithLifecycle, which
 * stops collecting on ON_STOP and starts again on ON_START; ON_RESUME follows in the same
 * main-thread turn, before any composition has seen the lock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ModalWindowRelockRaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class TestOwner(initial: Lifecycle.State) : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = initial }
        override val lifecycle: Lifecycle get() = registry
        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    // Paused, not stopped: under a permission dialog, or left mid enter transition.
    private val host = TestOwner(Lifecycle.State.STARTED)
    private val lockedFlow = MutableStateFlow(false)
    private val seen = mutableListOf<Pair<Boolean, Boolean>>() // locked to allowed

    private fun setGate() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides host) {
                val locked by lockedFlow.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalAppLocked provides locked) {
                    val allowed = rememberModalWindowAllowed(onDismissRequest = {})
                    SideEffect { seen += locked to allowed }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(listOf(false to false), seen.distinct())
    }

    @Test
    fun heldModal_activityStopsAndRelocks_thenComesBack_isNotShownUnderTheLock() {
        setGate()

        composeRule.runOnUiThread {
            host.moveTo(Lifecycle.State.CREATED) // ON_STOP, then onStop() locks the app.
            lockedFlow.value = true
            host.moveTo(Lifecycle.State.STARTED) // One transaction: ON_START, then ON_RESUME.
            host.moveTo(Lifecycle.State.RESUMED)
        }
        composeRule.waitForIdle()

        assertEquals(
            "a modal first shown under the app lock: $seen",
            emptyList<Pair<Boolean, Boolean>>(),
            seen.filter { (locked, allowed) -> locked && allowed },
        )
    }

    // The lock reaches the composition a frame before the resume.
    @Test
    fun control_lockSeenBeforeTheResume_staysHeld() {
        setGate()

        composeRule.runOnUiThread {
            host.moveTo(Lifecycle.State.CREATED)
            lockedFlow.value = true
            host.moveTo(Lifecycle.State.STARTED)
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { host.moveTo(Lifecycle.State.RESUMED) }
        composeRule.waitForIdle()

        assertEquals(
            emptyList<Pair<Boolean, Boolean>>(),
            seen.filter { (locked, allowed) -> locked && allowed },
        )
    }

    // And it still shows once the app is unlocked again.
    @Test
    fun heldModal_relockedAcrossAStop_showsOnUnlock() {
        setGate()
        composeRule.runOnUiThread {
            host.moveTo(Lifecycle.State.CREATED)
            lockedFlow.value = true
            host.moveTo(Lifecycle.State.STARTED)
            host.moveTo(Lifecycle.State.RESUMED)
        }
        composeRule.waitForIdle()

        lockedFlow.value = false
        composeRule.waitForIdle()

        assertEquals("not shown after the unlock: $seen", false to true, seen.last())
    }
}
