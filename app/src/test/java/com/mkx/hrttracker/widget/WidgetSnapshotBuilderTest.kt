package com.mkx.hrttracker.widget

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.HOME_SNAPSHOT_SCHEMA_VERSION
import com.mkx.hrttracker.data.repository.HomePkDenseSamplePolicyRecord
import com.mkx.hrttracker.data.repository.HomePkProjectionDoseMarkerRecord
import com.mkx.hrttracker.data.repository.HomePkProjectionRecord
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
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
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
                HomePkProjectionDoseMarkerRecord(
                    timeH = 0.5,
                    concentration = 95.0,
                    isPlanned = false
                )
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
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        )

        assertEquals(false, snapshot.hasActiveGroups)
        assertEquals(1, snapshot.doseRows.size)
        assertEquals(false, isEmptySetup(snapshot))
    }

    @Test
    fun marksImportedManualLogRows() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        stubMedicationStrings()
        val imported = testMedicationLogEntry(
            sourceGroupUuid = null,
            appliedAt = now.atZone(zoneId).toInstant(),
        ).copy(
            importSourceApp = "transmtf",
            importExternalId = "dose-1",
        )

        val snapshot = buildWidgetSnapshotRecord(
            context = context,
            homeSnapshot = homeSnapshotRecord(now = now, activeGroups = emptyList())
                .copy(scheduleEntries = listOf(imported)),
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        )

        assertEquals(true, snapshot.doseRows.single().isImportedRecord)
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
            archivedGroups = archivedGroups,
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        )
    }
}
