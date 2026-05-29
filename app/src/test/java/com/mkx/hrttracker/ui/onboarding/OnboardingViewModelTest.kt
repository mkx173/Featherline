package com.mkx.hrttracker.ui.onboarding

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import kotlinx.coroutines.CancellationException
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val settingsRepository: SettingsRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicineRepository: MedicineRepository = mockk()
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
        every { medicineRepository.observeAllActiveOrNull() } returns flowOf(emptyList())
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

    @Test
    fun setRemindersEnabledDuringOnboardingFailureEmitsMutationEventAndReturnsFalse() = runTest {
        val viewModel = createViewModel()
        val failure = IllegalStateException("settings write failed")
        coEvery { settingsRepository.setRemindersEnabled(true) } throws failure
        val event = backgroundScope.async { viewModel.onboardingMutationEvents.first() }

        val result = viewModel.setRemindersEnabledDuringOnboarding(true)
        advanceUntilIdle()

        assertFalse(result)
        val failureEvent = event.await() as OnboardingMutationEvent.Failure
        assertSame(failure, failureEvent.error)
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun setRemindersEnabledDuringOnboardingRethrowsSchedulerCancellation() = runTest {
        val viewModel = createViewModel()
        val cancellation = CancellationException("scheduler cancelled")
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } throws cancellation

        try {
            viewModel.setRemindersEnabledDuringOnboarding(true)
            fail("Expected scheduler cancellation to be rethrown.")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
    }

    @Test
    fun setWeightFailureEmitsMutationEvent() = runTest {
        val viewModel = createViewModel()
        val failure = IllegalStateException("weight write failed")
        coEvery {
            userProfileRepository.setWeight(72.0, WeightUnit.KILOGRAMS, any())
        } throws failure
        val event = backgroundScope.async { viewModel.onboardingMutationEvents.first() }

        viewModel.setWeight(72.0, WeightUnit.KILOGRAMS)
        advanceUntilIdle()

        val failureEvent = event.await() as OnboardingMutationEvent.Failure
        assertSame(failure, failureEvent.error)
    }

    @Test
    fun uiState_countsMedicinesWithStockTrackingEnabled() = runTest {
        every { medicineRepository.observeAllActiveOrNull() } returns flowOf(
            listOf(
                medicine(trackingEnabled = true),
                medicine(trackingEnabled = false),
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.trackedMedicineCount)
    }

    @Test
    fun uiState_loadsBeforeMedicineRepositoryEmitsActiveMedicines() = runTest {
        every { medicineRepository.observeAllActiveOrNull() } returns flowOf(null)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoaded)
        assertEquals(0, viewModel.uiState.value.trackedMedicineCount)
    }

    private fun createViewModel(): OnboardingViewModel {
        return OnboardingViewModel(
            settingsRepository = settingsRepository,
            userProfileRepository = userProfileRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationGroupRepository = medicationGroupRepository,
            medicineRepository = medicineRepository,
        )
    }

    private fun medicine(trackingEnabled: Boolean): Medicine {
        val uuid = UUID.randomUUID()
        val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
        return Medicine(
            uuid = uuid,
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL),
            category = MedicationCategory.ESTRADIOL,
            preparation = preparation,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(MedicationKey.ESTRADIOL, preparation),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            archivedAt = null,
            displayDoseUnit = MedicineDisplayDoseUnit.MG,
            stock = MedicineStock(trackingEnabled = trackingEnabled),
        )
    }
}
