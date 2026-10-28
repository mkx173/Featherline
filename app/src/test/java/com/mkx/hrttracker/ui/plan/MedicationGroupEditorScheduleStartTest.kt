package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.util.MedicationDayBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
        // A whole day ending at 04:00 starts at 04:00, so turning the switch off
        // later can't pull that night's doses in before the start.
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
        // An all-night plan shifted by turning night owl on: its start date moved
        // back a day, its saved start didn't.
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
    fun upcomingPreview_skipsDosesBeforeAFutureStart() {
        val occurrences = buildMedicationGroupEditorUpcomingOccurrences(
            uiState = state(sinceDate = today.plusDays(1), startTime = LocalTime.of(12, 0)),
            start = now,
        )

        assertEquals(LocalDateTime.of(today.plusDays(2), LocalTime.of(9, 0)), occurrences.first())
    }
}
