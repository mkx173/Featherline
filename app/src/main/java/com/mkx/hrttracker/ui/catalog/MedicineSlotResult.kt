package com.mkx.hrttracker.ui.catalog

import android.os.Bundle
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import java.util.UUID

/**
 * A completed medication slot picked from the manager: which medicine, which
 * route, the dose instruction the user entered, and how many of it. The
 * medicine manager writes one of these to the caller's savedStateHandle when
 * the dose sheet is saved, replacing the old "uuid only" result. The plan
 * editor reads it and appends the slot directly — no second sheet to fill in.
 */
data class MedicineSlotResult(
    val medicineUuid: UUID,
    val applicationType: MedicationApplicationType,
    val doseInstruction: DoseInstruction,
    val count: Int,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_MEDICINE_UUID, medicineUuid.toString())
        putString(KEY_APPLICATION_TYPE, applicationType.name)
        putInt(KEY_COUNT, count)
        when (val instruction = doseInstruction) {
            is DoseInstruction.TabletFraction -> {
                putString(KEY_DOSE_KIND, DOSE_KIND_TABLET_FRACTION)
                putInt(KEY_TABLET_NUMERATOR, instruction.numerator)
                putInt(KEY_TABLET_DENOMINATOR, instruction.denominator)
            }
            is DoseInstruction.VolumeMl -> {
                putString(KEY_DOSE_KIND, DOSE_KIND_VOLUME_ML)
                putDouble(KEY_VOLUME_ML, instruction.valueMl)
            }
            is DoseInstruction.WeightGrams -> {
                putString(KEY_DOSE_KIND, DOSE_KIND_WEIGHT_GRAMS)
                putDouble(KEY_WEIGHT_GRAMS, instruction.valueGrams)
            }
            DoseInstruction.WholeUnit -> putString(KEY_DOSE_KIND, DOSE_KIND_WHOLE_UNIT)
            DoseInstruction.Noop -> putString(KEY_DOSE_KIND, DOSE_KIND_NOOP)
        }
    }

    companion object {
        fun fromBundle(bundle: Bundle): MedicineSlotResult? {
            val uuid = runCatching {
                UUID.fromString(bundle.getString(KEY_MEDICINE_UUID) ?: return null)
            }.getOrNull() ?: return null
            val applicationType = runCatching {
                MedicationApplicationType.valueOf(
                    bundle.getString(KEY_APPLICATION_TYPE) ?: return null
                )
            }.getOrNull() ?: return null
            val count = bundle.getInt(KEY_COUNT, 1).coerceAtLeast(1)
            val doseInstruction = when (bundle.getString(KEY_DOSE_KIND)) {
                DOSE_KIND_TABLET_FRACTION -> DoseInstruction.TabletFraction(
                    numerator = bundle.getInt(KEY_TABLET_NUMERATOR),
                    denominator = bundle.getInt(KEY_TABLET_DENOMINATOR),
                )
                DOSE_KIND_VOLUME_ML -> DoseInstruction.VolumeMl(
                    valueMl = bundle.getDouble(KEY_VOLUME_ML),
                )
                DOSE_KIND_WEIGHT_GRAMS -> DoseInstruction.WeightGrams(
                    valueGrams = bundle.getDouble(KEY_WEIGHT_GRAMS),
                )
                DOSE_KIND_WHOLE_UNIT -> DoseInstruction.WholeUnit
                DOSE_KIND_NOOP -> DoseInstruction.Noop
                else -> return null
            }
            return MedicineSlotResult(
                medicineUuid = uuid,
                applicationType = applicationType,
                doseInstruction = doseInstruction,
                count = count,
            )
        }

        private const val KEY_MEDICINE_UUID = "medicineUuid"
        private const val KEY_APPLICATION_TYPE = "applicationType"
        private const val KEY_COUNT = "count"
        private const val KEY_DOSE_KIND = "doseKind"
        private const val KEY_TABLET_NUMERATOR = "tabletNumerator"
        private const val KEY_TABLET_DENOMINATOR = "tabletDenominator"
        private const val KEY_VOLUME_ML = "volumeMl"
        private const val KEY_WEIGHT_GRAMS = "weightGrams"

        private const val DOSE_KIND_TABLET_FRACTION = "tabletFraction"
        private const val DOSE_KIND_VOLUME_ML = "volumeMl"
        private const val DOSE_KIND_WEIGHT_GRAMS = "weightGrams"
        private const val DOSE_KIND_WHOLE_UNIT = "wholeUnit"
        private const val DOSE_KIND_NOOP = "noop"
    }
}
