package com.mkx.hrttracker.ui.settings

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.data.backup.BackupCrypto
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupKeyStore
import com.mkx.hrttracker.data.backup.BackupKeyUnreadableException
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.backup.IncompatibleBackupFileException
import com.mkx.hrttracker.data.backup.KeyringCrypto
import com.mkx.hrttracker.data.backup.PasskeyCancelledException
import com.mkx.hrttracker.data.backup.PasskeyPrfUnsupportedException
import com.mkx.hrttracker.data.backup.PasskeyProviderUnavailableException
import com.mkx.hrttracker.data.backup.PasskeySlots
import com.mkx.hrttracker.data.backup.SecurityKeyNotLinkedException
import com.mkx.hrttracker.data.backup.SecurityKeyPinBlockedException
import com.mkx.hrttracker.data.backup.SecurityKeyPinNotSetException
import com.mkx.hrttracker.data.backup.SecurityKeySlots
import com.mkx.hrttracker.data.backup.SecurityKeyUnsupportedException
import com.mkx.hrttracker.data.backup.SecurityKeyWrongPinException
import com.mkx.hrttracker.data.backup.PreparedBackupExport
import com.mkx.hrttracker.data.backup.RecoveryKey
import com.mkx.hrttracker.data.backup.TestBackupArgon2KeyDeriver
import com.mkx.hrttracker.data.importer.ExternalImportCommitResult
import com.mkx.hrttracker.data.importer.ExternalImportFatalException
import com.mkx.hrttracker.data.importer.ExternalImportParseResult
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.data.importer.ExternalImportService
import com.mkx.hrttracker.data.importer.ExternalTrackerSourceApp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.util.AppLockSecurityManager
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
import kotlinx.coroutines.awaitCancellation
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
import org.junit.Assert.assertNotEquals
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
    private val passkeySlots: PasskeySlots = mockk()
    private val securityKeySlots: SecurityKeySlots = mockk()
    private val appLockSecurityManager: AppLockSecurityManager = mockk()
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
        every { appLockSecurityManager.isScreenLockMissing() } returns false
        every { appLockSecurityManager.availabilityErrorMessageRes() } returns null
        every { passkeySlots.canOffer() } returns false
        every { securityKeySlots.status } returns MutableStateFlow(null)
        every { securityKeySlots.isAvailable() } returns false
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
        assertEquals(setup.recoveryKey, RecoveryKey.parse(setup.recoveryKey))
        val result = viewModel.confirmKeySetup()
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
        // Not eligible for a passkey: no offer.
        assertFalse(viewModel.uiState.value.encryptionKey.isPasskeyOfferShown)
    }

    @Test
    fun confirmKeySetup_whenInstallFails_endsSetupAndInstallsNothingFurther() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupKeyStore.install(any(), any(), any()) } throws IOException("keyring.json")
        startKeySetup(viewModel)
        val mk = checkNotNull(viewModel.keySetupMasterKey)

        val error = runCatching { viewModel.confirmKeySetup() }.exceptionOrNull()
        advanceUntilIdle()

        assertTrue(error is IOException)
        assertNull(viewModel.uiState.value.keySetup)
        assertArrayEquals(ByteArray(32), mk)
        assertFalse(viewModel.uiState.value.isBackupExportInProgress)
        // A second confirm has no setup left to install.
        assertNull(viewModel.confirmKeySetup())
        coVerify(exactly = 1) { backupKeyStore.install(any(), any(), any()) }
        coVerify(exactly = 0) { backupExportService.prepareBackupExport(any(), any(), any()) }
    }

    // Installs Flow A's key with the offer eligible; the store then answers
    // with what was installed, as the real one would.
    private suspend fun TestScope.installWithOffer(
        viewModel: BackupSettingsViewModel,
        prepareCalls: List<Pair<ByteArray, String>>,
    ): Pair<ByteArray, String> {
        every { passkeySlots.canOffer() } returns true
        lateinit var installedMk: ByteArray
        lateinit var installedKeyring: String
        coEvery { backupKeyStore.install(any(), any(), any()) } coAnswers {
            installedMk = firstArg<ByteArray>().copyOf()
            installedKeyring = thirdArg()
        }
        startKeySetup(viewModel)
        assertNull(viewModel.confirmKeySetup())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.encryptionKey.isPasskeyOfferShown)
        assertNull(viewModel.uiState.value.keySetup)
        assertNull(viewModel.uiState.value.pendingPreparedBackupExport)
        assertTrue(prepareCalls.isEmpty())
        coEvery { backupKeyStore.masterKey() } answers { installedMk.copyOf() }
        coEvery { backupKeyStore.keyringJson() } answers { installedKeyring }
        return installedMk to installedKeyring
    }

    @Test
    fun passkeyOffer_addAppendsTheSlotThenPrepares() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val prepareCalls = stubPrepare()
        val (mk, keyringJson) = installWithOffer(viewModel, prepareCalls)
        val added = passkeySlot(mk)
        lateinit var addedWith: ByteArray
        coEvery { passkeySlots.add(activity, any()) } coAnswers {
            addedWith = secondArg<ByteArray>().copyOf()
            added
        }
        val written = stubReplaceKeyring()
        coEvery { backupKeyStore.keyringJson() } answers { written.lastOrNull() ?: keyringJson }

        val result = viewModel.acceptPasskeyOffer(activity)
        advanceUntilIdle()

        assertSame(preparedBackupExport, result)
        assertArrayEquals(mk, addedWith)
        val original = keyringCrypto.decode(keyringJson)
        assertEquals(original.slots + added, keyringCrypto.decode(written.single()).slots)
        assertArrayEquals(mk, prepareCalls.single().first)
        assertEquals(written.single(), prepareCalls.single().second)
        val state = viewModel.uiState.value.encryptionKey
        assertFalse(state.isPasskeyOfferShown)
        assertFalse(state.isPasskeyInProgress)
        assertNull(state.messageRes)
    }

    @Test
    fun passkeyOffer_notNowPreparesWithoutAPasskey() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val prepareCalls = stubPrepare()
        val (mk, keyringJson) = installWithOffer(viewModel, prepareCalls)

        assertSame(preparedBackupExport, viewModel.declinePasskeyOffer())
        advanceUntilIdle()

        assertArrayEquals(mk, prepareCalls.single().first)
        assertEquals(keyringJson, prepareCalls.single().second)
        assertFalse(viewModel.uiState.value.encryptionKey.isPasskeyOfferShown)
        coVerify(exactly = 0) { passkeySlots.add(any(), any()) }
        // The offer is gone: a second tap does nothing.
        assertNull(viewModel.declinePasskeyOffer())
    }

    @Test
    fun passkeyOffer_addFailuresToastAndStillPrepare() = runTest {
        listOf(
            PasskeyProviderUnavailableException(IOException()) to R.string.settings_backup_passkey_no_provider,
            PasskeyPrfUnsupportedException() to R.string.settings_backup_passkey_no_prf,
            PasskeyCancelledException(IOException()) to null,
            IOException("other") to R.string.settings_backup_passkey_failed,
        ).forEach { (error, messageRes) ->
            val viewModel = createViewModel()
            advanceUntilIdle()
            val prepareCalls = stubPrepare()
            val (mk, keyringJson) = installWithOffer(viewModel, prepareCalls)
            coEvery { passkeySlots.add(any(), any()) } throws error

            assertSame(preparedBackupExport, viewModel.acceptPasskeyOffer(activity))
            advanceUntilIdle()

            assertEquals(messageRes, viewModel.uiState.value.encryptionKey.messageRes)
            assertFalse(viewModel.uiState.value.encryptionKey.isPasskeyOfferShown)
            assertFalse(viewModel.uiState.value.encryptionKey.isPasskeyInProgress)
            assertArrayEquals(mk, prepareCalls.single().first)
            assertEquals(keyringJson, prepareCalls.single().second)
        }
        coVerify(exactly = 0) { backupKeyStore.replaceKeyring(any()) }
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
        coEvery { backupKeyStore.masterKey() } throws BackupKeyUnreadableException(IOException("Keystore"))

        assertNull(viewModel.onBackupToFileClick())
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.keySetup)
        coVerify(exactly = 0) { backupExportService.prepareBackupExport(any(), any(), any()) }
    }

    @Test
    fun backupToFile_whenTheKeyringFileReadThrows_failsWithoutKeySetup() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        // A plain file error out of masterKey() is not the Keystore case.
        coEvery { backupKeyStore.masterKey() } throws IOException("keyring.json")

        val error = runCatching { viewModel.onBackupToFileClick() }.exceptionOrNull()
        advanceUntilIdle()

        assertTrue(error is IOException)
        assertNull(viewModel.uiState.value.keySetup)
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
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
        coEvery { backupKeyStore.masterKey() } throws BackupKeyUnreadableException(IOException("Keystore"))

        viewModel.setPendingRestoreRequest(mockk(), "backup.hrtbackup", byteArrayOf(4))
        advanceUntilIdle()

        val state = checkNotNull(viewModel.uiState.value.restoreKey)
        assertFalse(state.isUnlocked)
        assertTrue(state.canAdopt)
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
        coVerify(exactly = 0) { backupKeyStore.clear() }
    }

    @Test
    fun restoreV4_whenTheKeyringFileReadThrowsAtOpen_isARestoreFailureNotNoKey() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } returns fileKeyringJson
        coEvery { backupKeyStore.masterKey() } throws IOException("keyring.json")

        val error = runCatching {
            viewModel.setPendingRestoreRequest(mockk(), "backup.hrtbackup", byteArrayOf(4))
        }.exceptionOrNull()
        advanceUntilIdle()

        assertTrue(error is IOException)
        assertNull(viewModel.uiState.value.restoreKey)
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
    }

    @Test
    fun restoreV4_whenTheKeyCheckBeforeAdoptFails_restoresWithoutInstalling() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        stubKeyRestore()
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } returns fileKeyringJson
        coEvery { backupKeyStore.masterKey() } throws BackupKeyUnreadableException(IOException("Keystore"))
        viewModel.setPendingRestoreRequest(mockk(), "backup.hrtbackup", byteArrayOf(4))
        advanceUntilIdle()
        unlock(viewModel, fileRecoveryKey)
        // A file error at the re-check: the phone may hold a key, so no install.
        coEvery { backupKeyStore.masterKey() } throws IOException("keyring.json")

        viewModel.requestBackupRestore()
        advanceUntilIdle()

        coVerify(exactly = 1) { backupRestoreService.restoreBackupBytes(any(), any<ByteArray>()) }
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
    }

    @Test
    fun restoreV4_whenTheKeystoreThrowOnlyAtOpen_doesNotReplaceThePhonesKey() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        stubKeyRestore()
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } returns fileKeyringJson
        coEvery { backupKeyStore.masterKey() } throws BackupKeyUnreadableException(IOException("Keystore"))
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

    @Test
    fun showRecoveryKey_needsTheCredentialUnlessThereIsNoScreenLock() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupKeyStore.recoveryKey() } returns fileRecoveryKey

        viewModel.onShowRecoveryKeyClick()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.encryptionKey.pendingPrompt)
        assertNull(viewModel.uiState.value.encryptionKey.shownRecoveryKey)
        coVerify(exactly = 0) { backupKeyStore.recoveryKey() }
        viewModel.onKeyCredentialAuthenticated()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.encryptionKey.pendingPrompt)
        assertEquals(fileRecoveryKey, viewModel.uiState.value.encryptionKey.shownRecoveryKey)
        viewModel.dismissShownRecoveryKey()

        every { appLockSecurityManager.isScreenLockMissing() } returns true
        viewModel.onShowRecoveryKeyClick()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.encryptionKey.pendingPrompt)
        assertEquals(fileRecoveryKey, viewModel.uiState.value.encryptionKey.shownRecoveryKey)
    }

    @Test
    fun showRecoveryKey_whenTheReadThrows_showsTheErrorAndNothingElse() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        every { appLockSecurityManager.isScreenLockMissing() } returns true
        coEvery { backupKeyStore.recoveryKey() } throws BackupKeyUnreadableException(IllegalStateException())

        viewModel.onShowRecoveryKeyClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value.encryptionKey
        assertEquals(R.string.settings_backup_key_read_failed, state.messageRes)
        assertNull(state.shownRecoveryKey)
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
        coVerify(exactly = 0) { backupKeyStore.clear() }
    }

    @Test
    fun replaceKey_afterTheCredential_installsANewKeyOnceWithoutAnExport() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        lateinit var installedMk: ByteArray
        lateinit var installedKeyring: String
        coEvery { backupKeyStore.install(any(), any(), any()) } coAnswers {
            installedMk = firstArg<ByteArray>().copyOf()
            installedKeyring = thirdArg()
        }

        viewModel.onReplaceKeyClick()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.encryptionKey.pendingPrompt)
        assertNull(viewModel.uiState.value.keySetup)
        viewModel.onKeyCredentialAuthenticated()
        advanceUntilIdle()
        val setup = checkNotNull(viewModel.uiState.value.keySetup)
        assertTrue(setup.isReplace)
        assertNotEquals(fileRecoveryKey, setup.recoveryKey)
        assertEquals(setup.recoveryKey, RecoveryKey.parse(setup.recoveryKey))
        val mk = checkNotNull(viewModel.keySetupMasterKey).copyOf()
        val heldMk = checkNotNull(viewModel.keySetupMasterKey)
        // Flow A's confirm doesn't take a Replace setup.
        every { passkeySlots.canOffer() } returns true
        assertNull(viewModel.confirmKeySetup())

        viewModel.confirmReplaceKey()
        advanceUntilIdle()
        viewModel.uiState.first { it.encryptionKey.isNewKeyActive }
        // Replace never offers a passkey.
        assertFalse(viewModel.uiState.value.encryptionKey.isPasskeyOfferShown)

        coVerify(exactly = 1) { backupKeyStore.install(any(), setup.recoveryKey, any()) }
        assertArrayEquals(mk, installedMk)
        val keyring = keyringCrypto.decode(installedKeyring)
        assertEquals(keyringCrypto.mkCheck(mk), keyring.mkCheck)
        assertArrayEquals(mk, keyringCrypto.unwrapRecovery(keyring, setup.recoveryKey))
        assertArrayEquals(ByteArray(32), heldMk)
        assertNull(viewModel.keySetupMasterKey)
        assertNull(viewModel.uiState.value.keySetup)
        coVerify(exactly = 0) { backupExportService.prepareBackupExport(any(), any(), any()) }

        // Back up now is Flow B, under the new key.
        coEvery { backupKeyStore.masterKey() } answers { installedMk.copyOf() }
        coEvery { backupKeyStore.keyringJson() } answers { installedKeyring }
        val prepareCalls = stubPrepare()
        viewModel.dismissNewKeyActive()
        assertSame(preparedBackupExport, viewModel.onBackupToFileClick())
        assertArrayEquals(mk, prepareCalls.single().first)
        assertEquals(installedKeyring, prepareCalls.single().second)
    }

    @Test
    fun replaceKey_cancelledAtThePromptOrBeforeISavedIt_changesNothing() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        every { appLockSecurityManager.promptErrorMessageRes(any()) } returns null

        viewModel.onReplaceKeyClick()
        viewModel.onKeyCredentialError(10)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.encryptionKey.pendingPrompt)
        assertNull(viewModel.uiState.value.encryptionKey.messageRes)
        assertNull(viewModel.uiState.value.keySetup)
        // A late success after the cancel does nothing.
        viewModel.onKeyCredentialAuthenticated()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.keySetup)

        every { appLockSecurityManager.isScreenLockMissing() } returns true
        viewModel.onReplaceKeyClick()
        val mk = checkNotNull(viewModel.keySetupMasterKey)
        viewModel.cancelKeySetup()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.keySetup)
        assertArrayEquals(ByteArray(32), mk)
        assertFalse(viewModel.uiState.value.encryptionKey.isNewKeyActive)
        coVerify(exactly = 0) { backupKeyStore.install(any(), any(), any()) }
    }

    @Test
    fun scanAtUnlock_aRecoveryKeyUnlocksAtOnce_anythingElseIsNotAKey() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        openV4Restore(viewModel, phoneMk = null)

        viewModel.onUnlockScanned(null)
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.restoreKey?.input)
        viewModel.onUnlockScanned("https://example.com")
        advanceUntilIdle()
        var state = checkNotNull(viewModel.uiState.value.restoreKey)
        assertTrue(state.isNotAKey)
        assertFalse(state.isUnlocking)
        viewModel.onUnlockInputChanged("A")
        advanceUntilIdle()
        assertFalse(checkNotNull(viewModel.uiState.value.restoreKey).isNotAKey)

        viewModel.onUnlockScanned(fileRecoveryKey)
        advanceUntilIdle()
        state = checkNotNull(viewModel.uiState.first { it.restoreKey?.isUnlocking != true }.restoreKey)
        assertEquals(fileRecoveryKey, state.input)
        assertTrue(state.isUnlocked)
        assertNotNull(viewModel.restoreMasterKey)
    }

    @Test
    fun addPasskey_noticeWithoutCredentialThenAppendsTheSlot() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val phoneKeyring = keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
        coEvery { backupKeyStore.masterKey() } answers { fileMk.copyOf() }
        coEvery { backupKeyStore.keyringJson() } returns keyringCrypto.encode(phoneKeyring)
        lateinit var addedWith: ByteArray
        val added = passkeySlot(fileMk)
        coEvery { passkeySlots.add(activity, any()) } coAnswers {
            addedWith = secondArg<ByteArray>().copyOf()
            added
        }
        val written = stubReplaceKeyring()

        viewModel.onAddPasskeyClick()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.encryptionKey.pendingPrompt)
        assertTrue(viewModel.uiState.value.encryptionKey.isAddPasskeyNoticeShown)
        viewModel.confirmAddPasskey(activity)
        advanceUntilIdle()

        assertArrayEquals(fileMk, addedWith)
        val keyring = keyringCrypto.decode(written.single())
        assertEquals(phoneKeyring.slots + added, keyring.slots)
        assertEquals(phoneKeyring.recoveryKeyWrapped, keyring.recoveryKeyWrapped)
        val state = viewModel.uiState.value.encryptionKey
        assertFalse(state.isAddPasskeyNoticeShown)
        assertFalse(state.isPasskeyInProgress)
        assertNull(state.messageRes)
    }

    @Test
    fun addPasskey_failuresShowTheirMessageAndWriteNothing() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupKeyStore.masterKey() } answers { fileMk.copyOf() }
        coEvery { backupKeyStore.keyringJson() } returns fileKeyringJson
        listOf(
            PasskeyProviderUnavailableException(IOException()) to R.string.settings_backup_passkey_no_provider,
            PasskeyPrfUnsupportedException() to R.string.settings_backup_passkey_no_prf,
            PasskeyCancelledException(IOException()) to null,
            IOException("other") to R.string.settings_backup_passkey_failed,
        ).forEach { (error, messageRes) ->
            coEvery { passkeySlots.add(any(), any()) } throws error
            viewModel.onAddPasskeyClick()
            viewModel.confirmAddPasskey(activity)
            advanceUntilIdle()
            assertEquals(messageRes, viewModel.uiState.value.encryptionKey.messageRes)
            assertFalse(viewModel.uiState.value.encryptionKey.isPasskeyInProgress)
            viewModel.consumeEncryptionKeyMessage()
        }
        coVerify(exactly = 0) { backupKeyStore.replaceKeyring(any()) }
    }

    @Test
    fun removePasskey_credentialThenConfirmRewritesWithoutTheSlot() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val phoneKeyring = keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
        val slot = passkeySlot(fileMk)
        coEvery { backupKeyStore.keyringJson() } returns
                keyringCrypto.encode(phoneKeyring.copy(slots = phoneKeyring.slots + slot))
        val written = stubReplaceKeyring()

        viewModel.onRemoveSlotClick(slot)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.encryptionKey.pendingPrompt)
        assertNull(viewModel.uiState.value.encryptionKey.removeSlot?.id)
        viewModel.onKeyCredentialAuthenticated()
        advanceUntilIdle()
        assertEquals(slot.id, viewModel.uiState.value.encryptionKey.removeSlot?.id)
        viewModel.confirmRemoveSlot()
        advanceUntilIdle()

        assertEquals(phoneKeyring.slots, keyringCrypto.decode(written.single()).slots)
        assertNull(viewModel.uiState.value.encryptionKey.removeSlot?.id)
    }

    @Test
    fun usePasskeyAtUnlock_holdsTheMkAndDerivesTheRecoveryKeyForTheAdopt() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val slot = passkeySlot(fileMk)
        val baseKeyring = keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
        val keyringJson = keyringCrypto.encode(baseKeyring.copy(slots = baseKeyring.slots + slot))
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } returns keyringJson
        coEvery { backupKeyStore.masterKey() } returns null
        coEvery { backupKeyStore.install(any(), any(), any()) } just Runs
        stubKeyRestore()
        coEvery { passkeySlots.unlock(activity, listOf(slot)) } coAnswers {
            slot to keyringCrypto.unwrapPasskeySlot(slot, prfOutput)
        }
        viewModel.setPendingRestoreRequest(mockk(), "backup.hrtbackup", byteArrayOf(4))
        advanceUntilIdle()
        assertEquals(listOf(slot), viewModel.uiState.value.restoreKey?.passkeySlots)

        viewModel.unlockBackupRestoreWithPasskey(activity)
        advanceUntilIdle()

        assertTrue(checkNotNull(viewModel.uiState.value.restoreKey).isUnlocked)
        assertArrayEquals(fileMk, viewModel.restoreMasterKey)
        viewModel.requestBackupRestore()
        advanceUntilIdle()
        coVerify { backupKeyStore.install(any(), fileRecoveryKey, keyringJson) }
    }

    @Test
    fun usePasskeyAtUnlock_cancelOrNoProviderStaysAtC1() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val baseKeyring = keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } returns
                keyringCrypto.encode(baseKeyring.copy(slots = baseKeyring.slots + passkeySlot(fileMk)))
        coEvery { backupKeyStore.masterKey() } returns null
        coEvery { passkeySlots.unlock(any(), any()) } throws PasskeyProviderUnavailableException(IOException())
        viewModel.setPendingRestoreRequest(mockk(), "backup.hrtbackup", byteArrayOf(4))
        advanceUntilIdle()

        viewModel.unlockBackupRestoreWithPasskey(activity)
        advanceUntilIdle()

        val state = checkNotNull(viewModel.uiState.value.restoreKey)
        assertFalse(state.isUnlocked)
        assertFalse(state.isUnlocking)
        assertNull(viewModel.restoreMasterKey)
        assertEquals(R.string.settings_backup_passkey_no_provider, viewModel.uiState.value.encryptionKey.messageRes)
    }

    @Test
    fun replaceKey_relinkLinksOrSkipsEachOldPasskeyThenZeroesTheNewMk() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        every { appLockSecurityManager.isScreenLockMissing() } returns true
        val oldKeyring = keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
        val linked = passkeySlot(fileMk)
        val skipped = passkeySlot(fileMk, credentialId = "c2tpcA")
        var currentKeyring = keyringCrypto.encode(oldKeyring.copy(slots = oldKeyring.slots + linked + skipped))
        coEvery { backupKeyStore.keyringJson() } answers { currentKeyring }
        coEvery { backupKeyStore.install(any(), any(), any()) } coAnswers { currentKeyring = thirdArg() }
        coEvery { backupKeyStore.replaceKeyring(any()) } coAnswers { currentKeyring = firstArg() }
        lateinit var rewrapMk: ByteArray
        coEvery { passkeySlots.rewrap(activity, linked, any()) } coAnswers {
            rewrapMk = thirdArg<ByteArray>().copyOf()
            passkeySlot(rewrapMk)
        }

        viewModel.onReplaceKeyClick()
        val newMk = checkNotNull(viewModel.keySetupMasterKey).copyOf()
        viewModel.confirmReplaceKey()
        viewModel.uiState.first { it.encryptionKey.relinkSlot != null }
        assertEquals(linked, viewModel.uiState.value.encryptionKey.relinkSlot)
        assertFalse(viewModel.uiState.value.encryptionKey.isNewKeyActive)
        val heldMk = checkNotNull(viewModel.relinkMasterKey)

        viewModel.linkSlot(activity)
        advanceUntilIdle()
        assertArrayEquals(newMk, rewrapMk)
        assertEquals(skipped, viewModel.uiState.value.encryptionKey.relinkSlot)
        viewModel.skipSlotLink()
        advanceUntilIdle()

        val keyring = keyringCrypto.decode(currentKeyring)
        assertEquals(keyringCrypto.mkCheck(newMk), keyring.mkCheck)
        assertEquals(listOf("recovery", "passkey"), keyring.slots.map { it.type })
        assertArrayEquals(newMk, keyringCrypto.unwrapPasskeySlot(keyring.slots.last(), prfOutput))
        assertNull(viewModel.uiState.value.encryptionKey.relinkSlot)
        assertTrue(viewModel.uiState.value.encryptionKey.isNewKeyActive)
        assertArrayEquals(ByteArray(32), heldMk)
        assertNull(viewModel.relinkMasterKey)
    }

    @Test
    fun addSecurityKey_credentialThenSheetThenAppendsTheSlot() = runTest {
        every { securityKeySlots.isAvailable() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(AddKeyChoice.SECURITY_KEY, viewModel.addKeyChoice)
        val phoneKeyring = keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
        coEvery { backupKeyStore.masterKey() } answers { fileMk.copyOf() }
        coEvery { backupKeyStore.keyringJson() } returns keyringCrypto.encode(phoneKeyring)
        lateinit var addedWith: ByteArray
        val added = securityKeySlot(fileMk)
        lateinit var pinSeen: String
        lateinit var pinPassed: CharArray
        coEvery { securityKeySlots.add(activity, any(), any()) } coAnswers {
            addedWith = secondArg<ByteArray>().copyOf()
            pinPassed = thirdArg()
            pinSeen = String(pinPassed)
            added
        }
        val written = stubReplaceKeyring()

        viewModel.onAddKeyClick()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.encryptionKey.pendingPrompt)
        assertNull(viewModel.uiState.value.encryptionKey.securityKeyPrompt)
        viewModel.onKeyCredentialAuthenticated()
        advanceUntilIdle()
        // The PIN step first; nothing talks to the key before Continue.
        assertEquals(SecurityKeyPromptUiState(needsPin = true), viewModel.uiState.value.encryptionKey.securityKeyPrompt)
        coVerify(exactly = 0) { securityKeySlots.add(any<Activity>(), any(), any()) }
        viewModel.submitSecurityKeyPin(activity, "1234")
        advanceUntilIdle()

        assertArrayEquals(fileMk, addedWith)
        assertEquals("1234", pinSeen)
        assertTrue(pinPassed.all { it == ' ' })
        val keyring = keyringCrypto.decode(written.single())
        assertEquals(phoneKeyring.slots + added, keyring.slots)
        assertEquals("c2FsdA==", keyring.slots.last().hmacSalt)
        val state = viewModel.uiState.value.encryptionKey
        assertNull(state.securityKeyPrompt)
        assertFalse(state.isPasskeyInProgress)
        assertNull(state.messageRes)
    }

    @Test
    fun addSecurityKey_wrongPinShowsTheFieldAgainWithRetries() = runTest {
        every { securityKeySlots.isAvailable() } returns true
        every { appLockSecurityManager.isScreenLockMissing() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupKeyStore.masterKey() } answers { fileMk.copyOf() }
        coEvery { backupKeyStore.keyringJson() } returns fileKeyringJson
        val pins = mutableListOf<String>()
        coEvery { securityKeySlots.add(activity, any(), any()) } coAnswers {
            val pin = String(thirdArg<CharArray>()).also(pins::add)
            if (pin != "1234") throw SecurityKeyWrongPinException(retries = 7, cause = Exception())
            securityKeySlot(fileMk)
        }
        val written = stubReplaceKeyring()

        viewModel.onAddSecurityKeyClick()
        viewModel.submitSecurityKeyPin(activity, "0000")
        advanceUntilIdle()
        assertEquals(
            SecurityKeyPromptUiState(needsPin = true, isWrongPin = true, pinRetries = 7),
            viewModel.uiState.value.encryptionKey.securityKeyPrompt,
        )
        assertFalse(viewModel.uiState.value.encryptionKey.isPasskeyInProgress)
        assertTrue(written.isEmpty())
        // Never retried on its own.
        assertEquals(listOf("0000"), pins)
        viewModel.submitSecurityKeyPin(activity, "1234")
        advanceUntilIdle()

        assertEquals(listOf("0000", "1234"), pins)
        assertEquals(1, written.size)
        assertNull(viewModel.uiState.value.encryptionKey.securityKeyPrompt)
    }

    @Test
    fun addSecurityKey_pinNotSetOrBlockedEndsWithItsMessage() = runTest {
        every { securityKeySlots.isAvailable() } returns true
        every { appLockSecurityManager.isScreenLockMissing() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupKeyStore.masterKey() } answers { fileMk.copyOf() }
        coEvery { backupKeyStore.keyringJson() } returns fileKeyringJson

        for ((error, messageRes) in listOf(
            SecurityKeyPinNotSetException() to R.string.settings_backup_security_key_pin_not_set,
            SecurityKeyPinBlockedException(Exception()) to R.string.settings_backup_security_key_pin_blocked,
        )) {
            coEvery { securityKeySlots.add(any<Activity>(), any(), any()) } throws error
            viewModel.onAddSecurityKeyClick()
            viewModel.submitSecurityKeyPin(activity, "1234")
            advanceUntilIdle()
            assertEquals(messageRes, viewModel.uiState.value.encryptionKey.messageRes)
            assertNull(viewModel.uiState.value.encryptionKey.securityKeyPrompt)
            viewModel.consumeEncryptionKeyMessage()
        }
        coVerify(exactly = 0) { backupKeyStore.replaceKeyring(any()) }
    }

    @Test
    fun addKey_choiceSheetOnlyWhenBothKindsCanWork() = runTest {
        every { securityKeySlots.isAvailable() } returns true
        every { passkeySlots.canOffer() } returns true
        val both = createViewModel()
        advanceUntilIdle()
        both.onAddKeyClick()
        assertTrue(both.uiState.first { it.encryptionKey.isKeyChoiceShown }.encryptionKey.isKeyChoiceShown)
        both.onAddPasskeyClick()
        advanceUntilIdle()
        assertFalse(both.uiState.value.encryptionKey.isKeyChoiceShown)
        assertTrue(both.uiState.value.encryptionKey.isAddPasskeyNoticeShown)

        every { securityKeySlots.isAvailable() } returns false
        val passkeyOnly = createViewModel()
        advanceUntilIdle()
        assertEquals(AddKeyChoice.PASSKEY, passkeyOnly.addKeyChoice)
        passkeyOnly.onAddKeyClick()
        advanceUntilIdle()
        assertFalse(passkeyOnly.uiState.value.encryptionKey.isKeyChoiceShown)
        assertTrue(passkeyOnly.uiState.value.encryptionKey.isAddPasskeyNoticeShown)
        assertNull(passkeyOnly.uiState.value.encryptionKey.pendingPrompt)
    }

    @Test
    fun addSecurityKey_unsupportedOrCancelWritesNothing() = runTest {
        every { securityKeySlots.isAvailable() } returns true
        every { appLockSecurityManager.isScreenLockMissing() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { backupKeyStore.masterKey() } answers { fileMk.copyOf() }
        coEvery { backupKeyStore.keyringJson() } returns fileKeyringJson

        coEvery { securityKeySlots.add(any<Activity>(), any(), any()) } throws SecurityKeyUnsupportedException()
        viewModel.onAddSecurityKeyClick()
        viewModel.submitSecurityKeyPin(activity, "1234")
        advanceUntilIdle()
        assertEquals(R.string.settings_backup_security_key_unsupported, viewModel.uiState.value.encryptionKey.messageRes)
        assertNull(viewModel.uiState.value.encryptionKey.securityKeyPrompt)
        viewModel.consumeEncryptionKeyMessage()

        lateinit var mkSeen: ByteArray
        coEvery { securityKeySlots.add(any<Activity>(), any(), any()) } coAnswers {
            mkSeen = secondArg()
            awaitCancellation()
        }
        // Cancel at the PIN step: nothing ran.
        viewModel.onAddSecurityKeyClick()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.encryptionKey.securityKeyPrompt)
        viewModel.cancelSecurityKey()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.encryptionKey.securityKeyPrompt)
        // Only the unsupported try above reached the key.
        coVerify(exactly = 1) { securityKeySlots.add(any<Activity>(), any(), any()) }
        // Cancel while waiting for the key.
        viewModel.onAddSecurityKeyClick()
        viewModel.submitSecurityKeyPin(activity, "1234")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.encryptionKey.securityKeyPrompt)
        viewModel.cancelSecurityKey()
        advanceUntilIdle()

        val state = viewModel.uiState.value.encryptionKey
        assertNull(state.securityKeyPrompt)
        assertFalse(state.isPasskeyInProgress)
        assertNull(state.messageRes)
        assertArrayEquals(ByteArray(32), mkSeen)
        coVerify(exactly = 0) { backupKeyStore.replaceKeyring(any()) }
    }

    @Test
    fun removeSecurityKey_credentialThenConfirmRewritesWithoutTheSlot() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val phoneKeyring = keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
        val slot = securityKeySlot(fileMk)
        coEvery { backupKeyStore.keyringJson() } returns
                keyringCrypto.encode(phoneKeyring.copy(slots = phoneKeyring.slots + slot))
        val written = stubReplaceKeyring()

        viewModel.onRemoveSlotClick(slot)
        advanceUntilIdle()
        assertEquals(
            R.string.settings_backup_remove_security_key,
            viewModel.uiState.value.encryptionKey.pendingPrompt?.titleRes,
        )
        viewModel.onKeyCredentialAuthenticated()
        viewModel.confirmRemoveSlot()
        advanceUntilIdle()

        assertEquals(phoneKeyring.slots, keyringCrypto.decode(written.single()).slots)
    }

    @Test
    fun useSecurityKeyAtUnlock_holdsTheMkAndDerivesTheRecoveryKey() = runTest {
        every { securityKeySlots.isAvailable() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        val slot = securityKeySlot(fileMk)
        val baseKeyring = keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
        val keyringJson = keyringCrypto.encode(baseKeyring.copy(slots = baseKeyring.slots + slot))
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } returns keyringJson
        coEvery { backupKeyStore.masterKey() } returns null
        coEvery { backupKeyStore.install(any(), any(), any()) } just Runs
        val restoredWith = stubKeyRestore()
        val pins = mutableListOf<String>()
        coEvery { securityKeySlots.unlock(activity, listOf(slot), any()) } coAnswers {
            val pin = String(thirdArg<CharArray>()).also(pins::add)
            if (pin != "1234") throw SecurityKeyWrongPinException(retries = 2, cause = Exception())
            slot to keyringCrypto.unwrapSecurityKeySlot(slot, hmacOutput)
        }
        viewModel.setPendingRestoreRequest(mockk(), "backup.hrtbackup", byteArrayOf(4))
        advanceUntilIdle()
        val opened = checkNotNull(viewModel.uiState.value.restoreKey)
        assertEquals(listOf(slot), opened.securityKeySlots)
        assertTrue(opened.passkeySlots.isEmpty())

        viewModel.unlockBackupRestoreWithSecurityKey()
        advanceUntilIdle()
        assertEquals(SecurityKeyPromptUiState(needsPin = true), viewModel.uiState.value.encryptionKey.securityKeyPrompt)
        viewModel.submitSecurityKeyPin(activity, "0000")
        advanceUntilIdle()
        assertEquals(
            SecurityKeyPromptUiState(needsPin = true, isWrongPin = true, pinRetries = 2),
            viewModel.uiState.value.encryptionKey.securityKeyPrompt,
        )
        assertFalse(checkNotNull(viewModel.uiState.value.restoreKey).isUnlocking)
        assertNull(viewModel.uiState.value.encryptionKey.messageRes)
        viewModel.submitSecurityKeyPin(activity, "1234")
        advanceUntilIdle()

        assertEquals(listOf("0000", "1234"), pins)
        assertTrue(checkNotNull(viewModel.uiState.value.restoreKey).isUnlocked)
        assertNull(viewModel.uiState.value.encryptionKey.securityKeyPrompt)
        assertArrayEquals(fileMk, viewModel.restoreMasterKey)
        val held = checkNotNull(viewModel.restoreMasterKey)
        viewModel.requestBackupRestore()
        advanceUntilIdle()
        assertArrayEquals(fileMk, restoredWith.single())
        coVerify { backupKeyStore.install(any(), fileRecoveryKey, keyringJson) }
        assertArrayEquals(ByteArray(32), held)
    }

    @Test
    fun unlock_bothKindsOfferTheChoiceAndHideSecurityKeysWhereUnavailable() = runTest {
        every { securityKeySlots.isAvailable() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        val baseKeyring = keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
        val passkey = passkeySlot(fileMk)
        val securityKey = securityKeySlot(fileMk)
        coEvery { backupRestoreService.peekRestoreKeyring(any()) } returns
                keyringCrypto.encode(baseKeyring.copy(slots = baseKeyring.slots + passkey + securityKey))
        coEvery { backupKeyStore.masterKey() } returns null
        coEvery { securityKeySlots.unlock(any<Activity>(), any(), any()) } throws SecurityKeyNotLinkedException()
        viewModel.setPendingRestoreRequest(mockk(), "backup.hrtbackup", byteArrayOf(4))
        advanceUntilIdle()
        val state = checkNotNull(viewModel.uiState.value.restoreKey)
        assertEquals(listOf(passkey), state.passkeySlots)
        assertEquals(listOf(securityKey), state.securityKeySlots)

        viewModel.onUseKeyClick()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.encryptionKey.isKeyChoiceShown)
        viewModel.unlockBackupRestoreWithSecurityKey()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.encryptionKey.isKeyChoiceShown)
        viewModel.submitSecurityKeyPin(activity, "1234")
        advanceUntilIdle()
        assertFalse(checkNotNull(viewModel.uiState.value.restoreKey).isUnlocked)
        assertEquals(R.string.settings_backup_security_key_not_linked, viewModel.uiState.value.encryptionKey.messageRes)

        every { securityKeySlots.isAvailable() } returns false
        viewModel.setPendingRestoreRequest(mockk(), "backup.hrtbackup", byteArrayOf(4))
        advanceUntilIdle()
        assertTrue(checkNotNull(viewModel.uiState.value.restoreKey).securityKeySlots.isEmpty())
        assertEquals(listOf(passkey), viewModel.uiState.value.restoreKey?.passkeySlots)
    }

    @Test
    fun replaceKey_relinkLinksOrSkipsEachOldSecurityKey() = runTest {
        every { securityKeySlots.isAvailable() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        every { appLockSecurityManager.isScreenLockMissing() } returns true
        val oldKeyring = keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
        val linked = securityKeySlot(fileMk)
        val skipped = securityKeySlot(fileMk, credentialId = "c2tpcA")
        var currentKeyring = keyringCrypto.encode(oldKeyring.copy(slots = oldKeyring.slots + linked + skipped))
        coEvery { backupKeyStore.keyringJson() } answers { currentKeyring }
        coEvery { backupKeyStore.install(any(), any(), any()) } coAnswers { currentKeyring = thirdArg() }
        coEvery { backupKeyStore.replaceKeyring(any()) } coAnswers { currentKeyring = firstArg() }
        val pins = mutableListOf<String>()
        coEvery { securityKeySlots.rewrap(activity, linked, any(), any()) } coAnswers {
            val pin = String(arg<CharArray>(3)).also(pins::add)
            if (pin != "1234") throw SecurityKeyWrongPinException(retries = 5, cause = Exception())
            securityKeySlot(thirdArg<ByteArray>().copyOf())
        }

        viewModel.onReplaceKeyClick()
        val newMk = checkNotNull(viewModel.keySetupMasterKey).copyOf()
        viewModel.confirmReplaceKey()
        viewModel.uiState.first { it.encryptionKey.relinkSlot != null }
        assertEquals(linked, viewModel.uiState.value.encryptionKey.relinkSlot)
        val heldMk = checkNotNull(viewModel.relinkMasterKey)

        viewModel.linkSlot(activity)
        advanceUntilIdle()
        assertEquals(SecurityKeyPromptUiState(needsPin = true), viewModel.uiState.value.encryptionKey.securityKeyPrompt)
        coVerify(exactly = 0) { securityKeySlots.rewrap(any<Activity>(), any(), any(), any()) }
        viewModel.submitSecurityKeyPin(activity, "0000")
        advanceUntilIdle()
        assertEquals(
            SecurityKeyPromptUiState(needsPin = true, isWrongPin = true, pinRetries = 5),
            viewModel.uiState.value.encryptionKey.securityKeyPrompt,
        )
        assertEquals(linked, viewModel.uiState.value.encryptionKey.relinkSlot)
        assertFalse(viewModel.uiState.value.encryptionKey.isPasskeyInProgress)
        viewModel.submitSecurityKeyPin(activity, "1234")
        advanceUntilIdle()
        assertEquals(listOf("0000", "1234"), pins)
        assertNull(viewModel.uiState.value.encryptionKey.securityKeyPrompt)
        assertEquals(skipped, viewModel.uiState.value.encryptionKey.relinkSlot)
        viewModel.skipSlotLink()
        advanceUntilIdle()

        val keyring = keyringCrypto.decode(currentKeyring)
        assertEquals(listOf("recovery", "securityKey"), keyring.slots.map { it.type })
        assertArrayEquals(newMk, keyringCrypto.unwrapSecurityKeySlot(keyring.slots.last(), hmacOutput))
        assertTrue(viewModel.uiState.value.encryptionKey.isNewKeyActive)
        assertArrayEquals(ByteArray(32), heldMk)
        assertNull(viewModel.relinkMasterKey)
    }

    @Test
    fun replaceKey_dropsSecurityKeySlotsWhereNoKeyCanConnect() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        every { appLockSecurityManager.isScreenLockMissing() } returns true
        val oldKeyring = keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
        var currentKeyring = keyringCrypto.encode(oldKeyring.copy(slots = oldKeyring.slots + securityKeySlot(fileMk)))
        coEvery { backupKeyStore.keyringJson() } answers { currentKeyring }
        coEvery { backupKeyStore.install(any(), any(), any()) } coAnswers { currentKeyring = thirdArg() }

        viewModel.onReplaceKeyClick()
        viewModel.confirmReplaceKey()
        viewModel.uiState.first { it.encryptionKey.isNewKeyActive }

        assertNull(viewModel.uiState.value.encryptionKey.relinkSlot)
        assertEquals(listOf("recovery"), keyringCrypto.decode(currentKeyring).slots.map { it.type })
    }

    private val activity: Activity = mockk()
    private val prfOutput = ByteArray(32) { 5 }
    private val hmacOutput = ByteArray(32) { 6 }

    private fun securityKeySlot(mk: ByteArray, credentialId: String = "a2V5") =
        keyringCrypto.wrapSecurityKeySlot(mk, hmacOutput, "featherline-backup", credentialId, "c2FsdA==", now = 3L)

    private fun passkeySlot(mk: ByteArray, credentialId: String = "Y3JlZA") =
        keyringCrypto.wrapPasskeySlot(mk, prfOutput, "asterismlabs.io", credentialId, "c2FsdA==", now = 2L)

    private fun stubReplaceKeyring(): MutableList<String> {
        val calls = mutableListOf<String>()
        coEvery { backupKeyStore.replaceKeyring(any()) } coAnswers { calls += firstArg<String>() }
        return calls
    }

    private val fileMk = ByteArray(32) { 3 }
    private val fileRecoveryKey = RecoveryKey.generate()

    // A field this build doesn't model, to prove the adopt is verbatim.
    private val fileKeyringJson = keyringCrypto.encode(
        keyringCrypto.newKeyring(fileMk, fileRecoveryKey, now = 1L)
    ).replaceFirst("{", "{\"futureField\":\"x\",")

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
            passkeySlots = passkeySlots,
            securityKeySlots = securityKeySlots,
            appLockSecurityManager = appLockSecurityManager,
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
