package com.mkx.hrttracker.model.bloodtest

enum class BloodAnalyteKey(val storageValue: String) {
    E2("e2"),
    T("t"),
    PROG("prog"),
    PRL("prl"),
    FSH("fsh"),
    LH("lh");

    companion object {
        fun fromStorageValue(value: String?): BloodAnalyteKey? {
            return entries.firstOrNull { analyte -> analyte.storageValue == value }
        }
    }
}

enum class BloodUnitKey(val storageValue: String) {
    PG_ML("pg_ml"),
    NG_ML("ng_ml"),
    NG_DL("ng_dl"),
    PMOL_L("pmol_l"),
    NMOL_L("nmol_l"),
    MIU_ML("miu_ml"),
    IU_L("iu_l");

    companion object {
        fun fromStorageValue(value: String?): BloodUnitKey? {
            return entries.firstOrNull { unit -> unit.storageValue == value }
        }
    }
}

data class BloodAnalyteDefinition(
    val canonicalUnit: BloodUnitKey,
    val allowedUnits: Set<BloodUnitKey>,
)

object BloodTestCatalog {
    private const val E2_PMOL_L_PER_PG_ML = 3.671
    private const val PG_ML_PER_NG_DL = 10.0
    private const val T_NMOL_L_PER_NG_DL = 0.0347
    private const val NG_DL_PER_NG_ML = 100.0
    private const val PROG_NMOL_L_PER_NG_ML = 3.18

    private val definitions: Map<BloodAnalyteKey, BloodAnalyteDefinition> = mapOf(
        BloodAnalyteKey.E2 to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.PG_ML,
            allowedUnits = setOf(BloodUnitKey.PG_ML, BloodUnitKey.PMOL_L, BloodUnitKey.NG_DL)
        ),
        BloodAnalyteKey.T to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.NG_DL,
            allowedUnits = setOf(BloodUnitKey.NG_DL, BloodUnitKey.NMOL_L, BloodUnitKey.NG_ML)
        ),
        BloodAnalyteKey.PROG to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.NG_ML,
            allowedUnits = setOf(BloodUnitKey.NG_ML, BloodUnitKey.NMOL_L)
        ),
        BloodAnalyteKey.PRL to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.NG_ML,
            allowedUnits = setOf(BloodUnitKey.NG_ML)
        ),
        BloodAnalyteKey.FSH to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.MIU_ML,
            allowedUnits = setOf(BloodUnitKey.MIU_ML, BloodUnitKey.IU_L)
        ),
        BloodAnalyteKey.LH to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.MIU_ML,
            allowedUnits = setOf(BloodUnitKey.MIU_ML, BloodUnitKey.IU_L)
        ),
    )

    fun definitionFor(analyteKey: BloodAnalyteKey): BloodAnalyteDefinition {
        return checkNotNull(definitions[analyteKey]) {
            "Missing blood test catalog definition for $analyteKey."
        }
    }

    fun canonicalUnitFor(analyteKey: BloodAnalyteKey): BloodUnitKey {
        return definitionFor(analyteKey).canonicalUnit
    }

    fun isUnitAllowed(analyteKey: BloodAnalyteKey, unit: BloodUnitKey): Boolean {
        return unit in definitionFor(analyteKey).allowedUnits
    }

    fun toCanonical(
        analyteKey: BloodAnalyteKey,
        value: Double,
        unit: BloodUnitKey,
    ): Double {
        require(value.isFinite()) { "Blood test value must be finite." }
        require(isUnitAllowed(analyteKey, unit)) {
            "Unit ${unit.storageValue} is not allowed for analyte ${analyteKey.storageValue}."
        }

        return when (analyteKey) {
            BloodAnalyteKey.E2 -> when (unit) {
                BloodUnitKey.PG_ML -> value
                BloodUnitKey.PMOL_L -> value / E2_PMOL_L_PER_PG_ML
                BloodUnitKey.NG_DL -> value * PG_ML_PER_NG_DL
                else -> unreachableUnit(analyteKey, unit)
            }

            BloodAnalyteKey.T -> when (unit) {
                BloodUnitKey.NG_DL -> value
                BloodUnitKey.NMOL_L -> value / T_NMOL_L_PER_NG_DL
                BloodUnitKey.NG_ML -> value * NG_DL_PER_NG_ML
                else -> unreachableUnit(analyteKey, unit)
            }

            BloodAnalyteKey.PROG -> when (unit) {
                BloodUnitKey.NG_ML -> value
                BloodUnitKey.NMOL_L -> value / PROG_NMOL_L_PER_NG_ML
                else -> unreachableUnit(analyteKey, unit)
            }

            BloodAnalyteKey.PRL -> when (unit) {
                BloodUnitKey.NG_ML -> value
                else -> unreachableUnit(analyteKey, unit)
            }

            BloodAnalyteKey.FSH,
            BloodAnalyteKey.LH,
            -> when (unit) {
                BloodUnitKey.MIU_ML,
                BloodUnitKey.IU_L,
                -> value

                else -> unreachableUnit(analyteKey, unit)
            }
        }
    }

    fun fromCanonical(
        analyteKey: BloodAnalyteKey,
        canonicalValue: Double,
        unit: BloodUnitKey,
    ): Double {
        require(canonicalValue.isFinite()) { "Blood test value must be finite." }
        require(isUnitAllowed(analyteKey, unit)) {
            "Unit ${unit.storageValue} is not allowed for analyte ${analyteKey.storageValue}."
        }

        return when (analyteKey) {
            BloodAnalyteKey.E2 -> when (unit) {
                BloodUnitKey.PG_ML -> canonicalValue
                BloodUnitKey.PMOL_L -> canonicalValue * E2_PMOL_L_PER_PG_ML
                BloodUnitKey.NG_DL -> canonicalValue / PG_ML_PER_NG_DL
                else -> unreachableUnit(analyteKey, unit)
            }

            BloodAnalyteKey.T -> when (unit) {
                BloodUnitKey.NG_DL -> canonicalValue
                BloodUnitKey.NMOL_L -> canonicalValue * T_NMOL_L_PER_NG_DL
                BloodUnitKey.NG_ML -> canonicalValue / NG_DL_PER_NG_ML
                else -> unreachableUnit(analyteKey, unit)
            }

            BloodAnalyteKey.PROG -> when (unit) {
                BloodUnitKey.NG_ML -> canonicalValue
                BloodUnitKey.NMOL_L -> canonicalValue * PROG_NMOL_L_PER_NG_ML
                else -> unreachableUnit(analyteKey, unit)
            }

            BloodAnalyteKey.PRL -> when (unit) {
                BloodUnitKey.NG_ML -> canonicalValue
                else -> unreachableUnit(analyteKey, unit)
            }

            BloodAnalyteKey.FSH,
            BloodAnalyteKey.LH,
            -> when (unit) {
                BloodUnitKey.MIU_ML,
                BloodUnitKey.IU_L,
                -> canonicalValue

                else -> unreachableUnit(analyteKey, unit)
            }
        }
    }

    private fun unreachableUnit(
        analyteKey: BloodAnalyteKey,
        unit: BloodUnitKey,
    ): Nothing {
        error("Unexpected unit ${unit.storageValue} for analyte ${analyteKey.storageValue}.")
    }
}
