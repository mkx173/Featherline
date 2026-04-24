package com.mkx.hrttracker.model.bloodtest

import org.junit.Assert.assertEquals
import org.junit.Test

class BloodTestCatalogTest {
    @Test
    fun toCanonical_converts_e2_pmol_l_to_pg_ml() {
        val canonical = BloodTestCatalog.toCanonical(
            analyteKey = BloodAnalyteKey.E2,
            value = 367.1,
            unit = BloodUnitKey.PMOL_L
        )

        assertEquals(100.0, canonical, 1e-6)
    }

    @Test
    fun toCanonical_converts_testosterone_nmol_l_to_ng_dl() {
        val canonical = BloodTestCatalog.toCanonical(
            analyteKey = BloodAnalyteKey.T,
            value = 3.47,
            unit = BloodUnitKey.NMOL_L
        )

        assertEquals(100.0, canonical, 1e-6)
    }

    @Test
    fun toCanonical_keeps_fsh_iu_l_value_unchanged() {
        val canonical = BloodTestCatalog.toCanonical(
            analyteKey = BloodAnalyteKey.FSH,
            value = 12.5,
            unit = BloodUnitKey.IU_L
        )

        assertEquals(12.5, canonical, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun toCanonical_rejects_disallowed_unit() {
        BloodTestCatalog.toCanonical(
            analyteKey = BloodAnalyteKey.PRL,
            value = 10.0,
            unit = BloodUnitKey.IU_L
        )
    }
}
