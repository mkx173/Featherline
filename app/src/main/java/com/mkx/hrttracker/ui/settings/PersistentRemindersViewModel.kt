package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.settings.PersistentReminderSettings
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderRepeatScheduler
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PersistentRemindersViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    medicationGroupRepository: MedicationGroupRepository,
    private val medicationReminderRepeatScheduler: MedicationReminderRepeatScheduler,
    private val reminderNotificationManager: ReminderNotificationManager,
    private val diagnosticsLogger: AppDiagnosticsLogger,
) : ViewModel() {
    val settingsState: StateFlow<SettingsState> = settingsRepository.settingsState

    /** The plans that can remind, oldest first, as the Plan tab orders them. */
    val activeGroups: StateFlow<List<MedicationGroup>> = medicationGroupRepository.observeGroups()
        .map { groups ->
            groups.orEmpty()
                .filter(MedicationGroup::isActive)
                .sortedBy(MedicationGroup::createdAt)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setGroupIncluded(groupUuid: UUID, included: Boolean) {
        update { current ->
            current.copy(
                excludedGroupUuids = if (included) {
                    current.excludedGroupUuids - groupUuid
                } else {
                    current.excludedGroupUuids + groupUuid
                }
            )
        }
    }

    private val failures = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits once per failed write, for a toast. */
    val updateFailures: SharedFlow<Unit> = failures.asSharedFlow()

    // Each change reads the stored value and writes it back changed; taps in
    // quick succession must apply in order, not overwrite one another.
    private val updateMutex = Mutex()

    fun update(transform: (PersistentReminderSettings) -> PersistentReminderSettings) {
        viewModelScope.launch {
            updateMutex.withLock {
                val previous = try {
                    settingsRepository.getCurrentSettings().persistentReminders
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    diagnosticsLogger.warning(TAG, "persistent_reminders_read_failed", error)
                    failures.tryEmit(Unit)
                    return@withLock
                }
                val next = transform(previous)
                if (next == previous) {
                    return@withLock
                }
                try {
                    settingsRepository.setPersistentReminderSettings(next)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    diagnosticsLogger.warning(TAG, "persistent_reminders_write_failed", error)
                    failures.tryEmit(Unit)
                    return@withLock
                }
                applyToPendingRepeats(previous, next)
            }
        }
    }

    /**
     * Brings repeats already armed in line: turning repeats off ends every
     * chain at once rather than at its next fire, a plan left out ends its
     * chains, and switching the full-screen alarm re-arms them with the
     * matching alarm kind. A new sound drops the channels of earlier picks.
     */
    private suspend fun applyToPendingRepeats(
        previous: PersistentReminderSettings,
        next: PersistentReminderSettings,
    ) {
        runCatching {
            if (previous.customSoundUri != next.customSoundUri) {
                reminderNotificationManager.deleteUnusedCustomSoundChannels(next.customSoundUri)
            }
            val newlyExcluded = next.excludedGroupUuids - previous.excludedGroupUuids
            if (next.repeatEnabled && newlyExcluded.isNotEmpty()) {
                medicationReminderRepeatScheduler.clearRepeatsForGroups(newlyExcluded)
            }
            when {
                previous.repeatEnabled && !next.repeatEnabled ->
                    medicationReminderRepeatScheduler.clearAllRepeats()

                next.repeatEnabled && previous.fullScreenAlarmEnabled != next.fullScreenAlarmEnabled ->
                    medicationReminderRepeatScheduler.rescheduleAll()
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            diagnosticsLogger.warning(TAG, "persistent_reminders_repeat_sync_failed", error)
        }
    }

    private companion object {
        const val TAG = "PersistentRemindersViewModel"
    }
}
