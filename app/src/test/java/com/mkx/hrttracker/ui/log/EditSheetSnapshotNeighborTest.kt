package com.mkx.hrttracker.ui.log

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.isEntryWithinScheduleFulfillmentWindow
import com.mkx.hrttracker.model.medication.nextScheduledForAfter
import com.mkx.hrttracker.model.medication.previousScheduledForBefore
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.UUID

/**
 * The edit sheet opened from Home carries Home's neighbours, picked in the device zone. Once the
 * plan has loaded, buildEditingUiState takes the plan's neighbours in the record's zone, also when
 * the plan has none there.
 */
class EditSheetSnapshotNeighborTest {
    private val newYork = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val day = LocalDate.of(2026, 3, 8)
    private val medicine = testMedicine()

    private fun plan(since: LocalDate) = MedicationGroup(
        uuid = UUID.randomUUID(),
        name = "Night",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = since,
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(2, 10), LocalTime.of(3, 5)),
        ),
        medications = listOf(testMedicationGroupMedication(medicine = medicine)),
    )

    private fun recordAt0302(plan: MedicationGroup) = testMedicationLogEntry(
        medicine = medicine,
        sourceGroupUuid = plan.uuid,
        scheduleTimeUuid = plan.schedule.timeSlots.single { it.time == LocalTime.of(3, 5) }.uuid,
        appliedAt = day.atTime(3, 2).atZone(newYork).toInstant(),
        appliedAtTimeZoneId = newYork.id,
        scheduledFor = day.atTime(3, 5),
    )

    /** The sheet as Home opens it on a phone now on Tokyo time, once the plan has loaded. */
    private fun warnsOnSave(plan: MedicationGroup): Boolean {
        val record = recordAt0302(plan)
        val slot = day.atTime(3, 5)
        val deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(tokyo))
        try {
            val sheet = requireNotNull(
                buildEditingUiState(
                    entries = listOf(record),
                    sourceGroup = plan,
                    sourceGroupName = plan.name,
                    // What Home's row holds: its neighbours in the device zone.
                    sourceGroupPreviousScheduledFor = plan.previousScheduledForBefore(slot, zoneId = tokyo),
                    sourceGroupNextScheduledFor = plan.nextScheduledForAfter(slot, zoneId = tokyo),
                )
            )
            return sheet.shouldWarnScheduleWillNotBeFulfilled()
        } finally {
            TimeZone.setDefault(deviceZone)
        }
    }

    // The plan's first day is the spring-forward day: in New York 03:05 has no previous dose
    // (02:10 lands at 03:10 and is silenced), in Tokyo it has 02:10.
    @Test
    fun onThePlansFirstDay_theSheetAgreesWithHome() {
        val plan = plan(since = day)
        val slot = day.atTime(3, 5)
        assertEquals(null, plan.previousScheduledForBefore(slot, zoneId = newYork))
        assertEquals(day.atTime(2, 10), plan.previousScheduledForBefore(slot, zoneId = tokyo))

        // Home, Plan and History: on time (the early side falls back to the next dose, 23 h away).
        assertEquals(true, isEntryWithinScheduleFulfillmentWindow(plan, recordAt0302(plan), tokyo))
        // The sheet: Home's 02:10 does not stand in for the missing neighbour.
        assertEquals(false, warnsOnSave(plan))
    }

    // Any later day: the live plan has a previous dose in the record's zone (the day before's
    // 03:05).
    @Test
    fun onAnyLaterDay_theSheetAgreesWithHome() {
        val plan = plan(since = day.minusDays(1))
        assertEquals(true, isEntryWithinScheduleFulfillmentWindow(plan, recordAt0302(plan), tokyo))
        assertEquals(false, warnsOnSave(plan))
    }

    // The next side: a plan archived after this dose has no next dose, and a Home row built
    // before the archive still names tomorrow's. The loaded plan's null wins.
    @Test
    fun aLoadedPlanWithNoNextDose_dropsTheSnapshotsNextDose() {
        val slot = day.atTime(3, 5)
        val plan = plan(since = day.minusDays(1)).copy(
            archivedAt = slot.plusHours(1).atZone(newYork).toInstant(),
            archivedAtLocal = slot.plusHours(1),
        )
        assertEquals(null, plan.nextScheduledForAfter(slot, zoneId = newYork))

        val sheet = requireNotNull(
            buildEditingUiState(
                entries = listOf(recordAt0302(plan)),
                sourceGroup = plan,
                sourceGroupName = plan.name,
                sourceGroupNextScheduledFor = slot.plusDays(1).minusMinutes(55),
            )
        )
        assertEquals(null, sheet.sourceGroupNextScheduledFor)
    }
}
