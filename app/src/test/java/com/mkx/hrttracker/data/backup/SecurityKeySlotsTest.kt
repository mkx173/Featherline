package com.mkx.hrttracker.data.backup

import android.content.Context
import com.yubico.yubikit.fido.webauthn.ResidentKeyRequirement
import com.yubico.yubikit.fido.webauthn.UserVerificationRequirement
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

    private val pin = "1234".toCharArray()

    @Test
    fun add_with_hmac_secret_mc_takes_the_output_from_creation_alone() {
        val key = FakeKey()

        val slot = slots.add(key, mk, pin)

        assertEquals(listOf("mc"), key.calls)
        val salt = Base64.getDecoder().decode(slot.hmacSalt)
        assertArrayEquals(salt, key.saltsSeen.single())
        val credentialId = Base64.getUrlDecoder().decode(slot.credentialId)
        assertArrayEquals(mk, keyringCrypto.unwrapSecurityKeySlot(slot, key.output(credentialId, salt)))
        // The assertion unlock makes derives the same output.
        assertArrayEquals(mk, slots.unlock(key, listOf(slot), pin).second)
    }

    @Test
    fun add_falls_back_to_an_assertion_without_hmac_secret_mc_or_its_output() {
        for (key in listOf(FakeKey(supportsHmacSecretMc = false), FakeKey(returnsMcOutput = false))) {
            val slot = slots.add(key, mk, pin)
            assertEquals(listOf("mc", "ga"), key.calls)
            val salt = Base64.getDecoder().decode(slot.hmacSalt)
            assertArrayEquals(salt, key.saltsSeen.last())
            assertArrayEquals(mk, slots.unlock(key, listOf(slot), pin).second)
        }
    }

    @Test
    fun add_asserts_with_a_fresh_salt_and_wraps_the_mk_under_its_output() {
        val key = FakeKey(supportsHmacSecretMc = false)

        val slot = slots.add(key, mk, pin)

        assertEquals(KeyringCrypto.SLOT_TYPE_SECURITY_KEY, slot.type)
        assertEquals(SecurityKeySlots.RP_ID, slot.rpId)
        val credentialId = Base64.getUrlDecoder().decode(slot.credentialId)
        assertArrayEquals(key.credentials.single(), credentialId)
        val salt = Base64.getDecoder().decode(slot.hmacSalt)
        assertEquals(32, salt.size)
        assertArrayEquals(salt, key.saltsSeen.single())
        assertArrayEquals(mk, keyringCrypto.unwrapSecurityKeySlot(slot, key.output(credentialId, salt)))
        // A second Add uses a new salt.
        assertTrue(!salt.contentEquals(Base64.getDecoder().decode(slots.add(key, mk, pin).hmacSalt)))
    }

    @Test
    fun add_unlock_and_rewrap_pass_the_pin_on_every_call() {
        val key = FakeKey()
        val slot = slots.add(key, mk, pin)
        slots.unlock(key, listOf(slot), pin)
        slots.rewrap(key, slot, ByteArray(32) { 4 }, pin)

        // makeCredential (hmac-secret-mc), then unlock's and rewrap's assertions.
        assertEquals(listOf("mc", "ga", "ga"), key.calls)
        assertEquals(List(3) { "1234" }, key.pinsSeen)
    }

    @Test
    fun options_ask_for_a_non_discoverable_user_verified_credential_and_uv_assertions() {
        val creation = SecurityKeySlots.creationOptions(ByteArray(16), null)
        assertEquals(ResidentKeyRequirement.DISCOURAGED, creation.authenticatorSelection?.residentKey)
        assertEquals(UserVerificationRequirement.REQUIRED, creation.authenticatorSelection?.userVerification)
        assertEquals(SecurityKeySlots.RP_ID, creation.rp.id)
        assertEquals(emptyMap<String, Any>(), creation.extensions?.get("prf"))
        val salt = ByteArray(32) { 3 }
        assertEquals(
            mapOf("eval" to mapOf("first" to Base64.getUrlEncoder().withoutPadding().encodeToString(salt))),
            SecurityKeySlots.creationOptions(ByteArray(16), salt).extensions?.get("prf"),
        )

        val request = SecurityKeySlots.requestOptions(listOf(ByteArray(16)), ByteArray(32))
        assertEquals(UserVerificationRequirement.REQUIRED, request.userVerification)
        assertEquals(SecurityKeySlots.RP_ID, request.rpId)
        assertEquals(1, request.allowCredentials.size)
    }

    @Test
    fun add_without_hmac_secret_or_pin_makes_no_credential() {
        val key = FakeKey(supportsHmacSecret = false)
        assertFails<SecurityKeyUnsupportedException> { slots.add(key, mk, pin) }
        assertTrue(key.calls.isEmpty())

        val noPin = FakeKey(hasPin = false)
        assertFails<SecurityKeyPinNotSetException> { slots.add(noPin, mk, pin) }
        assertTrue(noPin.calls.isEmpty())

        val noOutput = FakeKey(returnsOutput = false, returnsMcOutput = false)
        assertFails<SecurityKeyUnsupportedException> { slots.add(noOutput, mk, pin) }
    }

    @Test
    fun a_wrong_pin_surfaces_its_retries() {
        val key = FakeKey()
        val error = assertFails<SecurityKeyWrongPinException> { slots.add(key, mk, "0000".toCharArray()) }
        assertEquals(7, error.retries)
        assertTrue(key.credentials.isEmpty())

        val slot = slots.add(key, mk, pin)
        assertEquals(7, assertFails<SecurityKeyWrongPinException> { slots.unlock(key, listOf(slot), "0000".toCharArray()) }.retries)
    }

    @Test
    fun unlock_matches_the_returned_credential_to_its_slot() {
        val key = FakeKey()
        val other = slots.add(FakeKey(), mk, pin)
        val mine = slots.add(key, mk, pin)

        val (slot, unlocked) = slots.unlock(key, listOf(other, mine), pin)

        assertSame(mine, slot)
        assertArrayEquals(mk, unlocked)
        // Add's creation salt, then one assertion per salt group: the other slot's, then this one's.
        assertEquals(3, key.saltsSeen.size)
        assertFails<SecurityKeyNotLinkedException> { slots.unlock(key, listOf(other), pin) }
    }

    @Test
    fun rewrap_keeps_credential_and_salt_and_wraps_the_new_mk() {
        val key = FakeKey()
        val old = slots.add(key, mk, pin)
        val newMk = ByteArray(32) { 4 }

        val relinked = slots.rewrap(key, old, newMk, pin)

        assertEquals(old.credentialId, relinked.credentialId)
        assertEquals(old.hmacSalt, relinked.hmacSalt)
        assertTrue(old.id != relinked.id && old.nonce != relinked.nonce)
        assertArrayEquals(newMk, slots.unlock(key, listOf(relinked), pin).second)
        assertFails<SecurityKeyNotLinkedException> { slots.rewrap(FakeKey(), old, newMk, pin) }
    }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error !is T) throw error
            return error
        }
        fail("Expected ${T::class.simpleName}.")
        throw AssertionError()
    }

    // A key with a PIN "1234" and a per-key secret: hmac-secret = HMAC(secret, credentialId || salt).
    private class FakeKey(
        override val supportsHmacSecret: Boolean = true,
        override val hasPin: Boolean = true,
        override val supportsHmacSecretMc: Boolean = true,
        private val returnsOutput: Boolean = true,
        private val returnsMcOutput: Boolean = true,
    ) : SecurityKeyAuthenticator {
        private val secret = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        val credentials = mutableListOf<ByteArray>()
        val saltsSeen = mutableListOf<ByteArray>()
        val calls = mutableListOf<String>()
        val pinsSeen = mutableListOf<String>()

        private fun checkPin(call: String, pin: CharArray) {
            calls += call
            pinsSeen += String(pin)
            if (String(pin) != "1234") throw SecurityKeyWrongPinException(7, Exception())
        }

        override fun makeCredential(userId: ByteArray, pin: CharArray, hmacSalt: ByteArray?): Pair<ByteArray, ByteArray?> {
            checkPin("mc", pin)
            assertEquals(supportsHmacSecretMc, hmacSalt != null)
            val id = ByteArray(16).also(java.security.SecureRandom()::nextBytes).also(credentials::add)
            hmacSalt?.let { saltsSeen += it.copyOf() }
            return id to hmacSalt?.takeIf { returnsMcOutput }?.let { output(id, it) }
        }

        override fun getAssertion(credentialIds: List<ByteArray>, salt: ByteArray, pin: CharArray): Pair<ByteArray, ByteArray?>? {
            checkPin("ga", pin)
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

class NfcCtapApdusTest {
    private val sent = mutableListOf<Pair<String, Boolean>>()
    private var reply = "9000"

    private fun apdus(maxExtended: Int) = NfcCtapApdus(maxExtended) { apdu, extended ->
        sent += hex(apdu) to extended
        unhex(reply)
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(value: String) = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val first = "90108000" + "ff" + "aa".repeat(255)
    private val last = "80108000" + "05" + "bb".repeat(5)

    @Test
    fun a_chained_command_goes_as_one_extended_apdu() {
        val apdus = apdus(maxExtended = 1000)
        reply = "01029000"

        assertEquals("9000", hex(apdus.sendAndReceive(unhex(first))))
        assertTrue(sent.isEmpty())
        assertEquals("01029000", hex(apdus.sendAndReceive(unhex(last))))

        val expected = "80108000" + "000104" + "aa".repeat(255) + "bb".repeat(5) + "0000"
        assertEquals(listOf(expected to true), sent)
    }

    @Test
    fun without_extended_length_or_room_it_chains() {
        for (max in listOf(0, 260)) {
            sent.clear()
            val apdus = apdus(max)
            apdus.sendAndReceive(unhex(first))
            apdus.sendAndReceive(unhex(last))
            assertEquals(listOf(first to false, last to false), sent)
        }
    }

    @Test
    fun a_short_command_and_the_poll_go_as_before() {
        val apdus = apdus(maxExtended = 1000)
        apdus.sendAndReceive(unhex(last))
        assertEquals("9000", hex(apdus.sendAndReceive(unhex("90110000ff" + "aa".repeat(255)))))
        apdus.sendAndReceive(unhex("8011000005" + "bb".repeat(5)))
        assertEquals(listOf(last to false, "80110000" to false), sent)
    }
}
