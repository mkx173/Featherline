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
    fun toCanonical_converts_e2_ng_dl_to_pg_ml() {
        val canonical = BloodTestCatalog.toCanonical(
            analyteKey = BloodAnalyteKey.E2,
            value = 15.24,
            unit = BloodUnitKey.NG_DL
        )

        assertEquals(152.4, canonical, 1e-6)
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
    fun toCanonical_converts_testosterone_ng_ml_to_ng_dl() {
        val canonical = BloodTestCatalog.toCanonical(
            analyteKey = BloodAnalyteKey.T,
            value = 3.17,
            unit = BloodUnitKey.NG_ML
        )

        assertEquals(317.0, canonical, 1e-6)
    }

    @Test
    fun toCanonical_converts_prolactin_miu_l_to_ng_ml() {
        val canonical = BloodTestCatalog.toCanonical(
            analyteKey = BloodAnalyteKey.PRL,
            value = 212.0,
            unit = BloodUnitKey.MIU_L
        )

        assertEquals(10.0, canonical, 1e-6)
    }

    @Test
    fun fromCanonical_converts_prolactin_ng_ml_to_miu_l() {
        val value = BloodTestCatalog.fromCanonical(
            analyteKey = BloodAnalyteKey.PRL,
            canonicalValue = 10.0,
            unit = BloodUnitKey.MIU_L
        )

        assertEquals(212.0, value, 1e-6)
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

    @Test
    fun definitionFor_exposes_new_e2_and_t_units() {
        assertEquals(
            setOf(BloodUnitKey.PG_ML, BloodUnitKey.PMOL_L, BloodUnitKey.NG_DL),
            BloodTestCatalog.definitionFor(BloodAnalyteKey.E2).allowedUnits
        )
        assertEquals(
            setOf(BloodUnitKey.NG_DL, BloodUnitKey.NMOL_L, BloodUnitKey.NG_ML),
            BloodTestCatalog.definitionFor(BloodAnalyteKey.T).allowedUnits
        )
        assertEquals(
            setOf(BloodUnitKey.NG_ML, BloodUnitKey.MIU_L),
            BloodTestCatalog.definitionFor(BloodAnalyteKey.PRL).allowedUnits
        )
    }
}
