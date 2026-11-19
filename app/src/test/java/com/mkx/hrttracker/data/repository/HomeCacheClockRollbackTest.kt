package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderSlot
import com.mkx.hrttracker.reminder.buildMissingScheduledLogEntries
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.uses24HourTimeFormat
import com.mkx.hrttracker.widget.WidgetDoseStatus
import com.mkx.hrttracker.widget.WidgetSnapshotRecord
import com.mkx.hrttracker.widget.WidgetSnapshotRepository
import com.mkx.hrttracker.widget.WidgetSnapshotStore
import com.mkx.hrttracker.widget.handleWidgetDailyRefresh
import com.mkx.hrttracker.widget.pushHrtWidgets
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.UUID

/**
 * The clock is set back in the same zone and the time-change rebuild (WidgetDateReceiver) fails
 * through its retries, so Home's cache still holds the build of the later date, with records
 * only from the day before it. The handout (HomeSnapshotRepository.currentState and
 * currentCache) withholds that state by the load's date rule and asks for a run, so neither the
 * 15-minute worker nor Home shows the logged dose as not taken.
 *
 * Real code: Room (in memory), MedicationGroupRepository, MedicationLogRepository,
 * HomeSnapshotRepository (the rebuild worker, its retries, its Room read), HomeRepository's cache
 * path, WidgetSnapshotRepository, buildWidgetSnapshotRecord and handleWidgetDailyRefresh. Faked:
 * the two clocks, the part files, the widget's store, the push, the stock projection and the
 * database open (to fail the rebuild). The cache observer's write (HomeWidgetManager) is called
 * by hand after each publish.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class HomeCacheClockRollbackTest {
    private val zoneId = ZoneId.systemDefault()
    private val laterDate = LocalDate.of(2026, 5, 20)

    private lateinit var database: HrtTrackerDatabase
    private val databaseHolder: DatabaseHolder = mockk()

    @Volatile
    private var open: suspend () -> HrtTrackerDatabase = { database }
    private val opens = MutableStateFlow(0)

    @Volatile
    private var durable = 0L
    private val generationStore: HomeSnapshotGenerationStore = mockk {
        coEvery { readGeneration() } answers { durable }
        coEvery { readEpoch() } returns 0L
        coEvery { incrementGeneration() } answers { ++durable }
    }
    private val homeSnapshotStore: HomeSnapshotStore = mockk {
        coEvery { readSnapshot() } returns null
        coEvery { readE2Inputs() } returns null
        coEvery { writeSnapshot(any()) } returns Unit
        coEvery { writeE2Inputs(any()) } returns Unit
    }
    private val settingsRepository: SettingsRepository = mockk {
        every { homeE2ChartWindowOptionFlow } returns MutableStateFlow(HomeE2ChartWindowOption.SEVEN_DAYS)
        every { settingsState } returns MutableStateFlow(SettingsState())
        coEvery { getCurrentSettings() } returns SettingsState()
    }

    private var widgetStored: WidgetSnapshotRecord? = null
    private var widgetWrites = 0
    private val widgetStore: WidgetSnapshotStore = mockk {
        coEvery { readSnapshot() } answers { widgetStored }
        coEvery { writeSnapshot(any()) } answers {
            widgetStored = firstArg()
            widgetWrites += 1
        }
    }
    private val widgetClock = SettableClock(laterDate.atTime(10, 0).atZone(zoneId).toInstant(), zoneId)

    private val estradiol = testMedicine(
        uuid = UUID.fromString("5a1e0000-0000-0000-0000-000000000001"),
        key = MedicationKey.ESTRADIOL,
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        every { databaseHolder.get() } returns database
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        coEvery { databaseHolder.withTransaction<Any?>(any()) } coAnswers {
            database.withTransaction {
                firstArg<suspend (HrtTrackerDatabase) -> Any?>().invoke(database)
            }
        }
        coEvery { databaseHolder.awaitOpen() } coAnswers {
            opens.value += 1
            open()
        }
        mockkStatic("com.mkx.hrttracker.widget.HrtWidgetKt")
        coEvery { pushHrtWidgets(any(), any()) } returns Unit
        mockkStatic("com.mkx.hrttracker.util.AppDateTimeFormattersKt")
        every { any<Context>().uses24HourTimeFormat() } returns true
    }

    @After
    fun tearDown() {
        database.close()
        unmockkAll()
        clearAllMocks()
    }

    // ── The widget ───────────────────────────────────────────────────────────

    @Test
    fun aThreeDayRollback_whoseRebuildFailed_showsTheRecordedDoseAsDone() = runTest {
        val result = rollBack(days = 3, rebuildFails = true)
        assertEquals(
            "Room holds the record of ${result.slotAt}; the widget after the rollback shows it as",
            WidgetDoseStatus.DONE,
            result.afterWorker.rowAt(result.slotAt)?.status,
        )
    }

    @Test
    fun aTwoDayRollback_whoseRebuildFailed_showsTheRecordedDoseAsDone() = runTest {
        val result = rollBack(days = 2, rebuildFails = true)
        assertEquals(WidgetDoseStatus.DONE, result.afterWorker.rowAt(result.slotAt)?.status)
    }

    /** The cache's state is withheld, so the worker waits for a run from Room and writes nothing from it. */
    @Test
    fun replay_aThreeDayRollback_whoseRebuildFailed_waitsForARunFromRoom() = runTest {
        val result = rollBack(days = 3, rebuildFails = true)

        // The record of the later date; the cache still the later date's, without the record.
        assertEquals(laterDate.toEpochDay(), result.beforeRollback.anchorDateEpochDay)
        assertEquals(laterDate.toEpochDay(), result.cacheAnchorDateAtWorker)
        assertFalse("the cache's build holds no record of the rolled-back day", result.cacheHoldsTheRecord)
        assertTrue("Room does", result.roomHoldsTheRecord)
        // The worker wrote nothing from that cache: it waited for a run, which read Room.
        assertEquals(0, result.widgetWritesByWorker)
        assertEquals(1, result.roomOpensByWorker)
        // The observer's write of that run: the rolled-back day, the dose done.
        val record = result.afterWorker
        assertEquals(result.workerNow.toLocalDate().toEpochDay(), record.anchorDateEpochDay)
        assertEquals(WidgetDoseStatus.DONE, record.rowAt(result.slotAt)?.status)
        assertEquals(1, record.doneCount)
        // The next run, 15 minutes on: nothing is stale, so it only re-renders.
        assertEquals(1, result.rerendersBySecondRun)
        assertEquals(0, result.widgetWritesBySecondRun)
        assertEquals(0, result.roomOpensBySecondRun)
        assertEquals(WidgetDoseStatus.DONE, result.afterSecondRun.rowAt(result.slotAt)?.status)
    }

    @Test
    fun aOneDayRollback_whoseRebuildFailed_showsTheRecordedDoseAsDone() = runTest {
        val result = rollBack(days = 1, rebuildFails = true)
        assertEquals(WidgetDoseStatus.DONE, result.afterWorker.rowAt(result.slotAt)?.status)
    }

    /** One day back, the evening dose of the night before (Last night until noon) is outside the cache's buffer. */
    @Test
    fun aOneDayRollback_whoseRebuildFailed_showsLastNightsRecordedDoseAsDone() = runTest {
        val result = rollBack(days = 1, rebuildFails = true, doseTime = LocalTime.of(22, 0), slotDaysEarlier = 1)
        assertEquals(WidgetDoseStatus.DONE, result.afterWorker.rowAt(result.slotAt)?.status)
    }

    @Test
    fun aOneDayRollback_whoseRebuildRan_showsLastNightsRecordedDoseAsDone() = runTest {
        val result = rollBack(days = 1, rebuildFails = false, doseTime = LocalTime.of(22, 0), slotDaysEarlier = 1)
        assertEquals(WidgetDoseStatus.DONE, result.afterWorker.rowAt(result.slotAt)?.status)
    }

    @Test
    fun aThreeDayRollback_whoseRebuildRan_showsTheRecordedDoseAsDone() = runTest {
        val result = rollBack(days = 3, rebuildFails = false)
        assertEquals(WidgetDoseStatus.DONE, result.afterWorker.rowAt(result.slotAt)?.status)
    }

    // ── Home ─────────────────────────────────────────────────────────────────

    /** While the run the handout asks for fails too, Home's cache path draws nothing, so its Room path draws Home. */
    @Test
    fun home_aThreeDayRollback_whileTheRebuildStillFails_getsNoCache() = runTest {
        val rolled = prepare(days = 3, rebuildFails = true, faultClears = false)
        assertNull(rolled.repository.currentCache.first())
        assertNull(rolled.repository.currentState())
        // In real time: under runTest's virtual clock the timeout would pass before the flow's
        // hop to Dispatchers.IO delivers, and the assertion couldn't fail.
        val inputs = withContext(Dispatchers.Default) {
            withTimeoutOrNull(500L) {
                rolled.homeRepository.observeHomeSnapshotInputs(rolled.back, zoneId).first()
            }
        }
        assertNull("Home's cache path must not draw the later date's build", inputs)
        rolled.scope.cancel()
    }

    /** Once the fault clears, Home's first look asks for the run, and its cache path draws the rolled-back day. */
    @Test
    fun home_aThreeDayRollback_whoseRebuildFailed_showsTheRecordedDose() = runTest {
        val rolled = prepare(days = 3, rebuildFails = true)
        val inputs = rolled.homeRepository.observeHomeSnapshotInputs(rolled.back, zoneId).first()
        assertTrue(
            "Room holds the record of ${rolled.slotAt}; Home's inputs hold it too",
            inputs.scheduleEntries.any { entry -> entry.scheduledFor == rolled.slotAt },
        )
        rolled.scope.cancel()
    }

    /** No rollback: the next day, and ten days on, the cache is handed out as it is, with no run. */
    @Test
    fun anOrdinaryDay_handsOutTheCacheAsItIs() = runTest {
        val rolled = prepare(days = 0, rebuildFails = false, rollBack = false)
        val built = checkNotNull(rolled.repository.cache.value)
        val opensBefore = opens.value
        for (daysOn in listOf(1L, 10L)) {
            rolled.time.setCurrentMinute(laterDate.plusDays(daysOn).atTime(10, 0))
            assertSame(built, rolled.repository.currentState())
            assertSame(built, rolled.repository.currentCache.first())
        }
        settle()
        assertEquals("no run was asked for", opensBefore, opens.value)
        // Past the ten days the load's rule rejects it, and so does the handout.
        rolled.time.setCurrentMinute(laterDate.plusDays(11).atTime(10, 0))
        assertNull(rolled.repository.currentState())
        rolled.scope.cancel()
    }

    /**
     * A zone change whose run failed for good: neither handout gives the old zone's cache, and the
     * worker writes nothing from it. After a run, the new zone's state is handed out.
     */
    @Test
    fun aZoneChange_whoseRebuildFailed_withholdsTheOldZonesCache() = runTest {
        val defaultZone = TimeZone.getDefault()
        try {
            val rolled = prepare(days = 0, rebuildFails = false, rollBack = false)
            val repository = rolled.repository
            // Another zone at the same offset, so the local date doesn't move: only the zone tells
            // the cache apart (a westward trip would trip the date rule as well).
            val offset = zoneId.rules.getOffset(rolled.time.now())
            TimeZone.setDefault(TimeZone.getTimeZone(offset))
            val other = ZoneId.systemDefault().id
            check(other != zoneId.id) { "the same zone: $other" }
            // The same instant, in the new zone. TIME_ZONE_CHANGED: its run fails all four attempts.
            open = { throw IOException("database open failed") }
            val opensBefore = opens.value
            repository.rebuildNow()
            opens.first { count -> count == opensBefore + 4 }
            advanceTimeBy(10 * 60_000L)
            settle()
            check(!repository.rebuilding.value)
            check(repository.cache.value?.homeData?.zoneId == zoneId.id)

            assertNull(repository.currentState())
            assertNull(repository.currentCache.first())
            val writesBefore = widgetWrites
            val nowThere = LocalDateTime.ofInstant(rolled.time.now(), ZoneId.of(other))
            handleWidgetDailyRefresh(
                now = nowThere,
                snapshot = widgetStored,
                homeSnapshotRepository = repository,
                widgetSnapshotRepository = rolled.widgets,
                e2EstimateRepository = mockk(relaxed = true),
                rerender = {},
            )
            settle()
            assertEquals("no widget write from the old zone's cache", writesBefore, widgetWrites)

            // The fault is over: a run publishes the new zone's state, and it is handed out.
            open = { database }
            repository.rebuildNow()
            settle()
            val handed = checkNotNull(repository.currentState())
            assertEquals(other, handed.homeData?.zoneId)
            assertSame(handed, repository.currentCache.first())
            rolled.scope.cancel()
        } finally {
            TimeZone.setDefault(defaultZone)
        }
    }

    // ── The steps ────────────────────────────────────────────────────────────

    private class Rolled(
        val scope: CoroutineScope,
        val time: FakeAppTimeSource,
        val repository: HomeSnapshotRepository,
        val homeRepository: HomeRepository,
        val widgets: WidgetSnapshotRepository,
        val slotAt: LocalDateTime,
        val back: LocalDateTime,
        val beforeRollback: WidgetSnapshotRecord,
    )

    private class Result(
        val slotAt: LocalDateTime,
        val workerNow: LocalDateTime,
        val beforeRollback: WidgetSnapshotRecord,
        val afterWorker: WidgetSnapshotRecord,
        val afterSecondRun: WidgetSnapshotRecord,
        val widgetWritesByWorker: Int,
        val roomOpensByWorker: Int,
        val cacheAnchorDateAtWorker: Long?,
        val cacheHoldsTheRecord: Boolean,
        val roomHoldsTheRecord: Boolean,
        val rerendersBySecondRun: Int,
        val widgetWritesBySecondRun: Int,
        val roomOpensBySecondRun: Int,
    )

    /**
     * A daily plan with one dose logged on the day the clock goes back to, a run and a widget
     * write at the later date's 10:00; then, when [rollBack], the clock set back [days] days to
     * 10:10 and the time-change run, failing through its retries when [rebuildFails].
     */
    private suspend fun TestScope.prepare(
        days: Long,
        rebuildFails: Boolean,
        doseTime: LocalTime = LocalTime.of(9, 0),
        /** The logged dose's day, counted back from the day the clock goes back to. */
        slotDaysEarlier: Long = 0,
        rollBack: Boolean = true,
        faultClears: Boolean = true,
    ): Rolled {
        val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val time = FakeAppTimeSource(laterDate.atTime(10, 0))
        val repository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = generationStore,
            settingsRepository = settingsRepository,
            e2EstimateRepository = { mockk(relaxed = true) },
            appTimeSource = time,
            appScope = scope,
            defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
            diagnosticsLogger = mockk(relaxed = true),
        )
        val groupRepository = MedicationGroupRepository(databaseHolder, repository, scope)
        val logRepository = MedicationLogRepository(databaseHolder, repository, MedicineStockMutator(), scope)
        val widgets = WidgetSnapshotRepository(
            context = RuntimeEnvironment.getApplication(),
            homeSnapshotRepository = repository,
            e2EstimateRepository = mockk { coEvery { widgetEstimate(any(), any()) } returns null },
            settingsRepository = settingsRepository,
            widgetSnapshotStore = widgetStore,
            clock = widgetClock,
            diagnosticsLogger = mockk(relaxed = true),
        )
        val homeRepository = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = repository,
            medicineStockRepository = mockk {
                every { projectAll(any(), any(), any(), any(), any()) } returns emptyList()
            },
            medicineRepository = mockk(relaxed = true),
            medicationLogRepository = logRepository,
            journalRepository = mockk(relaxed = true),
            diagnosticsLogger = mockk(relaxed = true),
        )
        repository.cache.filterNotNull().first()

        // A daily plan, one estradiol tablet at [doseTime], since May 1.
        database.medicineDao().insert(estradiol.toEntity())
        val planUuid = groupRepository.saveGroup(
            uuid = null,
            name = "Morning",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 5, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(doseTime),
                timeSlots = listOf(MedicationGroupScheduleTimeInput(uuid = null, time = doseTime)),
                dayBoundary = MedicationDayBoundary.Midnight,
            ),
            medications = listOf(
                MedicationGroupMedicationInput(
                    medicineUuid = estradiol.uuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    count = 1,
                ),
            ),
            now = testInstant(LocalDateTime.of(2026, 5, 1, 7, 0)),
        )
        val plan = checkNotNull(groupRepository.getGroup(planUuid))
        // The dose of the day the clock will go back to, logged that day five minutes late, as
        // the widget's quick-log writes it.
        val slotAt = laterDate.minusDays(days + slotDaysEarlier).atTime(doseTime)
        val saved = logRepository.saveNewEntries(
            buildMissingScheduledLogEntries(
                group = plan,
                slot = MedicationReminderSlot(plan.uuid, slotAt, plan.schedule.timeSlots.single().uuid),
                entries = emptyList(),
                appliedAt = testInstant(slotAt.plusMinutes(5)),
            )
        )
        check(saved.size == 1) { "the dose was not logged: $saved" }

        // The later date, 10:00: a run, and the cache observer's widget write.
        repository.rebuildNow()
        settle()
        check(widgets.writeWidgetSnapshot(checkNotNull(repository.currentState())))
        val beforeRollback = checkNotNull(widgetStored)

        // The clock goes back [days] days, to 10:10. TIME_SET: WidgetDateReceiver's rebuildNow().
        val back = laterDate.minusDays(days).atTime(10, 10)
        if (rollBack) {
            time.setCurrentMinute(back)
            widgetClock.instant = back.atZone(zoneId).toInstant()
            if (rebuildFails) {
                // Every attempt throws: the first, and the retries after 1, 5 and 30 s.
                open = { throw IOException("database open failed") }
                val opensBefore = opens.value
                repository.rebuildNow()
                opens.first { count -> count == opensBefore + 4 }
                advanceTimeBy(10 * 60_000L)
                settle()
                check(opens.value == opensBefore + 4)
                check(!repository.rebuilding.value)
                // The fault is over; nothing has asked for another run.
                if (faultClears) open = { database }
            } else {
                repository.rebuildNow()
                settle()
                check(widgets.writeWidgetSnapshot(checkNotNull(repository.currentState())))
            }
        }
        return Rolled(scope, time, repository, homeRepository, widgets, slotAt, back, beforeRollback)
    }

    private suspend fun TestScope.rollBack(
        days: Long,
        rebuildFails: Boolean,
        doseTime: LocalTime = LocalTime.of(9, 0),
        slotDaysEarlier: Long = 0,
    ): Result {
        val rolled = prepare(days, rebuildFails, doseTime, slotDaysEarlier)
        val repository = rolled.repository
        val widgets = rolled.widgets

        // The 15-minute worker, at 10:15 on the rolled-back day.
        val workerNow = laterDate.minusDays(days).atTime(10, 15)
        widgetClock.instant = workerNow.atZone(zoneId).toInstant()
        // The raw cache: reading it through the handout would ask for a run.
        val cacheAtWorker = repository.cache.value
        val writesBeforeWorker = widgetWrites
        val opensBeforeWorker = opens.value
        handleWidgetDailyRefresh(
            now = workerNow,
            snapshot = widgetStored,
            homeSnapshotRepository = repository,
            widgetSnapshotRepository = widgets,
            e2EstimateRepository = mockk(relaxed = true),
            rerender = {},
        )
        settle()
        val widgetWritesByWorker = widgetWrites - writesBeforeWorker
        val roomOpensByWorker = opens.value - opensBeforeWorker
        // The cache observer's write of the run the worker waited for.
        if (repository.cache.value !== cacheAtWorker) {
            check(widgets.writeWidgetSnapshot(checkNotNull(repository.currentState())))
        }
        val afterWorker = checkNotNull(widgetStored)

        // And its next run, at 10:30.
        val secondNow = workerNow.plusMinutes(15)
        widgetClock.instant = secondNow.atZone(zoneId).toInstant()
        val opensBeforeSecond = opens.value
        val writesBeforeSecond = widgetWrites
        var rerenders = 0
        handleWidgetDailyRefresh(
            now = secondNow,
            snapshot = widgetStored,
            homeSnapshotRepository = repository,
            widgetSnapshotRepository = widgets,
            e2EstimateRepository = mockk(relaxed = true),
            rerender = { rerenders += 1 },
        )
        settle()

        val result = Result(
            slotAt = rolled.slotAt,
            workerNow = workerNow,
            beforeRollback = rolled.beforeRollback,
            afterWorker = afterWorker,
            afterSecondRun = checkNotNull(widgetStored),
            widgetWritesByWorker = widgetWritesByWorker,
            roomOpensByWorker = roomOpensByWorker,
            cacheAnchorDateAtWorker = cacheAtWorker?.homeData?.anchorDateEpochDay,
            cacheHoldsTheRecord = cacheAtWorker?.homeData?.scheduleEntries
                ?.any { entry -> entry.scheduledFor == rolled.slotAt } == true,
            roomHoldsTheRecord = database.medicationLogDao().getEntries()
                .any { entry -> entry.scheduledForIso == rolled.slotAt.toString() },
            rerendersBySecondRun = rerenders,
            widgetWritesBySecondRun = widgetWrites - writesBeforeSecond,
            roomOpensBySecondRun = opens.value - opensBeforeSecond,
        )
        rolled.scope.cancel()
        return result
    }

    private fun WidgetSnapshotRecord.rowAt(at: LocalDateTime) = doseRows.singleOrNull { row -> row.scheduledAt == at }

    private class SettableClock(@Volatile var instant: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = SettableClock(instant, zone)
        override fun instant(): Instant = instant
    }

    // Runs hop through Dispatchers.IO and Room's executors: run what is due, and poll in real time.
    private fun TestScope.settle() {
        repeat(5) {
            runCurrent()
            Thread.sleep(10L)
        }
        runCurrent()
    }
}
