package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.MedicationDayBoundary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The editor's start rows, and whether it keeps showing a new plan as one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicationGroupEditorStartRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val date = LocalDate.of(2026, 11, 8)

    @Test
    fun aStartTimeRowNamesItsDateOnlyWhenItIsAnotherDay() {
        var shown by mutableStateOf(
            MedicationGroupEditorUiState(
                sinceDate = date,
                scheduleStart = ScheduleStart.At(LocalTime.of(1, 0)),
                medicationDayBoundary = MedicationDayBoundary.FourAm,
            )
        )
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                ScheduleStartEditor(
                    sinceDate = shown.sinceDate,
                    startsNow = shown.startsNow,
                    start = shown.scheduleStartAt(date.atTime(12, 0)),
                    dateFormatter = { it.toString() },
                    timeFormatter = DateTimeFormatter.ofPattern("HH:mm"),
                    onDateClick = {},
                    onTimeClick = {},
                )
            }
        }
        val pastMidnight = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.medication_day_past_midnight)

        // A night owl's picked 01:00 is that calendar date's 01:00: no date repeated, no moon.
        composeRule.onNodeWithText("01:00").assertExists()
        composeRule.onNodeWithContentDescription(pastMidnight).assertDoesNotExist()

        // A start the form holds on another date (a hand-made state) still names its day.
        shown = shown.copy(scheduleStart = ScheduleStart.Loaded(LocalDateTime.of(date.plusDays(2), LocalTime.of(14, 0)), date))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("01:00").assertDoesNotExist()
        composeRule.onNodeWithText("${date.plusDays(2)}", substring = true).assertExists()
    }

    @Test
    fun aNewPlanStaysNewOnceItsSaveGivesItAnId() {
        var state by mutableStateOf(MedicationGroupEditorUiState(editingGroupId = "a", isLoadingGroupForEditing = false))
        var started = false
        composeRule.setContent { started = rememberStartedAsNewGroupCreationFlow(state) }
        assertEquals(false, started)

        // The archive shortcut's copy shown in place, then saved.
        state = MedicationGroupEditorUiState(editingGroupId = null)
        composeRule.waitForIdle()
        assertEquals(true, started)
        state = state.copy(editingGroupId = "b", isSaved = true, isFinishingAfterSave = true)
        composeRule.waitForIdle()
        assertEquals(true, started)
        assertEquals(
            false,
            shouldRenderMedicationGroupEditorAsEditing(
                uiState = state,
                isNewGroupCreationFlow = resolveMedicationGroupEditorIsNewGroupCreationFlow(state, started, false),
            ),
        )
    }
}
