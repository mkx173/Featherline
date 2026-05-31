package com.mkx.hrttracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    medicationGroupRepository: MedicationGroupRepository,
    medicineRepository: MedicineRepository,
) : ViewModel() {

    private val mutationEvents = MutableSharedFlow<OnboardingMutationEvent>(
        replay = 1,
        extraBufferCapacity = 4,
    )

    val uiState: StateFlow<OnboardingUiState> = combine(
        settingsRepository.onboardingCompleted,
        userProfileRepository.observeProfile(),
        medicationGroupRepository.observeGroups(),
        medicineRepository.observeAllActiveOrNull(),
        settingsRepository.settingsState,
    ) { completed, profile, groups, medicines, settings ->
        OnboardingUiState(
            isLoaded = true,
            isCompleted = completed,
            userProfile = profile ?: UserProfile(),
            activeGroupCount = groups.orEmpty().count { it.archivedAt == null },
            trackedMedicineCount = medicines.orEmpty().count { it.stock.trackingEnabled },
            // Persisted reminder master state, so onboarding reflects changes made
            // elsewhere during the flow (e.g. enabling reminders in the step-3
            // group editor) instead of tracking a stale local choice.
            remindersEnabled = settings.remindersEnabled,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = OnboardingUiState()
        )

    val onboardingMutationEvents: SharedFlow<OnboardingMutationEvent> =
        mutationEvents.asSharedFlow()

    fun completeWithRemindersEnabled() {
        viewModelScope.launch {
            try {
                settingsRepository.setRemindersEnabled(true)
                settingsRepository.setOnboardingCompleted(true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutationEvents.tryEmit(OnboardingMutationEvent.Failure(error))
                return@launch
            }
            rescheduleRemindersBestEffort()
        }
    }

    suspend fun setRemindersEnabledDuringOnboarding(enabled: Boolean): Boolean {
        try {
            settingsRepository.setRemindersEnabled(enabled)
            settingsRepository.settingsState.first { settingsState ->
                settingsState.remindersEnabled == enabled
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutationEvents.tryEmit(OnboardingMutationEvent.Failure(error))
            return false
        }
        if (enabled) {
            rescheduleRemindersBestEffort()
        }
        return true
    }

    fun completeWithRemindersDeclined() {
        viewModelScope.launch {
            try {
                settingsRepository.setRemindersEnabled(false)
                settingsRepository.setOnboardingCompleted(true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutationEvents.tryEmit(OnboardingMutationEvent.Failure(error))
            }
        }
    }

    fun setWeight(value: Double, unit: WeightUnit) {
        viewModelScope.launch {
            try {
                userProfileRepository.setWeight(value, unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutationEvents.tryEmit(OnboardingMutationEvent.Failure(error))
            }
        }
    }

    fun clearWeight() {
        viewModelScope.launch {
            try {
                userProfileRepository.clearWeight()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutationEvents.tryEmit(OnboardingMutationEvent.Failure(error))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeOnboardingMutationEvent() {
        mutationEvents.resetReplayCache()
    }

    private suspend fun rescheduleRemindersBestEffort() {
        try {
            medicationReminderScheduler.rescheduleAll()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Reminder alarms are reconciled again by the app lifecycle; the
            // DataStore write above is the user-visible state change.
        }
    }
}

data class OnboardingUiState(
    val isLoaded: Boolean = false,
    val isCompleted: Boolean = false,
    val userProfile: UserProfile = UserProfile(),
    val activeGroupCount: Int = 0,
    val trackedMedicineCount: Int = 0,
    val remindersEnabled: Boolean = false,
) {
    val shouldShowOnboarding: Boolean
        get() = isLoaded && !isCompleted
}

sealed class OnboardingMutationEvent {
    data class Failure(val error: Throwable) : OnboardingMutationEvent()
}
