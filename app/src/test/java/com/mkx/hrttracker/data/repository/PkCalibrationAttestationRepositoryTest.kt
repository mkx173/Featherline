package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PkCalibrationAttestationRepositoryTest {
    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context: Context = mockk()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: PkCalibrationAttestationRepository
    private lateinit var provider: PersistedPkCalibrationAttestationProvider

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("attestation_test.preferences_pb") },
        )
        repository = PkCalibrationAttestationRepository(
            context = context,
            clock = Clock.fixed(Instant.ofEpochMilli(FixedNowMillis), ZoneOffset.UTC),
            appScope = testScope.backgroundScope,
            dataStore = dataStore,
        )
        provider = PersistedPkCalibrationAttestationProvider(repository)
    }

    @Test
    fun storeRoundTrip_coversTheFullTriState() = testScope.runTest {
        assertEquals(
            PkCalibrationAttestationState.Unseen,
            repository.state.filterNotNull().first(),
        )

        repository.confirm()
        assertEquals(
            PkCalibrationAttestationState.Attested(FixedNowMillis),
            repository.state.value,
        )

        // §U1: withdrawal takes effect immediately; stored as DECLINED so
        // later entries never auto-present again.
        repository.withdraw()
        assertEquals(PkCalibrationAttestationState.Declined, repository.state.value)

        // Re-attesting is the same flow again.
        repository.confirm()
        assertEquals(
            PkCalibrationAttestationState.Attested(FixedNowMillis),
            repository.state.value,
        )

        repository.decline()
        assertEquals(PkCalibrationAttestationState.Declined, repository.state.value)
    }

    @Test
    fun providerMapping_onlyAttestedYieldsACurrentAttestation() = testScope.runTest {
        // Not loaded yet and UNSEEN both map to null: the engine stays at
        // SCOPE_NOT_CONFIRMED (population everywhere).
        assertNull(provider.currentAttestation())
        repository.state.filterNotNull().first()
        assertNull(provider.currentAttestation())

        repository.confirm()
        assertEquals(
            FixedNowMillis,
            checkNotNull(provider.currentAttestation()).attestedAtEpochMillis,
        )

        // Withdraw returns every route to population: the provider goes null,
        // which the evidence layer already maps to SCOPE_NOT_CONFIRMED.
        repository.withdraw()
        assertNull(provider.currentAttestation())

        repository.decline()
        assertNull(provider.currentAttestation())
    }

    @Test
    fun corruptedAttestedRecord_failsClosedToUnseen() = testScope.runTest {
        // ATTESTED without a usable timestamp cannot back an engine
        // attestation: no personalization, and the sheet re-asks.
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("status")] = "ATTESTED"
        }
        assertEquals(
            PkCalibrationAttestationState.Unseen,
            repository.state.filterNotNull().first(),
        )
        assertNull(provider.currentAttestation())

        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("status")] = "ATTESTED"
            preferences[longPreferencesKey("attested_at_epoch_millis")] = Long.MAX_VALUE
        }
        assertEquals(PkCalibrationAttestationState.Unseen, repository.state.value)
        assertNull(provider.currentAttestation())
    }

    private companion object {
        const val FixedNowMillis = 1_700_000_000_000L
    }
}
