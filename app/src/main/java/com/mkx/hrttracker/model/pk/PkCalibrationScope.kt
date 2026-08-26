package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.squareup.moshi.Moshi
import org.erdtman.jcs.JsonCanonicalizer
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

// v2 (Phase-3 decision, 2026-08-09): the per-lab payload dropped
// providerId/assayMethodId — lab comparability is the user's own call, not
// tracked as structured identity.
const val PK_CALIBRATION_SCOPE_INPUT_DIGEST_SCHEMA =
    "hrttracker.calibration-scope-input/v2"

internal sealed interface PkCalibrationCanonicalE2ValueVerification {
    data class Verified(val canonicalValuePgml: Double) :
        PkCalibrationCanonicalE2ValueVerification

    data object InvalidInput : PkCalibrationCanonicalE2ValueVerification
    data object NumericFailure : PkCalibrationCanonicalE2ValueVerification
}

/**
 * Immutable E2 source row used by the pure calibration adapter.
 *
 * Unit and analyte identities are explicit; display labels are never
 * converted into digest identities. Provider/assay identity is deliberately
 * absent: lab comparability is the user's own judgment, not structured state
 * the app tracks (Phase-3 decision, 2026-08-09).
 */
@ConsistentCopyVisibility
data class PkCalibrationE2LabSource private constructor(
    val resultId: UUID,
    val collectedAtEpochMillis: Long,
    val analyteId: String,
    val sourceUnitSnapshot: String,
    val unitId: String,
    val sourceValue: Double,
    val canonicalValuePgml: Double,
) {
    val sourceValueBits: String?
        get() = sourceValue.takeIf(Double::isFinite)?.toCanonicalBinary64Bits()

    internal fun verifyCanonicalValuePgml(): PkCalibrationCanonicalE2ValueVerification {
        val sourceUnit = BloodUnitKey.fromStorageValue(sourceUnitSnapshot)
            ?: return PkCalibrationCanonicalE2ValueVerification.InvalidInput
        if (!BloodTestCatalog.isUnitAllowed(BloodAnalyteKey.E2, sourceUnit)) {
            return PkCalibrationCanonicalE2ValueVerification.InvalidInput
        }
        if (!sourceValue.isFinite() || !canonicalValuePgml.isFinite()) {
            return PkCalibrationCanonicalE2ValueVerification.NumericFailure
        }
        val derivedCanonicalValue = runCatching {
            BloodTestCatalog.toCanonical(BloodAnalyteKey.E2, sourceValue, sourceUnit)
        }.getOrNull() ?: return PkCalibrationCanonicalE2ValueVerification.InvalidInput
        if (!derivedCanonicalValue.isFinite()) {
            return PkCalibrationCanonicalE2ValueVerification.NumericFailure
        }
        if (derivedCanonicalValue.normalizePositiveZero().toBits() !=
            canonicalValuePgml.normalizePositiveZero().toBits()
        ) {
            return PkCalibrationCanonicalE2ValueVerification.InvalidInput
        }
        return PkCalibrationCanonicalE2ValueVerification.Verified(
            derivedCanonicalValue.normalizePositiveZero()
        )
    }

    companion object {
        fun create(
            panel: BloodTestPanel,
            result: BloodTestResult,
            analyteId: String,
            unitId: String,
        ): PkCalibrationE2LabSource? {
            val storedResult = panel.results.singleOrNull { candidate ->
                candidate.uuid == result.uuid
            } ?: return null
            if (storedResult != result) return null
            if ((result.analyte as? BloodTestResultAnalyte.Builtin)?.key !=
                BloodAnalyteKey.E2
            ) {
                return null
            }
            if (!analyteId.isStableAsciiIdentity() || !unitId.isStableAsciiIdentity()) {
                return null
            }
            val collectedAtEpochMillis = runCatching(panel.collectedAt::toEpochMilli)
                .getOrNull() ?: return null
            if (!collectedAtEpochMillis.isJcsSafeInteger()) return null
            return PkCalibrationE2LabSource(
                resultId = result.uuid,
                collectedAtEpochMillis = collectedAtEpochMillis,
                analyteId = analyteId,
                sourceUnitSnapshot = result.unitSnapshot,
                unitId = unitId,
                sourceValue = result.value.let { value ->
                    if (value == 0.0) 0.0 else value
                },
                canonicalValuePgml = result.canonicalValue.let { value ->
                    if (value == 0.0) 0.0 else value
                },
            )
        }
    }
}

/**
 * Exact medication-event identity used by the scope-input payload.
 *
 * The caller must supply the persisted event instant and explicit stable IDs.
 * This avoids reverse-engineering identities from enum names or localized text.
 */
@ConsistentCopyVisibility
data class PkCalibrationMedicationEventSource private constructor(
    val event: PkDoseEvent,
    val epochMillis: Long,
    val eventTypeId: String,
    val hormoneId: String,
    val routeId: String,
    val compoundId: String,
) {
    companion object {
        fun create(
            event: PkDoseEvent,
            epochMillis: Long,
            eventTypeId: String,
            hormoneId: String,
            routeId: String,
            compoundId: String,
        ): PkCalibrationMedicationEventSource? {
            if (event.hormone != PkHormone.ESTRADIOL || event.isPlanned) return null
            if (!epochMillis.isJcsSafeInteger()) return null
            if (!eventTypeId.isStableAsciiIdentity() ||
                !hormoneId.isStableAsciiIdentity() ||
                !routeId.isStableAsciiIdentity() ||
                !compoundId.isStableAsciiIdentity()
            ) {
                return null
            }
            if (!event.timeH.isFinite() || !event.doseMg.isFinite() || event.doseMg < 0.0) {
                return null
            }
            if (!event.releaseRateMcgPerDay.isFiniteNonNegativeOrNull()) return null
            if (!event.areaCm2.isFiniteNonNegativeOrNull()) return null
            if (event.sublingualTheta != null && !event.sublingualTheta.isFinite()) return null
            return PkCalibrationMedicationEventSource(
                event = event.copy(
                    doseMg = event.doseMg.normalizePositiveZero(),
                    releaseRateMcgPerDay = event.releaseRateMcgPerDay?.normalizePositiveZero(),
                    areaCm2 = event.areaCm2?.normalizePositiveZero(),
                    sublingualTheta = event.sublingualTheta?.normalizePositiveZero(),
                ),
                epochMillis = epochMillis,
                eventTypeId = eventTypeId,
                hormoneId = hormoneId,
                routeId = routeId,
                compoundId = compoundId,
            )
        }
    }
}

/**
 * Exact scope-input snapshot, ordered before hashing or forward evaluation. Its
 * digest is cache identity for the durable display artifact, not a trust claim.
 */
@ConsistentCopyVisibility
data class PkCalibrationScopeInputSnapshot private constructor(
    val labs: List<PkCalibrationE2LabSource>,
    val medicationEvents: List<PkCalibrationMedicationEventSource>,
    val resolvedCurrentWeightKg: Double,
    val forwardModelVersion: String,
    val digest: CanonicalDigest,
) {
    companion object {
        fun create(
            labs: List<PkCalibrationE2LabSource>,
            medicationEvents: List<PkCalibrationMedicationEventSource>,
            resolvedCurrentWeightKg: Double,
            forwardModelVersion: String,
        ): PkCalibrationScopeInputSnapshot? {
            if (labs.isEmpty()) return null
            if (!resolvedCurrentWeightKg.isFinite() || resolvedCurrentWeightKg <= 0.0) {
                return null
            }
            if (!forwardModelVersion.isStableAsciiIdentity()) return null
            if (labs.map(PkCalibrationE2LabSource::resultId).distinct().size != labs.size) {
                return null
            }
            if (medicationEvents.map { source -> source.event.id }.distinct().size !=
                medicationEvents.size
            ) {
                return null
            }

            val orderedLabs = labs.sortedBy { lab -> lab.resultId.toCanonicalString() }
            val orderedEvents = medicationEvents.sortedWith(
                compareBy<PkCalibrationMedicationEventSource>(
                    PkCalibrationMedicationEventSource::epochMillis,
                    PkCalibrationMedicationEventSource::eventTypeId,
                ).thenBy { source -> source.event.id.toCanonicalString() }
            )
            val payload = linkedMapOf<String, Any?>(
                "schema" to PK_CALIBRATION_SCOPE_INPUT_DIGEST_SCHEMA,
                "labs" to orderedLabs.map(::labIdentityPayload),
                "medicationEvents" to orderedEvents.map(::medicationEventPayload),
                "resolvedCurrentWeight" to resolvedCurrentWeightKg.toCanonicalBinary64Bits(),
                "forwardModelVersion" to forwardModelVersion,
            )
            return PkCalibrationScopeInputSnapshot(
                labs = immutableList(orderedLabs),
                medicationEvents = immutableList(orderedEvents),
                resolvedCurrentWeightKg = resolvedCurrentWeightKg.normalizePositiveZero(),
                forwardModelVersion = forwardModelVersion,
                digest = canonicalDigest(PK_CALIBRATION_SCOPE_INPUT_DIGEST_SCHEMA, payload),
            )
        }
    }
}

internal fun canonicalJsonBytes(payload: Map<String, Any?>): ByteArray {
    // Moshi only produces valid JSON. JsonCanonicalizer, not Moshi, defines the
    // cryptographic bytes and the RFC 8785 number/property rules.
    val ordinaryJson = CanonicalPayloadJsonAdapter.toJson(payload)
    return JsonCanonicalizer(ordinaryJson).encodedUTF8
}

internal fun canonicalDigest(
    schema: String,
    payload: Map<String, Any?>,
): CanonicalDigest {
    val hash = MessageDigest.getInstance("SHA-256").digest(canonicalJsonBytes(payload))
    val hex = hash.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return requireNotNull(CanonicalDigest.create(schema, "SHA-256", hex))
}

internal fun Double.toCanonicalBinary64Bits(): String {
    require(isFinite())
    return normalizePositiveZero().toBits().toULong().toString(16).padStart(16, '0')
}

internal fun String.isStableAsciiIdentity(): Boolean {
    return isNotEmpty() && all { character -> character.code in 0x21..0x7e }
}

private fun labIdentityPayload(lab: PkCalibrationE2LabSource): Map<String, Any?> {
    return linkedMapOf(
        "resultId" to lab.resultId.toCanonicalString(),
        "analyteId" to lab.analyteId,
        "collectedAt" to lab.collectedAtEpochMillis,
    )
}

private fun medicationEventPayload(
    source: PkCalibrationMedicationEventSource,
): Map<String, Any?> {
    return linkedMapOf(
        "epochMillis" to source.epochMillis,
        "eventTypeId" to source.eventTypeId,
        "eventUuid" to source.event.id.toCanonicalString(),
        "sourceGroupUuid" to source.event.sourceGroupUuid?.toCanonicalString(),
        "hormoneId" to source.hormoneId,
        "routeId" to source.routeId,
        "compoundId" to source.compoundId,
        "doseMg" to source.event.doseMg.toCanonicalBinary64Bits(),
        "releaseRateMcgPerDay" to source.event.releaseRateMcgPerDay
            ?.toCanonicalBinary64Bits(),
        "areaCm2" to source.event.areaCm2?.toCanonicalBinary64Bits(),
        "sublingualTheta" to source.event.sublingualTheta?.toCanonicalBinary64Bits(),
    )
}

private fun CanonicalDigest.toPayload(): Map<String, Any?> = linkedMapOf(
    "schema" to schema,
    "algorithm" to algorithm,
    "hexLower" to hexLower,
)

private fun UUID.toCanonicalString(): String = toString().lowercase()

private fun Double?.isFiniteNonNegativeOrNull(): Boolean {
    return this == null || (isFinite() && this >= 0.0)
}

private fun Double.normalizePositiveZero(): Double = if (this == 0.0) 0.0 else this

private fun Long.isJcsSafeInteger(): Boolean = this in -JCS_SAFE_INTEGER_MAX..JCS_SAFE_INTEGER_MAX

private fun <T> immutableList(source: List<T>): List<T> {
    return Collections.unmodifiableList(ArrayList(source))
}

private val CanonicalPayloadJsonAdapter = Moshi.Builder()
    .build()
    .adapter(Any::class.java)
    .serializeNulls()

private const val JCS_SAFE_INTEGER_MAX = 9_007_199_254_740_991L
