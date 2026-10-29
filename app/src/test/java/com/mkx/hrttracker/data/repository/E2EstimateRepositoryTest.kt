package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationLab
import com.mkx.hrttracker.model.pk.PkCalibrationLabIgnoreReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class E2EstimateRepositoryTest {
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 5, 20)

    // Before and after today's 08:00 slot, which is still planned at the first.
    private val beforeSlot = today.atTime(7, 30)
    private val afterSlot = today.atTime(8, 5)

    private val estradiol = testMedicine(
        uuid = UUID.fromString("e2e2e2e2-0000-0000-0000-000000000000"),
        preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
    )
    private val group = MedicationGroup(
        uuid = UUID.fromString("11111111-2222-3333-4444-555555555555"),
        name = "E2",
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = today.minusDays(40),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        ),
        medications = listOf(
            testMedicationGroupMedication(
                uuid = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                medicine = estradiol,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            ),
        ),
        createdAt = Instant.parse("2026-03-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
    )

    /** Every slot logged on time until today's 08:00 one. */
    private val logged: List<MedicationLogEntry> =
        (0..ChronoUnit.DAYS.between(group.schedule.since, today))
            .flatMap { day ->
                group.schedule.timeSlots.map { slot ->
                    val scheduledFor = group.schedule.since.plusDays(day).atTime(slot.time)
                    testMedicationLogEntry(
                        uuid = UUID.nameUUIDFromBytes("dose:$scheduledFor".toByteArray()),
                        medicine = estradiol,
                        doseInstruction = DoseInstruction.TabletFraction(1, 1),
                        sourceGroupUuid = group.uuid,
                        appliedAt = scheduledFor.atZone(zoneId).toInstant(),
                        appliedAtTimeZoneId = zoneId.id,
                        scheduledFor = scheduledFor,
                        scheduleTimeUuid = slot.uuid,
                    )
                }
            }
            .filter { entry -> entry.scheduledFor!!.isBefore(beforeSlot) }

    // What the worker published, after the disk load found nothing valid,
    // and the durable generation.
    private val cache = MutableStateFlow<HomeCacheState?>(HomeCacheState(0L, homeData = null, e2Inputs = null))
    private val generation = MutableStateFlow(0L)

    // Whether the worker has a run pending or going, and the runs the
    // observations asked for while it had none.
    private val rebuilding = MutableStateFlow(false)
    private var runsAsked = 0
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true) {
        every { cache } returns this@E2EstimateRepositoryTest.cache
        every { generation } returns this@E2EstimateRepositoryTest.generation
        every { rebuilding } returns this@E2EstimateRepositoryTest.rebuilding
        every { requestRebuildIfIdle() } answers { if (!rebuilding.value) runsAsked++ }
    }

    private var stored: HomeE2EstimateRecord? = null
    private var writes = 0
    private val estimateStore: HomeE2EstimateStore = mockk {
        coEvery { read() } answers { stored }
        coEvery { write(any()) } answers {
            stored = firstArg()
            writes++
        }
    }
    private val clock = MutableClock(beforeSlot.atZone(zoneId).toInstant())
    private val minutes = MutableStateFlow(beforeSlot)
    private var derivations = 0
    private var calibrationRenders = 0

    @Before
    fun setUp() {
        mockkObject(E2Derivation)
        every { E2Derivation.deriveHomeEstimate(any(), any(), any(), any()) } answers {
            derivations++
            callOriginal()
        }
        every { E2Derivation.deriveCalibrationRender(any(), any(), any()) } answers {
            calibrationRenders++
            callOriginal()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun coldStart_withAStoredMatch_showsItInTheFirstState_withoutDeriving() = runTest {
        publish(record())
        stored = storedFor(record(), beforeSlot)
        val estimates = estimates()

        val states = observe(estimates)
        runCurrent()

        assertSame(stored?.estimate, states.first().value)
        assertNull(states.first().pending)
        assertEquals(0, derivations)
        assertEquals(0, writes)
    }

    @Test
    fun coldStart_withoutAMatch_hasNoValueUntilDerived_thenStoresIt() = runTest {
        publish(record())
        // Stored before the slot passed: its planned doses no longer match.
        stored = storedFor(record(), beforeSlot)
        setNow(afterSlot)
        val estimates = estimates()

        val states = observe(estimates)
        runCurrent()

        assertNull(states.first().value)
        assertEquals(1, derivations)
        val shown = checkNotNull(states.last().value)
        assertNull(states.last().pending)
        assertEquals(keyAt(record(), afterSlot), stored?.key)
        assertSame(shown, stored?.estimate)
        assertEquals(HomeE2ChartWindowOption.SEVEN_DAYS, shown.option)
    }

    @Test
    fun coldStart_withNoInputs_hasNoValue_andAsksForARunUnlessOneIsGoing() = runTest {
        stored = storedFor(record(), beforeSlot)
        val estimates = estimates()

        // The startup's own run is going: the observation adds none.
        rebuilding.value = true
        val states = observe(estimates)
        runCurrent()
        assertEquals(0, runsAsked)

        // With none going, as after that run failed for good, it asks.
        stopAll()
        rebuilding.value = false
        val again = observe(estimates)
        runCurrent()

        assertEquals(listOf(E2State<HomeE2Estimate>(null)), (states + again).distinct())
        assertEquals(0, derivations)
        assertEquals(1, runsAsked)
    }

    @Test
    fun theFirstState_readsTheWallClock_notTheCachedMinute() = runTest {
        // On a resume the minute flow can still hold the time the app left
        // at, from before the slot passed, when the stored estimate matched.
        publish(record())
        stored = storedFor(record(), beforeSlot)
        clock.instant = afterSlot.atZone(zoneId).toInstant()
        val estimates = estimates()

        val states = observe(estimates) // minutes still says 07:30
        runCurrent()

        assertNull(states.first().value)
        assertEquals(keyAt(record(), afterSlot), stored?.key)
        assertTrue(states.none { state -> state.value != null && state.value === storedFor(record(), beforeSlot).estimate })
    }

    @Test
    fun aCachedMinuteAheadOfTheClock_isNeverUsed() = runTest {
        // After the clock or the zone moved back, the minute flow can still
        // hold the later minute, from after the slot passed, until the time
        // source re-reads the clock.
        publish(record())
        minutes.value = afterSlot
        val ahead = storedFor(record(), afterSlot).estimate
        val estimates = estimates()

        val states = observe(estimates)
        runCurrent()
        assertEquals(1, derivations)
        assertEquals(keyAt(record(), beforeSlot), stored?.key)
        assertSame(stored?.estimate, states.last().value)

        // The gated minute flow replays its cached value whenever the screen
        // subscribes again, in the middle of an observation.
        val before = states.size
        minutes.value = afterSlot.plusMinutes(1)
        runCurrent()
        assertEquals(before, states.size)
        assertEquals(1, derivations)
        assertTrue(states.none { state -> state.value == ahead })
    }

    @Test
    fun aRestart_withAMovedKeyHasNoValue_andWithAMatchingKeyHasItAtOnce() = runTest {
        publish(record())
        val estimates = estimates()
        val first = observe(estimates)
        runCurrent()
        val beforeSlotEstimate = checkNotNull(first.last().value)
        stopAll()

        // Away while the slot passed.
        setNow(afterSlot)
        val returned = observe(estimates)
        assertNull(returned.first().value)
        runCurrent()
        val afterSlotEstimate = checkNotNull(returned.last().value)
        assertNotEquals(beforeSlotEstimate, afterSlotEstimate)
        stopAll()

        // Away and back with nothing moved: the value in the first state,
        // before anything else runs.
        val again = observe(estimates)
        assertSame(afterSlotEstimate, again.first().value)
        assertEquals(2, derivations)
    }

    @Test
    fun inputsBehindAMutation_areNeverAdmitted_whileARunningObservationKeepsItsValue() = runTest {
        publish(record())
        stored = storedFor(record(), beforeSlot)
        val estimates = estimates()
        val running = observe(estimates)
        runCurrent()
        val shown = checkNotNull(running.last().value)

        // A mutation commits while its run is paused (or fails): the old
        // inputs and the stored estimate still match each other, but the
        // inputs are behind the durable generation. The shown value holds,
        // a change someone made.
        commit(1L)
        runCurrent()
        assertEquals(E2State(shown, E2Pending.USER), running.last())

        // A screen coming to the foreground meanwhile shows the skeleton, with
        // nothing to hold, and adds no run to the mutation's.
        val returned = observe(estimates)
        runCurrent()
        assertTrue(returned.all { state -> state == E2State<HomeE2Estimate>(null) })
        assertEquals(0, runsAsked)
        assertSame(shown, running.last().value)
        assertEquals(0, derivations)
    }

    @Test
    fun anObservationBehind_asksEachMinuteWhileNoRunIsGoing_untilAPublish() = runTest {
        publish(record())
        val estimates = estimates()
        val running = observe(estimates)
        runCurrent()

        // A mutation commits, and its run is going or backing off: nothing
        // to ask for, at the bump or at the next minute.
        commit(1L)
        runCurrent()
        setNow(beforeSlot.plusMinutes(1))
        runCurrent()
        assertEquals(0, runsAsked)

        // The run failed for good: each minute asks again while it is behind.
        rebuilding.value = false
        setNow(beforeSlot.plusMinutes(2))
        runCurrent()
        assertEquals(1, runsAsked)
        setNow(beforeSlot.plusMinutes(3))
        runCurrent()
        assertEquals(2, runsAsked)

        // A run publishes the mutation: current again, nothing more to ask.
        publish(record(generation = 1L))
        runCurrent()
        setNow(beforeSlot.plusMinutes(4))
        runCurrent()
        assertEquals(2, runsAsked)
        assertNotNull(running.last().value)
        assertNull(running.last().pending)
    }

    @Test
    fun pending_isUser_forNewerInputsFromAChange_andSystem_forTheClock() = runTest {
        publish(record())
        val estimates = estimates()
        val states = observe(estimates)
        runCurrent()
        val initial = checkNotNull(states.last().value)

        // Logging a dose: a gated change, so the next record's generation is newer.
        val withDose = logged + dose(today.minusDays(1).atTime(12, 0))
        commit(1L)
        publish(record(generation = 1L, logged = withDose))
        runCurrent()
        val afterLog = checkNotNull(states.last().value)
        val userTransition = states.dropWhile { state -> state.value !== initial || state.pending == null }
        assertEquals(E2State(initial, E2Pending.USER), userTransition.first())

        // The slot passing while the app is open: the same generation.
        setNow(afterSlot)
        runCurrent()
        val swapped = states.dropWhile { state -> state.value !== afterLog }
        assertEquals(E2State(afterLog, E2Pending.SYSTEM), swapped[1])
        assertNull(swapped.last().pending)
        assertTrue(swapped.last().value !== afterLog)
    }

    @Test
    fun aChangeTheEstimateDoesntRead_holdsFromItsCommit_andReleasesAtThePublish() = runTest {
        publish(record())
        val estimates = estimates()
        val states = observe(estimates)
        runCurrent()
        val shown = checkNotNull(states.last().value)
        val before = states.size

        // A stock recount: a gated change whose E2 inputs are the same. Held
        // through its run; the key doesn't move, so the publish releases it.
        commit(1L)
        runCurrent()
        publish(record(generation = 1L))
        runCurrent()
        assertEquals(listOf(E2State(shown, E2Pending.USER), E2State(shown, null)), states.drop(before))

        // And the clock after it is still the system's, not the user's.
        setNow(afterSlot)
        runCurrent()
        assertEquals(E2State(shown, E2Pending.SYSTEM), states[before + 2])
        assertEquals(1 + 1, derivations)
    }

    @Test
    fun aHold_releasesWhenTheWorkerGivesUp_keepingTheValue_andTheReAsksDontHoldAgain() = runTest {
        publish(record())
        val estimates = estimates()
        val states = observe(estimates)
        runCurrent()
        val shown = checkNotNull(states.last().value)

        // A logged dose: held while its run is pending or going, retries included.
        commit(1L)
        runCurrent()
        assertEquals(E2State(shown, E2Pending.USER), states.last())

        // The run failed four times: the value stays, released, nothing admitted.
        rebuilding.value = false
        runCurrent()
        assertEquals(E2State(shown, null), states.last())
        assertEquals(0, runsAsked)

        // The next minute asks again, and that run goes: silent for the same generation.
        setNow(beforeSlot.plusMinutes(1))
        runCurrent()
        assertEquals(1, runsAsked)
        rebuilding.value = true
        runCurrent()
        assertEquals(E2State(shown, null), states.last())

        // Its publish: the user's change, derived, then shown.
        val before = states.size
        publish(record(generation = 1L, logged = logged + dose(today.minusDays(1).atTime(12, 0))))
        runCurrent()
        assertEquals(E2State(shown, E2Pending.USER), states[before])
        assertNull(states.last().pending)
        assertTrue(checkNotNull(states.last().value) !== shown)
    }

    @Test
    fun observers_shareInFlightDerivations_andTheMemoKeepsTheLastTwoKeys() = runTest {
        publish(record(weightKg = 60.0))
        val estimates = estimates()
        val home = observe(estimates)
        val settings = observe(estimates)
        backgroundScope.launch { estimates.warmHome() }
        runCurrent()
        assertEquals(1, derivations)
        assertSame(home.last().value, settings.last().value)

        publish(record(weightKg = 61.0))
        runCurrent()
        publish(record(weightKg = 62.0))
        runCurrent()
        assertEquals(3, derivations)

        // 60 kg was the eldest of three: derived again. 62 kg is kept.
        publish(record(weightKg = 60.0))
        runCurrent()
        assertEquals(4, derivations)
        publish(record(weightKg = 62.0))
        runCurrent()
        assertEquals(4, derivations)
    }

    @Test
    fun aDerivationThatThrows_isMemoized_butNeverStored() = runTest {
        every { E2Derivation.deriveHomeEstimate(any(), any(), any(), any()) } answers {
            derivations++
            throw IllegalStateException("simulation")
        }
        publish(record())
        val estimates = estimates()
        val states = observe(estimates)
        runCurrent()
        // A minute tick with the same key does not derive it again.
        setNow(beforeSlot.plusMinutes(1))
        runCurrent()

        assertTrue(states.all { state -> state.value == null })
        assertNull(states.last().pending)
        assertEquals(1, derivations)
        assertEquals(0, writes)
    }

    @Test
    fun aBandRenderThatThrows_isShownWithoutItsBand_butNeverStored() = runTest {
        mockkObject(PkCalibrationRenderer)
        every {
            PkCalibrationRenderer.render(
                result = any(),
                originEpochMillis = any(),
                weightKg = any(),
                config = any(),
                domain = any(),
                doseEvents = any(),
                statusRange = any(),
            )
        } throws IllegalStateException("render")
        publish(record(fit = solvedFit(epoch = 0)))
        val estimates = estimates()

        val states = observe(estimates)
        runCurrent()

        val shown = checkNotNull(states.last().value)
        assertTrue(shown.bandKnots.isEmpty())
        val status = checkNotNull(shown.calibration)
        assertTrue(status.adjusted)
        assertTrue(status.bandUnavailable)
        assertEquals(0, writes)
    }

    @Test
    fun theWriter_neverStoresAnEstimateWhoseKeyMovedWhileItWasDerived() = runTest {
        publish(record())
        // A slow derivation: the slot passes before it lands.
        every { E2Derivation.deriveHomeEstimate(any(), any(), any(), any()) } answers {
            derivations++
            callOriginal().also { clock.instant = afterSlot.atZone(zoneId).toInstant() }
        }
        val estimates = estimates()

        backgroundScope.launch { estimates.warmHome() }
        runCurrent()
        assertEquals(0, writes)

        every { E2Derivation.deriveHomeEstimate(any(), any(), any(), any()) } answers {
            derivations++
            callOriginal()
        }
        backgroundScope.launch { estimates.warmHome() }
        runCurrent()
        assertEquals(1, writes)
        assertEquals(keyAt(record(), afterSlot), stored?.key)
    }

    @Test
    fun tryAgain_afterAFailedSolve_reDerivesFromUnchangedInputs() = runTest {
        // The build's solve failed: the population estimate is shown and
        // stored. Try again re-solves with a new epoch.
        val failed = E2FitRecord(solvedFit(epoch = 0).fitId, epoch = 0, outcome = FitOutcome.Failed)
        publish(record(fit = failed))
        val estimates = estimates()
        val states = observe(estimates)
        runCurrent()
        assertNull(checkNotNull(states.last().value).calibration)

        commit(1L)
        publish(record(generation = 1L, fit = solvedFit(epoch = 1)))
        runCurrent()

        assertEquals(2, derivations)
        assertNotNull(checkNotNull(states.last().value).calibration)
        assertEquals(keyAt(record(fit = solvedFit(epoch = 1)), beforeSlot), stored?.key)
    }

    @Test
    fun tryAgain_afterAThrownRender_reDerives_evenWithTheSameOutcome() = runTest {
        // The render failed, not the solve: the re-solve gives the same
        // outcome, and only the epoch keeps the memo from returning the
        // band-less estimate.
        var renders = 0
        mockkObject(PkCalibrationRenderer)
        every {
            PkCalibrationRenderer.render(
                result = any(),
                originEpochMillis = any(),
                weightKg = any(),
                config = any(),
                domain = any(),
                doseEvents = any(),
                statusRange = any(),
            )
        } answers {
            if (renders++ == 0) throw IllegalStateException("render") else callOriginal()
        }
        publish(record(fit = solvedFit(epoch = 0)))
        val estimates = estimates()
        val states = observe(estimates)
        runCurrent()
        assertTrue(checkNotNull(states.last().value).bandKnots.isEmpty())
        assertEquals(0, writes)

        commit(1L)
        publish(record(generation = 1L, fit = solvedFit(epoch = 1)))
        runCurrent()

        assertEquals(2, derivations)
        assertTrue(checkNotNull(states.last().value).bandKnots.isNotEmpty())
        assertEquals(1, writes)
    }

    @Test
    fun warmHome_storesHomesKey_andAColdStartRightAfterShowsIt() = runTest {
        publish(record())
        val estimates = estimates()

        backgroundScope.launch { estimates.warmHome() }
        runCurrent()
        assertEquals(keyAt(record(), beforeSlot), stored?.key)

        val states = observe(estimates)
        assertSame(stored?.estimate, states.first().value)
        assertEquals(1, derivations)
    }

    @Test
    fun warmHome_leavesInputsThatArentCurrent_alone() = runTest {
        publish(record())
        commit(1L)
        val estimates = estimates()

        backgroundScope.launch { estimates.warmHome() }
        runCurrent()

        assertEquals(0, derivations)
        assertEquals(0, writes)
    }

    @Test
    fun warmHome_withAnUnchangedKey_onlyComputesTheKey_andAfterASlotPassesDerivesOnce() = runTest {
        publish(record())
        stored = storedFor(record(), beforeSlot)
        val estimates = estimates()

        backgroundScope.launch { estimates.warmHome() }
        runCurrent()
        assertEquals(0, derivations)
        assertEquals(0, writes)

        clock.instant = afterSlot.atZone(zoneId).toInstant()
        backgroundScope.launch { estimates.warmHome() }
        runCurrent()
        backgroundScope.launch { estimates.warmHome() }
        runCurrent()
        assertEquals(1, derivations)
        assertEquals(1, writes)
        assertEquals(keyAt(record(), afterSlot), stored?.key)
    }

    @Test
    fun widgetEstimate_isDerivedFromTheInputsItIsGiven_sharedByKey_andNeverStored() = runTest {
        var widgetDerivations = 0
        every { E2Derivation.deriveWidgetEstimate(any(), any(), any()) } answers {
            widgetDerivations++
            callOriginal()
        }
        val estimates = estimates()

        // Logged doses only: the slot passing between the two doesn't move
        // the widget's key, so they share one derivation.
        val before = async { estimates.widgetEstimate(record(), beforeSlot) }
        val after = async { estimates.widgetEstimate(record(), afterSlot) }
        val shown = checkNotNull(before.await())
        assertSame(shown, after.await())
        assertEquals(1, widgetDerivations)
        assertEquals(0, writes)
        assertEquals(E2Derivation.deriveWidgetEstimate(record().e2Inputs(), beforeSlot, zoneId), shown)
        // From the record it is handed, whatever the cache holds.
        assertNotEquals(shown, estimates.widgetEstimate(record(weightKg = 80.0), beforeSlot))
    }

    @Test
    fun aFailedUserDerivation_leavesTheNextClockChangeToTheSystem() = runTest {
        val extra = dose(today.minusDays(1).atTime(12, 0))
        every { E2Derivation.deriveHomeEstimate(any(), any(), any(), any()) } answers {
            derivations++
            if (extra in firstArg<E2Inputs>().estradiolEntries) throw IllegalStateException("simulation")
            callOriginal()
        }
        publish(record())
        val estimates = estimates()
        val states = observe(estimates)
        runCurrent()
        val shown = checkNotNull(states.last().value)

        // A logged dose whose estimate fails: the value stays, nothing pending.
        commit(1L)
        publish(record(generation = 1L, logged = logged + extra))
        runCurrent()
        assertEquals(E2State(shown, null), states.last())
        assertTrue(E2State(shown, E2Pending.USER) in states)

        // The slot passing after it is the clock's change, not the user's.
        val before = states.size
        setNow(afterSlot)
        runCurrent()
        assertEquals(E2State(shown, E2Pending.SYSTEM), states[before])
    }

    @Test
    fun latestHome_isSeededFromTheStoredEstimate_andFollowsWhatAnObservationAdmits() = runTest {
        publish(record())
        stored = storedFor(record(), beforeSlot)
        val estimates = estimates()
        val seed = checkNotNull(stored).estimate
        assertSame(seed, estimates.latestHome.value)

        // A warm-up derives and stores the moved key but leaves latestHome
        // alone; the observation admitting it from the memo updates it.
        clock.instant = afterSlot.atZone(zoneId).toInstant()
        backgroundScope.launch { estimates.warmHome() }
        runCurrent()
        assertEquals(keyAt(record(), afterSlot), stored?.key)
        assertSame(seed, estimates.latestHome.value)
        setNow(afterSlot)
        val states = observe(estimates)
        assertSame(states.first().value, estimates.latestHome.value)
        assertEquals(1, derivations)

        // A derivation updates it too.
        commit(1L)
        publish(record(generation = 1L, weightKg = 70.0))
        runCurrent()
        assertSame(states.last().value, estimates.latestHome.value)
        assertEquals(2, derivations)
    }

    @Test
    fun latestHome_isNotTakenBackByAWarmUpForAnOlderKey() = runTest {
        publish(record())
        setNow(afterSlot)
        val estimates = estimates()
        val states = observe(estimates)
        runCurrent()
        val newer = checkNotNull(states.last().value)

        // A warm-up that read the clock before the slot passed and lands now.
        clock.instant = beforeSlot.atZone(zoneId).toInstant()
        backgroundScope.launch { estimates.warmHome() }
        runCurrent()

        assertEquals(2, derivations)
        assertSame(newer, estimates.latestHome.value)
    }

    @Test
    fun calibration_coldStart_hasNoValueUntilDerived_andARestartWithAMatchHasItAtOnce() = runTest {
        // Never stored, so a process's first observation derives it; later
        // ones (the page again, the editor, Home's sheet) share it.
        publish(record(fit = solvedFit(epoch = 0)))
        val estimates = estimates()

        val first = observeCalibration(estimates)
        assertNull(first.first().value)
        runCurrent()
        val shown = checkNotNull(first.last().value)
        assertNull(first.last().pending)
        assertNotNull(shown.result)
        assertNotNull(shown.render)
        stopAll()

        val again = observeCalibration(estimates)
        assertSame(shown, again.first().value)
        assertEquals(1, calibrationRenders)
        assertEquals(0, writes)
    }

    @Test
    fun calibration_aRestart_afterTheKeyMoved_hasNoValue_untilItIsDerived() = runTest {
        publish(record(fit = solvedFit(epoch = 0)))
        val estimates = estimates()
        val first = observeCalibration(estimates)
        runCurrent()
        val yesterday = checkNotNull(first.last().value)
        stopAll()

        // Away past midnight: the page's domain moved with the date.
        setNow(today.plusDays(1).atTime(9, 0))
        val returned = observeCalibration(estimates)
        assertNull(returned.first().value)
        runCurrent()
        assertNotEquals(yesterday, checkNotNull(returned.last().value))
        assertEquals(2, calibrationRenders)
    }

    @Test
    fun calibration_pending_isUser_forAReview_andSystem_forMidnight() = runTest {
        publish(record(fit = solvedFit(epoch = 0)))
        val estimates = estimates()
        val states = observeCalibration(estimates)
        runCurrent()
        val initial = checkNotNull(states.last().value)

        // Accepting a lab: a gated change whose re-solved fit moves the key.
        val review = listOf(E2CalibrationMetadata(fitLabs.first().resultId, E2CalibrationDisposition.REVIEWED, Instant.EPOCH))
        commit(1L)
        publish(record(generation = 1L, fit = solvedFit(epoch = 0, metadata = review), labMetadata = review))
        runCurrent()
        val reviewed = checkNotNull(states.last().value)
        val userTransition = states.dropWhile { state -> state.value !== initial || state.pending == null }
        assertEquals(E2State(initial, E2Pending.USER), userTransition.first())
        assertEquals(setOf(fitLabs.first().resultId), reviewed.acceptedLabIds)

        // Midnight while the page is open: the same generation.
        setNow(today.plusDays(1).atTime(0, 1))
        runCurrent()
        val swapped = states.dropWhile { state -> state.value !== reviewed }
        assertEquals(E2State(reviewed, E2Pending.SYSTEM), swapped[1])
        assertNull(swapped.last().pending)
        assertTrue(swapped.last().value !== reviewed)
    }

    @Test
    fun calibration_aChangeTheEstimateDoesntRead_holdsFromItsCommit_andReleasesAtThePublish() = runTest {
        publish(record(fit = solvedFit(epoch = 0)))
        val estimates = estimates()
        val states = observeCalibration(estimates)
        runCurrent()
        val shown = checkNotNull(states.last().value)
        val before = states.size

        // A stock recount, its stored fit reused, held through its run; then
        // the morning slot passing, which moves Home's key but not the page's.
        commit(1L)
        runCurrent()
        publish(record(generation = 1L, fit = solvedFit(epoch = 0)))
        runCurrent()
        setNow(afterSlot)
        runCurrent()

        assertEquals(listOf(E2State(shown, E2Pending.USER), E2State(shown, null)), states.drop(before))
        assertEquals(1, calibrationRenders)
    }

    @Test
    fun calibration_aRenderThatThrows_isTheNumericFailure_memoized_butNeverStored() = runTest {
        every { E2Derivation.deriveCalibrationRender(any(), any(), any()) } answers {
            calibrationRenders++
            throw IllegalStateException("render")
        }
        publish(record(fit = solvedFit(epoch = 0)))
        val estimates = estimates()
        val first = observeCalibration(estimates)
        runCurrent()
        val failed = checkNotNull(first.last().value)
        assertNull(failed.result)
        assertNull(failed.render)
        assertNull(first.last().pending)

        // A restart with the same key takes it from the memo: the page, the
        // editor and Home's sheet don't each render it again.
        stopAll()
        val again = observeCalibration(estimates)
        runCurrent()

        assertSame(failed, again.first().value)
        assertEquals(1, calibrationRenders)
        assertEquals(0, writes)
    }

    @Test
    fun calibration_coldStart_flagsComeFromTheStoredLabsAndMetadata_andMatchTheFit() = runTest {
        // What the build stored beside the fit: an accepted, an excluded and
        // a non-positive result. The estimator has no database at all (the
        // snapshot repository's is a bare mock), so no flag waits for Room.
        val nonPositive = PkCalibrationLab(
            resultId = UUID(0L, 9L),
            collectedAtEpochMillis = today.minusDays(4).atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli(),
            valuePgml = 0.0,
        )
        val labs = fitLabs + nonPositive
        val metadata = listOf(
            E2CalibrationMetadata(labs[0].resultId, E2CalibrationDisposition.REVIEWED, Instant.EPOCH),
            E2CalibrationMetadata(labs[1].resultId, E2CalibrationDisposition.EXCLUDED, Instant.EPOCH),
        )
        val input = buildPkCalibrationInput(labs = labs, entries = logged, weightKg = 62.0, metadata = metadata)
        val fit = E2FitRecord(
            fitId = E2Derivation.fitId(input),
            epoch = 0,
            outcome = FitOutcome.Solved(PkCalibrationEngine.evaluate(input).result, input.originEpochMillis),
        )
        publish(record(fit = fit, labs = labs, labMetadata = metadata))
        val estimates = estimates()

        val states = observeCalibration(estimates)
        runCurrent()

        val shown = checkNotNull(states.last().value)
        assertEquals(setOf(labs[0].resultId), shown.acceptedLabIds)
        assertEquals(setOf(labs[1].resultId), shown.excludedLabIds)
        assertEquals(setOf(nonPositive.resultId), shown.nonPositiveLabIds)
        // The same run's fit: solved from these dispositions, it set the
        // non-positive result aside, and the excluded one never reached it.
        assertEquals(input.acceptedLabIds, shown.acceptedLabIds)
        assertEquals(input.excludedLabIds, shown.excludedLabIds)
        val result = checkNotNull(shown.result)
        assertEquals(PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE, result.ignoredLabs[nonPositive.resultId])
        assertTrue(labs[1].resultId !in result.ignoredLabs)
    }

    private val observers = mutableListOf<Job>()

    private fun TestScope.estimates(): E2EstimateRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val estimates = E2EstimateRepository(
            homeSnapshotRepository = homeSnapshotRepository,
            store = estimateStore,
            clock = clock,
            appScope = backgroundScope,
            defaultDispatcher = dispatcher,
        )
        // The stored estimate's load.
        runCurrent()
        return estimates
    }

    private fun TestScope.observe(estimates: E2EstimateRepository): List<E2State<HomeE2Estimate>> {
        val states = mutableListOf<E2State<HomeE2Estimate>>()
        observers += backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            estimates.observeHome(minutes).collect { state -> states += state }
        }
        return states
    }

    private fun TestScope.observeCalibration(estimates: E2EstimateRepository): List<E2State<CalibrationEstimate>> {
        val states = mutableListOf<E2State<CalibrationEstimate>>()
        observers += backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            estimates.observeCalibration(minutes).collect { state -> states += state }
        }
        return states
    }

    private fun stopAll() {
        observers.forEach(Job::cancel)
        observers.clear()
    }

    private fun setNow(now: LocalDateTime) {
        clock.instant = now.atZone(zoneId).toInstant()
        minutes.value = now
    }

    private fun record(
        generation: Long = 0L,
        weightKg: Double = 62.0,
        logged: List<MedicationLogEntry> = this.logged,
        fit: E2FitRecord? = null,
        labs: List<PkCalibrationLab> = emptyList(),
        labMetadata: List<E2CalibrationMetadata> = emptyList(),
    ) = E2InputsRecord(
        generation = generation,
        anchorDateEpochDay = today.toEpochDay(),
        zoneId = zoneId.id,
        option = HomeE2ChartWindowOption.SEVEN_DAYS,
        weightKg = weightKg,
        estradiolEntries = logged,
        plannedSources = listOf(group),
        labs = labs,
        labMetadata = labMetadata,
        fit = fit,
    )

    /** A run's publish: its E2 inputs, at their generation. */
    // A gated mutation's commit: its run pending, then the bump.
    private fun commit(generation: Long) {
        rebuilding.value = true
        this.generation.value = generation
    }

    private fun publish(inputs: E2InputsRecord) {
        cache.value = HomeCacheState(inputs.generation, homeData = null, e2Inputs = inputs)
    }

    private fun keyAt(record: E2InputsRecord, now: LocalDateTime): HomeEstimateKey =
        E2Derivation.homeEstimateKey(record.e2Inputs(), now, zoneId)

    private fun storedFor(record: E2InputsRecord, now: LocalDateTime): HomeE2EstimateRecord {
        val inputs = record.e2Inputs()
        return storedEstimates.getOrPut(keyAt(record, now)) {
            HomeE2EstimateRecord(
                key = keyAt(record, now),
                estimate = E2Derivation.deriveHomeEstimate(inputs, now, zoneId) { throw it }.also { derivations-- },
            )
        }
    }

    private val storedEstimates = mutableMapOf<HomeEstimateKey, HomeE2EstimateRecord>()

    // An hour after a morning dose, on three days: oral is adjusted, with a band.
    private val fitLabs = listOf(10L, 6L, 2L).mapIndexed { index, daysAgo ->
        PkCalibrationLab(
            resultId = UUID(0L, index + 1L),
            collectedAtEpochMillis = today.minusDays(daysAgo).atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli(),
            valuePgml = 140.0,
        )
    }

    private fun solvedFit(epoch: Int, metadata: List<E2CalibrationMetadata> = emptyList()): E2FitRecord {
        val input = buildPkCalibrationInput(labs = fitLabs, entries = logged, weightKg = 62.0, metadata = metadata)
        return E2FitRecord(
            fitId = E2Derivation.fitId(input),
            epoch = epoch,
            outcome = FitOutcome.Solved(PkCalibrationEngine.evaluate(input).result, input.originEpochMillis),
        )
    }

    private fun dose(at: LocalDateTime) = testMedicationLogEntry(
        uuid = UUID.nameUUIDFromBytes("extra:$at".toByteArray()),
        medicine = estradiol,
        doseInstruction = DoseInstruction.TabletFraction(1, 1),
        sourceGroupUuid = null,
        appliedAt = at.atZone(zoneId).toInstant(),
        appliedAtTimeZoneId = zoneId.id,
    )

    private class MutableClock(@Volatile var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }
}
