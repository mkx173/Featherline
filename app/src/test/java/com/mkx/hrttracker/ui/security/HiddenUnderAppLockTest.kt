package com.mkx.hrttracker.ui.security

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.mkx.hrttracker.ui.components.InsetsDispatchedAgainWhenShown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The home shell stays composed under the in-window lock screen (MainActivity). The lock screen
 * covers it for touch and accessibility, and keys must not reach it either: a hardware keyboard or
 * a D-pad can't move focus into it, nor type into a field focused before the lock came up.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HiddenUnderAppLockTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var locked by mutableStateOf(false)

    // MainActivity's layers: the home shell, and the lock screen over it while locked.
    private fun setShell(content: @Composable () -> Unit) {
        composeRule.setContent {
            InsetsDispatchedAgainWhenShown()
            val inputModeManager = LocalInputModeManager.current
            SideEffect { inputModeManager.requestInputMode(InputMode.Keyboard) }
            Box(Modifier.fillMaxSize()) {
                HiddenUnderAppLock(locked = locked, modifier = Modifier.fillMaxSize(), content = content)
                if (locked) AppLockScreen(errorMessageRes = null, onUnlockClick = {})
            }
        }
    }

    @Test
    fun keyboardFocus_cannotReachAButtonUnderTheLock() {
        var clicks = 0
        locked = true
        setShell { Button(onClick = { clicks++ }) { Text("Log dose") } }

        composeRule.onRoot().performKeyInput {
            pressKey(Key.Tab)
            pressKey(Key.Enter)
        }
        composeRule.waitForIdle()

        assertEquals("Tab then Enter clicked a button under the lock screen", 0, clicks)
    }

    @Test
    fun dpadFocus_cannotReachAButtonUnderTheLock() {
        var clicks = 0
        locked = true
        setShell { Button(onClick = { clicks++ }) { Text("Log dose") } }

        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitForIdle()

        assertEquals("D-pad down then center clicked a button under the lock screen", 0, clicks)
    }

    @Test
    fun aFieldFocusedBeforeTheLock_takesNoTypingUnderIt() {
        var text by mutableStateOf("")
        val focusRequester = FocusRequester()
        setShell {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
        composeRule.runOnIdle { focusRequester.requestFocus() }
        composeRule.onRoot().performKeyInput { pressKey(Key.A) }
        composeRule.waitForIdle()
        assertEquals("a", text)

        locked = true
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.B) }
        composeRule.waitForIdle()

        assertEquals("Typing under the lock screen edited a field behind it", "a", text)
    }

    // Before API 30 the IME inset is worked out from the window's visible insets, which ViewRootImpl
    // updates only after dispatching the insets of a window shown again: a keyboard closed while it
    // was hidden can stay in the layout until they are dispatched again. Whenever the window shows
    // again, they are.
    @Test
    @Config(sdk = [29])
    fun theWindowShownAgain_dispatchesItsInsetsAgain() {
        // As the app comes back from Home, no lock.
        assertInsetsDispatchedWhenShownAgain(lockedWhileHidden = false)
    }

    // Before API 30 the PIN screen is an activity over this one: the unlock comes as the window
    // shows again.
    @Test
    @Config(sdk = [29])
    fun unlock_dispatchesTheWindowInsetsAgain() {
        assertInsetsDispatchedWhenShownAgain(lockedWhileHidden = true)
    }

    private fun assertInsetsDispatchedWhenShownAgain(lockedWhileHidden: Boolean) {
        lateinit var view: View
        setShell { view = LocalView.current }
        var dispatches = 0
        composeRule.runOnIdle {
            view.rootView.setOnApplyWindowInsetsListener { root, insets ->
                dispatches++
                root.onApplyWindowInsets(insets)
            }
            locked = lockedWhileHidden
            // As ActivityThread hides the activity's window when it stops.
            view.rootView.visibility = View.INVISIBLE
        }
        composeRule.runOnIdle { dispatches = 0 }

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            locked = false
            view.rootView.visibility = View.VISIBLE
        }
        // Frame by frame past the compat insets animation, each frame's traversal run in between as
        // on a device: once right away, once after it.
        repeat(25) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle {
            assertTrue(
                "The window shown again didn't get its insets dispatched again, right away and " +
                    "after the compat insets animation: $dispatches dispatch(es)",
                dispatches >= 2,
            )
        }
    }

    // The test shell above calls it itself, as MainActivity's root must.
    @Test
    fun mainActivity_dispatchesItsWindowInsetsAgainWhenShown() {
        val root = File("src/main/java/com/mkx/hrttracker/MainActivity.kt").readLines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
            .substringAfter("setContent {")
            .substringBefore("HiddenUnderAppLock(")

        assertTrue(
            "MainActivity's root setContent must call InsetsDispatchedAgainWhenShown(), or its " +
                "window keeps a keyboard closed while it was hidden before API 30",
            root.contains("InsetsDispatchedAgainWhenShown()"),
        )
    }

    // A restored backup can bring onboarding back on an install with the lock on.
    @Test
    fun mainActivity_hidesTheOnboardingOverlayUnderTheLock() {
        val overlay = File("src/main/java/com/mkx/hrttracker/MainActivity.kt").readText()
            .substringAfter("label = \"onboarding-overlay\"")
            .substringBefore("OnboardingScreen(")

        assertTrue(
            "Onboarding under the lock screen must sit in HiddenUnderAppLock, not only " +
                "provide LocalAppLocked, so keys and accessibility can't reach it",
            overlay.contains("HiddenUnderAppLock(") &&
                overlay.contains("locked = contentLayers.showInWindowLockScreen"),
        )
    }
}
