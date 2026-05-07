package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HomeDao
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.UserProfileDao
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HomeSnapshotRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val homeSnapshotStore: HomeSnapshotStore = mockk()
    private val homeSnapshotGenerationStore: HomeSnapshotGenerationStore = mockk()
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private val generationState = MutableStateFlow(0L)

    @Before
    fun setUp() {
        generationState.value = 0L
        every { homeSnapshotGenerationStore.observeGeneration() } returns generationState
        coEvery { homeSnapshotGenerationStore.readGeneration() } coAnswers {
            generationState.value
        }
        coEvery { homeSnapshotGenerationStore.incrementGeneration() } coAnswers {
            val nextGeneration = generationState.value + 1L
            generationState.value = nextGeneration
            nextGeneration
        }
    }

    @Test
    fun invalidateHomeSnapshot_clearsStoredSnapshot() = runTest {
        coEvery { homeSnapshotStore.clearSnapshot() } returns Unit
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        )

        repository.invalidateHomeSnapshot()

        coVerify(exactly = 1) { homeSnapshotStore.clearSnapshot() }
    }

    @Test
    fun runHomeDataMutation_doesNotBlockWriteWhenClearSnapshotFails() = runTest {
        val dispatcher = StandardTestDispatcher()
        val events = mutableListOf<String>()
        coEvery { homeSnapshotStore.clearSnapshot() } coAnswers {
            events += "clear"
            throw IOException("clear failed")
        }
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        )

        val result = repository.runHomeDataMutation {
            events += "write"
            "saved"
        }

        assertEquals("saved", result)
        assertEquals(listOf("write", "clear"), events)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun runHomeDataMutation_delaysRefreshUntilAfterMutationCommit() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val appDispatcher = StandardTestDispatcher()
        val database: HrtTrackerDatabase = mockk()
        val homeDao: HomeDao = mockk()
        val userProfileDao: UserProfileDao = mockk()
        val writeStarted = CompletableDeferred<Unit>()
        val mutationCommitted = CompletableDeferred<Unit>()
        lateinit var refreshJob: kotlinx.coroutines.Job

        coEvery { homeSnapshotStore.readSnapshot() } returns null
        coEvery { homeSnapshotStore.clearSnapshot() } returns Unit
        coEvery { homeSnapshotStore.writeSnapshot(any()) } coAnswers {
            assertTrue(mutationCommitted.isCompleted)
            writeStarted.complete(Unit)
            Unit
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
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            appScope = CoroutineScope(appDispatcher),
            defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        repository.runHomeDataMutation {
            refreshJob = launch {
                repository.refreshHomeSnapshotIfNeeded(now = now, force = true)
            }
            assertNull(withTimeoutOrNull(250) { writeStarted.await() })
            mutationCommitted.complete(Unit)
        }
        refreshJob.join()

        coVerify(exactly = 1) { homeSnapshotStore.writeSnapshot(any()) }
    }

    @Test
    fun observeHomeSnapshot_rejectsStoredSnapshotOlderThanDurableGeneration() = runTest {
        val snapshot = homeSnapshotRecord(
            generation = 1L,
            now = LocalDateTime.of(2026, 5, 6, 10, 15),
        )
        generationState.value = 2L
        every { homeSnapshotStore.observeSnapshot() } returns MutableStateFlow(snapshot)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        )

        assertNull(repository.observeHomeSnapshot().first())
    }

    @Test
    fun readUsableHomeSnapshot_rejectsStoredSnapshotOlderThanDurableGeneration() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        coEvery { homeSnapshotStore.readSnapshot() } returns homeSnapshotRecord(
            generation = 1L,
            now = now,
        )
        generationState.value = 2L
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        )

        assertNull(repository.readUsableHomeSnapshot(now = now))
    }

    @Test
    fun readUsableHomeSnapshot_logsVerificationRejectionReason() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        coEvery { homeSnapshotStore.readSnapshot() } returns homeSnapshotRecord(
            generation = 1L,
            now = now,
        ).copy(schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION - 1)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
            diagnosticsLogger = diagnosticsLogger,
        )

        assertNull(repository.readUsableHomeSnapshot(now = now))

        verify {
            diagnosticsLogger.info(
                "HomeSnapshotRepository",
                match { message ->
                    "home_snapshot_read_rejected" in message &&
                        "reason=schema_version" in message &&
                        "actual=2" in message
                }
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun refreshHomeSnapshotAsync_swallowsNonCriticalRefreshFailure() = runTest {
        val failures = mutableListOf<Throwable>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
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
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            appScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            defaultDispatcher = StandardTestDispatcher(testScheduler),
        )
        val snapshot = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = 0L,
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
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
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
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
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

    private fun homeSnapshotRecord(
        generation: Long,
        now: LocalDateTime,
    ): HomeSnapshotRecord {
        val zoneId = ZoneId.systemDefault()
        return HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = generation,
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = zoneId.id,
            sourceFingerprint = "fingerprint",
            pkProjection = HomePkProjectionRecord(
                generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
                windowStartEpochMillis = now.toLocalDate()
                    .minusDays(3)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli(),
                windowEndEpochMillis = now.toLocalDate()
                    .plusDays(14)
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
    }
}
