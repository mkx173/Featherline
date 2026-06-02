package com.mkx.hrttracker.ui

import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDismissalTest {
    @Test
    fun dismissInput_clearsFocusBeforeHidingKeyboard() {
        val calls = mutableListOf<String>()
        val focusManager = mockk<FocusManager>(relaxed = true)
        val keyboardController = mockk<SoftwareKeyboardController>(relaxed = true)

        every { focusManager.clearFocus(force = true) } answers {
            calls += "focus"
        }
        every { keyboardController.hide() } answers {
            calls += "hide"
        }

        dismissInput(
            focusManager = focusManager,
            keyboardController = keyboardController,
        )

        assertEquals(listOf("focus", "hide"), calls)
    }

    @Test
    fun dismissInputAndRun_dismissesInputBeforeAction() {
        val calls = mutableListOf<String>()
        val focusManager = mockk<FocusManager>(relaxed = true)
        val keyboardController = mockk<SoftwareKeyboardController>(relaxed = true)

        every { focusManager.clearFocus(force = true) } answers {
            calls += "focus"
        }
        every { keyboardController.hide() } answers {
            calls += "hide"
        }

        dismissInputAndRun(
            focusManager = focusManager,
            keyboardController = keyboardController,
        ) {
            calls += "action"
        }

        assertEquals(listOf("focus", "hide", "action"), calls)
    }

    @Test
    fun dismissInputAndRunWhenHidden_waitsForVisibleInputToHideBeforeAction() = runTest {
        val calls = mutableListOf<String>()
        val focusManager = mockk<FocusManager>(relaxed = true)
        val keyboardController = mockk<SoftwareKeyboardController>(relaxed = true)
        val inputHidden = CompletableDeferred<Unit>()
        var inputVisible = true

        every { focusManager.clearFocus(force = true) } answers {
            calls += "focus"
        }
        every { keyboardController.hide() } answers {
            calls += "hide"
        }

        val job = launch {
            dismissInputAndRunWhenHidden(
                focusManager = focusManager,
                keyboardController = keyboardController,
                isInputVisible = { inputVisible },
                awaitInputHidden = {
                    calls += "await-hidden"
                    inputHidden.await()
                },
            ) {
                calls += "action"
            }
        }

        yield()

        assertEquals(listOf("focus", "hide", "await-hidden"), calls)

        inputVisible = false
        inputHidden.complete(Unit)
        job.join()

        assertEquals(listOf("focus", "hide", "await-hidden", "action"), calls)
    }

    @Test
    fun dismissInputAndRunWhenHidden_skipsAwaitWhenInputAlreadyHidden() = runTest {
        val calls = mutableListOf<String>()
        val focusManager = mockk<FocusManager>(relaxed = true)
        val keyboardController = mockk<SoftwareKeyboardController>(relaxed = true)
        var awaitedHidden = false

        every { focusManager.clearFocus(force = true) } answers {
            calls += "focus"
        }
        every { keyboardController.hide() } answers {
            calls += "hide"
        }

        dismissInputAndRunWhenHidden(
            focusManager = focusManager,
            keyboardController = keyboardController,
            isInputVisible = { false },
            awaitInputHidden = {
                awaitedHidden = true
            },
        ) {
            calls += "action"
        }

        assertEquals(listOf("focus", "hide", "action"), calls)
        assertFalse(awaitedHidden)
    }

    @Test
    fun inputIsVisibleOrAnimating_treatsInsetsAsVisibleEvenWhenImeFlagIsFalse() {
        assertTrue(
            inputIsVisibleOrAnimating(
                imeVisible = false,
                imeBottom = 1008,
                imeAnimationTargetBottom = 0,
            )
        )
        assertTrue(
            inputIsVisibleOrAnimating(
                imeVisible = false,
                imeBottom = 0,
                imeAnimationTargetBottom = 1008,
            )
        )
        assertTrue(
            inputIsVisibleOrAnimating(
                imeVisible = true,
                imeBottom = 0,
                imeAnimationTargetBottom = 0,
            )
        )
        assertFalse(
            inputIsVisibleOrAnimating(
                imeVisible = false,
                imeBottom = 0,
                imeAnimationTargetBottom = 0,
            )
        )
    }
}
