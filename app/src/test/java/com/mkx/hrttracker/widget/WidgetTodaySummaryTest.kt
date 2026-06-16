package com.mkx.hrttracker.widget

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WidgetTodaySummaryTest {

    // ── widgetMediumCount ───────────────────────────────────────────────────────

    @Test
    fun mediumCountPlanOnlyShowsDoneOverTotal() {
        val count = widgetMediumCount(
            doneCount = 2, totalCount = 3, manualCount = 0,
            doneLabel = DONE, manualLabel = MANUAL,
        )

        assertEquals(WidgetMediumCount(hero = "2", suffix = "/3 DONE"), count)
    }

    @Test
    fun mediumCountPlanWithManualAppendsManualCount() {
        val count = widgetMediumCount(
            doneCount = 2, totalCount = 3, manualCount = 1,
            doneLabel = DONE, manualLabel = MANUAL,
        )

        assertEquals(WidgetMediumCount(hero = "2", suffix = "/3 (1) DONE"), count)
    }

    @Test
    fun mediumCountManualOnlyShowsManualLabelInsteadOfZeroOverZero() {
        // The reported bug: a plan-less day with a manual record used to render "0/0".
        val count = widgetMediumCount(
            doneCount = 0, totalCount = 0, manualCount = 1,
            doneLabel = DONE, manualLabel = MANUAL,
        )

        assertEquals(WidgetMediumCount(hero = "1", suffix = " MANUAL"), count)
    }

    @Test
    fun mediumCountNothingAtAllIsNullSoTheRowIsHidden() {
        val count = widgetMediumCount(
            doneCount = 0, totalCount = 0, manualCount = 0,
            doneLabel = DONE, manualLabel = MANUAL,
        )

        assertNull(count)
    }

    // ── widgetLargeCountLabel ───────────────────────────────────────────────────

    @Test
    fun largeLabelMatchesMediumHeroPlusSuffixForEveryCase() {
        // Both widgets must read identically; the large label is exactly the medium
        // hero + suffix so they can never drift apart.
        fun large(done: Int, total: Int, manual: Int) =
            widgetLargeCountLabel(done, total, manual, DONE, MANUAL)

        assertEquals("2/3 DONE", large(2, 3, 0))
        assertEquals("2/3 (1) DONE", large(2, 3, 1))
        assertEquals("1 MANUAL", large(0, 0, 1))
        assertNull(large(0, 0, 0))
    }

    // ── mediumNothingScheduledToday ─────────────────────────────────────────────

    @Test
    fun nothingScheduledWhenNoRowsAtAll() {
        assertTrue(mediumNothingScheduledToday(emptyList()))
    }

    @Test
    fun nothingScheduledWhenOnlyManualRecords() {
        // Regression for the blank medium bottom panel: a manual-only day must read as
        // "nothing scheduled" so the bottom shows the "no doses today" badge rather than
        // falling through to a blank panel.
        assertTrue(
            mediumNothingScheduledToday(
                listOf(doseRow(isManualRecord = true, contextChip = null))
            )
        )
    }

    @Test
    fun scheduledRowMeansSomethingIsScheduled() {
        assertFalse(
            mediumNothingScheduledToday(
                listOf(doseRow(isManualRecord = false, contextChip = null))
            )
        )
    }

    @Test
    fun comingUpRowMeansSomethingIsScheduled() {
        assertFalse(
            mediumNothingScheduledToday(
                listOf(doseRow(isManualRecord = false, contextChip = WidgetDoseChip.COMING_UP))
            )
        )
    }

    @Test
    fun manualRecordsAlongsideAScheduledRowAreStillScheduled() {
        assertFalse(
            mediumNothingScheduledToday(
                listOf(
                    doseRow(isManualRecord = true, contextChip = null),
                    doseRow(isManualRecord = false, contextChip = null),
                )
            )
        )
    }

    // ── mediumBadgeIconRes ──────────────────────────────────────────────────────

    @Test
    fun allDoneGetsTheEmphaticDoneAllIcon() {
        // The best outcome (every dose in-window) must win over everythingLogged — which it
        // implies — and get the double-check, not the plain one.
        assertEquals(
            R.drawable.ic_done_all,
            mediumBadgeIconRes(
                allInWindow = true,
                nothingScheduledToday = false,
                everythingLogged = true,
            ),
        )
    }

    @Test
    fun allLoggedButOffWindowGetsThePlainCheck() {
        assertEquals(
            R.drawable.ic_check,
            mediumBadgeIconRes(
                allInWindow = false,
                nothingScheduledToday = false,
                everythingLogged = true,
            ),
        )
    }

    @Test
    fun noDosesTodayGetsThePlainCheck() {
        assertEquals(
            R.drawable.ic_check,
            mediumBadgeIconRes(
                allInWindow = false,
                nothingScheduledToday = true,
                everythingLogged = false,
            ),
        )
    }

    @Test
    fun aMissedSlotGetsTheExclamation() {
        assertEquals(
            R.drawable.ic_exclamation,
            mediumBadgeIconRes(
                allInWindow = false,
                nothingScheduledToday = false,
                everythingLogged = false,
            ),
        )
    }

    private fun doseRow(
        isManualRecord: Boolean,
        contextChip: WidgetDoseChip?,
    ): WidgetDoseRow = WidgetDoseRow(
        medicationName = "Estradiol valerate",
        groupName = "Estradiol",
        colorKey = MedicationGroupColorKey.ROSE,
        routeLabel = "Oral",
        doseText = "2 mg",
        status = WidgetDoseStatus.DONE,
        scheduledAt = LocalDateTime.of(2026, 1, 1, 9, 0),
        trailingText = null,
        isManualRecord = isManualRecord,
        contextChip = contextChip,
        groupUuid = if (isManualRecord) null else "group-1",
        scheduleTimeUuid = null,
    )

    private companion object {
        const val DONE = "DONE"
        const val MANUAL = "MANUAL"
    }
}
