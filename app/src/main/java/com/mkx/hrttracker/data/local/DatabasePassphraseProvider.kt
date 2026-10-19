package com.mkx.hrttracker.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabasePassphraseProvider internal constructor(
    internal val secret: WrappedSecret
) {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(
        KeystoreWrappedSecret(
            context = context,
            keyAlias = MASTER_KEY_ALIAS,
            blobPrefKey = KEY_DATABASE_PASSPHRASE,
            ivPrefKey = KEY_DATABASE_PASSPHRASE_IV
        )
    )

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    fun getPassphrase(): ByteArray {
        cachedPassphrase?.let { return it.copyOf() }

        secret.read()?.let { passphrase ->
            try {
                cachePassphrase(passphrase)
                return passphrase.copyOf()
            } finally {
                passphrase.fill(0)
            }
        }

        val passphrase = ByteArray(PASSPHRASE_SIZE_BYTES).also(SecureRandom()::nextBytes)
        try {
            secret.write(passphrase)
            cachePassphrase(passphrase)
            return passphrase.copyOf()
        } finally {
            passphrase.fill(0)
        }
    }

    private fun cachePassphrase(passphrase: ByteArray) {
        cachedPassphrase?.fill(0)
        cachedPassphrase = passphrase.copyOf()
    }

    // These three names open every existing database. Never change them.
    private companion object {
        private const val KEY_DATABASE_PASSPHRASE = "database_passphrase"
        private const val KEY_DATABASE_PASSPHRASE_IV = "database_passphrase_iv"
        private const val MASTER_KEY_ALIAS = "hrt_tracker_database_master_key"
        private const val PASSPHRASE_SIZE_BYTES = 32
    }
}
