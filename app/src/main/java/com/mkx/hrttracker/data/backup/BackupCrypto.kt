package com.mkx.hrttracker.data.backup

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
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

    fun validateEncryptedBackupContainer(
        encryptedBytes: ByteArray,
    ) {
        val parsedContainer = parseContainer(encryptedBytes)
        parsedContainer.header.fill(0)
        parsedContainer.salt.fill(0)
        parsedContainer.nonce.fill(0)
        parsedContainer.ciphertext.fill(0)
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

        val compressedPlaintext = gzip(plaintext)
        return try {
            val header = buildArgon2Header(
                parameters = argon2Parameters,
                salt = salt,
                nonce = nonce,
                uncompressedLengthBytes = plaintext.size.toLong(),
            )
            val key = deriveSecretKey(
                password = password,
                salt = salt,
                argon2Parameters = argon2Parameters,
            )
            try {
                encryptWithAesGcm(
                    plaintext = compressedPlaintext,
                    nonce = nonce,
                    header = header,
                    key = key,
                )
            } finally {
                header.fill(0)
            }
        } finally {
            compressedPlaintext.fill(0)
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
            argon2Parameters = parsedContainer.argon2Parameters,
        )
        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, parsedContainer.nonce),
            )
            cipher.updateAAD(parsedContainer.header)
            val decryptedPayload = cipher.doFinal(parsedContainer.ciphertext)
            try {
                when (parsedContainer.compressionType) {
                    COMPRESSION_NONE -> decryptedPayload
                    COMPRESSION_GZIP -> gunzip(
                        compressedPayload = decryptedPayload,
                        expectedUncompressedLengthBytes = checkNotNull(
                            parsedContainer.uncompressedLengthBytes
                        ),
                    )

                    else -> error("Unexpected backup compression: ${parsedContainer.compressionType}.")
                }
            } finally {
                if (parsedContainer.compressionType != COMPRESSION_NONE) {
                    decryptedPayload.fill(0)
                }
            }
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
        argon2Parameters: BackupArgon2Parameters,
    ): SecretKeySpec {
        val rawKey = backupArgon2KeyDeriver.deriveKey(
            password = password,
            salt = salt,
            parameters = argon2Parameters,
        )
        return try {
            require(rawKey.size == AES_KEY_LENGTH_BYTES) {
                "Backup key derivation returned ${rawKey.size} bytes, expected $AES_KEY_LENGTH_BYTES."
            }
            SecretKeySpec(rawKey, AES_KEY_ALGORITHM)
        } finally {
            rawKey.fill(0)
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

    private fun buildArgon2Header(
        parameters: BackupArgon2Parameters,
        salt: ByteArray,
        nonce: ByteArray,
        uncompressedLengthBytes: Long,
    ): ByteArray {
        return ByteBuffer.allocate(FIXED_HEADER_LENGTH_V3 + salt.size + nonce.size)
            .put(MAGIC_BYTES)
            .put(CURRENT_BACKUP_CONTAINER_VERSION.toByte())
            .put(KDF_ARGON2_ID.toByte())
            .put(CIPHER_AES_256_GCM.toByte())
            .put(COMPRESSION_GZIP.toByte())
            .putLong(uncompressedLengthBytes)
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
        require(encryptedBytes.size > MAGIC_BYTES.size) {
            "Backup file is too small to be valid."
        }

        val buffer = ByteBuffer.wrap(encryptedBytes)
        val actualMagic = ByteArray(MAGIC_BYTES.size)
        buffer.get(actualMagic)
        require(actualMagic.contentEquals(MAGIC_BYTES)) {
            "Backup file does not have a supported header."
        }

        val backupVersion = buffer.get().toInt() and BYTE_MASK
        val fixedHeaderLength = when (backupVersion) {
            LEGACY_UNCOMPRESSED_BACKUP_CONTAINER_VERSION -> FIXED_HEADER_LENGTH_V2
            CURRENT_BACKUP_CONTAINER_VERSION -> FIXED_HEADER_LENGTH_V3
            else -> throw IllegalArgumentException("Unsupported backup file version: $backupVersion.")
        }
        require(encryptedBytes.size >= fixedHeaderLength) {
            "Backup file header is truncated."
        }
        return parseArgon2Container(
            encryptedBytes = encryptedBytes,
            buffer = buffer,
            backupVersion = backupVersion,
        )
    }

    private fun parseArgon2Container(
        encryptedBytes: ByteArray,
        buffer: ByteBuffer,
        backupVersion: Int,
    ): ParsedBackupContainer {
        val kdfType = buffer.get().toInt() and BYTE_MASK
        require(kdfType == KDF_ARGON2_ID) {
            "Unsupported backup KDF: $kdfType."
        }
        val cipherType = buffer.get().toInt() and BYTE_MASK
        require(cipherType == CIPHER_AES_256_GCM) {
            "Unsupported backup cipher: $cipherType."
        }
        val compressionType = when (backupVersion) {
            LEGACY_UNCOMPRESSED_BACKUP_CONTAINER_VERSION -> COMPRESSION_NONE
            CURRENT_BACKUP_CONTAINER_VERSION -> buffer.get().toInt() and BYTE_MASK
            else -> error("Unexpected backup file version: $backupVersion.")
        }
        require(compressionType in SUPPORTED_COMPRESSION_TYPES) {
            "Unsupported backup compression: $compressionType."
        }
        val uncompressedLengthBytes = when (backupVersion) {
            LEGACY_UNCOMPRESSED_BACKUP_CONTAINER_VERSION -> null
            CURRENT_BACKUP_CONTAINER_VERSION -> buffer.long
            else -> error("Unexpected backup file version: $backupVersion.")
        }
        if (uncompressedLengthBytes != null) {
            require(uncompressedLengthBytes >= 0L) {
                "Backup file declares an invalid uncompressed payload length."
            }
            require(uncompressedLengthBytes <= MAX_BACKUP_JSON_BYTES) {
                "Backup file exceeds the maximum supported restore size."
            }
        }

        val argon2Parameters = BackupArgon2Parameters(
            timeCostInIterations = buffer.int,
            memoryCostInKibibyte = buffer.int,
            parallelism = buffer.int,
            hashLengthBytes = buffer.int,
        )
        // Reject hostile or corrupt KDF profiles before deriving a key. Argon2
        // runs before AES-GCM authentication, so an unbounded memory/time cost
        // in a malicious or damaged header would otherwise hang or OOM an
        // otherwise-legitimate restore attempt. The bounds sit well above the
        // export defaults (time 3, memory 64 MiB, parallelism 1) so real
        // backups — and headroom for future hardening — stay valid.
        require(argon2Parameters.timeCostInIterations in 1..MAX_ARGON2_TIME_COST) {
            "Backup file declares an unsupported Argon2 time cost."
        }
        require(argon2Parameters.memoryCostInKibibyte in 1..MAX_ARGON2_MEMORY_KIB) {
            "Backup file declares an unsupported Argon2 memory cost."
        }
        require(argon2Parameters.parallelism in 1..MAX_ARGON2_PARALLELISM) {
            "Backup file declares an unsupported Argon2 parallelism."
        }
        require(argon2Parameters.hashLengthBytes == AES_KEY_LENGTH_BYTES) {
            "Backup file declares an unsupported Argon2 hash length."
        }

        return finishParsingContainer(
            encryptedBytes = encryptedBytes,
            buffer = buffer,
            argon2Parameters = argon2Parameters,
            compressionType = compressionType,
            uncompressedLengthBytes = uncompressedLengthBytes,
        )
    }

    private fun finishParsingContainer(
        encryptedBytes: ByteArray,
        buffer: ByteBuffer,
        argon2Parameters: BackupArgon2Parameters,
        compressionType: Int,
        uncompressedLengthBytes: Long?,
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
            argon2Parameters = argon2Parameters,
            compressionType = compressionType,
            uncompressedLengthBytes = uncompressedLengthBytes,
            salt = salt,
            nonce = nonce,
            ciphertext = encryptedBytes.copyOfRange(headerLength, encryptedBytes.size),
        )
    }

    private fun gzip(plaintext: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { gzipStream ->
            gzipStream.write(plaintext)
        }
        return outputStream.toByteArray()
    }

    private fun gunzip(
        compressedPayload: ByteArray,
        expectedUncompressedLengthBytes: Long,
    ): ByteArray {
        return try {
            GZIPInputStream(ByteArrayInputStream(compressedPayload)).use { gzipStream ->
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0L
                while (true) {
                    val bytesRead = gzipStream.read(buffer)
                    if (bytesRead == -1) break
                    totalBytes += bytesRead.toLong()
                    if (totalBytes > MAX_BACKUP_JSON_BYTES) {
                        throw IOException("Backup file exceeds the maximum supported restore size.")
                    }
                    if (totalBytes > expectedUncompressedLengthBytes) {
                        throw IOException("Backup file does not match its declared payload length.")
                    }
                    outputStream.write(buffer, 0, bytesRead)
                }
                if (totalBytes != expectedUncompressedLengthBytes) {
                    throw IOException("Backup file does not match its declared payload length.")
                }
                outputStream.toByteArray()
            }
        } catch (error: IOException) {
            throw IOException("Unable to decompress the selected backup file.", error)
        }
    }

    companion object {
        internal const val CURRENT_BACKUP_CONTAINER_VERSION = 3
        internal val DEFAULT_ARGON2_PARAMETERS = BackupArgon2Parameters(
            timeCostInIterations = 3,
            memoryCostInKibibyte = 65_536,
            parallelism = 1,
            hashLengthBytes = AES_KEY_LENGTH_BYTES,
        )
        internal val MAGIC_BYTES: ByteArray = "HRTBKP1".encodeToByteArray()

        private const val LEGACY_UNCOMPRESSED_BACKUP_CONTAINER_VERSION = 2
        private const val KDF_ARGON2_ID = 2
        private const val CIPHER_AES_256_GCM = 1
        private const val COMPRESSION_NONE = 0
        private const val COMPRESSION_GZIP = 1
        private val SUPPORTED_COMPRESSION_TYPES = setOf(COMPRESSION_NONE, COMPRESSION_GZIP)
        private const val FIXED_HEADER_LENGTH_V2 = 28
        private const val FIXED_HEADER_LENGTH_V3 = 37
        private const val MAX_BACKUP_JSON_BYTES = 128L * 1024L * 1024L
        private const val MAX_ARGON2_TIME_COST = 10
        private const val MAX_ARGON2_MEMORY_KIB = 256 * 1024
        private const val MAX_ARGON2_PARALLELISM = 4
        private const val SALT_LENGTH_BYTES = 16
        private const val NONCE_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val AES_KEY_LENGTH_BYTES = 32
        private const val BYTE_MASK = 0xFF
        private const val AES_KEY_ALGORITHM = "AES"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
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
)

private data class ParsedBackupContainer(
    val header: ByteArray,
    val argon2Parameters: BackupArgon2Parameters,
    val compressionType: Int,
    val uncompressedLengthBytes: Long?,
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
