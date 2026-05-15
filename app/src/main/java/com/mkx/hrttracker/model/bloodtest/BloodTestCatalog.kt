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
    MIU_L("miu_l"),
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
    val factorsToCanonical: Map<BloodUnitKey, Double>,
) {
    val allowedUnits: Set<BloodUnitKey> get() = factorsToCanonical.keys

    init {
        val canonicalFactor = factorsToCanonical[canonicalUnit]
        require(canonicalFactor == 1.0) {
            "Canonical unit $canonicalUnit must be present with factor 1.0; got $canonicalFactor."
        }
    }
}

object BloodTestCatalog {
    private const val E2_PMOL_L_PER_PG_ML = 3.671
    private const val PG_ML_PER_NG_DL = 10.0
    private const val T_NMOL_L_PER_NG_DL = 0.0347
    private const val NG_DL_PER_NG_ML = 100.0
    private const val PROG_NMOL_L_PER_NG_ML = 3.18
    private const val PRL_MIU_L_PER_NG_ML = 21.2

    private val definitions: Map<BloodAnalyteKey, BloodAnalyteDefinition> = mapOf(
        BloodAnalyteKey.E2 to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.PG_ML,
            factorsToCanonical = mapOf(
                BloodUnitKey.PG_ML to 1.0,
                BloodUnitKey.PMOL_L to 1.0 / E2_PMOL_L_PER_PG_ML,
                BloodUnitKey.NG_DL to PG_ML_PER_NG_DL,
            ),
        ),
        BloodAnalyteKey.T to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.NG_DL,
            factorsToCanonical = mapOf(
                BloodUnitKey.NG_DL to 1.0,
                BloodUnitKey.NMOL_L to 1.0 / T_NMOL_L_PER_NG_DL,
                BloodUnitKey.NG_ML to NG_DL_PER_NG_ML,
            ),
        ),
        BloodAnalyteKey.PROG to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.NG_ML,
            factorsToCanonical = mapOf(
                BloodUnitKey.NG_ML to 1.0,
                BloodUnitKey.NMOL_L to 1.0 / PROG_NMOL_L_PER_NG_ML,
            ),
        ),
        BloodAnalyteKey.PRL to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.NG_ML,
            factorsToCanonical = mapOf(
                BloodUnitKey.NG_ML to 1.0,
                BloodUnitKey.MIU_L to 1.0 / PRL_MIU_L_PER_NG_ML,
            ),
        ),
        BloodAnalyteKey.FSH to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.MIU_ML,
            factorsToCanonical = mapOf(
                BloodUnitKey.MIU_ML to 1.0,
                BloodUnitKey.IU_L to 1.0,
            ),
        ),
        BloodAnalyteKey.LH to BloodAnalyteDefinition(
            canonicalUnit = BloodUnitKey.MIU_ML,
            factorsToCanonical = mapOf(
                BloodUnitKey.MIU_ML to 1.0,
                BloodUnitKey.IU_L to 1.0,
            ),
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
        return value * factorToCanonical(analyteKey, unit)
    }

    fun fromCanonical(
        analyteKey: BloodAnalyteKey,
        canonicalValue: Double,
        unit: BloodUnitKey,
    ): Double {
        require(canonicalValue.isFinite()) { "Blood test value must be finite." }
        return canonicalValue / factorToCanonical(analyteKey, unit)
    }

    private fun factorToCanonical(
        analyteKey: BloodAnalyteKey,
        unit: BloodUnitKey,
    ): Double {
        return requireNotNull(definitionFor(analyteKey).factorsToCanonical[unit]) {
            "Unit ${unit.storageValue} is not allowed for analyte ${analyteKey.storageValue}."
        }
    }
}
