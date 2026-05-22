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
    }
}
