package com.mkx.hrttracker.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.FulfilledScheduledSlot
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.model.medication.isCompatibleWith
import com.mkx.hrttracker.model.medication.isWithinScheduleFulfillmentWindow
import com.mkx.hrttracker.model.medication.nextScheduledForAfter
import com.mkx.hrttracker.model.medication.previousScheduledForBefore
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.PostLogStockWarning
import com.mkx.hrttracker.reminder.captureStockStatesForLog
import com.mkx.hrttracker.reminder.resolvePostLogStockWarning
import com.mkx.hrttracker.ui.medication.DoseInstructionDraftUiState
import com.mkx.hrttracker.ui.medication.MedicationDoseDraft
import com.mkx.hrttracker.ui.medication.MedicinePickerUiState
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import com.mkx.hrttracker.ui.medication.doseAmountDeltaForActual
import com.mkx.hrttracker.ui.medication.doseInstructionDraftFromInstruction
import com.mkx.hrttracker.ui.medication.effectiveActualDoseAmount
import com.mkx.hrttracker.ui.medication.medicineDraftFromMedicine
import com.mkx.hrttracker.ui.medication.normalizeMedicationCount
import com.mkx.hrttracker.ui.medication.parseMedicationCountText
import com.mkx.hrttracker.ui.medication.resolvedApplicationTypeForDose
import com.mkx.hrttracker.ui.medication.resolvedMedicationCountForSave
import com.mkx.hrttracker.ui.medication.selectedMedicineValidationErrorRes
import com.mkx.hrttracker.ui.medication.toDoseInstruction
import com.mkx.hrttracker.ui.medication.toDoseInstructionDraft
import com.mkx.hrttracker.ui.medication.validatedWith
import com.mkx.hrttracker.util.appliedAtAsLocalDateTime
import com.mkx.hrttracker.util.displayZoneOf
import com.mkx.hrttracker.util.zoneDisplayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
class MedicationLogEntryViewModel @Inject constructor(
    private val medicationLogRepository: MedicationLogRepository,
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicineStockRepository: MedicineStockRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MedicationLogEntryUiState())
    val uiState: StateFlow<MedicationLogEntryUiState> = _uiState.asStateFlow()
    private var loadEntryJob: Job? = null
    private var pendingSave: PendingSaveRequest? = null
    private var pendingDelete: PendingDeleteRequest? = null
    private var stockProjections: List<MedicineStockProjection> =
        medicineStockRepository.getCachedProjections().orEmpty()

    init {
        viewModelScope.launch {
            medicineStockRepository.observeProjections().collect { projections ->
                stockProjections = projections
                _uiState.update { current ->
                    if (current.isSaving || current.isDeleting || current.isSaved) {
                        current
                    } else {
                        current.withSelectedStockProjection()
                    }
                }
            }
        }
    }

    fun previewState(
        medicineUuid: UUID,
        hypotheticalStock: MedicineStock,
        fulfilledSlot: FulfilledScheduledSlot? = null,
    ): MedicineStockState? =
        medicineStockRepository.previewState(medicineUuid, hypotheticalStock, fulfilledSlot)

    fun initialize(
        entryIds: List<String>,
        editSnapshot: MedicationLogEntryEditSnapshot? = null,
    ) {
        loadEntryJob?.cancel()
        pendingSave = null
        pendingDelete = null
        val normalizedEntryIds = normalizeEditingEntryIds(entryIds)
        val normalizedEntryUuids = normalizedEntryIds.map(UUID::fromString)
        val matchingEditSnapshot = editSnapshot?.matchingEntries(normalizedEntryUuids)
        val initialEditingState = matchingEditSnapshot?.toEditingUiState()
        val initialState = initialEditingState
            ?.copy(isLoading = normalizedEntryIds.isNotEmpty())
            ?: MedicationLogEntryUiState(
                editingEntryIds = normalizedEntryIds,
                isLoading = normalizedEntryIds.isNotEmpty()
            )
        _uiState.value = initialState.withSelectedStockProjection()

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
        sourceGroupIsArchived: Boolean = false,
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
            sourceGroupIsArchived = sourceGroupIsArchived,
            sourceGroupPreviousScheduledFor = sourceGroupPreviousScheduledFor,
            sourceGroupNextScheduledFor = sourceGroupNextScheduledFor,
            appliedAt = LocalDateTime.now(),
            isLoading = cachedGroup == null && !hasSourceGroupSnapshot,
        ).withSelectedStockProjection()

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
                        sourceGroupIsArchived = group.isArchived(),
                        sourceGroupPreviousScheduledFor = group.previousScheduledForBefore(
                            scheduledFor
                        ),
                        sourceGroupNextScheduledFor = group.nextScheduledForAfter(scheduledFor),
                        isLoading = false,
                    )
                } else {
                    currentState
                }
            }
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

    fun setDoseAmountDelta(delta: Double?) {
        _uiState.update { currentState ->
            val scheduledAmount = currentState.scheduledNativeAmount ?: return@update currentState
            if (!currentState.allowsActualDoseDelta) {
                return@update currentState
            }
            currentState.copy(
                doseAmountDelta = doseAmountDeltaForActual(
                    scheduledAmount = scheduledAmount,
                    actualAmount = scheduledAmount + (delta ?: 0.0),
                ),
            )
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
        val applicationType = resolvedApplicationType(currentState)
        val preparationType = resolvedPreparationType(currentState)
        val errorRes = MedicationDoseDraft(
            medicineDraft = currentState.medicineDraft,
            doseInstructionDraft = currentState.doseInstructionDraft,
            countText = currentState.countText,
            resolvedMedicine = currentState.resolvedMedicine,
        ).validatedWith(
            preparationType = preparationType,
            validateMedicineDraft = { it.selectedMedicineValidationErrorRes() },
        ).errorMessageRes

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

        if (!skipFulfillmentWarning && currentState.shouldWarnScheduleWillNotBeFulfilled(
                appliedAtLocal
            )
        ) {
            _uiState.update {
                it.copy(
                    errorMessageRes = null,
                    saveEntryResult = null,
                    isScheduleFulfillmentWarningVisible = true,
                )
            }
            return
        }

        val isPatchOff = applicationType == MedicationApplicationType.PATCH_OFF
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
            applicationType = applicationType,
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
                applicationType = applicationType,
                countText = currentState.countText,
                preparationType = preparationType,
            ),
            doseAmountDelta = currentState.doseAmountDelta
                ?.takeIf { currentState.allowsActualDoseDelta },
        )

        _uiState.update {
            it.copy(
                isSaving = true,
                errorMessageRes = null,
                saveEntryResult = null,
                postLogStockWarning = null,
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
            val medicineUuid = if (request.isEditing) {
                request.lockedMedicineUuid
            } else {
                request.selectedMedicineUuid
            }
            // Snapshot stock before the deduction so a new-insert warning only
            // fires when this log worsens the medicine's tier (edits never warn).
            val beforeStockStates =
                if (request.editingEntryUuids.isEmpty() && medicineUuid != null) {
                    captureStockStatesForLog(
                        medicineStockRepository = medicineStockRepository,
                        now = Instant.now(),
                    )
                } else {
                    emptyMap()
                }
            val saveResult = runCatching {
                // On edit the repository ignores medicine identity (locked); on a
                // new log the routed picker already resolved a Medicine UUID.
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
                        doseAmountDelta = request.doseAmountDelta
                            ?.takeIf { !request.isEditing && request.editingEntryUuids.isEmpty() },
                    )
                }
            }.fold(
                onSuccess = { null },
                onFailure = { SaveEntryResult.FAILURE },
            )
            val isSaved = saveResult == null
            val isNewInsert = request.editingEntryUuids.isEmpty()
            val postLogStockWarning = if (isSaved && isNewInsert && medicineUuid != null) {
                runCatching {
                    resolvePostLogStockWarning(
                        projections = medicineStockRepository.projectAllOnce(now = Instant.now()),
                        affectedMedicineUuids = setOf(medicineUuid),
                        beforeStatesByUuid = beforeStockStates,
                    )
                }.getOrElse { failure ->
                    if (failure is CancellationException) throw failure
                    null
                }
            } else {
                null
            }
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
                val updated = it.copy(
                    isLoading = false,
                    isSaving = false,
                    isSaved = isSaved,
                    errorMessageRes = null,
                    saveEntryResult = saveResult,
                    isScheduleFulfillmentWarningVisible = false,
                    savedCrossZoneZoneText = crossZoneText,
                    postLogStockWarning = postLogStockWarning,
                )
                if (isSaved) updated else updated.withSelectedStockProjection()
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

    fun consumePostLogStockWarning() {
        _uiState.update { it.copy(postLogStockWarning = null) }
    }

    private fun resolvedApplicationType(state: MedicationLogEntryUiState): MedicationApplicationType {
        return resolvedApplicationTypeForDose(
            preparationType = resolvedPreparationType(state),
            doseInstructionDraft = state.doseInstructionDraft,
        )
    }

    private fun resolvedPreparationType(state: MedicationLogEntryUiState): MedicinePreparationType {
        return state.resolvedMedicine?.preparation?.type
            ?: state.doseInstructionDraft.preparationType
    }

    private fun loadEntriesForEditing(
        entryIds: List<String>,
        editSnapshot: MedicationLogEntryEditSnapshot? = null,
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
                sourceGroupIsArchived = editSnapshot?.sourceGroupIsArchived ?: false,
                sourceGroupPreviousScheduledFor = editSnapshot?.sourceGroupPreviousScheduledFor,
                sourceGroupNextScheduledFor = editSnapshot?.sourceGroupNextScheduledFor,
            ) ?: editSnapshot
                ?.toEditingUiState()
                ?.copy(isLoading = false)
                    ?: MedicationLogEntryUiState()
            _uiState.value = _uiState.value.withSelectedStockProjection()
        }
    }

    private fun MedicationLogEntryUiState.withSelectedStockProjection(): MedicationLogEntryUiState {
        val selectedMedicineUuid = selectedMedicineUuidForStock()
        return copy(
            selectedStockProjection = stockProjections.firstOrNull { projection ->
                projection.medicine.uuid == selectedMedicineUuid
            },
        )
    }

    private fun MedicationLogEntryUiState.selectedMedicineUuidForStock(): UUID? {
        return when {
            isEditing -> resolvedMedicine?.uuid
            sourceGroupUuid != null -> resolvedMedicine?.uuid
            else -> medicineDraft.selectedMedicineUuid ?: resolvedMedicine?.uuid
        }
    }

}

data class MedicationLogEntryUiState(
    val editingEntryIds: List<String> = emptyList(),
    val medicineDraft: MedicinePickerUiState = defaultMedicineDraft(),
    val doseInstructionDraft: DoseInstructionDraftUiState =
        defaultMedicineDraft().toDoseInstructionDraft(),
    val resolvedMedicine: Medicine? = null,
    val selectedStockProjection: MedicineStockProjection? = null,
    val doseAmountDelta: Double? = null,
    val sourceGroupUuid: UUID? = null,
    val scheduleTimeUuid: UUID? = null,
    val sourceGroupName: String? = null,
    val sourceGroupColorKey: MedicationGroupColorKey? = null,
    val sourceGroupIsArchived: Boolean = false,
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
    val postLogStockWarning: PostLogStockWarning? = null,
) {
    val count: Int
        get() = parseMedicationCountText(countText)

    val isEditing: Boolean
        get() = editingEntryIds.isNotEmpty()

    val isBulkEditing: Boolean
        get() = editingEntryIds.size > 1

    val canDelete: Boolean
        get() = isEditing

    private val isActualDoseDeltaCapableForm: Boolean
        get() = isActualDoseDeltaForm(
            preparationType = resolvedMedicine?.preparation?.type
                ?: doseInstructionDraft.preparationType,
            applicationType = resolvedApplicationTypeForDose(
                preparationType = resolvedMedicine?.preparation?.type
                    ?: doseInstructionDraft.preparationType,
                doseInstructionDraft = doseInstructionDraft,
            ),
        )

    // New logs of measured forms get the interactive stepper.
    val allowsActualDoseDelta: Boolean
        get() = !isEditing &&
                isActualDoseDeltaCapableForm &&
                scheduledNativeAmount != null

    // Reopening a logged measured dose that was adjusted shows the planned
    // amount and its delta read-only (the delta is frozen at log time and is
    // not re-editable). Only single-entry edits carry a delta into state.
    val showActualDoseDeltaReadOnly: Boolean
        get() = isEditing &&
                (doseAmountDelta ?: 0.0) != 0.0 &&
                isActualDoseDeltaCapableForm &&
                scheduledNativeAmount != null

    val effectiveActualAmount: Double?
        get() {
            val scheduledAmount = scheduledNativeAmount ?: return null
            return effectiveActualDoseAmount(
                scheduledAmount = scheduledAmount,
                doseAmountDelta = doseAmountDelta,
            )
        }

    internal val scheduledNativeAmount: Double?
        get() {
            val instruction = runCatching { doseInstructionDraft.toDoseInstruction() }.getOrNull()
                ?: return null
            return when (val preparation = resolvedMedicine?.preparation) {
                is MedicinePreparation.InjectionSingleUseVial ->
                    if (instruction == DoseInstruction.WholeUnit) {
                        preparation.strengthMgPerVial
                    } else {
                        null
                    }

                is MedicinePreparation.InjectionMultiUseVial ->
                    (instruction as? DoseInstruction.VolumeMl)?.valueMl

                is MedicinePreparation.GelContainer ->
                    (instruction as? DoseInstruction.WeightGrams)?.valueGrams

                else -> null
            }
        }

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

data class MedicationLogEntryQuickLogRequest(
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
    val sourceGroupIsArchived: Boolean = false,
    val sourceGroupPreviousScheduledFor: LocalDateTime? = null,
    val sourceGroupNextScheduledFor: LocalDateTime? = null,
)

data class MedicationLogEntryEditSnapshot(
    val entries: List<MedicationLogEntry>,
    val sourceGroupName: String? = null,
    val sourceGroupColorKey: MedicationGroupColorKey? = null,
    val sourceGroupIsArchived: Boolean = false,
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
    val doseAmountDelta: Double?,
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

internal fun isMedicationLogEntryBusy(uiState: MedicationLogEntryUiState): Boolean {
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

internal fun isActualDoseDeltaForm(
    preparationType: MedicinePreparationType?,
    applicationType: MedicationApplicationType,
): Boolean = applicationType.isCompatibleWith(preparationType) &&
        (preparationType == MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ||
                preparationType == MedicinePreparationType.INJECTION_MULTI_USE_VIAL ||
                preparationType == MedicinePreparationType.GEL_CONTAINER)

internal fun buildEditingUiState(
    entries: List<MedicationLogEntry>,
    sourceGroup: MedicationGroup? = null,
    sourceGroupName: String? = null,
    sourceGroupColorKey: MedicationGroupColorKey? = null,
    sourceGroupIsArchived: Boolean = false,
    sourceGroupPreviousScheduledFor: LocalDateTime? = null,
    sourceGroupNextScheduledFor: LocalDateTime? = null,
): MedicationLogEntryUiState? {
    val representativeEntry = entries.firstOrNull() ?: return null
    val editableEntries = if (canBulkEditTogether(entries)) entries else listOf(representativeEntry)
    val appliedAtLocal = appliedAtAsLocalDateTime(representativeEntry)
    val matchingSourceGroup = sourceGroup?.takeIf { group ->
        group.uuid == representativeEntry.sourceGroupUuid
    }
    val hasMatchingSourceGroupSnapshot = representativeEntry.sourceGroupUuid != null &&
            !sourceGroupName.isNullOrBlank()

    return MedicationLogEntryUiState(
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
        sourceGroupIsArchived = matchingSourceGroup?.isArchived()
            ?: (hasMatchingSourceGroupSnapshot && sourceGroupIsArchived),
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
            applicationType = representativeEntry.applicationType,
            count = representativeEntry.count,
            preparationType = representativeEntry.medicine?.preparation?.type,
        ).toString(),
        // Carry the frozen delta only for single-entry edits; bulk edits may span
        // entries with differing deltas, so the read-only line is suppressed.
        doseAmountDelta = representativeEntry.doseAmountDelta
            .takeIf { editableEntries.size == 1 },
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
        preparationType = preparationTypeForNullableMedicine(
            medicine = entry.medicine,
            applicationType = entry.applicationType,
        ),
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

private fun MedicationLogEntryEditSnapshot.matchingEntries(entryUuids: List<UUID>): MedicationLogEntryEditSnapshot? {
    if (entryUuids.isEmpty()) {
        return null
    }

    val entriesByUuid = entries.associateBy(MedicationLogEntry::uuid)
    val matchingEntries = entryUuids.mapNotNull(entriesByUuid::get)
    return takeIf { matchingEntries.isNotEmpty() }?.copy(entries = matchingEntries)
}

private fun MedicationLogEntryEditSnapshot.toEditingUiState(): MedicationLogEntryUiState? {
    return buildEditingUiState(
        entries = entries,
        sourceGroupName = sourceGroupName,
        sourceGroupColorKey = sourceGroupColorKey,
        sourceGroupIsArchived = sourceGroupIsArchived,
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
    sourceGroupIsArchived: Boolean = false,
    sourceGroupPreviousScheduledFor: LocalDateTime? = null,
    sourceGroupNextScheduledFor: LocalDateTime? = null,
    appliedAt: LocalDateTime,
    isLoading: Boolean = false,
): MedicationLogEntryUiState {
    return MedicationLogEntryUiState(
        medicineDraft = medicineDraftForQuickLog(medicine, applicationType),
        doseInstructionDraft = doseInstructionDraftFromInstruction(
            applicationType = applicationType,
            preparationType = preparationTypeForNullableMedicine(
                medicine = medicine,
                applicationType = applicationType,
            ),
            doseInstruction = doseInstruction,
        ),
        resolvedMedicine = medicine,
        sourceGroupUuid = groupId,
        scheduleTimeUuid = scheduleTimeUuid,
        sourceGroupName = group?.name ?: sourceGroupName,
        sourceGroupColorKey = group?.colorKey ?: sourceGroupColorKey,
        sourceGroupIsArchived = group?.isArchived() ?: sourceGroupIsArchived,
        scheduledFor = scheduledFor,
        sourceGroupPreviousScheduledFor = group?.previousScheduledForBefore(scheduledFor)
            ?: sourceGroupPreviousScheduledFor,
        sourceGroupNextScheduledFor = group?.nextScheduledForAfter(scheduledFor)
            ?: sourceGroupNextScheduledFor,
        countText = normalizeMedicationCount(
            applicationType = applicationType,
            count = medicationCount,
            preparationType = medicine?.preparation?.type,
        ).toString(),
        appliedDate = appliedAt.toLocalDate(),
        appliedTime = appliedAt.toLocalTime().withSecond(0).withNano(0),
        isLoading = isLoading,
    )
}

private fun preparationTypeForNullableMedicine(
    medicine: Medicine?,
    applicationType: MedicationApplicationType,
): MedicinePreparationType {
    return medicine?.preparation?.type ?: when (applicationType) {
        MedicationApplicationType.PATCH_OFF -> MedicinePreparationType.PATCH_OFF
        MedicationApplicationType.ORAL,
        MedicationApplicationType.SUBLINGUAL,
        MedicationApplicationType.INJECTION,
        MedicationApplicationType.GEL,
        MedicationApplicationType.PATCH_ON -> MedicinePreparationType.PILL
    }
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
