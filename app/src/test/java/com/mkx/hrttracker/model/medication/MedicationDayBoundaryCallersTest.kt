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
    private val scheduledFor = LocalDateTime.of(2026, 11, 2, 1, 0)
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
        ),
        medications = listOf(testMedicationGroupMedication(medicine = medicine)),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun adjacentDoseLookupKeepsTheCalendarCadenceUnderFourAm() {
        // The every-other-day phase counts calendar dates, so the switch moves no
        // dose: Nov 2 01:00 (medication day Nov 1) has Oct 31 and Nov 4 as neighbours.
        assertEquals(
            LocalDateTime.of(2026, 10, 31, 1, 0),
            group.previousScheduledForBefore(scheduledFor, zoneId = zone, boundary = fourAm),
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 4, 1, 0),
            group.nextScheduledForAfter(scheduledFor, zoneId = zone, boundary = fourAm),
        )
    }

    @Test
    fun lateDoseStillFulfillsTheSlotUnderFourAm() {
        val entry = testMedicationLogEntry(
            medicine = medicine,
            sourceGroupUuid = group.uuid,
            scheduleTimeUuid = group.schedule.timeSlots.single().uuid,
            scheduledFor = scheduledFor,
            appliedAt = LocalDateTime.of(2026, 11, 2, 20, 0).atZone(zone).toInstant(),
            appliedAtTimeZoneId = zone.id,
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 11, 1),
            groups = listOf(group),
            entries = listOf(entry),
            now = LocalDateTime.of(2026, 11, 2, 20, 0),
            zoneId = zone,
            boundary = fourAm,
        )

        val slot = schedule.scheduledEntries.single()
        assertTrue(slot.isFulfilled)
        assertTrue(slot.outsideScheduleWindowEntryUuids.isEmpty())
        assertFalse(schedule.unplannedEntries.contains(entry))
    }
}
