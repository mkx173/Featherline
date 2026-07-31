package com.mkx.hrttracker.cloudsync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cloudSyncDataStore by preferencesDataStore(name = "cloud_sync")

@Singleton
class CloudSyncPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val state: Flow<CloudSyncStoredState> = context.cloudSyncDataStore.data.map { preferences ->
        CloudSyncStoredState(
            enabled = preferences[ENABLED] ?: false,
            interval = preferences[INTERVAL]
                ?.let { stored -> CloudSyncInterval.entries.firstOrNull { it.name == stored } }
                ?: CloudSyncInterval.DAILY,
            status = preferences[STATUS]
                ?.let { stored -> CloudSyncStatus.entries.firstOrNull { it.name == stored } }
                ?: CloudSyncStatus.DISCONNECTED,
            lastSyncAt = preferences[LAST_SYNC_AT]?.let(Instant::ofEpochMilli),
            lastSyncedRevision = preferences[LAST_SYNCED_REVISION],
            lastSyncedContentHash = preferences[LAST_SYNCED_CONTENT_HASH],
            deviceId = preferences[DEVICE_ID],
            lastError = preferences[LAST_ERROR],
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.cloudSyncDataStore.edit { preferences ->
            preferences[ENABLED] = enabled
            preferences[STATUS] = if (enabled) {
                CloudSyncStatus.READY.name
            } else {
                CloudSyncStatus.DISCONNECTED.name
            }
            preferences.remove(LAST_ERROR)
        }
    }

    suspend fun setInterval(interval: CloudSyncInterval) {
        context.cloudSyncDataStore.edit { it[INTERVAL] = interval.name }
    }

    suspend fun deviceId(): String {
        var result: String? = null
        context.cloudSyncDataStore.edit { preferences ->
            result = preferences[DEVICE_ID] ?: UUID.randomUUID().toString().also {
                preferences[DEVICE_ID] = it
            }
        }
        return checkNotNull(result)
    }

    suspend fun markSyncing() {
        context.cloudSyncDataStore.edit { preferences ->
            preferences[STATUS] = CloudSyncStatus.SYNCING.name
            preferences.remove(LAST_ERROR)
        }
    }

    suspend fun recordSuccess(
        syncedAt: Instant,
        revision: String,
        contentHash: String,
    ) {
        context.cloudSyncDataStore.edit { preferences ->
            preferences[STATUS] = CloudSyncStatus.READY.name
            preferences[LAST_SYNC_AT] = syncedAt.toEpochMilli()
            preferences[LAST_SYNCED_REVISION] = revision
            preferences[LAST_SYNCED_CONTENT_HASH] = contentHash
            preferences.remove(LAST_ERROR)
        }
    }

    suspend fun recordNeedsAuthorization() {
        context.cloudSyncDataStore.edit { preferences ->
            preferences[STATUS] = CloudSyncStatus.NEEDS_AUTHORIZATION.name
            preferences.remove(LAST_ERROR)
        }
    }

    suspend fun recordConflict() {
        context.cloudSyncDataStore.edit { preferences ->
            preferences[STATUS] = CloudSyncStatus.CONFLICT.name
            preferences.remove(LAST_ERROR)
        }
    }

    suspend fun recordFailure(error: Throwable) {
        context.cloudSyncDataStore.edit { preferences ->
            preferences[STATUS] = CloudSyncStatus.ERROR.name
            preferences[LAST_ERROR] = error.message ?: error::class.java.simpleName
        }
    }

    suspend fun clearConnection() {
        context.cloudSyncDataStore.edit { preferences ->
            val interval = preferences[INTERVAL]
            val deviceId = preferences[DEVICE_ID]
            preferences.clear()
            if (interval != null) preferences[INTERVAL] = interval
            if (deviceId != null) preferences[DEVICE_ID] = deviceId
            preferences[STATUS] = CloudSyncStatus.DISCONNECTED.name
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val INTERVAL = stringPreferencesKey("interval")
        val STATUS = stringPreferencesKey("status")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_SYNCED_REVISION = stringPreferencesKey("last_synced_revision")
        val LAST_SYNCED_CONTENT_HASH = stringPreferencesKey("last_synced_content_hash")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val LAST_ERROR = stringPreferencesKey("last_error")
    }
}
