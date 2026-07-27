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
    val displayValue = convertWidgetE2Value(
        concentration = currentConcentration,
        concentrationUnit = concentrationUnit,
        displayUnit = displayUnit,
    )
    val formatted = formatMainE2ConcentrationValue(displayValue, displayUnit)
    return "E2 ~$formatted ${calibrationUnitLabel(displayUnit)}"
}

internal fun convertWidgetE2Value(
    concentration: Double,
    concentrationUnit: PkConcentrationUnit,
    displayUnit: BloodUnitKey,
): Double {
    val canonical = when (concentrationUnit) {
        PkConcentrationUnit.PG_PER_ML -> concentration
        PkConcentrationUnit.PMOL_PER_L -> BloodTestCatalog.toCanonical(
            BloodAnalyteKey.E2,
            concentration,
            BloodUnitKey.PMOL_L,
        )

        PkConcentrationUnit.NG_PER_DL -> BloodTestCatalog.toCanonical(
            BloodAnalyteKey.E2,
            concentration,
            BloodUnitKey.NG_DL,
        )

        PkConcentrationUnit.NG_PER_ML -> concentration * 1_000.0
        PkConcentrationUnit.NMOL_PER_L -> BloodTestCatalog.toCanonical(
            BloodAnalyteKey.E2,
            concentration * 1_000.0,
            BloodUnitKey.PMOL_L,
        )
    }
    return BloodTestCatalog.fromCanonical(BloodAnalyteKey.E2, canonical, displayUnit)
}

// The widest realistic E2 label for a unit — a 4-digit value. Rendered invisibly behind the
// live value to reserve a fixed-width slot, so the large widget's progress bar (which shares the
// top row) keeps a constant length instead of jumping as the live value shrinks/grows. The
// integer units (pg/mL, pmol/L) top out around 4 digits and the decimal units format shorter, so
// a 4-digit integer is a safe upper bound; digits are equal-advance, so 8888 covers any value.
// Keep the "E2 ~<n> <unit>" shape in sync with formatWidgetE2Text above.
internal fun widgetE2PlaceholderText(displayUnit: BloodUnitKey): String =
    "E2 ~8888 ${calibrationUnitLabel(displayUnit)}"
