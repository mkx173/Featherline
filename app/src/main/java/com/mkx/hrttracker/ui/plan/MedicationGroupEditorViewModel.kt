package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleInput
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MedicationGroupEditorViewModel @Inject constructor(
    private val medicationGroupRepository: MedicationGroupRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(MedicationGroupEditorUiState())
    val uiState: StateFlow<MedicationGroupEditorUiState> = _uiState.asStateFlow()
    private val editingGroupId = savedStateHandle.get<String>(GROUP_ID_ARG)

    init {
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

    fun updateWeeklyDayOfWeek(dayOfWeek: DayOfWeek) {
        _uiState.update {
            it.copy(
                weeklyDayOfWeek = dayOfWeek,
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
                editingMedication = MedicationGroupMedicationItemUiState(),
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
                editingMedication = currentState.medications.firstOrNull { it.localId == localId },
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

    fun updateEditingMedicationRoute(routeOfAdministration: RouteOfAdministration) {
        updateEditingMedication { medication ->
            medication.copy(routeOfAdministration = routeOfAdministration)
        }
    }

    fun updateEditingMedicationName(medicineName: String) {
        updateEditingMedication { medication ->
            medication.copy(medicineName = medicineName)
        }
    }

    fun updateEditingMedicationDosage(dosageMg: String) {
        updateEditingMedication { medication ->
            medication.copy(dosageMg = dosageMg)
        }
    }

    fun saveEditingMedication() {
        val currentState = _uiState.value
        val editingMedication = currentState.editingMedication ?: return
        val trimmedName = editingMedication.medicineName.trim()
        val parsedDose = editingMedication.dosageMg.toDoubleOrNull()

        val errorRes = when {
            trimmedName.isEmpty() -> R.string.validation_group_medication_name_required
            parsedDose == null || parsedDose <= 0.0 -> R.string.validation_group_medication_dose_required
            else -> null
        }

        if (errorRes != null) {
            _uiState.update { it.copy(medicationEditorErrorMessageRes = errorRes) }
            return
        }

        val savedMedication = editingMedication.copy(
            medicineName = trimmedName,
            dosageMg = parsedDose!!.toInputString()
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
                editingMedication = savedMedication,
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

            medicationGroupRepository.saveGroup(
                uuid = currentState.editingGroupId?.let(UUID::fromString),
                name = trimmedGroupName,
                schedule = when (currentState.scheduleType) {
                    MedicationGroupScheduleType.WEEKLY -> MedicationGroupScheduleInput(
                        type = MedicationGroupScheduleType.WEEKLY,
                        interval = parsedWeeklyInterval!!,
                        since = currentState.sinceDate,
                        weeklyDayOfWeek = currentState.weeklyDayOfWeek,
                        times = listOf(currentState.weeklyTime)
                    )
                    MedicationGroupScheduleType.DAILY -> MedicationGroupScheduleInput(
                        type = MedicationGroupScheduleType.DAILY,
                        interval = parsedDailyInterval!!,
                        since = currentState.sinceDate,
                        weeklyDayOfWeek = null,
                        times = currentState.dailyTimes
                            .map(MedicationGroupScheduleTimeUiState::time)
                            .sorted()
                    )
                },
                medications = currentState.medications.map { medication ->
                    MedicationGroupMedicationInput(
                        uuid = medication.persistedMedicationId?.let(UUID::fromString),
                        routeOfAdministration = medication.routeOfAdministration,
                        medicineName = medication.medicineName.trim(),
                        dosageMgAsMedicine = medication.dosageMg.toDouble()
                    )
                }
            )

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
                weeklyDayOfWeek = group.schedule.weeklyDayOfWeek ?: DayOfWeek.MONDAY,
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
                medications = group.medications.map { medication ->
                    MedicationGroupMedicationItemUiState(
                        localId = medication.uuid.toString(),
                        persistedMedicationId = medication.uuid.toString(),
                        routeOfAdministration = medication.routeOfAdministration,
                        medicineName = medication.medicineName,
                        dosageMg = medication.dosageMgAsMedicine.toInputString()
                    )
                }
            )
        }
    }

    private fun updateEditingMedication(
        transform: (MedicationGroupMedicationItemUiState) -> MedicationGroupMedicationItemUiState
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                editingMedication = currentState.editingMedication?.let(transform),
                medicationEditorErrorMessageRes = null
            )
        }
    }

    private fun Double.toInputString(): String {
        return if (this % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", this)
        } else {
            String.format(Locale.US, "%.2f", this)
        }
    }

    companion object {
        const val GROUP_ID_ARG = "groupId"
    }
}

data class MedicationGroupEditorUiState(
    val editingGroupId: String? = null,
    val groupName: String = "",
    val scheduleType: MedicationGroupScheduleType = MedicationGroupScheduleType.WEEKLY,
    val sinceDate: LocalDate = LocalDate.now(),
    val weeklyIntervalWeeks: String = "1",
    val weeklyDayOfWeek: DayOfWeek = LocalDate.now().dayOfWeek,
    val weeklyTime: LocalTime = LocalTime.of(9, 0),
    val dailyIntervalDays: String = "1",
    val dailyTimes: List<MedicationGroupScheduleTimeUiState> = listOf(
        MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0))
    ),
    val medications: List<MedicationGroupMedicationItemUiState> = emptyList(),
    val editingMedication: MedicationGroupMedicationItemUiState? = null,
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
    val routeOfAdministration: RouteOfAdministration = RouteOfAdministration.OTHER,
    val medicineName: String = "",
    val dosageMg: String = "",
)

data class MedicationGroupScheduleTimeUiState(
    val localId: String = UUID.randomUUID().toString(),
    val time: LocalTime,
)
