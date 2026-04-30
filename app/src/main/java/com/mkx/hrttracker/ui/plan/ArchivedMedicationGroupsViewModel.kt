package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ArchivedMedicationGroupsViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    appTimeSource: AppTimeSource,
) : ViewModel() {
    private val currentDateTime = appTimeSource.currentMinute

    val uiState: StateFlow<ArchivedMedicationGroupsUiState> = combine(
        medicationGroupRepository.observeGroups(),
        currentDateTime,
    ) { groupsOrNull, now ->
        ArchivedMedicationGroupsUiState(
            isLoading = groupsOrNull == null,
            today = now.toLocalDate(),
            groups = sortPlanMedicationGroups(groupsOrNull.orEmpty())
                .filter(MedicationGroup::isArchived),
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
            initialValue = ArchivedMedicationGroupsUiState(
                today = currentDateTime.value.toLocalDate(),
            )
        )

    private companion object {
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class ArchivedMedicationGroupsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val groups: List<MedicationGroup> = emptyList(),
)
