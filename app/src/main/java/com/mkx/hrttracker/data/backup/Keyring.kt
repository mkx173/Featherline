package com.mkx.hrttracker.data.backup

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.IOException
import java.util.Base64
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class Keyring(
    val version: Int,
    val createdAt: Long,
    val mkCheck: String,
    val slots: List<KeyringSlot>,
)

// One flat shape for every slot type. Fields of types this build does not
// know (security key, passkey) are skipped by Moshi, and slots are picked by
// [type], so a keyring from a newer build still opens with the recovery key.
@JsonClass(generateAdapter = true)
data class KeyringSlot(
    val type: String,
    val id: String,
    val createdAt: Long,
    val wrapped: String,
)

// The recovery slot's GCM tag failed: the key was typed wrong, not a corrupt file.
class WrongRecoveryKeyException(cause: Throwable) :
    IOException("The recovery key does not unlock this keyring.", cause)

@Singleton
class KeyringCrypto @Inject constructor(
    private val backupCrypto: BackupCrypto,
) {
    // Lets a device holding MK recognise its own keyring without unwrapping a
    // slot. Truncated HMAC under a random 256-bit key, so it reveals nothing.
    fun mkCheck(mk: ByteArray): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(mk, HMAC_ALGORITHM))
        val digest = mac.doFinal(MK_CHECK_LABEL.toByteArray(Charsets.UTF_8))
        return try {
            Base64.getEncoder().encodeToString(digest.copyOf(MK_CHECK_LENGTH_BYTES))
        } finally {
            digest.fill(0)
        }
    }

    // [recoveryKey] is the canonical form from [RecoveryKey.parse] or [RecoveryKey.generate].
    fun newKeyring(
        mk: ByteArray,
        recoveryKey: String,
        now: Long,
    ): Keyring {
        val recoveryKeyChars = recoveryKey.toCharArray()
        val wrapped = try {
            backupCrypto.encrypt(plaintext = mk, password = recoveryKeyChars)
        } finally {
            recoveryKeyChars.fill(' ')
        }
        return Keyring(
            version = KEYRING_VERSION,
            createdAt = now,
            mkCheck = mkCheck(mk),
            slots = listOf(
                KeyringSlot(
                    type = SLOT_TYPE_RECOVERY,
                    id = UUID.randomUUID().toString(),
                    createdAt = now,
                    wrapped = Base64.getEncoder().encodeToString(wrapped),
                )
            ),
        )
    }

    fun unwrapRecovery(
        keyring: Keyring,
        recoveryKey: String,
    ): ByteArray {
        val slot = requireNotNull(keyring.slots.firstOrNull { it.type == SLOT_TYPE_RECOVERY }) {
            "Keyring has no recovery slot."
        }
        val wrapped = Base64.getDecoder().decode(slot.wrapped)
        val recoveryKeyChars = recoveryKey.toCharArray()
        val mk = try {
            backupCrypto.decrypt(encryptedBytes = wrapped, password = recoveryKeyChars)
        } catch (error: IOException) {
            if (error.cause is AEADBadTagException) throw WrongRecoveryKeyException(error)
            throw error
        } finally {
            recoveryKeyChars.fill(' ')
            wrapped.fill(0)
        }
        if (mk.size != MK_LENGTH_BYTES) {
            mk.fill(0)
            throw IllegalArgumentException("Keyring recovery slot holds ${mk.size} bytes, expected $MK_LENGTH_BYTES.")
        }
        return mk
    }

    fun encode(keyring: Keyring): String = adapter.toJson(keyring)

    // Input is untrusted (read from a backup header before any tag check), so it
    // is size-capped first and every malformed shape becomes IllegalArgumentException.
    fun decode(json: String): Keyring {
        require(
            json.length <= BackupCrypto.MAX_KEYRING_JSON_BYTES &&
                    json.toByteArray(Charsets.UTF_8).size <= BackupCrypto.MAX_KEYRING_JSON_BYTES
        ) {
            "Keyring exceeds the maximum supported size."
        }
        val keyring = try {
            adapter.fromJson(json)
        } catch (error: Exception) {
            throw IllegalArgumentException("Keyring is malformed.", error)
        }
        requireNotNull(keyring) { "Keyring is empty." }
        require(keyring.version == KEYRING_VERSION) {
            "Unsupported keyring version: ${keyring.version}."
        }
        return keyring
    }

    private companion object {
        private const val KEYRING_VERSION = 1
        private const val SLOT_TYPE_RECOVERY = "recovery"
        private const val MK_LENGTH_BYTES = 32
        private const val MK_CHECK_LENGTH_BYTES = 16
        private const val MK_CHECK_LABEL = "featherline-keyring-check-v1"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private val adapter: JsonAdapter<Keyring> = Moshi.Builder().build().adapter(Keyring::class.java)
    }
}
