package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.nextAvailableMedicationGroupColor
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.ui.medication.MedicationDraftUiState
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
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MedicationGroupEditorViewModel @Inject constructor(
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val settingsRepository: SettingsRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    appTimeSource: AppTimeSource
) : ViewModel() {
    private val requestedEditingGroupId = savedStateHandle.get<String>(GROUP_ID_ARG)
    private val editingGroupUuid = requestedEditingGroupId?.let { groupId ->
        runCatching { UUID.fromString(groupId) }.getOrNull()
    }
    private val editingGroupId = editingGroupUuid?.toString()
    private val pendingGroupUuid = editingGroupUuid ?: UUID.randomUUID()
    private val cachedEditingGroup = editingGroupUuid?.let(medicationGroupRepository::getCachedGroup)
    private val defaultScheduleDateTime =
        nextMedicationGroupEditorDefaultDateTime(appTimeSource.currentMinute.value)
    private val _uiState = MutableStateFlow(
        cachedEditingGroup?.toEditorState(
            remindersEnabled = settingsRepository.settingsState.value.remindersEnabled,
            relatedEntryCount = 0,
            plannedEntryCount = 0,
        ) ?: MedicationGroupEditorUiState(
            editingGroupId = editingGroupId,
            isLoadingGroupForEditing = editingGroupUuid != null,
            sinceDate = defaultScheduleDateTime.toLocalDate(),
            weeklyDaysOfWeek = setOf(defaultScheduleDateTime.dayOfWeek),
            weeklyTime = defaultScheduleDateTime.toLocalTime(),
            dailyTimes = listOf(
                MedicationGroupScheduleTimeUiState(time = defaultScheduleDateTime.toLocalTime())
            )
        )
    )
    val uiState: StateFlow<MedicationGroupEditorUiState> = _uiState.asStateFlow()
    val currentMinute: StateFlow<LocalDateTime> = appTimeSource.currentMinute

    init {
        viewModelScope.launch {
            settingsRepository.settingsState.collect { settingsState ->
                _uiState.update { currentState ->
                    applyReminderSettingsToEditorState(
                        currentState = currentState,
                        remindersEnabled = settingsState.remindersEnabled
                    )
                }
            }
        }

        viewModelScope.launch {
            medicationGroupRepository.observeGroups().collect { groupsOrNull ->
                _uiState.update { currentState ->
                    val allGroups = groupsOrNull.orEmpty()
                    val editingGroup = allGroups.firstOrNull { group ->
                        group.uuid.toString() == currentState.editingGroupId
                    }
                    val visibleGroups = allGroups.filterNot { group ->
                        group.uuid.toString() == currentState.editingGroupId
                    }
                    val hasActiveReplacement = editingGroup
                        ?.replacedByGroupUuid
                        ?.let { replacementUuid ->
                            allGroups.any { it.uuid == replacementUuid }
                        } == true
                    val resolvedGroupColorKey = resolveMedicationGroupColorKey(
                        currentColorKey = currentState.groupColorKey,
                        usedColors = visibleGroups.map { group -> group.colorKey },
                        seed = pendingGroupUuid.hashCode(),
                        isEditing = currentState.isEditing,
                        hasAssignedColor = currentState.hasAssignedGroupColor
                    )
                    applyDefaultGroupNameToEditorState(
                        currentState = currentState,
                        defaultGroupName = defaultMedicationGroupName(
                            existingGroupCount = visibleGroups.size,
                            formatName = { index ->
                                context.getString(R.string.default_group_name_format, index)
                            }
                        )
                    ).copy(
                        groupColorKey = resolvedGroupColorKey,
                        hasAssignedGroupColor = true,
                        hasActiveReplacement = hasActiveReplacement,
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                medicationLogRepository.observeEntries(),
                _uiState.map { it.editingGroupId }.distinctUntilChanged(),
            ) { entriesOrNull, currentEditingGroupId ->
                entryCountsForGroup(
                    entries = entriesOrNull.orEmpty(),
                    groupId = currentEditingGroupId,
                )
            }
                .collect { counts ->
                    _uiState.update {
                        it.copy(
                            relatedEntryCount = counts.relatedEntryCount,
                            plannedEntryCount = counts.plannedEntryCount,
                            scheduleTimeOrderError = if (counts.plannedEntryCount == 0) {
                                false
                            } else {
                                it.scheduleTimeOrderError
                            },
                        )
                    }
                }
        }

        if (editingGroupUuid != null && cachedEditingGroup == null) {
            loadGroupForEditing(editingGroupUuid)
        }
    }

    fun updateGroupName(name: String) {
        _uiState.update {
            it.copy(
                groupName = name,
                hasResolvedInitialGroupName = true
            )
        }
    }

    fun updateScheduleType(scheduleType: MedicationGroupScheduleType) {
        _uiState.update {
            if (it.areScheduleShapeFieldsLocked) {
                it
            } else {
                it.copy(scheduleType = scheduleType)
            }
        }
    }

    fun updateWeeklyIntervalWeeks(intervalWeeks: String) {
        _uiState.update {
            if (it.areScheduleShapeFieldsLocked) {
                it
            } else {
                it.copy(weeklyIntervalWeeks = parseScheduleInterval(intervalWeeks).toString())
            }
        }
    }

    fun toggleWeeklyDayOfWeek(dayOfWeek: DayOfWeek) {
        _uiState.update {
            if (it.areScheduleShapeFieldsLocked) {
                it
            } else {
                it.copy(weeklyDaysOfWeek = toggleWeeklyDaySelection(it.weeklyDaysOfWeek, dayOfWeek))
            }
        }
    }

    fun updateWeeklyTime(time: LocalTime) {
        _uiState.update {
            if (it.isArchived) {
                it
            } else {
                it.copy(
                    weeklyTime = time.withSecond(0).withNano(0),
                    scheduleTimeOrderError = false,
                )
            }
        }
    }

    fun updateSinceDate(date: LocalDate) {
        _uiState.update {
            if (it.areScheduleShapeFieldsLocked || !it.isSinceDateSelectable(date)) {
                it
            } else {
                it.copy(sinceDate = date)
            }
        }
    }

    fun updateDailyIntervalDays(intervalDays: String) {
        _uiState.update {
            if (it.areScheduleShapeFieldsLocked) {
                it
            } else {
                it.copy(dailyIntervalDays = parseScheduleInterval(intervalDays).toString())
            }
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(notificationsEnabled = enabled)
        }
    }

    fun setMasterRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRemindersEnabled(enabled)
            medicationReminderScheduler.rescheduleAll()
        }
    }

    fun addDailyTime(time: LocalTime) {
        _uiState.update {
            if (it.areScheduleShapeFieldsLocked) {
                it
            } else {
                it.copy(
                    dailyTimes = appendDailyTime(it.dailyTimes, time)
                )
            }
        }
    }

    fun updateDailyTime(localId: String, time: LocalTime) {
        _uiState.update { currentState ->
            if (currentState.isArchived) {
                return@update currentState
            }
            val normalizedTime = time.withSecond(0).withNano(0)
            val updatedDailyTimesInCurrentOrder = dailyTimesWithUpdatedTimeInCurrentOrder(
                dailyTimes = currentState.dailyTimes,
                localId = localId,
                time = normalizedTime,
            )
            val updatedState = currentState.copy(
                dailyTimes = sortDailyTimesByTime(updatedDailyTimesInCurrentOrder)
            )
            if (
                currentState.isLocked &&
                !areScheduleTimesInLockedOrder(updatedDailyTimesInCurrentOrder.map { it.time })
            ) {
                currentState.copy(scheduleTimeOrderError = false)
            } else {
                updatedState.copy(scheduleTimeOrderError = false)
            }
        }
    }

    fun removeDailyTime(localId: String) {
        _uiState.update { currentState ->
            if (currentState.areScheduleShapeFieldsLocked || currentState.dailyTimes.size <= 1) {
                currentState
            } else {
                currentState.copy(
                    dailyTimes = currentState.dailyTimes.filterNot { dailyTime ->
                        dailyTime.localId == localId
                    }
                )
            }
        }
    }

    fun showAddMedicationEditor() {
        _uiState.update {
            if (it.areMedicationsLocked) {
                it
            } else {
                it.copy(
                    editingMedication = MedicationGroupMedicationEditorUiState(),
                    isMedicationEditorSaved = false,
                    medicationEditorErrorMessageRes = null,
                    medicationEditorInfoMessageRes = null,
                )
            }
        }
    }

    fun removeMedication(localId: String) {
        _uiState.update {
            if (it.areMedicationsLocked) {
                it
            } else {
                it.copy(
                    medications = removeMedicationItem(
                        medications = it.medications,
                        localId = localId
                    )
                )
            }
        }
    }

    fun decreaseEditingMedicationCount() {
        updateEditingMedication { medication ->
            medication.copy(
                countText = stepMedicationCount(
                    applicationType = medication.draft.applicationType,
                    countText = medication.countText,
                    delta = -1
                ).toString()
            )
        }
    }

    fun increaseEditingMedicationCount() {
        updateEditingMedication { medication ->
            medication.copy(
                countText = stepMedicationCount(
                    applicationType = medication.draft.applicationType,
                    countText = medication.countText,
                    delta = 1
                ).toString()
            )
        }
    }

    fun updateEditingMedicationCountText(countText: String) {
        updateEditingMedication { medication ->
            medication.copy(
                countText = sanitizeMedicationCountText(countText)
            )
        }
    }

    fun showMedicationEditor(localId: String) {
        _uiState.update { currentState ->
            if (currentState.areMedicationsLocked) {
                currentState
            } else {
                currentState.copy(
                    editingMedication = currentState.medications
                        .firstOrNull { it.localId == localId }
                        ?.toEditorUiState(),
                    isMedicationEditorSaved = false,
                    medicationEditorErrorMessageRes = null,
                    medicationEditorInfoMessageRes = null,
                )
            }
        }
    }

    fun dismissMedicationEditor() {
        _uiState.update {
            it.copy(
                editingMedication = null,
                isMedicationEditorSaved = false,
                medicationEditorErrorMessageRes = null,
                medicationEditorInfoMessageRes = null,
            )
        }
    }

    fun consumeMedicationEditorSaved() {
        _uiState.update {
            it.copy(
                isMedicationEditorSaved = false,
                medicationEditorInfoMessageRes = null,
            )
        }
    }

    fun updateEditingMedicationDraft(
        transform: (MedicationDraftUiState) -> MedicationDraftUiState
    ) {
        updateEditingMedication { medication ->
            val updatedDraft = transform(medication.draft)
            medication.copy(
                draft = updatedDraft,
                countText = resolveMedicationCountTextAfterDraftChange(
                    previousDraft = medication.draft,
                    updatedDraft = updatedDraft,
                    currentCountText = medication.countText
                )
            )
        }
    }

    fun saveEditingMedication() {
        val currentState = _uiState.value
        if (currentState.areMedicationsLocked) {
            return
        }
        val editingMedication = currentState.editingMedication ?: return
        val errorRes = editingMedication.draft.validationErrorRes()
            ?: medicationCountValidationErrorRes(
                applicationType = editingMedication.draft.applicationType,
                countText = editingMedication.countText
            )

        if (errorRes != null) {
            _uiState.update {
                it.copy(
                    medicationEditorErrorMessageRes = errorRes,
                    medicationEditorInfoMessageRes = null,
                )
            }
            return
        }

        val savedMedication = MedicationGroupMedicationItemUiState(
            localId = editingMedication.localId,
            persistedMedicationId = editingMedication.persistedMedicationId,
            details = editingMedication.draft.toMedicationDetails(),
            count = resolvedMedicationCountForSave(
                applicationType = editingMedication.draft.applicationType,
                countText = editingMedication.countText
            )
        )

        _uiState.update {
            val saveResult = upsertMedication(
                medications = it.medications,
                savedMedication = savedMedication
            )

            it.copy(
                medications = saveResult.medications,
                editingMedication = saveResult.resolvedMedication.toEditorUiState(),
                isMedicationEditorSaved = true,
                medicationEditorErrorMessageRes = null,
                medicationEditorInfoMessageRes = if (saveResult.mergedIntoExisting) {
                    R.string.group_medication_duplicate_merged
                } else {
                    null
                },
            )
        }
    }

    fun saveGroup() {
        val currentState = _uiState.value
        if (
            currentState.isSaving ||
            currentState.isDeleting ||
            currentState.isArchiving ||
            currentState.isUnarchiving ||
            currentState.isRecreatingAfterArchive ||
            currentState.isDeletingRelatedEntries ||
            currentState.isArchived ||
            currentState.scheduleTimeOrderError ||
            !hasSaveableMedicationGroupContent(currentState)
        ) {
            return
        }
        val resolvedGroupName = resolveMedicationGroupName(
            groupName = currentState.groupName,
            defaultGroupName = currentState.defaultGroupName,
            isEditing = currentState.isEditing
        )

        val parsedWeeklyInterval = parseScheduleInterval(currentState.weeklyIntervalWeeks)
        val parsedDailyInterval = parseScheduleInterval(currentState.dailyIntervalDays)
        val resolvedDailyTimes = currentState.dailyTimes.ifEmpty {
            listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0)))
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveMedicationGroupResult = null,
                )
            }

            val savedGroupUuidResult = runCatching {
                if (currentState.isLocked && lockedScheduleTimesChanged(currentState)) {
                    medicationGroupRepository.updateScheduleTimes(
                        groupUuid = UUID.fromString(checkNotNull(currentState.editingGroupId)),
                        newTimes = scheduleTimesForSave(currentState),
                    )
                }
                medicationGroupRepository.saveGroup(
                    uuid = currentState.editingGroupId?.let(UUID::fromString),
                    name = resolvedGroupName,
                    colorKey = currentState.groupColorKey,
                    schedule = when (currentState.scheduleType) {
                        MedicationGroupScheduleType.WEEKLY -> MedicationGroupScheduleInput(
                            type = MedicationGroupScheduleType.WEEKLY,
                            interval = parsedWeeklyInterval,
                            since = currentState.sinceDate,
                            weeklyDaysOfWeek = currentState.weeklyDaysOfWeek,
                            times = listOf(currentState.weeklyTime)
                        )
                        MedicationGroupScheduleType.DAILY -> MedicationGroupScheduleInput(
                            type = MedicationGroupScheduleType.DAILY,
                            interval = parsedDailyInterval,
                            since = currentState.sinceDate,
                            weeklyDaysOfWeek = emptySet(),
                            times = resolvedDailyTimes
                                .map(MedicationGroupScheduleTimeUiState::time)
                                .sorted()
                        )
                    },
                    medications = currentState.medications.map { medication ->
                        MedicationGroupMedicationInput(
                            uuid = medication.persistedMedicationId?.let(UUID::fromString),
                            details = medication.details,
                            count = medication.count
                        )
                    },
                    notificationsEnabled = currentState.notificationsEnabled,
                    replacesGroupUuid = currentState.pendingReplacementGroupId?.let(UUID::fromString),
                )
            }
            val saveResult = savedGroupUuidResult.fold(
                onSuccess = { null },
                onFailure = { SaveMedicationGroupResult.FAILURE },
            )
            val isSaved = saveResult == null
            val savedGroupUuid = savedGroupUuidResult.getOrNull()
            if (savedGroupUuid != null) {
                runCatching { medicationReminderScheduler.rescheduleGroup(savedGroupUuid) }
            }

            _uiState.update {
                it.copy(
                    groupName = if (isSaved) {
                        resolvedGroupName
                    } else {
                        it.groupName
                    },
                    editingGroupId = savedGroupUuid?.toString() ?: it.editingGroupId,
                    pendingReplacementGroupId = if (isSaved) {
                        null
                    } else {
                        it.pendingReplacementGroupId
                    },
                    isSaving = false,
                    isSaved = isSaved,
                    saveMedicationGroupResult = saveResult,
                )
            }
        }
    }

    fun showDeleteConfirmation() {
        if (_uiState.value.isEditing) {
            _uiState.update {
                it.copy(isDeleteConfirmationVisible = true)
            }
        }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update {
            it.copy(isDeleteConfirmationVisible = false)
        }
    }

    fun showArchiveConfirmation() {
        if (_uiState.value.isEditing && !_uiState.value.isArchived) {
            _uiState.update {
                it.copy(
                    isArchiveConfirmationVisible = true,
                    archiveMedicationGroupResult = null,
                    archiveAndRecreateMedicationGroupResult = null,
                    pendingReplacementGroupId = null,
                )
            }
        }
    }

    fun dismissArchiveConfirmation() {
        _uiState.update {
            it.copy(isArchiveConfirmationVisible = false)
        }
    }

    fun showUnarchiveConfirmation() {
        val currentState = _uiState.value
        if (
            currentState.isEditing &&
            currentState.isArchived &&
            !currentState.isSaving &&
            !currentState.isDeleting &&
            !currentState.isArchiving &&
            !currentState.isUnarchiving &&
            !currentState.isRecreatingAfterArchive &&
            !currentState.isDeletingRelatedEntries
        ) {
            _uiState.update {
                it.copy(
                    isUnarchiveConfirmationVisible = true,
                    unarchiveMedicationGroupResult = null,
                )
            }
        }
    }

    fun dismissUnarchiveConfirmation() {
        _uiState.update {
            it.copy(isUnarchiveConfirmationVisible = false)
        }
    }

    fun archiveGroup() {
        val groupId = _uiState.value.editingGroupId ?: return
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isArchiving = true,
                    isArchiveConfirmationVisible = false,
                    archiveMedicationGroupResult = null,
                )
            }

            val archiveResult = runCatching {
                medicationGroupRepository.archiveGroup(uuid)
            }.fold(
                onSuccess = { null },
                onFailure = { ArchiveMedicationGroupResult.FAILURE },
            )
            val archived = archiveResult == null

            if (archived) {
                runCatching { medicationReminderScheduler.cancelReminder(uuid) }
                runCatching { medicationReminderScheduler.rescheduleAll() }
            }

            _uiState.update {
                it.copy(
                    isArchiving = false,
                    isDeleted = archived,
                    archiveMedicationGroupResult = archiveResult,
                )
            }
        }
    }

    fun unarchiveGroup() {
        val currentState = _uiState.value
        val groupId = currentState.editingGroupId ?: return
        if (
            !currentState.isArchived ||
            currentState.isSaving ||
            currentState.isDeleting ||
            currentState.isArchiving ||
            currentState.isUnarchiving ||
            currentState.isRecreatingAfterArchive ||
            currentState.isDeletingRelatedEntries
        ) {
            return
        }
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUnarchiving = true,
                    isUnarchiveConfirmationVisible = false,
                    unarchiveMedicationGroupResult = null,
                )
            }

            val unarchiveResult = runCatching {
                medicationGroupRepository.unarchiveGroup(uuid)
            }.fold(
                onSuccess = { null },
                onFailure = { UnarchiveMedicationGroupResult.FAILURE },
            )
            val unarchived = unarchiveResult == null

            if (unarchived && currentState.notificationsEnabled) {
                runCatching { medicationReminderScheduler.rescheduleGroup(uuid) }
            }

            _uiState.update {
                it.copy(
                    isUnarchiving = false,
                    isSaved = unarchived,
                    unarchiveMedicationGroupResult = unarchiveResult,
                )
            }
        }
    }

    fun archiveAndRecreateGroup() {
        val currentState = _uiState.value
        val groupId = currentState.editingGroupId ?: return
        if (
            currentState.isArchived ||
            currentState.isSaving ||
            currentState.isDeleting ||
            currentState.isArchiving ||
            currentState.isUnarchiving ||
            currentState.isRecreatingAfterArchive ||
            currentState.isDeletingRelatedEntries
        ) {
            return
        }
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull() ?: return
        val recreateScheduleStartDate = currentMinute.value.toLocalDate()
        val resolvedGroupName = resolveMedicationGroupName(
            groupName = currentState.groupName,
            defaultGroupName = currentState.defaultGroupName,
            isEditing = currentState.isEditing,
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRecreatingAfterArchive = true,
                    isArchiveConfirmationVisible = false,
                    archiveAndRecreateMedicationGroupResult = null,
                )
            }

            val recreateResult = runCatching {
                medicationGroupRepository.archiveGroup(uuid)
            }.fold(
                onSuccess = { ArchiveAndRecreateMedicationGroupResult.SUCCESS },
                onFailure = { ArchiveAndRecreateMedicationGroupResult.FAILURE },
            )

            if (recreateResult == ArchiveAndRecreateMedicationGroupResult.SUCCESS) {
                runCatching { medicationReminderScheduler.cancelReminder(uuid) }
                runCatching { medicationReminderScheduler.rescheduleAll() }
            }

            _uiState.update {
                if (recreateResult == ArchiveAndRecreateMedicationGroupResult.SUCCESS) {
                    currentState.toUnsavedRecreatedGroupState(
                        archivedGroupId = groupId,
                        scheduleStartDate = recreateScheduleStartDate,
                        resolvedGroupName = resolvedGroupName,
                    ).copy(
                        remindersEnabled = it.remindersEnabled,
                        isRecreatingAfterArchive = false,
                        archiveAndRecreateMedicationGroupResult = recreateResult,
                    )
                } else {
                    it.copy(
                        isRecreatingAfterArchive = false,
                        archiveAndRecreateMedicationGroupResult = recreateResult,
                    )
                }
            }
        }
    }

    fun showDeleteRelatedEntriesConfirmation() {
        val currentState = _uiState.value
        if (currentState.isEditing && currentState.relatedEntryCount > 0) {
            _uiState.update {
                it.copy(isDeleteRelatedEntriesConfirmationVisible = true)
            }
        }
    }

    fun dismissDeleteRelatedEntriesConfirmation() {
        _uiState.update {
            it.copy(isDeleteRelatedEntriesConfirmationVisible = false)
        }
    }

    fun deleteGroup() {
        deleteGroup(deleteRelatedEntries = false)
    }

    fun deleteGroupAndRelatedEntries() {
        deleteGroup(deleteRelatedEntries = true)
    }

    fun deleteRelatedEntries() {
        val currentState = _uiState.value
        val groupId = currentState.editingGroupId ?: return
        if (currentState.relatedEntryCount <= 0) {
            return
        }
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDeletingRelatedEntries = true,
                    isDeleteRelatedEntriesConfirmationVisible = false,
                )
            }

            val result = runCatching {
                medicationLogRepository.deleteEntriesForGroup(uuid)
            }.fold(
                onSuccess = { DeleteRelatedEntriesResult.SUCCESS },
                onFailure = { DeleteRelatedEntriesResult.FAILURE },
            )
            if (result == DeleteRelatedEntriesResult.SUCCESS) {
                runCatching { medicationReminderScheduler.rescheduleAll() }
            }

            _uiState.update {
                it.copy(
                    isDeletingRelatedEntries = false,
                    deleteRelatedEntriesResult = result,
                    relatedEntryCount = if (result == DeleteRelatedEntriesResult.SUCCESS) {
                        0
                    } else {
                        it.relatedEntryCount
                    },
                )
            }
        }
    }

    fun consumeSavedState() {
        _uiState.update { it.copy(isSaved = false) }
    }

    fun consumeSaveMedicationGroupResult() {
        _uiState.update { it.copy(saveMedicationGroupResult = null) }
    }

    fun consumeDeletedState() {
        _uiState.update { it.copy(isDeleted = false) }
    }

    fun consumeDeleteRelatedEntriesResult() {
        _uiState.update { it.copy(deleteRelatedEntriesResult = null) }
    }

    fun consumeDeleteMedicationGroupResult() {
        _uiState.update { it.copy(deleteMedicationGroupResult = null) }
    }

    fun consumeArchiveMedicationGroupResult() {
        _uiState.update { it.copy(archiveMedicationGroupResult = null) }
    }

    fun consumeUnarchiveMedicationGroupResult() {
        _uiState.update { it.copy(unarchiveMedicationGroupResult = null) }
    }

    fun consumeArchiveAndRecreateMedicationGroupResult() {
        _uiState.update { it.copy(archiveAndRecreateMedicationGroupResult = null) }
    }

    private fun loadGroupForEditing(uuid: UUID) {
        viewModelScope.launch {
            val group = medicationGroupRepository.getGroup(uuid)
            if (group == null) {
                _uiState.update { it.copy(isLoadingGroupForEditing = false) }
                return@launch
            }
            val remindersEnabled = settingsRepository.getCurrentSettings().remindersEnabled

            _uiState.update { currentState ->
                group.toEditorState(
                    remindersEnabled = remindersEnabled,
                    relatedEntryCount = currentState.relatedEntryCount,
                    plannedEntryCount = currentState.plannedEntryCount,
                )
            }
        }
    }

    private fun updateEditingMedication(
        transform: (MedicationGroupMedicationEditorUiState) -> MedicationGroupMedicationEditorUiState
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                editingMedication = currentState.editingMedication?.let(transform),
                medicationEditorErrorMessageRes = null,
                medicationEditorInfoMessageRes = null,
            )
        }
    }

    private fun deleteGroup(deleteRelatedEntries: Boolean) {
        val groupId = _uiState.value.editingGroupId ?: return
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDeleting = true,
                    isDeleteConfirmationVisible = false,
                    deleteMedicationGroupResult = null,
                )
            }

            val deleteResult = runCatching {
                if (deleteRelatedEntries) {
                    medicationGroupRepository.deleteGroupAndRelatedEntries(uuid)
                } else {
                    medicationGroupRepository.deleteGroup(uuid)
                }
            }.fold(
                onSuccess = { null },
                onFailure = { DeleteMedicationGroupResult.FAILURE },
            )
            val isDeleted = deleteResult == null

            if (isDeleted) {
                runCatching { medicationReminderScheduler.cancelReminder(uuid) }
            }

            _uiState.update {
                it.copy(
                    isDeleting = false,
                    isDeleted = isDeleted,
                    deleteMedicationGroupResult = deleteResult,
                )
            }
        }
    }

    companion object {
        const val GROUP_ID_ARG = "groupId"
    }
}

private fun MedicationGroup.toEditorState(
    remindersEnabled: Boolean,
    relatedEntryCount: Int,
    plannedEntryCount: Int,
): MedicationGroupEditorUiState {
    val normalizedScheduleTimes = schedule.times
        .ifEmpty { listOf(LocalTime.of(9, 0)) }
        .sorted()
    val editorMedications = medications.map { medication ->
        MedicationGroupMedicationItemUiState(
            localId = medication.uuid.toString(),
            persistedMedicationId = medication.uuid.toString(),
            details = medication.details,
            count = normalizeMedicationCount(
                medication.details.applicationType,
                medication.count
            )
        )
    }
    return MedicationGroupEditorUiState(
        editingGroupId = uuid.toString(),
        groupName = name,
        defaultGroupName = name,
        hasResolvedInitialGroupName = true,
        scheduleType = schedule.type,
        sinceDate = schedule.since,
        weeklyIntervalWeeks = if (schedule.type == MedicationGroupScheduleType.WEEKLY) {
            parseScheduleInterval(schedule.interval.toString()).toString()
        } else {
            "1"
        },
        weeklyDaysOfWeek = schedule.weeklyDaysOfWeek.ifEmpty {
            setOf(LocalDate.now().dayOfWeek)
        },
        weeklyTime = schedule.times.firstOrNull() ?: LocalTime.of(9, 0),
        dailyIntervalDays = if (schedule.type == MedicationGroupScheduleType.DAILY) {
            parseScheduleInterval(schedule.interval.toString()).toString()
        } else {
            "1"
        },
        dailyTimes = if (schedule.type == MedicationGroupScheduleType.DAILY) {
            normalizedScheduleTimes.map { time -> MedicationGroupScheduleTimeUiState(time = time) }
        } else {
            listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0)))
        },
        remindersEnabled = remindersEnabled,
        notificationsEnabled = notificationsEnabled,
        hasResolvedNotificationDefault = true,
        includePastScheduledSlots = includePastScheduledSlots,
        minimumSelectableSinceDate = if (includePastScheduledSlots) null else schedule.since,
        groupColorKey = colorKey,
        hasAssignedGroupColor = true,
        isLoadingGroupForEditing = false,
        medications = editorMedications,
        relatedEntryCount = relatedEntryCount,
        plannedEntryCount = plannedEntryCount,
        isArchived = archivedAt != null,
        originalScheduleType = schedule.type,
        originalSinceDate = schedule.since,
        originalWeeklyIntervalWeeks = if (schedule.type == MedicationGroupScheduleType.WEEKLY) {
            parseScheduleInterval(schedule.interval.toString()).toString()
        } else {
            "1"
        },
        originalWeeklyDaysOfWeek = schedule.weeklyDaysOfWeek,
        originalWeeklyTime = schedule.times.firstOrNull() ?: LocalTime.of(9, 0),
        originalDailyIntervalDays = if (schedule.type == MedicationGroupScheduleType.DAILY) {
            parseScheduleInterval(schedule.interval.toString()).toString()
        } else {
            "1"
        },
        originalDailyTimes = if (schedule.type == MedicationGroupScheduleType.DAILY) {
            normalizedScheduleTimes
        } else {
            listOf(LocalTime.of(9, 0))
        },
        originalMedications = editorMedications,
    )
}

private fun MedicationGroupEditorUiState.toUnsavedRecreatedGroupState(
    archivedGroupId: String,
    scheduleStartDate: LocalDate,
    resolvedGroupName: String,
): MedicationGroupEditorUiState {
    val copiedMedications = medications.map { medication ->
        medication.copy(
            localId = UUID.randomUUID().toString(),
            persistedMedicationId = null,
        )
    }
    return copy(
        editingGroupId = null,
        groupName = resolvedGroupName,
        defaultGroupName = resolvedGroupName,
        hasResolvedInitialGroupName = true,
        sinceDate = scheduleStartDate,
        dailyTimes = dailyTimes.map { dailyTime ->
            MedicationGroupScheduleTimeUiState(time = dailyTime.time)
        },
        medications = copiedMedications,
        editingMedication = null,
        isMedicationEditorSaved = false,
        medicationEditorErrorMessageRes = null,
        medicationEditorInfoMessageRes = null,
        isSaving = false,
        isDeleting = false,
        isArchiving = false,
        isUnarchiving = false,
        isDeletingRelatedEntries = false,
        isLoadingGroupForEditing = false,
        isSaved = false,
        isDeleted = false,
        isArchived = false,
        includePastScheduledSlots = false,
        minimumSelectableSinceDate = scheduleStartDate,
        hasActiveReplacement = false,
        saveMedicationGroupResult = null,
        relatedEntryCount = 0,
        plannedEntryCount = 0,
        scheduleTimeOrderError = false,
        originalScheduleType = null,
        originalSinceDate = null,
        originalWeeklyIntervalWeeks = "1",
        originalWeeklyDaysOfWeek = emptySet(),
        originalWeeklyTime = LocalTime.of(9, 0),
        originalDailyIntervalDays = "1",
        originalDailyTimes = emptyList(),
        originalMedications = emptyList(),
        isArchiveConfirmationVisible = false,
        isUnarchiveConfirmationVisible = false,
        isDeleteConfirmationVisible = false,
        isDeleteRelatedEntriesConfirmationVisible = false,
        pendingReplacementGroupId = archivedGroupId,
        archiveMedicationGroupResult = null,
        unarchiveMedicationGroupResult = null,
        deleteRelatedEntriesResult = null,
        deleteMedicationGroupResult = null,
    )
}

internal fun toggleWeeklyDaySelection(
    selectedDays: Set<DayOfWeek>,
    dayOfWeek: DayOfWeek
): Set<DayOfWeek> {
    return if (dayOfWeek in selectedDays) {
        selectedDays - dayOfWeek
    } else {
        selectedDays + dayOfWeek
    }
}

internal fun hasSaveableMedicationGroupContent(
    uiState: MedicationGroupEditorUiState
): Boolean {
    val resolvedGroupName = resolveMedicationGroupName(
        groupName = uiState.groupName,
        defaultGroupName = uiState.defaultGroupName,
        isEditing = uiState.isEditing
    )
    val hasWeeklyDays = uiState.scheduleType != MedicationGroupScheduleType.WEEKLY ||
        uiState.weeklyDaysOfWeek.isNotEmpty()
    return resolvedGroupName.isNotEmpty() &&
        uiState.medications.isNotEmpty() &&
        hasWeeklyDays
}

internal fun removeMedicationItem(
    medications: List<MedicationGroupMedicationItemUiState>,
    localId: String
): List<MedicationGroupMedicationItemUiState> {
    return medications.filterNot { medication -> medication.localId == localId }
}

internal fun appendDailyTime(
    dailyTimes: List<MedicationGroupScheduleTimeUiState>,
    time: LocalTime
): List<MedicationGroupScheduleTimeUiState> {
    return sortDailyTimesByTime(
        dailyTimes + MedicationGroupScheduleTimeUiState(
            time = time.withSecond(0).withNano(0)
        )
    )
}

internal fun dailyTimesWithUpdatedTime(
    dailyTimes: List<MedicationGroupScheduleTimeUiState>,
    localId: String,
    time: LocalTime,
): List<MedicationGroupScheduleTimeUiState> {
    return sortDailyTimesByTime(
        dailyTimesWithUpdatedTimeInCurrentOrder(
            dailyTimes = dailyTimes,
            localId = localId,
            time = time,
        )
    )
}

private fun dailyTimesWithUpdatedTimeInCurrentOrder(
    dailyTimes: List<MedicationGroupScheduleTimeUiState>,
    localId: String,
    time: LocalTime,
): List<MedicationGroupScheduleTimeUiState> {
    val normalizedTime = time.withSecond(0).withNano(0)
    return dailyTimes.map { dailyTime ->
        if (dailyTime.localId == localId) {
            dailyTime.copy(time = normalizedTime)
        } else {
            dailyTime
        }
    }
}

internal fun sortDailyTimesByTime(
    dailyTimes: List<MedicationGroupScheduleTimeUiState>,
): List<MedicationGroupScheduleTimeUiState> {
    return dailyTimes.sortedBy { dailyTime -> dailyTime.time }
}

internal fun nextMedicationGroupEditorDefaultDateTime(
    currentDateTime: LocalDateTime
): LocalDateTime {
    val currentMinute = currentDateTime.withSecond(0).withNano(0)
    return if (currentMinute.minute < 30) {
        currentMinute.withMinute(30)
    } else {
        currentMinute.plusHours(1).withMinute(0)
    }
}

internal fun hasDuplicateDailyTime(
    dailyTimes: List<MedicationGroupScheduleTimeUiState>,
    time: LocalTime,
    excludingLocalId: String? = null,
): Boolean {
    val normalizedTime = time.withSecond(0).withNano(0)
    return dailyTimes.any { dailyTime ->
        dailyTime.localId != excludingLocalId && dailyTime.time == normalizedTime
    }
}

internal data class MedicationGroupMedicationSaveResult(
    val medications: List<MedicationGroupMedicationItemUiState>,
    val resolvedMedication: MedicationGroupMedicationItemUiState,
    val mergedIntoExisting: Boolean,
)

internal fun upsertMedication(
    medications: List<MedicationGroupMedicationItemUiState>,
    savedMedication: MedicationGroupMedicationItemUiState,
): MedicationGroupMedicationSaveResult {
    val duplicateMedication = medications.firstOrNull { medication ->
        medication.localId != savedMedication.localId &&
            medication.details == savedMedication.details
    }

    if (duplicateMedication != null) {
        val mergedMedication = duplicateMedication.copy(
            count = duplicateMedication.count + savedMedication.count
        )
        val updatedMedications = medications
            .filterNot { medication -> medication.localId == savedMedication.localId }
            .map { medication ->
                if (medication.localId == duplicateMedication.localId) {
                    mergedMedication
                } else {
                    medication
                }
            }
        return MedicationGroupMedicationSaveResult(
            medications = updatedMedications,
            resolvedMedication = mergedMedication,
            mergedIntoExisting = true
        )
    }

    val existingIndex = medications.indexOfFirst { medication ->
        medication.localId == savedMedication.localId
    }
    val updatedMedications = if (existingIndex >= 0) {
        medications.toMutableList().apply {
            this[existingIndex] = savedMedication
        }
    } else {
        medications + savedMedication
    }
    return MedicationGroupMedicationSaveResult(
        medications = updatedMedications,
        resolvedMedication = savedMedication,
        mergedIntoExisting = false
    )
}

data class MedicationGroupEditorUiState(
    val editingGroupId: String? = null,
    val groupName: String = "",
    val defaultGroupName: String = "",
    val hasResolvedInitialGroupName: Boolean = false,
    val groupColorKey: MedicationGroupColorKey = MedicationGroupColorKey.ROSE,
    val hasAssignedGroupColor: Boolean = false,
    val scheduleType: MedicationGroupScheduleType = MedicationGroupScheduleType.DAILY,
    val sinceDate: LocalDate = LocalDate.now(),
    val weeklyIntervalWeeks: String = "1",
    val weeklyDaysOfWeek: Set<DayOfWeek> = setOf(LocalDate.now().dayOfWeek),
    val weeklyTime: LocalTime = LocalTime.of(9, 0),
    val dailyIntervalDays: String = "1",
    val dailyTimes: List<MedicationGroupScheduleTimeUiState> = listOf(
        MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0))
    ),
    val remindersEnabled: Boolean = true,
    val notificationsEnabled: Boolean = false,
    val hasResolvedNotificationDefault: Boolean = false,
    val medications: List<MedicationGroupMedicationItemUiState> = emptyList(),
    val editingMedication: MedicationGroupMedicationEditorUiState? = null,
    val isMedicationEditorSaved: Boolean = false,
    val medicationEditorErrorMessageRes: Int? = null,
    val medicationEditorInfoMessageRes: Int? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isArchiving: Boolean = false,
    val isUnarchiving: Boolean = false,
    val isRecreatingAfterArchive: Boolean = false,
    val isDeletingRelatedEntries: Boolean = false,
    val isLoadingGroupForEditing: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isArchived: Boolean = false,
    val includePastScheduledSlots: Boolean = true,
    val minimumSelectableSinceDate: LocalDate? = null,
    val hasActiveReplacement: Boolean = false,
    val saveMedicationGroupResult: SaveMedicationGroupResult? = null,
    val relatedEntryCount: Int = 0,
    val plannedEntryCount: Int = 0,
    val scheduleTimeOrderError: Boolean = false,
    val originalScheduleType: MedicationGroupScheduleType? = null,
    val originalSinceDate: LocalDate? = null,
    val originalWeeklyIntervalWeeks: String = "1",
    val originalWeeklyDaysOfWeek: Set<DayOfWeek> = emptySet(),
    val originalWeeklyTime: LocalTime = LocalTime.of(9, 0),
    val originalDailyIntervalDays: String = "1",
    val originalDailyTimes: List<LocalTime> = emptyList(),
    val originalMedications: List<MedicationGroupMedicationItemUiState> = emptyList(),
    val isArchiveConfirmationVisible: Boolean = false,
    val isUnarchiveConfirmationVisible: Boolean = false,
    val isDeleteConfirmationVisible: Boolean = false,
    val isDeleteRelatedEntriesConfirmationVisible: Boolean = false,
    val pendingReplacementGroupId: String? = null,
    val archiveMedicationGroupResult: ArchiveMedicationGroupResult? = null,
    val unarchiveMedicationGroupResult: UnarchiveMedicationGroupResult? = null,
    val archiveAndRecreateMedicationGroupResult: ArchiveAndRecreateMedicationGroupResult? = null,
    val deleteRelatedEntriesResult: DeleteRelatedEntriesResult? = null,
    val deleteMedicationGroupResult: DeleteMedicationGroupResult? = null,
) {
    val isEditing: Boolean
        get() = editingGroupId != null

    val isLocked: Boolean
        get() = isEditing && !isArchived && plannedEntryCount > 0

    val areScheduleShapeFieldsLocked: Boolean
        get() = isLocked || isArchived

    val areMedicationsLocked: Boolean
        get() = isLocked || isArchived
}

internal fun MedicationGroupEditorUiState.isSinceDateSelectable(date: LocalDate): Boolean {
    val minimumDate = minimumSelectableSinceDate ?: return true
    return !date.isBefore(minimumDate)
}

enum class SaveMedicationGroupResult {
    FAILURE,
}

enum class DeleteRelatedEntriesResult {
    SUCCESS,
    FAILURE,
}

enum class DeleteMedicationGroupResult {
    FAILURE,
}

enum class ArchiveMedicationGroupResult {
    FAILURE,
}

enum class UnarchiveMedicationGroupResult {
    FAILURE,
}

enum class ArchiveAndRecreateMedicationGroupResult {
    SUCCESS,
    FAILURE,
}

internal fun applyDefaultGroupNameToEditorState(
    currentState: MedicationGroupEditorUiState,
    defaultGroupName: String,
): MedicationGroupEditorUiState {
    val shouldApplyInitialGroupName = !currentState.isEditing &&
        !currentState.hasResolvedInitialGroupName &&
        currentState.groupName.isBlank() &&
        defaultGroupName.isNotBlank()
    return currentState.copy(
        groupName = if (shouldApplyInitialGroupName) {
            defaultGroupName
        } else {
            currentState.groupName
        },
        defaultGroupName = defaultGroupName,
        hasResolvedInitialGroupName = currentState.hasResolvedInitialGroupName ||
            shouldApplyInitialGroupName
    )
}

internal fun applyReminderSettingsToEditorState(
    currentState: MedicationGroupEditorUiState,
    remindersEnabled: Boolean,
): MedicationGroupEditorUiState {
    val shouldApplyNotificationDefault =
        !currentState.isEditing && !currentState.hasResolvedNotificationDefault
    return currentState.copy(
        remindersEnabled = remindersEnabled,
        notificationsEnabled = if (shouldApplyNotificationDefault) {
            remindersEnabled
        } else {
            currentState.notificationsEnabled
        },
        hasResolvedNotificationDefault = currentState.hasResolvedNotificationDefault ||
            shouldApplyNotificationDefault
    )
}

internal fun resolveMedicationGroupName(
    groupName: String,
    defaultGroupName: String,
    isEditing: Boolean
): String {
    val trimmedGroupName = groupName.trim()
    return if (trimmedGroupName.isNotEmpty()) {
        trimmedGroupName
    } else if (!isEditing) {
        defaultGroupName.trim()
    } else {
        ""
    }
}

internal fun defaultMedicationGroupName(
    existingGroupCount: Int,
    formatName: (Int) -> String
): String {
    return formatName(existingGroupCount + 1)
}

internal fun resolveMedicationGroupColorKey(
    currentColorKey: MedicationGroupColorKey,
    usedColors: Collection<MedicationGroupColorKey>,
    seed: Int,
    isEditing: Boolean,
    hasAssignedColor: Boolean
): MedicationGroupColorKey {
    return if (isEditing || hasAssignedColor) {
        currentColorKey
    } else {
        nextAvailableMedicationGroupColor(
            usedColors = usedColors,
            seed = seed
        )
    }
}

internal data class MedicationGroupEntryCounts(
    val relatedEntryCount: Int,
    val plannedEntryCount: Int,
)

internal fun entryCountsForGroup(
    entries: List<MedicationLogEntry>,
    groupId: String?,
): MedicationGroupEntryCounts {
    if (groupId == null) {
        return MedicationGroupEntryCounts(
            relatedEntryCount = 0,
            plannedEntryCount = 0,
        )
    }

    val groupUuid = runCatching { UUID.fromString(groupId) }.getOrNull()
        ?: return MedicationGroupEntryCounts(
            relatedEntryCount = 0,
            plannedEntryCount = 0,
        )
    val relatedEntries = entries.filter { entry -> entry.sourceGroupUuid == groupUuid }
    return MedicationGroupEntryCounts(
        relatedEntryCount = relatedEntries.size,
        plannedEntryCount = relatedEntries.count { entry -> entry.scheduledFor != null },
    )
}

internal fun relatedEntryCountForGroup(
    entries: List<MedicationLogEntry>,
    groupId: String?,
): Int = entryCountsForGroup(entries, groupId).relatedEntryCount

internal fun scheduleTimesForSave(uiState: MedicationGroupEditorUiState): List<LocalTime> {
    return when (uiState.scheduleType) {
        MedicationGroupScheduleType.WEEKLY -> listOf(uiState.weeklyTime.withSecond(0).withNano(0))
        MedicationGroupScheduleType.DAILY -> uiState.dailyTimes
            .ifEmpty { listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0))) }
            .map { dailyTime -> dailyTime.time.withSecond(0).withNano(0) }
    }
}

internal fun lockedScheduleTimesChanged(uiState: MedicationGroupEditorUiState): Boolean {
    val originalTimes = when (uiState.originalScheduleType) {
        MedicationGroupScheduleType.WEEKLY -> listOf(uiState.originalWeeklyTime)
        MedicationGroupScheduleType.DAILY -> uiState.originalDailyTimes
        null -> emptyList()
    }
    return scheduleTimesForSave(uiState) != originalTimes
}

internal fun areScheduleTimesInLockedOrder(times: List<LocalTime>): Boolean {
    return times.zipWithNext().all { (first, second) -> first.isBefore(second) }
}

data class MedicationGroupMedicationItemUiState(
    val localId: String = UUID.randomUUID().toString(),
    val persistedMedicationId: String? = null,
    val details: MedicationDetails = defaultMedicationDraft().toMedicationDetails(),
    val count: Int = 1,
) {
    fun toEditorUiState(): MedicationGroupMedicationEditorUiState {
        return MedicationGroupMedicationEditorUiState(
            localId = localId,
            persistedMedicationId = persistedMedicationId,
            draft = medicationDraftFromDetails(details),
            countText = normalizeMedicationCount(details.applicationType, count).toString()
        )
    }
}

data class MedicationGroupMedicationEditorUiState(
    val localId: String = UUID.randomUUID().toString(),
    val persistedMedicationId: String? = null,
    val draft: MedicationDraftUiState = defaultMedicationDraft(),
    val countText: String = "1",
) {
    val count: Int
        get() = parseMedicationCountText(countText)
}

data class MedicationGroupScheduleTimeUiState(
    val localId: String = UUID.randomUUID().toString(),
    val time: LocalTime,
)
