package com.mkx.hrttracker.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class KeyringTest {
    private val keyringCrypto = KeyringCrypto(BackupCrypto(TestBackupArgon2KeyDeriver()))
    private val mk = ByteArray(32) { index -> index.toByte() }

    @Test
    fun new_keyring_has_one_recovery_slot_that_unwraps_to_the_master_key() {
        val keyring = keyringCrypto.newKeyring(mk, RECOVERY_KEY, now = 1_760_140_000_000L)

        assertEquals(1, keyring.version)
        assertEquals(1_760_140_000_000L, keyring.createdAt)
        assertEquals(keyringCrypto.mkCheck(mk), keyring.mkCheck)
        assertEquals(listOf("recovery"), keyring.slots.map { it.type })
        assertArrayEquals(mk, keyringCrypto.unwrapRecovery(keyring, RECOVERY_KEY))
    }

    @Test
    fun recovery_slot_is_a_v3_password_container() {
        val keyring = keyringCrypto.newKeyring(mk, RECOVERY_KEY, now = 0L)
        val wrapped = Base64.getDecoder().decode(keyring.slots.single().wrapped)

        assertEquals(
            BackupCrypto.CURRENT_BACKUP_CONTAINER_VERSION.toByte(),
            wrapped[BackupCrypto.MAGIC_BYTES.size],
        )
    }

    @Test
    fun unwrap_with_wrong_recovery_key_throws_wrong_recovery_key_exception() {
        val keyring = keyringCrypto.newKeyring(mk, RECOVERY_KEY, now = 0L)

        try {
            keyringCrypto.unwrapRecovery(keyring, "0000000000000000")
            fail("Expected a wrong recovery key to be rejected.")
        } catch (_: WrongRecoveryKeyException) {
            // Expected.
        }
    }

    @Test
    fun unwrap_of_corrupt_slot_is_not_a_wrong_key() {
        val keyring = keyringCrypto.newKeyring(mk, RECOVERY_KEY, now = 0L)
        val corrupt = keyring.copy(
            slots = keyring.slots.map { it.copy(wrapped = Base64.getEncoder().encodeToString(ByteArray(4))) },
        )

        try {
            keyringCrypto.unwrapRecovery(corrupt, RECOVERY_KEY)
            fail("Expected a corrupt recovery slot to be rejected.")
        } catch (error: WrongRecoveryKeyException) {
            fail("A corrupt slot must not read as a wrong key: $error")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun mk_check_is_stable_and_differs_per_master_key() {
        val otherMk = ByteArray(32) { index -> (index + 1).toByte() }

        assertEquals(keyringCrypto.mkCheck(mk), keyringCrypto.mkCheck(mk.copyOf()))
        assertNotEquals(keyringCrypto.mkCheck(mk), keyringCrypto.mkCheck(otherMk))
        assertEquals(16, Base64.getDecoder().decode(keyringCrypto.mkCheck(mk)).size)
    }

    @Test
    fun json_round_trips() {
        val keyring = keyringCrypto.newKeyring(mk, RECOVERY_KEY, now = 42L)

        assertEquals(keyring, keyringCrypto.decode(keyringCrypto.encode(keyring)))
    }

    @Test
    fun unknown_slot_types_and_fields_are_ignored() {
        val keyring = keyringCrypto.newKeyring(mk, RECOVERY_KEY, now = 42L)
        val recoverySlot = keyring.slots.single()
        val json = """
            {
              "version": 1,
              "createdAt": 42,
              "mkCheck": "${keyring.mkCheck}",
              "recoveryKeyWrapped": "AAAA",
              "slots": [
                {
                  "type": "passkey",
                  "id": "p1",
                  "createdAt": 43,
                  "rpId": "example.com",
                  "credentialId": "Y3JlZA",
                  "prfSalt": "AAAA",
                  "nonce": "AAAA",
                  "wrapped": "AAAA"
                },
                {
                  "type": "recovery",
                  "id": "${recoverySlot.id}",
                  "createdAt": 42,
                  "wrapped": "${recoverySlot.wrapped}"
                }
              ]
            }
        """.trimIndent()

        val decoded = keyringCrypto.decode(json)

        assertEquals(listOf("passkey", "recovery"), decoded.slots.map { it.type })
        assertArrayEquals(mk, keyringCrypto.unwrapRecovery(decoded, RECOVERY_KEY))
    }

    @Test
    fun keyring_without_recovery_slot_is_rejected_as_malformed() {
        val keyring = keyringCrypto.newKeyring(mk, RECOVERY_KEY, now = 0L)
        val noRecovery = keyring.copy(slots = keyring.slots.map { it.copy(type = "securityKey") })

        try {
            keyringCrypto.unwrapRecovery(noRecovery, RECOVERY_KEY)
            fail("Expected a keyring without a recovery slot to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun decode_rejects_oversize_malformed_and_unknown_version_keyrings() {
        val valid = keyringCrypto.encode(keyringCrypto.newKeyring(mk, RECOVERY_KEY, now = 0L))
        val oversize = valid.dropLast(1) + " ".repeat(BackupCrypto.MAX_KEYRING_JSON_BYTES) + "}"
        val multiByteOversize = "é".repeat(BackupCrypto.MAX_KEYRING_JSON_BYTES / 2 + 1)

        listOf(
            oversize,
            multiByteOversize,
            "not json",
            "null",
            """{"version":1}""",
            valid.replace("\"version\":1", "\"version\":2"),
        ).forEach { json ->
            try {
                keyringCrypto.decode(json)
                fail("Expected keyring to be rejected: ${json.take(40)}")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    private companion object {
        private const val RECOVERY_KEY = "K7QM3XRT9F2BHW4D"
    }
}
