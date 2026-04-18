package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository
) : ViewModel() {
    val uiState: StateFlow<PlanUiState> = medicationGroupRepository.observeGroups()
        .map { groups -> PlanUiState(medicationGroups = groups) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlanUiState()
        )
}

data class PlanUiState(
    val medicationGroups: List<MedicationGroup> = emptyList(),
)
