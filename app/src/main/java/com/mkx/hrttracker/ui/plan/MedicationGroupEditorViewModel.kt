package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleInput
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.nextAvailableMedicationGroupColor
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.ui.medication.MedicationDraftUiState
import com.mkx.hrttracker.ui.medication.defaultMedicationDraft
import com.mkx.hrttracker.ui.medication.medicationDraftFromDetails
import com.mkx.hrttracker.ui.medication.toMedicationDetails
import com.mkx.hrttracker.ui.medication.validationErrorRes
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MedicationGroupEditorViewModel @Inject constructor(
    private val medicationGroupRepository: MedicationGroupRepository,
    private val settingsRepository: SettingsRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(MedicationGroupEditorUiState())
    val uiState: StateFlow<MedicationGroupEditorUiState> = _uiState.asStateFlow()
    private val editingGroupId = savedStateHandle.get<String>(GROUP_ID_ARG)
    private val pendingGroupUuid = editingGroupId?.let { groupId ->
        runCatching { UUID.fromString(groupId) }.getOrNull()
    } ?: UUID.randomUUID()

    init {
        viewModelScope.launch {
            settingsRepository.settingsState.collect { settingsState ->
                _uiState.update { currentState ->
                    currentState.copy(remindersEnabled = settingsState.remindersEnabled)
                }
            }
        }

        viewModelScope.launch {
            medicationGroupRepository.observeGroups().collect { groupsOrNull ->
                _uiState.update { currentState ->
                    val visibleGroups = groupsOrNull.orEmpty()
                        .filterNot { group ->
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
                        defaultGroupName = defaultMedicationGroupName(
                            existingGroupCount = visibleGroups.size,
                            formatName = { index ->
                                context.getString(R.string.default_group_name_format, index)
                            }
                        ),
                        groupColorKey = resolvedGroupColorKey,
                        hasAssignedGroupColor = true
                    )
                }
            }
        }

        if (editingGroupId != null) {
            loadGroupForEditing(editingGroupId)
        }
    }

    fun updateGroupName(name: String) {
        _uiState.update {
            it.copy(groupName = name)
        }
    }

    fun updateScheduleType(scheduleType: MedicationGroupScheduleType) {
        _uiState.update {
            it.copy(scheduleType = scheduleType)
        }
    }

    fun updateWeeklyIntervalWeeks(intervalWeeks: String) {
        _uiState.update {
            it.copy(weeklyIntervalWeeks = parseScheduleInterval(intervalWeeks).toString())
        }
    }

    fun toggleWeeklyDayOfWeek(dayOfWeek: DayOfWeek) {
        _uiState.update {
            it.copy(weeklyDaysOfWeek = toggleWeeklyDaySelection(it.weeklyDaysOfWeek, dayOfWeek))
        }
    }

    fun updateWeeklyTime(time: LocalTime) {
        _uiState.update {
            it.copy(weeklyTime = time.withSecond(0).withNano(0))
        }
    }

    fun updateSinceDate(date: LocalDate) {
        _uiState.update {
            it.copy(sinceDate = date)
        }
    }

    fun updateDailyIntervalDays(intervalDays: String) {
        _uiState.update {
            it.copy(dailyIntervalDays = parseScheduleInterval(intervalDays).toString())
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(notificationsEnabled = enabled)
        }
    }

    fun addDailyTime() {
        _uiState.update {
            it.copy(
                dailyTimes = it.dailyTimes + MedicationGroupScheduleTimeUiState(
                    time = LocalTime.now().withSecond(0).withNano(0)
                )
            )
        }
    }

    fun updateDailyTime(localId: String, time: LocalTime) {
        _uiState.update { currentState ->
            currentState.copy(
                dailyTimes = currentState.dailyTimes.map { dailyTime ->
                    if (dailyTime.localId == localId) {
                        dailyTime.copy(time = time.withSecond(0).withNano(0))
                    } else {
                        dailyTime
                    }
                }
            )
        }
    }

    fun removeDailyTime(localId: String) {
        _uiState.update { currentState ->
            if (currentState.dailyTimes.size <= 1) {
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
            it.copy(
                editingMedication = MedicationGroupMedicationEditorUiState(),
                isMedicationEditorSaved = false,
                medicationEditorErrorMessageRes = null
            )
        }
    }

    fun removeMedication(localId: String) {
        _uiState.update {
            it.copy(
                medications = decrementMedicationCountOrRemove(
                    medications = it.medications,
                    localId = localId
                )
            )
        }
    }

    fun increaseMedicationCount(localId: String) {
        _uiState.update {
            it.copy(
                medications = incrementMedicationCount(
                    medications = it.medications,
                    localId = localId
                )
            )
        }
    }

    fun showMedicationEditor(localId: String) {
        _uiState.update { currentState ->
            currentState.copy(
                editingMedication = currentState.medications
                    .firstOrNull { it.localId == localId }
                    ?.toEditorUiState(),
                isMedicationEditorSaved = false,
                medicationEditorErrorMessageRes = null
            )
        }
    }

    fun dismissMedicationEditor() {
        _uiState.update {
            it.copy(
                editingMedication = null,
                isMedicationEditorSaved = false,
                medicationEditorErrorMessageRes = null
            )
        }
    }

    fun consumeMedicationEditorSaved() {
        _uiState.update {
            it.copy(
                isMedicationEditorSaved = false
            )
        }
    }

    fun updateEditingMedicationDraft(
        transform: (MedicationDraftUiState) -> MedicationDraftUiState
    ) {
        updateEditingMedication { medication ->
            medication.copy(draft = transform(medication.draft))
        }
    }

    fun saveEditingMedication() {
        val currentState = _uiState.value
        val editingMedication = currentState.editingMedication ?: return
        val errorRes = editingMedication.draft.validationErrorRes()

        if (errorRes != null) {
            _uiState.update { it.copy(medicationEditorErrorMessageRes = errorRes) }
            return
        }

        val savedMedication = MedicationGroupMedicationItemUiState(
            localId = editingMedication.localId,
            persistedMedicationId = editingMedication.persistedMedicationId,
            details = editingMedication.draft.toMedicationDetails(),
            count = editingMedication.count
        )

        _uiState.update {
            val existingIndex = it.medications.indexOfFirst { medication ->
                medication.localId == savedMedication.localId
            }
            val updatedMedications = if (existingIndex >= 0) {
                it.medications.toMutableList().apply {
                    this[existingIndex] = savedMedication
                }
            } else {
                it.medications + savedMedication
            }

            it.copy(
                medications = updatedMedications,
                editingMedication = savedMedication.toEditorUiState(),
                isMedicationEditorSaved = true,
                medicationEditorErrorMessageRes = null
            )
        }
    }

    fun saveGroup() {
        val currentState = _uiState.value
        val resolvedGroupName = resolveMedicationGroupName(
            groupName = currentState.groupName,
            defaultGroupName = currentState.defaultGroupName,
            isEditing = currentState.isEditing
        )
        if (resolvedGroupName.isEmpty() || currentState.medications.isEmpty()) {
            return
        }

        val parsedWeeklyInterval = parseScheduleInterval(currentState.weeklyIntervalWeeks)
        val parsedDailyInterval = parseScheduleInterval(currentState.dailyIntervalDays)
        val resolvedWeeklyDays = currentState.weeklyDaysOfWeek.ifEmpty {
            setOf(LocalDate.now().dayOfWeek)
        }
        val resolvedDailyTimes = currentState.dailyTimes.ifEmpty {
            listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0)))
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val savedGroupUuid = medicationGroupRepository.saveGroup(
                uuid = currentState.editingGroupId?.let(UUID::fromString),
                name = resolvedGroupName,
                colorKey = currentState.groupColorKey,
                schedule = when (currentState.scheduleType) {
                    MedicationGroupScheduleType.WEEKLY -> MedicationGroupScheduleInput(
                        type = MedicationGroupScheduleType.WEEKLY,
                        interval = parsedWeeklyInterval,
                        since = currentState.sinceDate,
                        weeklyDaysOfWeek = resolvedWeeklyDays,
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
                notificationsEnabled = currentState.notificationsEnabled
            )
            medicationReminderScheduler.rescheduleGroup(savedGroupUuid)

            _uiState.update {
                it.copy(
                    groupName = resolvedGroupName,
                    isSaving = false,
                    isSaved = true
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

    fun deleteGroup() {
        val groupId = _uiState.value.editingGroupId ?: return
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDeleting = true,
                    isDeleteConfirmationVisible = false
                )
            }

            medicationReminderScheduler.cancelReminder(uuid)
            medicationGroupRepository.deleteGroup(uuid)

            _uiState.update {
                it.copy(
                    isDeleting = false,
                    isDeleted = true
                )
            }
        }
    }

    fun consumeSavedState() {
        _uiState.update { it.copy(isSaved = false) }
    }

    fun consumeDeletedState() {
        _uiState.update { it.copy(isDeleted = false) }
    }

    private fun loadGroupForEditing(groupId: String) {
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull() ?: return

        viewModelScope.launch {
            val group = medicationGroupRepository.getGroup(uuid) ?: return@launch
            val remindersEnabled = settingsRepository.getCurrentSettings().remindersEnabled

            _uiState.value = MedicationGroupEditorUiState(
                editingGroupId = group.uuid.toString(),
                groupName = group.name,
                defaultGroupName = group.name,
                scheduleType = group.schedule.type,
                sinceDate = group.schedule.since,
                weeklyIntervalWeeks = if (group.schedule.type == MedicationGroupScheduleType.WEEKLY) {
                    parseScheduleInterval(group.schedule.interval.toString()).toString()
                } else {
                    "1"
                },
                weeklyDaysOfWeek = group.schedule.weeklyDaysOfWeek.ifEmpty {
                    setOf(LocalDate.now().dayOfWeek)
                },
                weeklyTime = group.schedule.times.firstOrNull() ?: LocalTime.of(9, 0),
                dailyIntervalDays = if (group.schedule.type == MedicationGroupScheduleType.DAILY) {
                    parseScheduleInterval(group.schedule.interval.toString()).toString()
                } else {
                    "1"
                },
                dailyTimes = if (group.schedule.type == MedicationGroupScheduleType.DAILY) {
                    group.schedule.times
                        .ifEmpty { listOf(LocalTime.of(9, 0)) }
                        .sorted()
                        .map { time ->
                        MedicationGroupScheduleTimeUiState(time = time)
                    }
                } else {
                    listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0)))
                },
                remindersEnabled = remindersEnabled,
                notificationsEnabled = group.notificationsEnabled,
                groupColorKey = group.colorKey,
                hasAssignedGroupColor = true,
                medications = group.medications.map { medication ->
                    MedicationGroupMedicationItemUiState(
                        localId = medication.uuid.toString(),
                        persistedMedicationId = medication.uuid.toString(),
                        details = medication.details,
                        count = medication.count
                    )
                }
            )
        }
    }

    private fun updateEditingMedication(
        transform: (MedicationGroupMedicationEditorUiState) -> MedicationGroupMedicationEditorUiState
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                editingMedication = currentState.editingMedication?.let(transform),
                medicationEditorErrorMessageRes = null
            )
        }
    }

    companion object {
        const val GROUP_ID_ARG = "groupId"
    }
}

internal fun toggleWeeklyDaySelection(
    selectedDays: Set<DayOfWeek>,
    dayOfWeek: DayOfWeek
): Set<DayOfWeek> {
    return if (dayOfWeek in selectedDays) {
        if (selectedDays.size == 1) {
            selectedDays
        } else {
            selectedDays - dayOfWeek
        }
    } else {
        selectedDays + dayOfWeek
    }
}

internal fun incrementMedicationCount(
    medications: List<MedicationGroupMedicationItemUiState>,
    localId: String
): List<MedicationGroupMedicationItemUiState> {
    return medications.map { medication ->
        if (medication.localId == localId) {
            medication.copy(count = medication.count + 1)
        } else {
            medication
        }
    }
}

internal fun decrementMedicationCountOrRemove(
    medications: List<MedicationGroupMedicationItemUiState>,
    localId: String
): List<MedicationGroupMedicationItemUiState> {
    return medications.flatMap { medication ->
        if (medication.localId != localId) {
            listOf(medication)
        } else if (medication.count <= 1) {
            emptyList()
        } else {
            listOf(medication.copy(count = medication.count - 1))
        }
    }
}

data class MedicationGroupEditorUiState(
    val editingGroupId: String? = null,
    val groupName: String = "",
    val defaultGroupName: String = "",
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
    val medications: List<MedicationGroupMedicationItemUiState> = emptyList(),
    val editingMedication: MedicationGroupMedicationEditorUiState? = null,
    val isMedicationEditorSaved: Boolean = false,
    val medicationEditorErrorMessageRes: Int? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isDeleteConfirmationVisible: Boolean = false,
) {
    val isEditing: Boolean
        get() = editingGroupId != null
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
            count = count
        )
    }
}

data class MedicationGroupMedicationEditorUiState(
    val localId: String = UUID.randomUUID().toString(),
    val persistedMedicationId: String? = null,
    val draft: MedicationDraftUiState = defaultMedicationDraft(),
    val count: Int = 1,
)

data class MedicationGroupScheduleTimeUiState(
    val localId: String = UUID.randomUUID().toString(),
    val time: LocalTime,
)
