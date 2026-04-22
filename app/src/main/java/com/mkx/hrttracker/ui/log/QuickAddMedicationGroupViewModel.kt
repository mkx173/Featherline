package com.mkx.hrttracker.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.expandedInstances
import com.mkx.hrttracker.model.medication.occurrencesBetween
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val medicationLogRepository: MedicationLogRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler
) : ViewModel() {
    private val groups = medicationGroupRepository.observeGroups()
        .map { it.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
    private val entries = medicationLogRepository.observeEntries()
        .map { it.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
    private val selectedGroupId = MutableStateFlow<UUID?>(null)
    private val selectedSlotId = MutableStateFlow<String?>(null)
    private val draftEntries = MutableStateFlow<List<QuickAddMedicationGroupItemUiState>>(emptyList())
    private val isSaving = MutableStateFlow(false)
    private val isSaved = MutableStateFlow(false)

    val uiState: StateFlow<QuickAddMedicationGroupUiState> = combine(
        combine(groups, selectedGroupId, entries) { availableGroups, currentSelectedGroupId, currentEntries ->
            val selectedGroup = availableGroups.firstOrNull { it.uuid == currentSelectedGroupId }
            val availableSlots = selectedGroup?.let { buildAvailableSlots(it, currentEntries) }.orEmpty()
            Triple(availableGroups, selectedGroup, availableSlots)
        },
        selectedSlotId,
        draftEntries,
        isSaving,
        isSaved
    ) { (availableGroups, selectedGroup, availableSlots), currentSelectedSlotId, currentDraftEntries, currentIsSaving, currentIsSaved ->
        QuickAddMedicationGroupUiState(
            groups = availableGroups,
            selectedGroup = selectedGroup,
            availableSlots = availableSlots,
            selectedSlotId = currentSelectedSlotId.takeIf { id -> availableSlots.any { it.slotId == id } },
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
        selectedSlotId.value = null
        draftEntries.value = group.medications.expandedInstances().map { medication ->
            QuickAddMedicationGroupItemUiState(
                groupMedicationId = medication.groupMedicationUuid,
                details = medication.details,
                appliedDate = defaultDate,
                appliedTime = defaultTime
            )
        }
    }

    fun clearSelectedGroup() {
        selectedGroupId.value = null
        selectedSlotId.value = null
        draftEntries.value = emptyList()
    }

    fun selectSlot(slotId: String?) {
        if (slotId == null) {
            selectedSlotId.value = null
            return
        }

        val slot = uiState.value.availableSlots.firstOrNull { it.slotId == slotId } ?: return
        selectedSlotId.value = slotId
        draftEntries.update { currentEntries ->
            currentEntries.map { entry ->
                entry.copy(appliedDate = slot.date, appliedTime = slot.time)
            }
        }
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

        val scheduledFor = uiState.value.availableSlots
            .firstOrNull { it.slotId == selectedSlotId.value }
            ?.let { LocalDateTime.of(it.date, it.time) }

        viewModelScope.launch {
            isSaving.value = true

            currentEntries.forEach { entry ->
                val appliedAt = LocalDateTime.of(entry.appliedDate, entry.appliedTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()

                medicationLogRepository.saveEntry(
                    uuid = null,
                    medication = entry.details,
                    sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
                    sourceGroupUuid = currentGroupId,
                    appliedAt = appliedAt,
                    scheduledFor = scheduledFor
                )
            }

            medicationReminderScheduler.rescheduleAll()

            isSaving.value = false
            isSaved.value = true
        }
    }

    fun reset() {
        selectedGroupId.value = null
        selectedSlotId.value = null
        draftEntries.value = emptyList()
        isSaving.value = false
        isSaved.value = false
    }

    private fun buildAvailableSlots(
        group: MedicationGroup,
        allEntries: List<MedicationLogEntry>
    ): List<QuickAddScheduleSlotUiOption> {
        val today = LocalDate.now()
        val windowStart = today.minusDays(SLOT_WINDOW_PAST_DAYS)
        val windowEnd = today.plusDays(SLOT_WINDOW_FUTURE_DAYS)

        val fulfilledSlots = allEntries
            .filter { it.sourceGroupUuid == group.uuid && it.scheduledFor != null }
            .mapNotNull { it.scheduledFor }
            .toSet()

        return group.schedule.occurrencesBetween(windowStart, windowEnd)
            .filter { occurrence -> occurrence !in fulfilledSlots }
            .map { occurrence ->
                QuickAddScheduleSlotUiOption(
                    slotId = occurrence.toString(),
                    date = occurrence.toLocalDate(),
                    time = occurrence.toLocalTime()
                )
            }
    }

    private companion object {
        const val SLOT_WINDOW_PAST_DAYS = 3L
        const val SLOT_WINDOW_FUTURE_DAYS = 1L
    }
}

data class QuickAddMedicationGroupUiState(
    val groups: List<MedicationGroup> = emptyList(),
    val selectedGroup: MedicationGroup? = null,
    val availableSlots: List<QuickAddScheduleSlotUiOption> = emptyList(),
    val selectedSlotId: String? = null,
    val draftEntries: List<QuickAddMedicationGroupItemUiState> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
)

data class QuickAddMedicationGroupItemUiState(
    val localId: String = UUID.randomUUID().toString(),
    val groupMedicationId: UUID,
    val details: MedicationDetails,
    val appliedDate: LocalDate,
    val appliedTime: LocalTime,
)

data class QuickAddScheduleSlotUiOption(
    val slotId: String,
    val date: LocalDate,
    val time: LocalTime,
)
