package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HomeDao
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.UserProfileDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HomeSnapshotRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val homeSnapshotStore: HomeSnapshotStore = mockk()

    @Test
    fun invalidateHomeSnapshot_clearsStoredSnapshot() = runTest {
        coEvery { homeSnapshotStore.clearSnapshot() } returns Unit
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        )

        repository.invalidateHomeSnapshot()

        coVerify(exactly = 1) { homeSnapshotStore.clearSnapshot() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun refreshHomeSnapshotAsync_swallowsNonCriticalRefreshFailure() = runTest {
        val failures = mutableListOf<Throwable>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            appScope = CoroutineScope(
                SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, throwable ->
                    failures += throwable
                }
            ),
            defaultDispatcher = dispatcher,
        )
        coEvery { homeSnapshotStore.readSnapshot() } throws IOException("read failed")

        repository.refreshHomeSnapshotAsync()
        advanceUntilIdle()

        assertTrue(failures.isEmpty())
    }

    @Test
    fun isSnapshotUsable_acceptsTenDayOldSnapshotWhenProjectionCoversCurrentChart() = runTest {
        val anchorDate = LocalDate.of(2026, 5, 6)
        val now = anchorDate.plusDays(10).atTime(23, 59)
        val zoneId = ZoneId.systemDefault()
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            appScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            defaultDispatcher = StandardTestDispatcher(testScheduler),
        )
        val snapshot = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = anchorDate.atTime(10, 15)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli(),
            anchorDateEpochDay = anchorDate.toEpochDay(),
            zoneId = zoneId.id,
            sourceFingerprint = "fingerprint",
            pkProjection = HomePkProjectionRecord(
                generatedAtEpochMillis = anchorDate.atTime(10, 15)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli(),
                windowStartEpochMillis = anchorDate.minusDays(3)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli(),
                windowEndEpochMillis = anchorDate.plusDays(14)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli(),
                sourceFingerprint = "fingerprint",
                payloadJson = "{}",
                latestEstradiolEntry = null,
            ),
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        )

        assertTrue(repository.isSnapshotUsable(snapshot = snapshot, now = now, zoneId = zoneId))
    }

    @Test
    fun refreshHomeSnapshotIfNeeded_capturesRowsForTenDaySnapshotValidity() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val anchorDate = now.toLocalDate()
        val zoneId = ZoneId.systemDefault()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val database: HrtTrackerDatabase = mockk()
        val homeDao: HomeDao = mockk()
        val userProfileDao: UserProfileDao = mockk()
        val writtenSnapshot = slot<HomeSnapshotRecord>()
        val scheduledStartIso = slot<String>()
        val scheduledEndIso = slot<String>()
        val manualStartEpochMillis = slot<Long>()
        val manualEndEpochMillis = slot<Long>()
        val expectedProjectionWindowEnd = anchorDate.plusDays(14)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        coEvery { homeSnapshotStore.readSnapshot() } returns null
        coEvery { homeSnapshotStore.writeSnapshot(capture(writtenSnapshot)) } returns Unit
        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.userProfileDao() } returns userProfileDao
        coEvery { homeDao.getActiveGroups() } returns emptyList()
        coEvery {
            homeDao.getScheduleEntries(
                capture(scheduledStartIso),
                capture(scheduledEndIso),
                capture(manualStartEpochMillis),
                capture(manualEndEpochMillis),
            )
        } returns emptyList()
        coEvery { homeDao.getLatestAntiandrogenEntriesOnOrBefore(any()) } returns emptyList()
        coEvery { homeDao.getEstradiolPkEntries(any(), any()) } returns emptyList()
        coEvery { homeDao.getLatestEstradiolEntryOnOrBefore(any()) } returns null
        coEvery { userProfileDao.getProfile() } returns null

        HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        ).refreshHomeSnapshotIfNeeded(now = now, force = true)

        assertTrue(writtenSnapshot.isCaptured)
        assertEquals(
            anchorDate.minusDays(1).atStartOfDay().toString(),
            scheduledStartIso.captured,
        )
        assertEquals(
            anchorDate.plusDays(100).atTime(23, 59, 59).toString(),
            scheduledEndIso.captured,
        )
        assertEquals(
            anchorDate.minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            manualStartEpochMillis.captured,
        )
        assertEquals(
            anchorDate.plusDays(11).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            manualEndEpochMillis.captured,
        )
        assertEquals(
            expectedProjectionWindowEnd,
            checkNotNull(writtenSnapshot.captured.pkProjection).windowEndEpochMillis,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun invalidateHomeSnapshot_waitsForInFlightSnapshotWriteBeforeClearing() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val database: HrtTrackerDatabase = mockk()
        val homeDao: HomeDao = mockk()
        val userProfileDao: UserProfileDao = mockk()
        val writeStarted = CompletableDeferred<Unit>()
        val allowWrite = CompletableDeferred<Unit>()

        coEvery { homeSnapshotStore.readSnapshot() } returns null
        coEvery { homeSnapshotStore.clearSnapshot() } returns Unit
        coEvery { homeSnapshotStore.writeSnapshot(any()) } coAnswers {
            writeStarted.complete(Unit)
            allowWrite.await()
        }
        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.userProfileDao() } returns userProfileDao
        coEvery { homeDao.getActiveGroups() } returns emptyList()
        coEvery { homeDao.getScheduleEntries(any(), any(), any(), any()) } returns emptyList()
        coEvery { homeDao.getLatestAntiandrogenEntriesOnOrBefore(any()) } returns emptyList()
        coEvery { homeDao.getEstradiolPkEntries(any(), any()) } returns emptyList()
        coEvery { homeDao.getLatestEstradiolEntryOnOrBefore(any()) } returns null
        coEvery { userProfileDao.getProfile() } returns null
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        )

        val refreshJob = launch {
            repository.refreshHomeSnapshotIfNeeded(now = now, force = true)
        }
        advanceUntilIdle()
        writeStarted.await()

        val invalidateJob = launch {
            repository.invalidateHomeSnapshot()
        }
        advanceUntilIdle()

        assertEquals(false, invalidateJob.isCompleted)

        allowWrite.complete(Unit)
        refreshJob.join()
        invalidateJob.join()

        coVerify(exactly = 1) { homeSnapshotStore.writeSnapshot(any()) }
        coVerify(exactly = 1) { homeSnapshotStore.clearSnapshot() }
    }
}
