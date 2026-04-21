package com.mkx.hrttracker.ui.plan

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
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.ui.medication.MedicationDraftUiState
import com.mkx.hrttracker.ui.medication.defaultMedicationDraft
import com.mkx.hrttracker.ui.medication.medicationDraftFromDetails
import com.mkx.hrttracker.ui.medication.toMedicationDetails
import com.mkx.hrttracker.ui.medication.validationErrorRes
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(MedicationGroupEditorUiState())
    val uiState: StateFlow<MedicationGroupEditorUiState> = _uiState.asStateFlow()
    private val editingGroupId = savedStateHandle.get<String>(GROUP_ID_ARG)

    init {
        viewModelScope.launch {
            settingsRepository.settingsState.collect { settingsState ->
                _uiState.update { currentState ->
                    currentState.copy(remindersEnabled = settingsState.remindersEnabled)
                }
            }
        }

        if (editingGroupId != null) {
            loadGroupForEditing(editingGroupId)
        }
    }

    fun updateGroupName(name: String) {
        _uiState.update {
            it.copy(
                groupName = name,
                errorMessageRes = null
            )
        }
    }

    fun updateScheduleType(scheduleType: MedicationGroupScheduleType) {
        _uiState.update {
            it.copy(
                scheduleType = scheduleType,
                errorMessageRes = null
            )
        }
    }

    fun updateWeeklyIntervalWeeks(intervalWeeks: String) {
        _uiState.update {
            it.copy(
                weeklyIntervalWeeks = intervalWeeks,
                errorMessageRes = null
            )
        }
    }

    fun toggleWeeklyDayOfWeek(dayOfWeek: DayOfWeek) {
        _uiState.update {
            it.copy(
                weeklyDaysOfWeek = toggleWeeklyDaySelection(it.weeklyDaysOfWeek, dayOfWeek),
                errorMessageRes = null
            )
        }
    }

    fun updateWeeklyTime(time: LocalTime) {
        _uiState.update {
            it.copy(
                weeklyTime = time.withSecond(0).withNano(0),
                errorMessageRes = null
            )
        }
    }

    fun updateSinceDate(date: LocalDate) {
        _uiState.update {
            it.copy(
                sinceDate = date,
                errorMessageRes = null
            )
        }
    }

    fun updateDailyIntervalDays(intervalDays: String) {
        _uiState.update {
            it.copy(
                dailyIntervalDays = intervalDays,
                errorMessageRes = null
            )
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                notificationsEnabled = enabled,
                errorMessageRes = null
            )
        }
    }

    fun addDailyTime() {
        _uiState.update {
            it.copy(
                dailyTimes = it.dailyTimes + MedicationGroupScheduleTimeUiState(
                    time = LocalTime.now().withSecond(0).withNano(0)
                ),
                errorMessageRes = null
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
                },
                errorMessageRes = null
            )
        }
    }

    fun removeDailyTime(localId: String) {
        _uiState.update {
            it.copy(
                dailyTimes = it.dailyTimes.filterNot { dailyTime -> dailyTime.localId == localId },
                errorMessageRes = null
            )
        }
    }

    fun showAddMedicationEditor() {
        _uiState.update {
            it.copy(
                editingMedication = MedicationGroupMedicationEditorUiState(),
                isMedicationEditorSaved = false,
                medicationEditorErrorMessageRes = null,
                errorMessageRes = null
            )
        }
    }

    fun removeMedication(localId: String) {
        _uiState.update {
            it.copy(
                medications = it.medications.filterNot { medication -> medication.localId == localId },
                errorMessageRes = null
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
                medicationEditorErrorMessageRes = null,
                errorMessageRes = null
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
            details = editingMedication.draft.toMedicationDetails()
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
                medicationEditorErrorMessageRes = null,
                errorMessageRes = null
            )
        }
    }

    fun saveGroup() {
        val currentState = _uiState.value
        val trimmedGroupName = currentState.groupName.trim()
        val parsedWeeklyInterval = currentState.weeklyIntervalWeeks.toIntOrNull()
        val parsedDailyInterval = currentState.dailyIntervalDays.toIntOrNull()

        val errorRes = when {
            trimmedGroupName.isEmpty() -> R.string.validation_group_name_required
            currentState.scheduleType == MedicationGroupScheduleType.WEEKLY &&
                (parsedWeeklyInterval == null || parsedWeeklyInterval <= 0) ->
                R.string.validation_group_weekly_interval_required
            currentState.scheduleType == MedicationGroupScheduleType.WEEKLY &&
                currentState.weeklyDaysOfWeek.isEmpty() ->
                R.string.validation_group_weekly_days_required
            currentState.scheduleType == MedicationGroupScheduleType.DAILY &&
                (parsedDailyInterval == null || parsedDailyInterval <= 0) ->
                R.string.validation_group_daily_interval_required
            currentState.scheduleType == MedicationGroupScheduleType.DAILY &&
                currentState.dailyTimes.isEmpty() ->
                R.string.validation_group_daily_times_required
            currentState.medications.isEmpty() -> R.string.validation_group_medications_required
            else -> null
        }

        if (errorRes != null) {
            _uiState.update { it.copy(errorMessageRes = errorRes) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }

            val savedGroupUuid = medicationGroupRepository.saveGroup(
                uuid = currentState.editingGroupId?.let(UUID::fromString),
                name = trimmedGroupName,
                schedule = when (currentState.scheduleType) {
                    MedicationGroupScheduleType.WEEKLY -> MedicationGroupScheduleInput(
                        type = MedicationGroupScheduleType.WEEKLY,
                        interval = parsedWeeklyInterval!!,
                        since = currentState.sinceDate,
                        weeklyDaysOfWeek = currentState.weeklyDaysOfWeek,
                        times = listOf(currentState.weeklyTime)
                    )
                    MedicationGroupScheduleType.DAILY -> MedicationGroupScheduleInput(
                        type = MedicationGroupScheduleType.DAILY,
                        interval = parsedDailyInterval!!,
                        since = currentState.sinceDate,
                        weeklyDaysOfWeek = emptySet(),
                        times = currentState.dailyTimes
                            .map(MedicationGroupScheduleTimeUiState::time)
                            .sorted()
                    )
                },
                medications = currentState.medications.map { medication ->
                    MedicationGroupMedicationInput(
                        uuid = medication.persistedMedicationId?.let(UUID::fromString),
                        details = medication.details
                    )
                },
                notificationsEnabled = currentState.notificationsEnabled
            )
            medicationReminderScheduler.rescheduleGroup(savedGroupUuid)

            _uiState.update {
                it.copy(
                    groupName = trimmedGroupName,
                    isSaving = false,
                    isSaved = true,
                    errorMessageRes = null
                )
            }
        }
    }

    fun showDeleteConfirmation() {
        if (_uiState.value.isEditing) {
            _uiState.update {
                it.copy(
                    isDeleteConfirmationVisible = true,
                    errorMessageRes = null
                )
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
                    isDeleteConfirmationVisible = false,
                    errorMessageRes = null
                )
            }

            medicationReminderScheduler.cancelReminder(uuid)
            medicationGroupRepository.deleteGroup(uuid)

            _uiState.update {
                it.copy(
                    isDeleting = false,
                    isDeleted = true,
                    errorMessageRes = null
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
                scheduleType = group.schedule.type,
                sinceDate = group.schedule.since,
                weeklyIntervalWeeks = if (group.schedule.type == MedicationGroupScheduleType.WEEKLY) {
                    group.schedule.interval.toString()
                } else {
                    "1"
                },
                weeklyDaysOfWeek = group.schedule.weeklyDaysOfWeek.ifEmpty {
                    setOf(DayOfWeek.MONDAY)
                },
                weeklyTime = group.schedule.times.firstOrNull() ?: LocalTime.of(9, 0),
                dailyIntervalDays = if (group.schedule.type == MedicationGroupScheduleType.DAILY) {
                    group.schedule.interval.toString()
                } else {
                    "1"
                },
                dailyTimes = if (group.schedule.type == MedicationGroupScheduleType.DAILY) {
                    group.schedule.times.sorted().map { time ->
                        MedicationGroupScheduleTimeUiState(time = time)
                    }
                } else {
                    listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0)))
                },
                remindersEnabled = remindersEnabled,
                notificationsEnabled = group.notificationsEnabled,
                groupColorKey = group.colorKey,
                medications = group.medications.map { medication ->
                    MedicationGroupMedicationItemUiState(
                        localId = medication.uuid.toString(),
                        persistedMedicationId = medication.uuid.toString(),
                        details = medication.details
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
        selectedDays - dayOfWeek
    } else {
        selectedDays + dayOfWeek
    }
}

data class MedicationGroupEditorUiState(
    val editingGroupId: String? = null,
    val groupName: String = "",
    val groupColorKey: MedicationGroupColorKey = MedicationGroupColorKey.ROSE,
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
    val errorMessageRes: Int? = null,
) {
    val isEditing: Boolean
        get() = editingGroupId != null
}

data class MedicationGroupMedicationItemUiState(
    val localId: String = UUID.randomUUID().toString(),
    val persistedMedicationId: String? = null,
    val details: MedicationDetails = defaultMedicationDraft().toMedicationDetails(),
) {
    fun toEditorUiState(): MedicationGroupMedicationEditorUiState {
        return MedicationGroupMedicationEditorUiState(
            localId = localId,
            persistedMedicationId = persistedMedicationId,
            draft = medicationDraftFromDetails(details)
        )
    }
}

data class MedicationGroupMedicationEditorUiState(
    val localId: String = UUID.randomUUID().toString(),
    val persistedMedicationId: String? = null,
    val draft: MedicationDraftUiState = defaultMedicationDraft(),
)

data class MedicationGroupScheduleTimeUiState(
    val localId: String = UUID.randomUUID().toString(),
    val time: LocalTime,
)
