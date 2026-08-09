package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.pk.PkCalibrationAttestation
import com.mkx.hrttracker.model.pk.PkCalibrationAttestationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Durable §U1 attestation record (model §A2): a tri-state plus timestamp.
 * UNSEEN means the sheet was never decided; DECLINED covers both an explicit
 * decline and a later withdrawal — both fail closed to population.
 */
sealed interface PkCalibrationAttestationState {
    data object Unseen : PkCalibrationAttestationState
    data object Declined : PkCalibrationAttestationState
    data class Attested(val attestedAtEpochMillis: Long) : PkCalibrationAttestationState
}

private val Context.pkCalibrationAttestationDataStore by preferencesDataStore(
    name = "pk_calibration_attestation",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class PkCalibrationAttestationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
    @param:AppScope private val appScope: CoroutineScope,
) {
    internal constructor(
        context: Context,
        clock: Clock,
        appScope: CoroutineScope,
        dataStore: DataStore<Preferences>,
    ) : this(context, clock, appScope) {
        storedPreferencesOverride = dataStore
    }

    // Null in production (the context store is used); set by the internal test
    // constructor. [state] is lazy so the override is always assigned first.
    @Volatile
    private var storedPreferencesOverride: DataStore<Preferences>? = null

    private fun activeDataStore(): DataStore<Preferences> =
        storedPreferencesOverride ?: context.pkCalibrationAttestationDataStore

    /**
     * Null until the first DataStore read lands. Consumers must treat null as
     * "not loaded yet", never as UNSEEN — the first-entry auto-present keys
     * off a loaded UNSEEN only.
     */
    val state: StateFlow<PkCalibrationAttestationState?> by lazy {
        activeDataStore().data
            .map(::readState)
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }

    /** §U1: one explicit affirmative action; the record is a flag + timestamp. */
    suspend fun confirm() {
        val attestedAtEpochMillis = clock.millis()
        activeDataStore().edit { preferences ->
            preferences[statusKey] = STATUS_ATTESTED
            preferences[attestedAtKey] = attestedAtEpochMillis
        }
    }

    suspend fun decline() {
        activeDataStore().edit { preferences ->
            preferences[statusKey] = STATUS_DECLINED
            preferences.remove(attestedAtKey)
        }
    }

    /**
     * §U1: withdrawal takes effect immediately and returns every route to
     * population. Stored identically to a decline; re-attesting is the same
     * flow again.
     */
    suspend fun withdraw() = decline()

    private fun readState(preferences: Preferences): PkCalibrationAttestationState {
        return when (preferences[statusKey]) {
            STATUS_ATTESTED -> {
                val attestedAtEpochMillis = preferences[attestedAtKey]
                // A record that cannot back a valid engine attestation fails
                // closed to UNSEEN: no personalization, and the sheet re-asks.
                if (attestedAtEpochMillis != null &&
                    runCatching { PkCalibrationAttestation(attestedAtEpochMillis) }.isSuccess
                ) {
                    PkCalibrationAttestationState.Attested(attestedAtEpochMillis)
                } else {
                    PkCalibrationAttestationState.Unseen
                }
            }

            STATUS_DECLINED -> PkCalibrationAttestationState.Declined
            else -> PkCalibrationAttestationState.Unseen
        }
    }

    private companion object {
        val statusKey = stringPreferencesKey("status")
        val attestedAtKey = longPreferencesKey("attested_at_epoch_millis")
        const val STATUS_ATTESTED = "ATTESTED"
        const val STATUS_DECLINED = "DECLINED"
    }
}

/**
 * The real engine-facing provider (§U5 blocker 1): ATTESTED maps to a current
 * [PkCalibrationAttestation], everything else (including not-yet-loaded) is
 * null and the engine stays at SCOPE_NOT_CONFIRMED. Build-agnostic — the
 * runtime policy (Phase 3.3) is what gates release builds.
 */
@Singleton
class PersistedPkCalibrationAttestationProvider @Inject constructor(
    private val repository: PkCalibrationAttestationRepository,
) : PkCalibrationAttestationProvider {
    override fun currentAttestation(): PkCalibrationAttestation? {
        val attested = repository.state.value
            as? PkCalibrationAttestationState.Attested ?: return null
        return PkCalibrationAttestation(attested.attestedAtEpochMillis)
    }
}
