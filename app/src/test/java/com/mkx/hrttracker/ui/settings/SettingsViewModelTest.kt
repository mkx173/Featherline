package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.util.AppLockSecurityManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settingsRepository: SettingsRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
    private val bloodTestRepository: BloodTestRepository = mockk()
    private val appLockSecurityManager: AppLockSecurityManager = mockk(relaxed = true)
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk(relaxed = true)
    private val backupExportService: BackupExportService = mockk()
    private val backupRestoreService: BackupRestoreService = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settingsRepository.settingsState } returns MutableStateFlow(SettingsState())
        every { userProfileRepository.observeProfile() } returns flowOf(null)
        coEvery { bloodTestRepository.getPanels() } returns emptyList()
        coEvery { bloodTestRepository.preloadActiveCustomAnalytes() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_preloadsCalibrationDataForSettingsFlow() = runTest {
        SettingsViewModel(
            settingsRepository = settingsRepository,
            userProfileRepository = userProfileRepository,
            bloodTestRepository = bloodTestRepository,
            appLockSecurityManager = appLockSecurityManager,
            medicationReminderScheduler = medicationReminderScheduler,
            backupExportService = backupExportService,
            backupRestoreService = backupRestoreService,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { bloodTestRepository.getPanels() }
        coVerify(exactly = 1) { bloodTestRepository.preloadActiveCustomAnalytes() }
    }
}
