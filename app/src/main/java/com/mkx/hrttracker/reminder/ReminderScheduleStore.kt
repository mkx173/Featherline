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
    suspend fun getScheduledGroupUuids(): Set<UUID> {
        return context.reminderScheduleDataStore.data
            .map { preferences ->
                preferences[scheduledGroupUuidsKey]
                    .orEmpty()
                    .mapNotNull { value -> runCatching { UUID.fromString(value) }.getOrNull() }
                    .toSet()
            }
            .first()
    }

    suspend fun addScheduledGroupUuid(uuid: UUID) {
        context.reminderScheduleDataStore.edit { preferences ->
            preferences[scheduledGroupUuidsKey] = preferences[scheduledGroupUuidsKey]
                .orEmpty()
                .plus(uuid.toString())
        }
    }

    suspend fun removeScheduledGroupUuid(uuid: UUID) {
        context.reminderScheduleDataStore.edit { preferences ->
            preferences[scheduledGroupUuidsKey] = preferences[scheduledGroupUuidsKey]
                .orEmpty()
                .minus(uuid.toString())
        }
    }

    suspend fun replaceScheduledGroupUuids(uuids: Set<UUID>) {
        context.reminderScheduleDataStore.edit { preferences ->
            preferences[scheduledGroupUuidsKey] = uuids.mapTo(mutableSetOf(), UUID::toString)
        }
    }

    private companion object {
        val scheduledGroupUuidsKey = stringSetPreferencesKey("scheduled_group_uuids")
    }
}
