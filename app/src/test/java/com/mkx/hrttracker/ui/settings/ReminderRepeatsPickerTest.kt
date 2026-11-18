package com.mkx.hrttracker.ui.settings

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.settings.ReminderRepeats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReminderRepeatsPickerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val confirm get() = RuntimeEnvironment.getApplication().getString(R.string.confirm)

    // Reopened after Confirm, before that save reached settingsState: the wheels follow it.
    @Test
    fun aPickerOpenedBeforeTheLastSaveLanded_untouched_confirmsTheSavedValue() {
        var repeats by mutableStateOf(ReminderRepeats(count = 3, intervalMinutes = 15))
        var confirmed: ReminderRepeats? = null
        composeRule.setContent {
            ReminderRepeatsPickerDialog(repeats = repeats, onDismiss = {}, onConfirm = { confirmed = it })
        }
        composeRule.waitForIdle()

        repeats = ReminderRepeats(count = 1, intervalMinutes = 30)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirm).performClick()

        assertEquals(ReminderRepeats(count = 1, intervalMinutes = 30), confirmed)
    }

    // After a process death Settings composes the default repeats first, then the stored ones;
    // the wheels still come back as they were on screen.
    @Test
    fun aTurnedWheel_survivesRestoration_whenTheDefaultComposesFirst() {
        val stored = ReminderRepeats(count = 2, intervalMinutes = 30)
        var confirmed: ReminderRepeats? = null
        var defaultsComposed = 0
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            var loaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { loaded = true }
            if (!loaded) defaultsComposed++
            ReminderRepeatsPickerDialog(
                repeats = if (loaded) stored else ReminderRepeats(),
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3").performClick() // a tap on the next row turns the wheel to it
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirm).performClick()
        assertEquals(ReminderRepeats(count = 3, intervalMinutes = 30), confirmed)
        val before = defaultsComposed

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        confirmed = null
        composeRule.onNodeWithText(confirm).performClick()

        assertTrue("the default composed first after the restore", defaultsComposed > before)
        assertEquals(ReminderRepeats(count = 3, intervalMinutes = 30), confirmed)
    }
}
