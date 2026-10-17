package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationLab
import com.mkx.hrttracker.startup.StartupTiming
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.AppTimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Home data and E2 inputs as one run published them, or as the disk load
 * found them. A part is null only when the load found its file invalid; a
 * run always publishes both.
 */
data class HomeCacheState(
    val generation: Long,
    val homeData: HomeSnapshotRecord?,
    val e2Inputs: E2InputsRecord?,
)

/**
 * The mutation gate, the rebuild worker, and the two parts the worker keeps
 * in step with Room: Home data and the E2 inputs. Only the worker reads Room
 * to build them, one run at a time.
 */
@Singleton
class HomeSnapshotRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotStore: HomeSnapshotStore,
    private val homeSnapshotGenerationStore: HomeSnapshotGenerationStore,
    private val settingsRepository: SettingsRepository,
    // A provider: the estimator reads its inputs from this repository.
    private val e2EstimateRepository: Provider<E2EstimateRepository>,
    private val appTimeSource: AppTimeSource,
    @param:AppScope private val appScope: CoroutineScope,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val mutationMutex = Mutex()

    /** The durable generation, held in memory; each gated mutation bumps it under the gate's lock. */
    val generation: StateFlow<Long>
        get() = generationState
    private val generationState = MutableStateFlow(0L)

    /**
     * Home data and E2 inputs: loaded from disk at startup, then replaced by
     * each run's publish; null until the load. A state is current while its
     * generation equals [generation]. After a mutation the old state stays,
     * for the screens already showing it, until the mutation's run publishes.
     * Nothing checks the date or the zone again in memory: a part goes
     * invalid by date only after ten days, and the date and zone broadcasts
     * (WidgetDateReceiver, MedicationReminderRescheduleReceiver) ask for a
     * run at every date and zone change.
     */
    val cache: StateFlow<HomeCacheState?>
        get() = cacheState
    private val cacheState = MutableStateFlow<HomeCacheState?>(null)

    /** [cache] while it is current: null before the load, and from a mutation until its run publishes. */
    val currentCache: Flow<HomeCacheState?> = combine(cacheState, generationState) { state, current ->
        state?.takeIf { it.generation == current }
    }.distinctUntilChanged()

    /**
     * The fit epoch Try again asks for. Every run reads it when it starts,
     * re-solves while the published fit's epoch is below it, and gives a fit
     * it solves that epoch. It is state, not part of a request, so a retry
     * outlives conflated requests and discarded or failed runs until a
     * publish carries it, and its new epoch changes every estimate key built
     * on the fit. Starts at the loaded fit's. Guarded by [mutationMutex].
     */
    private var requestedFitEpoch = 0

    // Conflated: every request made before a run starts becomes that run.
    // Requests carry nothing; a run reads everything it needs itself.
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val runsStarted = AtomicLong(0L)
    private val runsFinished = MutableStateFlow(0L)

    /**
     * True while a run is pending or going, its retries' backoff included;
     * false while the worker waits for a request, including after it gave
     * up. A mutation sets it under the gate's lock before it bumps the
     * generation, and the worker clears it under the same lock only when no
     * request waits, so a mutation's run, or the one following a discarded
     * run, never shows as idle: false behind a mutation means the worker
     * gave up on it.
     */
    val rebuilding: StateFlow<Boolean>
        get() = rebuildingState
    private val rebuildingState = MutableStateFlow(false)

    init {
        // Undispatched, so the load holds the gate's lock before anything can
        // call the gate: no mutation lands before it reads the durable
        // generation and the fit's epoch. The disk reads themselves suspend,
        // and decode on DataStore's own thread.
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            load()
            while (true) {
                // Idle only with no request waiting, checked under the lock a
                // mutation marks its run pending under (see [rebuilding]).
                val waiting = mutationMutex.withLock {
                    requests.tryReceive().isSuccess.also { waiting -> if (!waiting) rebuildingState.value = false }
                }
                if (!waiting) requests.receive()
                rebuildingState.value = true
                // A failed run is retried after each backoff, or at once on a
                // newer request, and then waits for the next request.
                var retries = 0
                while (!run() && retries < RETRY_BACKOFF_MILLIS.size) {
                    withTimeoutOrNull(RETRY_BACKOFF_MILLIS[retries++]) { requests.receive() }
                }
            }
        }
    }

    // All medication group, medication log, and profile writes that affect Home data
    // must go through this gate, so the cached parts stop being current before the DB
    // changes.
    //
    // The work inside the lock runs in NonCancellable: once the generation is bumped,
    // the mutation must finish and its run must be asked for, or a caller whose scope
    // dies mid-mutation (e.g. an OnboardingViewModel's viewModelScope when the user
    // finishes onboarding right after saving weight) leaves the cache behind Room until
    // something else asks for a run. Lock acquisition stays cancellable so callers
    // blocked on a long-running peer can still be cancelled cleanly.
    //
    // The run itself is the worker's: the caller returns as soon as the DB commit lands
    // instead of waiting through the calibration solve and the encrypted writes, and
    // other mutations are not queued behind them. Nothing is cleared: the old state
    // stays for the screens already showing it, but it is no longer current, so a
    // screen that starts its observation waits for the run's publish.
    suspend fun <T> runHomeDataMutation(block: suspend () -> T): T {
        val result = mutationMutex.withLock {
            withContext(NonCancellable) {
                diagnosticsLogger.info(TAG, "home_data_mutation_start")
                val generation = try {
                    homeSnapshotGenerationStore.incrementGeneration()
                } catch (throwable: Throwable) {
                    // The only trace of a write, or a Try again, that never ran.
                    diagnosticsLogger.warning(TAG, "home_data_mutation_generation_failed", throwable)
                    throw throwable
                }
                // Before the bump: an observation that sees the new
                // generation sees its run pending, not a worker that gave up.
                rebuildingState.value = true
                generationState.value = generation
                diagnosticsLogger.info(
                    TAG,
                    "home_data_mutation_generation_incremented generation=$generation"
                )
                runCatching { block() }.also { result ->
                    result.onSuccess {
                        diagnosticsLogger.info(TAG, "home_data_mutation_committed generation=$generation")
                    }
                    requestRebuild()
                }
            }
        }
        return result.getOrThrow()
    }

    /**
     * A change Home data reads that no gated write makes: the journal's first
     * pinned date. It bumps the generation and asks for a run; the E2 keys
     * don't move, so nothing holds.
     */
    suspend fun invalidate() {
        runHomeDataMutation {}
    }

    /**
     * Calibration's Try again: a gated change like any other, so screens hold
     * on it as a user change, that also raises [requestedFitEpoch]. Runs
     * re-solve until one publishes its fit with the new epoch, so every
     * estimate built on the fit is derived again, even from unchanged inputs.
     */
    suspend fun retryCalibration() {
        runHomeDataMutation { requestedFitEpoch += 1 }
    }

    /** Asks the worker for a run; every request made before a run starts becomes that run. */
    fun requestRebuild() {
        requests.trySend(Unit)
    }

    /**
     * Asks for a run unless one is pending or going, retries included: for
     * observations, which ask again at each minute while their inputs aren't
     * current, so one whose run failed for good gets another. A request
     * racing the worker's give-up is lost; the next minute asks again.
     */
    fun requestRebuildIfIdle() {
        if (!rebuildingState.value) requestRebuild()
    }

    /**
     * Asks for a run, and waits until one that started after the call has
     * published, its warm-up included, or failed. A run that a newer mutation
     * discarded doesn't count: that mutation's own run follows it. Cancelling
     * the wait leaves the run going in the app scope.
     */
    suspend fun rebuildNow() {
        val started = runsStarted.get()
        requestRebuild()
        runsFinished.first { finished -> finished > started }
    }

    /** [cache] once the disk load is done, while it is current; null otherwise. */
    suspend fun currentState(): HomeCacheState? {
        val state = cacheState.filterNotNull().first()
        return state.takeIf { state.generation == generationState.value }
    }

    fun scheduleEntriesForHome(
        snapshot: HomeSnapshotRecord,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<MedicationLogEntry> {
        val window = HomeSnapshotWindow.forNow(now = now, zoneId = zoneId)
        return snapshot.scheduleEntries.filter { entry ->
            val scheduledFor = entry.scheduledFor
            if (scheduledFor != null) {
                scheduledFor >= window.homeScheduledStart &&
                        scheduledFor <= window.homeScheduledEnd
            } else {
                val appliedAtEpochMillis = entry.appliedAt.toEpochMilli()
                appliedAtEpochMillis >= window.homeManualStartEpochMillis &&
                        appliedAtEpochMillis < window.homeManualEndEpochMillis
            }
        }
    }

    /**
     * Seeds [generation] and [cache] from disk before the worker's first run:
     * each part only when its own file is valid at the durable generation.
     * The three files are read at once, and each part is marked as it lands.
     */
    private suspend fun load() {
        mutationMutex.withLock {
            val zoneId = ZoneId.systemDefault()
            val today = appTimeSource.now().atZone(zoneId).toLocalDate()
            val (durable, homeData, e2Inputs) = coroutineScope {
                // Unreadable, it can't vouch for either file: both wait for a run.
                val durable = async {
                    readOrNull("home_snapshot_generation") { homeSnapshotGenerationStore.readGeneration() }
                }
                val homeData = async {
                    val record = readOrNull("home_snapshot") { homeSnapshotStore.readSnapshot() }
                    val generation = durable.await()
                    record
                        ?.takeIf { generation != null && isValid("home_snapshot", it.validityFailure(generation, today, zoneId)) }
                        .also { part -> StartupTiming.mark(if (part == null) "home_snapshot_missing" else "home_snapshot_loaded") }
                }
                val e2Inputs = async {
                    val record = readOrNull("home_e2_inputs") { homeSnapshotStore.readE2Inputs() }
                    val generation = durable.await()
                    record
                        ?.takeIf { generation != null && isValid("home_e2_inputs", it.validityFailure(generation, today, zoneId)) }
                        .also { part -> StartupTiming.mark(if (part == null) "home_e2_inputs_missing" else "home_e2_inputs_loaded") }
                }
                Triple(durable.await(), homeData.await(), e2Inputs.await())
            }
            durable?.let { generationState.value = it }
            requestedFitEpoch = e2Inputs?.fit?.epoch ?: 0
            cacheState.value = HomeCacheState(generationState.value, homeData, e2Inputs)
            diagnosticsLogger.info(
                TAG,
                "home_cache_loaded generation=${generationState.value} " +
                        "homeData=[${homeData?.diagnosticSummary() ?: "none"}] " +
                        "e2Inputs=[${e2Inputs?.diagnosticSummary() ?: "none"}]"
            )
            // The only date and zone check: at startup. A part missing then,
            // for its generation, schema or engine, or its zone or ten-day
            // window, is rebuilt now; later date and zone changes reach the
            // repository as the broadcasts' runs (see [cache]).
            if (homeData == null || e2Inputs == null) requestRebuild()
        }
    }

    /** One run; false when it failed, so the worker retries it. */
    private suspend fun run(): Boolean {
        // A request made before the run reads anything is answered by it, so
        // a mutation followed by rebuildNow() costs one run, not two. Before
        // the count: rebuildNow() waits for a run counted after its request.
        // Under the gate's lock: an observation asks at a mutation's bump,
        // while it holds the lock, and the mutation's own request follows;
        // both are answered by the run that reads its commit.
        mutationMutex.withLock { requests.tryReceive() }
        val number = runsStarted.incrementAndGet()
        val published = try {
            build()
        } catch (throwable: Throwable) {
            // Only the worker's own cancellation ends it. Anything else, an
            // Error or a stray cancellation from inside the run included, is a
            // failed run: escaping into the handler-less app scope would crash
            // the process, and then every start through its startup run.
            currentCoroutineContext().ensureActive()
            // Nothing was published: screens keep what they show, and one that
            // starts its observation shows the skeleton until a run succeeds.
            diagnosticsLogger.warning(TAG, "home_snapshot_run_failed run=$number", throwable)
            runsFinished.value = number
            return false
        }
        // A discarded run doesn't count for rebuildNow(): the mutation that
        // discarded it asked for a run after this one read the generation,
        // so another always follows.
        if (published) runsFinished.value = number
        return true
    }

    /** One run's work; false when a mutation committed meanwhile and it published nothing. */
    private suspend fun build(): Boolean {
        // Under the lock, so no mutation is half done.
        val (generation, fitEpoch) = mutationMutex.withLock { generationState.value to requestedFitEpoch }
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(appTimeSource.now(), zoneId)
        diagnosticsLogger.info(TAG, "home_snapshot_run_start generation=$generation now=$now")
        // The persisted option, never settingsState's placeholder.
        val option = settingsRepository.homeE2ChartWindowOptionFlow.first()
        val snapshotWindow = HomeSnapshotWindow.forNow(now = now, zoneId = zoneId)

        val inputs = withContext(Dispatchers.IO) {
            // awaitOpen, not get(): the blocking get() is uncancellable, and a
            // stalled SQLCipher open would hold the worker with it.
            val database = databaseHolder.awaitOpen()
            val homeDao = database.homeDao()
            val medicineDao = database.medicineDao()
            val medicationLogDao = database.medicationLogDao()
            val activeGroupEntities = homeDao.getActiveGroups()
            val archivedGroupEntities = homeDao.getArchivedGroups()
            val scheduleEntryEntities = homeDao.getScheduleEntries(
                scheduledStartIso = snapshotWindow.bufferedScheduledStart.toString(),
                scheduledEndIso = snapshotWindow.bufferedScheduledEnd.toString(),
                manualStartEpochMillis = snapshotWindow.bufferedManualStartEpochMillis,
                manualEndEpochMillis = snapshotWindow.bufferedManualEndEpochMillis,
            )
            val antiandrogenHistoryEntities = homeDao.getLatestAntiandrogenEntriesOnOrBefore(
                onOrBeforeEpochMillis = snapshotWindow.onOrBeforeEpochMillis,
            )
            val latestEstradiolEntryEntity = homeDao.getLatestEstradiolEntryOnOrBefore(
                onOrBeforeEpochMillis = snapshotWindow.onOrBeforeEpochMillis,
            )
            val stockMedicineEntities = medicineDao.getAllActiveTrackedEntities()
            val stockWindowStartIso = now.toLocalDate()
                .minusDays(1)
                .atStartOfDay()
                .toString()
            val stockWindowEndIso = now.toLocalDate()
                .plusDays(ScheduledRunwayCalculator.HORIZON_DAYS)
                .atTime(23, 59, 59)
                .toString()
            val stockFulfillmentEntities = medicationLogDao.getScheduledEntriesInWindow(
                scheduledStartIso = stockWindowStartIso,
                scheduledEndIso = stockWindowEndIso,
            )
            val homeAnchor = database.journalDao().getFirstPinnedTrackedDate()?.toModel()
            // Calibration inputs: every E2 lab, the estradiol doses that can
            // still shape a lab (see calibrationDoseWindowStartEpochMillis),
            // and the user's review metadata. None of the reads follows the
            // clock, so neither does the fit's identity.
            val calibrationLabs = database.bloodTestDao().getPanels().flatMap { panel ->
                panel.results
                    .filter { result ->
                        result.builtinAnalyteKey == BloodAnalyteKey.E2.storageValue
                    }
                    .map { result ->
                        PkCalibrationLab(
                            resultId = UUID.fromString(result.uuid),
                            collectedAtEpochMillis = panel.panel.collectedAtInstantEpochMillis,
                            valuePgml = result.canonicalValue,
                        )
                    }
            }
            // The estimates read from every estimate's PK lookback, the
            // calibration page's included, whatever the option, with no
            // upper bound: one read serves every option, so a 7/30 switch
            // needs no other query. The calibration reads from its dose
            // window. Both come from one read from the earlier start; newest
            // first, so each is a prefix of it.
            val pkEntriesStartEpochMillis = E2Derivation.loggedEntriesStart(now, zoneId).toEpochMilli()
            val calibrationEntriesStartEpochMillis = calibrationDoseWindowStartEpochMillis(calibrationLabs)
            val estradiolEntities = homeDao.getEstradiolPkEntries(
                startEpochMillis = minOf(
                    pkEntriesStartEpochMillis,
                    calibrationEntriesStartEpochMillis ?: pkEntriesStartEpochMillis,
                ),
                endEpochMillis = Long.MAX_VALUE,
            )
            fun estradiolEntitiesFrom(startEpochMillis: Long) =
                estradiolEntities.takeWhile { entry -> entry.appliedAtEpochMillis >= startEpochMillis }
            val pkEntries = estradiolEntitiesFrom(pkEntriesStartEpochMillis)
            val calibrationEntryEntities = calibrationEntriesStartEpochMillis?.let(::estradiolEntitiesFrom).orEmpty()
            // Whenever it is logged for: buildPkCalibrationInput keeps it when
            // every dose predates the lab window. Without labs it is the only
            // dose read, since the solve then only tells no dose history from
            // no labs; a latest dose the model can't place reads as none.
            val latestCalibrationEntryEntity = homeDao.getLatestEstradiolEntryOnOrBefore(
                onOrBeforeEpochMillis = Long.MAX_VALUE,
            )
            val calibrationMetadata = database.pkCalibrationDao().getAllMetadata()
                .map { entity -> entity.toModel() }

            val groupMedicinesByUuid = database.resolveMedicinesForGroups(
                activeGroupEntities + archivedGroupEntities
            )
            val entryMedicinesByUuid = database.resolveMedicinesForEntries(
                scheduleEntryEntities + antiandrogenHistoryEntities + estradiolEntities +
                        listOfNotNull(latestEstradiolEntryEntity) + stockFulfillmentEntities +
                        listOfNotNull(latestCalibrationEntryEntity)
            )
            val activeGroups = activeGroupEntities.map { group ->
                group.toMedicationGroupModel(groupMedicinesByUuid)
            }
            val archivedGroups = archivedGroupEntities.map { group ->
                group.toMedicationGroupModel(groupMedicinesByUuid)
            }
            val scheduleEntries = scheduleEntryEntities.map { entry ->
                entry.toMedicationLogEntryModel(entryMedicinesByUuid)
            }
            val antiandrogenHistoryEntries = antiandrogenHistoryEntities.map { entry ->
                entry.toMedicationLogEntryModel(entryMedicinesByUuid)
            }
            val estradiolEntries = estradiolEntities.map { entry ->
                entry.toMedicationLogEntryModel(entryMedicinesByUuid)
            }
            val latestEstradiolEntry = latestEstradiolEntryEntity?.toMedicationLogEntryModel(
                entryMedicinesByUuid
            )
            val stockMedicines = stockMedicineEntities.map { entity -> entity.toMedicineModel() }
            val stockFulfillmentEntries = stockFulfillmentEntities.map { entry ->
                entry.toMedicationLogEntryModel(entryMedicinesByUuid)
            }
            HomeSnapshotBuildInputs(
                calibrationLabs = calibrationLabs,
                // Plus the latest dose.
                calibrationEntries = (
                    estradiolEntries.take(calibrationEntryEntities.size) +
                        listOfNotNull(latestCalibrationEntryEntity?.toMedicationLogEntryModel(entryMedicinesByUuid))
                    ).distinctBy { entry -> entry.uuid },
                calibrationMetadata = calibrationMetadata,
                activeGroups = activeGroups,
                archivedGroups = archivedGroups,
                scheduleEntries = scheduleEntries,
                antiandrogenHistoryEntries = antiandrogenHistoryEntries,
                pkEntries = estradiolEntries.take(pkEntries.size),
                latestEstradiolEntry = latestEstradiolEntry,
                profile = database.userProfileDao()
                    .getProfile()
                    ?.toUserProfileModel()
                    ?: UserProfile(),
                stockMedicines = stockMedicines,
                stockFulfillmentEntries = stockFulfillmentEntries,
                homeAnchor = homeAnchor,
            )
        }
        diagnosticsLogger.info(
            TAG,
            "home_snapshot_run_inputs_loaded generation=$generation " +
                    "activeGroups=${inputs.activeGroups.size} " +
                    "archivedGroups=${inputs.archivedGroups.size} " +
                    "scheduleEntries=${inputs.scheduleEntries.size} " +
                    "antiandrogenEntries=${inputs.antiandrogenHistoryEntries.size} " +
                    "pkEntries=${inputs.pkEntries.size} " +
                    "hasLatestEstradiol=${inputs.latestEstradiolEntry != null}"
        )

        // Lab calibration is part of the E2 inputs so the calibrated curve is
        // what Home and the widget show first, not a population curve swapped
        // out later.
        val phaseStartNanos = System.nanoTime()
        fun phaseMillis(): Long = (System.nanoTime() - phaseStartNanos) / 1_000_000L
        val calibrationInput = buildPkCalibrationInput(
            labs = inputs.calibrationLabs,
            entries = inputs.calibrationEntries,
            weightKg = inputs.profile.weightKg,
            metadata = inputs.calibrationMetadata,
        )
        val fitId = E2Derivation.fitId(calibrationInput)
        diagnosticsLogger.info(
            TAG,
            "home_snapshot_phase calibration_input_built ms=${phaseMillis()} " +
                    "labs=${calibrationInput.labs.size} doseEvents=${calibrationInput.doseEvents.size}"
        )
        // The solve reads only what fitId digests, so the published fit stands
        // in for it: a stock edit, an antiandrogen dose, midnight or a 7/30
        // switch never re-solves. Not a failed one, which the next run tries
        // again, nor one older than a pending Try again; nor the solve of a
        // discarded run, which was never published. The labs and review
        // metadata published beside it then flag what it was solved from:
        // fitId digests every value the calibration page's flags read.
        val reused = cacheState.value?.e2Inputs?.fit?.takeIf { published ->
            published.fitId == fitId && published.outcome != FitOutcome.Failed && published.epoch >= fitEpoch
        }
        val fit = reused ?: E2FitRecord(
            fitId = fitId,
            epoch = fitEpoch,
            // A throwing solve must not fail the run: Home would have no
            // estimate. Treat it as no calibration and still publish.
            outcome = runCatching {
                withContext(defaultDispatcher) { PkCalibrationEngine.evaluate(calibrationInput) }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                diagnosticsLogger.warning(
                    TAG,
                    "home_snapshot_calibration_failed generation=$generation",
                    throwable,
                )
            }.getOrNull()
                ?.let { evaluation -> FitOutcome.Solved(evaluation.result, calibrationInput.originEpochMillis) }
                ?: FitOutcome.Failed,
        )
        val result = (fit.outcome as? FitOutcome.Solved)?.result
        diagnosticsLogger.info(
            TAG,
            "home_snapshot_phase calibration_${if (reused != null) "reused" else "solved"} ms=${phaseMillis()} " +
                    "epoch=${fit.epoch} state=${result?.globalState} promoted=${result?.promotedRoutes}"
        )
        val homeData = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = generation,
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = zoneId.id,
            activeGroups = inputs.activeGroups,
            archivedGroups = inputs.archivedGroups,
            scheduleEntries = inputs.scheduleEntries,
            antiandrogenHistoryEntries = inputs.antiandrogenHistoryEntries,
            userProfile = inputs.profile,
            stockMedicines = inputs.stockMedicines,
            stockFulfillmentEntries = inputs.stockFulfillmentEntries,
            homeAnchor = inputs.homeAnchor,
            latestEstradiolEntry = inputs.latestEstradiolEntry,
        )
        val e2Inputs = E2InputsRecord(
            generation = generation,
            anchorDateEpochDay = homeData.anchorDateEpochDay,
            zoneId = homeData.zoneId,
            option = option,
            weightKg = inputs.profile.weightKg,
            estradiolEntries = inputs.pkEntries,
            plannedSources = inputs.activeGroups.filter { group ->
                group.medications.any { medication -> medication.category == MedicationCategory.ESTRADIOL }
            },
            labs = inputs.calibrationLabs,
            labMetadata = inputs.calibrationMetadata,
            fit = fit,
        )

        // The check and the publish share the gate's lock, so no mutation can
        // commit between them: a publish never carries data older than a
        // committed mutation.
        val published = mutationMutex.withLock {
            (generationState.value == generation).also { current ->
                if (current) cacheState.value = HomeCacheState(generation, homeData, e2Inputs)
            }
        }
        if (!published) {
            // A mutation committed during the run; its own run is queued.
            diagnosticsLogger.info(
                TAG,
                "home_snapshot_run_discarded generation=$generation current=${generationState.value}"
            )
            return false
        }
        StartupTiming.mark("home_snapshot_rebuilt")
        diagnosticsLogger.info(
            TAG,
            "home_snapshot_published ${homeData.diagnosticSummary()} ${e2Inputs.diagnosticSummary()}"
        )
        // Outside the lock: a mutation that commits meanwhile leaves the files
        // behind the durable generation, and the next start rejects them.
        homeSnapshotStore.writeSnapshot(homeData)
        homeSnapshotStore.writeE2Inputs(e2Inputs)
        // Home's estimate for what was just published, so the next open finds
        // it stored. Part of the run, so whoever waits for the run waits for it.
        e2EstimateRepository.get().warmHome()
        return true
    }

    /** [read], or null when it throws: an unreadable part is a missing one. */
    private suspend fun <T : Any> readOrNull(name: String, read: suspend () -> T?): T? = try {
        read()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        diagnosticsLogger.warning(TAG, "${name}_read_failed", exception)
        null
    }

    private fun isValid(name: String, failure: String?): Boolean {
        if (failure != null) diagnosticsLogger.info(TAG, "${name}_rejected reason=$failure")
        return failure == null
    }

    private data class HomeSnapshotBuildInputs(
        val calibrationLabs: List<PkCalibrationLab>,
        val calibrationEntries: List<MedicationLogEntry>,
        val calibrationMetadata: List<E2CalibrationMetadata>,
        val activeGroups: List<MedicationGroup>,
        val archivedGroups: List<MedicationGroup>,
        val scheduleEntries: List<MedicationLogEntry>,
        val antiandrogenHistoryEntries: List<MedicationLogEntry>,
        val pkEntries: List<MedicationLogEntry>,
        val latestEstradiolEntry: MedicationLogEntry?,
        val profile: UserProfile,
        val stockMedicines: List<Medicine>,
        val stockFulfillmentEntries: List<MedicationLogEntry>,
        val homeAnchor: TrackedDate? = null,
    )

    private data class HomeSnapshotWindow(
        val homeScheduledStart: LocalDateTime,
        val homeScheduledEnd: LocalDateTime,
        val homeManualStartEpochMillis: Long,
        val homeManualEndEpochMillis: Long,
        val bufferedScheduledStart: LocalDateTime,
        val bufferedScheduledEnd: LocalDateTime,
        val bufferedManualStartEpochMillis: Long,
        val bufferedManualEndEpochMillis: Long,
        val onOrBeforeEpochMillis: Long,
    ) {
        companion object {
            fun forNow(
                now: LocalDateTime,
                zoneId: ZoneId,
            ): HomeSnapshotWindow {
                val today = now.toLocalDate()
                return HomeSnapshotWindow(
                    homeScheduledStart = today.minusDays(1).atStartOfDay(),
                    homeScheduledEnd = today.plusDays(HOME_SCHEDULE_LOOKAHEAD_DAYS)
                        .atTime(23, 59, 59),
                    homeManualStartEpochMillis = today.minusDays(1)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    homeManualEndEpochMillis = today.plusDays(1)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    bufferedScheduledStart = today.minusDays(HOME_SNAPSHOT_PAST_BUFFER_DAYS)
                        .atStartOfDay(),
                    bufferedScheduledEnd = today.plusDays(HOME_SCHEDULE_LOOKAHEAD_DAYS + HOME_SNAPSHOT_VALIDITY_DAYS)
                        .atTime(23, 59, 59),
                    bufferedManualStartEpochMillis = today.minusDays(HOME_SNAPSHOT_PAST_BUFFER_DAYS)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    bufferedManualEndEpochMillis = today.plusDays(HOME_SNAPSHOT_VALIDITY_DAYS + 1)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    onOrBeforeEpochMillis = now
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                )
            }
        }
    }
}

/**
 * Why Home data can't stand in for Room at the durable [generation], [today]
 * and [zoneId]; null when it can. Its one validity rule: readers take Home
 * data only from [HomeSnapshotRepository.cache], which the load seeds with
 * it only when this passes, and each run's publish replaces.
 */
internal fun HomeSnapshotRecord.validityFailure(generation: Long, today: LocalDate, zoneId: ZoneId): String? =
    if (schemaVersion != HOME_SNAPSHOT_SCHEMA_VERSION) {
        "schema_version expected=$HOME_SNAPSHOT_SCHEMA_VERSION actual=$schemaVersion"
    } else {
        generationZoneOrDateFailure(this.generation, this.zoneId, anchorDateEpochDay, generation, today, zoneId)
    }

/**
 * The E2 inputs' one validity rule: Home data's, apart from the schema, and
 * only from this engine, which solved the fit and keys every estimate.
 */
internal fun E2InputsRecord.validityFailure(generation: Long, today: LocalDate, zoneId: ZoneId): String? =
    if (engineVersion != PK_ENGINE_VERSION) {
        "engine_version expected=$PK_ENGINE_VERSION actual=$engineVersion"
    } else {
        generationZoneOrDateFailure(this.generation, this.zoneId, anchorDateEpochDay, generation, today, zoneId)
    }

private fun generationZoneOrDateFailure(
    recordGeneration: Long,
    recordZoneId: String,
    anchorDateEpochDay: Long,
    generation: Long,
    today: LocalDate,
    zoneId: ZoneId,
): String? {
    val anchorDate = LocalDate.ofEpochDay(anchorDateEpochDay)
    val validUntil = anchorDate.plusDays(HOME_SNAPSHOT_VALIDITY_DAYS)
    return when {
        recordGeneration != generation -> "generation expected=$generation actual=$recordGeneration"
        recordZoneId != zoneId.id -> "zone_id expected=${zoneId.id} actual=$recordZoneId"
        today.isBefore(anchorDate) || today.isAfter(validUntil) ->
            "date_window anchorDate=$anchorDate validUntil=$validUntil today=$today"

        else -> null
    }
}

private fun HomeSnapshotRecord.diagnosticSummary(): String {
    return "schema=$schemaVersion " +
            "generation=$generation " +
            "anchorDate=${LocalDate.ofEpochDay(anchorDateEpochDay)} " +
            "zone=$zoneId " +
            "groups=${activeGroups.size} " +
            "archivedGroups=${archivedGroups.size} " +
            "scheduleEntries=${scheduleEntries.size} " +
            "antiandrogenEntries=${antiandrogenHistoryEntries.size}"
}

private fun E2InputsRecord.diagnosticSummary(): String {
    return "engine=$engineVersion " +
            "option=$option " +
            "estradiolEntries=${estradiolEntries.size} " +
            "plannedSources=${plannedSources.size} " +
            "labs=${labs.size} " +
            "fitEpoch=${fit?.epoch}"
}

// Bumped to 9 with the Oct 2026 estradiol PK refit so every install rebuilt its cached
// projections and calibration on the first open after the update. A PK or calibration
// change now bumps PK_ENGINE_VERSION instead, which drops the E2 inputs part, so the next
// run re-solves. A bump here, like a Home data codec bump, drops Home data only: the run
// reuses the fit published in the still-valid E2 inputs part.
internal const val HOME_SNAPSHOT_SCHEMA_VERSION = 9

private const val TAG = "HomeSnapshotRepository"
private const val HOME_SCHEDULE_LOOKAHEAD_DAYS = 90L
private const val HOME_SNAPSHOT_VALIDITY_DAYS = 10L
private const val HOME_SNAPSHOT_PAST_BUFFER_DAYS = 1L

// A failed run's retries; after the last, the worker waits for the next request.
private val RETRY_BACKOFF_MILLIS = listOf(1_000L, 5_000L, 30_000L)
