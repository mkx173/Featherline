package com.mkx.hrttracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.security.SecureRandom

class BackupCryptoTest {
    private val backupCrypto = BackupCrypto(TestBackupArgon2KeyDeriver())

    @Test
    fun encrypt_and_decrypt_round_trip_with_empty_password() {
        val encryptedBytes = backupCrypto.encrypt(
            plaintext = """{"snapshotVersion":1}""".toByteArray(),
            password = charArrayOf(),
            secureRandom = SecureRandom(),
        )
        val decryptedBytes = backupCrypto.decrypt(
            encryptedBytes = encryptedBytes,
            password = charArrayOf(),
        )

        assertTrue(encryptedBytes.copyOfRange(0, BackupCrypto.MAGIC_BYTES.size).contentEquals(BackupCrypto.MAGIC_BYTES))
        assertEquals(
            BackupCrypto.CURRENT_BACKUP_CONTAINER_VERSION.toByte(),
            encryptedBytes[BackupCrypto.MAGIC_BYTES.size],
        )
        assertEquals("""{"snapshotVersion":1}""", decryptedBytes.toString(Charsets.UTF_8))
    }

    @Test
    fun decrypt_rejects_wrong_password() {
        val encryptedBytes = backupCrypto.encrypt(
            plaintext = """{"snapshotVersion":1}""".toByteArray(),
            password = "secret".toCharArray(),
            secureRandom = SecureRandom(),
        )

        try {
            backupCrypto.decrypt(
                encryptedBytes = encryptedBytes,
                password = "wrong".toCharArray(),
            )
            fail("Expected decryption with the wrong password to fail.")
        } catch (_: IOException) {
            // Expected.
        }
    }

    @Test
    fun decrypt_rejects_corrupted_ciphertext() {
        val encryptedBytes = backupCrypto.encrypt(
            plaintext = """{"snapshotVersion":1}""".toByteArray(),
            password = "secret".toCharArray(),
            secureRandom = SecureRandom(),
        )
        encryptedBytes[encryptedBytes.lastIndex] =
            (encryptedBytes.last().toInt() xor 0x01).toByte()

        try {
            backupCrypto.decrypt(
                encryptedBytes = encryptedBytes,
                password = "secret".toCharArray(),
            )
            fail("Expected decryption of corrupted data to fail.")
        } catch (_: IOException) {
            // Expected.
        }
    }

    @Test
    fun decrypt_rejects_unsupported_container_version() {
        val encryptedBytes = ByteArray(BackupCrypto.MAGIC_BYTES.size + 1)
        BackupCrypto.MAGIC_BYTES.copyInto(encryptedBytes, destinationOffset = 0)
        encryptedBytes[BackupCrypto.MAGIC_BYTES.size] = 1

        try {
            backupCrypto.decrypt(
                encryptedBytes = encryptedBytes,
                password = "secret".toCharArray(),
            )
            fail("Expected unsupported backup container versions to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun decrypt_rejects_truncated_v2_header_without_buffer_underflow() {
        val truncatedBytes = ByteArray(BackupCrypto.MAGIC_BYTES.size + 10)
        BackupCrypto.MAGIC_BYTES.copyInto(truncatedBytes, destinationOffset = 0)
        truncatedBytes[BackupCrypto.MAGIC_BYTES.size] =
            BackupCrypto.CURRENT_BACKUP_CONTAINER_VERSION.toByte()

        try {
            backupCrypto.decrypt(
                encryptedBytes = truncatedBytes,
                password = "secret".toCharArray(),
            )
            fail("Expected truncated v2 backup headers to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
