package com.mkx.hrttracker.healthconnect

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.UserProfileDao
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WeightSyncCoordinatorTest {
    private val dao = FakeUserProfileDao()
    private val hc = FakeHealthConnectManager()
    private val settingsRepository: SettingsRepository = mockk()
    private var syncEnabled = true
    private lateinit var repository: UserProfileRepository
    private lateinit var coordinator: WeightSyncCoordinator

    private val t0 = Instant.parse("2026-10-01T07:00:00Z")
    private val t1 = Instant.parse("2026-10-10T07:00:00Z")
    private val now = Instant.parse("2026-10-16T09:00:00Z")

    @Before
    fun setUp() {
        val databaseHolder: DatabaseHolder = mockk()
        val database: HrtTrackerDatabase = mockk()
        val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns database
        every { database.userProfileDao() } returns dao
        coEvery { homeSnapshotRepository.runHomeDataMutation<Any?>(any()) } coAnswers {
            firstArg<suspend () -> Any?>().invoke()
        }
        coEvery { settingsRepository.getCurrentSettings() } answers {
            SettingsState(healthConnectWeightSyncEnabled = syncEnabled)
        }
        coEvery { settingsRepository.setHealthConnectWeightSyncEnabled(any()) } answers {
            syncEnabled = firstArg()
        }
        val scope = CoroutineScope(StandardTestDispatcher())
        repository = UserProfileRepository(databaseHolder, homeSnapshotRepository, scope)
        coordinator = WeightSyncCoordinator(hc, repository, settingsRepository)
    }

    @Test
    fun turningOnReplacesTypedWeightWithLatestReading_evenAnOlderOne() = runTest {
        // Typed after the reading: with sync on, HC is the source regardless.
        repository.setWeight(150.0, WeightUnit.POUNDS, now = t1)
        hc.records += HealthConnectWeight(weightKg = 70.0, time = t0)

        coordinator.sync(now = now)

        val row = dao.row!!
        // Into the user's unit, rounded to 0.1.
        assertEquals(WeightUnit.POUNDS.name, row.weightOriginalUnit)
        assertEquals(154.3, row.weightOriginalValue!!, 0.0)
        assertEquals(t0.toEpochMilli(), row.weightMeasuredAtEpochMillis)
        assertFalse(coordinator.hasNoReading.value)
    }

    @Test
    fun aFastSyncStaysSyncingForTheMinimumTime() = runTest {
        hc.records += HealthConnectWeight(weightKg = 70.0, time = t0)

        launch { coordinator.sync(now = now) }
        runCurrent()
        assertTrue(coordinator.isSyncing.value)

        // The fake read returns at once; the state still holds for one whole turn.
        // The margin absorbs the few real ms the sync itself takes.
        advanceTimeBy(WeightSyncCoordinator.MIN_SYNCING_MILLIS - 100)
        assertTrue(coordinator.isSyncing.value)
        advanceTimeBy(200)
        assertFalse(coordinator.isSyncing.value)
    }

    @Test
    fun noReadingKeepsCurrentWeightAndReportsNone() = runTest {
        repository.setWeight(62.4, WeightUnit.KILOGRAMS, now = t1)
        val before = dao.row

        coordinator.sync(now = now)

        assertEquals(before, dao.row)
        assertTrue(coordinator.hasNoReading.value)
    }

    @Test
    fun unchangedReadingWritesNothing() = runTest {
        hc.records += HealthConnectWeight(weightKg = 61.83, time = t1)
        coordinator.sync(now = now)
        val adoptWritesAfterFirst = dao.adoptWrites

        // Same reading, even with extra sub-millisecond precision.
        hc.records[0] = HealthConnectWeight(weightKg = 61.81, time = t1.plusNanos(300))
        coordinator.sync(now = now)

        assertEquals(1, adoptWritesAfterFirst)
        assertEquals(1, dao.adoptWrites)
        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
    }

    @Test
    fun deletedLatestReadingFallsBackToNextLatest() = runTest {
        val older = HealthConnectWeight(weightKg = 60.0, time = t0)
        val latest = HealthConnectWeight(weightKg = 61.8, time = t1)
        hc.records += listOf(older, latest)
        coordinator.sync(now = now)
        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)

        hc.records -= latest
        coordinator.sync(now = now)

        assertEquals(60.0, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t0.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)

        // Nothing left: the weight stays, but no longer claims a measured time.
        hc.records.clear()
        coordinator.sync(now = now)
        assertEquals(60.0, dao.row!!.weightOriginalValue!!, 0.0)
        assertNull(dao.row!!.weightMeasuredAtEpochMillis)
        assertTrue(coordinator.hasNoReading.value)
    }

    @Test
    fun zeroKgReadingIsIgnored() = runTest {
        repository.setWeight(62.4, WeightUnit.KILOGRAMS, now = t0)
        // Backup restore rejects a 0 weight, so adopting one would break every backup.
        hc.records += HealthConnectWeight(weightKg = 0.04, time = t1)

        coordinator.sync(now = now)

        assertEquals(62.4, dao.row!!.weightOriginalValue!!, 0.0)
        assertTrue(coordinator.hasNoReading.value)
    }

    @Test
    fun cancelledSyncDoesNotLeaveSyncingOn() = runTest {
        hc.records += HealthConnectWeight(weightKg = 70.0, time = t0)

        val job = launch { coordinator.sync(now = now) }
        runCurrent()
        assertTrue(coordinator.isSyncing.value)
        job.cancel()
        runCurrent()

        assertFalse(coordinator.isSyncing.value)
    }

    @Test
    fun turningSyncOffDuringARead_keepsTheTypedWeight() = runTest {
        repository.setWeight(62.4, WeightUnit.KILOGRAMS, now = t1)
        hc.records += HealthConnectWeight(weightKg = 70.0, time = t0)
        val gate = CompletableDeferred<Unit>()
        hc.readGate = gate

        launch { coordinator.sync(now = now) }
        runCurrent()
        launch { coordinator.disable() }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(syncEnabled)
        assertEquals(62.4, dao.row!!.weightOriginalValue!!, 0.0)
        assertNull(dao.row!!.weightMeasuredAtEpochMillis)
    }

    @Test
    fun aWeightTypedRightAfterTurningSyncOff_survivesThePendingAdoption() = runTest {
        hc.records += HealthConnectWeight(weightKg = 70.0, time = t0)
        // Holds the sync in adoptHcWeight's profile read, before its mutation.
        val gate = CompletableDeferred<Unit>()
        dao.getProfileGate = gate

        launch { coordinator.sync(now = now) }
        runCurrent()
        launch { coordinator.disable() }
        runCurrent()
        // The field is editable once the flag is off.
        repository.setWeight(65.0, WeightUnit.KILOGRAMS, now = now)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(65.0, dao.row!!.weightOriginalValue!!, 0.0)
        assertNull(dao.row!!.weightMeasuredAtEpochMillis)
    }

    @Test
    fun skipIfFresh_skipsRightAfterASync_untilSyncIsTurnedOff() = runTest {
        hc.records += HealthConnectWeight(weightKg = 70.0, time = t0)
        coordinator.sync(now = now)

        // A foreground sync, then the grant's: the second is skipped...
        coordinator.sync(now = now, skipIfFresh = true)
        assertEquals(1, hc.reads)
        // ...but Sync now isn't.
        coordinator.sync(now = now)
        assertEquals(2, hc.reads)

        // Turned off and straight back on: the enable's sync runs.
        coordinator.disable()
        syncEnabled = true
        coordinator.sync(now = now, skipIfFresh = true)
        assertEquals(3, hc.reads)
    }

    @Test
    fun readingWithFutureTimestampIsIgnored() = runTest {
        repository.setWeight(60.0, WeightUnit.KILOGRAMS, now = t1)
        hc.records += HealthConnectWeight(weightKg = 80.0, time = now + Duration.ofMinutes(10))

        coordinator.sync(now = now)

        assertEquals(60.0, dao.row!!.weightOriginalValue!!, 0.0)
        assertNull(dao.row!!.weightMeasuredAtEpochMillis)
        assertEquals(now + Duration.ofMinutes(5), hc.lastNotAfter)
    }

    @Test
    fun doesNothingWhenSyncIsOffOrAccessIsMissing() = runTest {
        repository.setWeight(62.4, WeightUnit.KILOGRAMS, now = t1)
        hc.records += HealthConnectWeight(weightKg = 70.0, time = t0)

        syncEnabled = false
        coordinator.sync(now = now)
        syncEnabled = true
        hc.access = false
        coordinator.sync(now = now)
        hc.access = true
        hc.available = false
        coordinator.sync(now = now)

        assertEquals(0, hc.reads)
        assertEquals(62.4, dao.row!!.weightOriginalValue!!, 0.0)
    }
}

private class FakeUserProfileDao : UserProfileDao {
    var row: UserProfileEntity? = null
    var adoptWrites = 0 // writes carrying an HC measured time
    var getProfileGate: CompletableDeferred<Unit>? = null // the next read waits on it

    override suspend fun getProfile(id: String): UserProfileEntity? {
        getProfileGate?.let { gate ->
            getProfileGate = null
            gate.await()
        }
        return row
    }

    override fun observeProfile(id: String): Flow<UserProfileEntity?> = MutableStateFlow(row)

    override suspend fun upsertProfile(profile: UserProfileEntity) {
        if (profile.weightMeasuredAtEpochMillis != null) adoptWrites++
        row = profile
    }

    override suspend fun deleteProfile() {
        row = null
    }
}

private class FakeHealthConnectManager : HealthConnectManager {
    var available = true
    var access = true
    val records = mutableListOf<HealthConnectWeight>()
    var lastNotAfter: Instant? = null
    var reads = 0
    var readGate: CompletableDeferred<Unit>? = null

    override fun isAvailable(): Boolean = available

    override fun isProviderUpdateRequired(): Boolean = false

    override suspend fun hasWeightAccess(): Boolean = access

    // Returns the latest record regardless of notAfter, so the coordinator's own
    // future-timestamp guard is what the tests exercise.
    override suspend fun readLatestWeight(notAfter: Instant): HealthConnectWeight? {
        reads++
        lastNotAfter = notAfter
        readGate?.await()
        return records.maxByOrNull { it.time }
    }
}
