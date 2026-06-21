package com.mkx.hrttracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MilestonesViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    appTimeSource: AppTimeSource,
) : ViewModel() {
    private val editMode = MutableStateFlow(false)
    private val todayFlow = appTimeSource.currentMinute
        .map { it.toLocalDate() }
        .distinctUntilChanged()

    val uiState: StateFlow<MilestonesUiState> = combine(
        journalRepository.observeTrackedDates(),
        editMode,
        todayFlow,
    ) { all, isEdit, today ->
        val sorted = all.sortedWith(compareBy<TrackedDate> { it.date }.thenBy { it.name })
        val pinned = all
            .filter { it.pinnedOrder != null }
            .sortedWith(
                compareBy<TrackedDate> { it.pinnedOrder ?: Int.MAX_VALUE }
                    .thenBy { it.date }
                    .thenBy { it.createdAtEpochMillis }
                    .thenBy { it.id }
            )
        val pinnedIds = pinned.map { it.id }.toSet()
        val hero = pinned.firstOrNull()
        val heroRow = hero?.toAnchorRowUiState(today)
        MilestonesUiState(
            isLoading = false,
            today = today,
            hero = heroRow,
            heroNextMilestone = hero?.let { nextMilestoneUiState(it.date, today) },
            pinnedTray = pinned.map { it.toAnchorRowUiState(today) },
            timeline = sorted.map {
                TimelineNodeUiState(
                    anchor = it.toAnchorRowUiState(today),
                    isPinned = it.id in pinnedIds,
                )
            },
            todayDividerIndex = sorted.count { it.date.isBefore(today) },
            isEditMode = isEdit,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MilestonesUiState(today = appTimeSource.currentMinute.value.toLocalDate()),
    )

    fun toggleEditMode() {
        editMode.value = !editMode.value
    }

    fun exitEditMode() {
        editMode.value = false
    }

    fun setPinned(id: String, pinned: Boolean) = viewModelScope.launch {
        journalRepository.setPinned(id, pinned)
    }

    fun setHeroBackground(id: String, background: HeroBackground) = viewModelScope.launch {
        journalRepository.setHeroBackground(id, background)
    }

    fun reorderPinned(ids: List<String>) = viewModelScope.launch {
        journalRepository.reorderPinned(ids)
    }

    fun addDate(
        name: String,
        icon: String,
        date: LocalDate,
        paletteKey: String?,
    ) = viewModelScope.launch {
        journalRepository.addTrackedDate(name, icon, date, paletteKey)
    }

    fun updateDate(
        id: String,
        name: String,
        icon: String,
        date: LocalDate,
        paletteKey: String?,
    ) = viewModelScope.launch {
        journalRepository.updateTrackedDate(id, name, icon, date, paletteKey)
    }

    fun deleteDate(id: String) = viewModelScope.launch {
        journalRepository.deleteTrackedDate(id)
    }
}
