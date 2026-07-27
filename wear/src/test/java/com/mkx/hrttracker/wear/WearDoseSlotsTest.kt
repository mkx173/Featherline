package com.mkx.hrttracker.wear

import com.mkx.hrttracker.wear.protocol.WearDoseRow
import com.mkx.hrttracker.wear.protocol.WearDoseSnapshot
import com.mkx.hrttracker.wear.protocol.WearDoseStatus
import com.mkx.hrttracker.wear.protocol.WearEstradiolSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WearDoseSlotsTest {
    @Test
    fun doseSlots_groupsMedicationRowsForOneScheduledSlot() {
        val snapshot = snapshot(
            row("Estradiol", "2 mg", WearDoseStatus.DUE_SOON),
            row("Spironolactone", "50 mg", WearDoseStatus.DONE),
        )

        val slot = snapshot.doseSlots().single()

        assertEquals("Estradiol 2 mg · Spironolactone 50 mg", slot.medicationSummary)
        assertEquals(WearDoseStatus.DUE_SOON, slot.status)
    }

    @Test
    fun nextActionableSlot_prefersDueSoonOverOverdueAndUpcoming() {
        val snapshot = snapshot(
            row(
                "Earlier",
                "1 mg",
                WearDoseStatus.OVERDUE,
                scheduledAt = "2026-07-26T08:00",
            ),
            row(
                "Now",
                "2 mg",
                WearDoseStatus.DUE_SOON,
                scheduledAt = "2026-07-26T12:00",
                groupUuid = "due",
            ),
            row(
                "Later",
                "3 mg",
                WearDoseStatus.UPCOMING,
                scheduledAt = "2026-07-26T20:00",
            ),
        )

        assertEquals("due", snapshot.nextActionableSlot()?.groupUuid)
    }

    @Test
    fun nextActionableSlot_isNullWhenEverythingIsDone() {
        val snapshot = snapshot(row("Done", "2 mg", WearDoseStatus.DONE))

        assertNull(snapshot.nextActionableSlot())
    }

    @Test
    fun todayDoseSlots_excludesTomorrowComingUpRows() {
        val snapshot = snapshot(
            row("Today", "2 mg", WearDoseStatus.DONE),
            row(
                "Tomorrow",
                "2 mg",
                WearDoseStatus.UPCOMING,
                scheduledAt = "2026-07-27T05:00",
                groupUuid = "tomorrow",
            ),
            anchorDateEpochDay = LocalDate.of(2026, 7, 26).toEpochDay(),
        )

        assertEquals(listOf("Today 2 mg"), snapshot.todayDoseSlots().map { it.medicationSummary })
    }

    @Test
    fun nextPlanSlots_excludesHandledTokenAndKeepsCrossDayOrder() {
        val snapshot = snapshot(
            row(
                "First",
                "1 mg",
                WearDoseStatus.DUE_SOON,
                scheduledAt = "2026-07-26T20:00",
                groupUuid = "first",
            ),
            row(
                "Second",
                "2 mg",
                WearDoseStatus.UPCOMING,
                scheduledAt = "2026-07-27T08:00",
                groupUuid = "second",
            ),
            anchorDateEpochDay = LocalDate.of(2026, 7, 26).toEpochDay(),
        )
        val handledToken = snapshot.doseSlots().first().actionToken

        val remaining = snapshot.nextPlanSlots(excludedActionTokens = setOf(handledToken))

        assertEquals(listOf("second"), remaining.map(WearDoseSlot::groupUuid))
        assertEquals(
            "07-27 08:00",
            remaining.single().scheduledDateTimeText(snapshot.anchorDateEpochDay),
        )
    }

    @Test
    fun normalizedSamples_handlesChangingAndFlatCurves() {
        val changing = WearEstradiolSnapshot(
            currentValueText = "30",
            unitLabel = "pg/mL",
            samples = listOf(10.0, 20.0, 30.0),
            sampleIntervalMinutes = 120,
        )
        val flat = changing.copy(samples = listOf(20.0, 20.0))

        assertEquals(listOf(0f, 0.5f, 1f), changing.normalizedSamples())
        assertEquals(listOf(0.5f, 0.5f), flat.normalizedSamples())
    }

    private fun snapshot(
        vararg rows: WearDoseRow,
        anchorDateEpochDay: Long = 1L,
    ): WearDoseSnapshot = WearDoseSnapshot(
        generatedAtEpochMillis = 1L,
        zoneId = "UTC",
        anchorDateEpochDay = anchorDateEpochDay,
        doneCount = 0,
        totalCount = rows.size,
        hideMedicationDetails = false,
        appLanguageTag = "en",
        rows = rows.toList(),
    )

    private fun row(
        medicationName: String,
        doseText: String,
        status: WearDoseStatus,
        scheduledAt: String = "2026-07-26T12:00",
        groupUuid: String = "group",
    ): WearDoseRow = WearDoseRow(
        medicationName = medicationName,
        groupName = "Daily",
        routeLabel = "Oral",
        doseText = doseText,
        status = status,
        scheduledAt = scheduledAt,
        trailingText = null,
        groupUuid = groupUuid,
        scheduleTimeUuid = "slot",
    )
}
