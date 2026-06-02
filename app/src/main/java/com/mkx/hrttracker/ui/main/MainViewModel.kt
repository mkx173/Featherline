package com.mkx.hrttracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.HomeInputSource
import com.mkx.hrttracker.data.repository.HomeInputs
import com.mkx.hrttracker.data.repository.HomeRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.visibleMedicationEntries
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.AppTimeSnapshot
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.TimeZoneChangeNotice
import com.mkx.hrttracker.util.TimeZoneChangeNoticeController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    appTimeSource: AppTimeSource,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val currentSnapshot = appTimeSource.currentSnapshot
    private var lastHomeSnapshotRefreshKey: HomeSnapshotRefreshKey? = null

    // Re-subscribe the home inputs flow only on local date or zone change.
    // Within a stable date+zone, the underlying Room flows already emit on data
    // changes; piping per-minute ticks through `flatMapLatest` would tear down
    // and rebuild the snapshot/Room race, every Room observer in
    // `observeHomeStartupInputs`, and the `combine` layered on top of them. The
    // minute tick is still observed via `combine` below so `buildHomeUiState`
    // can recompute the PK trend, "next dose in N min", and other
    // `now`-dependent UI per minute without re-issuing any queries.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MainUiState> = combine(
        currentSnapshot
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
                homeRepository.observeHomeInputs(key.now, key.zoneId)
                    .map { inputs -> KeyedHomeInputs(key = key, inputs = inputs) }
            },
        currentSnapshot,
        timeZoneChangeNoticeController.notice,
        settingsRepository.homeLowStockSectionExpandedFlow,
        settingsRepository.homeLowStockAcknowledgedWarningStatesFlow,
    ) { keyedInputs, timeSnapshot, timeZoneNotice, storedLowStockSectionExpanded, acknowledgedWarningStates ->
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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
            initialValue = MainUiState(
                homeDataReady = false,
                now = currentSnapshot.value.minute,
            )
        )

    private val _highlightRequest = MutableStateFlow<DoseRowHighlightRequest?>(null)
    val highlightRequest: StateFlow<DoseRowHighlightRequest?> = _highlightRequest.asStateFlow()

    private val _homeDeepLinkSignal = MutableStateFlow(0)
    val homeDeepLinkSignal: StateFlow<Int> = _homeDeepLinkSignal.asStateFlow()

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

    private fun refreshHomeSnapshotForDateIfNeeded(now: LocalDateTime, zoneId: ZoneId) {
        val key = HomeSnapshotRefreshKey(
            date = now.toLocalDate(),
            zoneId = zoneId.id,
        )
        if (lastHomeSnapshotRefreshKey == key) {
            return
        }
        lastHomeSnapshotRefreshKey = key
        homeRepository.refreshHomeSnapshotAsync(now = now, force = false)
    }

    private fun buildHomeUiState(
        inputs: HomeInputs,
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
        val scheduleGroups = if (inputs.settings.showArchivedGroupRecords) allGroups else inputs.activeGroups
        // HomeInputs was constructed against the date-scoped anchor `now` at
        // flow subscription time. The live `now` ticks per minute and may
        // cross a planned-slot expiry without a fresh upstream emission, so
        // re-validate against the live `now` here:
        //   - pkProjection: drop if the cached expiry is on or before now.
        //   - estradiolPkPlannedEntries: drop synthetic doses whose slot has
        //     already passed (the user didn't take them — don't keep them on
        //     the curve as if they did).
        val nowInstant = now.atZone(zoneId).toInstant()
        val freshProjection = inputs.pkProjection?.takeIf {
            inputs.pkProjectionExpiresAt?.isAfter(nowInstant) ?: true
        }
        val freshPlannedEntries = inputs.estradiolPkPlannedEntries.filter { entry ->
            entry.scheduledFor?.isAfter(now) ?: false
        }
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
            hideReferenceRanges = inputs.settings.hideReferenceRanges,
            stockWarnings = inputs.stockWarnings,
            lowStockSectionExpanded = lowStockSectionExpanded,
            e2Hero = buildMainE2Hero(
                entries = listOfNotNull(inputs.latestEstradiolEntry),
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
            antiandrogenCards = buildMainAntiandrogenCards(
                groups = inputs.activeGroups,
                entries = homeEntries,
                now = now,
                zoneId = zoneId,
            ),
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

    private fun MedicineStockState.lowStockSeverityRank(): Int {
        return when (this) {
            MedicineStockState.USER_LOW -> 1
            MedicineStockState.IMMINENT -> 2
            MedicineStockState.OUT -> 3
            MedicineStockState.HEALTHY,
            MedicineStockState.UNTRACKED,
            MedicineStockState.NO_RUNWAY -> 0
        }
    }

    private data class HomeTimeKey(val now: LocalDateTime, val date: LocalDate, val zoneId: ZoneId)

    private data class HomeSnapshotRefreshKey(val date: LocalDate, val zoneId: String)

    private data class KeyedHomeInputs(val key: HomeTimeKey, val inputs: HomeInputs)

    private fun HomeTimeKey.matches(snapshot: AppTimeSnapshot): Boolean {
        return date == snapshot.minute.toLocalDate() && zoneId == snapshot.zone
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class MainUiState(
    val homeDataReady: Boolean = false,
    val e2TrendReady: Boolean = false,
    val homeSource: HomeInputSource? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val homeE2DisplayUnit: BloodUnitKey = BloodUnitKey.PG_ML,
    val homeE2ChartWindowOption: HomeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
    val hideReferenceRanges: Boolean = false,
    val stockWarnings: List<MedicineStockProjection> = emptyList(),
    val lowStockSectionExpanded: Boolean = true,
    val e2Hero: MainE2HeroUiState = MainE2HeroUiState(),
    val e2Chart: MainE2ChartUiState = MainE2ChartUiState(),
    val antiandrogenCards: List<MainAntiandrogenCardUiState> = emptyList(),
    val todaySection: MainTodaySectionUiState = MainTodaySectionUiState(
        date = now.toLocalDate()
    ),
    val lastNightSection: MainLastNightSectionUiState = MainLastNightSectionUiState(),
    val upcomingSection: MainUpcomingSectionUiState = MainUpcomingSectionUiState(),
    val timeZoneChangeNotice: TimeZoneChangeNotice? = null,
) {
    val splashReady: Boolean
        get() = homeDataReady
}
