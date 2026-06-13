package com.mkx.hrttracker.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WeightDialog seeds its editable state from the profile passed by SettingsScreen, which
 * comes from uiState — a StateFlow whose initial value is SettingsUiState() (weight unset,
 * KILOGRAMS). A dialog composed before the profile loads — e.g. a process-death restore
 * that reopens it — must re-seed to the real profile once it arrives, so it shows the
 * user's actual weight and unit (and Save reports them) instead of stranding an empty
 * field at the default unit. Without the keyed remember in WeightDialog the local state
 * freezes on the placeholder, hiding the weight and risking a save under the wrong unit.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WeightDialogReseedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialog_reseedsToRealProfile_whenItArrivesAfterPlaceholder() {
        // Starts at the uiState placeholder: weight unset, default KILOGRAMS.
        var profile by mutableStateOf(UserProfile())
        var saved: Pair<Double, WeightUnit>? = null

        composeRule.setContent {
            WeightDialog(
                profile = profile,
                onSave = { value, unit -> saved = value to unit },
                onClear = {},
                onDismiss = {},
            )
        }

        // First frame shows the placeholder: the field is empty, so the user's real
        // weight is not displayed yet.
        composeRule.onNodeWithText("115").assertDoesNotExist()

        // The real profile lands a frame later (DB read completes).
        profile = UserProfile(
            weightOriginalValue = 115.0,
            weightOriginalUnit = WeightUnit.POUNDS,
        )
        composeRule.waitForIdle()

        // The field must now show the real weight, not the stranded empty placeholder.
        composeRule.onNodeWithText("115").assertExists()

        // ...and Save reports the real value AND unit (POUNDS), not the default KILOGRAMS.
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(115.0 to WeightUnit.POUNDS, saved)
    }
}
