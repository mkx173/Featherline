package com.mkx.hrttracker.reminder

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private val Context.medicationReminderSnoozeDataStore by preferencesDataStore(
    name = "medication_reminder_snoozes",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    produceMigrations = { listOf(legacySnoozeTimeMigration()) },
)

private val snoozeRecordsKey = stringSetPreferencesKey("snooze_records")

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
}

internal fun MedicationReminderSnoozeRecord.toStorageValue(): String {
    return listOf(
        slot.toStorageValue(),
        snoozeAt.toEpochMilli().toString(),
        snoozeCount.toString(),
        fired.toString(),
    ).joinToString(separator = SEPARATOR)
}

internal fun snoozeRecordFromStorageValue(value: String): MedicationReminderSnoozeRecord? {
    val parts = value.split(SEPARATOR, limit = 4)
    if (parts.size != 4) {
        return null
    }
    return runCatching {
        MedicationReminderSnoozeRecord(
            slot = checkNotNull(medicationReminderSlotFromStorageValue(parts[0])),
            snoozeAt = Instant.ofEpochMilli(parts[1].toLong()),
            snoozeCount = parts[2].toInt(),
            fired = parts[3].toBooleanStrict(),
        )
    }.getOrNull()
}

/**
 * Older builds stored a record as its slot, its local snooze time and its count. This reads
 * that time once, in the zone at migration, and takes one already past as fired: nothing
 * tells whether its alarm came, and those builds dropped such a record at their next pass.
 */
internal fun legacySnoozeTimeMigration(
    zoneId: () -> ZoneId = ZoneId::systemDefault,
    now: () -> Instant = Instant::now,
): DataMigration<Preferences> = object : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[snoozeRecordsKey].orEmpty().any(::isLegacySnoozeRecord)

    override suspend fun migrate(currentData: Preferences): Preferences {
        val zone = zoneId()
        val migratedAt = now()
        return currentData.toMutablePreferences().apply {
            this[snoozeRecordsKey] = currentData[snoozeRecordsKey].orEmpty()
                .mapNotNullTo(mutableSetOf()) { value ->
                    if (isLegacySnoozeRecord(value)) {
                        legacySnoozeRecord(value, zone, migratedAt)?.toStorageValue()
                    } else {
                        value
                    }
                }
        }
    }

    override suspend fun cleanUp() = Unit
}

private fun isLegacySnoozeRecord(value: String): Boolean = value.split(SEPARATOR).size == 3

private fun legacySnoozeRecord(
    value: String,
    zone: ZoneId,
    now: Instant,
): MedicationReminderSnoozeRecord? {
    val (slot, snoozeAt, snoozeCount) = value.split(SEPARATOR)
    return runCatching {
        val deadline = LocalDateTime.parse(snoozeAt).atZone(zone).toInstant()
        MedicationReminderSnoozeRecord(
            slot = checkNotNull(medicationReminderSlotFromStorageValue(slot)),
            snoozeAt = deadline,
            snoozeCount = snoozeCount.toInt(),
            fired = !deadline.isAfter(now),
        )
    }.getOrNull()
}

private const val SEPARATOR = "\u001F"
