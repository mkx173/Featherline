package com.mkx.hrttracker.data.backup

import java.nio.ByteBuffer
import java.security.MessageDigest

internal class TestBackupArgon2KeyDeriver : BackupArgon2KeyDeriver {
    override fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        parameters: BackupArgon2Parameters,
    ): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(password.concatToString().toByteArray(Charsets.UTF_8))
        digest.update(salt)
        digest.update(
            ByteBuffer.allocate(16)
                .putInt(parameters.timeCostInIterations)
                .putInt(parameters.memoryCostInKibibyte)
                .putInt(parameters.parallelism)
                .putInt(parameters.hashLengthBytes)
                .array()
        )
        return digest.digest().copyOf(parameters.hashLengthBytes)
    }
}

// Writes a v3 (password) container. Export now writes v4, so this survives
// only as the fixture builder for the v2/v3 restore tests.
internal fun BackupCrypto.encryptSnapshotJson(
    json: String,
    password: CharArray,
): ByteArray {
    val plaintextBytes = json.toByteArray(Charsets.UTF_8)
    return try {
        encrypt(plaintext = plaintextBytes, password = password)
    } finally {
        plaintextBytes.fill(0)
    }
}
