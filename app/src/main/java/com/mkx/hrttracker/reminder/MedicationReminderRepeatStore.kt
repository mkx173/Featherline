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
import javax.inject.Inject
import javax.inject.Singleton

private val Context.medicationReminderRepeatDataStore by preferencesDataStore(
    name = "medication_reminder_repeats",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Persists repeat records so a chain survives reboot, encoded as the snooze store does. */
@Singleton
class MedicationReminderRepeatStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun getRepeatRecords(): List<MedicationReminderRepeatRecord> {
        return context.medicationReminderRepeatDataStore.data
            .map { preferences ->
                preferences[repeatRecordsKey]
                    .orEmpty()
                    .mapNotNull(::repeatRecordFromStorageValue)
            }
            .first()
    }

    suspend fun replaceRepeatRecords(records: List<MedicationReminderRepeatRecord>) {
        context.medicationReminderRepeatDataStore.edit { preferences ->
            preferences[repeatRecordsKey] = records
                .mapTo(mutableSetOf(), MedicationReminderRepeatRecord::toStorageValue)
        }
    }

    private companion object {
        val repeatRecordsKey = stringSetPreferencesKey("repeat_records")
    }
}

internal fun MedicationReminderRepeatRecord.toStorageValue(): String {
    return listOf(
        slot.toStorageValue(),
        repeatAt.toString(),
        repeatCount.toString(),
    ).joinToString(separator = "\u001F")
}

internal fun repeatRecordFromStorageValue(value: String): MedicationReminderRepeatRecord? {
    val parts = value.split("\u001F", limit = 3)
    if (parts.size != 3) {
        return null
    }
    return runCatching {
        MedicationReminderRepeatRecord(
            slot = checkNotNull(medicationReminderSlotFromStorageValue(parts[0])),
            repeatAt = LocalDateTime.parse(parts[1]),
            repeatCount = parts[2].toInt(),
        )
    }.getOrNull()
}
