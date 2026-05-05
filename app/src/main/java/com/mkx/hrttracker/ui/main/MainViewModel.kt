package com.mkx.hrttracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    medicationLogRepository: MedicationLogRepository,
    userProfileRepository: UserProfileRepository,
    appTimeSource: AppTimeSource
) : ViewModel() {
    private val currentDateTime = appTimeSource.currentMinute
    private val zoneId = ZoneId.systemDefault()

    val uiState: StateFlow<MainUiState> = combine(
        medicationGroupRepository.observeGroups(),
        medicationLogRepository.observeEntries(),
        userProfileRepository.observeProfile(),
        currentDateTime
    ) { groupsOrNull, entriesOrNull, profileOrNull, now ->
        val isLoading = groupsOrNull == null || entriesOrNull == null || profileOrNull == null
        val groups = groupsOrNull.orEmpty().filter(MedicationGroup::isActive)
        val entries = entriesOrNull.orEmpty()
        val estradiolTrend = PkMedicationSimulation.simulateMainEstradiolTrend(
            entries = entries,
            bodyWeightKg = profileOrNull?.weightKg,
            now = now,
            zoneId = zoneId,
        )

        MainUiState(
            isLoading = isLoading,
            now = now,
            e2Hero = buildMainE2Hero(
                entries = entries,
                trendResult = estradiolTrend,
                zoneId = zoneId,
            ),
            e2Chart = buildMainE2Chart(
                trendResult = estradiolTrend,
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

    private companion object {
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class MainUiState(
    val isLoading: Boolean = true,
    val now: LocalDateTime = LocalDateTime.now(),
    val e2Hero: MainE2HeroUiState = MainE2HeroUiState(),
    val e2Chart: MainE2ChartUiState = MainE2ChartUiState(),
    val antiandrogenCards: List<MainAntiandrogenCardUiState> = emptyList(),
    val todaySection: MainTodaySectionUiState = MainTodaySectionUiState(
        date = now.toLocalDate()
    ),
    val upcomingSection: MainUpcomingSectionUiState = MainUpcomingSectionUiState()
)
