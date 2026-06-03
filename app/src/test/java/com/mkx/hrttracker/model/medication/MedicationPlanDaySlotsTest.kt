package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class MedicationPlanDaySlotsTest {
    @Test
    fun primitivePlanCalendarDate_usesScheduledForDateForPlanLinkedEntry() {
        val actual = planCalendarDate(
            scheduledForIso = "2026-05-08T23:30",
            appliedAtEpochMillis = Instant.parse("2026-05-08T01:00:00Z").toEpochMilli(),
            appliedAtTimeZoneId = "Asia/Tokyo",
            zoneId = ZoneId.of("America/Los_Angeles"),
        )

        assertEquals(LocalDate.of(2026, 5, 8), actual)
    }

    @Test
    fun primitivePlanCalendarDate_usesStoredZoneForAdHocEntry() {
        val actual = planCalendarDate(
            scheduledForIso = null,
            appliedAtEpochMillis = Instant.parse("2026-05-08T15:30:00Z").toEpochMilli(),
            appliedAtTimeZoneId = "Asia/Tokyo",
            zoneId = ZoneId.of("America/Los_Angeles"),
        )

        assertEquals(LocalDate.of(2026, 5, 9), actual)
    }

    @Test
    fun medicationLogEntryPlanCalendarDate_delegatesToPrimitiveRule() {
        val entry = testMedicationLogEntry(
            sourceGroupUuid = null,
            appliedAt = Instant.parse("2026-05-08T15:30:00Z"),
            appliedAtTimeZoneId = "Asia/Tokyo",
            scheduledFor = null,
        )
        val zoneId = ZoneId.of("America/Los_Angeles")

        assertEquals(
            planCalendarDate(
                scheduledForIso = null,
                appliedAtEpochMillis = entry.appliedAt.toEpochMilli(),
                appliedAtTimeZoneId = entry.appliedAtTimeZoneId,
                zoneId = zoneId,
            ),
            entry.planCalendarDate(zoneId),
        )
    }

    @Test
    fun medicationLogEntryPlanCalendarDate_usesScheduledForWhenPresent() {
        val entry = testMedicationLogEntry(
            sourceGroupUuid = UUID.randomUUID(),
            appliedAt = Instant.parse("2026-05-08T01:00:00Z"),
            appliedAtTimeZoneId = "Asia/Tokyo",
            scheduledFor = LocalDateTime.of(2026, 5, 8, 23, 30),
        )

        assertEquals(
            LocalDate.of(2026, 5, 8),
            entry.planCalendarDate(ZoneId.of("America/Los_Angeles")),
        )
    }
}
