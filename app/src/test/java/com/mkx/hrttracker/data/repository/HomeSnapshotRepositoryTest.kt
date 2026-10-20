package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.BloodTestDao
import com.mkx.hrttracker.data.local.BloodTestPanelEntity
import com.mkx.hrttracker.data.local.BloodTestPanelWithResultsEntity
import com.mkx.hrttracker.data.local.BloodTestResultEntity
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.E2CalibrationMetadataEntity
import com.mkx.hrttracker.data.local.HomeDao
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.JournalDao
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.data.local.MedicationLogDao
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.data.local.PkCalibrationDao
import com.mkx.hrttracker.data.local.TrackedDateEntity
import com.mkx.hrttracker.data.local.UserProfileDao
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationEvaluation
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import com.mkx.hrttracker.model.pk.resultIdsWith
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.MedicationDayBoundary
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Collections
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSnapshotRepositoryTest {
    private val now = LocalDateTime.of(2026, 5, 6, 10, 15)
    private val zoneId = ZoneId.systemDefault()

    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val homeDao: HomeDao = mockk()
    private val medicineDao: MedicineDao = mockk()
    private val medicationLogDao: MedicationLogDao = mockk()
    private val userProfileDao: UserProfileDao = mockk()
    private val journalDao: JournalDao = mockk()
    private val homeSnapshotStore: HomeSnapshotStore = mockk()
    private val homeSnapshotGenerationStore: HomeSnapshotGenerationStore = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private val e2EstimateRepository: E2EstimateRepository = mockk(relaxed = true)
    private val generationState = MutableStateFlow(0L)
    private val chartWindowOptionState = MutableStateFlow(HomeE2ChartWindowOption.SEVEN_DAYS)

    // The two files: what the load reads, valid at generation 0 unless a test
    // says otherwise, so that no run starts by itself; and each write.
    @Volatile
    private var storedSnapshot: HomeSnapshotRecord? = homeData(generation = 0L)

    @Volatile
    private var storedInputs: E2InputsRecord? = e2Inputs(generation = 0L)
    private val writtenSnapshots: MutableList<HomeSnapshotRecord> = Collections.synchronizedList(mutableListOf())
    private val writtenInputs: MutableList<E2InputsRecord> = Collections.synchronizedList(mutableListOf())

    // Room as the runs read it.
    @Volatile
    private var room = RoomData()

    /** Runs that read Room, counted at their first read. */
    private val roomReads = AtomicInteger()
    private val nextReadPause = AtomicReference<Pause?>(null)

    @Before
    fun setUp() {
        coEvery { homeSnapshotGenerationStore.readGeneration() } answers { generationState.value }
        coEvery { homeSnapshotGenerationStore.incrementGeneration() } answers {
            (generationState.value + 1L).also { next -> generationState.value = next }
        }
        coEvery { homeSnapshotStore.readSnapshot() } answers { storedSnapshot }
        coEvery { homeSnapshotStore.readE2Inputs() } answers { storedInputs }
        coEvery { homeSnapshotStore.writeSnapshot(any()) } answers {
            storedSnapshot = firstArg()
            writtenSnapshots += firstArg<HomeSnapshotRecord>()
        }
        coEvery { homeSnapshotStore.writeE2Inputs(any()) } answers {
            storedInputs = firstArg()
            writtenInputs += firstArg<E2InputsRecord>()
        }
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns chartWindowOptionState
        every { settingsRepository.settingsState } returns MutableStateFlow(SettingsState())
        coEvery { databaseHolder.awaitOpen() } returns database
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns medicineDao
        every { database.medicationLogDao() } returns medicationLogDao
        every { database.userProfileDao() } returns userProfileDao
        every { database.journalDao() } returns journalDao
        every { database.bloodTestDao() } returns mockk<BloodTestDao> {
            coEvery { getPanels() } answers { room.panels }
        }
        every { database.pkCalibrationDao() } returns mockk<PkCalibrationDao> {
            coEvery { getAllMetadata() } answers { room.metadata }
        }
        coEvery { homeDao.getActiveGroups() } coAnswers {
            roomReads.incrementAndGet()
            nextReadPause.getAndSet(null)?.let { pause ->
                pause.reached.complete(Unit)
                pause.release.await()
            }
            room.activeGroups
        }
        coEvery { homeDao.getArchivedGroups() } returns emptyList()
        coEvery { homeDao.getScheduleEntries(any(), any(), any(), any()) } returns emptyList()
        coEvery { homeDao.getLatestAntiandrogenEntriesOnOrBefore(any()) } answers { room.antiandrogens }
        coEvery { homeDao.getEstradiolPkEntries(any(), any()) } answers {
            val bounds = firstArg<Long>()..secondArg<Long>()
            room.pkReads += bounds
            room.doses
                .filter { dose -> dose.appliedAtEpochMillis in bounds }
                .sortedByDescending { dose -> dose.appliedAtEpochMillis }
        }
        coEvery { homeDao.getLatestEstradiolEntryOnOrBefore(any()) } answers {
            room.doses
                .filter { dose -> dose.appliedAtEpochMillis <= firstArg<Long>() }
                .maxByOrNull { dose -> dose.appliedAtEpochMillis }
        }
        coEvery { medicineDao.getAllActiveTrackedEntities() } answers { room.medicines }
        coEvery { medicineDao.getByUuids(any()) } answers { room.medicines }
        coEvery { medicationLogDao.getScheduledEntriesInWindow(any(), any()) } returns emptyList()
        coEvery { userProfileDao.getProfile() } returns null
        coEvery { journalDao.getFirstPinnedTrackedDate() } returns null
    }

    // ── The gate ─────────────────────────────────────────────────────────────

    @Test
    fun aMutationsRun_publishesWhatItCommitted_inEveryGroupSlot_bothFilesStampedWithItsGeneration() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
        room.activeGroups = listOf(groupWithTwoSlotsReferencing(medicineUuid))
        room.medicines = listOf(medicineEntity(uuid = medicineUuid, displayName = null))
        val repository = repository()

        repository.runHomeDataMutation {
            room.medicines = listOf(medicineEntity(uuid = medicineUuid, displayName = "Renamed"))
        }
        assertEquals(1L, repository.generation.value)
        repository.rebuildNow()

        val state = checkNotNull(repository.cache.value)
        assertEquals(1L, state.generation)
        assertEquals(
            listOf("Renamed", "Renamed"),
            state.homeData?.activeGroups?.single()?.medications?.map { medication -> medication.medicine?.displayName },
        )
        assertEquals(listOf(1L), writtenSnapshots.map { record -> record.generation })
        assertEquals(listOf(1L), writtenInputs.map { record -> record.generation })
    }

    @Test
    fun theRun_readsRoomOnlyOnceTheMutationHasCommitted() = runTest {
        val events = Collections.synchronizedList(mutableListOf<String>())
        coEvery { homeDao.getActiveGroups() } answers {
            events += "read"
            emptyList()
        }
        val repository = repository()

        repository.runHomeDataMutation { events += "commit" }
        // The caller is back before the run, which waits for nothing of its own.
        assertEquals(listOf("commit"), events.toList())
        repository.currentCache.first { state -> state != null }

        assertEquals(listOf("commit", "read"), events.toList())
    }

    @Test
    fun aMutationThatThrows_stillBumpsTheGeneration_andAsksForARun() = runTest {
        val repository = repository()

        val failure = runCatching {
            repository.runHomeDataMutation { throw IllegalStateException("simulated mutation failure") }
        }.exceptionOrNull()

        // The failure propagates, and the cache is no longer current until a
        // run that only this mutation asked for publishes.
        assertEquals("simulated mutation failure", failure?.message)
        assertEquals(1L, repository.generation.value)
        assertNull(repository.currentState())
        assertEquals(1L, repository.currentCache.filterNotNull().first().generation)
    }

    @Test
    fun aFailedBump_runsNoBlock_andAsksForNothing() = runTest {
        coEvery { homeSnapshotGenerationStore.incrementGeneration() } throws IOException("bump failed")
        val repository = repository()
        var blockRan = false

        val failure = runCatching { repository.runHomeDataMutation { blockRan = true } }.exceptionOrNull()
        settle()

        assertTrue(failure is IOException)
        assertFalse(blockRan)
        assertEquals(0L, repository.generation.value)
        // Still current: there is nothing to rebuild.
        assertNotNull(repository.currentState())
        assertEquals(0, roomReads.get())
    }

    @Test
    fun invalidate_bumpsTheGeneration_andAsksForARun() = runTest {
        val repository = repository()

        repository.invalidate()

        assertEquals(1L, repository.generation.value)
        assertEquals(1L, repository.currentCache.filterNotNull().first().generation)
        assertEquals(1, roomReads.get())
    }

    @Test
    fun currentState_isNone_fromAMutationUntilItsRunPublishes_whileTheOldStateStaysHeld() = runTest {
        val repository = repository()
        assertEquals(0L, repository.currentState()?.generation)
        val pause = pauseNextRead()

        repository.runHomeDataMutation {}
        pause.reached.await()

        assertNull(repository.currentState())
        assertEquals(0L, repository.cache.value?.homeData?.generation)
        pause.release.complete(Unit)
        repository.currentCache.first { state -> state != null }
        assertEquals(1L, repository.currentState()?.homeData?.generation)
    }

    // ── The load and each part's validity ────────────────────────────────────

    @Test
    fun eachPart_hasOneValidityRule_homeDataWithItsSchema_theInputsWithTheirEngine() {
        val today = now.toLocalDate()
        val otherZone = ZoneId.of(if (zoneId.id == "UTC") "Asia/Tokyo" else "UTC")
        val homeData = homeData(generation = 3L)
        val inputs = e2Inputs(generation = 3L)
        val rules = listOf<(Long, LocalDate, ZoneId) -> String?>(
            { generation, day, zone -> homeData.validityFailure(generation, day, zone) },
            { generation, day, zone -> inputs.validityFailure(generation, day, zone) },
        )
        for (failure in rules) {
            assertNull(failure(3L, today, zoneId))
            assertNull(failure(3L, today.plusDays(10), zoneId))
            assertNotNull(failure(3L, today.plusDays(11), zoneId))
            assertNotNull(failure(3L, today.minusDays(1), zoneId))
            assertNotNull(failure(3L, today, otherZone))
            // Behind the durable generation, or ahead of a reset one.
            assertNotNull(failure(4L, today, zoneId))
            assertNotNull(failure(2L, today, zoneId))
        }
        // No PK and no chart option in Home data's rule; no schema in the inputs'.
        assertNull(homeData.validityFailure(3L, today, zoneId))
        assertNotNull(homeData.copy(schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION - 1).validityFailure(3L, today, zoneId))
        assertNull(inputs.copy(option = HomeE2ChartWindowOption.THIRTY_DAYS).validityFailure(3L, today, zoneId))
        assertNotNull(inputs.copy(engineVersion = PK_ENGINE_VERSION + 1).validityFailure(3L, today, zoneId))
    }

    @Test
    fun theLoad_seedsEachPartOnlyWhenItsOwnFileIsValid_andAsksForNoRunWhenBothAre() = runTest {
        val repository = repository()

        val loaded = repository.cache.filterNotNull().first()
        settle()

        assertEquals(storedSnapshot, loaded.homeData)
        assertEquals(storedInputs, loaded.e2Inputs)
        assertEquals(0, roomReads.get())
    }

    @Test
    fun anEngineMismatch_dropsOnlyTheE2Inputs_andTheStartupCheckAsksForARun() = runTest {
        // An app update with a PK refit: Home's lists still paint from the
        // file, and only the inputs, and their fit, wait for a run.
        storedInputs = e2Inputs(generation = 0L).copy(engineVersion = PK_ENGINE_VERSION + 1)
        val pause = pauseNextRead()
        val repository = repository()

        val loaded = repository.cache.filterNotNull().first()
        assertNotNull(loaded.homeData)
        assertNull(loaded.e2Inputs)
        assertSame(loaded.homeData, repository.currentState()?.homeData)
        pause.reached.await()
        pause.release.complete(Unit)
        val published = checkNotNull(repository.cache.first { state -> state?.e2Inputs != null })

        assertEquals(PK_ENGINE_VERSION, published.e2Inputs?.engineVersion)
        assertNotNull(published.homeData)
        assertEquals(listOf(PK_ENGINE_VERSION), writtenInputs.map { record -> record.engineVersion })
    }

    @Test
    fun partsBehindTheDurableGeneration_asAfterAProcessDeathMidMutation_areMissing_andRebuiltAtStartup() = runTest {
        generationState.value = 1L
        val repository = repository()

        val loaded = repository.cache.filterNotNull().first()
        assertEquals(1L, loaded.generation)
        assertNull(loaded.homeData)
        assertNull(loaded.e2Inputs)
        assertNull(repository.currentState()?.homeData)
        val published = checkNotNull(repository.cache.first { state -> state?.homeData != null })

        assertEquals(1L, published.generation)
        assertEquals(1L, published.e2Inputs?.generation)
    }

    @Test
    fun aCodec28Snapshot_withNoInputsFile_readsAsMissing_andTheStartupCheckRebuildsOnce() = runTest {
        // The first launch after the update: the old file can't be read as
        // codec 29, and there is no inputs file yet.
        val serializer = EncryptedRecordSerializer(
            crypto = object : HomeSnapshotCrypto {
                override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
                override fun decrypt(ciphertext: ByteArray): ByteArray = ciphertext
            },
            name = "home_snapshot",
            encode = HomeSnapshotCodec::encode,
            decode = HomeSnapshotCodec::decode,
        )
        val codec28 = HomeSnapshotCodec.encode(homeData(generation = 0L)).also { bytes ->
            ByteBuffer.wrap(bytes).putInt(0, 28)
        }
        coEvery { homeSnapshotStore.readSnapshot() } coAnswers {
            serializer.readFrom(ByteArrayInputStream(codec28)).record
        }
        storedInputs = null
        val repository = repository()

        val loaded = repository.cache.filterNotNull().first()
        assertNull(loaded.homeData)
        assertNull(loaded.e2Inputs)
        // Home's first frame comes from Room meanwhile.
        assertNull(repository.currentState()?.homeData)
        repository.cache.first { state -> state?.homeData != null }
        settle()

        assertEquals(1, roomReads.get())
        assertEquals(1, writtenSnapshots.size)
        assertEquals(1, writtenInputs.size)
    }

    @Test
    fun aMissingPart_isRebuiltByOneRun_whileTheObservationsStartingDuringItAddNone() = runTest {
        storedInputs = null
        lateinit var estimates: E2EstimateRepository
        val pause = pauseNextRead()
        val repository = repository(estimates = { estimates })
        estimates = estimates(repository)
        pause.reached.await()

        // Home and its routes sheet open while the startup run reads.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            estimates.observeHome(MutableStateFlow(now)).collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            estimates.observeCalibration(MutableStateFlow(now)).collect {}
        }
        runCurrent()
        pause.release.complete(Unit)
        repository.currentCache.first { state -> state?.e2Inputs != null }
        settle()

        assertEquals(1, roomReads.get())
        assertEquals(1, writtenSnapshots.size)
        assertEquals(1, writtenInputs.size)
    }

    @Test
    fun aLoadReadThatThrows_readsAsMissing_andOneRunRebuildsIt() = runTest {
        coEvery { homeSnapshotStore.readSnapshot() } throws IOException("read failed")
        val repository = repository()

        val loaded = repository.cache.filterNotNull().first()
        assertNull(loaded.homeData)
        assertNotNull(loaded.e2Inputs)
        repository.cache.first { state -> state?.homeData != null }
        settle()

        assertEquals(1, roomReads.get())
        assertEquals(1, writtenSnapshots.size)
        assertEquals(1, writtenInputs.size)
    }

    @Test
    fun aDateOrZoneChange_leavesTheCacheToTheBroadcastsRun_whichPublishesForTheNewDayAndZone() = runTest {
        // Nothing checks the date or zone in memory: the date and zone
        // broadcasts' receivers ask for the run, as rebuildNow() does here.
        val originalTimeZone = TimeZone.getDefault()
        val otherZone = ZoneId.of(if (zoneId.id == "Asia/Tokyo") "UTC" else "Asia/Tokyo")
        val timeSource = FakeAppTimeSource(now)
        try {
            val repository = repository(timeSource = timeSource)
            val loaded = repository.cache.filterNotNull().first()

            timeSource.setCurrentMinute(now.toLocalDate().plusDays(1).atStartOfDay())
            TimeZone.setDefault(TimeZone.getTimeZone(otherZone))
            timeSource.setCurrentZone(otherZone)
            settle()
            assertSame(loaded, repository.cache.value)
            assertEquals(0, roomReads.get())
            repository.rebuildNow()

            val published = checkNotNull(repository.cache.value)
            assertEquals(otherZone.id, published.homeData?.zoneId)
            assertEquals(otherZone.id, published.e2Inputs?.zoneId)
            assertEquals(now.toLocalDate().plusDays(1).toEpochDay(), published.homeData?.anchorDateEpochDay)
            assertEquals(0L, published.generation)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    // ── The worker ───────────────────────────────────────────────────────────

    @Test
    fun nRequests_causeAtMostTwoRuns() = runTest {
        // The two receivers behind one broadcast, a group editor's gated
        // calls behind one Save, a restore and its journal follow-up.
        val repository = repository()
        val pause = pauseNextRead()
        repository.requestRebuild()
        pause.reached.await()

        repeat(5) { repository.requestRebuild() }
        repository.runHomeDataMutation {}
        repository.runHomeDataMutation {}
        repository.invalidate()
        pause.release.complete(Unit)
        repository.rebuildNow()
        settle()

        assertEquals(2, roomReads.get())
        assertEquals(3L, repository.cache.value?.generation)
        assertEquals(listOf(3L), writtenSnapshots.map { record -> record.generation })
    }

    @Test
    fun aMutationDuringTheRead_discardsTheRun_andTheNextRunPublishesTheNewGeneration() = runTest {
        val repository = repository()
        val published = publishes(repository)
        val pause = pauseNextRead()
        repository.requestRebuild()
        pause.reached.await()

        repository.runHomeDataMutation {}
        pause.release.complete(Unit)
        repository.rebuildNow()
        settle()

        assertEquals(2, roomReads.get())
        assertEquals(listOf(0L, 1L), published.map { state -> state.generation })
        published.assertBothPartsAtOneGeneration()
        assertEquals(listOf(1L), writtenSnapshots.map { record -> record.generation })
        assertEquals(listOf(1L), writtenInputs.map { record -> record.generation })
    }

    @Test
    fun aMutationJustBeforePublication_leavesNothingStampedWithTheOldGeneration() = runTest {
        // The run waits for the gate's lock to publish while a mutation holds
        // it; the mutation commits first, so the check under the lock fails.
        val repository = repository()
        val published = publishes(repository)
        val solved = CompletableDeferred<Unit>()
        mockkObject(PkCalibrationEngine)
        try {
            every { PkCalibrationEngine.evaluate(any()) } answers {
                solved.complete(Unit)
                callOriginal()
            }
            val pause = pauseNextRead()
            repository.requestRebuild()
            pause.reached.await()
            val commit = CompletableDeferred<Unit>()
            val mutation = launch { repository.runHomeDataMutation { commit.await() } }
            runCurrent()
            // Bumped, and its block still running under the lock.
            assertEquals(1L, repository.generation.value)
            pause.release.complete(Unit)
            solved.await()
            // The run has solved and waits for the lock to publish.
            assertEquals(listOf(0L), published.map { state -> state.generation })

            commit.complete(Unit)
            mutation.join()
            repository.currentCache.first { state -> state != null }
            settle()
        } finally {
            unmockkObject(PkCalibrationEngine)
        }

        assertEquals(2, roomReads.get())
        assertEquals(listOf(0L, 1L), published.map { state -> state.generation })
        published.assertBothPartsAtOneGeneration()
        assertEquals(listOf(1L), writtenSnapshots.map { record -> record.generation })
        assertEquals(listOf(1L), writtenInputs.map { record -> record.generation })
    }

    @Test
    fun homeDataAndE2Inputs_areAlwaysPublishedTogether_atOneGeneration() = runTest {
        val repository = repository()
        val published = publishes(repository)

        repeat(3) { index ->
            repository.runHomeDataMutation { chartWindowOptionState.value = HomeE2ChartWindowOption.entries[index % 2] }
            repository.rebuildNow()
        }

        assertEquals(listOf(0L, 1L, 2L, 3L), published.map { state -> state.generation })
        published.assertBothPartsAtOneGeneration()
        assertEquals(writtenSnapshots.map { record -> record.generation }, writtenInputs.map { record -> record.generation })
    }

    @Test
    fun aFailingRun_publishesNothing_isRetriedWithBackoff_thenWaitsForTheNextRequest() = runTest {
        val attempts = MutableStateFlow(emptyList<Long>())
        coEvery { databaseHolder.awaitOpen() } answers {
            attempts.value = attempts.value + testScheduler.currentTime
            throw IOException("database open failed")
        }
        lateinit var estimates: E2EstimateRepository
        val repository = repository(estimates = { estimates })
        estimates = estimates(repository)
        val published = publishes(repository)

        repository.runHomeDataMutation {}
        val times = attempts.first { times -> times.size == 4 }
        advanceTimeBy(10 * 60_000L)
        settle()

        assertEquals(listOf(0L, 1_000L, 6_000L, 36_000L), times.map { time -> time - times.first() })
        assertEquals(4, attempts.value.size)
        assertEquals(listOf(0L), published.map { state -> state.generation })
        assertTrue(writtenSnapshots.isEmpty() && writtenInputs.isEmpty())
        // A screen that comes to the foreground meanwhile has no estimate and
        // nothing being derived: Home shows the skeleton, and the calibration
        // page its live lab list without the section.
        assertEquals(E2State<HomeE2Estimate>(null), estimates.observeHome(MutableStateFlow(now)).first())
        assertEquals(E2State<CalibrationEstimate>(null), estimates.observeCalibration(MutableStateFlow(now)).first())

        // The observations' own requests, and any other, try again.
        repository.requestRebuild()
        attempts.first { times -> times.size > 4 }
    }

    @Test
    fun requestRebuildIfIdle_addsNothingToARunsRetries_butStartsARunOnceTheyGaveUp() = runTest {
        val attempts = MutableStateFlow(emptyList<Long>())
        coEvery { databaseHolder.awaitOpen() } answers {
            attempts.value = attempts.value + testScheduler.currentTime
            throw IOException("database open failed")
        }
        val repository = repository()
        // What an observation would read of the worker at each bump.
        val rebuildingAtBumps = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.generation.collect { rebuildingAtBumps += repository.rebuilding.value }
        }

        repository.runHomeDataMutation {}
        // Pending from the commit, before the worker takes the run up: never
        // seen behind the mutation and idle, which would read as a give-up.
        assertEquals(listOf(false, true), rebuildingAtBumps)
        assertTrue(repository.rebuilding.value)
        attempts.first { times -> times.size == 1 }
        // An observation behind the mutation, at its minute: no sooner retry.
        repository.requestRebuildIfIdle()
        val times = attempts.first { times -> times.size == 4 }
        assertTrue(repository.rebuilding.value)
        advanceTimeBy(10 * 60_000L)
        settle()
        assertEquals(listOf(0L, 1_000L, 6_000L, 36_000L), times.map { time -> time - times.first() })
        assertEquals(4, attempts.value.size)
        assertFalse(repository.rebuilding.value)

        // Given up: the observation's next minute starts a run, which converges.
        coEvery { databaseHolder.awaitOpen() } returns database
        repository.requestRebuildIfIdle()
        repository.currentCache.first { state -> state != null }

        assertEquals(1L, repository.cache.value?.generation)
        assertEquals(listOf(1L), writtenSnapshots.map { record -> record.generation })
    }

    @Test
    fun rebuildNow_waitsPastARunANewerMutationDiscarded_forTheRunThatPublishesIt() = runTest {
        // "Log now" commits and waits; its run is discarded by a later
        // mutation, whose own run is the one that publishes the log.
        val warmed = Collections.synchronizedList(mutableListOf<Long?>())
        lateinit var repository: HomeSnapshotRepository
        coEvery { e2EstimateRepository.warmHome() } answers { warmed += repository.cache.value?.generation }
        repository = repository()
        val rebuilding = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.rebuilding.collect { rebuilding += it } }
        val pause = pauseNextRead()

        repository.runHomeDataMutation {}
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { repository.rebuildNow() }
        pause.reached.await()
        repository.runHomeDataMutation {}
        pause.release.complete(Unit)
        waiting.await()
        settle()

        // The discarded run's follow-up never shows the worker idle.
        assertEquals(listOf(false, true, false), rebuilding)

        assertEquals(2L, repository.cache.value?.generation)
        assertEquals(listOf(2L), writtenSnapshots.map { record -> record.generation })
        assertEquals(listOf<Long?>(2L), warmed.toList())
        assertEquals(2, roomReads.get())
    }

    @Test
    fun aMutationWhileHomeIsObserved_costsOneRun() = runTest {
        lateinit var estimates: E2EstimateRepository
        val repository = repository(estimates = { estimates })
        estimates = estimates(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            estimates.observeHome(MutableStateFlow(now)).collect {}
        }
        repository.cache.filterNotNull().first()
        runCurrent()

        // A write that suspends, as Room's do: the observation sees the bump
        // while the mutation still holds the gate's lock.
        repository.runHomeDataMutation { yield() }
        repository.currentCache.first { state -> state != null }
        settle()

        assertEquals(1, roomReads.get())
    }

    @Test
    fun aWriteFailureAfterThePublish_failsTheRun_whichIsRetried_andOnlyTheRetryWarms() = runTest {
        val snapshotWrites = AtomicInteger()
        coEvery { homeSnapshotStore.writeSnapshot(any()) } answers {
            if (snapshotWrites.getAndIncrement() == 0) throw IOException("disk full")
            writtenSnapshots += firstArg<HomeSnapshotRecord>()
        }
        val warmUps = MutableStateFlow(0)
        coEvery { e2EstimateRepository.warmHome() } answers { warmUps.value += 1 }
        val repository = repository()
        val current = Collections.synchronizedList(mutableListOf<HomeCacheState?>())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.currentCache.collect { state -> current += state }
        }

        repository.runHomeDataMutation {}
        warmUps.first { count -> count == 1 }
        settle()

        // Before the load, the load, the mutation, and the failed run's
        // publish; the retry publishes an equal state, which isn't emitted
        // again, then writes and warms.
        assertEquals(listOf(null, 0L, null, 1L), current.map { state -> state?.generation })
        assertEquals(2, roomReads.get())
        assertEquals(listOf(1L), writtenSnapshots.map { record -> record.generation })
        assertEquals(listOf(1L), writtenInputs.map { record -> record.generation })
        assertEquals(1, warmUps.value)
    }

    @Test
    fun rebuildNow_waitsForARunThatStartedAfterIt_warmUpIncluded() = runTest {
        val warmUps = MutableStateFlow(0)
        val finishWarmUps = listOf(CompletableDeferred<Unit>(), CompletableDeferred<Unit>())
        coEvery { e2EstimateRepository.warmHome() } coAnswers {
            val index = warmUps.value
            warmUps.value = index + 1
            finishWarmUps[index].await()
        }
        val repository = repository()
        val pause = pauseNextRead()
        repository.requestRebuild()
        pause.reached.await()

        // A run already going when it is called doesn't count.
        val waiting = async { repository.rebuildNow() }
        runCurrent()
        pause.release.complete(Unit)
        warmUps.first { count -> count == 1 }
        assertEquals(1, writtenSnapshots.size)
        finishWarmUps[0].complete(Unit)
        warmUps.first { count -> count == 2 }
        runCurrent()
        assertFalse(waiting.isCompleted)

        finishWarmUps[1].complete(Unit)
        waiting.await()
        assertEquals(2, roomReads.get())
    }

    @Test
    fun aPublishesWarmUp_storesHomesKey_soAColdStartRightAfterShowsIt() = runTest {
        var stored: HomeE2EstimateRecord? = null
        val estimateStore = mockk<HomeE2EstimateStore> {
            coEvery { read() } answers { stored }
            coEvery { write(any()) } answers { stored = firstArg() }
        }
        lateinit var estimates: E2EstimateRepository
        val repository = repository(estimates = { estimates })
        estimates = estimates(repository, estimateStore)

        repository.runHomeDataMutation {}
        repository.rebuildNow()

        val inputs = checkNotNull(repository.cache.value?.e2Inputs)
        assertEquals(1L, inputs.generation)
        assertEquals(E2Derivation.homeEstimateKey(inputs.e2Inputs(), now, zoneId), stored?.key)
        assertEquals(stored?.estimate, estimates.observeHome(MutableStateFlow(now)).first().value)
    }

    @Test
    fun aThrowingSolve_stillPublishes_withAFailedFit() = runTest {
        val repository = repository()

        mockkObject(PkCalibrationEngine)
        try {
            every { PkCalibrationEngine.evaluate(any()) } throws IllegalStateException("solver")
            repository.runHomeDataMutation {}
            repository.rebuildNow()
        } finally {
            unmockkObject(PkCalibrationEngine)
        }

        assertEquals(FitOutcome.Failed, repository.cache.value?.e2Inputs?.fit?.outcome)
        assertEquals(FitOutcome.Failed, writtenInputs.single().fit?.outcome)
        assertNotNull(writtenSnapshots.single())
    }

    // ── What a run reads and publishes ───────────────────────────────────────

    @Test
    fun aRun_readsTheRowsForTheTenDayValidity() = runTest {
        val scheduledStartIso = mutableListOf<String>()
        val scheduledEndIso = mutableListOf<String>()
        val manualStart = mutableListOf<Long>()
        val manualEnd = mutableListOf<Long>()
        coEvery {
            homeDao.getScheduleEntries(
                capture(scheduledStartIso),
                capture(scheduledEndIso),
                capture(manualStart),
                capture(manualEnd),
            )
        } returns emptyList()
        val anchorDate = now.toLocalDate()

        repository().rebuildNow()

        assertEquals(anchorDate.minusDays(1).atStartOfDay().toString(), scheduledStartIso.single())
        assertEquals(anchorDate.plusDays(100).atTime(23, 59, 59).toString(), scheduledEndIso.single())
        assertEquals(anchorDate.minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(), manualStart.single())
        assertEquals(anchorDate.plusDays(12).atStartOfDay(zoneId).toInstant().toEpochMilli(), manualEnd.single())
    }

    @Test
    fun aRun_readsTheDeviceZonesWindows() = runTest {
        val originalTimeZone = TimeZone.getDefault()
        val newYork = ZoneId.of("America/New_York")
        TimeZone.setDefault(TimeZone.getTimeZone(newYork))
        try {
            val manualStart = mutableListOf<Long>()
            val manualEnd = mutableListOf<Long>()
            coEvery {
                homeDao.getScheduleEntries(any(), any(), capture(manualStart), capture(manualEnd))
            } returns emptyList()
            val anchorDate = now.toLocalDate()
            val repository = repository(timeSource = FakeAppTimeSource(now, initialZone = newYork))

            repository.rebuildNow()

            assertEquals(anchorDate.minusDays(1).atStartOfDay(newYork).toInstant().toEpochMilli(), manualStart.last())
            assertEquals(anchorDate.plusDays(12).atStartOfDay(newYork).toInstant().toEpochMilli(), manualEnd.last())
            assertEquals(newYork.id, repository.cache.value?.homeData?.zoneId)
            assertEquals(newYork.id, repository.cache.value?.e2Inputs?.zoneId)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun aRun_publishesWhatTheEstimatesAreDerivedFrom_andTheRealProfile() = runTest {
        // Home's 30-day option, the fit at the requested epoch, the weight
        // the solve used, and only the active groups with an estradiol item.
        chartWindowOptionState.value = HomeE2ChartWindowOption.THIRTY_DAYS
        val estradiol = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
        val spironolactone = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002")
        room.medicines = listOf(
            medicineEntity(uuid = estradiol, displayName = "E2"),
            medicineEntity(uuid = spironolactone, displayName = "Spiro", category = MedicationCategory.ANTIANDROGEN),
        )
        room.activeGroups = listOf(
            groupWithTwoSlotsReferencing(estradiol),
            groupWithTwoSlotsReferencing(
                spironolactone,
                groupUuid = UUID.fromString("44bff3cb-b4dd-4be4-9eda-442cf91185c2"),
                name = "Home antiandrogen",
            ),
        )

        repository().rebuildNow()

        val inputs = checkNotNull(writtenInputs.single())
        assertEquals(PK_ENGINE_VERSION, inputs.engineVersion)
        assertEquals(HomeE2ChartWindowOption.THIRTY_DAYS, inputs.option)
        assertEquals(0, inputs.fit?.epoch)
        assertNull(inputs.weightKg)
        assertEquals(listOf("Home estradiol"), inputs.plannedSources.map { group -> group.name })
        assertEquals(now.toLocalDate().toEpochDay(), inputs.anchorDateEpochDay)
        val homeData = writtenSnapshots.single()
        assertEquals(UserProfile(), homeData.userProfile)
        assertEquals(2, homeData.activeGroups.size)
    }

    @Test
    fun aRun_storesTheFirstPinnedTrackedDateAsHomeAnchor() = runTest {
        coEvery { journalDao.getFirstPinnedTrackedDate() } returns TrackedDateEntity(
            uuid = "hero-1",
            name = "HRT start",
            iconKey = "favorite",
            dateIso = "2025-03-14",
            paletteKey = null,
            heroBackgroundKey = null,
            pinnedOrder = 0,
            createdAtEpochMillis = 10L,
            updatedAtEpochMillis = 10L,
        )

        repository().rebuildNow()

        assertEquals("hero-1", writtenSnapshots.single().homeAnchor?.id)
        assertEquals(LocalDate.of(2025, 3, 14), writtenSnapshots.single().homeAnchor?.date)
    }

    @Test
    fun aRun_storesTheStockInputs() = runTest {
        val anchorDate = now.toLocalDate()
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
        val stockMedicine = medicineEntity(
            uuid = medicineUuid,
            displayName = "Tracked stock",
            trackingEnabled = true,
            stockUnitsRemaining = 2.0,
            stockUnitsLastTotal = 10.0,
        )
        val stockEntry = medicationLogEntryEntity(
            uuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"),
            medicineUuid = medicineUuid,
            scheduledFor = anchorDate.plusDays(2).atTime(8, 0),
        )
        room.medicines = listOf(stockMedicine)
        val stockStartIso = mutableListOf<String>()
        val stockEndIso = mutableListOf<String>()
        coEvery {
            medicationLogDao.getScheduledEntriesInWindow(capture(stockStartIso), capture(stockEndIso))
        } returns listOf(stockEntry)

        repository().rebuildNow()

        assertEquals(anchorDate.minusDays(1).atStartOfDay().toString(), stockStartIso.single())
        assertEquals(
            anchorDate.plusDays(ScheduledRunwayCalculator.HORIZON_DAYS).atTime(23, 59, 59).toString(),
            stockEndIso.single(),
        )
        val homeData = writtenSnapshots.single()
        assertEquals(listOf(medicineUuid), homeData.stockMedicines.map { medicine -> medicine.uuid })
        assertEquals(listOf(stockEntry.uuid), homeData.stockFulfillmentEntries.map { entry -> entry.uuid.toString() })
    }

    @Test
    fun aRun_readsEveryEstimatesLookback_withNoUpperBound_withoutMovingHomesKeys() = runTest {
        for (option in HomeE2ChartWindowOption.entries) {
            room = calibrationRoom(now)
            chartWindowOptionState.value = option
            writtenInputs.clear()

            repository().rebuildNow()

            // From 180 days before the calibration page's domain, 17 days
            // back, for every option, and through whatever is logged ahead.
            // One read, which also serves the calibration's dose window: 180
            // days before the first lab, 20 days back, the earlier start.
            val pageLookback = now.toLocalDate().minusDays(17).atStartOfDay(zoneId).toInstant()
                .minus(PK_CALIBRATION_DOSE_LOOKBACK)
            val calibrationStart = now.toLocalDate().minusDays(20).atTime(9, 0).atZone(zoneId)
                .toInstant().minus(PK_CALIBRATION_DOSE_LOOKBACK)
            val pkRead = room.pkReads.single()
            assertEquals(calibrationStart.toEpochMilli(), pkRead.first)
            assertEquals(Long.MAX_VALUE, pkRead.last)
            val inputs = writtenInputs.single()
            assertEquals(
                room.doses.count { dose -> dose.appliedAtEpochMillis >= pageLookback.toEpochMilli() },
                inputs.estradiolEntries.size,
            )
            // Home's and the widget's keys are the ones the entries their own
            // lookbacks read give.
            val homeLookback = now.toLocalDate().minusDays(option.pastDays).atStartOfDay(zoneId).toInstant()
                .minus(PK_CALIBRATION_DOSE_LOOKBACK)
            val homeOwn = inputs.copy(
                estradiolEntries = inputs.estradiolEntries.filter { entry -> !entry.appliedAt.isBefore(homeLookback) },
            )
            assertTrue(homeOwn.estradiolEntries.size < inputs.estradiolEntries.size)
            assertEquals(
                E2Derivation.homeEstimateKey(homeOwn.e2Inputs(), now, zoneId),
                E2Derivation.homeEstimateKey(inputs.e2Inputs(), now, zoneId),
            )
            assertEquals(
                E2Derivation.widgetEstimateKey(homeOwn.e2Inputs(), now, zoneId),
                E2Derivation.widgetEstimateKey(inputs.e2Inputs(), now, zoneId),
            )
        }
    }

    // ── The fit ──────────────────────────────────────────────────────────────

    @Test
    fun thePublishedFit_standsInForTheSolve_whenNothingItReadsChanged() = runTest {
        room = calibrationRoom(now)
        val timeSource = FakeAppTimeSource(now)
        val repository = repository(timeSource = timeSource)
        var solves = 0
        mockkObject(PkCalibrationEngine)
        try {
            every { PkCalibrationEngine.evaluate(any()) } answers {
                solves++
                callOriginal()
            }
            repository.rebuildNow()
            assertEquals(1, solves)

            val edits = listOf<() -> Unit>(
                // A testosterone-only result.
                { room.panels += labPanel(UUID(0L, 7L), now.minusDays(1), 30.0, BloodAnalyteKey.T) },
                // An antiandrogen dose.
                {
                    room.antiandrogens += room.doses.last().copy(
                        uuid = UUID(0L, 8L).toString(),
                        category = MedicationCategory.ANTIANDROGEN.name,
                    )
                },
                // A stock recount of the E2 medicine every dose embeds.
                {
                    room.medicines = room.medicines.map { medicine ->
                        medicine.copy(stockUnitsRemaining = 3.0, updatedAtEpochMillis = 900L)
                    }
                },
                // Accepting the accepted result again: only its timestamp moves.
                { room.metadata = room.metadata.map { review -> review.copy(updatedAtEpochMillis = 2_000L) } },
                // The 7/30 switch.
                { chartWindowOptionState.value = HomeE2ChartWindowOption.THIRTY_DAYS },
            )
            for (edit in edits) {
                repository.runHomeDataMutation { edit() }
                repository.rebuildNow()
            }
            // Midnight.
            timeSource.setCurrentMinute(now.plusDays(1))
            repository.rebuildNow()
        } finally {
            unmockkObject(PkCalibrationEngine)
        }

        assertEquals(1, solves)
        assertEquals(7, writtenInputs.size)
        val fit = checkNotNull(writtenInputs.first().fit)
        assertTrue(fit.outcome is FitOutcome.Solved)
        assertTrue(writtenInputs.all { record -> record.fit === fit })
        // Published beside it, the labs and reviews it was solved from: a
        // review's timestamp, which the fit doesn't read, aside.
        assertTrue(writtenInputs.all { record -> record.labs == writtenInputs.first().labs })
        for (disposition in E2CalibrationDisposition.entries) {
            assertEquals(
                setOf(writtenInputs.first().labMetadata.resultIdsWith(disposition)),
                writtenInputs.map { record -> record.labMetadata.resultIdsWith(disposition) }.toSet(),
            )
        }
        assertEquals(2_000L, writtenInputs.last().labMetadata.single().updatedAt.toEpochMilli())
    }

    @Test
    fun thePublishedFit_isSolvedAgain_whenItFailed_orWhileATryAgainIsPending() = runTest {
        room = calibrationRoom(now)
        val timeSource = FakeAppTimeSource(now)
        val repository = repository(timeSource = timeSource)
        var solves = 0
        mockkObject(PkCalibrationEngine)
        try {
            every { PkCalibrationEngine.evaluate(any()) } answers {
                if (solves++ == 0) throw IllegalStateException("solver") else callOriginal()
            }
            repository.rebuildNow()
            // A transient failure: the next run, here an antiandrogen dose's,
            // solves again without the user acting.
            repository.runHomeDataMutation {
                room.antiandrogens += room.doses.last().copy(
                    uuid = UUID(0L, 8L).toString(),
                    category = MedicationCategory.ANTIANDROGEN.name,
                )
            }
            repository.rebuildNow()
            // Try again over a solved fit whose inputs are unchanged.
            repository.retryCalibration()
            repository.rebuildNow()
            // Satisfied: the next run reuses it.
            timeSource.setCurrentMinute(now.plusHours(1))
            repository.rebuildNow()
        } finally {
            unmockkObject(PkCalibrationEngine)
        }

        assertEquals(3, solves)
        assertEquals(listOf(0, 0, 1, 1), writtenInputs.map { record -> record.fit?.epoch })
        assertEquals(FitOutcome.Failed, writtenInputs[0].fit?.outcome)
        assertTrue(writtenInputs.drop(1).all { record -> record.fit?.outcome is FitOutcome.Solved })
        assertEquals(1, writtenInputs.map { record -> record.fit?.fitId }.distinct().size)
        assertTrue(writtenInputs[3].fit === writtenInputs[2].fit)
    }

    @Test
    fun aStartupRun_reusesTheLoadedFit_butNotOneWhosePartIsInvalid() = runTest {
        // An earlier process published and wrote both files; nothing the fit
        // read has changed since.
        room = calibrationRoom(now)
        var solves = 0
        mockkObject(PkCalibrationEngine)
        try {
            every { PkCalibrationEngine.evaluate(any()) } answers {
                solves++
                callOriginal()
            }
            repository().rebuildNow()
            val fit = checkNotNull(writtenInputs.single().fit)
            assertEquals(1, solves)

            // A schema bump: Home data is rebuilt at startup, the valid
            // inputs' fit stands in for the solve.
            storedSnapshot = storedSnapshot?.copy(schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION - 1)
            val second = repository()
            second.cache.first { state -> state?.homeData != null }
            assertEquals(1, solves)
            assertEquals(fit, writtenInputs.last().fit)

            // Twelve days on, the inputs are outside their window too: the
            // part, and its fit, are missing, so the startup run solves.
            val third = repository(timeSource = FakeAppTimeSource(now.plusDays(12)))
            third.cache.first { state -> state?.e2Inputs != null }
            assertEquals(2, solves)
            assertEquals(fit.fitId, writtenInputs.last().fit?.fitId)
        } finally {
            unmockkObject(PkCalibrationEngine)
        }
    }

    @Test
    fun theFitsIdentity_followsNoClock_soAnotherDayOrOptionNeedsNoSolve() = runTest {
        // Doses go back further than any window. Without labs the read used
        // to start at the option's PK window, which moved with the date.
        for (withLabs in listOf(false, true)) {
            room = calibrationRoom(now, withLabs = withLabs)
            chartWindowOptionState.value = HomeE2ChartWindowOption.SEVEN_DAYS
            writtenInputs.clear()
            val timeSource = FakeAppTimeSource(now)
            val repository = repository(timeSource = timeSource)
            var solves = 0
            mockkObject(PkCalibrationEngine)
            try {
                every { PkCalibrationEngine.evaluate(any()) } answers {
                    solves++
                    callOriginal()
                }
                repository.rebuildNow()
                chartWindowOptionState.value = HomeE2ChartWindowOption.THIRTY_DAYS
                timeSource.setCurrentMinute(now.plusDays(3).withHour(22))
                repository.rebuildNow()
            } finally {
                unmockkObject(PkCalibrationEngine)
            }

            assertEquals(1, solves)
            assertTrue(writtenInputs[1].fit === writtenInputs[0].fit)
            val expected = if (withLabs) PkCalibrationGlobalState.READY else PkCalibrationGlobalState.NO_USABLE_LABS
            assertEquals(expected, (writtenInputs[1].fit?.outcome as? FitOutcome.Solved)?.result?.globalState)
        }
    }

    @Test
    fun requestedFitEpoch_startsAtTheLoadedFits() = runTest {
        // Try again in an earlier process left epoch 3. An ordinary run must
        // keep it, or a stock edit would move every estimate key.
        storedInputs = e2Inputs(generation = 0L, fit = E2FitRecord(Digest("00"), epoch = 3, outcome = FitOutcome.Failed))
        val repository = repository()

        repository.runHomeDataMutation {}
        repository.rebuildNow()
        repository.retryCalibration()
        repository.rebuildNow()

        assertEquals(listOf(3, 4), writtenInputs.map { record -> record.fit?.epoch })
    }

    @Test
    fun retryCalibration_reSolvesWithARaisedEpoch_fromUnchangedInputs() = runTest {
        // Try again after a failed solve: the inputs are the same, so only
        // the epoch tells the estimates the fit is new. It is a gated change.
        val repository = repository()
        var solves = 0
        mockkObject(PkCalibrationEngine)
        try {
            every { PkCalibrationEngine.evaluate(any()) } answers {
                if (solves++ == 0) throw IllegalStateException("solver") else readyEvaluation()
            }
            repository.rebuildNow()

            repository.retryCalibration()
            assertEquals(1L, repository.generation.value)
            repository.rebuildNow()
        } finally {
            unmockkObject(PkCalibrationEngine)
        }

        assertEquals(FitOutcome.Failed, writtenInputs[0].fit?.outcome)
        assertEquals(0, writtenInputs[0].fit?.epoch)
        assertTrue(writtenInputs[1].fit?.outcome is FitOutcome.Solved)
        assertEquals(1, writtenInputs[1].fit?.epoch)
        assertEquals(writtenInputs[0].fit?.fitId, writtenInputs[1].fit?.fitId)
    }

    @Test
    fun aRetry_survivesConflation_andChangesEveryKeyBuiltOnTheFit() = runTest {
        room = calibrationRoom(now)
        val repository = repository()
        var solves = 0
        mockkObject(PkCalibrationEngine)
        try {
            every { PkCalibrationEngine.evaluate(any()) } answers {
                solves++
                callOriginal()
            }
            repository.rebuildNow()
            val before = checkNotNull(repository.cache.value?.e2Inputs)

            // Try again while a run is going, then an ordinary request that
            // the channel folds into the same one.
            val pause = pauseNextRead()
            repository.requestRebuild()
            pause.reached.await()
            repository.retryCalibration()
            repository.requestRebuild()
            pause.release.complete(Unit)
            repository.rebuildNow()
            settle()
            val after = checkNotNull(repository.cache.value?.e2Inputs)

            assertEquals(2, solves)
            assertEquals(1, after.fit?.epoch)
            assertEquals(before.fit?.fitId, after.fit?.fitId)
            assertNotEquals(
                E2Derivation.homeEstimateKey(before.e2Inputs(), now, zoneId),
                E2Derivation.homeEstimateKey(after.e2Inputs(), now, zoneId),
            )
            assertNotEquals(
                E2Derivation.calibrationRenderKey(before.e2Inputs(), now, zoneId),
                E2Derivation.calibrationRenderKey(after.e2Inputs(), now, zoneId),
            )
        } finally {
            unmockkObject(PkCalibrationEngine)
        }
    }

    @Test
    fun aRetry_staysPendingUntilAPublishSatisfiesIt_evenAfterARunFails() = runTest {
        room = calibrationRoom(now)
        val repository = repository()
        var solves = 0
        mockkObject(PkCalibrationEngine)
        try {
            every { PkCalibrationEngine.evaluate(any()) } answers {
                solves++
                callOriginal()
            }
            repository.rebuildNow()

            // The retry's run fails; its backoff retry still solves with the
            // requested epoch.
            val opens = AtomicInteger()
            coEvery { databaseHolder.awaitOpen() } answers {
                if (opens.getAndIncrement() == 0) throw IOException("database open failed")
                database
            }
            repository.retryCalibration()
            repository.cache.first { state -> state?.e2Inputs?.fit?.epoch == 1 }
            // Satisfied: an ordinary run after it reuses that fit.
            repository.runHomeDataMutation {}
            repository.rebuildNow()
        } finally {
            unmockkObject(PkCalibrationEngine)
        }

        assertEquals(2, solves)
        assertEquals(listOf(0, 1, 1), writtenInputs.map { record -> record.fit?.epoch })
        assertTrue(writtenInputs[2].fit === writtenInputs[1].fit)
    }

    @Test
    fun aRetry_survivesADiscardedRun_andTheFailedRenderIsntReturned() = runTest {
        room = calibrationRoom(now)
        lateinit var estimates: E2EstimateRepository
        val repository = repository(
            estimates = { estimates },
            // Real threads: the retry's solve blocks one while a mutation
            // commits.
            defaultDispatcher = Dispatchers.Default,
        )
        estimates = estimates(repository)
        val solves = AtomicInteger()
        val retrySolveStarted = CountDownLatch(1)
        val releaseRetrySolve = CountDownLatch(1)
        var renders = 0
        val states = Collections.synchronizedList(mutableListOf<E2State<CalibrationEstimate>>())
        mockkObject(PkCalibrationEngine, E2Derivation)
        try {
            every { PkCalibrationEngine.evaluate(any()) } answers {
                if (solves.incrementAndGet() == 2) {
                    retrySolveStarted.countDown()
                    releaseRetrySolve.await()
                }
                callOriginal()
            }
            every { E2Derivation.deriveCalibrationRender(any(), any(), any()) } answers {
                if (renders++ == 0) throw IllegalStateException("render") else callOriginal()
            }
            repository.rebuildNow()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                estimates.observeCalibration(MutableStateFlow(now)).collect { state -> states += state }
            }
            awaitCondition { states.lastOrNull()?.value != null }
            // The page shows the failed render's numeric failure, with Try again.
            assertNull(states.last().value?.result)

            // Try again: its run pauses in the solve it forces, and a non-E2
            // edit commits meanwhile, which discards it.
            repository.retryCalibration()
            awaitCondition { retrySolveStarted.count == 0L }
            repository.runHomeDataMutation {
                room.antiandrogens += room.doses.last().copy(
                    uuid = UUID(0L, 8L).toString(),
                    category = MedicationCategory.ANTIANDROGEN.name,
                )
            }
            releaseRetrySolve.countDown()
            awaitCondition { states.last().value?.result != null }
        } finally {
            releaseRetrySolve.countDown()
            unmockkObject(PkCalibrationEngine, E2Derivation)
        }

        // The mutation's run solved again with the requested epoch rather
        // than reusing the old fit; the discarded one published nothing.
        assertEquals(listOf(0, 1), writtenInputs.map { record -> record.fit?.epoch })
        assertEquals(3, solves.get())
        assertEquals(2, renders)
        assertNotNull(states.last().value?.render)
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun TestScope.repository(
        timeSource: AppTimeSource = FakeAppTimeSource(now),
        estimates: () -> E2EstimateRepository = { e2EstimateRepository },
        defaultDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
    ) = HomeSnapshotRepository(
        databaseHolder = databaseHolder,
        homeSnapshotStore = homeSnapshotStore,
        homeSnapshotGenerationStore = homeSnapshotGenerationStore,
        settingsRepository = settingsRepository,
        e2EstimateRepository = { estimates() },
        appTimeSource = timeSource,
        appScope = backgroundScope,
        defaultDispatcher = defaultDispatcher,
        diagnosticsLogger = diagnosticsLogger,
    )

    private fun TestScope.estimates(
        repository: HomeSnapshotRepository,
        store: HomeE2EstimateStore = mockk(relaxed = true) { coEvery { read() } returns null },
    ) = E2EstimateRepository(
        homeSnapshotRepository = repository,
        store = store,
        clock = Clock.fixed(now.atZone(zoneId).toInstant(), zoneId),
        appScope = backgroundScope,
        defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    /** Every state [repository] holds from its load on: the load's, then each publish. */
    private fun TestScope.publishes(repository: HomeSnapshotRepository): List<HomeCacheState> {
        val states = Collections.synchronizedList(mutableListOf<HomeCacheState>())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.cache.filterNotNull().collect { state -> states += state }
        }
        return states
    }

    private fun List<HomeCacheState>.assertBothPartsAtOneGeneration() {
        for (state in this) {
            assertEquals(state.generation, state.homeData?.generation)
            assertEquals(state.generation, state.e2Inputs?.generation)
        }
    }

    /** Holds a run in its first Room read: [reached] once it is there, until [release]. */
    private class Pause {
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    private fun pauseNextRead(): Pause = Pause().also(nextReadPause::set)

    // The worker runs in backgroundScope, whose tasks advanceUntilIdle()
    // leaves alone once no foreground work is left: these run what is due
    // with runCurrent(), and poll in real time, since runs hop through
    // Dispatchers.IO.

    /** Lets every run that is due finish. */
    private fun TestScope.settle() {
        repeat(5) {
            runCurrent()
            Thread.sleep(10L)
        }
        runCurrent()
    }

    /** Runs what is due until [condition] holds, for work that also runs on real threads. */
    private fun TestScope.awaitCondition(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition did not hold in time" }
            runCurrent()
            Thread.sleep(2L)
        }
    }

    private fun homeData(generation: Long, at: LocalDateTime = now) = HomeSnapshotRecord(
        schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
        generation = generation,
        generatedAtEpochMillis = at.atZone(zoneId).toInstant().toEpochMilli(),
        anchorDateEpochDay = at.toLocalDate().toEpochDay(),
        zoneId = zoneId.id,
        activeGroups = emptyList(),
        scheduleEntries = emptyList(),
        antiandrogenHistoryEntries = emptyList(),
    )

    private fun e2Inputs(generation: Long, at: LocalDateTime = now, fit: E2FitRecord? = null) = E2InputsRecord(
        generation = generation,
        anchorDateEpochDay = at.toLocalDate().toEpochDay(),
        zoneId = zoneId.id,
        medicationDayBoundary = MedicationDayBoundary.Midnight,
        option = HomeE2ChartWindowOption.SEVEN_DAYS,
        weightKg = null,
        estradiolEntries = emptyList(),
        plannedSources = emptyList(),
        fit = fit,
    )

    /** Room's rows, read with each query's bounds. */
    private class RoomData(
        var activeGroups: List<MedicationGroupWithItemsEntity> = emptyList(),
        var doses: List<MedicationLogEntryEntity> = emptyList(),
        var antiandrogens: List<MedicationLogEntryEntity> = emptyList(),
        var panels: List<BloodTestPanelWithResultsEntity> = emptyList(),
        var metadata: List<E2CalibrationMetadataEntity> = emptyList(),
        var medicines: List<MedicineEntity> = emptyList(),
    ) {
        /** Every getEstradiolPkEntries read's bounds. */
        val pkReads: MutableList<LongRange> = Collections.synchronizedList(mutableListOf())
    }

    /**
     * A morning oral dose on each of the 300 days before [now], and, with
     * labs, three E2 results an hour after one, the first accepted.
     */
    private fun calibrationRoom(now: LocalDateTime, withLabs: Boolean = true): RoomData {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000e2")
        return RoomData(
            doses = (1L..300L).map { daysAgo ->
                val at = now.toLocalDate().minusDays(daysAgo).atTime(8, 0)
                medicationLogEntryEntity(UUID.nameUUIDFromBytes("e2:$at".toByteArray()), medicineUuid, at)
            },
            panels = if (withLabs) {
                listOf(20L, 12L, 5L).mapIndexed { index, daysAgo ->
                    labPanel(UUID(0L, index + 1L), now.toLocalDate().minusDays(daysAgo).atTime(9, 0), 140.0)
                }
            } else {
                emptyList()
            },
            metadata = if (withLabs) {
                listOf(E2CalibrationMetadataEntity(UUID(0L, 1L).toString(), E2CalibrationDisposition.REVIEWED.name, 1_000L))
            } else {
                emptyList()
            },
            medicines = listOf(medicineEntity(uuid = medicineUuid, displayName = "E2")),
        )
    }

    private fun labPanel(
        resultId: UUID,
        collectedAt: LocalDateTime,
        value: Double,
        analyte: BloodAnalyteKey = BloodAnalyteKey.E2,
    ): BloodTestPanelWithResultsEntity {
        val panelUuid = UUID.nameUUIDFromBytes("panel:$resultId".toByteArray()).toString()
        return BloodTestPanelWithResultsEntity(
            panel = BloodTestPanelEntity(
                uuid = panelUuid,
                collectedAtInstantEpochMillis = collectedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                collectedAtTimeZoneId = ZoneId.systemDefault().id,
                notes = null,
                timeSinceLastEstradiolDoseMillis = null,
                timeSinceLastTestosteroneDoseMillis = null,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
            ),
            results = listOf(
                BloodTestResultEntity(
                    uuid = resultId.toString(),
                    panelUuid = panelUuid,
                    createdAtEpochMillis = 0L,
                    displayOrder = 0,
                    builtinAnalyteKey = analyte.storageValue,
                    customAnalyteUuid = null,
                    value = value,
                    unitSnapshot = "pg_ml",
                    canonicalValue = value,
                )
            ),
        )
    }

    private fun readyEvaluation() = PkCalibrationEvaluation(
        PkCalibrationResult(
            PkCalibrationGlobalState.READY,
            PkCalibrationRoute.entries.map { route ->
                PkRouteCalibrationResult(route, PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL)
            },
        ),
        evidence = null,
    )

    private fun groupWithTwoSlotsReferencing(
        medicineUuid: UUID,
        groupUuid: UUID = UUID.fromString("44bff3cb-b4dd-4be4-9eda-442cf91185c1"),
        name: String = "Home estradiol",
    ): MedicationGroupWithItemsEntity {
        return MedicationGroupWithItemsEntity(
            group = MedicationGroupEntity(
                uuid = groupUuid.toString(),
                name = name,
                colorKey = MedicationGroupColorKey.PLUM.name,
                scheduleType = MedicationGroupScheduleType.DAILY.name,
                scheduleInterval = 1,
                scheduleSinceEpochDay = LocalDate.of(2026, 5, 1).toEpochDay(),
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
            ),
            items = listOf(0, 1).map { slotIndex ->
                MedicationGroupItemEntity(
                    uuid = UUID.nameUUIDFromBytes("$groupUuid:item:$slotIndex".toByteArray()).toString(),
                    groupUuid = groupUuid.toString(),
                    sortOrder = slotIndex,
                    count = 1,
                    medicineUuid = medicineUuid.toString(),
                    applicationType = MedicationApplicationType.ORAL.name,
                    doseInstructionKind = DoseInstructionKind.TABLET_FRACTION.name,
                    tabletFractionNumerator = 1,
                    tabletFractionDenominator = 1,
                    doseVolumeMl = null,
                    doseWeightGrams = null,
                )
            },
            scheduleTimes = listOf(
                MedicationGroupScheduleTimeEntity(
                    uuid = UUID.nameUUIDFromBytes("$groupUuid:time".toByteArray()).toString(),
                    groupUuid = groupUuid.toString(),
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
        category: MedicationCategory = MedicationCategory.ESTRADIOL,
        trackingEnabled: Boolean = false,
        stockUnitsRemaining: Double? = null,
        stockUnitsLastTotal: Double? = null,
    ): MedicineEntity {
        val key = if (category == MedicationCategory.ESTRADIOL) MedicationKey.ESTRADIOL else MedicationKey.SPIRONOLACTONE
        return MedicineEntity(
            uuid = uuid.toString(),
            selectionKind = "CATALOG",
            medicationKey = key.name,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = category.name,
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
            identityKey = MedicineIdentityKey.catalog(key, MedicinePreparation.Pill(strengthMgPerTablet = 2.0)),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 500L,
            archivedAtEpochMillis = null,
            trackingEnabled = trackingEnabled,
            stockUnitsRemaining = stockUnitsRemaining,
            stockUnitsLastTotal = stockUnitsLastTotal,
        )
    }

    private fun medicationLogEntryEntity(
        uuid: UUID,
        medicineUuid: UUID,
        scheduledFor: LocalDateTime,
    ): MedicationLogEntryEntity {
        return MedicationLogEntryEntity(
            uuid = uuid.toString(),
            category = MedicationCategory.ESTRADIOL.name,
            medicineUuid = medicineUuid.toString(),
            applicationType = MedicationApplicationType.ORAL.name,
            doseInstructionKind = DoseInstructionKind.TABLET_FRACTION.name,
            tabletFractionNumerator = 1,
            tabletFractionDenominator = 1,
            doseVolumeMl = null,
            doseWeightGrams = null,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = UUID.fromString("cccccccc-0000-0000-0000-000000000001").toString(),
            scheduleTimeUuid = UUID.fromString("dddddddd-0000-0000-0000-000000000001").toString(),
            appliedAtEpochMillis = scheduledFor.atZone(ZoneId.systemDefault()).toInstant()
                .toEpochMilli(),
            scheduledForIso = scheduledFor.toString(),
        )
    }
}
