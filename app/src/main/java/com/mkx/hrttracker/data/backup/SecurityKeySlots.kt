package com.mkx.hrttracker.data.backup

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.activity.ComponentActivity
import androidx.lifecycle.withResumed
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.usb.DeviceFilter
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.core.Transport
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.application.ApplicationNotAvailableException
import com.yubico.yubikit.core.application.CommandException
import com.yubico.yubikit.core.application.CommandState
import com.yubico.yubikit.core.fido.FidoConnection
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.fido.client.AuthInvalidClientError
import com.yubico.yubikit.fido.client.ClientError
import com.yubico.yubikit.fido.client.Ctap2Client
import com.yubico.yubikit.fido.client.PinRequiredClientError
import com.yubico.yubikit.fido.client.clientdata.ClientDataProvider
import com.yubico.yubikit.fido.client.extensions.HmacSecretExtension
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.webauthn.AuthenticatorSelectionCriteria
import com.yubico.yubikit.fido.webauthn.Extensions
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialCreationOptions
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialDescriptor
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialParameters
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialRequestOptions
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialRpEntity
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialUserEntity
import com.yubico.yubikit.fido.webauthn.ResidentKeyRequirement
import com.yubico.yubikit.fido.webauthn.SerializationType
import com.yubico.yubikit.fido.webauthn.UserVerificationRequirement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A security-key failure the UI names. A plain [IOException] from the
 * transport means lost contact, so [SecurityKeySlots] waits for the key again.
 */
open class SecurityKeyException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The phone has neither NFC nor USB host. */
class SecurityKeyUnavailableException : SecurityKeyException("This phone can't talk to security keys.")

/** The key has no CTAP2 hmac-secret (or asks for user verification on every touch). */
class SecurityKeyUnsupportedException(cause: Throwable? = null) :
    SecurityKeyException("The security key can't unlock backups.", cause)

/** The key has a PIN and making a credential needs it. */
class SecurityKeyPinRequiredException : SecurityKeyException("The security key needs its PIN.")

class SecurityKeyWrongPinException(val retries: Int, cause: Throwable) :
    SecurityKeyException("Wrong security key PIN.", cause)

/** The key holds none of the slots' credentials. */
class SecurityKeyNotLinkedException : SecurityKeyException("This security key isn't linked.")

/** What E4's status line shows while a key operation runs; null when none does. */
enum class SecurityKeyStatus { WAITING, NFC_OFF, TOUCH, KEEP_HOLDING, DONE }

/**
 * The one CTAP2 key this app needs from an authenticator: the seam that keeps
 * [SecurityKeySlots]' logic testable without hardware. Blocking calls.
 */
internal interface SecurityKeyAuthenticator {
    val supportsHmacSecret: Boolean

    /**
     * A non-discoverable credential for [SecurityKeySlots.RP_ID]; its id.
     * Throws [SecurityKeyPinRequiredException] when the key needs a PIN and [pin] is null.
     */
    fun makeCredential(userId: ByteArray, pin: CharArray?): ByteArray

    /**
     * A user-presence-only assertion for one of [credentialIds] with the
     * hmac-secret extension on [salt]: the credential id and its 32-byte
     * output (null when the key returned none), or null when none of
     * [credentialIds] is on this key.
     */
    fun getAssertion(credentialIds: List<ByteArray>, salt: ByteArray): Pair<ByteArray, ByteArray?>?
}

/**
 * Security-key slots (CTAP2 hmac-secret) through yubikit, over NFC or USB.
 * The only class that talks to yubikit; each call takes the Activity NFC
 * reader mode needs and never keeps it. Unlock is a touch, never a PIN.
 */
@Singleton
class SecurityKeySlots @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyringCrypto: KeyringCrypto,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val statusState = MutableStateFlow<SecurityKeyStatus?>(null)

    /** E4's status line. */
    val status: StateFlow<SecurityKeyStatus?> = statusState.asStateFlow()

    fun isAvailable(): Boolean = NfcAdapter.getDefaultAdapter(context) != null ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)

    /**
     * Makes a credential on the key, asserts it once with a fresh salt, and
     * wraps [mk]. [pin] only after a [SecurityKeyPinRequiredException].
     */
    suspend fun add(activity: Activity, mk: ByteArray, pin: CharArray?): KeyringSlot =
        withKey(activity) { add(it, mk, pin) }

    /** MK from whichever of [slots] the key holds. The caller zeroes it. */
    suspend fun unlock(activity: Activity, slots: List<KeyringSlot>): Pair<KeyringSlot, ByteArray> =
        withKey(activity) { unlock(it, slots) }

    /** E2 re-link: the same credential and salt, wrapping [newMk] in a new slot. */
    suspend fun rewrap(activity: Activity, slot: KeyringSlot, newMk: ByteArray): KeyringSlot =
        withKey(activity) { rewrap(it, slot, newMk) }

    internal fun add(key: SecurityKeyAuthenticator, mk: ByteArray, pin: CharArray?): KeyringSlot {
        // Before the credential, so a key without it is left untouched.
        if (!key.supportsHmacSecret) throw SecurityKeyUnsupportedException()
        val random = SecureRandom()
        val credentialId = key.makeCredential(ByteArray(USER_ID_LENGTH_BYTES).also(random::nextBytes), pin)
        diagnosticsLogger.info(TAG, "security_key_created")
        // Always derive from this UV-off assertion, never from hmac-secret-mc
        // at creation: CTAP 2.1 keys derive a different secret with UV, and
        // creation may have used the PIN.
        val hmacSalt = ByteArray(HMAC_SALT_LENGTH_BYTES).also(random::nextBytes)
        val (assertedId, output) = key.getAssertion(listOf(credentialId), hmacSalt)
            ?: throw SecurityKeyException("The security key doesn't hold the credential just made.")
        return useOutput(output) { hmacOutput ->
            if (!MessageDigest.isEqual(assertedId, credentialId)) {
                throw SecurityKeyException("The security key asserted another credential.")
            }
            keyringCrypto.wrapSecurityKeySlot(
                mk = mk,
                hmacOutput = hmacOutput,
                rpId = RP_ID,
                credentialId = base64Url(credentialId),
                hmacSalt = Base64.getEncoder().encodeToString(hmacSalt),
                now = System.currentTimeMillis(),
            )
        }
    }

    internal fun unlock(key: SecurityKeyAuthenticator, slots: List<KeyringSlot>): Pair<KeyringSlot, ByteArray> {
        val keySlots = slots.filter { it.type == KeyringCrypto.SLOT_TYPE_SECURITY_KEY }
        require(keySlots.isNotEmpty()) { "No security-key slot." }
        // One assertion per salt; each slot this app writes has its own.
        for ((salt, group) in keySlots.groupBy { requireNotNull(it.hmacSalt) }) {
            val ids = group.map { decodeBase64Url(requireNotNull(it.credentialId)) }
            val (assertedId, output) = key.getAssertion(ids, Base64.getDecoder().decode(salt)) ?: continue
            val slot = group.firstOrNull { MessageDigest.isEqual(decodeBase64Url(requireNotNull(it.credentialId)), assertedId) }
            if (slot == null) {
                output?.fill(0)
                throw SecurityKeyException("The security key asserted a credential not in this keyring.")
            }
            return useOutput(output) { hmacOutput -> slot to unwrap(slot, hmacOutput) }
        }
        throw SecurityKeyNotLinkedException()
    }

    internal fun rewrap(key: SecurityKeyAuthenticator, slot: KeyringSlot, newMk: ByteArray): KeyringSlot {
        val credentialId = requireNotNull(slot.credentialId)
        val hmacSalt = requireNotNull(slot.hmacSalt)
        val (_, output) = key.getAssertion(listOf(decodeBase64Url(credentialId)), Base64.getDecoder().decode(hmacSalt))
            ?: throw SecurityKeyNotLinkedException()
        return useOutput(output) { hmacOutput ->
            // Proves it's the same key before the new MK goes under it.
            unwrap(slot, hmacOutput).fill(0)
            keyringCrypto.wrapSecurityKeySlot(
                mk = newMk,
                hmacOutput = hmacOutput,
                rpId = slot.rpId ?: RP_ID,
                credentialId = credentialId,
                hmacSalt = hmacSalt,
                now = System.currentTimeMillis(),
            )
        }
    }

    private fun <T> useOutput(output: ByteArray?, block: (ByteArray) -> T): T {
        if (output == null || output.size != HMAC_OUTPUT_LENGTH_BYTES) {
            output?.fill(0)
            diagnosticsLogger.warning(TAG, "security_key_hmac_missing size=${output?.size}")
            throw SecurityKeyUnsupportedException()
        }
        return try {
            block(output)
        } finally {
            output.fill(0)
        }
    }

    private fun unwrap(slot: KeyringSlot, hmacOutput: ByteArray): ByteArray = try {
        keyringCrypto.unwrapSecurityKeySlot(slot, hmacOutput)
    } catch (error: Exception) {
        throw SecurityKeyException("The security key does not unlock its slot.", error)
    }

    /**
     * Discovers a key over NFC (reader mode) and USB until [block] finishes on
     * one; a lost NFC contact or unplugged key waits for it again. Discovery
     * stops when this returns or is cancelled.
     */
    private suspend fun <T> withKey(activity: Activity, block: (SecurityKeyAuthenticator) -> T): T {
        if (!isAvailable()) throw SecurityKeyUnavailableException()
        val devices = Channel<YubiKeyDevice>(Channel.CONFLATED)
        val manager = YubiKitManager(context)
        statusState.value = SecurityKeyStatus.WAITING
        try {
            // Reader mode needs a resumed activity; the device-credential
            // prompt before Add may still be closing.
            val start = {
                // ponytail: match-all, so non-Yubico keys work; any other USB
                // device plugged in meanwhile gets a permission dialog too.
                manager.startUsbDiscovery(UsbConfiguration().setDeviceFilter(DeviceFilter())) { devices.trySend(it) }
                try {
                    manager.startNfcDiscovery(NfcConfiguration(), activity) { devices.trySend(it) }
                } catch (error: NfcNotAvailable) {
                    diagnosticsLogger.info(TAG, "security_key_nfc_unavailable disabled=${error.isDisabled}")
                    if (error.isDisabled) statusState.value = SecurityKeyStatus.NFC_OFF
                }
            }
            (activity as? ComponentActivity)?.lifecycle?.withResumed(start) ?: withContext(Dispatchers.Main) { start() }
            while (true) {
                val device = devices.receive()
                val nfc = device.transport == Transport.NFC
                diagnosticsLogger.info(TAG, "security_key_found transport=${device.transport}")
                try {
                    val result = coroutineScope {
                        // Cancel (the sheet's button) aborts a USB touch wait
                        // on the key instead of blocking until it times out.
                        val commandState = CommandState()
                        val canceller = launch {
                            try {
                                awaitCancellation()
                            } finally {
                                commandState.cancel()
                            }
                        }
                        try {
                            withContext(Dispatchers.IO) {
                                statusState.value = if (nfc) SecurityKeyStatus.KEEP_HOLDING else SecurityKeyStatus.TOUCH
                                session(device, commandState) { block(it) }
                            }
                        } finally {
                            canceller.cancel()
                        }
                    } ?: continue
                    statusState.value = SecurityKeyStatus.DONE
                    delay(DONE_DISPLAY_MILLIS)
                    return result
                } catch (error: SecurityKeyException) {
                    throw error
                } catch (error: IOException) {
                    // Contact lost mid-operation: wait for the key again.
                    diagnosticsLogger.warning(TAG, "security_key_lost transport=${device.transport}", error)
                    statusState.value = SecurityKeyStatus.WAITING
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                manager.stopUsbDiscovery()
                manager.stopNfcDiscovery(activity)
            }
            statusState.value = null
        }
    }

    // [block]'s result, or null for a device that isn't a FIDO2 key (another
    // USB device, a payment card), so discovery keeps waiting.
    private fun <T> session(
        device: YubiKeyDevice,
        commandState: CommandState,
        block: (SecurityKeyAuthenticator) -> T,
    ): T? {
        val connection = when {
            device.supportsConnection(FidoConnection::class.java) -> device.openConnection(FidoConnection::class.java)
            device.supportsConnection(SmartCardConnection::class.java) -> device.openConnection(SmartCardConnection::class.java)
            else -> return null
        }
        connection.use {
            val ctap = try {
                when (connection) {
                    is FidoConnection -> Ctap2Session(connection)
                    else -> Ctap2Session(connection as SmartCardConnection)
                }
            } catch (_: ApplicationNotAvailableException) {
                return null
            } catch (error: CommandException) {
                // U2F-only keys answer no CTAP2 getInfo.
                throw SecurityKeyUnsupportedException(error)
            }
            return try {
                block(YubikitAuthenticator(Ctap2Client(ctap, listOf(HmacSecretExtension())), commandState))
            } catch (error: CommandException) {
                throw SecurityKeyFailedException(error)
            }
        }
    }

    private class SecurityKeyFailedException(cause: Throwable) :
        SecurityKeyException("Security key request failed.", cause)

    // hmac-secret through yubikit's WebAuthn PRF processing: the key gets
    // SHA-256("WebAuthn PRF" || 0x00 || hmacSalt) as its salt, so one
    // hmacSalt gives the same output on every phone (and in any PRF client).
    private class YubikitAuthenticator(
        private val client: Ctap2Client,
        private val commandState: CommandState,
    ) : SecurityKeyAuthenticator {
        override val supportsHmacSecret: Boolean =
            client.session.cachedInfo.extensions.contains(HMAC_SECRET)

        override fun makeCredential(userId: ByteArray, pin: CharArray?): ByteArray {
            val options = PublicKeyCredentialCreationOptions(
                PublicKeyCredentialRpEntity(RP_NAME, RP_ID),
                PublicKeyCredentialUserEntity(USER_NAME, userId, USER_NAME),
                randomBytes(CHALLENGE_LENGTH_BYTES),
                listOf(PublicKeyCredentialParameters("public-key", COSE_ES256)),
                null,
                null,
                AuthenticatorSelectionCriteria(
                    null,
                    ResidentKeyRequirement.DISCOURAGED,
                    UserVerificationRequirement.DISCOURAGED,
                ),
                null,
                // hmac-secret on, with no salt: nothing derives at creation.
                Extensions.fromMap(mapOf("prf" to emptyMap<String, Any>())),
            )
            return try {
                client.makeCredential(clientData(), options, RP_ID, pin, null, commandState).rawId
            } catch (_: PinRequiredClientError) {
                throw SecurityKeyPinRequiredException()
            } catch (error: AuthInvalidClientError) {
                if (error.authType != AuthInvalidClientError.AuthType.PIN) throw SecurityKeyFailedException(error)
                throw SecurityKeyWrongPinException(error.retries, error)
            } catch (error: ClientError) {
                throw SecurityKeyFailedException(error)
            }
        }

        override fun getAssertion(credentialIds: List<ByteArray>, salt: ByteArray): Pair<ByteArray, ByteArray?>? {
            val options = PublicKeyCredentialRequestOptions(
                randomBytes(CHALLENGE_LENGTH_BYTES),
                null,
                RP_ID,
                credentialIds.map { PublicKeyCredentialDescriptor("public-key", it) },
                UserVerificationRequirement.DISCOURAGED,
                Extensions.fromMap(
                    mapOf("prf" to mapOf("eval" to mapOf("first" to base64Url(salt))))
                ),
            )
            val credential = try {
                client.getAssertion(clientData(), options, RP_ID, null, commandState)
            } catch (_: PinRequiredClientError) {
                // alwaysUv keys: unlock is a touch only, so they can't hold a slot.
                throw SecurityKeyUnsupportedException()
            } catch (error: ClientError) {
                if (error.errorCode == ClientError.Code.DEVICE_INELIGIBLE) return null
                throw SecurityKeyFailedException(error)
            }
            val first = (credential.clientExtensionResults?.toMap(SerializationType.CBOR)?.get("prf") as? Map<*, *>)
                ?.let { it["results"] as? Map<*, *> }
                ?.let { it["first"] as? ByteArray }
            return credential.rawId to first
        }

        private fun clientData() = ClientDataProvider.fromHash(randomBytes(CHALLENGE_LENGTH_BYTES))
    }

    companion object {
        private const val TAG = "SecurityKeySlots"

        // A fixed string: with direct CTAP nothing checks it against a domain.
        const val RP_ID = "featherline-backup"
        private const val RP_NAME = "Featherline"
        private const val USER_NAME = "Featherline backup"
        private const val HMAC_SECRET = "hmac-secret"
        private const val COSE_ES256 = -7
        private const val USER_ID_LENGTH_BYTES = 16
        private const val CHALLENGE_LENGTH_BYTES = 32
        private const val HMAC_SALT_LENGTH_BYTES = 32
        private const val HMAC_OUTPUT_LENGTH_BYTES = 32
        private const val DONE_DISPLAY_MILLIS = 600L

        private fun randomBytes(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes)

        private fun base64Url(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun decodeBase64Url(value: String): ByteArray = Base64.getUrlDecoder().decode(value.trimEnd('='))
    }
}
