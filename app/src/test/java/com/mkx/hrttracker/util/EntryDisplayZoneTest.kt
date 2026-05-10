package com.mkx.hrttracker.util

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import org.junit.Assert.assertEquals
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
}
