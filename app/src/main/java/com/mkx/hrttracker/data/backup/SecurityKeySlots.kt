package com.mkx.hrttracker.data.backup

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import androidx.activity.ComponentActivity
import androidx.lifecycle.withResumed
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.DeviceFilter
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.core.Transport
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.application.ApplicationNotAvailableException
import com.yubico.yubikit.core.application.CommandException
import com.yubico.yubikit.core.application.CommandState
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.core.fido.FidoConnection
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.fido.client.AuthInvalidClientError
import com.yubico.yubikit.fido.client.ClientError
import com.yubico.yubikit.fido.client.Ctap2Client
import com.yubico.yubikit.fido.client.clientdata.ClientDataProvider
import com.yubico.yubikit.fido.client.extensions.HmacSecretExtension
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.webauthn.AuthenticatorSelectionCriteria
import com.yubico.yubikit.fido.webauthn.Extensions
import com.yubico.yubikit.fido.webauthn.PublicKeyCredential
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
import kotlinx.coroutines.CancellationException
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

/** The key has no CTAP2 hmac-secret. */
class SecurityKeyUnsupportedException(cause: Throwable? = null) :
    SecurityKeyException("The security key can't unlock backups.", cause)

/** The key has no PIN; every slot needs one (user verification on every call). */
class SecurityKeyPinNotSetException : SecurityKeyException("The security key has no PIN.")

/** The key's PIN is blocked: for good (PIN_BLOCKED) or until a power cycle (PIN_AUTH_BLOCKED). */
class SecurityKeyPinBlockedException(cause: Throwable) : SecurityKeyException("The security key's PIN is blocked.", cause)

class SecurityKeyWrongPinException(val retries: Int, cause: Throwable) :
    SecurityKeyException("Wrong security key PIN.", cause)

/** The key holds none of the slots' credentials. */
class SecurityKeyNotLinkedException : SecurityKeyException("This security key isn't linked.")

/**
 * What E4's status line shows while a key operation runs; null when none does.
 * TOUCH_AGAIN: Add's second ceremony over USB, on a key without hmac-secret-mc.
 */
enum class SecurityKeyStatus { WAITING, NFC_OFF, TOUCH, TOUCH_AGAIN, KEEP_HOLDING, DONE }

/**
 * The one CTAP2 key this app needs from an authenticator: the seam that keeps
 * [SecurityKeySlots]' logic testable without hardware. Blocking calls.
 */
internal interface SecurityKeyAuthenticator {
    val supportsHmacSecret: Boolean

    /** getInfo lists hmac-secret-mc: the key returns the hmac-secret output at creation. */
    val supportsHmacSecretMc: Boolean

    /** getInfo's clientPin option: the key has a PIN set. */
    val hasPin: Boolean

    /**
     * [SecurityKeySlots.creationOptions] on the key with [pin]: the credential
     * id and, when [hmacSalt] is given, hmac-secret-mc's 32-byte output for it
     * (null when the key returned none). A wrong PIN throws
     * [SecurityKeyWrongPinException], a blocked one [SecurityKeyPinBlockedException].
     */
    fun makeCredential(userId: ByteArray, pin: CharArray, hmacSalt: ByteArray?): Pair<ByteArray, ByteArray?>

    /**
     * [SecurityKeySlots.requestOptions] on the key with [pin]: the credential
     * id and its 32-byte hmac-secret output (null when the key returned none),
     * or null when none of [credentialIds] is on this key. PIN errors as above.
     */
    fun getAssertion(credentialIds: List<ByteArray>, salt: ByteArray, pin: CharArray): Pair<ByteArray, ByteArray?>?
}

/**
 * Security-key slots (CTAP2 hmac-secret) through yubikit, over NFC or USB.
 * The only class that talks to yubikit; each call takes the Activity NFC
 * reader mode needs and never keeps it. Every call verifies the user with
 * the key's PIN: hmac-secret derives a different secret with UV than without
 * (two CredRandoms), so enrolment and every unlock must agree, and a YubiKey
 * answers PIN_AUTH_BLOCKED to an hmac-secret assertion without a PIN token.
 * The caller zeroes the PIN.
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
     * Makes a credential on the key, gets its hmac-secret output for a fresh
     * salt (at creation, or from one assertion), and wraps [mk].
     */
    suspend fun add(activity: Activity, mk: ByteArray, pin: CharArray): KeyringSlot =
        withKey(activity) { add(it, mk, pin) }

    /** MK from whichever of [slots] the key holds. The caller zeroes it. */
    suspend fun unlock(activity: Activity, slots: List<KeyringSlot>, pin: CharArray): Pair<KeyringSlot, ByteArray> =
        withKey(activity) { unlock(it, slots, pin) }

    /** E2 re-link: the same credential and salt, wrapping [newMk] in a new slot. */
    suspend fun rewrap(activity: Activity, slot: KeyringSlot, newMk: ByteArray, pin: CharArray): KeyringSlot =
        withKey(activity) { rewrap(it, slot, newMk, pin) }

    // Before any credential or assertion, so such a key is left untouched.
    private fun requireUsable(key: SecurityKeyAuthenticator) {
        if (!key.supportsHmacSecret) throw SecurityKeyUnsupportedException()
        if (!key.hasPin) throw SecurityKeyPinNotSetException()
    }

    internal fun add(key: SecurityKeyAuthenticator, mk: ByteArray, pin: CharArray): KeyringSlot {
        requireUsable(key)
        val random = SecureRandom()
        val hmacSalt = ByteArray(HMAC_SALT_LENGTH_BYTES).also(random::nextBytes)
        val (credentialId, created) = key.makeCredential(
            ByteArray(USER_ID_LENGTH_BYTES).also(random::nextBytes),
            pin,
            hmacSalt.takeIf { key.supportsHmacSecretMc },
        )
        diagnosticsLogger.info(TAG, "security_key_created mc=${key.supportsHmacSecretMc} output=${created != null}")
        // Creation and every assertion run with UV, so hmac-secret-mc's output
        // is CredRandomWithUV's, the same unlock gets from an assertion with
        // this salt. Without it, a second ceremony.
        val output = created ?: run {
            statusState.compareAndSet(SecurityKeyStatus.TOUCH, SecurityKeyStatus.TOUCH_AGAIN)
            val (assertedId, asserted) = key.getAssertion(listOf(credentialId), hmacSalt, pin)
                ?: throw SecurityKeyException("The security key doesn't hold the credential just made.")
            if (!MessageDigest.isEqual(assertedId, credentialId)) {
                asserted?.fill(0)
                throw SecurityKeyException("The security key asserted another credential.")
            }
            asserted
        }
        return useOutput(output) { hmacOutput ->
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

    internal fun unlock(key: SecurityKeyAuthenticator, slots: List<KeyringSlot>, pin: CharArray): Pair<KeyringSlot, ByteArray> {
        val keySlots = slots.filter { it.type == KeyringCrypto.SLOT_TYPE_SECURITY_KEY }
        require(keySlots.isNotEmpty()) { "No security-key slot." }
        requireUsable(key)
        // One assertion per salt; each slot this app writes has its own.
        for ((salt, group) in keySlots.groupBy { requireNotNull(it.hmacSalt) }) {
            val ids = group.map { decodeBase64Url(requireNotNull(it.credentialId)) }
            val (assertedId, output) = key.getAssertion(ids, Base64.getDecoder().decode(salt), pin) ?: continue
            val slot = group.firstOrNull { MessageDigest.isEqual(decodeBase64Url(requireNotNull(it.credentialId)), assertedId) }
            if (slot == null) {
                output?.fill(0)
                throw SecurityKeyException("The security key asserted a credential not in this keyring.")
            }
            return useOutput(output) { hmacOutput -> slot to unwrap(slot, hmacOutput) }
        }
        throw SecurityKeyNotLinkedException()
    }

    internal fun rewrap(key: SecurityKeyAuthenticator, slot: KeyringSlot, newMk: ByteArray, pin: CharArray): KeyringSlot {
        val credentialId = requireNotNull(slot.credentialId)
        val hmacSalt = requireNotNull(slot.hmacSalt)
        requireUsable(key)
        val (_, output) = key.getAssertion(listOf(decodeBase64Url(credentialId)), Base64.getDecoder().decode(hmacSalt), pin)
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
                    // yubikit's 1 s default is shorter than a YubiKey's makeCredential
                    // or getAssertion over NFC, and a transceive timeout throws the same
                    // TagLostException as a real contact loss. A pulled key still fails
                    // at once whatever this value is.
                    manager.startNfcDiscovery(NfcConfiguration().timeout(NFC_TIMEOUT_MS), activity) { devices.trySend(it) }
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
                    diagnosticsLogger.warning(
                        TAG,
                        "security_key_failed transport=${device.transport} cause=${error.cause}",
                        error,
                    )
                    throw error
                } catch (error: IOException) {
                    // Contact lost mid-operation: wait for the key again.
                    diagnosticsLogger.warning(TAG, "security_key_lost transport=${device.transport}", error)
                    statusState.value = SecurityKeyStatus.WAITING
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // Anything else is a bug in this code or yubikit: log it with its trace.
                    diagnosticsLogger.warning(TAG, "security_key_unexpected transport=${device.transport}", error)
                    throw error
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                manager.stopUsbDiscovery()
                // Android throws IllegalStateException for a destroyed activity;
                // reader mode went with it, and the key's result must survive.
                try {
                    manager.stopNfcDiscovery(activity)
                } catch (error: IllegalStateException) {
                    diagnosticsLogger.warning(TAG, "security_key_nfc_stop_failed", error)
                }
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
        val info = openCtap(device)?.use { it.cachedInfo } ?: return null
        diagnosticsLogger.info(
            TAG,
            "security_key_info versions=${info.versions} pinProtocols=${info.pinUvAuthProtocols} " +
                "options=${info.options} extensions=${info.extensions}",
        )
        return try {
            block(YubikitAuthenticator(info, { requireNotNull(openCtap(device)) }, commandState))
        } catch (error: CommandException) {
            throw SecurityKeyFailedException(error)
        }
    }

    // A fresh connection and CTAP2 session (SELECT + getInfo), as Yubico's
    // own client opens for every ceremony; null when the device has no FIDO2.
    private fun openCtap(device: YubiKeyDevice): Ctap2Session? {
        val connection = when {
            device.supportsConnection(FidoConnection::class.java) -> device.openConnection(FidoConnection::class.java)
            device.supportsConnection(SmartCardConnection::class.java) -> NfcCtapConnection(
                device.openConnection(SmartCardConnection::class.java),
                (device as? NfcYubiKeyDevice)?.tag?.let(IsoDep::get)?.maxTransceiveLength ?: 0,
            )
            else -> return null
        }
        return try {
            when (connection) {
                is FidoConnection -> Ctap2Session(connection)
                else -> Ctap2Session(connection as SmartCardConnection)
            }
        } catch (_: ApplicationNotAvailableException) {
            connection.close()
            null
        } catch (error: CommandException) {
            connection.close()
            // U2F-only keys answer no CTAP2 getInfo.
            throw SecurityKeyUnsupportedException(error)
        } catch (error: Exception) {
            connection.close()
            throw error
        }
    }

    // [NfcCtapApdus] on the connection, logging each APDU's header and
    // status (and the CTAP status byte), never its data.
    private inner class NfcCtapConnection(
        private val inner: SmartCardConnection,
        maxTransceiveLength: Int,
    ) : SmartCardConnection by inner {
        private val apdus = run {
            val extended = inner.isExtendedLengthApduSupported
            diagnosticsLogger.info(TAG, "security_key_nfc extended=$extended maxTransceive=$maxTransceiveLength")
            NfcCtapApdus(if (extended) maxTransceiveLength else 0) { sent, ext ->
                val received = inner.sendAndReceive(sent)
                val sw = received.takeLast(2).joinToString("") { "%02x".format(it) }
                diagnosticsLogger.info(
                    TAG,
                    "security_key_apdu cla=%02x ins=%02x p1=%02x ext=%s size=%d sw=%s data0=%s".format(
                        sent[0], sent[1], sent[2], ext, sent.size, sw,
                        received.firstOrNull()?.takeIf { received.size > 2 }?.let { "%02x".format(it) },
                    ),
                )
                received
            }
        }

        override fun sendAndReceive(apdu: ByteArray): ByteArray = apdus.sendAndReceive(apdu)
    }

    private class SecurityKeyFailedException(cause: Throwable) :
        SecurityKeyException("Security key request failed.", cause)

    // hmac-secret through yubikit's WebAuthn PRF processing: the key gets
    // SHA-256("WebAuthn PRF" || 0x00 || hmacSalt) as its salt, so one
    // hmacSalt gives the same output on every phone (and in any PRF client).
    // Each ceremony gets its own connection, session and client, as in
    // Yubico's FidoClientService and test suite.
    private class YubikitAuthenticator(
        info: Ctap2Session.InfoData,
        private val open: () -> Ctap2Session,
        private val commandState: CommandState,
    ) : SecurityKeyAuthenticator {
        override val supportsHmacSecret: Boolean = info.extensions.contains(HMAC_SECRET)

        override val supportsHmacSecretMc: Boolean = info.extensions.contains(HMAC_SECRET_MC)

        override val hasPin: Boolean = info.options[CLIENT_PIN] == true

        private fun <R> withClient(block: (Ctap2Client) -> R): R =
            open().use { block(Ctap2Client(it, listOf(HmacSecretExtension()))) }

        override fun makeCredential(
            userId: ByteArray,
            pin: CharArray,
            hmacSalt: ByteArray?,
        ): Pair<ByteArray, ByteArray?> = withClient { client ->
            val credential = try {
                client.makeCredential(clientData(), creationOptions(userId, hmacSalt), RP_ID, pin, null, commandState)
            } catch (error: ClientError) {
                throw pinError(error)
            }
            credential.rawId to prfFirst(credential)
        }

        override fun getAssertion(
            credentialIds: List<ByteArray>,
            salt: ByteArray,
            pin: CharArray,
        ): Pair<ByteArray, ByteArray?>? = withClient { client ->
            val credential = try {
                client.getAssertion(clientData(), requestOptions(credentialIds, salt), RP_ID, pin, commandState)
            } catch (error: ClientError) {
                if (error.errorCode == ClientError.Code.DEVICE_INELIGIBLE) return@withClient null
                throw pinError(error)
            }
            credential.rawId to prfFirst(credential)
        }

        private fun prfFirst(credential: PublicKeyCredential): ByteArray? =
            (credential.clientExtensionResults?.toMap(SerializationType.CBOR)?.get("prf") as? Map<*, *>)
                ?.let { it["results"] as? Map<*, *> }
                ?.let { it["first"] as? ByteArray }

        private fun pinError(error: ClientError): SecurityKeyException {
            if (error is AuthInvalidClientError && error.authType == AuthInvalidClientError.AuthType.PIN) {
                return SecurityKeyWrongPinException(error.retries, error)
            }
            return when ((error.cause as? CtapException)?.ctapError) {
                CtapException.ERR_PIN_BLOCKED, CtapException.ERR_PIN_AUTH_BLOCKED -> SecurityKeyPinBlockedException(error)
                else -> SecurityKeyFailedException(error)
            }
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
        private const val HMAC_SECRET_MC = "hmac-secret-mc"
        private const val CLIENT_PIN = "clientPin"
        private const val NFC_TIMEOUT_MS = 30_000
        private const val COSE_ES256 = -7
        private const val USER_ID_LENGTH_BYTES = 16
        private const val CHALLENGE_LENGTH_BYTES = 32
        private const val HMAC_SALT_LENGTH_BYTES = 32
        private const val HMAC_OUTPUT_LENGTH_BYTES = 32
        private const val DONE_DISPLAY_MILLIS = 600L

        private fun randomBytes(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes)

        // A non-discoverable credential, user verified (the PIN), with hmac-secret
        // on; with [hmacSalt], hmac-secret-mc evaluates it at creation.
        internal fun creationOptions(userId: ByteArray, hmacSalt: ByteArray?) = PublicKeyCredentialCreationOptions(
            PublicKeyCredentialRpEntity(RP_NAME, RP_ID),
            PublicKeyCredentialUserEntity(USER_NAME, userId, USER_NAME),
            randomBytes(CHALLENGE_LENGTH_BYTES),
            listOf(PublicKeyCredentialParameters("public-key", COSE_ES256)),
            null,
            null,
            // Non-discoverable: the key stores nothing, so Remove needs no key
            // and a failed attempt leaves nothing behind. UV stays required.
            AuthenticatorSelectionCriteria(
                null,
                ResidentKeyRequirement.DISCOURAGED,
                UserVerificationRequirement.REQUIRED,
            ),
            null,
            Extensions.fromMap(mapOf("prf" to (hmacSalt?.let(::prfEval) ?: emptyMap()))),
        )

        // UV required as at creation: the output is CredRandomWithUV's every time.
        internal fun requestOptions(credentialIds: List<ByteArray>, salt: ByteArray) = PublicKeyCredentialRequestOptions(
            randomBytes(CHALLENGE_LENGTH_BYTES),
            null,
            RP_ID,
            credentialIds.map { PublicKeyCredentialDescriptor("public-key", it) },
            UserVerificationRequirement.REQUIRED,
            Extensions.fromMap(mapOf("prf" to prfEval(salt))),
        )

        private fun prfEval(salt: ByteArray) = mapOf("eval" to mapOf("first" to base64Url(salt)))

        private fun base64Url(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun decodeBase64Url(value: String): ByteArray = Base64.getUrlDecoder().decode(value.trimEnd('='))
    }
}

/**
 * CTAP over ISO 7816 (NFC), free of Android classes. yubikit sends a CTAP
 * request over 255 bytes (an hmac-secret getAssertion, a makeCredential with
 * hmac-secret-mc) as ISO command chaining, and a YubiKey 5 over NFC answers
 * the last chunk of a chained getAssertion with PIN_AUTH_BLOCKED (0x34) at
 * once. So a chained command goes as one extended-length APDU,
 * CLA INS P1 P2 00 Lc(2) data 00 00 as yubikit's ExtendedApduFormatter frames
 * it, when the reader takes one that long ([maxExtendedLength], 0 when it
 * takes none); its chunks are answered 9000 here. Otherwise it is chained as
 * yubikit asked. 61xx and 9100 replies go back to yubikit, which sends the
 * GET RESPONSE or NFCCTAP_GETRESPONSE poll. yubikit re-sends the whole
 * request with every such poll; CTAP and python-fido2 send it with no data,
 * so this drops the data.
 */
internal class NfcCtapApdus(
    private val maxExtendedLength: Int,
    private val transmit: (apdu: ByteArray, extended: Boolean) -> ByteArray,
) {
    private val pending = mutableListOf<ByteArray>()

    fun sendAndReceive(apdu: ByteArray): ByteArray {
        val chained = apdu[0].toInt() and CLA_CHAINING != 0
        if (apdu[1].toInt() and 0xff == INS_NFCCTAP_GETRESPONSE) {
            return if (chained) SW_OK.copyOf() else transmit(apdu.copyOf(4), false)
        }
        if (maxExtendedLength == 0) return transmit(apdu, false)
        if (chained) {
            pending += apdu
            return SW_OK.copyOf()
        }
        if (pending.isEmpty()) return transmit(apdu, false)
        val chunks = pending + listOf(apdu)
        pending.clear()
        val extended = extendedApdu(chunks)
        if (extended != null && extended.size <= maxExtendedLength) return transmit(extended, true)
        for (chunk in chunks.dropLast(1)) {
            val response = transmit(chunk, false)
            if (!response.copyOfRange(response.size - 2, response.size).contentEquals(SW_OK)) return response
        }
        return transmit(apdu, false)
    }

    companion object {
        private const val CLA_CHAINING = 0x10
        private const val INS_NFCCTAP_GETRESPONSE = 0x11
        private const val MAX_EXTENDED_DATA = 0xffff
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)

        // Short APDUs CLA INS P1 P2 [Lc data], the last one unchained; null
        // when they aren't one command's chunks.
        internal fun extendedApdu(chunks: List<ByteArray>): ByteArray? {
            val last = chunks.last()
            val sameCommand = chunks.dropLast(1).all {
                it.size >= 4 && it[0].toInt() == last[0].toInt() or CLA_CHAINING &&
                    it.copyOfRange(1, 4).contentEquals(last.copyOfRange(1, 4))
            }
            if (!sameCommand) return null
            val data = chunks.fold(ByteArray(0)) { acc, chunk ->
                acc + if (chunk.size > 5) chunk.copyOfRange(5, 5 + (chunk[4].toInt() and 0xff)) else ByteArray(0)
            }
            if (data.size > MAX_EXTENDED_DATA) return null
            return last.copyOf(4) + byteArrayOf(0, (data.size shr 8).toByte(), data.size.toByte()) + data + byteArrayOf(0, 0)
        }
    }
}
