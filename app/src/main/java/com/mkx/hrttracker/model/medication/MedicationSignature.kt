package com.mkx.hrttracker.model.medication

import java.util.UUID

// Identity used to match planned and logged doses across the Plan UI and PK
// projection. Two medications with the same signature are considered "the
// same dose" — same medicine, route, and dose instruction. Counts are tracked
// separately (e.g., logged count toward required count). A PATCH_OFF slot or
// log collapses to a global "remove all patches" signature keyed on route
// alone, since it carries no medicine.
data class MedicationSignature(
    val medicineUuid: String?,
    val applicationType: String,
    val doseInstructionKind: String?,
    val tabletFractionNumerator: Int?,
    val tabletFractionDenominator: Int?,
    val doseVolumeMl: Double?,
    val doseWeightGrams: Double?,
) {
    companion object {
        fun fromGroupMedication(medication: MedicationGroupMedication): MedicationSignature {
            return fromValues(
                medicineUuid = medication.medicineUuid,
                applicationType = medication.applicationType,
                doseInstruction = medication.doseInstruction,
            )
        }

        fun fromLogEntry(entry: MedicationLogEntry): MedicationSignature {
            return fromValues(
                medicineUuid = entry.medicineUuid,
                applicationType = entry.applicationType,
                doseInstruction = entry.doseInstruction,
            )
        }

        fun patchOff(): MedicationSignature {
            return MedicationSignature(
                medicineUuid = null,
                applicationType = MedicationApplicationType.PATCH_OFF.name,
                doseInstructionKind = null,
                tabletFractionNumerator = null,
                tabletFractionDenominator = null,
                doseVolumeMl = null,
                doseWeightGrams = null,
            )
        }

        private fun fromValues(
            medicineUuid: UUID?,
            applicationType: MedicationApplicationType,
            doseInstruction: DoseInstruction,
        ): MedicationSignature {
            // PATCH_OFF carries no medicine — collapse before dereferencing it.
            if (applicationType == MedicationApplicationType.PATCH_OFF) {
                return patchOff()
            }
            return MedicationSignature(
                medicineUuid = medicineUuid?.toString(),
                applicationType = applicationType.name,
                doseInstructionKind = doseInstruction.kind.name,
                tabletFractionNumerator = (doseInstruction as? DoseInstruction.TabletFraction)?.numerator,
                tabletFractionDenominator = (doseInstruction as? DoseInstruction.TabletFraction)?.denominator,
                doseVolumeMl = (doseInstruction as? DoseInstruction.VolumeMl)?.valueMl,
                doseWeightGrams = (doseInstruction as? DoseInstruction.WeightGrams)?.valueGrams,
            )
        }

        // Pipe-delimited 7-field encoding used to ship signatures across
        // process boundaries (PendingIntent extras). Empty fields stand in
        // for null. Pipe is safe: enum names, UUIDs, doubles, and integers
        // never produce one.
        internal fun fromStorageValue(value: String): MedicationSignature? {
            val parts = value.split("|", limit = 7)
            if (parts.size != 7) {
                return null
            }
            return runCatching {
                MedicationSignature(
                    medicineUuid = parts[0].takeIf(String::isNotEmpty),
                    applicationType = parts[1],
                    doseInstructionKind = parts[2].takeIf(String::isNotEmpty),
                    tabletFractionNumerator = parts[3].takeIf(String::isNotEmpty)?.toInt(),
                    tabletFractionDenominator = parts[4].takeIf(String::isNotEmpty)?.toInt(),
                    doseVolumeMl = parts[5].takeIf(String::isNotEmpty)?.toDouble(),
                    doseWeightGrams = parts[6].takeIf(String::isNotEmpty)?.toDouble(),
                )
            }.getOrNull()
        }
    }
}

internal fun MedicationSignature.toStorageValue(): String {
    return listOf(
        medicineUuid.orEmpty(),
        applicationType,
        doseInstructionKind.orEmpty(),
        tabletFractionNumerator?.toString().orEmpty(),
        tabletFractionDenominator?.toString().orEmpty(),
        doseVolumeMl?.toString().orEmpty(),
        doseWeightGrams?.toString().orEmpty(),
    ).joinToString(separator = "|")
}
