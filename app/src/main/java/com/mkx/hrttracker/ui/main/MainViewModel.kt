package com.mkx.hrttracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    medicationLogRepository: MedicationLogRepository
) : ViewModel() {
    private val currentDateTime = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(CURRENT_TIME_REFRESH_INTERVAL_MILLIS)
        }
    }

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
        started = SharingStarted.Eagerly,
        initialValue = MainUiState()
    )

    private companion object {
        const val CURRENT_TIME_REFRESH_INTERVAL_MILLIS = 60_000L
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
