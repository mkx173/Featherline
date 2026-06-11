package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.systemLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ArchivedMedicationGroupsViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    settingsRepository: SettingsRepository,
    appTimeSource: AppTimeSource,
) : ViewModel() {
    private val currentDateTime = appTimeSource.currentMinute

    val uiState: StateFlow<ArchivedMedicationGroupsUiState> = combine(
        medicationGroupRepository.observeGroups(),
        settingsRepository.settingsState,
        currentDateTime,
    ) { groupsOrNull, settingsState, now ->
        ArchivedMedicationGroupsUiState(
            isLoading = groupsOrNull == null,
            today = now.toLocalDate(),
            firstDayOfWeek = settingsState.firstDayOfWeekOption.resolve(systemLocale()),
            groups = sortPlanMedicationGroups(groupsOrNull.orEmpty())
                .filter(MedicationGroup::isArchived),
        )
    }
        .stateIn(
            scope = viewModelScope,
            // Lazily, not WhileSubscribed: the first frame after a subscriber
            // returns renders the retained StateFlow value before collection
            // restarts, so a stop-timeout lets data mutated while away (e.g. a
            // backup restore) flash one stale frame on re-entry. The repository
            // flows are hot (Eagerly, app-scoped) regardless; staying collected
            // only adds the ui-state rebuild work.
            started = SharingStarted.Lazily,
            initialValue = ArchivedMedicationGroupsUiState(
                today = currentDateTime.value.toLocalDate(),
            )
        )
}

data class ArchivedMedicationGroupsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val groups: List<MedicationGroup> = emptyList(),
)
