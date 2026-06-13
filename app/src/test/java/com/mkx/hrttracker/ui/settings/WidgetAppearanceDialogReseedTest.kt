package com.mkx.hrttracker.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.widget.WidgetAppearance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The in-app widget appearance dialog seeds its sliders from viewModel.widgetAppearance,
 * a StateFlow whose initial value is WidgetAppearance.Default. A dialog composed before
 * the first DataStore emission — e.g. a process-death restore that reopens the dialog —
 * must re-seed to the real persisted value once it arrives, so Save falls back to the
 * currently set value instead of overwriting it with the 100%/100%/Follow-system
 * placeholder (PR #63 finding 4). Without the keyed remember in WidgetAppearanceDialog
 * the local state would freeze on the placeholder and Save would clobber the user's
 * stored scale/alpha/darkMode.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetAppearanceDialogReseedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialog_reseedsToRealValue_whenItArrivesAfterPlaceholder() {
        // Starts at the StateFlow placeholder (Default = 100% / 100% / Follow system).
        var appearance by mutableStateOf(WidgetAppearance.Default)
        var saved: WidgetAppearance? = null

        composeRule.setContent {
            WidgetAppearanceDialog(
                appearance = appearance,
                onAppearanceChange = { saved = it },
                onDismiss = {},
            )
        }

        // First frame shows the placeholder: both scale and opacity read 100%.
        composeRule.onAllNodesWithText("100%").assertCountEquals(2)

        // The real persisted value lands a frame later (DataStore emission).
        appearance = WidgetAppearance.Default.copy(
            contentScale = 0.7f,
            backgroundAlpha = 0.6f,
            darkMode = DarkModeOption.DARK,
        )
        composeRule.waitForIdle()

        // The dialog must now reflect the real value, not the stranded placeholder.
        composeRule.onNodeWithText("70%").assertExists()
        composeRule.onNodeWithText("60%").assertExists()

        // ...and Save persists the real value — every field round-trips, with the theme
        // params (hue/saturation/balance) preserved at their re-seeded defaults.
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(
            WidgetAppearance.Default.copy(
                contentScale = 0.7f,
                backgroundAlpha = 0.6f,
                darkMode = DarkModeOption.DARK,
            ),
            saved,
        )
    }
}
