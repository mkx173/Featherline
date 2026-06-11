package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.systemLocale
import com.mkx.hrttracker.util.tickWhileSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ArchivedMedicationGroupsViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    settingsRepository: SettingsRepository,
    appTimeSource: AppTimeSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ArchivedMedicationGroupsUiState(
            today = appTimeSource.currentMinute.value.toLocalDate(),
        )
    )

    // The ui-state combine below stays collected for the ViewModel's whole
    // lifetime (nav-entry-scoped, so bounded): a stop-timeout would let data
    // mutated while away (e.g. a backup restore) flash one stale frame on
    // re-entry. The time input is gated on this state's own subscriber count,
    // matching the Main/Plan idiom: while unsubscribed only date changes pass
    // through, so minute ticks don't rebuild the ui state with nobody looking.
    val uiState: StateFlow<ArchivedMedicationGroupsUiState> = _uiState.asStateFlow()

    private val currentDateTime =
        appTimeSource.currentMinute.tickWhileSubscribed(_uiState.subscriptionCount) { minute ->
            minute.toLocalDate()
        }

    init {
        viewModelScope.launch {
            combine(
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
            }.collect { state -> _uiState.value = state }
        }
    }
}

data class ArchivedMedicationGroupsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val groups: List<MedicationGroup> = emptyList(),
)
