package com.mkx.hrttracker.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.data.backup.BackupCrypto
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupKeyStore
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.backup.IncompatibleBackupFileException
import com.mkx.hrttracker.data.backup.KeyringCrypto
import com.mkx.hrttracker.data.backup.PreparedBackupExport
import com.mkx.hrttracker.data.backup.RecoveryKey
import com.mkx.hrttracker.data.backup.TestBackupArgon2KeyDeriver
import com.mkx.hrttracker.data.importer.ExternalImportCommitResult
import com.mkx.hrttracker.data.importer.ExternalImportFatalException
import com.mkx.hrttracker.data.importer.ExternalImportParseResult
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.data.importer.ExternalImportService
import com.mkx.hrttracker.data.importer.ExternalTrackerSourceApp
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BackupSettingsViewModelTest {
    private val applicationContext: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private val backupExportService: BackupExportService = mockk()
    private val backupRestoreService: BackupRestoreService = mockk()
    private val externalImportService: ExternalImportService = mockk()
    private val backupKeyStore: BackupKeyStore = mockk()
    private val keyringCrypto = KeyringCrypto(BackupCrypto(TestBackupArgon2KeyDeriver()))
    private val preparedBackupExport = PreparedBackupExport(
        displayName = "featherline-backup.hrtbackup",
        tempFilePath = "/cache/prepared-backup.tmp",
    )
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { applicationContext.contentResolver } returns contentResolver
        every { backupKeyStore.keyState } returns MutableStateFlow(null)
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } returns null
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

    @Test
    fun backupToFile_atKey_preparesWithStoredKeyAndShowsNoDialog() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val mk = ByteArray(32) { 7 }
        coEvery { backupKeyStore.masterKey() } returns mk
        coEvery { backupKeyStore.keyringJson() } returns "keyring"
        val prepareCalls = stubPrepare()

        val result = viewModel.onBackupToFileClick()
        advanceUntilIdle()

        assertSame(preparedBackupExport, result)
        assertArrayEquals(ByteArray(32) { 7 }, prepareCalls.single().first)
        assertEquals("keyring", prepareCalls.single().second)
        assertArrayEquals(ByteArray(32), mk)
        assertNull(viewModel.uiState.value.keySetup)
        assertEquals(
            preparedBackupExport.tempFilePath,
            viewModel.uiState.value.pendingPreparedBackupExport?.tempFilePath,
        )
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
    }

    @Test
    fun backupToFile_atNoKey_setsUpKeyThenInstallsAndPrepares() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val prepareCalls = stubPrepare()
        lateinit var installedMk: ByteArray
        lateinit var installedKeyring: String
        coEvery { backupKeyStore.install(any(), any(), any()) } coAnswers {
            installedMk = firstArg<ByteArray>().copyOf()
            installedKeyring = thirdArg()
        }

        val setup = startKeySetup(viewModel)
        assertEquals(BackupKeySetupStep.REVEAL, setup.step)
        assertEquals(setup.recoveryKey, RecoveryKey.parse(setup.recoveryKey))
        viewModel.onRecoveryKeySaved()
        advanceUntilIdle()
        assertEquals(BackupKeySetupStep.ENTER_BACK, viewModel.uiState.value.keySetup?.step)
        // Typed the lenient way: lower case with dashes.
        viewModel.onEnterBackInputChanged(RecoveryKey.format(setup.recoveryKey).lowercase())
        val result = viewModel.onEnterBackSubmit()
        advanceUntilIdle()

        assertSame(preparedBackupExport, result)
        coVerify(exactly = 1) { backupKeyStore.install(any(), setup.recoveryKey, any()) }
        val keyring = keyringCrypto.decode(installedKeyring)
        assertEquals(keyringCrypto.mkCheck(installedMk), keyring.mkCheck)
        assertArrayEquals(installedMk, keyringCrypto.unwrapRecovery(keyring, setup.recoveryKey))
        assertArrayEquals(installedMk, prepareCalls.single().first)
        assertEquals(installedKeyring, prepareCalls.single().second)
        assertNull(viewModel.uiState.value.keySetup)
        assertNotNull(viewModel.uiState.value.pendingPreparedBackupExport)
    }

    @Test
    fun enterBackMismatch_installsNothingAndShowsError() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val setup = startKeySetup(viewModel)
        viewModel.onRecoveryKeySaved()
        val wrongKey = if (setup.recoveryKey.startsWith("0")) "1" + setup.recoveryKey.drop(1)
        else "0" + setup.recoveryKey.drop(1)

        viewModel.onEnterBackInputChanged(wrongKey)
        assertNull(viewModel.onEnterBackSubmit())
        advanceUntilIdle()

        val state = checkNotNull(viewModel.uiState.value.keySetup)
        assertTrue(state.isMismatch)
        assertEquals(setup.recoveryKey, state.recoveryKey)
        // Back returns to A1 with the same key.
        viewModel.onEnterBackBack()
        advanceUntilIdle()
        assertEquals(BackupKeySetupStep.REVEAL, viewModel.uiState.value.keySetup?.step)
        assertEquals(setup.recoveryKey, viewModel.uiState.value.keySetup?.recoveryKey)
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
        coVerify(exactly = 0) { backupExportService.prepareBackupExport(any(), any(), any()) }
    }

    @Test
    fun cancelKeySetup_installsNothingAndZeroesTheMasterKey() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        startKeySetup(viewModel)
        val mk = checkNotNull(viewModel.keySetupMasterKey)

        viewModel.cancelKeySetup()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.keySetup)
        assertArrayEquals(ByteArray(32), mk)
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
        coVerify(exactly = 0) { backupExportService.prepareBackupExport(any(), any(), any()) }
        // The next tap makes a new key.
        assertNotNull(startKeySetup(viewModel))
    }

    @Test
    fun backupToFile_whenKeystoreReadThrows_startsKeySetup() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupKeyStore.masterKey() } throws IOException("Keystore")

        assertNull(viewModel.onBackupToFileClick())
        advanceUntilIdle()

        assertEquals(BackupKeySetupStep.REVEAL, viewModel.uiState.value.keySetup?.step)
        coVerify(exactly = 0) { backupExportService.prepareBackupExport(any(), any(), any()) }
    }

    @Test
    fun backupToFile_whenKeyringReadThrows_failsWithoutKeySetup() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupKeyStore.masterKey() } returns ByteArray(32)
        coEvery { backupKeyStore.keyringJson() } throws IOException("keyring.json")

        val error = runCatching { viewModel.onBackupToFileClick() }.exceptionOrNull()
        advanceUntilIdle()

        assertTrue(error is IOException)

        assertNull(viewModel.uiState.value.keySetup)
        coVerify(exactly = 0) { backupExportService.prepareBackupExport(any(), any(), any()) }
    }

    @Test
    fun restoreV2V3_showsThePasswordDialog() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setPendingRestoreRequest(mockk(), "old.hrtbackup", byteArrayOf(1))
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.pendingRestoreRequest)
        assertNull(viewModel.uiState.value.restoreKey)
        assertNull(viewModel.restoreMasterKey)
    }

    @Test
    fun restoreV4_onThePhoneThatMadeIt_goesStraightToPlainConfirmAndNeverAdopts() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val restoredWith = stubKeyRestore()

        val state = openV4Restore(viewModel, phoneMk = fileMk)
        assertTrue(state.isUnlocked)
        assertFalse(state.canAdopt)
        val heldMk = checkNotNull(viewModel.restoreMasterKey)
        assertArrayEquals(fileMk, heldMk)

        viewModel.requestBackupRestore()
        advanceUntilIdle()

        assertArrayEquals(fileMk, restoredWith.single())
        assertArrayEquals(ByteArray(32), heldMk)
        assertNull(viewModel.restoreMasterKey)
        assertNull(viewModel.uiState.value.pendingRestoreRequest)
        assertNull(viewModel.uiState.value.restoreKey)
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
    }

    @Test
    fun restoreV4_withADifferentKey_unlocksThenConfirmsWithoutTheBoxAndNeverAdopts() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        stubKeyRestore()

        val state = openV4Restore(viewModel, phoneMk = ByteArray(32) { 9 })
        assertFalse(state.isUnlocked)
        assertFalse(state.canAdopt)
        assertNull(viewModel.restoreMasterKey)

        val unlocked = unlock(viewModel, fileRecoveryKey)
        assertTrue(unlocked.isUnlocked)
        assertFalse(unlocked.canAdopt)
        viewModel.requestBackupRestore()
        advanceUntilIdle()

        coVerify(exactly = 1) { backupRestoreService.restoreBackupBytes(any(), any<ByteArray>()) }
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
    }

    @Test
    fun restoreV4_onANoKeyPhone_unlocksAndAdoptsTheVerbatimKeyringAfterTheRestore() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val restoredWith = stubKeyRestore()
        lateinit var installedMk: ByteArray
        coEvery { backupKeyStore.install(any(), any(), any()) } coAnswers {
            installedMk = firstArg<ByteArray>().copyOf()
        }

        val state = openV4Restore(viewModel, phoneMk = null)
        assertFalse(state.isUnlocked)
        assertTrue(state.canAdopt)
        // Typed the lenient way.
        val unlocked = unlock(viewModel, RecoveryKey.format(fileRecoveryKey).lowercase())
        assertTrue(unlocked.isUnlocked)
        assertTrue(unlocked.canAdopt)
        assertTrue(unlocked.adopt)
        val heldMk = checkNotNull(viewModel.restoreMasterKey)
        assertArrayEquals(fileMk, heldMk)

        viewModel.requestBackupRestore()
        advanceUntilIdle()

        assertArrayEquals(fileMk, restoredWith.single())
        coVerifyOrder {
            backupRestoreService.restoreBackupBytes(any(), any<ByteArray>())
            backupKeyStore.install(any(), fileRecoveryKey, fileKeyringJson)
        }
        assertArrayEquals(fileMk, installedMk)
        assertArrayEquals(ByteArray(32), heldMk)
        assertNull(viewModel.restoreMasterKey)
    }

    @Test
    fun restoreV4_whenTheKeystoreReadThrows_offersToAdopt() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } returns fileKeyringJson
        coEvery { backupKeyStore.masterKey() } throws IOException("Keystore")

        viewModel.setPendingRestoreRequest(mockk(), "backup.hrtbackup", byteArrayOf(4))
        advanceUntilIdle()

        val state = checkNotNull(viewModel.uiState.value.restoreKey)
        assertFalse(state.isUnlocked)
        assertTrue(state.canAdopt)
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
        coVerify(exactly = 0) { backupKeyStore.clear() }
    }

    @Test
    fun restoreV4_whenTheKeystoreThrowOnlyAtOpen_doesNotReplaceThePhonesKey() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        stubKeyRestore()
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } returns fileKeyringJson
        coEvery { backupKeyStore.masterKey() } throws IOException("Keystore")
        viewModel.setPendingRestoreRequest(mockk(), "backup.hrtbackup", byteArrayOf(4))
        advanceUntilIdle()
        unlock(viewModel, fileRecoveryKey)
        // The Keystore recovers before Restore: the phone has its own key.
        coEvery { backupKeyStore.masterKey() } returns ByteArray(32) { 7 }

        viewModel.requestBackupRestore()
        advanceUntilIdle()

        coVerify(exactly = 1) { backupRestoreService.restoreBackupBytes(any(), any<ByteArray>()) }
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
    }

    @Test
    fun restoreV4_unticked_restoresWithoutAdopting() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        stubKeyRestore()
        openV4Restore(viewModel, phoneMk = null)
        unlock(viewModel, fileRecoveryKey)

        viewModel.onAdoptRestoreKeyChanged(false)
        viewModel.requestBackupRestore()
        advanceUntilIdle()

        coVerify(exactly = 1) { backupRestoreService.restoreBackupBytes(any(), any<ByteArray>()) }
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
        assertNull(viewModel.restoreMasterKey)
    }

    @Test
    fun restoreV4_wrongKey_keepsUnlockOpenAndHoldsNoKey() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        openV4Restore(viewModel, phoneMk = null)
        val wrongKey = if (fileRecoveryKey.startsWith("0")) "1" + fileRecoveryKey.drop(1)
        else "0" + fileRecoveryKey.drop(1)

        val state = unlock(viewModel, wrongKey)

        assertFalse(state.isUnlocked)
        assertTrue(state.isWrongKey)
        assertNull(viewModel.restoreMasterKey)
        assertNotNull(viewModel.uiState.value.pendingRestoreRequest)
        // No attempt limit: the right key still opens it.
        assertTrue(unlock(viewModel, fileRecoveryKey).isUnlocked)
    }

    @Test
    fun restoreV4_failedRestore_installsNothingAndZeroesTheKey() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupRestoreService.restoreBackupBytes(any(), any<ByteArray>()) } throws IOException("damaged")
        openV4Restore(viewModel, phoneMk = null)
        unlock(viewModel, fileRecoveryKey)
        val heldMk = checkNotNull(viewModel.restoreMasterKey)
        val event = backgroundScope.async { viewModel.backupRestoreEvents.first() }

        viewModel.requestBackupRestore()
        advanceUntilIdle()

        assertTrue(event.await() is BackupRestoreEvent.Failure)
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
        assertArrayEquals(ByteArray(32), heldMk)
        assertNull(viewModel.restoreMasterKey)
        assertNull(viewModel.uiState.value.restoreKey)
    }

    @Test
    fun restoreV4_cancelAtUnlockOrConfirm_installsNothingAndZeroesTheKey() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        openV4Restore(viewModel, phoneMk = null)
        viewModel.clearPendingRestoreRequest()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.restoreKey)

        openV4Restore(viewModel, phoneMk = null)
        unlock(viewModel, fileRecoveryKey)
        val heldMk = checkNotNull(viewModel.restoreMasterKey)
        viewModel.clearPendingRestoreRequest()
        advanceUntilIdle()

        assertArrayEquals(ByteArray(32), heldMk)
        assertNull(viewModel.restoreMasterKey)
        assertNull(viewModel.uiState.value.restoreKey)
        coVerify(exactly = 0) { backupRestoreService.restoreBackupBytes(any(), any<ByteArray>()) }
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
    }

    @Test
    fun restoreV4_withoutAKeyring_isIncompatibleAndHoldsNothing() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } throws
                IncompatibleBackupFileException("Backup file has no keyring.")

        val error = runCatching {
            viewModel.setPendingRestoreRequest(mockk(), "cloud.hrtbackup", byteArrayOf(4))
        }.exceptionOrNull()
        advanceUntilIdle()

        assertTrue(error is IncompatibleBackupFileException)
        assertNull(viewModel.uiState.value.pendingRestoreRequest)
        assertNull(viewModel.uiState.value.restoreKey)
    }

    private val fileMk = ByteArray(32) { 3 }
    private val fileRecoveryKey = RecoveryKey.generate()

    // A field this build doesn't model, to prove the adopt is verbatim.
    private val fileKeyringJson = keyringCrypto.encode(
        keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
    ).replaceFirst("{", "{\"recoveryKeyWrapped\":\"x\",")

    // Records the MK each v4 restore saw: the ViewModel zeroes it right after.
    private fun stubKeyRestore(): MutableList<ByteArray> {
        val calls = mutableListOf<ByteArray>()
        coEvery { backupRestoreService.restoreBackupBytes(any(), any<ByteArray>()) } coAnswers {
            calls += secondArg<ByteArray>().copyOf()
        }
        return calls
    }

    private suspend fun TestScope.openV4Restore(
        viewModel: BackupSettingsViewModel,
        phoneMk: ByteArray?,
    ): BackupRestoreKeyUiState {
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } returns fileKeyringJson
        coEvery { backupKeyStore.masterKey() } answers { phoneMk?.copyOf() }
        viewModel.setPendingRestoreRequest(mockk(), "backup.hrtbackup", byteArrayOf(4))
        advanceUntilIdle()
        return checkNotNull(viewModel.uiState.value.restoreKey)
    }

    // The unwrap runs on Dispatchers.Default, so wait for its result.
    private suspend fun TestScope.unlock(
        viewModel: BackupSettingsViewModel,
        input: String,
    ): BackupRestoreKeyUiState {
        viewModel.onUnlockInputChanged(RecoveryKey.symbols(input))
        viewModel.unlockBackupRestore()
        advanceUntilIdle()
        return checkNotNull(viewModel.uiState.first { it.restoreKey?.isUnlocking != true }.restoreKey)
    }

    private fun createViewModel(): BackupSettingsViewModel {
        return BackupSettingsViewModel(
            applicationContext = applicationContext,
            backupExportService = backupExportService,
            backupRestoreService = backupRestoreService,
            externalImportService = externalImportService,
            backupKeyStore = backupKeyStore,
            keyringCrypto = keyringCrypto,
        )
    }

    // Records what prepareBackupExport saw: the caller zeroes its MK right after.
    private fun stubPrepare(): MutableList<Pair<ByteArray, String>> {
        val calls = mutableListOf<Pair<ByteArray, String>>()
        coEvery { backupExportService.prepareBackupExport(any(), any(), any()) } coAnswers {
            calls += firstArg<ByteArray>().copyOf() to secondArg<String>()
            preparedBackupExport
        }
        return calls
    }

    private suspend fun TestScope.startKeySetup(viewModel: BackupSettingsViewModel): BackupKeySetupUiState {
        coEvery { backupKeyStore.masterKey() } returns null
        assertNull(viewModel.onBackupToFileClick())
        advanceUntilIdle()
        return checkNotNull(viewModel.uiState.value.keySetup)
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
