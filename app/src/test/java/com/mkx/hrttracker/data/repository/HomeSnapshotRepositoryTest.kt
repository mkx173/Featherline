package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HomeDao
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.data.local.UserProfileDao
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class HomeSnapshotRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val homeSnapshotStore: HomeSnapshotStore = mockk()
    private val homeSnapshotGenerationStore: HomeSnapshotGenerationStore = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private val generationState = MutableStateFlow(0L)
    private val chartWindowOptionState = MutableStateFlow(HomeE2ChartWindowOption.SEVEN_DAYS)
    private val settingsState = MutableStateFlow(SettingsState())

    @Before
    fun setUp() {
        generationState.value = 0L
        chartWindowOptionState.value = HomeE2ChartWindowOption.SEVEN_DAYS
        every { homeSnapshotGenerationStore.observeGeneration() } returns generationState
        coEvery { homeSnapshotGenerationStore.readGeneration() } coAnswers {
            generationState.value
        }
        coEvery { homeSnapshotGenerationStore.incrementGeneration() } coAnswers {
            val nextGeneration = generationState.value + 1L
            generationState.value = nextGeneration
            nextGeneration
        }
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns chartWindowOptionState
        every { settingsRepository.settingsState } returns settingsState
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
    }

    @Test
    fun decodeProjection_returnsNullWhenProjectionExpired() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            settingsRepository = settingsRepository,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        )
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 5, 7, 9, 0)
        // Expiry was set at the previous planned slot (May 7 at 08:00).
        val expiredAt = LocalDateTime.of(2026, 5, 7, 8, 0)
            .atZone(zoneId).toInstant().toEpochMilli()
        val record = HomePkProjectionRecord(
            generatedAtEpochMillis = 0L,
            windowStartEpochMillis = 0L,
            windowEndEpochMillis = Long.MAX_VALUE,
            pkProjectionExpiresAtEpochMillis = expiredAt,
            concentrationUnit = PkConcentrationUnit.PG_PER_ML.name,
            timeH = emptyList(),
            concentrations = emptyList(),
            doseMarkers = emptyList(),
            latestEstradiolEntry = null,
            chartWindowHours = 168,
            densePolicy = HomePkDenseSamplePolicyRecord.Interval(hours = 0.1),
            includesPostDoseOffsets = false,
        )

        assertNull(repository.decodeProjection(record, now, zoneId))
        assertTrue(repository.isPkProjectionExpired(record, now, zoneId))
    }

    @Test
    fun decodeProjection_returnsResultWhenProjectionStillValid() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            settingsRepository = settingsRepository,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        )
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 5, 7, 7, 0)
        // Expiry is one hour in the future.
        val expiresAt = LocalDateTime.of(2026, 5, 7, 8, 0)
            .atZone(zoneId).toInstant().toEpochMilli()
        val record = HomePkProjectionRecord(
            generatedAtEpochMillis = 0L,
            windowStartEpochMillis = 0L,
            windowEndEpochMillis = Long.MAX_VALUE,
            pkProjectionExpiresAtEpochMillis = expiresAt,
            concentrationUnit = PkConcentrationUnit.PG_PER_ML.name,
            timeH = emptyList(),
            concentrations = emptyList(),
            doseMarkers = emptyList(),
            latestEstradiolEntry = null,
            chartWindowHours = 168,
            densePolicy = HomePkDenseSamplePolicyRecord.Interval(hours = 0.1),
            includesPostDoseOffsets = false,
        )

        assertNotNull(repository.decodeProjection(record, now, zoneId))
        assertFalse(repository.isPkProjectionExpired(record, now, zoneId))
    }

    @Test
    fun invalidateHomeSnapshot_clearsStoredSnapshot() = runTest {
        coEvery { homeSnapshotStore.clearSnapshot() } returns Unit
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            settingsRepository = settingsRepository,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        )

        repository.invalidateHomeSnapshot()

        coVerify(exactly = 1) { homeSnapshotStore.clearSnapshot() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun runHomeDataMutation_rebuildContainsUpdatedDisplayNameForEveryGroupSlot() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val database: HrtTrackerDatabase = mockk()
        val homeDao: HomeDao = mockk()
        val medicineDao: MedicineDao = mockk()
        val userProfileDao: UserProfileDao = mockk()
        val writtenSnapshot = slot<HomeSnapshotRecord>()
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
        var displayName: String? = null

        coEvery { homeSnapshotStore.readSnapshot() } returns null
        coEvery { homeSnapshotStore.clearSnapshot() } returns Unit
        coEvery { homeSnapshotStore.writeSnapshot(capture(writtenSnapshot)) } returns Unit
        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns medicineDao
        every { database.userProfileDao() } returns userProfileDao
        coEvery { homeDao.getActiveGroups() } returns listOf(
            groupWithTwoSlotsReferencing(medicineUuid)
        )
        coEvery { homeDao.getScheduleEntries(any(), any(), any(), any()) } returns emptyList()
        coEvery { homeDao.getLatestAntiandrogenEntriesOnOrBefore(any()) } returns emptyList()
        coEvery { homeDao.getEstradiolPkEntries(any(), any()) } returns emptyList()
        coEvery { homeDao.getLatestEstradiolEntryOnOrBefore(any()) } returns null
        coEvery { userProfileDao.getProfile() } returns null
        coEvery { medicineDao.getByUuids(listOf(medicineUuid.toString())) } coAnswers {
            listOf(medicineEntity(uuid = medicineUuid, displayName = displayName))
        }
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            settingsRepository = settingsRepository,
            appScope = CoroutineScope(dispatcher + SupervisorJob()),
            defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        repository.runHomeDataMutation {
            displayName = "Renamed"
        }

        assertTrue(writtenSnapshot.isCaptured)
        assertEquals(
            listOf("Renamed", "Renamed"),
            writtenSnapshot.captured.activeGroups
                .single()
                .medications
                .map { medication -> medication.medicine?.displayName },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun runHomeDataMutation_runsCleanupAndRefreshEvenWhenBlockThrows() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = mutableListOf<String>()
        coEvery { homeSnapshotStore.clearSnapshot() } coAnswers {
            events += "clear"
            Unit
        }
        coEvery { homeSnapshotStore.readSnapshot() } coAnswers {
            events += "refresh_read"
            null
        }
        // Refresh path will hit the database; minimal stubs so it returns quickly.
        val database: HrtTrackerDatabase = mockk()
        val homeDao: HomeDao = mockk()
        val userProfileDao: UserProfileDao = mockk()
        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.userProfileDao() } returns userProfileDao
        coEvery { homeDao.getActiveGroups() } returns emptyList()
        coEvery { homeDao.getScheduleEntries(any(), any(), any(), any()) } returns emptyList()
        coEvery { homeDao.getLatestAntiandrogenEntriesOnOrBefore(any()) } returns emptyList()
        coEvery { homeDao.getEstradiolPkEntries(any(), any()) } returns emptyList()
        coEvery { homeDao.getLatestEstradiolEntryOnOrBefore(any()) } returns null
        coEvery { userProfileDao.getProfile() } returns null
        coEvery { homeSnapshotStore.writeSnapshot(any()) } returns Unit
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            settingsRepository = settingsRepository,
            appScope = CoroutineScope(dispatcher + SupervisorJob()),
            defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        var caught: Throwable? = null
        try {
            repository.runHomeDataMutation {
                events += "throw"
                throw IllegalStateException("simulated mutation failure")
            }
        } catch (t: IllegalStateException) {
            caught = t
        }
        advanceUntilIdle()

        // Failure must propagate (we don't silently swallow), AND cleanup must have run.
        assertEquals("simulated mutation failure", caught.message)
        assertTrue("clearSnapshot should run on the throwing path", events.contains("clear"))
        coVerify(atLeast = 1) { homeSnapshotStore.clearSnapshot() }
        // The async refresh should have been enqueued; readSnapshot is the first
        // action of the refresh worker.
        assertTrue("refresh should be enqueued", events.contains("refresh_read"))
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
            settingsRepository = settingsRepository,
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
    fun runHomeDataMutation_awaitsRefreshAfterMutationCommit() = runTest {
        val appDispatcher = StandardTestDispatcher()
        val database: HrtTrackerDatabase = mockk()
        val homeDao: HomeDao = mockk()
        val userProfileDao: UserProfileDao = mockk()
        val mutationCommitted = CompletableDeferred<Unit>()

        coEvery { homeSnapshotStore.readSnapshot() } returns null
        coEvery { homeSnapshotStore.clearSnapshot() } returns Unit
        coEvery { homeSnapshotStore.writeSnapshot(any()) } coAnswers {
            assertTrue(mutationCommitted.isCompleted)
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
            settingsRepository = settingsRepository,
            appScope = CoroutineScope(appDispatcher),
            defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        repository.runHomeDataMutation {
            mutationCommitted.complete(Unit)
        }

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
            settingsRepository = settingsRepository,
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
            settingsRepository = settingsRepository,
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
            settingsRepository = settingsRepository,
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
                        "actual=${HOME_SNAPSHOT_SCHEMA_VERSION - 1}" in message
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
            settingsRepository = settingsRepository,
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
            settingsRepository = settingsRepository,
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
                pkProjectionExpiresAtEpochMillis = anchorDate.plusDays(14)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli(),
                concentrationUnit = PkConcentrationUnit.PG_PER_ML.name,
                timeH = emptyList(),
                concentrations = emptyList(),
                doseMarkers = emptyList(),
                latestEstradiolEntry = null,
                chartWindowHours = 168,
                densePolicy = HomePkDenseSamplePolicyRecord.Interval(hours = 0.1),
                includesPostDoseOffsets = false,
            ),
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        )

        assertTrue(
            repository.isSnapshotUsable(
                snapshot = snapshot,
                now = now,
                zoneId = zoneId,
                option = HomeE2ChartWindowOption.SEVEN_DAYS,
            )
        )
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
            settingsRepository = settingsRepository,
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
            settingsRepository = settingsRepository,
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
                pkProjectionExpiresAtEpochMillis = now.toLocalDate()
                    .plusDays(14)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli(),
                concentrationUnit = PkConcentrationUnit.PG_PER_ML.name,
                timeH = emptyList(),
                concentrations = emptyList(),
                doseMarkers = emptyList(),
                latestEstradiolEntry = null,
                chartWindowHours = 168,
                densePolicy = HomePkDenseSamplePolicyRecord.Interval(hours = 0.1),
                includesPostDoseOffsets = false,
            ),
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        )
    }

    private fun groupWithTwoSlotsReferencing(medicineUuid: UUID): MedicationGroupWithItemsEntity {
        val groupUuid = UUID.fromString("44bff3cb-b4dd-4be4-9eda-442cf91185c1").toString()
        return MedicationGroupWithItemsEntity(
            group = MedicationGroupEntity(
                uuid = groupUuid,
                name = "Home estradiol",
                colorKey = MedicationGroupColorKey.PLUM.name,
                scheduleType = MedicationGroupScheduleType.DAILY.name,
                scheduleInterval = 1,
                scheduleSinceEpochDay = LocalDate.of(2026, 5, 1).toEpochDay(),
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
            ),
            items = listOf(
                UUID.fromString("79dfe41c-b684-4e48-858d-d47d369b5b30"),
                UUID.fromString("79dfe41c-b684-4e48-858d-d47d369b5b31"),
            ).mapIndexed { slotIndex, slotUuid ->
                MedicationGroupItemEntity(
                    uuid = slotUuid.toString(),
                    groupUuid = groupUuid,
                    sortOrder = slotIndex,
                    count = 1,
                    medicineUuid = medicineUuid.toString(),
                    applicationType = MedicationApplicationType.ORAL.name,
                    doseInstructionKind = DoseInstructionKind.WHOLE_UNIT.name,
                    tabletFractionNumerator = null,
                    tabletFractionDenominator = null,
                    doseVolumeMl = null,
                    doseWeightGrams = null,
                )
            },
            scheduleTimes = listOf(
                MedicationGroupScheduleTimeEntity(
                    uuid = UUID.fromString("52d9acc8-11be-43ff-b6cb-035571ec0371").toString(),
                    groupUuid = groupUuid,
                    sortOrder = 0,
                    hourOfDay = 8,
                    minuteOfHour = 0,
                    effectiveFromLocalIso = LocalDate.of(2026, 5, 1).atStartOfDay().toString(),
                )
            ),
            weeklyDays = emptyList(),
        )
    }

    private fun medicineEntity(
        uuid: UUID,
        displayName: String?,
    ): MedicineEntity {
        return MedicineEntity(
            uuid = uuid.toString(),
            selectionKind = "CATALOG",
            medicationKey = MedicationKey.ESTRADIOL.name,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = MedicationCategory.ESTRADIOL.name,
            preparationType = "PILL",
            strengthMgPerTablet = 2.0,
            strengthMgPerVial = null,
            concentrationMgPerMl = null,
            vialVolumeMl = null,
            concentrationPercent = null,
            sachetWeightGrams = null,
            containerWeightGrams = null,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
            displayName = displayName,
            identityKey = "C|ESTRADIOL|PILL|strengthMgPerTablet=2",
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 500L,
            archivedAtEpochMillis = null,
        )
    }
}
