package com.mkx.hrttracker.widget

import com.mkx.hrttracker.data.repository.HOME_SNAPSHOT_SCHEMA_VERSION
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.settings.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomMedicineWidgetRouteTest {
    @Test
    fun customMedicine_showsCustomLabelInsteadOfInferredRoute() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val group = MedicationGroup(
            uuid = UUID.randomUUID(),
            name = "Progesterone",
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
        val homeSnapshot = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = zoneId.id,
            pkProjection = null,
            widgetPkProjection = null,
            activeGroups = listOf(group),
            archivedGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        )
        val context = RuntimeEnvironment.getApplication().applicationContext

        val row = buildWidgetSnapshotRecord(
            context = context,
            homeSnapshot = homeSnapshot,
            settings = SettingsState(),
            now = now,
            zoneId = zoneId,
        ).doseRows.single()

        assertEquals("Custom", row.routeLabel)
        assertEquals("2 capsules · 10 mg", row.doseText)
    }
}
