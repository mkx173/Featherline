package com.mkx.hrttracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.HomeInputSource
import com.mkx.hrttracker.data.repository.HomeInputs
import com.mkx.hrttracker.data.repository.HomeRepository
import com.mkx.hrttracker.data.repository.PkCalibrationLiveRepository
import com.mkx.hrttracker.data.repository.PkCalibrationLiveState
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.lowStockSeverityRank
import com.mkx.hrttracker.model.medication.visibleMedicationEntries
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkPredictiveBandKnot
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.ui.calibration.PkCalibrationUiState
import com.mkx.hrttracker.ui.calibration.pkCalibrationUiState
import com.mkx.hrttracker.ui.journal.toAnchorRowUiState
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationUiFixture
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationUiFixtureBridge
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.AppTimeSnapshot
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.TimeZoneChangeNotice
import com.mkx.hrttracker.util.TimeZoneChangeNoticeController
import com.mkx.hrttracker.util.tickWhileSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val settingsRepository: SettingsRepository,
    private val timeZoneChangeNoticeController: TimeZoneChangeNoticeController,
    private val pkCalibrationLiveRepository: PkCalibrationLiveRepository,
    private val pkUiFixtureBridge: PkCalibrationUiFixtureBridge,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    appTimeSource: AppTimeSource,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val currentSnapshot = appTimeSource.currentSnapshot
    private var lastHomeSnapshotRefreshKey: HomeSnapshotRefreshKey? = null

    private val _uiState = MutableStateFlow(
        MainUiState(
            homeDataReady = false,
            now = currentSnapshot.value.minute,
        )
    )

    // The ui-state combine below stays collected for the ViewModel's whole
    // (activity-scoped) lifetime: a stop-timeout would let data mutated while
    // away (e.g. a backup restore) flash one stale frame on re-entry, so data
    // emissions must keep rebuilding the retained value in the background.
    // The time inputs, however, are gated on this state's own subscriber
    // count: a live minute tick would otherwise rebuild the ui state — PK
    // simulation fallbacks included — every minute for the activity's whole
    // lifetime, including backgrounded. While unsubscribed only date/zone
    // changes pass through, keeping the midnight/timezone re-anchor.
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val gatedSnapshot: StateFlow<AppTimeSnapshot> =
        currentSnapshot.tickWhileSubscribed(_uiState.subscriptionCount) { snapshot ->
            snapshot.minute.toLocalDate() to snapshot.zone
        }
    private val gatedMinute: StateFlow<LocalDateTime> =
        appTimeSource.currentMinute.tickWhileSubscribed(_uiState.subscriptionCount) { minute ->
            minute.toLocalDate()
        }

    init {
        viewModelScope.launch {
            buildUiStateFlow().collect { state -> _uiState.value = state }
        }
    }

    // Re-subscribe the home inputs flow only on local date or zone changes.
    // Room query windows are date+zone-derived, while per-minute and explicit
    // wall-clock refreshes are fed to HomeRepository as a combine input so
    // now-sensitive projections re-anchor without tearing down the Room flows.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun buildUiStateFlow(): Flow<MainUiState> {
        val keyedInputsFlow = gatedSnapshot
            .map { snapshot ->
                HomeTimeKey(
                    now = snapshot.minute,
                    date = snapshot.minute.toLocalDate(),
                    zoneId = snapshot.zone,
                )
            }
            .distinctUntilChangedBy { key -> key.date to key.zoneId }
            .flatMapLatest { key ->
                refreshHomeSnapshotForDateIfNeeded(key.now, key.zoneId)
                homeRepository.observeHomeInputs(
                    date = key.date,
                    nowFlow = gatedMinute,
                    zoneId = key.zoneId,
                )
                    .map { inputs -> KeyedHomeInputs(key = key, inputs = inputs) }
            }

        // The Home calibration presentation comes from the snapshot, so it is
        // on the first frame with the curve. The debug harness fixture (plan
        // D3) overrides it so QA exercises the shipping surface.
        val keyedInputsWithPkFlow = combine(
            keyedInputsFlow,
            pkUiFixtureBridge.fixture,
        ) { keyedInputs, fixture -> keyedInputs to fixture }

        val homeStateFlow = combine(
            keyedInputsWithPkFlow,
            gatedSnapshot,
            timeZoneChangeNoticeController.notice,
            settingsRepository.homeLowStockSectionExpandedFlow,
            settingsRepository.homeLowStockAcknowledgedWarningStatesFlow,
        ) { keyedInputsWithPk, timeSnapshot, timeZoneNotice, storedLowStockSectionExpanded, acknowledgedWarningStates ->
            val (keyedInputs, fixture) = keyedInputsWithPk
            if (!keyedInputs.key.matches(timeSnapshot)) {
                return@combine null
            }
            val inputs = keyedInputs.inputs
            if (
                inputs.source == HomeInputSource.ROOM &&
                inputs.stockWarnings.isEmpty() &&
                acknowledgedWarningStates.isNotEmpty()
            ) {
                // Swallow DataStore failures: letting them propagate would tear
                // down this combine -> stateIn home flow. A dropped clear is
                // self-correcting on the next empty-ROOM emission.
                try {
                    settingsRepository.clearHomeLowStockAcknowledgedWarningStates()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Best-effort cleanup; see comment above.
                }
            }
            withContext(defaultDispatcher) {
                buildHomeUiState(
                    inputs = inputs,
                    fixture = fixture,
                    now = timeSnapshot.minute,
                    zoneId = timeSnapshot.zone,
                    timeZoneNotice = timeZoneNotice,
                    lowStockSectionExpanded = shouldExpandLowStockSection(
                        storedExpanded = storedLowStockSectionExpanded,
                        stockWarnings = inputs.stockWarnings,
                        acknowledgedWarningStates = acknowledgedWarningStates,
                    ),
                )
            }
        }
            .filterNotNull()

        return combine(
            homeStateFlow,
            settingsRepository.homeCardLayoutFlow,
        ) { state, layout ->
            state.copy(homeCardLayout = layout)
        }
    }

    private val _highlightRequest = MutableStateFlow<DoseRowHighlightRequest?>(null)
    val highlightRequest: StateFlow<DoseRowHighlightRequest?> = _highlightRequest.asStateFlow()

    private val _homeDeepLinkSignal = MutableStateFlow(0)
    val homeDeepLinkSignal: StateFlow<Int> = _homeDeepLinkSignal.asStateFlow()

    private val _milestonesDeepLinkSignal = MutableStateFlow(0)
    val milestonesDeepLinkSignal: StateFlow<Int> = _milestonesDeepLinkSignal.asStateFlow()

    // Dedup marker for the milestones deep link. Deliberately a ViewModel field
    // rather than NavHost rememberSaveable state so it shares the signal's exact
    // lifetime: both reset to 0 on process death, both survive a config-change
    // recreation together. A marker saved to instance state would outlive the
    // signal — a process-death re-tap would restore lastHandled ahead of the
    // fresh 0-based signal and silently drop the deep link, and a font-scale (or
    // any non-configChanges) recreation would replay the retained intent.
    private var lastHandledMilestonesSignal = 0

    // True once an onCreate has parsed its launch intent. ViewModel-lifetime on purpose
    // (same rationale as lastHandledMilestonesSignal): survives a config recreation, so
    // the retained intent's widget extras are not replayed and cannot yank the user back
    // to a deep link they navigated away from — but resets on process death, so a widget
    // or shortcut tap that recreates a killed task (which lands in onCreate with a
    // non-null savedInstanceState, because a dead instance can never receive onNewIntent)
    // still parses the fresh intent and navigates.
    var launchIntentParsed = false

    fun requestMilestonesDeepLink() {
        _milestonesDeepLinkSignal.update { it + 1 }
    }

    // Fires the milestones navigation once per request: returns true for the
    // first observer of each new signal value and marks it handled; later calls
    // for the same (or an already-superseded) signal return false.
    fun consumeMilestonesDeepLink(): Boolean {
        val signal = _milestonesDeepLinkSignal.value
        if (signal <= lastHandledMilestonesSignal) {
            return false
        }
        lastHandledMilestonesSignal = signal
        return true
    }

    fun requestDoseRowHighlight(keys: List<DoseRowHighlightKey>) {
        if (keys.isEmpty()) {
            return
        }
        _highlightRequest.value = DoseRowHighlightRequest(keys)
        _homeDeepLinkSignal.update { it + 1 }
    }

    fun consumeHighlightRequest(request: DoseRowHighlightRequest) {
        // Clear only if this is still the active request. A cancellation-time
        // consume (tab switch) must not clobber a newer request that arrived
        // from a fresh deep link.
        _highlightRequest.compareAndSet(request, null)
    }

    // Once-per-process view concern owned here (this ViewModel is activity-
    // scoped in a single-activity app) rather than as a file-level static: a
    // static is invisible to tests, replays only on process death anyway, and
    // is racy across simultaneous Home compositions — the loser would read the
    // pre-claim value but never consume it, sticking on the intro render path
    // forever.
    private var homeE2ChartIntroAnimationClaimed = false

    /**
     * Claims the once-per-ViewModel-lifetime E2 chart intro animation.
     * Returns true for exactly one caller; that composition plays the intro,
     * every other (and later) composition renders the synchronous-model path
     * directly.
     */
    fun claimHomeE2ChartIntroAnimation(): Boolean {
        if (homeE2ChartIntroAnimationClaimed) {
            return false
        }
        homeE2ChartIntroAnimationClaimed = true
        return true
    }

    fun dismissTimeZoneChangeNotice() {
        timeZoneChangeNoticeController.dismiss()
    }

    fun setHomeE2DisplayUnit(unit: BloodUnitKey) {
        viewModelScope.launch {
            settingsRepository.setHomeE2DisplayUnit(AllowedAnalyteUnit.of(BloodAnalyteKey.E2, unit))
        }
    }

    fun setHomeE2ChartWindowOption(option: HomeE2ChartWindowOption) {
        viewModelScope.launch {
            settingsRepository.setHomeE2ChartWindowOption(option)
        }
    }

    fun setHomeCardLayout(order: List<HomeCardType>, hidden: Set<HomeCardType>) {
        viewModelScope.launch {
            settingsRepository.setHomeCardLayout(order, hidden)
        }
    }

    fun setLowStockSectionExpanded(expanded: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHomeLowStockSectionFoldState(
                expanded = expanded,
                acknowledgedWarningStates = if (expanded) {
                    emptyMap()
                } else {
                    uiState.value.stockWarnings.toLowStockWarningStateMap()
                },
            )
        }
    }

    private fun refreshHomeSnapshotForDateIfNeeded(
        now: LocalDateTime,
        zoneId: ZoneId,
    ) {
        val key = HomeSnapshotRefreshKey(
            date = now.toLocalDate(),
            zoneId = zoneId.id,
        )
        if (lastHomeSnapshotRefreshKey == key) {
            return
        }
        lastHomeSnapshotRefreshKey = key
        homeRepository.refreshHomeSnapshotAsync(now = now, force = false, zoneId = zoneId)
    }

    private fun buildHomeUiState(
        inputs: HomeInputs,
        fixture: PkCalibrationUiFixture?,
        now: LocalDateTime,
        zoneId: ZoneId,
        timeZoneNotice: TimeZoneChangeNotice?,
        lowStockSectionExpanded: Boolean,
    ): MainUiState {
        val homeE2DisplayUnit = inputs.settings.homeE2DisplayUnit
        val chartWindowOption = inputs.settings.homeE2ChartWindowOption
        val homeEntries = (inputs.scheduleEntries + inputs.antiandrogenHistoryEntries)
            .distinctBy { entry -> entry.uuid }
            .sortedByDescending { entry -> entry.appliedAt }
        val allGroups = inputs.activeGroups + inputs.archivedGroups
        val visibleEntries = visibleMedicationEntries(
            homeEntries,
            allGroups,
            inputs.settings.showArchivedGroupRecords,
        )
        val scheduleGroups =
            if (inputs.settings.showArchivedGroupRecords) allGroups else inputs.activeGroups
        // HomeInputs was constructed against the date-scoped anchor `now` at
        // flow subscription time. The live `now` ticks per minute and may
        // cross a planned-slot expiry without a fresh upstream emission, so
        // re-validate against the live `now` here:
        //   - pkProjection: drop if the cached expiry is on or before now.
        //   - estradiolPkPlannedEntries: drop synthetic doses whose slot has
        //     already passed (the user didn't take them — don't keep them on
        //     the curve as if they did).
        val nowInstant = now.atZone(zoneId).toInstant()
        // The cached projection was simulated with the snapshot's calibration,
        // so it is the calibrated curve from the first frame; a local
        // re-simulation (expired projection) uses the same params. The live
        // evaluation only contributes the band and the hero/status.
        val freshProjection = inputs.pkProjection?.takeIf {
            inputs.pkProjectionExpiresAt?.isAfter(nowInstant) ?: true
        }
        val freshPlannedEntries = inputs.estradiolPkPlannedEntries.filter { entry ->
            entry.scheduledFor?.isAfter(now) ?: false
        }
        val simulationPersonalParams = inputs.pkPersonalParams
        val trendResult = freshProjection?.toMainEstradiolTrend(
            now = now,
            zoneId = zoneId,
            option = chartWindowOption,
        )
            ?: PkMedicationSimulation.simulateMainEstradiolTrend(
                entries = inputs.estradiolPkEntries,
                plannedEntries = freshPlannedEntries,
                bodyWeightKg = inputs.profile.weightKg,
                now = now,
                zoneId = zoneId,
                option = chartWindowOption,
                personalParams = simulationPersonalParams,
            )

        // When the cached projection is invalid the trend falls back to
        // simulating over `estradiolPkEntries`. The SNAPSHOT path now embeds
        // those entries, so it can recompute a real curve without waiting for
        // Room; mark it ready whenever a usable projection or PK entries are
        // present. Only a SNAPSHOT with neither (no dose history) stays gated
        // on the ROOM emission.
        val e2TrendReady = freshProjection != null ||
                inputs.estradiolPkEntries.isNotEmpty() ||
                inputs.source == HomeInputSource.ROOM

        // Local re-simulation only happens when no usable cached projection
        // remains but embedded PK entries do — log just that case, not every
        // per-minute tick.
        if (freshProjection == null && inputs.estradiolPkEntries.isNotEmpty()) {
            diagnosticsLogger.info(
                TAG,
                "home_pk_trend_resimulated source=${inputs.source} " +
                        "projectionExpired=${inputs.pkProjection != null} " +
                        "pkEntries=${inputs.estradiolPkEntries.size} " +
                        "plannedEntries=${freshPlannedEntries.size}",
            )
        }

        return MainUiState(
            homeDataReady = true,
            e2TrendReady = e2TrendReady,
            homeSource = inputs.source,
            now = now,
            homeE2DisplayUnit = homeE2DisplayUnit,
            homeE2ChartWindowOption = chartWindowOption,
            // Hero, notes and band all come from the snapshot (same doses, same
            // calibration as the cached curve); the band is dropped with the
            // projection on expiry until the snapshot rebuilds.
            pkCalibration = buildMainPkCalibration(
                inputs = inputs,
                fixture = fixture,
                bandKnots = if (freshProjection != null) inputs.pkBandKnots else emptyList(),
                now = now,
                zoneId = zoneId,
                chartWindowOption = chartWindowOption,
                displayUnit = homeE2DisplayUnit,
            ),
            hideReferenceRanges = inputs.settings.hideReferenceRanges,
            stockWarnings = inputs.stockWarnings,
            lowStockSectionExpanded = lowStockSectionExpanded,
            homeAnchor = inputs.homeAnchor?.toAnchorRowUiState(now.toLocalDate()),
            e2Hero = buildMainE2Hero(
                // The single-row `latestEstradiolEntry` query is bounded at the
                // frozen subscription `now`, so on its own it misses a dose the
                // user logs later in the same session. Feed the schedule window
                // (which carries today's logged doses) alongside it, mirroring
                // the antiandrogen card; buildMainE2Hero filters to estradiol
                // entries at or before the live `now` and picks the latest.
                entries = homeEntries + listOfNotNull(inputs.latestEstradiolEntry),
                trendResult = trendResult,
                displayUnit = homeE2DisplayUnit,
                zoneId = zoneId,
                now = now,
            ),
            e2Chart = buildMainE2Chart(
                trendResult = trendResult,
                displayUnit = homeE2DisplayUnit,
                chartWindowOption = chartWindowOption,
            ),
            antiandrogenGroupSections = listOf(
                MedicationCategory.ANTIANDROGEN,
                MedicationCategory.SERM,
                MedicationCategory.GNRH_AGONIST,
            ).mapNotNull { category ->
                val cards = buildMainAntiandrogenCards(
                    groups = inputs.activeGroups,
                    entries = homeEntries,
                    now = now,
                    zoneId = zoneId,
                    category = category,
                )
                cards.takeIf { it.isNotEmpty() }
                    ?.let { MainMedicationCategorySection(category = category, cards = it) }
            },
            todaySection = buildMainTodaySection(
                groups = scheduleGroups,
                entries = visibleEntries,
                now = now,
                zoneId = zoneId,
                includeUnloggedArchivedSlots = false,
                unloggedArchivedSlotCutoff = now,
            ),
            lastNightSection = buildMainLastNightSection(
                groups = scheduleGroups,
                entries = visibleEntries,
                now = now,
                zoneId = zoneId,
                includeUnloggedArchivedSlots = false,
                unloggedArchivedSlotCutoff = now,
            ),
            comingUpSection = buildMainComingUpSection(
                groups = scheduleGroups,
                entries = visibleEntries,
                now = now,
                zoneId = zoneId,
                includeUnloggedArchivedSlots = false,
                unloggedArchivedSlotCutoff = now,
            ),
            upcomingSection = buildMainUpcomingSection(
                groups = inputs.activeGroups,
                entries = homeEntries,
                now = now,
                zoneId = zoneId,
            ),
            timeZoneChangeNotice = timeZoneNotice,
        )
    }

    private fun shouldExpandLowStockSection(
        storedExpanded: Boolean,
        stockWarnings: List<MedicineStockProjection>,
        acknowledgedWarningStates: Map<String, MedicineStockState>,
    ): Boolean {
        if (storedExpanded) {
            return true
        }
        val currentWarningStates = stockWarnings.toLowStockWarningStateMap()
        return currentWarningStates.any { (uuid, currentState) ->
            val acknowledgedState = acknowledgedWarningStates[uuid]
            acknowledgedState == null ||
                    acknowledgedState.lowStockSeverityRank() < currentState.lowStockSeverityRank()
        }
    }

    private fun List<MedicineStockProjection>.toLowStockWarningStateMap(): Map<String, MedicineStockState> {
        return mapNotNull { projection ->
            projection.state
                .takeIf { state -> state.lowStockSeverityRank() > 0 }
                ?.let { state -> projection.medicine.uuid.toString() to state }
        }.toMap()
    }

    private data class HomeTimeKey(
        val now: LocalDateTime,
        val date: LocalDate,
        val zoneId: ZoneId,
    )

    private data class HomeSnapshotRefreshKey(
        val date: LocalDate,
        val zoneId: String,
    )

    private data class KeyedHomeInputs(val key: HomeTimeKey, val inputs: HomeInputs)

    /** Route details for the hero's info sheet; the live evaluation (or the debug fixture). */
    val pkCalibrationDetails: StateFlow<PkCalibrationUiState?> = combine(
        pkCalibrationLiveRepository.liveState,
        pkUiFixtureBridge.fixture,
    ) { liveState, fixture ->
        when {
            fixture != null -> pkCalibrationUiState(fixture.result, fixture.render)
            else -> (liveState as? PkCalibrationLiveState.Available)?.let { available ->
                pkCalibrationUiState(available.evaluation.result, available.render)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun buildMainPkCalibration(
        inputs: HomeInputs,
        fixture: PkCalibrationUiFixture?,
        bandKnots: List<PkPredictiveBandKnot>,
        now: LocalDateTime,
        zoneId: ZoneId,
        chartWindowOption: HomeE2ChartWindowOption,
        displayUnit: BloodUnitKey,
    ): MainPkCalibrationUiState? {
        fun band(knots: List<PkPredictiveBandKnot>) = buildMainE2CalibrationBand(
            bandKnots = knots,
            now = now,
            zoneId = zoneId,
            pastDays = chartWindowOption.pastDays,
            windowHours = chartWindowOption.chartWindowHours,
            displayUnit = displayUnit,
        )
        if (fixture != null) {
            val ui = pkCalibrationUiState(fixture.result, fixture.render)
            return MainPkCalibrationUiState(
                effectivePromotedRoutes = ui.effectivePromotedRoutes,
                limitedConfidence = ui.limitedConfidence,
                renderUnavailable = ui.renderState == PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
                bandUnavailable = ui.bandState == PkCalibrationBandState.NUMERIC_UNAVAILABLE,
                band = fixture.render
                    ?.takeIf { it.bandState == PkCalibrationBandState.READY }
                    ?.let { band(it.bandKnots) },
            )
        }
        val record = inputs.pkCalibration ?: return null
        return MainPkCalibrationUiState(
            effectivePromotedRoutes = record.effectivePromotedRoutes
                .mapNotNull(PkCalibrationRoute::fromStableId),
            limitedConfidence = record.limitedConfidence,
            renderUnavailable = record.renderUnavailable,
            bandUnavailable = record.bandUnavailable,
            band = band(bandKnots),
        )
    }

    private fun HomeTimeKey.matches(snapshot: AppTimeSnapshot): Boolean {
        return date == snapshot.minute.toLocalDate() &&
                zoneId == snapshot.zone
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
