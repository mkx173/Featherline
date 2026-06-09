package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.util.calibrationUnitLabel
import com.mkx.hrttracker.util.formatMainE2ConcentrationValue

internal fun formatWidgetE2Text(
    currentConcentration: Double,
    concentrationUnit: PkConcentrationUnit,
    displayUnit: BloodUnitKey,
): String {
    val canonical = when (concentrationUnit) {
        PkConcentrationUnit.PG_PER_ML -> currentConcentration
        PkConcentrationUnit.PMOL_PER_L -> BloodTestCatalog.toCanonical(
            BloodAnalyteKey.E2,
            currentConcentration,
            BloodUnitKey.PMOL_L,
        )

        PkConcentrationUnit.NG_PER_DL -> BloodTestCatalog.toCanonical(
            BloodAnalyteKey.E2,
            currentConcentration,
            BloodUnitKey.NG_DL,
        )

        PkConcentrationUnit.NG_PER_ML -> currentConcentration * 1_000.0
        PkConcentrationUnit.NMOL_PER_L -> BloodTestCatalog.toCanonical(
            BloodAnalyteKey.E2,
            currentConcentration * 1_000.0,
            BloodUnitKey.PMOL_L,
        )
    }
    val displayValue = BloodTestCatalog.fromCanonical(BloodAnalyteKey.E2, canonical, displayUnit)
    val formatted = formatMainE2ConcentrationValue(displayValue, displayUnit)
    return "E2 ~$formatted ${calibrationUnitLabel(displayUnit)}"
}

