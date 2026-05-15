package com.mkx.hrttracker.model.bloodtest

@ConsistentCopyVisibility
data class AllowedAnalyteUnit private constructor(
    val analyte: BloodAnalyteKey,
    val unit: BloodUnitKey,
) {
    companion object {
        fun of(analyte: BloodAnalyteKey, unit: BloodUnitKey): AllowedAnalyteUnit {
            require(BloodTestCatalog.isUnitAllowed(analyte, unit)) {
                "Unit ${unit.storageValue} is not allowed for analyte ${analyte.storageValue}."
            }
            return AllowedAnalyteUnit(analyte, unit)
        }
    }
}
