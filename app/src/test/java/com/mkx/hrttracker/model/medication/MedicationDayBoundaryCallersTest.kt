package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.util.MedicationDayBoundary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The active call paths that used to omit the medication-day boundary and so
 * silently fell back to calendar-day cadence.
 */
class MedicationDayBoundaryCallersTest {
    private val zone = ZoneId.of("UTC")
    private val since = LocalDate.of(2026, 10, 29)
    private val scheduledFor = LocalDateTime.of(2026, 11, 1, 1, 0)
    private val fourAm = MedicationDayBoundary.FourAm
    private val medicine = testMedicine()
    private val group = MedicationGroup(
        uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        name = "Every other day",
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 2,
            since = since,
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(1, 0)),
            dayBoundary = fourAm,
        ),
        medications = listOf(testMedicationGroupMedication(medicine = medicine)),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun adjacentDoseLookupUsesMedicationDayCadence() {
        // Under FourAm the 01:00 doses are owned by Oct 29 / Oct 31 / Nov 2, so
        // the Nov 1 01:00 dose has Oct 30 01:00 and Nov 3 01:00 as neighbours.
        // Calendar-day cadence would report Oct 31 and Nov 2 instead.
        assertEquals(
            LocalDateTime.of(2026, 10, 30, 1, 0),
            group.previousScheduledForBefore(scheduledFor, zoneId = zone),
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 3, 1, 0),
            group.nextScheduledForAfter(scheduledFor, zoneId = zone),
        )
    }

    @Test
    fun lateDoseStillFulfillsTheSlotUnderFourAm() {
        val entry = testMedicationLogEntry(
            medicine = medicine,
            sourceGroupUuid = group.uuid,
            scheduleTimeUuid = group.schedule.timeSlots.single().uuid,
            scheduledFor = scheduledFor,
            appliedAt = LocalDateTime.of(2026, 11, 1, 20, 0).atZone(zone).toInstant(),
            appliedAtTimeZoneId = zone.id,
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 10, 31),
            groups = listOf(group),
            entries = listOf(entry),
            now = LocalDateTime.of(2026, 11, 1, 20, 0),
            zoneId = zone,
        )

        val slot = schedule.scheduledEntries.single()
        assertTrue(slot.isFulfilled)
        assertTrue(slot.outsideScheduleWindowEntryUuids.isEmpty())
        assertFalse(schedule.unplannedEntries.contains(entry))
    }
}
