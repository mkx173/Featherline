package com.mkx.hrttracker.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.importer.ExternalImportCommitResult
import com.mkx.hrttracker.data.importer.ExternalImportFatalException
import com.mkx.hrttracker.data.importer.ExternalImportParseResult
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.data.importer.ExternalImportService
import com.mkx.hrttracker.data.importer.ExternalTrackerSourceApp
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class BackupSettingsViewModelTest {
    private val applicationContext: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private val backupExportService: BackupExportService = mockk()
    private val backupRestoreService: BackupRestoreService = mockk()
    private val externalImportService: ExternalImportService = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { applicationContext.contentResolver } returns contentResolver
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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

    private fun createViewModel(): BackupSettingsViewModel {
        return BackupSettingsViewModel(
            applicationContext = applicationContext,
            backupExportService = backupExportService,
            backupRestoreService = backupRestoreService,
            externalImportService = externalImportService,
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
            medicationRowsUnchanged = 0,
            labRowsToCreate = 0,
            labRowsToUpdate = 0,
            labRowsUnchanged = 0,
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
            medicationRowsUnchanged = 0,
            labRowsCreated = 0,
            labRowsUpdated = 0,
            labRowsUnchanged = 0,
            importedMedicinesCreated = 0,
            importedMedicinesReused = 0,
            warnings = emptyList(),
        )
    }
}
