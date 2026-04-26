package com.mkx.hrttracker.data.backup

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupCrypto internal constructor(
    private val backupArgon2KeyDeriver: BackupArgon2KeyDeriver,
) {
    @Inject
    constructor() : this(Argon2KtBackupArgon2KeyDeriver())

    fun encryptSnapshotJson(
        json: String,
        password: CharArray,
    ): ByteArray {
        val plaintextBytes = json.toByteArray(Charsets.UTF_8)
        return try {
            encrypt(
                plaintext = plaintextBytes,
                password = password,
            )
        } finally {
            plaintextBytes.fill(0)
        }
    }

    fun decryptSnapshotJson(
        encryptedBytes: ByteArray,
        password: CharArray,
    ): String {
        val plaintextBytes = decrypt(
            encryptedBytes = encryptedBytes,
            password = password,
        )
        return try {
            plaintextBytes.toString(Charsets.UTF_8)
        } finally {
            plaintextBytes.fill(0)
        }
    }

    internal fun encrypt(
        plaintext: ByteArray,
        password: CharArray,
        secureRandom: SecureRandom = SecureRandom(),
        argon2Parameters: BackupArgon2Parameters = DEFAULT_ARGON2_PARAMETERS,
    ): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        val nonce = ByteArray(NONCE_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(nonce)

        val header = buildArgon2Header(
            parameters = argon2Parameters,
            salt = salt,
            nonce = nonce,
        )
        val key = deriveSecretKey(
            password = password,
            salt = salt,
            kdfParameters = argon2Parameters,
        )
        return try {
            encryptWithAesGcm(
                plaintext = plaintext,
                nonce = nonce,
                header = header,
                key = key,
            )
        } finally {
            header.fill(0)
            salt.fill(0)
            nonce.fill(0)
        }
    }

    internal fun encryptLegacyPbkdf2(
        plaintext: ByteArray,
        password: CharArray,
        secureRandom: SecureRandom = SecureRandom(),
        pbkdf2Iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
    ): ByteArray {
        require(pbkdf2Iterations > 0) {
            "PBKDF2 iterations must be positive."
        }
        val salt = ByteArray(SALT_LENGTH_BYTES)
        val nonce = ByteArray(NONCE_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(nonce)

        val header = buildLegacyPbkdf2Header(
            pbkdf2Iterations = pbkdf2Iterations,
            salt = salt,
            nonce = nonce,
        )
        val key = deriveSecretKey(
            password = password,
            salt = salt,
            kdfParameters = LegacyPbkdf2Parameters(
                iterations = pbkdf2Iterations,
            ),
        )
        return try {
            encryptWithAesGcm(
                plaintext = plaintext,
                nonce = nonce,
                header = header,
                key = key,
            )
        } finally {
            header.fill(0)
            salt.fill(0)
            nonce.fill(0)
        }
    }

    internal fun decrypt(
        encryptedBytes: ByteArray,
        password: CharArray,
    ): ByteArray {
        val parsedContainer = parseContainer(encryptedBytes)
        val key = deriveSecretKey(
            password = password,
            salt = parsedContainer.salt,
            kdfParameters = parsedContainer.kdfParameters,
        )
        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, parsedContainer.nonce),
            )
            cipher.updateAAD(parsedContainer.header)
            cipher.doFinal(parsedContainer.ciphertext)
        } catch (error: GeneralSecurityException) {
            throw IOException("Unable to decrypt the selected backup file.", error)
        } finally {
            parsedContainer.header.fill(0)
            parsedContainer.salt.fill(0)
            parsedContainer.nonce.fill(0)
            parsedContainer.ciphertext.fill(0)
        }
    }

    private fun deriveSecretKey(
        password: CharArray,
        salt: ByteArray,
        kdfParameters: BackupKdfParameters,
    ): SecretKeySpec {
        val rawKey = when (kdfParameters) {
            is BackupArgon2Parameters -> backupArgon2KeyDeriver.deriveKey(
                password = password,
                salt = salt,
                parameters = kdfParameters,
            )

            is LegacyPbkdf2Parameters -> deriveLegacyPbkdf2Key(
                password = password,
                salt = salt,
                iterations = kdfParameters.iterations,
            )
        }
        return try {
            require(rawKey.size == AES_KEY_LENGTH_BYTES) {
                "Backup key derivation returned ${rawKey.size} bytes, expected $AES_KEY_LENGTH_BYTES."
            }
            SecretKeySpec(rawKey, AES_KEY_ALGORITHM)
        } finally {
            rawKey.fill(0)
        }
    }

    private fun deriveLegacyPbkdf2Key(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val keySpec = PBEKeySpec(
            password,
            salt,
            iterations,
            AES_KEY_LENGTH_BITS,
        )
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                .generateSecret(keySpec)
                .encoded
        } catch (error: GeneralSecurityException) {
            throw IOException("Unable to derive the backup encryption key.", error)
        } finally {
            keySpec.clearPassword()
        }
    }

    private fun encryptWithAesGcm(
        plaintext: ByteArray,
        nonce: ByteArray,
        header: ByteArray,
        key: SecretKeySpec,
    ): ByteArray {
        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce),
            )
            cipher.updateAAD(header)
            val ciphertext = cipher.doFinal(plaintext)
            try {
                ByteArray(header.size + ciphertext.size).also { container ->
                    header.copyInto(container, destinationOffset = 0)
                    ciphertext.copyInto(container, destinationOffset = header.size)
                }
            } finally {
                ciphertext.fill(0)
            }
        } catch (error: GeneralSecurityException) {
            throw IOException("Unable to encrypt the backup payload.", error)
        }
    }

    private fun buildLegacyPbkdf2Header(
        pbkdf2Iterations: Int,
        salt: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        return ByteBuffer.allocate(FIXED_HEADER_LENGTH_V1 + salt.size + nonce.size)
            .put(MAGIC_BYTES)
            .put(LEGACY_BACKUP_CONTAINER_VERSION.toByte())
            .put(KDF_PBKDF2_SHA256.toByte())
            .put(CIPHER_AES_256_GCM.toByte())
            .putInt(pbkdf2Iterations)
            .put(salt.size.toByte())
            .put(nonce.size.toByte())
            .put(salt)
            .put(nonce)
            .array()
    }

    private fun buildArgon2Header(
        parameters: BackupArgon2Parameters,
        salt: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        return ByteBuffer.allocate(FIXED_HEADER_LENGTH_V2 + salt.size + nonce.size)
            .put(MAGIC_BYTES)
            .put(CURRENT_BACKUP_CONTAINER_VERSION.toByte())
            .put(KDF_ARGON2_ID.toByte())
            .put(CIPHER_AES_256_GCM.toByte())
            .putInt(parameters.timeCostInIterations)
            .putInt(parameters.memoryCostInKibibyte)
            .putInt(parameters.parallelism)
            .putInt(parameters.hashLengthBytes)
            .put(salt.size.toByte())
            .put(nonce.size.toByte())
            .put(salt)
            .put(nonce)
            .array()
    }

    private fun parseContainer(
        encryptedBytes: ByteArray,
    ): ParsedBackupContainer {
        require(encryptedBytes.size > FIXED_HEADER_LENGTH_V1) {
            "Backup file is too small to be valid."
        }

        val buffer = ByteBuffer.wrap(encryptedBytes)
        val actualMagic = ByteArray(MAGIC_BYTES.size)
        buffer.get(actualMagic)
        require(actualMagic.contentEquals(MAGIC_BYTES)) {
            "Backup file does not have a supported header."
        }

        return when (val backupVersion = buffer.get().toInt() and BYTE_MASK) {
            LEGACY_BACKUP_CONTAINER_VERSION -> parseLegacyPbkdf2Container(
                encryptedBytes = encryptedBytes,
                buffer = buffer,
            )

            CURRENT_BACKUP_CONTAINER_VERSION -> parseArgon2Container(
                encryptedBytes = encryptedBytes,
                buffer = buffer,
            )

            else -> throw IllegalArgumentException(
                "Unsupported backup file version: $backupVersion."
            )
        }
    }

    private fun parseLegacyPbkdf2Container(
        encryptedBytes: ByteArray,
        buffer: ByteBuffer,
    ): ParsedBackupContainer {
        val kdfType = buffer.get().toInt() and BYTE_MASK
        require(kdfType == KDF_PBKDF2_SHA256) {
            "Unsupported legacy backup KDF: $kdfType."
        }
        val cipherType = buffer.get().toInt() and BYTE_MASK
        require(cipherType == CIPHER_AES_256_GCM) {
            "Unsupported backup cipher: $cipherType."
        }
        val pbkdf2Iterations = buffer.int
        require(pbkdf2Iterations > 0) {
            "Backup file declares an invalid KDF iteration count."
        }
        return finishParsingContainer(
            encryptedBytes = encryptedBytes,
            buffer = buffer,
            kdfParameters = LegacyPbkdf2Parameters(
                iterations = pbkdf2Iterations,
            ),
        )
    }

    private fun parseArgon2Container(
        encryptedBytes: ByteArray,
        buffer: ByteBuffer,
    ): ParsedBackupContainer {
        val kdfType = buffer.get().toInt() and BYTE_MASK
        require(kdfType == KDF_ARGON2_ID) {
            "Unsupported backup KDF: $kdfType."
        }
        val cipherType = buffer.get().toInt() and BYTE_MASK
        require(cipherType == CIPHER_AES_256_GCM) {
            "Unsupported backup cipher: $cipherType."
        }

        val argon2Parameters = BackupArgon2Parameters(
            timeCostInIterations = buffer.int,
            memoryCostInKibibyte = buffer.int,
            parallelism = buffer.int,
            hashLengthBytes = buffer.int,
        )
        require(argon2Parameters.timeCostInIterations > 0) {
            "Backup file declares an invalid Argon2 time cost."
        }
        require(argon2Parameters.memoryCostInKibibyte > 0) {
            "Backup file declares an invalid Argon2 memory cost."
        }
        require(argon2Parameters.parallelism > 0) {
            "Backup file declares an invalid Argon2 parallelism."
        }
        require(argon2Parameters.hashLengthBytes > 0) {
            "Backup file declares an invalid Argon2 hash length."
        }

        return finishParsingContainer(
            encryptedBytes = encryptedBytes,
            buffer = buffer,
            kdfParameters = argon2Parameters,
        )
    }

    private fun finishParsingContainer(
        encryptedBytes: ByteArray,
        buffer: ByteBuffer,
        kdfParameters: BackupKdfParameters,
    ): ParsedBackupContainer {
        val saltLength = buffer.get().toInt() and BYTE_MASK
        val nonceLength = buffer.get().toInt() and BYTE_MASK
        require(saltLength > 0) {
            "Backup file declares an invalid salt length."
        }
        require(nonceLength > 0) {
            "Backup file declares an invalid nonce length."
        }
        require(buffer.remaining() > saltLength + nonceLength) {
            "Backup file payload is truncated."
        }

        val salt = ByteArray(saltLength)
        buffer.get(salt)
        val nonce = ByteArray(nonceLength)
        buffer.get(nonce)
        val headerLength = buffer.position()
        val ciphertextLength = encryptedBytes.size - headerLength
        require(ciphertextLength > 0) {
            "Backup file ciphertext is missing."
        }

        return ParsedBackupContainer(
            header = encryptedBytes.copyOfRange(0, headerLength),
            kdfParameters = kdfParameters,
            salt = salt,
            nonce = nonce,
            ciphertext = encryptedBytes.copyOfRange(headerLength, encryptedBytes.size),
        )
    }

    companion object {
        internal const val LEGACY_BACKUP_CONTAINER_VERSION = 1
        internal const val CURRENT_BACKUP_CONTAINER_VERSION = 2
        internal const val DEFAULT_PBKDF2_ITERATIONS = 310_000
        internal val DEFAULT_ARGON2_PARAMETERS = BackupArgon2Parameters(
            timeCostInIterations = 3,
            memoryCostInKibibyte = 65_536,
            parallelism = 1,
            hashLengthBytes = AES_KEY_LENGTH_BYTES,
        )
        internal val MAGIC_BYTES: ByteArray = "HRTBKP1".encodeToByteArray()

        private const val KDF_PBKDF2_SHA256 = 1
        private const val KDF_ARGON2_ID = 2
        private const val CIPHER_AES_256_GCM = 1
        private const val FIXED_HEADER_LENGTH_V1 = 16
        private const val FIXED_HEADER_LENGTH_V2 = 28
        private const val SALT_LENGTH_BYTES = 16
        private const val NONCE_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val AES_KEY_LENGTH_BITS = 256
        private const val AES_KEY_LENGTH_BYTES = 32
        private const val BYTE_MASK = 0xFF
        private const val AES_KEY_ALGORITHM = "AES"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    }
}

internal fun interface BackupArgon2KeyDeriver {
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        parameters: BackupArgon2Parameters,
    ): ByteArray
}

internal data class BackupArgon2Parameters(
    val timeCostInIterations: Int,
    val memoryCostInKibibyte: Int,
    val parallelism: Int,
    val hashLengthBytes: Int,
) : BackupKdfParameters

private data class LegacyPbkdf2Parameters(
    val iterations: Int,
) : BackupKdfParameters

private sealed interface BackupKdfParameters

private data class ParsedBackupContainer(
    val header: ByteArray,
    val kdfParameters: BackupKdfParameters,
    val salt: ByteArray,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

private class Argon2KtBackupArgon2KeyDeriver : BackupArgon2KeyDeriver {
    private val argon2Kt by lazy(LazyThreadSafetyMode.NONE) { Argon2Kt() }

    override fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        parameters: BackupArgon2Parameters,
    ): ByteArray {
        val passwordBytes = password.toUtf8ByteArray()
        return try {
            argon2Kt.hash(
                Argon2Mode.ARGON2_ID,
                passwordBytes,
                salt,
                parameters.timeCostInIterations,
                parameters.memoryCostInKibibyte,
                parameters.parallelism,
                parameters.hashLengthBytes,
            ).rawHashAsByteArray()
        } catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) {
                throw error
            }
            throw IOException("Unable to derive the backup encryption key.", error)
        } finally {
            passwordBytes.fill(0)
        }
    }
}

private fun CharArray.toUtf8ByteArray(): ByteArray {
    val encodedBuffer = StandardCharsets.UTF_8
        .newEncoder()
        .encode(CharBuffer.wrap(this))
    return try {
        ByteArray(encodedBuffer.remaining()).also { encodedBytes ->
            encodedBuffer.get(encodedBytes)
        }
    } finally {
        if (encodedBuffer.hasArray()) {
            encodedBuffer.array().fill(0)
        }
    }
}
