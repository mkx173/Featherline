package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.pk.resultIdsWith
import com.mkx.hrttracker.startup.StartupTiming
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.currentMinute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Why a newer estimate is being derived: a change the user made, or the clock or the system. */
enum class E2Pending { USER, SYSTEM }

/**
 * An estimate observation. [value] is the last estimate it derived or
 * admitted, null until the first. [pending] is set from a mutation's commit
 * until its run publishes, and while the estimate for a newer key is being
 * derived: [E2Pending.USER] exactly when the inputs' generation is newer than
 * the one behind [value]. A hold on a run the worker gave up on releases,
 * keeping [value].
 */
data class E2State<T>(val value: T?, val pending: E2Pending? = null)

/** One state of an observation's inputs: the current ones, or null behind a mutation or while missing. */
private data class Observed(
    val current: E2InputsRecord?,
    val behind: Boolean,
    val generation: Long,
    val minute: LocalDateTime,
)

/** The calibration page's estimate section: the fit as the page renders it, and the lab flags beside it. */
data class CalibrationEstimate(
    /** Null when the solve failed or the render threw: the numeric failure, with Try again. */
    val result: PkCalibrationResult?,
    /** Null without a READY fit. */
    val render: PkCalibrationRenderResult?,
    // From the labs and review metadata the fit was solved from, stored
    // beside it, so they always match it and need no Room read.
    val acceptedLabIds: Set<UUID>,
    val excludedLabIds: Set<UUID>,
    /** Results the fit can never use (value ≤ 0). */
    val nonPositiveLabIds: Set<UUID>,
)

/**
 * The E2 estimates, derived from the published E2 inputs and the clock by
 * key. An estimate stands in for a derivation only under the exact key it was
 * derived for, so nothing expires: when the key moves, the estimate is
 * derived again.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class E2EstimateRepository @Inject constructor(
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val store: HomeE2EstimateStore,
    private val clock: Clock,
    @param:AppScope private val appScope: CoroutineScope,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    // The last keys of each kind derived, done or in flight, Home's with the
    // stored estimate among them: every observer of a kind, and the warm-up,
    // share them, so no key is derived twice. Each guarded by itself.
    private val homeMemo = lastKeys<HomeEstimateKey, Deferred<Derivation<HomeE2Estimate>>>()
    private val calibrationMemo = lastKeys<CalibrationRenderKey, Deferred<Derivation<CalibrationEstimate>>>()
    private val widgetMemo = lastKeys<WidgetEstimateKey, Deferred<Derivation<PkProjectionResult>>>()

    @Volatile
    private var storedKey: HomeEstimateKey? = null
    private val writeMutex = Mutex()

    /**
     * The newest Home estimate an observation admitted or derived, seeded
     * with the stored one; null until the first. For readers exempt from the
     * skeleton (the Settings row), which keep their last value and update it
     * in place. A warm-up leaves it alone: one for an older key can land
     * after an observation's newer value.
     */
    val latestHome: StateFlow<HomeE2Estimate?>
        get() = latestHomeState
    private val latestHomeState = MutableStateFlow<HomeE2Estimate?>(null)

    // The stored estimate seeds the memo before any observation starts, so a
    // match is in the first frame.
    private val loaded: Deferred<Unit> = appScope.async {
        val stored = try {
            store.read()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            diagnosticsLogger.warning(TAG, "home_e2_estimate_read_failed", exception)
            null
        }
        if (stored != null) {
            synchronized(homeMemo) {
                homeMemo[stored.key] = CompletableDeferred(Derivation(stored.estimate, storable = true))
            }
            storedKey = stored.key
            latestHomeState.value = stored.estimate
        }
        StartupTiming.mark("home_e2_estimate_loaded")
    }

    /**
     * Home's estimate, looked at again each time [now] emits. Each
     * observation starts cold: its first state, after the stored estimate's
     * load, uses the published inputs and a fresh clock read, and admits a
     * value only when those inputs are current and the memo has their key;
     * otherwise the observation has none until the estimate is derived, and
     * asks for a run at each state whose inputs aren't current, unless one
     * is pending or going. Every later minute is a fresh read too, never
     * [now]'s own value, which can be a cached minute: behind after a
     * resume, ahead after the clock or the zone moved back. A value, once
     * shown, is kept while a newer one is pending, including while a
     * mutation's run hasn't published: that gap is [E2Pending.USER] too,
     * from the commit, so the hold covers the run. If the worker gives up
     * on that run, the hold releases and the value stays; the minute's
     * re-asks for the same generation don't hold again.
     */
    fun observeHome(now: Flow<LocalDateTime>): Flow<E2State<HomeE2Estimate>> = observe(
        now = now,
        keyOf = E2Derivation::homeEstimateKey,
        derivationOf = { key, inputs, minute, zoneId -> homeDerivation(key, inputs.e2Inputs(), minute, zoneId) },
        onAdmitted = { estimate -> latestHomeState.value = estimate },
    )

    /**
     * The calibration page's estimate section, looked at again each time
     * [now] emits, under the same rules as [observeHome]: shared
     * derivations, fresh clock reads, a cold start, nothing from inputs
     * that aren't current, [E2Pending.USER] for a change the user made. It
     * is never stored, so a process's first observation derives it. A render
     * that throws gives the numeric failure, which is kept like any other
     * value until the key moves.
     */
    fun observeCalibration(now: Flow<LocalDateTime>): Flow<E2State<CalibrationEstimate>> = observe(
        now = now,
        keyOf = E2Derivation::calibrationRenderKey,
        derivationOf = { key, inputs, minute, zoneId ->
            calibrationMemo.derivation(key) { deriveCalibration(key, inputs, minute, zoneId) }
        },
    )

    /** One kind of estimate, observed as [observeHome] describes. */
    private fun <K, T : Any> observe(
        now: Flow<LocalDateTime>,
        keyOf: (E2Inputs, LocalDateTime, ZoneId) -> K,
        derivationOf: (K, E2InputsRecord, LocalDateTime, ZoneId) -> Deferred<Derivation<T>>,
        onAdmitted: (T) -> Unit = {},
    ): Flow<E2State<T>> = flow {
        loaded.await()
        var state = E2State<T>(null)
        var shownKey: K? = null
        // The generation of the inputs behind the value.
        var shownGeneration = 0L
        // The generation whose run the worker gave up on: its hold released,
        // and the minute's re-asks for it run silently.
        var releasedGeneration = -1L
        // Only a trigger: [now]'s own minute can be a cached one.
        val minutes = now
            .map { currentMinute(clock) }
            .onStart { emit(currentMinute(clock)) }
            .distinctUntilChanged()
        emitAll(
            combine(
                homeSnapshotRepository.cache.filterNotNull(),
                homeSnapshotRepository.generation,
                minutes,
            ) { cache, generation, minute ->
                val behind = cache.generation != generation
                Observed(cache.e2Inputs?.takeIf { !behind }, behind, generation, minute)
            }
                .transformLatest { (current, behind, generation, minute) ->
                    val zoneId = ZoneId.systemDefault()
                    if (current == null) {
                        // Behind a mutation, or missing: nothing to derive
                        // from until a run publishes current inputs. Asks
                        // for one only while the worker has none pending or
                        // going, so a start during the mutation's or the
                        // startup's run adds none; asked again each minute,
                        // it retries a run that failed for good.
                        homeSnapshotRepository.requestRebuildIfIdle()
                        // Every gated mutation is a change someone made: a
                        // shown value holds from its commit, through the run.
                        val holds = behind && state.value != null && generation != releasedGeneration
                        state = state.copy(pending = if (holds) E2Pending.USER else null)
                        emit(state)
                        if (!holds) return@transformLatest
                        // Until the worker gives up on the run: the value
                        // stays, and the minute's re-asks for this generation
                        // run silently. A mutation marks its run pending
                        // before the bump, so this never sees the worker idle
                        // before it took the run up. A wait, not an input:
                        // the worker going idle must not ask again at once.
                        homeSnapshotRepository.rebuilding.first { rebuilding -> !rebuilding }
                        releasedGeneration = generation
                        state = state.copy(pending = null)
                        emit(state)
                        return@transformLatest
                    }
                    val key = keyOf(current.e2Inputs(), minute, zoneId)
                    if (key == shownKey) {
                        // A change the estimate doesn't read: settled, the hold released.
                        shownGeneration = current.generation
                        state = state.copy(pending = null)
                        emit(state)
                        return@transformLatest
                    }
                    val derivation = derivationOf(key, current, minute, zoneId)
                    if (!derivation.isCompleted) {
                        val pending = if (state.value != null && current.generation > shownGeneration) {
                            E2Pending.USER
                        } else {
                            E2Pending.SYSTEM
                        }
                        emit(state.copy(pending = pending))
                    }
                    val value = derivation.await().value
                    // The change behind it is spent either way: the clock
                    // moving after it is the system's.
                    shownGeneration = current.generation
                    state = if (value == null) {
                        // A failed derivation keeps the last value.
                        state.copy(pending = null)
                    } else {
                        shownKey = key
                        onAdmitted(value)
                        E2State(value)
                    }
                    emit(state)
                }
        )
    }.distinctUntilChanged()

    /**
     * Derives and stores Home's estimate for the current inputs and a fresh
     * clock read, so the next open shows it in its first frame. Background
     * jobs that move Home's key call it. Inputs that aren't current are left
     * alone: the run publishing them warms when it lands. Costs a key
     * when the stored estimate has it already.
     */
    suspend fun warmHome() {
        try {
            loaded.await()
            val inputs = currentInputs()?.e2Inputs() ?: return
            val now = currentMinute(clock)
            val zoneId = ZoneId.systemDefault()
            val key = E2Derivation.homeEstimateKey(inputs, now, zoneId)
            if (key == storedKey) return
            val derived = homeDerivation(key, inputs, now, zoneId).await()
            val estimate = derived.value ?: return
            if (derived.storable) persist(HomeE2EstimateRecord(key, estimate))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            diagnosticsLogger.warning(TAG, "home_e2_estimate_warm_up_failed", exception)
        }
    }

    /**
     * The widget's seven-day estimate at [now], from [record]: the inputs
     * published with the Home data the widget shows. Shared through its own
     * memo, and never stored, since the widget record keeps what the widget
     * shows. Null when the derivation threw.
     */
    suspend fun widgetEstimate(record: E2InputsRecord, now: LocalDateTime): PkProjectionResult? {
        val inputs = record.e2Inputs()
        val zoneId = ZoneId.systemDefault()
        val key = E2Derivation.widgetEstimateKey(inputs, now, zoneId)
        return widgetMemo.derivation(key) {
            Derivation(
                try {
                    E2Derivation.deriveWidgetEstimate(inputs, now, zoneId)
                } catch (exception: Exception) {
                    diagnosticsLogger.warning(TAG, "widget_e2_estimate_failed", exception)
                    null
                }
            )
        }.await().value
    }

    /** The E2 inputs once the cache's disk load is done, while they are current; null otherwise. */
    private suspend fun currentInputs(): E2InputsRecord? {
        val cache = homeSnapshotRepository.cache.filterNotNull().first()
        return cache.e2Inputs?.takeIf { cache.generation == homeSnapshotRepository.generation.value }
    }

    private fun homeDerivation(
        key: HomeEstimateKey,
        inputs: E2Inputs,
        now: LocalDateTime,
        zoneId: ZoneId,
    ): Deferred<Derivation<HomeE2Estimate>> = homeMemo.derivation(key) {
        deriveHome(key, inputs, now, zoneId).also { derived ->
            // Written behind, outside this derivation, so no one waiting for
            // the estimate waits for the disk.
            if (derived.storable) {
                appScope.launch { persist(HomeE2EstimateRecord(key, checkNotNull(derived.value))) }
            }
        }
    }

    /** The memo's derivation of [key], started on [defaultDispatcher] on a miss. */
    private fun <K, T> MutableMap<K, Deferred<Derivation<T>>>.derivation(
        key: K,
        derive: () -> Derivation<T>,
    ): Deferred<Derivation<T>> = synchronized(this) {
        getOrPut(key) { appScope.async(defaultDispatcher) { derive() } }
    }

    private fun deriveHome(
        key: HomeEstimateKey,
        inputs: E2Inputs,
        now: LocalDateTime,
        zoneId: ZoneId,
    ): Derivation<HomeE2Estimate> {
        val startNanos = System.nanoTime()
        var bandFailed = false
        val estimate = try {
            E2Derivation.deriveHomeEstimate(inputs, now, zoneId) { throwable ->
                bandFailed = true
                diagnosticsLogger.warning(TAG, "home_e2_estimate_band_failed", throwable)
            }
        } catch (exception: Exception) {
            diagnosticsLogger.warning(TAG, "home_e2_estimate_failed", exception)
            null
        }
        diagnosticsLogger.info(
            TAG,
            "home_e2_estimate_derived ms=${(System.nanoTime() - startNanos) / 1_000_000L} " +
                    "option=${key.option} windowDate=${key.windowDate} " +
                    "failed=${estimate == null} bandFailed=$bandFailed"
        )
        return Derivation(estimate, storable = estimate != null && !bandFailed)
    }

    private fun deriveCalibration(
        key: CalibrationRenderKey,
        record: E2InputsRecord,
        now: LocalDateTime,
        zoneId: ZoneId,
    ): Derivation<CalibrationEstimate> {
        val startNanos = System.nanoTime()
        val inputs = record.e2Inputs()
        var failed = false
        val render = try {
            E2Derivation.deriveCalibrationRender(inputs, now, zoneId)
        } catch (exception: Exception) {
            // Shown as the numeric failure with Try again; the log is the
            // only trace of why.
            failed = true
            diagnosticsLogger.warning(TAG, "calibration_render_failed", exception)
            null
        }
        diagnosticsLogger.info(
            TAG,
            "calibration_render_derived ms=${(System.nanoTime() - startNanos) / 1_000_000L} " +
                    "option=${key.option} windowDate=${key.windowDate} failed=$failed"
        )
        return Derivation(
            CalibrationEstimate(
                result = (inputs.fit?.outcome as? FitOutcome.Solved)?.result?.takeUnless { failed },
                render = render,
                acceptedLabIds = record.labMetadata.resultIdsWith(E2CalibrationDisposition.REVIEWED),
                excludedLabIds = record.labMetadata.resultIdsWith(E2CalibrationDisposition.EXCLUDED),
                nonPositiveLabIds = record.labs
                    .filter { lab -> lab.valuePgml <= 0.0 }
                    .mapTo(HashSet()) { lab -> lab.resultId },
            ),
        )
    }

    /**
     * Stores [record] unless it is stored already, or its key is no longer
     * the one the current inputs and a fresh clock read give: a slow
     * derivation must not overwrite a newer estimate.
     */
    private suspend fun persist(record: HomeE2EstimateRecord) {
        try {
            writeMutex.withLock {
                if (record.key == storedKey) return
                val now = currentMinute(clock)
                val current = currentInputs()
                    ?.let { inputs -> E2Derivation.homeEstimateKey(inputs.e2Inputs(), now, ZoneId.systemDefault()) }
                if (current != record.key) {
                    diagnosticsLogger.info(TAG, "home_e2_estimate_write_skipped reason=key_moved")
                    return
                }
                store.write(record)
                storedKey = record.key
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            diagnosticsLogger.warning(TAG, "home_e2_estimate_write_failed", exception)
        }
    }

    /**
     * One derivation. Home's and the widget's have a null [value] when they
     * threw; the calibration page's always has one, the numeric failure when
     * its render threw. Not [storable] when Home's, or its band render,
     * threw: such a result is kept in the memo, so it isn't derived again on
     * every tick, but never stored, so a restart tries again. Only Home's
     * estimate is ever stored.
     */
    private class Derivation<T>(val value: T?, val storable: Boolean = false)

    private companion object {
        const val TAG = "E2EstimateRepository"
        const val MEMO_SIZE = 2

        /** Access-ordered: keeps the [MEMO_SIZE] keys used last. */
        fun <K, V> lastKeys(): MutableMap<K, V> = object : LinkedHashMap<K, V>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > MEMO_SIZE
        }
    }
}

/** What the record's E2 estimates are derived from. */
internal fun E2InputsRecord.e2Inputs() = E2Inputs(
    estradiolEntries = estradiolEntries,
    plannedSources = plannedSources,
    weightKg = weightKg,
    option = option,
    fit = fit,
)
