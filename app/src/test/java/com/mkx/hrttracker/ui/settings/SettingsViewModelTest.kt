package com.mkx.hrttracker.ui.settings

import android.net.Uri
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.util.AppDiagnosticsExportService
import com.mkx.hrttracker.util.AppDiagnosticsExportedFile
import com.mkx.hrttracker.util.AppLockSecurityManager
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settingsRepository: SettingsRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
    private val bloodTestRepository: BloodTestRepository = mockk()
    private val appLockSecurityManager: AppLockSecurityManager = mockk(relaxed = true)
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk(relaxed = true)
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler = mockk()
    private val backupExportService: BackupExportService = mockk()
    private val backupRestoreService: BackupRestoreService = mockk()
    private val diagnosticsExportService: AppDiagnosticsExportService = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settingsRepository.settingsState } returns MutableStateFlow(SettingsState())
        every { userProfileRepository.observeProfile() } returns flowOf(null)
        every { appLockSecurityManager.availabilityErrorMessageRes() } returns null
        coEvery { bloodTestRepository.getPanels() } returns emptyList()
        coEvery { bloodTestRepository.preloadActiveCustomAnalytes() } returns emptyList()
        coEvery { settingsRepository.setRemindersEnabled(any()) } just Runs
        coEvery { settingsRepository.setScreenLockProtectionEnabled(any()) } just Runs
        coEvery { settingsRepository.setHideScreenContentEnabled(any()) } just Runs
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } just Runs
        coEvery { medicationReminderSnoozeScheduler.clearAllSnoozes() } just Runs
        every { diagnosticsExportService.buildExportFileName(any(), any()) } returns
            "hrttracker-diagnostics-2026-05-07_12-04-05.txt"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_preloadsCalibrationDataForSettingsFlow() = runTest {
        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { bloodTestRepository.getPanels() }
        coVerify(exactly = 1) { bloodTestRepository.preloadActiveCustomAnalytes() }
    }

    @Test
    fun pendingRestoreRequest_staysInViewModelUntilCleared() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val uri = mockk<Uri>()

        viewModel.setPendingRestoreRequest(
            fileUri = uri,
            displayName = "backup.json",
        )
        advanceUntilIdle()

        val restoreRequest = viewModel.uiState.value.pendingRestoreRequest
        assertNotNull(restoreRequest)
        checkNotNull(restoreRequest)
        assertSame(uri, restoreRequest.uri)
        assertEquals("backup.json", restoreRequest.displayName)

        viewModel.clearPendingRestoreRequest()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingRestoreRequest)
    }

    @Test
    fun pendingPreparedBackupExport_staysInViewModelUntilCleared() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setPendingPreparedBackupExport(
            displayName = "plume-backup.hrtbackup",
            tempFilePath = "C:\\temp\\backup.tmp",
        )
        advanceUntilIdle()

        val pendingPreparedBackupExport = viewModel.uiState.value.pendingPreparedBackupExport
        assertNotNull(pendingPreparedBackupExport)
        checkNotNull(pendingPreparedBackupExport)
        assertEquals("plume-backup.hrtbackup", pendingPreparedBackupExport.displayName)
        assertEquals("C:\\temp\\backup.tmp", pendingPreparedBackupExport.tempFilePath)

        viewModel.clearPendingPreparedBackupExport()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingPreparedBackupExport)
    }

    @Test
    fun validateBackupFile_delegatesToBackupRestoreService() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val uri: Uri = mockk()
        coEvery { backupRestoreService.validateBackupFile(uri) } just Runs

        viewModel.validateBackupFile(uri)

        coVerify(exactly = 1) { backupRestoreService.validateBackupFile(uri) }
    }

    @Test
    fun setRemindersEnabled_clearsSnoozesWhenDisablingMasterSwitch() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setRemindersEnabled(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setRemindersEnabled(false) }
        coVerify(exactly = 1) { medicationReminderSnoozeScheduler.clearAllSnoozes() }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun setRemindersEnabled_keepsSnoozesWhenEnablingMasterSwitch() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setRemindersEnabled(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setRemindersEnabled(true) }
        coVerify(exactly = 0) { medicationReminderSnoozeScheduler.clearAllSnoozes() }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun onScreenLockProtectionAuthenticated_enablesAppLock_andTurnsOnHideScreenContent() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(settingsRepository, answers = false, recordedCalls = true)

        viewModel.onScreenLockProtectionToggle(true)
        viewModel.onScreenLockProtectionAuthenticated()
        advanceUntilIdle()

        coVerifyOrder {
            settingsRepository.setScreenLockProtectionEnabled(true)
            settingsRepository.setHideScreenContentEnabled(true)
        }
    }

    @Test
    fun setHideScreenContentEnabled_delegatesToRepository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(settingsRepository, answers = false, recordedCalls = true)

        viewModel.setHideScreenContentEnabled(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setHideScreenContentEnabled(false) }
    }

    @Test
    fun exportDiagnosticLogs_delegatesToDiagnosticsExportService() = runTest {
        val viewModel = createViewModel()
        val uri: Uri = mockk()
        coEvery {
            diagnosticsExportService.exportLogs(destinationUri = uri, exportedAt = any())
        } returns AppDiagnosticsExportedFile(displayName = "diagnostics.txt")

        val exportedFile = viewModel.exportDiagnosticLogs(uri)

        assertEquals("diagnostics.txt", exportedFile.displayName)
        coVerify(exactly = 1) {
            diagnosticsExportService.exportLogs(destinationUri = uri, exportedAt = any())
        }
    }

    @Test
    fun diagnosticsExportFileName_delegatesToDiagnosticsExportService() {
        val viewModel = createViewModel()

        assertEquals(
            "hrttracker-diagnostics-2026-05-07_12-04-05.txt",
            viewModel.diagnosticsExportFileName(),
        )
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            settingsRepository = settingsRepository,
            userProfileRepository = userProfileRepository,
            bloodTestRepository = bloodTestRepository,
            appLockSecurityManager = appLockSecurityManager,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            backupExportService = backupExportService,
            backupRestoreService = backupRestoreService,
            diagnosticsExportService = diagnosticsExportService,
        )
    }
}
