package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleInput
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleTimeInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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
    private var latestGroups: List<MedicationGroup> = emptyList()
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
                    latestGroups = allGroups
                    val editingGroup = allGroups.firstOrNull { group ->
                        group.uuid.toString() == currentState.editingGroupId
                    }
                    val visibleGroups = allGroups.filterNot { group ->
                        group.uuid.toString() == currentState.editingGroupId
                    }
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
            if (it.areScheduleShapeFieldsLocked || it.isScheduleStartDateLocked) {
                it
            } else {
                it.copy(sinceDate = date)
            }
        }
    }

    fun updateIncludePastScheduledSlots(includePastScheduledSlots: Boolean) {
        _uiState.update {
            if (!it.canEditBackfillOption) {
                it
            } else {
                it.copy(
                    includePastScheduledSlots = includePastScheduledSlots,
                    createPastScheduledSlotRecords = if (includePastScheduledSlots) {
                        it.createPastScheduledSlotRecords
                    } else {
                        false
                    },
                )
            }
        }
    }

    fun updateCreatePastScheduledSlotRecords(createPastScheduledSlotRecords: Boolean) {
        _uiState.update {
            if (!it.canCreatePastScheduledSlotRecords) {
                it
            } else {
                it.copy(createPastScheduledSlotRecords = createPastScheduledSlotRecords)
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
                dailyTimes = sortDailyTimesByOriginalTime(updatedDailyTimesInCurrentOrder)
            )
            updatedState.copy(scheduleTimeOrderError = false)
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

    fun consumeMedicationEditorInfoMessage() {
        _uiState.update {
            it.copy(medicationEditorInfoMessageRes = null)
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

            if (saveResult.duplicateAlreadyExists) {
                return@update it.copy(
                    isMedicationEditorSaved = false,
                    medicationEditorErrorMessageRes = null,
                    medicationEditorInfoMessageRes = R.string.group_medication_duplicate_exists,
                )
            }

            it.copy(
                medications = saveResult.medications,
                editingMedication = saveResult.resolvedMedication.toEditorUiState(),
                isMedicationEditorSaved = true,
                medicationEditorErrorMessageRes = null,
                medicationEditorInfoMessageRes = null,
            )
        }
    }

    fun saveGroup() {
        val currentState = _uiState.value
        if (
            currentState.isSaving ||
            currentState.isDeleting ||
            currentState.isArchiving ||
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
        val scheduleTimeInputs = scheduleTimeInputsForSave(
            uiState = currentState,
            resolvedDailyTimes = resolvedDailyTimes,
        )
        val recordGenerationNow = currentMinute.value
        val recordGenerationInstant = recordGenerationNow.toSystemInstant()
        val shouldCreatePastRecords = currentState.canCreatePastScheduledSlotRecords &&
            currentState.createPastScheduledSlotRecords &&
            hasPastScheduleOptionWindow(
                uiState = currentState,
                referenceTime = recordGenerationNow,
            )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveMedicationGroupResult = null,
                    createPastScheduledSlotRecordsResult = null,
                    createdPastScheduledSlotRecordCount = null,
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
                            times = scheduleTimeInputs.map(MedicationGroupScheduleTimeInput::time),
                            timeSlots = scheduleTimeInputs,
                        )
                        MedicationGroupScheduleType.DAILY -> MedicationGroupScheduleInput(
                            type = MedicationGroupScheduleType.DAILY,
                            interval = parsedDailyInterval,
                            since = currentState.sinceDate,
                            weeklyDaysOfWeek = emptySet(),
                            times = scheduleTimeInputs.map(MedicationGroupScheduleTimeInput::time),
                            timeSlots = scheduleTimeInputs,
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
                    includePastScheduledSlots = currentState.includePastScheduledSlots,
                    replacesGroupUuid = currentState.pendingReplacementGroupId?.let(UUID::fromString),
                    now = recordGenerationInstant,
                )
            }
            val saveResult = savedGroupUuidResult.fold(
                onSuccess = { null },
                onFailure = { SaveMedicationGroupResult.FAILURE },
            )
            val isSaved = saveResult == null
            val savedGroupUuid = savedGroupUuidResult.getOrNull()
            val recordGenerationResult = if (isSaved && shouldCreatePastRecords && savedGroupUuid != null) {
                savePastScheduledSlotRecords(
                    groupUuid = savedGroupUuid,
                    now = recordGenerationNow,
                )
            } else {
                null
            }
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
                    createPastScheduledSlotRecordsResult = recordGenerationResult?.result,
                    createdPastScheduledSlotRecordCount = recordGenerationResult?.savedRecordCount,
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
        val currentState = _uiState.value
        if (
            currentState.isEditing &&
            !currentState.isArchived &&
            !currentState.isSaving &&
            !currentState.isDeleting &&
            !currentState.isArchiving &&
            !currentState.isRecreatingAfterArchive &&
            !currentState.isDeletingRelatedEntries
        ) {
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

    fun duplicateArchivedGroup() {
        val currentState = _uiState.value
        if (
            !currentState.isEditing ||
            !currentState.isArchived ||
            currentState.isSaving ||
            currentState.isDeleting ||
            currentState.isArchiving ||
            currentState.isRecreatingAfterArchive ||
            currentState.isDeletingRelatedEntries
        ) {
            return
        }
        val existingGroupCount = latestGroups.size.takeIf { it > 0 } ?: 1
        val usedColors = latestGroups.map(MedicationGroup::colorKey)
            .ifEmpty { listOf(currentState.groupColorKey) }
        val defaultName = defaultMedicationGroupName(
            existingGroupCount = existingGroupCount,
            formatName = { index ->
                context.getString(R.string.default_group_name_format, index)
            }
        )
        val resolvedGroupName = resolveMedicationGroupName(
            groupName = currentState.groupName,
            defaultGroupName = currentState.defaultGroupName,
            isEditing = currentState.isEditing,
        ).ifEmpty { defaultName }
        val colorKey = nextAvailableMedicationGroupColor(
            usedColors = usedColors,
            seed = UUID.randomUUID().hashCode(),
        )
        val duplicateScheduleStartDate = currentMinute.value.toLocalDate()

        _uiState.update {
            currentState.toUnsavedDuplicatedGroupState(
                resolvedGroupName = resolvedGroupName,
                defaultGroupName = defaultName,
                colorKey = colorKey,
                scheduleStartDate = duplicateScheduleStartDate,
            ).copy(
                scrollToTopRequestVersion = it.scrollToTopRequestVersion + 1,
            )
        }
    }

    fun archiveGroup() {
        val currentState = _uiState.value
        if (
            currentState.isArchived ||
            currentState.isSaving ||
            currentState.isDeleting ||
            currentState.isArchiving ||
            currentState.isRecreatingAfterArchive ||
            currentState.isDeletingRelatedEntries
        ) {
            return
        }
        val groupId = currentState.editingGroupId ?: return
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull() ?: return
        val archiveNow = currentMinute.value.toSystemInstant()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isArchiving = true,
                    isArchiveConfirmationVisible = false,
                    archiveMedicationGroupResult = null,
                )
            }

            val archiveResult = runCatching {
                medicationGroupRepository.archiveGroup(uuid, now = archiveNow)
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

    fun archiveAndRecreateGroup() {
        val currentState = _uiState.value
        val groupId = currentState.editingGroupId ?: return
        if (
            currentState.isArchived ||
            currentState.isSaving ||
            currentState.isDeleting ||
            currentState.isArchiving ||
            currentState.isRecreatingAfterArchive ||
            currentState.isDeletingRelatedEntries
        ) {
            return
        }
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull() ?: return
        val recreateNow = currentMinute.value
        val recreateNowInstant = recreateNow.toSystemInstant()
        val recreateScheduleStartDate = recreateNow.toLocalDate()
        val resolvedGroupName = resolveMedicationGroupName(
            groupName = currentState.groupName,
            defaultGroupName = currentState.defaultGroupName,
            isEditing = currentState.isEditing,
        )
        val draftState = currentState.toUnsavedRecreatedGroupState(
            archivedGroupId = groupId,
            scheduleStartDate = recreateScheduleStartDate,
            resolvedGroupName = resolvedGroupName,
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRecreatingAfterArchive = true,
                    isArchiveConfirmationVisible = false,
                    archiveAndRecreateMedicationGroupResult = null,
                )
            }

            val archiveSucceeded = runCatching {
                medicationGroupRepository.archiveGroup(uuid, now = recreateNowInstant)
            }.isSuccess

            if (!archiveSucceeded) {
                _uiState.update {
                    it.copy(
                        isRecreatingAfterArchive = false,
                        archiveAndRecreateMedicationGroupResult =
                            ArchiveAndRecreateMedicationGroupResult.FAILURE,
                    )
                }
                return@launch
            }

            runCatching { medicationReminderScheduler.cancelReminder(uuid) }
            runCatching { medicationReminderScheduler.rescheduleAll() }

            val savedGroupUuid = saveRecreatedDraftGroup(
                draftState = draftState,
                resolvedGroupName = resolvedGroupName,
                replacesGroupUuid = uuid,
                now = recreateNowInstant,
            )
            if (savedGroupUuid != null) {
                runCatching { medicationReminderScheduler.rescheduleGroup(savedGroupUuid) }
            }
            val savedGroup = savedGroupUuid?.let { medicationGroupRepository.getGroup(it) }
            val remindersEnabled = settingsRepository.getCurrentSettings().remindersEnabled

            _uiState.update {
                if (savedGroup != null) {
                    savedGroup.toEditorState(
                        remindersEnabled = remindersEnabled,
                        relatedEntryCount = 0,
                        plannedEntryCount = 0,
                    ).copy(
                        isRecreatingAfterArchive = false,
                        archiveAndRecreateMedicationGroupResult =
                            ArchiveAndRecreateMedicationGroupResult.SUCCESS,
                        scrollToTopRequestVersion = it.scrollToTopRequestVersion + 1,
                    )
                } else {
                    it.copy(
                        isRecreatingAfterArchive = false,
                        archiveAndRecreateMedicationGroupResult =
                            ArchiveAndRecreateMedicationGroupResult.FAILURE,
                    )
                }
            }
        }
    }

    private suspend fun saveRecreatedDraftGroup(
        draftState: MedicationGroupEditorUiState,
        resolvedGroupName: String,
        replacesGroupUuid: UUID,
        now: Instant,
    ): UUID? {
        val parsedWeeklyInterval = parseScheduleInterval(draftState.weeklyIntervalWeeks)
        val parsedDailyInterval = parseScheduleInterval(draftState.dailyIntervalDays)
        val resolvedDailyTimes = draftState.dailyTimes.ifEmpty {
            listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0)))
        }
        val scheduleTimeInputs = scheduleTimeInputsForSave(
            uiState = draftState,
            resolvedDailyTimes = resolvedDailyTimes,
        )
        val scheduleInput = when (draftState.scheduleType) {
            MedicationGroupScheduleType.WEEKLY -> MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = parsedWeeklyInterval,
                since = draftState.sinceDate,
                weeklyDaysOfWeek = draftState.weeklyDaysOfWeek,
                times = scheduleTimeInputs.map(MedicationGroupScheduleTimeInput::time),
                timeSlots = scheduleTimeInputs,
            )
            MedicationGroupScheduleType.DAILY -> MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = parsedDailyInterval,
                since = draftState.sinceDate,
                weeklyDaysOfWeek = emptySet(),
                times = scheduleTimeInputs.map(MedicationGroupScheduleTimeInput::time),
                timeSlots = scheduleTimeInputs,
            )
        }
        return runCatching {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = resolvedGroupName,
                colorKey = draftState.groupColorKey,
                schedule = scheduleInput,
                medications = draftState.medications.map { medication ->
                    MedicationGroupMedicationInput(
                        uuid = medication.persistedMedicationId?.let(UUID::fromString),
                        details = medication.details,
                        count = medication.count,
                    )
                },
                notificationsEnabled = draftState.notificationsEnabled,
                includePastScheduledSlots = draftState.includePastScheduledSlots,
                replacesGroupUuid = replacesGroupUuid,
                now = now,
            )
        }.getOrNull()
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
        _uiState.update {
            it.copy(
                isSaved = false,
                createPastScheduledSlotRecordsResult = null,
                createdPastScheduledSlotRecordCount = null,
            )
        }
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

    private suspend fun savePastScheduledSlotRecords(
        groupUuid: UUID,
        now: LocalDateTime,
    ): CreatePastScheduledSlotRecordsSaveState {
        return runCatching {
            val savedGroup = medicationGroupRepository.getGroup(groupUuid)
                ?: throw NoSuchElementException("Medication group $groupUuid was not found.")
            val entriesToSave = buildPlanBatchAddEntries(
                group = savedGroup,
                existingEntries = medicationLogRepository.getEntries(),
                startDate = savedGroup.schedule.since,
                endDate = now.toLocalDate(),
                now = now,
                zoneId = ZoneId.systemDefault(),
            )
            medicationLogRepository.saveNewEntries(entriesToSave)
            CreatePastScheduledSlotRecordsSaveState(
                result = null,
                savedRecordCount = entriesToSave.size,
            )
        }.getOrElse {
            CreatePastScheduledSlotRecordsSaveState(
                result = CreatePastScheduledSlotRecordsResult.FAILURE,
                savedRecordCount = null,
            )
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
    val normalizedScheduleTimeSlots = schedule.timeSlots
        .ifEmpty {
            listOf(
                MedicationGroupScheduleTime(
                    uuid = UUID.randomUUID(),
                    time = LocalTime.of(9, 0),
                    effectiveFrom = schedule.since.atStartOfDay(),
                )
            )
        }
        .sortedBy { slot -> slot.time }
    val normalizedScheduleTimes = normalizedScheduleTimeSlots.map { slot -> slot.time }
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
        weeklyTimeLocalId = if (schedule.type == MedicationGroupScheduleType.WEEKLY) {
            normalizedScheduleTimeSlots.firstOrNull()?.uuid?.toString() ?: UUID.randomUUID().toString()
        } else {
            UUID.randomUUID().toString()
        },
        weeklyTime = schedule.times.firstOrNull() ?: LocalTime.of(9, 0),
        dailyIntervalDays = if (schedule.type == MedicationGroupScheduleType.DAILY) {
            parseScheduleInterval(schedule.interval.toString()).toString()
        } else {
            "1"
        },
        dailyTimes = if (schedule.type == MedicationGroupScheduleType.DAILY) {
            normalizedScheduleTimeSlots.map { slot ->
                MedicationGroupScheduleTimeUiState(
                    localId = slot.uuid.toString(),
                    time = slot.time,
                    originalTime = slot.time,
                )
            }
        } else {
            listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0)))
        },
        remindersEnabled = remindersEnabled,
        notificationsEnabled = notificationsEnabled,
        hasResolvedNotificationDefault = true,
        includePastScheduledSlots = includePastScheduledSlots,
        isScheduleStartDateLocked = recreatedFromGroupUuid != null,
        recreatedFromGroupId = recreatedFromGroupUuid?.toString(),
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
    return toUnsavedCopiedGroupState(
        resolvedGroupName = resolvedGroupName,
        defaultGroupName = resolvedGroupName,
        colorKey = groupColorKey,
        includePastScheduledSlots = false,
        isScheduleStartDateLocked = true,
        pendingReplacementGroupId = archivedGroupId,
        notificationsEnabled = notificationsEnabled,
    ).copy(
        sinceDate = scheduleStartDate,
    )
}

private fun MedicationGroupEditorUiState.toUnsavedDuplicatedGroupState(
    resolvedGroupName: String,
    defaultGroupName: String,
    colorKey: MedicationGroupColorKey,
    scheduleStartDate: LocalDate,
): MedicationGroupEditorUiState {
    return toUnsavedCopiedGroupState(
        resolvedGroupName = resolvedGroupName,
        defaultGroupName = defaultGroupName,
        colorKey = colorKey,
        includePastScheduledSlots = true,
        isScheduleStartDateLocked = false,
        pendingReplacementGroupId = null,
        notificationsEnabled = notificationsEnabled,
    ).copy(
        sinceDate = scheduleStartDate,
    )
}

private fun MedicationGroupEditorUiState.toUnsavedCopiedGroupState(
    resolvedGroupName: String,
    defaultGroupName: String,
    colorKey: MedicationGroupColorKey,
    includePastScheduledSlots: Boolean,
    isScheduleStartDateLocked: Boolean,
    pendingReplacementGroupId: String?,
    notificationsEnabled: Boolean,
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
        defaultGroupName = defaultGroupName,
        hasResolvedInitialGroupName = true,
        weeklyTimeLocalId = UUID.randomUUID().toString(),
        dailyTimes = dailyTimes.map { dailyTime ->
            MedicationGroupScheduleTimeUiState(time = dailyTime.time)
        },
        medications = copiedMedications,
        remindersEnabled = remindersEnabled,
        notificationsEnabled = notificationsEnabled,
        hasResolvedNotificationDefault = true,
        groupColorKey = colorKey,
        hasAssignedGroupColor = true,
        editingMedication = null,
        isMedicationEditorSaved = false,
        medicationEditorErrorMessageRes = null,
        medicationEditorInfoMessageRes = null,
        isSaving = false,
        isDeleting = false,
        isArchiving = false,
        isDeletingRelatedEntries = false,
        isLoadingGroupForEditing = false,
        isSaved = false,
        isDeleted = false,
        isArchived = false,
        includePastScheduledSlots = includePastScheduledSlots,
        createPastScheduledSlotRecords = false,
        isScheduleStartDateLocked = isScheduleStartDateLocked,
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
        isDeleteConfirmationVisible = false,
        isDeleteRelatedEntriesConfirmationVisible = false,
        pendingReplacementGroupId = pendingReplacementGroupId,
        recreatedFromGroupId = pendingReplacementGroupId,
        archiveMedicationGroupResult = null,
        archiveAndRecreateMedicationGroupResult = null,
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
    return sortDailyTimesByOriginalTime(
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
    return sortDailyTimesByOriginalTime(
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

internal fun sortDailyTimesByOriginalTime(
    dailyTimes: List<MedicationGroupScheduleTimeUiState>,
): List<MedicationGroupScheduleTimeUiState> {
    return dailyTimes.sortedWith(
        compareBy<MedicationGroupScheduleTimeUiState> { dailyTime ->
            dailyTime.originalTime ?: dailyTime.time
        }.thenBy { dailyTime ->
            dailyTime.time
        }.thenBy { dailyTime ->
            dailyTime.localId
        }
    )
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
    val duplicateAlreadyExists: Boolean,
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
        return MedicationGroupMedicationSaveResult(
            medications = medications,
            resolvedMedication = savedMedication,
            duplicateAlreadyExists = true
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
        duplicateAlreadyExists = false
    )
}

private data class CreatePastScheduledSlotRecordsSaveState(
    val result: CreatePastScheduledSlotRecordsResult?,
    val savedRecordCount: Int?,
)

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
    val weeklyTimeLocalId: String = UUID.randomUUID().toString(),
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
    val isRecreatingAfterArchive: Boolean = false,
    val isDeletingRelatedEntries: Boolean = false,
    val isLoadingGroupForEditing: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isArchived: Boolean = false,
    val includePastScheduledSlots: Boolean = true,
    val createPastScheduledSlotRecords: Boolean = false,
    val isScheduleStartDateLocked: Boolean = false,
    val saveMedicationGroupResult: SaveMedicationGroupResult? = null,
    val createPastScheduledSlotRecordsResult: CreatePastScheduledSlotRecordsResult? = null,
    val createdPastScheduledSlotRecordCount: Int? = null,
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
    val isDeleteConfirmationVisible: Boolean = false,
    val isDeleteRelatedEntriesConfirmationVisible: Boolean = false,
    val pendingReplacementGroupId: String? = null,
    val recreatedFromGroupId: String? = null,
    val archiveMedicationGroupResult: ArchiveMedicationGroupResult? = null,
    val archiveAndRecreateMedicationGroupResult: ArchiveAndRecreateMedicationGroupResult? = null,
    val deleteRelatedEntriesResult: DeleteRelatedEntriesResult? = null,
    val deleteMedicationGroupResult: DeleteMedicationGroupResult? = null,
    val scrollToTopRequestVersion: Int = 0,
) {
    val isEditing: Boolean
        get() = editingGroupId != null

    val isLocked: Boolean
        get() = isEditing && !isArchived && plannedEntryCount > 0

    val areScheduleShapeFieldsLocked: Boolean
        get() = isLocked || isArchived

    val areMedicationsLocked: Boolean
        get() = isLocked || isArchived

    val canEditBackfillOption: Boolean
        get() = !isArchived &&
            pendingReplacementGroupId == null &&
            (
                !isEditing ||
                    (
                        recreatedFromGroupId == null &&
                            relatedEntryCount == 0 &&
                            plannedEntryCount == 0
                    )
            )

    val canCreatePastScheduledSlotRecords: Boolean
        get() = !isEditing &&
            includePastScheduledSlots &&
            canEditBackfillOption
}

enum class CreatePastScheduledSlotRecordsResult {
    FAILURE,
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

private fun LocalDateTime.toSystemInstant(): Instant =
    atZone(ZoneId.systemDefault()).toInstant()

internal fun scheduleTimeInputsForSave(
    uiState: MedicationGroupEditorUiState,
    resolvedDailyTimes: List<MedicationGroupScheduleTimeUiState> = uiState.dailyTimes,
): List<MedicationGroupScheduleTimeInput> {
    return when (uiState.scheduleType) {
        MedicationGroupScheduleType.WEEKLY -> listOf(
            MedicationGroupScheduleTimeInput(
                uuid = uiState.weeklyTimeLocalId.toUuidOrNull(),
                time = uiState.weeklyTime.withSecond(0).withNano(0),
            )
        )

        MedicationGroupScheduleType.DAILY -> resolvedDailyTimes
            .ifEmpty { listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0))) }
            .sortedBy(MedicationGroupScheduleTimeUiState::time)
            .map { dailyTime ->
                MedicationGroupScheduleTimeInput(
                    uuid = dailyTime.localId.toUuidOrNull(),
                    time = dailyTime.time.withSecond(0).withNano(0),
                )
            }
    }
}

internal fun scheduleTimesForSave(uiState: MedicationGroupEditorUiState): List<LocalTime> {
    return when (uiState.scheduleType) {
        MedicationGroupScheduleType.WEEKLY -> listOf(uiState.weeklyTime.withSecond(0).withNano(0))
        MedicationGroupScheduleType.DAILY -> uiState.dailyTimes
            .ifEmpty { listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0))) }
            .map { dailyTime -> dailyTime.time.withSecond(0).withNano(0) }
    }
}

private fun String.toUuidOrNull(): UUID? {
    return runCatching { UUID.fromString(this) }.getOrNull()
}

internal fun lockedScheduleTimesChanged(uiState: MedicationGroupEditorUiState): Boolean {
    val originalTimes = when (uiState.originalScheduleType) {
        MedicationGroupScheduleType.WEEKLY -> listOf(uiState.originalWeeklyTime)
        MedicationGroupScheduleType.DAILY -> uiState.originalDailyTimes
        null -> emptyList()
    }
    return scheduleTimesForSave(uiState) != originalTimes
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
    val originalTime: LocalTime? = null,
)
