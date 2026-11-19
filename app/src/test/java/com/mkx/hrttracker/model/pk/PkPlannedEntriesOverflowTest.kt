package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * A dose ahead of now that its records cover adds no planned dose to the estimate, also when a
 * hand-edited backup holds records whose counts add up past Int.MAX_VALUE.
 */
class PkPlannedEntriesOverflowTest {
    private fun planned(vararg counts: Int): List<Pair<LocalDateTime?, Int>> {
        val medicine = testMedicine()
        val group = MedicationGroup(
            uuid = UUID.randomUUID(),
            name = "Daily",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 5, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val slot = LocalDateTime.of(2026, 5, 7, 8, 0)
        val real = counts.map { count ->
            testMedicationLogEntry(
                medicine = medicine,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = group.uuid,
                appliedAt = testInstant(slot.minusDays(1)),
                scheduledFor = slot,
                scheduleTimeUuid = group.schedule.timeSlots.single().uuid,
                count = count,
            )
        }
        return buildEstradiolPkSimulationEntries(
            realEntries = real,
            activeGroups = listOf(group),
            now = LocalDateTime.of(2026, 5, 6, 10, 0),
            horizon = LocalDateTime.of(2026, 5, 8, 0, 0),
            zoneId = ZoneId.systemDefault(),
        ).planned.map { it.scheduledFor to it.count }
    }

    @Test
    fun oneRecord_suppressesThePlannedDose() {
        assertEquals(emptyList<Pair<LocalDateTime?, Int>>(), planned(1))
    }

    @Test
    fun twoRecordsOfIntMax_onADoseAhead_suppressThePlannedDose() {
        assertEquals(emptyList<Pair<LocalDateTime?, Int>>(), planned(Int.MAX_VALUE, Int.MAX_VALUE))
    }
}
