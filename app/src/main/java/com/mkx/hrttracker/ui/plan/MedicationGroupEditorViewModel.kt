package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.UnsavedChangesState
import com.mkx.hrttracker.data.repository.CurrentOrFuturePlannedSlotsBlockArchiveException
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleInput
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleTimeInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicationGroupSaveRefusedException
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SaveRefusal
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.saveRefusal
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.nextAvailableMedicationGroupColor
import com.mkx.hrttracker.model.medication.planCalendarDate
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.ui.catalog.DraftGroupReferences
import com.mkx.hrttracker.ui.medication.DoseInstructionDraftUiState
import com.mkx.hrttracker.ui.medication.MedicationDoseDraft
import com.mkx.hrttracker.ui.medication.MedicinePickerUiState
import com.mkx.hrttracker.ui.medication.applyMedicinePicker
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import com.mkx.hrttracker.ui.medication.inferredOrSelectedPreparationType
import com.mkx.hrttracker.ui.medication.medicationCountValidationErrorRes
import com.mkx.hrttracker.ui.medication.normalizeMedicationCount
import com.mkx.hrttracker.ui.medication.parseMedicationCountText
import com.mkx.hrttracker.ui.medication.requiresCustomName
import com.mkx.hrttracker.ui.medication.resolvedApplicationTypeForDose
import com.mkx.hrttracker.ui.medication.resolvedMedicationCountForSave
import com.mkx.hrttracker.ui.medication.selectedMedicineValidationErrorRes
import com.mkx.hrttracker.ui.medication.stepMedicationCount
import com.mkx.hrttracker.ui.medication.toDoseInstruction
import com.mkx.hrttracker.ui.medication.toDoseInstructionDraft
import com.mkx.hrttracker.ui.medication.validationErrorRes
import com.mkx.hrttracker.ui.medication.withCountText
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.timeOfDayOrder
import com.mkx.hrttracker.util.medicationDay
import com.mkx.hrttracker.util.medicationDayStart
import com.mkx.hrttracker.util.systemLocale
import com.mkx.hrttracker.util.withAppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
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
    private val medicineRepository: MedicineRepository,
    private val settingsRepository: SettingsRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val appTimeSource: AppTimeSource,
    unsavedChanges: UnsavedChangesState = UnsavedChangesState(),
) : ViewModel() {
    private val savedStateHandle: SavedStateHandle = savedStateHandle

    // Set while duplicating an archived group (an unsaved new-group draft). Persisted so
    // process death can re-derive the duplicate from its source rather than falling back
    // to the archived original named by the launch route arg.
    private val restoredDuplicateSourceGroupId =
        savedStateHandle.get<String>(DUPLICATE_SOURCE_GROUP_ID_KEY)

    // The launch route argument names the group the editor was opened for. A saved copy
    // becomes the edited group while the route arg still names the archived original, so
    // prefer the mirrored edited id (written in init) after process death. A restored
    // duplicate draft is a brand-new group, so don't reload a persisted group by id —
    // it is re-derived from its source in init instead.
    private val requestedEditingGroupId =
        if (restoredDuplicateSourceGroupId != null) {
            null
        } else {
            savedStateHandle.get<String>(EDITED_GROUP_ID_KEY)
                ?: savedStateHandle.get<String>(GROUP_ID_ARG)
        }
    private val editingGroupUuid = requestedEditingGroupId?.let { groupId ->
        runCatching { UUID.fromString(groupId) }.getOrNull()
    }
    private val editingGroupId = editingGroupUuid?.toString()
    private val pendingGroupUuid = editingGroupUuid ?: UUID.randomUUID()
    private val cachedEditingGroup =
        editingGroupUuid?.let(medicationGroupRepository::getCachedGroup)
    private val defaultScheduleDateTime =
        nextMedicationGroupEditorDefaultDateTime(appTimeSource.currentMinute.value)

    private var latestGroups: List<MedicationGroup> = emptyList()

    // The edited plan as saved, which saveRefusal judges the draft against.
    private var savedPlan: MedicationGroup? = cachedEditingGroup

    // Its planned records as last seen, which a retime moves with their slots.
    private var plannedRecords: List<MedicationGroupSlotKey> = emptyList()
    private val _uiState = MutableStateFlow(
        run {
            // A new plan's day ends at midnight until the user turns night owl on.
            val defaultDay = defaultScheduleDateTime.toLocalDate()
            val baseState = (
                cachedEditingGroup?.toEditorState(
                    remindersEnabled = settingsRepository.settingsState.value.remindersEnabled,
                    relatedEntryCount = 0,
                    plannedEntryCount = 0,
                ) ?: MedicationGroupEditorUiState(
                    editingGroupId = editingGroupId,
                    isLoadingGroupForEditing = editingGroupUuid != null,
                    sinceDate = defaultDay,
                    // Starts now unless the default dose time already rolled the
                    // start date over to the next day.
                    scheduleStart = if (defaultDay == appTimeSource.currentMinute.value.toLocalDate()) {
                        ScheduleStart.Now
                    } else {
                        ScheduleStart.WholeDay
                    },
                    weeklyDaysOfWeek = setOf(defaultDay.dayOfWeek),
                    weeklyTime = defaultScheduleDateTime.toLocalTime(),
                    dailyTimes = listOf(
                        MedicationGroupScheduleTimeUiState(time = defaultScheduleDateTime.toLocalTime())
                    )
                )
            )
            if (restoredDuplicateSourceGroupId != null) {
                baseState.copy(
                    duplicateSourceGroupId = restoredDuplicateSourceGroupId,
                    isLoadingGroupForEditing = true,
                )
            } else {
                baseState
            }
        }
    )
    val uiState: StateFlow<MedicationGroupEditorUiState> = _uiState.asStateFlow()

    // The plan as it first settled, against which leaving asks to discard. Not persisted: the
    // edits themselves aren't kept across process death, so a restored editor has none to lose.
    private var baselineDraft: MedicationGroupEditedDraft? = null

    /** Whether leaving would lose the user's edits to the plan, before it is saved. */
    val hasUnsavedChanges: StateFlow<Boolean> = _uiState
        .map { state ->
            if (!state.hasSettledForEditing) return@map false
            val baseline = baselineDraft ?: state.toEditedDraft().also { baselineDraft = it }
            val draft = state.toEditedDraft(weekdaysFollowStart = baseline.weekdaysFollowStart)
            !state.isSaved &&
                    !state.isDeleted &&
                    !state.isFinishingAfterSave &&
                    !state.isFinishingAfterDeleteOrArchive &&
                    // The archive shortcut's copy was asked for: leaving drops it, so it asks.
                    (draft != baseline || state.duplicateStartsAtCutoff)
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        unsavedChanges.track(this, hasUnsavedChanges)
    }

    private val _completionEvents =
        MutableSharedFlow<MedicationGroupEditorCompletionEvent>(extraBufferCapacity = 1)
    val completionEvents: SharedFlow<MedicationGroupEditorCompletionEvent> =
        _completionEvents.asSharedFlow()
    val currentMinute: StateFlow<LocalDateTime> = appTimeSource.currentMinute

    init {
        viewModelScope.launch {
            // Mirror the edited group id. A saved copy moves it to the new plan while the
            // launch route arg still names the archived original, so this is what lets
            // process death restore the saved copy instead of the original.
            _uiState
                .map { it.editingGroupId }
                .distinctUntilChanged()
                .collect { value ->
                    this@MedicationGroupEditorViewModel.savedStateHandle[
                        EDITED_GROUP_ID_KEY
                    ] = value
                }
        }

        viewModelScope.launch {
            // Mirror the duplicate-source lineage so process death can re-derive an
            // unsaved duplicate-of-archived draft instead of reloading the source.
            _uiState
                .map { it.duplicateSourceGroupId }
                .distinctUntilChanged()
                .collect { value ->
                    this@MedicationGroupEditorViewModel.savedStateHandle[
                        DUPLICATE_SOURCE_GROUP_ID_KEY
                    ] = value
                }
        }

        viewModelScope.launch {
            medicineRepository.observeAllActive().collect { medicines ->
                _uiState.update { current -> current.copy(activeMedicines = medicines) }
            }
        }

        viewModelScope.launch {
            settingsRepository.settingsState.collect { settingsState ->
                val resolvedFirstDayOfWeek = settingsState.firstDayOfWeekOption
                    .resolve(systemLocale())
                _uiState.update { currentState ->
                    applyReminderSettingsToEditorState(
                        currentState = currentState,
                        remindersEnabled = settingsState.remindersEnabled
                    ).copy(firstDayOfWeek = resolvedFirstDayOfWeek)
                }
            }
        }

        viewModelScope.launch {
            medicationGroupRepository.observeGroups().collect { groupsOrNull ->
                _uiState.update { currentState ->
                    val allGroups = groupsOrNull.orEmpty()
                    latestGroups = allGroups
                    allGroups.firstOrNull { group -> group.uuid.toString() == currentState.editingGroupId }
                        ?.let { group -> savedPlan = group }
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
                    currentState.copy(
                        groupColorKey = resolvedGroupColorKey,
                        hasAssignedGroupColor = true,
                    )
                }
            }
        }

        if (editingGroupUuid == null && restoredDuplicateSourceGroupId == null) {
            viewModelScope.launch {
                val index = settingsRepository.peekNextGroupNameIndex()
                _uiState.update { currentState ->
                    applyDefaultGroupNameToEditorState(
                        currentState = currentState,
                        defaultGroupName = context.withAppLanguage().getString(
                            R.string.default_group_name_format, index
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                medicationLogRepository.observeEntries(),
                // The saved plan's ending, as the repository archives with, not an
                // unsaved toggle.
                _uiState
                    .map { it.editingGroupId to it.originalMedicationDayBoundary }
                    .distinctUntilChanged(),
                appTimeSource.currentSnapshot,
            ) { entriesOrNull, (currentEditingGroupId, boundary), timeSnapshot ->
                Triple(
                    entryCountsForGroup(
                        entries = entriesOrNull.orEmpty(),
                        groupId = currentEditingGroupId,
                        currentMinute = timeSnapshot.minute,
                        boundary = boundary,
                        zoneId = timeSnapshot.zone,
                        areEntriesLoaded = entriesOrNull != null,
                    ),
                    timeSnapshot,
                    boundary,
                )
            }
                .collect { (counts, timeSnapshot, boundary) ->
                    plannedRecords = counts.plannedRecords
                    _uiState.update {
                        val archiveDateWindow = resolveArchiveDateWindow(
                            scheduleSince = it.originalSinceDate,
                            latestRecordedDoseDate = counts.latestRecordedDoseDate,
                            areEntriesLoaded = counts.areEntriesLoaded,
                            today = medicationDay(timeSnapshot.minute, boundary),
                        )
                        val nextState = it.copy(
                            relatedEntryCount = counts.relatedEntryCount,
                            plannedEntryCount = counts.plannedEntryCount,
                            currentOrFuturePlannedSlotCount =
                                counts.currentOrFuturePlannedSlotCount,
                            latestRecordedDoseDate = counts.latestRecordedDoseDate,
                            areEntriesLoaded = counts.areEntriesLoaded,
                            archiveDateWindow = archiveDateWindow,
                            scheduleTimeOrderError = if (counts.plannedEntryCount == 0) {
                                false
                            } else {
                                it.scheduleTimeOrderError
                            },
                        )
                        // A record logged meanwhile reverts nothing: saveRefusal refuses a draft
                        // it conflicts with at Save.
                        resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
                            previousState = it,
                            nextState = nextState.withStartsNowOn(timeSnapshot.minute),
                        )
                    }
                }
        }

        if (editingGroupUuid != null && cachedEditingGroup == null) {
            loadGroupForEditing(editingGroupUuid)
        }

        if (restoredDuplicateSourceGroupId != null) {
            recreateDuplicateDraftFromSource(
                sourceGroupId = restoredDuplicateSourceGroupId,
                startsAtCutoff = savedStateHandle.get<Boolean>(DUPLICATE_STARTS_AT_CUTOFF_KEY) == true,
            )
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

    fun updateGroupColor(colorKey: MedicationGroupColorKey) {
        _uiState.update { current ->
            if (current.isArchived || current.groupColorKey == colorKey) {
                current
            } else {
                current.copy(
                    groupColorKey = colorKey,
                    hasAssignedGroupColor = true,
                )
            }
        }
    }

    fun updateScheduleType(scheduleType: MedicationGroupScheduleType) {
        _uiState.update {
            if (it.areScheduleShapeFieldsLocked) {
                it
            } else {
                editorStateWithUpdatedScheduleType(it, scheduleType)
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
                it.copy(
                    weeklyDaysOfWeek = toggleWeeklyDaySelection(it.scheduleWeekdays(currentMinute.value), dayOfWeek),
                    hasUserCustomizedWeeklyDays = true,
                )
            }
        }
    }

    fun resetWeeklyDaysOfWeekToDefault() {
        _uiState.update {
            if (it.areScheduleShapeFieldsLocked) {
                it
            } else {
                editorStateWithResetWeeklyDaysOfWeek(it)
            }
        }
    }

    /** Sets the weekly time, unless saveRefusal refuses it: then the draft stays, and that reason is returned. */
    fun updateWeeklyTime(time: LocalTime): SaveRefusal? = retime { state ->
        state.copy(weeklyTime = time.withSecond(0).withNano(0), scheduleTimeOrderError = false)
    }

    fun updateSinceDate(date: LocalDate) {
        _uiState.update { editorStateWithUpdatedSinceDate(uiState = it, date = date) }
    }

    // The start stays as chosen: its moment follows the new day ending (see scheduleStartAt).
    fun updateDayEndsAtFourAm(enabled: Boolean) {
        _uiState.update {
            if (!it.canEditDayBoundary) {
                it
            } else {
                it.copy(medicationDayBoundary = MedicationDayBoundary.fromSwitch(enabled))
            }
        }
    }

    // A calendar date and time: 11:00 picked at 00:30 is 11:00 that day, night owl or not. A saved
    // start past its first day (a plan that hid its past) shows on its own date's row and keeps
    // counting from that first day, so the time moves on that row's date.
    fun updateStartTime(time: LocalTime) {
        _uiState.update {
            val start = it.scheduleStart
            when {
                !it.canEditScheduleStart -> it
                start is ScheduleStart.Loaded && it.sinceDate != start.at.toLocalDate() ->
                    it.copy(scheduleStart = start.copy(at = start.at.toLocalDate().atTime(time)))
                else -> it.copy(scheduleStart = ScheduleStart.At(time))
            }
        }
    }

    fun resetStartToNow() {
        _uiState.update {
            if (!it.canEditScheduleStart) {
                it
            } else {
                editorStateStartingNow(uiState = it, today = currentMinute.value.toLocalDate())
            }
        }
    }

    fun updateIncludePastScheduledSlots(includePastScheduledSlots: Boolean) {
        _uiState.update {
            if (!it.canEditBackfillOption) {
                it
            } else {
                // createPastScheduledSlotRecords is deliberately left untouched
                // when past slots are hidden: it is the ViewModel-owned memory of
                // the generate-records checkbox, so re-enabling "Show past"
                // restores the user's previous choice. Effective generation is
                // derived at the consumers via canCreatePastScheduledSlotRecords,
                // which requires includePastScheduledSlots.
                it.withCreatePastScheduledSlotRecordsEnabledAt(
                    includePastScheduledSlots = includePastScheduledSlots,
                    createPastScheduledSlotRecords = it.createPastScheduledSlotRecords,
                    enabledAt = currentMinute.value,
                )
            }
        }
    }

    fun updateCreatePastScheduledSlotRecords(createPastScheduledSlotRecords: Boolean) {
        _uiState.update {
            if (!it.canCreatePastScheduledSlotRecords) {
                it
            } else {
                it.withCreatePastScheduledSlotRecordsEnabledAt(
                    includePastScheduledSlots = it.includePastScheduledSlots,
                    createPastScheduledSlotRecords = createPastScheduledSlotRecords,
                    enabledAt = currentMinute.value,
                )
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
            // Startup reconciles if the process dies before the reschedule; it outlives this editor.
            val reconcileMark = markReconcilePendingBestEffort()
            try {
                settingsRepository.setRemindersEnabled(enabled)
            } finally {
                reconcileMark?.committed()
            }
            withContext(NonCancellable) { medicationReminderScheduler.rescheduleAll() }
        }
    }

    /**
     * Null when the mark can't be stored, and the write goes ahead without it. A cancelled
     * caller stops here instead: its mark may be down, and a write it then commits would
     * have no mark to release. A caller still active got the store's own failure.
     */
    private suspend fun markReconcilePendingBestEffort(): MedicationReminderScheduler.ReconcileMark? =
        try {
            medicationReminderScheduler.markReconcilePending()
        } catch (_: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        } catch (_: Exception) {
            null
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

    /** Sets a daily time, unless saveRefusal refuses it: then the draft stays, and that reason is returned. */
    fun updateDailyTime(localId: String, time: LocalTime): SaveRefusal? = retime { state ->
        state.copy(
            // Clock order on purpose, whatever the plan's ending: lockedScheduleTimesChanged
            // compares these in order with originalDailyTimes.
            dailyTimes = sortDailyTimesByOriginalTime(
                dailyTimesWithUpdatedTimeInCurrentOrder(
                    dailyTimes = state.dailyTimes,
                    localId = localId,
                    time = time,
                ),
                MedicationDayBoundary.Midnight,
            ),
            scheduleTimeOrderError = false,
        )
    }

    // A locked plan's records move with their slot, so its time picker asks saveRefusal first, as
    // Save will: a time that makes the draft refused keeps the picker open with the reason. A draft
    // already refused (a record logged meanwhile) takes any time, and Save says why.
    private fun retime(change: (MedicationGroupEditorUiState) -> MedicationGroupEditorUiState): SaveRefusal? {
        var refusal: SaveRefusal? = null
        _uiState.update { state ->
            if (state.isArchived) return@update state
            val retimed = change(state)
            refusal = retimed.saveRefusal()?.takeIf { state.saveRefusal() == null }
            if (refusal == null) retimed else state
        }
        return refusal
    }

    /** Why saving this draft would be refused, from the records and the plan as last seen. */
    private fun MedicationGroupEditorUiState.saveRefusal(): SaveRefusal? = saveRefusal(
        isEditing = isEditing,
        saved = savedPlan?.takeIf { plan -> plan.uuid.toString() == editingGroupId },
        schedule = toScheduleInput(currentMinute.value),
        medications = medications.map { medication -> medication.toInput() },
        hasRecords = relatedEntryCount > 0,
        records = plannedRecords,
    )

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
                    applicationType = medication.resolvedApplicationType(),
                    countText = medication.countText,
                    delta = -1,
                    preparationType = medication.resolvedPreparationType(),
                ).toString()
            )
        }
    }

    fun increaseEditingMedicationCount() {
        updateEditingMedication { medication ->
            medication.copy(
                countText = stepMedicationCount(
                    applicationType = medication.resolvedApplicationType(),
                    countText = medication.countText,
                    delta = 1,
                    preparationType = medication.resolvedPreparationType(),
                ).toString()
            )
        }
    }

    fun updateEditingMedicationCountText(countText: String) {
        updateEditingMedication { medication ->
            val reduced = MedicationDoseDraft(
                medicineDraft = medication.medicineDraft,
                doseInstructionDraft = medication.doseInstructionDraft,
                countText = medication.countText,
                resolvedMedicine = medication.resolvedMedicine,
            ).withCountText(countText)
            medication.copy(countText = reduced.countText)
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

    fun updateEditingMedicineDraft(
        transform: (MedicinePickerUiState) -> MedicinePickerUiState
    ) {
        updateEditingMedication { medication ->
            val reduced = MedicationDoseDraft(
                medicineDraft = medication.medicineDraft,
                doseInstructionDraft = medication.doseInstructionDraft,
                countText = medication.countText,
                resolvedMedicine = medication.resolvedMedicine,
            ).applyMedicinePicker(transform)
            medication.copy(
                medicineDraft = reduced.medicineDraft,
                resolvedMedicine = reduced.resolvedMedicine,
                doseInstructionDraft = reduced.doseInstructionDraft,
                countText = reduced.countText,
            )
        }
    }

    fun updateEditingDoseInstructionDraft(
        transform: (DoseInstructionDraftUiState) -> DoseInstructionDraftUiState
    ) {
        updateEditingMedication { medication ->
            medication.copy(doseInstructionDraft = transform(medication.doseInstructionDraft))
        }
    }

    fun selectEditingExistingMedicine(medicineUuid: UUID) {
        val localId = _uiState.value.editingMedication?.localId ?: return
        onEditingMedicineSelected(localId = localId, medicineUuid = medicineUuid)
    }

    fun onEditingMedicineSelected(localId: String, medicineUuid: UUID) {
        val currentState = _uiState.value
        val editingMedication = currentState.editingMedication ?: return
        if (
            editingMedication.localId != localId ||
            !currentState.canEditEditingMedicationIdentity(editingMedication)
        ) {
            return
        }
        val activeMedicine = currentState.activeMedicines.firstOrNull { it.uuid == medicineUuid }
        if (activeMedicine != null) {
            applySelectedEditingMedicine(localId = localId, medicine = activeMedicine)
            return
        }
        viewModelScope.launch {
            medicineRepository.getByUuid(medicineUuid)?.let { medicine ->
                applySelectedEditingMedicine(localId = localId, medicine = medicine)
            }
        }
    }

    // Manager-hosted dose sheet returns a completed slot; append it directly
    // without opening another sheet on this side. localId is the host-generated
    // slot id from the "+ Add medication" tap.
    fun addCompletedMedicationSlot(
        localId: String,
        slot: com.mkx.hrttracker.ui.catalog.MedicineSlotResult,
    ) {
        val currentState = _uiState.value
        if (currentState.areMedicationsLocked) return
        if (currentState.medications.any { it.localId == localId }) return

        val activeMedicine =
            currentState.activeMedicines.firstOrNull { it.uuid == slot.medicineUuid }
        if (activeMedicine != null) {
            applyCompletedMedicationSlot(localId, activeMedicine, slot)
            return
        }
        viewModelScope.launch {
            medicineRepository.getByUuid(slot.medicineUuid)?.let { medicine ->
                applyCompletedMedicationSlot(localId, medicine, slot)
            }
        }
    }

    private fun applyCompletedMedicationSlot(
        localId: String,
        medicine: Medicine,
        slot: com.mkx.hrttracker.ui.catalog.MedicineSlotResult,
    ) {
        _uiState.update { state ->
            if (state.areMedicationsLocked) return@update state
            if (state.medications.any { it.localId == localId }) return@update state

            val draft = com.mkx.hrttracker.ui.medication.medicineDraftFromMedicine(
                medicine = medicine,
                applicationType = slot.applicationType,
            )
            val newItem = MedicationGroupMedicationItemUiState(
                localId = localId,
                persistedMedicationId = null,
                resolvedMedicine = medicine,
                medicineDraft = draft,
                // Back-project the slot's dose instruction onto the draft so a
                // later edit reopens the form with the picked values instead of
                // empty defaults. Mirrors toItemUiState() for persisted items.
                doseInstructionDraft = com.mkx.hrttracker.ui.medication
                    .doseInstructionDraftFromInstruction(
                        applicationType = slot.applicationType,
                        preparationType = medicine.preparation.type,
                        doseInstruction = slot.doseInstruction,
                    ),
                applicationType = slot.applicationType,
                doseInstruction = slot.doseInstruction,
                count = normalizeMedicationCount(
                    applicationType = slot.applicationType,
                    count = slot.count,
                    preparationType = medicine.preparation.type,
                ),
            )
            val upsertResult = upsertMedication(
                medications = state.medications,
                savedMedication = newItem,
            )
            if (upsertResult.duplicateAlreadyExists) {
                state.copy(
                    medicationEditorInfoMessageRes = R.string.group_medication_duplicate_exists,
                )
            } else {
                state.copy(medications = upsertResult.medications)
            }
        }
    }

    // Picker-first add flow: opens the slot editor with a freshly-resolved medicine
    // already populated. Used when the user taps "+ Add medication", picks a
    // medicine in the manager, and lands back here — the sheet must open
    // pre-filled rather than starting from an empty draft.
    fun beginAddMedicationWithMedicine(localId: String, medicineUuid: UUID) {
        val currentState = _uiState.value
        if (currentState.areMedicationsLocked) return
        if (currentState.medications.any { it.localId == localId }) return
        if (currentState.editingMedication != null) return

        val activeMedicine = currentState.activeMedicines.firstOrNull { it.uuid == medicineUuid }
        if (activeMedicine != null) {
            openAddMedicationEditorWithMedicine(localId, activeMedicine)
            return
        }
        viewModelScope.launch {
            medicineRepository.getByUuid(medicineUuid)?.let { medicine ->
                if (_uiState.value.editingMedication != null) return@let
                openAddMedicationEditorWithMedicine(localId, medicine)
            }
        }
    }

    private fun openAddMedicationEditorWithMedicine(localId: String, medicine: Medicine) {
        _uiState.update { state ->
            if (state.areMedicationsLocked) return@update state
            if (state.medications.any { it.localId == localId }) return@update state
            if (state.editingMedication != null) return@update state

            val draft = com.mkx.hrttracker.ui.medication.medicineDraftFromMedicine(
                medicine = medicine,
                applicationType = MedicationApplicationType.ORAL,
            )
            state.copy(
                editingMedication = MedicationGroupMedicationEditorUiState(
                    localId = localId,
                    resolvedMedicine = medicine,
                    medicineDraft = draft,
                    doseInstructionDraft = DoseInstructionDraftUiState(
                        applicationType = draft.catalogFilterApplicationType,
                        preparationType = medicine.preparation.type,
                    ),
                ),
                isMedicationEditorSaved = false,
                medicationEditorErrorMessageRes = null,
                medicationEditorInfoMessageRes = null,
            )
        }
    }

    private fun applySelectedEditingMedicine(localId: String, medicine: Medicine) {
        updateEditingMedication { medication ->
            if (medication.localId != localId) {
                return@updateEditingMedication medication
            }
            val updatedDraft = com.mkx.hrttracker.ui.medication.medicineDraftFromMedicine(
                medicine = medicine,
                applicationType = medication.resolvedApplicationType(),
            )
            val previousPreparationType = medication.doseInstructionDraft.preparationType
            val updatedDoseDraft = if (previousPreparationType == medicine.preparation.type) {
                medication.doseInstructionDraft.copy(preparationType = medicine.preparation.type)
            } else {
                updatedDraft.toDoseInstructionDraft()
                    .copy(preparationType = medicine.preparation.type)
            }
            medication.copy(
                resolvedMedicine = medicine,
                medicineDraft = updatedDraft,
                doseInstructionDraft = updatedDoseDraft,
            )
        }
    }

    private fun MedicationGroupEditorUiState.canEditEditingMedicationIdentity(
        medication: MedicationGroupMedicationEditorUiState,
    ): Boolean {
        return !areMedicationsLocked &&
                medications.none { existingMedication -> existingMedication.localId == medication.localId }
    }

    fun saveEditingMedication() {
        val currentState = _uiState.value
        if (currentState.areMedicationsLocked || currentState.isMedicationEditorSaved) {
            return
        }
        val editingMedication = currentState.editingMedication ?: return
        val applicationType = editingMedication.resolvedApplicationType()
        val preparationType = editingMedication.resolvedPreparationType()
        val isPatchOff = applicationType == MedicationApplicationType.PATCH_OFF
        val errorRes = editingMedication.medicineDraft.selectedMedicineValidationErrorRes()
            ?: editingMedication.doseInstructionDraft.validationErrorRes()
            ?: medicationCountValidationErrorRes(
                applicationType = applicationType,
                countText = editingMedication.countText,
                preparationType = preparationType,
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

        // Identity is resolved into a Medicine lazily at saveGroup; the slot draft
        // keeps the picker/dose drafts so the picker can be reopened for editing.
        val savedMedication = MedicationGroupMedicationItemUiState(
            localId = editingMedication.localId,
            persistedMedicationId = editingMedication.persistedMedicationId,
            resolvedMedicine = editingMedication.resolvedMedicine,
            medicineDraft = editingMedication.medicineDraft,
            doseInstructionDraft = editingMedication.doseInstructionDraft,
            applicationType = applicationType,
            doseInstruction = if (isPatchOff) {
                DoseInstruction.Noop
            } else {
                editingMedication.doseInstructionDraft.toDoseInstruction()
            },
            count = resolvedMedicationCountForSave(
                applicationType = applicationType,
                countText = editingMedication.countText,
                preparationType = preparationType,
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

    private fun MedicationGroupMedicationItemUiState.toInput(): MedicationGroupMedicationInput {
        return MedicationGroupMedicationInput(
            uuid = persistedMedicationId?.let(UUID::fromString),
            medicineUuid = medicineUuid,
            applicationType = applicationType,
            doseInstruction = doseInstruction,
            count = count,
        )
    }

    private suspend fun resolveMedicationGroupNameForSave(
        currentState: MedicationGroupEditorUiState,
    ): String {
        val trimmedGroupName = currentState.groupName.trim()
        val trimmedDefaultName = currentState.defaultGroupName.trim()
        if (
            currentState.isEditing ||
            (trimmedGroupName.isNotEmpty() && trimmedGroupName != trimmedDefaultName)
        ) {
            return resolveMedicationGroupName(
                groupName = currentState.groupName,
                defaultGroupName = currentState.defaultGroupName,
                isEditing = currentState.isEditing,
            )
        }

        val index = settingsRepository.consumeNextGroupNameIndex()
        return context.withAppLanguage().getString(R.string.default_group_name_format, index)
    }

    fun saveGroup(
        onCreatePastScheduledSlotRecordsCompleted:
        ((CreatePastScheduledSlotRecordsSaveState) -> Unit)? = null,
    ) {
        // A fresh clock read: the minute tick that moves "Now" past the day's end may not
        // have run yet, so move it here, or the save pairs yesterday's date with today's time.
        val recordGenerationNow = currentMinute.value
        val currentState = _uiState.updateAndGet { it.withStartsNowOn(recordGenerationNow) }
        if (
            currentState.isSaving ||
            currentState.isDeleting ||
            currentState.isArchiving ||
            currentState.isDeletingRelatedEntries ||
            currentState.isSaved ||
            currentState.isFinishingAfterSave ||
            currentState.isDeleted ||
            currentState.isFinishingAfterDeleteOrArchive ||
            currentState.isArchived ||
            currentState.scheduleTimeOrderError ||
            !hasSaveableMedicationGroupContent(currentState)
        ) {
            return
        }
        // Refused for what the editor already saw; the repository asks again at commit.
        currentState.saveRefusal()?.let { refusal ->
            _uiState.update { it.copy(saveMedicationGroupResult = refusal.toSaveResult()) }
            return
        }

        val recordGenerationCutoff = minOf(
            recordGenerationNow,
            currentState.createPastScheduledSlotRecordsEnabledAt ?: recordGenerationNow,
        )
        val saveNow = appTimeSource.now()
        val shouldCreatePastRecords = currentState.canCreatePastScheduledSlotRecords &&
                currentState.createPastScheduledSlotRecords &&
                hasPastScheduleOptionWindow(
                    uiState = currentState,
                    referenceTime = recordGenerationCutoff,
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

            var resolvedGroupName = resolveMedicationGroupName(
                groupName = currentState.groupName,
                defaultGroupName = currentState.defaultGroupName,
                isEditing = currentState.isEditing
            )
            val savedGroupUuidResult = runCatching {
                resolvedGroupName = resolveMedicationGroupNameForSave(currentState)
                // A locked plan's time change moves its records in this same save.
                medicationGroupRepository.saveGroup(
                    uuid = currentState.editingGroupId?.let(UUID::fromString),
                    name = resolvedGroupName,
                    colorKey = currentState.groupColorKey,
                    schedule = currentState.toScheduleInput(recordGenerationNow),
                    medications = currentState.medications.map { medication ->
                        medication.toInput()
                    },
                    notificationsEnabled = currentState.notificationsEnabled,
                    includePastScheduledSlots = currentState.includePastScheduledSlots,
                    now = saveNow,
                )
            }
            val saveResult = savedGroupUuidResult.fold(
                onSuccess = { null },
                onFailure = { cause ->
                    // A record logged, or the plan deleted, after the editor last saw it.
                    (cause as? MedicationGroupSaveRefusedException)?.refusal?.toSaveResult()
                        ?: SaveMedicationGroupResult.FAILURE
                },
            )
            val isSaved = saveResult == null
            val savedGroupUuid = savedGroupUuidResult.getOrNull()

            _uiState.update {
                val nextState = it.copy(
                    groupName = if (isSaved) {
                        resolvedGroupName
                    } else {
                        it.groupName
                    },
                    editingGroupId = savedGroupUuid?.toString() ?: it.editingGroupId,
                    originalMedicationDayBoundary = if (isSaved) {
                        currentState.medicationDayBoundary
                    } else {
                        it.originalMedicationDayBoundary
                    },
                    // Once saved it's a real group; drop the duplicate-recovery lineage
                    // so process death reloads the saved group, not a re-derived copy.
                    duplicateSourceGroupId = if (isSaved) {
                        null
                    } else {
                        it.duplicateSourceGroupId
                    },
                    isSaving = false,
                    isSaved = isSaved,
                    isFinishingAfterSave = isSaved,
                    saveMedicationGroupResult = saveResult,
                    createPastScheduledSlotRecordsResult = null,
                    createdPastScheduledSlotRecordCount = null,
                )
                resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
                    previousState = it,
                    nextState = nextState,
                )
            }
            if (isSaved && savedGroupUuid != null) {
                _completionEvents.tryEmit(MedicationGroupEditorCompletionEvent.SAVE_COMPLETED)
                withContext(NonCancellable) {
                    val recordGenerationResult = if (shouldCreatePastRecords) {
                        savePastScheduledSlotRecords(
                            groupUuid = savedGroupUuid,
                            now = recordGenerationCutoff,
                        )
                    } else {
                        null
                    }
                    // After the records, so a reminder for a dose they log is cleared.
                    runCatching { medicationReminderScheduler.rescheduleGroup(savedGroupUuid) }
                    if (recordGenerationResult != null) {
                        _uiState.update {
                            it.copy(
                                createPastScheduledSlotRecordsResult = recordGenerationResult.result,
                                createdPastScheduledSlotRecordCount =
                                    recordGenerationResult.savedRecordCount,
                            )
                        }
                        onCreatePastScheduledSlotRecordsCompleted?.invoke(recordGenerationResult)
                    }
                }
            }
        }
    }

    fun showDeleteConfirmation() {
        val currentState = _uiState.value
        if (
            currentState.isEditing &&
            !isMedicationGroupEditorBusy(currentState)
        ) {
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
            !isMedicationGroupEditorBusy(currentState)
        ) {
            _uiState.update {
                it.copy(
                    isArchiveConfirmationVisible = true,
                    archiveMedicationGroupResult = null,
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
            isMedicationGroupEditorBusy(currentState)
        ) {
            return
        }
        showDuplicatedDraft(source = currentState, startAt = null)
    }

    /** Shows [source]'s copy as an unsaved new plan, starting at [startAt], or now when null. */
    private fun showDuplicatedDraft(source: MedicationGroupEditorUiState, startAt: LocalDateTime?) {
        // Slots whose resolved medicine is archived are dropped (see
        // buildDuplicatedDraftState); toast the count so the user can re-add them
        // intentionally rather than silently reviving an archived medicine on save.
        val skippedArchivedCount = source.medications.count { medication ->
            medication.resolvedMedicine?.isArchived == true
        }
        val duplicatedState = buildDuplicatedDraftState(source, startAt)
            .copy(duplicateStartsAtCutoff = startAt != null)
        // The copy is what this editor now edits, as after process death, where it is re-derived:
        // leaving it untouched loses nothing. Set before the state, so no emission compares it
        // with the original.
        baselineDraft = duplicatedState.toEditedDraft()
        _uiState.update {
            duplicatedState.copy(
                activeMedicines = it.activeMedicines,
                firstDayOfWeek = it.firstDayOfWeek,
                scrollToTopRequestVersion = it.scrollToTopRequestVersion + 1,
                duplicateArchivedGroupSkippedCount = skippedArchivedCount.takeIf { count ->
                    count > 0
                },
            )
        }
    }

    // Builds the unsaved duplicate draft from a source editor state (the archived group
    // being copied). Shared by the live "Duplicate" action and process-death recovery so
    // both produce the same draft. Drops archived-medicine slots: the save path's
    // findOrCreate would silently revive a matching archived medicine, too lossy for a
    // user who explicitly archived it.
    private fun buildDuplicatedDraftState(
        source: MedicationGroupEditorUiState,
        startAt: LocalDateTime?,
    ): MedicationGroupEditorUiState {
        val usedColors = latestGroups.map(MedicationGroup::colorKey)
            .ifEmpty { listOf(source.groupColorKey) }
        val resolvedGroupName = resolveMedicationGroupName(
            groupName = source.groupName,
            defaultGroupName = source.defaultGroupName,
            isEditing = source.isEditing,
        )
        val colorKey = nextAvailableMedicationGroupColor(
            usedColors = usedColors,
            seed = UUID.randomUUID().hashCode(),
        )
        val activeMedications = source.medications.filterNot {
            it.resolvedMedicine?.isArchived == true
        }
        val draft = source.copy(medications = activeMedications).toUnsavedDuplicatedGroupState(
            resolvedGroupName = resolvedGroupName,
            defaultGroupName = source.defaultGroupName,
            colorKey = colorKey,
            scheduleStartDate = currentMinute.value.toLocalDate(),
        )
        // A fixed date and time, so it stays put as the clock moves, until the user changes it.
        return if (startAt == null) {
            draft
        } else {
            draft.copy(sinceDate = startAt.toLocalDate(), scheduleStart = ScheduleStart.At(startAt.toLocalTime()))
        }
    }

    // Process-death recovery for an unsaved duplicate-of-archived draft: reload the
    // remembered source group and re-derive the duplicate, instead of leaving the user
    // on the read-only archived original. The user's in-draft edits are not preserved
    // (input loss is acceptable); the copied content is, and so is a copy's start at the
    // archive cutoff ([startsAtCutoff]), which the archived source still holds.
    private fun recreateDuplicateDraftFromSource(sourceGroupId: String, startsAtCutoff: Boolean) {
        val sourceUuid = runCatching { UUID.fromString(sourceGroupId) }.getOrNull()
        if (sourceUuid == null) {
            _uiState.update {
                it.copy(isLoadingGroupForEditing = false, duplicateSourceGroupId = null)
            }
            return
        }
        viewModelScope.launch {
            val sourceGroup = loadPersistedGroupSnapshot(sourceUuid)
            if (sourceGroup == null) {
                _uiState.update {
                    it.copy(isLoadingGroupForEditing = false, duplicateSourceGroupId = null)
                }
                return@launch
            }
            val sourceState = sourceGroup.toEditorState(
                remindersEnabled = settingsRepository.settingsState.value.remindersEnabled,
                relatedEntryCount = 0,
                plannedEntryCount = 0,
            )
            _uiState.update { current ->
                buildDuplicatedDraftState(
                    source = sourceState,
                    startAt = sourceGroup.archivedAtLocal?.takeIf { startsAtCutoff },
                ).copy(
                    activeMedicines = current.activeMedicines,
                    firstDayOfWeek = current.firstDayOfWeek,
                    duplicateStartsAtCutoff = startsAtCutoff,
                )
            }
        }
    }

    fun consumeDuplicateArchivedGroupResult() {
        _uiState.update { it.copy(duplicateArchivedGroupSkippedCount = null) }
    }

    // null archivedThroughDate archives as of now; a non-null date archives
    // through the end of that selected day. [thenDuplicate] then shows the plan's copy,
    // unsaved and starting at the archive cutoff, instead of leaving.
    fun archiveGroup(archivedThroughDate: LocalDate? = null, thenDuplicate: Boolean = false) {
        val currentState = _uiState.value
        if (
            currentState.isArchived ||
            currentState.currentOrFuturePlannedSlotCount > 0 ||
            isArchiveDateSelectionInvalid(archivedThroughDate, currentState.archiveDateWindow) ||
            isMedicationGroupEditorBusy(currentState)
        ) {
            return
        }
        val groupId = currentState.editingGroupId ?: return
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull() ?: return
        val archiveNow = appTimeSource.now()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isArchiving = true,
                    isArchiveConfirmationVisible = false,
                    archiveMedicationGroupResult = null,
                )
            }

            // The copy is of the saved plan, not of unsaved edits (P8), read before archiving so
            // the archive never goes ahead without it: unreadable, nothing is archived, and the
            // failure shows.
            val copySource = if (thenDuplicate) loadPersistedGroupSnapshot(uuid) else null
            var cutoff: LocalDateTime? = null
            val archiveResult = runCatching {
                check(!thenDuplicate || copySource != null) { "Medication group $uuid couldn't be read." }
                cutoff = medicationGroupRepository.archiveGroup(
                    uuid = uuid,
                    archivedThroughDate = archivedThroughDate,
                    now = archiveNow,
                )
            }.fold(
                onSuccess = { null },
                onFailure = { cause ->
                    when (cause) {
                        is CurrentOrFuturePlannedSlotsBlockArchiveException ->
                            ArchiveMedicationGroupResult.BLOCKED_BY_FUTURE_PLANNED_SLOTS

                        else -> ArchiveMedicationGroupResult.FAILURE
                    }
                },
            )
            val archived = archiveResult == null
            val copyStart = cutoff?.takeIf { copySource != null }

            if (copySource != null && copyStart != null) {
                // Before the draft, whose source id is mirrored after it, so process death
                // re-derives this copy with the same start.
                savedStateHandle[DUPLICATE_STARTS_AT_CUTOFF_KEY] = true
                showDuplicatedDraft(
                    source = copySource.toEditorState(
                        remindersEnabled = currentState.remindersEnabled,
                        relatedEntryCount = 0,
                        plannedEntryCount = 0,
                    ),
                    startAt = copyStart,
                )
            } else {
                _uiState.update {
                    val nextState = it.copy(
                        isArchiving = false,
                        isDeleted = archived,
                        isFinishingAfterDeleteOrArchive = archived,
                        archiveMedicationGroupResult = archiveResult,
                    )
                    resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
                        previousState = it,
                        nextState = nextState,
                    )
                }
            }
            if (archived) {
                if (copyStart == null) {
                    _completionEvents.tryEmit(
                        MedicationGroupEditorCompletionEvent.DELETE_OR_ARCHIVE_COMPLETED
                    )
                }
                withContext(NonCancellable) {
                    runCatching { medicationReminderScheduler.cancelReminder(uuid) }
                    runCatching { medicationReminderScheduler.rescheduleAll() }
                }
            }
        }
    }

    private suspend fun loadPersistedGroupSnapshot(groupUuid: UUID): MedicationGroup? {
        return latestGroups.firstOrNull { group -> group.uuid == groupUuid }
            ?: medicationGroupRepository.getCachedGroup(groupUuid)
            ?: runCatching { medicationGroupRepository.getGroup(groupUuid) }.getOrNull()
    }

    fun showDeleteRelatedEntriesConfirmation() {
        val currentState = _uiState.value
        if (
            currentState.isEditing &&
            currentState.relatedEntryCount > 0 &&
            !isMedicationGroupEditorBusy(currentState)
        ) {
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
        if (
            currentState.relatedEntryCount <= 0 ||
            isMedicationGroupEditorBusy(currentState)
        ) {
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
                    plannedEntryCount = if (result == DeleteRelatedEntriesResult.SUCCESS) {
                        0
                    } else {
                        it.plannedEntryCount
                    },
                    currentOrFuturePlannedSlotCount =
                        if (result == DeleteRelatedEntriesResult.SUCCESS) {
                            0
                        } else {
                            it.currentOrFuturePlannedSlotCount
                        },
                    scheduleTimeOrderError = if (result == DeleteRelatedEntriesResult.SUCCESS) {
                        false
                    } else {
                        it.scheduleTimeOrderError
                    },
                )
            }
        }
    }

    fun consumeSaveMedicationGroupResult() {
        _uiState.update { it.copy(saveMedicationGroupResult = null) }
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

    // Called only after the exit navigation has been confirmed by the host
    // (the pop or onboarding dismissal actually happened). A failed exit must
    // leave the finishing flags retained so the exit effect re-fires when the
    // editor is restored.
    fun consumeExitNavigation() {
        _uiState.update {
            it.copy(
                isFinishingAfterSave = false,
                isFinishingAfterDeleteOrArchive = false,
            )
        }
    }

    fun retryLoadingGroup() {
        val uuid = editingGroupUuid ?: return
        if (!_uiState.value.isGroupLoadFailed) return
        _uiState.update { it.copy(isGroupLoadFailed = false) }
        loadGroupForEditing(uuid)
    }

    private fun loadGroupForEditing(uuid: UUID) {
        viewModelScope.launch {
            val (group, remindersEnabled) = try {
                val group = medicationGroupRepository.getGroup(uuid)
                if (group == null) {
                    _uiState.update { it.copy(isLoadingGroupForEditing = false) }
                    return@launch
                }
                group to settingsRepository.getCurrentSettings().remindersEnabled
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Still loading, so nothing can be saved over the plan.
                _uiState.update { it.copy(isGroupLoadFailed = true) }
                return@launch
            }
            savedPlan = group

            _uiState.update { currentState ->
                val snapshot = appTimeSource.currentSnapshot.value
                group.toEditorState(
                    remindersEnabled = remindersEnabled,
                    relatedEntryCount = currentState.relatedEntryCount,
                    plannedEntryCount = currentState.plannedEntryCount,
                    currentOrFuturePlannedSlotCount =
                        currentState.currentOrFuturePlannedSlotCount,
                ).copy(
                    // Not stored with the plan: their observers don't emit again.
                    activeMedicines = currentState.activeMedicines,
                    firstDayOfWeek = currentState.firstDayOfWeek,
                    areEntriesLoaded = currentState.areEntriesLoaded,
                    latestRecordedDoseDate = currentState.latestRecordedDoseDate,
                    archiveDateWindow = resolveArchiveDateWindow(
                        scheduleSince = group.schedule.since,
                        latestRecordedDoseDate = currentState.latestRecordedDoseDate,
                        areEntriesLoaded = currentState.areEntriesLoaded,
                        today = medicationDay(snapshot.minute, group.schedule.dayBoundary),
                    ),
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
                endDate = medicationDay(now, savedGroup.schedule.dayBoundary),
                now = now,
                zoneId = ZoneId.systemDefault(),
            )
            medicationLogRepository.saveBackfillEntries(entriesToSave)
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
        val currentState = _uiState.value
        if (isMedicationGroupEditorBusy(currentState)) {
            return
        }
        val groupId = currentState.editingGroupId ?: return
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

            _uiState.update {
                val nextState = it.copy(
                    isDeleting = false,
                    isDeleted = isDeleted,
                    isFinishingAfterDeleteOrArchive = isDeleted,
                    deleteMedicationGroupResult = deleteResult,
                )
                resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
                    previousState = it,
                    nextState = nextState,
                )
            }
            if (isDeleted) {
                _completionEvents.tryEmit(
                    MedicationGroupEditorCompletionEvent.DELETE_OR_ARCHIVE_COMPLETED
                )
                withContext(NonCancellable) {
                    runCatching { medicationReminderScheduler.cancelReminder(uuid) }
                }
            }
        }
    }

    companion object {
        const val GROUP_ID_ARG = "groupId"
        private const val EDITED_GROUP_ID_KEY = "editedGroupId"
        private const val DUPLICATE_SOURCE_GROUP_ID_KEY = "duplicateSourceGroupId"
        private const val DUPLICATE_STARTS_AT_CUTOFF_KEY = "duplicateStartsAtCutoff"
    }
}

private fun MedicationGroup.toEditorState(
    remindersEnabled: Boolean,
    relatedEntryCount: Int,
    plannedEntryCount: Int,
    currentOrFuturePlannedSlotCount: Int = 0,
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
    // Not stored on its own: the earliest effectiveFrom is the start, when after the start
    // date's own day start. The form's date is the first day as entered: the start's own date
    // when the start falls on its first medication day, else (a plan that hid its past) that
    // first day, with the start's date on the time row. The saved first day is kept beside the
    // start, so the plan keeps its rhythm (every-N days and weeks count from it) until the start
    // is edited, night owl turned on or off included.
    val loadedStartAt = schedule.timeSlots
        .minOfOrNull(MedicationGroupScheduleTime::effectiveFrom)
        ?.takeIf { it.isAfter(medicationDayStart(schedule.since, schedule.dayBoundary)) }
    val editorMedications = medications.map { medication ->
        medication.toItemUiState()
    }
    return MedicationGroupEditorUiState(
        editingGroupId = uuid.toString(),
        groupName = name,
        defaultGroupName = name,
        hasResolvedInitialGroupName = true,
        scheduleType = schedule.type,
        sinceDate = loadedStartAt
            ?.takeIf { medicationDay(it, schedule.dayBoundary) == schedule.since }
            ?.toLocalDate()
            ?: schedule.since,
        scheduleStart = loadedStartAt?.let { ScheduleStart.Loaded(it, schedule.since) } ?: ScheduleStart.WholeDay,
        medicationDayBoundary = schedule.dayBoundary,
        originalMedicationDayBoundary = schedule.dayBoundary,
        weeklyIntervalWeeks = if (schedule.type == MedicationGroupScheduleType.WEEKLY) {
            parseScheduleInterval(schedule.interval.toString()).toString()
        } else {
            "1"
        },
        weeklyDaysOfWeek = schedule.weeklyDaysOfWeek.ifEmpty {
            setOf(LocalDate.now().dayOfWeek)
        },
        hasUserCustomizedWeeklyDays = schedule.type == MedicationGroupScheduleType.WEEKLY &&
                schedule.weeklyDaysOfWeek.isNotEmpty(),
        weeklyTimeLocalId = if (schedule.type == MedicationGroupScheduleType.WEEKLY) {
            normalizedScheduleTimeSlots.firstOrNull()?.uuid?.toString() ?: UUID.randomUUID()
                .toString()
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
        groupColorKey = colorKey,
        hasAssignedGroupColor = true,
        isLoadingGroupForEditing = false,
        medications = editorMedications,
        relatedEntryCount = relatedEntryCount,
        plannedEntryCount = plannedEntryCount,
        currentOrFuturePlannedSlotCount = currentOrFuturePlannedSlotCount,
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

private fun MedicationGroupEditorUiState.toUnsavedDuplicatedGroupState(
    resolvedGroupName: String,
    defaultGroupName: String,
    colorKey: MedicationGroupColorKey,
    scheduleStartDate: LocalDate,
): MedicationGroupEditorUiState {
    val copiedMedications = medications.map { medication ->
        medication.copy(
            localId = UUID.randomUUID().toString(),
            persistedMedicationId = null,
        )
    }
    return copy(
        editingGroupId = null,
        sinceDate = scheduleStartDate,
        scheduleStart = ScheduleStart.Now,
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
        isFinishingAfterSave = false,
        isFinishingAfterDeleteOrArchive = false,
        isArchived = false,
        // Shown, so a start in the past (such as an archive cutoff) is the start saved.
        includePastScheduledSlots = true,
        createPastScheduledSlotRecords = false,
        saveMedicationGroupResult = null,
        relatedEntryCount = 0,
        plannedEntryCount = 0,
        currentOrFuturePlannedSlotCount = 0,
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
        // Remember the archived source so process death can re-derive this duplicate.
        duplicateSourceGroupId = editingGroupId,
        archiveMedicationGroupResult = null,
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

internal fun editorStateWithUpdatedScheduleType(
    uiState: MedicationGroupEditorUiState,
    scheduleType: MedicationGroupScheduleType,
): MedicationGroupEditorUiState {
    val isEnteringWeekly = scheduleType == MedicationGroupScheduleType.WEEKLY &&
            uiState.scheduleType != MedicationGroupScheduleType.WEEKLY
    val withType = uiState.copy(scheduleType = scheduleType)
    return if (isEnteringWeekly && !uiState.hasUserCustomizedWeeklyDays) {
        editorStateWithResetWeeklyDaysOfWeek(withType)
    } else {
        withType
    }
}

internal fun editorStateWithResetWeeklyDaysOfWeek(
    uiState: MedicationGroupEditorUiState,
): MedicationGroupEditorUiState {
    return uiState.copy(
        weeklyDaysOfWeek = setOf(uiState.sinceDate.dayOfWeek),
        hasUserCustomizedWeeklyDays = false,
    )
}

internal fun editorStateWithUpdatedSinceDate(
    uiState: MedicationGroupEditorUiState,
    date: LocalDate,
): MedicationGroupEditorUiState {
    if (uiState.areScheduleShapeFieldsLocked) {
        return uiState
    }

    // A picked date starts with the whole day: 00:00, or 04:00 when the day ends then.
    // Re-picking the same date does too, so "Now" can widen to today's earlier doses.
    val withDate = uiState.copy(
        sinceDate = date,
        scheduleStart = ScheduleStart.WholeDay,
    )
    return if (
        uiState.scheduleType == MedicationGroupScheduleType.WEEKLY &&
        uiState.sinceDate != date
    ) {
        editorStateWithResetWeeklyDaysOfWeek(withDate)
    } else {
        withDate
    }
}

/**
 * The draft starting now, on [today]. Unlike picking a date, it keeps weekdays the user
 * chose, since only the start changed; untouched ones still follow the start date.
 */
internal fun editorStateStartingNow(
    uiState: MedicationGroupEditorUiState,
    today: LocalDate,
): MedicationGroupEditorUiState {
    val startingNow = uiState.copy(
        sinceDate = today,
        scheduleStart = ScheduleStart.Now,
    )
    return if (
        startingNow.scheduleType == MedicationGroupScheduleType.WEEKLY &&
        !startingNow.hasUserCustomizedWeeklyDays
    ) {
        editorStateWithResetWeeklyDaysOfWeek(startingNow)
    } else {
        startingNow
    }
}

/**
 * A draft starting now, on [now]'s date: one left open past midnight starts today.
 */
internal fun MedicationGroupEditorUiState.withStartsNowOn(now: LocalDateTime): MedicationGroupEditorUiState {
    if (!startsNow) return this
    val today = now.toLocalDate()
    return if (sinceDate == today) this else editorStateStartingNow(this, today)
}

/**
 * The moment the draft starts, from the start as the user chose it and the plan's day ending.
 * A picked date and time is that calendar moment, whatever the day ending; a whole start date
 * starts at its day boundary, 04:00 when the plan's day ends then.
 */
internal fun MedicationGroupEditorUiState.scheduleStartAt(now: LocalDateTime): LocalDateTime {
    val dayStart = medicationDayStart(sinceDate, medicationDayBoundary)
    return when (val start = scheduleStart) {
        ScheduleStart.Now -> now
        ScheduleStart.WholeDay -> dayStart
        // Nothing before its first day's start can fall anyway.
        is ScheduleStart.Loaded -> maxOf(start.at, medicationDayStart(start.since, medicationDayBoundary))
        is ScheduleStart.At -> sinceDate.atTime(start.time)
    }
}

/**
 * The plan's first medication day, which every-N-days and every-N-weeks count from: the one its
 * start falls on, so a night owl's 01:00 start belongs to the day before. A saved plan's loaded
 * start keeps its saved date, whatever the day ending, so its doses stay on their days.
 */
internal fun MedicationGroupEditorUiState.scheduleSince(now: LocalDateTime): LocalDate =
    (scheduleStart as? ScheduleStart.Loaded)?.since ?: medicationDay(scheduleStartAt(now), medicationDayBoundary)

/**
 * The weekdays the plan saves: the user's, or, left untouched, its first medication day's, as
 * weekdays mean medication days everywhere else.
 */
internal fun MedicationGroupEditorUiState.scheduleWeekdays(now: LocalDateTime): Set<DayOfWeek> =
    if (hasUserCustomizedWeeklyDays) weeklyDaysOfWeek else setOf(scheduleSince(now).dayOfWeek)

/** The schedule this draft saves, its start resolved at [now]: the one place a start becomes the stored model. */
internal fun MedicationGroupEditorUiState.toScheduleInput(now: LocalDateTime): MedicationGroupScheduleInput {
    val isWeekly = scheduleType == MedicationGroupScheduleType.WEEKLY
    val timeSlots = scheduleTimeInputsForSave(this)
    return MedicationGroupScheduleInput(
        type = scheduleType,
        interval = parseScheduleInterval(if (isWeekly) weeklyIntervalWeeks else dailyIntervalDays),
        since = scheduleSince(now),
        weeklyDaysOfWeek = if (isWeekly) scheduleWeekdays(now) else emptySet(),
        times = timeSlots.map(MedicationGroupScheduleTimeInput::time),
        timeSlots = timeSlots,
        startAt = scheduleStartAt(now),
        dayBoundary = medicationDayBoundary,
    )
}

internal fun hasSaveableMedicationGroupContent(
    uiState: MedicationGroupEditorUiState
): Boolean {
    val hasGroupName = uiState.groupName.trim().isNotEmpty() ||
            (!uiState.isEditing && uiState.defaultGroupName.trim().isNotEmpty())
    val hasWeeklyDays = uiState.scheduleType != MedicationGroupScheduleType.WEEKLY ||
            uiState.weeklyDaysOfWeek.isNotEmpty()
    return hasGroupName &&
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
        ),
        MedicationDayBoundary.Midnight,
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
        ),
        MedicationDayBoundary.Midnight,
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
    // The plan's ending for display order only. The editor state and save path keep
    // plain clock order (Midnight), which the locked time-change check compares with.
    boundary: MedicationDayBoundary,
): List<MedicationGroupScheduleTimeUiState> {
    return dailyTimes.sortedWith(
        compareBy<MedicationGroupScheduleTimeUiState> { dailyTime ->
            boundary.timeOfDayOrder(dailyTime.originalTime ?: dailyTime.time)
        }.thenBy { dailyTime ->
            boundary.timeOfDayOrder(dailyTime.time)
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

// A draft-aware duplicate key. Two slots are "the same dose" when they resolve
// to the same medicine identity, route, and dose instruction. We compare picker
// identity (catalog key / normalized custom name) since brand-new "+ New" slots
// have no medicine uuid yet; a resolved medicine collapses to its uuid.
internal fun MedicationGroupMedicationItemUiState.duplicateKey(): String {
    val resolved = resolvedMedicine
    val identity = if (resolved != null) {
        "M:${resolved.uuid}"
    } else {
        val draft = medicineDraft
        if (draft.requiresCustomName()) {
            "X:${
                com.mkx.hrttracker.model.medication.normalizeCustomMedicationName(
                    draft.customMedicationName,
                )
            }:${draft.inferredOrSelectedPreparationType()}"
        } else {
            "C:${draft.medicationKey}:${draft.inferredOrSelectedPreparationType()}"
        }
    }
    return "$identity|${applicationType.name}|${doseInstruction}"
}

internal fun upsertMedication(
    medications: List<MedicationGroupMedicationItemUiState>,
    savedMedication: MedicationGroupMedicationItemUiState,
): MedicationGroupMedicationSaveResult {
    // Match by identity/route/dose so the editor's notion of "duplicate" lines up
    // with downstream layers that treat same-signature meds as one dose.
    val savedKey = savedMedication.duplicateKey()
    val duplicateMedication = medications.firstOrNull { medication ->
        medication.localId != savedMedication.localId &&
                medication.duplicateKey() == savedKey
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

data class CreatePastScheduledSlotRecordsSaveState(
    val result: CreatePastScheduledSlotRecordsResult?,
    val savedRecordCount: Int?,
)

enum class MedicationGroupEditorCompletionEvent {
    SAVE_COMPLETED,
    DELETE_OR_ARCHIVE_COMPLETED,
}

/** Where a draft's schedule starts on its start date. */
sealed interface ScheduleStart {
    /** At the moment the plan is saved. */
    data object Now : ScheduleStart

    /** With the whole start date, from its day boundary on. */
    data object WholeDay : ScheduleStart

    /** At a picked time of the start date: a plain calendar date and time, night owl or not. */
    data class At(val time: LocalTime) : ScheduleStart

    /**
     * The saved plan's start [at] (the form's date is its date) and its saved first day [since],
     * kept until the start is edited. A day ending turned so that [since]'s day starts after [at]
     * moves the start up to that day start.
     */
    data class Loaded(val at: LocalDateTime, val since: LocalDate) : ScheduleStart
}

// What the user edits in a plan, without the ids and loaded objects that change under them,
// such as a slot's medicine refreshed as its stock moves.
internal data class MedicationGroupEditedDraft(
    val groupName: String,
    val groupColorKey: MedicationGroupColorKey,
    val scheduleType: MedicationGroupScheduleType,
    // Null while the clock keeps it: a plan starting now starts today, and its weekdays left on
    // the start date's follow it, both moving on past the day's end untouched.
    val sinceDate: LocalDate?,
    // The moment it starts, however the form holds it (a whole day, a picked time, a loaded
    // start), so a start picked back onto the loaded moment is no edit. Null starting now, which
    // moves with the clock.
    val startAt: LocalDateTime?,
    val medicationDayBoundary: MedicationDayBoundary,
    // The other schedule type's fields are null: the plan saves only its own type's.
    val weeklyIntervalWeeks: String?,
    val weeklyDaysOfWeek: Set<DayOfWeek>?,
    val weeklyTime: LocalTime?,
    val dailyIntervalDays: String?,
    val dailyTimes: List<LocalTime>?,
    val notificationsEnabled: Boolean,
    val medications: List<MedicationGroupEditedSlot>,
    val includePastScheduledSlots: Boolean,
    val createPastScheduledSlotRecords: Boolean,
    val weekdaysFollowStart: Boolean,
)

internal data class MedicationGroupEditedSlot(
    val medicineUuid: UUID?,
    val applicationType: MedicationApplicationType,
    val doseInstruction: DoseInstruction,
    val count: Int,
)

// What the saved plan would be, not how the form got there: the weekday flag stays set after a
// chip is turned back, and a start date picked and picked back resets it, neither of which the
// saved plan sees. [weekdaysFollowStart] is the baseline's: a new plan's untouched weekdays move
// with the start date as the clock moves it, so a draft starting now on its start date's weekday
// matches them; a copied plan's weekdays stay put, so the draft's are compared as they are.
internal fun MedicationGroupEditorUiState.toEditedDraft(
    weekdaysFollowStart: Boolean = startsNow && !hasUserCustomizedWeeklyDays,
): MedicationGroupEditedDraft {
    val isWeekly = scheduleType == MedicationGroupScheduleType.WEEKLY
    val isDaily = scheduleType == MedicationGroupScheduleType.DAILY
    return MedicationGroupEditedDraft(
        groupName = groupName.trim(),
        groupColorKey = groupColorKey,
        scheduleType = scheduleType,
        // The first day it saves, which a start re-picked on the same moment can still change.
        sinceDate = if (startsNow) null else scheduleSince(LocalDateTime.MIN),
        // Only a "Now" start reads the time it is given.
        startAt = if (startsNow) null else scheduleStartAt(now = LocalDateTime.MIN),
        medicationDayBoundary = medicationDayBoundary,
        weeklyIntervalWeeks = weeklyIntervalWeeks.takeIf { isWeekly },
        // The weekdays it saves; a "Now" draft's untouched ones move with the clock, as its start does.
        weeklyDaysOfWeek = (if (startsNow) weeklyDaysOfWeek.takeIf { hasUserCustomizedWeeklyDays } else scheduleWeekdays(LocalDateTime.MIN))
            ?.takeIf { days -> isWeekly && !(weekdaysFollowStart && startsNow && days == setOf(sinceDate.dayOfWeek)) },
        weeklyTime = weeklyTime.takeIf { isWeekly },
        dailyIntervalDays = dailyIntervalDays.takeIf { isDaily },
        dailyTimes = dailyTimes.map { time -> time.time }.takeIf { isDaily },
        notificationsEnabled = notificationsEnabled,
        medications = medications.map { slot ->
            MedicationGroupEditedSlot(
                medicineUuid = slot.medicineUuid,
                applicationType = slot.applicationType,
                doseInstruction = slot.doseInstruction,
                count = slot.count,
            )
        },
        includePastScheduledSlots = includePastScheduledSlots,
        createPastScheduledSlotRecords = createPastScheduledSlotRecords,
        weekdaysFollowStart = weekdaysFollowStart,
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
    val scheduleStart: ScheduleStart = ScheduleStart.WholeDay,
    // This plan's own day ending (night owl). New plans end at midnight.
    val medicationDayBoundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
    val weeklyIntervalWeeks: String = "1",
    val weeklyDaysOfWeek: Set<DayOfWeek> = setOf(LocalDate.now().dayOfWeek),
    val hasUserCustomizedWeeklyDays: Boolean = false,
    val weeklyTimeLocalId: String = UUID.randomUUID().toString(),
    val weeklyTime: LocalTime = LocalTime.of(9, 0),
    val dailyIntervalDays: String = "1",
    val dailyTimes: List<MedicationGroupScheduleTimeUiState> = listOf(
        MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0))
    ),
    val remindersEnabled: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val notificationsEnabled: Boolean = false,
    val hasResolvedNotificationDefault: Boolean = false,
    val medications: List<MedicationGroupMedicationItemUiState> = emptyList(),
    val activeMedicines: List<Medicine> = emptyList(),
    val editingMedication: MedicationGroupMedicationEditorUiState? = null,
    val isMedicationEditorSaved: Boolean = false,
    val medicationEditorErrorMessageRes: Int? = null,
    val medicationEditorInfoMessageRes: Int? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isArchiving: Boolean = false,
    val isDeletingRelatedEntries: Boolean = false,
    val isLoadingGroupForEditing: Boolean = false,
    // The plan couldn't be read: still loading, and the screen offers to try again.
    val isGroupLoadFailed: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isFinishingAfterSave: Boolean = false,
    val isFinishingAfterDeleteOrArchive: Boolean = false,
    val isArchived: Boolean = false,
    val includePastScheduledSlots: Boolean = true,
    val createPastScheduledSlotRecords: Boolean = false,
    val createPastScheduledSlotRecordsEnabledAt: LocalDateTime? = null,
    val saveMedicationGroupResult: SaveMedicationGroupResult? = null,
    val createPastScheduledSlotRecordsResult: CreatePastScheduledSlotRecordsResult? = null,
    val createdPastScheduledSlotRecordCount: Int? = null,
    val relatedEntryCount: Int = 0,
    val plannedEntryCount: Int = 0,
    val currentOrFuturePlannedSlotCount: Int = 0,
    val latestRecordedDoseDate: LocalDate? = null,
    val areEntriesLoaded: Boolean = false,
    val archiveDateWindow: ArchiveDateWindowUiState = ArchiveDateWindowUiState(),
    val scheduleTimeOrderError: Boolean = false,
    val originalScheduleType: MedicationGroupScheduleType? = null,
    val originalSinceDate: LocalDate? = null,
    // The saved plan's ending, which archiving uses. An unsaved toggle doesn't change it.
    val originalMedicationDayBoundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
    val originalWeeklyIntervalWeeks: String = "1",
    val originalWeeklyDaysOfWeek: Set<DayOfWeek> = emptySet(),
    val originalWeeklyTime: LocalTime = LocalTime.of(9, 0),
    val originalDailyIntervalDays: String = "1",
    val originalDailyTimes: List<LocalTime> = emptyList(),
    val originalMedications: List<MedicationGroupMedicationItemUiState> = emptyList(),
    val isArchiveConfirmationVisible: Boolean = false,
    val isDeleteConfirmationVisible: Boolean = false,
    val isDeleteRelatedEntriesConfirmationVisible: Boolean = false,
    // Set on an unsaved duplicate-of-archived draft: the archived source's id, persisted
    // so process death can re-derive the duplicate instead of reloading the source.
    val duplicateSourceGroupId: String? = null,
    // That draft is the archive shortcut's copy, starting at the archive cutoff.
    val duplicateStartsAtCutoff: Boolean = false,
    val archiveMedicationGroupResult: ArchiveMedicationGroupResult? = null,
    val deleteRelatedEntriesResult: DeleteRelatedEntriesResult? = null,
    val deleteMedicationGroupResult: DeleteMedicationGroupResult? = null,
    // Set by duplicateArchivedGroup() to the count of archived-medicine slots
    // that were dropped from the duplicated copy. We skip those slots because
    // including them would silently revive archived medicines via the save-
    // path's findOrCreate. The screen reads this to toast the count, then
    // calls consumeDuplicateArchivedGroupResult().
    val duplicateArchivedGroupSkippedCount: Int? = null,
    val scrollToTopRequestVersion: Int = 0,
) {
    val startsNow: Boolean
        get() = scheduleStart == ScheduleStart.Now

    val isEditing: Boolean
        get() = editingGroupId != null

    // Loaded, and the defaults filled in after it (a new plan's color and notifications), so
    // what's in the form from here on is what the user would lose.
    val hasSettledForEditing: Boolean
        get() = !isLoadingGroupForEditing && hasAssignedGroupColor && hasResolvedNotificationDefault

    val isLocked: Boolean
        get() = isEditing && !isArchived && plannedEntryCount > 0

    val areScheduleShapeFieldsLocked: Boolean
        get() = isLocked || isArchived

    val areMedicationsLocked: Boolean
        get() = isLocked || isArchived

    val canEditScheduleStart: Boolean
        get() = !areScheduleShapeFieldsLocked

    // Locked once the plan has any record or is archived, as saveRefusal enforces.
    // Changing it then means archiving it and saving a copy.
    val canEditDayBoundary: Boolean
        get() = !isArchived && (!isEditing || (areEntriesLoaded && relatedEntryCount == 0))

    val canEditBackfillOption: Boolean
        get() = !isArchived &&
                (
                        !isEditing ||
                                (
                                        relatedEntryCount == 0 &&
                                                plannedEntryCount == 0
                                        )
                        )

    val canCreatePastScheduledSlotRecords: Boolean
        get() = includePastScheduledSlots &&
                canEditBackfillOption
}

private fun MedicationGroupEditorUiState.withCreatePastScheduledSlotRecordsEnabledAt(
    includePastScheduledSlots: Boolean,
    createPastScheduledSlotRecords: Boolean,
    enabledAt: LocalDateTime,
): MedicationGroupEditorUiState {
    val wasEffective = this.includePastScheduledSlots && this.createPastScheduledSlotRecords
    val isEffective = includePastScheduledSlots && createPastScheduledSlotRecords
    return copy(
        includePastScheduledSlots = includePastScheduledSlots,
        createPastScheduledSlotRecords = createPastScheduledSlotRecords,
        createPastScheduledSlotRecordsEnabledAt = when {
            !isEffective -> null
            wasEffective -> createPastScheduledSlotRecordsEnabledAt ?: enabledAt
            else -> enabledAt
        },
    )
}

data class ArchiveDateWindowUiState(
    val minDate: LocalDate? = null,
    val maxDate: LocalDate = LocalDate.ofEpochDay(0),
    val isLoaded: Boolean = false,
    val isSelectable: Boolean = false,
)

internal fun resolveMedicationGroupEditorUiStateWithStableRecordPresentation(
    previousState: MedicationGroupEditorUiState,
    nextState: MedicationGroupEditorUiState,
): MedicationGroupEditorUiState {
    return if (shouldFreezeMedicationGroupEditorRecordPresentation(nextState)) {
        nextState.copy(
            relatedEntryCount = previousState.relatedEntryCount,
            plannedEntryCount = previousState.plannedEntryCount,
            currentOrFuturePlannedSlotCount = previousState.currentOrFuturePlannedSlotCount,
            isArchived = previousState.isArchived,
            scheduleTimeOrderError = previousState.scheduleTimeOrderError,
        )
    } else {
        nextState
    }
}

internal fun shouldFreezeMedicationGroupEditorRecordPresentation(
    uiState: MedicationGroupEditorUiState,
): Boolean {
    return uiState.isSaving ||
            uiState.isSaved ||
            uiState.isFinishingAfterSave ||
            uiState.isDeleting ||
            uiState.isDeleted ||
            uiState.isFinishingAfterDeleteOrArchive ||
            uiState.isArchiving ||
            uiState.isDeletingRelatedEntries
}

enum class CreatePastScheduledSlotRecordsResult {
    FAILURE,
}

enum class SaveMedicationGroupResult {
    FAILURE,

    /** Refused because the change would take the plan's records off their slots or medications. */
    LOCKED_BY_RECORDS,

    /** Refused because a retime would move a logged dose to before the plan's start. */
    LOGGED_DOSE_BEFORE_START,

    /** Refused because the plan was deleted while being edited. */
    GROUP_DELETED,
}

internal fun SaveRefusal.toSaveResult(): SaveMedicationGroupResult = when (this) {
    SaveRefusal.GROUP_DELETED -> SaveMedicationGroupResult.GROUP_DELETED
    SaveRefusal.LOCKED_BY_RECORDS -> SaveMedicationGroupResult.LOCKED_BY_RECORDS
    SaveRefusal.LOGGED_DOSE_BEFORE_START -> SaveMedicationGroupResult.LOGGED_DOSE_BEFORE_START
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

    /**
     * The repository rejected the archive because there are still current or
     * future planned slots that haven't been resolved. Surfaced as a distinct
     * outcome so the UI can guide the user toward the recovery action instead
     * of showing a generic failure toast.
     */
    BLOCKED_BY_FUTURE_PLANNED_SLOTS,
}

internal fun applyDefaultGroupNameToEditorState(
    currentState: MedicationGroupEditorUiState,
    defaultGroupName: String,
): MedicationGroupEditorUiState {
    val shouldResolveInitialGroupName = !currentState.isEditing &&
            !currentState.hasResolvedInitialGroupName &&
            defaultGroupName.isNotBlank()
    return currentState.copy(
        defaultGroupName = defaultGroupName,
        hasResolvedInitialGroupName = currentState.hasResolvedInitialGroupName ||
                shouldResolveInitialGroupName
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
    val currentOrFuturePlannedSlotCount: Int,
    val latestRecordedDoseDate: LocalDate?,
    val areEntriesLoaded: Boolean,
    val plannedRecords: List<MedicationGroupSlotKey> = emptyList(),
)

internal fun entryCountsForGroup(
    entries: List<MedicationLogEntry>,
    groupId: String?,
    currentMinute: LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0),
    boundary: MedicationDayBoundary,
    zoneId: ZoneId = ZoneId.systemDefault(),
    areEntriesLoaded: Boolean = true,
): MedicationGroupEntryCounts {
    fun emptyCounts() = MedicationGroupEntryCounts(
        relatedEntryCount = 0,
        plannedEntryCount = 0,
        currentOrFuturePlannedSlotCount = 0,
        latestRecordedDoseDate = null,
        areEntriesLoaded = areEntriesLoaded,
    )
    if (groupId == null) return emptyCounts()

    val groupUuid = runCatching { UUID.fromString(groupId) }.getOrNull()
        ?: return emptyCounts()
    val relatedEntries = entries.filter { entry -> entry.sourceGroupUuid == groupUuid }
    val referenceMinute = currentMinute.withSecond(0).withNano(0)
    return MedicationGroupEntryCounts(
        relatedEntryCount = relatedEntries.size,
        plannedEntryCount = relatedEntries.count { entry -> entry.scheduledFor != null },
        currentOrFuturePlannedSlotCount = relatedEntries
            .mapNotNull(MedicationLogEntry::scheduledFor)
            .filter { scheduledFor -> !scheduledFor.isBefore(referenceMinute) }
            .toSet()
            .size,
        latestRecordedDoseDate = relatedEntries.maxOfOrNull { entry ->
            entry.planCalendarDate(boundary, zoneId)
        },
        areEntriesLoaded = areEntriesLoaded,
        plannedRecords = relatedEntries.mapNotNull { entry ->
            entry.scheduledFor?.let { scheduledFor ->
                MedicationGroupSlotKey(entry.scheduleTimeUuid, scheduledFor)
            }
        },
    )
}

// A null selection means "archive as of now" and is always inside the window
// when archiving is otherwise allowed; a non-null date must fall within
// [min, max].
private fun LocalDate?.isOutsideArchiveWindow(
    minArchiveDate: LocalDate,
    maxArchiveDate: LocalDate,
): Boolean {
    if (this == null) return false
    return isBefore(minArchiveDate) || isAfter(maxArchiveDate)
}

// Shared by the ViewModel archive guards and the dialog confirm button so both
// agree on what counts as a valid archive-date selection. The "Now" default
// (null) only needs the window loaded — a plan whose window is loaded but has
// no selectable backdate (min > yesterday: it starts today or later, or has a
// record today) can still be archived as of now. An explicitly picked date
// additionally needs a selectable window and must fall inside [min, max].
internal fun isArchiveDateSelectionInvalid(
    archivedThroughDate: LocalDate?,
    window: ArchiveDateWindowUiState,
): Boolean {
    if (!window.isLoaded) return true
    if (archivedThroughDate == null) return false
    val minDate = window.minDate
    return !window.isSelectable ||
            minDate == null ||
            archivedThroughDate.isOutsideArchiveWindow(minDate, window.maxDate)
}

internal fun resolveArchiveDateWindow(
    scheduleSince: LocalDate?,
    latestRecordedDoseDate: LocalDate?,
    areEntriesLoaded: Boolean,
    today: LocalDate,
): ArchiveDateWindowUiState {
    // Through yesterday at the latest: archived through today, the plan would stop
    // reminding now but still own tonight's slots, so they'd fall through. "Now"
    // covers today.
    val maxDate = today.minusDays(1)
    if (!areEntriesLoaded || scheduleSince == null) {
        return ArchiveDateWindowUiState(
            maxDate = maxDate,
            isLoaded = false,
            isSelectable = false,
        )
    }
    val minDate = maxOf(scheduleSince, latestRecordedDoseDate ?: scheduleSince)
    return ArchiveDateWindowUiState(
        minDate = minDate,
        maxDate = maxDate,
        isLoaded = true,
        isSelectable = !minDate.isAfter(maxDate),
    )
}

internal fun relatedEntryCountForGroup(
    entries: List<MedicationLogEntry>,
    groupId: String?,
    boundary: MedicationDayBoundary,
): Int = entryCountsForGroup(entries, groupId, boundary = boundary).relatedEntryCount

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

internal fun shouldShowLockedScheduleTimeNote(uiState: MedicationGroupEditorUiState): Boolean {
    return uiState.isLocked && lockedScheduleTimesChanged(uiState)
}

// A slot draft inside the group editor. `resolvedMedicine` is set when the slot
// references an existing medicine (loaded from DB, or picked from the existing
// cards); a brand-new "+ New" slot keeps it null until `saveGroup` resolves it.
/**
 * This plan as the medicine picker should count it, unsaved edits included.
 * [pickingLocalId] is the slot the picker is choosing a medicine for, left out
 * so its current medicine doesn't count while being replaced. Archived plans
 * never count, so they have no draft.
 */
internal fun MedicationGroupEditorUiState.toDraftGroupReferences(
    pickingLocalId: String?,
): DraftGroupReferences? {
    if (isArchived) return null
    return DraftGroupReferences(
        groupId = editingGroupId?.let(UUID::fromString),
        medicineUuids = medications
            .filterNot { it.localId == pickingLocalId }
            .mapNotNullTo(mutableSetOf()) { it.medicineUuid },
    )
}

data class MedicationGroupMedicationItemUiState(
    val localId: String = UUID.randomUUID().toString(),
    val persistedMedicationId: String? = null,
    val resolvedMedicine: Medicine? = null,
    val medicineDraft: MedicinePickerUiState = defaultMedicineDraft(),
    val doseInstructionDraft: DoseInstructionDraftUiState =
        defaultMedicineDraft().toDoseInstructionDraft(),
    val applicationType: MedicationApplicationType = medicineDraft.catalogFilterApplicationType,
    val doseInstruction: DoseInstruction = DoseInstruction.Noop,
    val count: Int = 1,
) {
    val medicineUuid: UUID?
        get() = resolvedMedicine?.uuid ?: medicineDraft.selectedMedicineUuid

    fun toEditorUiState(): MedicationGroupMedicationEditorUiState {
        return MedicationGroupMedicationEditorUiState(
            localId = localId,
            persistedMedicationId = persistedMedicationId,
            resolvedMedicine = resolvedMedicine,
            medicineDraft = medicineDraft,
            doseInstructionDraft = doseInstructionDraft,
            countText = normalizeMedicationCount(applicationType, count).toString(),
        )
    }
}

data class MedicationGroupMedicationEditorUiState(
    val localId: String = UUID.randomUUID().toString(),
    val persistedMedicationId: String? = null,
    val resolvedMedicine: Medicine? = null,
    val medicineDraft: MedicinePickerUiState = defaultMedicineDraft(),
    val doseInstructionDraft: DoseInstructionDraftUiState =
        defaultMedicineDraft().toDoseInstructionDraft(),
    val countText: String = "1",
) {
    val count: Int
        get() = parseMedicationCountText(countText)
}

internal fun MedicationGroupMedicationEditorUiState.resolvedApplicationType(): MedicationApplicationType {
    return resolvedApplicationTypeForDose(
        preparationType = resolvedPreparationType(),
        doseInstructionDraft = doseInstructionDraft,
    )
}

internal fun MedicationGroupMedicationEditorUiState.resolvedPreparationType(): MedicinePreparationType {
    return resolvedMedicine?.preparation?.type
        ?: doseInstructionDraft.preparationType
}

// Builds an editor slot draft from a loaded domain medication. A PATCH_OFF slot
// carries no medicine; its picker draft keeps `resolvedMedicine` null.
internal fun MedicationGroupMedication.toItemUiState(): MedicationGroupMedicationItemUiState {
    val medicineDraft = if (medicine != null) {
        com.mkx.hrttracker.ui.medication.medicineDraftFromMedicine(medicine, applicationType)
    } else {
        defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
        )
    }
    return MedicationGroupMedicationItemUiState(
        localId = uuid.toString(),
        persistedMedicationId = uuid.toString(),
        resolvedMedicine = medicine,
        medicineDraft = medicineDraft,
        doseInstructionDraft = com.mkx.hrttracker.ui.medication.doseInstructionDraftFromInstruction(
            applicationType = applicationType,
            preparationType = medicine?.preparation?.type ?: when (applicationType) {
                MedicationApplicationType.PATCH_OFF -> MedicinePreparationType.PATCH_OFF
                MedicationApplicationType.ORAL,
                MedicationApplicationType.SUBLINGUAL,
                MedicationApplicationType.INJECTION,
                MedicationApplicationType.GEL,
                MedicationApplicationType.PATCH_ON -> MedicinePreparationType.PILL
            },
            doseInstruction = doseInstruction,
        ),
        applicationType = applicationType,
        doseInstruction = doseInstruction,
        count = normalizeMedicationCount(
            applicationType = applicationType,
            count = count,
            preparationType = medicine?.preparation?.type,
        ),
    )
}

data class MedicationGroupScheduleTimeUiState(
    val localId: String = UUID.randomUUID().toString(),
    val time: LocalTime,
    val originalTime: LocalTime? = null,
)
