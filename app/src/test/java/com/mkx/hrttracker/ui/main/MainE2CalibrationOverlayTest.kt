package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.PkPredictiveBandKnot
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainE2CalibrationOverlayTest {

    @Test
    fun bandGapSegments_splitOnSpacingJumps_soShadingNeverBridgesAGap() {
        // Hourly knots with one 12-hour hole: §7 spans without promoted
        // contribution stay unshaded, so the fill must break at the hole.
        val xHours = listOf(0.0, 1.0, 2.0, 3.0, 15.0, 16.0, 17.0)
        assertEquals(listOf(0..3, 4..6), bandGapSegments(xHours))
    }

    @Test
    fun bandGapSegments_uniformSpacing_isOneSegment() {
        val xHours = (0..10).map { it.toDouble() }
        assertEquals(listOf(0..10), bandGapSegments(xHours))
    }

    @Test
    fun buildBand_mapsKnotsIntoWindowHours_andDisplayUnit() {
        val now = LocalDateTime.of(2026, 5, 8, 12, 0)
        val zone = ZoneOffset.UTC
        // Window start is midnight `pastDays` back; a knot at exactly that
        // instant maps to x = 0, one day later to x = 24.
        val windowStart = mainE2ChartWindowStart(now, pastDays = 6)
            .atZone(zone).toInstant().toEpochMilli()
        val knots = listOf(
            knot(windowStart, 100.0),
            knot(windowStart + 24 * 3_600_000L, 200.0),
        )

        val band = checkNotNull(
            buildMainE2CalibrationBand(
                bandKnots = knots,
                now = now,
                zoneId = zone,
                pastDays = 6,
                windowHours = 168,
                displayUnit = BloodUnitKey.PMOL_L,
            )
        )

        assertEquals(listOf(0.0, 24.0), band.xHours)
        // pg/mL → pmol/L uses the app's own conversion, keeping the band in
        // the same selected unit as the line and axes (§7).
        assertEquals(100.0 * 3.671, band.p50Sanity(0).toDouble(), 0.5)
    }

    @Test
    fun buildBand_withNoKnotInsideTheVisibleWindow_returnsNull() {
        // Phase-3 #9: a READY band whose every knot misses the visible window
        // must not surface a legend row for an invisible band.
        val now = LocalDateTime.of(2026, 5, 8, 12, 0)
        val zone = ZoneOffset.UTC
        val windowStart = mainE2ChartWindowStart(now, pastDays = 6)
            .atZone(zone).toInstant().toEpochMilli()
        val staleKnots = listOf(
            knot(windowStart - 60L * 24 * 3_600_000L, 100.0),
            knot(windowStart - 59L * 24 * 3_600_000L, 200.0),
        )

        assertNull(
            buildMainE2CalibrationBand(
                bandKnots = staleKnots,
                now = now,
                zoneId = zone,
                pastDays = 6,
                windowHours = 168,
                displayUnit = BloodUnitKey.PMOL_L,
            )
        )
    }

    private fun MainE2CalibrationBand.p50Sanity(index: Int): Float {
        // The knot below fills every quantile with the same value, so any
        // quantile works as the conversion probe.
        return p158655254[index]
    }

    private fun knot(epochMillis: Long, valuePgml: Double): PkPredictiveBandKnot {
        return checkNotNull(
            PkPredictiveBandKnot.create(
                epochMillis = epochMillis,
                p025Pgml = valuePgml,
                p158655254Pgml = valuePgml,
                p50Pgml = valuePgml,
                p841344746Pgml = valuePgml,
                p975Pgml = valuePgml,
            )
        )
    }
}
