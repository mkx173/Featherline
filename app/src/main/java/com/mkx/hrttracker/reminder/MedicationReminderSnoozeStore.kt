package com.mkx.hrttracker.reminder

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.medicationReminderSnoozeDataStore by preferencesDataStore(
    name = "medication_reminder_snoozes"
)

@Singleton
class MedicationReminderSnoozeStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun getSnoozeRecords(): List<MedicationReminderSnoozeRecord> {
        return context.medicationReminderSnoozeDataStore.data
            .map { preferences ->
                preferences[snoozeRecordsKey]
                    .orEmpty()
                    .mapNotNull(::snoozeRecordFromStorageValue)
            }
            .first()
    }

    suspend fun replaceSnoozeRecords(records: List<MedicationReminderSnoozeRecord>) {
        context.medicationReminderSnoozeDataStore.edit { preferences ->
            preferences[snoozeRecordsKey] = records
                .mapTo(mutableSetOf(), MedicationReminderSnoozeRecord::toStorageValue)
        }
    }

    private companion object {
        val snoozeRecordsKey = stringSetPreferencesKey("snooze_records")
    }
}

private fun MedicationReminderSnoozeRecord.toStorageValue(): String {
    return listOf(
        slot.toStorageValue(),
        snoozeAt.toString(),
        snoozeCount.toString(),
    ).joinToString(separator = "\u001F")
}

private fun snoozeRecordFromStorageValue(value: String): MedicationReminderSnoozeRecord? {
    val parts = value.split("\u001F", limit = 3)
    if (parts.size != 3) {
        return null
    }
    return runCatching {
        MedicationReminderSnoozeRecord(
            slot = checkNotNull(medicationReminderSlotFromStorageValue(parts[0])),
            snoozeAt = LocalDateTime.parse(parts[1]),
            snoozeCount = parts[2].toInt(),
        )
    }.getOrNull()
}
