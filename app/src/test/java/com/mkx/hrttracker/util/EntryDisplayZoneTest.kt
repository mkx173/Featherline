package com.mkx.hrttracker.util

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class EntryDisplayZoneTest {
    @Test
    fun displayZoneOf_returns_entry_zone_when_valid() {
        val entry = testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 15, 9, 0)),
            appliedAtTimeZoneId = "Asia/Tokyo"
        )
        assertEquals(ZoneId.of("Asia/Tokyo"), displayZoneOf(entry, deviceZone = ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun displayZoneOf_falls_back_to_device_zone_when_entry_zone_invalid() {
        val entry = testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 15, 9, 0)),
            appliedAtTimeZoneId = "Not/A_Zone"
        )
        val deviceZone = ZoneId.of("America/Los_Angeles")
        assertEquals(deviceZone, displayZoneOf(entry, deviceZone = deviceZone))
    }

    @Test
    fun isCrossZone_false_when_same_zone() {
        val entry = testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 15, 9, 0)),
            appliedAtTimeZoneId = "Asia/Tokyo"
        )
        assertEquals(false, isCrossZone(entry, deviceZone = ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun isCrossZone_false_when_aliased_zones_share_offset() {
        // America/Toronto and America/New_York share -05:00 / -04:00 rules.
        val entry = testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 15, 9, 0)),
            appliedAtTimeZoneId = "America/Toronto"
        )
        assertEquals(false, isCrossZone(entry, deviceZone = ZoneId.of("America/New_York")))
    }

    @Test
    fun isCrossZone_true_when_offsets_differ_at_entry_instant() {
        val entry = testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = LocalDateTime.of(2026, 4, 15, 9, 0)
                .atZone(ZoneId.of("Asia/Tokyo"))
                .toInstant(),
            appliedAtTimeZoneId = "Asia/Tokyo"
        )
        assertEquals(true, isCrossZone(entry, deviceZone = ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun isCrossZone_falls_back_to_false_when_entry_zone_invalid() {
        val entry = testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 15, 9, 0)),
            appliedAtTimeZoneId = "Not/A_Zone"
        )
        assertEquals(false, isCrossZone(entry, deviceZone = ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun appliedAtAsLocalDateTime_uses_entry_zone() {
        val instant = java.time.LocalDateTime.of(2026, 4, 15, 9, 0)
            .atZone(ZoneId.of("Asia/Tokyo"))
            .toInstant()
        val entry = testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = instant,
            appliedAtTimeZoneId = "Asia/Tokyo"
        )
        assertEquals(
            java.time.LocalDateTime.of(2026, 4, 15, 9, 0),
            appliedAtAsLocalDateTime(entry, deviceZone = ZoneId.of("America/Los_Angeles"))
        )
    }

    @Test
    fun formatEntryWallTime_renders_time_in_entry_zone() {
        val instant = java.time.LocalDateTime.of(2026, 4, 15, 9, 0)
            .atZone(ZoneId.of("Asia/Tokyo"))
            .toInstant()
        val entry = testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = instant,
            appliedAtTimeZoneId = "Asia/Tokyo"
        )
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        assertEquals("09:00", formatEntryWallTime(entry, formatter, deviceZone = ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun formatEditorZoneLabel_null_when_picker_zone_matches_device() {
        assertNull(
            formatEditorZoneLabel(
                appliedZoneId = ZoneId.of("America/Los_Angeles"),
                appliedAtInstant = Instant.parse("2026-04-15T16:00:00Z"),
                deviceZone = ZoneId.of("America/Los_Angeles"),
            )
        )
    }

    @Test
    fun formatEditorZoneLabel_returns_long_name_with_utc_offset_when_picker_zone_differs() {
        val instant = LocalDateTime.of(2026, 4, 15, 9, 0)
            .atZone(ZoneId.of("Asia/Tokyo"))
            .toInstant()
        val label = formatEditorZoneLabel(
            appliedZoneId = ZoneId.of("Asia/Tokyo"),
            appliedAtInstant = instant,
            deviceZone = ZoneId.of("America/Los_Angeles"),
            locale = java.util.Locale.US,
        )
        // Long name varies subtly across runtimes ("Japan Time" vs "Japan Standard Time"),
        // but the UTC offset suffix is stable, and the label should not be the bare IANA id.
        assertEquals(true, label?.endsWith(" · UTC+9"))
        assertEquals(false, label?.startsWith("Asia/Tokyo"))
    }

    @Test
    fun formatEditorZoneLabel_renders_half_hour_offset() {
        // India is UTC+5:30 year-round.
        val instant = LocalDateTime.of(2026, 4, 15, 9, 0)
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toInstant()
        val label = formatEditorZoneLabel(
            appliedZoneId = ZoneId.of("Asia/Kolkata"),
            appliedAtInstant = instant,
            deviceZone = ZoneId.of("America/Los_Angeles"),
            locale = java.util.Locale.US,
        )
        assertEquals(true, label?.endsWith(" · UTC+5:30"))
    }

    @Test
    fun appliedAtAsLocalDateTime_yields_picker_match_for_cross_zone_entry() {
        // A 09:05 Tokyo dose against a 09:00 Tokyo schedule should be "5 min late"
        // when the device is in Los Angeles. Prior bug: device-zone derivation showed it as ~17h late.
        val tokyo = ZoneId.of("Asia/Tokyo")
        val scheduledFor = LocalDateTime.of(2026, 4, 15, 9, 0)
        val entry = testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = LocalDateTime.of(2026, 4, 15, 9, 5).atZone(tokyo).toInstant(),
            appliedAtTimeZoneId = "Asia/Tokyo",
            scheduledFor = scheduledFor,
        )
        val deviceZone = ZoneId.of("America/Los_Angeles")
        val applied = appliedAtAsLocalDateTime(entry, deviceZone)
        assertEquals(LocalDateTime.of(2026, 4, 15, 9, 5), applied)
        assertEquals(5, java.time.temporal.ChronoUnit.MINUTES.between(scheduledFor, applied))
    }
}
