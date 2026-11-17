package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.mkx.hrttracker.data.local.BloodTestDao
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HomeDao
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.JournalDao
import com.mkx.hrttracker.data.local.MedicationLogDao
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.PkCalibrationDao
import com.mkx.hrttracker.data.local.TrackedDateEntity
import com.mkx.hrttracker.data.local.UserProfileDao
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.medicationDay
import com.mkx.hrttracker.util.observeUses24HourTimeFormat
import com.mkx.hrttracker.util.uses24HourTimeFormat
import com.mkx.hrttracker.widget.HomeWidgetManager
import com.mkx.hrttracker.widget.WIDGET_SNAPSHOT_SCHEMA_VERSION
import com.mkx.hrttracker.widget.WidgetSnapshotRecord
import com.mkx.hrttracker.widget.WidgetSnapshotRepository
import com.mkx.hrttracker.widget.WidgetSnapshotStore
import com.mkx.hrttracker.widget.buildWidgetSnapshotRecord
import com.mkx.hrttracker.widget.handleWidgetDailyRefresh
import com.mkx.hrttracker.widget.pushHrtWidgets
import com.mkx.hrttracker.widget.scheduleNextWidgetDateRefresh
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Replays of the counterexamples TLC finds in .claude/skills/verify-home-widget-snapshot/model/,
 * one step per model action, against the real HomeSnapshotRepository, WidgetSnapshotRepository,
 * HomeWidgetManager's cache observer, handleWidgetDailyRefresh and HomeSnapshotGenerationStore.
 * Room, the part files and the widget's store are fakes that keep what they hold across a
 * process death: a second repository over the same fakes is the next process's cold start.
 * The data a write changes is Home's hero, Room's first pinned tracked date, as a journal
 * anchor write changes it.
 *
 * Findings 1, 2 and 3 are fixed, and their tests assert the rule the model checks along the path
 * that broke it. The guard asserts a rule along the path a broken variant of the model takes.
 *
 * Only finding 3's test may build the real HomeSnapshotGenerationStore: its DataStore is one per
 * JVM, and the test needs it to read the corrupt file first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class HomeWidgetSnapshotModelReplayTest {
    private val now = LocalDateTime.of(2026, 5, 6, 10, 15)
    private val zoneId = ZoneId.systemDefault()

    // ── Durable: survives a process death ────────────────────────────────────

    /** Room's hero: the name of the first pinned tracked date. */
    @Volatile
    private var hero = "A"

    /** home_snapshot_metadata, as the fake generation store holds it. */
    @Volatile
    private var durable = 0L

    // The two part files, valid at generation 0 for hero A: the model's Init.
    @Volatile
    private var storedSnapshot: HomeSnapshotRecord? = HomeSnapshotRecord(
        schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
        generation = 0L,
        // An hour before this process's runs, which build at now: each publish has its own stamp.
        generatedAtEpochMillis = now.minusHours(1).atZone(zoneId).toInstant().toEpochMilli(),
        anchorDateEpochDay = now.toLocalDate().toEpochDay(),
        zoneId = zoneId.id,
        activeGroups = emptyList(),
        scheduleEntries = emptyList(),
        antiandrogenHistoryEntries = emptyList(),
        homeAnchor = anchor("A").toModel(),
    )

    @Volatile
    private var storedInputs: E2InputsRecord? = E2InputsRecord(
        generation = 0L,
        anchorDateEpochDay = now.toLocalDate().toEpochDay(),
        zoneId = zoneId.id,
        option = HomeE2ChartWindowOption.SEVEN_DAYS,
        weightKg = null,
        estradiolEntries = emptyList(),
        plannedSources = emptyList(),
    )

    // ── How the next step goes ───────────────────────────────────────────────

    private val database: HrtTrackerDatabase = mockk()

    /** The run's Room open (awaitOpen): at once, unless a test makes it throw or hang. */
    @Volatile
    private var open: suspend () -> HrtTrackerDatabase = { database }
    private val opens = MutableStateFlow(0)

    /** The gate's generation write (incrementGeneration). */
    @Volatile
    private var bump: suspend () -> Long = { ++durable }

    private val generationStore: HomeSnapshotGenerationStore = mockk {
        coEvery { readGeneration() } answers { durable }
        // The epoch the part files carry.
        coEvery { readEpoch() } returns 0L
        coEvery { incrementGeneration() } coAnswers { bump() }
    }
    private val homeSnapshotStore: HomeSnapshotStore = mockk {
        coEvery { readSnapshot() } answers { storedSnapshot }
        coEvery { readE2Inputs() } answers { storedInputs }
        coEvery { writeSnapshot(any()) } answers { storedSnapshot = firstArg() }
        coEvery { writeE2Inputs(any()) } answers { storedInputs = firstArg() }
    }
    private val settingsRepository: SettingsRepository = mockk {
        every { homeE2ChartWindowOptionFlow } returns MutableStateFlow(HomeE2ChartWindowOption.SEVEN_DAYS)
        every { settingsState } returns MutableStateFlow(SettingsState())
        coEvery { getCurrentSettings() } returns SettingsState()
    }
    private val databaseHolder: DatabaseHolder = mockk {
        coEvery { awaitOpen() } coAnswers {
            opens.value += 1
            open()
        }
    }

    init {
        val homeDao = mockk<HomeDao> {
            coEvery { getActiveGroups() } returns emptyList()
            coEvery { getArchivedGroups() } returns emptyList()
            coEvery { getScheduleEntries(any(), any(), any(), any()) } returns emptyList()
            coEvery { getLatestAntiandrogenEntriesOnOrBefore(any()) } returns emptyList()
            coEvery { getLatestEstradiolEntryOnOrBefore(any()) } returns null
            coEvery { getEstradiolPkEntries(any(), any()) } returns emptyList()
        }
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns mockk<MedicineDao> {
            coEvery { getAllActiveTrackedEntities() } returns emptyList()
            coEvery { getByUuids(any()) } returns emptyList()
        }
        every { database.medicationLogDao() } returns mockk<MedicationLogDao> {
            coEvery { getScheduledEntriesInWindow(any(), any()) } returns emptyList()
        }
        every { database.userProfileDao() } returns mockk<UserProfileDao> { coEvery { getProfile() } returns null }
        every { database.journalDao() } returns mockk<JournalDao> {
            coEvery { getFirstPinnedTrackedDate() } answers { anchor(hero) }
        }
        every { database.bloodTestDao() } returns mockk<BloodTestDao> { coEvery { getPanels() } returns emptyList() }
        every { database.pkCalibrationDao() } returns mockk<PkCalibrationDao> { coEvery { getAllMetadata() } returns emptyList() }
    }

    @After
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    // ── Findings ─────────────────────────────────────────────────────────────

    /**
     * Finding 1, WorkerNeverGivesUpBehind on HomeWidgetSnapshot-giveup.cfg: WLoad MutBump
     * MutCommit MutRequest WTop (WDrain WGenRead RunFails) x4 WTop. The rebuild worker still
     * waits for the next request after its last retry; the 15-minute worker is that request
     * (PAsk), even with rows the clock hasn't made stale.
     */
    @Test
    fun finding1_aMutationsRunThatThrowsFourTimes_isAskedAgainByThe15MinuteWorker() = runTest {
        val process = startProcess()
        val repository = process.repository
        // WLoad: both files are valid at generation 0, so no run.
        repository.cache.filterNotNull().first()
        settle()
        assertEquals(0, opens.value)

        // Every attempt throws (the database open, HSR:371).
        open = { throw IOException("database open failed") }
        // MutBump, MutCommit, MutRequest: a journal anchor write renames the hero.
        repository.runHomeDataMutation { hero = "B" }
        // WTop, then four attempts: the first and three retries after 1, 5 and 30 s.
        opens.first { count -> count == 4 }
        advanceTimeBy(10 * 60_000L)
        settle()

        // WTop: idle, given up, behind the write.
        assertEquals(4, opens.value)
        assertFalse(repository.rebuilding.value)
        assertEquals(1L, repository.generation.value)
        assertEquals(0L, repository.cache.value?.generation)
        assertEquals(null, repository.currentState())
        assertEquals("A", repository.cache.value?.homeData?.homeAnchor?.name)

        // PAsk: the 15-minute worker, with rows the clock hasn't made stale, finds the cache
        // behind with no run pending, and asks for the run, which lands (the database opens now).
        open = { database }
        var rerenders = 0
        handleWidgetDailyRefresh(
            now = now,
            snapshot = widgetRecord(tag = "A", builtAt = now),
            homeSnapshotRepository = repository,
            widgetSnapshotRepository = mockk(),
            e2EstimateRepository = mockk(relaxed = true),
            rerender = { rerenders += 1 },
        )
        assertEquals(0, rerenders)
        assertEquals(5, opens.value)
        assertEquals("B", repository.currentState()?.homeData?.homeAnchor?.name)
        process.die()
    }

    /**
     * Finding 2, CatchUp on HomeWidgetSnapshot-widgetfail.cfg: WLoad MutBump MutCommit MutRequest
     * WTop WDrain WGenRead WReadRoom WPublish WWriteHome WWriteE2 WWarm WTop OReceive OCheck
     * OWriteFails, then the 15-minute worker (PStart FCheck FWrite). The observer only logs a
     * write that throws (HWM:85-96); the worker rewrites a record built before the cache's state.
     */
    @Test
    fun finding2_aWidgetWriteThatThrows_isRewrittenByTheNext15MinuteWorkerRun() = runTest {
        var widgetStored: WidgetSnapshotRecord? =
            widgetRecord(tag = "A", builtAt = now.minusHours(1), source = storedSnapshot?.generatedAtEpochMillis)
        var failed = false
        val widgetStore = mockk<WidgetSnapshotStore> {
            coEvery { readSnapshot() } answers { widgetStored }
            coEvery { writeSnapshot(any()) } answers {
                val record = firstArg<WidgetSnapshotRecord>()
                // The first write of the new hero throws: the encryption, or a full disk.
                if (record.e2DisplayUnit == "B" && !failed) {
                    failed = true
                    throw IOException("disk full")
                }
                widgetStored = record
            }
        }
        // The builder stub tags the record with the hero of the Home data it was built from.
        mockkStatic("com.mkx.hrttracker.widget.WidgetSnapshotBuilderKt")
        every { buildWidgetSnapshotRecord(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            val homeData = secondArg<HomeSnapshotRecord>()
            widgetRecord(tag = homeData.homeAnchor?.name ?: "none", builtAt = arg(4), source = homeData.generatedAtEpochMillis)
        }
        mockkStatic("com.mkx.hrttracker.widget.HrtWidgetKt")
        coEvery { pushHrtWidgets(any(), any()) } returns Unit
        mockkStatic("com.mkx.hrttracker.util.AppDateTimeFormattersKt")
        every { any<Context>().uses24HourTimeFormat() } returns true
        every { any<Context>().observeUses24HourTimeFormat() } returns flowOf(true)
        mockkStatic("com.mkx.hrttracker.widget.WidgetMidnightRefreshSchedulerKt")
        every { scheduleNextWidgetDateRefresh(any(), any(), any(), any()) } just Runs
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns mockk(relaxed = true)

        // WLoad.
        val process = startProcess()
        val repository = process.repository
        val context = RuntimeEnvironment.getApplication()
        val widgets = WidgetSnapshotRepository(
            context = context,
            homeSnapshotRepository = repository,
            e2EstimateRepository = mockk { coEvery { widgetEstimate(any(), any()) } returns null },
            settingsRepository = settingsRepository,
            widgetSnapshotStore = widgetStore,
            clock = Clock.fixed(now.atZone(zoneId).toInstant(), zoneId),
            diagnosticsLogger = mockk(relaxed = true),
        )
        HomeWidgetManager(
            context = context,
            homeSnapshotRepository = repository,
            settingsRepository = settingsRepository,
            widgetSnapshotRepository = widgets,
            widgetAppearanceRepository = mockk {
                coEvery { migrateFromLegacySettingsIfNeeded() } returns false
                every { observeChanges() } returns emptyFlow()
            },
            appScope = process.scope,
            diagnosticsLogger = mockk(relaxed = true),
        ).start()
        repository.cache.filterNotNull().first()
        settle()

        // MutBump, MutCommit, MutRequest, then the run: WTop ... WWarm WTop.
        repository.runHomeDataMutation { hero = "B" }
        repository.currentCache.first { state -> state?.homeData?.homeAnchor?.name == "B" }
        // OReceive, OCheck, OWriteFails.
        awaitCondition { failed }
        advanceTimeBy(60 * 60_000L)
        settle()

        // The worker idle with the cache current, and the widget still on A until the 15-minute
        // worker runs: its rows aren't stale by the clock, but the record predates the state.
        assertFalse(repository.rebuilding.value)
        assertEquals("B", repository.currentState()?.homeData?.homeAnchor?.name)
        assertEquals("A", widgetStored?.e2DisplayUnit)
        handleWidgetDailyRefresh(
            now = now.plusMinutes(15),
            snapshot = widgetStored,
            homeSnapshotRepository = repository,
            widgetSnapshotRepository = widgets,
            e2EstimateRepository = mockk(relaxed = true),
            rerender = {},
        )
        assertEquals("B", widgetStored?.e2DisplayUnit)
        process.die()
    }

    /**
     * Finding 3, ColdStartLoadsNoStalePart on HomeWidgetSnapshot-reset.cfg: WLoad MutBump
     * MutCommit Die MetadataResets WLoad. The cold start reads a corrupt home_snapshot_metadata
     * through the real HomeSnapshotGenerationStore, whose handler (HSS:114-117) makes it
     * generation 0, which the part files from before the write are at. The reset file gets a
     * new epoch, which they don't carry, so the load rejects them and rebuilds.
     */
    @Test
    fun finding3_aCorruptGenerationFile_readsAsGenerationZeroUnderANewEpoch_andTheColdStartRebuilds() = runTest {
        // WLoad. The run the write asks for never reads Room: the process dies first.
        open = { awaitCancellation() }
        val first = startProcess()
        first.repository.cache.filterNotNull().first()
        // MutBump, MutCommit.
        first.repository.runHomeDataMutation { hero = "B" }
        assertEquals(1L, durable)
        // Die.
        first.die()

        // MetadataResets: the file is corrupt when the next process first reads it.
        val context = RuntimeEnvironment.getApplication()
        val corrupt = byteArrayOf(0x0a, 0x0f, 0x7f)
        val metadataFile = File(context.filesDir, "datastore/home_snapshot_metadata.preferences_pb").apply {
            parentFile?.mkdirs()
            writeBytes(corrupt)
        }
        val realGenerationStore = HomeSnapshotGenerationStore(context)
        open = { database }
        val opensBefore = opens.value
        // WLoad, on the real generation store.
        val second = startProcess(realGenerationStore)
        val loaded = second.repository.cache.filterNotNull().first()
        settle()

        assertEquals(0L, realGenerationStore.readGeneration())
        val epoch = realGenerationStore.readEpoch()
        // The handler replaced this very file: the load read the corrupt one.
        assertFalse(metadataFile.readBytes().contentEquals(corrupt))
        assertEquals(0L, loaded.generation)
        // Nothing from before the write loads as current, and the run asked for writes Room's B.
        assertEquals(null, loaded.homeData)
        assertEquals(null, loaded.e2Inputs)
        val rebuilt = second.repository.currentCache.first { state -> state?.homeData != null }
        assertEquals("B", rebuilt?.homeData?.homeAnchor?.name)
        awaitCondition { storedInputs?.generation == 0L && storedInputs?.epoch == epoch }
        assertEquals(epoch, storedSnapshot?.epoch)
        assertEquals(epoch, storedInputs?.epoch)
        assertTrue(opens.value > opensBefore)
        second.die()
    }

    // ── Guard ────────────────────────────────────────────────────────────────

    /**
     * ColdStartLoadsNoStalePart holds at the sync commit. With the bump after the block (the
     * model variant MutCommit before MutBump), TLC's path is WLoad MutCommit Die WLoad: this
     * replays it, the death inside the generation write. At the sync commit the block hasn't
     * run there, so nothing is stale.
     */
    @Test
    fun guard_aDeathInsideTheGenerationWrite_leavesNoStalePartForTheNextStart() = runTest {
        val first = startProcess()
        first.repository.cache.filterNotNull().first()
        settle()
        // The generation write never lands: the process dies in it.
        val never = CompletableDeferred<Long>()
        bump = { never.await() }
        first.scope.launch { runCatching { first.repository.runHomeDataMutation { hero = "B" } } }
        runCurrent()
        settle()
        first.die()
        bump = { ++durable }

        val second = startProcess()
        second.repository.cache.filterNotNull().first()
        settle()
        val shown = second.repository.currentState()?.homeData?.homeAnchor?.name
        assertTrue("the cold start shows hero $shown as current while Room has $hero", shown == null || shown == hero)
        never.completeExceptionally(IOException("the process died"))
        second.die()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    /** A process: its repository runs in [scope], and [die] ends both. */
    private class Process(val scope: CoroutineScope, val repository: HomeSnapshotRepository) {
        fun die() = scope.cancel()
    }

    private fun TestScope.startProcess(store: HomeSnapshotGenerationStore = generationStore): Process {
        val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = store,
            settingsRepository = settingsRepository,
            e2EstimateRepository = { mockk(relaxed = true) },
            appTimeSource = FakeAppTimeSource(now),
            appScope = scope,
            defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
            diagnosticsLogger = mockk(relaxed = true),
        )
        return Process(scope, repository)
    }

    private fun anchor(name: String) = TrackedDateEntity(
        uuid = "hero",
        name = name,
        iconKey = "",
        dateIso = "2026-01-01",
        paletteKey = null,
        heroBackgroundKey = null,
        pinnedOrder = 0,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )

    // The tag stands in for the rows: e2DisplayUnit carries the hero they were built from.
    private fun widgetRecord(tag: String, builtAt: LocalDateTime, source: Long? = null) = WidgetSnapshotRecord(
        schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
        zoneId = zoneId.id,
        anchorDateEpochDay = builtAt.toLocalDate().toEpochDay(),
        fourAmAnchorDateEpochDay = medicationDay(builtAt, MedicationDayBoundary.FourAm).toEpochDay(),
        builtAt = builtAt,
        doneCount = 0,
        totalCount = 0,
        manualCount = 0,
        hasActiveGroups = false,
        hideMedicationDetails = false,
        adaptiveColorEnabled = false,
        e2DisplayUnit = tag,
        appLanguageTag = "en",
        doseRows = emptyList(),
        pkProjection = null,
        sourceGeneratedAtEpochMillis = source,
    )

    // Runs hop through Dispatchers.IO: run what is due, and poll in real time.
    private fun TestScope.settle() {
        repeat(5) {
            runCurrent()
            Thread.sleep(10L)
        }
        runCurrent()
    }

    private fun TestScope.awaitCondition(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition did not hold in time" }
            runCurrent()
            Thread.sleep(2L)
        }
    }
}
