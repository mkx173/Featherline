package com.mkx.hrttracker.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.data.importer.ExternalImportCommitResult
import com.mkx.hrttracker.data.importer.ExternalImportFatalException
import com.mkx.hrttracker.data.importer.ExternalImportParseResult
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.data.importer.ExternalImportService
import com.mkx.hrttracker.data.importer.ExternalTrackerSourceApp
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.util.AppDiagnosticsExportService
import com.mkx.hrttracker.util.AppDiagnosticsExportedFile
import com.mkx.hrttracker.util.AppLockSecurityManager
import com.mkx.hrttracker.widget.WidgetAppearance
import com.mkx.hrttracker.widget.WidgetAppearanceRepository
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val applicationContext: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
    private val bloodTestRepository: BloodTestRepository = mockk()
    private val appLockSecurityManager: AppLockSecurityManager = mockk(relaxed = true)
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk(relaxed = true)
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler = mockk()
    private val backupExportService: BackupExportService = mockk()
    private val backupRestoreService: BackupRestoreService = mockk()
    private val externalImportService: ExternalImportService = mockk()
    private val diagnosticsExportService: AppDiagnosticsExportService = mockk()
    private val widgetAppearanceRepository: WidgetAppearanceRepository = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { applicationContext.contentResolver } returns contentResolver
        every { settingsRepository.settingsState } returns MutableStateFlow(SettingsState())
        every { userProfileRepository.observeProfile() } returns flowOf(null)
        every { appLockSecurityManager.availabilityErrorMessageRes() } returns null
        coEvery { bloodTestRepository.getPanels() } returns emptyList()
        coEvery { bloodTestRepository.preloadActiveCustomAnalytes() } returns emptyList()
        coEvery { settingsRepository.setRemindersEnabled(any()) } just Runs
        coEvery { settingsRepository.setScreenLockProtectionEnabled(any()) } just Runs
        coEvery { settingsRepository.setHideScreenContentEnabled(any()) } just Runs
        coEvery { settingsRepository.setPureBlackEnabled(any()) } just Runs
        coEvery { settingsRepository.setHazeBlurEnabled(any()) } just Runs
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } just Runs
        coEvery { medicationReminderSnoozeScheduler.clearAllSnoozes() } just Runs
        every { diagnosticsExportService.buildExportFileName(any(), any()) } returns
                "hrttracker-diagnostics-2026-05-07_12-04-05.txt"
        every { widgetAppearanceRepository.effectiveFor(null) } returns
                flowOf(WidgetAppearance.Default)
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
            encryptedBytes = ByteArray(0),
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
            displayName = "featherline-backup.hrtbackup",
            tempFilePath = "C:\\temp\\backup.tmp",
        )
        advanceUntilIdle()

        val pendingPreparedBackupExport = viewModel.uiState.value.pendingPreparedBackupExport
        assertNotNull(pendingPreparedBackupExport)
        checkNotNull(pendingPreparedBackupExport)
        assertEquals("featherline-backup.hrtbackup", pendingPreparedBackupExport.displayName)
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
    fun loadExternalImportPreview_storesPreviewAndPerformsNoWrites() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val uri: Uri = mockk()
        val json = """{"events":[]}"""
        val preview = sampleExternalImportPreview()
        every { contentResolver.openInputStream(uri) } returns
                ByteArrayInputStream(json.toByteArray())
        coEvery { externalImportService.buildPreview(json, any()) } returns preview

        val result = viewModel.loadExternalImportPreview(uri)
        advanceUntilIdle()

        assertSame(preview, result)
        assertSame(preview, viewModel.uiState.value.pendingExternalImportPreview)
        coVerify(exactly = 1) { externalImportService.buildPreview(json, any()) }
        coVerify(exactly = 0) { externalImportService.commit(any(), any()) }
    }

    @Test
    fun loadExternalImportPreviewFatalFailure_emitsImportFailureEvent() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val uri: Uri = mockk()
        val json = """{"unknown":true}"""
        val failure = ExternalImportFatalException("Unknown external import JSON shape.")
        every { contentResolver.openInputStream(uri) } returns
                ByteArrayInputStream(json.toByteArray())
        coEvery { externalImportService.buildPreview(json, any()) } throws failure
        val event = backgroundScope.async { viewModel.externalImportEvents.first() }

        val thrown = try {
            viewModel.loadExternalImportPreview(uri)
            null
        } catch (error: ExternalImportFatalException) {
            error
        }
        advanceUntilIdle()

        assertNotNull(thrown)
        assertEquals(failure.message, thrown?.message)
        val failureEvent = event.await() as ExternalImportEvent.Failure
        assertTrue(failureEvent.error is ExternalImportFatalException)
        assertEquals(failure.message, failureEvent.error.message)
        assertNull(viewModel.uiState.value.pendingExternalImportPreview)
    }

    @Test
    fun requestExternalImport_delegatesToServiceAndClearsPreviewAfterSuccess() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val uri: Uri = mockk()
        val json = """{"events":[]}"""
        val preview = sampleExternalImportPreview()
        val commitResult = sampleExternalImportCommitResult()
        every { contentResolver.openInputStream(uri) } returns
                ByteArrayInputStream(json.toByteArray())
        coEvery { externalImportService.buildPreview(json, any()) } returns preview
        coEvery { externalImportService.commit(preview, any()) } returns commitResult
        viewModel.loadExternalImportPreview(uri)
        advanceUntilIdle()
        val event = backgroundScope.async { viewModel.externalImportEvents.first() }

        viewModel.requestExternalImport()
        advanceUntilIdle()

        val successEvent = event.await() as ExternalImportEvent.Success
        assertSame(commitResult, successEvent.result)
        assertNull(viewModel.uiState.value.pendingExternalImportPreview)
        assertFalse(viewModel.uiState.value.isExternalImportInProgress)
        coVerify(exactly = 1) { externalImportService.commit(preview, any()) }
    }

    @Test
    fun clearPendingExternalImportPreview_clearsPreviewAndWritesNothing() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val uri: Uri = mockk()
        val json = """{"events":[]}"""
        val preview = sampleExternalImportPreview()
        every { contentResolver.openInputStream(uri) } returns
                ByteArrayInputStream(json.toByteArray())
        coEvery { externalImportService.buildPreview(json, any()) } returns preview

        viewModel.loadExternalImportPreview(uri)
        advanceUntilIdle()
        viewModel.clearPendingExternalImportPreview()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingExternalImportPreview)
        coVerify(exactly = 0) { externalImportService.commit(any(), any()) }
    }

    @Test
    fun setWeight_marksWeightMutationInProgressUntilRepositoryWriteCompletes() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val writeStarted = CompletableDeferred<Unit>()
        val allowWriteToFinish = CompletableDeferred<Unit>()
        coEvery {
            userProfileRepository.setWeight(72.0, WeightUnit.KILOGRAMS, any())
        } coAnswers {
            writeStarted.complete(Unit)
            allowWriteToFinish.await()
        }

        viewModel.setWeight(72.0, WeightUnit.KILOGRAMS)
        runCurrent()
        writeStarted.await()

        assertTrue(viewModel.uiState.value.isWeightMutationInProgress)

        allowWriteToFinish.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isWeightMutationInProgress)
    }

    @Test
    fun clearWeight_marksWeightMutationInProgressUntilRepositoryWriteCompletes() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val writeStarted = CompletableDeferred<Unit>()
        val allowWriteToFinish = CompletableDeferred<Unit>()
        coEvery {
            userProfileRepository.clearWeight(any())
        } coAnswers {
            writeStarted.complete(Unit)
            allowWriteToFinish.await()
        }

        viewModel.clearWeight()
        runCurrent()
        writeStarted.await()

        assertTrue(viewModel.uiState.value.isWeightMutationInProgress)

        allowWriteToFinish.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isWeightMutationInProgress)
    }

    @Test
    fun setWeightFailureEmitsFailureEventAndClearsProgress() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val failure = IllegalStateException("write failed")
        coEvery {
            userProfileRepository.setWeight(72.0, WeightUnit.KILOGRAMS, any())
        } throws failure
        val event = backgroundScope.async { viewModel.weightMutationEvents.first() }

        viewModel.setWeight(72.0, WeightUnit.KILOGRAMS)
        advanceUntilIdle()

        val failureEvent = event.await() as WeightMutationEvent.Failure
        assertSame(failure, failureEvent.error)
        assertFalse(viewModel.uiState.value.isWeightMutationInProgress)
    }

    @Test
    fun clearWeightFailureEmitsFailureEventAndClearsProgress() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val failure = IllegalStateException("clear failed")
        coEvery {
            userProfileRepository.clearWeight(any())
        } throws failure
        val event = backgroundScope.async { viewModel.weightMutationEvents.first() }

        viewModel.clearWeight()
        advanceUntilIdle()

        val failureEvent = event.await() as WeightMutationEvent.Failure
        assertSame(failure, failureEvent.error)
        assertFalse(viewModel.uiState.value.isWeightMutationInProgress)
    }

    @Test
    fun requestBackupRestore_keepsPendingRequestWhileRestoreIsInFlight() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val uri = mockk<Uri>()
        val restoreStarted = CompletableDeferred<Unit>()
        val allowRestoreToFinish = CompletableDeferred<Unit>()
        coEvery {
            backupRestoreService.restoreBackupBytes(any(), "password")
        } coAnswers {
            restoreStarted.complete(Unit)
            allowRestoreToFinish.await()
        }

        viewModel.setPendingRestoreRequest(
            fileUri = uri,
            displayName = "backup.json",
            encryptedBytes = byteArrayOf(1),
        )
        advanceUntilIdle()

        viewModel.requestBackupRestore("password")
        advanceUntilIdle()
        restoreStarted.await()

        assertNotNull(viewModel.uiState.value.pendingRestoreRequest)
        assertTrue(viewModel.uiState.value.isBackupRestoreInProgress)

        allowRestoreToFinish.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingRestoreRequest)
        assertFalse(viewModel.uiState.value.isBackupRestoreInProgress)
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
    fun setRemindersEnabled_schedulerFailureDoesNotEmitSettingFailureEvent() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { medicationReminderScheduler.rescheduleAll() } coAnswers {
            throw IllegalStateException("alarm scheduling failed")
        }
        val events = mutableListOf<SettingsMutationEvent>()
        backgroundScope.launch {
            viewModel.settingsMutationEvents.collect { event -> events += event }
        }
        runCurrent()

        viewModel.setRemindersEnabled(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setRemindersEnabled(true) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun setRemindersEnabled_snoozeClearFailureDoesNotEmitSettingFailureEvent() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        var clearAttempted = false
        coEvery { medicationReminderSnoozeScheduler.clearAllSnoozes() } coAnswers {
            clearAttempted = true
            throw IllegalStateException("snooze clear failed")
        }
        val events = mutableListOf<SettingsMutationEvent>()
        backgroundScope.launch {
            viewModel.settingsMutationEvents.collect { event -> events += event }
        }
        runCurrent()

        viewModel.setRemindersEnabled(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setRemindersEnabled(false) }
        coVerify(exactly = 1) { medicationReminderSnoozeScheduler.clearAllSnoozes() }
        // The snooze-clear failure must not bail out of the launch block:
        // rescheduleAll still has to run afterward.
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
        assertTrue(clearAttempted)
        assertTrue(events.isEmpty())
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
    fun setPureBlackEnabled_delegatesToRepository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setPureBlackEnabled(true)
        advanceUntilIdle()

        coVerify { settingsRepository.setPureBlackEnabled(true) }
    }

    @Test
    fun setHazeBlurEnabled_delegatesToRepository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setHazeBlurEnabled(false)
        advanceUntilIdle()

        coVerify { settingsRepository.setHazeBlurEnabled(false) }
    }

    @Test
    fun settingsWriteFailureEmitsMutationEvent() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val failure = IllegalStateException("settings write failed")
        coEvery { settingsRepository.setHideScreenContentEnabled(false) } throws failure
        val event = backgroundScope.async { viewModel.settingsMutationEvents.first() }

        viewModel.setHideScreenContentEnabled(false)
        advanceUntilIdle()

        val failureEvent = event.await() as SettingsMutationEvent.Failure
        assertSame(failure, failureEvent.error)
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
            applicationContext = applicationContext,
            settingsRepository = settingsRepository,
            userProfileRepository = userProfileRepository,
            bloodTestRepository = bloodTestRepository,
            appLockSecurityManager = appLockSecurityManager,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            backupExportService = backupExportService,
            backupRestoreService = backupRestoreService,
            externalImportService = externalImportService,
            diagnosticsExportService = diagnosticsExportService,
            widgetAppearanceRepository = widgetAppearanceRepository,
        )
    }

    private fun sampleExternalImportPreview(): ExternalImportPreview {
        val parseResult = ExternalImportParseResult(
            sourceApp = ExternalTrackerSourceApp.NOMTF,
            exportVersion = "1",
            exportedAt = "2026-06-14T00:00:00Z",
            medicationDoses = emptyList(),
            labResults = emptyList(),
            warnings = emptyList(),
        )
        return ExternalImportPreview(
            parseResult = parseResult,
            sourceAppLabel = "NoMTF",
            medicationRowsToCreate = 1,
            medicationRowsToUpdate = 0,
            labRowsToCreate = 0,
            labRowsToUpdate = 0,
            importedMedicinesToCreate = emptyList(),
            importedMedicinesToReuse = emptyList(),
            warnings = emptyList(),
        )
    }

    private fun sampleExternalImportCommitResult(): ExternalImportCommitResult {
        return ExternalImportCommitResult(
            sourceAppLabel = "NoMTF",
            medicationRowsCreated = 1,
            medicationRowsUpdated = 0,
            labRowsCreated = 0,
            labRowsUpdated = 0,
            importedMedicinesCreated = 0,
            importedMedicinesReused = 0,
            warnings = emptyList(),
        )
    }
}
