package com.mkx.hrttracker.data.local

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabasePassphraseProviderTest {

    // These names open every existing user database: a change here locks users out.
    @Test
    fun storage_names_are_pinned() {
        val provider = DatabasePassphraseProvider(mockk<Context>())
        val secret = provider.secret as KeystoreWrappedSecret

        assertEquals("hrt_tracker_secure_storage", KeystoreWrappedSecret.PREFS_NAME)
        assertEquals("hrt_tracker_database_master_key", secret.keyAlias)
        assertEquals("database_passphrase", secret.blobPrefKey)
        assertEquals("database_passphrase_iv", secret.ivPrefKey)
    }

    @Test
    fun generate_on_miss_writes_once_then_reads_back_the_same_bytes() {
        val secret = FakeWrappedSecret()

        val generated = DatabasePassphraseProvider(secret).getPassphrase()
        assertEquals(32, generated.size)
        assertEquals(1, secret.writes)
        assertArrayEquals(generated, secret.value)

        // A fresh provider (new process) has no cache and must read, not regenerate.
        val reread = DatabasePassphraseProvider(secret).getPassphrase()
        assertArrayEquals(generated, reread)
        assertEquals(1, secret.writes)
    }
}

class FakeWrappedSecret(var value: ByteArray? = null) : WrappedSecret {
    var writes = 0
        private set

    override fun read(): ByteArray? = value?.copyOf()

    override fun write(clearBytes: ByteArray) {
        writes++
        value = clearBytes.copyOf()
    }

    override fun clear() {
        value = null
    }
}
