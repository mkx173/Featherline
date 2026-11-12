package com.mkx.hrttracker.reminder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

/** The schedule store and the v1.4.2 snooze cleanup, on real Preferences DataStores. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderScheduleStoreTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()
    private val plan = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001")

    private fun dataStore(name: String) = PreferenceDataStoreFactory.create(
        scope = TestScope(dispatcher),
        produceFile = { tmpFolder.root.resolve("$name.preferences_pb") },
    )

    @Test
    fun skipThrough_neverMovesBack() = runTest(dispatcher) {
        val store = ReminderScheduleStore(dataStore("schedule"))
        val evening = LocalDateTime.of(2026, 4, 20, 20, 0)

        store.skipThrough(plan, evening)
        // Skip on an older reminder still on screen.
        store.skipThrough(plan, evening.minusHours(12))

        assertEquals(mapOf(plan to evening), store.getSkippedThrough())
        store.skipThrough(plan, evening.plusDays(1))
        assertEquals(mapOf(plan to evening.plusDays(1)), store.getSkippedThrough())
        store.clearSkippedThrough()
        assertTrue(store.getSkippedThrough().isEmpty())
    }

    @Test
    fun armedAlerts_roundTripAsInstants_andAV142KeyReadsWithNoTime() = runTest(dispatcher) {
        val raw = dataStore("schedule")
        val store = ReminderScheduleStore(raw)
        val older = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002")
        val alertAt = Instant.parse("2026-04-20T09:15:00Z")

        store.addScheduledReminder(plan, alertAt)
        // v1.4.2 stored a plan's uuid alone.
        raw.edit { preferences ->
            preferences[stringSetPreferencesKey("scheduled_group_uuids")] =
                preferences[stringSetPreferencesKey("scheduled_group_uuids")].orEmpty() + older.toString()
        }

        assertEquals(mapOf(plan to alertAt, older to null), store.getScheduledReminders())
        store.removeScheduledGroupUuid(plan)
        assertEquals(mapOf<UUID, Instant?>(older to null), store.getScheduledReminders())
    }

    @Test
    fun cancelLegacySnoozes_cancelsEachAlarmThenDeletesTheStore() = runTest(dispatcher) {
        val file = tmpFolder.root.resolve("snoozes.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = TestScope(dispatcher), produceFile = { file })
        val slot = "aaaaaaaa-0000-4000-8000-000000000001|11111111-0000-4000-8000-000000000001|2026-04-20T09:00"
        val records = setOf("$slot\u001F2026-04-20T09:15\u001F1", "$slot\u001F2026-04-20T09:30\u001F2")
        store.edit { preferences -> preferences[stringSetPreferencesKey("snooze_records")] = records }
        val cancelled = mutableListOf<String>()

        val count = cancelLegacySnoozes(file, store) { alarm -> cancelled += alarm }

        assertEquals(2, count)
        assertEquals(legacySnoozeAlarmData(records).toSet(), cancelled.toSet())
        assertFalse("the store is deleted", file.exists())
        // Once it is gone, nothing more is done.
        assertEquals(0, cancelLegacySnoozes(file, store) { alarm -> cancelled += alarm })
        assertEquals(2, cancelled.size)
    }
}
