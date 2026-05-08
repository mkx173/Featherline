package com.mkx.hrttracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
) : ViewModel() {

    val uiState: StateFlow<OnboardingUiState> = combine(
        settingsRepository.onboardingCompleted,
        userProfileRepository.observeProfile(),
    ) { completed, profile ->
        OnboardingUiState(
            isLoaded = true,
            isCompleted = completed,
            userProfile = profile ?: UserProfile(),
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = OnboardingUiState()
        )

    fun completeWithRemindersEnabled() {
        viewModelScope.launch {
            settingsRepository.setRemindersEnabled(true)
            settingsRepository.setOnboardingCompleted(true)
            medicationReminderScheduler.rescheduleAll()
        }
    }

    fun completeWithRemindersDeclined() {
        viewModelScope.launch {
            settingsRepository.setRemindersEnabled(false)
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    fun setWeight(value: Double, unit: WeightUnit) {
        viewModelScope.launch {
            userProfileRepository.setWeight(value, unit)
        }
    }

    fun clearWeight() {
        viewModelScope.launch {
            userProfileRepository.clearWeight()
        }
    }
}

data class OnboardingUiState(
    val isLoaded: Boolean = false,
    val isCompleted: Boolean = false,
    val userProfile: UserProfile = UserProfile(),
) {
    val shouldShowOnboarding: Boolean
        get() = isLoaded && !isCompleted
}
