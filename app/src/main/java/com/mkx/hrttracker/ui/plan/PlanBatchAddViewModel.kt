package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogEntryInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlanBatchAddViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val selectionState = MutableStateFlow(PlanBatchAddSelectionState())

    val uiState: StateFlow<PlanBatchAddUiState> = combine(
        medicationGroupRepository.observeGroups(),
        medicationLogRepository.observeEntries(),
        settingsRepository.settingsState,
        selectionState,
    ) { groupsOrNull, entriesOrNull, settingsState, selection ->
        val now = LocalDateTime.now()
        val groups = sortPlanMedicationGroups(groupsOrNull.orEmpty())
        val entries = entriesOrNull.orEmpty()
        val selectedGroup = selection.selectedGroupUuid?.let { groupUuid ->
            groups.firstOrNull { group -> group.uuid == groupUuid }
        }
        val startDate = selection.startDate ?: selectedGroup?.schedule?.since
        val endDate = selection.endDate ?: now.toLocalDate()
        val entryInputs = if (selectedGroup != null && startDate != null && endDate != null) {
            buildPlanBatchAddEntries(
                group = selectedGroup,
                startDate = startDate,
                endDate = endDate,
            )
        } else {
            emptyList()
        }

        PlanBatchAddUiState(
            isLoading = groupsOrNull == null || entriesOrNull == null,
            groups = groups,
            selectedGroupUuid = selectedGroup?.uuid,
            selectedGroupName = selectedGroup?.name.orEmpty(),
            startDate = startDate,
            endDate = endDate,
            today = now.toLocalDate(),
            remindersEnabled = settingsState.remindersEnabled,
            nextOccurrencesByGroup = buildNextOccurrencesByGroup(
                groups = groups,
                entries = entries,
                start = now,
                limit = PLAN_BATCH_ADD_UPCOMING_OCCURRENCE_LIMIT
            ),
            entriesToAdd = entryInputs,
            manualEntryCount = entryInputs.count { entry -> entry.sourceGroupUuid == null },
            isSaving = selection.isSaving,
            isSaved = selection.isSaved,
            saveResult = selection.saveResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlanBatchAddUiState(),
    )

    fun selectGroup(groupUuid: UUID) {
        val group = uiState.value.groups.firstOrNull { group -> group.uuid == groupUuid } ?: return
        selectionState.update { state ->
            state.copy(
                selectedGroupUuid = groupUuid,
                startDate = group.schedule.since,
                endDate = LocalDate.now(),
                isSaved = false,
                saveResult = null,
            )
        }
    }

    fun updateStartDate(date: LocalDate) {
        selectionState.update { state ->
            state.copy(
                startDate = date,
                endDate = state.endDate?.let { endDate ->
                    if (date.isAfter(endDate)) date else endDate
                },
                isSaved = false,
                saveResult = null,
            )
        }
    }

    fun updateEndDate(date: LocalDate) {
        selectionState.update { state ->
            state.copy(
                startDate = state.startDate?.let { startDate ->
                    if (date.isBefore(startDate)) date else startDate
                },
                endDate = date,
                isSaved = false,
                saveResult = null,
            )
        }
    }

    fun saveSelectedRange() {
        val entriesToSave = uiState.value.entriesToAdd
        if (entriesToSave.isEmpty() || selectionState.value.isSaving) {
            return
        }

        viewModelScope.launch {
            selectionState.update { state ->
                state.copy(isSaving = true, isSaved = false, saveResult = null)
            }
            val saveResult = runCatching {
                medicationLogRepository.saveNewEntries(entriesToSave)
            }.fold(
                onSuccess = { null },
                onFailure = { PlanBatchAddSaveResult.FAILURE },
            )
            val isSaved = saveResult == null
            if (isSaved) {
                runCatching { medicationReminderScheduler.rescheduleAll() }
            }
            selectionState.update { state ->
                state.copy(
                    isSaving = false,
                    isSaved = isSaved,
                    saveResult = saveResult,
                )
            }
        }
    }

    fun consumeSavedState() {
        selectionState.update { state ->
            state.copy(isSaved = false)
        }
    }

    fun onSaveResultConsumed() {
        selectionState.update { state ->
            state.copy(saveResult = null)
        }
    }
}

data class PlanBatchAddUiState(
    val isLoading: Boolean = true,
    val groups: List<MedicationGroup> = emptyList(),
    val selectedGroupUuid: UUID? = null,
    val selectedGroupName: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val today: LocalDate = LocalDate.now(),
    val remindersEnabled: Boolean = true,
    val nextOccurrencesByGroup: Map<UUID, List<LocalDateTime>> = emptyMap(),
    val entriesToAdd: List<MedicationLogEntryInput> = emptyList(),
    val manualEntryCount: Int = 0,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveResult: PlanBatchAddSaveResult? = null,
) {
    val entryCount: Int
        get() = entriesToAdd.size

    val canConfirm: Boolean
        get() = entryCount > 0 && !isSaving
}

enum class PlanBatchAddSaveResult {
    FAILURE,
}

private data class PlanBatchAddSelectionState(
    val selectedGroupUuid: UUID? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveResult: PlanBatchAddSaveResult? = null,
)

internal fun buildPlanBatchAddEntries(
    group: MedicationGroup,
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MedicationLogEntryInput> {
    if (group.medications.isEmpty() || startDate.isAfter(endDate)) {
        return emptyList()
    }

    return buildPlanBatchAddOccurrences(
        schedule = group.schedule,
        startDate = startDate,
        endDate = endDate,
    ).flatMap { occurrence ->
        val isBeforePlanStart = occurrence.toLocalDate().isBefore(group.schedule.since)
        group.medications.map { medication ->
            MedicationLogEntryInput(
                medication = medication.details,
                sourceGroupUuid = if (isBeforePlanStart) null else group.uuid,
                appliedAt = occurrence.atZone(zoneId).toInstant(),
                scheduledFor = if (isBeforePlanStart) null else occurrence,
                count = medication.count,
                appliedAtTimeZoneId = zoneId.id,
            )
        }
    }
}

internal fun buildPlanBatchAddOccurrences(
    schedule: MedicationGroupSchedule,
    startDate: LocalDate,
    endDate: LocalDate,
): List<LocalDateTime> {
    if (startDate.isAfter(endDate) || schedule.times.isEmpty()) {
        return emptyList()
    }

    val sortedTimes = schedule.times.sorted()
    val result = mutableListOf<LocalDateTime>()
    var currentDate = startDate
    while (!currentDate.isAfter(endDate)) {
        if (schedule.isScheduledOnForBatchAdd(currentDate)) {
            sortedTimes.forEach { time ->
                result += LocalDateTime.of(currentDate, time)
            }
        }
        currentDate = currentDate.plusDays(1)
    }
    return result
}

private fun MedicationGroupSchedule.isScheduledOnForBatchAdd(date: LocalDate): Boolean {
    val normalizedInterval = interval.coerceAtLeast(1)

    return when (type) {
        MedicationGroupScheduleType.DAILY ->
            ChronoUnit.DAYS.between(since, date) % normalizedInterval.toLong() == 0L

        MedicationGroupScheduleType.WEEKLY -> {
            if (weeklyDaysOfWeek.isEmpty() || date.dayOfWeek !in weeklyDaysOfWeek) {
                return false
            }
            val sinceWeekStart = since.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val dateWeekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeksBetween = ChronoUnit.WEEKS.between(sinceWeekStart, dateWeekStart)
            weeksBetween % normalizedInterval.toLong() == 0L
        }
    }
}

private const val PLAN_BATCH_ADD_UPCOMING_OCCURRENCE_LIMIT = 3
