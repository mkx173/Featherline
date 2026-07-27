package com.mkx.hrttracker.wear

import com.mkx.hrttracker.widget.WidgetDoseRow
import com.mkx.hrttracker.widget.WidgetDoseStatus
import com.mkx.hrttracker.widget.WidgetPkProjectionRecord
import com.mkx.hrttracker.widget.WidgetSnapshotRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WearSnapshotSinkTest {
    @Test
    fun toWearDoseSnapshot_excludesManualRows() {
        val snapshot = snapshot(
            rows = listOf(
                row(groupUuid = "group", isManual = false),
                row(groupUuid = null, isManual = true),
            )
        )

        assertEquals(1, snapshot.toWearDoseSnapshot().rows.size)
    }

    @Test
    fun toWearDoseSnapshot_redactsMedicationDetailsWhenSettingIsEnabled() {
        val snapshot = snapshot(
            hideMedicationDetails = true,
            rows = listOf(row(groupUuid = "group", isManual = false)),
        )

        val row = snapshot.toWearDoseSnapshot().rows.single()

        assertEquals("Evening", row.groupName)
        assertTrue(row.medicationName.isEmpty())
        assertTrue(row.routeLabel.isEmpty())
        assertTrue(row.doseText.isEmpty())
    }

    @Test
    fun toWearDoseSnapshot_samplesThePrevious48HoursOfEstradiol() {
        val windowStart = 1_700_000_000_000L
        val generatedAt = windowStart + 72 * 60 * 60 * 1_000L
        val snapshot = snapshot(
            rows = listOf(row(groupUuid = "group", isManual = false)),
            pkProjection = WidgetPkProjectionRecord(
                generatedAtEpochMillis = generatedAt,
                windowStartEpochMillis = windowStart,
                windowEndEpochMillis = generatedAt + 24 * 60 * 60 * 1_000L,
                pkProjectionExpiresAtEpochMillis = generatedAt + 24 * 60 * 60 * 1_000L,
                concentrationUnit = "PG_PER_ML",
                timeH = listOf(0.0, 72.0, 96.0),
                concentrations = listOf(0.0, 72.0, 96.0),
                doseMarkers = emptyList(),
            ),
        )

        val estradiol = snapshot.toWearDoseSnapshot(generatedAt).estradiol

        assertNotNull(estradiol)
        assertEquals("72", estradiol?.currentValueText)
        assertEquals("pg/mL", estradiol?.unitLabel)
        assertEquals(25, estradiol?.samples?.size)
        assertEquals(24.0, estradiol?.samples?.first() ?: -1.0, 0.0001)
        assertEquals(72.0, estradiol?.samples?.last() ?: -1.0, 0.0001)
        assertEquals(120, estradiol?.sampleIntervalMinutes)
    }

    private fun snapshot(
        hideMedicationDetails: Boolean = false,
        rows: List<WidgetDoseRow>,
        pkProjection: WidgetPkProjectionRecord? = null,
    ): WidgetSnapshotRecord = WidgetSnapshotRecord(
        schemaVersion = 1,
        zoneId = "UTC",
        anchorDateEpochDay = 1L,
        doneCount = 0,
        totalCount = 1,
        manualCount = 0,
        hasActiveGroups = true,
        hideMedicationDetails = hideMedicationDetails,
        adaptiveColorEnabled = true,
        e2DisplayUnit = "pg_ml",
        appLanguageTag = "en",
        doseRows = rows,
        pkProjection = pkProjection,
    )

    private fun row(
        groupUuid: String?,
        isManual: Boolean,
    ): WidgetDoseRow = WidgetDoseRow(
        medicationName = "Estradiol",
        groupName = "Evening",
        colorKey = null,
        routeLabel = "Oral",
        doseText = "2 mg",
        status = WidgetDoseStatus.DUE_SOON,
        scheduledAt = LocalDateTime.of(2026, 7, 26, 21, 0),
        trailingText = "21:00",
        isManualRecord = isManual,
        contextChip = null,
        groupUuid = groupUuid,
        scheduleTimeUuid = "slot",
    )
}
