package com.mkx.hrttracker.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.isWithinScheduleFulfillmentWindow
import com.mkx.hrttracker.model.medication.nextScheduledForAfter
import com.mkx.hrttracker.model.medication.previousScheduledForBefore
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSlot
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.ui.medication.DoseInstructionDraftUiState
import com.mkx.hrttracker.ui.medication.MedicinePickerUiState
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import com.mkx.hrttracker.ui.medication.doseInstructionDraftFromInstruction
import com.mkx.hrttracker.ui.medication.inferredOrSelectedPreparationType
import com.mkx.hrttracker.ui.medication.medicationCountValidationErrorRes
import com.mkx.hrttracker.ui.medication.medicineDraftFromMedicine
import com.mkx.hrttracker.ui.medication.normalizeMedicationCount
import com.mkx.hrttracker.ui.medication.parseMedicationCountText
import com.mkx.hrttracker.ui.medication.resolveMedicationCountTextAfterDraftChange
import com.mkx.hrttracker.ui.medication.resolvedMedicationCountForSave
import com.mkx.hrttracker.ui.medication.sanitizeMedicationCountText
import com.mkx.hrttracker.ui.medication.selectedMedicineValidationErrorRes
import com.mkx.hrttracker.ui.medication.stepMedicationCount
import com.mkx.hrttracker.ui.medication.toDoseInstruction
import com.mkx.hrttracker.ui.medication.toDoseInstructionDraft
import com.mkx.hrttracker.ui.medication.validationErrorRes
import com.mkx.hrttracker.util.appliedAtAsLocalDateTime
import com.mkx.hrttracker.util.displayZoneOf
import com.mkx.hrttracker.util.zoneDisplayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
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
    private val medicineRepository: MedicineRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddEntryUiState())
    val uiState: StateFlow<AddEntryUiState> = _uiState.asStateFlow()
    private var loadEntryJob: Job? = null
    private var pendingSave: PendingSaveRequest? = null
    private var pendingDelete: PendingDeleteRequest? = null

    init {
        viewModelScope.launch {
            medicineRepository.observeAllActive().collect { medicines ->
                _uiState.update { current -> current.copy(activeMedicines = medicines) }
            }
        }
    }

    fun initialize(
        entryIds: List<String>,
        editSnapshot: AddEntryEditSnapshot? = null,
    ) {
        loadEntryJob?.cancel()
        pendingSave = null
        pendingDelete = null
        val normalizedEntryIds = normalizeEditingEntryIds(entryIds)
        val normalizedEntryUuids = normalizedEntryIds.map(UUID::fromString)
        val matchingEditSnapshot = editSnapshot?.matchingEntries(normalizedEntryUuids)
        val initialEditingState = matchingEditSnapshot?.toEditingUiState()
        _uiState.value = initialEditingState
            ?.copy(isLoading = normalizedEntryIds.isNotEmpty())
            ?: AddEntryUiState(
                editingEntryIds = normalizedEntryIds,
                isLoading = normalizedEntryIds.isNotEmpty()
            )

        if (normalizedEntryIds.isNotEmpty()) {
            loadEntriesForEditing(
                entryIds = normalizedEntryIds,
                editSnapshot = matchingEditSnapshot,
            )
        }
    }

    fun initializeQuickLog(
        groupId: UUID,
        scheduleTimeUuid: UUID? = null,
        scheduledFor: LocalDateTime,
        medicine: Medicine?,
        applicationType: MedicationApplicationType,
        doseInstruction: DoseInstruction,
        medicationCount: Int,
        sourceGroupName: String? = null,
        sourceGroupColorKey: MedicationGroupColorKey? = null,
        sourceGroupPreviousScheduledFor: LocalDateTime? = null,
        sourceGroupNextScheduledFor: LocalDateTime? = null,
    ) {
        loadEntryJob?.cancel()
        pendingSave = null
        pendingDelete = null
        val cachedGroup = medicationGroupRepository.getCachedGroup(groupId)
        val hasSourceGroupSnapshot = !sourceGroupName.isNullOrBlank()
        _uiState.value = buildQuickLogUiState(
            groupId = groupId,
            group = cachedGroup,
            scheduleTimeUuid = scheduleTimeUuid,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = applicationType,
            doseInstruction = doseInstruction,
            medicationCount = medicationCount,
            sourceGroupName = sourceGroupName,
            sourceGroupColorKey = sourceGroupColorKey,
            sourceGroupPreviousScheduledFor = sourceGroupPreviousScheduledFor,
            sourceGroupNextScheduledFor = sourceGroupNextScheduledFor,
            appliedAt = LocalDateTime.now(),
            isLoading = cachedGroup == null && !hasSourceGroupSnapshot,
        )

        loadEntryJob = viewModelScope.launch {
            val group = cachedGroup ?: runCatching {
                medicationGroupRepository.getGroup(groupId)
            }.getOrNull()
            val drained = pendingSave
            if (drained != null) {
                pendingSave = null
                _uiState.update { it.copy(isLoading = false) }
                performSave(drained)
                return@launch
            }
            if (group == null) {
                _uiState.update { currentState ->
                    if (currentState.sourceGroupUuid == groupId &&
                        currentState.scheduledFor == scheduledFor
                    ) {
                        if (hasSourceGroupSnapshot) {
                            currentState.copy(isLoading = false)
                        } else {
                            currentState.copy(isLoading = false, isSaved = true)
                        }
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

    fun updateMedicineDraft(
        transform: (MedicinePickerUiState) -> MedicinePickerUiState
    ) {
        _uiState.update { currentState ->
            val updatedDraft = transform(currentState.medicineDraft)
            currentState.copy(
                medicineDraft = updatedDraft,
                resolvedMedicine = if (updatedDraft.selectedMedicineUuid == null) {
                    null
                } else {
                    currentState.resolvedMedicine
                },
                doseInstructionDraft = currentState.doseInstructionDraft.copy(
                    applicationType = updatedDraft.applicationType,
                    preparationType = updatedDraft.inferredOrSelectedPreparationType()
                        ?: currentState.doseInstructionDraft.preparationType,
                ),
                countText = resolveMedicationCountTextAfterDraftChange(
                    previousDraft = currentState.medicineDraft,
                    updatedDraft = updatedDraft,
                    currentCountText = currentState.countText
                ),
                errorMessageRes = null,
                isScheduleFulfillmentWarningVisible = false
            )
        }
    }

    fun updateDoseInstructionDraft(
        transform: (DoseInstructionDraftUiState) -> DoseInstructionDraftUiState
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                doseInstructionDraft = transform(currentState.doseInstructionDraft),
                errorMessageRes = null,
                isScheduleFulfillmentWarningVisible = false,
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
                    applicationType = currentState.medicineDraft.applicationType,
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
                    applicationType = currentState.medicineDraft.applicationType,
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
        if (currentState.isSaving || currentState.isDeleting || currentState.isSaved) {
            return
        }
        val appliedAtLocal = LocalDateTime.of(
            currentState.appliedDate,
            currentState.appliedTime
        )
        val appliedAt = appliedAtLocal.atZone(currentState.appliedZoneId).toInstant()
        val appliedAtTimeZoneId = currentState.appliedZoneId.id
        val errorRes = currentState.medicineDraft.selectedMedicineValidationErrorRes()
            ?: currentState.doseInstructionDraft.validationErrorRes()
            ?: medicationCountValidationErrorRes(
                applicationType = currentState.medicineDraft.applicationType,
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

        val isPatchOff = currentState.medicineDraft.applicationType ==
            MedicationApplicationType.PATCH_OFF
        val request = PendingSaveRequest(
            editingEntryUuids = currentState.editingEntryIds.map(UUID::fromString),
            // Identity is locked on edit; carry the locked medicine straight through.
            // On a new log, resolve the picker draft into a Medicine at save time.
            lockedMedicineUuid = if (currentState.isEditing) {
                currentState.resolvedMedicine?.uuid
            } else {
                null
            },
            selectedMedicineUuid = currentState.resolvedMedicine?.uuid
                ?: currentState.medicineDraft.selectedMedicineUuid,
            medicineDraft = currentState.medicineDraft,
            applicationType = currentState.medicineDraft.applicationType,
            doseInstruction = if (isPatchOff) {
                DoseInstruction.Noop
            } else {
                currentState.doseInstructionDraft.toDoseInstruction()
            },
            isEditing = currentState.isEditing,
            sourceGroupUuid = currentState.sourceGroupUuid,
            scheduleTimeUuid = currentState.scheduleTimeUuid,
            appliedAt = appliedAt,
            appliedAtTimeZoneId = appliedAtTimeZoneId,
            appliedZoneId = currentState.appliedZoneId,
            scheduledFor = currentState.scheduledFor,
            count = resolvedMedicationCountForSave(
                applicationType = currentState.medicineDraft.applicationType,
                countText = currentState.countText,
            ),
        )

        _uiState.update {
            it.copy(
                isSaving = true,
                errorMessageRes = null,
                saveEntryResult = null,
                isScheduleFulfillmentWarningVisible = false,
            )
        }

        if (currentState.isLoading) {
            // Defer the repo write until the initial load drains it. Lets the user
            // tap save while the sheet is still backed by snapshot data.
            pendingSave = request
            return
        }

        performSave(request)
    }

    private fun performSave(request: PendingSaveRequest) {
        viewModelScope.launch {
            val saveResult = runCatching {
                // On edit the repository ignores medicine identity (locked); on a
                // new log the routed picker already resolved a Medicine UUID.
                val medicineUuid = if (request.isEditing) {
                    request.lockedMedicineUuid
                } else {
                    request.selectedMedicineUuid
                }
                if (request.editingEntryUuids.size > 1) {
                    medicationLogRepository.saveEntries(
                        uuids = request.editingEntryUuids,
                        medicineUuid = medicineUuid,
                        applicationType = request.applicationType,
                        doseInstruction = request.doseInstruction,
                        sourceGroupUuid = request.sourceGroupUuid,
                        scheduleTimeUuid = request.scheduleTimeUuid,
                        appliedAt = request.appliedAt,
                        scheduledFor = request.scheduledFor,
                        count = request.count,
                        appliedAtTimeZoneId = request.appliedAtTimeZoneId,
                    )
                } else {
                    medicationLogRepository.saveEntry(
                        uuid = request.editingEntryUuids.firstOrNull(),
                        medicineUuid = medicineUuid,
                        applicationType = request.applicationType,
                        doseInstruction = request.doseInstruction,
                        sourceGroupUuid = request.sourceGroupUuid,
                        scheduleTimeUuid = request.scheduleTimeUuid,
                        appliedAt = request.appliedAt,
                        scheduledFor = request.scheduledFor,
                        count = request.count,
                        appliedAtTimeZoneId = request.appliedAtTimeZoneId,
                    )
                }
            }.fold(
                onSuccess = { null },
                onFailure = { SaveEntryResult.FAILURE },
            )
            val isSaved = saveResult == null
            val crossZoneText = if (isSaved) {
                val pickerOffset = request.appliedZoneId.rules.getOffset(request.appliedAt)
                val deviceOffset = ZoneId.systemDefault().rules.getOffset(request.appliedAt)
                if (pickerOffset == deviceOffset) {
                    null
                } else {
                    zoneDisplayName(request.appliedZoneId)
                }
            } else {
                null
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isSaving = false,
                    isSaved = isSaved,
                    errorMessageRes = null,
                    saveEntryResult = saveResult,
                    isScheduleFulfillmentWarningVisible = false,
                    savedCrossZoneZoneText = crossZoneText,
                )
            }

            if (isSaved) {
                withContext(NonCancellable) {
                    // Mirror the notification action-handler path: when a manual log
                    // satisfies a planned slot, clear the matching snooze so we don't
                    // wake the device for a record the user has already filed.
                    val groupUuid = request.sourceGroupUuid
                    val scheduledFor = request.scheduledFor
                    if (groupUuid != null && scheduledFor != null) {
                        runCatching {
                            medicationReminderSnoozeScheduler.clearSnoozesForSlots(
                                listOf(
                                    MedicationReminderSlot(
                                        groupUuid = groupUuid,
                                        scheduledAt = scheduledFor,
                                        scheduleTimeUuid = request.scheduleTimeUuid,
                                    )
                                )
                            )
                        }
                    }
                    runCatching { medicationReminderScheduler.rescheduleAll() }
                }
            }
        }
    }

    fun deleteEntry() {
        val currentState = _uiState.value
        if (currentState.isSaving || currentState.isDeleting || currentState.isSaved) {
            return
        }
        val editingEntryUuids = currentState.editingEntryIds.map(UUID::fromString)
        if (editingEntryUuids.isEmpty()) {
            return
        }

        val request = PendingDeleteRequest(editingEntryUuids = editingEntryUuids)

        _uiState.update {
            it.copy(
                isDeleting = true,
                errorMessageRes = null,
                deleteEntryResult = null,
            )
        }

        if (currentState.isLoading) {
            pendingDelete = request
            return
        }

        performDelete(request)
    }

    private fun performDelete(request: PendingDeleteRequest) {
        viewModelScope.launch {
            // Capture each entry's slot identity before delete so we can mirror the
            // notification-action-handler path: when the entry being removed was
            // fulfilling a snoozed slot, clear that snooze. After deleteEntries
            // succeeds the rows are gone and we can no longer derive the slots.
            val slotsToClear = runCatching {
                medicationLogRepository.getEntries(request.editingEntryUuids)
            }.getOrDefault(emptyList())
                .mapNotNull { entry ->
                    val groupUuid = entry.sourceGroupUuid
                    val scheduledFor = entry.scheduledFor
                    if (groupUuid != null && scheduledFor != null) {
                        MedicationReminderSlot(
                            groupUuid = groupUuid,
                            scheduledAt = scheduledFor,
                            scheduleTimeUuid = entry.scheduleTimeUuid,
                        )
                    } else {
                        null
                    }
                }

            val result = runCatching {
                medicationLogRepository.deleteEntries(request.editingEntryUuids)
            }.fold(
                onSuccess = { null },
                onFailure = { DeleteEntryResult.FAILURE },
            )
            val isDeleted = result == null

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isDeleting = false,
                    isSaved = isDeleted,
                    errorMessageRes = null,
                    deleteEntryResult = result,
                )
            }

            if (isDeleted) {
                withContext(NonCancellable) {
                    if (slotsToClear.isNotEmpty()) {
                        runCatching {
                            medicationReminderSnoozeScheduler.clearSnoozesForSlots(slotsToClear)
                        }
                    }
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

    fun consumeCrossZoneToast() {
        _uiState.update { it.copy(savedCrossZoneZoneText = null) }
    }

    private fun loadEntriesForEditing(
        entryIds: List<String>,
        editSnapshot: AddEntryEditSnapshot? = null,
    ) {
        loadEntryJob = viewModelScope.launch {
            val entries = medicationLogRepository.getEntries(entryIds.map(UUID::fromString))
            val sourceGroup = entries.firstOrNull()?.sourceGroupUuid?.let { sourceGroupUuid ->
                medicationGroupRepository.getGroup(sourceGroupUuid)
            }
            val drainedSave = pendingSave
            if (drainedSave != null) {
                pendingSave = null
                if (entries.isEmpty()) {
                    // Edit target is gone from Room — the user asked to edit, not create.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSaving = false,
                            saveEntryResult = SaveEntryResult.FAILURE,
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(isLoading = false) }
                performSave(drainedSave)
                return@launch
            }
            val drainedDelete = pendingDelete
            if (drainedDelete != null) {
                pendingDelete = null
                if (entries.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isDeleting = false,
                            deleteEntryResult = DeleteEntryResult.FAILURE,
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(isLoading = false) }
                performDelete(drainedDelete)
                return@launch
            }
            _uiState.value = buildEditingUiState(
                entries = entries,
                sourceGroup = sourceGroup,
                sourceGroupName = editSnapshot?.sourceGroupName,
                sourceGroupColorKey = editSnapshot?.sourceGroupColorKey,
                sourceGroupPreviousScheduledFor = editSnapshot?.sourceGroupPreviousScheduledFor,
                sourceGroupNextScheduledFor = editSnapshot?.sourceGroupNextScheduledFor,
            ) ?: editSnapshot
                ?.toEditingUiState()
                ?.copy(isLoading = false)
                ?: AddEntryUiState()
        }
    }

}

data class AddEntryUiState(
    val editingEntryIds: List<String> = emptyList(),
    val medicineDraft: MedicinePickerUiState = defaultMedicineDraft(),
    val doseInstructionDraft: DoseInstructionDraftUiState =
        defaultMedicineDraft().toDoseInstructionDraft(),
    val resolvedMedicine: Medicine? = null,
    val activeMedicines: List<Medicine> = emptyList(),
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
    val savedCrossZoneZoneText: String? = null,
) {
    val count: Int
        get() = parseMedicationCountText(countText)

    val isEditing: Boolean
        get() = editingEntryIds.isNotEmpty()

    val isBulkEditing: Boolean
        get() = editingEntryIds.size > 1

    // Identity is editable only for fresh, free-form manual entries:
    // - Editing an existing entry (any number) preserves its medicine identity.
    // - Quick-log entries are bound to a group slot's medicine; users change
    //   what's in the slot via the group editor, not the log editor.
    val canEditMedicationIdentity: Boolean
        get() = !isEditing && sourceGroupUuid == null

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
    // A quick-logged PATCH_OFF slot has no medicine — see the PATCH_OFF rule.
    val medicine: Medicine?,
    val applicationType: MedicationApplicationType,
    val doseInstruction: DoseInstruction,
    val medicationCount: Int,
    val sourceGroupName: String? = null,
    val sourceGroupColorKey: MedicationGroupColorKey? = null,
    val sourceGroupPreviousScheduledFor: LocalDateTime? = null,
    val sourceGroupNextScheduledFor: LocalDateTime? = null,
)

data class AddEntryEditSnapshot(
    val entries: List<MedicationLogEntry>,
    val sourceGroupName: String? = null,
    val sourceGroupColorKey: MedicationGroupColorKey? = null,
    val sourceGroupPreviousScheduledFor: LocalDateTime? = null,
    val sourceGroupNextScheduledFor: LocalDateTime? = null,
)

private data class PendingSaveRequest(
    val editingEntryUuids: List<UUID>,
    val lockedMedicineUuid: UUID?,
    val selectedMedicineUuid: UUID?,
    val medicineDraft: MedicinePickerUiState,
    val applicationType: MedicationApplicationType,
    val doseInstruction: DoseInstruction,
    val isEditing: Boolean,
    val sourceGroupUuid: UUID?,
    val scheduleTimeUuid: UUID?,
    val appliedAt: Instant,
    val appliedAtTimeZoneId: String,
    val appliedZoneId: ZoneId,
    val scheduledFor: LocalDateTime?,
    val count: Int,
)

private data class PendingDeleteRequest(
    val editingEntryUuids: List<UUID>,
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
    sourceGroupName: String? = null,
    sourceGroupColorKey: MedicationGroupColorKey? = null,
    sourceGroupPreviousScheduledFor: LocalDateTime? = null,
    sourceGroupNextScheduledFor: LocalDateTime? = null,
): AddEntryUiState? {
    val representativeEntry = entries.firstOrNull() ?: return null
    val editableEntries = if (canBulkEditTogether(entries)) entries else listOf(representativeEntry)
    val appliedAtLocal = appliedAtAsLocalDateTime(representativeEntry)
    val matchingSourceGroup = sourceGroup?.takeIf { group ->
        group.uuid == representativeEntry.sourceGroupUuid
    }
    val hasMatchingSourceGroupSnapshot = representativeEntry.sourceGroupUuid != null &&
        !sourceGroupName.isNullOrBlank()

    return AddEntryUiState(
        editingEntryIds = editableEntries.map { entry -> entry.uuid.toString() },
        medicineDraft = medicineDraftForEntry(representativeEntry),
        doseInstructionDraft = doseInstructionDraftForEntry(representativeEntry),
        resolvedMedicine = representativeEntry.medicine,
        sourceGroupUuid = representativeEntry.sourceGroupUuid,
        scheduleTimeUuid = representativeEntry.scheduleTimeUuid,
        sourceGroupName = matchingSourceGroup?.name
            ?: sourceGroupName?.takeIf { hasMatchingSourceGroupSnapshot },
        sourceGroupColorKey = matchingSourceGroup?.colorKey
            ?: sourceGroupColorKey?.takeIf { hasMatchingSourceGroupSnapshot },
        scheduledFor = representativeEntry.scheduledFor,
        sourceGroupPreviousScheduledFor = representativeEntry.scheduledFor?.let { scheduledFor ->
            matchingSourceGroup?.previousScheduledForBefore(scheduledFor)
                ?: sourceGroupPreviousScheduledFor?.takeIf { hasMatchingSourceGroupSnapshot }
        },
        sourceGroupNextScheduledFor = representativeEntry.scheduledFor?.let { scheduledFor ->
            matchingSourceGroup?.nextScheduledForAfter(scheduledFor)
                ?: sourceGroupNextScheduledFor?.takeIf { hasMatchingSourceGroupSnapshot }
        },
        countText = normalizeMedicationCount(
            representativeEntry.applicationType,
            representativeEntry.count
        ).toString(),
        appliedZoneId = displayZoneOf(representativeEntry),
        appliedDate = appliedAtLocal.toLocalDate(),
        appliedTime = appliedAtLocal.toLocalTime().withSecond(0).withNano(0)
    )
}

// Builds a picker draft from a logged entry. A PATCH_OFF log has no medicine;
// its draft keeps the PATCH_OFF route and resolves no medicine.
internal fun medicineDraftForEntry(entry: MedicationLogEntry): MedicinePickerUiState {
    val medicine = entry.medicine
    return if (medicine != null) {
        medicineDraftFromMedicine(medicine, entry.applicationType)
    } else {
        defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
        )
    }
}

internal fun doseInstructionDraftForEntry(entry: MedicationLogEntry): DoseInstructionDraftUiState {
    return doseInstructionDraftFromInstruction(
        applicationType = entry.applicationType,
        preparationType = entry.medicine?.preparation?.type
            ?: com.mkx.hrttracker.model.medication.MedicinePreparationType.PILL,
        doseInstruction = entry.doseInstruction,
    )
}

private fun medicineDraftForQuickLog(
    medicine: Medicine?,
    applicationType: MedicationApplicationType,
): MedicinePickerUiState {
    return if (medicine != null) {
        medicineDraftFromMedicine(medicine, applicationType)
    } else {
        defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
        )
    }
}

private fun AddEntryEditSnapshot.matchingEntries(entryUuids: List<UUID>): AddEntryEditSnapshot? {
    if (entryUuids.isEmpty()) {
        return null
    }

    val entriesByUuid = entries.associateBy(MedicationLogEntry::uuid)
    val matchingEntries = entryUuids.mapNotNull(entriesByUuid::get)
    return takeIf { matchingEntries.isNotEmpty() }?.copy(entries = matchingEntries)
}

private fun AddEntryEditSnapshot.toEditingUiState(): AddEntryUiState? {
    return buildEditingUiState(
        entries = entries,
        sourceGroupName = sourceGroupName,
        sourceGroupColorKey = sourceGroupColorKey,
        sourceGroupPreviousScheduledFor = sourceGroupPreviousScheduledFor,
        sourceGroupNextScheduledFor = sourceGroupNextScheduledFor,
    )
}

internal fun buildQuickLogUiState(
    groupId: UUID,
    group: MedicationGroup?,
    scheduleTimeUuid: UUID? = null,
    scheduledFor: LocalDateTime,
    medicine: Medicine?,
    applicationType: MedicationApplicationType,
    doseInstruction: DoseInstruction,
    medicationCount: Int,
    sourceGroupName: String? = null,
    sourceGroupColorKey: MedicationGroupColorKey? = null,
    sourceGroupPreviousScheduledFor: LocalDateTime? = null,
    sourceGroupNextScheduledFor: LocalDateTime? = null,
    appliedAt: LocalDateTime,
    isLoading: Boolean = false,
): AddEntryUiState {
    return AddEntryUiState(
        medicineDraft = medicineDraftForQuickLog(medicine, applicationType),
        doseInstructionDraft = doseInstructionDraftFromInstruction(
            applicationType = applicationType,
            preparationType = medicine?.preparation?.type
                ?: com.mkx.hrttracker.model.medication.MedicinePreparationType.PILL,
            doseInstruction = doseInstruction,
        ),
        resolvedMedicine = medicine,
        sourceGroupUuid = groupId,
        scheduleTimeUuid = scheduleTimeUuid,
        sourceGroupName = group?.name ?: sourceGroupName,
        sourceGroupColorKey = group?.colorKey ?: sourceGroupColorKey,
        scheduledFor = scheduledFor,
        sourceGroupPreviousScheduledFor = group?.previousScheduledForBefore(scheduledFor)
            ?: sourceGroupPreviousScheduledFor,
        sourceGroupNextScheduledFor = group?.nextScheduledForAfter(scheduledFor)
            ?: sourceGroupNextScheduledFor,
        countText = normalizeMedicationCount(applicationType, medicationCount).toString(),
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
        entry.medicineUuid == firstEntry.medicineUuid &&
            entry.applicationType == firstEntry.applicationType &&
            entry.doseInstruction == firstEntry.doseInstruction &&
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
