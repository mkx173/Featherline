package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.PlanDaySchedule
import com.mkx.hrttracker.model.medication.buildPlanDaySchedule
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.isSlotFulfilled
import com.mkx.hrttracker.model.medication.nextOccurrencesInPlanWindowFrom
import com.mkx.hrttracker.model.medication.visibleMedicationEntries
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.systemLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mkx.hrttracker.util.tickWhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    medicationLogRepository: MedicationLogRepository,
    settingsRepository: SettingsRepository,
    appTimeSource: AppTimeSource
) : ViewModel() {
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val _uiState = MutableStateFlow(
        // Seed from the repositories' hot caches (both are Eagerly-shared
        // StateFlows loaded at app start) so the first frame after this
        // ViewModel is created renders real data. A bare loading default
        // flashed the loading indicator for the few frames until the
        // combine's first emission — previously hidden by the top-level
        // navigation transition, visible once that transition was removed.
        buildUiState(
            groupsOrNull = medicationGroupRepository.getCachedGroups(),
            entriesOrNull = medicationLogRepository.getCachedEntries(),
            settingsState = settingsRepository.settingsState.value,
            selection = null,
            now = appTimeSource.currentMinute.value,
        )
    )

    // The ui-state combine below stays collected for the ViewModel's whole
    // (activity-scoped) lifetime: a stop-timeout would let data mutated while
    // away (e.g. a backup restore) flash one stale frame on re-entry, so data
    // emissions must keep rebuilding the retained value in the background.
    // The time input, however, is gated on this state's own subscriber count:
    // a live minute tick would otherwise rebuild the ui state every minute for
    // the activity's whole lifetime, including backgrounded. While
    // unsubscribed only date changes pass through, keeping the midnight
    // re-anchor.
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    private val currentDateTime =
        appTimeSource.currentMinute.tickWhileSubscribed(_uiState.subscriptionCount) { minute ->
            minute.toLocalDate()
        }

    init {
        viewModelScope.launch {
            combine(
                medicationGroupRepository.observeGroups(),
                medicationLogRepository.observeEntries(),
                settingsRepository.settingsState,
                selectedDate,
                currentDateTime
            ) { groupsOrNull, entriesOrNull, settingsState, selection, now ->
                buildUiState(
                    groupsOrNull = groupsOrNull,
                    entriesOrNull = entriesOrNull,
                    settingsState = settingsState,
                    selection = selection,
                    now = now,
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    private fun buildUiState(
        groupsOrNull: List<MedicationGroup>?,
        entriesOrNull: List<MedicationLogEntry>?,
        settingsState: SettingsState,
        selection: LocalDate?,
        now: LocalDateTime,
    ): PlanUiState {
        val isLoading = groupsOrNull == null || entriesOrNull == null
        val allGroups = sortPlanMedicationGroups(groupsOrNull.orEmpty())
        val activeGroups = allGroups.filter(MedicationGroup::isActive)
        val entries = visibleMedicationEntries(
            entries = entriesOrNull.orEmpty(),
            groups = allGroups,
            showArchivedGroupRecords = settingsState.showArchivedGroupRecords,
        )
        val scheduleGroups = if (settingsState.showArchivedGroupRecords) allGroups else activeGroups
        val today = now.toLocalDate()
        val calendarRange = buildPlanCalendarRange(
            today = today,
            firstDayOfWeek = settingsState.firstDayOfWeekOption.resolve(systemLocale())
        )
        val displayedDate = selection ?: today
        val daySchedule = buildPlanDaySchedule(
            date = displayedDate,
            groups = scheduleGroups,
            entries = entries,
            now = now,
            includeUnloggedArchivedSlots = false,
            unloggedArchivedSlotCutoff = now,
        )
        val nextOccurrencesByGroup = buildNextOccurrencesByGroup(
            groups = activeGroups,
            entries = entries,
            start = now,
            limit = UPCOMING_OCCURRENCES_LIMIT
        )

        return PlanUiState(
            isLoading = isLoading,
            now = now,
            today = today,
            calendarFirstDayOfWeek = calendarRange.firstDayOfWeek,
            calendarStartDate = calendarRange.startDate,
            calendarEndDate = calendarRange.endDate,
            selectedDate = selection,
            entries = entries,
            medicationGroups = activeGroups,
            scheduleMedicationGroups = scheduleGroups,
            remindersEnabled = settingsState.remindersEnabled,
            calendarDays = buildPlanCalendarDayUiStateIncludingDisplayedDate(
                groups = scheduleGroups,
                entries = entries,
                startDate = calendarRange.startDate,
                endDate = calendarRange.endDate,
                displayedDate = displayedDate,
                includeUnloggedArchivedSlots = false,
                unloggedArchivedSlotCutoff = now,
            ),
            daySchedule = daySchedule,
            nextOccurrencesByGroup = nextOccurrencesByGroup
        )
    }

    fun toggleSelectedDate(date: LocalDate) {
        selectedDate.value = if (date == uiState.value.today || selectedDate.value == date) {
            null
        } else {
            date
        }
    }

    fun clearSelectedDate() {
        if (selectedDate.value != null) {
            selectedDate.value = null
        }
    }

    private companion object {
        const val UPCOMING_OCCURRENCES_LIMIT = 3
    }
}

internal fun sortPlanMedicationGroups(groups: List<MedicationGroup>): List<MedicationGroup> {
    return groups.sortedWith(
        compareBy<MedicationGroup> { it.createdAt }
            .thenBy { it.uuid }
    )
}

private fun buildPlanCalendarDayUiStateIncludingDisplayedDate(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    startDate: LocalDate,
    endDate: LocalDate,
    displayedDate: LocalDate,
    includeUnloggedArchivedSlots: Boolean = true,
    unloggedArchivedSlotCutoff: LocalDateTime? = null,
): Map<LocalDate, PlanCalendarDayUiState> {
    val calendarDays = buildPlanCalendarDayUiState(
        groups = groups,
        entries = entries,
        startDate = startDate,
        endDate = endDate,
        includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
        unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
    )

    if (displayedDate in calendarDays) {
        return calendarDays
    }

    return calendarDays + buildPlanCalendarDayUiState(
        groups = groups,
        entries = entries,
        startDate = displayedDate,
        endDate = displayedDate,
        includeUnloggedArchivedSlots = includeUnloggedArchivedSlots,
        unloggedArchivedSlotCutoff = unloggedArchivedSlotCutoff,
    )
}

internal fun buildNextOccurrencesByGroup(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    start: LocalDateTime,
    limit: Int,
    lookaheadDays: Long = 90L
): Map<UUID, List<LocalDateTime>> {
    return groups.associate { group ->
        group.uuid to group
            .nextOccurrencesInPlanWindowFrom(
                start = start,
                limit = Int.MAX_VALUE,
                lookaheadDays = lookaheadDays
            )
            .asSequence()
            .filterNot { occurrence ->
                isSlotFulfilled(
                    group = group,
                    slot = MedicationGroupSlotKey(
                        scheduleTimeUuid = occurrence.scheduleTimeUuid,
                        scheduledFor = occurrence.scheduledFor,
                    ),
                    entries = entries
                )
            }
            .take(limit)
            .map { occurrence -> occurrence.scheduledFor }
            .toList()
    }
}

data class PlanUiState(
    val isLoading: Boolean = true,
    val now: LocalDateTime = LocalDate.now().atStartOfDay(),
    val today: LocalDate = LocalDate.now(),
    val calendarFirstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val calendarStartDate: LocalDate = buildPlanCalendarRange(
        today = today,
        firstDayOfWeek = calendarFirstDayOfWeek
    ).startDate,
    val calendarEndDate: LocalDate = buildPlanCalendarRange(
        today = today,
        firstDayOfWeek = calendarFirstDayOfWeek
    ).endDate,
    val selectedDate: LocalDate? = null,
    val entries: List<MedicationLogEntry> = emptyList(),
    val medicationGroups: List<MedicationGroup> = emptyList(),
    val scheduleMedicationGroups: List<MedicationGroup> = medicationGroups,
    val remindersEnabled: Boolean = true,
    val calendarDays: Map<LocalDate, PlanCalendarDayUiState> = emptyMap(),
    val daySchedule: PlanDaySchedule = PlanDaySchedule(
        date = selectedDate ?: today,
        scheduledEntries = emptyList(),
        unplannedEntries = emptyList()
    ),
    val nextOccurrencesByGroup: Map<UUID, List<LocalDateTime>> = emptyMap(),
)
