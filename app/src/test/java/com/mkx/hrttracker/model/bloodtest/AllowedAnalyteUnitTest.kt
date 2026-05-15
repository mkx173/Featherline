package com.mkx.hrttracker.model.bloodtest

import org.junit.Assert.assertEquals
import org.junit.Test

class AllowedAnalyteUnitTest {
    @Test
    fun of_constructs_for_every_allowed_analyte_unit_pair() {
        BloodAnalyteKey.entries.forEach { analyte ->
            BloodTestCatalog.definitionFor(analyte).allowedUnits.forEach { unit ->
                val choice = AllowedAnalyteUnit.of(analyte, unit)
                assertEquals(analyte, choice.analyte)
                assertEquals(unit, choice.unit)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun of_rejects_disallowed_unit_for_analyte() {
        AllowedAnalyteUnit.of(BloodAnalyteKey.PRL, BloodUnitKey.IU_L)
    }
}
