package com.mkx.hrttracker.widget

import com.mkx.hrttracker.data.repository.E2Derivation
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.E2InputsRecord
import com.mkx.hrttracker.data.repository.HOME_SNAPSHOT_SCHEMA_VERSION
import com.mkx.hrttracker.data.repository.HomeCacheState
import com.mkx.hrttracker.data.repository.HomeE2EstimateRecord
import com.mkx.hrttracker.data.repository.HomeE2EstimateStore
import com.mkx.hrttracker.data.repository.HomeEstimateKey
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.e2Inputs
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetDailyRefreshWorkerTest {
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 5, 20)
    private val now = today.atTime(10, 0)

    // A run's publish, current: both parts at the durable generation.
    private val state = HomeCacheState(
        generation = 0L,
        homeData = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = 0L,
            anchorDateEpochDay = today.toEpochDay(),
            zoneId = zoneId.id,
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        ),
        e2Inputs = E2InputsRecord(
            generation = 0L,
            anchorDateEpochDay = today.toEpochDay(),
            zoneId = zoneId.id,
            option = HomeE2ChartWindowOption.SEVEN_DAYS,
            weightKg = 62.0,
            estradiolEntries = emptyList(),
            plannedSources = emptyList(),
        ),
    )
    private val cache = MutableStateFlow<HomeCacheState?>(state)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true) {
        every { cache } returns this@WidgetDailyRefreshWorkerTest.cache
        every { generation } returns MutableStateFlow(state.generation)
        coEvery { currentState() } returns state
    }
    private val widgetSnapshotStore: WidgetSnapshotStore = mockk()
    private val widgetSnapshotRepository = spyk(
        WidgetSnapshotRepository(
            context = mockk(),
            homeSnapshotRepository = homeSnapshotRepository,
            e2EstimateRepository = mockk(),
            settingsRepository = mockk(),
            widgetSnapshotStore = widgetSnapshotStore,
            diagnosticsLogger = mockk(relaxed = true),
        )
    )
    private val e2EstimateRepository: E2EstimateRepository = mockk(relaxed = true)
    private var rerenders = 0

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun staleRows_withACurrentStateWithBothParts_areRebuiltFromIt_withoutARun() = runTest {
        // The record's builder needs the app's resources; what matters here is
        // that the writer gets the state and the time.
        coEvery { widgetSnapshotRepository.writeWidgetSnapshot(any(), any()) } returns true
        val stale = listOf(
            // No record yet.
            null,
            // Written yesterday.
            widget(anchorDate = today.minusDays(1)),
            // A dose that turned due soon since.
            widget(rows = listOf(row(WidgetDoseStatus.UPCOMING, now.plusMinutes(30)))),
            // A dose due soon that turned overdue since.
            widget(rows = listOf(row(WidgetDoseStatus.DUE_SOON, now.minusMinutes(61)))),
        )

        for (snapshot in stale) refresh(snapshot)

        coVerify(exactly = 4) { widgetSnapshotRepository.writeWidgetSnapshot(state, now) }
        coVerify(exactly = 0) { homeSnapshotRepository.rebuildNow() }
        verify(exactly = 0) { homeSnapshotRepository.requestRebuild() }
        coVerify(exactly = 4) { e2EstimateRepository.warmHome() }
        assertEquals(0, rerenders)
    }

    @Test
    fun staleRows_withoutACurrentStateWithBothParts_waitForARun_andLeaveTheWidgetAlone() = runTest {
        // Behind a mutation whose run hasn't published; the first start after
        // an engine bump, whose load kept Home data and dropped the E2 inputs;
        // and the first start after a Home data schema bump, whose load kept
        // the E2 inputs. The run's publish rewrites the widget.
        for (current in listOf(null, state.copy(e2Inputs = null), state.copy(homeData = null))) {
            // The cache's latest state, so only the both-parts rule turns it away.
            if (current != null) cache.value = current
            coEvery { homeSnapshotRepository.currentState() } returns current
            val run = CompletableDeferred<Unit>()
            coEvery { homeSnapshotRepository.rebuildNow() } coAnswers { run.await() }
            var warmed = false
            coEvery { e2EstimateRepository.warmHome() } coAnswers { warmed = true }

            val refresh = launch { refresh(widget(anchorDate = today.minusDays(1))) }
            runCurrent()
            assertFalse(refresh.isCompleted)
            assertFalse(warmed)

            run.complete(Unit)
            runCurrent()
            assertTrue(refresh.isCompleted)
            assertTrue(warmed)
        }
        confirmVerified(widgetSnapshotStore)
        assertEquals(0, rerenders)
    }

    @Test
    fun anUnchangedKey_onlyComputesTheKey_andAfterTheDateChangesDerivesOnce() = runTest {
        stored = HomeE2EstimateRecord(
            key = keyAt(now),
            estimate = E2Derivation.deriveHomeEstimate(checkNotNull(state.e2Inputs).e2Inputs(), now, zoneId) {
                throw it
            },
        )
        var derivations = 0
        mockkObject(E2Derivation)
        every { E2Derivation.deriveHomeEstimate(any(), any(), any(), any()) } answers {
            derivations++
            callOriginal()
        }
        coEvery { widgetSnapshotRepository.writeWidgetSnapshot(any(), any()) } returns true
        val estimates = estimateRepository()

        // Fresh rows: the widget is re-rendered, and the stored estimate
        // already has Home's key.
        val fresh = widget(
            rows = listOf(
                row(WidgetDoseStatus.UPCOMING, now.plusHours(2)),
                row(WidgetDoseStatus.DUE_SOON, now.minusMinutes(30)),
            ),
        )
        refresh(fresh, e2EstimateRepository = estimates)
        assertEquals(1, rerenders)
        assertEquals(0, derivations)
        assertEquals(0, writes)

        // The date changed: the rows are rebuilt from the cache, and the
        // estimate for the new day is derived once.
        val tomorrow = now.plusDays(1)
        clock.instant = tomorrow.atZone(zoneId).toInstant()
        refresh(widget(), at = tomorrow, e2EstimateRepository = estimates)
        val later = tomorrow.plusMinutes(15)
        clock.instant = later.atZone(zoneId).toInstant()
        refresh(widget(anchorDate = later.toLocalDate()), at = later, e2EstimateRepository = estimates)

        assertEquals(1, derivations)
        assertEquals(1, writes)
        assertEquals(keyAt(tomorrow), stored?.key)
        coVerify(exactly = 1) { widgetSnapshotRepository.writeWidgetSnapshot(state, tomorrow) }
        assertEquals(2, rerenders)
        // No Room read and no solve: only a run does either.
        coVerify(exactly = 0) { homeSnapshotRepository.rebuildNow() }
        verify(exactly = 0) { homeSnapshotRepository.requestRebuild() }
    }

    private suspend fun refresh(
        snapshot: WidgetSnapshotRecord?,
        at: LocalDateTime = now,
        e2EstimateRepository: E2EstimateRepository = this.e2EstimateRepository,
    ) = handleWidgetDailyRefresh(
        now = at,
        snapshot = snapshot,
        homeSnapshotRepository = homeSnapshotRepository,
        widgetSnapshotRepository = widgetSnapshotRepository,
        e2EstimateRepository = e2EstimateRepository,
        rerender = { rerenders++ },
    )

    private val clock = MutableClock(now.atZone(zoneId).toInstant())
    private var stored: HomeE2EstimateRecord? = null
    private var writes = 0
    private val estimateStore: HomeE2EstimateStore = mockk {
        coEvery { read() } answers { stored }
        coEvery { write(any()) } answers {
            stored = firstArg()
            writes++
        }
    }

    private fun TestScope.estimateRepository(): E2EstimateRepository {
        val estimates = E2EstimateRepository(
            homeSnapshotRepository = homeSnapshotRepository,
            store = estimateStore,
            clock = clock,
            appScope = backgroundScope,
            defaultDispatcher = StandardTestDispatcher(testScheduler),
        )
        // The stored estimate's load.
        runCurrent()
        return estimates
    }

    private fun keyAt(at: LocalDateTime): HomeEstimateKey =
        E2Derivation.homeEstimateKey(checkNotNull(state.e2Inputs).e2Inputs(), at, zoneId)

    private fun widget(
        anchorDate: LocalDate = today,
        rows: List<WidgetDoseRow> = emptyList(),
    ) = WidgetSnapshotRecord(
        schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
        zoneId = zoneId.id,
        anchorDateEpochDay = anchorDate.toEpochDay(),
        doneCount = 0,
        totalCount = rows.size,
        manualCount = 0,
        hasActiveGroups = rows.isNotEmpty(),
        hideMedicationDetails = false,
        adaptiveColorEnabled = false,
        e2DisplayUnit = "pg_ml",
        appLanguageTag = "en",
        doseRows = rows,
        pkProjection = null,
    )

    private fun row(status: WidgetDoseStatus, scheduledAt: LocalDateTime) = WidgetDoseRow(
        medicationName = "Estradiol",
        groupName = "Morning group",
        colorKey = null,
        routeLabel = "Oral",
        doseText = "2 mg",
        status = status,
        scheduledAt = scheduledAt,
        trailingText = null,
        isManualRecord = false,
        contextChip = null,
        groupUuid = null,
        scheduleTimeUuid = null,
    )

    private class MutableClock(@Volatile var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }
}
