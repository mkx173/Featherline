package com.mkx.hrttracker.ui

import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
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
}
