package com.mkx.hrttracker.data.backup

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecurityKeySlotsTest {
    private val keyringCrypto = KeyringCrypto(BackupCrypto(TestBackupArgon2KeyDeriver()))
    private val slots = SecurityKeySlots(mockk<Context>(), keyringCrypto)
    private val mk = ByteArray(32) { 9 }

    @Test
    fun add_asserts_with_a_fresh_salt_and_wraps_the_mk_under_its_output() {
        val key = FakeKey()

        val slot = slots.add(key, mk, pin = null)

        assertEquals(KeyringCrypto.SLOT_TYPE_SECURITY_KEY, slot.type)
        assertEquals(SecurityKeySlots.RP_ID, slot.rpId)
        val credentialId = Base64.getUrlDecoder().decode(slot.credentialId)
        assertArrayEquals(key.credentials.single(), credentialId)
        val salt = Base64.getDecoder().decode(slot.hmacSalt)
        assertEquals(32, salt.size)
        assertArrayEquals(salt, key.saltsSeen.single())
        assertArrayEquals(mk, keyringCrypto.unwrapSecurityKeySlot(slot, key.output(credentialId, salt)))
        // A second Add uses a new salt.
        assertTrue(!salt.contentEquals(Base64.getDecoder().decode(slots.add(key, mk, null).hmacSalt)))
    }

    @Test
    fun add_without_hmac_secret_makes_no_credential() {
        val key = FakeKey(supportsHmacSecret = false)
        assertFails<SecurityKeyUnsupportedException> { slots.add(key, mk, null) }
        assertTrue(key.credentials.isEmpty())

        val noOutput = FakeKey(returnsOutput = false)
        assertFails<SecurityKeyUnsupportedException> { slots.add(noOutput, mk, null) }
    }

    @Test
    fun add_passes_the_pin_only_when_given_and_surfaces_pin_required() {
        val key = FakeKey(pin = "1234")
        assertFails<SecurityKeyPinRequiredException> { slots.add(key, mk, null) }
        assertTrue(key.credentials.isEmpty())

        val slot = slots.add(key, mk, "1234".toCharArray())

        assertArrayEquals(mk, slots.unlock(key, listOf(slot)).second)
    }

    @Test
    fun unlock_matches_the_returned_credential_to_its_slot() {
        val key = FakeKey()
        val other = slots.add(FakeKey(), mk, null)
        val mine = slots.add(key, mk, null)

        val (slot, unlocked) = slots.unlock(key, listOf(other, mine))

        assertSame(mine, slot)
        assertArrayEquals(mk, unlocked)
        // Add's assertion, then one per salt group: the other slot's, then this one's.
        assertEquals(3, key.saltsSeen.size)
        assertFails<SecurityKeyNotLinkedException> { slots.unlock(key, listOf(other)) }
    }

    @Test
    fun rewrap_keeps_credential_and_salt_and_wraps_the_new_mk() {
        val key = FakeKey()
        val old = slots.add(key, mk, null)
        val newMk = ByteArray(32) { 4 }

        val relinked = slots.rewrap(key, old, newMk)

        assertEquals(old.credentialId, relinked.credentialId)
        assertEquals(old.hmacSalt, relinked.hmacSalt)
        assertTrue(old.id != relinked.id && old.nonce != relinked.nonce)
        assertArrayEquals(newMk, slots.unlock(key, listOf(relinked)).second)
        assertFails<SecurityKeyNotLinkedException> { slots.rewrap(FakeKey(), old, newMk) }
    }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}.")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }

    // A key with a per-key secret: hmac-secret = HMAC(secret, credentialId || salt).
    private class FakeKey(
        override val supportsHmacSecret: Boolean = true,
        private val returnsOutput: Boolean = true,
        private val pin: String? = null,
    ) : SecurityKeyAuthenticator {
        private val secret = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        val credentials = mutableListOf<ByteArray>()
        val saltsSeen = mutableListOf<ByteArray>()

        override fun makeCredential(userId: ByteArray, pin: CharArray?): ByteArray {
            if (this.pin != null && pin?.let(::String) != this.pin) throw SecurityKeyPinRequiredException()
            return ByteArray(16).also(java.security.SecureRandom()::nextBytes).also(credentials::add)
        }

        override fun getAssertion(credentialIds: List<ByteArray>, salt: ByteArray): Pair<ByteArray, ByteArray?>? {
            saltsSeen += salt.copyOf()
            val id = credentialIds.firstOrNull { wanted -> credentials.any { it.contentEquals(wanted) } } ?: return null
            return id to output(id, salt).takeIf { returnsOutput }
        }

        fun output(credentialId: ByteArray, salt: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret, "HmacSHA256"))
            return mac.doFinal(credentialId + salt)
        }
    }
}
