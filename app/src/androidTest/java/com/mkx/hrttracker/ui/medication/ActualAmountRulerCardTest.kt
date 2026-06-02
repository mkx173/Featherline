package com.mkx.hrttracker.ui.medication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActualAmountRulerCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialCompositionDoesNotEmitAlreadySelectedDelta() {
        val emittedDeltas = mutableListOf<Double?>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                ActualAmountRulerCard(
                    preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
                    allowsActualDoseDelta = true,
                    plannedAmount = 0.25,
                    doseAmountDelta = null,
                    isSaving = false,
                    onDoseAmountDeltaChange = emittedDeltas::add,
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        assertTrue(
            "No unchanged delta should be emitted during initial ruler composition.",
            emittedDeltas.isEmpty(),
        )
    }

    @Test
    fun selectionChangeSettlesWithSingleChangedDeltaEmission() {
        var doseAmountDelta by mutableStateOf<Double?>(null)
        val emittedDeltas = mutableListOf<Double?>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                ActualAmountRulerCard(
                    preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
                    allowsActualDoseDelta = true,
                    plannedAmount = 0.25,
                    doseAmountDelta = doseAmountDelta,
                    isSaving = false,
                    onDoseAmountDeltaChange = { delta ->
                        emittedDeltas += delta
                        doseAmountDelta = delta
                    },
                )
            }
        }
        composeRule.waitForIdle()
        emittedDeltas.clear()

        composeRule
            .onNode(hasScrollToIndexAction())
            .performScrollToIndex(10)
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        assertEquals(
            "A settled ruler selection change should emit exactly one changed delta.",
            1,
            emittedDeltas.size,
        )
        assertEquals(0.05, emittedDeltas.single() ?: 0.0, 1e-9)
    }

    @Test
    fun resetButtonReturnsRulerToPlannedAmountAndEmitsNullOnce() {
        val resetDescription = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.medication_log_actual_amount_reset)
        var doseAmountDelta by mutableStateOf<Double?>(null)
        val emittedDeltas = mutableListOf<Double?>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                ActualAmountRulerCard(
                    preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
                    allowsActualDoseDelta = true,
                    plannedAmount = 0.25,
                    doseAmountDelta = doseAmountDelta,
                    isSaving = false,
                    onDoseAmountDeltaChange = { delta ->
                        emittedDeltas += delta
                        doseAmountDelta = delta
                    },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithContentDescription(resetDescription)
            .assertIsNotEnabled()

        composeRule
            .onNode(hasScrollToIndexAction())
            .performScrollToIndex(10)
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        assertEquals(0.05, emittedDeltas.single() ?: 0.0, 1e-9)
        emittedDeltas.clear()

        composeRule
            .onNodeWithContentDescription(resetDescription)
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        assertEquals(
            "Reset should settle at the planned amount with a single null delta.",
            listOf(null),
            emittedDeltas,
        )
        composeRule
            .onNodeWithContentDescription(resetDescription)
            .assertIsNotEnabled()
    }

    @Test
    fun scrollBlockedSignalClearsOnlyAfterDeltaCommits() {
        // The scroll-blocked signal exists so callers can swallow a Save tapped
        // mid-scroll. The delta commits one settle-debounce after scrolling
        // visually stops, so the signal must stay blocked across that window:
        // clearing it the instant scrolling stops would re-enable Save before
        // the committed delta lands, persisting the pre-settle value. Record
        // both callbacks into one ordered log and assert the commit precedes the
        // unblock.
        var doseAmountDelta by mutableStateOf<Double?>(null)
        val events = mutableListOf<String>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                ActualAmountRulerCard(
                    preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
                    allowsActualDoseDelta = true,
                    plannedAmount = 0.25,
                    doseAmountDelta = doseAmountDelta,
                    isSaving = false,
                    onDoseAmountDeltaChange = { delta ->
                        events += "commit"
                        doseAmountDelta = delta
                    },
                    onScrollingChange = { scrolling ->
                        events += if (scrolling) "block" else "unblock"
                    },
                )
            }
        }
        composeRule.waitForIdle()
        // Drop the initial settle (no-op commit skipped, trailing unblock) so the
        // assertions only inspect the scroll under test.
        events.clear()

        composeRule
            .onNode(hasScrollToIndexAction())
            .performScrollToIndex(10)
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        val commitIndex = events.indexOf("commit")
        val lastUnblockIndex = events.lastIndexOf("unblock")
        assertTrue("Expected a delta commit during the settle: $events", commitIndex >= 0)
        assertTrue("Expected an unblock after settling: $events", lastUnblockIndex >= 0)
        assertTrue(
            "Scroll-blocked signal cleared before the delta committed: $events",
            commitIndex < lastUnblockIndex,
        )
    }
}
