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
    fun bandSegments_splitOnEngineTags_soShadingNeverBridgesAHole() {
        // §7 spans without promoted contribution stay unshaded. The engine
        // tags each run; a one- or two-knot hole leaves no spacing jump a
        // heuristic could see, so the fill must break on the tag alone.
        assertEquals(listOf(0..3, 4..6), bandSegments(listOf(0, 0, 0, 0, 1, 1, 1)))
        assertEquals(listOf(0..0, 1..2, 3..3), bandSegments(listOf(0, 1, 1, 2)))
        assertEquals(listOf(0..10), bandSegments(List(11) { 0 }))
        assertEquals(emptyList<IntRange>(), bandSegments(emptyList()))
    }

    @Test
    fun buildBand_keepsTheEngineSegments_regardlessOfKnotSpacing() {
        val now = LocalDateTime.of(2026, 5, 8, 12, 0)
        val zone = ZoneOffset.UTC
        val windowStart = mainE2ChartWindowStart(now, pastDays = 6)
            .atZone(zone).toInstant().toEpochMilli()
        // Evenly spaced at 6h with a single-knot hole after the second knot:
        // the spacing is only 2x, but the tags say the shading breaks there.
        val knots = listOf(
            knot(windowStart, 100.0, segment = 0),
            knot(windowStart + 6 * 3_600_000L, 100.0, segment = 0),
            knot(windowStart + 18 * 3_600_000L, 100.0, segment = 1),
            knot(windowStart + 24 * 3_600_000L, 100.0, segment = 1),
        )

        val band = checkNotNull(
            buildMainE2CalibrationBand(
                bandKnots = knots,
                now = now,
                zoneId = zone,
                pastDays = 6,
                windowHours = 168,
                displayUnit = BloodUnitKey.PG_ML,
            )
        )

        assertEquals(listOf(0..1, 2..3), band.segments)
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

    @Test
    fun visibleUpper_dropsKnotsOutsideTheWindow_soForecastBufferDoesNotInflateTheAxis() {
        // The snapshot carries ten extra forecast days past the visible range;
        // a peak out there must not stretch the y-axis of the seven-day chart.
        val band = MainE2CalibrationBand(
            xHours = listOf(-1.0, 0.0, 100.0, 168.0, 200.0),
            p025 = List(5) { 0f },
            p158655254 = List(5) { 0f },
            p841344746 = List(5) { 0f },
            p975 = listOf(900f, 500f, 600f, 550f, 750f),
        )

        assertEquals(listOf(500f, 600f, 550f), band.visibleUpper(windowHours = 168))
    }

    private fun MainE2CalibrationBand.p50Sanity(index: Int): Float {
        // The knot below fills every quantile with the same value, so any
        // quantile works as the conversion probe.
        return p158655254[index]
    }

    private fun knot(epochMillis: Long, valuePgml: Double, segment: Int = 0): PkPredictiveBandKnot {
        return PkPredictiveBandKnot(
            epochMillis = epochMillis,
            p025Pgml = valuePgml,
            p158655254Pgml = valuePgml,
            p50Pgml = valuePgml,
            p841344746Pgml = valuePgml,
            p975Pgml = valuePgml,
            segment = segment,
        )
    }
}
