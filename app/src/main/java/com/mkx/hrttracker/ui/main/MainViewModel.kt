package com.mkx.hrttracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    medicationLogRepository: MedicationLogRepository,
    appTimeSource: AppTimeSource
) : ViewModel() {
    private val currentDateTime = appTimeSource.currentMinute

    val uiState: StateFlow<MainUiState> = combine(
        medicationGroupRepository.observeGroups(),
        medicationLogRepository.observeEntries(),
        currentDateTime
    ) { groupsOrNull, entriesOrNull, now ->
        val isLoading = groupsOrNull == null || entriesOrNull == null
        val groups = groupsOrNull.orEmpty()
        val entries = entriesOrNull.orEmpty()

        MainUiState(
            isLoading = isLoading,
            now = now,
            e2Hero = buildMainE2Hero(
                entries = entries
            ),
            e2Chart = buildMainE2Chart(),
            antiandrogenCards = buildMainAntiandrogenCards(
                groups = groups,
                entries = entries,
                now = now
            ),
            todaySection = buildMainTodaySection(
                groups = groups,
                entries = entries,
                now = now
            ),
            upcomingSection = buildMainUpcomingSection(
                groups = groups,
                entries = entries,
                now = now
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
