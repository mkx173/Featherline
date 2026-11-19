package com.mkx.hrttracker.data.local

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.startup.StartupPreloader
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.SecureRandom
import java.security.Security
import javax.crypto.spec.SecretKeySpec

/**
 * The stored database passphrase can't be unwrapped in ways other than a missing key or a
 * missing stored value, and each is the typed lost-key error, so StartupPreloader flags it
 * and the activities show the toast and close instead of an empty Home.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabasePassphraseKeyLossTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs
        get() = context.getSharedPreferences("hrt_tracker_secure_storage", Context.MODE_PRIVATE)
    private val databaseFile
        get() = context.getDatabasePath("hrt_tracker.db")

    @Before
    fun setUp() {
        Security.addProvider(FakeAndroidKeyStore)
        prefs.edit().clear().commit()
        databaseFile.delete()
        // An installed app with data: a passphrase wrapped by the keystore key, and its database.
        DatabasePassphraseProvider(context).getPassphrase()
        databaseFile.parentFile?.mkdirs()
        databaseFile.writeText("encrypted database")
    }

    @After
    fun tearDown() {
        Security.removeProvider(FakeAndroidKeyStore.name)
        FakeAndroidKeyStore.storedKeys.clear()
        databaseFile.delete()
    }

    /**
     * The alias holds another key than the one that wrapped the passphrase. v1.4.2 leaves this
     * behind when the key is lost: its decrypt() generated a new key under the alias and failed.
     */
    @Test
    fun aKeyReplacedUnderTheAlias_isALostKey() {
        replaceTheKey()

        assertLostKey(passphraseFailure())
    }

    @Test
    fun aDamagedStoredPassphrase_isALostKey() {
        val wrapped = Base64.decode(prefs.getString("database_passphrase", null), Base64.NO_WRAP)
        wrapped[0] = (wrapped[0].toInt() xor 1).toByte()
        prefs.edit().putString("database_passphrase", Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit()

        assertLostKey(passphraseFailure())
    }

    @Test
    fun aStoredPassphraseThatIsNotBase64_isALostKey() {
        prefs.edit().putString("database_passphrase", "***not base64***").commit()

        assertLostKey(passphraseFailure())
    }

    @Test
    fun aMissingKey_startupFlagsTheLostKey() = runBlocking {
        FakeAndroidKeyStore.storedKeys.clear()

        assertEquals(true, keyLostAfterStartupOpen(passphraseFailure()))
    }

    /** What MainActivity and WidgetConfigActivity wait on (closeOnLostDatabaseKey). */
    @Test
    fun aKeyReplacedUnderTheAlias_startupFlagsTheLostKey() = runBlocking {
        replaceTheKey()

        assertEquals(true, keyLostAfterStartupOpen(passphraseFailure()))
    }

    private fun replaceTheKey() {
        FakeAndroidKeyStore.storedKeys[ALIAS] = SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes), "AES")
    }

    private fun passphraseFailure(): Throwable =
        runCatching { DatabasePassphraseProvider(context).getPassphrase() }.exceptionOrNull()!!

    private fun assertLostKey(failure: Throwable) {
        assertTrue("fails with $failure", failure is DatabaseKeyUnavailableException)
    }

    /**
     * The real StartupPreloader over a DatabaseHolder whose open throws what the real provider
     * threw (the real holder can't run here: it loads the SQLCipher native library first).
     * True once databaseKeyLost flips, null if it hasn't after the open has failed.
     */
    private suspend fun keyLostAfterStartupOpen(openFailure: Throwable): Boolean? {
        val holder: DatabaseHolder = mockk()
        every { holder.openAndSignal() } throws openFailure
        val preloader = StartupPreloader(
            appScope = CoroutineScope(Dispatchers.IO),
            databaseHolder = holder,
            homeSnapshotRepository = mockk(),
            medicationGroupRepository = mockk(),
            medicationLogRepository = mockk(),
            medicineRepository = mockk(),
            userProfileRepository = mockk(),
            settingsRepository = mockk(),
            medicationReminderScheduler = mockk(),
            diagnosticsLogger = mockk(relaxed = true),
        )
        preloader.startDatabaseOpen()
        return withTimeoutOrNull(3_000) {
            preloader.databaseKeyLost.first { it }
        }
    }

    private companion object {
        const val ALIAS = "hrt_tracker_database_master_key"
    }
}
