package com.mkx.hrttracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    medicationLogRepository: MedicationLogRepository,
    userProfileRepository: UserProfileRepository,
    private val settingsRepository: SettingsRepository,
    appTimeSource: AppTimeSource
) : ViewModel() {
    private val currentDateTime = appTimeSource.currentMinute
    private val zoneId = ZoneId.systemDefault()

    val uiState: StateFlow<MainUiState> = combine(
        medicationGroupRepository.observeGroups(),
        medicationLogRepository.observeEntries(),
        userProfileRepository.observeProfile(),
        settingsRepository.settingsState,
        currentDateTime
    ) { groupsOrNull, entriesOrNull, profileOrNull, settingsState, now ->
        val isLoading = groupsOrNull == null || entriesOrNull == null || profileOrNull == null
        val groups = groupsOrNull.orEmpty().filter(MedicationGroup::isActive)
        val entries = entriesOrNull.orEmpty()
        val homeE2DisplayUnit = settingsState.homeE2DisplayUnit
        val estradiolTrend = PkMedicationSimulation.simulateMainEstradiolTrend(
            entries = entries,
            bodyWeightKg = profileOrNull?.weightKg,
            now = now,
            zoneId = zoneId,
        )

        MainUiState(
            isLoading = isLoading,
            now = now,
            homeE2DisplayUnit = homeE2DisplayUnit,
            e2Hero = buildMainE2Hero(
                entries = entries,
                trendResult = estradiolTrend,
                displayUnit = homeE2DisplayUnit,
                zoneId = zoneId,
            ),
            e2Chart = buildMainE2Chart(
                trendResult = estradiolTrend,
                displayUnit = homeE2DisplayUnit,
            ),
            antiandrogenCards = buildMainAntiandrogenCards(
                groups = groups,
                entries = entries,
                now = now,
                zoneId = zoneId,
            ),
            todaySection = buildMainTodaySection(
                groups = groups,
                entries = entries,
                now = now,
                zoneId = zoneId,
            ),
            upcomingSection = buildMainUpcomingSection(
                groups = groups,
                entries = entries,
                now = now,
                zoneId = zoneId,
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
        initialValue = MainUiState(now = currentDateTime.value)
    )

    fun setHomeE2DisplayUnit(unit: BloodUnitKey) {
        viewModelScope.launch {
            settingsRepository.setHomeE2DisplayUnit(unit)
        }
    }

    private companion object {
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class MainUiState(
    val isLoading: Boolean = true,
    val now: LocalDateTime = LocalDateTime.now(),
    val homeE2DisplayUnit: BloodUnitKey = BloodUnitKey.PG_ML,
    val e2Hero: MainE2HeroUiState = MainE2HeroUiState(),
    val e2Chart: MainE2ChartUiState = MainE2ChartUiState(),
    val antiandrogenCards: List<MainAntiandrogenCardUiState> = emptyList(),
    val todaySection: MainTodaySectionUiState = MainTodaySectionUiState(
        date = now.toLocalDate()
    ),
    val upcomingSection: MainUpcomingSectionUiState = MainUpcomingSectionUiState()
)
