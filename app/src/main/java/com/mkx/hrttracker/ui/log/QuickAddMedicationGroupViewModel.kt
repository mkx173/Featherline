package com.mkx.hrttracker.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QuickAddMedicationGroupViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository
) : ViewModel() {
    private val groups = medicationGroupRepository.observeGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
    private val selectedGroupId = MutableStateFlow<UUID?>(null)
    private val draftEntries = MutableStateFlow<List<QuickAddMedicationGroupItemUiState>>(emptyList())
    private val isSaving = MutableStateFlow(false)
    private val isSaved = MutableStateFlow(false)

    val uiState: StateFlow<QuickAddMedicationGroupUiState> = combine(
        groups,
        selectedGroupId,
        draftEntries,
        isSaving,
        isSaved
    ) { availableGroups, currentSelectedGroupId, currentDraftEntries, currentIsSaving, currentIsSaved ->
        QuickAddMedicationGroupUiState(
            groups = availableGroups,
            selectedGroup = availableGroups.firstOrNull { it.uuid == currentSelectedGroupId },
            draftEntries = currentDraftEntries,
            isSaving = currentIsSaving,
            isSaved = currentIsSaved
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QuickAddMedicationGroupUiState()
    )

    fun initialize() {
        isSaved.value = false
    }

    fun selectGroup(groupId: UUID) {
        val group = groups.value.firstOrNull { it.uuid == groupId } ?: return
        val defaultDate = LocalDate.now()
        val defaultTime = LocalTime.now().withSecond(0).withNano(0)

        selectedGroupId.value = groupId
        draftEntries.value = group.medications.map { medication ->
            QuickAddMedicationGroupItemUiState(
                groupMedicationId = medication.uuid,
                routeOfAdministration = medication.routeOfAdministration,
                medicineName = medication.medicineName,
                dosageMgAsMedicine = medication.dosageMgAsMedicine,
                appliedDate = defaultDate,
                appliedTime = defaultTime
            )
        }
    }

    fun clearSelectedGroup() {
        selectedGroupId.value = null
        draftEntries.value = emptyList()
    }

    fun updateItemTime(localId: String, appliedTime: LocalTime) {
        draftEntries.update { currentEntries ->
            currentEntries.map { entry ->
                if (entry.localId == localId) {
                    entry.copy(appliedTime = appliedTime.withSecond(0).withNano(0))
                } else {
                    entry
                }
            }
        }
    }

    fun updateItemDate(localId: String, appliedDate: LocalDate) {
        draftEntries.update { currentEntries ->
            currentEntries.map { entry ->
                if (entry.localId == localId) {
                    entry.copy(appliedDate = appliedDate)
                } else {
                    entry
                }
            }
        }
    }

    fun saveEntries() {
        val currentEntries = draftEntries.value
        val currentGroupId = selectedGroupId.value
        if (currentEntries.isEmpty()) {
            return
        }
        if (currentGroupId == null) {
            return
        }

        viewModelScope.launch {
            isSaving.value = true

            currentEntries.forEach { entry ->
                val appliedAt = LocalDateTime.of(entry.appliedDate, entry.appliedTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()

                medicationLogRepository.saveEntry(
                    uuid = null,
                    routeOfAdministration = entry.routeOfAdministration,
                    medicineName = entry.medicineName,
                    dosageMgAsMedicine = entry.dosageMgAsMedicine,
                    sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
                    sourceGroupUuid = currentGroupId,
                    appliedAt = appliedAt
                )
            }

            isSaving.value = false
            isSaved.value = true
        }
    }

    fun reset() {
        selectedGroupId.value = null
        draftEntries.value = emptyList()
        isSaving.value = false
        isSaved.value = false
    }
}

data class QuickAddMedicationGroupUiState(
    val groups: List<MedicationGroup> = emptyList(),
    val selectedGroup: MedicationGroup? = null,
    val draftEntries: List<QuickAddMedicationGroupItemUiState> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
)

data class QuickAddMedicationGroupItemUiState(
    val localId: String = UUID.randomUUID().toString(),
    val groupMedicationId: UUID,
    val routeOfAdministration: RouteOfAdministration,
    val medicineName: String,
    val dosageMgAsMedicine: Double,
    val appliedDate: LocalDate,
    val appliedTime: LocalTime,
)
