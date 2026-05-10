package com.mkx.hrttracker.util

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class EntryDisplayZoneTest {
    @Test
    fun displayZoneOf_returns_entry_zone_when_valid() {
        val entry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 15, 9, 0)),
            appliedAtTimeZoneId = "Asia/Tokyo"
        )
        assertEquals(ZoneId.of("Asia/Tokyo"), displayZoneOf(entry, deviceZone = ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun displayZoneOf_falls_back_to_device_zone_when_entry_zone_invalid() {
        val entry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
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
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
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
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 15, 9, 0)),
            appliedAtTimeZoneId = "America/Toronto"
        )
        assertEquals(false, isCrossZone(entry, deviceZone = ZoneId.of("America/New_York")))
    }

    @Test
    fun isCrossZone_true_when_offsets_differ_at_entry_instant() {
        val entry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
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
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
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
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
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
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            sourceGroupUuid = null,
            appliedAt = instant,
            appliedAtTimeZoneId = "Asia/Tokyo"
        )
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        assertEquals("09:00", formatEntryWallTime(entry, formatter, deviceZone = ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun formatZoneLabel_null_when_not_cross_zone() {
        val entry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 15, 9, 0)),
            appliedAtTimeZoneId = "America/Los_Angeles"
        )
        assertNull(formatZoneLabel(entry, deviceZone = ZoneId.of("America/Los_Angeles"), locale = java.util.Locale.US))
    }

    @Test
    fun formatZoneLabel_renders_iana_short_name_and_offset_for_cross_zone() {
        val instant = java.time.LocalDateTime.of(2026, 4, 15, 9, 0)
            .atZone(ZoneId.of("Asia/Tokyo"))
            .toInstant()
        val entry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            sourceGroupUuid = null,
            appliedAt = instant,
            appliedAtTimeZoneId = "Asia/Tokyo"
        )
        assertEquals(
            "Asia/Tokyo · JST · +09:00",
            formatZoneLabel(entry, deviceZone = ZoneId.of("America/Los_Angeles"), locale = java.util.Locale.US)
        )
    }
}
