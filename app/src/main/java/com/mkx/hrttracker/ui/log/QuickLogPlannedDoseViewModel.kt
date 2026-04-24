package com.mkx.hrttracker.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QuickLogPlannedDoseViewModel @Inject constructor(
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuickLogPlannedDoseUiState())
    val uiState: StateFlow<QuickLogPlannedDoseUiState> = _uiState.asStateFlow()

    private var loadedKey: QuickLogPlannedDoseLoadKey? = null

    fun initialize(
        groupId: UUID,
        scheduledFor: LocalDateTime,
        medicationDetails: MedicationDetails,
        medicationCount: Int,
    ) {
        val key = QuickLogPlannedDoseLoadKey(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicationDetails = medicationDetails,
            medicationCount = medicationCount.coerceAtLeast(1)
        )
        if (loadedKey == key) {
            return
        }
        loadedKey = key

        viewModelScope.launch {
            val group = medicationGroupRepository.getGroup(groupId)
            if (group == null) {
                loadedKey = null
                _uiState.value = QuickLogPlannedDoseUiState(isSaved = true)
                return@launch
            }
            val today = LocalDate.now()
            val now = LocalTime.now().withSecond(0).withNano(0)
            _uiState.value = QuickLogPlannedDoseUiState(
                group = group,
                scheduledFor = scheduledFor,
                draftEntries = buildCollapsedQuickLogDraftEntries(
                    details = medicationDetails,
                    count = medicationCount,
                    appliedDate = today,
                    appliedTime = now
                )
            )
        }
    }

    fun updateItemDate(localId: String, appliedDate: LocalDate) {
        _uiState.update { state ->
            state.copy(
                draftEntries = state.draftEntries.map {
                    if (it.localId == localId) it.copy(appliedDate = appliedDate) else it
                }
            )
        }
    }

    fun updateItemTime(localId: String, appliedTime: LocalTime) {
        _uiState.update { state ->
            state.copy(
                draftEntries = state.draftEntries.map {
                    if (it.localId == localId) {
                        it.copy(appliedTime = appliedTime.withSecond(0).withNano(0))
                    } else {
                        it
                    }
                }
            )
        }
    }

    fun saveEntries() {
        val state = _uiState.value
        val group = state.group ?: return
        val scheduledFor = state.scheduledFor ?: return
        if (state.draftEntries.isEmpty() || state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            state.draftEntries.forEach { entry ->
                val appliedAt = LocalDateTime.of(entry.appliedDate, entry.appliedTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                medicationLogRepository.saveEntry(
                    uuid = null,
                    medication = entry.details,
                    sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
                    sourceGroupUuid = group.uuid,
                    appliedAt = appliedAt,
                    scheduledFor = scheduledFor,
                    count = entry.count
                )
            }

            medicationReminderScheduler.rescheduleAll()

            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    fun reset() {
        loadedKey = null
        _uiState.value = QuickLogPlannedDoseUiState()
    }
}

data class QuickLogPlannedDoseUiState(
    val group: MedicationGroup? = null,
    val scheduledFor: LocalDateTime? = null,
    val draftEntries: List<QuickLogPlannedDoseItemUiState> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
)

data class QuickLogPlannedDoseItemUiState(
    val localId: String = UUID.randomUUID().toString(),
    val details: MedicationDetails,
    val appliedDate: LocalDate,
    val appliedTime: LocalTime,
    val count: Int = 1,
)

internal fun buildCollapsedQuickLogDraftEntries(
    details: MedicationDetails,
    count: Int,
    appliedDate: LocalDate,
    appliedTime: LocalTime,
): List<QuickLogPlannedDoseItemUiState> {
    return listOf(
        QuickLogPlannedDoseItemUiState(
            details = details,
            appliedDate = appliedDate,
            appliedTime = appliedTime,
            count = count.coerceAtLeast(1)
        )
    )
}

private data class QuickLogPlannedDoseLoadKey(
    val groupId: UUID,
    val scheduledFor: LocalDateTime,
    val medicationDetails: MedicationDetails,
    val medicationCount: Int,
)
