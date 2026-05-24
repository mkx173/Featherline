package com.mkx.hrttracker.widget

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.HOME_SNAPSHOT_SCHEMA_VERSION
import com.mkx.hrttracker.data.repository.HomePkDenseSamplePolicyRecord
import com.mkx.hrttracker.data.repository.HomePkProjectionDoseMarkerRecord
import com.mkx.hrttracker.data.repository.HomePkProjectionRecord
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class WidgetSnapshotBuilderTest {
    private val context: Context = mockk(relaxed = true)
    private val zoneId: ZoneId = ZoneId.systemDefault()

    @Test
    fun writesMedicationNamesToWidgetRows() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val group = widgetTestGroup(
            groupName = "Evening group",
            medicationKey = MedicationKey.BICALUTAMIDE,
            since = now.toLocalDate(),
            time = LocalTime.of(20, 0),
        )
        stubMedicationStrings()

        val snapshot = buildWidgetSnapshotRecord(
            context = context,
            homeSnapshot = homeSnapshotRecord(now = now, activeGroups = listOf(group)),
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        )

        val todayRow = snapshot.doseRows.first { row -> row.contextChip == null && !row.isManualRecord }
        assertEquals("Bicalutamide", todayRow.medicationName)
        // Pill at count=1: portion + active joined; count piece is Hidden.
        assertEquals("1 tablet · 2 mg", todayRow.doseText)
        assertEquals(1, snapshot.totalCount)
        assertEquals(0, snapshot.doneCount)
        assertEquals(now.toLocalDate().toEpochDay(), snapshot.anchorDateEpochDay)
    }

    @Test
    fun keepsFuturePlanOutOfEmptyWidgetState() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val group = widgetTestGroup(
            groupName = "Tomorrow group",
            medicationKey = MedicationKey.BICALUTAMIDE,
            since = now.toLocalDate().plusDays(1),
            time = LocalTime.of(8, 0),
        )
        stubMedicationStrings()

        val snapshot = buildWidgetSnapshotRecord(
            context = context,
            homeSnapshot = homeSnapshotRecord(now = now, activeGroups = listOf(group)),
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        )

        assertEquals(0, snapshot.totalCount)
        assertNull(snapshot.doseRows.firstOrNull { row -> row.contextChip == WidgetDoseChip.COMING_UP })
    }

    @Test
    fun preservesHomeProjectionFieldsForWidgetSnapshot() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val projection = HomePkProjectionRecord(
            generatedAtEpochMillis = 1_000L,
            windowStartEpochMillis = 500L,
            windowEndEpochMillis = 2_000L,
            pkProjectionExpiresAtEpochMillis = 3_000L,
            concentrationUnit = PkConcentrationUnit.PG_PER_ML.name,
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

        val widget = buildWidgetSnapshotRecord(
            context = context,
            homeSnapshot = homeSnapshotRecord(now = now, widgetPkProjection = projection),
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        ).pkProjection

        requireNotNull(widget)
        assertEquals(1_000L, widget.generatedAtEpochMillis)
        assertEquals(500L, widget.windowStartEpochMillis)
        assertEquals(2_000L, widget.windowEndEpochMillis)
        assertEquals(3_000L, widget.pkProjectionExpiresAtEpochMillis)
        assertEquals(PkConcentrationUnit.PG_PER_ML.name, widget.concentrationUnit)
        assertEquals(listOf(0.0, 1.0), widget.timeH)
        assertEquals(listOf(100.0, 90.0), widget.concentrations)
        assertEquals(1, widget.doseMarkers.size)
        assertEquals(0.5, widget.doseMarkers[0].timeH, 0.0001)
        assertEquals(95.0, widget.doseMarkers[0].concentration, 0.0001)
        assertEquals(false, widget.doseMarkers[0].isPlanned)
    }

    private fun stubMedicationStrings() {
        every { context.getString(R.string.medication_name_bicalutamide) } returns "Bicalutamide"
        every { context.getString(R.string.medication_application_oral) } returns "Oral"
        every {
            context.getString(R.string.dose_instruction_summary_tablet_fraction, any())
        } returns "1 tablet"
        every { context.getString(R.string.unit_mg) } returns "mg"
        every {
            context.getString(R.string.dose_instruction_summary_active_amount, any(), any())
        } returns "2 mg"
        every { context.getString(R.string.plan_entry_label_manual) } returns "Manual"
    }

    private fun widgetTestGroup(
        groupName: String,
        medicationKey: MedicationKey,
        since: LocalDate,
        time: LocalTime,
    ): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.randomUUID(),
            name = groupName,
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = since,
                weeklyDaysOfWeek = emptySet(),
                times = listOf(time),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    medicine = testMedicine(key = medicationKey),
                    applicationType = MedicationApplicationType.ORAL,
                )
            ),
            createdAt = Instant.parse("2026-05-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-05-01T00:00:00Z"),
        )
    }

    private fun homeSnapshotRecord(
        now: LocalDateTime,
        activeGroups: List<MedicationGroup> = emptyList(),
        pkProjection: HomePkProjectionRecord? = null,
        widgetPkProjection: HomePkProjectionRecord? = null,
    ): HomeSnapshotRecord {
        return HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = zoneId.id,
            pkProjection = pkProjection,
            widgetPkProjection = widgetPkProjection,
            activeGroups = activeGroups,
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        )
    }
}
