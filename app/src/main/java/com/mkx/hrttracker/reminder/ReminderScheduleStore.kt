package com.mkx.hrttracker.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reminderScheduleDataStore by preferencesDataStore(
    name = "reminder_schedule",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class ReminderScheduleStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.reminderScheduleDataStore)

    /**
     * Each armed group's alert, as the instant its alarm goes off. Null for a key v1.4.2 stored
     * with no time, which takes any delivery.
     */
    suspend fun getScheduledReminders(): Map<UUID, Instant?> {
        return dataStore.data
            .map { preferences -> preferences[scheduledGroupUuidsKey].orEmpty().decode(::parseInstant) }
            .first()
    }

    suspend fun addScheduledReminder(uuid: UUID, alertAt: Instant) {
        dataStore.edit { preferences ->
            preferences[scheduledGroupUuidsKey] = preferences[scheduledGroupUuidsKey].orEmpty()
                .withoutGroup(uuid)
                .plus(storageValue(uuid, alertAt.toEpochMilli().toString()))
        }
    }

    suspend fun removeScheduledGroupUuid(uuid: UUID) {
        dataStore.edit { preferences ->
            preferences[scheduledGroupUuidsKey] = preferences[scheduledGroupUuidsKey].orEmpty().withoutGroup(uuid)
        }
    }

    suspend fun replaceScheduledReminders(alertAtByUuid: Map<UUID, Instant>) {
        dataStore.edit { preferences ->
            preferences[scheduledGroupUuidsKey] = alertAtByUuid
                .mapTo(mutableSetOf()) { (uuid, alertAt) -> storageValue(uuid, alertAt.toEpochMilli().toString()) }
        }
    }

    /** Each group's skip watermark: Skip stopped its doses at or before this time. */
    suspend fun getSkippedThrough(): Map<UUID, LocalDateTime> {
        return dataStore.data
            .map { preferences ->
                preferences[skippedThroughKey].orEmpty()
                    .decode { at -> runCatching { LocalDateTime.parse(at) }.getOrNull() }
                    .mapNotNull { (uuid, at) -> at?.let { uuid to it } }
                    .toMap()
            }
            .first()
    }

    /** Moves [uuid]'s watermark to [doseAt], never back: a Skip on an older reminder keeps a later one. */
    // ponytail: a deleted plan's watermark stays, one short value per plan ever skipped; prune in
    // rescheduleAll if that ever matters.
    suspend fun skipThrough(uuid: UUID, doseAt: LocalDateTime) {
        dataStore.edit { preferences ->
            val values = preferences[skippedThroughKey].orEmpty()
            val current = values.decode { at -> runCatching { LocalDateTime.parse(at) }.getOrNull() }[uuid]
            if (current == null || doseAt.isAfter(current)) {
                preferences[skippedThroughKey] = values.withoutGroup(uuid).plus(storageValue(uuid, doseAt.toString()))
            }
        }
    }

    suspend fun clearSkippedThrough() {
        dataStore.edit { preferences -> preferences.remove(skippedThroughKey) }
    }

    suspend fun isReconcilePending(): Boolean {
        return dataStore.data
            .map { preferences -> preferences[reconcilePendingKey] == true }
            .first()
    }

    suspend fun setReconcilePending(pending: Boolean) {
        dataStore.edit { preferences ->
            if (pending) {
                preferences[reconcilePendingKey] = true
            } else {
                preferences.remove(reconcilePendingKey)
            }
        }
    }

    private fun storageValue(uuid: UUID, value: String): String = "$uuid$SEPARATOR$value"

    private fun Set<String>.withoutGroup(uuid: UUID): Set<String> =
        filterNotTo(mutableSetOf()) { value -> value.startsWith(uuid.toString()) }

    private fun <T> Set<String>.decode(parse: (String) -> T?): Map<UUID, T?> =
        mapNotNull { value ->
            val parts = value.split(SEPARATOR, limit = 2)
            val uuid = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return@mapNotNull null
            uuid to parts.getOrNull(1)?.let(parse)
        }.toMap()

    private fun parseInstant(value: String): Instant? =
        value.toLongOrNull()?.let(Instant::ofEpochMilli)

    private companion object {
        // Each value is "<uuid>" (v1.4.2) or "<uuid>\u001F<alert's epoch millis>". A value from an
        // unreleased build that stored a local time reads as v1.4.2's.
        val scheduledGroupUuidsKey = stringSetPreferencesKey("scheduled_group_uuids")
        // Each value is "<uuid>\u001F<dose's local date-time>".
        val skippedThroughKey = stringSetPreferencesKey("skipped_through")
        val reconcilePendingKey = booleanPreferencesKey("reconcile_pending")
        const val SEPARATOR = "\u001F"
    }
}
