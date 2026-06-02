package com.mkx.hrttracker.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Rule
import org.junit.Test

class HrtSnackbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsMessageAndActionThenAutoDismissesAfterFiveSeconds() {
        val hostState = SnackbarHostState()
        // Control the clock so the 5s countdown is deterministic.
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                SnackbarHost(
                    hostState = hostState,
                    snackbar = { data -> HrtSnackbar(data) },
                )
                LaunchedEffect(Unit) {
                    hostState.showSnackbar(
                        message = "Low stock: Estradiol",
                        actionLabel = "View",
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite,
                    )
                }
            }
        }

        // Advance past the enter animation; the snackbar is on screen.
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("Low stock: Estradiol").assertIsDisplayed()
        composeRule.onNodeWithText("View").assertIsDisplayed()

        // Advance past the 5s countdown (plus the exit animation); it dismisses.
        composeRule.mainClock.advanceTimeBy(6_000)
        composeRule.onNodeWithText("Low stock: Estradiol").assertDoesNotExist()
    }
}
