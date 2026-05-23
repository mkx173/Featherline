package com.mkx.hrttracker.model.medication

/**
 * Unit the user picks for raw-mass fields on a custom medicine — pill strength,
 * single-use vial strength, and patch `TotalMg`. Storage on [Medicine] stays in
 * milligrams; this enum is solely how the value is displayed and re-parsed in
 * the editor.
 *
 * Catalog medicines are always [MG] — there is no picker for them.
 */
enum class MedicineDisplayDoseUnit {
    /** Storage unit; identity conversion. */
    MG,

    /** Micrograms (μg). 1 mg = 1_000 μg. */
    MCG,

    /** Grams. 1 g = 1_000 mg. */
    G;

    /** Convert a user-typed value in this unit back to milligrams for storage. */
    fun toMg(value: Double): Double {
        return when (this) {
            MG -> value
            MCG -> value / MG_PER_MCG
            G -> value * MG_PER_G
        }
    }

    /** Convert a stored milligram value back into this unit for display. */
    fun fromMg(mg: Double): Double {
        return when (this) {
            MG -> mg
            MCG -> mg * MG_PER_MCG
            G -> mg / MG_PER_G
        }
    }

    companion object {
        private const val MG_PER_MCG = 1_000.0
        private const val MG_PER_G = 1_000.0

        fun fromStorageValue(value: String?): MedicineDisplayDoseUnit {
            return entries.firstOrNull { it.name == value } ?: MG
        }
    }
}
