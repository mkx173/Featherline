package com.mkx.hrttracker.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * The database opens only with the passphrase it was created with. When that passphrase,
 * or the keystore key wrapping it, is gone, a new one can't open the database: the
 * provider must fail, not generate one over what is left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabasePassphraseProviderTest {
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
    }

    @After
    fun tearDown() {
        Security.removeProvider(FakeAndroidKeyStore.name)
        FakeAndroidKeyStore.storedKeys.clear()
        databaseFile.delete()
    }

    @Test
    fun aFreshInstallGeneratesAPassphraseAndReadsTheSameOneBack() {
        val generated = DatabasePassphraseProvider(context).getPassphrase()
        createDatabaseFile()

        assertArrayEquals(generated, DatabasePassphraseProvider(context).getPassphrase())
    }

    @Test
    fun aLostStoredPassphraseFailsWithoutGeneratingOneOverTheDatabase() {
        DatabasePassphraseProvider(context).getPassphrase()
        createDatabaseFile()
        // The wrapped passphrase is gone; the database it opens is still there.
        prefs.edit().clear().commit()

        val result = runCatching { DatabasePassphraseProvider(context).getPassphrase() }

        assertFalse(
            "a new passphrase was stored over the existing database",
            prefs.contains("database_passphrase"),
        )
        assertTrue(
            "fails with ${result.exceptionOrNull()}",
            result.exceptionOrNull() is DatabaseKeyUnavailableException,
        )
    }

    @Test
    fun aLostKeystoreKeyFailsWithoutCreatingANewOne() {
        DatabasePassphraseProvider(context).getPassphrase()
        createDatabaseFile()
        // The keystore lost the key that wraps the stored passphrase.
        FakeAndroidKeyStore.storedKeys.clear()

        val result = runCatching { DatabasePassphraseProvider(context).getPassphrase() }

        assertTrue(
            "unwrapping created keys ${FakeAndroidKeyStore.storedKeys.keys} in the keystore",
            FakeAndroidKeyStore.storedKeys.isEmpty(),
        )
        assertTrue(
            "fails with ${result.exceptionOrNull()}",
            result.exceptionOrNull() is DatabaseKeyUnavailableException,
        )
    }

    private fun createDatabaseFile() {
        databaseFile.parentFile?.mkdirs()
        databaseFile.writeText("encrypted database")
    }
}

/** Robolectric has no AndroidKeyStore; this keeps its AES keys in memory instead. */
@Suppress("DEPRECATION")
object FakeAndroidKeyStore : Provider("AndroidKeyStore", 1.0, "In-memory AndroidKeyStore") {
    val storedKeys = ConcurrentHashMap<String, SecretKey>()

    init {
        put("KeyStore.AndroidKeyStore", FakeKeyStoreSpi::class.java.name)
        put("KeyGenerator.AES", FakeAesKeyGeneratorSpi::class.java.name)
    }
}

class FakeKeyStoreSpi : KeyStoreSpi() {
    private val keys get() = FakeAndroidKeyStore.storedKeys

    override fun engineGetKey(alias: String, password: CharArray?): Key? = keys[alias]
    override fun engineContainsAlias(alias: String): Boolean = keys.containsKey(alias)
    override fun engineDeleteEntry(alias: String) {
        keys.remove(alias)
    }

    override fun engineAliases(): Enumeration<String> = Collections.enumeration(keys.keys)
    override fun engineSize(): Int = keys.size
    override fun engineIsKeyEntry(alias: String): Boolean = keys.containsKey(alias)
    override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
    override fun engineGetCertificate(alias: String): Certificate? = null
    override fun engineGetCreationDate(alias: String): Date? = null
    override fun engineIsCertificateEntry(alias: String): Boolean = false
    override fun engineGetCertificateAlias(cert: Certificate): String? = null
    override fun engineSetKeyEntry(
        alias: String,
        key: Key,
        password: CharArray?,
        chain: Array<out Certificate>?,
    ) = throw UnsupportedOperationException()

    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) =
        throw UnsupportedOperationException()

    override fun engineSetCertificateEntry(alias: String, cert: Certificate) =
        throw UnsupportedOperationException()

    override fun engineStore(stream: OutputStream?, password: CharArray?) =
        throw UnsupportedOperationException()
}

class FakeAesKeyGeneratorSpi : KeyGeneratorSpi() {
    private var alias: String? = null

    override fun engineInit(params: AlgorithmParameterSpec, random: SecureRandom?) {
        alias = (params as KeyGenParameterSpec).keystoreAlias
    }

    override fun engineInit(random: SecureRandom?) = throw UnsupportedOperationException()
    override fun engineInit(keysize: Int, random: SecureRandom?) =
        throw UnsupportedOperationException()

    override fun engineGenerateKey(): SecretKey {
        val key = SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes), "AES")
        FakeAndroidKeyStore.storedKeys[checkNotNull(alias)] = key
        return key
    }
}
