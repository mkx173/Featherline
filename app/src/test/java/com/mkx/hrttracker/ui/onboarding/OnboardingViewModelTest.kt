package com.mkx.hrttracker.ui.onboarding

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val settingsRepository: SettingsRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val dispatcher = StandardTestDispatcher()
    private val settingsState = MutableStateFlow(SettingsState(remindersEnabled = false))
    private var autoPublishSettingsState = true

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settingsState.value = SettingsState(remindersEnabled = false)
        autoPublishSettingsState = true
        every { settingsRepository.settingsState } returns settingsState
        every { settingsRepository.onboardingCompleted } returns flowOf(false)
        every { userProfileRepository.observeProfile() } returns flowOf(null)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { settingsRepository.setRemindersEnabled(any()) } coAnswers {
            if (autoPublishSettingsState) {
                settingsState.value = settingsState.value.copy(remindersEnabled = firstArg())
            }
        }
        coEvery { settingsRepository.setOnboardingCompleted(any()) } just Runs
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setRemindersEnabledDuringOnboarding_enablesMasterSwitchWithoutCompletingOnboarding() = runTest {
        val viewModel = createViewModel()

        viewModel.setRemindersEnabledDuringOnboarding(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setRemindersEnabled(true) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
        coVerify(exactly = 0) { settingsRepository.setOnboardingCompleted(any()) }
    }

    @Test
    fun setRemindersEnabledDuringOnboarding_disablesMasterSwitchWithoutRescheduling() = runTest {
        val viewModel = createViewModel()

        viewModel.setRemindersEnabledDuringOnboarding(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setRemindersEnabled(false) }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleAll(any()) }
        coVerify(exactly = 0) { settingsRepository.setOnboardingCompleted(any()) }
    }

    @Test
    fun setRemindersEnabledDuringOnboarding_waitsForSharedStateBeforeReturning() = runTest {
        val viewModel = createViewModel()
        autoPublishSettingsState = false
        var returned = false

        launch {
            viewModel.setRemindersEnabledDuringOnboarding(true)
            returned = true
        }
        advanceUntilIdle()

        assertFalse(returned)

        settingsState.value = settingsState.value.copy(remindersEnabled = true)
        advanceUntilIdle()

        assertTrue(returned)
    }

    private fun createViewModel(): OnboardingViewModel {
        return OnboardingViewModel(
            settingsRepository = settingsRepository,
            userProfileRepository = userProfileRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationGroupRepository = medicationGroupRepository,
        )
    }
}
