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
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.ownsUnloggedOccurrence
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.systemLocale
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
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlanBatchAddViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    settingsRepository: SettingsRepository,
    appTimeSource: AppTimeSource,
) : ViewModel() {
    private val selectionState = MutableStateFlow(PlanBatchAddSelectionState())

    // Drives `today`/`now` so date-derived state (the date-range picker's upper
    // bound, the future-start guard) stays fresh while the screen is open —
    // currentMinute ticks each minute and re-reads on foreground/clock changes.
    private val currentDateTime = appTimeSource.currentMinute

    val uiState: StateFlow<PlanBatchAddUiState> = combine(
        medicationGroupRepository.observeGroups(),
        medicationLogRepository.observeEntries(),
        settingsRepository.settingsState,
        selectionState,
        currentDateTime,
    ) { groupsOrNull, entriesOrNull, settingsState, selection, now ->
        val groups = sortPlanMedicationGroups(groupsOrNull.orEmpty().filter(MedicationGroup::isActive))
        val entries = entriesOrNull.orEmpty()
        val selectedGroup = selection.selectedGroupUuid?.let { groupUuid ->
            groups.firstOrNull { group -> group.uuid == groupUuid }
        }
        val today = now.toLocalDate()
        // A not-yet-started plan (future `since`) has no past occurrences to
        // backfill, and defaulting startDate to `since` would invert the range
        // (start after end). Flag it so the UI shows a prompt instead.
        val selectedGroupStartsInFuture = selectedGroup != null &&
            selectedGroup.schedule.since.isAfter(today)
        val startDate = selection.startDate ?: selectedGroup?.schedule?.since ?: today
        val endDate = selection.endDate ?: today
        val entryPlan = if (selectedGroup != null) {
            buildPlanBatchAddEntryPlan(
                group = selectedGroup,
                existingEntries = entries,
                startDate = startDate,
                endDate = endDate,
                now = now,
            )
        } else {
            PlanBatchAddEntryPlan()
        }

        PlanBatchAddUiState(
            isLoading = groupsOrNull == null || entriesOrNull == null,
            groups = groups,
            selectedGroupUuid = selectedGroup?.uuid,
            selectedGroupName = selectedGroup?.name.orEmpty(),
            selectedGroupStartsInFuture = selectedGroupStartsInFuture,
            startDate = startDate,
            endDate = endDate,
            today = today,
            remindersEnabled = settingsState.remindersEnabled,
            firstDayOfWeek = settingsState.firstDayOfWeekOption.resolve(systemLocale()),
            nextOccurrencesByGroup = buildNextOccurrencesByGroup(
                groups = groups,
                entries = entries,
                start = now,
                limit = PLAN_BATCH_ADD_UPCOMING_OCCURRENCE_LIMIT
            ),
            entriesToAdd = entryPlan.entries,
            manualEntryCount = entryPlan.entries.count { entry -> entry.sourceGroupUuid == null },
            skippedEntryCount = entryPlan.skippedEntryCount,
            isSaving = selection.isSaving,
            isSaved = selection.isSaved,
            savedEntryCount = selection.savedEntryCount,
            saveResult = selection.saveResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlanBatchAddUiState(today = currentDateTime.value.toLocalDate()),
    )

    fun selectGroup(groupUuid: UUID) {
        if (selectionState.value.isSaving) {
            return
        }

        if (selectionState.value.selectedGroupUuid == groupUuid) {
            clearSelection()
            return
        }

        val group = uiState.value.groups.firstOrNull { group -> group.uuid == groupUuid } ?: return
        val today = uiState.value.today
        selectionState.update { state ->
            state.copy(
                selectedGroupUuid = groupUuid,
                startDate = group.schedule.since,
                endDate = today,
                isSaved = false,
                savedEntryCount = null,
                saveResult = null,
            )
        }
    }

    fun clearSelection() {
        selectionState.update { state ->
            state.copy(
                selectedGroupUuid = null,
                isSaved = false,
                savedEntryCount = null,
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
                savedEntryCount = null,
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
                savedEntryCount = null,
                saveResult = null,
            )
        }
    }

    fun saveSelectedRange() {
        val entriesToSave = uiState.value.entriesToAdd
        if (entriesToSave.isEmpty() || selectionState.value.isSaving) {
            return
        }

        selectionState.update { state ->
            state.copy(
                selectedGroupUuid = null,
                isSaving = true,
                isSaved = false,
                savedEntryCount = null,
                saveResult = null,
            )
        }

        viewModelScope.launch {
            val saveResult = runCatching {
                medicationLogRepository.saveBackfillEntries(entriesToSave)
            }.fold(
                onSuccess = { null },
                onFailure = { PlanBatchAddSaveResult.FAILURE },
            )
            val isSaved = saveResult == null
            if (isSaved) {
                runCatching { medicationReminderScheduler.rescheduleAll() }
            }
            selectionState.update { state ->
                if (isSaved) {
                    state.copy(
                        selectedGroupUuid = null,
                        isSaving = false,
                        isSaved = true,
                        savedEntryCount = entriesToSave.size,
                        saveResult = null,
                    )
                } else {
                    state.copy(
                        selectedGroupUuid = null,
                        isSaving = false,
                        isSaved = false,
                        savedEntryCount = null,
                        saveResult = saveResult,
                    )
                }
            }
        }
    }

    fun consumeSavedState() {
        selectionState.update { state ->
            state.copy(isSaved = false, savedEntryCount = null)
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
    val selectedGroupStartsInFuture: Boolean = false,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val remindersEnabled: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val nextOccurrencesByGroup: Map<UUID, List<LocalDateTime>> = emptyMap(),
    val entriesToAdd: List<MedicationLogEntryInput> = emptyList(),
    val manualEntryCount: Int = 0,
    val skippedEntryCount: Int = 0,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val savedEntryCount: Int? = null,
    val saveResult: PlanBatchAddSaveResult? = null,
) {
    val entryCount: Int
        get() = entriesToAdd.size

    val canConfirm: Boolean
        get() = selectedGroupUuid != null && entryCount > 0 && !isSaving
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
    val savedEntryCount: Int? = null,
    val saveResult: PlanBatchAddSaveResult? = null,
)

internal fun buildPlanBatchAddEntries(
    group: MedicationGroup,
    existingEntries: List<MedicationLogEntry> = emptyList(),
    startDate: LocalDate,
    endDate: LocalDate,
    now: LocalDateTime = LocalDateTime.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MedicationLogEntryInput> {
    return buildPlanBatchAddEntryPlan(
        group = group,
        existingEntries = existingEntries,
        startDate = startDate,
        endDate = endDate,
        now = now,
        zoneId = zoneId,
    ).entries
}

internal data class PlanBatchAddEntryPlan(
    val entries: List<MedicationLogEntryInput> = emptyList(),
    val skippedEntryCount: Int = 0,
)

internal fun buildPlanBatchAddEntryPlan(
    group: MedicationGroup,
    existingEntries: List<MedicationLogEntry> = emptyList(),
    startDate: LocalDate,
    endDate: LocalDate,
    now: LocalDateTime = LocalDateTime.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): PlanBatchAddEntryPlan {
    if (group.medications.isEmpty() || startDate.isAfter(endDate)) {
        return PlanBatchAddEntryPlan()
    }

    val fulfilledPlanSlots = existingEntries
        .asSequence()
        .filter { entry -> entry.sourceGroupUuid == group.uuid }
        .mapNotNull(MedicationLogEntry::scheduledFor)
        .toSet()

    val entries = mutableListOf<MedicationLogEntryInput>()
    var skippedEntryCount = 0

    buildPlanBatchAddOccurrences(
        schedule = group.schedule,
        startDate = startDate,
        endDate = endDate,
    ).forEach { occurrence ->
        if (occurrence.isAfter(now)) {
            return@forEach
        }
        val scheduleTime = group.schedule.timeSlots.firstOrNull { slot ->
            slot.time == occurrence.toLocalTime()
        } ?: return@forEach
        val isBeforePlanStart = occurrence.toLocalDate().isBefore(group.schedule.since)
        if (!isBeforePlanStart && !group.ownsUnloggedOccurrence(scheduleTime, occurrence)) {
            return@forEach
        }

        if (!isBeforePlanStart && occurrence in fulfilledPlanSlots) {
            skippedEntryCount += group.medications.size
            return@forEach
        }

        group.medications.forEach { medication ->
            entries += MedicationLogEntryInput(
                medicineUuid = medication.medicineUuid,
                applicationType = medication.applicationType,
                doseInstruction = medication.doseInstruction,
                sourceGroupUuid = if (isBeforePlanStart) null else group.uuid,
                scheduleTimeUuid = if (isBeforePlanStart) null else scheduleTime.uuid,
                appliedAt = occurrence.atZone(zoneId).toInstant(),
                scheduledFor = if (isBeforePlanStart) null else occurrence,
                count = medication.count,
                appliedAtTimeZoneId = zoneId.id,
            )
        }
    }

    return PlanBatchAddEntryPlan(
        entries = entries,
        skippedEntryCount = skippedEntryCount,
    )
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
            // Mirrors MedicationGroupSchedule.isScheduledOn's since-anchored
            // cadence. Diverges in that batch-add allows dates before `since`
            // (for backfill), so the date.isBefore(since) guard is dropped
            // and we use Math.floorDiv — JVM `/` truncates toward zero, which
            // for negative deltas would lump dates in the 6 days before
            // `since` into week 0 and push dates 7–13 days before into
            // week -1, breaking every-N-weeks parity around the boundary.
            val weekIndex = Math.floorDiv(ChronoUnit.DAYS.between(since, date), 7L)
            weekIndex % normalizedInterval.toLong() == 0L
        }
    }
}

private const val PLAN_BATCH_ADD_UPCOMING_OCCURRENCE_LIMIT = 3
