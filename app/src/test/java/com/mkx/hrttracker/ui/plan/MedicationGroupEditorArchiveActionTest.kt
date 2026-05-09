package com.mkx.hrttracker.ui.plan

import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationGroupEditorArchiveActionTest {
    @Test
    fun runArchiveConfirmationAction_whenRecreating_dismissesInputBeforeAction() {
        val calls = mutableListOf<String>()
        val focusManager = mockk<FocusManager>(relaxed = true)
        val keyboardController = mockk<SoftwareKeyboardController>(relaxed = true)

        every { focusManager.clearFocus(force = true) } answers {
            calls += "focus"
        }
        every { keyboardController.hide() } answers {
            calls += "hide"
        }

        runArchiveConfirmationAction(
            shouldCreateActiveCopyAfterArchive = true,
            focusManager = focusManager,
            keyboardController = keyboardController,
            onArchiveConfirm = {
                calls += "archive"
            },
            onArchiveAndRecreateConfirm = {
                calls += "recreate"
            },
        )

        assertEquals(listOf("focus", "hide", "recreate"), calls)
    }
}
