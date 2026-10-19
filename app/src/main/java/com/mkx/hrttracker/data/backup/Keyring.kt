package com.mkx.hrttracker.data.backup

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class Keyring(
    val version: Int,
    val createdAt: Long,
    val mkCheck: String,
    // The recovery key under a key derived from MK, so a phone that unlocked
    // with a passkey can still show it (E1). Null in keyrings from before 1.6.
    val recoveryKeyWrapped: String? = null,
    val slots: List<KeyringSlot>,
)

// One flat shape for every slot type. Fields of types this build does not
// know (security key) are skipped by Moshi, and slots are picked by [type], so
// a keyring from a newer build still opens with the recovery key.
@JsonClass(generateAdapter = true)
data class KeyringSlot(
    val type: String,
    val id: String,
    val createdAt: Long,
    val wrapped: String,
    // Passkey slots only: base64url credential id, base64 PRF salt and GCM nonce.
    val rpId: String? = null,
    val credentialId: String? = null,
    val prfSalt: String? = null,
    val nonce: String? = null,
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
            recoveryKeyWrapped = wrapRecoveryKey(mk, recoveryKey),
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

    /** The recovery key from [Keyring.recoveryKeyWrapped]; null when the keyring has none. */
    fun unwrapRecoveryKey(keyring: Keyring, mk: ByteArray): String? {
        val sealed = Base64.getDecoder().decode(keyring.recoveryKeyWrapped ?: return null)
        require(sealed.size > GCM_NONCE_LENGTH_BYTES) { "recoveryKeyWrapped is too short." }
        val key = recoveryWrapKey(mk)
        val plaintext = try {
            gcm(
                Cipher.DECRYPT_MODE, key, sealed.copyOf(GCM_NONCE_LENGTH_BYTES), RECOVERY_WRAP_AAD,
                sealed.copyOfRange(GCM_NONCE_LENGTH_BYTES, sealed.size),
            )
        } finally {
            key.fill(0)
        }
        return try {
            String(plaintext, Charsets.UTF_8)
        } finally {
            plaintext.fill(0)
        }
    }

    /** A passkey slot wrapping [mk] under the 32-byte WebAuthn PRF output. */
    fun wrapPasskeySlot(
        mk: ByteArray,
        prfOutput: ByteArray,
        rpId: String,
        credentialId: String,
        prfSalt: String,
        now: Long,
    ): KeyringSlot {
        require(prfOutput.size == PRF_OUTPUT_LENGTH_BYTES) { "PRF output must be 32 bytes." }
        val id = UUID.randomUUID().toString()
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES).also(SecureRandom()::nextBytes)
        val wrapped = gcm(Cipher.ENCRYPT_MODE, prfOutput, nonce, id.toByteArray(Charsets.UTF_8), mk)
        val encoder = Base64.getEncoder()
        return KeyringSlot(
            type = SLOT_TYPE_PASSKEY,
            id = id,
            createdAt = now,
            wrapped = encoder.encodeToString(wrapped),
            rpId = rpId,
            credentialId = credentialId,
            prfSalt = prfSalt,
            nonce = encoder.encodeToString(nonce),
        )
    }

    /** MK from a passkey slot; a wrong PRF output fails the GCM tag. The caller zeroes it. */
    fun unwrapPasskeySlot(slot: KeyringSlot, prfOutput: ByteArray): ByteArray {
        require(slot.type == SLOT_TYPE_PASSKEY) { "Not a passkey slot." }
        require(prfOutput.size == PRF_OUTPUT_LENGTH_BYTES) { "PRF output must be 32 bytes." }
        val decoder = Base64.getDecoder()
        val mk = gcm(
            Cipher.DECRYPT_MODE, prfOutput, decoder.decode(requireNotNull(slot.nonce)),
            slot.id.toByteArray(Charsets.UTF_8), decoder.decode(slot.wrapped),
        )
        if (mk.size != MK_LENGTH_BYTES) {
            mk.fill(0)
            throw IllegalArgumentException("Passkey slot holds ${mk.size} bytes, expected $MK_LENGTH_BYTES.")
        }
        return mk
    }

    private fun wrapRecoveryKey(mk: ByteArray, recoveryKey: String): String {
        val key = recoveryWrapKey(mk)
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES).also(SecureRandom()::nextBytes)
        val plaintext = recoveryKey.toByteArray(Charsets.UTF_8)
        return try {
            Base64.getEncoder().encodeToString(nonce + gcm(Cipher.ENCRYPT_MODE, key, nonce, RECOVERY_WRAP_AAD, plaintext))
        } finally {
            key.fill(0)
            plaintext.fill(0)
        }
    }

    private fun recoveryWrapKey(mk: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(mk, HMAC_ALGORITHM))
        return mac.doFinal(RECOVERY_WRAP_LABEL.toByteArray(Charsets.UTF_8))
    }

    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(input)
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

    companion object {
        private const val KEYRING_VERSION = 1
        private const val SLOT_TYPE_RECOVERY = "recovery"
        const val SLOT_TYPE_PASSKEY = "passkey"
        private const val PRF_OUTPUT_LENGTH_BYTES = 32
        private const val GCM_NONCE_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val RECOVERY_WRAP_LABEL = "featherline-recovery-wrap-v1"
        private val RECOVERY_WRAP_AAD = "recoveryKeyWrapped".toByteArray(Charsets.UTF_8)
        private const val MK_LENGTH_BYTES = 32
        private const val MK_CHECK_LENGTH_BYTES = 16
        private const val MK_CHECK_LABEL = "featherline-keyring-check-v1"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private val adapter: JsonAdapter<Keyring> = Moshi.Builder().build().adapter(Keyring::class.java)
    }
}
