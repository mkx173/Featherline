package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.pk.PkDoseMarker
import com.mkx.hrttracker.model.pk.PkProjectionResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HomePkProjectionJsonCodecTest {
    @Test
    fun encodeDecode_preservesProjectionPayload() {
        val projection = PkProjectionResult(
            generatedAt = Instant.parse("2026-05-05T03:00:00Z"),
            windowStart = Instant.parse("2026-05-02T00:00:00Z"),
            windowEnd = Instant.parse("2026-05-19T00:00:00Z"),
            concentrationUnit = PkConcentrationUnit.PG_PER_ML,
            timeH = listOf(0.0, 1.0, 2.0),
            concentrations = listOf(10.0, 12.5, 11.0),
            doseMarkers = listOf(PkDoseMarker(timeH = 1.0, concentration = 12.5)),
        )

        val decoded = checkNotNull(
            HomePkProjectionJsonCodec.decode(HomePkProjectionJsonCodec.encode(projection))
        )
        val restoredProjection = decoded.toProjection(
            generatedAtEpochMillis = projection.generatedAt.toEpochMilli(),
            windowStartEpochMillis = projection.windowStart.toEpochMilli(),
            windowEndEpochMillis = projection.windowEnd.toEpochMilli(),
        )

        assertEquals(projection, restoredProjection)
    }
}
