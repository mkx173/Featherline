package com.mkx.hrttracker.data.backup

import android.app.Activity
import android.os.Build
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.mkx.hrttracker.BuildConfig
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** No provider on this phone can create or return the passkey. */
class PasskeyProviderUnavailableException(cause: Throwable?) :
    IOException("No passkey provider is available.", cause)

/** The user closed the Credential Manager sheet. */
class PasskeyCancelledException(cause: Throwable) : IOException("Passkey request cancelled.", cause)

/** The provider returned no WebAuthn PRF result, so the passkey can't wrap a key. */
class PasskeyPrfUnsupportedException : IOException("The passkey provider does not support PRF.")

/**
 * Passkey slots (WebAuthn PRF) through Credential Manager. The only class that
 * talks to it; each call takes the Activity its sheet needs and never keeps it.
 * The assertion signature is never checked: the PRF output is all that's used.
 */
@Singleton
class PasskeySlots @Inject constructor(
    private val keyringCrypto: KeyringCrypto,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    /**
     * Whether key setup offers a passkey: Android 14 has Credential Manager
     * built in; Android 9–13 only through the Play Services bridge, which only
     * the play flavor carries.
     */
    fun canOffer(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && BuildConfig.FLAVOR == "play")

    /** Creates a passkey, asserts it once for its PRF output, and wraps [mk]. */
    suspend fun add(activity: Activity, mk: ByteArray): KeyringSlot {
        // Credential Manager has no passkeys below Android 9.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw PasskeyProviderUnavailableException(null)
        val random = SecureRandom()
        val prfSalt = ByteArray(PRF_SALT_LENGTH_BYTES).also(random::nextBytes)
        val createJson = JSONObject()
            .put("rp", JSONObject().put("id", RP_ID).put("name", RP_NAME))
            .put(
                "user",
                JSONObject()
                    .put("id", base64Url(ByteArray(16).also(random::nextBytes)))
                    .put("name", USER_NAME)
                    .put("displayName", USER_NAME),
            )
            .put("challenge", base64Url(ByteArray(32).also(random::nextBytes)))
            .put(
                "pubKeyCredParams",
                JSONArray()
                    .put(JSONObject().put("type", "public-key").put("alg", -7))
                    .put(JSONObject().put("type", "public-key").put("alg", -257)),
            )
            .put(
                "authenticatorSelection",
                JSONObject()
                    .put("residentKey", "required")
                    .put("requireResidentKey", true)
                    .put("userVerification", "required"),
            )
            .put("extensions", prfExtension(prfSalt))
            .toString()
        val response: CreateCredentialResponse = try {
            CredentialManager.create(activity)
                .createCredential(activity, CreatePublicKeyCredentialRequest(createJson))
        } catch (error: CreateCredentialException) {
            diagnosticsLogger.warning(
                TAG,
                "passkey_create_failed type=${error.type} message=${error.errorMessage}",
                error,
            )
            throw mapCreateError(error)
        }
        val registrationJson = (response as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
            ?: throw IOException("Credential Manager returned no passkey.")
        val credentialId = JSONObject(registrationJson).getString("id")
        val registrationExtensions = JSONObject(registrationJson).optJSONObject("clientExtensionResults")
        // Key names only: the values would include the PRF output.
        diagnosticsLogger.info(
            TAG,
            "passkey_created prfKeys=${registrationExtensions?.optJSONObject("prf")?.keys()?.asSequence()?.toList()}",
        )
        // Google Password Manager returns the PRF result at creation. A
        // provider that doesn't gets one assertion as the runtime check.
        val prfOutput = registrationExtensions
            ?.optJSONObject("prf")
            ?.optJSONObject("results")
            ?.optString("first")
            ?.takeIf { it.isNotEmpty() }
            ?.let { first ->
                val bytes = decodeBase64Url(first)
                if (bytes.size == PRF_OUTPUT_LENGTH_BYTES) bytes else null.also { bytes.fill(0) }
            }
            ?.also { diagnosticsLogger.info(TAG, "passkey_prf_from_creation") }
            ?: assertCreated(activity, credentialId, prfSalt)
        return try {
            keyringCrypto.wrapPasskeySlot(
                mk = mk,
                prfOutput = prfOutput,
                rpId = RP_ID,
                credentialId = credentialId,
                prfSalt = Base64.getEncoder().encodeToString(prfSalt),
                now = System.currentTimeMillis(),
            )
        } finally {
            prfOutput.fill(0)
        }
    }

    /** Unlocks MK from whichever of [slots] the user picks. The caller zeroes the MK. */
    suspend fun unlock(activity: Activity, slots: List<KeyringSlot>): Pair<KeyringSlot, ByteArray> {
        val passkeySlots = slots.filter { it.type == KeyringCrypto.SLOT_TYPE_PASSKEY }
        require(passkeySlots.isNotEmpty()) { "No passkey slot." }
        // One request per salt; every slot this build writes has its own
        // salt, but one passkey has one slot, so in practice this is one sheet.
        var lastError: IOException? = null
        for ((salt, group) in passkeySlots.groupBy { requireNotNull(it.prfSalt) }) {
            val (credentialId, prfOutput) = try {
                assert(activity, group.map { requireNotNull(it.credentialId) }, Base64.getDecoder().decode(salt))
            } catch (error: PasskeyProviderUnavailableException) {
                // None of this group's passkeys is on the phone; try the next salt.
                lastError = error
                continue
            }
            try {
                val slot = group.firstOrNull { sameId(it.credentialId, credentialId) }
                    ?: throw IOException("The passkey returned is not in this keyring.")
                return slot to unwrap(slot, prfOutput)
            } finally {
                prfOutput.fill(0)
            }
        }
        throw checkNotNull(lastError)
    }

    /** E2 re-link: the same passkey and salt, wrapping [newMk] in a new slot. */
    suspend fun rewrap(activity: Activity, slot: KeyringSlot, newMk: ByteArray): KeyringSlot {
        val credentialId = requireNotNull(slot.credentialId)
        val prfSalt = requireNotNull(slot.prfSalt)
        val (_, prfOutput) = assert(activity, listOf(credentialId), Base64.getDecoder().decode(prfSalt))
        return try {
            keyringCrypto.wrapPasskeySlot(
                mk = newMk,
                prfOutput = prfOutput,
                rpId = slot.rpId ?: RP_ID,
                credentialId = credentialId,
                prfSalt = prfSalt,
                now = System.currentTimeMillis(),
            )
        } finally {
            prfOutput.fill(0)
        }
    }

    // The PRF output of the passkey just created, by assertion.
    private suspend fun assertCreated(
        activity: Activity,
        credentialId: String,
        prfSalt: ByteArray,
    ): ByteArray {
        // Google Password Manager doesn't list a passkey for an allow list
        // until minutes after creating it, which is why Add prefers the
        // creation result above.
        val (assertedId, prfOutput) = assert(activity, listOf(credentialId), prfSalt)
        if (!sameId(assertedId, credentialId)) {
            prfOutput.fill(0)
            throw IOException("The passkey returned is not the one just created.")
        }
        return prfOutput
    }

    private fun unwrap(slot: KeyringSlot, prfOutput: ByteArray): ByteArray = try {
        keyringCrypto.unwrapPasskeySlot(slot, prfOutput)
    } catch (error: Exception) {
        throw IOException("The passkey does not unlock its slot.", error)
    }

    // Returns the asserted credential id and its 32-byte PRF output.
    private suspend fun assert(
        activity: Activity,
        credentialIds: List<String>,
        prfSalt: ByteArray,
    ): Pair<String, ByteArray> {
        // Credential Manager has no passkeys below Android 9.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw PasskeyProviderUnavailableException(null)
        val allowCredentials = JSONArray()
        credentialIds.forEach { allowCredentials.put(JSONObject().put("type", "public-key").put("id", it)) }
        val getJson = JSONObject()
            .put("rpId", RP_ID)
            .put("challenge", base64Url(ByteArray(32).also(SecureRandom()::nextBytes)))
            .apply { if (credentialIds.isNotEmpty()) put("allowCredentials", allowCredentials) }
            .put("userVerification", "required")
            .put("extensions", prfExtension(prfSalt))
            .toString()
        diagnosticsLogger.info(TAG, "passkey_assert_start allow=${credentialIds.size}")
        val credential = try {
            CredentialManager.create(activity).getCredential(
                activity,
                GetCredentialRequest(listOf(GetPublicKeyCredentialOption(getJson))),
            ).credential
        } catch (error: GetCredentialException) {
            diagnosticsLogger.warning(
                TAG,
                "passkey_assert_failed type=${error.type} message=${error.errorMessage}",
                error,
            )
            throw when (error) {
                is GetCredentialCancellationException -> PasskeyCancelledException(error)
                is NoCredentialException,
                is GetCredentialProviderConfigurationException -> PasskeyProviderUnavailableException(error)
                else -> IOException("Passkey assertion failed.", error)
            }
        }
        val assertionJson = (credential as? PublicKeyCredential)?.authenticationResponseJson
            ?: throw IOException("Credential Manager returned no passkey.")
        val assertion = JSONObject(assertionJson)
        val extensionResults = assertion.optJSONObject("clientExtensionResults")
        val first = extensionResults
            ?.optJSONObject("prf")
            ?.optJSONObject("results")
            ?.optString("first")
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                // Key names only: the values would be the PRF output.
                diagnosticsLogger.warning(
                    TAG,
                    "passkey_prf_missing extensionKeys=${extensionResults?.keys()?.asSequence()?.toList()}",
                )
                throw PasskeyPrfUnsupportedException()
            }
        val prfOutput = decodeBase64Url(first)
        if (prfOutput.size != PRF_OUTPUT_LENGTH_BYTES) {
            prfOutput.fill(0)
            throw PasskeyPrfUnsupportedException()
        }
        return assertion.getString("id") to prfOutput
    }

    private fun mapCreateError(error: CreateCredentialException): IOException = when (error) {
        is CreateCredentialCancellationException -> PasskeyCancelledException(error)
        is CreateCredentialProviderConfigurationException,
        is CreateCredentialNoCreateOptionException -> PasskeyProviderUnavailableException(error)
        else -> IOException("Passkey creation failed.", error)
    }

    private fun prfExtension(prfSalt: ByteArray) = JSONObject().put(
        "prf",
        JSONObject().put("eval", JSONObject().put("first", base64Url(prfSalt))),
    )

    private fun sameId(a: String?, b: String): Boolean =
        a != null && a.trimEnd('=') == b.trimEnd('=')

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    // Providers send base64url; tolerate padding or the standard alphabet.
    private fun decodeBase64Url(value: String): ByteArray =
        Base64.getUrlDecoder().decode(value.trimEnd('=').replace('+', '-').replace('/', '_'))

    companion object {
        private const val TAG = "PasskeySlots"
        const val RP_ID = "asterismlabs.io"
        private const val RP_NAME = "Featherline"
        private const val USER_NAME = "Featherline backup"
        private const val PRF_SALT_LENGTH_BYTES = 32
        private const val PRF_OUTPUT_LENGTH_BYTES = 32
    }
}
