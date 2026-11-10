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

    // A backup from a phone with a wrong clock, or a hand-edited one.
    private val farFuture = Instant.parse("2099-01-01T00:00:00Z")

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
        assertNull(coordinator.readingProblem.value)
    }

    @Test
    fun noReadingKeepsTheWeightAndItsDate_andReportsNone() = runTest {
        repository.setWeight(62.4, WeightUnit.KILOGRAMS, now = t1)
        val before = dao.row

        coordinator.sync(now = now)

        assertEquals(before, dao.row)
        assertEquals(ReadingProblem.NONE_FOUND, coordinator.readingProblem.value)

        // Turned off, the weight and its date stay, and turned back on there's
        // no old problem before the new sync.
        coordinator.disable()
        assertEquals(before, dao.row)
        assertNull(coordinator.readingProblem.value)
    }

    @Test
    fun unchangedReadingWritesNothing() = runTest {
        hc.records += HealthConnectWeight(weightKg = 61.83, time = t1)
        coordinator.sync(now = now)
        val writesAfterFirst = dao.writes

        // Same reading, even with extra sub-millisecond precision.
        hc.records[0] = HealthConnectWeight(weightKg = 61.81, time = t1.plusNanos(300))
        coordinator.sync(now = now)

        assertEquals(1, writesAfterFirst)
        assertEquals(1, dao.writes)
        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
    }

    @Test
    fun aTypedWeightWithTheReadingsDateAndValue_isNotRewritten() = runTest {
        repository.setWeight(61.8, WeightUnit.KILOGRAMS, now = t1)
        hc.records += HealthConnectWeight(weightKg = 61.83, time = t1)

        coordinator.sync(now = now)

        assertEquals(1, dao.writes)
    }

    @Test
    fun aUnitSwitch_thenTheSyncItForces_endsAtHcsOwnValueInTheNewUnit() = runTest {
        hc.records += HealthConnectWeight(weightKg = 61.83, time = t1)
        coordinator.sync(now = now)
        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)

        // From the rounded 61.8 kg: 136.2 lb, 0.1 off HC's own 136.3.
        repository.setWeightUnit(WeightUnit.POUNDS, now = now)
        assertEquals(136.2, dao.row!!.weightOriginalValue!!, 0.0)
        coordinator.sync(now = now)

        val row = dao.row!!
        assertEquals(WeightUnit.POUNDS.name, row.weightOriginalUnit)
        assertEquals(136.3, row.weightOriginalValue!!, 0.0)
        assertEquals(WeightUnit.POUNDS.toKg(136.3), row.weightKg!!, 0.0)
        assertEquals(t1.toEpochMilli(), row.weightMeasuredAtEpochMillis)

        // And back: the same reading, in kg.
        repository.setWeightUnit(WeightUnit.KILOGRAMS, now = now)
        coordinator.sync(now = now)
        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(61.8, dao.row!!.weightKg!!, 0.0)
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

        // Nothing left: the weight and its date stay.
        hc.records.clear()
        coordinator.sync(now = now)
        assertEquals(60.0, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t0.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
        assertEquals(ReadingProblem.NONE_FOUND, coordinator.readingProblem.value)
    }

    @Test
    fun invalidLatestReading_keepsTheSavedWeightAndItsDate() = runTest {
        hc.records += HealthConnectWeight(weightKg = 61.8, time = t0)
        coordinator.sync(now = now)

        // Backup restore rejects a 0 weight, so adopting one would break every backup.
        hc.records += HealthConnectWeight(weightKg = 0.04, time = t1)
        coordinator.sync(now = now)

        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t0.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
        assertEquals(ReadingProblem.INVALID, coordinator.readingProblem.value)
    }

    @Test
    fun cancelledSyncDoesNotLeaveSyncingOn() = runTest {
        hc.records += HealthConnectWeight(weightKg = 70.0, time = t0)
        hc.readGate = CompletableDeferred() // cancelled mid-read

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
        assertEquals(t1.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
    }

    @Test
    fun aWeightTypedRightAfterTurningSyncOff_survivesThePendingAdoption() = runTest {
        hc.records += HealthConnectWeight(weightKg = 70.0, time = t0)
        // Holds the sync in its first profile read, before it adopts.
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
        assertEquals(now.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
    }

    @Test
    fun aFutureReadingBesidePastOnes_isIgnored_andTheLatestPastOneAdopted() = runTest {
        repository.setWeight(60.0, WeightUnit.KILOGRAMS, now = t0)
        hc.records += listOf(
            HealthConnectWeight(weightKg = 60.5, time = t0),
            HealthConnectWeight(weightKg = 61.8, time = t1),
            // A scale whose clock is a day ahead: Android 14+ can't hand it out yet.
            HealthConnectWeight(weightKg = 80.0, time = now + Duration.ofDays(1)),
        )

        coordinator.sync(now = now)

        assertEquals(now, hc.lastReadEnd)
        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t1.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
        assertNull(coordinator.readingProblem.value)

        // Once its time comes, it's the latest.
        coordinator.sync(now = now + Duration.ofDays(2))
        assertEquals(80.0, dao.row!!.weightOriginalValue!!, 0.0)
    }

    @Test
    fun onlyFutureReadings_reportNone_andKeepTheWeightAndItsDate() = runTest {
        repository.setWeight(62.4, WeightUnit.KILOGRAMS, now = t1)
        val typed = dao.row
        hc.records += listOf(
            HealthConnectWeight(weightKg = 80.0, time = now + Duration.ofMinutes(3)),
            HealthConnectWeight(weightKg = 90.0, time = farFuture),
        )

        coordinator.sync(now = now)

        assertEquals(typed, dao.row)
        assertEquals(ReadingProblem.NONE_FOUND, coordinator.readingProblem.value)
    }

    @Test
    fun clearSyncResult_dropsTheProblem_andKeepsTheWeightAndItsDate() = runTest {
        hc.records += HealthConnectWeight(weightKg = 61.8, time = t0)
        coordinator.sync(now = now)
        hc.records += HealthConnectWeight(weightKg = 0.0, time = t1)
        coordinator.sync(now = now)
        // A restore turned the flag off; the problem stayed.
        syncEnabled = false

        coordinator.clearSyncResult()

        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t0.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
        assertNull(coordinator.readingProblem.value)
    }

    @Test
    fun restoredFutureWeightNotInHc_givesWayToTheLatestReading() = runTest {
        restoreWeightMeasuredAt(farFuture)
        hc.records += listOf(
            HealthConnectWeight(weightKg = 60.0, time = t0),
            HealthConnectWeight(weightKg = 61.8, time = t1),
        )

        coordinator.sync(now = now)

        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t1.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
        assertNull(coordinator.readingProblem.value)
    }

    @Test
    fun restoredFutureWeightWithNoHcReading_keepsTheWeightAndItsDate() = runTest {
        restoreWeightMeasuredAt(farFuture)
        val restored = dao.row

        coordinator.sync(now = now)

        assertEquals(restored, dao.row)
        assertEquals(ReadingProblem.NONE_FOUND, coordinator.readingProblem.value)
    }

    @Test
    fun invalidLatestReading_keepsARestoredFutureDateHcLacks() = runTest {
        restoreWeightMeasuredAt(farFuture)
        val restored = dao.row
        hc.records += HealthConnectWeight(weightKg = 0.0, time = t1)

        coordinator.sync(now = now)

        // W3: an invalid reading keeps the weight and its date.
        assertEquals(restored, dao.row)
        assertEquals(ReadingProblem.INVALID, coordinator.readingProblem.value)
    }

    private fun restoreWeightMeasuredAt(measuredAt: Instant) {
        dao.row = UserProfileEntity(
            weightKg = 90.0,
            weightOriginalValue = 90.0,
            weightOriginalUnit = WeightUnit.KILOGRAMS.name,
            updatedAtEpochMillis = now.toEpochMilli(),
            weightMeasuredAtEpochMillis = measuredAt.toEpochMilli(),
        )
    }

    @Test
    fun doesNothingWhenSyncIsOff() = runTest {
        repository.setWeight(62.4, WeightUnit.KILOGRAMS, now = t1)
        hc.records += HealthConnectWeight(weightKg = 70.0, time = t0)

        syncEnabled = false
        coordinator.sync(now = now)

        assertEquals(0, hc.reads)
        assertEquals(62.4, dao.row!!.weightOriginalValue!!, 0.0)
    }
}

private class FakeUserProfileDao : UserProfileDao {
    var row: UserProfileEntity? = null
    var writes = 0
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
        writes++
        row = profile
    }

    override suspend fun deleteProfile() {
        row = null
    }
}

internal class FakeHealthConnectManager : HealthConnectManager {
    var available = true
    var access = true
    var providerUpdateRequired = false
    var accessError: Throwable? = null
    val records = mutableListOf<HealthConnectWeight>()
    var reads = 0
    var readGate: CompletableDeferred<Unit>? = null
    var lastReadEnd: Instant? = null

    override fun isAvailable(): Boolean = available

    override fun isProviderUpdateRequired(): Boolean = providerUpdateRequired

    override suspend fun hasWeightAccess(): Boolean = accessError?.let { throw it } ?: access

    override fun supportsReadHistory(): Boolean = false

    // As the real read's before(now): the end is exclusive. On a tie, the first in
    // the list: HC's order between equal times is unspecified.
    override suspend fun readLatestWeight(now: Instant): HealthConnectWeight? {
        reads++
        lastReadEnd = now
        readGate?.await()
        return records.filter { it.time < now }.maxByOrNull { it.time }
    }
}
