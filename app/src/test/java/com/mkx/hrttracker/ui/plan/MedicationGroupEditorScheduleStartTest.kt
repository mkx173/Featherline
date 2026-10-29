package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.util.MedicationDayBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MedicationGroupEditorScheduleStartTest {
    private val today = LocalDate.of(2026, 4, 25)
    private val now = LocalDateTime.of(today, LocalTime.of(10, 0))

    private fun state(
        sinceDate: LocalDate = today,
        startsNow: Boolean = false,
        startTime: LocalTime? = null,
        boundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
    ) = MedicationGroupEditorUiState(
        sinceDate = sinceDate,
        startsNow = startsNow,
        startTime = startTime,
        dailyTimes = listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0))),
        medicationDayBoundary = boundary,
    )

    @Test
    fun scheduleStartAt_resolvesNowWholeDayAndSmallHours() {
        assertEquals(now, state(startsNow = true).scheduleStartAt(now))
        assertEquals(today.atStartOfDay(), state().scheduleStartAt(now))
        // A whole start date starts at its day boundary, 04:00 when the plan's day
        // ends then.
        assertEquals(
            LocalDateTime.of(today, LocalTime.of(4, 0)),
            state(boundary = MedicationDayBoundary.FourAm).scheduleStartAt(now),
        )
        assertEquals(
            LocalDateTime.of(today, LocalTime.of(8, 0)),
            state(startTime = LocalTime.of(8, 0)).scheduleStartAt(now),
        )
        // With the day ending at 04:00, 02:00 is the night after the start date.
        assertEquals(
            LocalDateTime.of(today.plusDays(1), LocalTime.of(2, 0)),
            state(startTime = LocalTime.of(2, 0), boundary = MedicationDayBoundary.FourAm).scheduleStartAt(now),
        )
    }

    @Test
    fun pastScheduleWindow_onlyCountsDosesAfterTheStart() {
        assertTrue(hasPastScheduleOptionWindow(state(), now))
        assertFalse(hasPastScheduleOptionWindow(state(startsNow = true), now))
        assertFalse(hasPastScheduleOptionWindow(state(startTime = LocalTime.of(9, 30)), now))
        assertTrue(hasPastScheduleOptionWindow(state(sinceDate = today.minusDays(1), startTime = LocalTime.of(9, 30)), now))
        // Every 91 days, starting after that day's dose: the next one is 91 days on, still past.
        assertTrue(
            hasPastScheduleOptionWindow(
                state(sinceDate = today.minusDays(200), startTime = LocalTime.of(12, 0)).copy(dailyIntervalDays = "91"),
                now,
            )
        )
    }

    @Test
    fun pastScheduleSection_hiddenOnceThePlanCanNoLongerChangeIt() {
        val editing = state().copy(editingGroupId = "c53536d0-fc0a-4726-890f-e0904b0c9f95")

        assertTrue(shouldShowPastScheduleSection(editing.copy(areEntriesLoaded = true), now))
        assertFalse(shouldShowPastScheduleSection(editing, now))
        assertFalse(shouldShowPastScheduleSection(editing.copy(areEntriesLoaded = true, plannedEntryCount = 1), now))
        assertFalse(shouldShowPastScheduleSection(editing.copy(areEntriesLoaded = true, relatedEntryCount = 1), now))
    }

    @Test
    fun loadedStart_survivesUntilTheStartIsEdited() {
        // A saved start past its start date's day, as a plan that hid its past from
        // a later save has: it holds until the start is edited.
        val loadedStartAt = LocalDateTime.of(today, LocalTime.of(23, 0))
        val loaded = state(sinceDate = today.minusDays(1), boundary = MedicationDayBoundary.FourAm)
            .copy(loadedStartAt = loadedStartAt)

        assertEquals(loadedStartAt, loaded.scheduleStartAt(now))
        assertEquals(
            LocalDateTime.of(today.minusDays(2), LocalTime.of(4, 0)),
            editorStateWithUpdatedSinceDate(loaded, today.minusDays(2)).scheduleStartAt(now),
        )
    }

    @Test
    fun loadedStart_isNeverBeforeTheStartDatesDay() {
        // 02:00 is still the previous day when the day ends at 04:00.
        val loaded = state(boundary = MedicationDayBoundary.FourAm)
            .copy(loadedStartAt = today.atTime(2, 0))

        assertEquals(today.atTime(4, 0), loaded.scheduleStartAt(now))
    }

    @Test
    fun changingStartDate_startsWithTheWholeDay() {
        val updated = editorStateWithUpdatedSinceDate(state(startsNow = true), today.minusDays(3))

        assertEquals(today.minusDays(3), updated.sinceDate)
        assertFalse(updated.startsNow)
        assertNull(updated.startTime)
        assertNull(updated.loadedStartAt)
        // Re-picking the same date keeps "Now".
        assertTrue(editorStateWithUpdatedSinceDate(state(startsNow = true), today).startsNow)
    }

    @Test
    fun startingNow_keepsPickedWeekdays() {
        val weekly = state(sinceDate = today.plusDays(3)).copy(
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            hasUserCustomizedWeeklyDays = true,
        )
        val started = editorStateStartingNow(weekly, today)

        assertEquals(today, started.sinceDate)
        assertTrue(started.startsNow)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), started.weeklyDaysOfWeek)
        // Untouched weekdays still follow the start date, as when picking one.
        assertEquals(
            setOf(today.dayOfWeek),
            editorStateStartingNow(weekly.copy(hasUserCustomizedWeeklyDays = false), today).weeklyDaysOfWeek,
        )
    }

    @Test
    fun upcomingPreview_skipsDosesBeforeAFutureStart() {
        val occurrences = buildMedicationGroupEditorUpcomingOccurrences(
            uiState = state(sinceDate = today.plusDays(1), startTime = LocalTime.of(12, 0)),
            start = now,
        )

        assertEquals(LocalDateTime.of(today.plusDays(2), LocalTime.of(9, 0)), occurrences.first())
    }
}
