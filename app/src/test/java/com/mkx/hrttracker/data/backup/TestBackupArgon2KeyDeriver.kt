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
