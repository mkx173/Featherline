package com.mkx.hrttracker.data.backup

import com.mkx.hrttracker.data.local.FakeWrappedSecret
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupKeyStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val keyringCrypto = KeyringCrypto(BackupCrypto(TestBackupArgon2KeyDeriver()))
    private val mk = ByteArray(32) { index -> index.toByte() }
    private val masterKeySecret = FakeWrappedSecret()
    private val recoveryKeySecret = FakeWrappedSecret()
    private val keyringFile by lazy { tempFolder.root.resolve("files/keyring.json") }

    // A newer build's field must survive: the store keeps the JSON verbatim.
    private val keyringJson by lazy {
        keyringCrypto.encode(keyringCrypto.newKeyring(mk, RECOVERY_KEY, now = CREATED_AT))
            .replaceFirst("{", "{\"recoveryKeyWrapped\":\"future\",")
    }

    private fun TestScope.newStore(): BackupKeyStore {
        tempFolder.root.resolve("files").mkdirs()
        tempFolder.root.resolve("no_backup").mkdirs()
        return BackupKeyStore(
            masterKeySecret = masterKeySecret,
            recoveryKeySecret = recoveryKeySecret,
            keyringFile = keyringFile,
            keyringTempFile = tempFolder.root.resolve("no_backup/keyring.json.tmp"),
            keyringCrypto = keyringCrypto,
            appScope = backgroundScope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    @Test
    fun install_then_read_back() = runTest {
        newStore().install(mk, RECOVERY_KEY, keyringJson)

        // A fresh store (new process) evaluates the state from disk.
        val store = newStore()
        val keyState = store.keyState
        assertNull(keyState.value)
        testScheduler.runCurrent()
        assertEquals(BackupKeyState.Key(recoveryCreatedAt = CREATED_AT), keyState.value)
        assertArrayEquals(mk, store.masterKey())
        assertEquals(RECOVERY_KEY, store.recoveryKey())
        assertEquals(keyringJson, store.keyringJson())
        assertArrayEquals(keyringJson.toByteArray(Charsets.UTF_8), keyringFile.readBytes())
    }

    @Test
    fun keyring_without_prefs_is_no_key_and_the_file_is_deleted() = runTest {
        newStore().install(mk, RECOVERY_KEY, keyringJson)
        masterKeySecret.clear()

        assertNoKey(newStore())
        assertFalse(keyringFile.exists())
        assertNull(recoveryKeySecret.value)
    }

    @Test
    fun prefs_without_keyring_is_no_key_and_the_prefs_are_cleared() = runTest {
        newStore().install(mk, RECOVERY_KEY, keyringJson)
        keyringFile.delete()

        assertNoKey(newStore())
        assertNull(masterKeySecret.value)
        assertNull(recoveryKeySecret.value)
    }

    @Test
    fun mk_check_mismatch_is_no_key() = runTest {
        newStore().install(mk, RECOVERY_KEY, keyringJson)
        masterKeySecret.write(ByteArray(32) { 7 })

        assertNoKey(newStore())
        assertFalse(keyringFile.exists())
        assertNull(masterKeySecret.value)
    }

    // A keyring this build can't decode may be a newer build's (downgrade), so
    // both halves stay on disk for it. Install still overwrites them.
    @Test
    fun undecodable_keyring_is_no_key_but_is_left_in_place() = runTest {
        newStore().install(mk, RECOVERY_KEY, keyringJson)
        val newerKeyring = keyringJson.replace("\"version\":1", "\"version\":2")
        keyringFile.writeText(newerKeyring)

        val store = newStore()
        assertNoKey(store)
        assertEquals(newerKeyring, keyringFile.readText())
        assertArrayEquals(mk, masterKeySecret.value)
        assertEquals(RECOVERY_KEY.toByteArray().toList(), recoveryKeySecret.value?.toList())

        store.install(mk, RECOVERY_KEY, keyringJson)
        assertEquals(keyringJson, store.keyringJson())
    }

    @Test
    fun undecodable_keyring_without_mk_is_deleted() = runTest {
        newStore().install(mk, RECOVERY_KEY, keyringJson)
        keyringFile.writeText("{not json")
        masterKeySecret.clear()

        assertNoKey(newStore())
        assertFalse(keyringFile.exists())
    }

    @Test
    fun lone_recovery_key_is_swept() = runTest {
        newStore().install(mk, RECOVERY_KEY, keyringJson)
        masterKeySecret.clear()
        keyringFile.delete()

        assertNoKey(newStore())
        assertNull(recoveryKeySecret.value)
    }

    @Test
    fun clear_is_no_key() = runTest {
        val store = newStore()
        store.install(mk, RECOVERY_KEY, keyringJson)
        store.clear()

        assertNoKey(store)
        assertFalse(keyringFile.exists())
        assertNull(masterKeySecret.value)
        assertNull(recoveryKeySecret.value)
    }

    @Test
    fun install_rejects_a_keyring_for_another_mk_and_keeps_the_current_key() = runTest {
        val store = newStore()
        store.install(mk, RECOVERY_KEY, keyringJson)

        val result = runCatching { store.install(ByteArray(32) { 7 }, null, keyringJson) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertArrayEquals(mk, store.masterKey())
        assertEquals(RECOVERY_KEY, store.recoveryKey())
    }

    @Test
    fun keystore_failure_is_unreadable_not_no_key_and_clears_nothing() = runTest {
        val store = newStore()
        store.install(mk, RECOVERY_KEY, keyringJson)
        masterKeySecret.readError = IllegalStateException("Keystore")

        val error = runCatching { store.masterKey() }.exceptionOrNull()

        assertTrue(error is BackupKeyUnreadableException)
        assertTrue(keyringFile.isFile)
        assertTrue(recoveryKeySecret.value != null)
    }

    private suspend fun assertNoKey(store: BackupKeyStore) {
        assertNull(store.masterKey())
        assertNull(store.recoveryKey())
        assertNull(store.keyringJson())
        assertEquals(BackupKeyState.NoKey, store.keyState.value)
    }

    private companion object {
        private const val RECOVERY_KEY = "0123456789ABCDEF"
        private const val CREATED_AT = 1_760_140_000_000L
    }
}
