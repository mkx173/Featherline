package com.mkx.hrttracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.HomeInputSource
import com.mkx.hrttracker.data.repository.HomeInputs
import com.mkx.hrttracker.data.repository.HomeRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
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
    appTimeSource: AppTimeSource,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val currentDateTime = appTimeSource.currentMinute
    private val zoneId = ZoneId.systemDefault()
    private var lastHomeSnapshotRefreshDate: LocalDate? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MainUiState> = currentDateTime
        .flatMapLatest { now ->
            refreshHomeSnapshotForDateIfNeeded(now)
            homeRepository.observeHomeInputs(now)
                .mapLatest { inputs ->
                    withContext(defaultDispatcher) {
                        buildHomeUiState(inputs = inputs, now = now)
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
            initialValue = MainUiState(
                homeDataReady = false,
                now = currentDateTime.value,
            )
        )

    fun setHomeE2DisplayUnit(unit: BloodUnitKey) {
        viewModelScope.launch {
            settingsRepository.setHomeE2DisplayUnit(unit)
        }
    }

    private companion object {
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
    }

    private fun refreshHomeSnapshotForDateIfNeeded(now: LocalDateTime) {
        val date = now.toLocalDate()
        if (lastHomeSnapshotRefreshDate == date) {
            return
        }
        lastHomeSnapshotRefreshDate = date
        homeRepository.refreshHomeSnapshotAsync(now = now, force = false)
    }

    private fun buildHomeUiState(
        inputs: HomeInputs,
        now: LocalDateTime,
    ): MainUiState {
        val homeE2DisplayUnit = inputs.settings.homeE2DisplayUnit
        val homeEntries = (inputs.scheduleEntries + inputs.antiandrogenHistoryEntries)
            .distinctBy { entry -> entry.uuid }
            .sortedByDescending { entry -> entry.appliedAt }
        val trendResult = inputs.trendResult ?: PkMedicationSimulation.simulateMainEstradiolTrend(
            entries = inputs.estradiolPkEntries,
            bodyWeightKg = inputs.profile.weightKg,
            now = now,
            zoneId = zoneId,
        )

        return MainUiState(
            homeDataReady = true,
            homeSource = inputs.source,
            now = now,
            homeE2DisplayUnit = homeE2DisplayUnit,
            e2Hero = buildMainE2Hero(
                entries = listOfNotNull(inputs.latestEstradiolEntry),
                trendResult = trendResult,
                displayUnit = homeE2DisplayUnit,
                zoneId = zoneId,
            ),
            e2Chart = buildMainE2Chart(
                trendResult = trendResult,
                displayUnit = homeE2DisplayUnit,
            ),
            antiandrogenCards = buildMainAntiandrogenCards(
                groups = inputs.activeGroups,
                entries = homeEntries,
                now = now,
                zoneId = zoneId,
            ),
            todaySection = buildMainTodaySection(
                groups = inputs.activeGroups,
                entries = homeEntries,
                now = now,
                zoneId = zoneId,
            ),
            upcomingSection = buildMainUpcomingSection(
                groups = inputs.activeGroups,
                entries = homeEntries,
                now = now,
                zoneId = zoneId,
            )
        )
    }
}

data class MainUiState(
    val homeDataReady: Boolean = false,
    val homeSource: HomeInputSource? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val homeE2DisplayUnit: BloodUnitKey = BloodUnitKey.PG_ML,
    val e2Hero: MainE2HeroUiState = MainE2HeroUiState(),
    val e2Chart: MainE2ChartUiState = MainE2ChartUiState(),
    val antiandrogenCards: List<MainAntiandrogenCardUiState> = emptyList(),
    val todaySection: MainTodaySectionUiState = MainTodaySectionUiState(
        date = now.toLocalDate()
    ),
    val upcomingSection: MainUpcomingSectionUiState = MainUpcomingSectionUiState()
) {
    val splashReady: Boolean
        get() = homeDataReady
}
