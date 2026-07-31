package com.mkx.hrttracker.healthconnect

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.healthConnectDataStore by preferencesDataStore(
    name = "health_connect",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
internal class HealthConnectPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val state: Flow<HealthConnectStoredState> = context.healthConnectDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            HealthConnectStoredState(
                weightSyncEnabled = preferences[WEIGHT_SYNC_ENABLED] ?: false,
                medicationSyncEnabled = preferences[MEDICATION_SYNC_ENABLED] ?: false,
                importedWeightFingerprint = preferences[IMPORTED_WEIGHT_FINGERPRINT],
                exportedMedicationFingerprints = decodeFingerprints(
                    preferences[EXPORTED_MEDICATION_FINGERPRINTS].orEmpty()
                ),
                medicalDataSourceId = preferences[MEDICAL_DATA_SOURCE_ID],
                lastWeightSyncAt = preferences[LAST_WEIGHT_SYNC_AT]?.let(Instant::ofEpochMilli),
                lastMedicationSyncAt =
                    preferences[LAST_MEDICATION_SYNC_AT]?.let(Instant::ofEpochMilli),
                lastImportedWeightKg =
                    preferences[LAST_IMPORTED_WEIGHT_KG]?.toDoubleOrNull(),
            )
        }

    suspend fun setWeightSyncEnabled(enabled: Boolean) {
        context.healthConnectDataStore.edit { it[WEIGHT_SYNC_ENABLED] = enabled }
    }

    suspend fun setMedicationSyncEnabled(enabled: Boolean) {
        context.healthConnectDataStore.edit { it[MEDICATION_SYNC_ENABLED] = enabled }
    }

    suspend fun recordWeightImport(
        fingerprint: String,
        weightKg: Double,
        syncedAt: Instant,
    ) {
        context.healthConnectDataStore.edit { preferences ->
            preferences[IMPORTED_WEIGHT_FINGERPRINT] = fingerprint
            preferences[LAST_IMPORTED_WEIGHT_KG] = weightKg.toString()
            preferences[LAST_WEIGHT_SYNC_AT] = syncedAt.toEpochMilli()
        }
    }

    suspend fun recordWeightCheck(syncedAt: Instant) {
        context.healthConnectDataStore.edit {
            it[LAST_WEIGHT_SYNC_AT] = syncedAt.toEpochMilli()
        }
    }

    suspend fun recordMedicationSync(
        dataSourceId: String,
        fingerprints: Map<String, String>,
        syncedAt: Instant,
    ) {
        context.healthConnectDataStore.edit { preferences ->
            preferences[MEDICAL_DATA_SOURCE_ID] = dataSourceId
            preferences[EXPORTED_MEDICATION_FINGERPRINTS] = encodeFingerprints(fingerprints)
            preferences[LAST_MEDICATION_SYNC_AT] = syncedAt.toEpochMilli()
        }
    }

    suspend fun resetMedicationExportState(dataSourceId: String?) {
        context.healthConnectDataStore.edit { preferences ->
            if (dataSourceId == null) {
                preferences.remove(MEDICAL_DATA_SOURCE_ID)
            } else {
                preferences[MEDICAL_DATA_SOURCE_ID] = dataSourceId
            }
            preferences.remove(EXPORTED_MEDICATION_FINGERPRINTS)
            preferences.remove(LAST_MEDICATION_SYNC_AT)
        }
    }

    private companion object {
        val WEIGHT_SYNC_ENABLED = booleanPreferencesKey("weight_sync_enabled")
        val MEDICATION_SYNC_ENABLED = booleanPreferencesKey("medication_sync_enabled")
        val IMPORTED_WEIGHT_FINGERPRINT = stringPreferencesKey("imported_weight_fingerprint")
        val EXPORTED_MEDICATION_FINGERPRINTS =
            stringSetPreferencesKey("exported_medication_fingerprints")
        val MEDICAL_DATA_SOURCE_ID = stringPreferencesKey("medical_data_source_id")
        val LAST_WEIGHT_SYNC_AT = longPreferencesKey("last_weight_sync_at")
        val LAST_MEDICATION_SYNC_AT = longPreferencesKey("last_medication_sync_at")
        val LAST_IMPORTED_WEIGHT_KG = stringPreferencesKey("last_imported_weight_kg")
    }
}

internal fun encodeFingerprints(fingerprints: Map<String, String>): Set<String> =
    fingerprints.mapTo(linkedSetOf()) { (id, fingerprint) -> "$id|$fingerprint" }

internal fun decodeFingerprints(encoded: Set<String>): Map<String, String> =
    encoded.mapNotNull { value ->
        val separator = value.indexOf('|')
        if (separator <= 0 || separator == value.lastIndex) {
            null
        } else {
            value.substring(0, separator) to value.substring(separator + 1)
        }
    }.toMap()
