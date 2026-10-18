package com.mkx.hrttracker.widget

import android.content.Context
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.E2InputsRecord
import com.mkx.hrttracker.data.repository.HOME_SNAPSHOT_SCHEMA_VERSION
import com.mkx.hrttracker.data.repository.HomeCacheState
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.uses24HourTimeFormat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSnapshotRepositoryTest {
    private val today = LocalDate.of(2026, 5, 20)
    private val now = today.atTime(10, 0)
    private val cache = MutableStateFlow<HomeCacheState?>(null)
    private val generation = MutableStateFlow(0L)
    private val homeSnapshotRepository = mockk<HomeSnapshotRepository>(relaxed = true) {
        every { cache } returns this@WidgetSnapshotRepositoryTest.cache
        every { generation } returns this@WidgetSnapshotRepositoryTest.generation
        coEvery { currentState() } answers {
            this@WidgetSnapshotRepositoryTest.cache.value
                ?.takeIf { state -> state.generation == this@WidgetSnapshotRepositoryTest.generation.value }
        }
    }
    private val estimates = mockk<E2EstimateRepository> {
        coEvery { widgetEstimate(any(), any()) } returns null
    }
    private val settingsRepository = mockk<SettingsRepository> {
        coEvery { getCurrentSettings() } returns SettingsState()
    }

    // The stored record, and each record written, by the tag of the state it
    // was built from.
    private var stored: WidgetSnapshotRecord? = null
    private val written = mutableListOf<String>()
    private val widgetSnapshotStore = mockk<WidgetSnapshotStore> {
        coEvery { readSnapshot() } answers { stored }
        coEvery { writeSnapshot(any()) } answers {
            stored = firstArg()
            written += firstArg<WidgetSnapshotRecord>().zoneId
        }
    }
    private val repository = WidgetSnapshotRepository(
        context = mockk(relaxed = true),
        homeSnapshotRepository = homeSnapshotRepository,
        e2EstimateRepository = estimates,
        settingsRepository = settingsRepository,
        widgetSnapshotStore = widgetSnapshotStore,
        diagnosticsLogger = mockk(relaxed = true),
    )

    @Before
    fun setUp() {
        // The record's builder and the push need the app's resources; the
        // builder stub carries the state's tag into the record, and a derived
        // estimate as [derivedProjection].
        mockkStatic("com.mkx.hrttracker.widget.HrtWidgetKt")
        coEvery { pushHrtWidgets(any(), any()) } returns Unit
        mockkStatic("com.mkx.hrttracker.util.AppDateTimeFormattersKt")
        every { any<Context>().uses24HourTimeFormat() } returns true
        mockkStatic("com.mkx.hrttracker.widget.WidgetSnapshotBuilderKt")
        every { buildWidgetSnapshotRecord(any(), any(), any(), any(), any(), any(), any()) } answers {
            record(
                tag = secondArg<HomeSnapshotRecord>().zoneId,
                pkProjection = thirdArg<PkProjectionResult?>()?.let { derivedProjection },
            )
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun refreshWidgetSnapshot_withNoCurrentState_asksForARun_andLeavesTheWidgetAlone() = runTest {
        // From a mutation until its run publishes: the widget keeps its last
        // snapshot, and the run's publish rewrites it.
        cache.value = state(generation = 0L, tag = "before-the-mutation")
        generation.value = 1L

        repository.refreshWidgetSnapshot()

        verify(exactly = 1) { homeSnapshotRepository.requestRebuild() }
        confirmVerified(widgetSnapshotStore)
    }

    @Test
    fun aStateWithCurrentHomeDataButNoE2Inputs_isNeverWritten_soTheWidgetKeepsItsEstimate() = runTest {
        // The first start after an engine bump: the load keeps Home data and
        // drops the inputs. Rows without their estimate would blank it.
        val homeDataOnly = state(generation = 0L, tag = "home-data-only").copy(e2Inputs = null)
        cache.value = homeDataOnly

        assertFalse(repository.writeWidgetSnapshot(homeDataOnly))
        repository.refreshWidgetSnapshot()

        verify(exactly = 1) { homeSnapshotRepository.requestRebuild() }
        confirmVerified(widgetSnapshotStore)
    }

    @Test
    fun aSlowWriteOfAnOlderState_landsBeforeTheNewerStatesWrite() = runTest {
        val before = state(generation = 0L, tag = "before-the-log")
        val after = state(generation = 1L, tag = "after-the-log")
        cache.value = before
        // The worker's estimate for the older state is a fresh derivation (a
        // process WorkManager just started); the newer one is ready.
        val slowDerivation = CompletableDeferred<Unit>()
        coEvery { estimates.widgetEstimate(checkNotNull(before.e2Inputs), any()) } coAnswers {
            slowDerivation.await()
            null
        }

        // The 15-minute worker finds a row that turned due soon.
        val worker = launch { refreshFromTheWorker() }
        runCurrent()
        // Meanwhile the user quick-logs that dose: the mutation commits, its
        // run publishes, and HomeWidgetManager's observer writes the result.
        generation.value = 1L
        cache.value = after
        val observer = launch { repository.writeWidgetSnapshot(after, now) }
        runCurrent()
        slowDerivation.complete(Unit)
        worker.join()
        observer.join()

        assertEquals(listOf("before-the-log", "after-the-log"), written)
        coVerify(exactly = 0) { homeSnapshotRepository.rebuildNow() }
    }

    @Test
    fun anOlderStateWrittenAfterTheNewerOne_isDropped() = runTest {
        val before = state(generation = 0L, tag = "before-the-log")
        val after = state(generation = 1L, tag = "after-the-log")
        cache.value = before

        // The worker reads the older state, and the log's run publishes, and
        // is written, before the worker writes.
        val read = CompletableDeferred<Unit>()
        coEvery { homeSnapshotRepository.currentState() } coAnswers {
            val state = cache.value
            read.await()
            state
        }
        val worker = launch { refreshFromTheWorker() }
        runCurrent()
        generation.value = 1L
        cache.value = after
        repository.writeWidgetSnapshot(after, now)
        read.complete(Unit)
        worker.join()

        assertEquals(listOf("after-the-log"), written)
        // Not written, so it waits for a run, which is only redundant.
        coVerify(exactly = 1) { homeSnapshotRepository.rebuildNow() }
    }

    @Test
    fun aStateTheMidnightRunSupersededAtTheSameGeneration_isDropped() = runTest {
        // A system run carries no newer generation: only the state's identity
        // tells the worker's older read from the midnight run's publish.
        val beforeMidnight = state(generation = 0L, tag = "before-midnight")
        val midnight = state(generation = 0L, tag = "midnight-run")
        cache.value = beforeMidnight

        val read = CompletableDeferred<Unit>()
        coEvery { homeSnapshotRepository.currentState() } coAnswers {
            val state = cache.value
            read.await()
            state
        }
        val worker = launch { refreshFromTheWorker() }
        runCurrent()
        cache.value = midnight
        repository.writeWidgetSnapshot(midnight, now)
        read.complete(Unit)
        worker.join()

        assertEquals(listOf("midnight-run"), written)
    }

    @Test
    fun aFailedEstimate_keepsTheLastOne_whileTheRowsUpdate() = runTest {
        // The derivation threw, and stays failed under its key until midnight,
        // an estradiol dose, the weight or the fit moves it. A row that turns
        // due meanwhile is still written, under the last estimate.
        stored = record(tag = "upcoming", pkProjection = lastProjection)
        val dueSoon = state(generation = 0L, tag = "due-soon")
        cache.value = dueSoon

        assertTrue(repository.writeWidgetSnapshot(dueSoon, now))

        assertEquals(listOf("due-soon"), written)
        assertEquals(lastProjection, stored?.pkProjection)
    }

    @Test
    fun aDerivedEstimate_replacesTheLastOne() = runTest {
        stored = record(tag = "before-the-log", pkProjection = lastProjection)
        coEvery { estimates.widgetEstimate(any(), any()) } returns mockk()
        val afterTheLog = state(generation = 1L, tag = "after-the-log")
        generation.value = 1L
        cache.value = afterTheLog

        assertTrue(repository.writeWidgetSnapshot(afterTheLog, now))

        assertEquals(listOf("after-the-log"), written)
        assertEquals(derivedProjection, stored?.pkProjection)
    }

    /** The 15-minute worker with stale rows: no record at all. */
    private suspend fun refreshFromTheWorker() = handleWidgetDailyRefresh(
        now = now,
        snapshot = null,
        homeSnapshotRepository = homeSnapshotRepository,
        widgetSnapshotRepository = repository,
        e2EstimateRepository = mockk(relaxed = true),
        rerender = {},
    )

    // What the builder stub writes for a derived estimate, and an older one.
    private val derivedProjection = projection(windowEndEpochMillis = 2_000L)
    private val lastProjection = projection(windowEndEpochMillis = 1_000L)

    private fun projection(windowEndEpochMillis: Long) = WidgetPkProjectionRecord(
        generatedAtEpochMillis = 0L,
        windowStartEpochMillis = 0L,
        windowEndEpochMillis = windowEndEpochMillis,
        pkProjectionExpiresAtEpochMillis = windowEndEpochMillis,
        concentrationUnit = "PG_PER_ML",
        timeH = emptyList(),
        concentrations = emptyList(),
        doseMarkers = emptyList(),
    )

    // The tag stands in for the rows.
    private fun record(tag: String, pkProjection: WidgetPkProjectionRecord?) = WidgetSnapshotRecord(
        schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
        zoneId = tag,
        anchorDateEpochDay = today.toEpochDay(),
        doneCount = 0,
        totalCount = 0,
        manualCount = 0,
        hasActiveGroups = false,
        hideMedicationDetails = false,
        adaptiveColorEnabled = false,
        e2DisplayUnit = "pg_ml",
        appLanguageTag = "en",
        doseRows = emptyList(),
        pkProjection = pkProjection,
    )

    // The tag stands in for the rows: the builder stub copies it into the record.
    private fun state(generation: Long, tag: String) = HomeCacheState(
        generation = generation,
        homeData = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = generation,
            generatedAtEpochMillis = 0L,
            anchorDateEpochDay = today.toEpochDay(),
            zoneId = tag,
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        ),
        e2Inputs = E2InputsRecord(
            generation = generation,
            anchorDateEpochDay = today.toEpochDay(),
            zoneId = tag,
            option = HomeE2ChartWindowOption.SEVEN_DAYS,
            weightKg = 62.0,
            estradiolEntries = emptyList(),
            plannedSources = emptyList(),
        ),
    )
}
