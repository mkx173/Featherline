package com.mkx.hrttracker.reminder

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import java.util.UUID

class MedicationReminderSnoozeStoreTest {
    private val originalTimeZone: TimeZone = TimeZone.getDefault()
    private val recordsKey = stringSetPreferencesKey("snooze_records")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun a_snooze_stored_as_local_time_keeps_the_instant_it_had_in_the_zone_at_migration() = runTest {
        val pending = reminderSlot("5f2a7c3e-8b1d-4e6a-9c4f-2d7b3e8a1c5f", LocalDateTime.of(2026, 4, 20, 9, 0))
        val past = reminderSlot("6a3b8d4f-9c2e-4f7b-8d5a-3e8c4f9b2d6a", LocalDateTime.of(2026, 4, 20, 8, 0))
        // As builds before absolute deadlines wrote them: slot, local snooze time, count.
        val legacy = preferencesOf(
            recordsKey to setOf(
                legacyValue(pending, "2026-04-20T09:17", 2),
                legacyValue(past, "2026-04-20T08:17", 1),
            )
        )
        val migration = legacySnoozeTimeMigration(
            zoneId = { tokyo },
            now = { ZonedDateTime.of(2026, 4, 20, 9, 5, 0, 0, tokyo).toInstant() },
        )

        val migrated = migration.migrate(legacy)
        // Read after a flight to Los Angeles.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val records = migrated[recordsKey].orEmpty()
            .mapNotNull(::snoozeRecordFromStorageValue)
            .associateBy(MedicationReminderSnoozeRecord::slot)

        assertEquals(
            ZonedDateTime.of(2026, 4, 20, 9, 17, 0, 0, tokyo).toInstant(),
            records[pending]?.snoozeAt,
        )
        assertEquals(2, records[pending]?.snoozeCount)
        assertEquals(false, records[pending]?.fired)
        // Past at migration: nothing tells whether it came, and those builds dropped it at the
        // next pass anyway.
        assertEquals(true, records[past]?.fired)
        assertTrue(migration.shouldMigrate(legacy))
        assertFalse(migration.shouldMigrate(migrated))
    }

    private fun reminderSlot(groupUuid: String, scheduledAt: LocalDateTime) = MedicationReminderSlot(
        groupUuid = UUID.fromString(groupUuid),
        scheduledAt = scheduledAt,
        scheduleTimeUuid = UUID.nameUUIDFromBytes(groupUuid.toByteArray()),
    )

    private fun legacyValue(slot: MedicationReminderSlot, snoozeAt: String, snoozeCount: Int) =
        listOf(slot.toStorageValue(), snoozeAt, snoozeCount.toString()).joinToString(separator = "\u001F")
}
