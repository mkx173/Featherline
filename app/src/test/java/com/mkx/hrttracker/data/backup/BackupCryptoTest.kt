package com.mkx.hrttracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupCryptoTest {
    private val backupCrypto = BackupCrypto(TestBackupArgon2KeyDeriver())

    @Test
    fun encrypt_writes_v3_gzip_container_and_decrypt_round_trips_with_empty_password() {
        val plaintext = """{"snapshotVersion":1}""".toByteArray()
        val encryptedBytes = backupCrypto.encrypt(
            plaintext = plaintext,
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
        assertEquals(1.toByte(), encryptedBytes[BackupCrypto.MAGIC_BYTES.size + 3])
        assertEquals(
            plaintext.size.toLong(),
            ByteBuffer.wrap(
                encryptedBytes,
                BackupCrypto.MAGIC_BYTES.size + V3_UNCOMPRESSED_LENGTH_OFFSET_AFTER_MAGIC,
                Long.SIZE_BYTES,
            ).long,
        )
        assertEquals("""{"snapshotVersion":1}""", decryptedBytes.toString(Charsets.UTF_8))
    }

    @Test
    fun encrypt_compresses_plaintext_before_encryption() {
        val repeatedJson = buildString {
            append("""{"medicationLogs":[""")
            repeat(400) { index ->
                if (index > 0) append(',')
                append(
                    """
                    {
                      "uuid": "00000000-0000-0000-0000-000000000001",
                      "category": "ESTRADIOL",
                      "applicationType": "ORAL",
                      "doseValueMg": 2.0
                    }
                    """.trimIndent()
                )
            }
            append("]}")
        }
        val plaintext = repeatedJson.toByteArray()
        val password = "secret".toCharArray()
        val v2EncryptedBytes = encryptLegacyV2Uncompressed(
            plaintext = plaintext,
            password = password,
        )
        val v3EncryptedBytes = backupCrypto.encrypt(
            plaintext = plaintext,
            password = password,
            secureRandom = SecureRandom(),
        )

        assertTrue(
            "Expected gzip v3 container (${v3EncryptedBytes.size} bytes) to be smaller than v2 (${v2EncryptedBytes.size} bytes).",
            v3EncryptedBytes.size < v2EncryptedBytes.size / 2,
        )
        assertEquals(repeatedJson, backupCrypto.decrypt(v3EncryptedBytes, password).toString(Charsets.UTF_8))
    }

    @Test
    fun decrypt_accepts_legacy_v2_uncompressed_container() {
        val encryptedBytes = encryptLegacyV2Uncompressed(
            plaintext = """{"snapshotVersion":1}""".toByteArray(),
            password = "secret".toCharArray(),
        )

        val decryptedBytes = backupCrypto.decrypt(
            encryptedBytes = encryptedBytes,
            password = "secret".toCharArray(),
        )

        assertEquals(2.toByte(), encryptedBytes[BackupCrypto.MAGIC_BYTES.size])
        assertEquals("""{"snapshotVersion":1}""", decryptedBytes.toString(Charsets.UTF_8))
    }

    @Test
    fun decrypt_rejects_v3_container_declaring_more_than_max_restore_size() {
        val encryptedBytes = encryptV3GzipWithDeclaredLength(
            compressedPayload = gzip("""{"snapshotVersion":1}""".toByteArray()),
            password = "secret".toCharArray(),
            declaredUncompressedLengthBytes = MAX_BACKUP_JSON_BYTES + 1,
        )

        try {
            backupCrypto.decrypt(
                encryptedBytes = encryptedBytes,
                password = "secret".toCharArray(),
            )
            fail("Expected oversized v3 backup containers to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun decrypt_rejects_v3_gzip_payload_exceeding_declared_length() {
        val encryptedBytes = encryptV3GzipWithDeclaredLength(
            compressedPayload = gzip("""{"snapshotVersion":1}""".toByteArray()),
            password = "secret".toCharArray(),
            declaredUncompressedLengthBytes = 0,
        )

        try {
            backupCrypto.decrypt(
                encryptedBytes = encryptedBytes,
                password = "secret".toCharArray(),
            )
            fail("Expected v3 backup containers with mismatched payload lengths to be rejected.")
        } catch (error: IOException) {
            assertTrue(
                "Expected decompression failure, got: ${error.message}",
                error.message?.contains("Unable to decompress") == true,
            )
        }
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
        truncatedBytes[BackupCrypto.MAGIC_BYTES.size] = 2

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

    private fun encryptV3GzipWithDeclaredLength(
        compressedPayload: ByteArray,
        password: CharArray,
        declaredUncompressedLengthBytes: Long,
    ): ByteArray {
        val parameters = BackupCrypto.DEFAULT_ARGON2_PARAMETERS
        val salt = ByteArray(V2_SALT_LENGTH_BYTES) { index -> (index + 1).toByte() }
        val nonce = ByteArray(V2_NONCE_LENGTH_BYTES) { index -> (index + 17).toByte() }
        val header = ByteBuffer.allocate(V3_FIXED_HEADER_LENGTH + salt.size + nonce.size)
            .put(BackupCrypto.MAGIC_BYTES)
            .put(BackupCrypto.CURRENT_BACKUP_CONTAINER_VERSION.toByte())
            .put(2.toByte())
            .put(1.toByte())
            .put(1.toByte())
            .putLong(declaredUncompressedLengthBytes)
            .putInt(parameters.timeCostInIterations)
            .putInt(parameters.memoryCostInKibibyte)
            .putInt(parameters.parallelism)
            .putInt(parameters.hashLengthBytes)
            .put(salt.size.toByte())
            .put(nonce.size.toByte())
            .put(salt)
            .put(nonce)
            .array()
        val rawKey = TestBackupArgon2KeyDeriver().deriveKey(
            password = password,
            salt = salt,
            parameters = parameters,
        )
        val ciphertext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(rawKey, "AES"),
                GCMParameterSpec(128, nonce),
            )
            cipher.updateAAD(header)
            cipher.doFinal(compressedPayload)
        } finally {
            rawKey.fill(0)
        }
        return ByteArray(header.size + ciphertext.size).also { container ->
            header.copyInto(container)
            ciphertext.copyInto(container, destinationOffset = header.size)
        }
    }

    private fun encryptLegacyV2Uncompressed(
        plaintext: ByteArray,
        password: CharArray,
    ): ByteArray {
        val parameters = BackupCrypto.DEFAULT_ARGON2_PARAMETERS
        val salt = ByteArray(V2_SALT_LENGTH_BYTES) { index -> (index + 1).toByte() }
        val nonce = ByteArray(V2_NONCE_LENGTH_BYTES) { index -> (index + 17).toByte() }
        val header = ByteBuffer.allocate(V2_FIXED_HEADER_LENGTH + salt.size + nonce.size)
            .put(BackupCrypto.MAGIC_BYTES)
            .put(2.toByte())
            .put(2.toByte())
            .put(1.toByte())
            .putInt(parameters.timeCostInIterations)
            .putInt(parameters.memoryCostInKibibyte)
            .putInt(parameters.parallelism)
            .putInt(parameters.hashLengthBytes)
            .put(salt.size.toByte())
            .put(nonce.size.toByte())
            .put(salt)
            .put(nonce)
            .array()
        val rawKey = TestBackupArgon2KeyDeriver().deriveKey(
            password = password,
            salt = salt,
            parameters = parameters,
        )
        val ciphertext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(rawKey, "AES"),
                GCMParameterSpec(128, nonce),
            )
            cipher.updateAAD(header)
            cipher.doFinal(plaintext)
        } finally {
            rawKey.fill(0)
        }
        return ByteArray(header.size + ciphertext.size).also { container ->
            header.copyInto(container)
            ciphertext.copyInto(container, destinationOffset = header.size)
        }
    }

    private fun gzip(plaintext: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { gzipStream ->
            gzipStream.write(plaintext)
        }
        return outputStream.toByteArray()
    }

    private companion object {
        private const val V2_FIXED_HEADER_LENGTH = 28
        private const val V3_FIXED_HEADER_LENGTH = 37
        private const val V2_SALT_LENGTH_BYTES = 16
        private const val V2_NONCE_LENGTH_BYTES = 12
        private const val V3_UNCOMPRESSED_LENGTH_OFFSET_AFTER_MAGIC = 4
        private const val MAX_BACKUP_JSON_BYTES = 128L * 1024L * 1024L
    }
}
