package com.mkx.hrttracker.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isWithinScheduleFulfillmentWindow
import com.mkx.hrttracker.model.medication.nextScheduledForAfter
import com.mkx.hrttracker.model.medication.previousScheduledForBefore
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.ui.medication.MedicationDraftUiState
import com.mkx.hrttracker.util.appliedAtAsLocalDateTime
import com.mkx.hrttracker.util.displayZoneOf
import com.mkx.hrttracker.ui.medication.defaultMedicationDraft
import com.mkx.hrttracker.ui.medication.medicationCountValidationErrorRes
import com.mkx.hrttracker.ui.medication.medicationDraftFromDetails
import com.mkx.hrttracker.ui.medication.normalizeMedicationCount
import com.mkx.hrttracker.ui.medication.parseMedicationCountText
import com.mkx.hrttracker.ui.medication.resolveMedicationCountTextAfterDraftChange
import com.mkx.hrttracker.ui.medication.resolvedMedicationCountForSave
import com.mkx.hrttracker.ui.medication.sanitizeMedicationCountText
import com.mkx.hrttracker.ui.medication.stepMedicationCount
import com.mkx.hrttracker.ui.medication.toMedicationDetails
import com.mkx.hrttracker.ui.medication.validationErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddEntryViewModel @Inject constructor(
    private val medicationLogRepository: MedicationLogRepository,
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddEntryUiState())
    val uiState: StateFlow<AddEntryUiState> = _uiState.asStateFlow()
    private var loadEntryJob: Job? = null

    fun initialize(entryIds: List<String>) {
        loadEntryJob?.cancel()
        val normalizedEntryIds = normalizeEditingEntryIds(entryIds)
        _uiState.value = AddEntryUiState(
            editingEntryIds = normalizedEntryIds,
            isLoading = normalizedEntryIds.isNotEmpty()
        )

        if (normalizedEntryIds.isNotEmpty()) {
            loadEntriesForEditing(normalizedEntryIds)
        }
    }

    fun initializeQuickLog(
        groupId: UUID,
        scheduleTimeUuid: UUID? = null,
        scheduledFor: LocalDateTime,
        medicationDetails: MedicationDetails,
        medicationCount: Int,
    ) {
        loadEntryJob?.cancel()
        val cachedGroup = medicationGroupRepository.getCachedGroup(groupId)
        _uiState.value = buildQuickLogUiState(
            groupId = groupId,
            group = cachedGroup,
            scheduleTimeUuid = scheduleTimeUuid,
            scheduledFor = scheduledFor,
            medicationDetails = medicationDetails,
            medicationCount = medicationCount,
            appliedAt = LocalDateTime.now(),
            isLoading = cachedGroup == null,
        )

        loadEntryJob = viewModelScope.launch {
            val group = cachedGroup ?: runCatching {
                medicationGroupRepository.getGroup(groupId)
            }.getOrNull()
            if (group == null) {
                _uiState.update { currentState ->
                    if (currentState.sourceGroupUuid == groupId &&
                        currentState.scheduledFor == scheduledFor
                    ) {
                        currentState.copy(isLoading = false, isSaved = true)
                    } else {
                        currentState
                    }
                }
                return@launch
            }

            _uiState.update { currentState ->
                if (currentState.sourceGroupUuid == groupId &&
                    currentState.scheduledFor == scheduledFor
                ) {
                    currentState.copy(
                        sourceGroupName = group.name,
                        sourceGroupColorKey = group.colorKey,
                        sourceGroupPreviousScheduledFor = group.previousScheduledForBefore(scheduledFor),
                        sourceGroupNextScheduledFor = group.nextScheduledForAfter(scheduledFor),
                        isLoading = false,
                    )
                } else {
                    currentState
                }
            }
        }
    }

    fun updateMedicationDraft(
        transform: (MedicationDraftUiState) -> MedicationDraftUiState
    ) {
        _uiState.update { currentState ->
            val updatedDraft = transform(currentState.medicationDraft)
            currentState.copy(
                medicationDraft = updatedDraft,
                countText = resolveMedicationCountTextAfterDraftChange(
                    previousDraft = currentState.medicationDraft,
                    updatedDraft = updatedDraft,
                    currentCountText = currentState.countText
                ),
                errorMessageRes = null,
                isScheduleFulfillmentWarningVisible = false
            )
        }
    }

    fun updateCountText(countText: String) {
        _uiState.update { currentState ->
            currentState.copy(
                countText = sanitizeMedicationCountText(countText),
                errorMessageRes = null,
                isScheduleFulfillmentWarningVisible = false
            )
        }
    }

    fun decreaseCount() {
        _uiState.update { currentState ->
            currentState.copy(
                countText = stepMedicationCount(
                    applicationType = currentState.medicationDraft.applicationType,
                    countText = currentState.countText,
                    delta = -1
                ).toString(),
                errorMessageRes = null,
                isScheduleFulfillmentWarningVisible = false
            )
        }
    }

    fun increaseCount() {
        _uiState.update { currentState ->
            currentState.copy(
                countText = stepMedicationCount(
                    applicationType = currentState.medicationDraft.applicationType,
                    countText = currentState.countText,
                    delta = 1
                ).toString(),
                errorMessageRes = null,
                isScheduleFulfillmentWarningVisible = false
            )
        }
    }

    fun updateAppliedDate(appliedDate: LocalDate) {
        _uiState.update {
            it.copy(
                appliedDate = appliedDate,
                errorMessageRes = null,
                isScheduleFulfillmentWarningVisible = false
            )
        }
    }

    fun updateAppliedTime(appliedTime: LocalTime) {
        _uiState.update {
            it.copy(
                appliedTime = appliedTime.withSecond(0).withNano(0),
                errorMessageRes = null,
                isScheduleFulfillmentWarningVisible = false
            )
        }
    }

    fun saveEntry() {
        saveEntry(skipFulfillmentWarning = false)
    }

    fun saveEntryAfterFulfillmentWarning() {
        saveEntry(skipFulfillmentWarning = true)
    }

    fun dismissScheduleFulfillmentWarning() {
        _uiState.update {
            it.copy(isScheduleFulfillmentWarningVisible = false)
        }
    }

    private fun saveEntry(skipFulfillmentWarning: Boolean) {
        val currentState = _uiState.value
        if (isAddEntryBusy(currentState)) {
            return
        }
        val appliedAtLocal = LocalDateTime.of(
            currentState.appliedDate,
            currentState.appliedTime
        )
        val appliedAt = appliedAtLocal.atZone(currentState.appliedZoneId).toInstant()
        val appliedAtTimeZoneId = currentState.appliedZoneId.id
        val errorRes = currentState.medicationDraft.validationErrorRes()
            ?: medicationCountValidationErrorRes(
                applicationType = currentState.medicationDraft.applicationType,
                countText = currentState.countText
            )

        if (errorRes != null) {
            _uiState.update {
                it.copy(
                    errorMessageRes = errorRes,
                    saveEntryResult = null,
                    isScheduleFulfillmentWarningVisible = false,
                )
            }
            return
        }

        if (!skipFulfillmentWarning && currentState.shouldWarnScheduleWillNotBeFulfilled(appliedAtLocal)) {
            _uiState.update {
                it.copy(
                    errorMessageRes = null,
                    saveEntryResult = null,
                    isScheduleFulfillmentWarningVisible = true,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessageRes = null,
                    saveEntryResult = null,
                    isScheduleFulfillmentWarningVisible = false,
                )
            }

            val editingEntryUuids = currentState.editingEntryIds.map(UUID::fromString)
            val resolvedCount = resolvedMedicationCountForSave(
                applicationType = currentState.medicationDraft.applicationType,
                countText = currentState.countText,
            )
            val medicationDetails = currentState.medicationDraft.toMedicationDetails()
            val saveResult = runCatching {
                if (editingEntryUuids.size > 1) {
                    medicationLogRepository.saveEntries(
                        uuids = editingEntryUuids,
                        medication = medicationDetails,
                        sourceGroupUuid = currentState.sourceGroupUuid,
                        scheduleTimeUuid = currentState.scheduleTimeUuid,
                        appliedAt = appliedAt,
                        scheduledFor = currentState.scheduledFor,
                        count = resolvedCount,
                        appliedAtTimeZoneId = appliedAtTimeZoneId
                    )
                } else {
                    medicationLogRepository.saveEntry(
                        uuid = editingEntryUuids.firstOrNull(),
                        medication = medicationDetails,
                        sourceGroupUuid = currentState.sourceGroupUuid,
                        scheduleTimeUuid = currentState.scheduleTimeUuid,
                        appliedAt = appliedAt,
                        scheduledFor = currentState.scheduledFor,
                        count = resolvedCount,
                        appliedAtTimeZoneId = appliedAtTimeZoneId
                    )
                }
            }.fold(
                onSuccess = { null },
                onFailure = { SaveEntryResult.FAILURE },
            )
            val isSaved = saveResult == null

            _uiState.update {
                it.copy(
                    isSaving = false,
                    isSaved = isSaved,
                    errorMessageRes = null,
                    saveEntryResult = saveResult,
                    isScheduleFulfillmentWarningVisible = false,
                )
            }

            if (isSaved) {
                withContext(NonCancellable) {
                    runCatching { medicationReminderScheduler.rescheduleAll() }
                }
            }
        }
    }

    fun deleteEntry() {
        val currentState = _uiState.value
        if (isAddEntryBusy(currentState)) {
            return
        }
        val editingEntryUuids = currentState.editingEntryIds.map(UUID::fromString)
        if (editingEntryUuids.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDeleting = true,
                    errorMessageRes = null,
                    deleteEntryResult = null,
                )
            }

            val result = runCatching {
                medicationLogRepository.deleteEntries(editingEntryUuids)
            }.fold(
                onSuccess = { null },
                onFailure = { DeleteEntryResult.FAILURE },
            )
            val isDeleted = result == null

            _uiState.update {
                it.copy(
                    isDeleting = false,
                    isSaved = isDeleted,
                    errorMessageRes = null,
                    deleteEntryResult = result,
                )
            }

            if (isDeleted) {
                withContext(NonCancellable) {
                    runCatching { medicationReminderScheduler.rescheduleAll() }
                }
            }
        }
    }

    fun consumeSavedState() {
        _uiState.update { it.copy(isSaved = false) }
    }

    fun consumeSaveEntryResult() {
        _uiState.update { it.copy(saveEntryResult = null) }
    }

    fun consumeDeleteEntryResult() {
        _uiState.update { it.copy(deleteEntryResult = null) }
    }

    private fun loadEntriesForEditing(entryIds: List<String>) {
        loadEntryJob = viewModelScope.launch {
            val entries = medicationLogRepository.getEntries(entryIds.map(UUID::fromString))
            val sourceGroup = entries.firstOrNull()?.sourceGroupUuid?.let { sourceGroupUuid ->
                medicationGroupRepository.getGroup(sourceGroupUuid)
            }
            _uiState.value = buildEditingUiState(
                entries = entries,
                sourceGroup = sourceGroup
            ) ?: AddEntryUiState()
        }
    }

}

data class AddEntryUiState(
    val editingEntryIds: List<String> = emptyList(),
    val medicationDraft: MedicationDraftUiState = defaultMedicationDraft(),
    val sourceGroupUuid: UUID? = null,
    val scheduleTimeUuid: UUID? = null,
    val sourceGroupName: String? = null,
    val sourceGroupColorKey: MedicationGroupColorKey? = null,
    val scheduledFor: LocalDateTime? = null,
    val sourceGroupPreviousScheduledFor: LocalDateTime? = null,
    val sourceGroupNextScheduledFor: LocalDateTime? = null,
    val countText: String = "1",
    val appliedDate: LocalDate = LocalDate.now(),
    val appliedTime: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val appliedZoneId: ZoneId = ZoneId.systemDefault(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessageRes: Int? = null,
    val saveEntryResult: SaveEntryResult? = null,
    val deleteEntryResult: DeleteEntryResult? = null,
    val isScheduleFulfillmentWarningVisible: Boolean = false,
) {
    val count: Int
        get() = parseMedicationCountText(countText)

    val isEditing: Boolean
        get() = editingEntryIds.isNotEmpty()

    val isBulkEditing: Boolean
        get() = editingEntryIds.size > 1

    val canEditMedicationIdentity: Boolean
        get() = sourceGroupUuid == null

    val canDelete: Boolean
        get() = isEditing

    fun shouldWarnScheduleWillNotBeFulfilled(appliedAt: LocalDateTime): Boolean {
        return shouldWarnScheduleWillNotBeFulfilled(
            sourceGroupUuid = sourceGroupUuid,
            scheduledFor = scheduledFor,
            sourceGroupPreviousScheduledFor = sourceGroupPreviousScheduledFor,
            sourceGroupNextScheduledFor = sourceGroupNextScheduledFor,
            appliedAt = appliedAt
        )
    }
}

data class AddEntryQuickLogRequest(
    val groupId: UUID,
    val scheduleTimeUuid: UUID? = null,
    val scheduledFor: LocalDateTime,
    val medicationDetails: MedicationDetails,
    val medicationCount: Int,
)

enum class SaveEntryResult {
    FAILURE,
}

enum class DeleteEntryResult {
    FAILURE,
}

internal fun isAddEntryBusy(uiState: AddEntryUiState): Boolean {
    return uiState.isLoading ||
        uiState.isSaving ||
        uiState.isDeleting ||
        uiState.isSaved
}

internal fun normalizeEditingEntryIds(entryIds: Collection<String>): List<String> {
    return entryIds.mapNotNull { entryId ->
        runCatching { UUID.fromString(entryId).toString() }.getOrNull()
    }.distinct()
}

internal fun buildEditingUiState(
    entries: List<MedicationLogEntry>,
    sourceGroup: MedicationGroup? = null,
): AddEntryUiState? {
    val representativeEntry = entries.firstOrNull() ?: return null
    val editableEntries = if (canBulkEditTogether(entries)) entries else listOf(representativeEntry)
    val appliedAtLocal = appliedAtAsLocalDateTime(representativeEntry)
    val matchingSourceGroup = sourceGroup?.takeIf { group ->
        group.uuid == representativeEntry.sourceGroupUuid
    }

    return AddEntryUiState(
        editingEntryIds = editableEntries.map { entry -> entry.uuid.toString() },
        medicationDraft = medicationDraftFromDetails(representativeEntry.details),
        sourceGroupUuid = representativeEntry.sourceGroupUuid,
        scheduleTimeUuid = representativeEntry.scheduleTimeUuid,
        sourceGroupName = matchingSourceGroup?.name,
        sourceGroupColorKey = matchingSourceGroup?.colorKey,
        scheduledFor = representativeEntry.scheduledFor,
        sourceGroupPreviousScheduledFor = representativeEntry.scheduledFor?.let { scheduledFor ->
            matchingSourceGroup?.previousScheduledForBefore(scheduledFor)
        },
        sourceGroupNextScheduledFor = representativeEntry.scheduledFor?.let { scheduledFor ->
            matchingSourceGroup?.nextScheduledForAfter(scheduledFor)
        },
        countText = normalizeMedicationCount(
            representativeEntry.details.applicationType,
            representativeEntry.count
        ).toString(),
        appliedZoneId = displayZoneOf(representativeEntry),
        appliedDate = appliedAtLocal.toLocalDate(),
        appliedTime = appliedAtLocal.toLocalTime().withSecond(0).withNano(0)
    )
}

internal fun buildQuickLogUiState(
    groupId: UUID,
    group: MedicationGroup?,
    scheduleTimeUuid: UUID? = null,
    scheduledFor: LocalDateTime,
    medicationDetails: MedicationDetails,
    medicationCount: Int,
    appliedAt: LocalDateTime,
    isLoading: Boolean = false,
): AddEntryUiState {
    return AddEntryUiState(
        medicationDraft = medicationDraftFromDetails(medicationDetails),
        sourceGroupUuid = groupId,
        scheduleTimeUuid = scheduleTimeUuid,
        sourceGroupName = group?.name,
        sourceGroupColorKey = group?.colorKey,
        scheduledFor = scheduledFor,
        sourceGroupPreviousScheduledFor = group?.previousScheduledForBefore(scheduledFor),
        sourceGroupNextScheduledFor = group?.nextScheduledForAfter(scheduledFor),
        countText = normalizeMedicationCount(
            medicationDetails.applicationType,
            medicationCount
        ).toString(),
        appliedDate = appliedAt.toLocalDate(),
        appliedTime = appliedAt.toLocalTime().withSecond(0).withNano(0),
        isLoading = isLoading,
    )
}

internal fun canBulkEditTogether(entries: List<MedicationLogEntry>): Boolean {
    if (entries.size <= 1) {
        return true
    }

    val firstEntry = entries.first()
    return entries.all { entry ->
        entry.details == firstEntry.details &&
            entry.sourceGroupUuid == firstEntry.sourceGroupUuid &&
            entry.scheduleTimeUuid == firstEntry.scheduleTimeUuid &&
            entry.appliedAt == firstEntry.appliedAt &&
            entry.scheduledFor == firstEntry.scheduledFor &&
            entry.count == firstEntry.count
    }
}

internal fun shouldWarnScheduleWillNotBeFulfilled(
    sourceGroupUuid: UUID?,
    scheduledFor: LocalDateTime?,
    sourceGroupPreviousScheduledFor: LocalDateTime?,
    sourceGroupNextScheduledFor: LocalDateTime?,
    appliedAt: LocalDateTime,
): Boolean {
    if (sourceGroupUuid == null || scheduledFor == null) {
        return false
    }

    return !isWithinScheduleFulfillmentWindow(
        scheduledFor = scheduledFor,
        appliedAt = appliedAt,
        previousScheduledFor = sourceGroupPreviousScheduledFor,
        nextScheduledFor = sourceGroupNextScheduledFor
    )
}
