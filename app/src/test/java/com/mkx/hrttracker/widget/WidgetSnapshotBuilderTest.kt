package com.mkx.hrttracker.widget

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.HOME_SNAPSHOT_SCHEMA_VERSION
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.pk.PkDoseMarker
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetSnapshotBuilderTest {
    private val context: Context = mockk(relaxed = true)
    private val realContext: Context
        get() = RuntimeEnvironment.getApplication().applicationContext
    private val zoneId: ZoneId = ZoneId.systemDefault()

    @Test
    fun fourAmKeepsMissedDawnInLastNightWithAPlainTimeMarkedPastMidnight() {
        val now = LocalDateTime.of(2026, 10, 2, 4, 30)
        val group = widgetTestGroup(
            groupName = "Night", medicationKey = MedicationKey.ESTRADIOL,
            since = now.toLocalDate().minusDays(2), time = LocalTime.of(1, 0),
        )
        val configuredGroup = group.copy(schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY, interval = 1,
            since = now.toLocalDate().minusDays(2), weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(1, 0), LocalTime.of(20, 0)),
        ))
        val entry = testMedicationLogEntry(
            medicine = configuredGroup.medications.single().medicine,
            sourceGroupUuid = null, appliedAt = now.atZone(zoneId).toInstant(), appliedAtTimeZoneId = zoneId.id,
        )
        val snapshot = buildWidgetSnapshotRecord(
            context = realContext,
            homeSnapshot = homeSnapshotRecord(now = now, activeGroups = listOf(configuredGroup)).copy(scheduleEntries = listOf(entry)),
            widgetEstimate = null,
            settings = SettingsState(medicationDayEndsAtFourAm = true), now = now, zoneId = zoneId,
        )
        assertEquals(now.toLocalDate().toEpochDay(), snapshot.anchorDateEpochDay)
        assertEquals(2, snapshot.totalCount)
        assertEquals(1, snapshot.manualCount)
        val missedDawn = snapshot.doseRows.first { row ->
            row.contextChip == WidgetDoseChip.LAST_NIGHT &&
                row.scheduledAt == now.toLocalDate().atTime(1, 0)
        }
        assertEquals("01:00", missedDawn.trailingText)
        assertTrue(missedDawn.isPastMidnight)
        assertFalse(snapshot.doseRows.first { it.scheduledAt.hour == 20 }.isPastMidnight)
    }

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
            widgetEstimate = null,
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        )

        val todayRow =
            snapshot.doseRows.first { row -> row.contextChip == null && !row.isManualRecord }
        assertEquals("Bicalutamide", todayRow.medicationName)
        // Pill at count=1 with TabletFraction(1, 1): "1 tablet" portion suppressed; only active mg shown.
        assertEquals("2 mg", todayRow.doseText)
        assertEquals(1, snapshot.totalCount)
        assertEquals(0, snapshot.doneCount)
        assertEquals(now.toLocalDate().toEpochDay(), snapshot.anchorDateEpochDay)
    }

    @Test
    fun capturesAppLanguageTagFromSettings() {
        // The widget chrome (section headers, "done"/E2 labels) is rendered live against
        // this tag so it matches the medication/dose strings baked into the snapshot,
        // even when the widget re-renders in a process that lost the per-app locale.
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        stubMedicationStrings()

        val snapshot = buildWidgetSnapshotRecord(
            context = context,
            homeSnapshot = homeSnapshotRecord(now = now),
            widgetEstimate = null,
            settings = SettingsState(appLanguageOption = AppLanguageOption.SIMPLIFIED_CHINESE),
            now = now,
            zoneId = zoneId,
        )

        assertEquals("zh-Hans", snapshot.appLanguageTag)
    }

    @Test
    fun writesAggregateDoseTextToWidgetRows() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val group = MedicationGroup(
            uuid = UUID.randomUUID(),
            name = "Evening group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = now.toLocalDate(),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    medicine = testCustomMedicine(
                        medicationName = "Progesterone",
                        preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 5.0),
                    ),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.WholeUnit,
                    count = 2,
                )
            ),
            createdAt = Instant.parse("2026-05-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-05-01T00:00:00Z"),
        )

        val snapshot = buildWidgetSnapshotRecord(
            context = realContext,
            homeSnapshot = homeSnapshotRecord(now = now, activeGroups = listOf(group)),
            widgetEstimate = null,
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        )

        val row = snapshot.doseRows.first { row -> row.contextChip == null && !row.isManualRecord }
        assertEquals("2 capsules · 10 mg", row.doseText)
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
            widgetEstimate = null,
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        )

        assertEquals(0, snapshot.totalCount)
        assertNull(snapshot.doseRows.firstOrNull { row -> row.contextChip == WidgetDoseChip.COMING_UP })
    }

    @Test
    fun carriesTheWidgetEstimate_stampedWithTheBuildTime_andExpiringAtItsWindowEnd() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val estimate = PkProjectionResult(
            windowStart = Instant.ofEpochMilli(500L),
            windowEnd = Instant.ofEpochMilli(2_000L),
            concentrationUnit = PkConcentrationUnit.PG_PER_ML,
            timeH = listOf(0.0, 1.0),
            concentrations = listOf(100.0, 90.0),
            doseMarkers = listOf(PkDoseMarker(timeH = 0.5, concentration = 95.0, isPlanned = false)),
        )

        val widget = buildWidgetSnapshotRecord(
            context = context,
            homeSnapshot = homeSnapshotRecord(now = now),
            widgetEstimate = estimate,
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        ).pkProjection

        requireNotNull(widget)
        assertEquals(now.atZone(zoneId).toInstant().toEpochMilli(), widget.generatedAtEpochMillis)
        assertEquals(500L, widget.windowStartEpochMillis)
        assertEquals(2_000L, widget.windowEndEpochMillis)
        assertEquals(2_000L, widget.pkProjectionExpiresAtEpochMillis)
        assertEquals(PkConcentrationUnit.PG_PER_ML.name, widget.concentrationUnit)
        assertEquals(listOf(0.0, 1.0), widget.timeH)
        assertEquals(listOf(100.0, 90.0), widget.concentrations)
        assertEquals(1, widget.doseMarkers.size)
        assertEquals(0.5, widget.doseMarkers[0].timeH, 0.0001)
        assertEquals(95.0, widget.doseMarkers[0].concentration, 0.0001)
        assertEquals(false, widget.doseMarkers[0].isPlanned)
        // Without an estimate the widget shows none, and its rows still come.
        assertNull(
            buildWidgetSnapshotRecord(
                context = context,
                homeSnapshot = homeSnapshotRecord(now = now),
                widgetEstimate = null,
                settings = SettingsState(),
                now = now,
                zoneId = zoneId,
            ).pkProjection
        )
    }

    @Test
    fun countsArchivedLoggedDoseWhenSettingOn() {
        val now = LocalDateTime.of(2026, 5, 6, 12, 0)
        stubMedicationStrings()
        val archived = widgetTestGroup(
            groupName = "Archived group",
            medicationKey = MedicationKey.BICALUTAMIDE,
            since = now.toLocalDate().minusDays(10),
            time = LocalTime.of(8, 0),
        ).copy(
            archivedAt = now.atZone(zoneId).toInstant(),
            archivedAtLocal = now,
        )
        val archivedMedication = archived.medications.single()
        val logged = testMedicationLogEntry(
            sourceGroupUuid = archived.uuid,
            scheduledFor = now.toLocalDate().atTime(8, 0),
            appliedAt = now.toLocalDate().atTime(8, 5).atZone(zoneId).toInstant(),
            medicine = archivedMedication.medicine,
            applicationType = archivedMedication.applicationType,
            doseInstruction = archivedMedication.doseInstruction,
        )

        val snapshot = buildWidgetSnapshotRecord(
            context = context,
            homeSnapshot = homeSnapshotRecord(now = now, archivedGroups = listOf(archived))
                .copy(scheduleEntries = listOf(logged)),
            widgetEstimate = null,
            settings = SettingsState(showArchivedGroupRecords = true),
            now = now,
            zoneId = zoneId,
        )

        val row = snapshot.doseRows.first { !it.isManualRecord }
        assertEquals("Archived group", row.groupName)
        assertEquals(archived.colorKey, row.colorKey)
        assertTrue(row.isFromArchivedGroup)
        assertEquals(1, snapshot.totalCount)
        assertEquals(1, snapshot.doneCount)
    }

    @Test
    fun dropsArchivedDoseWhenSettingOff() {
        val now = LocalDateTime.of(2026, 5, 6, 12, 0)
        stubMedicationStrings()
        val archived = widgetTestGroup(
            groupName = "Archived group",
            medicationKey = MedicationKey.BICALUTAMIDE,
            since = now.toLocalDate().minusDays(10),
            time = LocalTime.of(8, 0),
        ).copy(
            archivedAt = now.atZone(zoneId).toInstant(),
            archivedAtLocal = now,
        )
        val archivedMedication = archived.medications.single()
        val logged = testMedicationLogEntry(
            sourceGroupUuid = archived.uuid,
            scheduledFor = now.toLocalDate().atTime(8, 0),
            appliedAt = now.toLocalDate().atTime(8, 5).atZone(zoneId).toInstant(),
            medicine = archivedMedication.medicine,
            applicationType = archivedMedication.applicationType,
            doseInstruction = archivedMedication.doseInstruction,
        )

        val snapshot = buildWidgetSnapshotRecord(
            context = context,
            homeSnapshot = homeSnapshotRecord(now = now, archivedGroups = listOf(archived))
                .copy(scheduleEntries = listOf(logged)),
            widgetEstimate = null,
            settings = SettingsState(showArchivedGroupRecords = false),
            now = now,
            zoneId = zoneId,
        )

        assertEquals(0, snapshot.totalCount)
        assertEquals(0, snapshot.doseRows.size)
    }

    @Test
    fun showsManualLogWhenNoActiveGroups() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        stubMedicationStrings()
        val manual = testMedicationLogEntry(
            sourceGroupUuid = null,
            appliedAt = now.atZone(zoneId).toInstant(),
        )

        val snapshot = buildWidgetSnapshotRecord(
            context = context,
            homeSnapshot = homeSnapshotRecord(now = now, activeGroups = emptyList())
                .copy(scheduleEntries = listOf(manual)),
            widgetEstimate = null,
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        )

        assertEquals(false, snapshot.hasActiveGroups)
        assertEquals(1, snapshot.doseRows.size)
        assertEquals(false, isEmptySetup(snapshot))
    }

    @Test
    fun comingUpIncludesManualMidnightRowAtMidnightBoundary() {
        val now = LocalDateTime.of(2026, 10, 1, 20, 0)
        val manualMedicine = testCustomMedicine(medicationName = "Midnight manual")
        val manual = testMedicationLogEntry(
            medicine = manualMedicine,
            sourceGroupUuid = null,
            appliedAt = now.toLocalDate().plusDays(1).atTime(0, 30).atZone(zoneId).toInstant(),
            appliedAtTimeZoneId = zoneId.id,
        )

        val snapshot = buildWidgetSnapshotRecord(
            context = realContext,
            homeSnapshot = homeSnapshotRecord(now = now).copy(scheduleEntries = listOf(manual)),
            widgetEstimate = null,
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        )

        val row = snapshot.doseRows.single { row -> row.contextChip == WidgetDoseChip.COMING_UP }
        assertTrue(row.isManualRecord)
        assertEquals("Midnight manual", row.medicationName)
    }

    @Test
    fun comingUpIncludesArchivedGroupMidnightRowWhenSettingOn() {
        val now = LocalDateTime.of(2026, 10, 1, 20, 0)
        val archived = widgetTestGroup(
            groupName = "Archived night",
            medicationKey = MedicationKey.BICALUTAMIDE,
            since = now.toLocalDate().minusDays(10),
            time = LocalTime.of(0, 30),
        ).copy(
            archivedAt = now.minusHours(1).atZone(zoneId).toInstant(),
            archivedAtLocal = now.minusHours(1),
        )
        val logged = testMedicationLogEntry(
            medicine = archived.medications.single().medicine,
            sourceGroupUuid = archived.uuid,
            scheduleTimeUuid = archived.schedule.timeSlots.single().uuid,
            scheduledFor = now.toLocalDate().plusDays(1).atTime(0, 30),
            appliedAt = now.toLocalDate().plusDays(1).atTime(0, 35).atZone(zoneId).toInstant(),
            appliedAtTimeZoneId = zoneId.id,
        )

        val snapshot = buildWidgetSnapshotRecord(
            context = realContext,
            homeSnapshot = homeSnapshotRecord(now = now, archivedGroups = listOf(archived))
                .copy(scheduleEntries = listOf(logged)),
            widgetEstimate = null,
            settings = SettingsState(showArchivedGroupRecords = true),
            now = now,
            zoneId = zoneId,
        )

        val row = snapshot.doseRows.single { row -> row.contextChip == WidgetDoseChip.COMING_UP }
        assertEquals("Archived night", row.groupName)
        assertTrue(row.isFromArchivedGroup)
    }

    private fun stubMedicationStrings() {
        every { context.getString(R.string.medication_name_bicalutamide) } returns "Bicalutamide"
        every { context.getString(R.string.medication_application_oral) } returns "Oral"
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
        archivedGroups: List<MedicationGroup> = emptyList(),
    ): HomeSnapshotRecord {
        return HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = zoneId.id,
            activeGroups = activeGroups,
            archivedGroups = archivedGroups,
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        )
    }
}
