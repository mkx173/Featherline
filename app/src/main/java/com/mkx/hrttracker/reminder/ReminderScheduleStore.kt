package com.mkx.hrttracker.reminder

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reminderScheduleDataStore by preferencesDataStore(
    name = "reminder_schedule",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class ReminderScheduleStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * Each armed group's reminder time. Null for an entry stored before the
     * time was kept.
     */
    suspend fun getScheduledReminders(): Map<UUID, LocalDateTime?> {
        return context.reminderScheduleDataStore.data
            .map { preferences ->
                preferences[scheduledGroupUuidsKey]
                    .orEmpty()
                    .mapNotNull { value ->
                        val parts = value.split(SEPARATOR, limit = 2)
                        val uuid = runCatching { UUID.fromString(parts[0]) }.getOrNull()
                            ?: return@mapNotNull null
                        uuid to parts.getOrNull(1)?.let { at -> runCatching { LocalDateTime.parse(at) }.getOrNull() }
                    }
                    .toMap()
            }
            .first()
    }

    suspend fun addScheduledReminder(uuid: UUID, scheduledAt: LocalDateTime) {
        context.reminderScheduleDataStore.edit { preferences ->
            preferences[scheduledGroupUuidsKey] = preferences[scheduledGroupUuidsKey]
                .orEmpty()
                .filterNotTo(mutableSetOf()) { value -> value.startsWith(uuid.toString()) }
                .plus(storageValue(uuid, scheduledAt))
        }
    }

    suspend fun removeScheduledGroupUuid(uuid: UUID) {
        context.reminderScheduleDataStore.edit { preferences ->
            preferences[scheduledGroupUuidsKey] = preferences[scheduledGroupUuidsKey]
                .orEmpty()
                .filterNotTo(mutableSetOf()) { value -> value.startsWith(uuid.toString()) }
        }
    }

    suspend fun replaceScheduledReminders(scheduledAtByUuid: Map<UUID, LocalDateTime>) {
        context.reminderScheduleDataStore.edit { preferences ->
            preferences[scheduledGroupUuidsKey] = scheduledAtByUuid
                .mapTo(mutableSetOf()) { (uuid, scheduledAt) -> storageValue(uuid, scheduledAt) }
        }
    }

    private fun storageValue(uuid: UUID, scheduledAt: LocalDateTime): String {
        return "$uuid$SEPARATOR$scheduledAt"
    }

    private companion object {
        // Each value is "<uuid>" (older builds) or "<uuid>\u001F<scheduledAt>".
        val scheduledGroupUuidsKey = stringSetPreferencesKey("scheduled_group_uuids")
        const val SEPARATOR = "\u001F"
    }
}
