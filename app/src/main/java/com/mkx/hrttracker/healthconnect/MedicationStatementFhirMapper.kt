package com.mkx.hrttracker.healthconnect

import android.content.Context
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionCalculator
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.util.doseInstructionText
import com.mkx.hrttracker.util.medicationEntryTitle
import com.mkx.hrttracker.util.medicationRouteLabel
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.security.MessageDigest
import java.time.ZoneId
import java.time.ZoneOffset

internal class MedicationStatementFhirMapper(
    private val context: Context,
    moshi: Moshi = Moshi.Builder().build(),
) {
    private val adapter = moshi.adapter(FhirMedicationStatement::class.java)

    fun map(entry: MedicationLogEntry): MappedMedicationStatement? {
        val medicine = entry.medicine ?: return null
        val timestamp = entry.appliedAt
            .atZone(runCatching { ZoneId.of(entry.appliedAtTimeZoneId) }.getOrDefault(ZoneOffset.UTC))
            .toOffsetDateTime()
            .toString()
        val route = medicationRouteLabel(entry.applicationType, context)
        val dose = doseInstructionText(
            context = context,
            medicine = medicine,
            doseInstruction = entry.doseInstruction,
            count = entry.count,
            doseAmountDelta = entry.doseAmountDelta,
        )
        val statement = FhirMedicationStatement(
            id = fhirResourceId(entry),
            meta = FhirMeta(source = "$FHIR_BASE_URI/MedicationStatement/${entry.uuid}"),
            status = "completed",
            medicationCodeableConcept = FhirCodeableConcept(
                text = medicationEntryTitle(medicine, entry.applicationType, context)
            ),
            subject = FhirReference(display = "Self"),
            effectiveDateTime = timestamp,
            dateAsserted = timestamp,
            dosage = listOf(
                FhirDosage(
                    text = listOf(route, dose).joinToString(" · "),
                    route = FhirCodeableConcept(text = route),
                    doseAndRate = doseQuantity(entry)?.let { quantity ->
                        listOf(FhirDoseAndRate(doseQuantity = quantity))
                    },
                )
            ),
        )
        val json = adapter.toJson(statement)
        return MappedMedicationStatement(
            localId = entry.uuid.toString(),
            fhirResourceId = statement.id,
            json = json,
            fingerprint = sha256(json),
        )
    }

    private fun doseQuantity(entry: MedicationLogEntry): FhirQuantity? {
        val medicine = entry.medicine ?: return null
        DoseInstructionCalculator.perUnitAmountMg(
            medicine = medicine,
            doseInstruction = entry.doseInstruction,
            doseAmountDelta = entry.doseAmountDelta,
        )?.let { amountMg ->
            return FhirQuantity(
                value = amountMg * entry.count,
                unit = "mg",
                system = UCUM_SYSTEM,
                code = "mg",
            )
        }

        return when (val instruction = entry.doseInstruction) {
            is DoseInstruction.TabletFraction -> FhirQuantity(
                value = instruction.numerator.toDouble() /
                    instruction.denominator.toDouble() *
                    entry.count,
                unit = "tablet",
            )

            DoseInstruction.WholeUnit -> FhirQuantity(
                value = entry.count.toDouble(),
                unit = "dose",
            )

            is DoseInstruction.VolumeMl -> FhirQuantity(
                value = instruction.valueMl * entry.count,
                unit = "mL",
                system = UCUM_SYSTEM,
                code = "mL",
            )

            is DoseInstruction.WeightGrams -> FhirQuantity(
                value = instruction.valueGrams * entry.count,
                unit = "g",
                system = UCUM_SYSTEM,
                code = "g",
            )

            DoseInstruction.Noop -> null
        }
    }

    companion object {
        const val FHIR_BASE_URI = "featherline://health-connect/fhir"
        private const val UCUM_SYSTEM = "http://unitsofmeasure.org"

        fun fhirResourceId(entry: MedicationLogEntry): String =
            "featherline-${entry.uuid.toString().lowercase()}"
    }
}

internal data class MappedMedicationStatement(
    val localId: String,
    val fhirResourceId: String,
    val json: String,
    val fingerprint: String,
)

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

@JsonClass(generateAdapter = true)
internal data class FhirMedicationStatement(
    val resourceType: String = "MedicationStatement",
    val id: String,
    val meta: FhirMeta,
    val status: String,
    val medicationCodeableConcept: FhirCodeableConcept,
    val subject: FhirReference,
    val effectiveDateTime: String,
    val dateAsserted: String,
    val dosage: List<FhirDosage>,
)

@JsonClass(generateAdapter = true)
internal data class FhirMeta(val source: String)

@JsonClass(generateAdapter = true)
internal data class FhirCodeableConcept(val text: String)

@JsonClass(generateAdapter = true)
internal data class FhirReference(val display: String)

@JsonClass(generateAdapter = true)
internal data class FhirDosage(
    val text: String,
    val route: FhirCodeableConcept,
    val doseAndRate: List<FhirDoseAndRate>? = null,
)

@JsonClass(generateAdapter = true)
internal data class FhirDoseAndRate(val doseQuantity: FhirQuantity)

@JsonClass(generateAdapter = true)
internal data class FhirQuantity(
    val value: Double,
    val unit: String,
    val system: String? = null,
    val code: String? = null,
)
