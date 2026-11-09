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

    // A backup from a phone with a wrong clock, or a hand-edited one: HC never had it.
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
    fun noReadingKeepsCurrentWeightAndReportsNone() = runTest {
        repository.setWeight(62.4, WeightUnit.KILOGRAMS, now = t1)
        val before = dao.row

        coordinator.sync(now = now)

        assertEquals(before, dao.row)
        assertEquals(ReadingProblem.NONE_FOUND, coordinator.readingProblem.value)

        // Turned back on, it reads Syncing until the new sync, not the old result.
        coordinator.disable()
        assertNull(coordinator.readingProblem.value)
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
        assertNull(dao.row!!.weightMeasuredAtEpochMillis)
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
        assertNull(dao.row!!.weightMeasuredAtEpochMillis)
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
    fun clockSetBackPastTheStoredReading_doesNotAdoptAnOlderOne() = runTest {
        hc.filtersByNotAfter = true
        hc.records += listOf(
            HealthConnectWeight(weightKg = 60.0, time = t0),
            HealthConnectWeight(weightKg = 61.8, time = t1),
        )
        coordinator.sync(now = now)

        // The read's time filter now hides t1; it wasn't deleted.
        coordinator.sync(now = t1 - Duration.ofDays(1))

        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t1.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
    }

    @Test
    fun clockSetBackPastTheOnlyReading_keepsItsMeasuredDate() = runTest {
        hc.filtersByNotAfter = true
        hc.records += HealthConnectWeight(weightKg = 61.8, time = t1)
        coordinator.sync(now = now)

        coordinator.sync(now = t1 - Duration.ofDays(1))

        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t1.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
        // Held, not kept for want of a reading: HC still has it.
        assertNull(coordinator.readingProblem.value)
    }

    @Test
    fun aSecondRecordAtTheStoredMillisecond_doesNotHideTheHeldReading() = runTest {
        hc.filtersByNotAfter = true
        val stored = HealthConnectWeight(weightKg = 61.8, time = t1)
        hc.records += stored
        coordinator.sync(now = now)
        val twin = HealthConnectWeight(weightKg = 62.5, time = t1)

        // Either one may come first: HC's order between equal times is unspecified.
        for (records in listOf(listOf(twin, stored), listOf(stored, twin))) {
            hc.records.clear()
            hc.records += records
            coordinator.sync(now = t1 - Duration.ofDays(1))

            assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
            assertEquals(t1.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
            assertNull(coordinator.readingProblem.value)
        }
    }

    @Test
    fun aHeldSync_clearsTheLastSyncsProblem() = runTest {
        hc.filtersByNotAfter = true
        hc.records += HealthConnectWeight(weightKg = 61.8, time = t1)
        coordinator.sync(now = now)
        hc.records += HealthConnectWeight(weightKg = 0.0, time = t1 + Duration.ofHours(1))
        coordinator.sync(now = now)
        assertEquals(ReadingProblem.INVALID, coordinator.readingProblem.value)

        coordinator.sync(now = t1 - Duration.ofDays(1))

        assertEquals(t1.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
        // The sheet would show the problem above the held reading's date.
        assertNull(coordinator.readingProblem.value)
    }

    @Test
    fun clearSyncResult_dropsWhatADisableThatDiedOrARestoreLeftBehind() = runTest {
        hc.records += HealthConnectWeight(weightKg = 61.8, time = t0)
        coordinator.sync(now = now)
        hc.records += HealthConnectWeight(weightKg = 0.0, time = t1)
        coordinator.sync(now = now)
        // The flag went off, but the date and the problem stayed.
        syncEnabled = false

        coordinator.clearSyncResult()

        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertNull(dao.row!!.weightMeasuredAtEpochMillis)
        assertNull(coordinator.readingProblem.value)
    }

    @Test
    fun deletedReadingWithinTheTolerance_fallsBackToTheOlderOne() = runTest {
        hc.filtersByNotAfter = true
        val scaleAhead = HealthConnectWeight(weightKg = 61.8, time = now + Duration.ofMinutes(3))
        hc.records += listOf(HealthConnectWeight(weightKg = 60.0, time = t0), scaleAhead)
        coordinator.sync(now = now)
        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)

        hc.records -= scaleAhead
        coordinator.sync(now = now)

        assertEquals(60.0, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t0.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
    }

    @Test
    fun clockSetBackPastTheStoredReading_stillInHc_holdsItOverALaterOne() = runTest {
        hc.filtersByNotAfter = true
        hc.records += listOf(
            HealthConnectWeight(weightKg = 60.0, time = t0),
            HealthConnectWeight(weightKg = 61.8, time = t1),
        )
        coordinator.sync(now = now)

        // The clock goes back past t1, then a scale with the right clock adds one.
        hc.records += HealthConnectWeight(weightKg = 63.0, time = now + Duration.ofHours(1))
        coordinator.sync(now = t1 - Duration.ofDays(1))

        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t1.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
    }

    @Test
    fun clockSetBackToPutTheStoredReadingAtTheReadsEnd_holdsIt() = runTest {
        hc.filtersByNotAfter = true
        hc.records += listOf(
            HealthConnectWeight(weightKg = 60.0, time = t0),
            HealthConnectWeight(weightKg = 61.8, time = t1),
        )
        coordinator.sync(now = now)

        // notAfter is exactly t1, which the read's exclusive end leaves out.
        coordinator.sync(now = t1 - Duration.ofMinutes(5))

        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t1.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
    }

    @Test
    fun restoredFutureWeightNotInHc_givesWayToTheNewestPastReading() = runTest {
        hc.filtersByNotAfter = true
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
    fun futureHcReading_isIgnored_andTheNewestPastOneAdopted() = runTest {
        hc.filtersByNotAfter = true
        restoreWeightMeasuredAt(farFuture)
        hc.records += listOf(
            HealthConnectWeight(weightKg = 61.8, time = t1),
            // A scale whose clock is a day ahead.
            HealthConnectWeight(weightKg = 80.0, time = now + Duration.ofDays(1)),
        )

        coordinator.sync(now = now)

        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertEquals(t1.toEpochMilli(), dao.row!!.weightMeasuredAtEpochMillis)
    }

    @Test
    fun restoredFutureWeightWithNoHcReading_keepsTheWeightButDropsItsDate() = runTest {
        hc.filtersByNotAfter = true
        restoreWeightMeasuredAt(farFuture)

        coordinator.sync(now = now)

        // Not held, since HC lacks it: the weight stays, but not beside a 2099 date.
        assertEquals(90.0, dao.row!!.weightOriginalValue!!, 0.0)
        assertNull(dao.row!!.weightMeasuredAtEpochMillis)
        assertEquals(ReadingProblem.NONE_FOUND, coordinator.readingProblem.value)
    }

    @Test
    fun clockSetBackPastAReadingHcThenLost_keepsTheWeightButDropsItsDate() = runTest {
        hc.filtersByNotAfter = true
        val reading = HealthConnectWeight(weightKg = 61.8, time = t1)
        hc.records += reading
        coordinator.sync(now = now)

        // Deleted from HC, with the clock set back before it and nothing older.
        hc.records -= reading
        coordinator.sync(now = t1 - Duration.ofDays(1))

        assertEquals(61.8, dao.row!!.weightOriginalValue!!, 0.0)
        assertNull(dao.row!!.weightMeasuredAtEpochMillis)
        assertEquals(ReadingProblem.NONE_FOUND, coordinator.readingProblem.value)
    }

    @Test
    fun invalidLatestReading_keepsARestoredFutureDateHcLacks() = runTest {
        hc.filtersByNotAfter = true
        restoreWeightMeasuredAt(farFuture)
        val restored = dao.row
        hc.records += HealthConnectWeight(weightKg = 0.0, time = t1)

        coordinator.sync(now = now)

        // As now, pending W3: an invalid reading keeps the weight and its date.
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

internal class FakeHealthConnectManager : HealthConnectManager {
    var available = true
    var access = true
    var providerUpdateRequired = false
    var accessError: Throwable? = null
    val records = mutableListOf<HealthConnectWeight>()
    var lastNotAfter: Instant? = null
    var reads = 0
    var readGate: CompletableDeferred<Unit>? = null
    var filtersByNotAfter = false // as the real read's before(notAfter) does

    override fun isAvailable(): Boolean = available

    override fun isProviderUpdateRequired(): Boolean = providerUpdateRequired

    override suspend fun hasWeightAccess(): Boolean = accessError?.let { throw it } ?: access

    override fun supportsReadHistory(): Boolean = false

    // Returns the latest record regardless of notAfter unless filtersByNotAfter,
    // so the coordinator's own future-timestamp guard is what the tests exercise.
    override suspend fun readLatestWeight(notAfter: Instant): HealthConnectWeight? {
        reads++
        lastNotAfter = notAfter
        readGate?.await()
        return records.filter { !filtersByNotAfter || it.time < notAfter }.maxByOrNull { it.time }
    }

    // As the real read's between(time, time + 1 ms): start inclusive, end exclusive.
    override suspend fun readWeightsAt(time: Instant): List<HealthConnectWeight> {
        reads++
        readGate?.await()
        return records.filter { it.time >= time && it.time < time.plusMillis(1) }
    }
}
