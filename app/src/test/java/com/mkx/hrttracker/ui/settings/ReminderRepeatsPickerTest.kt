package com.mkx.hrttracker.ui.settings

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

    // Reopened on the old value, the count wheel turned to 4, then the earlier save lands: the
    // turned wheel keeps 4 and the untouched interval wheel keeps what it showed.
    @Test
    fun aWheelTurnedBeforeTheLastSaveLanded_keepsTheNewSelection() {
        var repeats by mutableStateOf(ReminderRepeats(count = 3, intervalMinutes = 15))
        var confirmed: ReminderRepeats? = null
        composeRule.setContent {
            ReminderRepeatsPickerDialog(repeats = repeats, onDismiss = {}, onConfirm = { confirmed = it })
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("4").performClick()
        composeRule.waitForIdle()

        repeats = ReminderRepeats(count = 1, intervalMinutes = 30)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirm).performClick()

        assertEquals(ReminderRepeats(count = 4, intervalMinutes = 15), confirmed)
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

    // The same restore with no wheel turned: the wheels end on the stored value, not the default.
    @Test
    fun untouchedWheels_afterRestoration_endOnTheStoredValue() {
        val stored = ReminderRepeats(count = 2, intervalMinutes = 30)
        var confirmed: ReminderRepeats? = null
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            var loaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { loaded = true }
            ReminderRepeatsPickerDialog(
                repeats = if (loaded) stored else ReminderRepeats(),
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }
        composeRule.waitForIdle()

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirm).performClick()

        assertEquals(stored, confirmed)
    }

    // Only the interval wheel turned: it still counts as touched, so the late save moves nothing.
    @Test
    fun anIntervalWheelTurnedBeforeTheLastSaveLanded_keepsTheNewSelection() {
        var repeats by mutableStateOf(ReminderRepeats(count = 3, intervalMinutes = 15))
        var confirmed: ReminderRepeats? = null
        composeRule.setContent {
            ReminderRepeatsPickerDialog(repeats = repeats, onDismiss = {}, onConfirm = { confirmed = it })
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(intervalLabel(20)).performClick()
        composeRule.waitForIdle()

        repeats = ReminderRepeats(count = 1, intervalMinutes = 30)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirm).performClick()

        assertEquals(ReminderRepeats(count = 3, intervalMinutes = 20), confirmed)
    }

    // Control for the next test: a drag of 1.3 rows and a release turns the wheel from 3 to 4.
    @Test
    fun aDragPastARow_turnsTheWheel() {
        var confirmed: ReminderRepeats? = null
        composeRule.setContent {
            ReminderRepeatsPickerDialog(
                repeats = ReminderRepeats(count = 3, intervalMinutes = 15),
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }
        composeRule.waitForIdle()
        val row = rowHeightPx()
        composeRule.onNodeWithText("3").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, -row * 1.3f / 10)) }
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirm).performClick()

        assertEquals(ReminderRepeats(count = 4, intervalMinutes = 15), confirmed)
    }

    // The late save lands while the finger is still down: the drag is kept.
    @Test
    fun aLateSaveLandingMidDrag_keepsTheDrag() {
        var repeats by mutableStateOf(ReminderRepeats(count = 3, intervalMinutes = 15))
        var confirmed: ReminderRepeats? = null
        composeRule.setContent {
            ReminderRepeatsPickerDialog(repeats = repeats, onDismiss = {}, onConfirm = { confirmed = it })
        }
        composeRule.waitForIdle()
        val row = rowHeightPx()
        composeRule.onNodeWithText("3").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, -row * 1.3f / 10)) }
        }
        composeRule.waitForIdle()

        repeats = ReminderRepeats(count = 1, intervalMinutes = 30)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3").performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirm).performClick()

        assertEquals(ReminderRepeats(count = 4, intervalMinutes = 15), confirmed)
    }

    // A wheel turned before the late save, then a process death in which the default composes
    // first: the turn survives (the seed is saved with the wheels).
    @Test
    fun aWheelTurnedBeforeTheLastSaveLanded_thenProcessDeath_keepsTheTurn() {
        val old = ReminderRepeats(count = 2, intervalMinutes = 20)
        val saved = ReminderRepeats(count = 1, intervalMinutes = 30)
        var late by mutableStateOf(false)
        var confirmed: ReminderRepeats? = null
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            var loaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { loaded = true }
            ReminderRepeatsPickerDialog(
                repeats = if (!loaded) ReminderRepeats() else if (late) saved else old,
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3").performClick()
        composeRule.waitForIdle()
        late = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirm).performClick()
        assertEquals(ReminderRepeats(count = 3, intervalMinutes = 20), confirmed)

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        confirmed = null
        composeRule.onNodeWithText(confirm).performClick()

        assertEquals(ReminderRepeats(count = 3, intervalMinutes = 20), confirmed)
    }

    private fun intervalLabel(minutes: Int): String = RuntimeEnvironment.getApplication()
        .getString(R.string.settings_reminder_repeats_interval_value, minutes)

    private fun rowHeightPx(): Float {
        val three = composeRule.onNodeWithText("3").getUnclippedBoundsInRoot()
        val four = composeRule.onNodeWithText("4").getUnclippedBoundsInRoot()
        return with(composeRule.density) { (four.top - three.top).toPx() }
    }
}
