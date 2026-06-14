package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogEntryInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.ownsUnloggedOccurrence
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import com.mkx.hrttracker.ui.plan.buildPlanBatchAddOccurrences

data class PlanRecordsUiState(
    val isLoading: Boolean = true,
    val groups: List<MedicationGroup> = emptyList(),
    val selectedGroupUuid: UUID? = null,
    val selectedGroupName: String = "",
    val selectedGroupColorKey: MedicationGroupColorKey? = null,
    val items: List<PlanRecordItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveCount: Int = 0,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val deleteCount: Int = 0,
)

data class PlanRecordItem(
    val id: String, // "logged|<uuid>" or "scheduled|<groupUuid>|<scheduleTimeUuid>|<time>|<medicationUuid>"
    val isLogged: Boolean,
    val logEntry: MedicationLogEntry? = null,
    val scheduledInput: MedicationLogEntryInput? = null,
    val medicine: Medicine? = null,
    val fallbackName: String = "",
    val doseInstruction: DoseInstruction = DoseInstruction.Noop,
    val doseAmountDelta: Double? = null,
    val count: Int = 1,
    val time: LocalDateTime,
)

data class PlanRecordsOperationalState(
    val selectedGroupUuid: UUID?,
    val selectedIds: Set<String>,
    val isSaving: Boolean,
    val isSaved: Boolean,
    val saveCount: Int,
    val isDeleting: Boolean,
    val isDeleted: Boolean,
    val deleteCount: Int
)

@HiltViewModel
class PlanRecordsViewModel @Inject constructor(
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    appTimeSource: AppTimeSource,
) : ViewModel() {

    private val selectedGroupUuidFlow = MutableStateFlow<UUID?>(null)
    private val selectedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    
    private val isSavingFlow = MutableStateFlow(false)
    private val isSavedFlow = MutableStateFlow(false)
    private val saveCountFlow = MutableStateFlow(0)

    private val isDeletingFlow = MutableStateFlow(false)
    private val isDeletedFlow = MutableStateFlow(false)
    private val deleteCountFlow = MutableStateFlow(0)

    private val currentDateTime = appTimeSource.currentMinute

    private val operationalStateFlow = combine(
        selectedGroupUuidFlow,
        selectedIdsFlow,
        isSavingFlow,
        isSavedFlow,
        saveCountFlow,
        isDeletingFlow,
        isDeletedFlow,
        deleteCountFlow
    ) { args: Array<Any?> ->
        PlanRecordsOperationalState(
            selectedGroupUuid = args[0] as UUID?,
            selectedIds = args[1] as Set<String>,
            isSaving = args[2] as Boolean,
            isSaved = args[3] as Boolean,
            saveCount = args[4] as Int,
            isDeleting = args[5] as Boolean,
            isDeleted = args[6] as Boolean,
            deleteCount = args[7] as Int
        )
    }

    val uiState: StateFlow<PlanRecordsUiState> = combine(
        medicationGroupRepository.observeGroups(),
        medicationLogRepository.observeEntries(),
        currentDateTime,
        operationalStateFlow
    ) { groupsOrNull, entriesOrNull, now, operationalState ->
        val groups = groupsOrNull.orEmpty().filter(MedicationGroup::isActive)
        val entries = entriesOrNull.orEmpty()
        
        val activeGroupUuid = operationalState.selectedGroupUuid ?: groups.firstOrNull()?.uuid
        val selectedGroup = groups.firstOrNull { it.uuid == activeGroupUuid }

        val items = if (selectedGroup != null) {
            buildRecordItems(selectedGroup, entries, now.toLocalDate(), now)
        } else {
            emptyList()
        }

        // Clean up selectedIds that are no longer in the list
        val validIds = items.map { it.id }.toSet()
        val filteredSelectedIds = operationalState.selectedIds.intersect(validIds)

        PlanRecordsUiState(
            isLoading = groupsOrNull == null || entriesOrNull == null,
            groups = groups,
            selectedGroupUuid = selectedGroup?.uuid,
            selectedGroupName = selectedGroup?.name.orEmpty(),
            selectedGroupColorKey = selectedGroup?.colorKey,
            items = items,
            selectedIds = filteredSelectedIds,
            isSaving = operationalState.isSaving,
            isSaved = operationalState.isSaved,
            saveCount = operationalState.saveCount,
            isDeleting = operationalState.isDeleting,
            isDeleted = operationalState.isDeleted,
            deleteCount = operationalState.deleteCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlanRecordsUiState(),
    )

    fun selectGroup(groupUuid: UUID) {
        selectedGroupUuidFlow.value = groupUuid
        selectedIdsFlow.value = emptySet()
        isSavedFlow.value = false
        isDeletedFlow.value = false
    }

    fun toggleSelection(itemId: String) {
        selectedIdsFlow.update { current ->
            if (itemId in current) current - itemId else current + itemId
        }
    }

    fun selectAll() {
        val currentItems = uiState.value.items
        selectedIdsFlow.value = currentItems.map { it.id }.toSet()
    }

    fun clearSelection() {
        selectedIdsFlow.value = emptySet()
    }

    fun consumeSaveStatus() {
        isSavedFlow.value = false
    }

    fun consumeDeleteStatus() {
        isDeletedFlow.value = false
    }

    fun logSelected() {
        val selected = selectedIdsFlow.value
        val currentItems = uiState.value.items
        val inputs = currentItems.filter { it.id in selected && !it.isLogged }
            .mapNotNull { it.scheduledInput }

        if (inputs.isEmpty()) return

        viewModelScope.launch {
            isSavingFlow.value = true
            try {
                medicationLogRepository.saveNewEntries(inputs)
                saveCountFlow.value = inputs.size
                isSavedFlow.value = true
                selectedIdsFlow.value = emptySet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Handle error
            } finally {
                isSavingFlow.value = false
            }
        }
    }

    fun deleteSelected() {
        val selected = selectedIdsFlow.value
        val currentItems = uiState.value.items
        val entryUuids = currentItems.filter { it.id in selected && it.isLogged }
            .mapNotNull { it.logEntry?.uuid }

        if (entryUuids.isEmpty()) return

        viewModelScope.launch {
            isDeletingFlow.value = true
            try {
                medicationLogRepository.deleteEntries(entryUuids)
                deleteCountFlow.value = entryUuids.size
                isDeletedFlow.value = true
                selectedIdsFlow.value = emptySet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Handle error
            } finally {
                isDeletingFlow.value = false
            }
        }
    }

    private fun buildRecordItems(
        group: MedicationGroup,
        entries: List<MedicationLogEntry>,
        today: LocalDate,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<PlanRecordItem> {
        val groupLogged = entries.filter { it.sourceGroupUuid == group.uuid }
        
        // Logged items
        val loggedItems = groupLogged.flatMap { entry ->
            val medicine = group.medications.firstOrNull { it.medicineUuid == entry.medicineUuid }
            listOf(
                PlanRecordItem(
                    id = "logged|${entry.uuid}",
                    isLogged = true,
                    logEntry = entry,
                    medicine = medicine?.medicine,
                    fallbackName = entry.medicineUuid?.toString().orEmpty(),
                    doseInstruction = entry.doseInstruction,
                    doseAmountDelta = entry.doseAmountDelta,
                    count = entry.count,
                    time = entry.appliedAt.atZone(zoneId).toLocalDateTime()
                )
            )
        }

        // Unlogged occurrences from the group since to today (capped at now)
        val fulfilledPlanSlots = groupLogged.mapNotNull(MedicationLogEntry::scheduledFor).toSet()
        val scheduledItems = mutableListOf<PlanRecordItem>()

        val planOccurrences = buildPlanBatchAddOccurrences(
            schedule = group.schedule,
            startDate = group.schedule.since,
            endDate = today
        )

        planOccurrences.forEach { occurrence ->
            if (occurrence.isAfter(now)) {
                return@forEach
            }
            if (occurrence in fulfilledPlanSlots) {
                return@forEach
            }
            val scheduleTime = group.schedule.timeSlots.firstOrNull { slot ->
                slot.time == occurrence.toLocalTime()
            } ?: return@forEach

            if (!group.ownsUnloggedOccurrence(scheduleTime, occurrence)) {
                return@forEach
            }

            group.medications.forEach { medication ->
                val input = MedicationLogEntryInput(
                    medicineUuid = medication.medicineUuid,
                    applicationType = medication.applicationType,
                    doseInstruction = medication.doseInstruction,
                    sourceGroupUuid = group.uuid,
                    scheduleTimeUuid = scheduleTime.uuid,
                    appliedAt = occurrence.atZone(zoneId).toInstant(),
                    scheduledFor = occurrence,
                    count = medication.count,
                    appliedAtTimeZoneId = zoneId.id,
                )
                scheduledItems += PlanRecordItem(
                    id = "scheduled|${group.uuid}|${scheduleTime.uuid}|${occurrence}|${medication.medicineUuid}",
                    isLogged = false,
                    scheduledInput = input,
                    medicine = medication.medicine,
                    fallbackName = medication.medicineUuid?.toString().orEmpty(),
                    doseInstruction = medication.doseInstruction,
                    count = medication.count,
                    time = occurrence
                )
            }
        }

        return (loggedItems + scheduledItems).sortedByDescending { it.time }
    }
}
