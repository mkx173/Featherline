package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

// The medium widget's bottom card must never claim "nothing more today" while a missed
// dose is still loggable: a past-grace slot stays selected (for an out-of-window log)
// until the next slot's window opens, and only a slot actionable right now outranks it.
class HrtWidgetActiveRowSelectionTest {
    private val morning = LocalDateTime.of(2026, 1, 1, 9, 0)
    private val noon = LocalDateTime.of(2026, 1, 1, 13, 0)
    private val evening = LocalDateTime.of(2026, 1, 1, 20, 0)

    @Test
    fun dueSoonSlotOutranksAnEarlierMissedSlot() {
        val overdue = row(WidgetDoseStatus.OVERDUE, morning)
        val dueSoon = row(WidgetDoseStatus.DUE_SOON, noon)

        val selected = selectActiveScheduledGroup(listOf(listOf(overdue), listOf(dueSoon)))

        assertEquals(listOf(dueSoon), selected)
    }

    @Test
    fun missedSlotHoldsUntilTheNextSlotWindowOpens() {
        // The evening dose is still UPCOMING (its window hasn't opened), so the missed
        // morning dose keeps the card instead of the far-future one.
        val overdue = row(WidgetDoseStatus.OVERDUE, morning)
        val upcoming = row(WidgetDoseStatus.UPCOMING, evening)

        val selected = selectActiveScheduledGroup(listOf(listOf(overdue), listOf(upcoming)))

        assertEquals(listOf(overdue), selected)
    }

    @Test
    fun mostRecentlyMissedSlotWins_whenSeveralAreMissed() {
        val morningMiss = row(WidgetDoseStatus.OVERDUE, morning)
        val noonMiss = row(WidgetDoseStatus.OVERDUE, noon)

        val selected = selectActiveScheduledGroup(listOf(listOf(morningMiss), listOf(noonMiss)))

        assertEquals(listOf(noonMiss), selected)
    }

    @Test
    fun earliestUpcomingSlotShows_whenNothingIsMissedOrDue() {
        val done = row(WidgetDoseStatus.DONE, morning)
        val upcoming = row(WidgetDoseStatus.UPCOMING, evening)

        val selected = selectActiveScheduledGroup(listOf(listOf(done), listOf(upcoming)))

        assertEquals(listOf(upcoming), selected)
    }

    @Test
    fun nothingSelected_whenEverySlotIsAddressed() {
        val done = row(WidgetDoseStatus.DONE, morning)
        val loggedLate = row(WidgetDoseStatus.LOGGED_OUT_OF_WINDOW, noon)

        assertNull(selectActiveScheduledGroup(listOf(listOf(done), listOf(loggedLate))))
    }

    @Test
    fun halfFulfilledGroupStillSelectedByItsUnaddressedMember() {
        val group = listOf(
            row(WidgetDoseStatus.DONE, morning),
            row(WidgetDoseStatus.OVERDUE, morning),
        )

        assertEquals(group, selectActiveScheduledGroup(listOf(group)))
    }

    @Test
    fun lastNightMissedDoseSurvivesMidnight_untilTodaysWindowOpens() {
        // After the midnight snapshot rebuild yesterday's 21:00 dose arrives as a
        // LAST_NIGHT carry-over; it must stay on the card instead of today's
        // still-far-off evening dose.
        val missedLastNight = row(
            WidgetDoseStatus.OVERDUE,
            LocalDateTime.of(2025, 12, 31, 21, 0),
            contextChip = WidgetDoseChip.LAST_NIGHT,
        )
        val todayUpcoming = row(WidgetDoseStatus.UPCOMING, evening)

        val selected = selectMediumActiveScheduledGroup(listOf(missedLastNight, todayUpcoming))

        assertEquals(listOf(missedLastNight), selected)
    }

    @Test
    fun lastNightDoseStillInItsWindowOutranksEverything() {
        // A 23:30 dose is still in its fulfillment window at 00:15; it must stay
        // actionable rather than vanish with the calendar day.
        val stillInWindow = row(
            WidgetDoseStatus.DUE_SOON,
            LocalDateTime.of(2025, 12, 31, 23, 30),
            contextChip = WidgetDoseChip.LAST_NIGHT,
        )
        val todayUpcoming = row(WidgetDoseStatus.UPCOMING, morning)

        val selected = selectMediumActiveScheduledGroup(listOf(stillInWindow, todayUpcoming))

        assertEquals(listOf(stillInWindow), selected)
    }

    @Test
    fun todaysActionableDoseOutranksALastNightMiss() {
        val missedLastNight = row(
            WidgetDoseStatus.OVERDUE,
            LocalDateTime.of(2025, 12, 31, 21, 0),
            contextChip = WidgetDoseChip.LAST_NIGHT,
        )
        val dueNow = row(WidgetDoseStatus.DUE_SOON, morning)

        val selected = selectMediumActiveScheduledGroup(listOf(missedLastNight, dueNow))

        assertEquals(listOf(dueNow), selected)
    }

    @Test
    fun manualRecordsNeverDriveTheCard() {
        val manual = row(WidgetDoseStatus.DONE, morning).copy(isManualRecord = true)

        assertNull(selectMediumActiveScheduledGroup(listOf(manual)))
    }

    private fun row(
        status: WidgetDoseStatus,
        scheduledAt: LocalDateTime,
        contextChip: WidgetDoseChip? = null,
    ): WidgetDoseRow {
        return WidgetDoseRow(
            medicationName = "Estradiol valerate",
            groupName = "Estradiol valerate",
            colorKey = MedicationGroupColorKey.ROSE,
            routeLabel = "Oral",
            doseText = "2 mg",
            status = status,
            scheduledAt = scheduledAt,
            trailingText = null,
            isManualRecord = false,
            contextChip = contextChip,
            groupUuid = "group-1",
            scheduleTimeUuid = null,
        )
    }
}
