package com.mkx.hrttracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class HomePkProjectionRecordTest {
    @Test
    fun `toWidgetRecord preserves core projection fields`() {
        val record = HomePkProjectionRecord(
            generatedAtEpochMillis = 1_000L,
            windowStartEpochMillis = 500L,
            windowEndEpochMillis = 2_000L,
            pkProjectionExpiresAtEpochMillis = 3_000L,
            concentrationUnit = "PG_PER_ML",
            timeH = listOf(0.0, 1.0),
            concentrations = listOf(100.0, 90.0),
            doseMarkers = listOf(
                HomePkProjectionDoseMarkerRecord(timeH = 0.5, concentration = 95.0, isPlanned = false)
            ),
            latestEstradiolEntry = null,
            chartWindowHours = 168,
            densePolicy = HomePkDenseSamplePolicyRecord.Interval(0.5),
            includesPostDoseOffsets = false,
        )
        val widget = record.toWidgetRecord()
        assertEquals(1_000L, widget.generatedAtEpochMillis)
        assertEquals(500L, widget.windowStartEpochMillis)
        assertEquals(2_000L, widget.windowEndEpochMillis)
        assertEquals(3_000L, widget.pkProjectionExpiresAtEpochMillis)
        assertEquals("PG_PER_ML", widget.concentrationUnit)
        assertEquals(listOf(0.0, 1.0), widget.timeH)
        assertEquals(listOf(100.0, 90.0), widget.concentrations)
        assertEquals(1, widget.doseMarkers.size)
        assertEquals(0.5, widget.doseMarkers[0].timeH, 0.0001)
        assertEquals(95.0, widget.doseMarkers[0].concentration, 0.0001)
        assertEquals(false, widget.doseMarkers[0].isPlanned)
    }
}
